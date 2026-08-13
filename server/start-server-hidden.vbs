' Runs start-server.bat with no visible console window (0 = hidden).
' Invoked by the "AIButlerServer" Windows Scheduled Task (trigger: at logon).
Set fso = CreateObject("Scripting.FileSystemObject")
Set WshShell = CreateObject("WScript.Shell")
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
WshShell.Run """" & scriptDir & "\start-server.bat""", 0, False
