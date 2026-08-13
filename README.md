# AI Butler

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

카카오톡·텔레그램을 대신할, **AI 에이전트(Claude Code / Codex / Gemini CLI) 전용 개인 비서 메신저**입니다.
안드로이드 폰에서 앱으로 대화하면, PC에서 돌아가는 브릿지 서버가 실제 CLI 에이전트를 실행해 응답을 스트리밍으로 돌려줍니다.

**셀프호스팅 프로젝트입니다.** 각자 자기 PC에 브릿지 서버를 설치하고, 이미 본인이 로그인해둔 `claude`/`codex`/`gemini`
CLI를 그대로 실행하는 구조라 — 저희(또는 누구든)가 여러분의 AI 요청을 대신 처리하거나 중계하지 않습니다.
Plex, Home Assistant, Tailscale처럼 "내 PC에 서버 + 내 폰에 클라이언트" 조합으로 동작합니다.

**지원 범위**: 서버는 Windows / macOS / Linux 모두 지원합니다. 클라이언트 앱은 현재 **안드로이드만** 제공하며,
iOS는 Apple 개발자 프로그램 비용·심사 등의 진입장벽으로 아직 계획에 없습니다 — 관심 있으신 분의 기여(PR)는 환영합니다.

## 왜 만들었나

- 카카오톡의 AI는 "나와의 채팅"에서만 쓸 수 있고, 텔레그램은 점점 접근성이 떨어짐
- Claude Code / Codex / Gemini CLI는 강력하지만 터미널 전용 — 폰에서 대화하듯 쓸 방법이 마땅치 않음
- 기존 유사 도구(MobileCLI, Termly, ccpocket 등)는 대부분 "터미널 스트리밍"에 초점 — 메신저 UI + 캘린더 + 스킬/스케줄까지 통합된 개인 비서는 없어 직접 제작

## 폴더 구조

```
ai-butler/
├── server/     # 브릿지 서버 (Node.js + TypeScript) — PC에서 상시 구동
├── app/        # 안드로이드 앱 (Kotlin + Jetpack Compose)
└── docs/       # 아키텍처 / 설치 가이드
```

## 빠른 시작

**1. 서버 설치** (PC — Node.js만 미리 설치되어 있으면 됩니다) — 한 줄이면 clone/빌드/자동시작 등록/실행까지 끝나고, 페어링용 QR코드 페이지가 자동으로 뜹니다:

```powershell
# Windows (PowerShell)
irm https://raw.githubusercontent.com/rooseol/ai-butler/master/server/install.ps1 | iex
```
```bash
# macOS / Linux
curl -fsSL https://raw.githubusercontent.com/rooseol/ai-butler/master/server/install.sh | bash
```

Windows에서 PowerShell 명령이 번거로우면, [최신 릴리스](https://github.com/rooseol/ai-butler/releases/latest)에서
`AI-Butler-Setup.exe`를 받아 더블클릭해도 동일하게 동작합니다 (서명된 배포판이 아니라서 첫 실행 시
"Windows에서 PC를 보호했습니다" 경고가 뜨면 **추가 정보 → 실행**을 눌러주세요).

**2. 앱 설치** (폰) — [최신 릴리스](https://github.com/rooseol/ai-butler/releases/latest)에서 `app-release.apk`를 다운로드해 설치 ([docs/SETUP.md](docs/SETUP.md)에 직접 빌드하는 방법도 있음)

**3. 페어링** — 앱 실행 → "QR 스캔으로 연결" → 1번에서 뜬 QR코드를 스캔하면 바로 대화 시작

세부 사항(수동 설치, 문제 해결 등)은 [docs/SETUP.md](docs/SETUP.md) 참고.

## 기능

| 기능 | 설명 |
|---|---|
| 💬 채팅 | Claude Code / Codex / Gemini 탭 전환, 실시간 스트리밍 응답 |
| 🔄 세션 이어가기 | PC에서 작업하던 Claude Code 대화가 폰에서도 자동으로 이어집니다. 출장 중엔 PC의 특정 세션을 골라 폰에서 이어갈 수도 있습니다 |
| 📅 캘린더 | 앱 내장 경량 캘린더 (일정 CRUD) |
| ⚡ 스킬 | 자주 쓰는 프롬프트를 저장해두고 원클릭 실행 |
| ⏰ 스케줄 | cron 표현식으로 스킬 자동 실행 (예: 매일 아침 브리핑) |
| 📎 파일 | 폰 ↔ 서버 파일 업로드/다운로드 |
| 🔔 알림 | 백그라운드에서도 에이전트 응답/스케줄 완료를 로컬 알림으로 수신 |

자세한 구조는 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) 참고.

## 기여

이슈/PR 환영합니다. 특히 macOS/Linux 실기기 검증, iOS 클라이언트, 다른 언어 CLI 에이전트 지원 등은
직접 테스트해볼 환경이 없어 기여가 특히 도움이 됩니다.

## 라이선스

[MIT](LICENSE)
