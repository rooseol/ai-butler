@echo off
REM Runs the AI Butler server in the background and auto-restarts it if it exits.
REM Launched hidden (no window) by start-server-hidden.vbs, which is run by the
REM "AIButlerServer" Windows Scheduled Task (trigger: at logon).
REM Logs accumulate in logs\server.log.

cd /d "%~dp0"
if not exist logs mkdir logs

:loop
echo [server starting] >> logs\server.log
node dist\index.js >> logs\server.log 2>>&1
echo [server exited, restarting in 5s] >> logs\server.log
timeout /t 5 /nobreak >nul
goto loop
