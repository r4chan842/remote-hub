// Copyright (c) RemoteHub contributors
// SPDX-License-Identifier: BSD-3-Clause

package tsbridge

import (
	"fmt"
	"os"
	"path/filepath"
)

// prepareProcessEnv points every directory Tailscale reaches for at app-owned
// storage before tsnet starts.
//
// An Android app process has no HOME, no XDG_CACHE_HOME and no writable TMPDIR,
// and its working directory is "/". tsnet's NewLocalBackend calls
// logpolicy.LogsDir, which walks that whole fallback chain:
//
//	paths.DefaultTailscaledStateFile() -> "" outside Windows
//	os.UserCacheDir()                  -> needs XDG_CACHE_HOME or HOME
//	os.Getwd()                         -> "/" for an app process, skipped
//	os.MkdirTemp("")                   -> /tmp, not writable by an app
//
// and ends in panic("no safe place found to store log state"). A Go panic on a
// JNI thread takes the whole process down inside libgojni.so with no Java stack
// trace, which is the crash seen on HyperOS / Android 16.
//
// LogsDir honours TS_LOGS_DIR first and returns it without touching any of those
// fallbacks, so setting it removes the panic. The remaining variables keep temp
// files, cache and config inside the same app-owned tree.
//
// References: tailscale/tailscale#9836, GlassHaven/Haven#866.
func prepareProcessEnv(stateDir string) error {
	dirs := []struct{ env, dir string }{
		{"TS_LOGS_DIR", filepath.Join(stateDir, "logs")},
		{"TMPDIR", filepath.Join(stateDir, "tmp")},
		{"HOME", filepath.Join(stateDir, "home")},
		{"XDG_CACHE_HOME", filepath.Join(stateDir, "cache")},
		{"XDG_CONFIG_HOME", filepath.Join(stateDir, "config")},
	}
	for _, entry := range dirs {
		if err := os.MkdirAll(entry.dir, 0o700); err != nil {
			diagLogf("ERROR", "env", "cannot create %s=%s: %v", entry.env, entry.dir, err)
			return fmt.Errorf("create %s dir %s: %w", entry.env, entry.dir, err)
		}
		if err := os.Setenv(entry.env, entry.dir); err != nil {
			return fmt.Errorf("set %s: %w", entry.env, err)
		}
	}

	settings := []struct{ env, value string }{
		// Never upload logs to log.tailscale.com: the session is the user's business.
		{"TS_NO_LOGS_NO_SUPPORT", "true"},
		// An unstable Tailscale build aborts on unexpected conditions by default.
		// The host app must report a problem, not die.
		{"TS_DEBUG_CRASH_ON_UNEXPECTED", "false"},
	}
	for _, entry := range settings {
		if err := os.Setenv(entry.env, entry.value); err != nil {
			return fmt.Errorf("set %s: %w", entry.env, err)
		}
	}
	diagLogf("INFO", "env", "writable dirs ready under %s (logs, tmp, home, cache, config); log uploads disabled", stateDir)
	return nil
}
