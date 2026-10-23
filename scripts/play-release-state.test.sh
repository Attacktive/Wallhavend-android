#!/usr/bin/env bash
# Unit tests for the pure Google Play release-history helpers.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/play-release-state.sh
source "${DIR}/play-release-state.sh"

published_and_pending='{"releases":[{"releaseName":"3.8.40","activeArtifacts":[{"versionCode":109}],"releaseLifecycleState":"RELEASE_LIFECYCLE_STATE_PUBLISHED"},{"releaseName":"3.8.41","activeArtifacts":[{"versionCode":110}],"releaseLifecycleState":"RELEASE_LIFECYCLE_STATE_PUBLISHED"},{"releaseName":"3.8.42","activeArtifacts":[{"versionCode":111}],"releaseLifecycleState":"RELEASE_LIFECYCLE_STATE_IN_REVIEW"}]}'
review_only='{"releases":[{"releaseName":"3.8.42","activeArtifacts":[{"versionCode":111}],"releaseLifecycleState":"RELEASE_LIFECYCLE_STATE_IN_REVIEW"}]}'
empty_releases='{"releases":[]}'

assert_eq() {
	local name="$1" expected="$2" actual="$3"
	if [ "${actual}" != "${expected}" ]; then
		printf 'FAIL - %s: expected %q, got %q\n' "${name}" "${expected}" "${actual}" >&2
		exit 1
	fi

	printf 'ok - %s\n' "${name}"
}

assert_status() {
	local name="$1" expected="$2" wanted="$3" releases="$4" actual=0
	is_published_version_code "${wanted}" <<< "${releases}" || actual=$?
	assert_eq "${name}" "${expected}" "${actual}"
}

assert_eq 'latest published ignores release in review' '110' "$(latest_published_version_code <<< "${published_and_pending}")"
assert_eq 'no published release produces an empty selection' '' "$(latest_published_version_code <<< "${review_only}")"
assert_eq 'empty release history produces an empty selection' '' "$(latest_published_version_code <<< "${empty_releases}")"
assert_status 'published beta artifact is eligible' 0 110 "${published_and_pending}"
assert_status 'in-review artifact is ineligible' 1 111 "${published_and_pending}"
assert_status 'unknown artifact is ineligible' 1 112 "${published_and_pending}"
assert_eq 'missing version has explanatory status' 'not present' "$(release_states_for_version_code 112 <<< "${published_and_pending}")"
assert_eq 'in-review status is reported' 'RELEASE_LIFECYCLE_STATE_IN_REVIEW' "$(release_states_for_version_code 111 <<< "${published_and_pending}")"
assert_eq 'pending review identified by version' '3.8.42 (version codes: 111)' "$(releases_in_review <<< "${published_and_pending}")"
assert_eq 'empty history has no pending review' '' "$(releases_in_review <<< "${empty_releases}")"
assert_eq 'human-readable release summary identifies both states' $'Google Play beta releases:\n  3.8.40 [109]: RELEASE_LIFECYCLE_STATE_PUBLISHED\n  3.8.41 [110]: RELEASE_LIFECYCLE_STATE_PUBLISHED\n  3.8.42 [111]: RELEASE_LIFECYCLE_STATE_IN_REVIEW' "$(summarize_play_releases beta <<< "${published_and_pending}" 2>&1)"

printf '\nall tests passed\n'
