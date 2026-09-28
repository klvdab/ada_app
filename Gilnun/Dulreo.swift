// 둘러보기 탭의 자료 — 웹 길눈과 같은 나스 자료 창고를 그대로 씁니다 (앱 2.7.0, 빌드 260928-9).
//   dulle.php a=<종류>·a=chatgi&q=<낱말>   둘레 찾기(가까운 곳부터, 전화번호 함께)
//   matjip.php a=galae · a=chatgi&g=<갈래>  밥집 갈래와 밥집
//   gabolgot.php a=gabol|chukje|mujangae   가는 김에 — 가 볼 곳, 축제, 무장애 여행 정보
//   gojang.php                              고장 이야기(지나는 고장의 먹을 곳·볼 곳·축제)
//   miri.php a=yeojeong                     어디서 어디로 미리 들어 보기
//   sajin.php (POST)                        사진 읽어 주기
//   /masil/masil_*.js                       마실 이야기
import Foundation
import Combine
import JavaScriptCore

/// 둘레의 한 곳
struct Got: Identifiable, Hashable {
    let ireum: String
    let juso: String
    let jeonhwa: String
    let galae: String
    let lat: Double
    let lon: Double
    let meter: Double?
    let cheo: String
    let kkeut: String
    var id: String { "\(ireum)|\(lat)|\(lon)" }
    var jangso: Jangso { Jangso(ireum: ireum, juso: juso, lat: lat, lon: lon) }

    /// 지금 자리에서 몇 미터
    var geori: Double? {
        if let m = meter { return m }
        guard let w = WichiEngine.shared.jigeum, lat != 0 else { return nil }
        return WichiEngine.geori(w.lat, w.lon, lat, lon)
    }

    /// 목록 한 줄 — 이름, 거리, 갈래(마지막 한 마디), 축제는 여는 날
    var julMal: String {
        var m = ireum
        if let d = geori { m += " — " + Annae.geoMal(d) }
        let g = galae.components(separatedBy: ">").last?.trimmingCharacters(in: .whitespaces) ?? ""
        if !g.isEmpty { m += ", " + g }
        if !cheo.isEmpty { m += ", " + Got.nalMal(cheo, kkeut) }
        return m
    }

    /// 20261022, 20261025 → "10월 22일부터 25일까지"
    static func nalMal(_ a: String, _ b: String) -> String {
        func md(_ s: String) -> (Int, Int)? {
            guard s.count >= 8, let m = Int(s.dropFirst(4).prefix(2)), let d = Int(s.dropFirst(6).prefix(2)) else { return nil }
            return (m, d)
        }
        guard let x = md(a) else { return "" }
        guard let y = md(b), !(x.0 == y.0 && x.1 == y.1) else { return "\(x.0)월 \(x.1)일" }
        return x.0 == y.0 ? "\(x.0)월 \(x.1)일부터 \(y.1)일까지" : "\(x.0)월 \(x.1)일부터 \(y.0)월 \(y.1)일까지"
    }

    static func batgi(_ r: [String: Any]) -> Got? {
        guard let la = Chatgi.su(r["lat"]), let lo = Chatgi.su(r["lon"]) else { return nil }
        return Got(ireum: (r["ireum"] as? String) ?? "",
                   juso: (r["juso"] as? String) ?? "",
                   jeonhwa: (r["jeonhwa"] as? String) ?? "",
                   galae: (r["galae"] as? String) ?? "",
                   lat: la, lon: lo,
                   meter: Chatgi.su(r["meter"]) ?? Chatgi.su(r["m"]),
                   cheo: (r["cheo"] as? String) ?? "",
                   kkeut: (r["kkeut"] as? String) ?? "")
    }
}

/// 둘레 찾기 종류 — 웹 길눈 dulle.js 와 같은 차례
struct DulleJong: Identifiable, Hashable {
    let id: String        // dulle.php a= 값(없으면 낱말로 찾음)
    let ireum: String
    let natmal: String    // a=chatgi&q= 로 찾을 낱말
    let geot: Bool        // 겉에 두는가

    static let modu: [DulleJong] = [
        DulleJong(id: "bapjip", ireum: "식당 — 밥집 갈래를 고르십니다", natmal: "", geot: true),
        DulleJong(id: "cafe", ireum: "카페", natmal: "", geot: true),
        DulleJong(id: "byeongwon", ireum: "병원", natmal: "", geot: true),
        DulleJong(id: "yakguk", ireum: "약국", natmal: "", geot: true),
        DulleJong(id: "pyeonui", ireum: "편의점", natmal: "", geot: true),
        DulleJong(id: "", ireum: "화장실", natmal: "공중화장실", geot: true),
        DulleJong(id: "jeongryu", ireum: "버스정류장 — 가까운 정류장과 정류장 번호", natmal: "", geot: true),
        DulleJong(id: "eungeup", ireum: "응급실 — 가까운 응급의료기관", natmal: "", geot: true),
        DulleJong(id: "daepiso", ireum: "대피소 — 지진·민방위·무더위·한파", natmal: "", geot: true),
        DulleJong(id: "eunhaeng", ireum: "은행", natmal: "", geot: false),
        DulleJong(id: "mart", ireum: "대형마트", natmal: "", geot: false),
        DulleJong(id: "juchajang", ireum: "주차장", natmal: "", geot: false),
        DulleJong(id: "juyuso", ireum: "주유소", natmal: "", geot: false),
        DulleJong(id: "gwangwang", ireum: "관광명소", natmal: "", geot: false),
        DulleJong(id: "munhwa", ireum: "문화시설", natmal: "", geot: false),
        DulleJong(id: "gonggong", ireum: "공공기관", natmal: "", geot: false),
        DulleJong(id: "sukbak", ireum: "숙박", natmal: "", geot: false)
    ]

    static let daepi: [(String, String)] = [("지진 대피소", "지진옥외대피소"), ("민방위 대피소", "민방위대피소"),
                                            ("무더위 쉼터", "무더위쉼터"), ("한파 쉼터", "한파쉼터")]

    /// 말로 하기에서 — "근처 약국" 같은 말의 종류 찾기
    static func malEseo(_ z: String) -> DulleJong? {
        let pyo: [(String, String)] = [("약국", "yakguk"), ("병원", "byeongwon"), ("편의점", "pyeonui"), ("카페", "cafe"),
                                       ("커피", "cafe"), ("화장실", ""), ("응급실", "eungeup"), ("은행", "eunhaeng"),
                                       ("마트", "mart"), ("주차장", "juchajang"), ("주유소", "juyuso"), ("숙박", "sukbak"),
                                       ("모텔", "sukbak"), ("호텔", "sukbak"), ("식당", "sikdang"), ("밥집", "sikdang"),
                                       ("정류장", "jeongryu")]
        for (m, id) in pyo where z.contains(m) {
            if id.isEmpty { return modu.first { $0.natmal == "공중화장실" } }
            if id == "sikdang" { return DulleJong(id: "sikdang", ireum: "식당", natmal: "", geot: true) }
            return modu.first { $0.id == id }
        }
        return nil
    }
}

/// 밥집 갈래
struct BapjipGalae: Identifiable, Hashable {
    let id: String
    let ireum: String
}

enum Dulreo {
    private static func jari() -> [String: String]? {
        guard let w = WichiEngine.shared.jigeum else { return nil }
        return ["lat": String(format: "%.6f", w.lat), "lon": String(format: "%.6f", w.lon)]
    }

    private static func rows(_ pail: String, _ q0: [String: String]) async -> [Got]? {
        guard var q = jari() else { return nil }
        for (k, v) in q0 { q[k] = v }
        guard let d = try? await Tongsin.shared.getSae(pail, q),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else { return nil }
        if (o["ok"] as? Bool) != true { return [] }
        return ((o["rows"] as? [[String: Any]]) ?? []).compactMap { Got.batgi($0) }
    }

    /// 둘레 찾기 — 종류로, 또는 낱말로(1킬로미터 안)
    static func dulle(_ j: DulleJong) async -> [Got]? {
        if !j.natmal.isEmpty { return await rows("dulle.php", ["a": "chatgi", "q": j.natmal, "m": "1000"]) }
        return await rows("dulle.php", ["a": j.id, "m": "1000"])
    }

    static func daepiso(_ natmal: String) async -> [Got]? {
        await rows("dulle.php", ["a": "chatgi", "q": natmal, "m": "3000"])
    }

    static func bapjipGalae() async -> [BapjipGalae]? {
        guard let d = try? await Tongsin.shared.get("matjip.php", ["a": "galae"]),
              let o = try? JSONSerialization.jsonObject(with: d.data) as? [String: Any], (o["ok"] as? Bool) == true else { return nil }
        return ((o["rows"] as? [[String: Any]]) ?? []).compactMap { r in
            guard let id = r["id"] as? String, let nm = r["ireum"] as? String else { return nil }
            return BapjipGalae(id: id, ireum: nm)
        }
    }

    static func bapjip(_ g: String) async -> [Got]? {
        await rows("matjip.php", ["a": "chatgi", "g": g, "m": g == "sogae" ? "5000" : "1000"])
    }

    /// 가는 김에 — gabol(가 볼 곳), chukje(축제), mujangae(무장애 여행 정보), 10킬로미터 안
    static func gabol(_ a: String) async -> [Got]? {
        await rows("gabolgot.php", ["a": a, "m": "10000"])
    }

    /// 고장 이야기 — (말, 고장 열쇠)
    static func gojang() async -> (String, String)? {
        guard let q = jari(),
              let d = try? await Tongsin.shared.getSae("gojang.php", q),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else { return nil }
        guard (o["ok"] as? Bool) == true else { return ((o["error"] as? String) ?? "이 고장 이야기를 알아내지 못했습니다.", "") }
        return ((o["mal"] as? String) ?? "", (o["key"] as? String) ?? "")
    }

    /// 어디서 어디로 미리 들어 보기 — 출발지와 목적지 사이를 한 덩이 말로
    static func miri(_ chul: Jangso, _ chulNae: Bool, _ mok: Jangso) async -> String? {
        var q = ["a": "yeojeong", "slat": String(format: "%.6f", chul.lat), "slon": String(format: "%.6f", chul.lon),
                 "mlat": String(format: "%.6f", mok.lat), "mlon": String(format: "%.6f", mok.lon), "ireum": mok.ireum]
        q["bopok"] = String(format: "%.2f", Seoljeong.shared.bopok)
        guard let d = try? await Tongsin.shared.getSae("miri.php", q),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any], (o["ok"] as? Bool) == true else { return nil }
        var t: [String] = []
        if chulNae {
            if let s = await Chatgi.juso(chul.lat, chul.lon) { t.append("지금 \(s)에 계십니다.") }
        } else {
            t.append("\(chul.ireum)에서 떠나십니다.")
        }
        t.append("가시려는 곳은 \(mok.ireum)입니다.")
        if let c = o["cha"] as? [String: Any], let m = Chatgi.su(c["m"]) {
            t.append("차로 가시면 \(Annae.geoMal(m))를 달려 \((c["sigan"] as? String) ?? "") 걸립니다.")
            if chulNae, let tt = c["ttae"] as? String { t.append("지금 떠나시면 \(tt)쯤 닿습니다.") }
            if let dr = c["doro"] as? [String], !dr.isEmpty { t.append("지나는 길은 " + dr.joined(separator: ", ") + "입니다.") }
            if let jn = c["jinam"] as? [String], !jn.isEmpty { t.append("거치는 자리는 " + jn.joined(separator: ", ") + "입니다.") }
        } else if let jik = Chatgi.su(o["jik"]) {
            t.append("차로 가는 길은 지금 받지 못했습니다. 곧장 재면 약 \(Annae.geoMal(jik))입니다.")
        }
        if let g = o["geot"] as? [String: Any], let m = Chatgi.su(g["m"]), m <= 3000 {
            var s = "걸어가시면 약 \(Annae.geoMal(m)), \((g["sigan"] as? String) ?? "") 걸립니다."
            let georeum = Int(m / max(0.3, Seoljeong.shared.bopok))
            s += " 걸음으로는 \(georeum)걸음쯤입니다."
            t.append(s)
        }
        return t.joined(separator: " ")
    }

    /// 사진 읽어 주기 — mode: boki(보기), jasehi(자세히)
    static func sajin(_ jpeg: Data, _ mode: String) async -> (Bool, String) {
        let gyeong = "gilnun-" + UUID().uuidString
        var b = Data()
        func s(_ t: String) { b.append(t.data(using: .utf8)!) }
        s("--\(gyeong)\r\nContent-Disposition: form-data; name=\"mode\"\r\n\r\n\(mode)\r\n")
        s("--\(gyeong)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sajin.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n")
        b.append(jpeg)
        s("\r\n--\(gyeong)--\r\n")
        guard let d = await Tongsin.shared.postYangsik("sajin.php", b, "multipart/form-data; boundary=\(gyeong)"),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else {
            return (false, "사진을 보내지 못했습니다. 통신을 확인하시고 다시 해 보십시오.")
        }
        return ((o["ok"] as? Bool) == true, (o["mal"] as? String) ?? "읽지 못했습니다.")
    }
}

// MARK: 고장 이야기 — 차 안에서 고장이 바뀌면 한 번(설정에서 끔)

final class GojangEngine {
    static let shared = GojangEngine()
    private var majimakKey = ""
    private var majimakTtae = Date.distantPast
    private var mutneunJung = false
    private var sigye: Timer?

    func sijak() {
        sigye?.invalidate()
        sigye = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in self?.boda() }
    }

    private func boda() {
        guard Seoljeong.shared.gojangJadong, !mutneunJung,
              let y = YeojeongEngine.shared.jigeum, y.danggye == .taneunJung,
              YeojeongEngine.shared.talgeot != .jihacheol else { return }
        mutneunJung = true
        Task {
            let r = await Dulreo.gojang()
            DispatchQueue.main.async {
                self.mutneunJung = false
                guard let rr = r else { return }
                let mal = rr.0
                let key = rr.1
                guard !key.isEmpty, !mal.isEmpty else { return }
                // 같은 고장은 한 번만, 다만 열다섯 분이 지나면 한 번 더(웹 길눈과 같게)
                if key == self.majimakKey && Date().timeIntervalSince(self.majimakTtae) < 900 { return }
                self.majimakKey = key
                self.majimakTtae = Date()
                SoriEngine.shared.mal(mal, .jeongbo)
                Girok.shared.namgi("gojang_jadong", [:])
            }
        }
    }
}

// MARK: 마실 — 나스의 마실 이야기(masil_*.js)를 앱 안의 자바스크립트 틀로 읽음(웹과 같은 자료, 나스는 그대로)

struct MasilMadi {
    let t: String
    let mal: String
    let gyeokda: String
    let mut: String
    let bogi: [String]
    let dap: Int
    let matda: String
    let teulida: String
    var quiz: Bool { !mut.isEmpty }
}

struct MasilGojang: Identifiable {
    let id: String
    let ireum: String
    let jjalb: String
    let han: String
    let madi: [MasilMadi]
}

final class Masil: ObservableObject {
    static let shared = Masil()
    @Published private(set) var gojang: [MasilGojang] = []
    @Published private(set) var batneunJung = false
    private(set) var gyeokda: [String: [String]] = [:]

    func bureogi() async -> Bool {
        if !gojang.isEmpty { return true }
        await MainActor.run { self.batneunJung = true }
        defer { DispatchQueue.main.async { self.batneunJung = false } }
        guard let d = try? await Tongsin.shared.get("/masil/masil.html"), let html = String(data: d.data, encoding: .utf8) else { return false }
        var pail: [String] = []
        let re = try? NSRegularExpression(pattern: "masil_[a-z_]+\\.js")
        for m in re?.matches(in: html, range: NSRange(html.startIndex..., in: html)) ?? [] {
            if let r = Range(m.range, in: html) {
                let p = String(html[r])
                if !pail.contains(p) { pail.append(p) }
            }
        }
        guard let jc = JSContext() else { return false }
        jc.evaluateScript("var window = this;")
        for p in pail {
            if let d = try? await Tongsin.shared.get("/masil/" + p), let js = String(data: d.data, encoding: .utf8) {
                jc.evaluateScript(js)
            }
        }
        guard let s = jc.evaluateScript("JSON.stringify(window.MASIL_DATA || {gojang:[], gyeokda:{}})")?.toString(),
              let o = try? JSONSerialization.jsonObject(with: Data(s.utf8)) as? [String: Any] else { return false }
        let gl: [MasilGojang] = ((o["gojang"] as? [[String: Any]]) ?? []).map { g in
            let md: [MasilMadi] = ((g["madi"] as? [[String: Any]]) ?? []).map { m in
                let q = (m["quiz"] as? [String: Any]) ?? [:]
                return MasilMadi(t: (m["t"] as? String) ?? "", mal: (m["mal"] as? String) ?? "",
                                 gyeokda: (m["gyeokda"] as? String) ?? "",
                                 mut: (q["mut"] as? String) ?? "", bogi: (q["bogi"] as? [String]) ?? [],
                                 dap: Int(Chatgi.su(q["dap"]) ?? -1),
                                 matda: (q["matda"] as? String) ?? "", teulida: (q["teulida"] as? String) ?? "")
            }
            return MasilGojang(id: (g["id"] as? String) ?? UUID().uuidString, ireum: (g["ireum"] as? String) ?? "",
                               jjalb: (g["jjalb"] as? String) ?? "", han: (g["han"] as? String) ?? "", madi: md)
        }
        let gy = (o["gyeokda"] as? [String: [String]]) ?? [:]
        await MainActor.run {
            self.gojang = gl
            self.gyeokda = gy
        }
        Girok.shared.namgi("masil_bureogi", ["su": gl.count])
        return !gl.isEmpty
    }

    func hanjogak(_ kind: String) -> String {
        guard !kind.isEmpty, let l = gyeokda[kind], !l.isEmpty else { return "" }
        return l.randomElement() ?? ""
    }
}
