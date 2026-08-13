# 아키텍처

```
┌─────────────────────┐        HTTP/WebSocket        ┌──────────────────────────┐
│   Android 앱          │  ────────────────────────▶  │   브릿지 서버 (PC)          │
│  Kotlin + Compose     │  ◀────────────────────────  │  Node.js + TypeScript     │
└─────────────────────┘   (Tailscale VPN 또는 같은 LAN)   └──────────┬───────────────┘
                                                                   │ child_process.spawn
                                                        ┌──────────┼───────────┐
                                                        ▼          ▼           ▼
                                                    claude -p   codex exec   gemini -p
                                                   (Claude Code)  (Codex)    (Gemini CLI)
```

## 왜 이렇게 나눴나

Claude Code / Codex / Gemini CLI는 모두 **로컬에 설치되고 로그인된 CLI 도구**입니다. 안드로이드에서 직접 실행할 수 없으므로,
CLI가 이미 설치·인증되어 있는 PC에서 **브릿지 서버**를 상시 구동하고, 폰 앱은 그 서버와 통신만 합니다.

- 서버가 실제 프로젝트 파일에 접근하고 CLI를 실행 (코드 실행/파일시스템 접근은 항상 PC에서만 발생)
- 폰은 대화창 역할만 — 텍스트를 보내고, 스트리밍 응답을 받고, 파일을 주고받음

## 세션 이어가기 (PC ↔ 폰 연속 작업)

`claude -p --resume <session-id> ... --output-format json`을 활용해, PC 터미널/데스크톱 앱에서 하던
Claude Code 대화를 폰에서 그대로 이어갈 수 있습니다.

- **자동 이어짐**: 폰에서 채팅 탭 하나(예: Claude Code)로 대화를 시작하면, 첫 응답에서 받은 `session_id`를
  서버가 `active_sessions` 테이블에 저장해두고, 그 뒤로는 매 메시지마다 자동으로 `--resume`해서 같은 맥락을 유지합니다.
- **PC 세션 선택 + 실제 대화 불러오기**: 채팅 화면 상단의 🕐 아이콘 → `GET /api/sessions?agent=claude`가
  `~/.claude/projects/<프로젝트>/*.jsonl`을 스캔해 PC에서 진행한 최근 대화 목록을 보여줍니다. 목록의 제목은
  Claude Code CLI가 세션마다 자동으로 남기는 `{"type":"ai-title","aiTitle":"..."}` 항목을 그대로 읽은 것이라
  **Claude Code 데스크톱 앱 사이드바에 뜨는 이름과 동일**합니다. 하나를 고르면 `POST /api/sessions/select`로
  그 세션이 "지금 이어가는 세션"이 되고, `GET /api/sessions/:id/transcript`가 그 세션의 실제 과거 대화(텍스트만,
  최근 최대 60턴)를 읽어와 폰 화면에 그대로 표시합니다. 사무실에서 작업하다 외출해 폰으로 이어가는 시나리오에
  맞춘 기능입니다.
- **세션 삭제**: 목록의 각 세션 옆 🗑 버튼으로 로컬 `.jsonl` 파일을 직접 삭제할 수 있습니다(`DELETE /api/sessions/:id`).
  Claude Code 데스크톱 앱의 "삭제" 기능은 로컬 원본 파일을 지우지 않는 것으로 확인되어(클라우드/UI 목록에서만
  제거하는 것으로 추정), 로컬 파일 정리가 필요하면 이 기능을 사용하세요. 삭제는 되돌릴 수 없어 확인 다이얼로그를 거칩니다.
- **범위**: 현재 Claude Code CLI만 지원합니다 (Codex/Gemini는 세션 이어가기 기능 자체가 아직 없어 제외).
- **주의**: 불러온 과거 대화는 화면 표시용입니다 — 실제로 서버 `messages` 테이블에 저장되는 건 그 이후
  폰에서 새로 주고받는 메시지부터입니다. 또한 표시되는 내용은 사람이 읽을 텍스트(사용자 입력, 에이전트 답변)만
  추린 것이며, 도구 호출/실행 결과 같은 세부 과정은 가독성을 위해 생략됩니다.
- **구현 시 주의(실기기 테스트로 발견한 함정)**:
  - 세션 제목을 별도 LLM 호출로 생성하는 방식은 `--no-session-persistence`를 줘도 실제로는 새 세션 파일을
    만들어 세션 목록을 오염시켰습니다 — 그래서 자체 생성 대신 CLI가 이미 만들어두는 `ai-title`을 읽는 방식으로 대체했습니다.
  - 안드로이드 `ApiClient`에서 응답 바디를 읽는 코드가 메인 스레드로 돌아와 있으면(코루틴 `withContext` 경계 문제)
    응답이 어느 정도 큰 요청(예: transcript)에서만 `NetworkOnMainThreadException`이 터집니다 — 작은 응답에서는
    우연히 안 걸릴 수 있어 발견이 늦었습니다. 요청~바디 읽기~JSON 파싱을 통째로 `withContext(Dispatchers.IO)`로
    감싸야 합니다.
  - **채팅 첨부파일**(`fileId`)은 서버 DB에는 저장되지만, CLI에 넘기는 프롬프트에 파일 경로를 직접 덧붙여주지
    않으면 에이전트가 "첨부파일이 안 보인다"고 답합니다 — `ws/index.ts`의 `buildPromptWithAttachment`가
    `[첨부파일] 전체 경로: ...` 형태로 프롬프트에 파일 경로를 추가해 Read 도구로 열어보게 합니다.
  - **개행이 포함된 프롬프트가 명령줄 인자로 전달되면 Windows에서 잘립니다** (macOS/Linux에서는 이 문제가
    없습니다 — cmd.exe를 거치지 않기 때문). cross-spawn이 `.cmd` shim을 cmd.exe 경유로 실행하는데, cmd.exe는
    인자 내 리터럴 줄바꿈을 안전하게 못 넘깁니다 — 위 첨부파일 경로 안내처럼 여러 줄짜리 텍스트를 인자로
    넘기면 첫 줄 이후가 통째로 사라집니다. **해결: claude 프롬프트는 커맨드라인 인자 대신 자식 프로세스의
    stdin으로 흘려보냅니다** (`runAgent.ts`, `stdinPrompt`) — OS/개행 유무와 무관하게 항상 안전해서, 모든
    플랫폼에서 동일하게 이 방식을 씁니다.
  - **(Windows 전용) 위 stdin 방식으로 바꾸자 이번엔 `claude.cmd`(셸 배치 래퍼)를 통해 실행할 때 요청이 응답
    없이 멈추는(hang) 새 문제가 생겼습니다.** cross-spawn이 `.cmd`를 실행하려고 `cmd.exe /d /s /c "..."`로
    한 번 더 감싸는데, 이 추가 프로세스 계층을 거치면서 우리가 보낸 stdin의 EOF(입력 종료 신호)가 실제
    `claude.exe`까지 제대로 전달되지 않아 CLI가 입력을 계속 기다리며 멈췄습니다. **해결: Windows에서는 `.env`의
    `CLAUDE_CMD`를 `claude.cmd`가 아니라 그 안에서 실제로 실행하는 `claude.exe`의 전체 경로로 직접
    지정합니다** — cmd.exe를 아예 거치지 않으므로 stdin이 바로 전달됩니다 (`.env.example`에 찾는 방법 기재).
    macOS/Linux는 `claude`가 셸 스크립트/심볼릭 링크로 직접 실행되어 이런 중간 래퍼 계층이 없으므로, 기본값
    `CLAUDE_CMD=claude`만으로 이 문제 자체가 발생하지 않습니다.
  - **안전장치**: 위 문제들처럼 CLI가 어떤 이유로든 응답 없이 멈추는 경우에 대비해, `runAgent.ts`에 타임아웃
    (`AGENT_TIMEOUT_MS`, 기본 10분)을 추가했습니다. 시간 안에 안 끝나면 프로세스를 강제 종료하고 에러로
    알려서, 사용자가 무한정 기다리는 상황을 방지합니다.
  - **PC에서 대화형으로 시작된 세션은 헤드리스로 `--resume` + `--dangerously-skip-permissions`를 줘도 파일
    수정/명령 실행 권한 승인을 계속 요구할 수 있습니다** (`Claude requested permissions to write to ..., but you
    haven't granted it yet.`). 세션이 처음 인터랙티브(Desktop 앱 등)로 생성되면서 확립된 승인 요구사항이,
    이후 헤드리스로 재개할 때도 유지되는 것으로 보입니다(추정 — 승인된 세션을 몰래 헤드리스로 가로채 권한을
    우회하지 못하도록 하는 의도적 안전장치일 가능성). 반면 **서버가 처음부터 새로 만든 세션**(PC 세션을 이어가지
    않고 "새 대화 시작"으로 만든 세션)은 처음부터 `bypassPermissions`로 생성되어 도구 사용이 정상 동작합니다.
    → **"PC 세션 이어가기"는 대화 맥락 유지에는 쓰고, 실제 파일 수정/명령 실행이 필요한 작업은 새 대화로
    시작하는 걸 권장합니다.**
  - **`npm run dev`는 터미널에 붙어 있는 임시 프로세스라 PC가 재부팅되면 서버도 같이 죽고, 다시 켜주는
    주체가 없어 폰에서 영영 연결이 안 됩니다** (Tailscale은 OS 서비스로 등록되어 재부팅해도 자체적으로
    살아나므로, 증상만 보면 "네트워킹이 안 된다"처럼 보이지만 실제로는 서버 프로세스 자체가 없는 것입니다).
    **해결**: `server/start-server.bat`(Windows)/`start-server.sh`(macOS/Linux)가 서버를 실행하고 죽으면 5초
    후 스스로 재시작하는 무한 루프를 돌며, 이걸 각 OS의 "로그인 시 자동 시작" 메커니즘(Windows 작업
    스케줄러 / macOS `launchd` / Linux `systemd --user`)에 등록해 로그인할 때마다 백그라운드로 자동
    기동되게 했습니다 (`docs/SETUP.md` 2-2 참고 — `server/install.ps1`/`install.sh`를 쓰면 자동으로 등록됨).
    세 방식 모두 트리거가 "로그인 시"를 전제로 하므로, 로그인
    없이(무인 상태) 재부팅만으로 뜨게 하려면 Windows는 서비스화(nssm 등, 계정 자격 증명 등록 필요), Linux는
    `loginctl enable-linger`, macOS는 `LaunchDaemon`(root 권한)으로 별도 전환이 필요합니다.

## 서버 (`server/`)

- **DB**: Node.js 내장 `node:sqlite` (better-sqlite3 같은 네이티브 빌드 도구 불필요)
- **REST API**: `/api/health`(무인증), `/api/messages`, `/api/calendar/events`, `/api/skills`, `/api/schedules`, `/api/files`, `/api/devices/register`
- **WebSocket** (`/ws?token=...`): 실시간 채팅. 클라이언트가 `{type:"chat", agent, text, fileId?}`를 보내면
  서버가 `claude -p` / `codex exec` / `gemini -p`를 실행하고 `chat_saved` → `chat_chunk`(스트리밍) → `chat_done`(완료) 순으로 이벤트를 브로드캐스트
- **스케줄러**: `node-cron`이 DB의 `schedules` 테이블을 60초마다 재로드. cron 시각이 되면 스킬(저장된 프롬프트)을 자동 실행하고 결과를 채팅 메시지로 저장 + 브로드캐스트

## 앱 (`app/`)

- Jetpack Compose 단일 화면 구성: 페어링 → (채팅 / 캘린더 / 스킬 / 파일 / 설정) 5탭
- `network/ApiClient.kt`: REST 래퍼, `network/WsClient.kt`: 자동 재연결되는 WebSocket 클라이언트
- 서버 연결 정보(host/port/token)는 DataStore Preferences에 저장 — 앱 재실행해도 재페어링 불필요
- 오프라인 캐시(Room 등)는 아직 없음 — 항상 서버에서 최신 데이터를 조회

## 네트워킹 / 보안 모델

- 서버 최초 실행 시 랜덤 토큰이 발급되어 모든 `/api/*` 요청과 WS 연결에 `Authorization: Bearer <token>` 필요
- **Tailscale을 기본 연결 방식으로 채택**: PC가 Wi-Fi 없이 유선랜으로 공인 IP를 직접 받는 환경(가정용 공유기 없는
  데스크톱 등)에서는 "같은 Wi-Fi"라는 전제 자체가 성립하지 않습니다. `server/src/auth/pairing.ts`의
  `listCandidateAddresses()`가 인터페이스 이름/IP 대역(100.64.0.0/10)으로 Tailscale 인터페이스를 감지해
  콘솔 안내와 페어링 QR에서 **항상 Tailscale 주소를 최우선으로** 보여줍니다. 폰이 어느 네트워크에 있든
  (집 Wi-Fi/모바일 데이터/카페 Wi-Fi) 동일한 Tailscale IP로 접속되고, PC의 공인 IP가 바뀌어도 영향 없습니다.
- 그 외 일반적인 "공유기 뒤 가정환경"이라면 같은 사설 IP 대역(LAN 평문 HTTP/WS 허용, `network_security_config.xml` 참고)으로도 동작합니다.
- **공인 IP를 직접 쓰는 건 권장하지 않습니다**: 방화벽으로 포트를 열어야 하고, 토큰 인증에만 의존해 서버를
  인터넷에 그대로 노출하는 셈이라 공격 표면이 커집니다. Tailscale이 이미 이 문제를 해결해주므로 별도
  리버스 프록시/포트포워딩은 보통 필요 없습니다.
- 페어링 토큰이 유출되면 `server/data/pairing.json`을 삭제하고 서버를 재시작하면 새 토큰이 발급됩니다

## 향후 확장 아이디어

- Room 기반 오프라인 캐시로 메시지/캘린더 로컬 저장
- FCM 연동으로 앱이 완전히 종료된 상태에서도 진짜 푸시 알림 수신 (`docs/SETUP.md`의 FCM 섹션 참고)
- Google Calendar 양방향 동기화 (현재는 서버 내장 SQLite 캘린더만 지원)
- 스트리밍을 진짜 토큰 단위로 받도록 각 CLI의 JSON 스트리밍 출력 모드 활용 (`claude --output-format stream-json` 등)
