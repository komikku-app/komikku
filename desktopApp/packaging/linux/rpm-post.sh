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
# The entry handles komikku:// links (MimeType=x-scheme-handler/komikku); browsers find it in the desktop database,
# which xdg-desktop-menu refreshes but a copied entry needs refreshed here.
if command -v update-desktop-database > /dev/null 2>&1; then
    update-desktop-database -q /usr/share/applications || true
fi
exit 0
