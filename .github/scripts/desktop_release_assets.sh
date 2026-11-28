#!/usr/bin/env bash
# KMK: collects the desktop installers that `:desktopApp:packageDistributionForCurrentOS` built (and, on Linux, the app
# image directory of `:desktopApp:createDistributable`, packed as the .tar.gz) into release assets named as the desktop
# update check expects (DesktopUpdaterConfig / DesktopArch, P6), and writes their SHA-256 sums.
# Used by .github/workflows/build_desktop.yml; runs the same on a developer machine (docs/kmp-desktop/BUILDING.md,
# "Release pipeline").
#
#   desktop_release_assets.sh <binaries dir> <version> <os> <arch> <out dir>
#
#   <binaries dir>  desktopApp/build/compose/binaries/main
#   <version>       the app's versionName, x.y.z without a suffix (the release tag is v<version>)
#   <os>            windows | macos | linux
#   <arch>          x64 | arm64 (the JVM's / runner's CPU architecture: jpackage builds for the JDK it runs on)
#   <out dir>       where the assets go (created; must not hold assets of another build)
#
# Asset names: Komikku-<version>-<os>-<arch>.<ext>
#   windows: .msi, .exe        macos: .dmg        linux: .deb, .rpm, .tar.gz (the app image directory, packed)
# and Komikku-<version>-<os>-<arch>-SHA256SUMS.txt (`sha256sum -c` format, "<hash>  <name>" lines sorted by name) for
# those files. On Linux with RPM_SIGNING_KEY_FILE set (the public key the workflow's RPM signing hook wrote), that key
# too, as Komikku-<version>-linux-<arch>-rpm-signing-key.asc; unset (no signing), the assets are the unsigned build's.
# Every format the OS builds must be there exactly once, otherwise the script fails.
set -euo pipefail

if [ "$#" -ne 5 ]; then
  echo "usage: $0 <binaries dir> <version> <os> <arch> <out dir>" >&2
  exit 2
fi
binaries=$1
version=$2
os=$3
arch=$4
out=$5

if ! [[ "$version" =~ ^[0-9]+(\.[0-9]+)*$ ]]; then
  echo "version '$version' is not x.y.z (desktop releases do not support a suffix such as -rc1)" >&2
  exit 2
fi
case "$os" in
  windows) formats="msi exe" ;;
  macos) formats="dmg" ;;
  linux) formats="deb rpm app" ;;
  *) echo "unknown os '$os' (windows, macos, linux)" >&2; exit 2 ;;
esac
case "$arch" in
  x64 | arm64) ;;
  *) echo "unknown arch '$arch' (x64, arm64)" >&2; exit 2 ;;
esac

mkdir -p "$out"
out=$(cd "$out" && pwd)
base="Komikku-$version-$os-$arch"
assets=()

# The single entry (file, or directory for the app image) of a format's output directory.
single() {
  local dir=$1 pattern=$2 kind=$3
  local found=()
  if [ -d "$dir" ]; then
    while IFS= read -r -d '' entry; do found+=("$entry"); done \
      < <(find "$dir" -mindepth 1 -maxdepth 1 -type "$kind" -name "$pattern" -print0)
  fi
  if [ "${#found[@]}" -ne 1 ]; then
    echo "expected exactly one '$pattern' in $dir, found ${#found[@]}" >&2
    exit 1
  fi
  printf '%s' "${found[0]}"
}

for format in $formats; do
  case "$format" in
    app)
      # The Linux app image is a directory (createDistributable, jpackage --type app-image): packed as a tarball with that
      # directory at its root, so that `tar -xzf` gives one folder with bin/ and lib/.
      image=$(single "$binaries/app" '*' d)
      name="$base.tar.gz"
      tar -czf "$out/$name" -C "$(dirname "$image")" "$(basename "$image")"
      ;;
    *)
      file=$(single "$binaries/$format" "*.$format" f)
      name="$base.$format"
      cp "$file" "$out/$name"
      ;;
  esac
  assets+=("$name")
done
if [ "$os" = linux ] && [ -n "${RPM_SIGNING_KEY_FILE:-}" ]; then
  name="$base-rpm-signing-key.asc"
  cp "$RPM_SIGNING_KEY_FILE" "$out/$name"
  assets+=("$name")
fi

sums="$base-SHA256SUMS.txt"
(
  cd "$out"
  if command -v sha256sum > /dev/null; then
    raw=$(sha256sum "${assets[@]}")
  else
    # macOS without coreutils
    raw=$(shasum -a 256 "${assets[@]}")
  fi
  # Every line as "<hash>  <name>" (Git Bash's sha256sum on Windows may write "<hash> *<name>" for binary mode, and
  # CRLF), sorted by name; anything else fails.
  printf '%s\n' "$raw" | tr -d '\r' | sed -E 's/^([0-9a-fA-F]{64}) [ *]/\1  /' \
    | awk '{ print substr($0, 67) "\t" tolower(substr($0, 1, 64)) "  " substr($0, 67) }' | LC_ALL=C sort | cut -f 2- \
    > "$sums"
  if [ "$(grep -c -E '^[0-9a-f]{64}  Komikku-[^ ]+$' "$sums")" -ne "${#assets[@]}" ] \
    || [ "$(wc -l < "$sums" | tr -d ' ')" -ne "${#assets[@]}" ]; then
    echo "unexpected checksum output:" >&2
    cat "$sums" >&2
    exit 1
  fi
)

printf '%s\n' "${assets[@]}" "$sums"
