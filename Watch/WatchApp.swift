// 길눈 워치 (0.2.0판, 빌드 260927-3) — 손목에서 내 자리·다음 갈림길·마지막 안내를 듣고, 진동으로 방향을 받고, 음향신호기를 울립니다.
import SwiftUI

@main
struct AdaWatchApp: App {
    @StateObject private var model = WatchModel.shared
    var body: some Scene {
        WindowGroup {
            WatchView().environmentObject(model)
        }
    }
}
