// 찾기 — 나스 자료 창고에 묻는 일을 한 곳에 모읍니다(웹 길눈이 쓰던 자료 창고를 그대로 씀).
//   jeom.php a=jangso      이름·주소로 곳 찾기(가까운 곳부터)
//   jeom.php a=jimyeong    좌표를 주소로
//   jarimal.php            지금 내 자리를 한 문장으로(가까운 출구·건물까지)
//   chatta.php a=gil       지금 달리는 길 이름과 동네
//   neagori.php            둘레의 사거리·갈림길
import Foundation

struct Jangso: Hashable, Identifiable, Codable {
    var ireum: String
    var juso: String
    var lat: Double
    var lon: Double
    var id: String { "\(ireum)|\(lat)|\(lon)" }
}

struct Neagori {
    let lat: Double
    let lon: Double
    let mal: String
}

enum Chatgi {
    static func su(_ v: Any?) -> Double? {
        if let d = v as? Double { return d }
        if let n = v as? NSNumber { return n.doubleValue }
        if let s = v as? String { return Double(s) }
        return nil
    }

    static func json(_ pail: String, _ q: [String: String]) async -> [String: Any]? {
        guard let d = try? await Tongsin.shared.get(pail, q),
              let o = try? JSONSerialization.jsonObject(with: d.data) as? [String: Any] else { return nil }
        return o
    }

    private static func jari(_ q: inout [String: String]) {
        if let w = WichiEngine.shared.jigeum {
            q["lat"] = String(format: "%.6f", w.lat)
            q["lon"] = String(format: "%.6f", w.lon)
        }
    }

    /// 이름이나 주소로 곳 찾기 — 못 받으면 nil, 없으면 빈 목록
    static func jangso(_ mal: String) async -> [Jangso]? {
        var q = ["a": "jangso", "q": mal]
        jari(&q)
        guard let o = await json("jeom.php", q) else { return nil }
        let rows = (o["rows"] as? [[String: Any]]) ?? []
        return rows.compactMap { r in
            guard let la = su(r["lat"]), let lo = su(r["lon"]) else { return nil }
            return Jangso(ireum: (r["ireum"] as? String) ?? "", juso: (r["juso"] as? String) ?? "", lat: la, lon: lo)
        }
    }

    static func juso(_ lat: Double, _ lon: Double) async -> String? {
        let q = ["a": "jimyeong", "lat": String(format: "%.6f", lat), "lon": String(format: "%.6f", lon)]
        guard let o = await json("jeom.php", q) else { return nil }
        let j = (o["juso"] as? String) ?? ""
        return j.isEmpty ? nil : j
    }

    static func jarimal(_ lat: Double, _ lon: Double) async -> String? {
        let q = ["lat": String(format: "%.6f", lat), "lon": String(format: "%.6f", lon)]
        guard let o = await json("jarimal.php", q) else { return nil }
        let m = (o["mal"] as? String) ?? ""
        return m.isEmpty ? nil : m
    }

    static func gil(_ lat: Double, _ lon: Double) async -> (gil: String, dong: String)? {
        let q = ["a": "gil", "lat": String(format: "%.5f", lat), "lon": String(format: "%.5f", lon)]
        guard let o = await json("chatta.php", q) else { return nil }
        return ((o["gil"] as? String) ?? "", (o["dong"] as? String) ?? "")
    }

    static func neagori(_ lat: Double, _ lon: Double) async -> [Neagori]? {
        let q = ["lat": String(format: "%.5f", lat), "lon": String(format: "%.5f", lon), "ban": "1000"]
        guard let o = await json("neagori.php", q) else { return nil }
        let rows = (o["rows"] as? [[String: Any]]) ?? []
        return rows.compactMap { r in
            guard let la = su(r["lat"]), let lo = su(r["lon"]) else { return nil }
            return Neagori(lat: la, lon: lo, mal: (r["mal"] as? String) ?? "")
        }
    }
}
