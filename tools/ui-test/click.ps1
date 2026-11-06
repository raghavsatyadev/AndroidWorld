param([int]$x,[int]$y)
Add-Type @"
using System; using System.Runtime.InteropServices;
public class C { [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
[DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
[DllImport("user32.dll")] public static extern bool SetCursorPos(int x,int y);
[DllImport("user32.dll")] public static extern void mouse_event(uint f,uint x,uint y,uint d,IntPtr e);
public struct RECT { public int L,T,R,B; } }
"@
[C]::SetProcessDPIAware() | Out-Null
$p = Get-Process java | Where-Object { $_.MainWindowTitle -eq 'AndroidWorld' } | Select-Object -First 1
$h=$p.MainWindowHandle; [C]::SetForegroundWindow($h) | Out-Null; Start-Sleep -Milliseconds 300
$r = New-Object C+RECT; [C]::GetWindowRect($h,[ref]$r) | Out-Null
[C]::SetCursorPos($r.L+$x,$r.T+$y) | Out-Null; Start-Sleep -Milliseconds 100
[C]::mouse_event(2,0,0,0,[IntPtr]::Zero); [C]::mouse_event(4,0,0,0,[IntPtr]::Zero); Start-Sleep -Milliseconds 800
