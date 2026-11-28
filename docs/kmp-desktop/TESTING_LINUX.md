# Komikku Desktop — testing on Linux

How to get the Linux build of the desktop app (`:desktopApp`, x64 only), install it and remove it again. What to test
and why is in [`WINDOWS_LINUX_TEST_PLAN.md`](WINDOWS_LINUX_TEST_PLAN.md); what CI already checks on every run is the
`smoke` job of `.github/workflows/build_desktop.yml` ([`CI_TEST_RELEASE.md`](CI_TEST_RELEASE.md)).

## Getting the build

- **A release:** the files `Komikku-<v>-linux-x64.deb`, `.rpm` and `.tar.gz` on the GitHub release `v<v>`, with
  `Komikku-<v>-desktop-SHA256SUMS.txt`.
- **A CI run:** Actions → "Desktop builder" → the run → **Artifacts** → `Komikku-desktop-linux-X64-<commit>` (signed in
  to GitHub). The `.zip` holds the same three files and `Komikku-<v>-linux-x64-SHA256SUMS.txt`.

Check the download first, in its folder: `sha256sum -c --ignore-missing Komikku-*-SHA256SUMS.txt`.

## Installing

| Format | For | Install | Remove |
|--------|-----|---------|--------|
| `.deb` | Debian, Ubuntu, Linux Mint | `sudo apt install ./Komikku-<v>-linux-x64.deb` | `sudo apt remove komikku` |
| `.rpm` | Fedora, openSUSE, RHEL | see below | `sudo dnf remove komikku` or `sudo rpm -e komikku` |
| `.tar.gz` | any distribution, nothing installed | `tar -xzf Komikku-<v>-linux-x64.tar.gz`, then start `Komikku/bin/Komikku` | delete the folder |

The packages install to `/opt/komikku` and add the menu entry `komikku-Komikku.desktop`.

**The `.rpm` is not signed.** Fedora's `rpm` refuses it ("package komikku-<v>-1.x86_64 does not verify: no signature")
unless the signature check is skipped:

```sh
sudo dnf install --nogpgcheck ./Komikku-<v>-linux-x64.rpm   # also installs the dependencies
sudo rpm -i --nosignature ./Komikku-<v>-linux-x64.rpm       # dependencies must already be there
sudo rpm -U --nosignature ./Komikku-<v>-linux-x64.rpm       # upgrade an installed one
```

Skipping the check means nothing proves that the package was built by Komikku and not changed since, so check its
SHA-256 (above) before installing it. Once the maintainer's signing key is set (BUILDING.md, "Signing"), the release
also has `Komikku-<v>-linux-x64-rpm-signing-key.asc`: `sudo rpm --import` it once, and the commands above work without
`--nogpgcheck` / `--nosignature`; `rpm -K Komikku-<v>-linux-x64.rpm` then says "digests signatures OK".

The `.deb` needs no flag: `apt` does not check signatures of a local `.deb` file.

## Data and logs

Data: `$XDG_DATA_HOME/komikku`, else `~/.local/share/komikku` (removing the package keeps it; delete it by hand for a
fresh start). Log: `<data folder>/logs/komikku.log`, also on stderr when started from a terminal
(`/opt/komikku/bin/Komikku`). Settings → Advanced → "Open the log folder" opens it. Details: BUILDING.md, "Data
directory" and "Logs".

## What to send back on a failure

The distribution and version (`cat /etc/os-release`), the desktop (GNOME, KDE, …, X11 or Wayland), which file and
command you installed with, the exact message, and `komikku.log`.
