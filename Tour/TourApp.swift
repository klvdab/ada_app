// LVD투어스튜디오 앱 — 시작과 탭 (판 1.0.1, 빌드 261014-T2, 2026-10-14 이사장님 승인 「1」 — 사진 안내 시계 방향, 네 개씩 목록 「넷」, 도움말 뒤로 단추 자리. 1.0.0 261012-T1 첫 판)
// 웹 투어스튜디오(https://lvd.ada.or.kr/lvdts/)를 아이폰 속까지 앱으로. 같은 서버·같은 자료, 웹과 앱 어느 쪽에서 고쳐도 같습니다.
// 탭 다섯: 코스, 답사, 지점, 현장영상해설, 설정. 탭 바는 모든 속 화면에서도 늘 보이고, 뒤로 단추는 위에만 둡니다.
// 규칙: 목록은 다섯 줄씩(더 보기·이전 보기), 넘기면 커서는 첫 줄, 고치면 2초 뒤 저절로 저장, 새로고침은 설정에 하나.
import SwiftUI
import UIKit

enum Pan {
    static let pan = "1.4.0"      // 261018-T6 앱만 할 수 있는 것 둘째 — 말로 대본 쓰기(받아쓰기를 바로 대본으로), 이어폰 한 번 누름을 대본 받아쓰기로(방클, 이사장님 「1」)
    // 1.3.0 261018-T5 앱만 할 수 있는 것 — 이어폰 단추로 답사, 걸으며 저절로 재기, 판 기록(방클, 이사장님 승인 1·2·8번)
    // 1.2.0 261017-T4 웹에 있는 기능 모두(관리자 일, 내보내기·협회 승인, 원칙 16개, 평면도·답사 지도, 사전, 장소 지식, 승인본 현장영상해설 화면, 저절로 찍기 등) — 방클, 이사장님 지시 「웹에 있는 모든 기능을 겉과 속이 같게」
    // 1.1.0 261017-T3 웹에 있던 것 열네 가지(사진 지우기·다시 찍기·크게 보기, 지점 지우기, 현장 메모, 연습 녹음, 못 올린 것, 판 되돌리기, 휴지통 등) — 방클, 이사장님 승인
    static let bildeu = "261018-T6"
    static let ireum = "LVD투어스튜디오 \(pan)판 (빌드 \(bildeu))"
}

@main
struct TourApp: App {
    @StateObject private var store = TourStore.shared
    @State private var sijakham = false
    var body: some Scene {
        WindowGroup {
            Group {
                if store.loggedIn {
                    RootView()
                } else if !sijakham && !TourAPI.token.isEmpty {
                    ProgressView("들어오는 중입니다").accessibilityLabel("들어오는 중입니다")
                } else {
                    LoginView()
                }
            }
            .environmentObject(store)
            .task {
                await store.sijak()
                sijakham = true
            }
        }
    }
}

final class Nav: ObservableObject {
    @Published var tab = 0
    @Published var gen = [0, 0, 0, 0, 0]   // 지금 탭을 다시 누르면 그 탭 첫 화면 맨 위로
    func tabGo(_ t: Int, _ same: Bool) {
        if same && t >= 0 && t < gen.count { gen[t] += 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { UIAccessibility.post(notification: .screenChanged, argument: nil) }
    }
}

struct RootView: View {
    @StateObject private var nav = Nav()
    @ObservedObject private var danchu = Danchu.shared
    @EnvironmentObject var store: TourStore
    var body: some View {
        TabView(selection: Binding(get: { nav.tab }, set: { t in let same = (t == nav.tab); nav.tab = t; nav.tabGo(t, same); Gwan.shared.tab = t; Gwan.shared.ping(force: true) })) {
            NavigationStack { KosTab() }
                .id(nav.gen[0])
                .tabItem { Label("코스", systemImage: "map") }
                .tag(0)
            NavigationStack { DapsaTab() }
                .id(nav.gen[1])
                .tabItem { Label("답사", systemImage: "figure.walk") }
                .tag(1)
            NavigationStack { JijeomTab() }
                .id(nav.gen[2])
                .tabItem { Label("지점", systemImage: "mappin.and.ellipse") }
                .tag(2)
            NavigationStack { HaeseolTab() }
                .id(nav.gen[3])
                .tabItem { Label("현장영상해설", systemImage: "text.book.closed") }
                .tag(3)
            NavigationStack { SeoljeongTab() }
                .id(nav.gen[4])
                .tabItem { Label("설정", systemImage: "gearshape") }
                .tag(4)
        }
        .environmentObject(nav)
        .onAppear { TourOllim.shared.sijak(); Gwan.shared.pingSijak(); Danchu.shared.buchigi(); Task { await PanGirok.alrigi() } }
        .background(EmptyView().fullScreenCover(isPresented: $danchu.kameraYeolgi) { JeoljeoroView(auto: UserDefaults.standard.object(forKey: "camAuto") as? Bool ?? false).environmentObject(store) })
        .onChange(of: nav.tab) { _, t in Gwan.shared.tab = t; if t != 1 { Beoseonam.shared.meomchum() } }
        .fullScreenCover(isPresented: Binding(get: { (store.me?["mustChange"] as? Bool) == true }, set: { _ in })) {
            NavigationStack { PwView(gangje: true) }.environmentObject(store)
        }
    }
}

extension View {
    /// 뒤로 단추는 탭 바가 있으므로 위에만 둔다. 보이스오버 두 손가락 문지르기로도 뒤로 간다.
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

/// 다섯 줄씩 보이는 목록 — 더 보기·이전 보기, 넘기면 커서는 첫 줄로
struct Jang<T: Identifiable, Row: View>: View {
    let items: [T]
    var per: Int = 5
    @ViewBuilder let row: (T) -> Row
    @State private var page = 0
    @AccessibilityFocusState private var cheot: Bool
    var body: some View {
        let n = max(1, (items.count + per - 1) / per)
        let p = min(page, n - 1)
        let part = Array(items.dropFirst(p * per).prefix(per))
        ForEach(Array(part.enumerated()), id: \.element.id) { i, x in
            if i == 0 { row(x).accessibilityFocused($cheot) } else { row(x) }
        }
        if p + 1 < n {
            Button("더 보기 (다음 \(seum(per)), \(p + 2)/\(n))") { page = p + 1; jjik() }
        }
        if p > 0 {
            Button("이전 보기 (앞의 \(seum(per)))") { page = p - 1; jjik() }
        }
    }
    private func jjik() { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { cheot = true } }
    private func seum(_ k: Int) -> String { k == 4 ? "넷" : (k == 5 ? "다섯" : "\(k)개") }
}

/// 지점 경계 규칙 — 잊지 않도록 답사·지점 탭 맨 위에 늘 보임
struct GyeonggyeJul: View {
    var body: some View {
        Text(RULE_TXT)
            .font(.callout.weight(.semibold))
            .foregroundStyle(Saek.ganjo)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityAddTraits(.isStaticText)
    }
}

/// 코스가 열려 있지 않을 때
struct KosEopseum: View {
    @EnvironmentObject var nav: Nav
    var body: some View {
        Section {
            Text("아직 연 코스가 없습니다. 코스 탭에서 코스를 열어 주십시오.")
            Button("코스 탭으로 가기") { nav.tab = 0 }
        }
    }
}

/// 안내가 바뀌면 커서가 그 줄로 가서 읽음
struct AllimJul: View {
    @EnvironmentObject var store: TourStore
    @AccessibilityFocusState private var f: Bool
    var body: some View {
        if !store.allim.isEmpty {
            Text(store.allim)
                .font(.callout)
                .accessibilityFocused($f)
                .onChange(of: store.allim) { _, _ in if !store.malsori { DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { f = true } } }
        }
    }
}
