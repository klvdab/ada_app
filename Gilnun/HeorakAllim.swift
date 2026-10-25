// 2.110.0 (261025-I64, 이사장님 승인 2026-10-25 「1」) 위치·마이크 허락 알림과 설정 단추.
//
// 아이폰은 허락을 사람만 바꿀 수 있게 막아 두어, 길눈이 스스로 켤 수 없습니다.
// 그래서 길눈이 먼저 알려 드리고, 단추 하나로 아이폰 설정의 길눈 화면을 바로 엽니다.
//   - 처음 여실 때 위치가 「항상 허락」이 아니면 한 번 알려 드림
//   - 그 뒤로는 허락이 나빠졌을 때만(항상 → 쓰는 동안만 → 거절) 한 번 알려 드림. 같은 상태로 열 때마다 되풀이하지 않음
//   - 설정 탭 맨 위에 지금 상태를 이름에 담은 단추 두 개(위치, 마이크와 음성 인식)
// (10-25 저녁 위치가 「거절됨」인 채로 한 시간 길 안내를 하신 일 — 위치가 한 번도 들어오지 않았음)
import Foundation
import UIKit
import CoreLocation
import AVFoundation
import Speech

final class HeorakAllim: ObservableObject {
    static let shared = HeorakAllim()

    /// 화면에 띄울 알림 글(있으면 창을 띄움)
    @Published var allimGeul: String? = nil
    /// 아이폰 전체의 위치 서비스가 켜져 있는가(따로 살펴 둠 — 주 스레드에서 묻지 않음)
    @Published private(set) var wichiSeobiseu = true
    /// 설정에서 돌아오시면 단추 이름(마이크 허락 등)을 새로 읽게 함
    @Published private(set) var gaengsin = 0

    private let jeonKi = "gn.wichiHeorakJeon"
    private var salpimJung = false

    private init() {
        NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            // 설정에서 돌아오셨을 때 등 — 잠시 뒤 다시 살핌
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { self?.salpigi() }
        }
    }

    // MARK: 지금 상태

    /// 위치 허락을 말로 — 아이폰 위치 서비스가 꺼져 있으면 그것을 먼저
    var wichiMal: String {
        if !wichiSeobiseu { return "아이폰 위치 서비스 꺼짐" }
        return WichiEngine.shared.heorak
    }

    /// 높을수록 좋음: 항상 3, 쓰는 동안만 2, 아직 묻지 않음 1, 거절·막힘·꺼짐 0
    private func deunggeup(_ m: String) -> Int {
        switch m {
        case "항상 허락": return 3
        case "앱을 쓰는 동안만 허락": return 2
        case "아직 묻지 않음": return 1
        default: return 0
        }
    }

    var maikMal: String {
        let m = AVAudioSession.sharedInstance().recordPermission
        let s = SFSpeechRecognizer.authorizationStatus()
        let maik = m == .granted ? "켜짐" : (m == .denied ? "꺼짐" : "아직 묻지 않음")
        let deut = s == .authorized ? "켜짐" : (s == .notDetermined ? "아직 묻지 않음" : "꺼짐")
        return "마이크 \(maik), 음성 인식 \(deut)"
    }

    var maikDaOk: Bool { MalDeutgi.heorakItda }

    // MARK: 살피기

    /// 앱을 연 뒤와 앞으로 돌아올 때마다 — 알릴 일이 있으면 한 번 알림
    func salpigi() {
        guard !salpimJung else { return }
        salpimJung = true
        DispatchQueue.global(qos: .utility).async { [weak self] in
            let on = CLLocationManager.locationServicesEnabled()
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.salpimJung = false
                self.wichiSeobiseu = on
                self.gaengsin += 1
                self.pandan()
            }
        }
    }

    private func pandan() {
        let jigeum = wichiMal
        if jigeum == "아직 묻지 않음" { return }   // 아이폰이 곧 창을 띄워 여쭘
        let ud = UserDefaults.standard
        let jeon = ud.string(forKey: jeonKi)
        ud.set(jigeum, forKey: jeonKi)
        let d = deunggeup(jigeum)
        guard d < 3 else { return }
        // 처음이거나, 지난번보다 나빠졌을 때만
        if let jeon = jeon, deunggeup(jeon) <= d { return }
        let geul: String
        if d == 0 {
            geul = (jigeum == "아이폰 위치 서비스 꺼짐" ? "아이폰의 위치 서비스가 꺼져 있습니다." : "길눈의 위치 허락이 꺼져 있습니다.")
                + " 이대로는 지금 자리를 알 수 없어 길 안내를 할 수 없습니다."
                + " 설정 열기를 누르시면 아이폰 설정의 길눈 화면이 바로 열립니다. 위치를 누르고 항상을 골라 주십시오."
                + (jigeum == "아이폰 위치 서비스 꺼짐" ? " 위치 서비스는 설정의 개인정보 보호 및 보안, 위치 서비스에서 켜십니다." : "")
        } else {
            geul = "길눈의 위치 허락이 앱을 쓰는 동안만으로 되어 있습니다. 화면을 잠그거나 주머니에 넣어도 길 안내가 이어지려면 항상 허락이 필요합니다."
                + " 설정 열기를 누르시면 아이폰 설정의 길눈 화면이 바로 열립니다. 위치를 누르고 항상을 골라 주십시오."
        }
        Girok.shared.namgi("heorak_allim", ["wichi": jigeum, "jeon": jeon ?? ""])
        allimGeul = geul
        if !UIAccessibility.isVoiceOverRunning { SoriEngine.shared.mal(geul) }   // 보이스오버가 알림 창을 읽으므로 두 번 듣지 않게
    }

    // MARK: 위치 없이는 길 안내를 시작하지 않음

    /// 위치가 거절·막힘이거나 아이폰 위치 서비스가 꺼져 있으면 참 — 그때는 까닭과 설정 단추를 알리고 안내를 시작하지 않음
    /// (10-25 저녁 위치가 거절된 채 한 시간 길 안내가 돌며 땅속으로 잘못 짐작한 일)
    func annaeMakgi() -> Bool {
        guard WichiEngine.shared.geojeoldoem || !wichiSeobiseu else { return false }
        let m = "위치가 꺼져 있어 길 안내를 시작하지 않았습니다. 설정 열기를 누르시면 아이폰 설정의 길눈 화면이 바로 열립니다. 위치를 누르고 항상을 골라 주십시오."
        Girok.shared.namgi("annae_wichi_eopseum", ["heorak": wichiMal])
        allimGeul = m
        if !UIAccessibility.isVoiceOverRunning { SoriEngine.shared.mal(m) }   // 보이스오버가 알림 창을 읽으므로 두 번 듣지 않게
        return true
    }

    // MARK: 설정 열기

    /// 아이폰 설정의 길눈 화면을 엶. 쓰는 동안만 허락이면 아이폰이 「항상 허용」 창을 한 번은 띄워 줄 수 있어 먼저 그것을 청함
    func wichiSeoljeongYeolgi() {
        Girok.shared.namgi("heorak_seoljeong", ["jong": "wichi", "jigeum": wichiMal])
        if wichiSeobiseu, WichiEngine.shared.heorak == "앱을 쓰는 동안만 허락", !UserDefaults.standard.bool(forKey: "gn.hangsangCheongHam") {
            UserDefaults.standard.set(true, forKey: "gn.hangsangCheongHam")
            WichiEngine.shared.hangsangHeorakCheong()
            return
        }
        appSeoljeongYeolgi()
    }

    func maikSeoljeongYeolgi() {
        Girok.shared.namgi("heorak_seoljeong", ["jong": "maik", "jigeum": maikMal])
        let m = AVAudioSession.sharedInstance().recordPermission
        let s = SFSpeechRecognizer.authorizationStatus()
        if m == .undetermined || s == .notDetermined {
            MalDeutgi.heorak { ok in
                if ok { SoriEngine.shared.mal("마이크와 음성 인식을 허락하셨습니다.") } else { HeorakAllim.shared.appSeoljeongYeolgi() }
            }
            return
        }
        appSeoljeongYeolgi()
    }

    func appSeoljeongYeolgi() {
        if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) }
    }
}
