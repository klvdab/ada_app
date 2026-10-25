// 첫 틀 — 탭 바 다섯 개. 모든 속 화면에도 탭 바가 늘 보입니다(탭마다 따로 길을 쌓음).
import SwiftUI

struct RootView: View {
    @ObservedObject private var tabGil = TabGil.shared   // 2.7.0 둘러보기에서 고른 곳으로 가면 길 찾기 탭으로
    @ObservedObject private var dongyeong = Dongyeong.shared   // 2.34.0 동영상 받아 틀기
    @ObservedObject private var heorak = HeorakAllim.shared   // 2.110.0 위치 허락 알림

    var body: some View {
        // 2.103.0 (261021-I57, 이사장님 승인 2026-10-21 「그래」) 탭 바에서 탭을 고르시면 그 탭의 맨 위 첫 줄로 커서를 보냄 — 같은 탭을 다시 고르셔도.
        //   iOS 27에서 탭을 바꿔도 화면이 새로 열리지 않아(onAppear 가 불리지 않아) 커서가 따라가지 않던 일(10-21)
        TabView(selection: Binding(get: { tabGil.tab }, set: { v in TabGil.shared.goreum(v) })) {
            GilChatgiTab()
                .environment(\.tabBeon, 0)   // 2.108.0 이 탭의 번호 — 탭을 고르면 보이는 화면이 첫 줄로
                .tabItem { Label("길 찾기", systemImage: "figure.walk") }
                .tag(0)
            DulreoTab()
            .environment(\.tabBeon, 1)   // 2.108.0 이 탭의 번호 — 탭을 고르면 보이는 화면이 첫 줄로
            .tabItem { Label("둘러보기", systemImage: "binoculars") }
            .tag(1)
            BangsongTab()
            .environment(\.tabBeon, 2)   // 2.108.0 이 탭의 번호 — 탭을 고르면 보이는 화면이 첫 줄로
            .tabItem { Label("음악·방송", systemImage: "music.note") }
            .tag(2)
            NanumTab()
            .environment(\.tabBeon, 3)   // 2.108.0 이 탭의 번호 — 탭을 고르면 보이는 화면이 첫 줄로
            .tabItem { Label("나눔", systemImage: "person.2") }
            .tag(3)
            NavigationStack { SeoljeongView() }
                .environment(\.tabBeon, 4)   // 2.108.0 이 탭의 번호 — 탭을 고르면 보이는 화면이 첫 줄로
                .tabItem { Label("설정", systemImage: "gearshape") }
                .tag(4)
        }
        .tint(Saek.nam)
        // 보이스오버 두 손가락 두 번 두드리기 — 어느 화면에서나 말로 하기
        .accessibilityAction(.magicTap) { MalHagi.shared.dudeurim() }
        // 2.34.0 다른 앱에서 "공유 → 길눈"으로 넘긴 동영상 — 워치가 있으면 워치로, 없으면 여기서 온 화면으로 틂
        .onOpenURL { u in Dongyeong.shared.batda(u) }
        .fullScreenCover(item: $dongyeong.ponJaesaeng) { g in DongyeongJaesaengView(url: g.url) }
        // 2.110.0 (261025-I64, 이사장님 승인 2026-10-25) 위치 허락이 모자라면 처음과 나빠졌을 때 한 번 알리고 설정을 바로 여는 단추
        .alert("위치 허락", isPresented: Binding(get: { heorak.allimGeul != nil }, set: { if !$0 { heorak.allimGeul = nil } })) {
            Button("설정 열기") { heorak.allimGeul = nil; HeorakAllim.shared.wichiSeoljeongYeolgi() }
            Button("나중에", role: .cancel) { heorak.allimGeul = nil }
        } message: {
            Text(heorak.allimGeul ?? "")
        }
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 4) { HeorakAllim.shared.salpigi() } }
        // 2.52.0 처음 안내는 화면 전체를 덮지 않음 — 길 찾기 첫 화면 안 한 줄로 옮김(이사장님 승인)
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
