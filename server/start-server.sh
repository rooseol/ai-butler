#!/usr/bin/env bash
# Runs the AI Butler server in the foreground and auto-restarts it if it exits.
# Used by the launchd (macOS) / systemd (Linux) service units described in docs/SETUP.md.
# Logs accumulate in logs/server.log (relative to this script's directory).

cd "$(dirname "$0")"
mkdir -p logs

while true; do
  echo "[server starting] $(date)" >> logs/server.log
  node dist/index.js >> logs/server.log 2>&1
  echo "[server exited, restarting in 5s] $(date)" >> logs/server.log
  sleep 5
done
