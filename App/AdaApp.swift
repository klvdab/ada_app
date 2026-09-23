// 협회 앱 — 아이폰 (0.1.0판, 빌드 260909-1)
// 나스의 웹(길눈·배프 등)을 안에 담고, 웹이 못 하는 손발(위치 계속 받기·알림·진동·기기 단추·워치)을 붙입니다.
import SwiftUI

@main
struct AdaApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(edges: .bottom)
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        NotificationService.shared.setup()
        WatchLink.shared.activate()
        RemoteCommandService.shared.activate()
        return true
    }
}
