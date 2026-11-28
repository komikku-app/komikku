Desktop installers for Komikku {{VERSION}}: Windows (`.msi`, `.exe`), macOS (`.dmg`, Apple silicon and Intel) and
Linux (`.deb`, `.rpm`, `.tar.gz`), x64 unless the name says arm64. Check a download against
`Komikku-{{VERSION}}-desktop-SHA256SUMS.txt`: `sha256sum -c --ignore-missing Komikku-{{VERSION}}-desktop-SHA256SUMS.txt`.

#### Linux

- `.deb` (Debian, Ubuntu, Linux Mint): `sudo apt install ./Komikku-{{VERSION}}-linux-x64.deb`
- `.tar.gz` (any distribution, nothing installed): unpack it and start `Komikku/bin/Komikku`
<!-- rpm:unsigned -->
- `.rpm` (Fedora, openSUSE, RHEL): **the package is not signed**, and Fedora's `rpm` refuses an unsigned package
  ("does not verify: no signature") unless told to skip the signature check:
  `sudo dnf install --nogpgcheck ./Komikku-{{VERSION}}-linux-x64.rpm` (installs the dependencies too) or
  `sudo rpm -i --nosignature ./Komikku-{{VERSION}}-linux-x64.rpm` (to upgrade: `sudo rpm -U --nosignature …`).
  Skipping the check means nothing proves that the package was built by Komikku and not changed since, so compare
  its SHA-256 with the checksum file above before installing it.
<!-- rpm:signed -->
- `.rpm` (Fedora, openSUSE, RHEL): signed. Import the signing key once, then install as usual:
  `sudo rpm --import Komikku-{{VERSION}}-linux-x64-rpm-signing-key.asc`, then
  `sudo dnf install ./Komikku-{{VERSION}}-linux-x64.rpm`. `rpm -K Komikku-{{VERSION}}-linux-x64.rpm` shows
  "digests signatures OK" for an unchanged package.
<!-- rpm:end -->
