// LVD 방송 아이폰(듣는 앱) — 영상으로 보기 (2.5.0판, 빌드 261024-LI7)
// 지금 나가는 인터넷 방송에 영상이 실려 있으면, 중계가 만들어 두는 HLS(조각 영상 목록, /hls/<sid>/index.m3u8)를
// AVKit 의 VideoPlayer 로 틉니다. 늦게 들어오셔도 지금 나가는 자리부터 영상과 소리가 나옵니다.
// - 맨 위와 맨 아래에 「뒤로」, 두 손가락 문지르기로도 첫 화면으로 돌아옵니다.
// - 보는 동안은 소리 듣기(Deut 의 mp3)를 쉬어 겹치지 않게 하고, 나오면 같은 방송을 이어 들려 드립니다.
// - 전화 따위로 소리가 끊기면(AVAudioSession 끊김) 영상을 멈추고, 끝나면 지금 자리부터 다시 틉니다.
import SwiftUI
import AVKit
import AVFoundation
import Combine
import UIKit

final class BogiTeulgae: ObservableObject {
    let player = AVPlayer()
    @Published private(set) var mal = ""
    @Published private(set) var dasiBoim = false   // 「영상 다시 이어 보기」를 보일까
    private var sid = ""
    private var dasiSu = 0
    private var kkeunkim = false
    private var gamsi: AnyCancellable?
    private var kkeutGamsi: AnyCancellable?

    init() {
        NotificationCenter.default.addObserver(self, selector: #selector(kkeunkimBatgi(_:)), name: AVAudioSession.interruptionNotification, object: nil)
    }

    deinit { NotificationCenter.default.removeObserver(self) }

    /// 바뀔 때만 한 번 알림
    private func allyeo(_ t: String) {
        guard t != mal else { return }
        mal = t
        UIAccessibility.post(notification: .announcement, argument: t)
    }

    func sijak(_ sid: String) {
        self.sid = sid
        dasiSu = 0
        teulgi()
    }

    /// 지금 나가는 자리부터 틂 — 생방송이라 목록을 처음부터 다시 받음
    func teulgi() {
        guard !sid.isEmpty, let u = URL(string: Deut.JUNGYE + "/hls/" + (sid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? sid) + "/index.m3u8") else {
            allyeo("볼 방송을 찾지 못했습니다. 뒤로 가서 다시 골라 주십시오."); return
        }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback, options: [])
        try? AVAudioSession.sharedInstance().setActive(true)
        let it = AVPlayerItem(url: u)
        it.preferredForwardBufferDuration = 4
        gamsi = it.publisher(for: \.status).receive(on: DispatchQueue.main).sink { [weak self] st in
            guard let s = self else { return }
            if st == .readyToPlay {
                s.dasiSu = 0; s.dasiBoim = false
                s.allyeo("영상으로 이어졌습니다.")
                BfbGirok.shared.namgi("bogi", ["mal": "영상 이어짐", "sid": s.sid])
            } else if st == .failed {
                s.jalmot()
            }
        }
        kkeutGamsi = NotificationCenter.default.publisher(for: .AVPlayerItemFailedToPlayToEndTime, object: it)
            .merge(with: NotificationCenter.default.publisher(for: .AVPlayerItemDidPlayToEndTime, object: it))
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.jalmot() }
        player.replaceCurrentItem(with: it)
        player.automaticallyWaitsToMinimizeStalling = true
        if !kkeunkim { player.play() }
        if mal.isEmpty { allyeo("영상을 불러오고 있습니다.") }
    }

    /// 끊김 — 세 번까지 저절로 다시 잇고, 그래도 안 되면 다시 이어 보기 단추
    private func jalmot() {
        BfbGirok.shared.namgi("bogi", ["mal": "영상 끊김", "sid": sid])
        if kkeunkim { return }
        if dasiSu < 3 {
            dasiSu += 1
            allyeo("영상이 끊겨 다시 잇고 있습니다.")
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) { [weak self] in
                guard let s = self, !s.kkeunkim, !s.sid.isEmpty else { return }
                s.teulgi()
            }
        } else {
            dasiBoim = true
            allyeo("영상을 잇지 못했습니다. 방송이 끝났거나 연결이 약합니다. 아래 영상 다시 이어 보기를 누르시거나 뒤로 가서 소리로 들으십시오.")
        }
    }

    func dasiNureum() { dasiSu = 0; dasiBoim = false; teulgi() }

    func kkeut() {
        sid = ""
        gamsi = nil; kkeutGamsi = nil
        player.pause()
        player.replaceCurrentItem(with: nil)
    }

    @objc private func kkeunkimBatgi(_ n: Notification) {
        guard let v = n.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let t = AVAudioSession.InterruptionType(rawValue: v) else { return }
        DispatchQueue.main.async {
            guard !self.sid.isEmpty else { return }
            if t == .began {
                self.kkeunkim = true
                self.player.pause()
                self.allyeo("전화나 다른 소리 때문에 영상을 멈추었습니다. 끝나면 저절로 다시 틀어 드립니다.")
            } else {
                self.kkeunkim = false
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
                    guard let s = self, !s.sid.isEmpty else { return }
                    s.allyeo("영상을 다시 틀어 드립니다.")
                    s.teulgi()   // 생방송이라 지금 자리부터
                }
            }
        }
    }
}

struct BogiHwamyeon: View {
    let sid: String
    let jemok: String
    let dwiro: () -> Void
    @StateObject private var teul = BogiTeulgae()
    @AccessibilityFocusState private var cheotCursor: Bool

    var body: some View {
        VStack(spacing: 10) {
            Danchu("뒤로") { naoda() }.accessibilityFocused($cheotCursor)
            Geul("영상으로 보기" + (jemok.isEmpty ? "" : " — " + jemok), gulgeum: true)
            if !teul.mal.isEmpty { Geul(teul.mal) }
            VideoPlayer(player: teul.player)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black)
                .accessibilityLabel("현장영상해설 방송 영상")
            if teul.dasiBoim { Danchu("영상 다시 이어 보기") { teul.dasiNureum() } }
            Danchu("뒤로") { naoda() }
        }
        .padding(12)
        .accessibilityAction(.escape) { naoda() }
        .onAppear {
            Deut.shared.bogiSijak()
            UIApplication.shared.isIdleTimerDisabled = true   // 보는 동안 화면이 꺼지지 않게
            teul.sijak(sid)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { cheotCursor = true }
        }
        .onDisappear {
            teul.kkeut()
            UIApplication.shared.isIdleTimerDisabled = false
            Deut.shared.bogiKkeut()
        }
    }

    private func naoda() {
        teul.kkeut()
        dwiro()
    }
}
