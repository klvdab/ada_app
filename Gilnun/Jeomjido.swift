// 점지도 자료 — 앱 2.10.0 (빌드 260928-12)
// 웹 길눈의 점지도 창고(jeom.php)를 그대로 씁니다.
//   a=list  점지도 목록      a=get&id  한 길의 점과 표시      a=find&lat&lon  가까운 길부터
//   a=save  점지도에 올리기   a=jimyeong  좌표를 곳 이름으로   a=bopok  보폭 잰 것 남기기
//   a=naeput / naelist / naedel  나만의 점지도를 잠가 맡기기(잠금말은 서버로 가지 않음)
// 나만의 점지도는 폰 안(길눈 자료 칸, 백업 제외)에 둡니다.
import Foundation
import CryptoKit
import CommonCrypto

struct JeomJeom: Codable {
    var lat: Double
    var lon: Double
    var acc: Double?
    var h: Double?
    var t: Double?
}

struct JeomPyo: Codable {
    var lat: Double?
    var lon: Double?
    var name: String?
    var kind: String?
    var cnt: Int?
    var mal: String?
    var t: Double?
    var acc: Double?
    var dist: Double?

    var ireum: String {
        let n = (name ?? "").trimmingCharacters(in: .whitespaces)
        if !n.isEmpty { return n }
        let k = (kind ?? "").trimmingCharacters(in: .whitespaces)
        return k.isEmpty ? "표시" : k
    }
}

struct JeomGil: Codable, Identifiable {
    var id: String
    var title: String
    var from: String
    var to: String
    var who: String
    var made: String
    var dist: Double
    var pts: [JeomJeom]
    var marks: [JeomPyo]
    var nae: Bool = false
    var matgim: Bool = false
    var ollim: Bool = false

    static func batgi(_ o: [String: Any]) -> JeomGil? {
        let pts: [JeomJeom] = ((o["pts"] as? [[String: Any]]) ?? []).compactMap { p in
            guard let la = Chatgi.su(p["lat"]), let lo = Chatgi.su(p["lon"]), la != 0, lo != 0 else { return nil }
            return JeomJeom(lat: la, lon: lo, acc: Chatgi.su(p["acc"]), h: Chatgi.su(p["h"]), t: Chatgi.su(p["t"]))
        }
        let marks: [JeomPyo] = ((o["marks"] as? [[String: Any]]) ?? []).map { m in
            JeomPyo(lat: Chatgi.su(m["lat"]), lon: Chatgi.su(m["lon"]), name: m["name"] as? String, kind: m["kind"] as? String,
                    cnt: Chatgi.su(m["cnt"]).map { Int($0) }, mal: m["mal"] as? String, t: Chatgi.su(m["t"]),
                    acc: Chatgi.su(m["acc"]), dist: Chatgi.su(m["dist"]))
        }
        return JeomGil(id: Nas.gul(o["id"]), title: Nas.gul(o["title"]), from: Nas.gul(o["from"]), to: Nas.gul(o["to"]),
                       who: Nas.gul(o["who"]), made: Nas.gul(o["made"]), dist: Chatgi.su(o["dist"]) ?? 0,
                       pts: pts, marks: marks)
    }

    /// 되돌아가는 길 — 점을 거꾸로, 표시 이름도 거꾸로(오른쪽 꺾임 ↔ 왼쪽 꺾임 등)
    func dwit() -> JeomGil {
        var g = self
        g.pts = pts.reversed()
        g.marks = marks.map { m in
            var x = m
            if let n = m.name { x.name = JeomGil.DWIT[n] ?? n }
            return x
        }
        g.from = to
        g.to = from
        return g
    }

    static let DWIT: [String: String] = [
        "왼쪽으로 꺾임": "오른쪽으로 꺾임", "오른쪽으로 꺾임": "왼쪽으로 꺾임",
        "올라가는 계단 시작": "내려가는 계단 시작", "내려가는 계단 시작": "올라가는 계단 시작",
        "오름턱": "내림턱", "내림턱": "오름턱",
        "횡단보도 건너기 시작": "횡단보도 건너기 끝", "횡단보도 건너기 끝": "횡단보도 건너기 시작",
        "엘리베이터 올라감": "엘리베이터 내려감", "엘리베이터 내려감": "엘리베이터 올라감"
    ]
}

/// 점지도 목록의 한 줄(가까운 길부터)
struct JeomMok: Identifiable, Hashable {
    let id: String
    let title: String
    let from: String
    let to: String
    let who: String
    let dist: Double
    let near: Double
    let pyo: Int
    let slat: Double, slon: Double, elat: Double, elon: Double

    var julMal: String {
        var m = title.isEmpty ? "\(from)에서 \(to)까지" : title
        m += ", \(Annae.geoMal(dist))"
        if near >= 0 { m += near < 15 ? ", 지금 이 길 위" : ", 여기서 \(Annae.geoMal(near))" }
        if pyo > 0 { m += ", 표시 \(pyo)개" }
        if !who.isEmpty { m += ", 그린 분 \(who)" }
        return m
    }
}

enum Jeomjido {
    static let MUNJE = ["점자블록이 없어졌습니다", "공사 중입니다", "무엇인가 길을 막고 있습니다",
                        "소리 안내가 나지 않습니다", "바닥이 파였거나 턱이 생겼습니다", "그 밖의 문제"]

    static func jari(_ la: Double, _ lo: Double) -> [(String, String)] {
        [("lat", String(format: "%.7f", la)), ("lon", String(format: "%.7f", lo))]
    }

    /// 가까운 점지도부터(내 자리 기준) — 못 받으면 nil
    static func gakkaun(_ la: Double, _ lo: Double) async -> [JeomMok]? {
        guard let o = await Nas.get("jeom.php", [("a", "find")] + jari(la, lo)) else { return nil }
        return ((o["rows"] as? [[String: Any]]) ?? []).compactMap { r in
            let id = Nas.gul(r["id"])
            guard !id.isEmpty else { return nil }
            return JeomMok(id: id, title: Nas.gul(r["title"]), from: Nas.gul(r["from"]), to: Nas.gul(r["to"]),
                           who: Nas.gul(r["who"]), dist: Chatgi.su(r["dist"]) ?? 0, near: Chatgi.su(r["near"]) ?? -1,
                           pyo: Int(Chatgi.su(r["marks"]) ?? 0),
                           slat: Chatgi.su(r["slat"]) ?? 0, slon: Chatgi.su(r["slon"]) ?? 0,
                           elat: Chatgi.su(r["elat"]) ?? 0, elon: Chatgi.su(r["elon"]) ?? 0)
        }
    }

    /// 한 길 불러오기 — 나만의 점지도(nae_)는 폰 안에서
    static func bulleoogi(_ id: String) async -> JeomGil? {
        if id.hasPrefix("nae_") { return NaeGil.shared.chatgi(id) }
        guard let o = await Nas.get("jeom.php", [("a", "get"), ("id", id)]) else { return nil }
        return JeomGil.batgi(o)
    }

    /// 걸어가실 곳에 맞는 점지도 — 시작이 가까이(50미터 안) 있고 끝이 목적지 가까이(80미터 안)인 길. 거꾸로 걸어도 맞으면 되돌아가기로.
    static func matneunGil(_ me: Wichi, _ mok: Jangso) async -> (JeomMok, Bool)? {
        guard let r = await gakkaun(me.lat, me.lon) else { return nil }
        var best: (JeomMok, Bool, Double)?
        for m in r {
            let eo = m.near >= 0 ? m.near : WichiEngine.geori(me.lat, me.lon, m.slat, m.slon)
            guard eo <= 50 else { continue }
            let ap = WichiEngine.geori(mok.lat, mok.lon, m.elat, m.elon)
            let dw = WichiEngine.geori(mok.lat, mok.lon, m.slat, m.slon)
            if ap <= 80, best == nil || ap < best!.2 { best = (m, false, ap) }
            if dw <= 80, best == nil || dw < best!.2 { best = (m, true, dw) }
        }
        for g in NaeGil.shared.mokrok where g.pts.count >= 3 {
            guard let s = g.pts.first, let e = g.pts.last else { continue }
            let jari = g.pts.map { WichiEngine.geori(me.lat, me.lon, $0.lat, $0.lon) }.min() ?? 1e9
            guard jari <= 50 else { continue }
            let ap = WichiEngine.geori(mok.lat, mok.lon, e.lat, e.lon)
            let dw = WichiEngine.geori(mok.lat, mok.lon, s.lat, s.lon)
            let jm = JeomMok(id: g.id, title: g.title, from: g.from, to: g.to, who: "", dist: g.dist, near: jari,
                             pyo: g.marks.count, slat: s.lat, slon: s.lon, elat: e.lat, elon: e.lon)
            if ap <= 80, best == nil || ap < best!.2 { best = (jm, false, ap) }
            if dw <= 80, best == nil || dw < best!.2 { best = (jm, true, dw) }
        }
        return best.map { ($0.0, $0.1) }
    }

    /// 좌표를 곳 이름으로(가까운 곳, 주소)
    static func jimyeong(_ la: Double, _ lo: Double) async -> String {
        guard let o = await Nas.get("jeom.php", [("a", "jimyeong")] + jari(la, lo)), (o["ok"] as? Bool) ?? false else { return "" }
        for k in ["gakkaun", "juso", "mal"] {
            let v = Nas.gul(o[k]).trimmingCharacters(in: .whitespaces)
            if !v.isEmpty { return v }
        }
        return ""
    }

    /// 모두가 쓰도록 점지도에 올리기
    static func olligi(_ g: JeomGil) async -> (Bool, String) {
        guard g.pts.count >= 5, let e = g.pts.last else { return (false, "자리가 적어 올릴 수 없습니다.") }
        var kkeut = await jimyeong(e.lat, e.lon)
        let nm = g.title.isEmpty ? "내 길" : g.title
        if kkeut.isEmpty { kkeut = nm + " 끝" }
        let pts: [[String: Any]] = g.pts.map { p in
            var d: [String: Any] = ["lat": p.lat, "lon": p.lon]
            if let a = p.acc { d["acc"] = a }
            if let h = p.h { d["h"] = h }
            if let t = p.t { d["t"] = t }
            return d
        }
        let marks: [[String: Any]] = g.marks.map { m in
            var d: [String: Any] = ["name": m.name ?? m.ireum, "mal": m.mal ?? ""]
            if let a = m.lat { d["lat"] = a }
            if let o = m.lon { d["lon"] = o }
            if let t = m.t { d["t"] = t }
            if let c = m.acc { d["acc"] = c }
            return d
        }
        let body: [String: Any] = ["title": "\(nm) 에서 \(kkeut) 까지", "from": nm, "to": kkeut, "who": "본인", "who_kind": "sigak",
                                   "dist": Int(g.dist), "secs": 0, "steps": 0, "stride": 0, "pts": pts, "marks": marks]
        guard let o = await Nas.postJson("jeom.php", [("a", "save")], body) else { return (false, "보내지 못했습니다. 잠시 뒤 다시 눌러 주십시오.") }
        if (o["ok"] as? Bool) ?? false { return (true, kkeut) }
        return (false, "보내지 못했습니다. " + Nas.gul(o["msg"]))
    }

    /// 보폭 잰 것을 나스에 남김(웹과 같은 자리)
    static func bopokNamgigi(_ m: Double, _ n: Int, _ s: Double, _ mode: String) {
        Task { _ = await Nas.get("jeom.php", [("a", "bopok"), ("m", String(format: "%.1f", m)), ("n", String(n)),
                                             ("s", String(format: "%.3f", s)), ("mo", mode)]) }
    }

    /// 여기 문제 있어요
    static func munje(_ k: String, _ mal: String, _ w: Wichi?) async -> String {
        var q: [(String, String)] = [("a", "georim"), ("kind", k), ("mal", mal), ("who_kind", "sigak")]
        if let w = w { q += jari(w.lat, w.lon) } else { q += [("lat", ""), ("lon", "")] }
        guard let o = await Nas.get("jeom_db.php", q) else { return "적어 두지 못했습니다. 통신을 확인해 주십시오." }
        let dama = Int(Chatgi.su(o["dama"]) ?? 1)
        return "적어 두었습니다. \(k). 이 자리에서 \(max(1, dama))번째입니다."
    }

    /// 걸음 오차 재기 결과 담기(jaegi.php)
    static func jaegiDamgi(_ mal: [(String, String)]) async -> String {
        guard let u = URL(string: "https://lvd.ada.or.kr/jeom/jaegi.php?a=damgi") else { return "담지 못했습니다." }
        var hm = CharacterSet.alphanumerics
        hm.insert(charactersIn: "-._~")
        let b = (mal + [("nuga", "")]).map { "\($0.0)=\($0.1.addingPercentEncoding(withAllowedCharacters: hm) ?? "")" }.joined(separator: "&")
        var r = URLRequest(url: u)
        r.httpMethod = "POST"
        r.httpBody = b.data(using: .utf8)
        r.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        r.timeoutInterval = 20
        guard let dr = try? await URLSession.shared.data(for: r),
              let o = (try? JSONSerialization.jsonObject(with: dr.0)) as? [String: Any] else { return "담지 못했습니다. 통신을 확인해 주십시오." }
        let m = Nas.gul(o["msg"])
        return m.isEmpty ? "담았습니다." : m
    }

    struct JaegiJul: Identifiable {
        let id: Int
        let mal: String
    }

    static func jaegiMokrok() async -> [JaegiJul]? {
        guard let o = await Nas.get("jaegi.php", [("a", "mokrok")]) else { return nil }
        let rows = (o["rows"] as? [[String: Any]]) ?? []
        return rows.enumerated().map { i, r in
            let sil = Chatgi.su(r["sil"]) ?? 0, chu = Chatgi.su(r["chujeong"]) ?? 0
            let og = sil > 0 ? abs(chu - sil) / sil * 100 : 0
            var nal = ""
            if let t = Chatgi.su(r["at"]), t > 0 {
                let c = Calendar.current.dateComponents([.month, .day], from: Date(timeIntervalSince1970: t))
                nal = "\(c.month ?? 0)월 \(c.day ?? 0)일 · "
            }
            let m = nal + "\(Nas.gul(r["sokdo"])) · 지팡이 \(Nas.gul(r["jipangi"])) · \(Int(Chatgi.su(r["geoleum"]) ?? 0))걸음 · 오차 "
                + String(format: "%.1f", og) + "퍼센트"
            return JaegiJul(id: i, mal: m)
        }
    }
}

// MARK: 나만의 점지도 — 폰 안에 두고, 원하실 때만 잠가 맡김

final class NaeGil: ObservableObject {
    static let shared = NaeGil()
    @Published private(set) var mokrok: [JeomGil] = []

    private var pail: URL? {
        guard let d = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else { return nil }
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d.appendingPathComponent("gilnun_naegil.json")
    }

    init() {
        if let p = pail, let d = try? Data(contentsOf: p), let l = try? JSONDecoder().decode([JeomGil].self, from: d) {
            mokrok = l
        }
    }

    private func jeojang() {
        guard var p = pail, let d = try? JSONEncoder().encode(mokrok) else { return }
        try? d.write(to: p, options: .atomic)
        var v = URLResourceValues()
        v.isExcludedFromBackup = true
        try? p.setResourceValues(v)
    }

    func chatgi(_ id: String) -> JeomGil? { mokrok.first { $0.id == id } }

    func damgi(_ g: JeomGil) {
        if let i = mokrok.firstIndex(where: { $0.id == g.id }) { mokrok[i] = g } else { mokrok.insert(g, at: 0) }
        jeojang()
    }

    func jiugi(_ id: String) {
        mokrok.removeAll { $0.id == id }
        jeojang()
    }

    // 잠금말 — 폰의 열쇠 칸에만. 서버에는 잠금말의 지문(되돌릴 수 없는 값)만 갑니다.
    var jamgeum: String {
        get { Yeolsoe.ilgi("naeGilJamgeum") ?? "" }
        set { Yeolsoe.sseugi("naeGilJamgeum", newValue) }
    }

    static func jimun(_ lock: String) -> String {
        SHA256.hash(data: Data(("jeomnae|" + lock).utf8)).map { String(format: "%02x", $0) }.joined()
    }

    /// 웹과 같은 방식(PBKDF2 12만 번, AES-GCM) — 웹 길눈에서 맡긴 길도 앱에서 풀 수 있습니다.
    static func yeolsoe(_ lock: String) -> SymmetricKey {
        let salt = Array("jeomnae-salt-2026".utf8)
        let pw = Array(lock.utf8)
        var out = [UInt8](repeating: 0, count: 32)
        _ = CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), pw.map { Int8(bitPattern: $0) }, pw.count,
                                 salt, salt.count, CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), 120000, &out, out.count)
        return SymmetricKey(data: out)
    }

    static func jamgeugi(_ g: JeomGil, _ lock: String) -> String? {
        guard let d = try? JSONEncoder().encode(g),
              let s = try? AES.GCM.seal(d, using: yeolsoe(lock)) else { return nil }
        let iv = Data(s.nonce)
        return iv.base64EncodedString() + "." + (s.ciphertext + s.tag).base64EncodedString()
    }

    static func pulgi(_ blob: String, _ lock: String) -> JeomGil? {
        let p = blob.split(separator: ".").map(String.init)
        guard p.count == 2, let iv = Data(base64Encoded: p[0]), let ct = Data(base64Encoded: p[1]), ct.count > 16,
              let n = try? AES.GCM.Nonce(data: iv),
              let box = try? AES.GCM.SealedBox(nonce: n, ciphertext: ct.dropLast(16), tag: ct.suffix(16)),
              let d = try? AES.GCM.open(box, using: yeolsoe(lock)),
              let o = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any],
              var g = JeomGil.batgi(o) else { return nil }
        g.nae = true
        g.matgim = true
        g.ollim = (o["ollim"] as? Bool) ?? false
        return g
    }

    func matgigi(_ g: JeomGil, _ lock: String) async -> Bool {
        guard let blob = NaeGil.jamgeugi(g, lock) else { return false }
        guard let o = await Nas.postJson("jeom.php", [("a", "naeput"), ("k", NaeGil.jimun(lock))], ["id": g.id, "blob": blob]),
              (o["ok"] as? Bool) ?? false else { return false }
        var x = g
        x.matgim = true
        await MainActor.run { self.damgi(x) }
        return true
    }

    /// 맡겨 둔 길을 찾아옴 — (찾은 수, 풀지 못한 수)
    func chajaogi(_ lock: String) async -> (Int, Int)? {
        guard let o = await Nas.get("jeom.php", [("a", "naelist"), ("k", NaeGil.jimun(lock))]) else { return nil }
        var got = 0, sal = 0
        for r in (o["rows"] as? [[String: Any]]) ?? [] {
            if let g = NaeGil.pulgi(Nas.gul(r["blob"]), lock) {
                await MainActor.run { self.damgi(g) }
                got += 1
            } else {
                sal += 1
            }
        }
        return (got, sal)
    }

    func matgimJiugi(_ id: String) {
        let lock = jamgeum
        jiugi(id)
        guard !lock.isEmpty else { return }
        Task { _ = await Nas.get("jeom.php", [("a", "naedel"), ("k", NaeGil.jimun(lock)), ("id", id)]) }
    }
}
