import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import { fileURLToPath } from "node:url";
import { nanoid } from "nanoid";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dataDir = path.resolve(__dirname, "..", "..", "data");
const pairingFile = path.join(dataDir, "pairing.json");

interface PairingInfo {
  token: string;
}

function loadOrCreateToken(): string {
  if (!fs.existsSync(dataDir)) fs.mkdirSync(dataDir, { recursive: true });
  if (fs.existsSync(pairingFile)) {
    const info = JSON.parse(fs.readFileSync(pairingFile, "utf8")) as PairingInfo;
    if (info.token) return info.token;
  }
  const token = nanoid(32);
  fs.writeFileSync(pairingFile, JSON.stringify({ token }, null, 2));
  return token;
}

export const PAIRING_TOKEN = loadOrCreateToken();

export interface NetworkAddress {
  /** 인터페이스 이름 (예: "Tailscale", "이더넷") */
  label: string;
  address: string;
  /** Tailscale VPN 인터페이스로 보이면 true — 외부에서도 안전하게 접속 가능해 우선 추천합니다. */
  isTailscale: boolean;
}

/** Tailscale은 100.64.0.0/10 CGNAT 대역을 씁니다 (대략 100.64.x.x ~ 100.127.x.x). */
function looksLikeTailscale(name: string, address: string): boolean {
  if (/tailscale/i.test(name)) return true;
  const parts = address.split(".").map(Number);
  return parts[0] === 100 && parts[1] >= 64 && parts[1] <= 127;
}

/** 사용 가능한 IPv4 주소 목록. Tailscale 인터페이스를 맨 앞으로 정렬합니다(있다면 그게 가장 안전하고 어디서든 접속 가능). */
export function listCandidateAddresses(): NetworkAddress[] {
  const nets = os.networkInterfaces();
  const results: NetworkAddress[] = [];
  for (const name of Object.keys(nets)) {
    for (const net of nets[name] || []) {
      if (net.family !== "IPv4" || net.internal) continue;
      results.push({ label: name, address: net.address, isTailscale: looksLikeTailscale(name, net.address) });
    }
  }
  results.sort((a, b) => Number(b.isTailscale) - Number(a.isTailscale));
  return results;
}

export function getPrimaryAddress(): NetworkAddress {
  return listCandidateAddresses()[0] || { label: "loopback", address: "127.0.0.1", isTailscale: false };
}

export function getLocalIPv4(): string {
  return getPrimaryAddress().address;
}

export function buildPairingUri(port: number): string {
  const host = getLocalIPv4();
  return `aibutler://pair?host=${host}&port=${port}&token=${PAIRING_TOKEN}`;
}

/** Express 미들웨어: Authorization: Bearer <token> 검사 */
export function requireAuth(req: any, res: any, next: any) {
  const header = req.headers["authorization"] || "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : req.query.token;
  if (token !== PAIRING_TOKEN) {
    return res.status(401).json({ error: "unauthorized" });
  }
  next();
}
