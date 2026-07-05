#!/usr/bin/env bash
# m2509 H4: install the pre-commit hook that blocks keystore commits.
# Idempotent — safe to re-run after every fresh clone or `.git` reset.

set -euo pipefail

repo_root=$(git rev-parse --show-toplevel)
hook_src="$repo_root/scripts/git-hooks/pre-commit"
hook_dst="$repo_root/.git/hooks/pre-commit"

if [ ! -f "$hook_src" ]; then
    echo "ERROR: missing $hook_src" >&2
    exit 1
fi

cp "$hook_src" "$hook_dst"
chmod +x "$hook_dst"

echo "Installed pre-commit hook -> $hook_dst"
