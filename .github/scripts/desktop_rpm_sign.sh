#!/usr/bin/env bash
# KMK: signs RPMs with `rpmsign --addsign` and checks the signatures with `rpm -K` (ATLAS_INSTRUCTIONS.md ANSWER 018).
# Run by .github/workflows/build_desktop.yml: in `package` (Linux) only when the secret RPM_SIGNING_KEY is set, after
# every step that runs project code, and in `smoke` with a throwaway key generated in the job (the CI proof of this
# script). Without the secret nothing calls it and the RPM stays unsigned.
#
#   desktop_rpm_sign.sh <public key out> <rpm>...
#
#   RPM_SIGNING_KEY             the ASCII-armoured OpenPGP secret key (one primary key; a signing subkey is used when
#                               it has one)
#   RPM_SIGNING_KEY_PASSPHRASE  its passphrase (empty or unset: the key has none)
#   <public key out>            the key's ASCII-armoured public key is written here (attached to the release)
#
# The key lives only in a keyring created for this run ($RUNNER_TEMP/komikku-rpm-gnupg, owner-only), deleted when the
# script ends, also on failure; the workflow deletes that folder again in an `always()` step. Each RPM is signed in
# place, then checked with `rpm -K` against a temporary rpm database holding only that public key: "digests signatures
# OK" or the script fails.
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "usage: $0 <public key out> <rpm>..." >&2
  exit 2
fi
public=$1
shift
: "${RPM_SIGNING_KEY:?RPM_SIGNING_KEY is not set}"
for tool in gpg gpgconf rpmsign rpmkeys rpmdb; do
  command -v "$tool" > /dev/null || { echo "$tool is not installed" >&2; exit 1; }
done

work=${RUNNER_TEMP:-${TMPDIR:-/tmp}}
export GNUPGHOME="$work/komikku-rpm-gnupg"
rpmdb="$work/komikku-rpm-keydb"
cleanup() {
  gpgconf --homedir "$GNUPGHOME" --kill all > /dev/null 2>&1 || true
  rm -rf "$GNUPGHOME" "$rpmdb"
}
trap cleanup EXIT
cleanup
mkdir -m 700 "$GNUPGHOME"
mkdir "$rpmdb"

printf '%s\n' "$RPM_SIGNING_KEY" | gpg --batch --quiet --import
mapfile -t primaries < <(gpg --batch --with-colons --list-secret-keys |
  awk -F: '$1 == "sec" { primary = 1; next } $1 == "fpr" && primary { print $10; primary = 0 }')
if [ "${#primaries[@]}" -ne 1 ]; then
  echo "RPM_SIGNING_KEY holds ${#primaries[@]} secret primary keys, expected 1" >&2
  exit 1
fi
fingerprint=${primaries[0]}
echo "Signing key $fingerprint ($(gpg --batch --with-colons --list-secret-keys | awk -F: '$1 == "uid" { print $10; exit }'))"
gpg --batch --armor --export "$fingerprint" > "$public"

passphrase="$GNUPGHOME/passphrase"
printf '%s' "${RPM_SIGNING_KEY_PASSPHRASE:-}" > "$passphrase"
rpmsign --addsign \
  --define "__gpg $(command -v gpg)" \
  --define "_gpg_path $GNUPGHOME" \
  --define "_gpg_name $fingerprint" \
  --define "_gpg_digest_algo sha256" \
  --define "_gpg_sign_cmd_extra_args --batch --pinentry-mode loopback --passphrase-file $passphrase" \
  "$@"

# Checked against this key only: a temporary rpm database (rpm 4.18 keeps keys there; rpm 6 is told to)
rpmdb --dbpath "$rpmdb" --define "_keyring rpmdb" --initdb
rpmkeys --dbpath "$rpmdb" --define "_keyring rpmdb" --import "$public"
for package in "$@"; do
  result=$(rpmkeys --dbpath "$rpmdb" --define "_keyring rpmdb" --checksig "$package" 2>&1) || {
    echo "rpm -K failed for $package: $result" >&2
    exit 1
  }
  echo "$result"
  case "$result" in
    *"signatures OK"*) ;;
    *) echo "rpm -K does not report a good signature for $package" >&2; exit 1 ;;
  esac
done
