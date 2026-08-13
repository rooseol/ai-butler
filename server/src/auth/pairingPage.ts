import { Router } from "express";
import QRCode from "qrcode";
import { buildPairingUri, listCandidateAddresses, PAIRING_TOKEN } from "./pairing.js";

export const pairingPageRouter = Router();

/**
 * 브라우저에서 열어 QR을 보여주는 페어링 페이지. 인증이 필요 없습니다 —
 * 이미 서버에 네트워크로 접근 가능한(=Tailscale 등으로 이미 신뢰된) 사람만 볼 수 있고,
 * 콘솔에 출력되는 정보와 동일한 수준입니다.
 */
pairingPageRouter.get("/pair", async (req, res) => {
  const port = Number(process.env.PORT) || 8787;
  const uri = buildPairingUri(port);
  const addresses = listCandidateAddresses();
  const primary = addresses[0];

  let qrDataUrl: string;
  try {
    qrDataUrl = await QRCode.toDataURL(uri, { width: 320, margin: 1 });
  } catch (err) {
    res.status(500).send("QR 생성 실패");
    return;
  }

  res.setHeader("Content-Type", "text/html; charset=utf-8");
  res.send(`<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>AI Butler 페어링</title>
<style>
  body { font-family: -apple-system, "Segoe UI", sans-serif; background: #f5f6fa; margin: 0; padding: 32px 16px; text-align: center; color: #1a1a2e; }
  h1 { font-size: 22px; margin-bottom: 4px; }
  p.sub { color: #666; margin-top: 0; }
  .card { background: white; border-radius: 16px; padding: 24px; max-width: 380px; margin: 24px auto; box-shadow: 0 2px 12px rgba(0,0,0,0.08); }
  img { width: 260px; height: 260px; }
  .info { text-align: left; font-size: 14px; line-height: 1.8; background: #f0f1f7; border-radius: 8px; padding: 12px 16px; margin-top: 16px; word-break: break-all; }
  .info b { color: #4F6BED; }
  .addr-list { text-align: left; font-size: 13px; color: #666; margin-top: 12px; }
</style>
</head>
<body>
  <h1>📱 AI Butler 페어링</h1>
  <p class="sub">앱을 열고 "QR 스캔"으로 아래 코드를 찍으세요</p>
  <div class="card">
    <img src="${qrDataUrl}" alt="페어링 QR 코드" />
    <div class="info">
      <div><b>host</b>: ${primary?.address ?? "-"}</div>
      <div><b>port</b>: ${port}</div>
      <div><b>token</b>: ${PAIRING_TOKEN}</div>
    </div>
    <p class="sub" style="font-size:12px;margin-top:12px;">QR이 안 찍히면 위 정보를 앱에 직접 입력하세요</p>
  </div>
  <div class="addr-list">
    ${addresses.map((a) => `${a.isTailscale ? "🔒 Tailscale" : a.label}: ${a.address}`).join("<br/>")}
  </div>
</body>
</html>`);
});
