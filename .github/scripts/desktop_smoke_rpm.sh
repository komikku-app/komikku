#!/usr/bin/env bash
# KMK: install, upgrade and erase of the Linux RPM (its own scriptlets, desktopApp/packaging/linux/rpm-post.sh and
# rpm-preun.sh) in a Fedora container, run by .github/workflows/build_desktop.yml (job `smoke`, ubuntu-24.04, after the
# .deb smoke test) on the `package` job's artifact. Nothing graphical is started. The menu folders are removed before
# the install, as on a system without a desktop: the install must still exit with 0 and leave the menu entry, an upgrade
# (the same package again, then a rebuilt one with a higher release) must keep it, and the erase must exit with 0 and
# take it away. The package is unsigned, which Fedora's rpm refuses: rpm installs it with --nosignature, and what rpm
# and dnf say without it is recorded (INFO, not a failure: whether to sign it is open). Then the release's signing hook
# (desktop_rpm_sign.sh) with a throwaway key generated here: signed, verified and installed without --nosignature in
# the container, and a tampered copy refused by rpm -K (ATLAS_INSTRUCTIONS.md ANSWER 018).
#
#   desktop_smoke_rpm.sh <packages dir> <output dir>
#
#   <packages dir>  the artifact Komikku-desktop-linux-X64-<sha> (Komikku-<v>-linux-x64.rpm)
#   <output dir>    logs/ (with the throwaway public key) and summary.md (created)
#
# Every check is printed as [PASS] / [FAIL] / [INFO] and written to <output dir>/summary.md and the job summary; the
# script exits 1 when a check failed.
set -uo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <packages dir> <output dir>" >&2
  exit 2
fi
here=$(cd "$(dirname "$0")" && pwd)
packages=$(cd "$1" && pwd) || exit 2
mkdir -p "$2/logs" || exit 2
out=$(cd "$2" && pwd)

# Fedora 45, pinned by digest (BUILDING.md "What CI downloads and runs")
image='fedora:45@sha256:a7d72ba2745b100a3351087472e5ee7b2450af7cf331e7673007e3817612839c'
container="komikku-rpm-smoke-$$"
entry_name=komikku-Komikku.desktop
menu_folders=(/usr/share/applications /usr/local/share/applications /usr/share/desktop-directories
  /usr/local/share/desktop-directories)

failures=0
rows=()
started=no

record() {
  local status=$1 name=$2 detail=${3:-}
  rows+=("| $status | $name | ${detail//|/\\|} |")
  echo "[$status] $name${detail:+: $detail}"
  if [ "$status" = FAIL ]; then
    failures=$((failures + 1))
    echo "::error title=Desktop RPM test::$name${detail:+: $detail}"
  fi
}
pass() { record PASS "$@"; }
fail() { record FAIL "$@"; }
info() { record INFO "$@"; }
# A failure after which nothing else can be checked
fatal() {
  fail "$@"
  exit 1
}

finish() {
  local status=$?
  if [ "$started" = yes ]; then docker rm -f "$container" > /dev/null 2>&1; fi
  {
    echo "## Desktop RPM install, upgrade and erase (Fedora container)"
    echo
    echo "| Result | Check | Detail |"
    echo "|--------|-------|--------|"
    printf '%s\n' "${rows[@]}"
    echo
    echo "Logs: rpm/logs/ in the artifact desktop-smoke-linux-… of this run."
  } > "$out/summary.md"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then cat "$out/summary.md" >> "$GITHUB_STEP_SUMMARY"; fi
  if [ "$failures" -gt 0 ]; then
    echo "$failures check(s) failed"
    exit 1
  fi
  exit "$status"
}
trap finish EXIT

# Runs a command in the container (no shell in between, so arguments keep their parentheses and spaces)
box() { docker exec "$container" "$@"; }

# The installed menu entries (xdg-desktop-menu writes to the first menu folder of XDG_DATA_DIRS it can use; without one,
# the post scriptlet copies the entry to /usr/share/applications)
menu_entries() {
  box sh -c "for dir in /usr/share/applications /usr/local/share/applications; do
    [ -e \"\$dir/$entry_name\" ] && echo \"\$dir/$entry_name\"; done; true" 2> /dev/null | tr '\n' ' ' | sed 's/ $//'
}

# rpm_step <log name> <check name> <rpm arguments...>: passes when rpm exits with 0 and no scriptlet failed
rpm_step() {
  local log=$1 name=$2 code
  shift 2
  box rpm "$@" > "$out/logs/$log" 2>&1
  code=$?
  local scriptlet
  scriptlet=$(grep -m 1 -i 'scriptlet failed' "$out/logs/$log")
  if [ "$code" = 0 ] && [ -z "$scriptlet" ]; then
    pass "$name" "exit code 0 (logs/$log)"
  else
    fail "$name" "exit code $code${scriptlet:+, $scriptlet} (logs/$log)"
  fi
  local said
  said=$(grep -m 1 '^komikku: ' "$out/logs/$log")
  if [ -n "$said" ]; then info "$name: scriptlet message" "$said"; fi
  return "$code"
}

shopt -s nullglob
rpms=("$packages"/*.rpm)
shopt -u nullglob
[ "${#rpms[@]}" -eq 1 ] || fatal "One .rpm in the packages" "found ${#rpms[@]} in $packages"
rpm_file=$(basename "${rpms[0]}")
pass "One .rpm in the packages" "$rpm_file"

if ! docker run -d --name "$container" -v "$packages:/packages:ro" "$image" sleep infinity \
  > "$out/logs/docker-run.log" 2>&1; then
  fatal "Fedora container" "docker run failed (logs/docker-run.log)"
fi
started=yes
pass "Fedora container" "$(box sh -c '. /etc/os-release && echo "$PRETTY_NAME"'), $image"

package=$(box rpm -qp --qf '%{NAME} %{VERSION}-%{RELEASE}' "/packages/$rpm_file" 2> "$out/logs/rpm-query.log")
[ -n "$package" ] || fatal "Package header" "rpm -qp failed (logs/rpm-query.log)"
name=${package%% *}
info "Package" "$package"

# The package's dependencies first, through dnf (rpm -i does not resolve them); they may bring menu folders, which are
# removed afterwards
mapfile -t requires < <(box rpm -qp --requires "/packages/$rpm_file" 2> /dev/null | grep -v '^rpmlib(' |
  sed 's/ *[<>=].*//' | sort -u)
if box dnf -y install --setopt=install_weak_deps=False "${requires[@]}" > "$out/logs/dnf-requires.log" 2>&1; then
  pass "Dependencies installed (dnf)" "${#requires[@]} requirement(s) of the package (logs/dnf-requires.log)"
else
  fatal "Dependencies installed (dnf)" "dnf failed (logs/dnf-requires.log)"
fi
existing=$(box sh -c 'ls -d "$@" 2> /dev/null; true' sh "${menu_folders[@]}" | tr '\n' ' ')
info "Menu folders before the install" "${existing:-none}; all removed"
box rm -rf "${menu_folders[@]}"

# The unsigned package as built: install without menu folders, two upgrades, erase. A failed install skips the rest
# of it, not the sections after it
unsigned_path() {
  # --- Install --------------------------------------------------------------------------------------------------------

  # Without --nosignature (a test transaction, nothing is installed)
  box rpm -i --test "/packages/$rpm_file" > "$out/logs/rpm-install-signature.log" 2>&1
  code=$?
  refusal=$(grep -m 1 -i -E 'signature|verify|NOKEY' "$out/logs/rpm-install-signature.log" | sed 's/^[[:space:]]*//')
  info "Package signature" "package is unsigned; Fedora's rpm refuses it without --nosignature (rpm -i --test: exit code $code${refusal:+, $refusal}; logs/rpm-install-signature.log)"

  # Nothing else can be checked without it
  rpm_step rpm-install.log "Install (rpm -i --nosignature)" -ivh --nosignature "/packages/$rpm_file" || return 0
  if box test -x /opt/komikku/bin/Komikku; then
    pass "Launcher installed" "/opt/komikku/bin/Komikku"
  else
    fail "Launcher installed" "no executable /opt/komikku/bin/Komikku"
  fi
  entries=$(menu_entries)
  if [ -n "$entries" ]; then
    pass "Menu entry installed without menu folders" "$entries"
  else
    fail "Menu entry installed without menu folders" "no $entry_name in /usr/share/applications or /usr/local/share/applications"
  fi
  # komikku:// links (tracker logins redirect there): the packaged entry declares the scheme and passes the link on
  if box grep -qx 'MimeType=x-scheme-handler/komikku;' "/opt/komikku/lib/$entry_name" \
    && box grep -Eq '^Exec=.* %u$' "/opt/komikku/lib/$entry_name"; then
    pass "Menu entry opens komikku:// links" "MimeType=x-scheme-handler/komikku; and %u in Exec"
  else
    fail "Menu entry opens komikku:// links" "no MimeType=x-scheme-handler/komikku; or no %u in Exec of /opt/komikku/lib/$entry_name"
  fi

  # --- Upgrade --------------------------------------------------------------------------------------------------------

  # The same package again: the new post runs ($1 = 2), then the old preun ($1 = 1), which must leave the entry
  rpm_step rpm-reinstall.log "Upgrade to the same package (rpm -U --replacepkgs --nosignature)" -Uvh --replacepkgs \
    --nosignature "/packages/$rpm_file"
  entries=$(menu_entries)
  if [ -n "$entries" ]; then
    pass "Menu entry kept by the upgrade to the same package" "$entries"
  else
    fail "Menu entry kept by the upgrade to the same package" "gone after rpm -U --replacepkgs"
  fi

  # A real version change: the package rebuilt with a higher release (rpmrebuild keeps its files and scriptlets)
  if box dnf -y install rpmrebuild > "$out/logs/dnf-rpmrebuild.log" 2>&1 &&
    box rpmrebuild --batch --package --notest-install --release=999 --directory=/tmp/rebuilt "/packages/$rpm_file" \
      > "$out/logs/rpmrebuild.log" 2>&1; then
    rebuilt=$(box sh -c 'find /tmp/rebuilt -name "*.rpm" | head -n 1')
  fi
  if [ -n "${rebuilt:-}" ]; then
    info "Rebuilt package" "$(box rpm -qp --qf '%{NAME} %{VERSION}-%{RELEASE}' "$rebuilt" 2> /dev/null)"
    rpm_step rpm-upgrade.log "Upgrade to a higher release (rpm -U --nosignature)" -Uvh --nosignature "$rebuilt"
    info "Installed after the upgrade" "$(box rpm -q "$name" 2>&1)"
    entries=$(menu_entries)
    if [ -n "$entries" ]; then
      pass "Menu entry kept by the upgrade to a higher release" "$entries"
    else
      fail "Menu entry kept by the upgrade to a higher release" "gone after rpm -U"
    fi
  else
    info "Upgrade to a higher release" "not run: the package could not be rebuilt (logs/dnf-rpmrebuild.log, rpmrebuild.log)"
  fi

  # --- Erase ----------------------------------------------------------------------------------------------------------

  rpm_step rpm-erase.log "Erase (rpm -e)" -ev "$name"
  if box rpm -q "$name" > /dev/null 2>&1; then
    fail "Package gone after the erase" "rpm -q $name still finds it"
  else
    pass "Package gone after the erase"
  fi
  if box test -e /opt/komikku/bin/Komikku; then
    fail "Launcher removed" "/opt/komikku/bin/Komikku is still there"
  else
    pass "Launcher removed"
  fi
  entries=$(menu_entries)
  if [ -z "$entries" ]; then
    pass "Menu entry removed" "no $entry_name left"
  else
    fail "Menu entry removed" "still there: $entries"
  fi
}
unsigned_path

# --- dnf ------------------------------------------------------------------------------------------------------------

# What a user's `dnf install ./<rpm>` says about the unsigned package (recorded, not checked); removed again if it
# installed it
box dnf -y install "/packages/$rpm_file" > "$out/logs/dnf-install.log" 2>&1
code=$?
said=$(grep -i -E 'signature|gpg|verify|not signed|error|problem' "$out/logs/dnf-install.log" | head -n 2 |
  sed 's/^[[:space:]]*//' | paste -s -d ';' -)
if [ "$code" = 0 ]; then
  info "dnf install ./$rpm_file" "exit code 0: dnf installed the unsigned package${said:+ ($said)} (logs/dnf-install.log)"
  box rpm -e "$name" > "$out/logs/dnf-install-erase.log" 2>&1
else
  info "dnf install ./$rpm_file" "exit code $code: ${said:-$(tail -n 1 "$out/logs/dnf-install.log")} (logs/dnf-install.log)"
fi

# --- Signed with a throwaway key ------------------------------------------------------------------------------------

# The release's signing hook (desktop_rpm_sign.sh, run by `package` when the secret RPM_SIGNING_KEY is set) proven
# with a key generated here and thrown away (never a real one): the package signed on this runner as the release would
# sign it, then in the container rpm -K with the public key imported, an install without --nosignature, and a
# tampered copy that rpm -K must refuse
signed_path() {
  # Outside the output folder, which is uploaded: the signed and tampered copies are as large as the package
  local work="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/komikku-rpm-signed" home passphrase key code result
  rm -rf "$work"
  mkdir -p "$work"
  if ! command -v rpmsign > /dev/null &&
    ! sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -q rpm > "$out/logs/apt-rpm.log" 2>&1; then
    fail "Signed with a throwaway key" "rpmsign could not be installed (logs/apt-rpm.log)"
    return 0
  fi
  home=$(mktemp -d)
  chmod 700 "$home"
  passphrase=$(openssl rand -hex 16)
  # A passphrase like a real key's, so the signing script's loopback path is the one tested
  if ! GNUPGHOME=$home gpg --batch --quiet --pinentry-mode loopback --passphrase "$passphrase" \
    --quick-gen-key "Komikku CI throwaway RPM key <ci@invalid>" rsa3072 sign 1d > "$out/logs/gpg-generate.log" 2>&1; then
    fail "Signed with a throwaway key" "gpg could not generate the key (logs/gpg-generate.log)"
    rm -rf "$home"
    return 0
  fi
  key=$(GNUPGHOME=$home gpg --batch --pinentry-mode loopback --passphrase "$passphrase" --armor --export-secret-keys)
  GNUPGHOME=$home gpgconf --kill all > /dev/null 2>&1
  rm -rf "$home"

  cp "$packages/$rpm_file" "$work/$rpm_file"
  RPM_SIGNING_KEY=$key RPM_SIGNING_KEY_PASSPHRASE=$passphrase \
    "$here/desktop_rpm_sign.sh" "$work/public-key.asc" "$work/$rpm_file" > "$out/logs/rpm-sign.log" 2>&1
  code=$?
  unset key passphrase
  result=$(grep -m 1 'signatures OK' "$out/logs/rpm-sign.log")
  if [ "$code" = 0 ] && [ -n "$result" ]; then
    pass "Signed with a throwaway key (desktop_rpm_sign.sh)" "${result##*: } on the runner (rpm $(rpm --version | awk '{ print $3 }'); logs/rpm-sign.log)"
  else
    fail "Signed with a throwaway key (desktop_rpm_sign.sh)" "exit code $code (logs/rpm-sign.log)"
    return 0
  fi
  cp "$work/public-key.asc" "$out/logs/throwaway-public-key.asc"
  python3 - "$work/$rpm_file" "$work/tampered.rpm" << 'PY'
import sys
data = bytearray(open(sys.argv[1], "rb").read())
data[len(data) - 20] ^= 0xFF
open(sys.argv[2], "wb").write(bytes(data))
PY

  docker cp "$work/." "$container:/signed" > /dev/null
  if box rpm --import /signed/public-key.asc > "$out/logs/rpm-import.log" 2>&1; then
    pass "Throwaway public key imported (rpm --import)"
  else
    fail "Throwaway public key imported (rpm --import)" "logs/rpm-import.log"
  fi
  result=$(box rpm -K "/signed/$rpm_file" 2>&1)
  code=$?
  if [ "$code" = 0 ] && [[ $result == *"signatures OK"* ]]; then
    pass "Signed package verifies in Fedora (rpm -K)" "${result##*: }"
  else
    fail "Signed package verifies in Fedora (rpm -K)" "exit code $code: $result"
  fi
  result=$(box rpm -K /signed/tampered.rpm 2>&1)
  code=$?
  if [ "$code" != 0 ] && [[ $result == *"NOT OK"* ]]; then
    pass "Tampered copy fails rpm -K" "exit code $code: ${result##*: }"
  else
    fail "Tampered copy fails rpm -K" "exit code $code: $result"
  fi

  rpm_step rpm-install-signed.log "Install the signed package (rpm -i, without --nosignature)" -ivh "/signed/$rpm_file" ||
    return 0
  if [ -n "$(menu_entries)" ]; then
    pass "Menu entry installed by the signed package" "$(menu_entries)"
  else
    fail "Menu entry installed by the signed package" "no $entry_name"
  fi
  rpm_step rpm-erase-signed.log "Erase the signed package (rpm -e)" -ev "$name"
}
signed_path
