// 폰 ↔ 워치 — 앱 2.6.0 (빌드 260928-8). 옛 앱(260927-2·3)에서 승인된 방식을 새 앱의 알맹이로 옮기고 "말로 하기"를 더함.
//   폰 → 워치: 마지막 안내(mal), 다음 갈림길(daeum), 방향 진동(jindong), 음향신호기 결과(sinhogiMal·sinhogiOk), 워치 번호
//   워치 → 폰: what = mal | daeum | sinhogi(cmd 1·2) | malhagi(t: 손목에 말씀하신 글)
//   폰이 곁에 없을 때 워치가 받을 수 있게 마지막 안내를 나스(watch.php)에도 남김 — 워치 번호는 폰 키체인에
import Foundation
import WatchConnectivity
import Security

final class WatchLink: NSObject, WCSessionDelegate {
    static let shared = WatchLink()
    private var last: [String: Any] = [:]
    private var seobeoYeyak: DispatchWorkItem?

    /// 워치 번호(여섯 자리) — 한 번 만들면 바뀌지 않음
    static var beonho: String {
        let (v0, st) = Yeolsoe.ilgiSangtae("watchBeonho")
        if let v = v0, v.count == 6 { return v }
        let s = String((0..<6).map { _ in "0123456789".randomElement()! })
        if st == errSecItemNotFound || v0 != nil { Yeolsoe.sseugi("watchBeonho", s) }   // 2.12.0 잠겨 못 읽은 것이면 지우지 않음
        return s
    }

    func sijak() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
        last["watchBeonho"] = WatchLink.beonho
    }

    private var iEojim: Bool {
        WCSession.isSupported() && WCSession.default.activationState == .activated && WCSession.default.isPaired
            && WCSession.default.isWatchAppInstalled
    }

    /// 길눈이 한 말 — 워치에 넘기고 나스에도 남김(1.5초 모아서)
    func malBonae(_ t: String) {
        DispatchQueue.main.async {
            self.last["mal"] = t
            if AnnaeEngine.shared.geonneunJung { self.last["daeum"] = AnnaeEngine.shared.daeumGalrimMal() }
            self.last["ttae"] = Date().timeIntervalSince1970
            self.last["watchBeonho"] = WatchLink.beonho
            if self.iEojim { try? WCSession.default.updateApplicationContext(self.last) }
            self.seobeoYeyak?.cancel()
            let w = DispatchWorkItem { [weak self] in self?.seobeoNamgi() }
            self.seobeoYeyak = w
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5, execute: w)
        }
    }

    private func seobeoNamgi() {
        let k = WatchLink.beonho
        let mal = (last["mal"] as? String) ?? ""
        let daeum = (last["daeum"] as? String) ?? ""
        Task {
            if !mal.isEmpty { _ = try? await Tongsin.shared.getSae("watch.php", ["a": "nam", "k": k, "what": "mal", "t": mal]) }
            if !daeum.isEmpty { _ = try? await Tongsin.shared.getSae("watch.php", ["a": "nam", "k": k, "what": "daeum", "t": daeum]) }
        }
    }

    /// 방향 진동 — 워치의 길눈이 열려 있으면 지금 한 번(지난 진동이 나중에 울리지 않게 메시지로만)
    func jindongBonae(_ mu: String) {
        guard iEojim, WCSession.default.isReachable else { return }
        WCSession.default.sendMessage(["jindong": mu], replyHandler: nil, errorHandler: nil)
    }

    /// 음향신호기 결과
    func sinhogiDap(_ ok: Bool, _ mal: String) {
        guard iEojim, WCSession.default.isReachable else { return }
        WCSession.default.sendMessage(["sinhogiMal": mal, "sinhogiOk": ok], replyHandler: nil, errorHandler: nil)
    }

    // MARK: WCSessionDelegate

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if activationState == .activated { try? session.updateApplicationContext(last) }
    }
    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) { session.activate() }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        let what = (message["what"] as? String) ?? ""
        Girok.shared.namgi("watch", ["what": what])
        DispatchQueue.main.async {
            switch what {
            case "sinhogi":
                let c = UInt8(max(1, min(3, (message["cmd"] as? Int) ?? 1)))
                replyHandler(["sinhogiMal": "음향신호기를 찾는 중입니다."])
                SinhogiEngine.shared.ulligi(c)
            case "malhagi":
                let t = (message["t"] as? String) ?? ""
                MalHagi.shared.bakkatCheori(t) { d in replyHandler(["dapMal": d]) }
            case "daeum":
                var r = self.last
                r["daeum"] = AnnaeEngine.shared.daeumGalrimMal()
                replyHandler(r)
            default:
                replyHandler(self.last)
            }
        }
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {}
}
