// 폰 ↔ 워치 — 마지막 안내와 다음 갈림길을 워치로 보내고, 워치가 누른 단추를 웹에 넘깁니다.
// 1.1 (빌드 260927-2, 이사장님 승인) 방향 진동을 워치로도 보냄(jindongBonae)
// 1.2 (빌드 260927-3, 이사장님 승인) 워치의 음향신호기 단추를 받아 폰이 블루투스로 울리고 결과를 워치에 돌려줌
import WatchConnectivity

final class WatchLink: NSObject, WCSessionDelegate {
    static let shared = WatchLink()
    var onRequest: ((String) -> Void)?
    /// 260927-3 워치의 음향신호기 단추 (1 위치안내 / 2 신호안내)
    var onSinhogi: ((UInt8) -> Void)?
    private var last: [String: Any] = [:]

    func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    // 최근 상태를 워치에 넘김 — 워치가 꺼져 있어도 다음에 켜질 때 받습니다(applicationContext)
    func push(_ data: [String: Any]) {
        for (k, v) in data { last[k] = v }
        last["ttae"] = Date().timeIntervalSince1970
        guard WCSession.isSupported(), WCSession.default.activationState == .activated else { return }
        try? WCSession.default.updateApplicationContext(last)
        if WCSession.default.isReachable { WCSession.default.sendMessage(last, replyHandler: nil, errorHandler: nil) }
    }

    /// 260927-2 방향 진동 — 워치의 길눈이 열려 있으면 지금 한 번 울립니다.
    /// 지난 진동이 나중에 다시 울리지 않게 applicationContext 에는 넣지 않고 메시지로만 보냅니다.
    func jindongBonae(_ mu: String) {
        guard WCSession.isSupported(), WCSession.default.activationState == .activated,
              WCSession.default.isReachable else { return }
        WCSession.default.sendMessage(["jindong": mu], replyHandler: nil, errorHandler: nil)
    }

    /// 260927-3 음향신호기 결과를 워치에 알림
    func sinhogiDap(_ ok: Bool, _ mal: String) {
        guard WCSession.isSupported(), WCSession.default.activationState == .activated,
              WCSession.default.isReachable else { return }
        WCSession.default.sendMessage(["sinhogiMal": mal, "sinhogiOk": ok], replyHandler: nil, errorHandler: nil)
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {}
    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) { session.activate() }

    // 워치 → 폰: {"what":"jari"|"daeum"|"mal"|"yudogi"}
    func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        let what = (message["what"] as? String) ?? ""
        if what == "sinhogi" {
            let c = UInt8(max(1, min(3, (message["cmd"] as? Int) ?? 1)))
            DispatchQueue.main.async { self.onSinhogi?(c) }
            replyHandler(["sinhogiMal": "음향신호기를 찾는 중입니다."])
            return
        }
        DispatchQueue.main.async { self.onRequest?(what) }
        replyHandler(last)
    }
    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        let what = (message["what"] as? String) ?? ""
        DispatchQueue.main.async { self.onRequest?(what) }
    }
}
