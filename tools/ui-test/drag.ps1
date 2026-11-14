param([int]$x1,[int]$y1,[int]$x2,[int]$y2,[int]$steps=20)
# Brings AndroidWorld to the front and drags with the left button from ($x1,$y1) to ($x2,$y2),
# relative to its window's top-left, in $steps moves ~16 ms apart.
Add-Type @"
using System; using System.Runtime.InteropServices;
public class D { [DllImport("user32.dll")] public static extern bool SetCursorPos(int x,int y);
[DllImport("user32.dll")] public static extern void mouse_event(uint f,uint x,uint y,uint d,IntPtr e); }
"@
. "$PSScriptRoot\aw-window.ps1"
$h = Get-AwWindow
if ($h -eq [IntPtr]::Zero) { "no window"; exit 1 }
[AwWin]::SetForegroundWindow($h) | Out-Null; Start-Sleep -Milliseconds 300
if ([AwWin]::GetForegroundWindow() -ne $h) { "AndroidWorld is not in the foreground; not dragging"; exit 1 }
$r = New-Object AwWin+RECT; [AwWin]::GetWindowRect($h,[ref]$r) | Out-Null
[D]::SetCursorPos($r.L+$x1,$r.T+$y1) | Out-Null; Start-Sleep -Milliseconds 100
[D]::mouse_event(2,0,0,0,[IntPtr]::Zero); Start-Sleep -Milliseconds 30
for ($i = 1; $i -le $steps; $i++) {
    $x = [int]($x1 + ($x2 - $x1) * $i / $steps); $y = [int]($y1 + ($y2 - $y1) * $i / $steps)
    [D]::SetCursorPos($r.L+$x,$r.T+$y) | Out-Null; Start-Sleep -Milliseconds 16
}
[D]::mouse_event(4,0,0,0,[IntPtr]::Zero); Start-Sleep -Milliseconds 800
