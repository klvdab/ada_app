// 기차 안내 — 2.91.0 (261017-I45, 이사장님 승인 2026-10-17 「그래 모두 승인」)
// 웹 길눈(chatta.html)에서 하던 기차 안내(2026-09-19 이사장님 지시)와 터널 미리 알림(2026-09-14 이사장님 지시)을
// 앱으로 옮기지 못해, KTX 부산행에서 남은 거리와 고장 이름만 말하던 일을 바로잡음.
//   역에 다가가면 「곧 ○○역에 도착합니다」, 역에 서면 「○○역에 도착했습니다」, 떠나면 「○○역을 떠났습니다. 다음 역은」,
//   지나치면 「지금 ○○역을 지나고 있습니다」, 내리실 역이면 「여기서 내리십니다」.
//   터널은 들어가기 전에 「곧 터널입니다」(터널 안에서는 위성이 끊겨 알 수 없음) — 협회 서버 cheolteoneol.php(오픈스트리트맵 기찻길 터널).
//   열차 시간표(공공데이터 TrainInfo, 협회 서버 gyotong.php)로 타신 열차를 맞춰 다음 역과 내리실 역의 도착 예정 시각을 함께.
import Foundation

final class GichaAnnae {
    static let shared = GichaAnnae()
    /// 2.97.0 지금 시각 — 여정 다시 돌려 보기 시험(tests/gicha)에서 기록의 시각을 넣으려고 바꿀 수 있게 함. 앱에서는 늘 지금
    static var sigye: () -> Date = { Date() }

    private struct Yeok { let ireum: String; let lat: Double; let lon: Double }
    private struct Teoneol { let ireum: String; let m: Int; let a: (Double, Double); let b: (Double, Double) }
    private struct Yeolcha { let beon: String; let jong: String; let chul: String; let chulId: String; let doIreum: String; let doTtae: Date }

    private(set) var kyeojim = false
    private var fx: [(t: Date, lat: Double, lon: Double)] = []
    private var vh: [(t: Date, v: Double)] = []
    private var yeok: [Yeok] = []
    private var yeokQ: (Double, Double)?
    private var yeokQTtae = Date.distantPast
    private var yeokBatneun = false
    private var mokYeok: Yeok?            // 내리실 역(목적지에서 가장 가까운 기차역)
    private var naerilM: Double = 8000
    private var said: [String: Date] = [:]
    /// 2.98.0 역마다 가장 가까웠던 거리 — 「지나고 있습니다」는 역을 실제로 지날 때(가장 가까운 자리를 넘어설 때)만
    private var gajangGakka: [String: Double] = [:]
    private var got: [String: Date] = [:]
    private var stopTtae: Date?
    private var stopSaid = ""
    private var tteonSaid = ""
    private var head: Double?
    private var headFrom: (Double, Double)?
    private var teoneol: [Teoneol] = []
    private var teoneolQ: (Double, Double)?
    private var teoneolQTtae = Date.distantPast
    private var teoneolBatneun = false
    private var teoneolSaid: Set<String> = []
    private var yeolcha: Yeolcha?
    private var yeolchaChatneun = false
    private var yeolchaChateun: Set<String> = []
    private var modu: [String: String] = [:]   // 역 이름 → 시간표 역 번호
    private var namTtae = GichaAnnae.sigye()

    // MARK: 켜기·끄기

    func kkeut() {
        guard kyeojim else { return }
        kyeojim = false
        fx = []; vh = []; said = [:]; gajangGakka = [:]; got = [:]; stopTtae = nil; stopSaid = ""; tteonSaid = ""
        head = nil; headFrom = nil; teoneolSaid = []; yeolcha = nil; yeolchaChateun = []; mokYeok = nil
        Girok.shared.namgi("gicha_annae_kkeut", [:])
    }

    /// AnnaeEngine 이 기차 안(타고 가는 중·기차)일 때 자리마다 부름
    func wichi(_ w: Wichi, _ y: Yeojeong) {
        if !kyeojim {
            kyeojim = true
            namTtae = GichaAnnae.sigye()
            Girok.shared.namgi("gicha_annae_sijak", [:])
            mal("기차 안내를 시작합니다. 역에 다가가거나 서고 떠날 때, 터널에 들어가기 전에 알려 드립니다.")
        }
        salpim(w, y)
    }

    private func mal(_ t: String) {
        SoriEngine.shared.mal(t, .annae)
        Girok.shared.namgi("gicha_mal", ["mal": t])
    }

    private func geori(_ a: (Double, Double), _ b: (Double, Double)) -> Double { WichiEngine.geori(a.0, a.1, b.0, b.1) }

    private func gakCha(_ a: Double, _ b: Double) -> Double { abs((a - b + 540).truncatingRemainder(dividingBy: 360) - 180) }

    // MARK: 살피기 — 웹 길눈 gichaSalpim 을 그대로 옮김

    private func salpim(_ w: Wichi, _ y: Yeojeong) {
        let now = GichaAnnae.sigye()
        let me = (w.lat, w.lon)
        if w.ochae <= 300 { fx.append((now, w.lat, w.lon)) }
        fx = fx.filter { now.timeIntervalSince($0.t) <= 60 }
        var v = 0.0
        if fx.count >= 2, let a = fx.first, let b = fx.last {
            let dt = b.t.timeIntervalSince(a.t)
            if dt >= 20 { v = geori((a.lat, a.lon), (b.lat, b.lon)) / dt }
        }
        let pv: Double? = w.sokdo >= 0 ? w.sokdo : nil
        let sfx = fx.filter { now.timeIntervalSince($0.t) <= 20 }
        var sv: Double?
        if sfx.count >= 2, let a = sfx.first, let b = sfx.last {
            let dt = b.t.timeIntervalSince(a.t)
            if dt >= 10 { sv = geori((a.lat, a.lon), (b.lat, b.lon)) / dt }
        }
        if sv == nil { sv = pv }
        if let s = sv { vh.append((now, s)) }
        vh = vh.filter { now.timeIntervalSince($0.t) <= 180 }
        let vmax = vh.map { $0.v }.max() ?? 0

        // 가는 쪽 — 300미터 넘게 움직였을 때만
        if let hf = headFrom {
            if geori(hf, me) > 300 { head = WichiEngine.bangwi(hf.0, hf.1, me.0, me.1); headFrom = me }
        } else { headFrom = me }
        if head == nil, w.banghyang >= 0, (pv ?? 0) > 5 { head = w.banghyang }

        if yeokQ == nil || now.timeIntervalSince(yeokQTtae) > 120 || geori(yeokQ!, me) > 8000 { yeokBatgi(me) }
        if mokYeok == nil { mokYeokJeonghagi(y) }
        if teoneolQ == nil || now.timeIntervalSince(teoneolQTtae) > 180 || geori(teoneolQ!, me) > 3000 { teoneolBatgi(me) }

        // 터널 미리 알림(역 안내보다 먼저 — 들어가면 알 수 없으므로)
        if let s = sv, s > 8 { teoneolBoda(me, s) }

        var near: (Yeok, Double)?
        for k in yeok {
            let m = geori(me, (k.lat, k.lon))
            if near == nil || m < near!.1 { near = (k, m) }
        }
        let seo = (sv.map { $0 < 1.5 } ?? false) && (pv.map { $0 < 1.5 } ?? true)
        if seo {
            if stopTtae == nil { stopTtae = now }
            if let st = stopTtae, now.timeIntervalSince(st) >= 10, let n = near, n.1 < 1500, stopSaid != n.0.ireum,
               yeokSeoji.contains(n.0.ireum) {
                // 2.97.0 시간표로 서지 않는 역 — 가까이에서 멈춰도 도착이 아님(신호를 기다리는 것)
                if said["멈춤:" + n.0.ireum] == nil {
                    said["멈춤:" + n.0.ireum] = now
                    mal("\(n.0.ireum) 가까이에서 잠시 멈췄습니다. 타신 열차는 이 역에 서지 않으니 그대로 계십시오.")
                }
                return
            }
            if let st = stopTtae, now.timeIntervalSince(st) >= 10, let n = near, n.1 < 1500, stopSaid != n.0.ireum {
                stopSaid = n.0.ireum; tteonSaid = ""
                said[n.0.ireum] = now; got[n.0.ireum] = now
                let nr = naeril(n.0)
                if nr == true {
                    mal("\(n.0.ireum)에 도착했습니다. 여기서 내리십시오.\(namMal(me, y)) 내리시면 맨 위의 내렸습니다를 누르십시오.")
                } else if nr == false {
                    mal("\(n.0.ireum)에 도착했습니다. 내리실 역이 아니니 그대로 계십시오.\(namMal(me, y))")
                } else {
                    mal("\(n.0.ireum)에 도착했습니다.")
                }
            }
            return
        }
        stopTtae = nil
        if !stopSaid.isEmpty, tteonSaid != stopSaid, let s = sv, s >= 4 {
            tteonSaid = stopSaid
            let tteonYeok = stopSaid
            let dm = daeum(me)
            var t = "\(tteonYeok)을 떠났습니다."
            if let d = dm { t += " 가는 쪽으로 다음 역은 \(d.0.ireum), 약 \(Annae.geoMal(d.1))입니다." }
            mal(t + namMal(me, y))
            yeolchaMatchugi(tteonYeok, now, y, daeumYeok: dm?.0)
            return
        }
        // 곧 도착 — 앞쪽 4킬로미터 안의 역에서 빠르기가 크게 줄면
        var ap: (Yeok, Double)?
        if let h = head {
            for k in yeok {
                let m = geori(me, (k.lat, k.lon))
                if m >= 4000 { continue }
                let df = gakCha(WichiEngine.bangwi(me.0, me.1, k.lat, k.lon), h)
                if df > 60 && m > 300 { continue }
                if ap == nil || m < ap!.1 { ap = (k, m) }
            }
        }
        if let a = ap, got[a.0.ireum] == nil, !yeokSeoji.contains(a.0.ireum), let s = sv, vmax > 20, s < vmax * 0.65, s < 25 {
            got[a.0.ireum] = now; said[a.0.ireum] = now
            let nr = naeril(a.0)
            let si = yeokSigak(a.0)
            if nr == true { mal("곧 \(a.0.ireum)에 도착합니다. 여기서 내리십니다. 내릴 준비를 하십시오.") }
            else if nr == false { mal("곧 \(a.0.ireum)에 섭니다. 내리실 역은 아닙니다.\(si)") }
            else { mal("곧 \(a.0.ireum)에 도착합니다.\(si)") }
            return
        }
        // 2.98.0 (시험에서 찾음) 역 1.2킬로미터 앞에서 「지나고 있습니다」라고 하고, 그 뒤에 그 역에 서던 일 —
        //   역에 250미터 안으로 들거나, 가장 가까웠던 거리보다 80미터 넘게 멀어질 때(지나쳤을 때)만 말함
        if let n = near, n.1 < 1200 { gajangGakka[n.0.ireum] = min(gajangGakka[n.0.ireum] ?? .infinity, n.1) }
        if let n = near, n.1 < 1200, said[n.0.ireum] == nil, n.1 < 250 || n.1 > (gajangGakka[n.0.ireum] ?? n.1) + 80 {
            said[n.0.ireum] = now
            var t = "지금 \(n.0.ireum)을 지나고 있습니다."
            let dm = daeum(me)
            if let d = dm { t += " 가는 쪽으로 다음 역은 \(d.0.ireum), 약 \(Annae.geoMal(d.1))입니다." }
            mal(t)
            // 2.97.0 서지 않고 지나는 역에서도 다음 역을 시간표로 봄 — 그 역에 서는지 미리 알아야 「곧 섭니다」를 잘못 말하지 않음
            if let yc = yeolcha, let d = dm { daeumSigakMal(yc, d.0) }
            return
        }
        // 10분마다 한 번 — 남은 거리와 도착 예정
        if v > 10, now.timeIntervalSince(namTtae) > 600 {
            namTtae = now
            var t = "기차로 가시는 중입니다." + namMal(me, y)
            if let yc = yeolcha {
                // 2.97.0 예정 시각이 지났으면 지난 시각을 그대로 말하지 않음
                if now > yc.doTtae.addingTimeInterval(60) { t += " \(yc.doIreum) 도착 예정 시각 \(sigakMal(yc.doTtae))을 조금 넘겼습니다. 열차가 늦어지고 있습니다." }
                else { t += " \(yc.doIreum) 도착 예정은 \(sigakMal(yc.doTtae))입니다." }
            }
            else {
                let bun = Int((geori(me, (y.mokjeok.lat, y.mokjeok.lon)) / v / 60).rounded())
                if bun > 0 { t += " 지금 빠르기로 곧장 가면 약 \(bun)분 거리입니다." }
            }
            mal(t)
        }
    }

    private func namMal(_ me: (Double, Double), _ y: Yeojeong) -> String {
        " \(y.mokjeok.ireum)까지 곧장 잰 거리로 \(Annae.geoMal(geori(me, (y.mokjeok.lat, y.mokjeok.lon)))) 남았습니다."
    }

    /// 가는 쪽 다음 역 — 2.5킬로미터 넘게 떨어지고, 가는 쪽 35도 안
    private func daeum(_ me: (Double, Double)) -> (Yeok, Double)? {
        guard let h = head else { return nil }
        var best: (Yeok, Double)?
        for k in yeok {
            let m = geori(me, (k.lat, k.lon))
            if m < 2500 { continue }
            if gakCha(WichiEngine.bangwi(me.0, me.1, k.lat, k.lon), h) > 35 { continue }
            if best == nil || m < best!.1 { best = (k, m) }
        }
        return best
    }

    /// 내리실 역인가 — 목적지가 없으면 nil
    private func naeril(_ k: Yeok) -> Bool? {
        guard YeojeongEngine.shared.jigeum != nil else { return nil }
        // 2.97.0 (261017-I51, 이사장님 KTX에서 겪으심 2026-10-17) 목적지 8킬로미터 안의 역을 모두 내릴 역으로 보아
        //   서울역에 가시는데 영등포역·용산역에서 「여기서 내리십시오」라고 하던 일 — 내릴 역은 한 곳만
        if yeokSeoji.contains(k.ireum) { return false }
        if let yc = yeolcha { return yeokIreum(k.ireum) == yeokIreum(yc.doIreum) }
        guard let mk = mokYeok else { return nil }
        return yeokIreum(k.ireum) == yeokIreum(mk.ireum) || geori((k.lat, k.lon), (mk.lat, mk.lon)) < 300
    }

    /// 목적지에서 가장 가까운 기차역 — 그 거리의 두 배(적어도 8킬로미터) 안의 역을 내릴 역으로 봄(웹 길눈과 같음)
    private func mokYeokJeonghagi(_ y: Yeojeong) {
        var best: (Yeok, Double)?
        for (n, la, lo) in GichaYeok.mokrok {
            let m = WichiEngine.geori(y.mokjeok.lat, y.mokjeok.lon, la, lo)
            if best == nil || m < best!.1 { best = (Yeok(ireum: n + "역", lat: la, lon: lo), m) }
        }
        guard let b = best else { return }
        mokYeok = b.0
        naerilM = max(8000, b.1 * 2)
    }

    // MARK: 역 목록 — 협회 서버 둘레 찾기(기차역, 20킬로미터)와 앱 안 기차역 목록을 합침

    private func yeokBatgi(_ me: (Double, Double)) {
        guard !yeokBatneun else { return }
        yeokBatneun = true
        yeokQ = me; yeokQTtae = GichaAnnae.sigye()
        // 앱 안 목록을 먼저(통신이 끊겨도 큰 역은 말함)
        var mok: [String: Yeok] = [:]
        for (n, la, lo) in GichaYeok.mokrok where WichiEngine.geori(me.0, me.1, la, lo) <= 30_000 {
            mok[n + "역"] = Yeok(ireum: n + "역", lat: la, lon: lo)
        }
        if yeok.isEmpty { yeok = Array(mok.values) }
        let q = ["a": "chatgi", "q": "기차역", "m": "20000", "lat": String(format: "%.6f", me.0), "lon": String(format: "%.6f", me.1)]
        let mok0 = mok
        Task { [weak self] in
            let d = try? await Tongsin.shared.getSae("dulle.php", q)
            await MainActor.run {
                guard let self = self else { return }
                self.yeokBatneun = false
                var mok = mok0
                if let d = d, let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any],
                   let rows = o["rows"] as? [[String: Any]] {
                    for r in rows {
                        guard var n = (r["ireum"] as? String)?.trimmingCharacters(in: .whitespaces),
                              let la = Chatgi.su(r["lat"]), let lo = Chatgi.su(r["lon"]) else { continue }
                        if n.range(of: "폐역|화물|신호장|기지|호선", options: .regularExpression) != nil { continue }
                        guard n.range(of: #"^[^\s]+역(\s+[^\s]+선)?$"#, options: .regularExpression) != nil else { continue }
                        if let sp = n.firstIndex(of: " ") { n = String(n[..<sp]) }
                        if mok[n] == nil { mok[n] = Yeok(ireum: n, lat: la, lon: lo) }
                    }
                }
                self.yeok = Array(mok.values)
            }
        }
    }

    // MARK: 터널 미리 알림

    private func teoneolBatgi(_ me: (Double, Double)) {
        guard !teoneolBatneun else { return }
        teoneolBatneun = true
        teoneolQ = me; teoneolQTtae = GichaAnnae.sigye()
        let q = ["lat": String(format: "%.6f", me.0), "lon": String(format: "%.6f", me.1), "ban": "8000"]
        Task { [weak self] in
            let d = try? await Tongsin.shared.getSae("cheolteoneol.php", q)
            await MainActor.run {
                guard let self = self else { return }
                self.teoneolBatneun = false
                guard let d = d, let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any],
                      let rows = o["rows"] as? [[String: Any]] else { return }
                self.teoneol = rows.compactMap { r in
                    guard let a = r["a"] as? [Any], let b = r["b"] as? [Any], a.count == 2, b.count == 2,
                          let a0 = Chatgi.su(a[0]), let a1 = Chatgi.su(a[1]), let b0 = Chatgi.su(b[0]), let b1 = Chatgi.su(b[1]) else { return nil }
                    let m = Int(Chatgi.su(r["m"]) ?? 0)
                    return Teoneol(ireum: (r["ireum"] as? String) ?? "", m: m, a: (a0, a1), b: (b0, b1))
                }
            }
        }
    }

    private func teoneolBoda(_ me: (Double, Double), _ sv: Double) {
        guard let h = head else { return }
        let ap = max(1500, sv * 45)   // 45초 앞
        for t in teoneol {
            let k = "\(t.a.0),\(t.a.1)"
            if teoneolSaid.contains(k) { continue }
            let da = geori(me, t.a), db = geori(me, t.b)
            let ip = da < db ? t.a : t.b
            let dIp = min(da, db)
            if dIp < 120 || dIp > ap { continue }
            if gakCha(WichiEngine.bangwi(me.0, me.1, ip.0, ip.1), h) > 30 { continue }
            // 2.97.0 터널이 가는 쪽으로 뻗어야 함 — 서울역·용산역 둘레에서 갈라져 나가는 공항철도·경의중앙선 터널(9.2·5.6킬로미터)을
            //   타신 기차의 터널로 잘못 알리던 일
            let meonKkeut = da < db ? t.b : t.a
            if t.m >= 300, gakCha(WichiEngine.bangwi(ip.0, ip.1, meonKkeut.0, meonKkeut.1), h) > 45 { continue }
            teoneolSaid.insert(k)
            // 같은 터널의 다른 조각(위아래 선로)도 함께 말한 것으로
            for u in teoneol where geori(u.a, t.a) < 300 || geori(u.b, t.b) < 300 || geori(u.a, t.b) < 300 || geori(u.b, t.a) < 300 {
                teoneolSaid.insert("\(u.a.0),\(u.a.1)")
            }
            var m = "곧 터널입니다."
            if !t.ireum.isEmpty { m = "곧 \(t.ireum)입니다." }
            if t.m >= 100 { m += " 길이 약 \(Annae.geoMal(Double(t.m)))" }
            if t.m >= 2000, sv > 10 {
                let bun = Int((Double(t.m) / sv / 60).rounded())
                m += bun >= 1 ? ", 약 \(bun)분 동안 터널 안을 지납니다." : "입니다."
            } else if t.m >= 100 { m += "입니다." }
            mal(m)
            return
        }
    }

    // MARK: 열차 시간표로 도착 예정 시각

    private static let sigakPyo: DateFormatter = {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.timeZone = TimeZone(identifier: "Asia/Seoul")
        f.dateFormat = "yyyyMMddHHmmss"; return f
    }()
    private static let nalPyo: DateFormatter = {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.timeZone = TimeZone(identifier: "Asia/Seoul")
        f.dateFormat = "yyyyMMdd"; return f
    }()

    /// 2.92.0 말로 하기 「얼마나 걸려」에서 씀 — 맞춘 열차의 내리실 역 도착 예정
    var dochakYejeong: (String, Date)? { yeolcha.map { ($0.doIreum, $0.doTtae) } }
    func sigakMalHagi(_ d: Date) -> String { sigakMal(d) }

    private func sigakMal(_ d: Date) -> String {
        var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "Asia/Seoul")!
        let h = c.component(.hour, from: d), m = c.component(.minute, from: d)
        return m == 0 ? "\(h)시" : "\(h)시 \(m)분"
    }

    private func yeokIreum(_ n: String) -> String { n.hasSuffix("역") ? String(n.dropLast()) : n }

    private func moduBatgi() async {
        guard modu.isEmpty, let d = try? await Tongsin.shared.getSae("gyotong.php", ["a": "modu", "gal": "gicha"]),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any],
              let rows = o["rows"] as? [[String: Any]] else { return }
        var m: [String: String] = [:]
        for r in rows { if let id = r["id"] as? String, let n = r["ireum"] as? String, m[n] == nil { m[n] = id } }
        await MainActor.run { self.modu = m }
    }

    private func pyeon(_ chulId: String, _ doId: String) async -> [[String: Any]]? {
        let q = ["a": "pyeon", "gal": "gicha", "chul": chulId, "do": doId, "nal": GichaAnnae.nalPyo.string(from: GichaAnnae.sigye())]
        guard let d = try? await Tongsin.shared.getSae("gyotong.php", q),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else { return nil }
        return o["rows"] as? [[String: Any]]
    }

    /// 역을 떠난 때로 타신 열차를 맞춤 — 그 역에서 내리실 역까지 가는 편 가운데 떠난 때와 가장 가까운 것(20분 안)
    private func yeolchaMatchugi(_ tteon: String, _ ttae: Date, _ y: Yeojeong, daeumYeok: Yeok?) {
        if let yc = yeolcha {
            if let dy = daeumYeok { daeumSigakMal(yc, dy) }
            return
        }
        guard !yeolchaChatneun, !yeolchaChateun.contains(tteon), let mk = mokYeok, mk.ireum != tteon else { return }
        yeolchaChatneun = true
        yeolchaChateun.insert(tteon)
        Task { [weak self] in
            guard let self = self else { return }
            await self.moduBatgi()
            let chul = self.yeokIreum(tteon), doN = self.yeokIreum(mk.ireum)
            guard let cid = self.modu[chul], let did = self.modu[doN], let rows = await self.pyeon(cid, did) else {
                await MainActor.run { self.yeolchaChatneun = false }
                return
            }
            var best: ([String: Any], Double)?
            for r in rows {
                guard let ds = r["depplandtime"] as? String ?? (r["depplandtime"] as? NSNumber)?.stringValue,
                      let dt = GichaAnnae.sigakPyo.date(from: ds) else { continue }
                let cha = ttae.timeIntervalSince(dt)   // 늦게 떠나는 것은 흔하고, 일찍 떠나는 것은 드묾
                if cha < -180 || cha > 1200 { continue }
                if best == nil || abs(cha) < abs(best!.1) { best = (r, cha) }
            }
            await MainActor.run {
                self.yeolchaChatneun = false
                guard let b = best?.0,
                      let as_ = b["arrplandtime"] as? String ?? (b["arrplandtime"] as? NSNumber)?.stringValue,
                      let at = GichaAnnae.sigakPyo.date(from: as_) else {
                    Girok.shared.namgi("gicha_yeolcha_mot", ["chul": chul, "do": doN, "su": rows.count])
                    return
                }
                let beon = (b["trainno"] as? String ?? (b["trainno"] as? NSNumber)?.stringValue ?? "")
                let jong = (b["traingradename"] as? String) ?? "열차"
                let yc = Yeolcha(beon: beon, jong: jong, chul: chul, chulId: cid, doIreum: mk.ireum, doTtae: at)
                self.yeolcha = yc
                let bn = Int(beon) ?? 0
                Girok.shared.namgi("gicha_yeolcha", ["beon": beon, "jong": jong, "chul": chul, "do": doN])
                self.mal("타신 열차는 \(jong) \(bn > 0 ? "\(bn)호" : "")로 보입니다. \(mk.ireum) 도착 예정은 \(self.sigakMal(at))입니다.")
                if let dy = daeumYeok, dy.ireum != mk.ireum { self.daeumSigakMal(yc, dy) }
            }
        }
    }

    /// 다음 역 도착 예정 — 같은 열차가 그 역에 서는지 시간표로 봄
    private var daeumMalhan: Set<String> = []
    private func daeumSigakMal(_ yc: Yeolcha, _ dy: Yeok) {
        let n = yeokIreum(dy.ireum)
        guard !daeumMalhan.contains(n), let did = modu[n] else { return }
        daeumMalhan.insert(n)
        Task { [weak self] in
            guard let self = self, let rows = await self.pyeon(yc.chulId, did) else { return }
            let gat = rows.first { ($0["trainno"] as? String ?? ($0["trainno"] as? NSNumber)?.stringValue) == yc.beon }
            await MainActor.run {
                if let g = gat, let s = g["arrplandtime"] as? String ?? (g["arrplandtime"] as? NSNumber)?.stringValue,
                   let t = GichaAnnae.sigakPyo.date(from: s) {
                    self.yeokSigakPyo[dy.ireum] = t
                    self.mal("\(dy.ireum) 도착 예정은 \(self.sigakMal(t))입니다.")
                } else if !rows.isEmpty {
                    self.yeokSeoji.insert(dy.ireum)
                    self.mal("타신 열차는 \(dy.ireum)에 서지 않고 지나갑니다.")
                }
            }
        }
    }
    private var yeokSigakPyo: [String: Date] = [:]
    private var yeokSeoji: Set<String> = []

    private func yeokSigak(_ k: Yeok) -> String {
        if let t = yeokSigakPyo[k.ireum] { return " 도착 예정은 \(sigakMal(t))입니다." }
        return ""
    }
}
