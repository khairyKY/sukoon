# Starts Sukoon for Windows now and at every sign-in (a shortcut in your Startup folder), windowless.
# Undo: delete "Sukoon.lnk" from shell:startup.
$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot 'sukoon_desktop.py'
$pythonw = (Get-Command pythonw.exe -ErrorAction SilentlyContinue).Source
if (-not $pythonw) {
    $python = (Get-Command python.exe -ErrorAction Stop).Source
    $pythonw = Join-Path (Split-Path $python) 'pythonw.exe'
}
$link = Join-Path ([Environment]::GetFolderPath('Startup')) 'Sukoon.lnk'
$shell = New-Object -ComObject WScript.Shell
$s = $shell.CreateShortcut($link)
$s.TargetPath = $pythonw
$s.Arguments = "`"$script`""
$s.WorkingDirectory = $PSScriptRoot
$s.Description = 'Sukoon for Windows: glucose and alarms'
$s.Save()
Start-Process $pythonw -ArgumentList "`"$script`"" -WorkingDirectory $PSScriptRoot
Write-Host "Sukoon starts at sign-in ($link). It's starting now: sign in with your Sukoon account."
