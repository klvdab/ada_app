// AI점자도서관 앱 — 시작과 탭 (판 0.1.0, 빌드 260930-1)
// 탭 셋: 도서관, 내 서재, 설정·도움말. 탭 바는 모든 속 화면에서도 늘 보인다.
import SwiftUI
import AVFoundation

@main
struct LibApp: App {
    @StateObject private var store = Store.shared
    @StateObject private var reader = Reader.shared
    @StateObject private var offline = Offline.shared
    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(reader)
                .environmentObject(offline)
        }
    }
}

enum Route: Hashable {
    case list(String)
    case jakbon(String, String)
    case find(String)
    case book(Int)
    case reader(Int, String, String)
    case marks(Int)
}

final class Nav: ObservableObject {
    @Published var lib = NavigationPath()
    @Published var seojae = NavigationPath()
    @Published var tab = 0
}

struct RootView: View {
    @StateObject private var nav = Nav()
    var body: some View {
        TabView(selection: $nav.tab) {
            NavigationStack(path: $nav.lib) {
                HomeView().routes()
            }
            .tabItem { Label("도서관", systemImage: "books.vertical") }
            .tag(0)
            NavigationStack(path: $nav.seojae) {
                SeojaeView().routes()
            }
            .tabItem { Label("내 서재", systemImage: "bookmark") }
            .tag(1)
            NavigationStack {
                SettingsView()
            }
            .tabItem { Label("설정·도움말", systemImage: "gearshape") }
            .tag(2)
        }
        .environmentObject(nav)
    }
}

extension View {
    func routes() -> some View {
        navigationDestination(for: Route.self) { r in
            switch r {
            case .list(let g): GalListView(g: g).dwiro()
            case .jakbon(let j, let t): JakbonView(j: j, ttl: t).dwiro()
            case .find(let s): FindView(s: s).dwiro()
            case .book(let i): BookView(i: i).dwiro()
            case .reader(let i, let t, let k): ReaderView(i: i, title: t, kind: k).dwiro()
            case .marks(let i): MarksView(i: i).dwiro()
            }
        }
    }
    /// 뒤로 단추는 탭 바가 있으므로 위에만 둔다. 두 손가락 문지르기(보이스오버)로도 뒤로 간다.
    func dwiro() -> some View { modifier(Dwiro()) }
}

struct Dwiro: ViewModifier {
    @Environment(\.dismiss) private var dismiss
    func body(content: Content) -> some View {
        content
            .navigationBarBackButtonHidden(true)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("뒤로") { dismiss() }
                }
            }
            .accessibilityAction(.escape) { dismiss() }
    }
}
