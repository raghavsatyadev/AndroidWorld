param([int]$x,[int]$y)
# Brings AndroidWorld to the front and left-clicks at ($x, $y), relative to its window's top-left.
Add-Type @"
using System; using System.Runtime.InteropServices;
public class C { [DllImport("user32.dll")] public static extern bool SetCursorPos(int x,int y);
[DllImport("user32.dll")] public static extern void mouse_event(uint f,uint x,uint y,uint d,IntPtr e); }
"@
. "$PSScriptRoot\aw-window.ps1"
$h = Get-AwWindow
if ($h -eq [IntPtr]::Zero) { "no window"; exit 1 }
[AwWin]::SetForegroundWindow($h) | Out-Null; Start-Sleep -Milliseconds 300
if ([AwWin]::GetForegroundWindow() -ne $h) { "AndroidWorld is not in the foreground; not clicking"; exit 1 }
$r = New-Object AwWin+RECT; [AwWin]::GetWindowRect($h,[ref]$r) | Out-Null
[C]::SetCursorPos($r.L+$x,$r.T+$y) | Out-Null; Start-Sleep -Milliseconds 100
[C]::mouse_event(2,0,0,0,[IntPtr]::Zero); [C]::mouse_event(4,0,0,0,[IntPtr]::Zero); Start-Sleep -Milliseconds 800
