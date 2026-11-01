// 차 안 거들기 — 2.92.0 (261017-I46, 이사장님 승인 2026-10-17 「그러자」)
// 웹 길눈(chatta.html·teoneol.js·mumchum.js)에 있었는데 앱으로 옮기지 못했던 차 안 안내 세 가지를 옮김.
//   ① 터널 미리 알림(2026-09-14 이사장님 지시) — 15초 뒤에 닿을 자리의 터널을 이름·길이와 함께. 이름이 없으면 고장 이름을 붙임.
//      터널 안에서 위성이 끊기면 「터널 안이라 위성이 잠시 끊깁니다」 한 번, 나오면 「터널을 지났습니다」.
//   ② 차가 크게 돌면(빌드 260906-70, 2.113.0 진행 방향으로 셈·한결같을 때만) 「방금 3시 방향으로 돌았습니다. ○○까지 ○ 남았습니다」, 조금 뒤 「여기는 ○○입니다」.
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
        pFrom = nil; head = nil; hSaem = []; anjeongBang = nil
        mJul = []; mum = false; mBunMal = []
        if let t = tJikimi { t.invalidate(); tJikimi = nil }
    }

    /// AnnaeEngine.chaAnnae 가 자리마다 부름
    func wichi(_ w: Wichi, _ y: Yeojeong) {
        kyeojim = true
        gyoJinam(w)
        jinaGyo(w)
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
    /// 2.113.0 가장 가까운 토막까지의 거리와 그 토막의 방향
    private func julGeoriBang(_ la: Double, _ lo: Double, _ pt: [(Double, Double)]) -> (Double, Double) {
        var m = 1e9, bang = 0.0
        if pt.count >= 2 {
            for i in 1..<pt.count {
                let g = tomakGeori(la, lo, pt[i - 1], pt[i])
                if g < m { m = g; bang = WichiEngine.bangwi(pt[i - 1].0, pt[i - 1].1, pt[i].0, pt[i].1) }
            }
        }
        return (m, bang)
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
            let (jul, julBang) = julGeoriBang(w.lat, w.lon, r.pt)
            let a = r.pt[0], b = r.pt[r.pt.count - 1]
            let da = geori(w.lat, w.lon, a.0, a.1), db = geori(w.lat, w.lon, b.0, b.1)
            let mun = da <= db ? a : b, de = min(da, db)
            let key = "\(r.ireum)_\(String(format: "%.4f", mun.0))"
            if jul < 25 {
                // 2.113.0 (261026-I67) 터널 길과 같은 쪽으로 달릴 때만 터널 안으로 침 — 위나 옆을 가로지를 때는 아님
                if let h = head, gakCha(h, julBang) > 40 && gakCha(h, julBang + 180) > 40 { continue }
                if tDeun?.key != key { tDeun = (key, r); tDeunTtae = Date(); tWiseongMal = false }
                continue
            }
            // 2.113.0 (261026-I67, 이사장님 승인 2026-10-26 「1」) 차가 그 터널 길 위를 터널 쪽으로 달릴 때만 미리 알림 —
            //   터널 입구에서 안쪽으로 향하는 방향과 차의 진행 방향이 40도 안이고, 입구에서 뻗은 터널 줄에서 옆으로 40미터 안일 때.
            //   (10-26 퇴계로에서 옆길·건물 밑 통로를 「곧 터널」로 일곱 번 알리던 일)
            guard let h = head else { continue }
            if gakCha(h, WichiEngine.bangwi(w.lat, w.lon, mun.0, mun.1)) >= 65 { continue }
            let an = da <= db ? r.pt[1] : r.pt[r.pt.count - 2]
            let anjjok = WichiEngine.bangwi(mun.0, mun.1, an.0, an.1)
            if gakCha(h, anjjok) > 40 { continue }
            let yeop = de * sin(gakCha(WichiEngine.bangwi(w.lat, w.lon, mun.0, mun.1), anjjok) * .pi / 180)
            if yeop > 40 { continue }
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

    // 2.113.0 (261026-I67, 이사장님 승인 2026-10-26 「1」) 돈 방향은 폰이 재 주는 차의 진행 방향으로 셈하고(오차 20미터 안 자리만),
    //   돈 뒤 방향이 3초 가까이 한결같을 때만 「돌기 전」 방향과 견주어 말함. 오락가락하면 말하지 않음.
    //   (10-26 시내에서 2시 방향으로 꺾었는데 11시 방향이라 하고, 퇴계로에서 14초마다 11·10·2·3·11시로 오락가락하던 일 —
    //    오차 50미터 자리를 15미터마다 이어 방향을 재고, 흔들림 가운데 더 크게 돈 쪽을 골랐던 탓)
    private var pFrom: (Double, Double)?
    private var head: Double?
    private var hSaem: [(t: Date, h: Double)] = []
    private var anjeongBang: Double?
    private var doraTtae = Date.distantPast

    /// 여러 방향의 평균(북쪽을 건너도 맞게)
    private func wonPyeong(_ l: [Double]) -> Double {
        var x = 0.0, y = 0.0
        for h in l { x += cos(h * .pi / 180); y += sin(h * .pi / 180) }
        let d = atan2(y, x) * 180 / .pi
        return d < 0 ? d + 360 : d
    }

    private func doramBoda(_ w: Wichi, _ y: Yeojeong) {
        guard w.ochae > 0, w.ochae <= 20, !w.georeumChu, !w.jjimjak else { return }
        let ije = Date()
        var bang: Double? = nil
        var bangsik = "jinhaeng"
        if w.sokdo > 3 && w.jinhaeng >= 0 {
            bang = w.jinhaeng           // 폰이 재 주는 진행 방향(빠르기로 잼 — 건물 사이에서도 덜 흔들림)
            pFrom = (w.lat, w.lon)
        } else if let pf = pFrom {
            if geori(pf.0, pf.1, w.lat, w.lon) >= 40 {   // 진행 방향이 없으면 40미터 넘게 움직인 두 자리로
                bang = WichiEngine.bangwi(pf.0, pf.1, w.lat, w.lon); bangsik = "jari"
                pFrom = (w.lat, w.lon)
            }
        } else {
            pFrom = (w.lat, w.lon)
        }
        guard let b = bang else { return }
        head = b   // 터널 미리 알림도 이 방향을 씀
        hSaem.append((ije, b))
        hSaem.removeAll { ije.timeIntervalSince($0.t) > 4 }
        guard let cheot = hSaem.first, ije.timeIntervalSince(cheot.t) >= 2.5, hSaem.count >= 3 else { return }
        let pyeong = wonPyeong(hSaem.map { $0.h })
        guard hSaem.allSatisfy({ gakCha($0.h, pyeong) <= 15 }) else { return }   // 아직 도는 중이거나 흔들림
        guard let jeon = anjeongBang else { anjeongBang = pyeong; return }
        let deul = (pyeong - jeon + 540).truncatingRemainder(dividingBy: 360) - 180
        anjeongBang = pyeong   // 천천히 굽는 길은 따라감
        guard abs(deul) >= 35, ije.timeIntervalSince(doraTtae) > 10 else { return }
        doraTtae = ije
        hSaem = []
        var sh = Int(((deul.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360) / 30).rounded()) % 12
        if sh == 0 { sh = 12 }
        let d = geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon)
        Girok.shared.namgi("cha_doram", ["jeon": Int(jeon), "hu": Int(pyeong), "deul": Int(deul), "si": sh,
                                         "ochae": Int(w.ochae), "sokdo": Int(w.sokdo * 3.6), "bangsik": bangsik])
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
    // 2.123.0 (261102, 이사장님 지시 2026-11-02 「교차로에서 멈추면 그 명칭도」) 멈춘 까닭 가리기
    private var sinhoMum = false          // 신호를 기다리는 것으로 본 멈춤
    private var oraeMal = false           // 「신호보다 오래 서 있습니다」 말함
    private var gyoGiok: (la: Double, lo: Double, ireum: String, gakkai: Bool)?   // 멈춘 교차로 — 지나면 알림
    private var mumGirok: [Date] = []     // 멈춘 때들(5분)
    private var jinJul: [(la: Double, lo: Double, t: Date)] = []   // 지나온 자리(5분)
    private var makimMalT = Date.distantPast

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
                if seoNa { mum = true; mumT = f.t; dasiSu = 0; kkadakMal = false; kkadakCh = false; mBunMal = []; sinhoMum = false; oraeMal = false; mumGirok.append(f.t); makimBoda(ije) }
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
            sinhoMum = false
            return false
        }
        if ije.timeIntervalSince(mumT) >= 10 { kkadakChatgi(w.lat, w.lon, beoseu: y.talgeot == .beoseu) }
        if sinhoMum && !oraeMal && ije.timeIntervalSince(mumT) >= 180 { oraeMal = true; mal("신호보다 오래 서 있습니다. 차가 밀리는 것 같습니다.") }
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
        let h = head
        Task { [weak self] in
            var jrMal: String?
            if beoseu, let jr = await Beoseu.gakkaun(la, lo)?.first, WichiEngine.geori(la, lo, jr.lat, jr.lon) <= 30 {
                jrMal = "\(jr.ireum) 정류장에 멈춰 있습니다."
            }
            // 2.123.0 (261102, 이사장님 지시) 교차로 이름·몇 갈래·신호등 — 오픈스트리트맵(jido.php gal=gyo)
            var gyoMal: String?, gyo: (Double, Double, String)?, sinho = false, jaryoIsseum = false
            if jrMal == nil, Seoljeong.shared.jidoOn,
               let o = await Chatgi.json("jido.php", ["gal": "gyo", "lat": String(format: "%.5f", la), "lon": String(format: "%.5f", lo), "ban": "90"]) {
                jaryoIsseum = Chatgi.su(o["deo"]) != 1
                var best: (d: Double, r: [String: Any])?
                for r in (o["gyo"] as? [[String: Any]]) ?? [] {
                    guard let a = Chatgi.su(r["a"]), let b = Chatgi.su(r["o"]) else { continue }
                    let d = WichiEngine.geori(la, lo, a, b)
                    if d > 70 { continue }
                    // 뒤에 두고 선 교차로는 빼고(25미터 안은 그대로) 가는 쪽 앞의 것
                    if let hh = h, d > 25 { let c = abs((WichiEngine.bangwi(la, lo, a, b) - hh + 540).truncatingRemainder(dividingBy: 360) - 180); if c > 70 { continue } }
                    if best == nil || d < best!.d { best = (d, r) }
                }
                if let b = best, let a = Chatgi.su(b.r["a"]), let oo = Chatgi.su(b.r["o"]) {
                    let ireum = ChaGeodeul.gyoIreum((b.r["n"] as? String) ?? "", Int(Chatgi.su(b.r["gal"]) ?? 0))
                    sinho = Chatgi.su(b.r["s"]) == 1
                    gyoMal = sinho ? "\(ireum) 앞에서 신호를 기다리는 것 같습니다." : "\(ireum) 앞에 멈춰 있습니다."
                    gyo = (a, oo, ireum)
                }
            }
            // 2.123.0 (261102, 이사장님 승인 「2」) 서울은 실시간 도로 속도로 막힘을 가림(서울 열린데이터 TOPIS)
            var gilMal = ""
            if Seoljeong.shared.jidoOn, let so = await Chatgi.json("jido.php", ["gal": "sokdo", "lat": String(format: "%.5f", la), "lon": String(format: "%.5f", lo), "bang": h.map { String(Int($0)) } ?? ""]),
               Chatgi.su(so["eopseum"]) != 1, let sd = Chatgi.su(so["sokdo"]) {
                gilMal = ChaGeodeul.sokdoMal(Int(sd), (so["gil"] as? String) ?? "", (so["e"] as? String) ?? "")
            }
            var sagori = false
            if jrMal == nil && gyoMal == nil && !jaryoIsseum, let o = await Chatgi.json("neagori.php", ["lat": String(format: "%.5f", la), "lon": String(format: "%.5f", lo), "ban": "80"]),
               let rows = o["rows"] as? [[String: Any]], let r = rows.first {
                if let m = Chatgi.su(r["meter"]) { sagori = m <= 30 }
                else if let a = Chatgi.su(r["lat"]), let b = Chatgi.su(r["lon"]) { sagori = WichiEngine.geori(la, lo, a, b) <= 30 }
            }
            await MainActor.run {
                guard let self = self, self.mum, !self.kkadakMal else { return }
                let deo = gilMal.isEmpty ? "" : " " + gilMal
                if let m = jrMal { self.kkadakMal = true; self.mal(m + deo) }
                else if let m = gyoMal {
                    self.kkadakMal = true; self.sinhoMum = sinho; self.mal(m + deo)
                    if let g = gyo { self.gyoGiok = (g.0, g.1, g.2, false) }
                }
                else if sagori { self.kkadakMal = true; self.mal("사거리 앞에 멈춰 있습니다." + deo) }
                else if jaryoIsseum { self.kkadakMal = true; self.mal(gilMal.isEmpty ? "교차로나 정류장이 아닌 곳입니다. 차가 밀려 멈춘 것 같습니다." : "교차로나 정류장이 아닌 곳에 멈춰 있습니다." + deo) }
                else if !gilMal.isEmpty { self.kkadakMal = true; self.mal("멈춰 있습니다." + deo) }
            }
        }
    }

    /// 서울 실시간 속도 말 — 시속 15 아래 막힘, 25 아래 천천히, 그 위 잘 빠짐
    static func sokdoMal(_ sd: Int, _ gil: String, _ e: String) -> String {
        let jeongdo = sd < 15 ? "막히고 있습니다" : (sd < 25 ? "천천히 가고 있습니다" : "잘 빠지고 있습니다")
        let eodi = gil.isEmpty ? "이 길" : (e.isEmpty ? gil : "\(gil) \(e) 쪽")
        return "\(JidoAllim.eun(eodi)) 지금 시속 \(sd)킬로미터로 \(jeongdo)."
    }

    /// 「서울역교차로」·「강남역사거리」는 그대로, 이름이 없으면 몇 갈래인지로
    /// 2.124.0 이름은 국토교통부 표준노드링크 공식 이름이 먼저(서버 jido.php 가 50미터 안 것만 붙임)
    static func gyoIreum(_ n: String, _ gal: Int) -> String {
        let moyang = gal == 3 ? "삼거리" : (gal == 4 ? "사거리" : (gal == 5 ? "오거리" : (gal == 6 ? "육거리" : "교차로")))
        let nm = n.trimmingCharacters(in: .whitespaces)
        if nm.isEmpty { return "이름 없는 " + moyang }
        if gyoIreumIm(nm) { return nm }
        return nm + " " + moyang
    }
    /// 이름 자체가 교차로 이름인지(○○사거리·○○교차로·○○네거리·○○나들목·○○IC 등)
    static func gyoIreumIm(_ nm: String) -> Bool {
        for k in ["거리", "교차로", "나들목", "분기점", "로터리", "IC", "JC", "요금소"] where nm.hasSuffix(k) { return true }
        return false
    }

    // 2.124.0 (261102, 이사장님 승인 「1」 4번) 지나는 교차로 — 이름이 교차로 이름인 공식 교차로만,
    //   30미터 안에 들었다가 45미터 넘게 멀어지면 「○○를 지났습니다. 다음은 ○○입니다」 한 번(다음은 가는 쪽 20도 안, 700미터 안).
    //   말과 말 사이 15초 넘게, 같은 교차로는 10분 안에 다시 말하지 않음. 멈췄던 교차로는 gyoJinam 이 말함.
    private var jgGyo: [(la: Double, lo: Double, n: String)] = []
    private var jgJari: (Double, Double)?
    private var jgBatTtae = Date.distantPast
    private var jgBatneun = false
    private var jgHan: [String: Date] = [:]
    private var jgGakkai: (la: Double, lo: Double, n: String)?
    private var jgMalTtae = Date.distantPast

    private func jinaGyo(_ w: Wichi) {
        guard Seoljeong.shared.jidoOn, Seoljeong.shared.gyoJinaOn, !mum else { return }
        if w.sokdo >= 0 && w.sokdo < 2 { return }
        if jgJari == nil || geori(w.lat, w.lon, jgJari!.0, jgJari!.1) > 250 { jgBatgi(w.lat, w.lon) }
        let ije = Date()
        jgHan = jgHan.filter { ije.timeIntervalSince($0.value) < 600 }
        if let g = jgGakkai {
            if geori(w.lat, w.lon, g.la, g.lo) <= 45 { return }
            jgGakkai = nil
            let key = "\(g.n)|\(Int(g.la * 1000))|\(Int(g.lo * 1000))"
            if jgHan[key] != nil { return }
            jgHan[key] = ije
            if let m = gyoGiok, m.ireum == g.n { return }
            guard ije.timeIntervalSince(jgMalTtae) >= 15 else { return }
            var t = "\(JidoAllim.eul(g.n)) 지났습니다."
            if let h = head {
                var best: (d: Double, n: String)?
                for x in jgGyo where x.n != g.n {
                    let d = geori(w.lat, w.lon, x.la, x.lo)
                    if d < 60 || d > 700 { continue }
                    let c = abs((WichiEngine.bangwi(w.lat, w.lon, x.la, x.lo) - h + 540).truncatingRemainder(dividingBy: 360) - 180)
                    if c > 20 { continue }
                    if best == nil || d < best!.d { best = (d, x.n) }
                }
                if let b = best { t += " 다음은 \(b.n)입니다." }
            }
            jgMalTtae = ije
            mal(t)
            return
        }
        for x in jgGyo where geori(w.lat, w.lon, x.la, x.lo) < 30 {
            let key = "\(x.n)|\(Int(x.la * 1000))|\(Int(x.lo * 1000))"
            if jgHan[key] == nil { jgGakkai = x; break }
        }
    }

    private func jgBatgi(_ la: Double, _ lo: Double) {
        guard !jgBatneun, Date().timeIntervalSince(jgBatTtae) >= 20 else { return }
        jgBatneun = true; jgBatTtae = Date()
        Task { [weak self] in
            let o = await Chatgi.json("jido.php", ["gal": "gyo", "lat": String(format: "%.5f", la), "lon": String(format: "%.5f", lo), "ban": "500"])
            await MainActor.run {
                guard let self = self else { return }
                self.jgBatneun = false
                guard let o = o else { return }
                self.jgGyo = ((o["gyo"] as? [[String: Any]]) ?? []).compactMap { r in
                    guard let a = Chatgi.su(r["a"]), let b = Chatgi.su(r["o"]) else { return nil }
                    let n = ((r["n"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
                    guard (r["geungeo"] as? String) == "pyojun", ChaGeodeul.gyoIreumIm(n) else { return nil }
                    return (a, b, n)
                }
                if Chatgi.su(o["deo"]) != 1 { self.jgJari = (la, lo) }
            }
        }
    }

    /// 멈췄던 교차로를 지나면 한 번 알림
    private func gyoJinam(_ w: Wichi) {
        let ije = Date()
        jinJul.append((w.lat, w.lon, ije)); jinJul.removeAll { ije.timeIntervalSince($0.t) > 300 }
        guard var g = gyoGiok, !mum else { return }
        let d = geori(w.lat, w.lon, g.la, g.lo)
        if d < 30 { g.gakkai = true; gyoGiok = g; return }
        if g.gakkai && d > 45 { gyoGiok = nil; mal("\(JidoAllim.eul(g.ireum)) 지났습니다."); return }
        if d > 400 { gyoGiok = nil }   // 다른 길로 감
    }

    /// 5분 안에 세 번 넘게 서고 800미터도 못 갔으면 길이 막히는 것
    private func makimBoda(_ ije: Date) {
        mumGirok.removeAll { ije.timeIntervalSince($0) > 300 }
        guard mumGirok.count >= 3, ije.timeIntervalSince(makimMalT) > 900, let f = jinJul.first, ije.timeIntervalSince(f.t) >= 240 else { return }
        var m = 0.0
        for i in 1..<jinJul.count { m += geori(jinJul[i - 1].la, jinJul[i - 1].lo, jinJul[i].la, jinJul[i].lo) }
        if m < 800 { makimMalT = ije; mal("길이 막히고 있습니다. 5분 동안 \(mumGirok.count)번 섰습니다.") }
    }
}
