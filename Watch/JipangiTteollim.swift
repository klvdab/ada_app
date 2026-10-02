// 지팡이 떨림 기록 — 길눈 2.37.0 (빌드 261002-5, 대표님 승인 2026-10-02) — 연구 1단계(기록 모으기)
// 워치를 지팡이 쥔 손에 차셨을 때, 지팡이 끝이 바닥을 쓸고 두드리는 떨림이 손목까지 옵니다.
// 그 떨림으로 점자블록·보도블록·아스팔트 같은 바닥을 가려낼 수 있는지 알아보려고, 먼저 바닥 이름을 붙인 기록을 모읍니다.
//   1) 워치에서 바닥 종류를 고르면 1초에 100번 손목 흔들림(가속도 세 축 + 돌림 세 축)을 담기 시작
//   2) 그만(두 번 집기로도) 누르거나 5분이 되면 멈추고, 기록을 폰 길눈으로 보냄 → 폰이 협회 나스(연구용)로 올림
//   폰이 곁에 없어도 워치가 들고 있다가 이어지면 넘김(파일 보내기는 아이폰이 맡아 이어 줌)
// 다음 단계(모인 기록으로 가려내기 셈법을 만든 뒤): 걷는 중 "점자블록입니다"를 알려 드리기
import Foundation
import CoreMotion
import WatchConnectivity
import WatchKit

final class JipangiTteollim: ObservableObject {
    static let shared = JipangiTteollim()
    static let badakdeul = ["점자블록", "보도블록", "아스팔트", "흙길", "그 밖의 바닥"]
    static let HZ = 100.0
    static let CHOEDAE = 300.0   // 한 번에 5분까지

    @Published private(set) var damneunJung = false
    @Published private(set) var pyo = ""
    @Published private(set) var cho = 0
    private let umjik = CMMotionManager()
    private var beop = Data()
    private var sijakT = Date()
    private var sigye: Timer?

    /// 워치를 지팡이 쥔 손에 차셨는가
    var jipangiSon: Bool {
        let j = UserDefaults.standard.string(forKey: "jipangiSon") ?? ""
        let w = UserDefaults.standard.string(forKey: "watchSonmok") ?? ""
        return !j.isEmpty && j == w
    }

    func sijak(_ badak: String) {
        guard !damneunJung else { return }
        guard umjik.isDeviceMotionAvailable else {
            WatchModel.shared.speak("이 워치에서는 떨림을 잴 수 없습니다.", jindong: .failure); return
        }
        pyo = badak; cho = 0
        beop = Data(); beop.reserveCapacity(Int(JipangiTteollim.HZ * JipangiTteollim.CHOEDAE) * 28)
        sijakT = Date()
        damneunJung = true
        WatchModel.shared.kkaeeoBojang(true)   // 손목을 내려도 멈추지 않게
        WatchModel.shared.speak("\(badak) 기록을 시작합니다. 평소처럼 지팡이를 쓰며 걸으십시오.", jindong: .start)
        umjik.deviceMotionUpdateInterval = 1.0 / JipangiTteollim.HZ
        umjik.startDeviceMotionUpdates(to: OperationQueue()) { [weak self] d, _ in
            guard let self = self, let d = d else { return }
            let t = Float(Date().timeIntervalSince(self.sijakT))
            let a = d.userAcceleration, g = d.rotationRate
            let v: [Float] = [t, Float(a.x), Float(a.y), Float(a.z), Float(g.x), Float(g.y), Float(g.z)]
            let bytes = v.withUnsafeBufferPointer { Data(buffer: $0) }
            DispatchQueue.main.async { if self.damneunJung { self.beop.append(bytes) } }
        }
        sigye = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            self.cho = Int(Date().timeIntervalSince(self.sijakT))
            if Double(self.cho) >= JipangiTteollim.CHOEDAE { self.geuman() }
        }
    }

    func geuman() {
        guard damneunJung else { return }
        damneunJung = false
        umjik.stopDeviceMotionUpdates()
        sigye?.invalidate(); sigye = nil
        WatchModel.shared.kkaeeoBojang(false)
        let gil = Int(Date().timeIntervalSince(sijakT))
        guard gil >= 5, beop.count > 28 * 100 else {
            WatchModel.shared.speak("기록이 너무 짧아 버렸습니다. 5초 넘게 걸어 주십시오.", jindong: .failure); return
        }
        let d = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("tteollim", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        let f = d.appendingPathComponent("tteollim_\(Int(sijakT.timeIntervalSince1970)).bin")
        do { try beop.write(to: f, options: .atomic) } catch {
            WatchModel.shared.speak("기록을 담지 못했습니다.", jindong: .failure); return
        }
        beop = Data()
        let son = UserDefaults.standard.string(forKey: "watchSonmok") ?? ""
        if WCSession.isSupported(), WCSession.default.activationState == .activated {
            WCSession.default.transferFile(f, metadata: ["what": "tteollim", "pyo": pyo, "cho": gil, "son": son, "hz": Int(JipangiTteollim.HZ)])
        }
        WatchModel.shared.speak("\(pyo) 기록 \(gil / 60)분 \(gil % 60)초를 담아 폰 길눈으로 보냅니다.", jindong: .success)
    }

    /// 폰에 다 넘어간 기록은 워치에서 지움(워치 저장 공간이 작음)
    func neomeoganGeotJiugi(_ f: URL) {
        try? FileManager.default.removeItem(at: f)
    }
}
