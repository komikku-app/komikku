#Requires -Version 7.0
<#
KMK: the packaged app's own UI smoke mode (`--ui-smoke=<report folder>`, docs/kmp-desktop/UI_SMOKE_DESIGN.md) on
Windows, run by .github/workflows/build_desktop.yml (job `smoke`, windows-2022) on the launcher unpacked from the .msi.
The mode starts the app on a new temporary data folder, drives a fixed script through the real window, takes a
screenshot after each step and makes no network connection; this script only starts it and turns its exit code and
report into rows. Linux and macOS: desktop_ui_smoke.sh (same rows).

  desktop_ui_smoke.ps1 -Launcher <Komikku.exe> -Output <output dir>

  <output dir>  report\ (the mode's report folder: report.txt, report.json, the step screenshots, komikku.log,
                last-frame-accessibility.txt), stdout.log, stderr.log, summary.md (created)

Every check is printed as [PASS] / [FAIL] / [INFO] and written to <output dir>\summary.md and the job summary; the
script exits 1 when a check failed.
#>
param(
    [Parameter(Mandatory = $true)][string] $Launcher,
    [Parameter(Mandatory = $true)][string] $Output
)
Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

# Seconds: the mode's watchdog ends it after 5 minutes; this is the limit for a launcher that hangs before or after it
$Timeout = 480

New-Item -ItemType Directory -Force -Path $Output | Out-Null
$Output = (Resolve-Path -LiteralPath $Output).Path
$Report = Join-Path $Output 'report'
$script:Rows = [System.Collections.Generic.List[string]]::new()
$script:Failures = 0

function Write-Check([string] $Status, [string] $Name, [string] $Detail = '') {
    $script:Rows.Add("| $Status | $Name | $($Detail -replace '\|', '\|') |")
    Write-Host "[$Status] $Name$(if ($Detail) { ": $Detail" })"
    if ($Status -eq 'FAIL') {
        $script:Failures++
        Write-Host "::error title=Desktop UI smoke (Windows)::$Name$(if ($Detail) { ": $Detail" })"
    }
}
function Pass([string] $Name, [string] $Detail = '') { Write-Check 'PASS' $Name $Detail }
function Fail([string] $Name, [string] $Detail = '') { Write-Check 'FAIL' $Name $Detail }
function Info([string] $Name, [string] $Detail = '') { Write-Check 'INFO' $Name $Detail }
function Get-OneLine([string] $Text) { ($Text -replace '\s+', ' ').Trim() }

try {
    if (-not (Test-Path -LiteralPath $Launcher -PathType Leaf)) {
        Fail 'Launcher' "no $Launcher; not run"
        throw [System.OperationCanceledException]::new('not run')
    }
    Info 'Launcher' $Launcher
    # Start-Process joins the arguments with spaces and does not quote them
    if ($Report.Contains(' ')) {
        Fail 'Report folder' "'$Report' contains a space; not run"
        throw [System.OperationCanceledException]::new('not run')
    }

    # The mode checks that its window is 1280 x 800, which a screen of 1024 x 768 (the runner's default) cannot hold
    Add-Type -AssemblyName System.Windows.Forms
    $screen = [System.Windows.Forms.SystemInformation]::VirtualScreen
    if ($screen.Width -lt 1280 -or $screen.Height -lt 800) {
        if (Get-Command Set-DisplayResolution -ErrorAction SilentlyContinue) {
            try { Set-DisplayResolution -Width 1920 -Height 1080 -Force } catch { Info 'Screen resolution change' $_.Exception.Message }
            $screen = [System.Windows.Forms.SystemInformation]::VirtualScreen
        }
    }
    Info 'Screen' "$($screen.Width)x$($screen.Height)"

    Remove-Item -LiteralPath $Report -Recurse -Force -ErrorAction SilentlyContinue
    # The mode's data folder is a new folder in java.io.tmpdir: RUNNER_TEMP (emptied after the job) rather than TEMP,
    # whose path on the runner is a short 8.3 name
    $tmpdir = if ($env:RUNNER_TEMP) { $env:RUNNER_TEMP } else { [IO.Path]::GetTempPath().TrimEnd('\') }
    $env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$tmpdir"
    try {
        $app = Start-Process -FilePath $Launcher -ArgumentList @("--ui-smoke=$Report") -PassThru `
            -RedirectStandardOutput (Join-Path $Output 'stdout.log') -RedirectStandardError (Join-Path $Output 'stderr.log')
        # Keeps the process handle, without which ExitCode stays empty after the process ended
        $null = $app.Handle
    } finally {
        Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
    }
    $startedAt = [DateTime]::UtcNow
    $ended = $app.WaitForExit($Timeout * 1000)
    $elapsed = [int]([DateTime]::UtcNow - $startedAt).TotalSeconds
    if ($ended) {
        # The parameterless wait also waits for the redirected output to be complete
        $app.WaitForExit()
    } else {
        Stop-Process -Id $app.Id -Force -ErrorAction SilentlyContinue
        $app.WaitForExit(10000) | Out-Null
    }

    # --- Exit code ------------------------------------------------------------------------------------------------

    if (-not $ended) {
        Fail 'Exit code' "still running after $Timeout s, killed (the mode's watchdog should end it after 300 s)"
    } else {
        switch ($app.ExitCode) {
            0 { Pass 'Exit code' "0 (passed), after $elapsed s" }
            1 { Fail 'Exit code' "1 (a step, a network attempt or the shutdown failed), after $elapsed s" }
            2 { Fail 'Exit code' "2 (refused before anything started), after $elapsed s; reason in stderr.log" }
            3 { Fail 'Exit code' "3 (watchdog: no result after 5 minutes), after $elapsed s" }
            default { Fail 'Exit code' "$($app.ExitCode) (not one of the mode's codes: the launcher or the JVM failed), after $elapsed s; stderr.log" }
        }
    }

    # --- Report ---------------------------------------------------------------------------------------------------

    $text = Join-Path $Report 'report.txt'
    $lines = @(if (Test-Path -LiteralPath $text) { Get-Content -LiteralPath $text | Where-Object { $_.Trim() } })
    $last = if ($lines.Count -gt 0) { $lines[-1] } else { 'no report.txt' }
    if ($last -ceq 'UI-SMOKE PASSED') { Pass 'Report result' $last } else { Fail 'Report result' "last line of report.txt: '$last'" }

    $json = Join-Path $Report 'report.json'
    if (Test-Path -LiteralPath $json) {
        $data = Get-Content -LiteralPath $json -Raw -Encoding utf8 | ConvertFrom-Json
        $steps = @($data.steps)
        $failed = @($steps | Where-Object { $_.status -ne 'PASS' })
        $failure = if ($data.failure) { Get-OneLine (($data.failure -split "`n")[0]) } else { '' }
        if ($failed.Count -eq 0 -and -not $failure) {
            Pass 'Steps' "all $($steps.Count) passed"
        } elseif ($failed.Count -eq 0) {
            Fail 'Steps' "$($steps.Count) step(s) ran, none failed; failure: $failure"
        } else {
            $detail = ($failed | Select-Object -First 3 | ForEach-Object {
                    Get-OneLine "$($_.name): $(if ($_.detail) { $_.detail } else { 'no detail' }) (until: $($_.condition))"
                }) -join ';'
            Fail 'Steps' "$($failed.Count) of $($steps.Count) failed: $detail$(if ($failure) { "; failure: $failure" })"
        }
        $attempts = @($data.networkAttempts)
        if ($attempts.Count -eq 0) {
            Pass 'Network attempts' '0'
        } else {
            Fail 'Network attempts' "$($attempts.Count): $(($attempts | Select-Object -First 3 | ForEach-Object { "$($_.uri) (thread $($_.thread))" }) -join ';')"
        }
        $environment = foreach ($key in 'render api', 'window', 'scale') {
            $value = $data.environment.PSObject.Properties[$key]
            if ($value) { "${key}: $($value.Value)" }
        }
        if ($environment) { Info 'Environment' (@($environment) -join ';') }
        $pngs = @(Get-ChildItem -LiteralPath $Report -Filter '*.png' -File)
        $others = @('komikku.log', 'last-frame-accessibility.txt' | Where-Object { Test-Path -LiteralPath (Join-Path $Report $_) })
        Info 'Report folder' "$($pngs.Count) screenshot(s) (13 when every step ran), $(if ($others.Count) { $others -join ' ' } else { 'no log or accessibility file' })"
    } else {
        Fail 'Steps' 'no report.json (stderr.log)'
        Fail 'Network attempts' 'unknown: no report.json'
    }
    $refused = Select-String -LiteralPath (Join-Path $Output 'stderr.log'), (Join-Path $Output 'stdout.log') `
        -Pattern 'UI smoke refused' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($refused) { Info 'Refusal' $refused.Line }
} catch [System.OperationCanceledException] {
    # Not run: the failure is recorded already
} catch {
    Fail 'Script error' "$($_.Exception.Message) at $($_.InvocationInfo.PositionMessage -replace '\s+', ' ')"
} finally {
    $summary = @('## Desktop UI smoke (Windows, --ui-smoke)', '', '| Result | Check | Detail |', '|--------|-------|--------|') +
        $script:Rows + @('', 'Report, screenshots and log: ui-smoke\ in the artifact desktop-smoke-windows-… of this run.')
    Set-Content -LiteralPath (Join-Path $Output 'summary.md') -Value $summary -Encoding utf8
    if ($env:GITHUB_STEP_SUMMARY) { Add-Content -LiteralPath $env:GITHUB_STEP_SUMMARY -Value $summary -Encoding utf8 }
}
if ($script:Failures -gt 0) {
    Write-Host "$($script:Failures) check(s) failed"
    exit 1
}
