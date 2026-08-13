export type AgentName = "claude" | "codex" | "gemini";

export interface AgentChunk {
  /** Raw text chunk emitted as the agent works. */
  text: string;
}

export interface RunAgentOptions {
  /**
   * 이어갈 세션 ID. Claude Code만 지원합니다 (`claude -p --resume <id>`).
   * 넘기지 않으면 새 세션이 시작되고, 완료 시 onDone의 sessionId로 새 ID가 전달됩니다.
   */
  sessionId?: string;
}

export interface AgentRunHandle {
  onChunk(cb: (chunk: AgentChunk) => void): void;
  /** sessionId: 이 턴이 속한(새로 생성되었거나 이어간) 세션 ID. Claude Code에서만 채워집니다. */
  onDone(cb: (fullText: string, sessionId?: string) => void): void;
  onError(cb: (err: Error) => void): void;
  cancel(): void;
}
