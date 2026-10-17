// 여정 다시 돌려 보기 시험 — 아이폰 길눈 GichaAnnae.swift 바깥 부품 흉내(시험에서만 씀, 앱에 들어가지 않음)
// 앱의 셈(거리·방위·거리 말)은 그대로 옮겨 적고, 소리·기록·통신만 시험용으로 바꿈
import Foundation

struct Wichi {
    let lat: Double
    let lon: Double
    let ochae: Double
    let banghyang: Double
    let sokdo: Double
    let ttae: Date
    let georeumChu: Bool
}

struct Mokjeok { var ireum: String; var lat: Double; var lon: Double }
struct Yeojeong { var mokjeok: Mokjeok }

final class YeojeongEngine {
    static let shared = YeojeongEngine()
    var jigeum: Yeojeong?
}

enum WichiEngine {
    static func geori(_ a1: Double, _ o1: Double, _ a2: Double, _ o2: Double) -> Double {
        let r = 6371000.0, p = Double.pi / 180
        let da = (a2 - a1) * p, dO = (o2 - o1) * p
        let h = sin(da / 2) * sin(da / 2) + cos(a1 * p) * cos(a2 * p) * sin(dO / 2) * sin(dO / 2)
        return 2 * r * asin(min(1, sqrt(h)))
    }
    static func bangwi(_ a1: Double, _ o1: Double, _ a2: Double, _ o2: Double) -> Double {
        let p = Double.pi / 180
        let y = sin((o2 - o1) * p) * cos(a2 * p)
        let x = cos(a1 * p) * sin(a2 * p) - sin(a1 * p) * cos(a2 * p) * cos((o2 - o1) * p)
        let b = atan2(y, x) / p
        return (b + 360).truncatingRemainder(dividingBy: 360)
    }
}

enum Annae {
    static func geoMal(_ d: Double) -> String {
        if d >= 995 {
            let k = (d / 100).rounded() / 10
            return k == k.rounded() ? "\(Int(k))킬로미터" : String(format: "%.1f킬로미터", k)
        }
        if d >= 100 { return "\(Int((d / 10).rounded()) * 10)미터" }
        return "\(max(1, Int((d / 5).rounded()) * 5))미터"
    }
}

enum Chatgi {
    static func su(_ v: Any?) -> Double? {
        if let d = v as? Double { return d }
        if let n = v as? NSNumber { return n.doubleValue }
        if let s = v as? String { return Double(s) }
        return nil
    }
}

enum MalGeup { case jeongbo, annae, gyeonggo }

final class SoriEngine {
    static let shared = SoriEngine()
    var malMok: [(Date, String)] = []
    func mal(_ t: String, _ geup: MalGeup = .annae) { malMok.append((GichaAnnae.sigye(), t)) }
}

final class Girok {
    static let shared = Girok()
    func namgi(_ e: String, _ d: [String: Any] = [:]) {}
}

/// 통신 — 시나리오 파일의 받은 자료를 그대로 돌려줌
final class Tongsin {
    static let shared = Tongsin()
    var jaryo: [String: Any] = [:]
    func getSae(_ pail: String, _ q: [String: String] = [:]) async throws -> Data {
        var o: [String: Any] = ["ok": true]
        if pail.hasPrefix("dulle") { o["rows"] = jaryo["dulle"] ?? [] }
        else if pail.hasPrefix("cheolteoneol") { o["rows"] = jaryo["teoneol"] ?? [] }
        else if pail.hasPrefix("gyotong") {
            let modu = (jaryo["modu"] as? [[String: Any]]) ?? []
            if q["a"] == "modu" { o["rows"] = modu }
            else {
                func ireum(_ id: String?) -> String { (modu.first { ($0["id"] as? String) == id }?["ireum"] as? String) ?? "" }
                let k = ireum(q["chul"]) + "-" + ireum(q["do"])
                o["rows"] = ((jaryo["pyeon"] as? [String: Any])?[k] as? [Any]) ?? []
            }
        }
        return try JSONSerialization.data(withJSONObject: o)
    }
}
