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
import UIKit

enum Flint {
    static let IP = "192.168.8.2"
    static let WEB: UInt16 = 7880
    static let SORI: UInt16 = 7881
    static let SSID = "현장영상해설방송"
    static let JUSO = "http://192.168.8.2/"

    /// 방송 방식: flint(플린트 2 공유기 안 서버, 기본) 또는 taebeullit(이 아이폰이 서버, 예비)
    static var flintInga: Bool {
        get { (UserDefaults.standard.string(forKey: "bangsik") ?? "flint") == "flint" }
        set { UserDefaults.standard.set(newValue ? "flint" : "taebeullit", forKey: "bangsik") }
    }

    /// 공유기 안 서버에 묻기
    static func mutgi(_ gil: String, _ kkeut: @escaping ([String: Any]?) -> Void) {
        guard let u = URL(string: "http://\(IP):\(WEB)\(gil)") else { kkeut(nil); return }
        var q = URLRequest(url: u); q.timeoutInterval = 3; q.cachePolicy = .reloadIgnoringLocalCacheData
        URLSession.shared.dataTask(with: q) { d, r, _ in
            var j: [String: Any]? = nil
            if (r as? HTTPURLResponse)?.statusCode == 200, let d = d { j = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any] }
            kkeut(j)
        }.resume()
    }
}

/// 해설 소리를 공유기로 보내는 일꾼 — 끊기면 2초마다 다시 붙음, 3초마다 듣는 분 수·도움 요청을 물음
final class FlintBonaegi {
    static let shared = FlintBonaegi()
    private let q = DispatchQueue(label: "byod.flint")
    private var con: NWConnection?
    private var dolgo = false
    private var butum = false            // 공유기가 OK 라고 답했는가
    private var ssain = 0                // 아직 보내지 못하고 쌓인 조각 수
    private var salpimTimer: DispatchSourceTimer?
    private var yseq = -1
    private var gigiIreum = "아이폰"

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
            self.yseq = -1
            self.butgi()
            let t = DispatchSource.makeTimerSource(queue: self.q)
            t.schedule(deadline: .now() + 1, repeating: 3)
            t.setEventHandler { [weak self] in self?.salpigi() }
            t.resume()
            self.salpimTimer = t
        }
    }

    func meomchugi() {
        q.async {
            self.dolgo = false
            self.butum = false
            self.con?.cancel(); self.con = nil
            self.salpimTimer?.cancel(); self.salpimTimer = nil
            self.jamgeum.lock(); self._bonaenda = false; self.jamgeum.unlock()
        }
    }

    /// 0.1초 조각 하나 — 붙어 있을 때만 보내고, 5초 넘게 밀리면 버림(늦어지지 않게)
    func neoki(_ d: Data) {
        q.async {
            guard self.dolgo, self.butum, let c = self.con, self.ssain < 50 else { return }
            self.ssain += 1
            c.send(content: d, completion: .contentProcessed { [weak self] e in
                self?.q.async {
                    guard let s = self else { return }
                    s.ssain -= 1
                    if e != nil { s.kkeunkim("공유기: 방송 서버에 소리를 보내지 못합니다. 공유기 전원과 와이파이를 확인하십시오.") }
                }
            })
        }
    }

    private func butgi() {
        guard dolgo else { return }
        let c = NWConnection(host: NWEndpoint.Host(Flint.IP), port: NWEndpoint.Port(rawValue: Flint.SORI)!, using: .tcp)
        con = c
        ssain = 0
        c.stateUpdateHandler = { [weak self] st in
            guard let s = self else { return }
            switch st {
            case .ready:
                // 1.3.0 행사 이름·현장영상해설사·이 아이폰 시각을 함께 보냄(공유기가 행사 녹음과 기록을 남김)
                func kan(_ k: String) -> String { (UserDefaults.standard.string(forKey: k) ?? "").replacingOccurrences(of: "\t", with: " ").replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces) }
                let meori = "BYOD - \(s.gigiIreum)\t\(kan("haengsa_jemok"))\t\(kan("haeseolsa"))\t\(Date().timeIntervalSince1970)\n"
                c.send(content: meori.data(using: .utf8), completion: .contentProcessed { _ in })
                c.receive(minimumIncompleteLength: 2, maximumLength: 64) { d, _, _, e in
                    s.q.async {
                        let t = d.flatMap { String(data: $0, encoding: .utf8) } ?? ""
                        if e == nil && t.hasPrefix("OK") {
                            s.butum = true
                            s.jamgeum.lock(); s._bonaenda = true; s.jamgeum.unlock()
                            Bang.shared.juge { b in if b.jalmot.hasPrefix("공유기") { b.jalmot = "" } }
                        } else {
                            s.kkeunkim("공유기: 방송 서버가 이 아이폰을 받지 않습니다. 안드로이드 BYOD 앱의 플린트 2 준비하기를 다시 하십시오.")
                        }
                    }
                }
            case .failed, .waiting:
                s.q.async { s.kkeunkim("공유기: 방송 서버에 닿지 않습니다. 이 아이폰을 와이파이 \(Flint.SSID) 에 붙이고, 처음 쓰는 공유기면 안드로이드 BYOD 앱에서 플린트 2 준비하기를 하십시오.") }
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

    private func salpigi() {
        Flint.mutgi("/status") { [weak self] j in
            guard let s = self, let j = j else { return }
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
