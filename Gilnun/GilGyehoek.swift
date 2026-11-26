// 여정 계획 — 길눈 2.142.0 (빌드 261126-I96, 대장클, 이사장님 승인 2026-11-26 「첫단계부터 끝까지 꼼꼼하게 해보자」)
// 「길눈아, 제주도 우도에 가는 길 알려 줘」처럼 물으시면 지금 자리에서 그곳까지 가장 알맞은 길을 길눈이 스스로 짜서
//   「첫째, 지하철로 김포공항까지 / 둘째, 비행기로 제주국제공항까지 / 셋째, 버스로 성산포항 종합여객터미널까지 / 넷째, 배로 우도까지」처럼 말씀드리고,
//   네 하시면 한 차례가 끝날 때마다 다음 탈것을 그때그때 알려 드리며 그 차례의 안내(걷기·지하철·버스·기차·배)를 시작함.
//   (11-26 「제주도 가자」 뒤 「교통편 알려줘」에서 무엇을 타고 갈지만 묻고 더 나가지 못했던 일)
// 첫 단계: 걸리는 시간은 보통 걸리는 시간으로 어림함(길 막힘·실제 시간표는 넣지 못함). 우도 배처럼 늘 다니는 배는 다니는 모양을 함께 알림.
//   둘째 단계(실시간 비행기·여객선·시외버스 시각)는 공공데이터 열쇠가 생기면 붙임.
// 비행기는 빠르기로 알아채지 않고(기차 알아채기를 흔들지 않게) 여정 계획이 스스로 다룸 — 공항에 닿으면 비행기 차례를 알리고, 내리는 공항 둘레에 오시면 다음 차례로.
import Foundation

/// 한 차례(구간)
struct Gugan: Codable, Equatable {
    /// georeum·jihacheol·beoseu·cha·gicha·bihaenggi·bae
    var tal: String
    var buteo: String
    var mok: String
    var lat: Double
    var lon: Double
    /// 대략 걸리는 분
    var bun: Int
    /// 이 차례를 시작할 때 덧붙일 말(챙길 것)
    var deo: String = ""
    /// 타기 전에 기다릴 분(비행기 한 시간 전 등) — 모두 걸리는 시간에만 더함
    var gidarim: Int = 0
    /// 마지막 차례인가(사용자가 고르신 바로 그곳 — 이름으로 다시 찾지 않음)
    var majimak: Bool = false
    /// 2차(2.142.0) 실제 시각 한 줄 — 「15시 30분 KTX, 부산역 18시 7분 도착」 (못 받았으면 빈 글)
    var sigak: String = ""
}

/// 짠 길 하나
struct Gyehoek: Codable, Equatable {
    var mokjeok: String
    var mlat: Double
    var mlon: Double
    var gugan: [Gugan]
    var i: Int = 0
    var sijak: Date = Date()
    /// 2차 실제 시각으로 셈한 닿는 때(못 셈하면 nil)
    var dochakTtae: Date? = nil
    var chong: Int { gugan.reduce(0) { $0 + $1.bun + $1.gidarim } }
}

final class GilGyehoek: ObservableObject {
    static let shared = GilGyehoek()

    /// 지금 따라가는 계획(없으면 nil)
    @Published private(set) var jigeum: Gyehoek?
    /// 여쭙는 중인 계획들(가장 알맞은 것이 맨 앞) — 「다른 길」이면 다음 것
    private(set) var hubo: [Gyehoek] = []
    private(set) var huboI = 0
    private var sigye: Timer?
    private static let KI = "gn.gilGyehoek"

    private init() {
        if let d = UserDefaults.standard.data(forKey: GilGyehoek.KI), let g = try? JSONDecoder().decode(Gyehoek.self, from: d),
           Date().timeIntervalSince(g.sijak) < 24 * 3600 {
            jigeum = g
            DispatchQueue.main.async { self.sigyeKyeogi() }
        }
    }

    // MARK: 여쭙기·정하기

    func jeanHagi(_ l: [Gyehoek]) {
        hubo = l
        huboI = 0
        Girok.shared.namgi("gyehoek_jean", ["mok": String((l.first?.mokjeok ?? "").prefix(30)), "su": l.count,
                                            "gil": l.first.map { GilGyehoek.galraeMal($0) } ?? "", "bun": l.first?.chong ?? 0])
    }
    var jeanJung: Gyehoek? { huboI < hubo.count ? hubo[huboI] : nil }

    /// 다음 길(없으면 nil)
    func dareunGil() -> Gyehoek? {
        guard !hubo.isEmpty else { return nil }
        huboI += 1
        if huboI >= hubo.count { return nil }
        Girok.shared.namgi("gyehoek_dareun", ["gil": GilGyehoek.galraeMal(hubo[huboI])])
        return hubo[huboI]
    }

    /// 말씀하신 탈것(배·비행기·기차·차)이 든 길로
    func talgeotGil(_ tal: String) -> Gyehoek? {
        guard let k = hubo.firstIndex(where: { g in g.gugan.contains { $0.tal == tal } }) else { return nil }
        huboI = k
        return hubo[k]
    }

    /// 여쭌 길로 정함 — 첫 차례를 돌려줌
    func hwakjeong() -> Gugan? {
        guard var g = jeanJung else { return nil }
        g.i = 0
        g.sijak = Date()
        jigeum = g
        hubo = []
        huboI = 0
        jeojang()
        sigyeKyeogi()
        Girok.shared.namgi("gyehoek_sijak", ["mok": String(g.mokjeok.prefix(30)), "gil": GilGyehoek.galraeMal(g), "su": g.gugan.count])
        return g.gugan.first
    }

    func geuman(_ iyu: String) {
        guard jigeum != nil || !hubo.isEmpty else { return }
        Girok.shared.namgi("gyehoek_geuman", ["iyu": iyu, "i": jigeum?.i ?? -1])
        jigeum = nil
        hubo = []
        huboI = 0
        sigye?.invalidate()
        sigye = nil
        UserDefaults.standard.removeObject(forKey: GilGyehoek.KI)
    }

    /// 다음 차례로 넘김 — 넘긴 차례를 돌려줌(마지막이었으면 nil 이고 계획을 마침)
    func daeum(_ iyu: String) -> Gugan? {
        guard var g = jigeum else { return nil }
        if g.i + 1 >= g.gugan.count {
            Girok.shared.namgi("gyehoek_kkeut", ["iyu": iyu])
            geuman("kkeut")
            return nil
        }
        g.i += 1
        jigeum = g
        jeojang()
        Girok.shared.namgi("gyehoek_daeum", ["i": g.i, "tal": g.gugan[g.i].tal, "iyu": iyu])
        return g.gugan[g.i]
    }

    private func jeojang() {
        if let g = jigeum, let d = try? JSONEncoder().encode(g) { UserDefaults.standard.set(d, forKey: GilGyehoek.KI) }
    }

    // MARK: 차례가 끝났는지 보기(15초마다)

    private func sigyeKyeogi() {
        sigye?.invalidate()
        guard jigeum != nil else { return }
        sigye = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { [weak self] _ in self?.boda() }
    }

    private func boda() {
        guard let g = jigeum, g.i < g.gugan.count, let w = WichiEngine.shared.jigeum else { return }
        let gu = g.gugan[g.i]
        let d = WichiEngine.geori(w.lat, w.lon, gu.lat, gu.lon)
        guard d <= GilGyehoek.dachamGeori(gu) else { return }
        if gu.majimak {
            // 마지막 차례는 걷기·탈것 안내가 도착을 알리므로 계획만 조용히 마침
            Girok.shared.namgi("gyehoek_kkeut", ["iyu": "dochak"])
            geuman("dochak")
            return
        }
        guard let n = daeum("dachaham") else { return }
        MalHagi.shared.guganSijak(n, ap: "\(gu.mok)\(GilGyehoek.ieGa(gu.mok)) 닿으셨습니다. ")
    }

    /// 이만큼 다가오면 그 차례를 마친 것으로 봄(미터)
    static func dachamGeori(_ g: Gugan) -> Double {
        switch g.tal {
        case "georeum": return g.majimak ? 40 : 60
        case "jihacheol": return 600
        case "beoseu", "cha": return 400
        case "gicha": return 1500
        case "bihaenggi": return 5000
        case "bae": return 1000
        default: return 300
        }
    }

    // MARK: 말

    static func talMal(_ t: String) -> String {
        switch t {
        case "georeum": return "걸어서"
        case "jihacheol": return "지하철로"
        case "beoseu": return "버스로"
        case "cha": return "차로"
        case "gicha": return "기차로"
        case "bihaenggi": return "비행기로"
        case "bae": return "배로"
        default: return ""
        }
    }

    static func bunMal(_ b: Int) -> String {
        if b < 60 { return "\(max(b, 1))분" }
        return b % 60 == 0 ? "\(b / 60)시간" : "\(b / 60)시간 \(b % 60)분"
    }

    static let chasu = ["첫째", "둘째", "셋째", "넷째", "다섯째", "여섯째", "일곱째", "여덟째"]

    /// 이 길이 어떤 갈래인가(기록·다른 길 말)
    static func galraeMal(_ g: Gyehoek) -> String {
        if g.gugan.contains(where: { $0.tal == "bihaenggi" }) { return "비행기" }
        if g.gugan.contains(where: { $0.tal == "bae" }) && !g.gugan.contains(where: { $0.tal == "bihaenggi" }) && jejuInga(g.mlat, g.mlon) { return "배" }
        if g.gugan.contains(where: { $0.tal == "gicha" }) { return "기차" }
        if g.gugan.allSatisfy({ $0.tal == "cha" || $0.tal == "georeum" }) { return "차" }
        return "대중교통"
    }

    /// 여쭐 때 말 — 모든 차례를 짧게
    static func jeanMal(_ g: Gyehoek, cheot: Bool) -> String {
        var t = cheot ? "\(g.mokjeok)까지 가장 알맞은 길을 짰습니다. " : "다른 길입니다. "
        t += g.gugan.count == 1 ? "한 차례로, 대략 \(bunMal(g.chong)) 걸립니다. " : "모두 \(g.gugan.count)차례, 대략 \(bunMal(g.chong)) 걸립니다. "
        for (k, gu) in g.gugan.enumerated() {
            let c = k < chasu.count ? chasu[k] : "\(k + 1)번째"
            t += "\(c), \(talMal(gu.tal)) \(gu.mok)까지 대략 \(bunMal(gu.bun)). "
            if !gu.sigak.isEmpty { t += gu.sigak + " " }
        }
        if let d = g.dochakTtae {
            let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "H시 m분"
            let nal = Calendar.current.isDateInToday(d) ? "" : (Calendar.current.isDateInTomorrow(d) ? "내일 " : "다음 날 이후 ")
            t += "지금 떠나시면 \(nal)\(f.string(from: d).replacingOccurrences(of: " 0분", with: ""))쯤 닿으십니다. "
        }
        if g.gugan.contains(where: { $0.tal == "bihaenggi" }) { t += "비행기는 뜨기 한 시간 전까지 공항에 닿으셔야 합니다. " }
        if g.gugan.contains(where: { $0.tal == "bae" }) { t += "배를 타실 때는 신분증이 꼭 있어야 합니다. " }
        t += g.gugan.contains(where: { !$0.sigak.isEmpty }) ? "시각은 오늘 운행표이고, 나머지 걸리는 시간은 보통 때의 어림입니다. " : "걸리는 시간은 보통 때의 어림입니다. "
        return t
    }

    /// 한 차례를 시작할 때 말
    static func guganMal(_ gu: Gugan, _ k: Int, _ su: Int) -> String {
        let c = k < chasu.count ? chasu[k] : "\(k + 1)번째"
        var t = su > 1 ? "\(c) 차례입니다. " : ""
        if gu.tal == "georeum" || k == 0 {
            t += "\(talMal(gu.tal)) \(gu.mok)까지 갑니다. 대략 \(bunMal(gu.bun)) 걸립니다. "
        } else {
            t += "\(gu.buteo)에서 \(talMal(gu.tal)) \(gu.mok)까지 갑니다. 대략 \(bunMal(gu.bun)) 걸립니다. "
        }
        if !gu.deo.isEmpty { t += gu.deo + " " }
        return t
    }

    /// 남은 차례 말
    func namEunMal() -> String {
        guard let g = jigeum else { return "따라가는 여정 계획이 없습니다." }
        var t = "\(g.mokjeok)까지 \(g.gugan.count)차례 가운데 지금 \(GilGyehoek.chasu[min(g.i, 7)]) 차례입니다. "
        for k in g.i..<g.gugan.count {
            let gu = g.gugan[k]
            t += "\(GilGyehoek.chasu[min(k, 7)]), \(GilGyehoek.talMal(gu.tal)) \(gu.mok)까지 대략 \(GilGyehoek.bunMal(gu.bun)). "
        }
        return t
    }

    static func ieGa(_ s: String) -> String { "에" }

    // MARK: 2차(2.142.0, 이사장님 승인 2026-11-26 「1 진행하고」) 실제 시각 — 협회 서버 gyotong.php(기차·여객선)·bihaeng.php(국내선)

    private static var moduGal: [String: [[String: Any]]] = [:]
    private static func modu(_ gal: String) async -> [[String: Any]] {
        if let m = moduGal[gal], !m.isEmpty { return m }
        guard let o = await Chatgi.json("gyotong.php", ["a": "modu", "gal": gal]), let r = o["rows"] as? [[String: Any]] else { return [] }
        moduGal[gal] = r
        return r
    }
    private static func idChatgi(_ rows: [[String: Any]], _ n: String) -> String? {
        if let r = rows.first(where: { ($0["ireum"] as? String) == n }) { return r["id"] as? String }
        return rows.first(where: { ($0["ireum"] as? String).map { !$0.isEmpty && (n.hasPrefix($0) || $0.hasPrefix(n)) } ?? false })?["id"] as? String
    }
    /// 우리 항구 이름 → 해양교통안전공단 이름
    private static let baeIreum: [(String, String)] = [("목포", "목포"), ("완도", "완도"), ("녹동", "녹동"), ("여수", "여수"), ("부산항", "부산"), ("제주항", "제주"),
        ("운진항", "모슬포"), ("포항", "포항"), ("강릉", "강릉"), ("묵호", "묵호"), ("후포", "후포"), ("인천", "인천"), ("통영", "통영"), ("격포", "격포"),
        ("군산", "군산"), ("마라도", "마라도 살레덕"), ("가파도", "가파도"), ("도동", "울릉(도동)"), ("울릉", "울릉(사동)")]
    private static func baeKomsa(_ n: String) -> String? { baeIreum.first { n.contains($0.0) }?.1 }
    private static let iata: [String: String] = ["김포공항": "GMP", "제주국제공항": "CJU", "김해국제공항": "PUS", "대구국제공항": "TAE", "청주국제공항": "CJJ",
        "무안국제공항": "MWX", "광주공항": "KWJ", "여수공항": "RSU", "사천공항": "HIN", "울산공항": "USN", "포항경주공항": "KPO", "군산공항": "KUV", "원주공항": "WJU", "양양국제공항": "YNY"]
    private static func hmMal(_ s: String) -> String {
        let c = Array(s)
        guard c.count >= 4, let h = Int(String(c[0...1])), let m = Int(String(c[2...3])) else { return s }
        return m == 0 ? "\(h)시" : "\(h)시 \(m)분"
    }
    private static func hmBun(_ s: String) -> Int? {
        let c = Array(s)
        guard c.count >= 4, let h = Int(String(c[0...1])), let m = Int(String(c[2...3])) else { return nil }
        return h * 60 + m
    }

    /// 이 차례를 t0 뒤에 떠날 수 있을 때 가장 빠른 실제 편 — (말 한 줄, 닿는 때). 받지 못하면 nil, 오늘 남은 편이 없으면 (말, nil)
    static func sigakChatgi(_ gu: Gugan, buteo t0: Date) async -> (String, Date?)? {
        let cal = Calendar.current
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyyMMdd"
        let nal = f.string(from: t0)
        let bun0 = cal.component(.hour, from: t0) * 60 + cal.component(.minute, from: t0)
        let jajeong = cal.startOfDay(for: t0)
        switch gu.tal {
        case "gicha":
            let rows = await modu("gicha")
            guard let a = idChatgi(rows, gu.buteo.replacingOccurrences(of: "역", with: "")),
                  let b = idChatgi(rows, gu.mok.replacingOccurrences(of: "역", with: "")),
                  let o = await Chatgi.json("gyotong.php", ["a": "pyeon", "gal": "gicha", "chul": a, "do": b, "nal": nal]),
                  let r = o["rows"] as? [[String: Any]] else { return nil }
            let l = r.compactMap { x -> (Int, Int, String)? in
                let d = "\(x["depplandtime"] ?? "")", ar = "\(x["arrplandtime"] ?? "")"
                guard d.count >= 12, ar.count >= 12, let db = hmBun(String(d.dropFirst(8))), let ab = hmBun(String(ar.dropFirst(8))) else { return nil }
                return (db, ab < db ? ab + 1440 : ab, "\(x["traingradename"] ?? "기차")")
            }.filter { $0.0 >= bun0 }.sorted { $0.0 < $1.0 }
            guard let c = l.first else { return r.isEmpty ? nil : ("오늘은 더 남은 기차가 없습니다.", nil) }
            let hd = String(format: "%02d%02d", c.0 / 60, c.0 % 60), ha = String(format: "%02d%02d", (c.1 / 60) % 24, c.1 % 60)
            return ("가장 빠른 기차는 \(hmMal(hd)) \(c.2)로, \(gu.mok)에 \(hmMal(ha))에 닿습니다.", jajeong.addingTimeInterval(Double(c.1) * 60))
        case "bae":
            let rows = await modu("bae")
            guard let ka = baeKomsa(gu.buteo), let kb = baeKomsa(gu.mok), let a = idChatgi(rows, ka), let b = idChatgi(rows, kb),
                  let o = await Chatgi.json("gyotong.php", ["a": "pyeon", "gal": "bae", "chul": a, "do": b, "nal": nal]),
                  let r = o["rows"] as? [[String: Any]] else { return nil }
            if r.isEmpty { return nil }
            let l = r.compactMap { x -> (Int, String, String)? in
                guard let tt = x["ttae"] as? String, let tb = hmBun(tt) else { return nil }
                return (tb, (x["bae"] as? String) ?? "배", (x["unhang"] as? String) ?? "")
            }.filter { $0.0 >= bun0 }.sorted { $0.0 < $1.0 }
            guard let c = l.first else { return ("오늘은 더 남은 배가 없습니다. 내일 첫 배를 확인하십시오.", nil) }
            let hd = String(format: "%02d%02d", c.0 / 60, c.0 % 60)
            let tongje = (!c.2.isEmpty && c.2 != "정상") ? " 다만 지금 운항 형편이 \(c.2)으로 나옵니다. 꼭 확인하십시오." : ""
            return ("오늘 남은 배 가운데 가장 빠른 것은 \(hmMal(hd)) \(c.1)입니다.\(tongje)", jajeong.addingTimeInterval(Double(c.0 + gu.bun - 15) * 60))
        case "bihaenggi":
            guard let a = iata[gu.buteo], let b = iata[gu.mok],
                  let o = await Chatgi.json("bihaeng.php", ["chul": a, "do": b, "nal": nal]),
                  let r = o["rows"] as? [[String: Any]], !r.isEmpty else { return nil }
            let l = r.compactMap { x -> (Int, Int, String)? in
                guard let s = x["chul"] as? String, let d = x["do"] as? String, let sb = hmBun(s), let db = hmBun(d) else { return nil }
                return (sb, db < sb ? db + 1440 : db, "\((x["hang"] as? String) ?? "") \((x["pyeon"] as? String) ?? "")편")
            }.filter { $0.0 >= bun0 + 60 }.sorted { $0.0 < $1.0 }
            guard let c = l.first else { return ("오늘은 타실 만한 비행기가 더 없습니다.", nil) }
            let hd = String(format: "%02d%02d", c.0 / 60, c.0 % 60), ha = String(format: "%02d%02d", (c.1 / 60) % 24, c.1 % 60)
            return ("타실 만한 비행기는 \(hmMal(hd)) \(c.2)으로, \(hmMal(ha))에 닿습니다.", jajeong.addingTimeInterval(Double(c.1) * 60))
        default:
            return nil
        }
    }

    /// 계획 전체에 실제 시각을 채움 — 차례마다 앞 차례가 끝나는 때부터 찾음(전체 6초 안, 넘으면 받은 데까지)
    static func sigakChaeugi(_ g0: Gyehoek) async -> Gyehoek {
        var g = g0
        var t = Date()
        var ok = true
        let kkeut = Date().addingTimeInterval(6)
        for k in g.gugan.indices {
            let gu = g.gugan[k]
            let r: (String, Date?)? = (["gicha", "bae", "bihaenggi"].contains(gu.tal) && Date() < kkeut) ? await sigakChatgi(gu, buteo: t) : nil
            if let r = r {
                g.gugan[k].sigak = r.0
                if let d = r.1 { t = d } else { ok = false; t = t.addingTimeInterval(Double(gu.bun + gu.gidarim) * 60) }
            } else {
                t = t.addingTimeInterval(Double(gu.bun + (gu.tal == "bihaenggi" ? gu.gidarim : 0)) * 60)
            }
        }
        if ok && g.gugan.contains(where: { !$0.sigak.isEmpty }) { g.dochakTtae = t }
        return g
    }

    /// 여쭙는 계획들에 실제 시각을 채워 둠
    func huboGochigi(_ l: [Gyehoek]) { hubo = l }

    // MARK: 길 묻는 말 알아듣기

    /// 「우도 가는 길 알려 줘」, 「우도 어떻게 가」, 「우도 가려면 뭐 타야 돼」 — 가실 곳 말(없으면 빈 글)을 돌려줌. 길 묻는 말이 아니면 nil
    static func gilMureum(_ t: String) -> String? {
        let z = MalSajeon.ttuk(t)
        guard z.count >= 3, z.count <= 40 else { return nil }
        let pyo = ["가는길좀알려", "가는길알려", "가는길가르쳐", "가는방법", "가는법", "가려면어떻게", "가려면뭐타", "가려면뭘타", "가려면무엇을타",
                   "어떻게가야", "어떻게가요", "어떻게가니", "어떻게가지", "어떻게갈수", "어떻게가", "뭐타고가", "뭘타고가", "무엇을타고가", "뭐타야",
                   "가는교통편", "교통편알려", "교통편좀알려", "교통편은", "교통편", "가는차편", "길알려줘", "가는코스", "코스짜줘", "여정짜줘"]
        var choe: (Int, Int)? = nil   // (z 안 자리, 길이)
        for p in pyo {
            if let r = z.range(of: p) {
                let k = z.distance(from: z.startIndex, to: r.lowerBound)
                if choe == nil || k < choe!.0 { choe = (k, p.count) }
            }
        }
        guard let c0 = choe else { return nil }
        let k = c0.0
        // z 의 k 자리까지를 띄어쓰기 있는 원래 말에서 자름
        var n = 0
        var cut = t.startIndex
        for i in t.indices {
            if n == k { cut = i; break }
            if !" \t\n.,!?~·'\"".contains(t[i]) { n += 1 }
            cut = t.index(after: i)
        }
        var q = String(t[t.startIndex..<cut]).trimmingCharacters(in: .whitespaces)
        for a in ["길눈아", "길눈", "하이 길눈", "여기서", "지금", "나는", "나", "저는", "제가", "내가"] where q.hasPrefix(a + " ") || q == a {
            q = String(q.dropFirst(a.count)).trimmingCharacters(in: .whitespaces)
        }
        for p in ["까지는", "까지", "으로", "에는", "에", "로", "은", "는"] where q.hasSuffix(p) && q.count > p.count + 1 {
            q = String(q.dropLast(p.count)).trimmingCharacters(in: .whitespaces)
            break
        }
        return q
    }

    // MARK: 길 짜기 — 자료

    private struct Got { let ireum: String; let lat: Double; let lon: Double }
    /// 제주 안 작은 섬 — 남북동서, 타는 곳, 내리는 곳, 배 분, 덧말
    private struct JageunSeom { let ireum: String; let la1, la2, lo1, lo2: Double; let tan: Got; let nae: Got; let bun: Int; let deo: String }
    private static let jageun: [JageunSeom] = [
        JageunSeom(ireum: "우도", la1: 33.485, la2: 33.530, lo1: 126.930, lo2: 126.980, tan: Got(ireum: "성산포항 종합여객터미널", lat: 33.4744, lon: 126.9330),
                   nae: Got(ireum: "우도 천진항", lat: 33.4964, lon: 126.9545), bun: 15,
                   deo: "우도 배는 대략 30분마다 다니고, 막배는 계절에 따라 저녁 대여섯 시쯤 끊깁니다. 신분증을 꼭 챙기시고, 승선신고서에 이름과 생년월일과 전화번호를 적습니다. 바람이 세면 배가 뜨지 않습니다."),
        JageunSeom(ireum: "마라도", la1: 33.105, la2: 33.125, lo1: 126.260, lo2: 126.280, tan: Got(ireum: "운진항", lat: 33.2178, lon: 126.2526),
                   nae: Got(ireum: "마라도 살레덕선착장", lat: 33.1179, lon: 126.2690), bun: 30,
                   deo: "마라도 배는 하루 몇 차례만 다니고 바람이 세면 뜨지 않습니다. 나서시기 전에 꼭 확인하시고 신분증을 챙기십시오."),
        JageunSeom(ireum: "가파도", la1: 33.158, la2: 33.182, lo1: 126.258, lo2: 126.288, tan: Got(ireum: "운진항", lat: 33.2178, lon: 126.2526),
                   nae: Got(ireum: "가파도 상동포구", lat: 33.1747, lon: 126.2737), bun: 15,
                   deo: "가파도 배는 하루 몇 차례만 다니고 바람이 세면 뜨지 않습니다. 나서시기 전에 꼭 확인하시고 신분증을 챙기십시오."),
        JageunSeom(ireum: "비양도", la1: 33.400, la2: 33.416, lo1: 126.218, lo2: 126.236, tan: Got(ireum: "한림항 비양도 도선 대합실", lat: 33.4140, lon: 126.2638),
                   nae: Got(ireum: "비양도 선착장", lat: 33.4087, lon: 126.2317), bun: 15,
                   deo: "비양도 배는 하루 몇 차례만 다닙니다. 나서시기 전에 꼭 확인하시고 신분증을 챙기십시오.")
    ]
    /// 제주 가는 배 — 항구 열쇠, 대략 분
    private static let jejuBae: [(String, Int)] = [("mokpo", 270), ("wando", 170), ("nokdong", 220), ("yeosu", 330), ("busan", 720)]
    /// 기차 — 이어진 선(같은 선 안의 두 역만 기차로 이음)
    private static let seon: [Set<String>] = [
        ["서울", "용산", "광명", "수서", "행신", "동탄", "평택지제", "수원", "천안아산", "오송", "대전", "김천구미", "동대구", "경주", "울산", "부산", "구포", "밀양", "포항", "창원중앙", "마산", "진주"],
        ["서울", "용산", "광명", "수서", "행신", "동탄", "평택지제", "수원", "천안아산", "오송", "공주", "서대전", "익산", "정읍", "광주송정", "목포", "전주", "남원", "순천", "여수엑스포"],
        ["서울", "청량리", "원주", "평창", "진부", "강릉", "동해"],
        ["청량리", "원주", "제천", "영주", "안동", "경주", "태화강", "부전"],
        ["청량리", "춘천"]
    ]
    private static let ktx: Set<String> = ["서울", "용산", "수서", "광명", "행신", "청량리", "수원", "평택지제", "동탄", "천안아산", "오송", "대전", "서대전", "김천구미", "동대구", "경주", "울산", "부산", "포항", "창원중앙", "마산", "진주", "밀양", "구포", "광주송정", "목포", "익산", "전주", "정읍", "공주", "남원", "순천", "여수엑스포", "강릉", "원주", "평창", "진부", "안동", "영주", "동해"]
    private static var juyoYeok: [(String, Double, Double)] {
        let s = seon.reduce(Set<String>()) { $0.union($1) }
        return GichaYeok.mokrok.filter { s.contains($0.0) }
    }

    private static func geori(_ a: Double, _ b: Double, _ c: Double, _ d: Double) -> Double { WichiEngine.geori(a, b, c, d) }
    static func jejuInga(_ la: Double, _ lo: Double) -> Bool { la >= 33.05 && la <= 33.65 && lo >= 126.05 && lo <= 127.00 }
    private static func jageunSeom(_ la: Double, _ lo: Double) -> JageunSeom? {
        jageun.first { la >= $0.la1 && la <= $0.la2 && lo >= $0.lo1 && lo <= $0.lo2 }
    }
    private static func iteona(_ a: String, _ b: String) -> Bool { seon.contains { $0.contains(a) && $0.contains(b) } }

    private static func G(_ tal: String, _ bu: String, _ mok: String, _ la: Double, _ lo: Double, _ bun: Double, _ deo: String = "", gid: Int = 0) -> Gugan {
        Gugan(tal: tal, buteo: bu, mok: mok, lat: la, lon: lo, bun: max(1, Int(bun.rounded())), deo: deo, gidarim: gid)
    }
    private static func chong(_ l: [Gugan]) -> Int { l.reduce(0) { $0 + $1.bun + $1.gidarim } }

    /// 같은 고장 안 — 걷기·지하철·버스(제주 안은 버스)
    private static func dongne(_ fn: String, _ fla: Double, _ flo: Double, _ tn: String, _ tla: Double, _ tlo: Double, jejuAn: Bool = false) -> [Gugan]? {
        let d = geori(fla, flo, tla, tlo)
        if d <= 1200 { return [G("georeum", fn, tn, tla, tlo, d * 1.3 / 66)] }
        if !jejuAn && Seom.jicheolItna(fla, flo) && Seom.jicheolItna(tla, tlo) && d <= 60_000 {
            return [G("jihacheol", fn, tn, tla, tlo, d * 1.25 / 550 + 12)]
        }
        if d <= 45_000 || jejuAn {
            return [G("beoseu", fn, tn, tla, tlo, d * 1.3 / (d > 15_000 ? 500 : 350) + 15, jejuAn ? "택시를 부르셔도 됩니다." : "")]
        }
        return nil
    }

    /// 가까운 주요 기차역(30킬로미터 안, 셋까지)
    private static func gakkaunYeok(_ la: Double, _ lo: Double) -> [((String, Double, Double), Double)] {
        juyoYeok.map { ($0, geori(la, lo, $0.1, $0.2)) }.filter { $0.1 <= 30_000 }.sorted { $0.1 < $1.1 }.prefix(3).map { $0 }
    }

    /// 육지 안 — 가까우면 동네 길, 멀면 기차 길과 차 길
    private static func yukji(_ fn: String, _ fla: Double, _ flo: Double, _ tn: String, _ tla: Double, _ tlo: Double) -> [[Gugan]] {
        let d = geori(fla, flo, tla, tlo)
        if d <= 45_000, let l = dongne(fn, fla, flo, tn, tla, tlo) { return [l] }
        var out: [[Gugan]] = []
        var choe: [Gugan]? = nil
        for a in gakkaunYeok(fla, flo) {
            for b in gakkaunYeok(tla, tlo) {
                let ya = a.0, yb = b.0
                if ya.0 == yb.0 || geori(ya.1, ya.2, yb.1, yb.2) < 40_000 || !iteona(ya.0, yb.0) { continue }
                let l1 = dongne(fn, fla, flo, ya.0 + "역", ya.1, ya.2) ?? [G("cha", fn, ya.0 + "역", ya.1, ya.2, a.1 * 1.35 / 1000 + 10)]
                let gd = geori(ya.1, ya.2, yb.1, yb.2)
                let sok: Double = (ktx.contains(ya.0) && ktx.contains(yb.0)) ? 2500 : 1500
                let l2 = [G("gicha", ya.0 + "역", yb.0 + "역", yb.1, yb.2, gd * 1.25 / sok + 15,
                            "표는 코레일톡 앱이나 역 창구에서 끊으시고, 역에서 도움이 필요하시면 역무원에게 말씀하십시오. 미리 신청하시면 타는 곳까지 안내를 받으실 수 있습니다.")]
                let l3 = dongne(yb.0 + "역", yb.1, yb.2, tn, tla, tlo) ?? [G("cha", yb.0 + "역", tn, tla, tlo, b.1 * 1.35 / 1000 + 10)]
                let l = l1 + l2 + l3
                if choe == nil || chong(l) < chong(choe!) { choe = l }
            }
        }
        if let c = choe { out.append(c) }
        out.append([G("cha", fn, tn, tla, tlo, d * 1.3 / 1150 + 10, "고속버스나 시외버스로 가셔도 됩니다.")])
        return out
    }

    /// 지금 자리에서 그곳까지 길들 — 가장 알맞은 것이 맨 앞
    static func jjagi(_ fla: Double, _ flo: Double, _ mok: Jangso) -> [Gyehoek] {
        let tn = mok.ireum, tla = mok.lat, tlo = mok.lon
        let fn = "지금 계신 곳"
        var plans: [(String, [Gugan])] = []
        let toJ = jejuInga(tla, tlo), frJ = jejuInga(fla, flo)
        let tj = jageunSeom(tla, tlo), fj = jageunSeom(fla, flo)
        let jejuGh = Seom.jejuGonghang
        let gonghang = Seom.gonghang.filter { $0.ireum != "인천국제공항" }
        let seomIreumMan = ["제주도", "제주", "제주특별자치도"].contains(tn)

        /// 제주에 닿은 뒤 남은 길
        func jejuSok(_ bn: String, _ bla: Double, _ blo: Double) -> [Gugan] {
            if let s = tj {
                var l = dongne(bn, bla, blo, s.tan.ireum, s.tan.lat, s.tan.lon, jejuAn: true) ?? []
                l.append(G("bae", s.tan.ireum, s.nae.ireum, s.nae.lat, s.nae.lon, Double(s.bun + 15), s.deo))
                if tn != s.ireum && geori(s.nae.lat, s.nae.lon, tla, tlo) > 150 {
                    l += dongne(s.nae.ireum, s.nae.lat, s.nae.lon, tn, tla, tlo, jejuAn: true) ?? []
                }
                return l
            }
            if seomIreumMan || geori(bla, blo, tla, tlo) < 300 { return [] }
            return dongne(bn, bla, blo, tn, tla, tlo, jejuAn: true) ?? []
        }

        if toJ && !frJ {
            for gh in gonghang {
                for yl in yukji(fn, fla, flo, gh.ireum, gh.lat, gh.lon) {
                    let fd = geori(gh.lat, gh.lon, jejuGh.lat, jejuGh.lon)
                    let b = [G("bihaenggi", gh.ireum, jejuGh.ireum, jejuGh.lat, jejuGh.lon, fd / 8000 + 15,
                               "비행기 뜨기 한 시간 전까지 공항에 닿으셔야 합니다. 표를 끊으실 때 항공사에 시각장애가 있다고 알리시면 공항에서 체크인부터 탑승까지 안내를 받으실 수 있습니다. 내리는 공항 둘레에 오시면 다음 차례를 알려 드립니다.", gid: 60)]
                    plans.append(("bihaeng", yl + b + jejuSok(jejuGh.ireum, jejuGh.lat, jejuGh.lon)))
                }
            }
            if let jh = Seom.hanggu["jejuhang"] {
                for (hk, bun) in jejuBae {
                    guard let h = Seom.hanggu[hk] else { continue }
                    for yl in yukji(fn, fla, flo, h.ireum, h.lat, h.lon) {
                        let b = [G("bae", h.ireum, jh.ireum, jh.lat, jh.lon, Double(bun + 40),
                                   "제주 가는 배는 하루 몇 차례뿐이니 미리 선사에 확인하시고, 신분증을 꼭 챙기십시오. 바람이 세면 배가 뜨지 않습니다.")]
                        plans.append(("bae", yl + b + jejuSok(jh.ireum, jh.lat, jh.lon)))
                    }
                }
            }
        } else if frJ && !toJ {
            let l1 = dongne(fn, fla, flo, jejuGh.ireum, jejuGh.lat, jejuGh.lon, jejuAn: true) ?? []
            for gh in gonghang {
                let fd = geori(gh.lat, gh.lon, jejuGh.lat, jejuGh.lon)
                let b = [G("bihaenggi", jejuGh.ireum, gh.ireum, gh.lat, gh.lon, fd / 8000 + 15,
                           "비행기 뜨기 한 시간 전까지 공항에 닿으셔야 합니다. 표를 끊으실 때 항공사에 시각장애가 있다고 알리시면 공항에서 안내를 받으실 수 있습니다. 내리는 공항 둘레에 오시면 다음 차례를 알려 드립니다.", gid: 60)]
                for yl in yukji(gh.ireum, gh.lat, gh.lon, tn, tla, tlo) { plans.append(("bihaeng", l1 + b + yl)) }
            }
        } else if toJ && frJ, let s = tj, s.ireum != fj?.ireum {
            var l = dongne(fn, fla, flo, s.tan.ireum, s.tan.lat, s.tan.lon, jejuAn: true) ?? []
            l.append(G("bae", s.tan.ireum, s.nae.ireum, s.nae.lat, s.nae.lon, Double(s.bun + 15), s.deo))
            if tn != s.ireum && geori(s.nae.lat, s.nae.lon, tla, tlo) > 150 {
                l += dongne(s.nae.ireum, s.nae.lat, s.nae.lon, tn, tla, tlo, jejuAn: true) ?? []
            }
            plans.append(("bae", l))
        } else if let sm = Seom.jarilo(tla, tlo), sm.ireum != "제주도", Seom.jarilo(fla, flo)?.ireum != sm.ireum {
            for hk in sm.bae {
                guard let h = Seom.hanggu[hk] else { continue }
                for yl in yukji(fn, fla, flo, h.ireum, h.lat, h.lon) {
                    let bd = geori(h.lat, h.lon, tla, tlo)
                    let b = [G("bae", h.ireum, tn, tla, tlo, bd * 1.05 / (bd > 80_000 ? 900 : 450) + 30,
                               sm.mal + " 배는 하루 몇 차례뿐이니 미리 선사에 확인하시고, 신분증을 꼭 챙기십시오. 바람이 세면 배가 뜨지 않습니다.")]
                    plans.append(("bae", yl + b))
                }
            }
        } else {
            for yl in yukji(fn, fla, flo, tn, tla, tlo) { plans.append(("yukji", yl)) }
        }

        // 바로 앞과 같은 자리로 가는 짧은 걸음은 뺌, 갈래마다 가장 짧은 것만
        var best: [String: [Gugan]] = [:]
        for (k, gs0) in plans {
            var gs: [Gugan] = []
            var pla = fla, plo = flo
            for g in gs0 {
                if g.tal == "georeum" && geori(pla, plo, g.lat, g.lon) < 150 { continue }
                gs.append(g); pla = g.lat; plo = g.lon
            }
            guard !gs.isEmpty else { continue }
            let kk = k + (gs.allSatisfy { $0.tal == "cha" || $0.tal == "georeum" } ? "_cha" : "")
            if best[kk] == nil || chong(gs) < chong(best[kk]!) { best[kk] = gs }
        }
        var l = best.values.sorted { chong($0) < chong($1) }
        // 차로만 가는 길이 맨 앞이어도, 대중교통 길이 1.5배 안이면 대중교통 길을 앞에(차는 누가 몰아 주셔야 하므로)
        if l.count >= 2, l[0].allSatisfy({ $0.tal == "cha" || $0.tal == "georeum" }),
           let k = l.firstIndex(where: { gs in !gs.allSatisfy { g in g.tal == "cha" || g.tal == "georeum" } }),
           Double(chong(l[k])) <= Double(chong(l[0])) * 1.5 {
            let d = l.remove(at: k); l.insert(d, at: 0)
        }
        return l.map { gs in
            var g2 = gs
            if !g2.isEmpty { g2[g2.count - 1].majimak = (g2[g2.count - 1].mok == tn) }
            return Gyehoek(mokjeok: tn, mlat: tla, mlon: tlo, gugan: g2)
        }
    }
}
