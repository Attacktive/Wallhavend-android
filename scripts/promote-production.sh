#!/usr/bin/env bash
# Promote an already-published beta version to Google Play production without rebuilding it.
# An explicit target must be an existing Wallhavend tag whose exact versionCode is completed on the beta track.
# With no target, the latest completed beta versionCode is mapped back to its Wallhavend tag.
set -euo pipefail

PACKAGE_NAME='xyz.attacktive.wallhavend'
TARGET_VERSION="${1:-${TARGET_VERSION:-}}"
GOOGLE_PLAY_SERVICE_ACCOUNT_JSON="${GOOGLE_PLAY_SERVICE_ACCOUNT_JSON:-}"

if [ -n "${TARGET_VERSION}" ] && [[ ! "${TARGET_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
	echo "Target version must be a semantic version tag such as 2.3.2: ${TARGET_VERSION}" >&2
	exit 2
fi
if [ -z "${GOOGLE_PLAY_SERVICE_ACCOUNT_JSON}" ]; then
	echo 'GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is required' >&2
	exit 2
fi

base64url() {
	openssl base64 -A | tr '+/' '-_' | tr -d '='
}

version_code_for_tag() {
	local tag="$1"

	git show "${tag}:app/build.gradle.kts" 2>/dev/null | sed -nE 's/^[[:space:]]*versionCode = ([0-9]+).*$/\1/p' | head -n 1
}

version_name_for_tag() {
	local tag="$1"

	git show "${tag}:app/build.gradle.kts" 2>/dev/null | sed -nE 's/^[[:space:]]*versionName = "([^"]+)".*$/\1/p' | head -n 1
}

tag_for_version_code() {
	local wanted="$1"
	local tag code

	while IFS= read -r tag; do
		code="$(version_code_for_tag "${tag}" || true)"
		if [ "${code}" = "${wanted}" ]; then
			printf '%s' "${tag}"
			return 0
		fi
	done < <(git tag --sort=-version:refname | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' || true)

	return 1
}

play_api() {
	local method="$1"
	local path="$2"
	local body="${3:-}"
	local args=(
		--fail-with-body
		--silent
		--show-error
		--request "${method}"
		--header "Authorization: Bearer ${access_token}"
		--header 'Content-Type: application/json'
	)

	if [ -n "${body}" ]; then
		args+=(--data "${body}")
	fi

	local response_file
	response_file="$(mktemp)"
	if ! curl "${args[@]}" --output "${response_file}" "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE_NAME}/${path}"; then
		echo "Google Play API rejected ${method} ${path}" >&2
		if [ -s "${response_file}" ]; then
			jq -c '{code: (.error.code // null), status: (.error.status // null), message: (.error.message // .message // "Unknown error")}' "${response_file}" >&2 || true
		fi
		rm -f "${response_file}"
		return 1
	fi

	cat "${response_file}"
	rm -f "${response_file}"
}

target_version_code=''
target_version_name=''
if [ -n "${TARGET_VERSION}" ]; then
	git rev-parse -q --verify "refs/tags/${TARGET_VERSION}" >/dev/null || {
		echo "Target tag does not exist: ${TARGET_VERSION}" >&2
		exit 1
	}

	target_version_code="$(version_code_for_tag "${TARGET_VERSION}" || true)"
	target_version_name="$(version_name_for_tag "${TARGET_VERSION}" || true)"
	if [ -z "${target_version_code}" ] || [ -z "${target_version_name}" ]; then
		echo "Could not read versionCode/versionName from ${TARGET_VERSION}" >&2
		exit 1
	fi
	if [ "${target_version_name}" != "${TARGET_VERSION}" ]; then
		echo "Tag ${TARGET_VERSION} contains versionName ${target_version_name}" >&2
		exit 1
	fi
fi

credentials_file="$(mktemp)"
private_key_file="$(mktemp)"
edit_id=''
cleanup() {
	if [ -n "${edit_id}" ]; then
		play_api DELETE "edits/${edit_id}" >/dev/null 2>&1 || true
	fi
	rm -f "${credentials_file}" "${private_key_file}"
}
trap cleanup EXIT

printf '%s' "${GOOGLE_PLAY_SERVICE_ACCOUNT_JSON}" > "${credentials_file}"
jq -r '.private_key // empty' "${credentials_file}" > "${private_key_file}"
chmod 600 "${private_key_file}"
client_email="$(jq -r '.client_email // empty' "${credentials_file}")"
if [ -z "${client_email}" ] || [ ! -s "${private_key_file}" ]; then
	echo 'Google Play service account JSON is missing client_email or private_key' >&2
	exit 1
fi

issued_at="$(date +%s)"
expires_at="$((issued_at + 3600))"
jwt_header="$(printf '%s' '{"alg":"RS256","typ":"JWT"}' | base64url)"
jwt_claims="$(jq -cn \
	--arg iss "${client_email}" \
	--arg scope 'https://www.googleapis.com/auth/androidpublisher' \
	--arg aud 'https://oauth2.googleapis.com/token' \
	--argjson iat "${issued_at}" \
	--argjson exp "${expires_at}" \
	'{iss:$iss,scope:$scope,aud:$aud,iat:$iat,exp:$exp}')"
jwt_claims="$(printf '%s' "${jwt_claims}" | base64url)"
unsigned_jwt="${jwt_header}.${jwt_claims}"
jwt_signature="$(printf '%s' "${unsigned_jwt}" | openssl dgst -sha256 -sign "${private_key_file}" -binary | base64url)"
assertion="${unsigned_jwt}.${jwt_signature}"
token_response="$(curl --fail-with-body --silent --show-error \
	--request POST \
	--header 'Content-Type: application/x-www-form-urlencoded' \
	--data-urlencode 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer' \
	--data-urlencode "assertion=${assertion}" \
	'https://oauth2.googleapis.com/token')"
access_token="$(jq -r '.access_token // empty' <<< "${token_response}")"
if [ -z "${access_token}" ]; then
	echo 'Google OAuth token response did not contain an access token' >&2
	exit 1
fi
if [ -n "${GITHUB_ACTIONS:-}" ]; then
	echo "::add-mask::${access_token}"
fi

edit="$(play_api POST 'edits' '{}')"
edit_id="$(jq -r '.id // empty' <<< "${edit}")"
if [ -z "${edit_id}" ]; then
	echo 'Google Play did not return an edit ID' >&2
	exit 1
fi

beta_track="$(play_api GET "edits/${edit_id}/tracks/beta")"
if [ -z "${TARGET_VERSION}" ]; then
	target_version_code="$(jq -r '[.releases[]? | select(.status == "completed") | .versionCodes[]? | tonumber] | max // empty' <<< "${beta_track}")"
	if [ -z "${target_version_code}" ]; then
		echo 'Beta has no completed release to promote' >&2
		exit 1
	fi

	TARGET_VERSION="$(tag_for_version_code "${target_version_code}" || true)"
	if [ -z "${TARGET_VERSION}" ]; then
		echo "Could not map latest completed beta versionCode ${target_version_code} to a Git tag" >&2
		exit 1
	fi
	if [[ ! "${TARGET_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
		echo "Latest completed beta versionCode ${target_version_code} maps to a non-semantic tag: ${TARGET_VERSION}" >&2
		exit 1
	fi

	target_version_name="$(version_name_for_tag "${TARGET_VERSION}" || true)"
	if [ "${target_version_name}" != "${TARGET_VERSION}" ]; then
		echo "Tag ${TARGET_VERSION} contains versionName ${target_version_name}" >&2
		exit 1
	fi
else
	beta_status="$(jq -r --arg version_code "${target_version_code}" '[.releases[]? | select(.status == "completed" and any(.versionCodes[]?; (. | tostring) == ($version_code | tostring)))] | if length > 0 then "completed" else empty end' <<< "${beta_track}")"
	if [ "${beta_status}" != 'completed' ]; then
		echo "Version ${TARGET_VERSION} (${target_version_code}) is not a completed beta release" >&2
		exit 1
	fi
fi

production_track="$(play_api GET "edits/${edit_id}/tracks/production")"
if jq -e '.releases[]? | select(.status == "inProgress" or .status == "draft")' <<< "${production_track}" >/dev/null; then
	echo 'Production has an in-progress or draft release; refusing to replace it' >&2
	exit 1
fi

production_version_code="$(jq -r '[.releases[]? | select(.status == "completed") | .versionCodes[]? | tonumber] | max // empty' <<< "${production_track}")"
production_max_version_code="$(jq -r '[.releases[]? | .versionCodes[]? | tonumber] | max // empty' <<< "${production_track}")"
if [ -n "${production_max_version_code}" ] && [ "${target_version_code}" -le "${production_max_version_code}" ]; then
	echo "Target versionCode ${target_version_code} must be newer than every production release (${production_max_version_code})" >&2
	exit 1
fi

production_version=''
if [ -n "${production_version_code}" ]; then
	production_version="$(tag_for_version_code "${production_version_code}" || true)"
	if [ -z "${production_version}" ]; then
		echo "Could not map production versionCode ${production_version_code} to a Git tag" >&2
		exit 1
	fi
fi

full_notes="$(bash scripts/release-notes.sh "${production_version}" "${TARGET_VERSION}")"
notes="$(printf '%s' "${full_notes}" | python3 -c 'import sys; sys.stdout.write(sys.stdin.read()[:500])')"
payload="$(jq -cn \
	--arg version_code "${target_version_code}" \
	--arg notes "${notes}" \
	'{releases:[({versionCodes:[$version_code],status:"completed"} + (if $notes == "" then {} else {releaseNotes:[{language:"en-US",text:$notes}]} end))]}')"

play_api PUT "edits/${edit_id}/tracks/production" "${payload}" >/dev/null
play_api POST "edits/${edit_id}:validate" '{}' >/dev/null
play_api POST "edits/${edit_id}:commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW" '{}' >/dev/null
edit_id=''

summary_file="${GITHUB_STEP_SUMMARY:-/dev/stdout}"
{
	echo '## Production promotion'
	echo
	if [ -n "${production_version}" ]; then
		echo "**${production_version} → ${TARGET_VERSION}**"
	else
		echo "**Initial production → ${TARGET_VERSION}**"
	fi
	echo
	echo "Promoted the exact beta artifact with versionCode ${target_version_code}."
	if [ "${#full_notes}" -gt 500 ]; then
		echo
		echo 'Release notes were truncated to 500 characters for Google Play.'
	fi
	echo
	echo '### What’s new'
	echo
	printf '%s\n' "${notes}"
} >> "${summary_file}"
