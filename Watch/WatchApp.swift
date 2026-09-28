// 길눈 워치 (2.6.0판, 빌드 260928-8) — 손목에서 말로 하기, 내 자리·다음 갈림길·마지막 안내를 듣고, 진동으로 방향을 받고, 음향신호기를 울립니다.
// 2.6.0 새 길눈 앱(속까지 앱)과 다시 이음, 말로 하기 단추 더함
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
