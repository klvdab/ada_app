// 2.40.0 (261003-T1, 이사장님 지시 "지하철을 타면 자동으로 걷는지 버스인지 자동차인지 지하철인지 알아챌 수 있는 능력은 되는 거 아니니")
// 탈것 저절로 알아채기 — 위성 빠르기에만 기대지 않고 폰의 움직임 감지기(걷기·탈것·멈춤)와 기압계(땅속으로 내려감·올라옴)를 함께 씀
// 2026-10-03 공덕 이마트 다녀오신 기록에서 찾은 잘못:
//   ① 사무실 지하에서 위성이 흔들려 빠르기가 크게 잡힌 채 남아 "벌써 차를 타고 가는 중"으로 알고 역 입구까지 걷는 안내를 건너뜀
//   ② 땅속에서는 위성이 없어 타고 가는 것을 알아채지 못함
// 이제:
//   걷기 15초 → 걸음(묵은 "차" 판단을 바로 지움)
//   탈것 20초 → 땅속이거나(기압으로 내려간 뒤) 위성이 30초 넘게 끊겼거나 역 200미터 안에서 탔으면 지하철
//              2.60.0 역 200미터는 위성이 좋지 않을 때만, 위성 끊김은 땅 위에서 차로 알아채기 전에만, 콜 배차 뒤에는 차로 못 박음
//              땅 위에서 버스 정류장 25미터 안에서 두 번 넘게 섰다 떠나면 버스, 아니면 차
//   멈춤 — 앞선 판단을 그대로 둠(신호 대기·역 정차)
//   2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 땅속은 내려가는 동안 걸음이 늘어야(승강기 빼냄), 30분 넘게 위성·탈것 없으면 땅속 판단 지움,
//              역 근처 판단은 이번에 타고 처음 판단할 때만, 여정 끝·하던 일 멈춤 때 처음부터(saeroSijak), 바로잡으신 탈것을 받음(barojapgi)
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
    /// 2.59.0 (261009-I13, 이사장님 승인) 마지막으로 걸은 때 — 걸어서 내려간 때만 땅속으로 봄
    private var majimakGeoreum: Date?
    /// 2.60.0 마지막으로 탈것이 움직인 때 — 새 목적지를 정할 때 묵은 판단을 지울지 가림
    private(set) var majimakTalgeot: Date?
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 기압을 받을 때마다 남기는 만보기 걸음 — 내려가는 동안 걸음이 늘었는지 봄(승강기는 걸음이 없음)
    private var gidoGeoreum: [(Date, Int)] = []
    /// 2.61.0 만보기 걸음이 마지막으로 늘어난 때
    private var georeumNeuneunTtae: Date?
    private var gidoJeonGeoreum = -1
    // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 걷기를 알아챈 때들(georeumHwalTtae)로 내려가는 동안 걸었는지 보던 것은 뺌 —
    //   알림이 늦게 오면 Date()로 남긴 때가 어긋나고 안드로이드에는 없던 것. 대신 내려간 빠르기(아래 gidoBatda)로 가림
    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 걸어 내려간 채로 머물기 시작한 때 — 10초 넘게 머물러야 땅속으로 봄(안드로이드와 같음)
    private var naeryeogaTtae: Date?

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
            majimakGeoreum = now
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
            majimakTalgeot = now
            georeumSijak = nil
            if let m = meomchumSijak {
                let t = now.timeIntervalSince(m)
                meomchumSijak = nil
                // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 걸음으로 본 채 1분 넘게 멈춰 있었으면 묵은 탈것 시작 때를 버림(안드로이드와 같음)
                //   — 예전에는 한참 전의 흔들림이 남아 다시 움직이자마자 「탈것 20초」로 판단했음
                if t > 60 && chujeong == .georeum { chaSijak = nil }
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
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 걸음으로 본 채 1분 넘게 멈춰 있으면 묵은 탈것 시작 때를 버림(안드로이드와 같음)
            if chaSijak != nil, let m = meomchumSijak, now.timeIntervalSince(m) > 60, chujeong == .georeum { chaSijak = nil }
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

    /// 2.60.0 새 목적지를 정할 때 — 30초 안에 탈것이 움직이지 않았으면 묵은 탈것 판단(지하철 등)을 지움
    ///   (2026-10-09 남산: 25분 전 잘못 본 "지하철"이 남아 길 위에서 목적지를 정할 때마다 "열차가 움직이는 것 같습니다")
    func saeYeojeong() {
        let umjigimNa = majimakTalgeot.map { Date().timeIntervalSince($0) < 30 } ?? false
        guard !umjigimNa, !jiha, chujeong != .georeum else { return }
        if chujeong == .cha && ChaBureugi.shared.chaGojeong { return }   // 콜 차 안에서 신호 대기 중일 수 있음
        chaSijak = nil
        meomchumSijak = nil
        yeokGeuncheo = false
        jeongryujangSeom = 0
        bakkugi(.georeum, "새 목적지 — 묵은 판단 지움")
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 여정을 끝내거나 하던 일을 멈출 때 — 묵은 탈것 판단을 모두 지우고 처음부터
    ///   (땅속 판단은 위성이 잘 잡힐 때만 지움)
    func saeroSijak() {
        chaSijak = nil
        meomchumSijak = nil
        yeokGeuncheo = false
        jeongryujangSeom = 0
        if jiha && TalgeotGamji.wiseongJoeum {
            jiha = false
            gido.removeAll()
            Girok.shared.namgi("jiha_naom", ["wiseong": true, "kkadak": "새로 시작"])
        }
        if chujeong != .georeum { bakkugi(.georeum, "새로 시작 — 여정 끝·하던 일 멈춤") }
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 이용자가 탈것을 바로잡으심 — 판단만 그 탈것으로 맞춤(안내 바꾸기는 AnnaeEngine 이 이미 함)
    func barojapgi(_ t: Talgeot) {
        if t == .georeum {
            chaSijak = nil
            meomchumSijak = nil
            yeokGeuncheo = false
            jeongryujangSeom = 0
        }
        if t != chujeong { bakkugi(t, "이용자가 바로잡음") }
    }

    /// 2.61.0 15초 넘게 걷고 있는가(앱을 다시 켠 뒤 지하철 안내를 마칠지 볼 때)
    var georeum15cho: Bool {
        guard jigeumUmjigim == "걸음", let g = georeumSijak else { return false }
        return Date().timeIntervalSince(g) >= 15
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
        // 2.60.0 (261009-I14, 이사장님 승인 2026-10-09 남산 — 약수역 위 댁 앞에서 복지콜을 탔는데 "지하철을 타신 것 같습니다")
        //   ① 콜을 불러 배차된 뒤(3시간 안, 아직 내리지 않음)에는 땅속으로 내려가지 않는 한 차로 못 박음
        //   ② 역 200미터 안에서 탔다는 것만으로는 지하철로 보지 않음 — 위성이 좋지 않을 때만 셈
        //   ③ 땅 위에서 차로 알아챈 뒤에는 터널·가방 속처럼 위성만 끊겨도 지하철로 바꾸지 않음(땅속으로 내려갔을 때만)
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 콜 차 못 박기는 땅속이라는 것만으로는 풀지 않고, 땅속(위성 없음)에서 탈것이 실제로 20초 넘게 움직일 때(지하철) 풂
        //   (11층 댁에서 승강기로 지하 사무실에 내려가 몇 걸음 걸으신 것만으로 콜 차 못 박기가 풀리던 일)
        if jiha && !TalgeotGamji.wiseongJoeum && ChaBureugi.shared.chaGojeong { ChaBureugi.shared.kolPulgi("jiha_talgeot") }
        let kolCha = ChaBureugi.shared.chaGojeong && !jiha
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 역 근처에서 탔다는 것은 이번에 타고 처음 판단할 때만 — 차·버스로 본 뒤에는(터널 등) 다시 쓰지 않음
        let yeok = yeokGeuncheo && !TalgeotGamji.wiseongJoeum && chujeong != .cha && chujeong != .beoseu
        let wiseongMan = TalgeotGamji.wiseongEopseum && chujeong != .cha && chujeong != .beoseu
        if kolCha {
            t = .cha
        } else if jiha || wiseongMan || yeok {
            t = .jihacheol
        } else if jeongryujangSeom >= 2 {
            t = .beoseu
        } else if chujeong == .beoseu {
            t = .beoseu
        } else {
            t = .cha
        }
        if t != chujeong { bakkugi(t, kkadak + (jiha ? ", 땅속" : "") + (yeok ? ", 역 근처에서 탐" : "") + (kolCha ? ", 콜 배차" : "")) }
    }

    private func bakkugi(_ t: Talgeot, _ kkadak: String) {
        chujeong = t
        Girok.shared.namgi("talgeot_gamji", ["talgeot": t.rawValue, "kkadak": kkadak])   // 2.59.0 칸 이름 "t" 가 기록 시각 칸을 덮어써 시각이 지워지던 것 고침
    }

    // MARK: 기압 — 땅속으로 내려감·올라옴

    private func gidoBatda(_ h: Double) {
        let now = Date()
        gido.append((now, h))
        gido.removeAll { now.timeIntervalSince($0.0) > 120 }
        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 높이와 함께 만보기 걸음도 남김
        let georeumSu = WichiEngine.shared.oneulGeoreum
        if gidoJeonGeoreum >= 0 && georeumSu > gidoJeonGeoreum { georeumNeuneunTtae = now }
        gidoJeonGeoreum = georeumSu
        gidoGeoreum.append((now, georeumSu))
        gidoGeoreum.removeAll { now.timeIntervalSince($0.0) > 120 }
        if !jiha && TalgeotGamji.wiseongJoeum { jisangJari = WichiEngine.shared.jigeum }
        if !jiha {
            // 90초 안에 3.5미터 넘게 내려가면 땅속(계단·에스컬레이터)
            let chang90 = gido.filter { now.timeIntervalSince($0.0) <= 90 }
            let jeonMax = chang90.map { $0.1 }.max() ?? h
            // 2.59.0 (261009-I13, 이사장님 승인) 집 안에 놓인 폰·차 타고 내리막을 지날 때 땅속으로 잘못 보던 것(10/8~9 기록) —
            // 땅속은 사람이 걸어서(계단·에스컬레이터) 내려갈 때만, 지금 탈것을 타고 있지 않을 때
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 승강기를 땅속으로 잘못 보던 것 — 가장 높던 때부터 지금까지 걸음이 여섯 걸음 넘게 늘었고
            //   지금도 걸음이 늘고 있어야(10초 안) 땅속으로 봄. 만보기가 없는 폰은 가장 높던 때 뒤에 걷기를 알아챘고 그것이 10초 안일 때
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09 — 11층 댁에서 승강기로 지하 사무실에 내려간 뒤 여섯 걸음 걸으시면 땅속으로 보던 일)
            //   걸음은 내려가는 동안(가장 높던 때부터 바닥에 닿은 때까지)에 늘어난 것만 셈 — 승강기는 내려가는 동안 걸음이 없음.
            //   그리고 내려간 채로 10초 넘게 머물러야 땅속으로 봄(안드로이드와 같음)
            let maxTtae = chang90.last { $0.1 >= jeonMax }?.0 ?? now
            let dwi = chang90.filter { $0.0 >= maxTtae }
            let badak = dwi.map { $0.1 }.min() ?? h
            let badakTtae = dwi.first { $0.1 <= badak + 0.5 }?.0 ?? now
            let maxGeoreum = gidoGeoreum.first { $0.0 >= maxTtae }?.1 ?? georeumSu
            let badakGeoreum = gidoGeoreum.last { $0.0 <= badakTtae }?.1 ?? georeumSu
            let naeryeoGaneunGeoreum = badakGeoreum - maxGeoreum
            let manbo = naeryeoGaneunGeoreum >= 6
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 에스컬레이터에 가만히 서서 내려가면 걸음이 없어 땅속으로 못 보던 일 —
            //   내려간 높이를 걸린 때(가장 높던 때부터 바닥에 닿은 때까지)로 나눈 빠르기가 초속 0.6미터보다 느리고 3미터 넘게 내려갔으면 땅속.
            //   승강기는 초속 1~2미터, 에스컬레이터·계단은 초속 0.3~0.5미터(안드로이드와 같음)
            let naeryeoNopi = jeonMax - badak
            // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 승강기가 중간 층에 서며 내려가면 평균 빠르기가 낮아져 땅속으로 잘못 보던 것 —
            //   걸린 때는 실제로 높이가 줄고 있던 때(초속 0.1미터 넘게 내려가던 사이)만 더해 셈(안드로이드와 같음)
            var naeryeoSigan = 0.0
            let naeryeoJeom = dwi.filter { $0.0 <= badakTtae }
            if naeryeoJeom.count >= 2 {
                for i in 1..<naeryeoJeom.count {
                    let dt = naeryeoJeom[i].0.timeIntervalSince(naeryeoJeom[i - 1].0)
                    let dh = naeryeoJeom[i - 1].1 - naeryeoJeom[i].1
                    if dt > 0 && dh / dt > 0.1 { naeryeoSigan += dt }
                }
            }
            let neurinNaeryeogam = naeryeoNopi >= 3 && naeryeoSigan > 0 && naeryeoNopi / naeryeoSigan < 0.6
            let georeoNaeryeogam = (manbo || neurinNaeryeogam) && jigeumUmjigim != "탈것"
            if jeonMax - h >= 3.5 && !TalgeotGamji.wiseongJoeum && georeoNaeryeogam {
                if naeryeogaTtae == nil { naeryeogaTtae = now }
                guard let nt = naeryeogaTtae, now.timeIntervalSince(nt) >= 10 else { return }
                naeryeogaTtae = nil
                jiha = true
                jihaTtae = now
                jihaMin = h
                Girok.shared.namgi("jiha_jinip", ["naeryeogam": Int((jeonMax - h) * 10), "georeum": naeryeoGaneunGeoreum,
                                                  "ppareugi": naeryeoSigan > 0 ? Int(naeryeoNopi / naeryeoSigan * 100) : -1])   // 2.61.0 초속 센티미터
                NotificationCenter.default.post(name: TalgeotGamji.jihaJinipAllim, object: nil)
                // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 땅속이라는 것만으로는 콜 차 못 박기를 풀지 않음 — 땅속에서 탈것이 움직일 때(pandan) 풂
            } else {
                naeryeogaTtae = nil
            }
        } else {
            jihaMin = min(jihaMin, h)
            jihaHwagin()
            jihaSumyeong(now)
        }
    }

    /// 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 땅속 판단의 수명 — 위성도 없고 탈것도 움직이지 않은 채 30분이 넘으면 지움
    ///   (지하 사무실·지하 주차장에 오래 머물 때 땅속 판단이 하루 종일 남던 일)
    private func jihaSumyeong(_ now: Date) {
        guard jiha, !TalgeotGamji.wiseongJoeum, let jt = jihaTtae else { return }
        let gijun = max(jt, majimakTalgeot ?? jt)
        guard now.timeIntervalSince(gijun) > 30 * 60 else { return }
        jiha = false
        gido.removeAll()
        Girok.shared.namgi("jiha_sumyeong", ["bun": Int(now.timeIntervalSince(jt) / 60)])
    }

    /// 땅 위로 나왔는가 — 열차 안의 바람 압력으로 잘못 알지 않게, 3.5미터 넘게 올라오고 걷거나 위성이 다시 잡혀야 함
    private func jihaHwagin() {
        guard jiha else { return }
        let h = gido.last?.1 ?? jihaMin
        let ollaom = h - jihaMin >= 3.5
        if TalgeotGamji.wiseongJoeum || (ollaom && jigeumUmjigim == "걸음") {
            jiha = false
            jisangJari = WichiEngine.shared.jigeum
            gido.removeAll()   // 2.59.0 나온 뒤 묵은 높이로 곧바로 다시 들어감을 막음(1초 간격 들락날락)
            Girok.shared.namgi("jiha_naom", ["wiseong": TalgeotGamji.wiseongJoeum])
        }
    }

    static let jihaJinipAllim = Notification.Name("gilnun.jihaJinip")
}
