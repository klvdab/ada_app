// AI점자도서관 앱 — 시작과 탭 (판 0.1.0, 빌드 260930-1)
// 탭 셋: 도서관, 내 서재, 설정·도움말. 탭 바는 모든 속 화면에서도 늘 보인다.
import SwiftUI
import UIKit
import AVFoundation

@main
struct LibApp: App {
    @StateObject private var store = Store.shared
    @StateObject private var reader = Reader.shared
    @StateObject private var offline = Offline.shared
    @StateObject private var hoewon = Hoewon.shared
    var body: some Scene {
        WindowGroup {
            if hoewon.deungrokdoem {
                RootView()
                    .environmentObject(store)
                    .environmentObject(reader)
                    .environmentObject(offline)
            } else {
                DeungrokView()   // 처음 켤 때 한 번만 나오는 회원 등록 화면
            }
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
    case mun(String, String)              // 0.3.0 세 겹의 문
    case seoga(String, String, String)    // 0.3.0 서가
}

final class Nav: ObservableObject {
    @Published var lib = NavigationPath()
    @Published var seojae = NavigationPath()
    @Published var tab = 0
    @Published var gen = [0, 0, 0]   // 0.3.1 — 탭을 고를 때마다 그 탭 화면을 새로 그려 맨 위에서 시작
    func tabGo(_ t: Int, _ same: Bool) {
        if same { if t == 0 { lib = NavigationPath() } else if t == 1 { seojae = NavigationPath() } }
        if t >= 0 && t < 3 { gen[t] += 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { UIAccessibility.post(notification: .screenChanged, argument: nil) }
    }
}

struct RootView: View {
    @StateObject private var nav = Nav()
    var body: some View {
        TabView(selection: Binding(get: { nav.tab }, set: { t in let same = (t == nav.tab); nav.tab = t; nav.tabGo(t, same) })) {   // 0.3.0 — 지금 탭을 다시 누르면 그 탭 첫 화면 맨 위로
            NavigationStack(path: $nav.lib) {
                HomeView().routes()
            }
            .id(nav.gen[0])
            .tabItem { Label("도서관", systemImage: "books.vertical") }
            .tag(0)
            NavigationStack(path: $nav.seojae) {
                SeojaeView().routes()
            }
            .id(nav.gen[1])
            .tabItem { Label("내 서재", systemImage: "bookmark") }
            .tag(1)
            NavigationStack {
                SettingsView()
            }
            .id(nav.gen[2])
            .tabItem { Label("설정·도움말", systemImage: "gearshape") }
            .tag(2)
        }
        .environmentObject(nav)
        .onAppear { LibOllim.shared.sijak() }   // 0.4.3 새 판 알림(대장클, 이사장님 지시)
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
            case .reader(let i, let t, let k): ReaderView(i: i, title: t, kind: k).dwiro().onDisappear { Reader.shared.pause() }   // 0.3.1 — 독서기 화면을 떠나면 멈춤
            case .marks(let i): MarksView(i: i).dwiro()
            case .mun(let m, let t): MunView(mun: m, ttl: t).dwiro()
            case .seoga(let m, let k, let t): SeogaView(mun: m, k: k, ttl: t).dwiro()
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
