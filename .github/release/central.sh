#!/usr/bin/env bash
set -euo pipefail

deployment_id=$1
[[ $deployment_id =~ ^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$ ]] || {
  echo "Invalid Central deployment ID." >&2
  exit 1
}

authorization=$(printf '%s:%s' "${CENTRAL_USERNAME:?Missing Central username}" "${CENTRAL_PASSWORD:?Missing Central password}" | base64 | tr -d '\n')
if [[ ${GITHUB_ACTIONS:-} == true ]]; then
  printf '::add-mask::%s\n' "$authorization" >&2
fi

# JReleaser can publish a validated deployment. If a previous request was
# accepted, let Central finish before deciding whether a retry is needed.
for (( attempt=1; attempt<=80; attempt++ )); do
  response=$(curl --silent --show-error --fail --max-time 30 --retry 3 \
    --header "Authorization: Bearer $authorization" --request POST \
    "https://central.sonatype.com/api/v1/publisher/status?id=$deployment_id")
  state=$(jq -er '.deploymentState' <<< "$response")
  echo "Central deployment $deployment_id is $state." >&2
  if [[ $state == FAILED ]]; then
    jq '.errors' <<< "$response" >&2
    exit 1
  fi
  if [[ $state != PUBLISHING || ${2:-} != --wait ]]; then
    printf '%s\n' "$state"
    exit 0
  fi
  sleep 15
done

echo "Timed out waiting for Central deployment $deployment_id. Re-run this job to resume." >&2
exit 1
