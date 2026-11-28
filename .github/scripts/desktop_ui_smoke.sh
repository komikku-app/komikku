#!/usr/bin/env bash
# KMK: the packaged app's own UI smoke mode (`--ui-smoke=<report folder>`, docs/kmp-desktop/UI_SMOKE_DESIGN.md) on Linux
# and macOS, run by .github/workflows/build_desktop.yml (job `smoke`) on the installed or unpacked launcher. The mode
# starts the app on a new temporary data folder, drives a fixed script through the real window, takes a screenshot
# after each step and makes no network connection; this script only starts it (on Linux on a virtual display of its
# own, 1920x1080) and turns its exit code and report into rows. Windows: desktop_ui_smoke.ps1.
#
#   desktop_ui_smoke.sh <launcher> <output dir>
#
#   <launcher>    the installed bin/Komikku (Linux) or Komikku.app/Contents/MacOS/Komikku (macOS)
#   <output dir>  report/ (the mode's report folder: report.txt, report.json, the step screenshots, komikku.log,
#                 last-frame-accessibility.txt), stdout.log, summary.md (created)
#
# Every check is printed as [PASS] / [FAIL] / [INFO] and written to <output dir>/summary.md and the job summary; the
# script exits 1 when a check failed.
set -uo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <launcher> <output dir>" >&2
  exit 2
fi
launcher=$1
mkdir -p "$2" || exit 2
out=$(cd "$2" && pwd)
report="$out/report"
os=$(uname -s)
os_name=$([ "$os" = Darwin ] && echo macOS || echo Linux)
# Seconds: the mode's watchdog ends it after 5 minutes; this is the limit for a launcher that hangs before or after it
timeout_seconds=480

failures=0
rows=()

record() {
  local status=$1 name=$2 detail=${3:-}
  rows+=("| $status | $name | ${detail//|/\\|} |")
  echo "[$status] $name${detail:+: $detail}"
  if [ "$status" = FAIL ]; then
    failures=$((failures + 1))
    echo "::error title=Desktop UI smoke ($os_name)::$name${detail:+: $detail}"
  fi
}
pass() { record PASS "$@"; }
fail() { record FAIL "$@"; }
info() { record INFO "$@"; }

finish() {
  {
    echo "## Desktop UI smoke ($os_name, --ui-smoke)"
    echo
    echo "| Result | Check | Detail |"
    echo "|--------|-------|--------|"
    printf '%s\n' "${rows[@]}"
    echo
    echo "Report, screenshots and log: ui-smoke/ in the artifact desktop-smoke-… of this run."
  } > "$out/summary.md"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then cat "$out/summary.md" >> "$GITHUB_STEP_SUMMARY"; fi
  if [ "$failures" -gt 0 ]; then
    echo "$failures check(s) failed"
    exit 1
  fi
  exit 0
}
trap finish EXIT

if [ ! -x "$launcher" ]; then
  fail "Launcher" "no executable $launcher; not run"
  exit 1
fi
info "Launcher" "$launcher"
rm -rf "$report"

# The mode's data folder is a new folder in java.io.tmpdir; RUNNER_TEMP is emptied after the job
tmpdir=${RUNNER_TEMP:-${TMPDIR:-/tmp}}
command=("$launcher" "--ui-smoke=$report")
if [ "$os" = Linux ]; then
  if ! command -v xvfb-run > /dev/null; then
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -q xvfb > "$out/apt-xvfb.log" 2>&1
  fi
  command=(xvfb-run -a -s "-screen 0 1920x1080x24" "${command[@]}")
fi

# No `timeout` on macOS: the launcher runs in the background and is killed after timeout_seconds
started=$SECONDS
JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$tmpdir" "${command[@]}" > "$out/stdout.log" 2>&1 &
pid=$!
while kill -0 "$pid" 2> /dev/null && [ $((SECONDS - started)) -lt "$timeout_seconds" ]; do sleep 2; done
timed_out=no
if kill -0 "$pid" 2> /dev/null; then
  timed_out=yes
  pkill -TERM -P "$pid" 2> /dev/null
  kill -TERM "$pid" 2> /dev/null
  sleep 5
  pkill -KILL -P "$pid" 2> /dev/null
  kill -KILL "$pid" 2> /dev/null
fi
wait "$pid"
code=$?
elapsed=$((SECONDS - started))

# --- Exit code ------------------------------------------------------------------------------------------------------

case "$timed_out/$code" in
  yes/*) fail "Exit code" "still running after $timeout_seconds s, killed (the mode's watchdog should end it after 300 s)" ;;
  no/0) pass "Exit code" "0 (passed), after $elapsed s" ;;
  no/1) fail "Exit code" "1 (a step, a network attempt or the shutdown failed), after $elapsed s" ;;
  no/2) fail "Exit code" "2 (refused before anything started), after $elapsed s; reason in stdout.log" ;;
  no/3) fail "Exit code" "3 (watchdog: no result after 5 minutes), after $elapsed s" ;;
  *) fail "Exit code" "$code (not one of the mode's codes: the launcher or the JVM failed), after $elapsed s; stdout.log" ;;
esac

# --- Report ---------------------------------------------------------------------------------------------------------

last=$(grep -v '^[[:space:]]*$' "$report/report.txt" 2> /dev/null | tail -n 1)
if [ "$last" = "UI-SMOKE PASSED" ]; then
  pass "Report result" "$last"
else
  fail "Report result" "last line of report.txt: '${last:-no report.txt}'"
fi

# The failing step, the failure, the network attempts and the files, from report.json, as "<kind>\t<text>" lines
if [ -f "$report/report.json" ]; then
  parsed=$(python3 - "$report" << 'EOF'
import json, os, sys
folder = sys.argv[1]
report = json.load(open(os.path.join(folder, "report.json"), encoding="utf-8"))
def line(kind, text):
    print(kind + "\t" + " ".join(str(text).split()))
steps = report.get("steps") or []
failed = [step for step in steps if step.get("status") != "PASS"]
line("steps", f"{len(steps)} {len(failed)}")
for step in failed[:3]:
    line("failed", f"{step.get('name')}: {step.get('detail') or 'no detail'} (until: {step.get('condition')})")
failure = report.get("failure")
if failure:
    line("failure", failure.strip().splitlines()[0][:300])
attempts = report.get("networkAttempts") or []
line("attempts", len(attempts))
for attempt in attempts[:3]:
    line("attempt", f"{attempt.get('uri')} (thread {attempt.get('thread')})")
for key in ("render api", "window", "scale"):
    value = (report.get("environment") or {}).get(key)
    if value is not None:
        line("environment", f"{key}: {value}")
pngs = sorted(name for name in os.listdir(folder) if name.endswith(".png"))
extra = [name for name in ("komikku.log", "last-frame-accessibility.txt") if os.path.isfile(os.path.join(folder, name))]
line("files", f"{len(pngs)} {' '.join(extra)}")
EOF
  )
  field() { printf '%s\n' "$parsed" | awk -F '\t' -v kind="$1" '$1 == kind { sub(/^[^\t]*\t/, ""); print }'; }
  read -r step_count failed_count <<< "$(field steps)"
  failure=$(field failure)
  if [ "${failed_count:-1}" = 0 ] && [ -z "$failure" ]; then
    pass "Steps" "all ${step_count:-0} passed"
  elif [ "${failed_count:-1}" = 0 ]; then
    fail "Steps" "${step_count:-0} step(s) ran, none failed; failure: $failure"
  else
    detail=$(field failed | paste -s -d ';' -)
    fail "Steps" "${failed_count:-?} of ${step_count:-?} failed: $detail${failure:+; failure: $failure}"
  fi
  attempts=$(field attempts)
  if [ "$attempts" = 0 ]; then
    pass "Network attempts" "0"
  else
    fail "Network attempts" "${attempts:-unknown}: $(field attempt | paste -s -d ';' -)"
  fi
  environment=$(field environment | paste -s -d ';' -)
  [ -z "$environment" ] || info "Environment" "$environment"
  read -r png_count others <<< "$(field files)"
  info "Report folder" "${png_count:-0} screenshot(s) (13 when every step ran), ${others:-no log or accessibility file}"
else
  fail "Steps" "no report.json (stdout.log)"
  fail "Network attempts" "unknown: no report.json"
fi
if grep -q 'UI smoke refused' "$out/stdout.log" 2> /dev/null; then
  info "Refusal" "$(grep -m 1 'UI smoke refused' "$out/stdout.log")"
fi
