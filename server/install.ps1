#Requires -Version 5.1
<#
  AI Butler 서버 원클릭 설치 스크립트 (Windows)

  실행:
    irm https://raw.githubusercontent.com/rooseol/ai-butler/master/server/install.ps1 | iex

  하는 일: Node.js 확인 → 저장소 clone/업데이트 → npm install/build → .env 생성 →
  "로그온 시 자동 시작" 작업 스케줄러 등록 → 지금 바로 서버 실행 → 페어링 QR 페이지를
  브라우저로 자동으로 엽니다.
#>

param(
    [string]$InstallDir = "$env:USERPROFILE\ai-butler",
    [int]$Port = 8787,
    [string]$TaskName = "AIButlerServer",
    [string]$RepoUrl = "https://github.com/rooseol/ai-butler.git",
    [string]$ZipUrl = "https://github.com/rooseol/ai-butler/archive/refs/heads/master.zip",
    [switch]$SkipAutostart,
    [switch]$SkipOpenBrowser
)

$ErrorActionPreference = "Stop"

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Warn2($msg) { Write-Host "! $msg" -ForegroundColor Yellow }
function Write-Ok($msg) { Write-Host "OK $msg" -ForegroundColor Green }

Write-Host @"
========================================
   AI Butler 서버 설치
========================================
"@ -ForegroundColor Blue

# 1. Node.js 확인
Write-Step "Node.js 확인"
$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) {
    Write-Warn2 "Node.js가 설치되어 있지 않습니다."
    Write-Host "  https://nodejs.org 에서 LTS 버전을 설치한 뒤 이 스크립트를 다시 실행하세요."
    exit 1
}
$nodeVersion = (& node --version).TrimStart("v")
Write-Ok "Node.js $nodeVersion"

# 2. 저장소 받기 (git이 있으면 clone/pull, 없으면 zip 다운로드)
Write-Step "AI Butler 소스 받기 ($InstallDir)"
$git = Get-Command git -ErrorAction SilentlyContinue
if (Test-Path "$InstallDir\.git") {
    Write-Host "  기존 설치 발견 — 최신 버전으로 업데이트합니다"
    Push-Location $InstallDir
    & git pull --ff-only
    Pop-Location
}
elseif ($git) {
    & git clone --depth 1 $RepoUrl $InstallDir
}
else {
    Write-Warn2 "git이 없어 zip으로 받습니다 (업데이트 시 git 설치를 권장)"
    $zipPath = Join-Path $env:TEMP "ai-butler.zip"
    Invoke-WebRequest -Uri $ZipUrl -OutFile $zipPath
    $extractDir = Join-Path $env:TEMP "ai-butler-extract"
    if (Test-Path $extractDir) { Remove-Item $extractDir -Recurse -Force }
    Expand-Archive -Path $zipPath -DestinationPath $extractDir
    $inner = Get-ChildItem $extractDir | Select-Object -First 1
    if (Test-Path $InstallDir) { Remove-Item $InstallDir -Recurse -Force }
    Move-Item $inner.FullName $InstallDir
    Remove-Item $zipPath, $extractDir -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Ok "소스 준비 완료"

$serverDir = Join-Path $InstallDir "server"

# 3. 의존성 설치 + 빌드
Write-Step "의존성 설치 (npm install)"
Push-Location $serverDir
& npm install --no-fund --no-audit
Write-Ok "설치 완료"

Write-Step "빌드 (npm run build)"
& npm run build
Write-Ok "빌드 완료"

# 4. .env 준비
Write-Step ".env 설정"
if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    if ($Port -ne 8787) {
        (Get-Content ".env") -replace "^PORT=.*", "PORT=$Port" | Set-Content ".env"
    }
    Write-Ok ".env 생성 (기본값 사용 — 필요시 $serverDir\.env 를 직접 수정하세요)"
}
else {
    Write-Host "  기존 .env 유지"
}

# 5. 로그온 시 자동 시작 등록
if (-not $SkipAutostart) {
    Write-Step "자동 시작 등록 (작업 스케줄러: $TaskName)"
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
    $action = New-ScheduledTaskAction -Execute "wscript.exe" -Argument "`"$serverDir\start-server-hidden.vbs`"" -WorkingDirectory $serverDir
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User "$env:USERDOMAIN\$env:USERNAME"
    $settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -DontStopOnIdleEnd -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew
    $principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive -RunLevel Limited
    Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Description "AI Butler 브릿지 서버를 로그온 시 자동으로 백그라운드에서 실행" | Out-Null
    Write-Ok "등록 완료 — 다음 로그온부터 자동 실행됩니다"
}
else {
    Write-Warn2 "자동 시작 등록을 건너뜁니다 (-SkipAutostart)"
}

# 6. 지금 바로 서버 실행
Write-Step "서버 시작"
if (-not $SkipAutostart) {
    Start-ScheduledTask -TaskName $TaskName
}
else {
    Start-Process "wscript.exe" -ArgumentList "`"$serverDir\start-server-hidden.vbs`"" -WorkingDirectory $serverDir
}

$healthUrl = "http://localhost:$Port/api/health"
$pairUrl = "http://localhost:$Port/pair"
$ready = $false
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Seconds 1
    try {
        $resp = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2 -ErrorAction Stop
        if ($resp.ok) { $ready = $true; break }
    } catch {}
}
Pop-Location

if (-not $ready) {
    Write-Warn2 "서버가 아직 응답하지 않습니다. $serverDir\logs\server.log 를 확인해보세요."
}
else {
    Write-Ok "서버 실행 중 ($healthUrl)"
}

# 7. CLI 에이전트 설치 여부 확인 (경고만, 설치를 막지는 않음)
Write-Step "AI 에이전트 CLI 확인"
foreach ($cli in @("claude", "codex", "gemini")) {
    if (Get-Command $cli -ErrorAction SilentlyContinue) {
        Write-Ok "$cli 발견됨"
    }
    else {
        Write-Warn2 "$cli 를 찾을 수 없습니다 — 해당 에이전트 탭을 쓰려면 설치 후 로그인하세요"
    }
}

# 8. 페어링 QR 페이지 열기
Write-Step "폰과 페어링"
Write-Host "  1) 폰 앱에서 'QR 스캔으로 연결' 버튼을 누르세요"
Write-Host "  2) 아래 페이지에 뜨는 QR코드를 스캔하면 바로 연결됩니다: $pairUrl"
if (-not $SkipOpenBrowser -and $ready) {
    Start-Process $pairUrl
}

Write-Host "`n설치 완료! 설치 위치: $InstallDir" -ForegroundColor Blue
