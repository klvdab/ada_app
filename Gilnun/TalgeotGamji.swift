// 2.40.0 (261003-T1, 이사장님 지시 "지하철을 타면 자동으로 걷는지 버스인지 자동차인지 지하철인지 알아챌 수 있는 능력은 되는 거 아니니")
// 탈것 저절로 알아채기 — 위성 빠르기에만 기대지 않고 폰의 움직임 감지기(걷기·탈것·멈춤)와 기압계(땅속으로 내려감·올라옴)를 함께 씀
// 2026-10-03 공덕 이마트 다녀오신 기록에서 찾은 잘못:
//   ① 사무실 지하에서 위성이 흔들려 빠르기가 크게 잡힌 채 남아 "벌써 차를 타고 가는 중"으로 알고 역 입구까지 걷는 안내를 건너뜀
//   ② 땅속에서는 위성이 없어 타고 가는 것을 알아채지 못함
// 이제:
//   걷기 15초 → 걸음(묵은 "차" 판단을 바로 지움)
//   탈것 20초 → 땅속이거나(기압으로 내려간 뒤) 위성이 30초 넘게 끊겼거나 역 200미터 안에서 탔으면 지하철
//              땅 위에서 버스 정류장 25미터 안에서 두 번 넘게 섰다 떠나면 버스, 아니면 차
//   멈춤 — 앞선 판단을 그대로 둠(신호 대기·역 정차)
import CoreMotion
import Combine
import Foundation

final class TalgeotGamji: ObservableObject {
    static let shared = TalgeotGamji()

    private let hwal = CMMotionActivityManager()
    private let gap = CMAltimeter()

    /// 움직임으로 알아챈 탈것
    @Published private(set) var chujeong: Talgeot = .georeum
    /// 땅속에 있음(기압으로 내려간 뒤 아직 땅 위로 나오지 않음)
    @Published private(set) var jiha = false
    private(set) var jihaTtae: Date?
    /// 땅속으로 내려가기 직전의 땅 위 자리(지하철 길을 찾을 때 씀)
    private(set) var jisangJari: Wichi?
    /// 탈것이 움직이기 시작한 때의 자리
    private(set) var chaSijakJari: Wichi?
    /// 지금 움직임: 걸음, 탈것, 멈춤, 모름
    private(set) var jigeumUmjigim = "모름"
    /// 탈것 안에서 섰다 떠난 때(지하철 역 세기에 씀)
    let seotdaTteonam = PassthroughSubject<TimeInterval, Never>()

    private var dolgo = false
    private var georeumSijak: Date?
    private var chaSijak: Date?
    private var meomchumSijak: Date?
    private var yeokGeuncheo = false
    private var jeongryujangSeom = 0
    private var gido: [(Date, Double)] = []
    private var jihaMin = 0.0

    var sseulSuItda: Bool { CMMotionActivityManager.isActivityAvailable() }

    func sijak() {
        guard !dolgo else { return }
        dolgo = true
        if CMMotionActivityManager.isActivityAvailable() {
            hwal.startActivityUpdates(to: OperationQueue.main) { [weak self] a in
                guard let self = self, let a = a, a.confidence != .low else { return }
                self.hwalBatda(a)
            }
        }
        if CMAltimeter.isRelativeAltitudeAvailable() {
            gap.startRelativeAltitudeUpdates(to: OperationQueue.main) { [weak self] d, _ in
                guard let self = self, let d = d else { return }
                self.gidoBatda(d.relativeAltitude.doubleValue)
            }
        }
    }

    /// 위성이 제대로 잡히는가(최근 15초 안에 오차 30미터 안)
    static var wiseongJoeum: Bool {
        guard let t = WichiEngine.shared.majimakWiseong, Date().timeIntervalSince(t) < 15 else { return false }
        return (WichiEngine.shared.jigeum?.ochae ?? 999) <= 30
    }

    private static var wiseongEopseum: Bool {
        guard let t = WichiEngine.shared.majimakWiseong else { return true }
        return Date().timeIntervalSince(t) > 30
    }

    // MARK: 움직임

    private func hwalBatda(_ a: CMMotionActivity) {
        let now = Date()
        if !jiha && TalgeotGamji.wiseongJoeum { jisangJari = WichiEngine.shared.jigeum }
        if (a.walking || a.running) && !a.automotive {
            jigeumUmjigim = "걸음"
            chaSijak = nil
            meomchumSijak = nil
            if georeumSijak == nil { georeumSijak = now }
            if let g = georeumSijak, now.timeIntervalSince(g) >= 15, chujeong != .georeum {
                jeongryujangSeom = 0
                yeokGeuncheo = false
                bakkugi(.georeum, "걷기 15초")
            }
            jihaHwagin()
        } else if a.automotive && !a.stationary {
            jigeumUmjigim = "탈것"
            georeumSijak = nil
            if let m = meomchumSijak {
                let t = now.timeIntervalSince(m)
                meomchumSijak = nil
                if t >= 5 && t <= 120 { meomchumKkeut(t) }
            }
            if chaSijak == nil {
                chaSijak = now
                chaSijakJari = WichiEngine.shared.jigeum
                yeokGeuncheoBoda()
            }
            if let c = chaSijak, now.timeIntervalSince(c) >= 20 { pandan("탈것 20초") }
        } else if a.stationary {
            jigeumUmjigim = "멈춤"
            if (chujeong != .georeum || chaSijak != nil) && meomchumSijak == nil { meomchumSijak = now }
        }
    }

    /// 탈것이 섰다가 다시 떠남 — 땅 위면 버스 정류장인지 봄, 지하철이면 역 세기에 알림
    private func meomchumKkeut(_ t: TimeInterval) {
        seotdaTteonam.send(t)
        guard chujeong != .jihacheol, !jiha, TalgeotGamji.wiseongJoeum, let w = WichiEngine.shared.jigeum else { return }
        Task {
            let l = await Beoseu.gakkaun(w.lat, w.lon) ?? []
            let gakkaum = l.contains { WichiEngine.geori(w.lat, w.lon, $0.lat, $0.lon) <= 25 }
            await MainActor.run {
                if gakkaum { self.jeongryujangSeom += 1 }
                if self.chujeong == .cha && self.jeongryujangSeom >= 2 { self.bakkugi(.beoseu, "정류장 \(self.jeongryujangSeom)번 섬") }
            }
        }
    }

    private func yeokGeuncheoBoda() {
        yeokGeuncheo = false
        guard let w = (jiha ? jisangJari : nil) ?? chaSijakJari ?? jisangJari else { return }
        Task {
            let d = await JihacheolEngine.gakkaunYeokGeori(w.lat, w.lon)
            await MainActor.run { self.yeokGeuncheo = (d ?? 9999) <= 200 }
        }
    }

    private func pandan(_ kkadak: String) {
        let t: Talgeot
        if jiha || TalgeotGamji.wiseongEopseum || yeokGeuncheo {
            t = .jihacheol
        } else if jeongryujangSeom >= 2 {
            t = .beoseu
        } else if chujeong == .beoseu {
            t = .beoseu
        } else {
            t = .cha
        }
        if t != chujeong { bakkugi(t, kkadak + (jiha ? ", 땅속" : "") + (yeokGeuncheo ? ", 역 근처에서 탐" : "")) }
    }

    private func bakkugi(_ t: Talgeot, _ kkadak: String) {
        chujeong = t
        Girok.shared.namgi("talgeot_gamji", ["t": t.rawValue, "kkadak": kkadak])
    }

    // MARK: 기압 — 땅속으로 내려감·올라옴

    private func gidoBatda(_ h: Double) {
        let now = Date()
        gido.append((now, h))
        gido.removeAll { now.timeIntervalSince($0.0) > 120 }
        if !jiha && TalgeotGamji.wiseongJoeum { jisangJari = WichiEngine.shared.jigeum }
        if !jiha {
            // 90초 안에 3.5미터 넘게 내려가면 땅속(계단·에스컬레이터)
            let jeonMax = gido.filter { now.timeIntervalSince($0.0) <= 90 }.map { $0.1 }.max() ?? h
            if jeonMax - h >= 3.5 && !TalgeotGamji.wiseongJoeum {
                jiha = true
                jihaTtae = now
                jihaMin = h
                Girok.shared.namgi("jiha_jinip", ["naeryeogam": Int((jeonMax - h) * 10)])
                NotificationCenter.default.post(name: TalgeotGamji.jihaJinipAllim, object: nil)
            }
        } else {
            jihaMin = min(jihaMin, h)
            jihaHwagin()
        }
    }

    /// 땅 위로 나왔는가 — 열차 안의 바람 압력으로 잘못 알지 않게, 3.5미터 넘게 올라오고 걷거나 위성이 다시 잡혀야 함
    private func jihaHwagin() {
        guard jiha else { return }
        let h = gido.last?.1 ?? jihaMin
        let ollaom = h - jihaMin >= 3.5
        if TalgeotGamji.wiseongJoeum || (ollaom && jigeumUmjigim == "걸음") {
            jiha = false
            jisangJari = WichiEngine.shared.jigeum
            Girok.shared.namgi("jiha_naom", ["wiseong": TalgeotGamji.wiseongJoeum])
        }
    }

    static let jihaJinipAllim = Notification.Name("gilnun.jihaJinip")
}
