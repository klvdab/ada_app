// 자봉 앱 몸 센서 — 점지도 센서 극대화 (2.3.0, 빌드 261001-13, 대표님 지시 2026-10-01)
// ★대표님: 가장 중요한 것은 걸으면서 찍어 주는 점지도. 몸이 움직이는 상황을 담는 센서를 최대한 끌어내 쓸 것.
// 폰이 가진 몸 센서를 1초에 50번 읽어 걸음마다 한 줄씩 남깁니다.
//   가속도계   — 발이 땅에 닿는 순간마다 한 걸음(만보기보다 빠르고, 느린 걸음·지팡이 걸음도 잡도록 문턱을 스스로 맞춤)
//   자이로     — 몸이 돈 각도(쇠붙이·건물 옆에서도 틀어지지 않음). 오른쪽으로 돌면 +, 왼쪽은 -
//   나침반     — 자이로가 오래 지나 조금씩 밀리는 것을 천천히 바로잡음(합성 방향)
//   기압계     — 그리기 화면이 이미 잼(ralt). 걸음 줄에 그때 높이를 붙임
//   움직임 상태 — 아이폰이 가려 주는 멈춤·걷기·탈것
//   만보기     — 아이폰 걸음 세기의 걸음 빠르기·오른 층·내린 층을 대조용으로 함께
// 모든 값은 기기 시계(부팅 뒤 흐른 시간)로 천분의 1초까지 같은 줄 위에 맞춥니다.
// 기록 모양은 안드로이드와 똑같이 따를 "센서 기록 규격"(점지도_폰센서_비교표_261001) 3번 표를 따릅니다.
import Foundation
import CoreMotion
import UIKit

/// 걸음마다 한 줄 — 센서 기록 규격
struct GrGeoreum: Codable {
    var n: Int            // 가속도로 센 걸음 번호(1부터)
    var ms: Int           // 그리기 시작부터 흐른 시간(천분의 1초)
    var hy: Double?       // 그 걸음의 합성 방향(자북 기준, 0~360도)
    var dol: Double       // 앞 걸음에서 돈 각도(자이로, 오른쪽 +, 도)
    var chung: Double     // 그 걸음의 위아래 충격 크기(중력 단위 g)
    var ralt: Double?     // 그때 상대 높이(미터)
    var sa: String?       // 움직임 상태: 걷기·멈춤·탈것·뜀
}

/// 기기 정보 — 기종마다 센서가 다르므로 함께 남김
struct GrGigi: Codable {
    var momo: String      // 기종 번호(예: iPhone15,3)
    var os: String        // 운영체제와 판
    var sensor: [String: Bool]
    var hz: Int           // 몸 센서를 읽은 빠르기(1초에 몇 번)
    var tteul: String     // 기록 규격 판
}

final class MomSensor {
    static let shared = MomSensor()
    static let HZ = 50
    static let GYUGYEOK = "센서기록규격 1.0 (261001)"

    private let mm = CMMotionManager()
    private let hwaldong = CMMotionActivityManager()
    private let manbo = CMPedometer()
    private let jul: OperationQueue = {
        let q = OperationQueue(); q.maxConcurrentOperationCount = 1; q.name = "kr.or.ada.jabong.momsensor"; return q
    }()

    // 밖에서 읽는 값(어느 줄에서나 안전하게 — 자물쇠로 지킴)
    private let jamulsoe = NSLock()
    private var _georeumSu = 0
    private var _hapseong: Double? = nil      // 합성 방향
    private var _nujeokDol: Double = 0        // 시작부터 돈 각도의 누계(오른쪽 +)
    private var _sangtae: String? = nil
    private var _cadence: Double? = nil       // 만보기 걸음 빠르기(1초에 몇 걸음)
    private var _cheung: (Int, Int) = (0, 0)  // 오른 층, 내린 층
    private var _dollyeo = false

    var georeumSu: Int { jamulsoe.lock(); defer { jamulsoe.unlock() }; return _georeumSu }
    var hapseong: Double? { jamulsoe.lock(); defer { jamulsoe.unlock() }; return _hapseong }
    var nujeokDol: Double { jamulsoe.lock(); defer { jamulsoe.unlock() }; return _nujeokDol }
    var sangtae: String? { jamulsoe.lock(); defer { jamulsoe.unlock() }; return _sangtae }
    var cadence: Double? { jamulsoe.lock(); defer { jamulsoe.unlock() }; return _cadence }
    var cheung: (Int, Int) { jamulsoe.lock(); defer { jamulsoe.unlock() }; return _cheung }
    var dollyeo: Bool { jamulsoe.lock(); defer { jamulsoe.unlock() }; return _dollyeo }

    /// 걸음 한 줄이 생길 때마다 부름(주 줄에서)
    var georeumNal: ((GrGeoreum) -> Void)?
    /// 그때 높이를 물을 곳(그리기 화면의 기압계 값)
    var nopiMutgi: (() -> Double?)?
    /// 나침반 값(0~360, 없으면 음수) — 그리기 화면이 1초마다 넣어 줌(주 줄의 값을 센서 줄에서 바로 읽지 않게)
    private var _nachimban: Double = -1
    func nachimbanNeogi(_ v: Double) { jamulsoe.lock(); _nachimban = v; jamulsoe.unlock() }

    // 걸음 찾기 셈
    private var sijakUptime: TimeInterval = 0
    private var lp: Double = 0                 // 위아래 가속도(낮은 떨림만 남김)
    private var lpAp: Double = 0, lpApAp: Double = 0
    private var gotgolMin: Double = 0          // 앞 걸음 뒤 가장 낮은 값(골)
    private var majimakGeoreum: TimeInterval = 0
    private var bongDeul: [Double] = []        // 최근 걸음 봉우리 높이(문턱 맞추기)
    private var saiDeul: [Double] = []         // 최근 걸음 사이 시간(한 걸음에 봉우리가 둘 잡히지 않게)
    private var chungMax: Double = 0           // 이 걸음 사이 가장 큰 위아래 충격
    private var dolGeoreumSai: Double = 0      // 앞 걸음 뒤 돈 각도
    private var majimakSaemple: TimeInterval = 0

    private init() {}

    // MARK: 켜고 끄기

    /// 그리기를 시작하거나 이어 그릴 때. n0 은 이미 센 걸음, dol0 은 이미 돈 각도, heureun 은 그리기 시작부터 흐른 초(이어 그리기)
    func kyeogi(n0: Int = 0, dol0: Double = 0, heureun: TimeInterval = 0) {
        jamulsoe.lock()
        if _dollyeo { jamulsoe.unlock(); return }
        _dollyeo = true
        _georeumSu = n0
        _nujeokDol = dol0
        jamulsoe.unlock()
        sijakUptime = ProcessInfo.processInfo.systemUptime - max(0, heureun)
        lp = 0; lpAp = 0; lpApAp = 0; gotgolMin = 0; majimakGeoreum = 0; bongDeul = []; saiDeul = []; chungMax = 0; dolGeoreumSai = 0; majimakSaemple = 0

        if mm.isDeviceMotionAvailable {
            mm.deviceMotionUpdateInterval = 1.0 / Double(MomSensor.HZ)
            let teul: CMAttitudeReferenceFrame = CMMotionManager.availableAttitudeReferenceFrames().contains(.xMagneticNorthZVertical)
                ? .xMagneticNorthZVertical : .xArbitraryCorrectedZVertical
            mm.startDeviceMotionUpdates(using: teul, to: jul) { [weak self] dm, _ in
                guard let self = self, let dm = dm else { return }
                self.saemple(dm)
            }
        }
        if CMMotionActivityManager.isActivityAvailable() {
            hwaldong.startActivityUpdates(to: jul) { [weak self] a in
                guard let self = self, let a = a, a.confidence != .low else { return }
                let s: String? = a.automotive ? "탈것" : a.cycling ? "탈것" : a.running ? "뜀" : a.walking ? "걷기" : a.stationary ? "멈춤" : nil
                self.jamulsoe.lock(); self._sangtae = s; self.jamulsoe.unlock()
            }
        }
        if CMPedometer.isCadenceAvailable() || CMPedometer.isFloorCountingAvailable() {
            manbo.startUpdates(from: Date()) { [weak self] d, _ in
                guard let self = self, let d = d else { return }
                self.jamulsoe.lock()
                if let c = d.currentCadence { self._cadence = (c.doubleValue * 100).rounded() / 100 }
                self._cheung = (d.floorsAscended?.intValue ?? 0, d.floorsDescended?.intValue ?? 0)
                self.jamulsoe.unlock()
            }
        }
    }

    /// 잠깐 멈춤이나 다 걸었을 때
    func kkeugi() {
        jamulsoe.lock(); let on = _dollyeo; _dollyeo = false; jamulsoe.unlock()
        guard on else { return }
        mm.stopDeviceMotionUpdates()
        hwaldong.stopActivityUpdates()
        manbo.stopUpdates()
    }

    // MARK: 한 번 읽을 때마다(1초에 50번)

    private func saemple(_ dm: CMDeviceMotion) {
        let t = dm.timestamp
        let dt = majimakSaemple > 0 ? min(0.1, max(0.0, t - majimakSaemple)) : 1.0 / Double(MomSensor.HZ)
        majimakSaemple = t

        // 중력 쪽(아래)과 위아래 가속도 — 폰을 어떻게 들든 몸의 위아래로 맞춤
        let g = dm.gravity, a = dm.userAcceleration, r = dm.rotationRate
        let gk = max(0.0001, sqrt(g.x * g.x + g.y * g.y + g.z * g.z))
        let wiArae = -(a.x * g.x + a.y * g.y + a.z * g.z) / gk          // 위쪽이 +, 중력 단위
        // 몸이 도는 빠르기(땅에 수직인 축으로) — 오른쪽 + 가 되게 부호를 바꿈
        let doneun = (r.x * g.x + r.y * g.y + r.z * g.z) / gk             // 라디안/초, 위에서 보아 오른쪽(시계 방향)이 +
        let dolDo = doneun * dt * 180 / Double.pi

        jamulsoe.lock()
        _nujeokDol += dolDo
        // 합성 방향 — 자이로로 돌리고, 나침반 쪽으로 아주 조금씩 당김(쇠붙이 옆에서 잠깐 틀려도 끌려가지 않게)
        if var h = _hapseong {
            h = (h + dolDo).truncatingRemainder(dividingBy: 360); if h < 0 { h += 360 }
            let nc = _nachimban
            if nc >= 0 {
                var cha = (nc - h).truncatingRemainder(dividingBy: 360)
                if cha > 180 { cha -= 360 }; if cha < -180 { cha += 360 }
                if abs(cha) < 60 { h += cha * 0.01 }           // 크게 다르면 나침반이 흔들린 것으로 보고 따르지 않음
                h = h.truncatingRemainder(dividingBy: 360); if h < 0 { h += 360 }
            }
            _hapseong = h
        } else if _nachimban >= 0 {
            _hapseong = _nachimban
        }
        jamulsoe.unlock()
        dolGeoreumSai += dolDo
        chungMax = max(chungMax, abs(wiArae))

        // 걸음 찾기 — 위아래 가속도를 부드럽게 한 뒤 봉우리를 찾음
        lpApAp = lpAp; lpAp = lp
        lp += (wiArae - lp) * 0.3
        gotgolMin = min(gotgolMin, lp)
        let pyeong = bongDeul.isEmpty ? 0.12 : bongDeul.reduce(0, +) / Double(bongDeul.count)
        let munteok = max(0.03, pyeong * 0.4)             // 느린 걸음·지팡이 걸음은 문턱이 낮아지고, 서 있을 때 흔들림만으로는 넘지 않게
        let bongwuri = lpAp > lpApAp && lpAp >= lp && lpAp > munteok
        let sai = t - majimakGeoreum
        // 한 걸음 안에 봉우리가 둘 잡히지 않게 — 최근 걸음 사이 시간의 절반 남짓은 쉼(시뮬레이션으로 맞춘 값)
        var swim = 0.28
        if saiDeul.count >= 4 { let j = saiDeul.sorted(); swim = max(0.28, min(1.0, j[j.count / 2] * 0.55)) }
        guard bongwuri, majimakGeoreum == 0 || sai >= swim else { return }
        let nopi = lpAp - gotgolMin
        guard nopi > max(0.05, pyeong * 0.5) else { return }
        if majimakGeoreum > 0 && sai < 2.0 { saiDeul.append(sai); if saiDeul.count > 8 { saiDeul.removeFirst() } }

        majimakGeoreum = t
        gotgolMin = lpAp
        bongDeul.append(lpAp); if bongDeul.count > 12 { bongDeul.removeFirst() }

        jamulsoe.lock()
        _georeumSu += 1
        let n = _georeumSu, hy = _hapseong, sa = _sangtae
        jamulsoe.unlock()
        let gr = GrGeoreum(n: n, ms: Int(((t - sijakUptime) * 1000).rounded()),
                           hy: hy.map { ($0 * 10).rounded() / 10 },
                           dol: (dolGeoreumSai * 10).rounded() / 10,
                           chung: (chungMax * 1000).rounded() / 1000,
                           ralt: nil, sa: sa)
        dolGeoreumSai = 0
        chungMax = 0
        DispatchQueue.main.async { [weak self] in
            var g2 = gr
            if let r = self?.nopiMutgi?() { g2.ralt = (r * 10).rounded() / 10 }
            self?.georeumNal?(g2)
        }
    }

    // MARK: 기기 정보

    static func gigiJeongbo() -> GrGigi {
        var u = utsname(); uname(&u)
        let momo = withUnsafePointer(to: &u.machine) { $0.withMemoryRebound(to: CChar.self, capacity: 1) { String(cString: $0) } }
        let mm = CMMotionManager()
        let s: [String: Bool] = [
            "가속도": mm.isAccelerometerAvailable,
            "자이로": mm.isGyroAvailable,
            "자력계": mm.isMagnetometerAvailable,
            "합성방향": mm.isDeviceMotionAvailable,
            "자북기준": CMMotionManager.availableAttitudeReferenceFrames().contains(.xMagneticNorthZVertical),
            "만보기": CMPedometer.isStepCountingAvailable(),
            "걸음빠르기": CMPedometer.isCadenceAvailable(),
            "층": CMPedometer.isFloorCountingAvailable(),
            "기압계": CMAltimeter.isRelativeAltitudeAvailable(),
            "움직임상태": CMMotionActivityManager.isActivityAvailable()
        ]
        return GrGigi(momo: momo, os: "iOS " + UIDevice.current.systemVersion, sensor: s, hz: HZ, tteul: GYUGYEOK)
    }
}
