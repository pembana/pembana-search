#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "$*" >&2
  exit 1
}

release_version=$1
next_snapshot=$2
output=$3
repository_url=$4
tag="v$release_version"
version_pattern='(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)'

[[ $release_version =~ ^${version_pattern}(-(M|RC)[1-9][0-9]*)?$ ]] ||
  fail "Enter a release version such as 1.0.0-M2, 1.0.0-RC1 or 1.0.0."
[[ $next_snapshot =~ ^${version_pattern}-SNAPSHOT$ ]] ||
  fail "Enter the next development version ending in -SNAPSHOT."

base=$(git rev-parse HEAD)
release_base=${release_version%%-*}
if ! current_version=$(env -u JRELEASER_GPG_PASSPHRASE ./mvnw -B -ntp -q -N help:evaluate \
    -Dexpression=project.version -DforceStdout -Dstyle.color=never); then
  fail "$current_version"
fi
[[ $current_version == "$release_base-SNAPSHOT" ]] ||
  fail "Expected $release_base-SNAPSHOT on main, found $current_version."
next_base=${next_snapshot%-SNAPSHOT}
[[ $(printf '%s\n' "$release_base" "$next_base" | sort -V | head -n 1) == "$release_base" ]] ||
  fail "The next snapshot cannot precede the release version."
[[ $release_version != "$next_base" ]] ||
  fail "After a stable release, choose a later snapshot version."

if git show-ref --verify --quiet "refs/tags/$tag"; then
  fail "Tag $tag already exists."
fi

mkdir -p "$output"
[[ $(grep -cx '## Unreleased' CHANGELOG.md) == 1 ]] ||
  fail "Keep one ## Unreleased section in CHANGELOG.md."
awk '
  /^## / { unreleased = ($0 == "## Unreleased") }
  unreleased && !/^## / { print }
' CHANGELOG.md > "$output/unreleased.md"
if ! grep -Eq '^[^#[:space:]]' "$output/unreleased.md"; then
  fail "Add a summary or a change entry under ## Unreleased in CHANGELOG.md."
fi

# Stage the curated text under the release version and keep prior entries intact.
sed "s/^## Unreleased$/## $release_version/" CHANGELOG.md > "$output/changelog.md"
cp "$output/changelog.md" CHANGELOG.md

set_version() {
  env -u JRELEASER_GPG_PASSPHRASE ./mvnw -B -ntp org.codehaus.mojo:versions-maven-plugin:2.23.0:set \
    "-DnewVersion=$1" -DgenerateBackupPoms=false
}

set_version "$release_version"
git add CHANGELOG.md pom.xml pembana-search-*/pom.xml
git commit -S -m "chore(release): prepare $release_version"
git tag -s "$tag" -m "Pembana Search™ $release_version"
git verify-commit HEAD
git verify-tag "$tag"

# Render the changelog body without headings that have no content.
awk '
  function flush() {
    sub(/^([[:blank:]]*\n)+/, "", body)
    sub(/(\n[[:blank:]]*)+$/, "", body)
    if (body != "") {
      if (heading != "") print heading "\n"
      print body "\n"
    }
    body = ""
  }
  /^### / { flush(); heading = "## " substr($0, 5); next }
  { body = body $0 "\n" }
  END { flush() }
' "$output/unreleased.md" |
  sed "s|](docs/|]($repository_url/blob/$tag/docs/|g" > "$output/release-notes.md"

if previous_tag=$(git describe --first-parent --match 'v[0-9]*' --abbrev=0 "$base" 2>/dev/null); then
  printf '[Full changelog](%s/compare/%s...%s)\n' "$repository_url" "$previous_tag" "$tag" >> "$output/release-notes.md"
else
  printf '[Full changelog](%s/blob/%s/CHANGELOG.md)\n' "$repository_url" "$tag" >> "$output/release-notes.md"
fi

set_version "$next_snapshot"
awk -v release="## $release_version" '
  $0 == release { print "## Unreleased\n" }
  { print }
' CHANGELOG.md > "$output/changelog.md"
cp "$output/changelog.md" CHANGELOG.md
git add CHANGELOG.md pom.xml pembana-search-*/pom.xml
git commit -S -m "chore(release): begin $next_snapshot"
git verify-commit HEAD
git branch release-next HEAD
git bundle create "$output/release.bundle" "refs/tags/$tag" refs/heads/release-next "^$base"
rm "$output/unreleased.md" "$output/changelog.md"
