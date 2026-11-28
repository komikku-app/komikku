#Requires -Version 7.0
<#
KMK: first smoke test of the packaged Windows app (docs/kmp-desktop/WINDOWS_LINUX_TEST_PLAN.md, section 4), run by
.github/workflows/build_desktop.yml (job `smoke`, windows-2022) on the `package` job's artifact. Installs the .msi
(per-user, silent), starts the installed launcher on a fresh data folder (-Dkomikku.dataDir under $env:RUNNER_TEMP,
never the default one), waits for the window, takes screenshots (.NET screen capture), switches the top-level tabs
with Ctrl+2…5 and Ctrl+1 (user32 key events), minimizes the window and starts the launcher a second time on the same
data folder (the single-instance lock must show the running window and end that start with exit code 0), closes the
window (WM_CLOSE), checks that the process ends with exit code 0 and that the log has no exception, then uninstalls. The app is driven only from outside (window system and
keyboard shortcuts).

  desktop_smoke_windows.ps1 -Packages <packages dir> -Output <output dir>

  <packages dir>  the artifact Komikku-desktop-windows-X64-<sha> (Komikku-<v>-windows-x64.msi and its SHA256SUMS)
  <output dir>    screenshots\, logs\, summary.md and the other files the workflow uploads (created)

Every check is printed as [PASS] / [FAIL] / [INFO] and written to <output dir>\summary.md and the job summary; the
script exits 1 when a check failed. How to read the output: the comment above the `smoke` job in the workflow.
#>
param(
    [Parameter(Mandatory = $true)][string] $Packages,
    [Parameter(Mandatory = $true)][string] $Output
)
Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

# Seconds: the first window after start, the process end after the close request, one screen to stop changing, the
# end of a second start on the same data folder
$WindowTimeout = 120
$ExitTimeout = 30
$SettleTimeout = 30
$SecondTimeout = 30
# Per 10000 pixels of the window: at most this many differ between two screenshots of a settled screen, at least this
# many after a tab switch (deliberately low: an empty screen differs from another one only in its title and a few
# lines of text; the screenshots are the real evidence)
$SettledMax = 5
$ChangedMin = 10

$Packages = (Resolve-Path -LiteralPath $Packages).Path
$Screens = Join-Path $Output 'screenshots'
$Logs = Join-Path $Output 'logs'
New-Item -ItemType Directory -Force -Path $Screens, $Logs | Out-Null
$Output = (Resolve-Path -LiteralPath $Output).Path
$Screens = Join-Path $Output 'screenshots'
$Logs = Join-Path $Output 'logs'
$Tmp = Join-Path ([IO.Path]::GetTempPath()) ('komikku-smoke-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $Tmp | Out-Null
$Work = if ($env:RUNNER_TEMP) { $env:RUNNER_TEMP } else { $Tmp }

$script:Rows = [System.Collections.Generic.List[string]]::new()
$script:Failures = 0
$script:App = $null
$script:Window = [IntPtr]::Zero
$script:Data = $null
$script:Attempt = 0
$script:ShotWidth = 0
$script:ShotHeight = 0
$script:Settled = $false
$script:CaptureError = $null

function Write-Check([string] $Status, [string] $Name, [string] $Detail = '') {
    $script:Rows.Add("| $Status | $Name | $($Detail -replace '\|', '\|') |")
    $line = "[$Status] $Name"
    if ($Detail) { $line += ": $Detail" }
    Write-Host $line
    if ($Status -eq 'FAIL') {
        $script:Failures++
        Write-Host "::error title=Desktop smoke test (Windows)::$Name$(if ($Detail) { ": $Detail" })"
    }
}
function Pass([string] $Name, [string] $Detail = '') { Write-Check 'PASS' $Name $Detail }
function Fail([string] $Name, [string] $Detail = '') { Write-Check 'FAIL' $Name $Detail }
function Info([string] $Name, [string] $Detail = '') { Write-Check 'INFO' $Name $Detail }
# A failure after which nothing else can be checked
function Stop-Smoke([string] $Name, [string] $Detail = '') {
    Fail $Name $Detail
    throw [System.OperationCanceledException]::new('smoke test stopped')
}

# Runs a program and returns its exit code, standard output and standard error (no shell, no PowerShell stream rules)
function Invoke-Native([string] $File, [string[]] $Arguments) {
    $info = [System.Diagnostics.ProcessStartInfo]::new($File)
    foreach ($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($info)
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    [pscustomobject]@{ ExitCode = $process.ExitCode; StdOut = $stdout.Result; StdErr = $stderr }
}

# Compiled at the start of the run (inside its error handling)
$Win32Source = @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

public static class SmokeWin32 {
    [StructLayout(LayoutKind.Sequential)] struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] struct POINT { public int X, Y; }
    delegate bool EnumWindowsProc(IntPtr window, IntPtr parameter);

    [DllImport("user32.dll")] static extern bool EnumWindows(EnumWindowsProc callback, IntPtr parameter);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr window, StringBuilder text, int count);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr window);
    [DllImport("user32.dll")] static extern bool IsIconic(IntPtr window);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);
    [DllImport("user32.dll")] static extern bool GetWindowRect(IntPtr window, out RECT rect);
    [DllImport("user32.dll")] static extern bool GetClientRect(IntPtr window, out RECT rect);
    [DllImport("user32.dll")] static extern bool ClientToScreen(IntPtr window, ref POINT point);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr window);
    [DllImport("user32.dll")] static extern bool BringWindowToTop(IntPtr window);
    [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr window, int command);
    [DllImport("user32.dll")] static extern bool AttachThreadInput(uint thread, uint toThread, bool attach);
    [DllImport("kernel32.dll")] static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] static extern void keybd_event(byte key, byte scan, uint flags, UIntPtr extra);
    [DllImport("user32.dll")] static extern bool PostMessage(IntPtr window, uint message, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("kernel32.dll")] public static extern uint WTSGetActiveConsoleSessionId();

    const uint KeyUp = 2;
    const byte VkControl = 0x11, VkMenu = 0x12;

    public static string Title(IntPtr window) {
        var text = new StringBuilder(512);
        GetWindowText(window, text, text.Capacity);
        return text.ToString();
    }

    public static uint ProcessId(IntPtr window) {
        uint processId;
        GetWindowThreadProcessId(window, out processId);
        return processId;
    }

    public static List<IntPtr> VisibleWindows() {
        var windows = new List<IntPtr>();
        EnumWindows((window, parameter) => { if (IsWindowVisible(window)) windows.Add(window); return true; }, IntPtr.Zero);
        return windows;
    }

    // x, y, width, height of the whole window, or null
    public static int[] Bounds(IntPtr window) {
        RECT rect;
        if (!GetWindowRect(window, out rect)) return null;
        return new[] { rect.Left, rect.Top, rect.Right - rect.Left, rect.Bottom - rect.Top };
    }

    // x, y, width, height of the window's content (without title bar and borders) on the screen, or null
    public static int[] ClientBounds(IntPtr window) {
        RECT rect;
        if (!GetClientRect(window, out rect)) return null;
        var origin = new POINT();
        if (!ClientToScreen(window, ref origin)) return null;
        return new[] { origin.X, origin.Y, rect.Right, rect.Bottom };
    }

    public static bool Minimized(IntPtr window) { return IsIconic(window); }

    public static void Minimize(IntPtr window) { ShowWindow(window, 6); }

    // Brings the window to the front with the keyboard focus; returns how, or "failed"
    public static string Focus(IntPtr window) {
        if (IsIconic(window)) ShowWindow(window, 9);
        if (GetForegroundWindow() == window) return "already in front";
        SetForegroundWindow(window);
        Thread.Sleep(300);
        if (GetForegroundWindow() == window) return "SetForegroundWindow";
        uint ignored;
        var foreground = GetForegroundWindow();
        uint foregroundThread = foreground == IntPtr.Zero ? 0 : GetWindowThreadProcessId(foreground, out ignored);
        uint thread = GetCurrentThreadId();
        if (foregroundThread != 0 && foregroundThread != thread) {
            AttachThreadInput(thread, foregroundThread, true);
            BringWindowToTop(window);
            SetForegroundWindow(window);
            AttachThreadInput(thread, foregroundThread, false);
            Thread.Sleep(300);
            if (GetForegroundWindow() == window) return "AttachThreadInput";
        }
        // Windows lets the process that sent the last input event set the foreground window
        keybd_event(VkMenu, 0, 0, UIntPtr.Zero);
        SetForegroundWindow(window);
        keybd_event(VkMenu, 0, KeyUp, UIntPtr.Zero);
        Thread.Sleep(300);
        return GetForegroundWindow() == window ? "Alt key event" : "failed";
    }

    // Ctrl+<key> to the foreground window (key: a virtual-key code, '1' = 0x31)
    public static void CtrlKey(byte key) {
        keybd_event(VkControl, 0, 0, UIntPtr.Zero);
        Thread.Sleep(50);
        keybd_event(key, 0, 0, UIntPtr.Zero);
        Thread.Sleep(50);
        keybd_event(key, 0, KeyUp, UIntPtr.Zero);
        Thread.Sleep(50);
        keybd_event(VkControl, 0, KeyUp, UIntPtr.Zero);
    }

    // WM_CLOSE: what the window's close button sends
    public static bool Close(IntPtr window) { return PostMessage(window, 0x0010, IntPtr.Zero, IntPtr.Zero); }
}
'@

function Save-Screen([int] $X, [int] $Y, [int] $Width, [int] $Height, [string] $Path) {
    $bitmap = [System.Drawing.Bitmap]::new($Width, $Height)
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try { $graphics.CopyFromScreen($X, $Y, 0, 0, [System.Drawing.Size]::new($Width, $Height)) } finally { $graphics.Dispose() }
        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
}

# The whole (virtual) screen; failures are recorded, never thrown
function Save-Desktop([string] $Path) {
    try {
        $screen = [System.Windows.Forms.SystemInformation]::VirtualScreen
        Save-Screen $screen.X $screen.Y $screen.Width $screen.Height $Path
    } catch {
        Info 'Screenshot of the screen' "$(Split-Path -Leaf $Path) failed: $($_.Exception.Message)"
    }
}

# The window's content, clipped to the screen; sets ShotWidth / ShotHeight. $false (and CaptureError) on failure.
function Save-AppWindow([string] $Path) {
    $bounds = [SmokeWin32]::ClientBounds($script:Window)
    if ($null -eq $bounds) { $script:CaptureError = 'the window has no bounds (closed?)'; return $false }
    $screen = [System.Windows.Forms.SystemInformation]::VirtualScreen
    $left = [Math]::Max($bounds[0], $screen.Left)
    $top = [Math]::Max($bounds[1], $screen.Top)
    $right = [Math]::Min($bounds[0] + $bounds[2], $screen.Right)
    $bottom = [Math]::Min($bounds[1] + $bounds[3], $screen.Bottom)
    if ($right - $left -lt 1 -or $bottom - $top -lt 1) {
        $script:CaptureError = "the window's content ($($bounds -join ',')) is off the screen ($screen)"
        return $false
    }
    try {
        Save-Screen $left $top ($right - $left) ($bottom - $top) $Path
    } catch {
        $script:CaptureError = $_.Exception.Message
        return $false
    }
    $script:ShotWidth = $right - $left
    $script:ShotHeight = $bottom - $top
    return $true
}

$command = Get-Command magick -ErrorAction SilentlyContinue
$Magick = if ($command) { $command.Source } else { $null }

# Number of pixels that differ between two screenshots (5 % colour tolerance); -1 when they cannot be compared
function Get-DiffPixels([string] $First, [string] $Second) {
    if (-not $Magick) { return -1 }
    $result = Invoke-Native $Magick @('compare', '-metric', 'AE', '-fuzz', '5%', $First, $Second, 'null:')
    if ($result.ExitCode -gt 1) { return -1 }
    # "1234", "1.2e+06" or, in newer versions, "1234 (0.0123)"
    $value = 0.0
    $token = ($result.StdErr.Trim() -split '\s+')[0]
    if (-not [double]::TryParse($token, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref] $value)) {
        return -1
    }
    return [long] $value
}

function Get-Colours([string] $Path) {
    if (-not $Magick) { return -1 }
    $result = Invoke-Native $Magick @('identify', '-format', '%k', $Path)
    $value = 0
    if ($result.ExitCode -ne 0 -or -not [int]::TryParse($result.StdOut.Trim(), [ref] $value)) { return -1 }
    return $value
}

function Format-Percent([long] $Count, [long] $Total) {
    if ($Total -le 0) { return '0' }
    return (100.0 * $Count / $Total).ToString('0.00', [Globalization.CultureInfo]::InvariantCulture)
}

# Screenshots of the window every 2 s until two in a row (almost) agree, at most $SettleTimeout s; the last one is
# kept as $Path. Sets Settled.
function Wait-Settled([string] $Path) {
    $script:Settled = $false
    $previous = $null
    $current = $null
    for ($i = 1; $i -le $SettleTimeout / 2; $i++) {
        Start-Sleep -Seconds 2
        $current = Join-Path $Tmp "settle-$i.png"
        if (-not (Save-AppWindow $current)) { return $false }
        if ($previous) {
            $changed = Get-DiffPixels $previous $current
            if ($changed -ge 0 -and $changed -le [long]($script:ShotWidth * $script:ShotHeight * $SettledMax / 10000)) {
                $script:Settled = $true
                break
            }
        }
        $previous = $current
    }
    Copy-Item -LiteralPath $current -Destination $Path -Force
    return $true
}

function Find-AppWindow {
    foreach ($window in [SmokeWin32]::VisibleWindows()) {
        if ([SmokeWin32]::Title($window) -ceq 'Komikku') { return $window }
    }
    return [IntPtr]::Zero
}

function Get-AppWindowCount {
    @([SmokeWin32]::VisibleWindows() | Where-Object { [SmokeWin32]::Title($_) -ceq 'Komikku' }).Count
}

function Save-WindowList([string] $Path) {
    $lines = foreach ($window in [SmokeWin32]::VisibleWindows()) {
        '0x{0:X} pid {1} "{2}"' -f $window.ToInt64(), [SmokeWin32]::ProcessId($window), [SmokeWin32]::Title($window)
    }
    [IO.File]::WriteAllLines($Path, [string[]] @($lines))
}

# Copies the data folder's logs and JVM crash files of the current attempt into <output>\logs\data-<attempt>
function Save-AppLogs {
    if (-not $script:Data -or -not (Test-Path -LiteralPath $script:Data)) { return }
    $destination = Join-Path $Logs "data-$($script:Attempt)"
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    $appLogs = Join-Path $script:Data 'logs'
    if (Test-Path -LiteralPath $appLogs) { Copy-Item -Path (Join-Path $appLogs '*') -Destination $destination -Force }
    Get-ChildItem -LiteralPath $script:Data -Filter 'hs_err_pid*.log' -File | Copy-Item -Destination $destination -Force
    Get-ChildItem -LiteralPath $script:Data -Recurse -Depth 2 -Name | Sort-Object |
        Set-Content -LiteralPath (Join-Path $destination 'data-folder-files.txt') -Encoding utf8
}

function Stop-App {
    if ($null -eq $script:App) { return }
    if (-not $script:App.HasExited) {
        Stop-Process -Id $script:App.Id -Force -ErrorAction SilentlyContinue
        $script:App.WaitForExit(10000) | Out-Null
    }
    $script:App = $null
}

# Starts the launcher on a fresh data folder; sets App, Data, Attempt, StartedAt
function Start-App([int] $Attempt, [string] $RenderApi = '') {
    $script:Attempt = $Attempt
    $script:Data = Join-Path $Work "komikku-smoke-data-$Attempt"
    if ($script:Data.Contains(' ')) { Stop-Smoke 'Data folder' "'$($script:Data)' contains a space, which JAVA_TOOL_OPTIONS cannot carry" }
    Remove-Item -LiteralPath $script:Data -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Path $script:Data | Out-Null
    $script:Stdout = Join-Path $Logs "app-stdout-$Attempt.log"
    $script:Stderr = Join-Path $Logs "app-stderr-$Attempt.log"
    $env:JAVA_TOOL_OPTIONS = "-Dkomikku.dataDir=$($script:Data) -Dkomikku.log.level=debug -Dskiko.hardwareInfo.enabled=true"
    if ($RenderApi) { $env:SKIKO_RENDER_API = $RenderApi } else { Remove-Item Env:SKIKO_RENDER_API -ErrorAction SilentlyContinue }
    try {
        # The working folder is where the JVM writes a crash file (hs_err_pid<pid>.log)
        $script:App = Start-Process -FilePath $Launcher -WorkingDirectory $script:Data -PassThru `
            -RedirectStandardOutput $script:Stdout -RedirectStandardError $script:Stderr
        # Keeps the process handle, without which ExitCode stays empty after the process ended
        $null = $script:App.Handle
    } finally {
        Remove-Item Env:JAVA_TOOL_OPTIONS, Env:SKIKO_RENDER_API -ErrorAction SilentlyContinue
    }
    $script:StartedAt = [DateTime]::UtcNow
    Write-Host "Started $Launcher (pid $($script:App.Id), attempt $Attempt$(if ($RenderApi) { ", SKIKO_RENDER_API=$RenderApi" })), data folder $($script:Data)"
}

function Get-Elapsed([DateTime] $Since) { [int](([DateTime]::UtcNow - $Since).TotalSeconds) }

# Minimizes the window and starts the launcher once more on the same data folder: DesktopSingleInstance must make that
# start ask the running app to show its window and end with exit code 0, without a second window
function Test-SecondInstance {
    if ($script:App.HasExited) { Fail 'Second start on the same data folder' 'the app had already ended'; return }
    [SmokeWin32]::Minimize($script:Window)
    Start-Sleep -Seconds 1
    Info 'Window minimized before the second start' "$([SmokeWin32]::Minimized($script:Window))"
    $env:JAVA_TOOL_OPTIONS = "-Dkomikku.dataDir=$($script:Data) -Dkomikku.log.level=debug"
    try {
        $second = Start-Process -FilePath $Launcher -WorkingDirectory $script:Data -PassThru `
            -RedirectStandardOutput (Join-Path $Logs 'second-start-stdout.log') `
            -RedirectStandardError (Join-Path $Logs 'second-start-stderr.log')
        $null = $second.Handle
    } finally {
        Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
    }
    $startedAt = [DateTime]::UtcNow
    if (-not $second.WaitForExit($SecondTimeout * 1000)) {
        Fail 'Second start on the same data folder ends' "still running after $SecondTimeout s (screenshots\second-start-timeout.png: a busy dialog?)"
        Save-Desktop (Join-Path $Screens 'second-start-timeout.png')
        Save-WindowList (Join-Path $Logs 'windows-second-start.txt')
        Stop-Process -Id $second.Id -Force -ErrorAction SilentlyContinue
        $second.WaitForExit(10000) | Out-Null
        return
    }
    $second.WaitForExit()
    if ($second.ExitCode -eq 0) {
        Pass 'Second start on the same data folder ends with exit code 0' "after $(Get-Elapsed $startedAt) s"
    } else {
        Fail 'Second start on the same data folder ends with exit code 0' "exit code $($second.ExitCode) (logs\second-start-stderr.log)"
    }
    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    while ([SmokeWin32]::Minimized($script:Window) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 500 }
    if ([SmokeWin32]::Minimized($script:Window)) {
        Fail 'Running window shown again' 'still minimized 10 s after the second start ended'
    } else {
        Pass 'Running window shown again' 'no longer minimized'
    }
    Save-Desktop (Join-Path $Screens '07-second-start-screen.png')
    $windows = Get-AppWindowCount
    if ($windows -eq 1) {
        Pass 'No second window'
    } else {
        Fail 'No second window' "$windows visible windows titled Komikku"
        Save-WindowList (Join-Path $Logs 'windows-second-start.txt')
    }
    if ($script:App.HasExited) {
        Fail 'First instance still running after the second start' "exit code $($script:App.ExitCode)"
    }
}

# Waits for a visible top-level window titled exactly "Komikku"; sets Window. 'found', 'timeout' or 'ended'.
function Wait-AppWindow {
    $script:Window = [IntPtr]::Zero
    while ((Get-Elapsed $script:StartedAt) -lt $WindowTimeout) {
        if ($script:App.HasExited) { return 'ended' }
        $window = Find-AppWindow
        if ($window -ne [IntPtr]::Zero) { $script:Window = $window; return 'found' }
        Start-Sleep -Seconds 1
    }
    return 'timeout'
}

# Records why no window appeared and ends that attempt
function Write-NoWindow([string] $Result, [string] $Name) {
    if ($Result -eq 'ended') {
        Fail $Name "the process ended with exit code $($script:App.ExitCode) before a window appeared (logs\app-stderr-$($script:Attempt).log, logs\data-$($script:Attempt))"
    } else {
        Fail $Name "no visible window titled Komikku within $WindowTimeout s (screenshots\no-window-$($script:Attempt).png, logs\windows-$($script:Attempt).txt)"
    }
    Save-Desktop (Join-Path $Screens "no-window-$($script:Attempt).png")
    Save-WindowList (Join-Path $Logs "windows-$($script:Attempt).txt")
    Stop-App
    Save-AppLogs
}

$Launcher = $null
$Msi = $null
$Installed = $false
try {
    Add-Type -TypeDefinition $Win32Source
    # Screen coordinates in physical pixels, before the drawing types read the screen
    [SmokeWin32]::SetProcessDPIAware() | Out-Null
    Add-Type -AssemblyName System.Drawing, System.Windows.Forms

    # --- Runner and package ---------------------------------------------------------------------------------------

    $session = [System.Diagnostics.Process]::GetCurrentProcess().SessionId
    Info 'Runner' "image $($env:ImageOS) $($env:ImageVersion), $([Environment]::OSVersion.VersionString), PowerShell $($PSVersionTable.PSVersion)"
    Info 'Desktop session' "this process in session $session, active console session $([SmokeWin32]::WTSGetActiveConsoleSessionId()), interactive: $([Environment]::UserInteractive)"
    if ($Magick) {
        $magickVersion = ((Invoke-Native $Magick @('-version')).StdOut -split "`n")[0].Trim()
        Info 'ImageMagick' $magickVersion
    } else {
        Fail 'ImageMagick' 'magick is not on the PATH; the screenshots are taken but not compared'
    }

    $sums = @(Get-ChildItem -LiteralPath $Packages -Filter '*-SHA256SUMS.txt' -File)
    $artifactFiles = (Get-ChildItem -LiteralPath $Packages -Name) -join ' '
    if ($sums.Count -ne 1) { Stop-Smoke 'Package checksums' "expected one *-SHA256SUMS.txt in the artifact, found: $artifactFiles" }
    $mismatches = @()
    foreach ($line in Get-Content -LiteralPath $sums[0].FullName) {
        if ($line -notmatch '^([0-9a-fA-F]{64}) [ *](.+)$') { continue }
        $file = Join-Path $Packages $Matches[2]
        $actual = if (Test-Path -LiteralPath $file) { (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash } else { 'missing' }
        if ($actual -ne $Matches[1].ToUpperInvariant()) { $mismatches += $Matches[2] }
    }
    if ($mismatches.Count -gt 0) { Stop-Smoke 'Package checksums' "mismatch or missing: $($mismatches -join ', ')" }
    Pass 'Package checksums' $sums[0].Name
    $msis = @(Get-ChildItem -LiteralPath $Packages -Filter '*.msi' -File)
    if ($msis.Count -ne 1) { Stop-Smoke 'Package' "expected one .msi in the artifact, found: $artifactFiles" }
    $Msi = $msis[0].FullName

    # --- Install --------------------------------------------------------------------------------------------------

    $install = Invoke-Native 'msiexec.exe' @('/i', $Msi, '/qn', '/norestart', '/l*v', (Join-Path $Logs 'install.log'))
    if ($install.ExitCode -notin @(0, 3010)) { Stop-Smoke 'Install (.msi with msiexec, per user)' "exit code $($install.ExitCode) (logs\install.log)" }
    $Installed = $true
    Pass 'Install (.msi with msiexec, per user)' "$($msis[0].Name), exit code $($install.ExitCode)"

    $candidates = [System.Collections.Generic.List[string]]::new()
    # The verbose MSI log may be UTF-16 without a byte order mark, which Select-String cannot read: hence the fallbacks
    $installDir = Select-String -LiteralPath (Join-Path $Logs 'install.log') -Pattern 'Property\(S\): INSTALLDIR = (.+)$' `
        -ErrorAction SilentlyContinue | Select-Object -Last 1
    if ($installDir) { $candidates.Add($installDir.Matches[0].Groups[1].Value.Trim()) }
    foreach ($root in $env:LOCALAPPDATA, (Join-Path $env:LOCALAPPDATA 'Programs'), $env:ProgramFiles) { $candidates.Add((Join-Path $root 'Komikku')) }
    foreach ($candidate in $candidates) {
        $exe = Join-Path $candidate 'Komikku.exe'
        if (Test-Path -LiteralPath $exe) { $Launcher = $exe; break }
    }
    if (-not $Launcher) {
        $found = Get-ChildItem -LiteralPath $env:LOCALAPPDATA, $env:ProgramFiles -Filter 'Komikku.exe' -File -Recurse -Depth 3 `
            -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { $Launcher = $found.FullName }
    }
    if (-not $Launcher) {
        Stop-Smoke 'Launcher installed' "no Komikku.exe in $($candidates -join '; ') (logs\install.log)"
    }
    Pass 'Launcher installed' $Launcher
    Get-ChildItem -LiteralPath (Split-Path -Parent $Launcher) -Recurse -Name |
        Set-Content -LiteralPath (Join-Path $Output 'installed-files.txt') -Encoding utf8
    $shortcutRoots = @(
        (Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs'),
        (Join-Path $env:ProgramData 'Microsoft\Windows\Start Menu\Programs'),
        [Environment]::GetFolderPath('Desktop'),
        (Join-Path $env:PUBLIC 'Desktop')
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }
    $shortcuts = @(Get-ChildItem -LiteralPath $shortcutRoots -Filter 'Komikku*.lnk' -Recurse -File -ErrorAction SilentlyContinue)
    if ($shortcuts.Count -gt 0) {
        Pass 'Start menu / desktop shortcut' (($shortcuts | ForEach-Object FullName) -join '; ')
    } else {
        Fail 'Start menu / desktop shortcut' 'no Komikku*.lnk in the Start menu or on the desktop'
    }

    $defaultData = Join-Path $env:APPDATA 'Komikku'
    $defaultDataExisted = Test-Path -LiteralPath $defaultData

    # --- Screen ---------------------------------------------------------------------------------------------------

    $screen = [System.Windows.Forms.SystemInformation]::VirtualScreen
    if ($screen.Width -lt 1280 -or $screen.Height -lt 800) {
        # Smaller than the app's default window (1280 x 800): try a larger mode, keep going either way
        $setResolution = Get-Command Set-DisplayResolution -ErrorAction SilentlyContinue
        if ($setResolution) {
            try { Set-DisplayResolution -Width 1920 -Height 1080 -Force } catch { Info 'Screen resolution change' $_.Exception.Message }
            $screen = [System.Windows.Forms.SystemInformation]::VirtualScreen
        }
    }
    Info 'Screen' "$($screen.Width)x$($screen.Height) at $($screen.X),$($screen.Y)"

    # --- Start ----------------------------------------------------------------------------------------------------

    Start-App 1
    $result = Wait-AppWindow
    if ($result -eq 'found') {
        Pass 'Window appears (default renderer)' "after $(Get-Elapsed $script:StartedAt) s"
    } else {
        Write-NoWindow $result 'Window appears (default renderer)'
        # Skiko may get no Direct3D / OpenGL device on the runner: one more try with its software renderer
        Start-App 2 'SOFTWARE'
        $result = Wait-AppWindow
        if ($result -ne 'found') {
            Write-NoWindow $result 'Window appears (SKIKO_RENDER_API=SOFTWARE)'
            throw [System.OperationCanceledException]::new('no window')
        }
        Pass 'Window appears (SKIKO_RENDER_API=SOFTWARE)' "after $(Get-Elapsed $script:StartedAt) s"
    }
    $windowPid = [SmokeWin32]::ProcessId($script:Window)
    Info 'Window' ('handle 0x{0:X}, process {1} (launcher {2})' -f $script:Window.ToInt64(), $windowPid, $script:App.Id)
    if ($windowPid -ne $script:App.Id) {
        # The launcher started the JVM in another process: wait for that one at the end
        $script:App = Get-Process -Id $windowPid
        $null = $script:App.Handle
    }

    $bounds = [SmokeWin32]::Bounds($script:Window)
    if ($null -ne $bounds -and $bounds[2] -ge 480 -and $bounds[3] -ge 360) {
        Pass 'Window size' "$($bounds[2])x$($bounds[3]) at $($bounds[0]),$($bounds[1])"
    } else {
        Fail 'Window size' "'$($bounds -join ',')' (at least 480x360 expected)"
    }
    $focus = [SmokeWin32]::Focus($script:Window)
    if ($focus -ne 'failed') {
        Pass 'Window has the keyboard focus' $focus
    } else {
        Fail 'Window has the keyboard focus' ('foreground window 0x{0:X} "{1}"' -f [SmokeWin32]::GetForegroundWindow().ToInt64(), [SmokeWin32]::Title([SmokeWin32]::GetForegroundWindow()))
    }

    # --- Screenshots and tabs -------------------------------------------------------------------------------------

    $startShot = Join-Path $Screens '01-start.png'
    if (Wait-Settled $startShot) {
        Save-Desktop (Join-Path $Screens '01-start-screen.png')
        $colours = Get-Colours $startShot
        if ($colours -ge 32) {
            Pass 'First screenshot is not blank' "$colours colours, settled: $($script:Settled)"
        } else {
            Fail 'First screenshot is not blank' "$colours colours (at least 32 expected), settled: $($script:Settled)"
        }
    } else {
        Fail 'First screenshot' "the window could not be captured: $($script:CaptureError)"
        Save-Desktop (Join-Path $Screens '01-start-screen.png')
    }

    $previous = $startShot
    $number = 2
    foreach ($step in @(@(2, 'updates'), @(3, 'history'), @(4, 'browse'), @(5, 'more'), @(1, 'library'))) {
        $key = $step[0]
        $name = $step[1]
        $shot = Join-Path $Screens ('{0:D2}-ctrl{1}-{2}.png' -f $number, $key, $name)
        $number++
        if ($script:App.HasExited) {
            Fail "Ctrl+$key ($name)" "the process ended (exit code $($script:App.ExitCode)) before the key was sent"
            break
        }
        $focus = [SmokeWin32]::Focus($script:Window)
        if ($focus -eq 'failed') { Info "Focus before Ctrl+$key" 'the window is not in front; the key is sent anyway' }
        [SmokeWin32]::CtrlKey([byte](0x30 + $key))
        if (-not (Wait-Settled $shot)) {
            Fail "Ctrl+$key ($name)" "the window could not be captured: $($script:CaptureError)"
            continue
        }
        $changed = Get-DiffPixels $previous $shot
        $pixels = [long]$script:ShotWidth * $script:ShotHeight
        if ($changed -lt 0) {
            Fail "Ctrl+$key ($name) changes the window" 'the screenshots could not be compared (size changed, or no ImageMagick)'
        } elseif ($changed -ge [long]($pixels * $ChangedMin / 10000)) {
            Pass "Ctrl+$key ($name) changes the window" "$(Format-Percent $changed $pixels) % of the pixels, settled: $($script:Settled)"
        } else {
            Fail "Ctrl+$key ($name) changes the window" "only $(Format-Percent $changed $pixels) % of the pixels, settled: $($script:Settled)"
        }
        $previous = $shot
    }
    if ($previous -ne $startShot -and (Test-Path -LiteralPath $previous) -and (Test-Path -LiteralPath $startShot)) {
        $changed = Get-DiffPixels $startShot $previous
        if ($changed -ge 0) {
            Info 'Library after Ctrl+1 against the first screenshot' "$(Format-Percent $changed ([long]$script:ShotWidth * $script:ShotHeight)) % of the pixels differ"
        }
    }

    # --- Second start ---------------------------------------------------------------------------------------------

    Test-SecondInstance
    Info 'Notification without a tray icon' 'not observed yet: needs the tray icon switched off and a notification-raising action'

    # --- Close ----------------------------------------------------------------------------------------------------

    if (-not $script:App.HasExited) {
        [SmokeWin32]::Close($script:Window) | Out-Null
        $closeAt = [DateTime]::UtcNow
        if ($script:App.WaitForExit($ExitTimeout * 1000)) {
            # The parameterless wait also waits for the redirected output to be complete
            $script:App.WaitForExit()
            Pass 'Process ends after the window is closed' "after $(Get-Elapsed $closeAt) s"
            if ($script:App.ExitCode -eq 0) { Pass 'Exit code 0' } else { Fail 'Exit code 0' "exit code $($script:App.ExitCode)" }
        } else {
            Fail 'Process ends after the window is closed' "still running after $ExitTimeout s; thread dump in logs\jstack.txt"
            Save-Desktop (Join-Path $Screens 'close-timeout.png')
            $jstack = if ($env:JAVA_HOME_21_X64) { Join-Path $env:JAVA_HOME_21_X64 'bin\jstack.exe' } else { $null }
            if ($jstack -and (Test-Path -LiteralPath $jstack)) {
                $dump = Invoke-Native $jstack @('-l', "$($script:App.Id)")
                Set-Content -LiteralPath (Join-Path $Logs 'jstack.txt') -Value ($dump.StdOut + $dump.StdErr) -Encoding utf8
            }
            Stop-App
        }
    } else {
        Fail 'Process ends after the window is closed' "the process had already ended (exit code $($script:App.ExitCode)) before the close request"
    }

    # --- Logs -----------------------------------------------------------------------------------------------------

    Save-AppLogs
    $logFile = Join-Path $script:Data 'logs\komikku.log'
    $logFiles = @((Join-Path $script:Data 'logs\komikku.1.log'), $logFile) | Where-Object { Test-Path -LiteralPath $_ }
    if ((Test-Path -LiteralPath $logFile) -and (Get-Item -LiteralPath $logFile).Length -gt 0) {
        Pass 'Log file written' "<data>\logs\komikku.log, $(@(Get-Content -LiteralPath $logFile).Count) lines"
    } else {
        Fail 'Log file written' 'no <data>\logs\komikku.log'
    }
    if (@($logFiles).Count -gt 0) {
        Write-Host '::group::App log (last 80 lines)'
        Get-Content -LiteralPath $logFile -Tail 80 -ErrorAction SilentlyContinue | Write-Host
        Write-Host '::endgroup::'
        $scanned = @($logFiles) + @($script:Stderr | Where-Object { Test-Path -LiteralPath $_ })
        $uncaught = @(Select-String -LiteralPath $scanned -Pattern 'Uncaught exception|Exception in thread|FATAL' -CaseSensitive)
        if ($uncaught.Count -eq 0) {
            Pass 'Log: no uncaught exception'
        } else {
            Fail 'Log: no uncaught exception' $uncaught[0].Line
        }
        # Error-level lines and stack-trace lines (the log writes a throwable's stack trace under its message)
        $problems = @(Select-String -LiteralPath $logFiles -CaseSensitive `
                -Pattern '^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d\.\d{3} E/', '^\s+at \S', '^Caused by:')
        [IO.File]::WriteAllLines((Join-Path $Output 'log-problems.txt'),
            [string[]] @($problems | ForEach-Object { "$($_.Filename):$($_.LineNumber):$($_.Line)" }))
        if ($problems.Count -eq 0) {
            Pass 'Log: no error or stack trace'
        } else {
            Write-Host '::group::Errors and stack traces in the log (log-problems.txt)'
            $problems | Select-Object -First 60 | ForEach-Object { Write-Host $_.Line }
            Write-Host '::endgroup::'
            Fail 'Log: no error or stack trace' "$($problems.Count) line(s), first: $($problems[0].Line)"
        }
        $tray = @(Select-String -LiteralPath $logFiles -Pattern 'System tray' -CaseSensitive | Select-Object -First 3 | ForEach-Object Line)
        Info 'System tray lines in the log' $(if ($tray.Count -gt 0) { $tray -join ' / ' } else { 'none (no tray error logged)' })
    }
    $crash = @(Get-ChildItem -LiteralPath $script:Data -Filter 'hs_err_pid*.log' -File)
    if ($crash.Count -eq 0) { Pass 'No JVM crash file' } else { Fail 'No JVM crash file' "$($crash[0].Name) (logs\data-$($script:Attempt))" }
    if (-not $defaultDataExisted -and (Test-Path -LiteralPath $defaultData)) {
        Fail 'Default data folder untouched' "$defaultData was created although -Dkomikku.dataDir was set"
    } else {
        Pass 'Default data folder untouched' "$defaultData (existed before: $defaultDataExisted)"
    }
} catch [System.OperationCanceledException] {
    # Stop-Smoke: the failure is recorded already
} catch {
    Fail 'Script error' "$($_.Exception.Message) at $($_.InvocationInfo.PositionMessage -replace '\s+', ' ')"
} finally {
    try {
        Stop-App
        Save-AppLogs
    } catch {
        Fail 'Collect the app logs' $_.Exception.Message
    }
    # --- Uninstall --------------------------------------------------------------------------------------------------
    if ($Installed) {
        try {
            $uninstall = Invoke-Native 'msiexec.exe' @('/x', $Msi, '/qn', '/norestart', '/l*v', (Join-Path $Logs 'uninstall.log'))
            $launcherLeft = [bool]$Launcher -and (Test-Path -LiteralPath $Launcher)
            if (($uninstall.ExitCode -in @(0, 3010)) -and -not $launcherLeft) {
                Pass 'Uninstall (msiexec /x)' 'launcher removed'
            } else {
                Fail 'Uninstall (msiexec /x)' "exit code $($uninstall.ExitCode), launcher still there: $launcherLeft (logs\uninstall.log)"
            }
            if ($script:Data -and (Test-Path -LiteralPath $script:Data)) { Info 'Data folder after uninstall' 'kept' }
        } catch {
            Fail 'Uninstall (msiexec /x)' $_.Exception.Message
        }
    }
    $summary = @('## Desktop smoke test (Windows)', '', '| Result | Check | Detail |', '|--------|-------|--------|') +
        $script:Rows + @('', 'Screenshots and logs: the artifact desktop-smoke-windows-… of this run.')
    Set-Content -LiteralPath (Join-Path $Output 'summary.md') -Value $summary -Encoding utf8
    if ($env:GITHUB_STEP_SUMMARY) { Add-Content -LiteralPath $env:GITHUB_STEP_SUMMARY -Value $summary -Encoding utf8 }
    Remove-Item -LiteralPath $Tmp -Recurse -Force -ErrorAction SilentlyContinue
}
if ($script:Failures -gt 0) {
    Write-Host "$($script:Failures) check(s) failed"
    exit 1
}
exit 0
