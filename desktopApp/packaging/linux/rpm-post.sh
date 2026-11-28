# The RPM's post scriptlet: jpackage's, with a menu entry that never fails the install (see postinst; rpm only warns
# when this fails, but dnf reports an error). Put into the spec by deb-maintainer-scripts.gradle.kts.
desktop_file=/opt/komikku/lib/komikku-Komikku.desktop
if ! xdg-desktop-menu install "$desktop_file"; then
    if mkdir -p /usr/share/applications && cp "$desktop_file" /usr/share/applications/; then
        echo "komikku: menu entry copied to /usr/share/applications" >&2
    else
        echo "komikku: the menu entry could not be installed" >&2
    fi
fi
exit 0
