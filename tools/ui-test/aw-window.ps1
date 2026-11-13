# Dot-source this: `. "$PSScriptRoot\aw-window.ps1"`, then call Get-AwWindow.
# Returns the handle of the main AndroidWorld window (the largest visible top-level window titled
# AndroidWorld), or IntPtr.Zero. Process.MainWindowHandle is not used: Compose popups and tooltips
# are top-level windows too, and Windows can report one of them as the process's main window.
Add-Type @"
using System; using System.Text; using System.Runtime.InteropServices;
public class AwWin {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc f, IntPtr l);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
  public struct RECT { public int L,T,R,B; }
  public static IntPtr Find(string title) {
    IntPtr best = IntPtr.Zero; long bestArea = 0;
    EnumWindows((h, l) => {
      if (!IsWindowVisible(h)) return true;
      var sb = new StringBuilder(256); GetWindowText(h, sb, 256);
      if (sb.ToString() != title) return true;
      RECT r; GetWindowRect(h, out r); long a = (long)(r.R - r.L) * (r.B - r.T);
      if (a > bestArea) { bestArea = a; best = h; }
      return true; }, IntPtr.Zero);
    return best;
  }
}
"@
[AwWin]::SetProcessDPIAware() | Out-Null

# Un-minimizes the window if needed. -NoActivate (cap.ps1) restores it without taking focus.
function Get-AwWindow([switch]$NoActivate) {
    $h = [AwWin]::Find('AndroidWorld')
    if ($h -ne [IntPtr]::Zero -and [AwWin]::IsIconic($h)) {
        [AwWin]::ShowWindow($h, $(if ($NoActivate) { 4 } else { 9 })) | Out-Null
        Start-Sleep -Milliseconds 500
    }
    $h
}
