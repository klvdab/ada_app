// BYOD 방송 아이폰 — 플린트 2 방식 (1.2.0판, 빌드 261014-BI3, 이사장님 승인 「1」 — 방송 서버를 공유기 안에 둠, 방송클)
// 안드로이드 1.2.0(Flint.kt)과 같습니다. 플린트 2(GL-MT6000) 공유기 안에서 방송 서버(byod_flint.py)가 돌고,
// 이 아이폰은 해설 소리 한 줄기만 공유기로 보냅니다.
//   소리 보내기   192.168.8.2:7881  첫 줄 「BYOD 열쇠 이름」 뒤로 뮤 8비트 1초에 16000
//   상태 묻기     192.168.8.2:7880/status, /yocheong_mokrok
// 수백 명에게 나눠 보내는 일은 공유기가 하므로, 아이폰은 와이파이로 붙어도 됩니다.
// 공유기에 방송 서버를 넣는 「플린트 2 준비하기」는 안드로이드 BYOD 방송 앱에서 합니다(아이폰에는 공유기에 들어갈 도구가 없음).
// 1.3.0 (261014-BI4) 소리를 보낼 때 행사 이름·현장영상해설사·이 아이폰 시각을 함께 보냄
import Foundation
import Network
import NetworkExtension
import UIKit

enum Flint {
    static let IP = "192.168.8.2"
    static let WEB: UInt16 = 7880
    static let SORI: UInt16 = 7881
    static let SSID = "현장영상해설방송"
    static let JUSO = "http://192.168.8.2/"

    /// 방송 방식: flint(플린트 2 공유기 안 서버) 또는 taebeullit(아이피타임 방식 — 이 아이폰이 서버)
    /// 1.7.0 (261021-BI9, 이사장님 승인) 처음값을 아이피타임으로 — 플린트 2가 오기 전이라 지금 가진 아이피타임 A1004NS 로 방송
    static var flintInga: Bool {
        get { true }   // 1.14.0 아이피타임 방식을 뺌 — 언제나 플린트 2
        set { }
    }
    /// 1.14.0 이 아이폰은 무대 현장음 폰인가
    static var hyeonPon: Bool {
        get { UserDefaults.standard.bool(forKey: "hyeon_pon") }
        set { UserDefaults.standard.set(newValue, forKey: "hyeon_pon") }
    }
    static var pdBeonho = ""   // 1.14.0 공유기가 알려 준 피디 번호
    /// 1.7.0 공유기 이름(첫 화면·도움말·현장 점검에 씀)
    static var gongyugiIreum: String { "플린트 2" }

    /// 1.16.0 (261105-BI20, 새방클, 이사장님 승인) 방송 와이파이에 저절로 붙기 — 아이폰이 연결할지 물음. 붙으면 true
    static func wifiButgi(_ kkeut: @escaping (Bool) -> Void) {
        let c = NEHotspotConfiguration(ssid: SSID)
        c.joinOnce = false
        NEHotspotConfigurationManager.shared.apply(c) { e in
            var ok = e == nil
            if let n = e as NSError?, n.domain == NEHotspotConfigurationErrorDomain, n.code == NEHotspotConfigurationError.alreadyAssociated.rawValue { ok = true }
            DispatchQueue.main.async { kkeut(ok) }
        }
    }

    /// 1.16.0 방송 전에도 설정을 바꿀 수 있게 공유기에 피디 번호를 물음(공유기 1.9.0부터)
    static func pdMutgi(_ kkeut: @escaping (Bool) -> Void) {
        if !pdBeonho.isEmpty { kkeut(true); return }
        guard let p = NWEndpoint.Port(rawValue: SORI) else { kkeut(false); return }
        let c = NWConnection(host: NWEndpoint.Host(IP), port: p, using: .tcp)
        let q = DispatchQueue(label: "byod.pdkey")
        var doem = false
        func matchim(_ ok: Bool) {
            if doem { return }
            doem = true
            c.cancel()
            DispatchQueue.main.async { kkeut(ok) }
        }
        c.stateUpdateHandler = { st in
            switch st {
            case .ready:
                let y = UserDefaults.standard.string(forKey: "flint_yeolsoe") ?? ""
                c.send(content: Data(("PDKEY " + (y.isEmpty ? "-" : y) + "\n").utf8), completion: .contentProcessed { _ in })
                c.receive(minimumIncompleteLength: 1, maximumLength: 200) { d, _, _, _ in
                    let k = String(decoding: d ?? Data(), as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines).components(separatedBy: " ")
                    if k.count >= 3, k[0] == "OK" { pdBeonho = k[2]; matchim(true) } else { matchim(false) }
                }
            case .failed:
                matchim(false)
            default:
                break
            }
        }
        c.start(queue: q)
        q.asyncAfter(deadline: .now() + 3.5) { matchim(false) }
    }

    /// 1.13.0 (261104-BI17) 플린트 2 주소대인가 — 플은 192.168.8~11 번대를 줌
    static func peulJuso(_ host: String) -> Bool { ["192.168.8.", "192.168.9.", "192.168.10.", "192.168.11."].contains { host.hasPrefix($0) } }

    /// 1.13.0 세 번까지 묻기 — 플이 바쁠 때 한 번 못 들었다고 빠지지 않게
    static func dasiMutgi(_ gil: String, _ nam: Int = 3, _ kkeut: @escaping ([String: Any]?) -> Void) {
        mutgi(gil) { j in
            if j != nil || nam <= 1 { kkeut(j); return }
            DispatchQueue.global().asyncAfter(deadline: .now() + 0.5) { dasiMutgi(gil, nam - 1, kkeut) }
        }
    }

    /// 공유기 안 서버에 묻기
    static func mutgi(_ gil: String, _ kkeut: @escaping ([String: Any]?) -> Void) {
        guard let u = URL(string: "http://\(IP):\(WEB)\(gil)") else { kkeut(nil); return }
        var q = URLRequest(url: u); q.timeoutInterval = 3; q.cachePolicy = .reloadIgnoringLocalCacheData
        q.allowsCellularAccess = false   // 1.13.0 플에 묻는 말이 이동통신으로 새지 않게
        URLSession.shared.dataTask(with: q) { d, r, _ in
            var j: [String: Any]? = nil
            if (r as? HTTPURLResponse)?.statusCode == 200, let d = d { j = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any] }
            kkeut(j)
        }.resume()
    }

    /// 261107 (이사장님 승인) 공유기에 설정을 넘김 — 본문 JSON, 피디 번호 확인. 번호·비밀번호를 주소에 싣지 않음
    static func seoljeong(_ mom: [String: Any], _ kkeut: @escaping ([String: Any]?) -> Void) {
        pdMutgi { ok in
            guard ok, let u = URL(string: "http://\(IP):\(WEB)/seoljeong?k=\(pdBeonho)"),
                  let b = try? JSONSerialization.data(withJSONObject: mom) else { kkeut(nil); return }
            var q = URLRequest(url: u); q.httpMethod = "POST"; q.httpBody = b; q.timeoutInterval = 4
            q.setValue("application/json", forHTTPHeaderField: "Content-Type")
            q.allowsCellularAccess = false
            URLSession.shared.dataTask(with: q) { d, r, _ in
                var j: [String: Any]? = nil
                if (r as? HTTPURLResponse)?.statusCode == 200, let d = d { j = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any] }
                DispatchQueue.main.async { kkeut(j) }
            }.resume()
        }
    }

    /// 261107 송출 앱에 등록된 현장영상해설사 번호를 공유기에 넘겨 기억시킴 — 폰 없이 수신기 해설로 인터넷 방송할 때 공유기가 씀
    static func beonhoNeomgigi() {
        let b = InteonetBonaegi.jeonhwa
        guard b.count >= 10 else { return }
        seoljeong(["beonho": b]) { _ in }
    }
}

/// 해설 소리를 공유기로 보내는 일꾼 — 끊기면 2초마다 다시 붙음, 3초마다 듣는 분 수·도움 요청을 물음
final class FlintBonaegi {
    static let shared = FlintBonaegi()
    private let q = DispatchQueue(label: "byod.flint")
    private var con: NWConnection?
    private var neom = false             // 1.16.2 넘겨받기를 고름
    private var buteotda = false         // 1.16.2 이번 방송에서 공유기에 붙은 적 있는가
    private var dolgo = false
    private var butum = false            // 공유기가 OK 라고 답했는가
    private var ssain = 0                // 아직 보내지 못하고 쌓인 조각 수
    private var kkol2 = false            // 1.10.0 (261026-BI14) 공유기가 BYOD2 꼴(종류 1바이트 + 길이 2바이트 + 소리)을 받아들였는가
    private var ssainOpus = 0            // 1.10.0 쌓인 오푸스 덩이 수
    private var salpimTimer: DispatchSourceTimer?
    private var yseq = -1
    private var gigiIreum = "아이폰"
    private var hyeon = false            // 1.14.0 무대 현장음 폰
    private var seokCon: NWConnection?   // 1.14.0 섞은 소리 받는 줄
    private var seokMeori = Data()
    private var seokJinaem = false

    // 현장 점검·화면이 보는 값
    private let jamgeum = NSLock()
    private var _bonaenda = false
    private var _dapTtae = Date.distantPast
    private var _pan = ""
    private var _mp3 = false
    var bonaenda: Bool { jamgeum.lock(); defer { jamgeum.unlock() }; return _bonaenda }
    var dapTtae: Date { jamgeum.lock(); defer { jamgeum.unlock() }; return _dapTtae }
    var seobeoPan: String { jamgeum.lock(); defer { jamgeum.unlock() }; return _pan }
    var mp3: Bool { jamgeum.lock(); defer { jamgeum.unlock() }; return _mp3 }

    func sijak() {
        let nm = UIDevice.current.name.replacingOccurrences(of: " ", with: "")   // 화면 줄기에서 미리 읽어 둠
        q.async {
            self.gigiIreum = nm.isEmpty ? "아이폰" : nm
            self.dolgo = true
            self.neom = false; self.buteotda = false   // 1.16.2
            self.hyeon = Flint.hyeonPon
            self.yseq = -1
            self.butgi()
            let t = DispatchSource.makeTimerSource(queue: self.q)
            t.schedule(deadline: .now() + 1, repeating: 3)
            t.setEventHandler { [weak self] in self?.salpigi() }
            t.resume()
            self.salpimTimer = t
        }
    }

    /// 1.16.2 넘겨받기를 고름 — 앞 기기는 다시 붙으려다 교대된 것을 알고 멈춤
    func neomgyeoBatgi() {
        q.async {
            guard self.dolgo else { return }
            self.neom = true
            self.butgi()
        }
    }

    func meomchugi() {
        q.async {
            self.dolgo = false
            self.butum = false
            self.kkol2 = false   // 1.10.0
            self.con?.cancel(); self.con = nil
            self.seokCon?.cancel(); self.seokCon = nil; Bang.shared.seokkimBatneun = false
            self.salpimTimer?.cancel(); self.salpimTimer = nil
            self.jamgeum.lock(); self._bonaenda = false; self.jamgeum.unlock()
        }
    }

    /// 0.1초 조각 하나 — 붙어 있을 때만 보내고, 5초 넘게 밀리면 버림(늦어지지 않게)
    func neoki(_ d: Data) {
        q.async {
            guard self.dolgo, self.butum, let c = self.con, self.ssain < 50 else { return }
            self.ssain += 1
            c.send(content: self.kkol2 ? FlintBonaegi.teul2(1, d) : d, completion: .contentProcessed { [weak self] e in
                self?.q.async {
                    guard let s = self else { return }
                    s.ssain -= 1
                    if e != nil { s.kkeunkim("공유기: 방송 서버에 소리를 보내지 못합니다. 공유기 전원과 와이파이를 확인하십시오.") }
                }
            })
        }
    }

    /// 1.10.0 종류 1바이트 + 길이 2바이트를 붙임
    private static func teul2(_ t: UInt8, _ d: Data) -> Data {
        var o = Data([t, UInt8((d.count >> 8) & 0xFF), UInt8(d.count & 0xFF)])
        o.append(d)
        return o
    }

    /// 1.10.0 (261026-BI14) 오푸스(시험) 한 덩이 — 공유기가 BYOD2 꼴을 받아들였을 때만(옛 공유기면 버림)
    func neokiOpus(_ p: Data) {
        q.async {
            guard self.dolgo, self.butum, self.kkol2, let c = self.con, self.ssainOpus < 250 else { return }
            self.ssainOpus += 1
            c.send(content: FlintBonaegi.teul2(2, p), completion: .contentProcessed { [weak self] _ in
                self?.q.async { self?.ssainOpus -= 1 }
            })
        }
    }

    private func butgi() {
        guard dolgo else { return }
        let pm = NWParameters.tcp; pm.prohibitedInterfaceTypes = [.cellular]   // 1.13.0 소리가 이동통신으로 새지 않게
        let c = NWConnection(host: NWEndpoint.Host(Flint.IP), port: NWEndpoint.Port(rawValue: Flint.SORI)!, using: pm)
        con = c
        ssain = 0
        ssainOpus = 0; kkol2 = false   // 1.10.0
        let opusHal = OpusSori.on   // 1.10.0 켜 두셨으면 머리 끝에 「2」(BYOD2 꼴로 보내겠다) — 옛 공유기는 이 칸을 보지 않음
        c.stateUpdateHandler = { [weak self] st in
            guard let s = self else { return }
            switch st {
            case .ready:
                // 1.3.0 행사 이름·현장영상해설사·이 아이폰 시각을 함께 보냄(공유기가 행사 녹음과 기록을 남김)
                func kan(_ k: String) -> String { (UserDefaults.standard.string(forKey: k) ?? "").replacingOccurrences(of: "\t", with: " ").replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces) }
                let meori = (s.hyeon ? "HYEON - \(s.gigiIreum)" : "BYOD - \(s.gigiIreum)\t\(kan("haengsa_jemok"))\t\(kan("haeseolsa"))\t\(Date().timeIntervalSince1970)" + (opusHal ? "\t2" : "\t-") + (Bang.shared.songchul ? "\t-" : "\tq") + (s.neom ? "\tneom" : "\tmut")) + "\n"   // 1.14.0 q. 1.16.1 (261105-BI21) 줄바꿈이 주석 뒤로 밀려 빠져 있어 공유기가 머리 줄을 끝까지 못 읽고 아이폰 소리를 받지 못했음(상암의 원인)
                c.send(content: meori.data(using: .utf8), completion: .contentProcessed { _ in })
                c.receive(minimumIncompleteLength: 2, maximumLength: 64) { d, _, _, e in
                    s.q.async {
                        let t = d.flatMap { String(data: $0, encoding: .utf8) } ?? ""
                        if e == nil && t.hasPrefix("OK") && (!opusHal || t.contains("\n")) {   // 1.10.0 오푸스를 청했으면 대답 한 줄을 다 받아야(반만 오면 다시 붙음)
                            // 1.10.0 공유기 1.5.0 부터는 받아들이면 대답 끝에 「 2」(OK 판 피디번호 2)
                            let jul = (t.split(separator: "\n").first.map(String.init) ?? t).split(separator: " ")
                            if jul.count >= 3 { Flint.pdBeonho = String(jul[2]).trimmingCharacters(in: .whitespacesAndNewlines) }   // 1.14.0
                            s.kkol2 = opusHal && jul.count >= 3 && jul.last.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) == "2" } == true
                            s.butum = true
                            if !s.hyeon { s.buteotda = true }   // 1.16.2
                            s.jamgeum.lock(); s._bonaenda = true; s.jamgeum.unlock()
                            Bang.shared.juge { b in if b.jalmot.hasPrefix("공유기") { b.jalmot = "" } }
                        } else if t.hasPrefix("BAPPEUM") {
                            // 1.16.2 (261106-BI22, 이사장님 승인) 다른 기기가 방송 중 — 앞서 보내던 기기면 교대된 것이니 멈추고, 새로 시작하면 넘겨받을지 물음
                            let nm = String(t.dropFirst(7)).trimmingCharacters(in: .whitespacesAndNewlines)
                            s.con?.stateUpdateHandler = nil
                            s.con?.cancel(); s.con = nil
                            let gyodae = s.buteotda
                            DispatchQueue.main.async {
                                if gyodae {
                                    BangIl.shared.kkeugi()
                                    Bang.shared.jalmot = "\(nm) 기기가 방송을 넘겨받아 이 아이폰은 방송을 멈췄습니다."
                                } else {
                                    Bang.shared.neomgyeoMul = nm
                                }
                            }
                        } else {
                            s.kkeunkim("공유기: 방송 서버가 이 아이폰을 받지 않습니다. 더 보기의 플린트 2 준비하기를 다시 하십시오.")
                        }
                    }
                }
            case .failed, .waiting:
                s.q.async { s.kkeunkim("공유기: 방송 서버에 닿지 않습니다. 이 아이폰을 와이파이 \(Flint.SSID) 에 붙이고, 처음 쓰는 공유기면 안드로이드 LVD 송출 앱에서 플린트 2 준비하기를 하십시오.") }
            default: break
            }
        }
        c.start(queue: q)
    }

    /// 끊김 — 알리고 2초 뒤 다시 붙음(q 줄기에서 부름)
    private func kkeunkim(_ m: String) {
        guard dolgo else { return }
        butum = false
        jamgeum.lock(); _bonaenda = false; jamgeum.unlock()
        con?.stateUpdateHandler = nil
        con?.cancel(); con = nil
        Bang.shared.juge { b in if b.jalmot != m { b.jalmot = m } }
        q.asyncAfter(deadline: .now() + 2) { [weak self] in
            guard let s = self, s.dolgo, s.con == nil else { return }
            s.butgi()
        }
    }

    /// 1.14.0 현장음이 있고 송출 중이면 공유기가 섞은 소리(갈래 8)를 받아 인터넷 방송으로. 끊기면 이 아이폰 소리로 돌아감
    private func seokSalpigi() {
        let hal = !hyeon && dolgo && Bang.shared.hyeonIssum && Bang.shared.songchul
        if !hal {
            if seokCon != nil { seokCon?.cancel(); seokCon = nil }
            Bang.shared.seokkimBatneun = false
            return
        }
        if seokCon != nil { return }
        let pm = NWParameters.tcp; pm.prohibitedInterfaceTypes = [.cellular]
        let c = NWConnection(host: NWEndpoint.Host(Flint.IP), port: NWEndpoint.Port(rawValue: Flint.WEB)!, using: pm)
        seokCon = c; seokMeori = Data(); seokJinaem = false
        c.stateUpdateHandler = { [weak self] st in
            guard let s = self else { return }
            switch st {
            case .ready:
                c.send(content: "GET /stream?gal=8 HTTP/1.0\r\n\r\n".data(using: .utf8), completion: .contentProcessed { _ in })
                s.seokBatgi(c)
            case .failed, .cancelled:
                s.q.async { if s.seokCon === c { s.seokCon = nil }; Bang.shared.seokkimBatneun = false }
            default: break
            }
        }
        c.start(queue: q)
    }

    private func seokBatgi(_ c: NWConnection) {
        c.receive(minimumIncompleteLength: 1, maximumLength: 3200) { [weak self] d, _, kkeut, e in
            guard let s = self else { return }
            if var b = d, !b.isEmpty {
                if !s.seokJinaem {
                    s.seokMeori.append(b)
                    if let r = s.seokMeori.range(of: Data([13, 10, 13, 10])) {
                        s.seokJinaem = true
                        b = s.seokMeori.subdata(in: r.upperBound..<s.seokMeori.endIndex)
                    } else { b = Data() }
                }
                if !b.isEmpty { Bang.shared.seokkimBatneun = true; InteonetBonaegi.shared.neoki(b) }
            }
            if kkeut || e != nil { c.cancel(); return }
            s.seokBatgi(c)
        }
    }

    private func salpigi() {
        Flint.mutgi("/status") { [weak self] j in
            guard let s = self, let j = j else { return }
            Bang.shared.hyeonIssum = j["hyeon"] as? Bool ?? false   // 1.14.0
            s.q.async { s.seokSalpigi() }
            s.jamgeum.lock()
            s._dapTtae = Date(); s._pan = j["pan"] as? String ?? ""; s._mp3 = j["mp3"] as? Bool ?? false
            s.jamgeum.unlock()
            Bang.shared.suGochigi(j["n"] as? Int ?? 0)
            let ys = j["yseq"] as? Int ?? 0
            s.q.async {
                if s.yseq < 0 { s.yseq = ys; return }
                guard ys != s.yseq else { return }
                let sae = ys - s.yseq
                s.yseq = ys
                Flint.mutgi("/yocheong_mokrok") { m in
                    guard let l = m?["mokrok"] as? [[String: Any]] else { return }
                    for y in l.suffix(max(0, sae)) {
                        _ = Bang.shared.yocheongBatgi(beonho: y["n"] as? String ?? "", jongryu: y["k"] as? String ?? "")
                    }
                }
            }
        }
    }
}
