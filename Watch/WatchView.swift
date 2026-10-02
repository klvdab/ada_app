// 워치 화면 — 단추 셋만. 이름 하나, 단추 하나.
// 2.33.0 (빌드 261002-1, 대표님 승인) 손가락 두 번 집기 — 화면을 만지지 않고 집기 횟수로 세 가지를 부름
//   한 번: 다음 갈림길(다음 안내) / 두 번: 내 자리 / 세 번: 폰 길눈에게 말하기(폰이 듣기 시작)
//   애플은 앱에 두 번 집기 하나만 내어 주므로(워치 시리즈 9·울트라 2 이후, watchOS 11 이후), 두 번 집기를 몇 번 잇달아 했는지 셈
import SwiftUI
import AVKit
import AVFoundation
import WatchKit

/// 두 번 집기를 이 단추에 이음 — watchOS 11 이후만
struct JipgiIeum: ViewModifier {
    func body(content: Content) -> some View {
        if #available(watchOS 11.0, *) { content.handGestureShortcut(.primaryAction) } else { content }
    }
}

struct WatchView: View {
    @EnvironmentObject var model: WatchModel
    var body: some View {
        ScrollView {
            VStack(spacing: 10) {
                // 2.33.0 손가락 두 번 집기 — 화면을 두드려도 같음
                Button { model.jipgi() } label: { Text("집기: 1 다음, 2 자리, 3 말하기").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent)
                    .modifier(JipgiIeum())
                    .accessibilityLabel("손가락 두 번 집기")
                    .accessibilityHint("엄지와 검지를 두 번 맞대는 두 번 집기를 한 번 하면 다음 갈림길, 잇달아 두 번 하면 내 자리, 세 번 하면 폰 길눈에게 말하기입니다. 집을 때마다 한 번씩 떨립니다")
                // 2.6.0 말로 하기 — 두드리면 받아쓰기가 열리고, 말씀을 폰 길눈이 알아듣고 대답합니다
                TextFieldLink(prompt: Text("말씀하십시오")) {
                    Text("말로 하기").frame(maxWidth: .infinity)
                } onSubmit: { s in
                    model.malhagi(s)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityHint("두드린 뒤 말씀하시면 폰의 길눈이 알아듣고 대답합니다")
                if !model.dapMal.isEmpty {
                    Text(model.dapMal)
                        .font(.footnote)
                        .accessibilityLabel("길눈의 대답. \(model.dapMal)")
                }
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
        // 2.34.0 폰 길눈이 동영상을 보내면 곧바로 재생 화면
        .sheet(item: $model.dongyeong) { g in DongyeongWatchView(url: g.url) }
    }
}

// MARK: 2.34.0 (빌드 261002-2, 대표님 승인) 워치에서 동영상 틀기
// 화면과 소리를 워치에서 냄. 두 번 집기는 이 화면에서 재생·멈춤. 소리가 워치 스피커로 나오지 않는 워치는 블루투스 이어폰으로 나옴.

struct WatchDongyeong: Identifiable {
    let id = UUID()
    let url: URL
}

struct DongyeongWatchView: View {
    let url: URL
    @Environment(\.dismiss) private var dwiro
    @State private var player: AVPlayer? = nil
    @State private var naoneunJung = false

    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                if let p = player {
                    VideoPlayer(player: p)
                        .frame(height: 110)
                        .accessibilityLabel("동영상 화면")
                }
                Button { jaesaengMeomchum() } label: { Text(naoneunJung ? "멈춤" : "재생").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent)
                    .modifier(JipgiIeum())
                    .accessibilityHint("두 번 집기로도 재생과 멈춤이 됩니다")
                HStack {
                    Button("10초 뒤로") { olgigi(-10) }
                    Button("10초 앞으로") { olgigi(10) }
                }
                Button("닫기") { player?.pause(); dwiro() }
            }
        }
        .onAppear {
            try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback, policy: .default, options: [])
            let p = AVPlayer(url: url)
            player = p
            p.play(); naoneunJung = true
        }
        .onDisappear { player?.pause() }
    }

    private func jaesaengMeomchum() {
        guard let p = player else { return }
        if naoneunJung { p.pause() } else { p.play() }
        naoneunJung.toggle()
        WKInterfaceDevice.current().play(.click)
    }

    private func olgigi(_ cho: Double) {
        guard let p = player else { return }
        p.seek(to: CMTime(seconds: max(0, p.currentTime().seconds + cho), preferredTimescale: 600))
    }
}
