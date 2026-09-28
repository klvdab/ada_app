// 길눈 앱 — 속까지 앱으로 다시 짓는 판 (2.0.0, 빌드 260928-1)
// 2026-09-28 이사장님 지시: 껍데기 앱은 다시 만들지 않는다. 제로베이스에서 기초부터 세우고 그 위에 기능을 단다.
// 이 판은 1단계 기초판입니다. 앱의 틀, 소리 엔진, 위치 엔진, 여정 엔진, 통신과 저장, 기록을 세웠습니다.
// 설계도: 나스 /test/ada_app/길눈앱_설계도_260928.txt
import SwiftUI
import UIKit
import BackgroundTasks

@main
struct GilnunApp: App {
    @UIApplicationDelegateAdaptor(GilnunAppDelegate.self) var delegate
    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

final class GilnunAppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // 2.11.2 앱을 닫아 두어도 아이폰이 틈틈이 길눈을 깨워 긴급 공지를 살핌 — 등록은 켜지는 이 자리에서만 할 수 있음
        BGTaskScheduler.shared.register(forTaskWithIdentifier: GongjiEngine.dwiId, using: nil) { t in
            if let r = t as? BGAppRefreshTask { GongjiEngine.shared.dwiSalpim(r) } else { t.setTaskCompleted(success: false) }
        }
        Bonche.shared.sijak()
        return true
    }
}
