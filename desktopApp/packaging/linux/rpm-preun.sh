# The RPM's preun scriptlet: jpackage's, with a menu entry whose removal never fails the removal (rpm stops an erase
# when this fails). Only on an erase ($1 = 0): in an upgrade the old package's preun runs after the new one's post,
# and would take away the entry the new one just installed. Put into the spec by deb-maintainer-scripts.gradle.kts.
if [ "$1" = 0 ]; then
    xdg-desktop-menu uninstall /opt/komikku/lib/komikku-Komikku.desktop || true
    rm -f /usr/share/applications/komikku-Komikku.desktop
fi
exit 0
