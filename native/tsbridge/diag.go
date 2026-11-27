// Copyright (c) RemoteHub contributors
// SPDX-License-Identifier: BSD-3-Clause

package tsbridge

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"runtime/debug"
	"strings"
	"sync"
	"time"
)

// This file is the app's black box. Everything the tunnel does is recorded here,
// including the verbose tsnet log that used to be thrown away, and every panic is
// caught at the JNI boundary so a Go failure becomes a readable error instead of
// a dead process.
//
// Records live in two places:
//   - a ring buffer the Kotlin side polls (LogsJSON), which survives node
//     recreation, so a failed start can be inspected after a retry;
//   - an append-only file under the app's own storage, so the lines leading to a
//     hard native crash are still on disk when the app is opened again.

// Record is one diagnostic line.
type Record struct {
	Seq        int64  `json:"seq"`
	TimeMillis int64  `json:"timeMillis"`
	Level      string `json:"level"`
	Source     string `json:"source"`
	Message    string `json:"message"`
}

// DirReport is the writability probe for one directory Tailscale needs.
type DirReport struct {
	Name     string `json:"name"`
	Path     string `json:"path"`
	Writable bool   `json:"writable"`
	Error    string `json:"error"`
}

// EnvReport answers "what does this process actually see" without needing root.
type EnvReport struct {
	GoVersion      string      `json:"goVersion"`
	Goos           string      `json:"goos"`
	Goarch         string      `json:"goarch"`
	AndroidVersion int         `json:"androidVersion"`
	WorkingDir     string      `json:"workingDir"`
	LogFile        string      `json:"logFile"`
	Goroutines     int         `json:"goroutines"`
	Dirs           []DirReport `json:"dirs"`
}

const (
	diagMaxRecords = 900
	diagMaxMessage = 8000
	diagFileMax    = 512 * 1024
)

var diag = struct {
	mu      sync.Mutex
	seq     int64
	records []Record
	dir     string
	path    string
	file    *os.File
	size    int64
}{records: make([]Record, 0, 256)}

// androidRelease is set from Kotlin because anet needs the device's major Android
// version to decide between ioctl and netlink.
var androidRelease struct {
	sync.Mutex
	value int
}

// SetAndroidVersion passes Build.VERSION.RELEASE's major number to the interface
// getter. Without it anet may fall back to netlink, which Android 11+ denies.
func SetAndroidVersion(major int) {
	androidRelease.Lock()
	androidRelease.value = major
	androidRelease.Unlock()
	applyAndroidVersion(major)
	diagLog("INFO", "bridge", fmt.Sprintf("android release=%d", major))
}

// SetLogDir opens the on-disk log inside app-owned storage. Call it once, before
// any node is created, so even a first-start failure is written to disk.
func SetLogDir(dir string) {
	diag.mu.Lock()
	defer diag.mu.Unlock()
	if dir == "" {
		return
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return
	}
	diag.dir = dir
	diag.path = filepath.Join(dir, "native.log")
	if diag.file != nil {
		diag.file.Close()
		diag.file = nil
	}
	f, err := os.OpenFile(diag.path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o600)
	if err != nil {
		diag.path = ""
		return
	}
	diag.file = f
	if info, err := f.Stat(); err == nil {
		diag.size = info.Size()
	}
}

// LogsJSON returns records newer than sinceSeq, oldest first. It always returns a
// JSON array, never null, and never panics across the JNI boundary.
func LogsJSON(sinceSeq int64, limit int) (out string) {
	defer protect("LogsJSON", func(string) { out = "[]" })
	if limit <= 0 || limit > diagMaxRecords {
		limit = diagMaxRecords
	}
	diag.mu.Lock()
	defer diag.mu.Unlock()
	picked := make([]Record, 0, limit)
	for _, rec := range diag.records {
		if rec.Seq > sinceSeq {
			picked = append(picked, rec)
			if len(picked) >= limit {
				break
			}
		}
	}
	return encode(picked)
}

// LogSeq is the newest sequence number, so a poller can ask only for what is new.
func LogSeq() int64 {
	diag.mu.Lock()
	defer diag.mu.Unlock()
	return diag.seq
}

// ClearLogs empties the buffer and truncates the file.
func ClearLogs() {
	diag.mu.Lock()
	defer diag.mu.Unlock()
	diag.records = make([]Record, 0, 256)
	if diag.file != nil {
		diag.file.Close()
		diag.file = nil
	}
	if diag.path != "" {
		os.Remove(diag.path)
		os.Remove(diag.path + ".1")
		f, err := os.OpenFile(diag.path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o600)
		if err == nil {
			diag.file = f
			diag.size = 0
		}
	}
}

// DiagnosticsJSON reports the environment the tunnel is running in: which
// directories exist and are writable, what the working directory is, and which
// Android version the interface getter was told about.
func DiagnosticsJSON() (out string) {
	defer protect("DiagnosticsJSON", func(string) { out = "{}" })
	report := EnvReport{
		GoVersion:  runtime.Version(),
		Goos:       runtime.GOOS,
		Goarch:     runtime.GOARCH,
		Goroutines: runtime.NumGoroutine(),
		Dirs:       []DirReport{},
	}
	androidRelease.Lock()
	report.AndroidVersion = androidRelease.value
	androidRelease.Unlock()
	if wd, err := os.Getwd(); err == nil {
		report.WorkingDir = wd
	} else {
		report.WorkingDir = "error: " + err.Error()
	}
	diag.mu.Lock()
	report.LogFile = diag.path
	diag.mu.Unlock()
	for _, name := range []string{"TS_LOGS_DIR", "TMPDIR", "HOME", "XDG_CACHE_HOME", "XDG_CONFIG_HOME", "STATE_DIRECTORY"} {
		path := os.Getenv(name)
		item := DirReport{Name: name, Path: path}
		if path == "" {
			item.Error = "unset"
		} else {
			item.Writable, item.Error = probeWritable(path)
		}
		report.Dirs = append(report.Dirs, item)
	}
	return encode(report)
}

func probeWritable(dir string) (bool, string) {
	info, err := os.Stat(dir)
	if err != nil {
		return false, err.Error()
	}
	if !info.IsDir() {
		return false, "not a directory"
	}
	probe, err := os.CreateTemp(dir, "rh-probe-")
	if err != nil {
		return false, err.Error()
	}
	name := probe.Name()
	probe.Close()
	os.Remove(name)
	return true, ""
}

func diagLog(level, source, message string) {
	message = redact(message)
	if len(message) > diagMaxMessage {
		message = message[:diagMaxMessage] + " …(truncated)"
	}
	diag.mu.Lock()
	defer diag.mu.Unlock()
	diag.seq++
	rec := Record{
		Seq:        diag.seq,
		TimeMillis: time.Now().UnixMilli(),
		Level:      level,
		Source:     source,
		Message:    message,
	}
	diag.records = append(diag.records, rec)
	if len(diag.records) > diagMaxRecords {
		diag.records = diag.records[len(diag.records)-diagMaxRecords:]
	}
	diagWrite(rec)
}

func diagLogf(level, source, format string, args ...any) {
	diagLog(level, source, fmt.Sprintf(format, args...))
}

// diagWrite appends one line to disk, rotating once the file grows past its cap.
// Callers hold diag.mu.
func diagWrite(rec Record) {
	if diag.file == nil {
		return
	}
	line := fmt.Sprintf("%d\t%s\t%s\t%s\t%s\n",
		rec.TimeMillis, rec.Level, rec.Source,
		strings.ReplaceAll(rec.Message, "\n", " ⏎ "), rec.Seq)
	n, err := diag.file.WriteString(line)
	if err != nil {
		return
	}
	diag.size += int64(n)
	if diag.size < diagFileMax {
		return
	}
	diag.file.Close()
	diag.file = nil
	os.Remove(diag.path + ".1")
	os.Rename(diag.path, diag.path+".1")
	if f, err := os.OpenFile(diag.path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o600); err == nil {
		diag.file = f
		diag.size = 0
	}
}

// protect is the deferred guard for every exported entry point. A Go panic on a
// thread called from Java unwinds into libgojni.so and aborts the whole app with
// no Java stack trace; recovering here keeps the app alive and records the stack.
func protect(name string, onPanic func(message string)) {
	r := recover()
	if r == nil {
		return
	}
	message := fmt.Sprintf("%s panic: %v", name, r)
	diagLog("ERROR", "bridge", message+"\n"+string(debug.Stack()))
	if onPanic != nil {
		onPanic(message)
	}
}

// goSafe runs a background goroutine with the same guard: a panic on a goroutine
// nobody recovers is fatal no matter what the JNI boundary does.
func goSafe(name string, fn func()) {
	go func() {
		defer protect(name, nil)
		fn()
	}()
}

var secretPatterns = []*regexp.Regexp{
	regexp.MustCompile(`tskey-[A-Za-z0-9\-]+`),
	regexp.MustCompile(`github_pat_[A-Za-z0-9_]+`),
	regexp.MustCompile(`gh[pousr]_[A-Za-z0-9]{8,}`),
	regexp.MustCompile(`(?i)\b(privkey|nodekey|machinekey):[0-9a-f]+`),
	regexp.MustCompile(`(?i)\b(password|passphrase|passwd|secret|authkey|auth_key|token|apikey|api_key)(\s*[=:]\s*)(\S+)`),
}

// redact keeps credentials out of the log the user may copy or share. The key
// type stays visible because "which kind of key" is often the actual problem.
func redact(text string) string {
	if text == "" {
		return text
	}
	text = secretPatterns[0].ReplaceAllString(text, "tskey-***")
	text = secretPatterns[1].ReplaceAllString(text, "github_pat_***")
	text = secretPatterns[2].ReplaceAllString(text, "gh*_***")
	text = secretPatterns[3].ReplaceAllString(text, "$1:***")
	text = secretPatterns[4].ReplaceAllString(text, "$1$2***")
	return text
}

// LogFromHost lets the Kotlin side write into the same on-disk log, so one file
// holds the whole story of a session.
func LogFromHost(level, source, message string) {
	switch strings.ToUpper(level) {
	case "DEBUG", "INFO", "WARN", "ERROR":
		diagLog(strings.ToUpper(level), source, message)
	default:
		diagLog("INFO", source, message)
	}
}
