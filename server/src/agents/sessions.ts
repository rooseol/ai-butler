import fs from "node:fs";
import path from "node:path";
import os from "node:os";

/**
 * Claude Code CLI가 로컬에 남기는 세션 기록(~/.claude/projects/<project>/<sessionId>.jsonl)을
 * 스캔해 "PC에서 하던 대화 목록"을 만들고, 특정 세션의 과거 대화를 읽어옵니다.
 * 이 목록에서 고른 세션 ID를 `claude -p --resume <id>`로 그대로 이어갈 수 있습니다.
 *
 * 제목은 Claude Code CLI가 세션마다 자동으로 남기는 `{"type":"ai-title","aiTitle":"..."}` 항목을
 * 그대로 읽어옵니다 — Claude Code 데스크톱 앱 사이드바에 뜨는 것과 동일한 제목입니다.
 * (처음에는 직접 LLM 호출로 제목을 생성해봤으나, 그 호출 자체가 --no-session-persistence를
 *  줘도 새 세션 파일을 만들어버려 세션 목록을 오염시키는 문제가 있어 이 방식으로 대체했습니다.)
 */

const CLAUDE_HOME = process.env.CLAUDE_HOME || path.join(os.homedir(), ".claude");
const PROJECTS_DIR = path.join(CLAUDE_HOME, "projects");

export interface SessionSummary {
  id: string;
  cwd: string;
  title: string;
  preview: string;
  updatedAt: number;
  approxMessageCount: number;
}

export interface TranscriptEntry {
  role: "user" | "agent";
  content: string;
  timestamp: number;
}

function readHeadChunk(filePath: string, maxBytes = 131072): string {
  const fd = fs.openSync(filePath, "r");
  try {
    const buf = Buffer.alloc(maxBytes);
    const bytesRead = fs.readSync(fd, buf, 0, maxBytes, 0);
    return buf.toString("utf8", 0, bytesRead);
  } finally {
    fs.closeSync(fd);
  }
}

/** content 필드(문자열 또는 블록 배열)에서 사람이 읽을 텍스트만 뽑아냅니다. tool_use/tool_result 등은 제외. */
function extractDisplayText(content: unknown): string {
  if (typeof content === "string") return content;
  if (Array.isArray(content)) {
    const parts: string[] = [];
    for (const block of content as any[]) {
      if (block && typeof block === "object" && block.type === "text" && typeof block.text === "string") {
        parts.push(block.text);
      }
    }
    return parts.join("\n").trim();
  }
  return "";
}

function inspectSessionFile(
  filePath: string
): { cwd: string | null; title: string | null; preview: string; approxMessageCount: number } {
  const head = readHeadChunk(filePath);
  const lines = head.split("\n").filter(Boolean);
  let cwd: string | null = null;
  let title: string | null = null;
  let preview = "";
  let approxMessageCount = 0;

  for (const line of lines) {
    let obj: any;
    try {
      obj = JSON.parse(line);
    } catch {
      continue; // 청크 경계에서 마지막 줄이 잘렸을 수 있음
    }
    if (!cwd && typeof obj.cwd === "string") cwd = obj.cwd;
    if (!title && obj.type === "ai-title" && typeof obj.aiTitle === "string") title = obj.aiTitle;
    if (obj.type === "user" || obj.type === "assistant") {
      approxMessageCount++;
      if (!preview && obj.type === "user") {
        const text = extractDisplayText(obj.message?.content);
        if (text) preview = text;
      }
    }
  }

  return { cwd, title, preview: preview.slice(0, 200), approxMessageCount };
}

function findSessionFilePath(sessionId: string): string | null {
  if (!fs.existsSync(PROJECTS_DIR)) return null;
  for (const projectDir of fs.readdirSync(PROJECTS_DIR)) {
    const candidate = path.join(PROJECTS_DIR, projectDir, `${sessionId}.jsonl`);
    if (fs.existsSync(candidate)) return candidate;
  }
  return null;
}

/** workdir(현재 AGENT_WORKDIR)과 cwd가 일치하는 세션만 골라 최신순으로 반환합니다. */
export function listClaudeSessions(workdir: string): SessionSummary[] {
  if (!fs.existsSync(PROJECTS_DIR)) return [];
  const targetCwd = path.resolve(workdir);
  const results: SessionSummary[] = [];

  for (const projectDir of fs.readdirSync(PROJECTS_DIR)) {
    const fullProjectDir = path.join(PROJECTS_DIR, projectDir);
    if (!fs.statSync(fullProjectDir).isDirectory()) continue;

    for (const file of fs.readdirSync(fullProjectDir)) {
      if (!file.endsWith(".jsonl")) continue;
      const filePath = path.join(fullProjectDir, file);
      const stat = fs.statSync(filePath);
      if (stat.size === 0) continue;

      try {
        const { cwd, title, preview, approxMessageCount } = inspectSessionFile(filePath);
        if (!cwd || path.resolve(cwd) !== targetCwd) continue;
        if (approxMessageCount === 0) continue;
        results.push({
          id: path.basename(file, ".jsonl"),
          cwd,
          title: title || preview.slice(0, 30) || "(제목 없음)",
          preview: preview || "(미리보기 없음)",
          // mtimeMs는 소수(마이크로초) 정밀도를 가진 float라 그대로 보내면 클라이언트의
          // Long 파서가 실패합니다. 정수 epoch-ms로 반올림합니다.
          updatedAt: Math.round(stat.mtimeMs),
          approxMessageCount,
        });
      } catch {
        // 손상되었거나 읽을 수 없는 세션 파일은 건너뜀
      }
    }
  }

  return results.sort((a, b) => b.updatedAt - a.updatedAt).slice(0, 20);
}

/**
 * 세션 기록 파일을 실제로 디스크에서 삭제합니다.
 * (Claude Code 데스크톱 앱의 "삭제" 버튼은 로컬 ~/.claude/projects의 원본 jsonl을 지우지
 *  않는 것으로 확인되어 — 앱에서 직접 지워야 폰/서버 어느 쪽에서도 다시 안 보입니다.)
 * 안전을 위해 workdir이 일치하는 세션만 지울 수 있게 확인합니다.
 */
export function deleteSessionFile(sessionId: string, workdir: string): boolean {
  const filePath = findSessionFilePath(sessionId);
  if (!filePath) return false;
  const { cwd } = inspectSessionFile(filePath);
  if (!cwd || path.resolve(cwd) !== path.resolve(workdir)) return false;
  fs.unlinkSync(filePath);
  return true;
}

/** 세션의 과거 대화(사용자/에이전트 텍스트만)를 최신 maxMessages개까지 시간순으로 반환합니다. */
export function readSessionTranscript(sessionId: string, maxMessages = 60): TranscriptEntry[] {
  const filePath = findSessionFilePath(sessionId);
  if (!filePath) return [];

  const raw = fs.readFileSync(filePath, "utf8");
  const entries: TranscriptEntry[] = [];

  for (const line of raw.split("\n")) {
    if (!line) continue;
    let obj: any;
    try {
      obj = JSON.parse(line);
    } catch {
      continue;
    }
    if (obj.type !== "user" && obj.type !== "assistant") continue;
    const text = extractDisplayText(obj.message?.content);
    if (!text) continue; // 도구 호출/결과만 있는 턴은 화면 가독성을 위해 생략
    entries.push({
      role: obj.type === "user" ? "user" : "agent",
      content: text,
      timestamp: obj.timestamp ? Date.parse(obj.timestamp) : Date.now(),
    });
  }

  return entries.slice(-maxMessages);
}
