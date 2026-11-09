#!/usr/bin/env bash
set -euo pipefail

base=$1
tag_ref="refs/tags/$2"
next_ref=refs/heads/release-next
main_ref=refs/heads/main

# Check the local history before using a lease to enforce the exact remote base.
release=$(git rev-parse "$tag_ref^{commit}")
next=$(git rev-parse "$next_ref")
[[ $(git rev-list --parents -n 1 "$release") == "$release $base" &&
   $(git rev-list --parents -n 1 "$next") == "$next $release" ]] || {
  echo "Expected the release and snapshot commits directly after $base." >&2
  exit 1
}

git fetch --no-tags origin "$main_ref"
remote_main=$(git rev-parse FETCH_HEAD)
remote_tag=$(git ls-remote --refs origin "$tag_ref" | cut -f 1)
if [[ -n $remote_tag ]]; then
  if [[ $remote_tag == "$(git rev-parse "$tag_ref")" ]] && git merge-base --is-ancestor "$next" "$remote_main"; then
    echo "The signed tag and snapshot commit have already been pushed."
    exit 0
  fi
  echo "Remote tag $tag_ref conflicts with this release." >&2
  exit 1
fi

if [[ $remote_main != "$base" ]]; then
  echo "Main advanced after dispatch. Review the new tip before starting a new release run." >&2
  exit 1
fi

git push --atomic "--force-with-lease=$main_ref:$base" origin "$next_ref:$main_ref" "$tag_ref:$tag_ref"
