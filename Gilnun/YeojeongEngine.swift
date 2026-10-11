// 여정 엔진 — 목적지, 지금 차례, 탈것을 한 곳에서 다룹니다.
// ① 화면이 바뀌어도, 음악을 틀어도, 앱을 껐다 켜도 여정은 그대로(폰 안 파일에 담음)
// ② 빠르기로 탈것을 알아채되, 이용자가 바로잡은 탈것이 가장 앞섬
// ③ 열두 시간 넘게 손대지 않은 여정은 저절로 끝냄
import Foundation
import Combine

enum Talgeot: String, Codable, CaseIterable {
    case georeum, cha, jihacheol, beoseu, gicha, gosokbeoseu
    /// 2.64.0 (261011-I18, 이사장님 승인 2026-10-11 「1」) 배(여객선·도선)
    case seonbak
    var ireum: String {
        switch self {
        case .georeum: return "걸어서"
        case .cha: return "차"
        case .jihacheol: return "지하철"
        case .beoseu: return "버스"
        case .gicha: return "기차"
        case .gosokbeoseu: return "고속버스"
        case .seonbak: return "배"
        }
    }
}

enum Danggye: String, Codable {
    case eotteoke, taneunGotKkaji, taneunJung, namEunGil, dochak
    var ireum: String {
        switch self {
        case .eotteoke: return "어떻게 갈지 정할 차례"
        case .taneunGotKkaji: return "타는 곳까지 가는 차례"
        case .taneunJung: return "타고 가는 중"
        case .namEunGil: return "남은 길을 걷는 차례"
        case .dochak: return "도착"
        }
    }
}

struct Mokjeok: Codable, Equatable {
    var ireum: String
    var lat: Double
    var lon: Double
    var juso: String
}

struct Yeojeong: Codable {
    var mokjeok: Mokjeok
    var danggye: Danggye
    var talgeot: Talgeot
    var barojabeum: Bool
    var sijak: Date
    var gaengsin: Date
    var jiha: JihaGil? = nil      // 지하철로 가는 여정이면 그 길
    var beoseu: BeoseuGil? = nil  // 버스로 가는 여정이면 그 정류장
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 지하철 안내를 역 입구 없이 짐작으로 시작했는가 — 앱을 다시 켜도 잇도록 담음
    var jungganJadong: Bool? = nil
    /// 2.64.0 (261011-I18, 이사장님 승인 2026-10-11 「1」) 탈것 지킴(기차·배) — 알아챘거나 「기차입니다」·「배입니다」를 누르시면 바꾸실 때까지 지킴.
    ///   기차·배 안에서 걸어 다니셔도(화장실·매점·갑판) 풀지 않음. 다른 탈것으로 바로잡으시거나 내렸습니다·도착·여정 끝에만 풂
    var jikim: Talgeot? = nil
}

final class YeojeongEngine: ObservableObject {
    static let shared = YeojeongEngine()

    @Published private(set) var jigeum: Yeojeong?
    /// 빠르기로 알아챈 탈것(이용자가 바로잡지 않았을 때 씀)
    @Published private(set) var sokdoChujeong: Talgeot = .georeum

    private var ssak = Set<AnyCancellable>()
    private var ppareunTtae: Date?
    private var neurinTtae: Date?
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 시속 150킬로미터 넘게 달린 마지막 때 — 10분 안이면 기차를 타고 계신 것으로 보고 지하철 짐작을 하지 않음
    private var gichaTtae: Date?
    var choegeunGicha: Bool { gichaJikimJung || (gichaTtae.map { Date().timeIntervalSince($0) < 600 } ?? false) }
    /// 2.64.0 (261011-I18, 이사장님 승인 2026-10-11 「1」) 지키는 탈것(기차·배)
    var jikimTalgeot: Talgeot? { jigeum?.jikim }
    var gichaJikimJung: Bool { jikimTalgeot == .gicha }
    var jikimJung: Bool { jikimTalgeot != nil }
    /// 2.64.0 기차·배로 알게 되었을 때 안내 쪽(AnnaeEngine)에 알림 — 탈것과 까닭 글을 함께 보냄
    let jikimSinho = PassthroughSubject<(Talgeot, String), Never>()
    /// 2.64.0 시속 150 넘은 자료 수(20초 안) — 한 번 튄 값에 속지 않게 두 번이면 기차
    private var gichaSu = 0
    private var gichaSuTtae: Date?
    private let pail: URL

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("gilnun", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        pail = dir.appendingPathComponent("yeojeong.json")
        WichiEngine.shared.saeWichi
            .sink { [weak self] w in self?.sokdoBoda(w) }
            .store(in: &ssak)
        // 2.40.0 움직임 감지기·기압계로 알아챈 탈것을 먼저 씀(땅속에서도 됨, 실내 위성 흔들림에 속지 않음)
        TalgeotGamji.shared.$chujeong
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] t in
                guard let self = self, TalgeotGamji.shared.sseulSuItda else { return }
                // 2.64.0 (261011-I18, 이사장님 승인 2026-10-11 「1」) 기차 지킴 중에는 움직임 감지기의 판단(차·지하철·걸음)으로 바꾸지 않음
                if self.jikimJung { return }
                // 2.64.0 기차역 근처에서 탔고 목적지가 멀리 있는 기차역이면 처음부터 기차(부산역에서 지하철로 알던 일)
                if (t == .cha || t == .jihacheol), self.sokdoChujeong == .georeum || self.sokdoChujeong == .jihacheol,
                   let y = self.jigeum, y.barojabeum == false || y.talgeot == .gicha,
                   let w = TalgeotGamji.shared.chaSijakJari ?? WichiEngine.shared.jigeum,
                   let yeok = GichaYeok.gichaInga(tan: w.lat, w.lon, mok: y.mokjeok) {
                    self.jikimSijak(.gicha, "\(yeok)역에서 탐")
                    return
                }
                if self.sokdoChujeong == .gicha && t == .cha { return }   // 기차는 빠르기로만 알아챔
                if self.sokdoChujeong == .gicha && t == .jihacheol && self.choegeunGicha { return }   // 2.61.0 기차 터널을 지하철로 보지 않음
                if t != self.sokdoChujeong { self.sokdoChujeong = t }
            }
            .store(in: &ssak)
    }

    /// 앱이 켜질 때 지난 여정을 되살림
    func bureogi() {
        guard let d = try? Data(contentsOf: pail),
              let y = try? JSONDecoder().decode(Yeojeong.self, from: d) else { return }
        if Date().timeIntervalSince(y.gaengsin) > 12 * 3600 {
            kkeut()
            return
        }
        jigeum = y
    }

    func jeonghagi(_ m: Mokjeok) {
        // 2.64.0 (261011-I18, 이사장님 승인 2026-10-11 「1」) 기차 안에서 목적지를 바꾸셔도 기차 지킴은 그대로
        let jikimIeum: Talgeot? = jigeum?.danggye == .taneunJung ? jigeum?.jikim : nil
        talgeotSaeroBogi()
        jigeum = Yeojeong(mokjeok: m, danggye: .eotteoke, talgeot: .georeum, barojabeum: false,
                          sijak: Date(), gaengsin: Date())
        if let jt = jikimIeum {
            jigeum?.jikim = jt
            jigeum?.talgeot = jt
            if jt == .gicha { sokdoChujeong = .gicha }
            DispatchQueue.main.async { self.jikimSinho.send((jt, "\(jt.ireum) 안에서 목적지를 바꾸심")) }
        }
        Girok.shared.namgi("yeojeong_sijak", ["mok": m.ireum])
        jeojang()
    }

    /// 2.60.0 (261009-I14, 이사장님 승인 2026-10-09 남산) 새 목적지를 정할 때 — 지금 탈것이 움직이고 있지 않으면 묵은 탈것 판단을 지우고 새로 봄
    ///   (25분 전 잘못 본 "지하철"이 남아, 길 위에서 목적지를 정할 때마다 "열차가 움직이는 것 같습니다"라던 일)
    func talgeotSaeroBogi() {
        let tg = TalgeotGamji.shared
        tg.saeYeojeong()
        if tg.sseulSuItda {
            if sokdoChujeong != .gicha && sokdoChujeong != tg.chujeong { sokdoChujeong = tg.chujeong }
        } else if ppareunTtae == nil && sokdoChujeong != .georeum {
            sokdoChujeong = .georeum
        }
    }

    /// 2.60.0 2분 안에 탈것이 움직였는가(움직임 감지기가 없는 폰은 빠르기 판단을 그대로 믿음)
    var choegeunTalgeotUmjigim: Bool {
        let tg = TalgeotGamji.shared
        guard tg.sseulSuItda else { return sokdoChujeong != .georeum }
        return tg.majimakTalgeot.map { Date().timeIntervalSince($0) < 120 } ?? false
    }

    func danggyeBakkugi(_ d: Danggye) {
        guard var y = jigeum else { return }
        y.danggye = d
        // 2.64.0 (261011-I18, 이사장님 승인 2026-10-11 「1」) 내리셨거나 도착하면 기차 지킴을 풂
        if d == .namEunGil || d == .dochak || d == .eotteoke { y.jikim = nil }
        y.gaengsin = Date()
        jigeum = y
        Girok.shared.namgi("yeojeong_danggye", ["d": d.rawValue])
        jeojang()
        // 2.12.7 차·버스·기차에 타면 지나는 고장 노래, 내리면 멈춤(지하철은 땅속이라 빼냄)
        let tg = talgeot
        DispatchQueue.main.async {
            if d == .taneunJung && [.cha, .gicha, .beoseu, .gosokbeoseu].contains(tg) { BangsongEngine.shared.chaTamGojangNorae() }
            else if d != .taneunJung { BangsongEngine.shared.chaNaerimGojangNorae() }
        }
    }

    /// 버스 정류장 담기(없애려면 nil)
    func beoseuNoki(_ b: BeoseuGil?) {
        guard var y = jigeum else { return }
        y.beoseu = b
        y.gaengsin = Date()
        jigeum = y
        jeojang()
    }

    /// 지하철 길 담기(없애려면 nil)
    func jihaNoki(_ g: JihaGil?) {
        guard var y = jigeum else { return }
        y.jiha = g
        y.gaengsin = Date()
        jigeum = y
        jeojang()
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 짐작으로 시작한 지하철 안내인지 담기
    func jungganJadongNoki(_ b: Bool) {
        guard var y = jigeum, (y.jungganJadong ?? false) != b else { return }
        y.jungganJadong = b
        jigeum = y
        jeojang()
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 여정 끝·하던 일 멈춤 — 빠르기로 알아챈 탈것을 처음부터(기차 기억 10분은 그대로)
    func talgeotChoGihwa() {
        ppareunTtae = nil
        neurinTtae = nil
        if sokdoChujeong != .georeum { sokdoChujeong = .georeum }
    }

    /// 탈것 정하기 — barojabeum 이 참이면 이용자가 바로잡은 것(가장 앞섬)
    func talgeotJeonghagi(_ t: Talgeot, barojabeum: Bool) {
        guard var y = jigeum else { return }
        y.talgeot = t
        y.barojabeum = barojabeum
        y.gaengsin = Date()
        jigeum = y
        jeojang()
    }

    /// 이용자가 탈것을 바로잡음 — 가장 앞섬
    func talgeotBarojapgi(_ t: Talgeot) {
        guard var y = jigeum else { return }
        y.talgeot = t
        y.barojabeum = true
        // 2.64.0 (261011-I18, 이사장님 승인 2026-10-11 「1」) 「기차입니다」는 바꾸실 때까지 지킴, 다른 탈것으로 바로잡으시면 풂
        y.jikim = (t == .gicha || t == .seonbak) ? t : nil
        if t != .gicha && sokdoChujeong == .gicha { gichaTtae = nil; sokdoChujeong = .georeum }   // 빠르기로 알던 기차도 함께 내려놓음
        y.gaengsin = Date()
        jigeum = y
        Girok.shared.namgi("yeojeong_barojapgi", ["tal": t.rawValue])
        jeojang()
    }

    func kkeut() {
        if jigeum != nil { Girok.shared.namgi("yeojeong_kkeut", [:]) }
        DispatchQueue.main.async { BangsongEngine.shared.chaNaerimGojangNorae() }
        jigeum = nil
        try? FileManager.default.removeItem(at: pail)
    }

    /// 2.64.0 (261011-I18, 이사장님 승인 2026-10-11 「1」) 탈것 지킴 시작(기차·배) — 바꾸실 때까지 지킴
    func jikimSijak(_ t: Talgeot, _ kkadak: String) {
        guard var y = jigeum, y.jikim != t else {
            if t == .gicha && sokdoChujeong != .gicha { sokdoChujeong = .gicha }
            return
        }
        if y.barojabeum && y.talgeot != t { return }   // 이용자가 다른 탈것으로 바로잡으신 것이 가장 앞섬
        y.jikim = t
        y.talgeot = t
        y.gaengsin = Date()
        jigeum = y
        jeojang()
        if t == .gicha { gichaTtae = Date() }
        Girok.shared.namgi("jikim", ["tal": t.rawValue, "kkadak": kkadak])
        if t == .gicha && sokdoChujeong != .gicha { sokdoChujeong = .gicha }
        jikimSinho.send((t, kkadak))
    }

    /// 지금 탈것 — 바로잡은 것이 가장 앞서고, 그다음이 기차 지킴, 그다음이 빠르기로 알아챈 것
    var talgeot: Talgeot {
        if let y = jigeum, y.barojabeum { return y.talgeot }
        if let jt = jikimTalgeot { return jt }
        if sokdoChujeong != .georeum { return sokdoChujeong }
        return jigeum?.talgeot ?? .georeum
    }

    func jeojang() {
        guard let y = jigeum, let d = try? JSONEncoder().encode(y) else { return }
        try? d.write(to: pail, options: .atomic)
    }

    /// 시속 15킬로미터를 15초 넘게 넘으면 차(시속 150을 넘으면 기차), 시속 8 아래로 3분이면 걸음
    private func sokdoBoda(_ w: Wichi) {
        guard !w.georeumChu, w.ochae <= 50 else { return }
        let kmh = w.sokdo * 3.6
        let now = Date()
        if kmh > 150 { gichaTtae = now }   // 2.61.0 기차 기억(잠깐이라도 시속 150 넘으면)
        // 2.64.0 (261011-I18, 이사장님 승인 2026-10-11 「1」) 예전에는 시속 150을 15초 동안 한 번도 끊기지 않고 넘어야 기차였음 —
        //   KTX는 역 앞뒤·굽은 길·터널에서 빠르기가 출렁여 이 잣대를 채우지 못했음. 이제 20초 안에 시속 150 넘은 자료가 두 번이면 기차로 보고 지킴
        //   (차는 시속 150을 넘지 않음. 한 번 튄 값에는 속지 않음)
        if kmh > 150 && w.ochae <= 30 {
            if let t0 = gichaSuTtae, now.timeIntervalSince(t0) > 20 { gichaSu = 0 }
            if gichaSu == 0 { gichaSuTtae = now }
            gichaSu += 1
            if gichaSu >= 2 && !gichaJikimJung && jikimTalgeot != .seonbak {
                Girok.shared.namgi("talgeot_chujeong", ["tal": "gicha", "kmh": Int(kmh)])
                jikimSijak(.gicha, "시속 \(Int(kmh))킬로미터")
                if jigeum == nil && sokdoChujeong != .gicha { sokdoChujeong = .gicha }
            }
        }
        if jikimJung { return }
        // 2.40.0 움직임 감지기가 있는 폰은 걷기·차·버스·지하철을 TalgeotGamji 가 가림 — 여기서는 기차(시속 150 넘게 15초)만 봄
        if TalgeotGamji.shared.sseulSuItda {
            if kmh > 150 {
                if ppareunTtae == nil { ppareunTtae = now }
                if let t = ppareunTtae, now.timeIntervalSince(t) >= 15, sokdoChujeong != .gicha {
                    sokdoChujeong = .gicha
                    Girok.shared.namgi("talgeot_chujeong", ["tal": "gicha", "kmh": Int(kmh)])
                }
            } else {
                ppareunTtae = nil
                if sokdoChujeong == .gicha && TalgeotGamji.shared.chujeong == .georeum { sokdoChujeong = .georeum }
            }
            return
        }
        if kmh > 15 {
            neurinTtae = nil
            if ppareunTtae == nil { ppareunTtae = now }
            if let t = ppareunTtae, now.timeIntervalSince(t) >= 15 {
                let sae: Talgeot = kmh > 150 ? .gicha : (sokdoChujeong == .gicha ? .gicha : .cha)
                if sae != sokdoChujeong {
                    sokdoChujeong = sae
                    Girok.shared.namgi("talgeot_chujeong", ["tal": sae.rawValue, "kmh": Int(kmh)])
                }
            }
        } else if kmh < 8 {
            ppareunTtae = nil
            if neurinTtae == nil { neurinTtae = now }
            if let t = neurinTtae, now.timeIntervalSince(t) >= 180, sokdoChujeong != .georeum {
                sokdoChujeong = .georeum
                Girok.shared.namgi("talgeot_chujeong", ["tal": "georeum", "kmh": Int(kmh)])
            }
        }
    }
}
