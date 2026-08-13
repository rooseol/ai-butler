import { Router } from "express";
import { listClaudeSessions, readSessionTranscript, deleteSessionFile } from "../agents/sessions.js";
import { getActiveSession, setActiveSession, clearActiveSession } from "./activeSessions.js";
import type { AgentName } from "../agents/types.js";

export const sessionsRouter = Router();

const SUPPORTED_AGENTS: AgentName[] = ["claude"]; // codex/gemini는 세션 이어가기 미지원

// PC에서 하던 세션 목록(Claude Code가 자동 생성한 제목 포함) + 지금 이어가는 중인 세션
sessionsRouter.get("/sessions", (req, res) => {
  const agent = (req.query.agent as AgentName) || "claude";
  if (!SUPPORTED_AGENTS.includes(agent)) {
    return res.json({ active: null, sessions: [] });
  }
  const workdir = process.env.AGENT_WORKDIR || process.cwd();
  const sessions = listClaudeSessions(workdir);
  res.json({ active: getActiveSession(agent) || null, sessions });
});

// 특정 세션의 과거 대화(텍스트만, 최근 일부)를 불러옵니다.
sessionsRouter.get("/sessions/:id/transcript", (req, res) => {
  const entries = readSessionTranscript(req.params.id);
  res.json({ entries });
});

// 세션 삭제: 로컬 jsonl 파일을 실제로 지웁니다 (되돌릴 수 없음).
sessionsRouter.delete("/sessions/:id", (req, res) => {
  const workdir = process.env.AGENT_WORKDIR || process.cwd();
  const deleted = deleteSessionFile(req.params.id, workdir);
  if (!deleted) return res.status(404).json({ error: "session not found" });

  // 방금 지운 세션이 "지금 이어가는 중"이었다면 연결을 풀어둡니다.
  for (const agent of SUPPORTED_AGENTS) {
    if (getActiveSession(agent) === req.params.id) clearActiveSession(agent);
  }
  res.json({ ok: true });
});

// 세션 선택: sessionId를 넘기면 그 세션을 이어감, null/생략하면 "새 대화 시작"으로 초기화
sessionsRouter.post("/sessions/select", (req, res) => {
  const { agent, sessionId } = req.body as { agent?: AgentName; sessionId?: string | null };
  if (!agent) return res.status(400).json({ error: "agent required" });

  if (sessionId) {
    setActiveSession(agent, sessionId);
  } else {
    clearActiveSession(agent);
  }
  res.json({ ok: true, active: sessionId || null });
});
