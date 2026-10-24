// 지하철 엔진 — 타는 역까지 걷기, 열차에 탄 것 알아채기, 지나는 역마다 알리기, 갈아타기, 내릴 역, 나갈 출구.
// 웹 길눈 tamseung.js 1.1(2026-09-18 이사장님 지시 "지하철을 타고 오는데 역 이름을 하나도 말해 주지 않더라")을 앱으로 옮김.
// 지나는 역은 세 겹으로 헤아립니다.
//   ① 서울 실시간 열차 위치(yeok.php a=silsi) — 우리가 탄 열차를 잡아 역마다 알림
//   ② 폰 흔들림으로 섰다 떠나는 것을 세어 몇 번째 역인지 헤아림(통신이 끊겨도 됨)
//   ③ 역 사이 걸리는 시간으로 셈함
// 타는 역 출구에 닿은 뒤 열차가 움직이는데 걸음이 없으면 "탔다"고 보고 저절로 역 알림을 시작합니다(손을 쓰지 않게).
// 2.40.0 (261003-T1, 2026-10-03 공덕 이마트 왕복에서 33분 동안 말이 없던 일을 바로잡음, 이사장님 승인)
//   ① 실시간 열차는 가는 방향이 맞고, 지금 역이나 바로 다음 역에 있는 열차만 "내 열차"로 붙잡음 — 처음 붙잡을 때 여러 역을 건너뛰지 않음
//   ② 붙잡은 열차가 지나간 시간에 맞지 않게 여러 역을 앞서 가면 엉뚱한 열차로 보고 놓음
//   ③ 실시간이 90초 넘게 내 열차를 확인해 주지 못하면 시간 세기·흔들림 세기가 곧바로 다시 맡음(예전에는 엉뚱한 열차를 쥔 채 둘 다 꺼 버렸음)
//   ④ 움직임 감지기의 "탈것이 섰다가 떠남"도 역 세기에 씀
//   ⑤ 땅 위로 나와 걷거나 위성이 다시 잡히면 지하철 안내를 저절로 마치고 걷는 안내로(사무실에 와서 역을 부르던 일)
//   ⑥ 타고 가는 중에도 시작(jungganSijak) — 땅속으로 내려가기 전 땅 위 자리로 가까운 역을 잡음
//   ⑦ 기압으로 계단·에스컬레이터를 내려가신 것을 알면 타는 역에 닿은 것으로 봄
import Foundation
import CoreMotion
import Combine

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
    /// 실시간 열차 위치가 내 열차를 마지막으로 확인해 준 때 — 90초가 지나면 다른 셈이 다시 맡음
    private var silsiHwagin = Date.distantPast
    private var silsiJal: Bool {
        get { !yeolcha.isEmpty && Date().timeIntervalSince(silsiHwagin) < 90 }
        set { if !newValue { silsiHwagin = .distantPast } }
    }
    private var ssak = Set<AnyCancellable>()
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
    private var tamTtae = Date.distantPast
    /// 후보 열차가 내 길의 몇 번째 역에 있었는지(다음 물음에서 앞으로 나아갔는지 봄)
    private var huboJikyeo: [String: Int] = [:]
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 세대 번호 — 멈추거나 탈것을 바로잡으시면 올려, 늦게 돌아온 지하철 길 찾기를 버림
    private(set) var sedae = 0
    /// 2.61.0 앱을 다시 켜서 이어 가는 중(탄 때를 모름) — 땅 위로 나온 것을 15초 걷기로만 봄
    private var ieoGamTamMoreum = false

    private var yj: YeojeongEngine { YeojeongEngine.shared }
    var gil: JihaGil? { yj.jigeum?.jiha }

    init() {
        // 움직임 감지기가 알려 주는 "탈것이 섰다가 떠남"으로도 역을 셈
        TalgeotGamji.shared.seotdaTteonam
            .receive(on: DispatchQueue.main)
            .sink { [weak self] t in
                guard let self = self, self.dolgo, t >= 8, t <= 120, !self.silsiJal,
                      Date().timeIntervalSince(self.majimak) > 40 else { return }
                self.hanYeok()
            }
            .store(in: &ssak)
        // 기압으로 땅속에 내려가신 것을 알면 — 타는 역 근처면 역에 닿은 것으로
        NotificationCenter.default.addObserver(forName: TalgeotGamji.jihaJinipAllim, object: nil, queue: .main) { [weak self] _ in
            guard let self = self, let y = self.yj.jigeum, y.danggye == .taneunGotKkaji, let g = y.jiha, !g.ipguDochak else { return }
            let w = TalgeotGamji.shared.jisangJari ?? WichiEngine.shared.jigeum
            if let w = w, WichiEngine.geori(w.lat, w.lon, g.ipgu.lat, g.ipgu.lon) > 300 { return }
            Girok.shared.namgi("jiha_ipgu_gido", [:])
            self.ipguDochak()
        }
    }

    static func ireum(_ n: String) -> String {
        n.hasSuffix("역") ? String(n.dropLast()) : n
    }

    // MARK: 길 찾기

    static func su(_ v: Any?) -> Double? { Chatgi.su(v) }

    /// 2.105.0 (261024-I59, 이사장님 지시 2026-10-24) 「6호선 탔어」처럼 말씀하신 호선 — 타는 역을 고를 때 그 호선 역을 앞에 둠(10분 동안)
    var hoseonHint: String? {
        get { hoseonHintTtae.map { Date().timeIntervalSince($0) < 600 } == true ? hoseonHint0 : nil }
        set { hoseonHint0 = newValue; hoseonHintTtae = newValue == nil ? nil : Date() }
    }
    private var hoseonHint0: String?
    private var hoseonHintTtae: Date?

    /// 2.105.0 역 이름 바로잡기 — 「약수역」「6호선 약수」를 지하철 길 자료의 이름(약수)으로. 호선을 주시면 그 호선에 있는 역만
    static func yeokIreum(_ q0: String, hoseon: String? = nil) async -> String? {
        var q = q0.replacingOccurrences(of: " ", with: "")
        if let r = q.range(of: "^[0-9]{1,2}호선", options: .regularExpression) { q.removeSubrange(r) }
        for k in ["지하철역", "전철역", "역"] where q.hasSuffix(k) && q.count > k.count { q = String(q.dropLast(k.count)); break }
        guard !q.isEmpty, let o = await Chatgi.json("yeok.php", ["a": "chatgi", "q": q]),
              let rows = o["rows"] as? [[String: Any]], !rows.isEmpty else { return nil }
        let ok = rows.filter { r in hoseon == nil || ((r["hoseon"] as? String) ?? "") == hoseon }
        let nms = ok.compactMap { $0["nm"] as? String }
        // 이름이 같거나 앞이 같은 역만(엉뚱한 역으로 맞추지 않게)
        return nms.first(where: { $0 == q }) ?? nms.first(where: { $0.hasPrefix(q) })
    }

    /// 지금 자리에서 목적지까지 지하철 길 — (길, 못 찾은 까닭)
    /// 2.105.0 (261024-I59, 이사장님 지시 2026-10-24) 가까운 역 하나만 보고 실패하던 것을 바로잡음 — 가까운 역 여럿 가운데 지하철 길 자료에 있는 역을 차례로 대 보고,
    ///   말씀하신 호선이 있으면 그 호선 역을 먼저, 타신 역을 말씀하셨으면(buteoYeok) 그 역에서
    ///   (6호선 땅속에서 엉뚱하게 잡힌 자리 옆의 경춘선 신내역을 골라 「그 역 이름을 자료에서 찾지 못했습니다」로 하루 내내 역 안내를 못 하던 일)
    static func gilChatgi(_ mok: Jangso, buteo: Wichi? = nil, buteoYeok: String? = nil, hoseon: String? = nil) async -> (JihaGil?, String) {
        var fromDeul: [Yeok] = []
        if let by = buteoYeok {
            var n0 = await yeokIreum(by, hoseon: hoseon)
            if n0 == nil && hoseon != nil { n0 = await yeokIreum(by) }
            guard let n = n0 else {
                return (nil, "\(by)\(by.hasSuffix("역") ? "" : "역")을 지하철 역 자료에서 찾지 못했습니다.")
            }
            // 그 역의 자리(그 역까지 걷는 안내에 씀) — 못 받으면 0
            var la = 0.0, lo = 0.0
            if let l = await Chatgi.jangso(n + "역"), let f = l.first(where: { $0.ireum.contains(n) }) { la = f.lat; lo = f.lon }
            fromDeul = [Yeok(yeok: n, ireum: n + "역", lat: la, lon: lo)]
        } else {
            // 2.105.0 (261024-I59) 자리가 아직 없거나 와이파이로 짐작한 자리면 타실 역을 정하지 않음 — 여쭘
            // 땅 위에서 10분 안에 위성이 확실히 잡은 자리가 있으면 그것을 씀(집 안 와이파이 자리로 묻지 않게)
            var w0 = buteo ?? WichiEngine.shared.jigeum
            if w0 == nil || w0!.jjimjak, !TalgeotGamji.shared.jiha, let m = WichiEngine.shared.majimakMideum, Date().timeIntervalSince(m.ttae) < 600 { w0 = m }
            guard let w = w0, !w.jjimjak else {
                Girok.shared.namgi("jiha_tanyeok_mureum", ["gil": true])
                return (nil, tanYeokMureum)
            }
            fromDeul = await gakkaunDeul(w.lat, w.lon)
            if let h = hoseon {
                let ap = fromDeul.filter { $0.ireum.contains(h) }
                fromDeul = ap + fromDeul.filter { !$0.ireum.contains(h) }
            }
        }
        let toDeul = Array(await gakkaunDeul(mok.lat, mok.lon).prefix(3))
        guard !fromDeul.isEmpty, !toDeul.isEmpty else { return (nil, "가까운 역을 받지 못했습니다. 통신이 끊겼을 수 있습니다.") }
        var o: [String: Any]? = nil
        var from = fromDeul[0], to = toDeul[0]
        var bon = 0
        bakkat: for f in fromDeul.prefix(5) {
            for t in toDeul {
                if f.yeok == t.yeok { continue }
                bon += 1
                if let r = await Chatgi.json("yeok.php", ["a": "gil", "from": f.yeok, "to": t.yeok]), (r["ok"] as? Bool) == true {
                    o = r; from = f; to = t
                    break bakkat
                }
            }
        }
        Girok.shared.namgi("jiha_gil_chatgi", ["from": from.yeok, "to": to.yeok, "ok": o != nil, "bon": bon, "hubo": fromDeul.prefix(5).map { $0.yeok }.joined(separator: ","),
                                               "son": buteoYeok != nil, "ho": hoseon ?? ""])
        guard let o = o else {
            if fromDeul.count == 1 && toDeul.contains(where: { $0.yeok == fromDeul[0].yeok }) {
                return (nil, "타실 역과 내리실 역이 같은 \(fromDeul[0].yeok)역입니다. 걸어가시는 편이 낫습니다.")
            }
            return (nil, "\(fromDeul[0].yeok)역에서 \(toDeul[0].yeok)역까지 가는 길을 찾지 못했습니다.")
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
        if buteoYeok == nil, let w = buteo ?? WichiEngine.shared.jigeum, let c = await chulgu(w.lat, w.lon), c.ireum.contains(from.yeok) { ipgu = c }
        let naeril = await chulgu(mok.lat, mok.lon)?.ireum ?? ""
        let g = JihaGil(from: from.yeok, to: to.yeok, mal: mal, bun: bun, jina: jina, gugan: gugan,
                        ipgu: ipgu, naeril: naeril.contains(to.yeok) ? naeril : "",
                        ipguDochak: false, i: -1, kkeutnam: false)
        return (g, "")
    }

    private struct Yeok { let yeok: String; let ireum: String; let lat: Double; let lon: Double }

    /// 2.40.0 가장 가까운 역까지의 거리(미터) — 역 근처에서 탄 것인지 볼 때
    static func gakkaunYeokGeori(_ lat: Double, _ lon: Double) async -> Double? {
        guard let y = await gakkaun(lat, lon) else { return nil }
        return WichiEngine.geori(lat, lon, y.lat, y.lon)
    }

    /// 2.105.0 가까운 역 여럿(가까운 차례)
    private static func gakkaunDeul(_ lat: Double, _ lon: Double) async -> [Yeok] {
        guard let o = await Chatgi.json("yeok.php", ["a": "gakkaun", "lat": String(format: "%.6f", lat), "lon": String(format: "%.6f", lon)]),
              let rows = o["rows"] as? [[String: Any]] else { return [] }
        return rows.compactMap { r in
            guard let la = su(r["lat"]), let lo = su(r["lon"]) else { return nil }
            return Yeok(yeok: (r["yeok"] as? String) ?? "", ireum: (r["ireum"] as? String) ?? "", lat: la, lon: lo)
        }
    }

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

    /// 2.92.0 (261017-I46, 이사장님 승인 2026-10-17) 다음 열차가 몇 분 뒤에 오는지 — 웹 길눈 jicheol.html(빌드 260926-11)에서 옮김.
    ///   타는 역의 실시간 도착(yeok.php a=dochak, 서울 열린데이터)에서 내 호선·방면 열차 가운데 가장 먼저 오는 것
    static func daeumYeolchaMal(_ g: JihaGil) async -> String? {
        guard let gg = g.gugan.first else { return nil }
        let yeok = g.from.hasSuffix("역") ? String(g.from.dropLast()) : g.from
        guard let o = await Chatgi.json("yeok.php", ["a": "dochak", "yeok": yeok]),
              let rows = o["rows"] as? [[String: Any]], !rows.isEmpty else { return nil }
        let ho = gg.hoseon.replacingOccurrences(of: " ", with: "")
        let bang = gg.bangmyeon.replacingOccurrences(of: "방면", with: "").replacingOccurrences(of: "행", with: "").trimmingCharacters(in: .whitespaces)
        var best: ([String: Any], Double)?
        for r in rows {
            let rh = ((r["hoseon"] as? String) ?? "").replacingOccurrences(of: " ", with: "")
            if !ho.isEmpty && !rh.isEmpty && rh != ho { continue }
            let rb = (r["bangmyeon"] as? String) ?? ""
            if !bang.isEmpty && !rb.contains(bang) { continue }
            let cho = Chatgi.su(r["cho"]) ?? 9999
            if best == nil || cho < best!.1 { best = (r, cho) }
        }
        guard let b = best else { return nil }
        let rb = (b.0["bangmyeon"] as? String) ?? ""
        let haeng = rb.components(separatedBy: " - ").first ?? rb
        let ml = (b.0["mal"] as? String) ?? ""
        let sigan: String
        if ml.contains("전역") || ml.contains("진입") || ml.contains("도착") {
            sigan = ml.contains("전역") ? "전 역을 떠났습니다" : "곧 들어옵니다"
        } else if b.1 < 9999 {
            let bun = Int(b.1) / 60, cho = Int(b.1) % 60
            sigan = (bun > 0 ? "\(bun)분" : "") + (cho > 0 ? " \(cho)초" : "") + " 뒤에 옵니다"
        } else { sigan = ml }
        return "다음 \(ho) \(haeng) 열차는 \(sigan.trimmingCharacters(in: .whitespaces))."
    }

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
        // 2.92.0 다음 열차가 몇 분 뒤에 오는지 이어서
        Task { if let m = await JihacheolEngine.daeumYeolchaMal(g) { SoriEngine.shared.mal(m) } }
        Girok.shared.namgi("jiha_ipgu", [:])
    }

    /// 열차에 탔습니다 — 누르셔도 되고, 저절로도 됨
    /// 2.60.0 역 입구를 거치지 않고 "이미 타고 가는 중"으로 짐작해 시작한 역 알림인가 — 땅 위를 차 빠르기로 달리면 차로 바로잡을 수 있음
    ///   (역 입구로 걸어 들어가 탄 것은 지상 구간을 달려도 그대로 둠)
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 여정 기록에도 담아 앱을 다시 켜도 이어 감
    private(set) var jungganJadong = false {
        didSet { if jungganJadong != oldValue { yj.jungganJadongNoki(jungganJadong) } }
    }

    /// 2.61.0 세대 번호를 올림(탈것을 바로잡으실 때)
    func sedaeOllim() { sedae += 1 }

    func tatda(jadong: Bool, tanYeokMal: Bool = false) {
        jungganJadong = false
        ieoGamTamMoreum = false
        guard var g = gil else { return }
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 이용자가 「지하철 탔어」라 하시거나 「열차에 탔습니다」를 누르신 때는
        //   바로잡기와 같게 — 늦게 돌아온 길 찾기를 버리고 콜 차 못 박기(3시간)를 풂
        if !jadong {
            sedaeOllim()
            ChaBureugi.shared.kolPulgi("barojapgi")
        }
        g.i = -1
        g.kkeutnam = false
        g.ipguDochak = true
        yj.jihaNoki(g)
        yj.talgeotJeonghagi(.jihacheol, barojabeum: true)
        yj.danggyeBakkugi(.taneunJung)
        junbi(g)
        majimak = Date()
        tamTtae = Date()
        let apmal = jadong ? "열차가 움직이는 것 같습니다. " : ""
        // 2.60.0 (이사장님 승인) 저절로 시작할 때는 단정하지 않고 바로잡는 말을 함께
        let dwimal = jadong ? " 지하철이 아니면 택시야라고 말씀해 주십시오." : ""
        // 2.105.0 (261024-I59) 타신 역을 짐작했으면 그 역을 밝히고 바로잡는 말을 알려 드림
        let tanMal = tanYeokMal ? "\(g.from)역에서 타신 것으로 압니다. 다르면 약수역에서 탔어처럼 말씀해 주십시오. " : ""
        SoriEngine.shared.mal(apmal + tanMal + "역 알림을 시작합니다. 내리실 역은 \(g.to)역, \(g.jina.count) 정거장 뒤입니다." + (g.bun > 0 ? " 약 \(g.bun)분 걸립니다." : "") + " 지나는 역마다 알려 드립니다." + dwimal)
        Girok.shared.namgi("jiha_tam", ["jadong": jadong])
        dolligi()
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 이용자가 지하철로 바로잡으심 — 짐작으로 시작한 안내가 아니므로 땅 위 구간(동호대교 등)에서 차로 되돌리지 않음
    func jadongPulgi() { jungganJadong = false }

    /// 2.40.0 이미 열차를 타고 가는 중에 시작 — 땅속으로 내려가기 전 땅 위 자리에서 가까운 역을 타는 역으로 잡고 바로 역 알림
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) jadong: 저절로 짐작해 시작했는가 — 이용자가 지하철이라고 하신 때(false)는 차로 저절로 되돌리지 않음
    /// 2.105.0 (261024-I59, 이사장님 지시 2026-10-24) 타신 역을 짐작할 믿을 만한 자리가 없어 여쭤야 함 — 이 말로 돌려줌
    static let tanYeokMureum = "?tanyeok"
    /// 2.105.0 중간에 지하철 길을 찾는 중
    private(set) var chatneunJung = false
    static var tanYeokMal: String {
        (TalgeotGamji.shared.jiha ? "땅속이라 위성이 닿지 않아 지금 자리를 확실히 알 수 없습니다." : "지금 자리를 확실히 알 수 없어 타실 역을 정하지 못했습니다.")
            + " 약수역에서 탈 거야처럼 타실 역을 말씀해 주십시오."
    }

    func jungganSijak(_ mok: Jangso, jadong: Bool = true, buteoYeok: String? = nil, _ kkeut: @escaping (Bool, String) -> Void) {
        // 2.105.0 (261024-I59) 땅속으로 내려가기 전 위성이 확실히 잡은 자리(1시간 안)만 타신 역을 짐작하는 데 씀 — 와이파이 짐작 자리로는 짐작하지 않고 여쭘
        var buteo: Wichi? = nil
        for c in [TalgeotGamji.shared.jisangJari, TalgeotGamji.shared.chaSijakJari, WichiEngine.shared.jigeum] {
            if let c = c, !c.jjimjak, Date().timeIntervalSince(c.ttae) < 3600, c.ochae <= 65 { buteo = c; break }
        }
        if buteoYeok == nil && buteo == nil {
            Girok.shared.namgi("jiha_tanyeok_mureum", ["jjimjak": WichiEngine.shared.jigeum?.jjimjak ?? true])
            kkeut(false, JihacheolEngine.tanYeokMureum)
            return
        }
        let ho = hoseonHint
        chatneunJung = true
        let sd = sedae   // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 찾는 사이 멈추거나 차로 바로잡으셨으면 버림
        Task {
            let (gg, k) = await JihacheolEngine.gilChatgi(mok, buteo: buteo, buteoYeok: buteoYeok, hoseon: ho)
            await MainActor.run {
                self.chatneunJung = false
                guard sd == self.sedae, let y = self.yj.jigeum, !(y.barojabeum && y.talgeot != .jihacheol) else {
                    Girok.shared.namgi("jiha_junggan_beorim", [:])
                    return
                }
                guard var g = gg else { kkeut(false, k); return }
                g.ipguDochak = true
                AnnaeEngine.shared.jihacheolGagi(mok, g, malEopsi: true)
                Girok.shared.namgi("jiha_junggan", ["from": g.from, "to": g.to])
                kkeut(true, "\(g.from)역에서 타신 것으로 보고 \(g.to)역까지 역을 알려 드립니다.")
                self.tatda(jadong: jadong, tanYeokMal: buteoYeok == nil)
                self.jungganJadong = jadong   // 2.61.0 (261009-I15) 이용자가 바로잡으신 지하철은 false — 땅 위 구간에서 차로 되돌리지 않음
            }
        }
    }

    /// 앱을 껐다 켰을 때 타고 가던 중이면 이어 감
    func ieoGagi() {
        guard let y = yj.jigeum, let g = y.jiha else { return }
        if y.danggye == .taneunJung && !g.kkeutnam {
            junbi(g)
            majimak = Date()
            dolligi()
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 짐작으로 시작한 안내였는지 되살리고, 탄 때를 모르니 땅 위로 나온 것은 15초 걷기로만 봄
            jungganJadong = y.jungganJadong ?? false
            ieoGamTamMoreum = true
        } else if y.danggye == .taneunGotKkaji && g.ipguDochak {
            tabeumGamsi = true
            heundeullimSijak()
        }
    }

    func meomchugi() {
        jungganJadong = false
        ieoGamTamMoreum = false
        sedae += 1   // 2.61.0 늦게 돌아온 길 찾기를 버림
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
        huboJikyeo = [:]
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
    private func hanYeok(malHam: Bool = true) {
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
        if malHam { SoriEngine.shared.mal(mal) }
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

    /// 2.105.0 (261024-I59, 이사장님 지시 2026-10-24) 「여기는 ○○역이야」 — 가는 길 위의 역이면 그 역으로 맞춤. 맞췄으면 알려 드릴 말, 길 위에 없으면 nil
    func yeokMatchugi(_ ireum: String) -> String? {
        guard let y = yj.jigeum, y.danggye == .taneunJung, var g = y.jiha, !g.kkeutnam else { return nil }
        let n = JihacheolEngine.ireum(ireum)
        let idx: Int
        if JihacheolEngine.ireum(g.from) == n { idx = -1 }
        else if let k = g.jina.firstIndex(where: { JihacheolEngine.ireum($0) == n }) { idx = k }
        else { return nil }
        g.i = idx
        yj.jihaNoki(g)
        junbi(g)
        majimak = Date()
        if !dolgo { dolligi() }
        Girok.shared.namgi("jiha_yeok_matchum", ["yeok": n, "i": idx])
        let nam = g.jina.count - 1 - idx
        if nam <= 0 {
            g.kkeutnam = true
            yj.jihaNoki(g)
            return "\(n)역으로 맞췄습니다. 내리실 역입니다. 내리십시오."
        }
        return "\(n)역으로 맞췄습니다. \(g.to)역까지 \(nam) 정거장 남았습니다." + (nam > 0 ? " 다음은 \(JihacheolEngine.ireum(g.jina[idx + 1]))역입니다." : "")
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
        if !yeolcha.isEmpty {
            nae = rows.first { ($0["yeolcha"] as? String) == yeolcha }
            if nae == nil { yeolcha = ""; silsiJal = false }   // 붙잡았던 열차가 사라짐 — 놓고 다시 찾음
        }
        if nae == nil {
            // 2.40.0 지금 역(지난 역)과 바로 다음 역에 있는 열차만, 가는 방향이 맞는 것만
            var chatja = [JihacheolEngine.ireum(g.i >= 0 ? g.jina[g.i] : from)]
            if g.i + 1 < g.jina.count { chatja.append(JihacheolEngine.ireum(g.jina[g.i + 1])) }
            let hubo = rows.filter { chatja.contains(JihacheolEngine.ireum(($0["yeok"] as? String) ?? "")) }
            if !kkeut.isEmpty {
                nae = hubo.first { JihacheolEngine.ireum(($0["jong"] as? String) ?? "") == JihacheolEngine.ireum(kkeut) }
            }
            // 방면 이름이 달리 오거나 모를 때 — 후보 열차를 지켜보다가 내 길을 따라 한 역 앞으로 나아간 열차만 붙잡음(반대 열차는 뒤로 감)
            if nae == nil {
                func jari(_ r: [String: Any]) -> Int? {
                    let y = JihacheolEngine.ireum((r["yeok"] as? String) ?? "")
                    if y == JihacheolEngine.ireum(from) && g.i < 0 { return -1 }
                    return g.jina.lastIndex { JihacheolEngine.ireum($0) == y }
                }
                var sae: [String: Int] = [:]
                for r in rows {
                    guard let id = r["yeolcha"] as? String, !id.isEmpty, let p = jari(r) else { continue }
                    sae[id] = p
                    if nae == nil, let ap = huboJikyeo[id], p == ap + 1, p <= g.i + 1 { nae = r }
                }
                huboJikyeo = sae
            }
            if let n = nae {
                yeolcha = (n["yeolcha"] as? String) ?? ""
                Girok.shared.namgi("jiha_yeolcha", ["yeolcha": yeolcha, "yeok": (n["yeok"] as? String) ?? ""])
            }
        }
        guard let n = nae, !yeolcha.isEmpty else { return }
        let yeok = JihacheolEngine.ireum((n["yeok"] as? String) ?? "")
        guard let ja = g.jina.lastIndex(where: { JihacheolEngine.ireum($0) == yeok }) else {
            // 내 길에 없는 역에 있는 열차 — 엉뚱한 열차
            Girok.shared.namgi("jiha_yeolcha_noh", ["kkadak": "길 밖", "yeok": yeok])
            yeolcha = ""; silsiJal = false
            return
        }
        // 지나간 시간에 비해 너무 많이 앞서 가면 엉뚱한 열차(역 사이 최소 1분 반)
        let heoyong = 1 + Int(Date().timeIntervalSince(majimak) / 90)
        if ja - g.i > heoyong {
            Girok.shared.namgi("jiha_yeolcha_noh", ["kkadak": "너무 앞섬", "ap": ja - g.i])
            yeolcha = ""; silsiJal = false
            return
        }
        if ja >= g.i { silsiHwagin = Date() }   // 내 열차가 지금 역이나 다음 역에 있음을 확인
        var bon = 0
        // 2.12.0 실시간으로 여러 역을 따라잡을 때는 조용히 넘기고 마지막 역(과 갈아타는 역)만 말함
        while (gil?.i ?? ja) < ja && !(gil?.kkeutnam ?? true) && bon < 12 {
            let daeum = (gil?.i ?? ja) + 1
            let galaTa = hwanJa.contains(daeum)
            hanYeok(malHam: daeum >= ja || galaTa)
            bon += 1
            if galaTa { break }
        }
    }

    // ③ 시간으로 셈하기
    private func sigan() {
        // 2.40.0 땅 위로 나와 걸으시거나 위성이 다시 잡히면 지하철 안내를 마치고 걷는 안내로
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 앱을 다시 켜 탄 때를 모르면 위성만으로 마치지 않고 15초 넘게 걸으셔야
        if dolgo, let g = gil, !g.kkeutnam, Date().timeIntervalSince(tamTtae) > 120,
           !TalgeotGamji.shared.jiha, TalgeotGamji.shared.chujeong == .georeum, TalgeotGamji.wiseongJoeum,
           !ieoGamTamMoreum || TalgeotGamji.shared.georeum15cho {
            Girok.shared.namgi("jiha_kkeut_jisang", ["i": g.i])
            meomchugi()
            var gg = g
            gg.kkeutnam = true
            yj.jihaNoki(gg)
            AnnaeEngine.shared.naeryeotda(jadong: true, mal: "땅 위로 나오신 것 같습니다. 지하철 안내를 마치고 남은 길을 걸어서 안내합니다.")
            return
        }
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
        // 2.12.0 긴 에스컬레이터를 열차로 잘못 알지 않게 30초
        if !dolgo && tabeumGamsi && dallim, let ds = dallimSijak, now.timeIntervalSince(ds) >= 30,
           WichiEngine.shared.oneulGeoreum - dallimGeoreum <= 3 {
            tabeumGamsi = false
            tatda(jadong: true)
        }
    }
}
