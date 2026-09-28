// 말로 하기 화면 — 길 찾기 첫 화면의 단추 하나, 설정 탭의 말로 하기 설정.
// 단추는 두드리면 곧바로 듣기 시작(소리 "띠링" 뒤에 말씀), 듣는 중에 다시 두드리면 그만.
// 보이스오버가 단추 이름을 되읽어 마이크에 섞이지 않게 "미디어 시작" 성질을 붙임.
import SwiftUI

struct MalHagiDanchu: View {
    @ObservedObject private var m = MalHagi.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button(action: { m.dudeurim() }) {
                Text(geul)
            }
            .buttonStyle(KeunDanchu())
            .accessibilityLabel(Text("말로 하기"))
            .accessibilityAddTraits(.startsMediaSession)
            if !m.dapMal.isEmpty {
                Text("들은 말 — \(m.deureunMal). 길눈 — \(m.dapMal)")
                    .font(.body)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private var geul: String {
        switch m.sangtae {
        case .swim: return "말로 하기 — 두드리고 말씀하십시오"
        case .deutneun: return "듣고 있습니다 — 두드리면 그만"
        case .araboneun: return "알아보는 중입니다"
        }
    }
}

/// 설정 탭 — 말로 하기 설정
struct MalHagiSeoljeongView: View {
    @ObservedObject private var s = Seoljeong.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Toggle(isOn: $s.haiGilnun) { Text("하이 길눈으로 부르기").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                TextField("길눈이 부르는 내 호칭 — 지금 \(s.ho)", text: $s.hoching)
                    .textFieldStyle(.roundedBorder)
                    .font(.title3)
                    .submitLabel(.done)
                Text("하이 길눈을 켜 두시면 길눈이 켜져 있는 동안 부르는 말을 기다립니다. 음악은 그대로 나옵니다. 블루투스 이어폰을 쓰시면 기다리는 동안 이어폰 소리가 전화 소리처럼 조금 낮아질 수 있으니, 그럴 때는 끄시고 말로 하기 단추나 시리로 부르십시오.")
                    .font(.body)
                Text("화면이 잠겨 있을 때는 시리야, 길눈에게 말하기라고 부르신 뒤 할 일을 말씀하십시오. 보이스오버를 쓰시면 어느 화면에서나 두 손가락으로 두 번 두드려 말로 하기를 여실 수 있습니다.")
                    .font(.body)
            }
            .padding()
        }
        .sokHwamyeon("말로 하기 설정")
    }
}
