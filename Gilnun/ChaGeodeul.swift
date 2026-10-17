// 차 안 거들기 — 2.92.0 (261017-I46, 이사장님 승인 2026-10-17 「그러자」)
// 웹 길눈(chatta.html·teoneol.js·mumchum.js)에 있었는데 앱으로 옮기지 못했던 차 안 안내 세 가지를 옮김.
//   ① 터널 미리 알림(2026-09-14 이사장님 지시) — 15초 뒤에 닿을 자리의 터널을 이름·길이와 함께. 이름이 없으면 고장 이름을 붙임.
//      터널 안에서 위성이 끊기면 「터널 안이라 위성이 잠시 끊깁니다」 한 번, 나오면 「터널을 지났습니다」.
//   ② 차가 크게 돌면(빌드 260906-70) 「방금 3시 방향으로 돌았습니다. ○○까지 ○ 남았습니다」, 조금 뒤 「여기는 ○○입니다」.
//   ③ 차가 멈춰 있을 때(2026-09-27 이사장님 지시) — 까닭을 확실히 알 때만 「○○ 정류장에 멈춰 있습니다」·「사거리 앞에 멈춰 있습니다」,
//      모르면 1·2·3·5·10분…째에 「○분째 멈춰 있습니다」, 20초 넘게 섰다가 움직이면 「다시 움직입니다」.
// 승용차·택시·콜·버스·고속버스 안에서만 돕니다(기차는 GichaAnnae, 배는 TalgeotJikimi).
import Foundation

final class ChaGeodeul {
    static let shared = ChaGeodeul()

    private(set) var kyeojim = false

    func kkeut() {
        guard kyeojim else { return }
        kyeojim = false
        tDat = []; tJari = nil; tHan = []; tDeun = nil; tWiseongMal = false; tGose = [:]
        hFrom = nil; head = nil; hJul = []
        mJul = []; mum = false; mBunMal = []
        if let t = tJikimi { t.invalidate(); tJikimi = nil }
    }

    /// AnnaeEngine.chaAnnae 가 자리마다 부름
    func wichi(_ w: Wichi, _ y: Yeojeong) {
        kyeojim = true
        let meomchum = mumchumBogo(w, y)
        if meomchum { return }   // 멈춘 동안은 위성 흔들림을 움직임으로 치지 않음
        doramBoda(w, y)
        teoneolSalpigi(w)
    }

    private func mal(_ t: String) {
        SoriEngine.shared.mal(t, .annae)
        Girok.shared.namgi("chageodeul_mal", ["mal": t])
    }

    private func geori(_ a1: Double, _ o1: Double, _ a2: Double, _ o2: Double) -> Double { WichiEngine.geori(a1, o1, a2, o2) }
    private func gakCha(_ a: Double, _ b: Double) -> Double { let d = abs(a - b).truncatingRemainder(dividingBy: 360); return d > 180 ? 360 - d : d }

    // MARK: ① 터널 미리 알림 — 웹 teoneol.js 1.7

    private struct Teoneol { let ireum: String; let gil: String; let m: Double; let pt: [(Double, Double)] }
    private var tDat: [Teoneol] = []
    private var tJari: (Double, Double)?
    private var tBatneun = false
    private var tBatTtae = Date.distantPast
    private var tHan: Set<String> = []
    private var tDeun: (key: String, t: Teoneol)?
    private var tDeunTtae = Date.distantPast
    private var tWiseongMal = false
    private var tMajimak = Date()
    private var tJikimi: Timer?
    private var tGose: [String: String] = [:]
    private var ijeonMal = ""
    private var ijeonTtae = Date.distantPast

    private func tomakGeori(_ la: Double, _ lo: Double, _ p1: (Double, Double), _ p2: (Double, Double)) -> Double {
        let K = 111320.0, co = cos(la * .pi / 180)
        let x = (lo - p1.1) * K * co, y = (la - p1.0) * K
        let bx = (p2.1 - p1.1) * K * co, by = (p2.0 - p1.0) * K
        let nn = bx * bx + by * by
        let t = nn > 0 ? max(0, min(1, (x * bx + y * by) / nn)) : 0
        let dx = x - bx * t, dy = y - by * t
        return (dx * dx + dy * dy).squareRoot()
    }
    private func julGeori(_ la: Double, _ lo: Double, _ pt: [(Double, Double)]) -> Double {
        var m = 1e9
        if pt.count >= 2 { for i in 1..<pt.count { m = min(m, tomakGeori(la, lo, pt[i - 1], pt[i])) } }
        return m
    }
    private func giriMal(_ m: Double) -> String {
        if m >= 1000 { let k = (m / 100).rounded() / 10; return "길이는 약 \(k == k.rounded() ? String(Int(k)) : String(format: "%.1f", k))킬로미터입니다." }
        return "길이는 약 \(Int((m / 10).rounded()) * 10)미터입니다."
    }

    private func teoneolBatgi(_ la: Double, _ lo: Double) {
        guard !tBatneun, Date().timeIntervalSince(tBatTtae) >= 20 else { return }
        tBatneun = true; tBatTtae = Date()
        let q = ["lat": String(format: "%.5f", la), "lon": String(format: "%.5f", lo), "ban": "3000"]
        Task { [weak self] in
            let d = try? await Tongsin.shared.getSae("teoneol.php", q)
            await MainActor.run {
                guard let self = self else { return }
                self.tBatneun = false
                guard let d = d, let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any],
                      let rows = o["rows"] as? [[String: Any]] else { return }
                self.tDat = rows.compactMap { r in
                    let pt: [(Double, Double)] = ((r["pt"] as? [Any]) ?? []).compactMap { p in
                        guard let a = p as? [Any], a.count >= 2, let x = Chatgi.su(a[0]), let y = Chatgi.su(a[1]) else { return nil }
                        return (x, y)
                    }
                    guard pt.count >= 2 else { return nil }
                    return Teoneol(ireum: ((r["ireum"] as? String) ?? "").replacingOccurrences(of: #"\s*\(.*\)\s*$"#, with: "", options: .regularExpression),
                                   gil: (r["gil"] as? String) ?? "", m: Chatgi.su(r["m"]) ?? 0, pt: pt)
                }
                self.tJari = (la, lo)
            }
        }
    }

    private func tJikimiSijak() {
        guard tJikimi == nil else { return }
        tJikimi = Timer.scheduledTimer(withTimeInterval: 3, repeats: true) { [weak self] _ in
            guard let self = self, self.tDeun != nil, !self.tWiseongMal else { return }
            if Date().timeIntervalSince(self.tMajimak) > 10 && Date().timeIntervalSince(self.tDeunTtae) < 600 {
                self.tWiseongMal = true
                self.mal("터널 안이라 위성이 잠시 끊깁니다.")
            }
        }
    }

    private func teoneolSalpigi(_ w: Wichi) {
        tMajimak = Date(); tJikimiSijak()
        if tDat.isEmpty || tJari == nil || geori(tJari!.0, tJari!.1, w.lat, w.lon) > 1500 { teoneolBatgi(w.lat, w.lon) }
        guard !tDat.isEmpty else { return }
        let s = max(0, w.sokdo)
        var munteok = s * 15
        if s < 3 { munteok = max(40, min(150, munteok > 0 ? munteok : 40)) }
        else if s > 42 { munteok = max(700, min(2500, munteok)) }
        else { munteok = max(150, min(700, munteok)) }
        var ap: [(t: Teoneol, de: Double, key: String)] = []
        for r in tDat {
            let jul = julGeori(w.lat, w.lon, r.pt)
            let a = r.pt[0], b = r.pt[r.pt.count - 1]
            let da = geori(w.lat, w.lon, a.0, a.1), db = geori(w.lat, w.lon, b.0, b.1)
            let mun = da <= db ? a : b, de = min(da, db)
            let key = "\(r.ireum)_\(String(format: "%.4f", mun.0))"
            if jul < 25 {
                if tDeun?.key != key { tDeun = (key, r); tDeunTtae = Date(); tWiseongMal = false }
                continue
            }
            if let h = head, gakCha(h, WichiEngine.bangwi(w.lat, w.lon, mun.0, mun.1)) >= 65 { continue }
            if de > munteok { continue }
            ap.append((r, de, key))
        }
        if let dn = tDeun, julGeori(w.lat, w.lon, dn.t.pt) > 80 {
            tDeun = nil; tWiseongMal = false
            if Date().timeIntervalSince(tDeunTtae) > 3 { mal("터널을 지났습니다.") }
        }
        guard !ap.isEmpty else { return }
        ap.sort { $0.de < $1.de }
        let ga = ap[0]
        if tHan.contains(ga.key) { return }
        var iut = 0
        for k in ap.dropFirst() {
            let teum = k.de - ga.de
            if teum < 150 { tHan.insert(k.key); continue }   // 오가는 두 굴은 하나로
            if teum < 900 { iut += 1; tHan.insert(k.key) }
        }
        tHan.insert(ga.key)
        let ttn = ga.t.ireum, gl = ga.t.gil, m = ga.t.m
        let bureum = !ttn.isEmpty ? ttn : (!gl.isEmpty ? "\(gl) 터널" : "이름 없는 터널")
        var t: String
        if iut > 0 { t = "곧 \(bureum)입니다. 터널이 \(iut + 1)개 이어집니다." }
        else if m < 300 { t = "곧 \(bureum), 짧습니다." }
        else { t = "곧 \(bureum)입니다. \(giriMal(m))" }
        // 이름 없는 긴 터널은 어느 고장인지라도 붙임
        if ttn.isEmpty && m >= 200 && tGose[ga.key] == nil, let p0 = ga.t.pt.first {
            tGose[ga.key] = ""
            let t0 = t
            Task { [weak self] in
                let o = await Chatgi.json("jeom.php", ["a": "jimyeong", "lat": String(format: "%.6f", p0.0), "lon": String(format: "%.6f", p0.1)])
                await MainActor.run {
                    guard let self = self else { return }
                    let got = self.gotChatgi((o?["juso"] as? String) ?? (o?["jibeon"] as? String) ?? "")
                    self.tGose[ga.key] = got
                    self.malHaji(got.isEmpty ? t0 : "곧 \(bureum)입니다. \(got)입니다. " + (m < 300 ? "짧습니다." : self.giriMal(m)))
                }
            }
            return
        }
        malHaji(t)
    }

    private func malHaji(_ t: String) {
        if t == ijeonMal && Date().timeIntervalSince(ijeonTtae) < 3 { return }
        ijeonMal = t; ijeonTtae = Date()
        mal(t)
    }

    private func gotChatgi(_ juso: String) -> String {
        var si = "", eup = ""
        for j in juso.split(separator: " ").map(String.init) {
            if (j.hasSuffix("시") || j.hasSuffix("군")) && !(j.hasSuffix("특별시") || j.hasSuffix("광역시") || j.hasSuffix("특별자치시")) { si = j }
            else if !si.isEmpty && eup.isEmpty && (j.hasSuffix("읍") || j.hasSuffix("면") || j.hasSuffix("동")) { eup = j }
        }
        return !si.isEmpty && !eup.isEmpty ? "\(si) \(eup)" : si
    }

    // MARK: ② 차가 크게 돌면 — 웹 chatta.html 빌드 260906-70·260910-2

    private var hFrom: (Double, Double)?
    private var head: Double?
    private var hJul: [(t: Date, h: Double)] = []
    private var doraTtae = Date.distantPast

    private func doramBoda(_ w: Wichi, _ y: Yeojeong) {
        guard w.ochae <= 50 else { return }
        guard let hf = hFrom else { hFrom = (w.lat, w.lon); return }
        guard geori(hf.0, hf.1, w.lat, w.lon) > 15 else { return }
        let sae = WichiEngine.bangwi(hf.0, hf.1, w.lat, w.lon)
        hFrom = (w.lat, w.lon)
        defer { head = sae }
        guard let h0 = head else { return }
        let ije = Date()
        hJul.append((ije, sae))
        if hJul.count > 60 { hJul.removeFirst(hJul.count - 60) }
        var deul = (sae - h0 + 540).truncatingRemainder(dividingBy: 360) - 180
        if let old = hJul.first(where: { let ag = ije.timeIntervalSince($0.t); return ag <= 14 && ag >= 3 })?.h {
            let d2 = (sae - old + 540).truncatingRemainder(dividingBy: 360) - 180
            if abs(d2) > abs(deul) { deul = d2 }
        }
        guard abs(deul) >= 30, ije.timeIntervalSince(doraTtae) > 12, w.sokdo < 0 || w.sokdo > 2 else { return }
        doraTtae = ije
        hJul = []
        var sh = Int(((deul.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360) / 30).rounded()) % 12
        if sh == 0 { sh = 12 }
        let d = geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon)
        mal("방금 " + (abs(deul) > 120 ? "크게 돌아 " : "") + "\(sh)시 방향으로 돌았습니다. \(y.mokjeok.ireum)까지 \(Annae.geoMal(d)) 남았습니다.")
        let la = w.lat, lo = w.lon
        Task { [weak self] in
            guard let m = await Chatgi.jarimal(la, lo) else { return }
            await MainActor.run { self?.mal("여기는 " + m + (m.hasSuffix("다.") || m.hasSuffix("다") ? "" : "입니다.")) }
        }
    }

    // MARK: ③ 차가 멈춰 있을 때 — 웹 mumchum.js 1.0

    private var mJul: [(lat: Double, lon: Double, t: Date, sp: Double?)] = []
    private var mum = false
    private var mumT = Date()
    private var dasiSu = 0
    private var kkadakMal = false
    private var kkadakCh = false
    private var mBunMal: Set<Int> = []

    /// 멈춘 동안이면 true — 그 자리를 움직임으로 치지 않음
    private func mumchumBogo(_ w: Wichi, _ y: Yeojeong) -> Bool {
        let ije = Date()
        let acc = w.ochae > 0 ? w.ochae : 10
        let sp: Double? = w.sokdo >= 0 ? w.sokdo : nil
        mJul.append((w.lat, w.lon, ije, sp))
        mJul.removeAll { ije.timeIntervalSince($0.t) > 15 }
        if !mum {
            let gidal: Double = sp != nil ? 4 : 8
            let sij = mJul.filter { ije.timeIntervalSince($0.t) <= gidal + 2 }
            var seoNa = false
            if sij.count >= 3, let f = sij.first, ije.timeIntervalSince(f.t) >= gidal {
                if sp != nil { seoNa = sij.allSatisfy { ($0.sp ?? 99) < 0.8 } }
                else { seoNa = sij.allSatisfy { geori($0.lat, $0.lon, f.lat, f.lon) <= max(8, acc) } }
                if seoNa { mum = true; mumT = f.t; dasiSu = 0; kkadakMal = false; kkadakCh = false; mBunMal = [] }
            }
            return false
        }
        let cheot = mJul.first { $0.t >= mumT } ?? (lat: w.lat, lon: w.lon, t: ije, sp: sp)
        let umjik = (sp ?? 0) > 2 || geori(w.lat, w.lon, cheot.lat, cheot.lon) > max(20, acc * 1.5)
        dasiSu = umjik ? dasiSu + 1 : 0
        if dasiSu >= 2 {
            let gil = ije.timeIntervalSince(mumT)
            mum = false; mJul = []
            if gil >= 20 { mal("다시 움직입니다.") }
            return false
        }
        if ije.timeIntervalSince(mumT) >= 10 { kkadakChatgi(w.lat, w.lon, beoseu: y.talgeot == .beoseu) }
        let bun = Int(ije.timeIntervalSince(mumT) / 60)
        // 자세히(차 길 맞춤)에서는 차 길 맞춤이 「○분째 서 있습니다」를 말하므로 까닭만 말함
        let chaMatMal = y.talgeot == .cha && Seoljeong.shared.chaJeongdo >= 3
        if !chaMatMal, !kkadakMal, bun >= 1, bun <= 3 || bun % 5 == 0, !mBunMal.contains(bun) {
            mBunMal.insert(bun)
            mal("\(bun)분째 멈춰 있습니다.")
        }
        return true
    }

    private func kkadakChatgi(_ la: Double, _ lo: Double, beoseu: Bool) {
        guard !kkadakCh else { return }
        kkadakCh = true
        Task { [weak self] in
            var jrMal: String?
            if beoseu, let jr = await Beoseu.gakkaun(la, lo)?.first, WichiEngine.geori(la, lo, jr.lat, jr.lon) <= 30 {
                jrMal = "\(jr.ireum) 정류장에 멈춰 있습니다."
            }
            var sagori = false
            if jrMal == nil, let o = await Chatgi.json("neagori.php", ["lat": String(format: "%.5f", la), "lon": String(format: "%.5f", lo), "ban": "80"]),
               let rows = o["rows"] as? [[String: Any]], let r = rows.first {
                if let m = Chatgi.su(r["meter"]) { sagori = m <= 30 }
                else if let a = Chatgi.su(r["lat"]), let b = Chatgi.su(r["lon"]) { sagori = WichiEngine.geori(la, lo, a, b) <= 30 }
            }
            await MainActor.run {
                guard let self = self, self.mum, !self.kkadakMal else { return }
                if let m = jrMal { self.kkadakMal = true; self.mal(m) }
                else if sagori { self.kkadakMal = true; self.mal("사거리 앞에 멈춰 있습니다.") }
            }
        }
    }
}
