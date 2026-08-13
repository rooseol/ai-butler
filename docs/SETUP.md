# 설치 & 실행 가이드

## 1. 사전 준비: CLI 에이전트 설치·로그인

브릿지 서버는 PC에 설치된 `claude`, `codex`, `gemini` 명령을 그대로 실행합니다. 미리 설치하고 로그인까지 마쳐두세요.

```bash
# 예시 (버전/명령은 각 도구 문서를 따르세요)
claude --version
codex --version
gemini --version
```

각 명령이 터미널에서 바로 실행되지 않는다면(예: 별도 경로에 설치됨), `server/.env`에서 `CLAUDE_CMD`, `CODEX_CMD`, `GEMINI_CMD`를 전체 경로로 지정하세요.

## 2. 브릿지 서버 실행 (PC)

```bash
cd server
npm install
cp .env.example .env   # 필요시 값 수정 (Windows PowerShell: copy .env.example .env)
npm run dev
```

정상 기동되면 콘솔에 다음이 출력됩니다 (실제 실행/테스트 완료 확인됨):

```
AI Butler 서버가 시작되었습니다
로컬:   http://localhost:8787
Tailscale (외부에서도 접속 가능, 권장): http://100.x.y.z:8787
이더넷: http://<그 외 인터페이스 IP>:8787
페어링 토큰: <랜덤 문자열>
```
및 QR 코드. **이 정보(호스트 IP / 포트 / 토큰)를 앱 페어링에 사용합니다.** Tailscale 주소가 보이면
그걸 쓰세요 — 집 Wi-Fi든 폰 모바일 데이터든 어디서든 그대로 접속됩니다 (4번 항목 참고).

### 2-1. PC 재부팅 후에도 서버가 자동으로 다시 뜨게 하기 (권장)

`npm run dev`는 터미널에 붙어 있는 임시 프로세스라서 **PC를 재부팅하면 서버도 같이 죽고, 아무것도 다시
켜주지 않습니다** — 외부에서 폰으로 접속이 안 될 때 원인의 상당수가 이것입니다(Tailscale 자체는 OS
서비스로 등록되므로 재부팅해도 자동으로 살아납니다). 아래처럼 "로그인 시 자동 시작 + 죽으면 자동 재시작"을
한 번만 등록해두면 해결됩니다. 먼저 공통으로 빌드부터:

```bash
cd server
npm run build   # dist/index.js 생성 (프로덕션 실행용, tsx watch보다 가볍고 안정적)
```

#### Windows

PowerShell(관리자 권한 불필요)에서 아래를 실행하세요. `<프로젝트 경로>`는 실제로 `ai-butler`를 clone한
절대경로로 바꾸세요 (예: `C:\Users\me\ai-butler`):

```powershell
$serverDir = "<프로젝트 경로>\server"
$action = New-ScheduledTaskAction -Execute "wscript.exe" -Argument "`"$serverDir\start-server-hidden.vbs`"" -WorkingDirectory $serverDir
$trigger = New-ScheduledTaskTrigger -AtLogOn -User "$env:USERDOMAIN\$env:USERNAME"
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -DontStopOnIdleEnd -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew
$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName "AIButlerServer" -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Description "AI Butler 브릿지 서버를 로그온 시 자동으로 백그라운드에서 실행"
```

동작 방식: `server/start-server.bat`이 `node dist\index.js`를 실행하고, 프로세스가 어떤 이유로든
종료되면 5초 후 자동 재시작하는 무한 루프를 돕니다. `server/start-server-hidden.vbs`는 이 배치 파일을
콘솔 창 없이(숨김) 실행하는 래퍼로, 작업 스케줄러가 이 파일을 실행합니다. 로그는 `server/logs/server.log`에
계속 쌓입니다.

확인:
```powershell
Get-ScheduledTaskInfo -TaskName "AIButlerServer"   # LastTaskResult가 0이면 정상
curl http://localhost:8787/api/health              # {"ok":true,...} 면 정상
```

#### macOS

`launchd`로 로그인 시 자동 실행되게 등록합니다. `~/Library/LaunchAgents/com.aibutler.server.plist` 파일을
만드세요 (`<프로젝트 경로>`는 실제 절대경로로 교체):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>com.aibutler.server</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/bash</string>
    <string>&lt;프로젝트 경로&gt;/server/start-server.sh</string>
  </array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><false/>
</dict>
</plist>
```

(재시작 루프는 `start-server.sh` 자체가 담당하므로 `KeepAlive`는 꺼둡니다.) 등록/확인:

```bash
launchctl load ~/Library/LaunchAgents/com.aibutler.server.plist
curl http://localhost:8787/api/health
```

#### Linux (systemd, 사용자 단위)

```bash
mkdir -p ~/.config/systemd/user
cat > ~/.config/systemd/user/aibutler-server.service <<'EOF'
[Unit]
Description=AI Butler bridge server

[Service]
ExecStart=/bin/bash <프로젝트 경로>/server/start-server.sh
Restart=no

[Install]
WantedBy=default.target
EOF

systemctl --user daemon-reload
systemctl --user enable --now aibutler-server
# 로그인 없이(무인 재부팅) 켜져 있게 하려면: sudo loginctl enable-linger $USER
```

세 방식 모두 트리거가 **"로그인 시"**입니다. 로그인 없이(원격/무인 상태에서) 재부팅만으로 항상 띄우고
싶다면, Windows는 서비스 방식(nssm 등, 계정 자격 증명 등록 필요), Linux는 위 `enable-linger`, macOS는
로그인 항목 대신 `LaunchDaemon`(root 권한)으로 바꿔야 합니다.

서버를 상시 켜두려면 PC가 절전모드로 빠지지 않도록 설정하세요.

## 3. 안드로이드 앱 빌드 (Android Studio 최초 설치부터)

> ✅ 이 프로젝트는 실제 Android Studio 환경(SDK android-37, JDK 25)에서 커맨드라인 Gradle로 **직접 빌드·실기기 설치·채팅 왕복까지 end-to-end로 검증 완료**되었습니다.
> 사용 버전: **Gradle 9.7.0 / AGP 9.3.1 / Kotlin 2.4.10 / Compose BOM 2026.06.01**, compileSdk·targetSdk 37 (AGP 9.0부터 Kotlin 지원이 내장되어 `org.jetbrains.kotlin.android` 플러그인은 더 이상 쓰지 않음).
> `gradlew`/`gradlew.bat`/`gradle-wrapper.jar`도 이미 프로젝트에 포함되어 있어 별도 wrapper 생성 없이 바로 sync됩니다.

1. [Android Studio](https://developer.android.com/studio) 다운로드 후 설치 (Windows용 설치 프로그램 실행 → 기본 옵션으로 진행하면 Android SDK도 함께 설치됨)
2. Android Studio 실행 → **Open** → clone한 `ai-butler/app` 폴더 선택
3. Gradle Sync가 자동 시작됩니다 (검증된 버전 조합이라 대부분 바로 성공합니다)
4. 실기기 연결: 폰 설정 → 휴대전화 정보 → 빌드 번호 7번 탭(개발자 옵션 활성화) → 개발자 옵션 → USB 디버깅 켜기 → PC와 USB로 연결
   - **삼성 기기는 USB 드라이버가 자동 설치 안 될 수 있습니다** — 장치 관리자에 노란 느낌표(코드 28)가 뜨면 드라이버 업데이트를 자동 검색하거나 [Samsung USB Driver](https://developer.samsung.com/mobile/android-usb-driver.html)를 설치하세요
   - 연결 후 폰 화면에 뜨는 "USB 디버깅 허용" 팝업에서 허용
5. Android Studio 상단 기기 목록에서 내 폰 선택 → ▶ Run 버튼

빌드 에러가 나면 에러 메시지를 그대로 들고 다시 요청해주세요 — 이어서 고쳐드립니다.

## 4. 폰-서버 페어링

PC에 Wi-Fi가 없거나(유선랜만 있는 데스크톱 등) 공인 IP를 직접 받는 환경이라면 "같은 Wi-Fi"라는 전제 자체가
성립하지 않을 수 있습니다 — 이런 경우 **Tailscale**을 쓰는 게 가장 간단하고 안전합니다 (외부/이동통신망에서도 동일하게 동작).

### 0. QR 스캔으로 페어링 (가장 쉬움)

1. PC 브라우저에서 서버 콘솔에 뜨는 주소를 엽니다: `http://<PC 주소>:8787/pair` (Tailscale IP 또는 같은 공유기 IP)
2. 화면에 큰 QR 코드가 뜹니다
3. 폰 앱 첫 화면(페어링 화면)에서 **"QR 스캔으로 연결"** 버튼 → 카메라 권한 허용 → PC 화면의 QR을 비추면 자동으로 연결됩니다
   (host/port/token을 손으로 입력할 필요가 없습니다)

### 방법 A: Tailscale (권장 — 어디서든 접속)

1. PC: [Tailscale](https://tailscale.com/download) (Windows/macOS/Linux 모두 지원) 설치 → 계정으로 로그인
2. 폰: Play 스토어에서 **Tailscale** 설치 → **PC와 동일한 계정**으로 로그인 (VPN 권한 허용)
3. 서버 콘솔/`/pair` 페이지에 뜨는 `Tailscale (외부에서도 접속 가능, 권장): http://100.x.y.z:8787` 의 IP를 그대로 페어링에 사용 (QR 스캔이 가장 편함)
4. 이후 집 Wi-Fi, 회사, 카페, 모바일 데이터 등 어디서 폰을 켜도 동일하게 접속됩니다 — PC의 공인 IP가 바뀌어도 무관합니다

### 방법 B: 같은 공유기 Wi-Fi (PC에 Wi-Fi가 있고, 공유기 뒤에 있는 일반적인 가정환경)

1. 폰과 PC가 **같은 공유기(사설 IP 대역, 보통 `192.168.x.x`/`10.x.x.x`)**에 연결되어 있는지 확인
2. `/pair` 페이지 QR을 스캔하거나, 서버 콘솔에 출력된 `host`, `port`(기본 8787), `token`을 페어링 화면에 직접 입력 → **연결하기**

세 방법 모두 "연결됨"이 표시되면 채팅 탭으로 이동해 아무 메시지나 보내보세요.

> PC의 IP가 `121.x.x.x`처럼 공인 IP로 보인다면(사설 IP가 아님), 공유기 없이 PC가 인터넷에 직접 연결된
> 환경입니다 — 방법 B(같은 Wi-Fi)가 애초에 적용되지 않으니 방법 A(Tailscale)를 쓰세요. 부득이 공인 IP를
> 직접 쓰려면 Windows 방화벽에서 8787 포트 인바운드를 열어야 하며, 인증 토큰에만 의존해 인터넷에 서버를
> 노출하는 것이라 보안상 권장하지 않습니다.

## 5. (선택) 백그라운드 알림 강화 — FCM 설정

기본 상태에서도 설정 화면의 "백그라운드 알림 유지" 토글을 켜면 앱이 백그라운드에 있는 동안 연결을 유지해 알림을 받습니다.
다만 이는 **앱 프로세스가 완전히 종료되면 끊깁니다**. 완전 종료 상태에서도 푸시를 받으려면:

1. [Firebase Console](https://console.firebase.google.com)에서 새 프로젝트 생성
2. 안드로이드 앱 등록 (패키지명: `com.aibutler.app`) → `google-services.json` 다운로드 → `app/app/google-services.json`에 저장
3. 프로젝트 설정 → 서비스 계정 → 새 비공개 키 생성(JSON) → `server/data/firebase-service-account.json`으로 저장 (서버가 자동 감지해 활성화)
4. `app/build.gradle.kts`(루트)와 `app/app/build.gradle.kts`에 `com.google.gms.google-services` 플러그인과 `firebase-messaging` 의존성 추가, `FirebaseMessagingService` 구현 필요 — 이 단계는 아직 코드에 포함되어 있지 않습니다. 필요해지면 요청해주세요.

## 문제 해결

| 증상 | 원인/해결 |
|---|---|
| 앱에서 "연결 실패" | 서버가 켜져 있는지, 같은 Wi-Fi인지, 방화벽이 8787 포트를 막고 있지 않은지 확인 (Windows 방화벽에서 Node.js 인바운드 허용 필요할 수 있음, macOS는 시스템 설정 → 개인정보 보호 및 보안 → 방화벽에서 확인, Linux는 `ufw`/`firewalld` 등에서 8787 허용) |
| 채팅 메시지에 `claude CLI exited with code 1...` | PC에 해당 CLI가 설치/로그인 안 됨, 또는 PATH에 없음 → `.env`에서 전체 경로 지정 |
| Gradle Sync 실패 | Android Studio가 제안하는 버전 업그레이드를 수락하거나, 에러 로그를 공유해주면 버전 조합을 맞춰드립니다 |
| 파일 다운로드가 안 보임 | Android 10+ 는 `다운로드/AIButler` 폴더에 저장됩니다. Android 9 이하는 앱 전용 저장소(`Android/data/com.aibutler.app/files/Download`)에 저장되어 일반 파일 앱에서 안 보일 수 있습니다 |
| 장치 관리자에 노란 느낌표 + "코드 28" | USB 드라이버 미설치. 장치 관리자에서 우클릭 → 드라이버 업데이트 → 자동 검색, 또는 제조사 공식 USB 드라이버 설치 |
| adb에 기기가 안 잡힘 | USB 케이블/포트를 바꿔보고, 폰 알림에서 USB 연결 모드를 "파일 전송"으로 변경, 개발자 옵션의 USB 디버깅이 켜져 있는지 재확인 |
