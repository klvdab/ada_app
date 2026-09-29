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
    private var iegoGijun = 0         // 마지막으로 자리를 셈한 때의 걸음
    private var sigye: Timer?
    private var dolgo = false

    override init() {
        super.init()
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

    /// "항상 허락"을 청함 — 앱을 쓰는 동안만 허락된 때에만 창이 뜸
    func hangsangHeorakCheong() {
        if mgr.authorizationStatus == .authorizedWhenInUse { mgr.requestAlwaysAuthorization() }
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
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let l = locations.last, l.horizontalAccuracy >= 0 else { return }
        if abs(l.timestamp.timeIntervalSinceNow) > 10 { return }
        batunSu += 1
        let heurim = l.horizontalAccuracy > 25
        if !heurim { majimakWiseong = Date() }
        // 걸음으로 이어 셈하는 중이면 흐린 위성으로 덮지 않음
        if heurim, let j = jigeum, j.georeumChu, Date().timeIntervalSince(j.ttae) < 10 { return }
        if majimakWiseong == nil { majimakWiseong = Date() }
        let bang = (l.course >= 0 && l.speed > 1.5) ? l.course : nachimban
        iegoGijun = georeumNujeok
        naegi(Wichi(lat: l.coordinate.latitude, lon: l.coordinate.longitude, ochae: l.horizontalAccuracy,
                    banghyang: bang, sokdo: max(0, l.speed), ttae: Date(), georeumChu: false))
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Girok.shared.namgi("wichi_oryu", ["code": (error as NSError).code])
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
                self.georeumNujeok = n - (self.georeumCheot ?? n)
            }
        }
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
                    sokdo: geori, ttae: Date(), georeumChu: true))
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
