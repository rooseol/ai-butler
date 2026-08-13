import fs from "node:fs";
import path from "node:path";

/**
 * FCM(Firebase Cloud Messaging) 푸시는 선택 사항입니다.
 * server/data/firebase-service-account.json 파일이 있으면 자동으로 활성화됩니다.
 * 없으면 조용히 no-op 처리되고, 앱이 켜져 있을 때 WebSocket 실시간 알림만 동작합니다.
 *
 * 설정 방법은 docs/SETUP.md 의 "푸시 알림(FCM) 설정" 항목을 참고하세요.
 */

let sendFn: ((token: string, title: string, body: string) => Promise<void>) | null = null;

async function initFcm() {
  const svcPath = path.resolve("./data/firebase-service-account.json");
  if (!fs.existsSync(svcPath)) {
    console.log("[fcm] 서비스 계정 파일이 없어 FCM 푸시를 비활성화합니다 (선택 기능).");
    return;
  }
  try {
    // firebase-admin은 선택 설치 패키지라 정적 타입 해석을 피하기 위해 변수로 모듈명을 전달합니다.
    const moduleName = "firebase-admin";
    const admin = await import(moduleName);
    const serviceAccount = JSON.parse(fs.readFileSync(svcPath, "utf8"));
    admin.default.initializeApp({ credential: admin.default.credential.cert(serviceAccount) });
    sendFn = async (token, title, body) => {
      await admin.default.messaging().send({
        token,
        notification: { title, body },
      });
    };
    console.log("[fcm] FCM 푸시 활성화됨");
  } catch (err) {
    console.warn("[fcm] 초기화 실패 (firebase-admin이 설치되어 있나요?):", (err as Error).message);
  }
}

await initFcm();

export async function sendPush(token: string, title: string, body: string) {
  if (!sendFn) return;
  try {
    await sendFn(token, title, body);
  } catch (err) {
    console.warn("[fcm] 전송 실패:", (err as Error).message);
  }
}
