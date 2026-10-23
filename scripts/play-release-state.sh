#!/usr/bin/env bash
# Parse the published and pending release history returned by applications.tracks.releases.list.

summarize_play_releases() {
	local track="$1"

	printf 'Google Play %s releases:\n' "${track}" >&2
	jq -r '[.releases[]? | "  \(.releaseName // "(unnamed)") [\([.activeArtifacts[]?.versionCode | tostring] | join(", "))]: \(.releaseLifecycleState // "UNKNOWN")"] | if length == 0 then ["  (none)"] else . end | .[]' >&2
}

latest_published_version_code() {
	jq -r '[.releases[]? | select(.releaseLifecycleState == "RELEASE_LIFECYCLE_STATE_PUBLISHED") | .activeArtifacts[]?.versionCode | tonumber] | max // empty'
}

is_published_version_code() {
	local wanted="$1"

	jq -e --arg wanted "${wanted}" 'any(.releases[]?; .releaseLifecycleState == "RELEASE_LIFECYCLE_STATE_PUBLISHED" and any(.activeArtifacts[]?; (.versionCode | tostring) == $wanted))' >/dev/null
}

release_states_for_version_code() {
	local wanted="$1"

	jq -r --arg wanted "${wanted}" '[.releases[]? | select(any(.activeArtifacts[]?; (.versionCode | tostring) == $wanted)) | .releaseLifecycleState // "UNKNOWN"] | unique | join(", ") | if . == "" then "not present" else . end'
}

releases_in_review() {
	jq -r '.releases[]? | select(.releaseLifecycleState == "RELEASE_LIFECYCLE_STATE_IN_REVIEW") | "\(.releaseName // "(unnamed)") (version codes: \([.activeArtifacts[]?.versionCode | tostring] | join(", ")))"'
}
