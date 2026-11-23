// LVD투어스튜디오 앱 — 저장 겹침 합치기 (판 1.4.3, 빌드 261123-T9, 2026-11-23 새방클, 이사장님 승인 「1」)
// 웹·앱이 같은 코스를 함께 고치다 저장이 겹치면, 예전에는 나중에 저장한 쪽이 코스를 통째로 덮었습니다.
// 이제는 마지막으로 맞춘 것(기준)과 비교해, 이쪽에서 실제로 고친 칸만 서버 최신 것에 얹습니다.
// 다른 쪽에서 고친 칸은 살리고, 같은 칸을 양쪽에서 고쳤을 때만 이쪽 것으로 맞춥니다(앞 것은 판 되돌리기에 남음).
// 웹 lvdts_sae143.js 의 hap()·안드로이드 Hapchigi.kt 와 같은 규칙. 목록은 id(없으면 at, 그것도 없으면 f)로 짝을 지어 합칩니다.
import Foundation

enum Hapchigi {
    static var gyeopchim = 0
    private struct Eop {}
    private static let EOP: Any = Eop()
    private static func isEop(_ v: Any?) -> Bool { v is Eop }
    private static func norm(_ v: Any?) -> Any? { if v == nil || v is NSNull { return nil }; return v }

    static func gata(_ a: Any?, _ b: Any?) -> Bool {
        let x = norm(a), y = norm(b)
        if x == nil || y == nil { return x == nil && y == nil }
        if let p = x as? [String: Any], let q = y as? [String: Any] {
            if p.count != q.count { return false }
            for (k, v) in p { guard q.keys.contains(k), gata(v, q[k]) else { return false } }
            return true
        }
        if let p = x as? [Any], let q = y as? [Any] {
            if p.count != q.count { return false }
            for i in 0..<p.count where !gata(p[i], q[i]) { return false }
            return true
        }
        if let p = x as? NSNumber, let q = y as? NSNumber, !(x is String), !(y is String) { return p == q }
        return "\(x!)" == "\(y!)"
    }

    static func hap(_ b: Any?, _ m: Any?, _ t: Any?) -> Any? {
        if gata(m, b) { return t }
        if gata(t, b) { return m }
        if gata(m, t) { return m }
        if let mm = norm(m) as? [String: Any], let tt = norm(t) as? [String: Any] { return hapGaek(norm(b) as? [String: Any], mm, tt) }
        if let mm = norm(m) as? [Any], let tt = norm(t) as? [Any], let r = hapMok(norm(b) as? [Any], mm, tt) { return r }
        gyeopchim += 1
        return m
    }
    private static func hapGaek(_ b: [String: Any]?, _ m: [String: Any], _ t: [String: Any]) -> [String: Any] {
        var r: [String: Any] = [:]
        for k in Set(t.keys).union(m.keys) {
            let v = hapKan(b?[k] ?? EOP, m[k] ?? EOP, t[k] ?? EOP)
            if !isEop(v) { r[k] = v ?? NSNull() }
        }
        return r
    }
    private static func hapKan(_ b: Any?, _ m: Any?, _ t: Any?) -> Any? {
        let bE = isEop(b), mE = isEop(m), tE = isEop(t)
        if mE && tE { return EOP }
        if mE { return (!bE && gata(t, b)) ? EOP : t }
        if tE { return (!bE && gata(m, b)) ? EOP : m }
        return hap(bE ? nil : b, m, t)
    }
    private static func yeolsoe(_ o: Any?) -> String? {
        guard let j = norm(o) as? [String: Any] else { return nil }
        for k in ["id", "at", "f"] { if let v = norm(j[k]) { let s = "\(v)"; if !s.isEmpty { return k + ":" + s } } }
        return nil
    }
    private static func hapMok(_ b: [Any]?, _ m: [Any], _ t: [Any]) -> [Any]? {
        func pyo(_ a: [Any]?) -> (k: [String], v: [String: Any])? {
            var ks: [String] = [], vs: [String: Any] = [:]
            for o in a ?? [] { guard let k = yeolsoe(o), vs[k] == nil else { return nil }; ks.append(k); vs[k] = o }
            return (ks, vs)
        }
        guard let pb = pyo(b), let pm = pyo(m), let pt = pyo(t) else { return nil }
        if pm.k.isEmpty && pt.k.isEmpty { return [] }
        let mb = pm.k.filter { pb.v[$0] != nil }, bm = pb.k.filter { pm.v[$0] != nil }
        let chare = mb != bm ? pm.k + pt.k.filter { pm.v[$0] == nil } : pt.k + pm.k.filter { pt.v[$0] == nil }
        var r: [Any] = []
        for k in chare {
            let v = hapKan(pb.v[k] ?? EOP, pm.v[k] ?? EOP, pt.v[k] ?? EOP)
            if !isEop(v), let vv = v { r.append(vv) }
        }
        return r
    }
    /// 기준이 없으면(폰에 남은 것을 올린 때 등) 이쪽 것 그대로
    static func kos(gijun: String, ijjok: [String: Any], seobeo: [String: Any]) -> [String: Any] {
        gyeopchim = 0
        guard !gijun.isEmpty, let d = gijun.data(using: .utf8), let b = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any] else { return ijjok }
        return (hap(b, ijjok, seobeo) as? [String: Any]) ?? ijjok
    }
}
