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
    var m: String? = nil      // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 자봉이 탈것을 타고 가는 동안 찍힌 점(지하철·버스·에스컬레이터) — 걸음으로 안내하지 않음
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
    var st: Int? = nil        // 2.55.0 그린 이의 걸음 자리
    var sori: String? = nil   // 2.55.0 자봉 목소리 토막(점검을 거친 mp3, 나스 sori/ 아래 자리)

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
            let tm = (p["m"] as? String) ?? ""
            return JeomJeom(lat: la, lon: lo, acc: Chatgi.su(p["acc"]), h: Chatgi.su(p["h"]), t: Chatgi.su(p["t"]), m: tm.isEmpty ? nil : tm)
        }
        // 2.59.0 (점검 — 자봉 앱과 잇기) 자봉은 위성이 흐린 자리(지하·실내)의 표시를 위치 없이 올림. 예전엔 길눈이 그런 표시를 버려
        // 지하 계단·문 표시가 사라졌음 — 같은 걸음 자리(st), 없으면 가까운 때(t)의 위치 있는 점으로 채움(안드로이드와 같음)
        let wonJeom: [(st: Double?, t: Double?, lat: Double, lon: Double)] = ((o["pts"] as? [[String: Any]]) ?? []).compactMap { p in
            guard let la = Chatgi.su(p["lat"]), let lo = Chatgi.su(p["lon"]), la != 0, lo != 0 else { return nil }
            return (Chatgi.su(p["st"]), Chatgi.su(p["t"]), la, lo)
        }
        func chaeum(_ m: [String: Any]) -> (Double, Double)? {
            if let st = Chatgi.su(m["st"]) {
                let h = wonJeom.filter { $0.st != nil }.min { abs($0.st! - st) < abs($1.st! - st) }
                if let h = h { return (h.lat, h.lon) }
            }
            if let t = Chatgi.su(m["t"]) {
                let h = wonJeom.filter { $0.t != nil }.min { abs($0.t! - t) < abs($1.t! - t) }
                if let h = h { return (h.lat, h.lon) }
            }
            return nil
        }
        let marks: [JeomPyo] = ((o["marks"] as? [[String: Any]]) ?? []).map { m in
            var la = Chatgi.su(m["lat"]), lo = Chatgi.su(m["lon"])
            if la == nil || lo == nil || la == 0 || lo == 0, let c = chaeum(m) { la = c.0; lo = c.1 }
            return JeomPyo(lat: la, lon: lo, name: m["name"] as? String, kind: m["kind"] as? String,
                    cnt: Chatgi.su(m["cnt"]).map { Int($0) }, mal: m["mal"] as? String, t: Chatgi.su(m["t"]),
                    acc: Chatgi.su(m["acc"]), dist: Chatgi.su(m["dist"]), st: Chatgi.su(m["st"]).map { Int($0) })
        }
        // 2.55.0 목소리 따라 걷기 — 받아쓰기·mp3 를 거친 토막만, 같은 걸음 자리의 표시에 붙임(이사장님 확정 방식)
        var marks2 = marks
        for t in (o["sori"] as? [[String: Any]]) ?? [] {
            guard let pail = t["pail"] as? String, pail.hasSuffix(".mp3"), let st = Chatgi.su(t["st"]).map({ Int($0) }) else { continue }
            let pn = (t["pyosi"] as? String) ?? ""
            if let k = marks2.firstIndex(where: { $0.st == st && $0.sori == nil && (pn.isEmpty || $0.name == pn) }) ?? marks2.firstIndex(where: { $0.st == st && $0.sori == nil }) {
                marks2[k].sori = pail
            }
        }
        return JeomGil(id: Nas.gul(o["id"]), title: Nas.gul(o["title"]), from: Nas.gul(o["from"]), to: Nas.gul(o["to"]),
                       who: Nas.gul(o["who"]), made: Nas.gul(o["made"]), dist: Chatgi.su(o["dist"]) ?? 0,
                       pts: pts, marks: marks2)
    }

    /// 되돌아가는 길 — 점을 거꾸로, 표시 이름도 거꾸로(오른쪽 꺾임 ↔ 왼쪽 꺾임 등)
    func dwit() -> JeomGil {
        var g = self
        g.pts = pts.reversed()
        var out: [JeomPyo] = marks.map { m in
            var x = m
            x.sori = nil   // 2.55.0 되돌아가는 길에서는 목소리 토막을 들려 드리지 않음(방향이 거꾸로라서)
            if let n = m.name { x.name = JeomGil.DWIT[n] ?? n }
            if let k = m.kind { x.kind = JeomGil.DWIT[k] ?? k }
            return x
        }
        // 2.12.0 계단과 건널목은 시작과 끝을 짝지어 바꿈 — 거꾸로 걸으면 끝이 시작이 됨
        func jari(_ m: JeomPyo) -> Int {
            guard let la = m.lat, let lo = m.lon, la != 0 else { return -1 }
            var b = -1, bd = 1e9
            for (k, p) in pts.enumerated() {
                let d = WichiEngine.geori(la, lo, p.lat, p.lon)
                if d < bd { bd = d; b = k }
            }
            return b
        }
        let ix = marks.map(jari)
        func gyedan(_ n: String) -> Bool { n.contains("계단") }
        func geonneol(_ n: String) -> Bool { n.contains("횡단보도") || n.contains("건널목") }
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 에스컬레이터도 계단처럼 짝을 바꿈 — 예전엔 오름·내림만 그 자리에서 뒤집어
        //   거꾸로 걸을 때 먼저 닿는 원래 내림 자리에서 "에스컬레이터 내림", 원래 타던 자리에서 "내려감"으로 거꾸로 알렸음.
        //   이제 원래 내림 자리가 타는 곳(방향을 뒤집어 "에스컬레이터 내려감/올라감"), 원래 타던 자리가 "에스컬레이터 내림"
        func eseu(_ n: String) -> Bool { n.contains("에스컬레이터") }
        func kkeutIn(_ n: String) -> Bool { eseu(n) ? n.contains("내림") : n.contains("끝") }
        var sseun = Set<Int>()
        for (n, m) in marks.enumerated() {
            let nm = m.ireum
            let gy = gyedan(nm), gn = geonneol(nm), es = eseu(nm)
            guard (gy || gn || es), !kkeutIn(nm), ix[n] >= 0 else { continue }
            var e: Int?
            for (k, q) in marks.enumerated() where k != n && !sseun.contains(k) && ix[k] >= ix[n] && kkeutIn(q.ireum) {
                let qn = q.ireum
                guard es ? eseu(qn) : (gy ? gyedan(qn) : geonneol(qn)) else { continue }
                if e == nil || ix[k] < ix[e!] { e = k }
            }
            guard let ek = e else { continue }
            sseun.insert(ek); sseun.insert(n)
            if es {
                out[ek].name = JeomGil.DWIT[nm] ?? nm
                out[ek].kind = marks[n].kind.map { JeomGil.DWIT[$0] ?? $0 }
                out[n].name = marks[ek].ireum
                out[n].kind = marks[ek].kind
            } else if gy {
                out[ek].name = JeomGil.DWIT[nm] ?? nm
                out[ek].cnt = m.cnt
                out[n].name = "계단 끝"
                out[n].cnt = nil
            } else {
                out[ek].name = nm
                out[ek].dist = m.dist
                out[n].name = marks[ek].ireum
            }
        }
        // 짝이 없는 건널목 끝은 거꾸로 걸으면 건너기 시작
        for (n, m) in marks.enumerated() where !sseun.contains(n) && geonneol(m.ireum) && m.ireum.contains("끝") {
            out[n].name = m.ireum.replacingOccurrences(of: "끝", with: "시작")
        }
        g.marks = out.reversed()
        g.from = to
        g.to = from
        return g
    }

    static let DWIT: [String: String] = [
        "왼쪽으로 꺾임": "3시 방향으로 꺾임", "오른쪽으로 꺾임": "9시 방향으로 꺾임",
        // 2.53.0 시계 방향 꺾임 — 거꾸로 걸으면 거울처럼(2시 ↔ 10시)
        "1시 방향으로 꺾임": "11시 방향으로 꺾임", "2시 방향으로 꺾임": "10시 방향으로 꺾임", "3시 방향으로 꺾임": "9시 방향으로 꺾임",
        "4시 방향으로 꺾임": "8시 방향으로 꺾임", "5시 방향으로 꺾임": "7시 방향으로 꺾임", "7시 방향으로 꺾임": "5시 방향으로 꺾임",
        "8시 방향으로 꺾임": "4시 방향으로 꺾임", "9시 방향으로 꺾임": "3시 방향으로 꺾임", "10시 방향으로 꺾임": "2시 방향으로 꺾임",
        "11시 방향으로 꺾임": "1시 방향으로 꺾임",
        "올라가는 계단 시작": "내려가는 계단 시작", "내려가는 계단 시작": "올라가는 계단 시작",
        "오름턱": "내림턱", "내림턱": "오름턱",
        "횡단보도 건너기 시작": "횡단보도 건너기 끝", "횡단보도 건너기 끝": "횡단보도 건너기 시작",
        "엘리베이터 올라감": "엘리베이터 내려감", "엘리베이터 내려감": "엘리베이터 올라감",
        // 2.59.0 (점검) 자봉 표시의 탈것 짝 — 거꾸로 걸으면 오름과 내림, 탐과 내림이 바뀜(안드로이드와 같음)
        "에스컬레이터 올라감": "에스컬레이터 내려감", "에스컬레이터 내려감": "에스컬레이터 올라감",
        "지하철 탐": "지하철 내림", "지하철 내림": "지하철 탐",
        "버스 탐": "버스 내림", "버스 내림": "버스 탐"
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

/// 이어진 길 한 줄(2.11.0)
struct JeomIeum: Identifiable {
    let from: String
    let to: String
    let dist: Double
    let gugan: [JeomGugan]
    var id: String { gugan.map { $0.id + ($0.dwit ? "|r" : "") }.joined(separator: ",") }
    var julMal: String { "\(from)에서 \(to)까지 — 길 \(gugan.count)개 이어서, \(Annae.geoMal(dist))" }
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
    static func matneunGil(_ me: Wichi, _ mok: Jangso) async -> (String, [JeomGugan])? {
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
        if let b = best {
            let g = b.0, dw = b.1
            let nm = dw ? "\(g.to)에서 \(g.from)까지 되돌아가는 점지도" : (g.title.isEmpty ? "\(g.from)에서 \(g.to)까지 점지도" : "\(g.title) 점지도")
            return (nm, [JeomGugan(id: g.id, dwit: dw)])
        }
        // 2.11.0 한 점지도로 닿지 않으면 이어진 길로 — 첫 구간이 가까이, 마지막 구간 끝이 목적지 가까이
        guard let ie = await ieumMok() else { return nil }
        let bm = Dictionary(r.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        var bi: (JeomIeum, Double)?
        var bulleon = 0
        for x in ie {
            guard let c = x.gugan.first, let k = x.gugan.last, let cm = bm[c.id] else { continue }
            let eo = cm.near >= 0 ? cm.near : WichiEngine.geori(me.lat, me.lon, c.dwit ? cm.elat : cm.slat, c.dwit ? cm.elon : cm.slon)
            guard eo <= 50 else { continue }
            // 2.12.0 마지막 구간이 가까운 길 목록에 없으면(멀리 있으면) 불러와 끝을 봄 — 세 번까지
            var kk: (Double, Double)?
            if let km = bm[k.id] {
                kk = k.dwit ? (km.slat, km.slon) : (km.elat, km.elon)
            } else if bulleon < 3 {
                bulleon += 1
                if let kg = await bulleoogi(k.id), let s = kg.pts.first, let e = kg.pts.last {
                    kk = k.dwit ? (s.lat, s.lon) : (e.lat, e.lon)
                }
            }
            guard let kp = kk else { continue }
            let kkeut = WichiEngine.geori(mok.lat, mok.lon, kp.0, kp.1)
            if kkeut <= 80, bi == nil || kkeut < bi!.1 { bi = (x, kkeut) }
        }
        guard let x = bi?.0 else { return nil }
        return ("\(x.from)에서 \(x.to)까지 이어진 점지도, 길 \(x.gugan.count)개", x.gugan)
    }

    /// 2.11.0 협회가 이어 둔 길(점지도 여러 개를 차례로) — jeom.php a=ieum
    static func ieumMok() async -> [JeomIeum]? {
        guard let o = await Nas.get("jeom.php", [("a", "ieum")]) else { return nil }
        return ((o["rows"] as? [[String: Any]]) ?? []).compactMap { r in
            let g: [JeomGugan] = ((r["gil"] as? [[String: Any]]) ?? []).compactMap { x in
                let id = Nas.gul(x["id"])
                return id.isEmpty ? nil : JeomGugan(id: id, dwit: (Chatgi.su(x["rev"]) ?? 0) > 0)
            }
            guard g.count >= 2 else { return nil }
            return JeomIeum(from: Nas.gul(r["from"]), to: Nas.gul(r["to"]), dist: Chatgi.su(r["dist"]) ?? 0, gugan: g)
        }
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

// MARK: 2.11.0 내 문 — 지금 선 자리를 내 문으로 담아 두면 문까지 안내에서 가장 먼저 씀(폰 안에만)

struct NaeMunHang: Codable, Identifiable {
    var id: String
    var ireum: String
    var lat: Double
    var lon: Double
    var bang: Double?
    var made: String
    var geul: [String]? = nil    // 2.15.0 두 번 찍을 때 카메라가 읽은 문 둘레 글자(호수 등) — 사진은 담지 않음
    var jjak: Bool? = nil        // 2.15.0 두 번 찍어 담은 문(문 앞 한 번, 지나서 한 번)
}

final class NaeMun: ObservableObject {
    static let shared = NaeMun()
    @Published private(set) var mokrok: [NaeMunHang] = []
    private let kiI = "gn.naeMun"

    init() {
        if let d = UserDefaults.standard.data(forKey: kiI), let l = try? JSONDecoder().decode([NaeMunHang].self, from: d) { mokrok = l }
    }

    private func jeojang() {
        if let d = try? JSONEncoder().encode(mokrok) { UserDefaults.standard.set(d, forKey: kiI) }
    }

    func damgi(_ h: NaeMunHang) {
        mokrok.insert(h, at: 0)
        jeojang()
    }

    func jiugi(_ id: String) {
        mokrok.removeAll { $0.id == id }
        jeojang()
    }

    /// 2.15.0 두 번 찍어 담기 — 5미터 안에 이미 담은 문이 있으면 새로 담지 않고 글자와 자리를 보탬. 돌려주는 값: 알릴 말
    @discardableResult
    func jjakDamgi(ireum: String, lat: Double, lon: Double, bang: Double?, geul: [String]) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "M월 d일"
        if let i = mokrok.firstIndex(where: { WichiEngine.geori($0.lat, $0.lon, lat, lon) < 5 }) {
            var h = mokrok[i]
            var g = h.geul ?? []
            for x in geul where !g.contains(x) { g.append(x) }
            h.geul = g.isEmpty ? nil : Array(g.prefix(4))
            if h.jjak != true { h.lat = lat; h.lon = lon }
            if let b = bang { h.bang = b }
            h.jjak = true
            if !ireum.isEmpty && (h.ireum == "내 문" || h.ireum == "문") { h.ireum = ireum }
            mokrok[i] = h
            jeojang()
            return "이미 담아 두신 \(h.ireum)입니다. 두 번 찍은 자리" + (geul.isEmpty ? "" : "와 문 둘레 글자") + "를 보탰습니다."
        }
        let nm = !ireum.isEmpty ? ireum : (geul.first ?? "내 문")
        damgi(NaeMunHang(id: UUID().uuidString, ireum: nm, lat: lat, lon: lon, bang: bang,
                         made: f.string(from: Date()), geul: geul.isEmpty ? nil : Array(geul.prefix(4)), jjak: true))
        return "\(nm)을 내 문으로 담았습니다." + (geul.isEmpty ? "" : " 문 둘레 글자 \(geul.prefix(2).joined(separator: ", "))도 함께 담았습니다.")
    }

    /// 2.15.0 가까운 내 문 가운데 글자를 담아 둔 것(문 찾기가 "찍어 두신 문"인지 가릴 때 씀)
    func geulMun(_ lat: Double, _ lon: Double, _ r: Double) -> NaeMunHang? {
        mokrok.filter { !($0.geul ?? []).isEmpty && WichiEngine.geori($0.lat, $0.lon, lat, lon) <= r }
            .min { WichiEngine.geori($0.lat, $0.lon, lat, lon) < WichiEngine.geori($1.lat, $1.lon, lat, lon) }
    }
}
