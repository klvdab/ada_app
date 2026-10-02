// 폰 ↔ 워치 — 앱 2.6.0 (빌드 260928-8). 옛 앱(260927-2·3)에서 승인된 방식을 새 앱의 알맹이로 옮기고 "말로 하기"를 더함.
//   폰 → 워치: 마지막 안내(mal), 다음 갈림길(daeum), 방향 진동(jindong), 음향신호기 결과(sinhogiMal·sinhogiOk), 워치 번호
//   워치 → 폰: what = mal | daeum | sinhogi(cmd 1·2) | malhagi(t: 손목에 말씀하신 글) | malhagiSijak(2.33.0 손가락 세 번 집기 — 폰이 듣기 시작)
//   2.37.0 워치 → 폰: 지팡이 떨림 기록 파일(what = tteollim) — 폰이 받아 협회 나스(jeom/tteollim.php, 연구용)로 올림. 못 올리면 들고 있다가 다음에
//   2.36.0 폰 → 워치: gariki(가야 할 쪽, 음수면 없음) / 워치 → 폰: momBang(가리키기 방향 맞추기 — 몸 방향을 물음)
//   2.35.0 폰 → 워치: geotneun(점지도 따라 걷는 중 — 워치가 깨어 있기를 엶) / 워치 → 폰: watchGeoreum(n: 워치가 센 걸음 누계)
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
        tteollimOlligi()   // 2.37.0 지난번에 못 올린 지팡이 떨림 기록
    }

    // MARK: 2.37.0 (빌드 261002-5, 대표님 승인) 지팡이 떨림 기록 — 워치에서 받아 나스로

    private var tteollimOlineun = false

    private static var tteollimHam: URL {
        let d = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("tteollim", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    func session(_ session: WCSession, didReceive file: WCSessionFile) {
        guard (file.metadata?["what"] as? String) == "tteollim" else { return }
        let m = file.metadata ?? [:]
        let pyo = ((m["pyo"] as? String) ?? "모름").replacingOccurrences(of: "_", with: " ")
        let cho = (m["cho"] as? Int) ?? 0, hz = (m["hz"] as? Int) ?? 100
        let son = (m["son"] as? String) ?? ""
        // 받은 파일은 이 함수가 끝나면 지워지므로 곧바로 옮김 — 이름에 바닥·길이를 담아 둠
        let ireum = "\(Int(Date().timeIntervalSince1970))_\(hz)_\(cho)_\(son)_\(pyo).bin"
        do { try FileManager.default.moveItem(at: file.fileURL, to: WatchLink.tteollimHam.appendingPathComponent(ireum)) } catch { return }
        Girok.shared.namgi("tteollim_batda", ["pyo": pyo, "cho": cho])
        DispatchQueue.main.async { self.tteollimOlligi() }
    }

    /// 들고 있는 떨림 기록을 차례로 나스에 올림 — 하나라도 못 올리면 멈추고 다음 기회에
    func tteollimOlligi() {
        DispatchQueue.main.async {
            guard !self.tteollimOlineun else { return }
            let fs = ((try? FileManager.default.contentsOfDirectory(at: WatchLink.tteollimHam, includingPropertiesForKeys: nil)) ?? [])
                .filter { $0.pathExtension == "bin" }.sorted { $0.lastPathComponent < $1.lastPathComponent }
            guard !fs.isEmpty else { return }
            self.tteollimOlineun = true
            Task {
                for f in fs {
                    let p = f.deletingPathExtension().lastPathComponent.split(separator: "_", maxSplits: 4).map(String.init)
                    guard p.count == 5, let d = try? Data(contentsOf: f) else { try? FileManager.default.removeItem(at: f); continue }
                    var c = URLComponents(string: "https://lvd.ada.or.kr/jeom/tteollim.php")!
                    c.queryItems = [URLQueryItem(name: "k", value: WatchLink.beonho), URLQueryItem(name: "ttae", value: p[0]),
                                    URLQueryItem(name: "hz", value: p[1]), URLQueryItem(name: "cho", value: p[2]),
                                    URLQueryItem(name: "son", value: p[3]), URLQueryItem(name: "pyo", value: p[4]),
                                    URLQueryItem(name: "pan", value: Pan.pan + "(" + Pan.bild + ")")]
                    guard let u = c.url else { break }
                    var r = URLRequest(url: u)
                    r.httpMethod = "POST"
                    r.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
                    r.httpBody = d
                    r.timeoutInterval = 90
                    guard let dr = try? await URLSession.shared.data(for: r), (dr.1 as? HTTPURLResponse)?.statusCode == 200,
                          String(data: dr.0, encoding: .utf8)?.contains("\"ok\":true") == true else { break }
                    try? FileManager.default.removeItem(at: f)
                    Girok.shared.namgi("tteollim_olim", ["pyo": p[4], "cho": p[2]])
                }
                DispatchQueue.main.async { self.tteollimOlineun = false }
            }
        }
    }

    /// 2.34.0 워치가 짝지어져 있고 워치 길눈이 깔려 있는가(동영상을 워치로 보낼지 정할 때)
    var watchItda: Bool { iEojim }

    /// 2.34.0 받은 동영상을 워치로 — 파일은 파일 보내기(폰 길눈이 뒤에 있어도 이어짐), 주소는 말로 보내고 안 닿으면 뒤에서 넘김
    func dongyeongBonae(_ u: URL) {
        guard iEojim else { return }
        if u.isFileURL {
            WCSession.default.transferFile(u, metadata: ["what": "dongyeong", "ireum": u.lastPathComponent])
        } else {
            let m: [String: Any] = ["what": "dongyeongJuso", "u": u.absoluteString]
            if WCSession.default.isReachable {
                WCSession.default.sendMessage(m, replyHandler: nil) { _ in WCSession.default.transferUserInfo(m) }
            } else {
                WCSession.default.transferUserInfo(m)
            }
        }
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

    /// 2.35.0 (빌드 261002-3, 대표님 승인) 점지도 따라 걷기 시작·그만 — 워치가 깨어 있기와 걸음 세기를 켜고 끔
    func geotgiAllim(_ on: Bool) {
        DispatchQueue.main.async {
            guard (self.last["geotneun"] as? Bool) != on else { return }
            self.last["geotneun"] = on
            guard self.iEojim else { return }
            try? WCSession.default.updateApplicationContext(self.last)
            if WCSession.default.isReachable { WCSession.default.sendMessage(["geotneun": on], replyHandler: nil, errorHandler: nil) }
        }
    }

    /// 2.36.0 (빌드 261002-4, 대표님 승인) 손목 가리키기 — 가야 할 쪽을 워치에 (지금 이어져 있을 때만, 지난 것이 늦게 닿지 않게)
    func garikiBonae(_ b: Double?) {
        guard iEojim, WCSession.default.isReachable else { return }
        WCSession.default.sendMessage(["gariki": b ?? -1.0], replyHandler: nil, errorHandler: nil)
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
            case "malhagiSijak":
                // 2.33.0 워치에서 두 번 집기를 세 번 — 폰 길눈이 말로 하기 단추를 누른 것처럼 듣기를 엶
                MalHagi.shared.dudeurim()
                replyHandler(["dapMal": "폰 길눈이 듣고 있습니다. 말씀하십시오."])
            case "momBang":
                // 2.36.0 가리키기 방향 맞추기 — 지금 몸이 향한 방향
                replyHandler(["momBang": JeomEngine.shared.momBang ?? -1.0])
            case "daeum":
                var r = self.last
                r["daeum"] = AnnaeEngine.shared.daeumGalrimMal()
                replyHandler(r)
            default:
                replyHandler(self.last)
            }
        }
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        // 2.35.0 워치가 센 걸음 — 폰이 걸음을 못 셀 때(가방 속 등) 점지도 따라 걷기가 이것으로 이어 감
        guard (message["what"] as? String) == "watchGeoreum", let n = message["n"] as? Int else { return }
        DispatchQueue.main.async { JeomEngine.shared.watchGeoreum(n) }
    }
}
