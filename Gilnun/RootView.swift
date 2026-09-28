// 첫 틀 — 탭 바 다섯 개. 모든 속 화면에도 탭 바가 늘 보입니다(탭마다 따로 길을 쌓음).
import SwiftUI

struct RootView: View {
    @ObservedObject private var tabGil = TabGil.shared   // 2.7.0 둘러보기에서 고른 곳으로 가면 길 찾기 탭으로

    var body: some View {
        TabView(selection: $tabGil.tab) {
            GilChatgiTab()
                .tabItem { Label("길 찾기", systemImage: "figure.walk") }
                .tag(0)
            DulreoTab()
            .tabItem { Label("둘러보기", systemImage: "binoculars") }
            .tag(1)
            BangsongTab()
            .tabItem { Label("음악·방송", systemImage: "music.note") }
            .tag(2)
            NavigationStack {
                JunbiView(mal: "나눔 — 1단계 기초판입니다. 나눔 마당, 걸음 나눔과 게시판, 길 부탁하기가 2단계에서 이 자리에 들어옵니다.")
            }
            .tabItem { Label("나눔", systemImage: "person.2") }
            .tag(3)
            NavigationStack { SeoljeongView() }
                .tabItem { Label("설정", systemImage: "gearshape") }
                .tag(4)
        }
        .tint(Saek.nam)
        // 보이스오버 두 손가락 두 번 두드리기 — 어느 화면에서나 말로 하기
        .accessibilityAction(.magicTap) { MalHagi.shared.dudeurim() }
    }
}

/// 2단계에서 채울 탭 — 한 줄 안내
struct JunbiView: View {
    let mal: String
    var body: some View {
        ScrollView {
            Text(mal)
                .font(.title3)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
        }
        .toolbar(.hidden, for: .navigationBar)
    }
}
