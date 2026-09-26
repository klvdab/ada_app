// 워치 화면 — 단추 셋만. 이름 하나, 단추 하나.
import SwiftUI

struct WatchView: View {
    @EnvironmentObject var model: WatchModel
    var body: some View {
        ScrollView {
            VStack(spacing: 10) {
                Button { model.daeumDeutgi() } label: { Text("다음 갈림길").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent)
                    .accessibilityHint("몇 미터 앞에서 어느 쪽으로 꺾는지 읽어 줍니다")
                Button { model.jariDeutgi() } label: { Text("내 자리").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered)
                    .accessibilityHint("지금 있는 곳의 주소와 가까운 건물을 읽어 줍니다")
                Button { model.malDeutgi() } label: { Text("마지막 안내").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered)
                    .accessibilityHint("폰의 길눈이 마지막으로 말한 안내를 다시 읽어 줍니다")
                // 260927-3 음향신호기 — 리모컨의 "유"(위치안내)와 "신"(신호안내)
                Button { model.sinhogi(1) } label: { Text("음향신호기 위치").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered)
                    .accessibilityHint("가까운 블루투스 음향신호기가 위치 안내 소리를 내게 합니다. 리모컨의 유 단추와 같습니다")
                Button { model.sinhogi(2) } label: { Text("음향신호기 신호").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered)
                    .accessibilityHint("가까운 블루투스 음향신호기가 지금 보행 신호를 알려 주게 합니다. 리모컨의 신 단추와 같습니다")
                Text(model.mal)
                    .font(.footnote)
                    .accessibilityLabel("마지막 안내. \(model.mal)")
                    .padding(.top, 4)
            }
            .padding(.horizontal, 4)
        }
        .navigationTitle("길눈")
    }
}
