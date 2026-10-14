// LVD투어스튜디오 앱 — 코스와 지점 (판 1.0.0, 빌드 261012-T1)
// 웹 투어스튜디오 1.12판(lvdts.html)과 같은 자료를 그대로 읽고 씁니다. 앱이 모르는 칸도 지우지 않고 그대로 돌려 보냅니다.
// 지점 경계 규칙(작성원칙 통합본 7번): 앞 지점의 현장영상해설을 마치고 출발하는 자리부터 다음 지점이 시작됩니다.
//   오프닝 지점은 첫 만남 자리부터, 클로징 지점은 마지막 지점을 떠나는 자리부터 해산 자리까지입니다.
//   지점 사이의 이동은 다음 지점의 몫(walkIn)입니다. 예전 칸 move 는 웹과 맞추려고 syncMove 로 함께 채웁니다.
import Foundation

let RULE_TXT = "지점 경계 규칙 — 앞 지점의 현장영상해설을 마치고 출발하는 자리부터 다음 지점이 시작됩니다. 오프닝 지점은 첫 만남 자리부터, 클로징 지점은 마지막 지점을 떠나는 자리부터 해산 자리까지입니다."

/// 자료 한 칸을 글자로(숫자·참거짓도 글자로, 없으면 빈 글자)
func geulja(_ v: Any?) -> String {
    if let s = v as? String { return s }
    if let n = v as? NSNumber { return n.stringValue }
    return ""
}
/// 자료 한 칸을 숫자로
func sutja(_ v: Any?) -> Double? {
    if let n = v as? NSNumber { return n.doubleValue }
    if let s = v as? String { return Double(s.trimmingCharacters(in: .whitespaces)) }
    return nil
}

final class Spot: Identifiable {
    var d: [String: Any]
    init(_ d: [String: Any]) { self.d = d }
    var id: String { geulja(d["id"]) }
    var kind: String { geulja(d["kind"]) }      // "open" 오프닝 지점, "close" 클로징 지점, "" 보통 지점
    func s(_ k: String) -> String { geulja(d[k]) }
    func n(_ k: String) -> Double? { sutja(d[k]) }
    func set(_ k: String, _ v: Any?) { d[k] = v ?? NSNull() }
    var name: String { get { s("name") } set { d["name"] = newValue } }
    var shots: [[String: Any]] { get { (d["shots"] as? [[String: Any]]) ?? [] } set { d["shots"] = newValue } }
    var lat: Double? { n("lat") }
    var lng: Double? { n("lng") ?? n("lon") }
}

final class Kos {
    var d: [String: Any]
    var spots: [Spot]
    var openSpot: Spot
    var closeSpot: Spot

    init(_ j: [String: Any]) {
        d = j
        spots = ((j["spots"] as? [[String: Any]]) ?? []).map { Spot($0) }
        openSpot = Spot((j["openSpot"] as? [String: Any]) ?? [:])
        closeSpot = Spot((j["closeSpot"] as? [String: Any]) ?? [:])
        ensureOC()
        if sutja(d["rangeV"]) != 1 { migrateRange() }
    }

    func json() -> [String: Any] {
        syncMove()
        var o = d
        o["spots"] = spots.map { $0.d }
        o["openSpot"] = openSpot.d
        o["closeSpot"] = closeSpot.d
        return o
    }

    var id: String { geulja(d["id"]) }
    var name: String { get { geulja(d["name"]) } set { d["name"] = newValue } }
    var isGyobon: Bool { id == "GYOBON" || (d["gyobon"] as? Bool) == true }
    func s(_ k: String) -> String { geulja(d[k]) }

    // ---- 오프닝·클로징 지점 ----
    private func biun(_ nm: String) -> [String: Any] {
        ["name": nm, "stay": 0, "walkIn": 0, "to": "", "access": "", "touch": "", "safe": "", "gpos": "", "shots": [Any]()]
    }
    func ensureOC() {
        if openSpot.d.isEmpty { openSpot.d = biun("오프닝") }
        if closeSpot.d.isEmpty { closeSpot.d = biun("클로징") }
        openSpot.d["id"] = "sOpen"; openSpot.d["kind"] = "open"
        closeSpot.d["id"] = "sClose"; closeSpot.d["kind"] = "close"
        if openSpot.d["shots"] == nil { openSpot.d["shots"] = [Any]() }
        if closeSpot.d["shots"] == nil { closeSpot.d["shots"] = [Any]() }
    }
    var allSpots: [Spot] { [openSpot] + spots + [closeSpot] }
    func spot(_ id: String) -> Spot? { allSpots.first { $0.id == id } }

    func spotLab(_ s: Spot?) -> String {
        guard let s else { return "" }
        if s.kind == "open" { return "오프닝 지점" }
        if s.kind == "close" { return "클로징 지점" }
        return "\((spots.firstIndex { $0 === s } ?? 0) + 1)지점"
    }
    func spotFull(_ s: Spot?) -> String {
        guard let s else { return "" }
        return s.kind.isEmpty ? spotLab(s) + " " + s.name : spotLab(s)
    }
    func spotPrev(_ s: Spot) -> Spot? { let a = allSpots; guard let i = a.firstIndex(where: { $0 === s }), i > 0 else { return nil }; return a[i - 1] }
    func spotNext(_ s: Spot) -> Spot? { let a = allSpots; guard let i = a.firstIndex(where: { $0 === s }), i < a.count - 1 else { return nil }; return a[i + 1] }

    /// 어디서부터 — 이 지점이 시작되는 자리
    func spotFrom(_ s: Spot) -> String {
        if s.kind == "open" {
            let m = s_meet
            return "첫 만남 자리" + (m.isEmpty ? " (코스 정보에서 집합 장소를 적어 주십시오)" : " — " + m)
        }
        guard let p = spotPrev(s) else { return "" }
        let pt = p.s("to").trimmingCharacters(in: .whitespacesAndNewlines)
        return spotFull(p) + "의 현장영상해설을 마치고 출발하는 자리" + (pt.isEmpty ? " (" + spotLab(p) + "의 어디까지를 아직 적지 않았습니다)" : " — " + pt)
    }
    private var s_meet: String { s("meet").trimmingCharacters(in: .whitespacesAndNewlines) }
    func spotToName(_ s: Spot) -> String {
        s.kind == "close" ? "어디까지 — 해산하는 자리" : "어디까지 — 이 지점 현장영상해설을 마치고 출발하는 자리"
    }

    // ---- 대본 ----
    func spotText(_ s: Spot) -> String {
        if s.kind == "open" { return self.s("opening") }
        if s.kind == "close" { return self.s("closing") }
        return s.s("text")
    }
    func setSpotText(_ s: Spot, _ v: String) {
        if s.kind == "open" { d["opening"] = v } else if s.kind == "close" { d["closing"] = v } else { s.d["text"] = v }
    }
    /// 읽는 데 걸리는 초 — 웹과 같이 1분에 330자(코스에 cps 가 있으면 그것)
    func readSec(_ t: String) -> Int {
        let cps = (sutja(d["cps"]) ?? 330) / 60
        let n = t.filter { !$0.isWhitespace }.count
        return Int((Double(n) / cps).rounded())
    }

    // ---- 시간 ----
    func stayOf(_ s: Spot) -> Double {
        let st = s.n("stay") ?? 0
        if !s.kind.isEmpty && !(st > 0) { return max(1, ceil(Double(readSec(spotText(s))) / 60)) }
        return st
    }
    func spotMinTxt(_ s: Spot) -> String {
        let w = Int(s.n("walkIn") ?? 0), st = Int(stayOf(s).rounded())
        return fmtMin(Double(w + st)) + " (이동 \(w)분, 정지 현장영상해설 \(st)분)"
    }
    func courseMin() -> Double { allSpots.reduce(0.0) { $0 + ($1.n("walkIn") ?? 0) + stayOf($1) } }

    func syncMove() {
        let a = allSpots
        for k in 0..<a.count {
            let nx: Spot? = k + 1 < a.count ? a[k + 1] : nil
            a[k].d["move"] = nx?.n("walkIn") ?? 0
            if let nx, let di = nx.n("distIn"), di > 0 { a[k].d["dist"] = di }
        }
    }
    func migrateRange() {
        let a = allSpots
        if a.count > 1 {
            for k in stride(from: a.count - 1, through: 1, by: -1) {
                let pv = a[k - 1]
                if a[k].d["walkIn"] == nil || a[k].d["walkIn"] is NSNull { a[k].d["walkIn"] = max(0, pv.n("move") ?? 0) }
                if let di = pv.n("dist"), di > 0, (a[k].n("distIn") ?? 0) == 0 { a[k].d["distIn"] = di }
            }
        }
        if openSpot.d["walkIn"] == nil || openSpot.d["walkIn"] is NSNull { openSpot.d["walkIn"] = 0 }
        d["rangeV"] = 1
    }

    // ---- 자리 ----
    func posOf(_ s: Spot?) -> (Double, Double)? {
        guard let s else { return nil }
        if let la = s.lat, let lo = s.lng { return (la, lo) }
        if s.kind == "open", let p = d["pos"] as? [String: Any], let la = sutja(p["la"]), let lo = sutja(p["lo"]) { return (la, lo) }
        return nil
    }
    func walkMinutes(_ m: Double) -> Int {
        let sp = sutja(d["walk"]) ?? 40
        return max(1, Int((m / (sp > 0 ? sp : 40)).rounded()) + 1)   // 서고 모이는 데 1분을 더함(웹과 같음)
    }

    // ---- 지점 넣기 ----
    func newSpotId() -> String {
        var n = 1
        while spots.contains(where: { $0.id == "s\(n)" }) { n += 1 }
        return "s\(n)"
    }
    /// 지금 지점 바로 뒤에 새 지점을 넣는다(오프닝이면 1지점 자리, 클로징이면 클로징 바로 앞)
    func addSpot(after cur: Spot?, name: String) -> Spot {
        let s = Spot(["id": newSpotId(), "name": name.isEmpty ? "이름 없는 지점" : name, "stay": 5, "walkIn": 0, "move": 0, "to": "", "text": "", "access": "", "touch": "", "safe": "", "gpos": "", "shots": [Any]()])
        var idx = 0
        if let cur {
            if cur.kind == "open" { idx = 0 } else if cur.kind == "close" { idx = spots.count } else { idx = (spots.firstIndex { $0 === cur } ?? spots.count - 1) + 1 }
        }
        spots.insert(s, at: min(idx, spots.count))
        return s
    }
    /// 지점을 앞뒤로 옮김 — 오프닝·클로징은 옮기지 않음
    func moveSpot(_ s: Spot, by: Int) -> Bool {
        guard let i = spots.firstIndex(where: { $0 === s }) else { return false }
        let j = i + by
        guard j >= 0 && j < spots.count else { return false }
        spots.swapAt(i, j)
        return true
    }
}

func fmtMin(_ m: Double) -> String {
    let n = Int(m.rounded())
    if n < 60 { return "\(n)분" }
    return "\(n / 60)시간 \(n % 60)분"
}

func metersBetween(_ a: (Double, Double)?, _ b: (Double, Double)?) -> Double? {
    guard let a, let b else { return nil }
    let R = 6371000.0, r = Double.pi / 180
    let dLat = (b.0 - a.0) * r, dLng = (b.1 - a.1) * r
    let h = sin(dLat / 2) * sin(dLat / 2) + cos(a.0 * r) * cos(b.0 * r) * sin(dLng / 2) * sin(dLng / 2)
    return (2 * R * atan2(sqrt(h), sqrt(1 - h))).rounded()
}

/// 원칙 점검(작성원칙 통합본 16개 가운데 앱이 지점마다 살필 수 있는 것)
enum Jeomgeom {
    static let siGakJeonje = ["보이", "보시", "보세요", "보입니다", "보면", "보듯"]
    static let sangdae = ["이쪽", "저쪽", "여기 보이", "앞쪽에 보이"]
    static func lines(_ c: Kos) -> [String] {
        var L: [String] = []
        func gat(_ f: (Spot) -> Bool) -> String { c.allSpots.filter(f).map { c.spotLab($0) }.joined(separator: ", ") }
        let meet = c.s("meet").trimmingCharacters(in: .whitespaces)
        if meet.isEmpty { L.append("집합 장소(첫 만남 자리)를 아직 적지 않았습니다(1번).") }
        if c.s("opening").trimmingCharacters(in: .whitespaces).isEmpty { L.append("오프닝 지점 대본이 비어 있습니다(2번).") }
        let noTo = gat { $0.s("to").trimmingCharacters(in: .whitespaces).isEmpty }
        if !noTo.isEmpty { L.append("어디까지를 안 적은 지점(7번): " + noTo) }
        let noTime = gat { !$0.kind.isEmpty ? false : (($0.n("stay") ?? 0) <= 0) }
        if !noTime.isEmpty { L.append("코스별 소요시간이 없는 지점(4번): " + noTime) }
        let noPos = gat { $0.kind.isEmpty && $0.s("gpos").trimmingCharacters(in: .whitespaces).isEmpty }
        if !noPos.isEmpty { L.append("현장영상해설사 위치를 안 적은 지점(8번): " + noPos) }
        let noText = gat { c.spotText($0).trimmingCharacters(in: .whitespaces).isEmpty }
        if !noText.isEmpty { L.append("대본이 빈 지점: " + noText) }
        let noDir = gat { let t = c.spotText($0); return !t.isEmpty && t.range(of: "[0-9]{1,2}시 ?방향", options: .regularExpression) == nil }
        if !noDir.isEmpty { L.append("시계 방향으로 짚지 않은 지점(11번): " + noDir) }
        let rel = gat { s in sangdae.contains { c.spotText(s).contains($0) } }
        if !rel.isEmpty { L.append("이쪽·저쪽 같은 말이 든 지점(11번): " + rel) }
        let bun = gat { c.spotText($0).contains("분면") }
        if !bun.isEmpty { L.append("분면이라는 말이 든 지점(9번): " + bun) }
        let deo = gat { c.spotText($0).contains("더듬") || $0.s("touch").contains("더듬") }
        if !deo.isEmpty { L.append("더듬다가 든 지점(13번): " + deo) }
        let si = gat { s in siGakJeonje.contains { c.spotText(s).contains($0) } }
        if !si.isEmpty { L.append("시각 전제 표현이 든 지점(12번): " + si) }
        if c.s("closing").trimmingCharacters(in: .whitespaces).isEmpty { L.append("클로징 지점 대본이 비어 있습니다(10번).") }
        return L
    }
}
