// 기기 설정 — 음향신호기, 이어폰 단추, 워치 (앱 2.6.0, 빌드 260928-8)
import SwiftUI

struct GigiSeoljeongView: View {
    @ObservedObject private var s = Seoljeong.shared
    @State private var beonho = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Toggle(isOn: $s.sinhogiJadong) { Text("음향신호기 자동 울리기").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                Toggle(isOn: $s.ieoponDanchu) { Text("안내 중 이어폰 단추 받기").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                Button("주변 신호기 살피기 — 있는지, 블루투스로 울릴 수 있는지") { SinhogiEngine.shared.juByeonSalpigi() }   // 2.45.0
                    .buttonStyle(KeunDanchu())
                Button("음향신호기 찾기 — 가까워질수록 소리가 빨라집니다") { SinhogiEngine.shared.chatgiKyeogi() }
                    .buttonStyle(KeunDanchu())
                Text("음향신호기 자동 울리기를 켜 두시면 블루투스 음향신호기가 가까이 잡힐 때 위치 안내를, 그 앞에 머무르시면 신호 안내를 스스로 울립니다. 이어폰 단추는 안내 중에만 길눈이 받습니다. 재생은 다시 듣기(신호기 앞이면 신호 안내), 다음은 다음 갈림길, 이전은 앞 안내입니다. 그동안 다른 앱의 음악은 멈춥니다.")
                    .font(.body)
                NavigationLink { RimoView() } label: { Text("리모컨 배우기 — 블루투스 리모컨 단추를 익혀 두기") }
                    .buttonStyle(KeunDanchu())
                Text("워치는 폰과 저절로 이어집니다. 워치 번호 \(beonho)")
                    .font(.body)
            }
            .padding()
        }
        .sokHwamyeon("기기 설정")
        .onAppear { beonho = WatchLink.beonho.map { String($0) }.joined(separator: " ") }
    }
}
