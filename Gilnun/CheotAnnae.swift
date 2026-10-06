// 처음 안내 — 흰지팡이와 보폭 재기 (앱 2.50.0, 빌드 261006-I10, 이사장님 승인 2026-10-06)
// 이사장님: 앱을 처음 설치하는 사람은 무조건 보폭을 재는 게 맞다. 단독보행을 하실 경우 꼭 흰지팡이를 짚어야 한다고 강조해 줘야 한다.
// 처음 여시면 이 화면부터 — 흰지팡이 당부와 보폭 재기. 급하실 때를 위해 「나중에 재기」를 두되, 재기 전에는 걸음 수 대신 미터로 안내.
// 이미 보폭을 재 두신 분께는 띄우지 않음.
import SwiftUI

struct CheotAnnaeView: View {
    let dachi: () -> Void
    @ObservedObject private var s = Seoljeong.shared
    @AccessibilityFocusState private var chojeom: Bool
    @Environment(\.dismiss) private var dwiro   // 2.52.0 첫 화면 안 줄에서 열렸을 때 돌아가기

    static let kki = "gn.cheotAnnae"
    static var boyeoya: Bool { !UserDefaults.standard.bool(forKey: kki) && !Seoljeong.shared.bopokJaem }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("길눈에 오신 것을 환영합니다")
                        .font(.title.bold())
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityFocused($chojeom)
                    Text("길눈은 혼자 걷기를 돕는 도구입니다. 단독보행을 하실 때는 반드시 흰지팡이를 짚어 주십시오. 길눈의 안내보다 흰지팡이와 주변 소리를 먼저 믿으십시오.")
                        .font(.title3)
                    Text("길눈은 걸음 수로 길을 안내합니다. 걸음 수가 정확하려면 처음 한 번 내 보폭을 재야 합니다. 거리를 미리 아는 곳에서 10미터쯤 평소대로 걸으시면 됩니다.")
                        .font(.title3)
                    NavigationLink { BopokView() } label: { Text("보폭 재기 — 처음 한 번, 10미터쯤 걸어 재기") }
                        .buttonStyle(KeunDanchu())
                    Button("나중에 재기 — 그때까지는 미터로 안내") {
                        UserDefaults.standard.set(true, forKey: CheotAnnaeView.kki)
                        SoriEngine.shared.mal("알겠습니다. 보폭을 재시기 전까지는 걸음 수 대신 미터로 안내합니다. 보폭은 설정 탭의 점지도와 걸음에서 언제든 재실 수 있습니다.")
                        Girok.shared.namgi("cheot_najunge", [:])
                        dachi()
                        dwiro()
                    }
                    .buttonStyle(KeunDanchu())
                }
                .padding()
            }
        }
        .onAppear {
            SoriEngine.shared.mal("길눈에 오신 것을 환영합니다. 단독보행을 하실 때는 반드시 흰지팡이를 짚어 주십시오. 처음 한 번 보폭을 재 주십시오.")
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true }
        }
        .onReceive(s.objectWillChange) { _ in
            DispatchQueue.main.async {
                guard Seoljeong.shared.bopokJaem else { return }
                UserDefaults.standard.set(true, forKey: CheotAnnaeView.kki)
                Girok.shared.namgi("cheot_bopok", [:])
                SoriEngine.shared.mal("보폭을 쟀습니다. 이제 걸음 수로 안내합니다. 길을 나서실 때는 꼭 흰지팡이를 짚어 주십시오.")
                dachi()
                dwiro()
            }
        }
    }
}
