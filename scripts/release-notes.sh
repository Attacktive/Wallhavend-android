#!/usr/bin/env bash
#
# Produce cleaned, flat release notes for a tag range — the single source of
# truth shared by release.yaml (per-tag delta) and promotion-notes.yaml
# (cumulative range for a beta -> production promotion).
#
# Usage: release-notes.sh <from-tag> <to-tag>
#   <from-tag>  Exclusive lower bound. Empty => behave like a normal release and
#               let GitHub auto-pick the previous tag (matches release.yaml).
#   <to-tag>    Inclusive upper bound (required).
#
# Prints cleaned notes to stdout. Applies no length limit; callers truncate as
# needed (Play caps "what's new" at ~500 chars).
set -euo pipefail

# Conventional-commit types dropped as non-user-facing noise. refactor is kept on purpose — it can be user-visible.
NOISE_TYPES='build|chore|ci|docs|style|test'

# Shown when nothing user-facing survives filtering (e.g. a chore-only release), so "what's new" is never blank.
EMPTY_NOTES='* Maintenance and improvements'

# Strip GitHub's auto-generated boilerplate down to a flat bullet list.
clean_notes() {
	sed '/^## What'\''s Changed$/d' | sed 's/ by @[^ ]* in [^ ]*//g' | sed '/^\*\*Full Changelog\*\*:/d' | sed '/^[[:space:]]*$/d'
}

# Drop bullets whose message is a non-user-facing commit type, e.g. "* ci: ...", "- chore!: ...", "* style(x): ...".
drop_noise() {
	grep -vE "^[*-] +(${NOISE_TYPES})(\([^)]*\))?!?: " || true
}

# Filter noise, then fall back to a generic line when nothing user-facing remains.
finalize_notes() {
	local n
	n="$(drop_noise)"
	[ -n "$n" ] || n="$EMPTY_NOTES"
	printf '%s' "$n"
}

# Echo cleaned notes for the (from, to] tag range.
release_notes() {
	local from="$1" to="$2"
	local repo body notes prev range

	repo="${GITHUB_REPOSITORY:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"

	git rev-parse -q --verify "refs/tags/${to}" >/dev/null || { echo "release-notes: tag not found: ${to}" >&2; return 1; }
	if [ -n "$from" ]; then
		git rev-parse -q --verify "refs/tags/${from}" >/dev/null || { echo "release-notes: tag not found: ${from}" >&2; return 1; }
	fi

	# Primary: GitHub's generated notes. A single call over the whole range is inherently one deduped list — no per-version merging needed.
	if [ -n "$from" ]; then
		body="$(gh api "repos/${repo}/releases/generate-notes" -f tag_name="${to}" -f previous_tag_name="${from}" --jq '.body' 2>/dev/null || true)"
	else
		body="$(gh api "repos/${repo}/releases/generate-notes" -f tag_name="${to}" --jq '.body' 2>/dev/null || true)"
	fi

	notes="$(printf '%s' "$body" | clean_notes)"

	# Fallback: commit subjects in range.
	if [ -z "$notes" ]; then
		if [ -n "$from" ]; then
			prev="$from"
		else
			prev="$(git describe --tags --abbrev=0 "${to}^" 2>/dev/null || true)"
		fi

		if [ -n "$prev" ]; then
			range="${prev}..${to}"
		else
			range="${to}"
		fi

		notes="$(git log "$range" --pretty='- %s' || true)"
	fi

	printf '%s' "$notes" | finalize_notes
}

main() {
	if [ "$#" -lt 2 ]; then
		echo "usage: $0 <from-tag> <to-tag>" >&2
		exit 2
	fi

	release_notes "$1" "$2"
}

if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
	main "$@"
fi
