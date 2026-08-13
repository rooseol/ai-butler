import "dotenv/config";
import express from "express";
import cors from "cors";
import http from "node:http";
import qrcode from "qrcode-terminal";

import "./db.js"; // 스키마 초기화
import { PAIRING_TOKEN, buildPairingUri, listCandidateAddresses, requireAuth } from "./auth/pairing.js";
import { pairingPageRouter } from "./auth/pairingPage.js";
import { deviceRouter } from "./auth/deviceRoutes.js";
import { chatRouter } from "./chat/routes.js";
import { filesRouter } from "./files/routes.js";
import { calendarRouter } from "./calendar/routes.js";
import { skillsRouter } from "./skills/routes.js";
import { sessionsRouter } from "./sessions/routes.js";
import { attachWebSocket } from "./ws/index.js";
import { startScheduleWatcher } from "./skills/scheduler.js";

const PORT = Number(process.env.PORT) || 8787;

const app = express();
app.use(cors());
app.use(express.json({ limit: "10mb" }));

// 헬스체크는 인증 없이 (페어링 화면에서 서버 확인용)
app.get("/api/health", (_req, res) => res.json({ ok: true, name: "ai-butler-server" }));

// QR 페어링 페이지도 인증 없이 (애초에 페어링 전이라 토큰이 없음)
app.use(pairingPageRouter);

// 이후 모든 /api 라우트는 토큰 필요
app.use("/api", requireAuth);
app.use("/api", chatRouter);
app.use("/api", filesRouter);
app.use("/api/calendar", calendarRouter);
app.use("/api", skillsRouter);
app.use("/api", deviceRouter);
app.use("/api", sessionsRouter);

// 404 및 에러를 JSON으로 반환 (스택트레이스 노출 방지)
app.use("/api", (_req, res) => res.status(404).json({ error: "not found" }));
app.use((err: any, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error("[api error]", err);
  res.status(500).json({ error: "internal server error" });
});

const server = http.createServer(app);
attachWebSocket(server);

server.listen(PORT, () => {
  const addresses = listCandidateAddresses();
  console.log("========================================");
  console.log(" AI Butler 서버가 시작되었습니다");
  console.log(` 로컬:   http://localhost:${PORT}`);
  if (addresses.length === 0) {
    console.log(" (외부에서 접속 가능한 네트워크 인터페이스를 찾지 못했습니다)");
  }
  for (const addr of addresses) {
    const tag = addr.isTailscale ? "Tailscale (외부에서도 접속 가능, 권장)" : addr.label;
    console.log(` ${tag}: http://${addr.address}:${PORT}`);
  }
  console.log(` 페어링 토큰: ${PAIRING_TOKEN}`);
  console.log("========================================");
  const primary = addresses[0];
  if (primary) {
    console.log(` 📱 브라우저에서 열기: http://${primary.address}:${PORT}/pair`);
    console.log("    (큰 QR코드가 뜹니다 — 앱의 'QR 스캔' 버튼으로 찍으세요)");
  }
  console.log(" 안드로이드 앱에서 아래 QR코드를 스캔하거나,");
  console.log(" host/port/token을 수동 입력해 페어링하세요.");
  console.log(" (Tailscale 주소가 있다면 그걸 쓰세요 — 어디서든 접속됩니다)");
  console.log("----------------------------------------");
  qrcode.generate(buildPairingUri(PORT), { small: true });

  startScheduleWatcher();
});
