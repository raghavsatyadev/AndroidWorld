param([Parameter(Mandatory = $true)][string]$text, [switch]$enter)
# Types $text into the AndroidWorld window with SendKeys, but only when that window is in the
# foreground. Exits 1 and types nothing otherwise, so keystrokes never land in another app.
Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System; using System.Runtime.InteropServices;
public class T { [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h); }
"@
$p = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq 'AndroidWorld' } | Select-Object -First 1
if (-not $p) { "no window"; exit 1 }
$h = $p.MainWindowHandle
[T]::SetForegroundWindow($h) | Out-Null; Start-Sleep -Milliseconds 300
if ([T]::GetForegroundWindow() -ne $h) { "AndroidWorld is not in the foreground; nothing typed"; exit 1 }
# Escape SendKeys metacharacters so the text is typed literally.
$escaped = [regex]::Replace($text, '[+^%~(){}\[\]]', { param($m) "{$($m.Value)}" })
[System.Windows.Forms.SendKeys]::SendWait($escaped)
if ($enter) {
    if ([T]::GetForegroundWindow() -ne $h) { "focus moved away; Enter not sent"; exit 1 }
    [System.Windows.Forms.SendKeys]::SendWait('{ENTER}')
}
"typed $($text.Length) chars"
