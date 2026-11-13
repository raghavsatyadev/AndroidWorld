param([Parameter(Mandatory = $true)][string]$text, [switch]$enter)
# Types $text into the AndroidWorld window with SendKeys, but only when that window is in the
# foreground. Exits 1 and types nothing otherwise, so keystrokes never land in another app.
Add-Type -AssemblyName System.Windows.Forms
. "$PSScriptRoot\aw-window.ps1"
$h = Get-AwWindow
if ($h -eq [IntPtr]::Zero) { "no window"; exit 1 }
[AwWin]::SetForegroundWindow($h) | Out-Null; Start-Sleep -Milliseconds 300
if ([AwWin]::GetForegroundWindow() -ne $h) { "AndroidWorld is not in the foreground; nothing typed"; exit 1 }
# Escape SendKeys metacharacters so the text is typed literally.
$escaped = [regex]::Replace($text, '[+^%~(){}\[\]]', { param($m) "{$($m.Value)}" })
[System.Windows.Forms.SendKeys]::SendWait($escaped)
if ($enter) {
    if ([AwWin]::GetForegroundWindow() -ne $h) { "focus moved away; Enter not sent"; exit 1 }
    [System.Windows.Forms.SendKeys]::SendWait('{ENTER}')
}
"typed $($text.Length) chars"
