#!/usr/bin/env bash
# KMK: attaches the checked desktop release assets (build_desktop.yml, job `release`) to the GitHub release
# $RELEASE_TAG of $GH_REPO with `gh` ($GH_TOKEN). Never publishes, edits or deletes a release.
#
#   desktop_release_upload.sh <assets dir>
#
#   RELEASE_MODE  existing (the release must exist) | draft (created as a draft for the existing tag when the tag has
#                 no release at all)
#   RELEASE_TAG   v<version>; the assets are Komikku-<version>-… and Komikku-<version>-desktop-SHA256SUMS.txt
#
# - The release is found in the list of releases (drafts included), so that a failing request is an error and never
#   reads as "no release": no second draft is ever created for a tag that has a release.
# - A draft gets every file (`--clobber`: same-named files replaced).
# - A published release only gets the files it is missing. A file it already has is compared by SHA-256 (the asset's
#   `digest`, or the downloaded file when there is none): identical = skipped (resuming a partly failed upload, e.g.
#   "Re-run failed jobs", which reuses the run's artifacts), different = the script fails before uploading anything.
# - The checksum file goes last, so a release that has it has every installer.
# - A draft it creates gets the notes of desktop_release_notes.md ({{VERSION}} filled in; the RPM paragraph for a
#   signed RPM when the assets have its signing key, else the one for the unsigned RPM). The notes of a release that
#   exists are never touched: paste that text into them by hand.
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <assets dir>" >&2
  exit 2
fi
dir=$1
: "${RELEASE_MODE:?}" "${RELEASE_TAG:?}" "${GH_REPO:?}"

fail() {
  echo "::error::$*"
  exit 1
}

case "$RELEASE_MODE" in
  existing | draft) ;;
  *) fail "Unknown release mode '$RELEASE_MODE' (existing, draft)" ;;
esac
version=${RELEASE_TAG#v}
sums_name="Komikku-$version-desktop-SHA256SUMS.txt"
[ -f "$dir/$sums_name" ] || fail "No $sums_name in $dir"
installers=()
for file in "$dir"/*; do
  name=$(basename "$file")
  case "$name" in
    "$sums_name") ;;
    "Komikku-$version-"*) installers+=("$file") ;;
    *) fail "Unexpected file $name (not Komikku-$version-…)" ;;
  esac
done
[ "${#installers[@]}" -gt 0 ] || fail "No installers in $dir"

sha256() {
  sha256sum "$1" | cut -d ' ' -f 1
}

# id <TAB> tag <TAB> draft, one line per release with this tag
if ! releases=$(gh api --paginate "repos/$GH_REPO/releases?per_page=100" --jq '.[] | [.id, .tag_name, .draft] | @tsv'); then
  fail "Could not list the releases of $GH_REPO"
fi
matches=$(printf '%s\n' "$releases" | awk -F '\t' -v tag="$RELEASE_TAG" '$2 == tag')
count=$(printf '%s' "$matches" | grep -c '^' || true)

if [ "$count" -gt 1 ]; then
  fail "$count releases have the tag $RELEASE_TAG (drafts included); leave one and run again"
elif [ "$count" -eq 0 ]; then
  if [ "$RELEASE_MODE" != draft ]; then
    fail "No release $RELEASE_TAG; create it first or run with release_mode draft"
  fi
  rpm_notes=unsigned
  if compgen -G "$dir/*-rpm-signing-key.asc" > /dev/null; then rpm_notes=signed; fi
  notes=$(mktemp)
  awk -v keep="$rpm_notes" '/^<!-- rpm:[a-z]+ -->$/ { block = substr($2, 5); next } block == "" || block == "end" || block == keep' \
    "$(dirname "$0")/desktop_release_notes.md" | sed "s/{{VERSION}}/$version/g" > "$notes"
  gh release create "$RELEASE_TAG" --draft --verify-tag --title "Komikku $RELEASE_TAG" --notes-file "$notes"
  rm -f "$notes"
  draft=true
else
  release_id=$(printf '%s\n' "$matches" | cut -f 1)
  draft=$(printf '%s\n' "$matches" | cut -f 3)
  if [ "$RELEASE_MODE" = draft ] && [ "$draft" != true ]; then
    fail "Release $RELEASE_TAG is published; run with release_mode existing to add the desktop files to it"
  fi
fi

if [ "$draft" = true ]; then
  gh release upload "$RELEASE_TAG" "${installers[@]}" --clobber
  gh release upload "$RELEASE_TAG" "$dir/$sums_name" --clobber
  echo "Uploaded to the draft $RELEASE_TAG:"
  ls "$dir"
  exit 0
fi

# Published: name <TAB> asset id <TAB> digest ("sha256:<hex>" or empty)
if ! assets=$(gh api --paginate "repos/$GH_REPO/releases/$release_id/assets?per_page=100" \
  --jq '.[] | [.name, .id, (.digest // "")] | @tsv'); then
  fail "Could not list the assets of release $RELEASE_TAG"
fi
missing=()
sums_missing=false
conflicts=()
download=$(mktemp)
trap 'rm -f "$download"' EXIT
for file in "${installers[@]}" "$dir/$sums_name"; do
  name=$(basename "$file")
  existing=$(printf '%s\n' "$assets" | awk -F '\t' -v name="$name" '$1 == name')
  if [ -z "$existing" ]; then
    if [ "$name" = "$sums_name" ]; then sums_missing=true; else missing+=("$file"); fi
    continue
  fi
  asset_id=$(printf '%s\n' "$existing" | head -n 1 | cut -f 2)
  digest=$(printf '%s\n' "$existing" | head -n 1 | cut -f 3)
  case "$digest" in
    sha256:*) remote=${digest#sha256:} ;;
    *)
      gh api -H 'Accept: application/octet-stream' "repos/$GH_REPO/releases/assets/$asset_id" > "$download" \
        || fail "Could not download the existing asset $name"
      remote=$(sha256 "$download")
      ;;
  esac
  local_hash=$(sha256 "$file")
  if [ "$(printf '%s' "$remote" | tr 'A-F' 'a-f')" = "$local_hash" ]; then
    echo "$name: already on the release, identical; skipped"
  else
    conflicts+=("$name")
  fi
done
if [ "${#conflicts[@]}" -gt 0 ]; then
  fail "Release $RELEASE_TAG is published and already has different files named ${conflicts[*]}; nothing uploaded (remove them by hand to replace them)"
fi
if [ "${#missing[@]}" -gt 0 ]; then
  # No --clobber: a name that appeared meanwhile fails instead of being replaced
  gh release upload "$RELEASE_TAG" "${missing[@]}"
fi
if [ "$sums_missing" = true ]; then
  gh release upload "$RELEASE_TAG" "$dir/$sums_name"
fi
echo "Release $RELEASE_TAG (published): ${#missing[@]} installer(s) uploaded, checksum file $([ "$sums_missing" = true ] && echo uploaded || echo already there)"
