// Copyright (c) RemoteHub contributors
// SPDX-License-Identifier: BSD-3-Clause

//go:build !android

package tsbridge

// applyAndroidVersion is a no-op off Android; the standard library enumerates
// interfaces correctly there.
func applyAndroidVersion(int) {}
