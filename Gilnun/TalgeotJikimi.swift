// 2.64.0 (261011-I18, 이사장님 승인 2026-10-11 「1」)
// 탈것 지키미 — 배(선박) 알아보기, 배 안 지금 자리 알리기, 기차·배에서 내리셨는지 한 번 여쭙기
//
// 배 알아보기: 물 위(땅 주소가 없음, 서버 jeom.php a=jimyeong 이 카카오 대신 오픈스트리트맵으로 답함)가 1분 간격으로 두 번 잇달아 맞고,
//   항구·여객선 터미널에서 떠났거나 시속 10~70킬로미터로 2분 넘게 고르게 갔으면 배로 보고 지킴(바다 위 다리를 달리는 차는 카카오 도로 주소가 나와 걸러짐)
// 배 안 지금 자리: 「지금 통영시 앞바다입니다. 통영항여객선터미널에서 12킬로미터 나왔고, 욕지도까지 18킬로미터, 약 35분 남았습니다.
//   3시 방향 2킬로미터에 연대도가 있습니다.」 — 섬·등대를 2킬로미터 안으로 지날 때 한 번, 설정 간격(처음 10분)마다, 물으실 때
// 내리셨는지 여쭙기: 기차·배 지킴 중 역·항구 가까이에서 2분 넘게 천천히 걸으며 서 있던 자리에서 100미터 넘게 멀어지면 한 번 여쭘.
//   길눈이 스스로 바꾸지는 않음 — 이사장님 원칙 「바꾸실 때까지 지킴」
import Foundation
import Combine

final class TalgeotJikimi: ObservableObject {
    static let shared = TalgeotJikimi()

    /// 내리셨는지 여쭙는 중 — 화면에 「아직 타고 있습니다」 단추를 냄
    @Published private(set) var naerimMureum = false

    static let hanggu: [(String, Double, Double)] = [
        ("인천항 연안여객터미널", 37.4541, 126.59853),
        ("인천항 국제여객터미널", 37.4237, 126.6053),
        ("방아머리선착장", 37.29636, 126.57442),
        ("대천연안여객선터미널", 36.32716, 126.51154),
        ("군산항 연안여객터미널", 35.97797, 126.62926),
        ("격포항", 35.62043, 126.47001),
        ("목포연안여객선터미널", 34.78202, 126.38428),
        ("목포국제여객터미널", 34.78108, 126.38196),
        ("진도항", 34.37408, 126.13434),
        ("완도항여객선터미널", 34.31672, 126.76133),
        ("땅끝항", 34.29827, 126.53036),
        ("녹동항여객선터미널", 34.52294, 127.14437),
        ("여수연안여객선터미널", 34.73847, 127.73261),
        ("삼천포신항 여객선터미널", 34.92698, 128.08838),
        ("통영항여객선터미널", 34.83985, 128.42024),
        ("저구항", 34.73075, 128.60607),
        ("옥포항", 34.89067, 128.69852),
        ("장승포항", 34.86604, 128.72456),
        ("부산항연안여객터미널", 35.10288, 129.0415),
        ("부산항국제여객터미널", 35.11738, 129.04911),
        ("포항여객선터미널", 36.05153, 129.37883),
        ("묵호항여객선터미널", 37.5503, 129.1125),
        ("강릉항여객터미널", 37.77283, 128.95284),
        ("후포항", 36.67821, 129.45795),
        ("도동항", 37.48153, 130.90881),
        ("사동항", 37.46123, 130.87891),
        ("저동항", 37.49622, 130.90997),
        ("제주항연안여객터미널", 33.51959, 126.535),
        ("제주항국제여객터미널", 33.52605, 126.54365),
        ("성산포항종합여객터미널", 33.47342, 126.93369),
        ("모슬포항", 33.21655, 126.24966),
        ("신양항", 33.94457, 126.32887),
        ("우도 천진항", 33.49263, 126.95115),
        ("외포리선착장", 37.70044, 126.38093),
        ("삼목선착장", 37.49951, 126.45244),
        ("용기포항", 37.95438, 124.73688),
        ("덕적도 진리선착장", 37.22716, 126.15504),
        ("송공항", 34.84814, 126.22693),
        ("남강선착장", 34.80241, 126.11992),
        ("홍도항", 34.68261, 125.19318),
        ("청산도항", 34.18125, 126.85425),
        ("거문도항", 34.0275, 127.30887),
        ("욕지도 여객선터미널", 34.63489, 128.26686),
        ("제승당 선착장", 34.79422, 128.47188),
    ]

    static func gakkaunHang(_ lat: Double, _ lon: Double, banGyeong: Double = 1500) -> (ireum: String, geori: Double)? {
        var choe: (String, Double)?
        for (n, la, lo) in hanggu {
            let d = WichiEngine.geori(lat, lon, la, lo)
            if d <= banGyeong && d < (choe?.1 ?? .infinity) { choe = (n, d) }
        }
        return choe.map { ($0.0, $0.1) }
    }

    private var ssak = Set<AnyCancellable>()
    private var yj: YeojeongEngine { YeojeongEngine.shared }

    // 배 알아보기
    private var cheonCheonJari: Wichi?          // 마지막으로 천천히 있던 자리(배가 떠난 곳 어림)
    private var umjigSijak: Date?
    private var umjigSijakJari: Wichi?
    private var mulSalpimTtae = Date.distantPast
    private var mulSalpimJung = false
    private var mulYeonsok = 0
    // 배 안 안내
    private(set) var tteonanHang: String?
    private var tteonanJari: (Double, Double)?
    private var jariMalTtae = Date.distantPast
    private var seomSalpimTtae = Date.distantPast
    private var malhanSeom = Set<String>()
    private var gotDochakMal = false
    private var sokdoGirok: [(Date, Double)] = []
    // 내리셨는지
    private var neurinSijak: Date?
    private var neurinJari: Wichi?
    private var ibeonMureum = false

    init() {
        WichiEngine.shared.saeWichi
            .receive(on: DispatchQueue.main)
            .sink { [weak self] w in self?.wichiBatda(w) }
            .store(in: &ssak)
        yj.$jigeum
            .map { $0?.jikim }
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] jt in
                guard let self = self else { return }
                if jt == nil { self.naerimMureum = false; self.ibeonMureum = false; self.neurinSijak = nil }
                if jt == .seonbak {
                    // 배에 오른 자리 — 떠난 항구 이름
                    let w = self.umjigSijakJari ?? self.cheonCheonJari ?? WichiEngine.shared.jigeum
                    if let w = w {
                        self.tteonanJari = (w.lat, w.lon)
                        self.tteonanHang = TalgeotJikimi.gakkaunHang(w.lat, w.lon, banGyeong: 3000)?.ireum
                    }
                    self.jariMalTtae = Date()
                    self.gotDochakMal = false
                    self.malhanSeom.removeAll()
                } else if jt == nil {
                    self.tteonanHang = nil
                    self.tteonanJari = nil
                }
            }
            .store(in: &ssak)
    }

    // MARK: 위치를 받을 때마다

    private func wichiBatda(_ w: Wichi) {
        guard w.ochae <= 50 else { return }
        let kmh = w.sokdo * 3.6
        let now = Date()
        sokdoGirok.append((now, max(0, w.sokdo)))
        sokdoGirok.removeAll { now.timeIntervalSince($0.0) > 600 }
        baeBoda(w, kmh, now)
        naerimBoda(w, kmh, now)
    }

    /// 배를 타셨는가
    private func baeBoda(_ w: Wichi, _ kmh: Double, _ now: Date) {
        guard let y = yj.jigeum, !yj.jikimJung, !w.georeumChu else {
            umjigSijak = nil; mulYeonsok = 0
            return
        }
        if y.barojabeum && y.talgeot != .seonbak { return }   // 다른 탈것으로 바로잡으셨으면 그대로
        if kmh < 6 {
            cheonCheonJari = w
            umjigSijak = nil
            umjigSijakJari = nil
            mulYeonsok = 0
            return
        }
        guard kmh >= 10 && kmh <= 70 else {
            if kmh > 70 { umjigSijak = nil; mulYeonsok = 0 }   // 배는 시속 70킬로미터를 넘지 않음
            return
        }
        if umjigSijak == nil { umjigSijak = now; umjigSijakJari = cheonCheonJari ?? w }
        guard now.timeIntervalSince(mulSalpimTtae) >= 60, !mulSalpimJung else { return }
        mulSalpimTtae = now
        mulSalpimJung = true
        let q = ["a": "jimyeong", "lat": String(format: "%.6f", w.lat), "lon": String(format: "%.6f", w.lon)]
        Task {
            let o = await Chatgi.json("jeom.php", q)
            await MainActor.run {
                self.mulSalpimJung = false
                let eodi = o?["eodi"] as? String ?? ""
                if o == nil { return }   // 통신이 안 되면 셈하지 않음
                self.mulYeonsok = eodi == "osm" ? self.mulYeonsok + 1 : 0
                guard self.mulYeonsok >= 2, let s = self.umjigSijak, let sj = self.umjigSijakJari else { return }
                let hang = TalgeotJikimi.gakkaunHang(sj.lat, sj.lon)
                let goreuge = now.timeIntervalSince(s) >= 120
                guard hang != nil || goreuge else { return }
                Girok.shared.namgi("bae_gamji", ["hang": hang?.ireum ?? "", "mul": self.mulYeonsok])
                self.yj.jikimSijak(.seonbak, hang.map { "\($0.ireum)에서 떠나 물 위" } ?? "물 위를 고르게 감")
            }
        }
    }

    /// 기차·배에서 내리셨는가 — 스스로 바꾸지 않고 한 번 여쭘
    private func naerimBoda(_ w: Wichi, _ kmh: Double, _ now: Date) {
        guard let jt = yj.jikimTalgeot, let y = yj.jigeum, y.danggye == .taneunJung else { return }
        if kmh > 15 {
            neurinSijak = nil
            neurinJari = nil
            ibeonMureum = false
            if naerimMureum { naerimMureum = false }
            return
        }
        guard kmh < 7, !ibeonMureum else { return }
        let gakka: String?
        if jt == .gicha {
            gakka = GichaYeok.gakkaun(w.lat, w.lon, banGyeong: 700).map { $0.ireum + "역" }
        } else {
            gakka = TalgeotJikimi.gakkaunHang(w.lat, w.lon)?.ireum
                ?? (WichiEngine.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon) <= 1500 ? y.mokjeok.ireum : nil)
        }
        guard let got = gakka else { neurinSijak = nil; neurinJari = nil; return }
        if neurinSijak == nil { neurinSijak = now; neurinJari = w; return }
        guard let ns = neurinSijak, let nj = neurinJari, now.timeIntervalSince(ns) >= 120,
              TalgeotGamji.shared.jigeumUmjigim == "걸음",
              WichiEngine.geori(w.lat, w.lon, nj.lat, nj.lon) >= 100 else { return }
        ibeonMureum = true
        naerimMureum = true
        Girok.shared.namgi("naerim_mureum", ["tal": jt.rawValue, "got": got])
        SoriEngine.shared.mal("\(got)에 닿은 뒤 걸어서 멀어지고 계십니다. \(jt.ireum)에서 내리셨습니까? 맨 위 내렸습니다 단추나, 그 아래 아직 타고 있습니다 단추를 눌러 주십시오.")
    }

    /// 「아직 타고 있습니다」 — 이번 정차에서는 다시 묻지 않음
    func ajikTago() {
        naerimMureum = false
        ibeonMureum = true
        Girok.shared.namgi("naerim_dap", ["tago": true])
        SoriEngine.shared.mal("\(yj.talgeot.ireum) 안 안내를 이어 갑니다.")
    }

    // MARK: 배 안 안내 — AnnaeEngine 이 타고 가는 중에 부름

    func baeAnnae(_ w: Wichi, _ d: Double, _ y: Yeojeong) {
        let now = Date()
        if d <= 1000 && !gotDochakMal {
            gotDochakMal = true
            SoriEngine.shared.mal("곧 \(y.mokjeok.ireum)에 닿습니다. 내리실 준비를 하십시오. 내리시면 맨 위 내렸습니다 단추를 눌러 주십시오.")
            return
        }
        let gap = Seoljeong.shared.baeGap
        if gap > 0 && now.timeIntervalSince(jariMalTtae) >= Double(gap * 60) {
            jariMalTtae = now
            jariMal(w, d, y, seomMan: false)
            return
        }
        if now.timeIntervalSince(seomSalpimTtae) >= 60 {
            seomSalpimTtae = now
            jariMal(w, d, y, seomMan: true)
        }
    }

    /// 「지금 어떻게 가고 있습니까」·「지금 내 자리」를 배 안에서 물으셨을 때
    func jigeumBaeJari() {
        guard let w = WichiEngine.shared.jigeum, let y = yj.jigeum else {
            SoriEngine.shared.mal("아직 위치를 잡는 중입니다. 잡히면 다시 눌러 주십시오.")
            return
        }
        jariMalTtae = Date()
        jariMal(w, WichiEngine.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon), y, seomMan: false)
    }

    /// 평균 빠르기(초속 미터) — 최근 10분, 배가 움직인 때만
    private var pyeonggyunSokdo: Double {
        let v = sokdoGirok.map { $0.1 }.filter { $0 > 2 }
        guard v.count >= 5 else { return 8 }
        return v.reduce(0, +) / Double(v.count)
    }

    private func jariMal(_ w: Wichi, _ d: Double, _ y: Yeojeong, seomMan: Bool) {
        let la = String(format: "%.6f", w.lat), lo = String(format: "%.6f", w.lon)
        let apjjok = (w.banghyang >= 0 && w.sokdo > 1.5) ? w.banghyang : WichiEngine.shared.nachimban
        Task {
            async let jm = Chatgi.json("jeom.php", ["a": "jimyeong", "lat": la, "lon": lo])
            async let sm = Chatgi.json("jeom.php", ["a": "jangso", "q": "섬", "lat": la, "lon": lo])
            async let dd = Chatgi.json("jeom.php", ["a": "jangso", "q": "등대", "lat": la, "lon": lo])
            let (o, s, g) = await (jm, sm, dd)
            // 가장 가까운 섬이나 등대
            var gakkaun: (String, Double, Double, Double)?
            for r in ((s?["rows"] as? [[String: Any]]) ?? []) + ((g?["rows"] as? [[String: Any]]) ?? []) {
                guard let n = r["ireum"] as? String, let a = Chatgi.su(r["lat"]), let b = Chatgi.su(r["lon"]) else { continue }
                let m = WichiEngine.geori(w.lat, w.lon, a, b)
                if m <= 5000 && m < (gakkaun?.1 ?? .infinity) { gakkaun = (n, m, a, b) }
            }
            let gakkaunGot = gakkaun   // 바뀌지 않는 값으로 넘김(동시 실행 규칙)
            await MainActor.run {
                var seomMal = ""
                if let gk = gakkaunGot {
                    let (n, m, a, b) = gk
                    let bang = WichiEngine.bangwi(w.lat, w.lon, a, b)
                    let si = apjjok >= 0 ? WichiEngine.sigyeBanghyang(jeongmyeon: apjjok, mokpyo: bang) : 0
                    seomMal = (si == 0 ? "" : "\(si)시 방향 ") + "\(Annae.geoMal(m))에 \(n)\(TalgeotJikimi.iGa(n)) 있습니다."
                    if seomMan {
                        guard m <= 2000, !self.malhanSeom.contains(n) else { return }
                        self.malhanSeom.insert(n)
                        SoriEngine.shared.mal(seomMal, .jeongbo)
                        return
                    }
                }
                if seomMan { return }
                var mal = ""
                if let o = o {
                    let juso = o["juso"] as? String ?? ""
                    if (o["eodi"] as? String) == "osm" {
                        // 「연곡리, 통영시, 경상남도, 대한민국」 — 시·군 이름을 찾아 앞바다로
                        let jogak = juso.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
                        if let sg = jogak.first(where: { $0.hasSuffix("시") || $0.hasSuffix("군") }) { mal = "지금 \(sg) 앞바다입니다. " }
                        else if let p = jogak.first, !p.isEmpty { mal = "지금 \(p) 앞바다입니다. " }
                    } else if !juso.isEmpty {
                        mal = "지금 \(juso) 가까이입니다. "
                    }
                }
                if let h = self.tteonanHang, let t = self.tteonanJari {
                    mal += "\(h)에서 \(Annae.geoMal(WichiEngine.geori(t.0, t.1, w.lat, w.lon))) 나왔고, "
                }
                let bun = max(1, Int((d * 1.15 / self.pyeonggyunSokdo / 60).rounded()))
                let bunMal = bun < 60 ? "\(bun)분" : (bun % 60 == 0 ? "\(bun / 60)시간" : "\(bun / 60)시간 \(bun % 60)분")
                mal += "\(y.mokjeok.ireum)까지 \(Annae.geoMal(d)), 약 \(bunMal) 남았습니다."
                if !seomMal.isEmpty { mal += " " + seomMal }
                if let n = gakkaunGot?.0, (gakkaunGot?.1 ?? 9999) <= 2000 { self.malhanSeom.insert(n) }
                SoriEngine.shared.mal(mal)
            }
        }
    }

    /// 받침이 있으면 「이」, 없으면 「가」
    static func iGa(_ s: String) -> String {
        guard let c = s.unicodeScalars.last?.value, c >= 0xAC00, c <= 0xD7A3 else { return "이" }
        return (c - 0xAC00) % 28 == 0 ? "가" : "이"
    }
}
