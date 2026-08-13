#!/usr/bin/env bash
# AI Butler 서버 원클릭 설치 스크립트 (macOS / Linux)
#
# 실행:
#   curl -fsSL https://raw.githubusercontent.com/rooseol/ai-butler/master/server/install.sh | bash
#
# 하는 일: Node.js 확인 → 저장소 clone/업데이트 → npm install/build → .env 생성 →
# 로그인 시 자동 시작 등록(macOS: launchd, Linux: systemd --user) → 지금 바로 서버 실행 →
# 페어링 QR 페이지를 브라우저로 자동으로 엽니다.

set -euo pipefail

INSTALL_DIR="${INSTALL_DIR:-$HOME/ai-butler}"
PORT="${PORT:-8787}"
REPO_URL="${REPO_URL:-https://github.com/rooseol/ai-butler.git}"
TARBALL_URL="${TARBALL_URL:-https://github.com/rooseol/ai-butler/archive/refs/heads/master.tar.gz}"
SKIP_AUTOSTART="${SKIP_AUTOSTART:-}"
SKIP_OPEN_BROWSER="${SKIP_OPEN_BROWSER:-}"

step() { echo -e "\n==> $1"; }
ok()   { echo "OK $1"; }
warn() { echo "! $1"; }

echo "========================================"
echo "   AI Butler 서버 설치"
echo "========================================"

OS="$(uname -s)"

# 1. Node.js 확인
step "Node.js 확인"
if ! command -v node >/dev/null 2>&1; then
  warn "Node.js가 설치되어 있지 않습니다."
  echo "  https://nodejs.org 에서 LTS 버전을 설치한 뒤(또는 nvm/brew 사용) 다시 실행하세요."
  exit 1
fi
ok "Node.js $(node --version)"

# 2. 저장소 받기
step "AI Butler 소스 받기 ($INSTALL_DIR)"
if [ -d "$INSTALL_DIR/.git" ]; then
  echo "  기존 설치 발견 — 최신 버전으로 업데이트합니다"
  git -C "$INSTALL_DIR" pull --ff-only
elif command -v git >/dev/null 2>&1; then
  git clone --depth 1 "$REPO_URL" "$INSTALL_DIR"
else
  warn "git이 없어 tarball로 받습니다 (업데이트 시 git 설치를 권장)"
  tmpTar="$(mktemp -t ai-butler.XXXXXX.tar.gz)"
  curl -fsSL "$TARBALL_URL" -o "$tmpTar"
  tmpDir="$(mktemp -d -t ai-butler-extract.XXXXXX)"
  tar -xzf "$tmpTar" -C "$tmpDir" --strip-components=1
  rm -rf "$INSTALL_DIR"
  mv "$tmpDir" "$INSTALL_DIR"
  rm -f "$tmpTar"
fi
ok "소스 준비 완료"

SERVER_DIR="$INSTALL_DIR/server"
cd "$SERVER_DIR"

# 3. 의존성 설치 + 빌드
step "의존성 설치 (npm install)"
npm install --no-fund --no-audit
ok "설치 완료"

step "빌드 (npm run build)"
npm run build
ok "빌드 완료"

# 4. .env 준비
step ".env 설정"
if [ ! -f ".env" ]; then
  cp ".env.example" ".env"
  if [ "$PORT" != "8787" ]; then
    sed -i.bak "s/^PORT=.*/PORT=$PORT/" ".env" && rm -f ".env.bak"
  fi
  ok ".env 생성 (기본값 사용 — 필요시 $SERVER_DIR/.env 를 직접 수정하세요)"
else
  echo "  기존 .env 유지"
fi

chmod +x start-server.sh

# 5. 로그인 시 자동 시작 등록
if [ -z "$SKIP_AUTOSTART" ]; then
  step "자동 시작 등록"
  if [ "$OS" = "Darwin" ]; then
    PLIST="$HOME/Library/LaunchAgents/com.aibutler.server.plist"
    mkdir -p "$HOME/Library/LaunchAgents"
    cat > "$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>com.aibutler.server</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/bash</string>
    <string>$SERVER_DIR/start-server.sh</string>
  </array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><false/>
</dict>
</plist>
EOF
    launchctl unload "$PLIST" >/dev/null 2>&1 || true
    launchctl load "$PLIST"
    ok "launchd 등록 완료 (com.aibutler.server) — 다음 로그인부터 자동 실행됩니다"
  elif [ "$OS" = "Linux" ]; then
    mkdir -p "$HOME/.config/systemd/user"
    cat > "$HOME/.config/systemd/user/aibutler-server.service" <<EOF
[Unit]
Description=AI Butler bridge server

[Service]
ExecStart=/bin/bash $SERVER_DIR/start-server.sh
Restart=no

[Install]
WantedBy=default.target
EOF
    systemctl --user daemon-reload
    systemctl --user enable --now aibutler-server
    ok "systemd(사용자 단위) 등록 완료 — 다음 로그인부터 자동 실행됩니다"
    echo "  (로그인 없이도 항상 띄우려면: sudo loginctl enable-linger \$USER)"
  else
    warn "알 수 없는 OS($OS) — 자동 시작 등록을 건너뜁니다. server/start-server.sh 를 직접 실행하세요."
    nohup bash "$SERVER_DIR/start-server.sh" > /dev/null 2>&1 &
  fi
else
  warn "자동 시작 등록을 건너뜁니다 (SKIP_AUTOSTART=1)"
  nohup bash "$SERVER_DIR/start-server.sh" > /dev/null 2>&1 &
fi

# 6. 서버 응답 대기
step "서버 확인"
HEALTH_URL="http://localhost:$PORT/api/health"
PAIR_URL="http://localhost:$PORT/pair"
ready=""
for _ in $(seq 1 20); do
  sleep 1
  if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
    ready=1
    break
  fi
done
if [ -n "$ready" ]; then
  ok "서버 실행 중 ($HEALTH_URL)"
else
  warn "서버가 아직 응답하지 않습니다. $SERVER_DIR/logs/server.log 를 확인해보세요."
fi

# 7. AI 에이전트 CLI 확인
step "AI 에이전트 CLI 확인"
for cli in claude codex gemini; do
  if command -v "$cli" >/dev/null 2>&1; then
    ok "$cli 발견됨"
  else
    warn "$cli 를 찾을 수 없습니다 — 해당 에이전트 탭을 쓰려면 설치 후 로그인하세요"
  fi
done

# 8. 페어링 QR 페이지 열기
step "폰과 페어링"
echo "  1) 폰 앱에서 'QR 스캔으로 연결' 버튼을 누르세요"
echo "  2) 아래 페이지에 뜨는 QR코드를 스캔하면 바로 연결됩니다: $PAIR_URL"
if [ -z "$SKIP_OPEN_BROWSER" ] && [ -n "$ready" ]; then
  if [ "$OS" = "Darwin" ]; then
    open "$PAIR_URL" 2>/dev/null || true
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$PAIR_URL" 2>/dev/null || true
  fi
fi

echo -e "\n설치 완료! 설치 위치: $INSTALL_DIR"
