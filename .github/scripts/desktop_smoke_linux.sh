#!/usr/bin/env bash
# KMK: first smoke test of the packaged Linux app (docs/kmp-desktop/WINDOWS_LINUX_TEST_PLAN.md, section 4), run by
# .github/workflows/build_desktop.yml (job `smoke`, ubuntu-24.04) on the `package` job's artifact. Installs the .deb
# with apt, starts the installed launcher on a fresh data folder (-Dkomikku.dataDir under $RUNNER_TEMP, never the
# default one) on a virtual display (Xvfb + openbox), waits for the window, takes screenshots, switches the top-level
# tabs with Ctrl+2…5 and Ctrl+1, minimizes the window and starts the launcher a second time on the same data folder (the
# single-instance lock must show the running window and end that start with exit code 0), closes the window, checks
# that the process ends with exit code 0 and that the log has no exception, runs the launcher's notification self-test
# (`--self-test-notify`, section 5) on session buses of its own, then uninstalls. The app is driven only from outside
# (window system and keyboard shortcuts).
#
#   desktop_smoke_linux.sh <packages dir> <output dir>
#
#   <packages dir>  the artifact Komikku-desktop-linux-X64-<sha> (Komikku-<v>-linux-x64.deb and its SHA256SUMS)
#   <output dir>    screenshots/, logs/, summary.md and the other files the workflow uploads (created)
#
# Every check is printed as [PASS] / [FAIL] / [INFO] and written to <output dir>/summary.md and the job summary; the
# script exits 1 when a check failed. How to read the output: the comment above the `smoke` job in the workflow.
set -uo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <packages dir> <output dir>" >&2
  exit 2
fi
packages=$(cd "$1" && pwd) || exit 2
mkdir -p "$2/screenshots" "$2/logs" || exit 2
out=$(cd "$2" && pwd)
tmp=$(mktemp -d)
work=${RUNNER_TEMP:-$tmp}

display=:99
# Seconds: the first window after start, the process end after the close request, one screen to stop changing, the end
# of a second start on the same data folder
window_timeout=120
exit_timeout=30
settle_timeout=30
second_timeout=30
# Per 10000 pixels of the window: at most this many differ between two screenshots of a settled screen, at least this
# many after a tab switch (deliberately low: an empty screen differs from another one only in its title and a few
# lines of text; the screenshots are the real evidence)
settled_max=5
changed_min=10

# Error lines the app log may have for a reason outside the app, as "<extended regular expression>|<reason>". Any
# other error-level line or stack-trace line fails the log check; a line matching an entry (and the stack trace under
# it) is reported as INFO. The same list is in desktop_smoke_windows.ps1.
allowed_errors=(
  'E/DesktopAppUpdateChecker: Unable to check for an app update$|the CI package has the update check on (-Penable-updater) and its unauthenticated GitHub API call from a shared runner can fail or hit the rate limit (REVIEW_STEP_D1.md 3.1); the smoke test does not cover the update check'
)

failures=0
rows=()
app_pid=""
data=""
launcher=""
installed=no
background=()

record() {
  local status=$1 name=$2 detail=${3:-}
  rows+=("| $status | $name | ${detail//|/\\|} |")
  echo "[$status] $name${detail:+: $detail}"
  if [ "$status" = FAIL ]; then
    failures=$((failures + 1))
    echo "::error title=Desktop smoke test (Linux)::$name${detail:+: $detail}"
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

# Whether the process runs (a zombie, i.e. ended but not yet waited for, does not)
running() {
  local state
  state=$(ps -o stat= -p "$1" 2> /dev/null) || return 1
  [[ $state != Z* ]]
}

# Ends the app: TERM, then KILL after 10 s
stop_app() {
  [ -n "$app_pid" ] || return 0
  if running "$app_pid"; then
    kill -TERM "$app_pid" 2> /dev/null
    for _ in $(seq 1 10); do running "$app_pid" || break; sleep 1; done
    running "$app_pid" && kill -KILL "$app_pid" 2> /dev/null
  fi
  wait "$app_pid" 2> /dev/null
  app_pid=""
}

# Copies the data folder's logs and JVM crash files of the current attempt into <output dir>/logs/data-<attempt>
collect_logs() {
  [ -n "$data" ] && [ -d "$data" ] || return 0
  local dest="$out/logs/data-$attempt"
  mkdir -p "$dest"
  [ -d "$data/logs" ] && cp -R "$data/logs/." "$dest/"
  find "$data" -maxdepth 1 -name 'hs_err_pid*.log' -exec cp {} "$dest/" \;
  (cd "$data" && find . -maxdepth 3 | LC_ALL=C sort) > "$dest/data-folder-files.txt"
}

# The installed menu entries of the package (jpackage names it <package>-<app name>.desktop)
menu_entry_name=komikku-Komikku.desktop
menu_entries() {
  local dir
  for dir in /usr/share/applications /usr/local/share/applications; do
    [ -e "$dir/$menu_entry_name" ] && echo "$dir/$menu_entry_name"
  done
  return 0
}

# Also after a failure that stopped the run, as long as the package was installed
uninstall() {
  if sudo DEBIAN_FRONTEND=noninteractive apt-get remove -y -q komikku > "$out/logs/apt-remove.log" 2>&1 \
    && { [ -z "$launcher" ] || [ ! -e "$launcher" ]; }; then
    pass "Uninstall (apt-get remove)" "launcher removed"
  else
    fail "Uninstall (apt-get remove)" "apt-get failed or $launcher is still there (logs/apt-remove.log)"
  fi
  local left
  left=$(menu_entries | tr '\n' ' ')
  if [ -z "$left" ]; then
    pass "Menu entry removed" "no $menu_entry_name left"
  else
    fail "Menu entry removed" "still there: $left"
  fi
  if [ -n "$data" ] && [ -d "$data" ]; then info "Data folder after uninstall" "kept"; fi
}

finish() {
  local status=$?
  stop_app
  collect_logs
  for pid in "${background[@]}"; do kill "$pid" 2> /dev/null; done
  if [ "$installed" = yes ]; then uninstall; fi
  {
    echo "## Desktop smoke test (Linux)"
    echo
    echo "| Result | Check | Detail |"
    echo "|--------|-------|--------|"
    printf '%s\n' "${rows[@]}"
    echo
    echo "Screenshots and logs: the artifact desktop-smoke-linux-… of this run."
  } > "$out/summary.md"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then cat "$out/summary.md" >> "$GITHUB_STEP_SUMMARY"; fi
  rm -rf "$tmp"
  if [ "$failures" -gt 0 ]; then
    echo "$failures check(s) failed"
    exit 1
  fi
  exit "$status"
}
trap finish EXIT

# wait_for <seconds> <command...>: runs the command every second until it succeeds
wait_for() {
  local seconds=$1
  shift
  for _ in $(seq 1 "$seconds"); do
    "$@" > /dev/null 2>&1 && return 0
    sleep 1
  done
  return 1
}

shoot_root() {
  import -silent -display "$DISPLAY" -window root "$1"
}

# Sets X, Y, WIDTH, HEIGHT to the window $wid's position and size (clipped to the screen's top-left corner)
read_geometry() {
  local key value
  X="" Y="" WIDTH="" HEIGHT=""
  while IFS='=' read -r key value; do
    case "$key" in
      X) X=$value ;;
      Y) Y=$value ;;
      WIDTH) WIDTH=$value ;;
      HEIGHT) HEIGHT=$value ;;
    esac
  done < <(xdotool getwindowgeometry --shell "$wid" 2> /dev/null)
  [ -n "$X" ] && [ -n "$Y" ] && [ -n "$WIDTH" ] && [ -n "$HEIGHT" ] || return 1
  if [ "$X" -lt 0 ]; then WIDTH=$((WIDTH + X)) X=0; fi
  if [ "$Y" -lt 0 ]; then HEIGHT=$((HEIGHT + Y)) Y=0; fi
}

# The window's area of a screenshot of the whole screen
shoot_window() {
  read_geometry || return 1
  shoot_root "$tmp/root.png" || return 1
  convert "$tmp/root.png" -crop "${WIDTH}x${HEIGHT}+${X}+${Y}" +repage "$1"
}

# Number of pixels that differ between two screenshots (5 % colour tolerance); -1 when they cannot be compared
diff_pixels() {
  local result code
  result=$(compare -metric AE -fuzz 5% "$1" "$2" null: 2>&1)
  code=$?
  if [ "$code" -gt 1 ]; then
    echo -1
    return
  fi
  # ImageMagick may print large counts in scientific notation (1.2e+06); awk reads both
  result=$(printf '%s\n' "$result" | awk 'NR == 1 { printf "%d\n", $1 }')
  if [[ $result =~ ^[0-9]+$ ]]; then echo "$result"; else echo -1; fi
}

percent() {
  awk -v n="$1" -v total="$2" 'BEGIN { printf "%.2f", (total > 0 ? 100 * n / total : 0) }'
}

# settle <file>: screenshots of the window every 2 s until two in a row (almost) agree, at most $settle_timeout s;
# the last one is kept as <file>. Sets `settled` to yes / no.
settle() {
  local dest=$1 previous="" current="" i changed
  settled=no
  for i in $(seq 1 $((settle_timeout / 2))); do
    sleep 2
    current="$tmp/settle-$i.png"
    shoot_window "$current" || return 1
    if [ -n "$previous" ]; then
      changed=$(diff_pixels "$previous" "$current")
      if [ "$changed" -ge 0 ] && [ "$changed" -le $((WIDTH * HEIGHT * settled_max / 10000)) ]; then
        settled=yes
        break
      fi
    fi
    previous=$current
  done
  cp "$current" "$dest"
}

# Gives the window the keyboard focus (through the window manager when there is one)
focus_window() {
  timeout 10 xdotool windowactivate --sync "$wid" > /dev/null 2>&1 \
    || timeout 10 xdotool windowfocus --sync "$wid" > /dev/null 2>&1
  [ "$(xdotool getactivewindow 2> /dev/null)" = "$wid" ] \
    || [ "$(xdotool getwindowfocus 2> /dev/null)" = "$wid" ]
}

# start_app <attempt> [SKIKO_RENDER_API]: starts the launcher on a fresh data folder; sets app_pid, data, started_at
start_app() {
  attempt=$1
  local render_api=${2:-}
  data="$work/komikku-smoke-data-$attempt"
  case "$data" in
    *' '*) fatal "Data folder" "'$data' contains a space, which JAVA_TOOL_OPTIONS cannot carry" ;;
  esac
  rm -rf "$data"
  mkdir -p "$data"
  stdout_log="$out/logs/app-stdout-$attempt.log"
  stderr_log="$out/logs/app-stderr-$attempt.log"
  (
    # The working folder is where the JVM writes a crash file (hs_err_pid<pid>.log)
    cd "$data" || exit 1
    export JAVA_TOOL_OPTIONS="-Dkomikku.dataDir=$data -Dkomikku.log.level=debug -Dskiko.hardwareInfo.enabled=true"
    if [ -n "$render_api" ]; then export SKIKO_RENDER_API=$render_api; fi
    exec "$launcher"
  ) > "$stdout_log" 2> "$stderr_log" &
  app_pid=$!
  started_at=$SECONDS
  echo "Started $launcher (pid $app_pid, attempt $attempt${render_api:+, SKIKO_RENDER_API=$render_api}), data folder $data"
}

# Waits for a visible top-level window titled exactly "Komikku"; sets wid. 0 = found, 1 = timeout, 2 = process ended.
wait_window() {
  wid=""
  while [ $((SECONDS - started_at)) -lt "$window_timeout" ]; do
    running "$app_pid" || return 2
    wid=$(xdotool search --onlyvisible --name '^Komikku$' 2> /dev/null | head -n 1)
    [ -n "$wid" ] && return 0
    sleep 1
  done
  return 1
}

# Whether the window $wid is mapped as a normal window (ICCCM WM_STATE; Iconic when minimized)
window_normal() {
  xprop -id "$wid" WM_STATE 2> /dev/null | grep -q 'window state: Normal'
}

# Minimizes the window and starts the launcher once more on the same data folder: DesktopSingleInstance must make that
# start ask the running app to show its window and end with exit code 0, without a second window
second_start() {
  local second_pid code started windows minimized
  if ! running "$app_pid"; then
    fail "Second start on the same data folder" "the app had already ended"
    return
  fi
  timeout 10 xdotool windowminimize --sync "$wid" > /dev/null 2>&1
  if window_normal; then minimized=no; else minimized=yes; fi
  info "Window minimized before the second start" "$minimized"
  (
    cd "$data" || exit 1
    export JAVA_TOOL_OPTIONS="-Dkomikku.dataDir=$data -Dkomikku.log.level=debug"
    exec "$launcher"
  ) > "$out/logs/second-start-stdout.log" 2> "$out/logs/second-start-stderr.log" &
  second_pid=$!
  started=$SECONDS
  while running "$second_pid" && [ $((SECONDS - started)) -lt "$second_timeout" ]; do sleep 1; done
  if running "$second_pid"; then
    fail "Second start on the same data folder ends" \
      "still running after ${second_timeout} s (screenshots/second-start-timeout.png: a busy dialog?)"
    shoot_root "$out/screenshots/second-start-timeout.png"
    kill -KILL "$second_pid" 2> /dev/null
    wait "$second_pid" 2> /dev/null
    return
  fi
  wait "$second_pid"
  code=$?
  if [ "$code" -eq 0 ]; then
    pass "Second start on the same data folder ends with exit code 0" "after $((SECONDS - started)) s"
  else
    fail "Second start on the same data folder ends with exit code 0" "exit code $code (logs/second-start-stderr.log)"
  fi
  if wait_for 10 window_normal; then
    pass "Running window shown again" "no longer minimized"
  else
    fail "Running window shown again" "still not a normal window 10 s after the second start ended"
  fi
  shoot_root "$out/screenshots/07-second-start-screen.png"
  windows=$(xdotool search --onlyvisible --name '^Komikku$' 2> /dev/null | wc -l)
  if [ "$windows" -eq 1 ]; then
    pass "No second window"
  else
    fail "No second window" "$windows visible windows titled Komikku"
    wmctrl -l -p > "$out/logs/windows-second-start.txt" 2>&1
  fi
  running "$app_pid" || fail "First instance still running after the second start"
}

# scan_log <file>...: the error-level and stack-trace lines (the log writes a throwable's stack trace under its
# message) as "P<tab><file>:<line>:<text>", and those an allowed_errors entry matches (the error line only; its stack
# trace is skipped) as "A<tab><entry number><tab><file>:<line>:<text>"
scan_log() {
  local patterns
  patterns=$(printf '%s\n' "${allowed_errors[@]%%|*}")
  awk -v patterns="$patterns" '
    BEGIN {
      count = split(patterns, allow, "\n")
      stamp = "^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9][.][0-9][0-9][0-9] "
    }
    FNR == 1 {
      skipping = 0
      name = FILENAME
      sub(/.*\//, "", name)
    }
    $0 ~ stamp {
      skipping = 0
      if ($0 !~ (stamp "E/")) next
      for (i = 1; i <= count; i++) {
        if (allow[i] != "" && $0 ~ allow[i]) {
          print "A\t" i "\t" name ":" FNR ":" $0
          skipping = 1
          next
        }
      }
      print "P\t" name ":" FNR ":" $0
      next
    }
    /^[ \t]+at [^ ]/ || /^Caused by:/ {
      if (!skipping) print "P\t" name ":" FNR ":" $0
    }
  ' "$@"
}

# Records why no window appeared and ends that attempt
no_window() {
  local result=$1 name=$2 code
  if [ "$result" -eq 2 ]; then
    wait "$app_pid"
    code=$?
    app_pid=""
    fail "$name" "the process ended with exit code $code before a window appeared (logs/app-stderr-$attempt.log)"
  else
    fail "$name" "no visible window titled Komikku within ${window_timeout} s (screenshots/no-window-$attempt.png)"
  fi
  shoot_root "$out/screenshots/no-window-$attempt.png"
  wmctrl -l -p > "$out/logs/windows-$attempt.txt" 2>&1
  xdotool search --name '.' getwindowname %@ >> "$out/logs/windows-$attempt.txt" 2>&1
  stop_app
  collect_logs
}

# --- Runner and package ---------------------------------------------------------------------------------------------

# shellcheck source=/dev/null
os_name=$(. /etc/os-release && echo "$PRETTY_NAME")
info "Runner" "image ${ImageOS:-?} ${ImageVersion:-?}, $os_name, $(uname -m)"

artifact_files=$(find "$packages" -mindepth 1 -maxdepth 1 -printf '%f ')
sums=("$packages"/*-SHA256SUMS.txt)
if [ "${#sums[@]}" -ne 1 ] || [ ! -f "${sums[0]}" ]; then
  fatal "Package checksums" "expected one *-SHA256SUMS.txt in the artifact, found: $artifact_files"
fi
if (cd "$packages" && sha256sum -c "$(basename "${sums[0]}")") > "$out/checksums.txt" 2>&1; then
  pass "Package checksums" "$(basename "${sums[0]}")"
else
  cat "$out/checksums.txt"
  fatal "Package checksums" "sha256sum -c failed (checksums.txt)"
fi
debs=("$packages"/*.deb)
if [ "${#debs[@]}" -ne 1 ] || [ ! -f "${debs[0]}" ]; then
  fatal "Package" "expected one .deb in the artifact, found: $artifact_files"
fi

echo "::group::Install the test tools"
# notify-send (libnotify-bin), gdbus (libglib2.0-bin), dbus-run-session (dbus-daemon) and dbus-monitor (dbus-bin) for
# the notification self-tests; not dunst yet, see "Notifications" below
tools=(xvfb openbox xdotool wmctrl imagemagick x11-utils libnotify-bin libglib2.0-bin dbus-daemon dbus-bin)
if { sudo apt-get update -q \
  && sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -q --no-install-recommends "${tools[@]}"; } \
  > "$out/logs/apt-tools.log" 2>&1; then
  echo "Installed: ${tools[*]}"
else
  tail -n 40 "$out/logs/apt-tools.log"
  echo "::endgroup::"
  fatal "Test tools" "apt-get could not install them (logs/apt-tools.log)"
fi
echo "::endgroup::"

# --- Install --------------------------------------------------------------------------------------------------------

# The runner's server image has no menu folders, unlike a desktop system: the package's post-install script must
# install the menu entry anyway (run 46120905511 failed there before desktopApp/packaging/linux/postinst)
{ ls -ld /usr/share/applications /usr/local/share/applications; sudo env | grep '^XDG_' || true; } \
  > "$out/logs/menu-folders.log" 2>&1
info "Menu folders before install" "$(ls -d /usr/share/applications /usr/local/share/applications 2> /dev/null \
  | tr '\n' ' ')"
# apt resolves the package's Depends, which is a check of its own
if sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -q "${debs[0]}" > "$out/logs/apt-install.log" 2>&1; then
  installed=yes
  pass "Install (.deb with apt-get)" "$(basename "${debs[0]}")"
else
  tail -n 40 "$out/logs/apt-install.log"
  fatal "Install (.deb with apt-get)" "apt-get failed (logs/apt-install.log)"
fi
dpkg -L komikku > "$out/installed-files.txt" 2>&1
dpkg -s komikku > "$out/package-status.txt" 2>&1
info "Package dependencies" "$(sed -n 's/^Depends: //p' "$out/package-status.txt")"
launcher=$(grep -E '/bin/Komikku$' "$out/installed-files.txt" | head -n 1)
if [ -n "$launcher" ] && [ -x "$launcher" ]; then
  pass "Launcher installed" "$launcher"
else
  fatal "Launcher installed" "no executable …/bin/Komikku in dpkg -L komikku (installed-files.txt)"
fi
desktop_file=$(grep -E '\.desktop$' "$out/installed-files.txt" | head -n 1)
if [ -n "$desktop_file" ]; then
  pass "Menu entry in the package" "$desktop_file"
else
  fail "Menu entry in the package" "no .desktop file in dpkg -L komikku (installed-files.txt)"
fi
# xdg-desktop-menu puts it into the first menu folder of XDG_DATA_DIRS it can write; without one, postinst copies it to
# /usr/share/applications
menu_entry=$(menu_entries | head -n 1)
if [ -n "$menu_entry" ]; then
  pass "Menu entry installed" "$menu_entry"
else
  fail "Menu entry installed" "no $menu_entry_name in /usr/share/applications or /usr/local/share/applications"
fi

default_data="${XDG_DATA_HOME:-$HOME/.local/share}/komikku"
default_data_existed=no
[ -e "$default_data" ] && default_data_existed=yes

# --- Display --------------------------------------------------------------------------------------------------------

Xvfb "$display" -screen 0 1920x1080x24 -nolisten tcp > "$out/logs/xvfb.log" 2>&1 &
background+=("$!")
export DISPLAY=$display
if wait_for 15 xdpyinfo; then
  pass "Virtual display" "Xvfb $display, $(xdpyinfo | sed -n 's/^ *dimensions: *//p' | head -n 1)"
else
  fatal "Virtual display" "Xvfb did not start (logs/xvfb.log)"
fi
# Without a window manager AWT's focus and window state do not behave as on a desktop
openbox --sm-disable > "$out/logs/openbox.log" 2>&1 &
background+=("$!")
if wait_for 15 wmctrl -m; then
  pass "Window manager" "$(wmctrl -m | head -n 1)"
else
  fail "Window manager" "openbox did not start (logs/openbox.log); going on without one"
fi

# --- Start ----------------------------------------------------------------------------------------------------------

start_app 1
wait_window
result=$?
if [ "$result" -eq 0 ]; then
  pass "Window appears (default renderer)" "after $((SECONDS - started_at)) s"
else
  no_window "$result" "Window appears (default renderer)"
  # Skiko may not get OpenGL on Xvfb: one more try with its software renderer, recorded as a finding
  start_app 2 SOFTWARE
  wait_window
  result=$?
  if [ "$result" -eq 0 ]; then
    pass "Window appears (SKIKO_RENDER_API=SOFTWARE)" "after $((SECONDS - started_at)) s"
  else
    no_window "$result" "Window appears (SKIKO_RENDER_API=SOFTWARE)"
    exit 1
  fi
fi
info "Window" "id $wid, title '$(xdotool getwindowname "$wid" 2> /dev/null)'"

if read_geometry && [ "$WIDTH" -ge 480 ] && [ "$HEIGHT" -ge 360 ]; then
  pass "Window size" "${WIDTH}x${HEIGHT} at $X,$Y"
else
  fail "Window size" "'${WIDTH}x${HEIGHT}' at '$X,$Y' (at least 480x360 expected)"
fi
if focus_window; then
  pass "Window has the keyboard focus"
else
  fail "Window has the keyboard focus" "active window $(xdotool getactivewindow 2> /dev/null || echo none), the app's is $wid"
fi

# --- Screenshots and tabs -------------------------------------------------------------------------------------------

start_shot="$out/screenshots/01-start.png"
if settle "$start_shot"; then
  shoot_root "$out/screenshots/01-start-screen.png"
  colours=$(identify -format '%k' "$start_shot" 2> /dev/null || echo 0)
  if [ "$colours" -ge 32 ]; then
    pass "First screenshot is not blank" "$colours colours, settled: $settled"
  else
    fail "First screenshot is not blank" "$colours colours (at least 32 expected), settled: $settled"
  fi
else
  fail "First screenshot" "the window could not be captured"
fi

previous=$start_shot
more_shot=""
library_shot=""
number=2
for step in "2 updates" "3 history" "4 browse" "5 more" "1 library"; do
  key=${step%% *}
  name=${step#* }
  shot=$(printf '%s/screenshots/%02d-ctrl%s-%s.png' "$out" "$number" "$key" "$name")
  number=$((number + 1))
  if ! running "$app_pid"; then
    fail "Ctrl+$key ($name)" "the process ended before the key was sent"
    break
  fi
  focus_window || info "Focus before Ctrl+$key" "the window was not active; the key is sent anyway"
  xdotool key --clearmodifiers "ctrl+$key"
  if ! settle "$shot"; then
    fail "Ctrl+$key ($name)" "the window could not be captured"
    continue
  fi
  changed=$(diff_pixels "$previous" "$shot")
  pixels=$((WIDTH * HEIGHT))
  if [ "$changed" -lt 0 ]; then
    fail "Ctrl+$key ($name) changes the window" "the screenshots could not be compared (size changed?)"
  elif [ "$changed" -ge $((pixels * changed_min / 10000)) ]; then
    pass "Ctrl+$key ($name) changes the window" "$(percent "$changed" "$pixels") % of the pixels, settled: $settled"
  else
    fail "Ctrl+$key ($name) changes the window" "only $(percent "$changed" "$pixels") % of the pixels, settled: $settled"
  fi
  [ "$name" = more ] && more_shot=$shot
  [ "$name" = library ] && library_shot=$shot
  previous=$shot
done
# Relative, as empty screens differ little in absolute terms: Ctrl+1 must lead back to the start screen (the library),
# so its screenshot is closer to the first one than to the More screen it came from
if [ -f "$library_shot" ] && [ -f "$more_shot" ] && [ -f "$start_shot" ]; then
  to_start=$(diff_pixels "$start_shot" "$library_shot")
  to_more=$(diff_pixels "$more_shot" "$library_shot")
  pixels=$((WIDTH * HEIGHT))
  detail="$(percent "$to_start" "$pixels") % of the pixels differ from the first screenshot, $(percent "$to_more" "$pixels") % from the More screen"
  if [ "$to_start" -lt 0 ] || [ "$to_more" -lt 0 ]; then
    fail "Library after Ctrl+1 looks like the start screen" "the screenshots could not be compared"
  elif [ "$to_start" -lt "$to_more" ]; then
    pass "Library after Ctrl+1 looks like the start screen" "$detail"
  else
    fail "Library after Ctrl+1 looks like the start screen" "$detail"
  fi
fi

# --- Second start ---------------------------------------------------------------------------------------------------

second_start

# --- Close ----------------------------------------------------------------------------------------------------------

if running "$app_pid"; then
  # _NET_CLOSE_WINDOW through the window manager, which sends WM_DELETE_WINDOW (the window's close button)
  wmctrl -i -c "$wid" || xdotool key --clearmodifiers alt+F4
  close_at=$SECONDS
  while running "$app_pid" && [ $((SECONDS - close_at)) -lt "$exit_timeout" ]; do sleep 1; done
  if running "$app_pid"; then
    fail "Process ends after the window is closed" "still running after ${exit_timeout} s; thread dump in logs/app-stdout-$attempt.log"
    shoot_root "$out/screenshots/close-timeout.png"
    kill -QUIT "$app_pid" 2> /dev/null
    sleep 3
    stop_app
  else
    wait "$app_pid"
    code=$?
    app_pid=""
    pass "Process ends after the window is closed" "after $((SECONDS - close_at)) s"
    if [ "$code" -eq 0 ]; then pass "Exit code 0"; else fail "Exit code 0" "exit code $code"; fi
  fi
else
  fail "Process ends after the window is closed" "the process had already ended before the close request"
fi

# --- Logs -----------------------------------------------------------------------------------------------------------

collect_logs
logs=()
for file in "$data/logs/komikku.1.log" "$data/logs/komikku.log"; do [ -f "$file" ] && logs+=("$file"); done
if [ -s "$data/logs/komikku.log" ]; then
  pass "Log file written" "<data>/logs/komikku.log, $(wc -l < "$data/logs/komikku.log") lines"
else
  fail "Log file written" "no <data>/logs/komikku.log"
fi
if [ "${#logs[@]}" -gt 0 ]; then
  echo "::group::App log (last 80 lines)"
  tail -n 80 "$data/logs/komikku.log"
  echo "::endgroup::"
  uncaught=$(grep -h -E 'Uncaught exception|Exception in thread|FATAL' "${logs[@]}" "$stderr_log")
  if [ -z "$uncaught" ]; then
    pass "Log: no uncaught exception"
  else
    fail "Log: no uncaught exception" "$(printf '%s\n' "$uncaught" | head -n 1)"
  fi
  scan_log "${logs[@]}" > "$tmp/log-scan.txt"
  grep '^P' "$tmp/log-scan.txt" | cut -f 2- > "$out/log-problems.txt"
  for entry in $(seq 1 "${#allowed_errors[@]}"); do
    matches=$(awk -F '\t' -v entry="$entry" '$1 == "A" && $2 == entry' "$tmp/log-scan.txt")
    [ -n "$matches" ] || continue
    info "Log: allowed error ($(printf '%s\n' "$matches" | wc -l) time(s))" \
      "$(printf '%s\n' "$matches" | head -n 1 | cut -f 3-) - allowed because ${allowed_errors[$((entry - 1))]#*|}"
  done
  problems=$(wc -l < "$out/log-problems.txt")
  if [ "$problems" -eq 0 ]; then
    pass "Log: no error or stack trace" "apart from the allowed ones above, if any"
  else
    echo "::group::Errors and stack traces in the log (log-problems.txt)"
    head -n 60 "$out/log-problems.txt"
    echo "::endgroup::"
    fail "Log: no error or stack trace" "$problems line(s), first: $(head -n 1 "$out/log-problems.txt")"
  fi
  tray=$(grep -h 'System tray' "${logs[@]}" | head -n 3)
  info "System tray lines in the log" "${tray:-none (no tray error logged)}"
fi
crash=$(find "$data" -maxdepth 1 -name 'hs_err_pid*.log' | head -n 1)
if [ -z "$crash" ]; then
  pass "No JVM crash file"
else
  fail "No JVM crash file" "$(basename "$crash") (logs/data-$attempt/)"
fi
if [ "$default_data_existed" = no ] && [ -e "$default_data" ]; then
  fail "Default data folder untouched" "$default_data was created although -Dkomikku.dataDir was set"
else
  pass "Default data folder untouched" "$default_data (existed before: $default_data_existed)"
fi

# --- Notifications --------------------------------------------------------------------------------------------------

# The installed launcher's `--self-test-notify[=<backend>]` (WINDOWS_LINUX_TEST_PLAN.md, section 5), each run on its own
# session bus with dbus-monitor recording the Notify calls: C without a notification service (must fail), then with
# dunst: the plain self-test (must post nothing), A in the app's order (notify-send) and B through gdbus.
notify_title="Komikku self-test"
notify_body_pieces=('"quotes"' '&amp; &lt;tags&gt;' 'ü 漫画 😀')

# notify_run <name> <self-test arguments...>: writes logs/notify-<name>.{txt,exit,monitor.log} and, while the bus (and
# dunst on it) still runs, screenshots/notify-<name>.png
notify_run() {
  local name=$1
  shift
  dbus-run-session -- bash -c '
    out=$1 name=$2 launcher=$3; shift 3
    dbus-monitor --session "type=method_call,interface=org.freedesktop.Notifications,member=Notify" \
      > "$out/logs/notify-$name.monitor.log" 2>&1 &
    monitor=$!
    sleep 1
    timeout 120 "$launcher" "--self-test=$out/logs/notify-$name.txt" "$@" \
      > "$out/logs/notify-$name.stdout.log" 2>&1
    echo $? > "$out/logs/notify-$name.exit"
    sleep 2
    import -silent -display "$DISPLAY" -window root "$out/screenshots/notify-$name.png"
    kill "$monitor"
  ' notify-run "$out" "$name" "$launcher" "$@"
}

notify_exit() { cat "$out/logs/notify-$1.exit" 2> /dev/null || echo none; }
notify_line() { grep -m 1 '^\(OK  \|FAIL\) system notification: ' "$out/logs/notify-$1.txt" 2> /dev/null; }
notify_calls() { grep -c 'member=Notify' "$out/logs/notify-$1.monitor.log" 2> /dev/null; }

# The body string of the first Notify call: its arguments are app name, replaces id, icon, summary, body, … (the fourth
# string)
notify_body() {
  awk '/member=Notify/ { call = 1; n = 0; next } call && /^ +string "/ { if (++n == 4) { print; exit } }' \
    "$out/logs/notify-$1.monitor.log" 2> /dev/null
}

# notify_check_call <name>: the one Notify call carries the app name, the title and the escaped body
notify_check_call() {
  local name=$1 log="$out/logs/notify-$1.monitor.log" calls missing=() piece
  calls=$(notify_calls "$name")
  grep -q -F 'string "Komikku"' "$log" || missing+=('string "Komikku"')
  grep -q -F "string \"$notify_title\"" "$log" || missing+=("string \"$notify_title\"")
  for piece in "${notify_body_pieces[@]}"; do grep -q -F "$piece" "$log" || missing+=("$piece"); done
  if [ "$calls" = 1 ] && [ "${#missing[@]}" -eq 0 ]; then
    pass "Notify call on the bus ($name)" "app name, title and escaped body as expected"
  else
    fail "Notify call on the bus ($name)" "$calls Notify call(s); missing: ${missing[*]:-nothing} (logs/notify-$name.monitor.log)"
  fi
  info "Notification body as dbus-monitor shows it ($name)" "$(notify_body "$name" | sed 's/^ *//')"
}

ls /usr/share/dbus-1/services > "$out/logs/dbus-services-before-dunst.txt" 2>&1
if [ -z "${DISPLAY:-}" ] || ! command -v dbus-run-session > /dev/null || ! command -v dbus-monitor > /dev/null; then
  fail "Notifications" "no display or no dbus-run-session / dbus-monitor (logs/apt-tools.log); not run"
else
  notify_run c --self-test-notify
  line=$(notify_line c)
  if [ "$(notify_exit c)" = 1 ] && [[ $line == "FAIL system notification: "* ]]; then
    pass "Notification without a notification service fails (C)" "exit code 1; ${line#FAIL system notification: }"
  else
    fail "Notification without a notification service fails (C)" \
      "exit code $(notify_exit c), report line '${line:-none}' (logs/notify-c.txt, dbus-services-before-dunst.txt)"
  fi
  info "Notify calls without a service (C)" "$(notify_calls c) (logs/notify-c.monitor.log)"

  if sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -q dunst > "$out/logs/apt-dunst.log" 2>&1; then
    pass "Notification service installed" "dunst (started by D-Bus activation on the first Notify)"
    notify_run silent
    if [ "$(notify_exit silent)" = 0 ] && [ "$(notify_calls silent)" = 0 ] && [ -z "$(notify_line silent)" ]; then
      pass "Plain self-test posts no notification" "exit code 0, no Notify call"
    else
      fail "Plain self-test posts no notification" \
        "exit code $(notify_exit silent), $(notify_calls silent) Notify call(s), report line '$(notify_line silent)'"
    fi

    for run in "a|--self-test-notify|notify-send" "b|--self-test-notify=gdbus|gdbus"; do
      IFS='|' read -r name argument backend <<< "$run"
      notify_run "$name" "$argument"
      line=$(notify_line "$name")
      expected="OK   system notification: $backend exited with 0 (accepted)"
      if [ "$(notify_exit "$name")" = 0 ] && [ "$line" = "$expected" ]; then
        pass "Notification through $backend (${name^^})" "exit code 0; screenshots/notify-$name.png"
      else
        fail "Notification through $backend (${name^^})" \
          "exit code $(notify_exit "$name"), report line '${line:-none}' (logs/notify-$name.txt)"
      fi
      notify_check_call "$name"
    done
    body_a=$(notify_body a)
    body_b=$(notify_body b)
    if [ -n "$body_a" ] && [ "$body_a" = "$body_b" ]; then
      pass "notify-send and gdbus send the same body"
    else
      fail "notify-send and gdbus send the same body" "A: '${body_a:-none}', B: '${body_b:-none}'"
    fi
  else
    tail -n 40 "$out/logs/apt-dunst.log"
    fail "Notification service installed" "apt-get could not install dunst (logs/apt-dunst.log); A, B and the plain run skipped"
  fi
fi
