// 자봉 앱 점지도 그리기 — 속까지 앱 (2.3.0, 빌드 261001-13, 설계도 자봉앱_설계도_261001 진행 차례 3)
// ★2.3.0 (대표님 지시) 몸 센서 극대화 — MomSensor.swift 가 가속도·자이로·나침반 합성 방향·움직임 상태·만보기 빠르기를 1초에 50번 읽어
//   걸음마다 한 줄(gs)을 남기고, 1초 줄(pts)에도 합성 방향(hy)·가속도 걸음(st2)·돈 각도 누계(dg)·걸음 빠르기(cad)·상태(sa)를 붙임.
//   꺾임 여쭙기는 나침반 대신 자이로 각도로 가름. 기존 걸음(st, 아이폰 만보기)은 그대로 두어 두 걸음을 견줌(1미터 원칙 점검·연구용).
// 웹 jeom_rec.js 의 기록 모양(자리 t·st·h·lat·lon·acc, 표시 이름과 짝·칸수·거리)을 그대로 따라, 앞으로 올리기에서 웹과 한곳에 모입니다.
// 점지도의 바탕은 폰 걸음 센서, 방향은 폰 방향 센서, 위성은 거듦. 높이는 폰 기압계(상대 높이, ralt)로 재어 계단을 여쭙니다.
// 화면이 잠기거나 다른 앱을 써도 이어 갑니다(위치 바탕 실행). 1분마다, 표시를 남길 때마다, 앱이 뒤로 갈 때 저절로 저장합니다.
// 폰이 알아챌 수 있는 것은 먼저 여쭙니다 — 방향이 크게 바뀌면 꺾이셨습니까, 높이가 바뀌면 계단입니까. 네라고 하셔야(말 또는 단추) 표시가 됩니다.
import SwiftUI
import CoreMotion
import Combine
import Foundation

// MARK: 기록 모양

struct GrJari: Codable {
    var t: Int
    var st: Int
    var h: Double?
    var lat: Double?
    var lon: Double?
    var acc: Double?
    var ralt: Double?
    var m: String?
    var cut: Int?
    // 2.3.0 몸 센서
    var hy: Double? = nil     // 합성 방향(자이로+나침반)
    var st2: Int? = nil       // 가속도로 센 걸음
    var dg: Double? = nil     // 시작부터 돈 각도 누계(자이로, 오른쪽 +)
    var cad: Double? = nil    // 만보기 걸음 빠르기(1초에 몇 걸음)
    var sa: String? = nil     // 움직임 상태
}

struct GrPyosi: Codable {
    var t: Int
    var st: Int
    var name: String
    var h: Double?
    var lat: Double?
    var lon: Double?
    var acc: Double?
    var kind: String?
    var up: String?
    var ride: Bool?
    var cnt: Int?
    var dist: Int?
    var secs: Int?
    var pairOf: String?
    var malo: Bool?      // 말로 남긴 표시
    var mureum: Bool?    // 폰이 여쭙고 네 하셔서 남긴 표시
}

struct GrGil: Codable, Identifiable {
    var id: String
    var sijak: Date
    var kkeut: Date?
    var from: String
    var to: String
    var bopok: Double
    var bopokMode: String
    var beonho: String
    var pts: [GrJari]
    var marks: [GrPyosi]
    var georeum: Int
    var olim: Bool
    var meomchum: Bool   // 그리다가 멈춘 채 저장됨
    // 2.3.0 몸 센서 — 걸음마다 한 줄과 기기 정보(센서 기록 규격)
    var gs: [GrGeoreum]? = nil
    var gigi: GrGigi? = nil
}

// MARK: 그리기 엔진

final class JeomGeurigi: ObservableObject {
    static let shared = JeomGeurigi()

    enum Sangtae { case swim, georeum, meomchum }
    struct Mureum: Equatable {
        let mal: String
        let ne: String          // 네일 때 남길 표시
        let danchu: String      // 단추 이름
        let jariI: Int          // 표시를 남길 자리(pts 번호)
        let ttae: Date
    }

    /// 표시 스물두 가지 — 웹 jeom_rec.js 와 같음
    static let MARKS = ["올라가는 계단 시작", "내려가는 계단 시작", "계단 끝", "오름턱", "내림턱", "횡단보도 건너기 시작", "횡단보도 건너기 끝",
                        "왼쪽으로 꺾임", "오른쪽으로 꺾임", "점자블록 끊김", "문", "엘리베이터", "버스 정류장", "지하철 개찰구", "조심할 곳",
                        "에스컬레이터 올라감", "에스컬레이터 내려감", "에스컬레이터 내림", "지하철 탐", "지하철 내림", "버스 탐", "버스 내림"]
    /// 자주 쓰는 표시 — 겉에 크게
    static let JAJU = ["왼쪽으로 꺾임", "오른쪽으로 꺾임", "올라가는 계단 시작", "내려가는 계단 시작", "계단 끝", "횡단보도 건너기 시작", "횡단보도 건너기 끝", "문"]
    struct Jjak { let end: String; let kind: String; let up: String; let ride: Bool }
    static let PAIR: [String: Jjak] = [
        "올라가는 계단 시작": Jjak(end: "계단 끝", kind: "계단", up: "오르막", ride: false),
        "내려가는 계단 시작": Jjak(end: "계단 끝", kind: "계단", up: "내리막", ride: false),
        "횡단보도 건너기 시작": Jjak(end: "횡단보도 건너기 끝", kind: "횡단보도", up: "", ride: false),
        "에스컬레이터 올라감": Jjak(end: "에스컬레이터 내림", kind: "에스컬레이터", up: "오르막", ride: true),
        "에스컬레이터 내려감": Jjak(end: "에스컬레이터 내림", kind: "에스컬레이터", up: "내리막", ride: true),
        "지하철 탐": Jjak(end: "지하철 내림", kind: "지하철", up: "", ride: true),
        "버스 탐": Jjak(end: "버스 내림", kind: "버스", up: "", ride: true)
    ]
    /// 말로 남길 때 알아듣는 다른 말 — 웹 BYEOLCHING 을 따름
    static let BYEOLCHING: [String: String] = [
        "우회전": "오른쪽으로 꺾임", "오른쪽": "오른쪽으로 꺾임", "오른편": "오른쪽으로 꺾임", "오른쪽으로": "오른쪽으로 꺾임",
        "좌회전": "왼쪽으로 꺾임", "왼쪽": "왼쪽으로 꺾임", "왼편": "왼쪽으로 꺾임", "왼쪽으로": "왼쪽으로 꺾임",
        "올라가는계단": "올라가는 계단 시작", "오르막계단": "올라가는 계단 시작", "계단올라감": "올라가는 계단 시작", "계단시작": "올라가는 계단 시작",
        "내려가는계단": "내려가는 계단 시작", "내리막계단": "내려가는 계단 시작", "계단내려감": "내려가는 계단 시작",
        "계단끝": "계단 끝", "계단끝남": "계단 끝", "계단다": "계단 끝",
        "턱": "오름턱", "오르막턱": "오름턱", "올라가는턱": "오름턱", "내리막턱": "내림턱", "내려가는턱": "내림턱",
        "횡단보도": "횡단보도 건너기 시작", "건널목": "횡단보도 건너기 시작", "횡단보도시작": "횡단보도 건너기 시작", "건너기시작": "횡단보도 건너기 시작",
        "횡단보도끝": "횡단보도 건너기 끝", "건널목끝": "횡단보도 건너기 끝", "다건넜어": "횡단보도 건너기 끝", "건너기끝": "횡단보도 건너기 끝",
        "점자블록": "점자블록 끊김", "블록끊김": "점자블록 끊김", "점자블록끊김": "점자블록 끊김",
        "문": "문", "출입문": "문", "입구": "문",
        "엘리베이터": "엘리베이터", "승강기": "엘리베이터",
        "정류장": "버스 정류장", "버스정류장": "버스 정류장",
        "개찰구": "지하철 개찰구", "조심": "조심할 곳", "위험": "조심할 곳", "조심할곳": "조심할 곳",
        "에스컬레이터올라감": "에스컬레이터 올라감", "에스컬레이터내려감": "에스컬레이터 내려감", "에스컬레이터내림": "에스컬레이터 내림",
        "지하철탐": "지하철 탐", "지하철탔어": "지하철 탐", "지하철내림": "지하철 내림", "지하철내렸어": "지하철 내림",
        "버스탐": "버스 탐", "버스탔어": "버스 탐", "버스내림": "버스 내림", "버스내렸어": "버스 내림"
    ]

    @Published private(set) var sangtae: Sangtae = .swim
    @Published private(set) var mureum: Mureum?
    @Published private(set) var allim = ""
    @Published private(set) var geurinGil: [GrGil] = []
    @Published private(set) var malDeutneun = false
    private(set) var gil: GrGil?

    private var stGijun = 0          // 걸음 센서 기준(앱이 켜진 뒤 센 걸음)
    private var meomchumSt0 = 0
    private var openPair: (name: String, st: Int, lat: Double?, lon: Double?, idx: Int, t: Int)?
    private var rideMode = ""
    private var sigye: Timer?
    private var jeojangSigye: Timer?
    private let gobdo = CMAltimeter()
    private var ralt: Double?
    private var majimakMureum = Date.distantPast
    private var majimakKkeokim = Date.distantPast
    private var gilJari: (Double, Double)?
    private var gilIreum = ""
    private var gilMutneunJung = false
    private var ssak = Set<AnyCancellable>()

    private static let jigeumPail: URL = {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("jabong_geurigi_jigeum.json")
    }()
    private static let mokPail: URL = {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("jabong_geurin_gil.json")
    }()

    init() {
        if let d = try? Data(contentsOf: JeomGeurigi.mokPail), let m = try? JSONDecoder().decode([GrGil].self, from: d) { geurinGil = m }
        // 그리다가 앱이 꺼졌으면 남은 길을 되살려 멈춤으로 둠 — 다시 걷기를 누르시면 이어 그림
        if let d = try? Data(contentsOf: JeomGeurigi.jigeumPail), let g = try? JSONDecoder().decode(GrGil.self, from: d), g.kkeut == nil {
            gil = g
            sangtae = .meomchum
            allim = "그리던 길이 남아 있습니다. 다시 걷기를 누르시면 이어 그리고, 다 걸었습니다를 누르시면 여기까지로 마칩니다."
        }
        NotificationCenter.default.publisher(for: JabongBonche.jeojangHal)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.jeojang() }
            .store(in: &ssak)
    }

    // MARK: 셈

    private var georeum: Int { max(0, WichiEngine.shared.georeumSu - stGijun) }
    private var chobun: Int { gil.map { Int(Date().timeIntervalSince($0.sijak)) } ?? 0 }
    private var banghyang: Double? { WichiEngine.shared.nachimban >= 0 ? (WichiEngine.shared.nachimban * 10).rounded() / 10 : nil }

    private func jigeumJari() -> GrJari {
        var p = GrJari(t: chobun, st: georeum)
        p.h = banghyang
        if let w = WichiEngine.shared.jigeum, Date().timeIntervalSince(w.ttae) < 15 {
            p.lat = w.lat; p.lon = w.lon; p.acc = (w.ochae * 10).rounded() / 10
        }
        if let r = ralt { p.ralt = (r * 10).rounded() / 10 }
        if !rideMode.isEmpty { p.m = rideMode }
        // 2.3.0 몸 센서
        let ms = MomSensor.shared
        if ms.dollyeo {
            if let h = ms.hapseong { p.hy = (h * 10).rounded() / 10 }
            p.st2 = ms.georeumSu
            p.dg = (ms.nujeokDol * 10).rounded() / 10
            p.cad = ms.cadence
            p.sa = ms.sangtae
        }
        return p
    }

    /// 2.3.0 몸 센서 켜기 — 이어 그리기면 앞에서 센 걸음과 돈 각도에 이어 셈
    private func momKyeogi() {
        let ms = MomSensor.shared
        ms.nopiMutgi = { [weak self] in self?.ralt }
        ms.georeumNal = { [weak self] g in
            guard let self = self, self.sangtae == .georeum, self.gil != nil else { return }
            if self.gil?.gs == nil { self.gil?.gs = [] }
            self.gil?.gs?.append(g)
        }
        ms.nachimbanNeogi(WichiEngine.shared.nachimban)
        let n0 = gil?.gs?.last?.n ?? 0
        let dol0 = gil?.pts.last(where: { $0.dg != nil })?.dg ?? 0
        ms.kyeogi(n0: n0, dol0: dol0, heureun: Date().timeIntervalSince(gil?.sijak ?? Date()))
    }

    // MARK: 시작·멈춤·끝

    /// 그리기 시작 — 보폭이 있어야 함
    func sijak() {
        guard Seoljeong.shared.bopok > 0.2, Seoljeong.shared.bopokJaem else {
            alrigi("먼저 보폭을 재 주십시오. 점지도의 걸음 수가 정확하려면 그리시는 분의 보폭이 꼭 있어야 합니다.")
            return
        }
        let s = Seoljeong.shared
        gil = GrGil(id: "JB" + String(Int(Date().timeIntervalSince1970)), sijak: Date(), kkeut: nil, from: "", to: "",
                    bopok: s.bopok, bopokMode: s.bopokMode, beonho: JabongNae.shared.beonho, pts: [], marks: [],
                    georeum: 0, olim: false, meomchum: false, gs: [], gigi: MomSensor.gigiJeongbo())
        stGijun = WichiEngine.shared.georeumSu
        openPair = nil
        rideMode = ""
        gilJari = nil
        gilIreum = ""
        mureum = nil
        sangtae = .georeum
        dolligi()
        momKyeogi()
        Girok.shared.namgi("jb_geurigi_sijak", ["id": gil?.id ?? ""])
        alrigi("걷기 시작했습니다. 평소 걸음으로 걸으시고, 꺾이는 곳과 계단, 건널목, 문에 닿는 순간 표시를 남겨 주십시오.")
        // 출발한 자리 주소를 저절로 적음
        if let w = WichiEngine.shared.jigeum {
            Task {
                let j = await Chatgi.juso(w.lat, w.lon)
                await MainActor.run {
                    guard let j = j else { return }
                    self.gil?.from = j
                    SoriEngine.shared.mal("출발한 자리는 \(j)입니다.", .jeongbo)
                }
            }
        }
        jeojang()
    }

    func jamkkan() {
        guard sangtae == .georeum else { return }
        sangtae = .meomchum
        meomchumSt0 = WichiEngine.shared.georeumSu
        mureum = nil
        meomchugi()
        MomSensor.shared.kkeugi()
        gil?.meomchum = true
        jeojang()
        alrigi("잠깐 멈췄습니다. 이어 걸으실 때 다시 걷기를 눌러 주십시오. 멈춘 동안의 걸음은 세지 않습니다.")
    }

    func dasiGeotgi() {
        guard sangtae == .meomchum, gil != nil else { return }
        // 멈춘 동안 센 걸음은 빼고, 앱이 다시 켜졌으면 저장해 둔 걸음에서 이어 셈
        if meomchumSt0 > 0 {
            stGijun += max(0, WichiEngine.shared.georeumSu - meomchumSt0)
        } else {
            stGijun = WichiEngine.shared.georeumSu - (gil?.georeum ?? 0)
        }
        meomchumSt0 = 0
        sangtae = .georeum
        gil?.meomchum = false
        var p = jigeumJari()
        p.cut = 1   // 멈췄다 이은 자리 — 이 사이는 이어 그리지 않음(웹과 같음)
        gil?.pts.append(p)
        dolligi()
        momKyeogi()
        alrigi("다시 걷습니다. 지금까지 \(georeum)걸음입니다.")
    }

    /// 다 걸었습니다
    func kkeut() {
        guard var g = gil else { return }
        meomchugi()
        if sangtae == .georeum { g.pts.append(jigeumJari()) }
        MomSensor.shared.kkeugi()
        g.georeum = sangtae == .georeum ? georeum : g.georeum
        g.kkeut = Date()
        g.meomchum = false
        sangtae = .swim
        mureum = nil
        let geori = Int(Double(g.georeum) * g.bopok)
        var mal = "걷기를 마쳤습니다. \(g.georeum)걸음, 약 \(geori)미터, 표시 \(g.marks.count)개입니다."
        // 2.3.0 두 걸음 견주기 — 만보기 걸음과 가속도 걸음이 많이 다르면 알림(1미터 원칙)
        if let n2 = g.gs?.last?.n, g.georeum > 20 {
            let cha = abs(Double(n2 - g.georeum)) / Double(g.georeum)
            mal += cha <= 0.1 ? " 몸 센서로 센 걸음도 \(n2)걸음으로 잘 맞습니다."
                              : " 몸 센서로 센 걸음은 \(n2)걸음이라 차이가 큽니다. 올리기 전 점검에서 살펴보겠습니다."
        }
        if let o = openPair {
            mal += " \(o.name)의 짝인 \(JeomGeurigi.PAIR[o.name]?.end ?? "끝") 표시가 없습니다. 올리기 전 점검에서 다시 여쭙겠습니다."
        }
        mal += " 그린 길은 폰에 담아 두었습니다. 올리기 전 점검과 올리기는 다음 판에 들어섭니다."
        openPair = nil
        rideMode = ""
        gil = nil
        geurinGil.insert(g, at: 0)
        mokJeojang()
        Hamkke.geurimAllim(gil: g.id, geori: geori)   // 2.8.0 함께한 기록판에 셈
        try? FileManager.default.removeItem(at: JeomGeurigi.jigeumPail)
        Girok.shared.namgi("jb_geurigi_kkeut", ["id": g.id, "georeum": g.georeum, "pyosi": g.marks.count, "jari": g.pts.count])
        alrigi(mal)
        // 도착한 자리 주소를 저절로 적음
        let id = g.id
        if let w = WichiEngine.shared.jigeum {
            Task {
                let j = await Chatgi.juso(w.lat, w.lon)
                await MainActor.run {
                    guard let j = j, let i = self.geurinGil.firstIndex(where: { $0.id == id }) else { return }
                    self.geurinGil[i].to = j
                    self.mokJeojang()
                    SoriEngine.shared.mal("도착한 자리는 \(j)입니다.", .jeongbo)
                }
            }
        }
    }

    // MARK: 돌리기

    private func dolligi() {
        sigye?.invalidate()
        sigye = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.tick() }
        jeojangSigye?.invalidate()
        jeojangSigye = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in self?.jeojang() }
        if CMAltimeter.isRelativeAltitudeAvailable() {
            gobdo.startRelativeAltitudeUpdates(to: .main) { [weak self] d, _ in
                guard let self = self, let d = d else { return }
                self.ralt = d.relativeAltitude.doubleValue + (self.raltDeoham ?? 0)
            }
        }
        // 앱이 다시 켜져 기압계가 0부터 시작하면 앞의 높이에 이어 붙임
        raltDeoham = gil?.pts.last(where: { $0.ralt != nil })?.ralt
    }
    private var raltDeoham: Double?

    private func meomchugi() {
        sigye?.invalidate(); sigye = nil
        jeojangSigye?.invalidate(); jeojangSigye = nil
        gobdo.stopRelativeAltitudeUpdates()
        ralt = nil
    }

    /// 1초마다 한 자리
    private func tick() {
        guard sangtae == .georeum, gil != nil else { return }
        MomSensor.shared.nachimbanNeogi(WichiEngine.shared.nachimban)
        let p = jigeumJari()
        gil?.pts.append(p)
        gil?.georeum = p.st
        // 물음은 20초 지나면 거둠
        if let m = mureum, Date().timeIntervalSince(m.ttae) > 20 { mureum = nil }
        if rideMode.isEmpty {
            kkeokimBoda()
            gyedanBoda()
        }
        if (gil?.pts.count ?? 0) % 15 == 0 { gilBoda() }
    }

    // MARK: 폰이 먼저 여쭘

    /// 방향이 크게 바뀌면 — 꺾이셨습니까
    private func kkeokimBoda() {
        guard let ps = gil?.pts, ps.count >= 10, mureum == nil,
              Date().timeIntervalSince(majimakMureum) > 15, Date().timeIntervalSince(majimakKkeokim) > 10 else { return }
        let n = ps.count
        let georeumSai = ps[n - 1].st - ps[n - 10].st
        guard georeumSai >= 5 else { return }
        var d: Double
        // 2.3.0 자이로가 있으면 몸이 실제로 돈 각도로 가름(나침반은 쇠붙이 옆에서 틀어짐)
        if let d0 = ps[n - 8].dg, let d1 = ps[n - 1].dg, let dm = ps[n - 4].dg {
            d = d1 - d0
            // 이미 돌고 난 뒤 4초 동안 또 돌고 있으면 아직 도는 중 — 다 돈 뒤에 여쭘
            guard abs(d1 - dm) < abs(d) * 0.7 else { return }
        } else {
            let ap = ps[(n - 10)..<(n - 6)].compactMap { $0.h }
            let dwi = ps[(n - 3)..<n].compactMap { $0.h }
            guard ap.count >= 3, dwi.count >= 2 else { return }
            d = JeomGeurigi.gakCha(JeomGeurigi.pyeonggyun(ap), JeomGeurigi.pyeonggyun(dwi))
        }
        guard abs(d) >= 55 else { return }
        let ireum = d > 0 ? "오른쪽으로 꺾임" : "왼쪽으로 꺾임"
        yeojjum(d > 0 ? "오른쪽으로 꺾이셨습니까?" : "왼쪽으로 꺾이셨습니까?", ireum, n - 6)
    }

    /// 높이가 바뀌면 — 계단입니까 / 계단이 끝났습니까
    private func gyedanBoda() {
        guard let ps = gil?.pts, ps.count >= 9, mureum == nil, Date().timeIntervalSince(majimakMureum) > 15 else { return }
        let n = ps.count
        guard let a0 = ps[n - 9].ralt, let a1 = ps[n - 1].ralt else { return }
        let georeumSai = ps[n - 1].st - ps[n - 9].st
        if let o = openPair, JeomGeurigi.PAIR[o.name]?.kind == "계단" {
            // 계단 중 — 4초 넘게 높이가 그대로면 끝났는지 여쭘
            guard let b0 = ps[n - 5].ralt, abs(a1 - b0) < 0.25, ps[n - 1].st - ps[n - 5].st >= 3,
                  n - 5 > o.idx else { return }
            yeojjum("계단이 끝났습니까?", "계단 끝", n - 5)
            return
        }
        guard openPair == nil, georeumSai >= 4 else { return }
        let cha = a1 - a0
        guard abs(cha) >= 1.2 else { return }
        yeojjum(cha > 0 ? "높이가 올라갑니다. 올라가는 계단입니까?" : "높이가 내려갑니다. 내려가는 계단입니까?",
                cha > 0 ? "올라가는 계단 시작" : "내려가는 계단 시작", n - 9)
    }

    /// 여쭙고, 말소리가 끝나면 네·아니오를 한 번 들음 — 단추로도 답할 수 있음
    private func yeojjum(_ mal: String, _ ne: String, _ jariI: Int) {
        majimakMureum = Date()
        mureum = Mureum(mal: mal, ne: ne, danchu: "네 — " + ne, jariI: max(0, jariI), ttae: Date())
        Girok.shared.namgi("jb_mureum", ["ne": ne])
        SoriEngine.shared.mal(mal, .annae)
        SoriEngine.shared.kkeutnamyeon { [weak self] in self?.neDeutgi() }
    }

    private func neDeutgi() {
        guard mureum != nil, sangtae == .georeum, MalDeutgi.heorakItda, !malDeutneun else { return }
        malDeutneun = true
        let ok = MalDeutgi.shared.myeongryeong(gidarim: 5) { [weak self] alts in
            guard let self = self else { return }
            self.malDeutneun = false
            MalDeutgi.shared.meomchugi()
            guard self.mureum != nil else { return }
            let z = alts.map { MalSajeon.ttuk($0) }
            if z.contains(where: { $0.hasPrefix("아니") || $0.hasPrefix("아뇨") || $0 == "no" }) { self.dap(false); return }
            if z.contains(where: { $0.hasPrefix("네") || $0.hasPrefix("예") || $0.hasPrefix("응") || $0.hasPrefix("맞") || $0.hasPrefix("그래") }) { self.dap(true); return }
        }
        if !ok { malDeutneun = false }
    }

    /// 물음에 답함
    func dap(_ ne: Bool) {
        guard let m = mureum else { return }
        mureum = nil
        if ne {
            let ps = gil?.pts ?? []
            let jari = m.jariI < ps.count ? ps[m.jariI] : nil
            pyosi(m.ne, jari: jari, mureum: true)
        } else {
            Girok.shared.namgi("jb_mureum_ani", ["ne": m.ne])
            alrigi("알겠습니다. 남기지 않았습니다.")
        }
    }

    // MARK: 표시

    /// 표시 남기기 — jari 가 있으면 그 자리(물음·말로 표시), 없으면 지금 자리
    func pyosi(_ name: String, jari: GrJari? = nil, malo: Bool = false, mureum mu: Bool = false) {
        guard gil != nil else { alrigi("걷기를 시작한 뒤에 눌러 주십시오."); return }
        guard sangtae == .georeum else { alrigi("지금은 잠깐 멈춤입니다. 다시 걷기를 먼저 눌러 주십시오."); return }
        let p = jari ?? jigeumJari()
        var m = GrPyosi(t: p.t, st: p.st, name: name)
        m.h = p.h; m.lat = p.lat; m.lon = p.lon; m.acc = p.acc
        if malo { m.malo = true }
        if mu { m.mureum = true }
        if name.hasSuffix("꺾임") { majimakKkeokim = Date() }
        mureum = nil
        defer { jeojang() }
        let st = m.st

        // 시작 표시면 짝을 열어 둠
        if let jj = JeomGeurigi.PAIR[name] {
            openPair = (name, st, m.lat, m.lon, gil?.marks.count ?? 0, m.t)
            m.kind = jj.kind
            if !jj.up.isEmpty { m.up = jj.up }
            if jj.ride { m.ride = true; rideMode = jj.kind }
            gil?.marks.append(m)
            alrigi(name + "을 남겼습니다. " + (jj.ride ? "내리실 때 \(jj.end)을 눌러 주십시오. 타고 가시는 동안은 걸음으로 재지 않습니다."
                                                      : "끝나는 곳에서 \(jj.end)을 눌러 주십시오."))
            return
        }
        // 탈것에서 내림
        if name == "에스컬레이터 내림" || name == "지하철 내림" || name == "버스 내림" {
            if let o = openPair, JeomGeurigi.PAIR[o.name]?.ride == true {
                let kk = JeomGeurigi.PAIR[o.name]?.kind ?? ""
                let secs = m.t - o.t
                let dd = JeomGeurigi.geori(o.lat, o.lon, m.lat, m.lon)
                if o.idx < (gil?.marks.count ?? 0) {
                    gil?.marks[o.idx].secs = secs
                    gil?.marks[o.idx].dist = dd
                    gil?.marks[o.idx].ride = true
                }
                m.pairOf = o.name; m.ride = true
                gil?.marks.append(m)
                rideMode = ""
                openPair = nil
                var mal = kk + "에서 내리셨습니다. "
                mal += secs >= 60 ? "\(secs / 60)분 \(secs % 60)초" : "\(secs)초"
                mal += " 타셨고, " + (dd >= 1000 ? String(format: "%.1f킬로미터", Double(dd) / 1000) : "\(dd)미터")
                mal += " 오셨습니다. 이 구간은 걸음으로 재지 않고 그대로 적었습니다."
                alrigi(mal)
                return
            }
            rideMode = ""
            gil?.marks.append(m)
            alrigi(name + "을 눌렀으나 탄 자리가 없습니다. 그냥 표시로만 남깁니다.")
            return
        }
        // 끝 표시면 그 사이 걸음과 거리를 셈해 시작 표시에 적음
        if name == "계단 끝" || name == "횡단보도 건너기 끝" {
            if let o = openPair, JeomGeurigi.PAIR[o.name]?.ride != true {
                let n = st - o.st
                let d = JeomGeurigi.geori(o.lat, o.lon, m.lat, m.lon)
                if o.idx < (gil?.marks.count ?? 0) {
                    gil?.marks[o.idx].cnt = n
                    gil?.marks[o.idx].dist = d
                }
                m.pairOf = o.name
                gil?.marks.append(m)
                if JeomGeurigi.PAIR[o.name]?.kind == "계단" {
                    alrigi("\(JeomGeurigi.PAIR[o.name]?.up ?? "") 계단이 \(n)칸입니다. 이대로 적습니다. 틀리면 계단 칸수 고치기를 눌러 주십시오.")
                } else {
                    alrigi("횡단보도를 \(n)걸음, 약 \(d)미터 건너셨습니다. 그대로 적었습니다.")
                }
                openPair = nil
                return
            }
            gil?.marks.append(m)
            alrigi(name + "을 눌렀으나 시작 표시가 없습니다. 그냥 표시로만 남깁니다.")
            return
        }
        gil?.marks.append(m)
        // 문은 딱 찍고 두 걸음 앞에서 한 번 더 — 걸음과 거리로 가림(웹과 같음)
        if name == "문" {
            let ms = gil?.marks ?? []
            let jjak = ms.dropLast().last(where: { $0.name == "문" })
            var doem = false
            if let jj = jjak, let la = m.lat, let lo = m.lon, let la2 = jj.lat, let lo2 = jj.lon {
                let dd2 = WichiEngine.geori(la, lo, la2, lo2)
                if dd2 >= 0.3 && dd2 <= 3.5 && (m.st - jj.st) <= 5 && (m.t - jj.t) <= 20 { doem = true }
            } else if let jj = jjak, (m.st - jj.st) >= 1, (m.st - jj.st) <= 5, (m.t - jj.t) <= 20 {
                doem = true   // 위성이 없는 안쪽 — 걸음으로만 가림
            }
            if doem {
                alrigi((jjak?.st ?? 0) <= 10 ? "나오시는 문을 두 번 찍으셨습니다. 되돌아오실 때 이 문 앞으로 안내됩니다."
                                             : "도착하시는 문을 두 번 찍으셨습니다. 이 문은 확실한 문으로 남고, 들어가는 방향까지 함께 남습니다.")
            } else {
                alrigi("문을 남겼습니다. 곧바로 두 걸음 앞으로 가서 문을 한 번 더 눌러 주십시오. 딱 찍고 두 걸음 뒤에 또 찍으셔야 문이 됩니다.")
            }
            return
        }
        alrigi(name + "을 남겼습니다. 지금까지 \(st)걸음, 표시 \(gil?.marks.count ?? 0)개입니다.")
    }

    /// 계단 칸수 고치기 — 마지막 계단의 칸수를 바꿈
    func gyedanGochigi(_ n: Int) {
        guard let i = gil?.marks.lastIndex(where: { $0.kind == "계단" && $0.cnt != nil }) else {
            alrigi("고칠 계단이 없습니다."); return
        }
        gil?.marks[i].cnt = n
        jeojang()
        alrigi("계단을 \(n)칸으로 고쳤습니다.")
    }
    var majimakGyedan: Int? { gil?.marks.last(where: { $0.kind == "계단" && $0.cnt != nil })?.cnt }

    /// 말로 표시 — 누른 순간의 자리를 잡고, 말씀이 끝나면 이름을 붙임
    func malloPyosi() {
        guard sangtae == .georeum else { alrigi("걷기를 시작한 뒤에 말씀해 주십시오."); return }
        guard MalDeutgi.heorakItda else {
            MalDeutgi.heorak { ok in
                if ok { self.malloPyosi() } else { self.alrigi("말로 표시하려면 마이크와 음성 인식 허락이 필요합니다.") }
            }
            return
        }
        guard !malDeutneun else { return }
        let jari = jigeumJari()
        malDeutneun = true
        SoriEngine.shared.sori(.deutgi)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
            let ok = MalDeutgi.shared.myeongryeong(gidarim: 6) { [weak self] alts in
                guard let self = self else { return }
                self.malDeutneun = false
                MalDeutgi.shared.meomchugi()
                if let ireum = JeomGeurigi.malChatgi(alts) {
                    self.pyosi(ireum, jari: jari, malo: true)
                } else {
                    let t = alts.first ?? ""
                    Girok.shared.namgi("jb_malpyosi_moreum", ["mal": String(t.prefix(30))])
                    self.alrigi(t.isEmpty ? "말씀이 들리지 않았습니다. 다시 말로 표시를 눌러 주십시오."
                                          : "\(t)는 표시 이름으로 알아듣지 못했습니다. 계단 시작, 왼쪽, 문처럼 말씀해 주십시오.")
                }
            }
            if !ok { self.malDeutneun = false; self.alrigi("지금은 마이크를 열지 못했습니다. 단추로 남겨 주십시오.") }
        }
    }

    static func malChatgi(_ alts: [String]) -> String? {
        for a in alts {
            let z = MalSajeon.ttuk(a)
            if z.isEmpty { continue }
            if let m = MARKS.first(where: { MalSajeon.ttuk($0) == z }) { return m }
            if let m = BYEOLCHING[z] { return m }
            // 긴 말 안에 든 것 — 긴 이름부터
            for k in BYEOLCHING.keys.sorted(by: { $0.count > $1.count }) where k.count >= 2 && z.contains(k) { return BYEOLCHING[k] }
        }
        return nil
    }

    // MARK: 지금 어디쯤 — 길을 접어들 때

    private func gilBoda() {
        guard !gilMutneunJung, let w = WichiEngine.shared.jigeum, w.ochae <= 30 else { return }
        if let j = gilJari, WichiEngine.geori(j.0, j.1, w.lat, w.lon) < 40 { return }
        gilJari = (w.lat, w.lon)
        gilMutneunJung = true
        Task {
            let r = await Chatgi.gil(w.lat, w.lon)
            await MainActor.run {
                self.gilMutneunJung = false
                guard self.sangtae == .georeum, let r = r, !r.gil.isEmpty, r.gil != self.gilIreum else { return }
                let cheot = self.gilIreum.isEmpty
                self.gilIreum = r.gil
                SoriEngine.shared.mal(cheot ? "지금 \(r.dong) \(r.gil)입니다." : "\(r.gil)에 접어드셨습니다.", .jeongbo)
            }
        }
    }

    // MARK: 지금 상태 듣기

    func sangtaeMal() -> String {
        switch sangtae {
        case .swim:
            return geurinGil.isEmpty ? "아직 그린 길이 없습니다." : "그린 길이 \(geurinGil.count)개 폰에 담겨 있습니다."
        case .meomchum:
            return "잠깐 멈춤입니다. 지금까지 \(gil?.georeum ?? 0)걸음, 표시 \(gil?.marks.count ?? 0)개입니다."
        case .georeum:
            var m = "그리는 중입니다. \(chobun / 60)분 \(chobun % 60)초 동안 \(georeum)걸음, 약 \(Int(Double(georeum) * Seoljeong.shared.bopok))미터, 표시 \(gil?.marks.count ?? 0)개입니다."
            if let o = openPair { m += " \(o.name) 뒤에 \(JeomGeurigi.PAIR[o.name]?.end ?? "끝")을 아직 남기지 않으셨습니다." }
            if let w = WichiEngine.shared.jigeum { m += w.ochae <= 15 ? " 위성이 잘 잡혀 있습니다." : " 위성이 흐려 걸음으로 이어 셉니다." }
            if MomSensor.shared.dollyeo { m += " 몸 센서로 센 걸음은 \(MomSensor.shared.georeumSu)걸음입니다." }
            return m
        }
    }

    // MARK: 저장

    func jeojang() {
        guard let g = gil else { return }
        if let d = try? JSONEncoder().encode(g) { try? d.write(to: JeomGeurigi.jigeumPail, options: .atomic) }
    }

    private func mokJeojang() {
        if let d = try? JSONEncoder().encode(geurinGil) { try? d.write(to: JeomGeurigi.mokPail, options: .atomic) }
    }

    private func alrigi(_ t: String) {
        allim = t
        SoriEngine.shared.mal(t, .annae)
    }

    // MARK: 작은 셈

    static func geori(_ a1: Double?, _ o1: Double?, _ a2: Double?, _ o2: Double?) -> Int {
        guard let a1 = a1, let o1 = o1, let a2 = a2, let o2 = o2 else { return 0 }
        return Int(WichiEngine.geori(a1, o1, a2, o2).rounded())
    }

    /// 각도들의 평균(0~360, 북쪽 근처에서 섞여도 바르게)
    static func pyeonggyun(_ gs: [Double]) -> Double {
        let p = Double.pi / 180
        let x = gs.reduce(0) { $0 + cos($1 * p) }, y = gs.reduce(0) { $0 + sin($1 * p) }
        var d = atan2(y, x) / p
        if d < 0 { d += 360 }
        return d
    }

    /// a 에서 b 로 돈 각도(-180~180, 오른쪽이 +)
    static func gakCha(_ a: Double, _ b: Double) -> Double {
        var d = (b - a).truncatingRemainder(dividingBy: 360)
        if d > 180 { d -= 360 }
        if d < -180 { d += 360 }
        return d
    }
}

// MARK: 화면

struct GeurigiView: View {
    @ObservedObject private var g = JeomGeurigi.shared
    @ObservedObject private var s = Seoljeong.shared
    @State private var gyedanSu = ""
    @AccessibilityFocusState private var allimChojeom: Bool
    @AccessibilityFocusState private var mureumChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                // 폰이 여쭙는 말과 답하는 단추는 한 자리에
                if let m = g.mureum {
                    Text(m.mal).font(.title3.bold()).accessibilityFocused($mureumChojeom)
                    Button(m.danchu) { g.dap(true) }.buttonStyle(KeunDanchu())
                    Button("아니오 — 남기지 않기") { g.dap(false) }.buttonStyle(KeunDanchu())
                }
                if !g.allim.isEmpty { Text(g.allim).font(.title3).accessibilityFocused($allimChojeom) }
                switch g.sangtae {
                case .swim: sijakJeon
                case .georeum: georeumJung
                case .meomchum: meomchumJung
                }
            }
            .padding()
        }
        .sokHwamyeon("점지도 그리기")
        .onChange(of: g.allim) { _ in DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { allimChojeom = true } }
        .onChange(of: g.mureum) { m in if m != nil { DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { mureumChojeom = true } } }
    }

    @ViewBuilder private var sijakJeon: some View {
        if s.bopok > 0.2 && s.bopokJaem {
            Button("걷기 시작 — 출발 자리 주소는 저절로 적습니다") { g.sijak() }.buttonStyle(KeunDanchu())
        } else {
            NavigationLink { BopokView() } label: { Text("먼저 내 보폭 재기 — 보폭이 있어야 그릴 수 있습니다") }.buttonStyle(KeunDanchu())
        }
        Button("지금 상태 듣기") { SoriEngine.shared.mal(g.sangtaeMal()) }.buttonStyle(KeunDanchu())
        if !g.geurinGil.isEmpty {
            DisclosureGroup("그린 길 \(g.geurinGil.count)개 펼치기") {
                Mokrok5(g.geurinGil) { gil in
                    Text(JeomGeurigi.gilJul(gil)).font(.body).frame(maxWidth: .infinity, alignment: .leading)
                }
            }.font(.title3)
        }
        Text("꺾이는 곳, 계단, 건널목, 문에 닿는 순간 표시를 남기시면 됩니다. 폰이 방향이나 높이가 바뀐 것을 알아채면 먼저 여쭙니다. 네라고 말씀하시거나 네 단추를 누르셔야 표시가 됩니다.")
            .font(.body)
    }

    @ViewBuilder private var georeumJung: some View {
        ForEach(JeomGeurigi.JAJU, id: \.self) { n in
            Button(n) { g.pyosi(n) }.buttonStyle(KeunDanchu())
        }
        Button(g.malDeutneun ? "말로 표시 — 듣는 중" : "말로 표시 — 누르고 계단 시작처럼 말씀하십시오") { g.malloPyosi() }
            .buttonStyle(KeunDanchu())
        Button("다 걸었습니다") { g.kkeut() }.buttonStyle(KeunDanchu())
        DisclosureGroup("다른 표시와 도구 펼치기") {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(JeomGeurigi.MARKS.filter { !JeomGeurigi.JAJU.contains($0) }, id: \.self) { n in
                    Button(n) { g.pyosi(n) }.buttonStyle(KeunDanchu())
                }
                Button("지금 상태 듣기") { SoriEngine.shared.mal(g.sangtaeMal()) }.buttonStyle(KeunDanchu())
                Button("잠깐 멈춤") { g.jamkkan() }.buttonStyle(KeunDanchu())
                if let n = g.majimakGyedan {
                    TextField("계단 칸수 — 지금 \(n)칸", text: $gyedanSu)
                        .keyboardType(.numberPad).textFieldStyle(.roundedBorder).font(.title3)
                    Button("계단 칸수 고치기") {
                        if let k = Int(gyedanSu.trimmingCharacters(in: .whitespaces)), k > 0 { g.gyedanGochigi(k); gyedanSu = "" }
                    }.buttonStyle(KeunDanchu())
                }
            }
        }.font(.title3)
    }

    @ViewBuilder private var meomchumJung: some View {
        Button("다시 걷기") { g.dasiGeotgi() }.buttonStyle(KeunDanchu())
        Button("다 걸었습니다 — 여기까지로 마치기") { g.kkeut() }.buttonStyle(KeunDanchu())
        Button("지금 상태 듣기") { SoriEngine.shared.mal(g.sangtaeMal()) }.buttonStyle(KeunDanchu())
    }
}

extension JeomGeurigi {
    /// 그린 길 한 줄
    static func gilJul(_ g: GrGil) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "M월 d일 H시 m분"
        let geori = Int(Double(g.georeum) * g.bopok)
        let eodi = g.from.isEmpty ? "" : " \(g.from)에서" + (g.to.isEmpty ? "" : " \(g.to)까지")
        return "\(f.string(from: g.sijak))\(eodi), \(g.georeum)걸음 약 \(geori)미터, 표시 \(g.marks.count)개" + (g.olim ? ", 올림" : ", 올리기 전")
    }
}
