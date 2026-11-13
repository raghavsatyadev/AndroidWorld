param([string]$out)
# Saves a screenshot of the AndroidWorld window (even when covered) to $out.
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System; using System.Runtime.InteropServices;
public class W { [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint f); }
"@
. "$PSScriptRoot\aw-window.ps1"
$h = Get-AwWindow -NoActivate
if ($h -eq [IntPtr]::Zero) { "no window"; exit 1 }
$r = New-Object AwWin+RECT; [AwWin]::GetWindowRect($h, [ref]$r) | Out-Null
$bmp = New-Object System.Drawing.Bitmap ($r.R-$r.L), ($r.B-$r.T); $g = [System.Drawing.Graphics]::FromImage($bmp)
$dc = $g.GetHdc(); [W]::PrintWindow($h, $dc, 2) | Out-Null; $g.ReleaseHdc($dc); $bmp.Save($out); "saved $($r.R-$r.L)x$($r.B-$r.T)"
