// 첫 틀 — 탭 바 다섯 개. 모든 속 화면에도 탭 바가 늘 보입니다(탭마다 따로 길을 쌓음).
import SwiftUI

struct RootView: View {
    @State private var tab = 0

    var body: some View {
        TabView(selection: $tab) {
            GilChatgiTab()
                .tabItem { Label("길 찾기", systemImage: "figure.walk") }
                .tag(0)
            NavigationStack {
                JunbiView(mal: "둘러보기 — 1단계 기초판입니다. 둘레 찾기, 가 볼 곳, 축제, 사진 읽어 주기 들이 2단계에서 이 자리에 들어옵니다.")
            }
            .tabItem { Label("둘러보기", systemImage: "binoculars") }
            .tag(1)
            NavigationStack {
                JunbiView(mal: "음악·방송 — 1단계 기초판입니다. 길 위의 음악, 라디오, TV, 지금 세상 이야기가 2단계에서 이 자리에 들어옵니다.")
            }
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
