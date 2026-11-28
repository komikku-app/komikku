#!/usr/bin/env bash
# KMK: the pinned real extensions of the desktop CI (docs/kmp-desktop/ATLAS_INSTRUCTIONS.md, ANSWER 010), listed in
# .github/desktop-test-extensions.txt (format: the comment at its top).
#
#   desktop_test_extensions.sh fetch <list> <dir>
#       Downloads each listed APK into <dir>: the URL comes from keiyoushi/extensions' index.pb at the listed commit,
#       the file must have the listed SHA-256 (else it is deleted and the script fails before anything loads it).
#   desktop_test_extensions.sh pin <list> [<commit>]
#       Prints the list's lines for the repo branch at <commit> (default: its head), with the APK names and SHA-256 of
#       that index, for a reviewed commit that bumps the list. Runs nothing it downloads.
set -euo pipefail

repo=keiyoushi/extensions
prefix=eu.kanade.tachiyomi.extension.

fail() {
  echo "::error::$*" >&2
  exit 1
}

sha256() {
  if command -v sha256sum > /dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d ' ' -f 1
}

# The list's entries, without comments and blank lines
entries() {
  sed -e 's/#.*//' -e '/^[[:space:]]*$/d' "$1"
}

# index <commit>: the repo branch's index.pb at <commit>, decompressed, as a file in $work (fetched once)
index() {
  local file="$work/index-$1.bin"
  if [ ! -f "$file" ]; then
    curl -fsSL --retry 3 -o "$work/index.pb" "https://raw.githubusercontent.com/$repo/$1/index.pb" \
      || fail "No index.pb at $repo commit $1"
    gzip -dc "$work/index.pb" > "$file"
  fi
  echo "$file"
}

# url <index file> <regular expression of the APK file name>: the first download URL in the index that matches
url() {
  grep -a -o -E "https://github\.com/$repo/releases/download/[^/\"]+/$2" "$1" | head -n 1 || true
}

check_package() {
  [[ $1 =~ ^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$ ]] && [[ $1 == "$prefix"* ]] || fail "Not an extension package name: '$1'"
}

fetch() {
  local list=$1 dir=$2 package commit hash apk rest link actual count=0
  mkdir -p "$dir"
  while read -r package commit hash apk rest; do
    check_package "$package"
    [[ $commit =~ ^[0-9a-f]{40}$ ]] || fail "$package: '$commit' is not a full commit hash"
    [[ $hash =~ ^[0-9a-f]{64}$ ]] || fail "$package: '$hash' is not a SHA-256"
    [[ $apk =~ ^tachiyomi-[a-z0-9_.]+-v[0-9.]+\.apk$ ]] || fail "$package: '$apk' is not an APK file name"
    [ -z "$rest" ] || fail "$package: unexpected fields '$rest'"
    link=$(url "$(index "$commit")" "${apk//./\\.}")
    [ -n "$link" ] || fail "$package: $apk is not in the index of $repo commit $commit"
    echo "$package: $link"
    curl -fsSL --retry 3 -o "$work/download.apk" "$link" || fail "$package: download failed"
    actual=$(sha256 "$work/download.apk")
    if [ "$actual" != "$hash" ]; then
      rm -f "$work/download.apk"
      fail "$package: $apk has SHA-256 $actual, the list pins $hash; not loaded"
    fi
    mv "$work/download.apk" "$dir/$apk"
    count=$((count + 1))
  done < <(entries "$list")
  [ "$count" -gt 0 ] || fail "No extension in $list"
  echo "$count extension(s) downloaded and verified into $dir"
}

pin() {
  local list=$1 commit=${2:-} package short link apk index_file
  if [ -z "$commit" ]; then
    commit=$(git ls-remote "https://github.com/$repo.git" refs/heads/repo | cut -f 1)
  fi
  [[ $commit =~ ^[0-9a-f]{40}$ ]] || fail "'$commit' is not a full commit hash"
  index_file=$(index "$commit")
  while read -r package _; do
    check_package "$package"
    short=${package#"$prefix"}
    link=$(url "$index_file" "tachiyomi-${short//./\\.}-v[0-9.]+\.apk")
    [ -n "$link" ] || fail "$package is not in the index of $repo commit $commit"
    apk=${link##*/}
    curl -fsSL --retry 3 -o "$work/download.apk" "$link" || fail "$package: download failed"
    echo "$package $commit $(sha256 "$work/download.apk") $apk"
    rm -f "$work/download.apk"
  done < <(entries "$list")
}

if [ "$#" -lt 2 ]; then
  echo "usage: $0 fetch <list> <dir> | pin <list> [<commit>]" >&2
  exit 2
fi
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
case "$1" in
  fetch)
    [ "$#" -eq 3 ] || fail "usage: $0 fetch <list> <dir>"
    fetch "$2" "$3"
    ;;
  pin)
    [ "$#" -le 3 ] || fail "usage: $0 pin <list> [<commit>]"
    pin "$2" "${3:-}"
    ;;
  *)
    echo "usage: $0 fetch <list> <dir> | pin <list> [<commit>]" >&2
    exit 2
    ;;
esac
