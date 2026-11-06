param([string]$out)
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System; using System.Runtime.InteropServices;
public class W { [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
[DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint f);
[DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
public struct RECT { public int L,T,R,B; } }
"@
[W]::SetProcessDPIAware() | Out-Null
$p = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq 'AndroidWorld' } | Select-Object -First 1
if (-not $p) { "no window"; exit 1 }
$h = $p.MainWindowHandle; $r = New-Object W+RECT; [W]::GetWindowRect($h, [ref]$r) | Out-Null
$bmp = New-Object System.Drawing.Bitmap ($r.R-$r.L), ($r.B-$r.T); $g = [System.Drawing.Graphics]::FromImage($bmp)
$dc = $g.GetHdc(); [W]::PrintWindow($h, $dc, 2) | Out-Null; $g.ReleaseHdc($dc); $bmp.Save($out); "saved $($p.Id) $($r.R-$r.L)x$($r.B-$r.T)"
