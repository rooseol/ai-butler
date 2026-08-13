import { WebSocketServer, type WebSocket } from "ws";
import type { Server } from "node:http";
import { nanoid } from "nanoid";
import { db } from "../db.js";
import { runAgent } from "../agents/runAgent.js";
import type { AgentName } from "../agents/types.js";
import { PAIRING_TOKEN } from "../auth/pairing.js";
import { getActiveSession, setActiveSession } from "../sessions/activeSessions.js";

interface ClientMsg {
  type: "chat";
  agent: AgentName;
  text: string;
  fileId?: string;
}

type ServerMsg =
  | { type: "chat_saved"; id: string; agent: AgentName; role: "user"; content: string; createdAt: number }
  | { type: "chat_chunk"; agent: AgentName; text: string }
  | { type: "chat_done"; id: string; agent: AgentName; content: string; createdAt: number }
  | { type: "chat_error"; agent: AgentName; error: string };

const clients = new Set<WebSocket>();

export function broadcast(msg: ServerMsg) {
  const payload = JSON.stringify(msg);
  for (const ws of clients) {
    if (ws.readyState === ws.OPEN) ws.send(payload);
  }
}

function saveMessage(agent: string, role: string, content: string, fileId?: string, status = "done") {
  const id = nanoid();
  const createdAt = Date.now();
  db.prepare(
    `INSERT INTO messages (id, agent, role, content, file_id, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)`
  ).run(id, agent, role, content, fileId || null, status, createdAt);
  return { id, createdAt };
}

/**
 * 채팅에 첨부된 파일을 CLI가 실제로 볼 수 있도록, 프롬프트에 파일의 절대 경로를 덧붙입니다.
 * (지금까지는 fileId가 messages 테이블에만 저장되고 CLI 프롬프트에는 전달되지 않아,
 *  에이전트가 "첨부파일을 못 받았다"고 답하는 버그가 있었습니다.)
 * 화면/DB에 저장되는 원본 텍스트(msg.text)는 그대로 두고, CLI에 보내는 프롬프트만 보강합니다.
 */
function buildPromptWithAttachment(text: string, fileId?: string): string {
  if (!fileId) return text;
  const file = db.prepare(`SELECT filename, path FROM files WHERE id = ?`).get(fileId) as
    | { filename: string; path: string }
    | undefined;
  if (!file) return text;
  return `${text}\n\n[첨부파일]\n파일명: ${file.filename}\n전체 경로: ${file.path}\n(위 경로를 Read 도구로 열어서 내용을 확인한 뒤 답변하세요. 이미지 파일도 Read로 볼 수 있습니다.)`;
}

export function attachWebSocket(server: Server) {
  const wss = new WebSocketServer({ server, path: "/ws" });

  wss.on("connection", (ws, req) => {
    const url = new URL(req.url || "", "http://localhost");
    const token = url.searchParams.get("token");
    if (token !== PAIRING_TOKEN) {
      ws.close(4001, "unauthorized");
      return;
    }

    clients.add(ws);
    ws.on("close", () => clients.delete(ws));

    ws.on("message", (raw) => {
      let msg: ClientMsg;
      try {
        msg = JSON.parse(raw.toString());
      } catch {
        return;
      }
      if (msg.type !== "chat") return;

      const saved = saveMessage(msg.agent, "user", msg.text, msg.fileId);
      broadcast({ type: "chat_saved", id: saved.id, agent: msg.agent, role: "user", content: msg.text, createdAt: saved.createdAt });

      // 이 에이전트에서 "지금 이어가는 중"인 세션이 있으면 그대로 이어서 실행합니다.
      // (첫 메시지는 세션이 없어 새로 시작 → 완료 시 받은 session_id가 다음 턴부터 자동으로 이어집니다.
      //  PC 세션 하나를 골라둔 경우엔 그 세션이 계속 이어집니다.)
      const sessionId = getActiveSession(msg.agent);
      const prompt = buildPromptWithAttachment(msg.text, msg.fileId);
      const handle = runAgent(msg.agent, prompt, { sessionId });
      handle.onChunk(({ text }) => broadcast({ type: "chat_chunk", agent: msg.agent, text }));
      handle.onDone((full, newSessionId) => {
        if (newSessionId) setActiveSession(msg.agent, newSessionId);
        const done = saveMessage(msg.agent, "agent", full);
        broadcast({ type: "chat_done", id: done.id, agent: msg.agent, content: full, createdAt: done.createdAt });
      });
      handle.onError((err) => {
        saveMessage(msg.agent, "agent", `[오류] ${err.message}`, undefined, "error");
        broadcast({ type: "chat_error", agent: msg.agent, error: err.message });
      });
    });
  });

  return wss;
}
