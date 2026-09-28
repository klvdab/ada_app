// 지하철 엔진 — 타는 역까지 걷기, 열차에 탄 것 알아채기, 지나는 역마다 알리기, 갈아타기, 내릴 역, 나갈 출구.
// 웹 길눈 tamseung.js 1.1(2026-09-18 이사장님 지시 "지하철을 타고 오는데 역 이름을 하나도 말해 주지 않더라")을 앱으로 옮김.
// 지나는 역은 세 겹으로 헤아립니다.
//   ① 서울 실시간 열차 위치(yeok.php a=silsi) — 우리가 탄 열차를 잡아 역마다 알림
//   ② 폰 흔들림으로 섰다 떠나는 것을 세어 몇 번째 역인지 헤아림(통신이 끊겨도 됨)
//   ③ 역 사이 걸리는 시간으로 셈함
// 타는 역 출구에 닿은 뒤 열차가 움직이는데 걸음이 없으면 "탔다"고 보고 저절로 역 알림을 시작합니다(손을 쓰지 않게).
import Foundation
import CoreMotion

struct JihaGugan: Codable {
    var hoseon: String
    var bangmyeon: String
    var jina: [String]
}

struct JihaGil: Codable {
    var from: String
    var to: String
    var mal: String
    var bun: Int
    var jina: [String]
    var gugan: [JihaGugan]
    var ipgu: Jangso          // 타는 역에서 들어갈 출구(걸어갈 곳)
    var naeril: String        // 내린 역에서 목적지에 가장 가까운 출구
    var ipguDochak: Bool
    var i: Int
    var kkeutnam: Bool
}

final class JihacheolEngine {
    static let shared = JihacheolEngine()

    private let motion = CMMotionManager()
    private var chang: [Double] = []
    private var dallim = false
    private var seonTtae: Date?
    private var dallimSijak: Date?
    private var dallimGeoreum = 0
    private var yeolcha = ""
    private var silsiJal = false
    private var silsiMot = 0
    private var majimak = Date()
    private var hwanJa: [Int] = []
    private var hoseon = ""
    private var kkeut = ""
    private var from = ""
    private var poller: Timer?
    private var ticker: Timer?
    private(set) var dolgo = false
    private var tabeumGamsi = false
    private var silsiMutneunJung = false

    private var yj: YeojeongEngine { YeojeongEngine.shared }
    var gil: JihaGil? { yj.jigeum?.jiha }

    static func ireum(_ n: String) -> String {
        n.hasSuffix("역") ? String(n.dropLast()) : n
    }

    // MARK: 길 찾기

    static func su(_ v: Any?) -> Double? { Chatgi.su(v) }

    /// 지금 자리에서 목적지까지 지하철 길 — (길, 못 찾은 까닭)
    static func gilChatgi(_ mok: Jangso) async -> (JihaGil?, String) {
        guard let w = WichiEngine.shared.jigeum else { return (nil, "아직 위치를 잡는 중입니다. 잠시 뒤 다시 눌러 주십시오.") }
        async let a = gakkaun(w.lat, w.lon)
        async let b = gakkaun(mok.lat, mok.lon)
        let (ya, yb) = await (a, b)
        guard let from = ya, let to = yb else { return (nil, "가까운 역을 받지 못했습니다. 통신이 끊겼을 수 있습니다.") }
        if from.yeok == to.yeok {
            return (nil, "타실 역과 내리실 역이 같은 \(from.yeok)역입니다. 걸어가시는 편이 낫습니다.")
        }
        guard let o = await Chatgi.json("yeok.php", ["a": "gil", "from": from.yeok, "to": to.yeok]),
              (o["ok"] as? Bool) == true else {
            return (nil, "\(from.yeok)역에서 \(to.yeok)역까지 가는 길을 찾지 못했습니다.")
        }
        let jina = (o["jina"] as? [String]) ?? []
        let gugan: [JihaGugan] = ((o["gugan"] as? [[String: Any]]) ?? []).map {
            JihaGugan(hoseon: ($0["hoseon"] as? String) ?? "", bangmyeon: ($0["bangmyeon"] as? String) ?? "",
                      jina: ($0["jina"] as? [String]) ?? [])
        }
        guard !jina.isEmpty else { return (nil, "지나는 역을 받지 못했습니다.") }
        let bun = Int(su(o["bun"]) ?? Double(jina.count * 2))
        let mal = (o["mal"] as? String) ?? ""
        // 들어갈 출구(지금 자리에서 가장 가까운 출구), 나갈 출구(목적지에서 가장 가까운 출구)
        var ipgu = Jangso(ireum: from.ireum, juso: "", lat: from.lat, lon: from.lon)
        if let c = await chulgu(w.lat, w.lon), c.ireum.contains(from.yeok) { ipgu = c }
        let naeril = await chulgu(mok.lat, mok.lon)?.ireum ?? ""
        let g = JihaGil(from: from.yeok, to: to.yeok, mal: mal, bun: bun, jina: jina, gugan: gugan,
                        ipgu: ipgu, naeril: naeril.contains(to.yeok) ? naeril : "",
                        ipguDochak: false, i: -1, kkeutnam: false)
        return (g, "")
    }

    private struct Yeok { let yeok: String; let ireum: String; let lat: Double; let lon: Double }

    private static func gakkaun(_ lat: Double, _ lon: Double) async -> Yeok? {
        guard let o = await Chatgi.json("yeok.php", ["a": "gakkaun", "lat": String(format: "%.6f", lat), "lon": String(format: "%.6f", lon)]),
              let r = (o["rows"] as? [[String: Any]])?.first,
              let la = su(r["lat"]), let lo = su(r["lon"]) else { return nil }
        return Yeok(yeok: (r["yeok"] as? String) ?? "", ireum: (r["ireum"] as? String) ?? "", lat: la, lon: lo)
    }

    private static func chulgu(_ lat: Double, _ lon: Double) async -> Jangso? {
        guard let o = await Chatgi.json("yeok.php", ["a": "chulgu", "lat": String(format: "%.6f", lat), "lon": String(format: "%.6f", lon)]),
              let r = (o["rows"] as? [[String: Any]])?.first,
              let la = su(r["lat"]), let lo = su(r["lon"]) else { return nil }
        return Jangso(ireum: (r["ireum"] as? String) ?? "", juso: "", lat: la, lon: lo)
    }

    // MARK: 타기와 역 알림

    /// 타는 역 출구에 닿음 — 이제 열차가 움직이면 저절로 역 알림을 시작
    func ipguDochak() {
        guard var g = gil else { return }
        g.ipguDochak = true
        yj.jihaNoki(g)
        tabeumGamsi = true
        heundeullimSijak()
        let gg = g.gugan.first
        let bang = (gg?.bangmyeon ?? "").isEmpty ? "" : " \(gg!.bangmyeon) 방면"
        SoriEngine.shared.mal("\(g.ipgu.ireum)입니다. 들어가셔서 \(gg?.hoseon ?? "")\(bang) 열차를 타십시오. 열차가 움직이면 저절로 역 알림을 시작합니다.")
        Girok.shared.namgi("jiha_ipgu", [:])
    }

    /// 열차에 탔습니다 — 누르셔도 되고, 저절로도 됨
    func tatda(jadong: Bool) {
        guard var g = gil else { return }
        g.i = -1
        g.kkeutnam = false
        g.ipguDochak = true
        yj.jihaNoki(g)
        yj.talgeotJeonghagi(.jihacheol, barojabeum: true)
        yj.danggyeBakkugi(.taneunJung)
        junbi(g)
        majimak = Date()
        let apmal = jadong ? "열차가 움직이는 것 같습니다. " : ""
        SoriEngine.shared.mal(apmal + "역 알림을 시작합니다. 내리실 역은 \(g.to)역, \(g.jina.count) 정거장 뒤입니다. 지나는 역마다 알려 드립니다.")
        Girok.shared.namgi("jiha_tam", ["jadong": jadong])
        dolligi()
    }

    /// 앱을 껐다 켰을 때 타고 가던 중이면 이어 감
    func ieoGagi() {
        guard let y = yj.jigeum, let g = y.jiha else { return }
        if y.danggye == .taneunJung && !g.kkeutnam {
            junbi(g)
            majimak = Date()
            dolligi()
        } else if y.danggye == .taneunGotKkaji && g.ipguDochak {
            tabeumGamsi = true
            heundeullimSijak()
        }
    }

    func meomchugi() {
        dolgo = false
        tabeumGamsi = false
        poller?.invalidate()
        ticker?.invalidate()
        poller = nil
        ticker = nil
        motion.stopAccelerometerUpdates()
    }

    private func junbi(_ g: JihaGil) {
        hwanJa = []
        var nu = -1
        if g.gugan.count > 1 {
            for gi in 0..<(g.gugan.count - 1) {
                nu += g.gugan[gi].jina.count
                hwanJa.append(nu)
            }
        }
        // 이미 지난 역 뒤의 구간으로 호선을 맞춤
        var gu = 0
        for (k, h) in hwanJa.enumerated() where g.i >= h { gu = k + 1 }
        hoseon = g.gugan.indices.contains(gu) ? g.gugan[gu].hoseon : (g.gugan.first?.hoseon ?? "")
        kkeut = g.gugan.indices.contains(gu) ? g.gugan[gu].bangmyeon : ""
        from = g.i >= 0 ? JihacheolEngine.ireum(g.jina[g.i]) : g.from
        yeolcha = ""
        silsiJal = false
        silsiMot = 0
    }

    private func dolligi() {
        dolgo = true
        tabeumGamsi = false
        heundeullimSijak()
        poller?.invalidate()
        ticker?.invalidate()
        silsi()
        poller = Timer.scheduledTimer(withTimeInterval: 20, repeats: true) { [weak self] _ in self?.silsi() }
        ticker = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in self?.sigan() }
    }

    /// 한 역 지났을 때
    private func hanYeok() {
        guard dolgo, var g = gil, !g.kkeutnam, !g.jina.isEmpty else { return }
        g.i = min(g.i + 1, g.jina.count - 1)
        majimak = Date()
        let ji = JihacheolEngine.ireum(g.jina[g.i])
        let nam = g.jina.count - 1 - g.i
        var mal: String
        if let hj = hwanJa.firstIndex(of: g.i), nam > 0, hj + 1 < g.gugan.count {
            let dg = g.gugan[hj + 1]
            hoseon = dg.hoseon
            kkeut = dg.bangmyeon
            from = ji
            yeolcha = ""
            silsiJal = false
            majimak = Date().addingTimeInterval(180)   // 갈아타는 3분 동안은 세지 않음
            let bang = dg.bangmyeon.isEmpty ? "으로" : " \(dg.bangmyeon) 방면으로"
            mal = "\(ji)역입니다. 여기서 내리셔서 \(dg.hoseon)\(bang) 갈아타십시오. 갈아탄 뒤에도 지나는 역을 이어서 알려 드립니다. 내리실 역까지 \(nam) 정거장 남았습니다."
        } else if let hj2 = hwanJa.firstIndex(of: g.i + 1), nam > 1, hj2 + 1 < g.gugan.count {
            mal = "\(ji)역입니다. 다음 \(JihacheolEngine.ireum(g.jina[g.i + 1]))역에서 내려 \(g.gugan[hj2 + 1].hoseon)으로 갈아타십니다. 내리실 준비를 하십시오."
        } else if nam <= 0 {
            g.kkeutnam = true
            mal = "\(ji)역입니다. 내리십시오."
            if !g.naeril.isEmpty { mal += " 내리셔서 \(g.naeril)로 나가시면 목적지가 가장 가깝습니다." }
            mal += " 밖으로 나오시면 남은 길을 걸어서 안내합니다."
        } else if nam == 1 {
            mal = "\(ji)역입니다. 다음 역에서 내리십니다. 내리실 준비를 하십시오."
        } else if nam == 2 {
            mal = "\(ji)역입니다. 두 역 뒤에 내리십니다."
        } else {
            mal = "\(ji)역입니다. 다음은 \(JihacheolEngine.ireum(g.jina[g.i + 1]))역입니다."
        }
        yj.jihaNoki(g)
        SoriEngine.shared.mal(mal)
        Girok.shared.namgi("jiha_yeok", ["i": g.i, "nam": nam])
        if g.kkeutnam {
            dolgo = false
            poller?.invalidate()
            ticker?.invalidate()
            poller = nil
            ticker = nil
            motion.stopAccelerometerUpdates()
        }
    }

    /// 지금 몇 정거장 남았습니까
    func hyeonhwang() -> String {
        guard let y = yj.jigeum, let g = y.jiha else { return "지금은 지하철로 가고 있지 않습니다." }
        switch y.danggye {
        case .taneunGotKkaji:
            if g.ipguDochak { return "\(g.ipgu.ireum)에 닿았습니다. 열차가 움직이면 저절로 역 알림을 시작합니다. \(g.to)역까지 \(g.jina.count) 정거장입니다." }
            return "\(g.ipgu.ireum)까지 걸어가는 중입니다. 타신 뒤 \(g.to)역까지 \(g.jina.count) 정거장입니다."
        case .taneunJung:
            if g.kkeutnam { return "\(g.to)역에 닿았습니다. 밖으로 나오시면 남은 길을 걸어서 안내합니다." }
            if g.i < 0 { return "아직 첫 역을 지나지 않았습니다. \(g.to)역까지 \(g.jina.count) 정거장입니다." }
            return "\(JihacheolEngine.ireum(g.jina[g.i]))역을 지났습니다. \(g.to)역까지 \(g.jina.count - 1 - g.i) 정거장 남았습니다."
        default:
            return ""
        }
    }

    // ① 실시간 열차 위치
    private func silsi() {
        guard dolgo, !silsiMutneunJung, majimak <= Date(), !hoseon.isEmpty else { return }
        silsiMutneunJung = true
        let ho = hoseon
        Task {
            var rows: [[String: Any]] = []
            if let d = try? await Tongsin.shared.get("yeok.php", ["a": "silsi", "hoseon": ho]), !d.badadunGeot,
               let o = try? JSONSerialization.jsonObject(with: d.data) as? [String: Any], (o["ok"] as? Bool) == true {
                rows = (o["rows"] as? [[String: Any]]) ?? []
            }
            await MainActor.run { self.silsiBatda(rows) }
        }
    }

    private func silsiBatda(_ rows: [[String: Any]]) {
        silsiMutneunJung = false
        guard dolgo, let g = gil else { return }
        if rows.isEmpty { silsiMot += 1; silsiJal = false; return }
        silsiMot = 0
        var nae: [String: Any]?
        if !yeolcha.isEmpty { nae = rows.first { ($0["yeolcha"] as? String) == yeolcha } }
        if nae == nil {
            var chatja = [JihacheolEngine.ireum(from)]
            let s = max(0, g.i)
            for k in s..<min(g.jina.count, s + 2) { chatja.append(JihacheolEngine.ireum(g.jina[k])) }
            let hubo = rows.filter { chatja.contains(JihacheolEngine.ireum(($0["yeok"] as? String) ?? "")) }
            nae = hubo.first { !kkeut.isEmpty && JihacheolEngine.ireum(($0["jong"] as? String) ?? "") == JihacheolEngine.ireum(kkeut) } ?? hubo.first
            if let n = nae { yeolcha = (n["yeolcha"] as? String) ?? "" }
        }
        silsiJal = !yeolcha.isEmpty
        guard let n = nae else { return }
        let yeok = JihacheolEngine.ireum((n["yeok"] as? String) ?? "")
        guard let ja = g.jina.lastIndex(where: { JihacheolEngine.ireum($0) == yeok }) else { return }
        var bon = 0
        while (gil?.i ?? ja) < ja && !(gil?.kkeutnam ?? true) && bon < 12 {
            hanYeok()
            bon += 1
        }
    }

    // ③ 시간으로 셈하기
    private func sigan() {
        guard dolgo, let g = gil, !g.kkeutnam, !silsiJal else { return }
        let teom = g.jina.isEmpty ? 130.0 : max(60.0, Double(g.bun * 60) / Double(g.jina.count))
        if Date().timeIntervalSince(majimak) > teom * 1.35 { hanYeok() }
    }

    // ② 폰 흔들림 — 섰다 떠나기 세기, 탄 것 알아채기
    private func heundeullimSijak() {
        guard motion.isAccelerometerAvailable, !motion.isAccelerometerActive else { return }
        chang = []
        dallim = false
        seonTtae = nil
        motion.accelerometerUpdateInterval = 0.05
        motion.startAccelerometerUpdates(to: OperationQueue.main) { [weak self] d, _ in
            guard let self = self, let a = d?.acceleration else { return }
            self.heundeullim(a)
        }
    }

    private func heundeullim(_ a: CMAcceleration) {
        let k = sqrt(a.x * a.x + a.y * a.y + a.z * a.z)
        chang.append(k)
        if chang.count > 40 { chang.removeFirst() }
        guard chang.count >= 30 else { return }
        let p = chang.reduce(0, +) / Double(chang.count)
        let pc = sqrt(chang.map { ($0 - p) * ($0 - p) }.reduce(0, +) / Double(chang.count))
        let jigeumDallim = pc > 0.056
        let now = Date()
        if dallim && !jigeumDallim {
            seonTtae = now
            dallim = false
            dallimSijak = nil
        } else if !dallim && jigeumDallim {
            dallim = true
            dallimSijak = now
            dallimGeoreum = WichiEngine.shared.oneulGeoreum
            if dolgo, let s = seonTtae {
                let t = now.timeIntervalSince(s)
                if t > 8 && t < 120 && !silsiJal && now.timeIntervalSince(majimak) > 40 { hanYeok() }
            }
            seonTtae = nil
        }
        // 타는 역에 닿은 뒤 — 흔들리며 움직이는데 걸음이 없으면 열차에 탄 것
        if !dolgo && tabeumGamsi && dallim, let ds = dallimSijak, now.timeIntervalSince(ds) >= 15,
           WichiEngine.shared.oneulGeoreum - dallimGeoreum <= 3 {
            tabeumGamsi = false
            tatda(jadong: true)
        }
    }
}
