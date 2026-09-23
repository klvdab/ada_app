// 위치 계속 받기 — 폰이 잠겨도 걷는 동안 이어집니다(UIBackgroundModes location).
import CoreLocation

final class LocationService: NSObject, CLLocationManagerDelegate {
    static let shared = LocationService()
    private let mgr = CLLocationManager()
    var onUpdate: ((Double, Double, Double, Double, Double) -> Void)?
    private var wantAlways = false
    private var running = false

    override init() {
        super.init()
        mgr.delegate = self
        mgr.desiredAccuracy = kCLLocationAccuracyBest
        mgr.distanceFilter = 1
        mgr.activityType = .fitness
        mgr.pausesLocationUpdatesAutomatically = false
        mgr.showsBackgroundLocationIndicator = true
    }

    func start(always: Bool) {
        wantAlways = always
        running = true
        switch mgr.authorizationStatus {
        case .notDetermined:
            mgr.requestWhenInUseAuthorization()
        case .authorizedWhenInUse:
            if always { mgr.requestAlwaysAuthorization() }
            begin()
        case .authorizedAlways:
            begin()
        default:
            break
        }
    }

    func stop() {
        running = false
        mgr.stopUpdatingLocation()
        mgr.stopUpdatingHeading()
        mgr.allowsBackgroundLocationUpdates = false
    }

    private func begin() {
        mgr.allowsBackgroundLocationUpdates = (mgr.authorizationStatus == .authorizedAlways)
        mgr.startUpdatingLocation()
        if CLLocationManager.headingAvailable() { mgr.startUpdatingHeading() }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard running else { return }
        if manager.authorizationStatus == .authorizedWhenInUse && wantAlways { manager.requestAlwaysAuthorization() }
        if manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways { begin() }
    }

    private var lastHeading: Double = -1
    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        lastHeading = newHeading.trueHeading >= 0 ? newHeading.trueHeading : newHeading.magneticHeading
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let l = locations.last else { return }
        let head = lastHeading >= 0 ? lastHeading : (l.course >= 0 ? l.course : -1)
        onUpdate?(l.coordinate.latitude, l.coordinate.longitude, l.horizontalAccuracy, head, max(l.speed, 0))
    }
}
