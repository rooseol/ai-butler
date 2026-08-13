import spawn from "cross-spawn";
import type { AgentName, AgentRunHandle, RunAgentOptions } from "./types.js";

/**
 * 각 CLI를 "1회성 비대화형(print/exec) 모드"로 실행합니다.
 * 세 CLI 모두 이미 PC에 설치 + 로그인(인증) 되어 있다고 가정합니다.
 *
 *  - Claude Code:  claude -p [--resume <sessionId>] "<prompt>" --output-format json
 *                  (JSON 응답의 session_id를 받아 다음 턴에 그대로 넘기면 대화가 이어집니다)
 *  - Codex:        codex exec "<prompt>"
 *  - Gemini CLI:   gemini -p "<prompt>"
 *
 * 실행 파일 경로/커맨드는 .env 의 CLAUDE_CMD / CODEX_CMD / GEMINI_CMD 로 바꿀 수 있습니다.
 * 각 CLI의 실제 플래그가 버전에 따라 다르면 이 파일만 수정하면 됩니다.
 *
 * 세션 이어가기(resume)는 현재 Claude Code만 지원합니다. Codex/Gemini는 매 요청이
 * 독립 실행이며 sessionId를 반환하지 않습니다 — 향후 각 CLI가 유사 기능을 지원하면 확장하세요.
 */

interface ClaudeJsonResult {
  result?: string;
  session_id?: string;
  is_error?: boolean;
}

/**
 * 헤드리스(-p) 모드는 대화형 터미널이 없어서 "이 파일을 수정해도 될까요?" 같은 권한 프롬프트에
 * 답할 방법이 없습니다 — 그대로 두면 도구를 쓰는 요청(파일 수정, 명령 실행 등)이 전부 멈춰버립니다.
 * 이 서버는 페어링 토큰을 가진 본인만 접근 가능한 개인용 브릿지이므로, 기본적으로
 * --dangerously-skip-permissions로 자동 승인합니다. 끄고 싶으면 .env에 AGENT_SKIP_PERMISSIONS=false.
 */
function skipPermissions(): boolean {
  return process.env.AGENT_SKIP_PERMISSIONS !== "false";
}

interface BuiltCommand {
  cmd: string;
  args: string[];
  /** 설정되면 prompt를 커맨드라인 인자 대신 stdin으로 흘려보냅니다. */
  stdinPrompt?: string;
}

function buildCommand(agent: AgentName, prompt: string, options: RunAgentOptions): BuiltCommand {
  switch (agent) {
    case "claude": {
      // 프롬프트를 커맨드라인 인자로 넘기면 Windows에서 개행이 포함된 긴 텍스트가 잘리는 문제가
      // 있었습니다(cmd.exe를 거치는 .cmd shim 특성상 인자 내 줄바꿈을 안전하게 못 넘김).
      // stdin으로 넘기면 이 문제가 없어(직접 확인함) 항상 stdin을 사용합니다.
      const args = ["-p"];
      if (options.sessionId) args.push("--resume", options.sessionId);
      args.push("--output-format", "json");
      if (skipPermissions()) args.push("--dangerously-skip-permissions");
      return { cmd: process.env.CLAUDE_CMD || "claude", args, stdinPrompt: prompt };
    }
    case "codex":
      return {
        cmd: process.env.CODEX_CMD || "codex",
        args: ["exec", prompt],
      };
    case "gemini":
      return {
        cmd: process.env.GEMINI_CMD || "gemini",
        args: ["-p", prompt],
      };
  }
}

function tryParseClaudeJson(raw: string): ClaudeJsonResult | null {
  const trimmed = raw.trim();
  if (!trimmed) return null;
  try {
    return JSON.parse(trimmed) as ClaudeJsonResult;
  } catch {
    return null;
  }
}

/** 이 시간(ms) 안에 끝나지 않으면 프로세스를 강제 종료하고 에러로 처리합니다 (기본 10분). */
function agentTimeoutMs(): number {
  const raw = Number(process.env.AGENT_TIMEOUT_MS);
  return Number.isFinite(raw) && raw > 0 ? raw : 10 * 60_000;
}

export function runAgent(agent: AgentName, prompt: string, options: RunAgentOptions = {}): AgentRunHandle {
  const { cmd, args, stdinPrompt } = buildCommand(agent, prompt, options);
  const cwd = process.env.AGENT_WORKDIR || process.cwd();

  // cross-spawn: Windows에서 .cmd/.bat(npm 전역 설치 CLI들의 실행 shim) 탐색과
  // 인자 전달을 올바르게 처리합니다. Node 기본 spawn을 shell:true로 쓰면 cmd.exe가
  // 프롬프트 문자열을 재해석하면서 "?", "&", "+" 등에서 인자가 잘리는 문제가 있었습니다.
  const child = spawn(cmd, args, { cwd, windowsHide: true });

  if (stdinPrompt !== undefined) {
    child.stdin!.write(stdinPrompt, "utf8");
  }
  child.stdin!.end();

  const chunkCbs: ((c: { text: string }) => void)[] = [];
  const doneCbs: ((full: string, sessionId?: string) => void)[] = [];
  const errorCbs: ((e: Error) => void)[] = [];

  // 안전장치: CLI가 어떤 이유로든(예: stdin이 자식 프로세스까지 제대로 안 닫히는 경우 등)
  // 응답 없이 멈추면, 무한정 기다리지 않고 죽인 뒤 에러로 사용자에게 알립니다.
  // settled로 timeout과 close/error 콜백이 중복 발화하지 않도록 막습니다.
  let settled = false;
  const timeoutMs = agentTimeoutMs();
  const timeout = setTimeout(() => {
    if (settled) return;
    settled = true;
    child.kill();
    const err = new Error(`${agent} 응답이 ${Math.round(timeoutMs / 1000)}초 동안 없어 중단했습니다 (시간 초과).`);
    for (const cb of errorCbs) cb(err);
  }, timeoutMs);
  timeout.unref?.();

  function done(full: string, sessionId?: string) {
    if (settled) return;
    settled = true;
    clearTimeout(timeout);
    for (const cb of doneCbs) cb(full, sessionId);
  }

  function fail(err: Error) {
    if (settled) return;
    settled = true;
    clearTimeout(timeout);
    for (const cb of errorCbs) cb(err);
  }

  // claude --output-format json은 실행 끝에 JSON 한 덩어리만 출력하므로,
  // 중간에 원시 JSON 조각이 "입력 중..." 미리보기로 노출되지 않도록 청크 이벤트를 보내지 않습니다.
  const emitChunks = agent !== "claude";

  let stdoutBuf = "";
  let stderrBuf = "";

  child.stdout!.on("data", (data: Buffer) => {
    const text = data.toString("utf8");
    stdoutBuf += text;
    if (emitChunks) {
      for (const cb of chunkCbs) cb({ text });
    }
  });

  child.stderr!.on("data", (data: Buffer) => {
    stderrBuf += data.toString("utf8");
  });

  child.on("error", (err) => {
    fail(err);
  });

  child.on("close", (code) => {
    if (agent === "claude") {
      const parsed = tryParseClaudeJson(stdoutBuf);
      if (parsed && typeof parsed.result === "string") {
        done(parsed.result, parsed.session_id);
        return;
      }
      // JSON으로 안 나온 경우(예: 예상치 못한 CLI 메시지)라도 stdout에 내용이 있으면
      // 있는 그대로 보여줍니다. stdout도 비어있을 때만 진짜 실행 오류로 취급합니다.
      if (stdoutBuf.trim().length > 0) {
        done(stdoutBuf.trim());
        return;
      }
      fail(new Error(`${agent} CLI exited with code ${code}${stderrBuf ? `: ${stderrBuf.slice(0, 2000)}` : ""}`));
      return;
    }

    if (code !== 0 && stdoutBuf.trim().length === 0) {
      fail(new Error(`${agent} CLI exited with code ${code}${stderrBuf ? `: ${stderrBuf.slice(0, 2000)}` : ""}`));
      return;
    }
    done(stdoutBuf);
  });

  return {
    onChunk: (cb) => chunkCbs.push(cb),
    onDone: (cb) => doneCbs.push(cb),
    onError: (cb) => errorCbs.push(cb),
    cancel: () => {
      settled = true;
      clearTimeout(timeout);
      child.kill();
    },
  };
}
