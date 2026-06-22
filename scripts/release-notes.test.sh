#!/usr/bin/env bash
#
# Unit tests for the pure text helpers in release-notes.sh.
# Run: bash scripts/release-notes.test.sh
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Source for its functions; main() is guarded, so nothing executes on source.
# shellcheck source=scripts/release-notes.sh
source "${DIR}/release-notes.sh"
set +e  # tests track failures manually

fails=0
assert_eq() {
	local name="$1" expected="$2" actual="$3"
	if [ "$expected" = "$actual" ]; then
		printf 'ok   - %s\n' "$name"
	else
		printf 'FAIL - %s\n' "$name"
		printf '  expected: %q\n' "$expected"
		printf '  actual:   %q\n' "$actual"
		fails=$((fails + 1))
	fi
}

# GitHub's generate-notes body collapses to a flat bullet list.
github_body="## What's Changed
* Fix crash on startup by @attacktive in https://github.com/a/b/pull/1
* Optimize image loading by @attacktive in https://github.com/a/b/pull/2

**Full Changelog**: https://github.com/a/b/compare/1.0.0...1.1.2"

assert_eq "cleans header, author/PR suffix, full-changelog, blanks" "* Fix crash on startup
* Optimize image loading" "$(printf '%s\n' "$github_body" | clean_notes)"

assert_eq "passes already-clean input through unchanged" "- a
- b" "$(printf '%s\n' "- a
- b" | clean_notes)"

assert_eq "empty input stays empty" "" "$(printf '' | clean_notes)"

# drop_noise removes non-user-facing commit types; keeps features/fixes/refactors/untyped.
noisy="* ci: bump some action
* feature: add a thing
* chore: tidy up
* Localize strings
* style: reformat
* bugfix: fix the bug
* refactor: move Pin to second slot"

assert_eq "drops ci/chore/style/test; keeps feature/bugfix/refactor/untyped" "* feature: add a thing
* Localize strings
* bugfix: fix the bug
* refactor: move Pin to second slot" "$(printf '%s\n' "$noisy" | drop_noise)"

assert_eq "drops scoped/breaking/hyphen variants too" "- keep me" "$(printf '%s\n' "- ci(deps): x
- chore!: y
- keep me
- test: z" | drop_noise)"

# finalize_notes: noise-only collapses to the generic line; real notes survive.
assert_eq "chore-only release falls back to the generic line" "$EMPTY_NOTES" "$(printf '%s\n' "* chore: bump version to 1.2.3
* ci: bump some action" | finalize_notes)"

assert_eq "real notes survive, noise dropped, no fallback" "* feature: shiny thing" "$(printf '%s\n' "* feature: shiny thing
* chore: tidy" | finalize_notes)"

if [ "$fails" -gt 0 ]; then
	printf '\n%d test(s) failed\n' "$fails" >&2
	exit 1
fi
printf '\nall tests passed\n'
