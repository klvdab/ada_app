// 위치 엔진 — 위성·나침반·걸음 센서·빠르기를 한 곳에서 받습니다.
// ① 폰이 잠겨도, 주머니 속에서도 계속(UIBackgroundModes location, 잠긴 뒤에도 받기 켬)
// ② 점지도의 바탕은 걸음 — 위성이 6초 넘게 끊기거나 오차가 25미터를 넘으면 걸음 수 × 보폭 × 방향으로 자리를 이어 셈
// ③ 화면들은 위성에 직접 붙지 않고 이 엔진에서만 받아 씀(위성 붙들기는 한 곳뿐)
import CoreLocation
import CoreMotion
import Combine
import Foundation

struct Wichi {
    let lat: Double
    let lon: Double
    let ochae: Double      // 미터
    let banghyang: Double  // 북쪽 기준 도, 모르면 -1
    let sokdo: Double      // 초속 미터
    let ttae: Date
    let georeumChu: Bool   // 위성이 흐려 걸음으로 이어 셈한 자리
    /// 2.105.0 (261024-I59, 이사장님 지시 2026-10-24) 위성이 아니라 와이파이·기지국으로 짐작한 자리(땅속·지하철 안) —
    ///   6호선 땅속에서 아이폰이 신내차량기지를 「오차 16미터」로 내놓아 하루 내내 엉뚱한 역에서 길을 찾던 일. 짐작 자리는 지금 자리로 단정하지 않음
    var jjimjak: Bool = false
    /// 2.105.0 위성 자리 모양새만 본 것(땅속 여부는 뺌) — 땅 위로 나왔는지·위성이 좋은지 가릴 때 씀
    var jjimjakWon: Bool = false
    /// 2.105.0 이용자가 「여기는 ○○역이야」로 알려 주신 자리
    var sonJari: Bool = false
    /// 2.113.0 (261026-I67) 폰이 빠르기로 재 준 진행 방향(도) — 없으면 -1. banghyang 은 느릴 때 나침반으로 채우므로 차 안 돌기 셈에는 이것만 씀
    var jinhaeng: Double = -1
}

final class WichiEngine: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = WichiEngine()

    private let mgr = CLLocationManager()
    private let pedo = CMPedometer()
    let saeWichi = PassthroughSubject<Wichi, Never>()

    @Published private(set) var jigeum: Wichi?
    @Published private(set) var heorak: String = "아직 묻지 않음"
    @Published private(set) var hangsang = false
    @Published private(set) var oneulGeoreum: Int = 0
    private(set) var batunSu = 0
    private(set) var majimakWiseong: Date?
    private(set) var nachimban: Double = -1

    private var georeumNujeok = 0     // 앱이 켜진 뒤 센 걸음
    /// 2.13.0 앱이 켜진 뒤 센 걸음(되짚어 나가기가 씀)
    var georeumSu: Int { georeumNujeok }
    private var georeumCheot: Int?
    /// 2.32.0 따라 걷기 중에는 몸 센서 걸음으로 만보기의 늦음(1~2초)을 메움 — 만보기 값이 올 때마다 그 값에 다시 맞춤
    var momBbareum = false
    private var manboNujeok = 0
    private var momManboTtae: Int?
    private var iegoGijun = 0         // 마지막으로 자리를 셈한 때의 걸음
    /// 2.105.0 (261024-I59) 마지막으로 위성이 확실히 잡아 준 자리(앱을 껐다 켜도 남김)
    private(set) var majimakMideum: Wichi?
    /// 2.105.0 이용자가 알려 주신 자리와 그 때 — 20분 안에는 이와 동떨어진 짐작 자리를 버림
    private var sonJariTtae: Date?
    private var jjimjakJeon: Bool?
    private var sigye: Timer?
    private var dolgo = false

    override init() {
        super.init()
        let ud = UserDefaults.standard
        if ud.object(forKey: "gn.mideumLat") != nil {
            majimakMideum = Wichi(lat: ud.double(forKey: "gn.mideumLat"), lon: ud.double(forKey: "gn.mideumLon"), ochae: 20,
                                  banghyang: -1, sokdo: 0, ttae: Date(timeIntervalSince1970: ud.double(forKey: "gn.mideumTtae")), georeumChu: false)
        }
        mgr.delegate = self
        mgr.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        mgr.distanceFilter = kCLDistanceFilterNone
        mgr.activityType = .otherNavigation
        mgr.pausesLocationUpdatesAutomatically = false
        mgr.headingFilter = 5
    }

    func sijak() {
        dolgo = true
        heorakBoda(mgr.authorizationStatus)
        switch mgr.authorizationStatus {
        case .notDetermined:
            mgr.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways:
            dolligi()
        default:
            break
        }
        georeumSijak()
        if sigye == nil {
            sigye = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.iegoSem() }
        }
    }

    /// 자봉 2.32.0 (261025-I26, 이사장님 승인 2026-10-25) 「프로그램 끝내기」 — 위성·방향·걸음 세기를 모두 멈춤(다시 켤 때 sijak)
    func meomchugi() {
        dolgo = false
        mgr.stopUpdatingLocation()
        mgr.stopUpdatingHeading()
        mgr.allowsBackgroundLocationUpdates = false
        pedo.stopUpdates()   // 걸음은 자정부터 세므로 다시 켜도 이어짐(georeumCheot 그대로)
        sigye?.invalidate()
        sigye = nil
    }

    /// "항상 허락"을 청함 — 앱을 쓰는 동안만 허락된 때에만 창이 뜸
    func hangsangHeorakCheong() {
        if mgr.authorizationStatus == .authorizedWhenInUse { mgr.requestAlwaysAuthorization() }
    }

    /// 2.111.0 (261026-I65, 이사장님 승인 2026-10-26 「1」) 허락 상태를 새로 읽고, 아직 묻지 않았으면 아이폰의 허락 창을 띄움 —
    ///   아이폰 위치 서비스가 꺼진 채 길눈이 열려 허락 창이 한 번도 뜨지 못하면, 위치 서비스 목록에 길눈이 아예 없던 일(10-26 아침)
    func heorakSaero() {
        let s = mgr.authorizationStatus
        heorakBoda(s)
        if s == .notDetermined { mgr.requestWhenInUseAuthorization() }
    }

    var geojeoldoem: Bool {
        mgr.authorizationStatus == .denied || mgr.authorizationStatus == .restricted
    }

    private func dolligi() {
        mgr.allowsBackgroundLocationUpdates = true
        mgr.showsBackgroundLocationIndicator = true
        mgr.startUpdatingLocation()
        if CLLocationManager.headingAvailable() { mgr.startUpdatingHeading() }
    }

    private func heorakBoda(_ s: CLAuthorizationStatus) {
        switch s {
        case .authorizedAlways: heorak = "항상 허락"
        case .authorizedWhenInUse: heorak = "앱을 쓰는 동안만 허락"
        case .denied: heorak = "거절됨"
        case .restricted: heorak = "막혀 있음"
        default: heorak = "아직 묻지 않음"
        }
        hangsang = (s == .authorizedAlways)
    }

    // MARK: CLLocationManagerDelegate

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        heorakBoda(manager.authorizationStatus)
        Girok.shared.namgi("wichi_heorak", ["s": heorak])
        let s = manager.authorizationStatus
        if dolgo && (s == .authorizedWhenInUse || s == .authorizedAlways) { dolligi() }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        nachimban = newHeading.trueHeading >= 0 ? newHeading.trueHeading : newHeading.magneticHeading
        MomSensor.shared.nachimbanNeogi(nachimban)   // 2.32.0 자이로 합성 방향이 나침반 쪽으로 천천히 맞춰지게
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let l = locations.last, l.horizontalAccuracy >= 0 else { return }
        if abs(l.timestamp.timeIntervalSinceNow) > 10 { return }
        batunSu += 1
        // 2.105.0 (261024-I59, 이사장님 지시 2026-10-24) 위성으로 잡은 자리인가 — 위성 자리는 높이(고도 오차)를 함께 줌.
        //   높이가 없거나 기압계로 땅속에 계신 것을 알면 와이파이·기지국으로 짐작한 자리로 봄
        //   jjimjakWon 은 자리 모양새만(땅속 여부를 넣으면 땅 위로 나와 위성이 잡혀도 「나왔다」를 알 수 없음)
        let jjimjakWon = !(l.verticalAccuracy > 0 && l.horizontalAccuracy <= 65)
        let jjimjak = jjimjakWon || TalgeotGamji.shared.jiha
        if jjimjakJeon != jjimjak {
            jjimjakJeon = jjimjak
            Girok.shared.namgi("wichi_jjimjak", ["jj": jjimjak, "va": Int(l.verticalAccuracy), "sp": Int(l.speed * 10), "oc": Int(l.horizontalAccuracy),
                                                 "jiha": TalgeotGamji.shared.jiha, "la": l.coordinate.latitude, "lo": l.coordinate.longitude])
        }
        if jjimjakWon, let beorim = jjimjakBeorimKkadak(l) ?? heoJariKkadak(l) {
            Girok.shared.namgi("wichi_beorim", ["kkadak": beorim, "oc": Int(l.horizontalAccuracy), "la": l.coordinate.latitude, "lo": l.coordinate.longitude])
            return
        }
        let heurim = l.horizontalAccuracy > 25 || jjimjakWon
        if !heurim { majimakWiseong = Date() }
        if !jjimjakWon && l.horizontalAccuracy <= 30 {
            sonJariTtae = nil
            let m = Wichi(lat: l.coordinate.latitude, lon: l.coordinate.longitude, ochae: l.horizontalAccuracy, banghyang: -1, sokdo: 0, ttae: Date(), georeumChu: false)
            majimakMideum = m
            let ud = UserDefaults.standard
            ud.set(m.lat, forKey: "gn.mideumLat"); ud.set(m.lon, forKey: "gn.mideumLon"); ud.set(m.ttae.timeIntervalSince1970, forKey: "gn.mideumTtae")
        }
        // 걸음으로 이어 셈하는 중이면 흐린 위성으로 덮지 않음
        if heurim, let j = jigeum, j.georeumChu, Date().timeIntervalSince(j.ttae) < 10 { return }
        if majimakWiseong == nil { majimakWiseong = Date() }
        let bang = (l.course >= 0 && l.speed > 1.5) ? l.course : nachimban
        iegoGijun = georeumNujeok
        naegi(Wichi(lat: l.coordinate.latitude, lon: l.coordinate.longitude, ochae: l.horizontalAccuracy,
                    banghyang: bang, sokdo: max(0, l.speed), ttae: Date(), georeumChu: false, jjimjak: jjimjak, jjimjakWon: jjimjakWon,
                    jinhaeng: (l.course >= 0 && l.speed > 0) ? l.course : -1))
    }

    /// 2.105.0 (261024-I59) 짐작 자리를 버릴 까닭 — 알려 주신 자리(20분 안)나 위성이 확실히 잡은 자리(3시간 안)에서 사람이 갈 수 없는 거리로 건너뛰었으면 버림
    private func jjimjakBeorimKkadak(_ l: CLLocation) -> String? {
        let now = Date()
        // 알려 주신 자리(20분 안)는 짐작 자리로 덮지 않음 — 위성이 다시 확실히 잡을 때까지 그대로
        if let st = sonJariTtae, now.timeIntervalSince(st) < 1200, let j = jigeum, j.sonJari {
            return "알려 주신 자리 지킴(\(Int(WichiEngine.geori(j.lat, j.lon, l.coordinate.latitude, l.coordinate.longitude)))미터)"
        }
        if let m = majimakMideum, now.timeIntervalSince(m.ttae) < 3 * 3600 {
            let d = WichiEngine.geori(m.lat, m.lon, l.coordinate.latitude, l.coordinate.longitude)
            if d > 1500 + 25 * now.timeIntervalSince(m.ttae) { return "위성 자리에서 \(Int(d))미터 건너뜀" }
        }
        return nil
    }

    /// 2.105.0 (261024-I59) 땅속에서 아이폰이 엉뚱하게 내놓는 것으로 확인된 자리 — 6호선 열차 안에서 신내차량기지(열차들이 밤에 서 있는 곳)가 두 번 나옴.
    ///   위성이 마지막으로 확실히 잡은 자리가 3킬로미터 넘게 떨어져 있으면 버림(정말 그 근처에 계시면 위성이 다시 잡아 줌)
    private static let heoJari: [(String, Double, Double)] = [("6호선 신내차량기지", 37.6128, 127.1034)]
    private func heoJariKkadak(_ l: CLLocation) -> String? {
        for h in WichiEngine.heoJari where WichiEngine.geori(h.1, h.2, l.coordinate.latitude, l.coordinate.longitude) < 400 {
            guard let m = majimakMideum else { return "\(h.0) 자리(위성 자리 없음)" }
            if WichiEngine.geori(m.lat, m.lon, h.1, h.2) > 3000 { return "\(h.0) 자리" }
        }
        return nil
    }

    /// 2.105.0 (261024-I59, 이사장님 지시 2026-10-24) 「여기는 ○○역이야」 — 이용자가 알려 주신 자리를 지금 자리로(위성이 다시 확실히 잡을 때까지)
    func sonJariNoki(_ lat: Double, _ lon: Double) {
        sonJariTtae = Date()
        iegoGijun = georeumNujeok
        naegi(Wichi(lat: lat, lon: lon, ochae: 50, banghyang: nachimban, sokdo: 0, ttae: Date(), georeumChu: false, jjimjak: false, sonJari: true))
    }

    /// 2.105.0 지금 자리를 믿을 만한가 — 위성이 15초 안에 확실히 잡았거나, 알려 주신 자리이거나, 그 둘에서 걸음으로 이어 셈한 자리
    var jigeumMideum: Bool {
        guard let j = jigeum, !j.jjimjak else { return false }
        if j.sonJari { return true }
        return majimakWiseong.map { Date().timeIntervalSince($0) < 60 } ?? false
    }

    /// 2.103.0 (261021-I57, 이사장님 승인 2026-10-21) 위치 오류를 까닭별로 — 잠깐 위성을 놓친 것(0)은 오류로 세지 않고, 위치 허락이 꺼진 것(1)만 오류로 세어 한 번 말씀드림.
    ///   (10-20 하루 위치 오류 80번이 모두 같은 오류로 적혀, 정말 안내를 못 하게 된 순간을 찾을 수 없던 일)
    private var heorakMalTtae = Date.distantPast
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        let code = (error as NSError).code
        switch code {
        case CLError.Code.locationUnknown.rawValue:
            Girok.shared.namgi("wichi_jamkkan", ["georeumChu": jigeum?.georeumChu ?? false])
        case CLError.Code.denied.rawValue:
            Girok.shared.namgi("wichi_oryu", ["code": code, "iyu": CLLocationManager.locationServicesEnabled() ? "heorak" : "wichi_kkeum"])
            DispatchQueue.main.async {
                guard Date().timeIntervalSince(self.heorakMalTtae) > 600 else { return }
                self.heorakMalTtae = Date()
                SoriEngine.shared.mal(CLLocationManager.locationServicesEnabled()
                    ? "위치를 잡을 수 없습니다. 아이폰 설정의 \(OllimEngine.shared.aeIreum)에서 위치를 켜 주십시오."   // 2.103.0 자봉에서도 제 앱 이름으로(261021-J1)
                    : "위치를 잡을 수 없습니다. 아이폰 설정의 개인정보 보호에서 위치 서비스를 켜 주십시오.", .gyeonggo)
            }
        default:
            Girok.shared.namgi("wichi_oryu", ["code": code, "iyu": "gita"])
        }
    }

    private func naegi(_ w: Wichi) {
        DispatchQueue.main.async {
            self.jigeum = w
            self.saeWichi.send(w)
        }
    }

    // MARK: 걸음

    private func georeumSijak() {
        guard CMPedometer.isStepCountingAvailable() else { return }
        let jajeong = Calendar.current.startOfDay(for: Date())
        pedo.startUpdates(from: jajeong) { [weak self] d, _ in
            guard let self = self, let d = d else { return }
            let n = d.numberOfSteps.intValue
            DispatchQueue.main.async {
                if self.georeumCheot == nil { self.georeumCheot = n }
                self.oneulGeoreum = n
                let m = n - (self.georeumCheot ?? n)
                self.manboNujeok = m
                self.momManboTtae = (self.momBbareum && MomSensor.shared.dollyeo) ? MomSensor.shared.georeumSu : nil
                if !self.momBbareum || m > self.georeumNujeok { self.georeumNujeok = m }   // 따라 걷기 중에는 뒤로 줄지 않게
            }
        }
    }

    /// 2.32.0 몸 센서가 한 걸음을 잡을 때마다(따라 걷기 중) — 만보기 값 + 그 뒤 몸 센서가 센 걸음
    func momGeoreumNal() {
        guard momBbareum, MomSensor.shared.dollyeo else { return }
        let ms = MomSensor.shared.georeumSu
        if momManboTtae == nil { momManboTtae = ms - 1; manboNujeok = georeumNujeok }
        let bbareun = manboNujeok + (ms - (momManboTtae ?? ms))
        if bbareun > georeumNujeok { georeumNujeok = bbareun }
    }

    /// 1초마다 — 위성이 끊기거나 흐리면 걸음으로 자리를 이어 셈
    private func iegoSem() {
        guard let j = jigeum else { return }
        let kkeunkim = majimakWiseong.map { Date().timeIntervalSince($0) > 6 } ?? true
        guard kkeunkim || j.ochae > 25 else { return }
        let sae = georeumNujeok - iegoGijun
        guard sae > 0, nachimban >= 0 else { return }
        let geori = Double(sae) * Seoljeong.shared.bopok
        let (la, lo) = WichiEngine.olgida(j.lat, j.lon, geori, nachimban)
        iegoGijun = georeumNujeok
        naegi(Wichi(lat: la, lon: lo, ochae: j.ochae + geori * 0.1, banghyang: nachimban,
                    sokdo: geori, ttae: Date(), georeumChu: true, jjimjak: j.jjimjak, jjimjakWon: j.jjimjakWon, sonJari: j.sonJari))
    }

    // MARK: 셈

    /// 두 자리 사이 거리(미터)
    static func geori(_ a1: Double, _ o1: Double, _ a2: Double, _ o2: Double) -> Double {
        let r = 6371000.0, p = Double.pi / 180
        let da = (a2 - a1) * p, dO = (o2 - o1) * p
        let h = sin(da / 2) * sin(da / 2) + cos(a1 * p) * cos(a2 * p) * sin(dO / 2) * sin(dO / 2)
        return 2 * r * asin(min(1, sqrt(h)))
    }

    /// 앞 자리에서 뒤 자리를 보는 방위(북쪽 0도, 시계 방향)
    static func bangwi(_ a1: Double, _ o1: Double, _ a2: Double, _ o2: Double) -> Double {
        let p = Double.pi / 180
        let y = sin((o2 - o1) * p) * cos(a2 * p)
        let x = cos(a1 * p) * sin(a2 * p) - sin(a1 * p) * cos(a2 * p) * cos((o2 - o1) * p)
        let b = atan2(y, x) / p
        return (b + 360).truncatingRemainder(dividingBy: 360)
    }

    /// 한 자리에서 방위 쪽으로 몇 미터 옮긴 자리
    static func olgida(_ la: Double, _ lo: Double, _ m: Double, _ deg: Double) -> (Double, Double) {
        let r = 6371000.0, p = Double.pi / 180
        let dla = m * cos(deg * p) / r / p
        let dlo = m * sin(deg * p) / (r * cos(la * p)) / p
        return (la + dla, lo + dlo)
    }

    /// 내가 보는 쪽을 12시로 할 때 목표가 몇 시 방향인지(1~12)
    static func sigyeBanghyang(jeongmyeon: Double, mokpyo: Double) -> Int {
        var d = (mokpyo - jeongmyeon).truncatingRemainder(dividingBy: 360)
        if d < 0 { d += 360 }
        let s = Int((d / 30).rounded()) % 12
        return s == 0 ? 12 : s
    }
}
