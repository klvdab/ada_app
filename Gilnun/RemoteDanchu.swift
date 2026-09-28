// 이어폰·리모컨 단추 — 앱 2.6.0 (빌드 260928-8), 이사장님 결정(1번): 안내 중에만 길눈이 이어폰 단추를 받음, 설정에서 끌 수 있음.
//   재생(멈춤) 단추: 음향신호기 앞이면 신호 안내, 아니면 마지막 안내 다시 듣기
//   다음 단추: 걷는 중이면 다음 갈림길, 아니면 내 자리
//   이전 단추: 앞 안내
// 아이폰은 "지금 소리를 내는 앱"에만 이어폰 단추를 넘기므로, 안내 중에는 길눈이 소리 없는 소리를 틀어 그 앱이 됩니다.
// 그동안 다른 앱의 음악은 멈추고, 여정이 끝나면 길눈이 소리 자리를 내놓아 다른 앱이 다시 틀 수 있게 알립니다.
// 2.8.0 이사장님 결정(나1): 여정이 아닐 때 길눈 방송(음악·라디오·TV·기사)이 나오고 있으면 단추가 방송을 다룹니다.
//   재생(멈춤) 단추: 멈춤과 다시 틀기 / 다음 단추: 다음 곡·다음 채널·다음 기사 / 이전 단추: 이전 곡·앞 채널·기사 처음부터
import Foundation
import AVFoundation
import MediaPlayer
import Combine

final class RemoteDanchu {
    static let shared = RemoteDanchu()
    enum Mode { case eopseum, annae, bangsong }
    private(set) var mode: Mode = .eopseum
    /// 안내 중 — 길눈이 단추와 소리 자리를 쥠
    var kyeojim: Bool { mode == .annae }
    private var player: AVAudioPlayer?
    private var ssak = Set<AnyCancellable>()
    private var sigye: Timer?
    private let jemok = "길눈 안내 — 재생은 다시 듣기, 다음은 다음 갈림길, 이전은 앞 안내"

    func sijak() {
        YeojeongEngine.shared.$jigeum
            .map { y -> Bool in
                guard let y = y else { return false }
                return y.danggye != .dochak
            }
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.matchugi() }
            .store(in: &ssak)
        Seoljeong.shared.$ieoponDanchu
            .dropFirst()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in DispatchQueue.main.async { self?.matchugi() } }
            .store(in: &ssak)
        GinGeup.shared.$sangtae
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in DispatchQueue.main.async { self?.matchugi() } }
            .store(in: &ssak)
        BangsongEngine.shared.$jong
            .map { $0 != .eopseum }
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in DispatchQueue.main.async { self?.matchugi() } }
            .store(in: &ssak)
    }

    /// 지금 형편에 맞춰 켜거나 끔
    func matchugi() {
        let y = YeojeongEngine.shared.jigeum
        let an = y != nil && y?.danggye != .dochak
        let dan = Seoljeong.shared.ieoponDanchu && GinGeup.shared.sangtae == .eopseum
        let mok: Mode = (an && dan) ? .annae : ((dan && BangsongEngine.shared.itda) ? .bangsong : .eopseum)
        guard mok != mode else { return }
        if mode == .annae { kkeugi(dollyeojugi: mok == .eopseum) }
        if mode == .bangsong { danchuPulgi() }
        mode = mok
        if mok == .annae { kyeogi() }
        if mok == .bangsong { bangsongKyeogi() }
    }

    /// 2.8.0 여정이 아닐 때 — 단추가 길눈 방송을 다룸
    private func bangsongKyeogi() {
        let c = MPRemoteCommandCenter.shared()
        let mukgi: (MPRemoteCommand, @escaping () -> Void) -> Void = { cmd, f in
            cmd.isEnabled = true
            cmd.removeTarget(nil)
            cmd.addTarget { _ in
                DispatchQueue.main.async { f() }
                return .success
            }
        }
        let b = BangsongEngine.shared
        mukgi(c.playCommand) { if b.meomchum { b.meomchumTogeul() } }
        mukgi(c.pauseCommand) { if !b.meomchum { b.meomchumTogeul() } }
        mukgi(c.togglePlayPauseCommand) { b.meomchumTogeul() }
        mukgi(c.stopCommand) { b.geuman() }
        mukgi(c.nextTrackCommand) { b.daeum() }
        mukgi(c.previousTrackCommand) { b.ijeon() }
        c.seekForwardCommand.isEnabled = false
        c.seekBackwardCommand.isEnabled = false
        b.pyosiGaengsin()
        Girok.shared.namgi("ieopon_danchu", ["bangsong": true])
    }

    private func danchuPulgi() {
        let c = MPRemoteCommandCenter.shared()
        for cmd in [c.playCommand, c.pauseCommand, c.togglePlayPauseCommand, c.stopCommand,
                    c.nextTrackCommand, c.previousTrackCommand, c.seekForwardCommand, c.seekBackwardCommand] {
            cmd.removeTarget(nil)
            cmd.isEnabled = false
        }
    }

    private func kyeogi() {
        sesyeonJapgi()
        if player == nil {
            player = try? AVAudioPlayer(data: SoriEngine.wav([(0, 0.5)]))
            player?.numberOfLoops = -1
            player?.volume = 0.01
        }
        player?.play()
        let c = MPRemoteCommandCenter.shared()
        let mukgi: (MPRemoteCommand, String) -> Void = { cmd, ireum in
            cmd.isEnabled = true
            cmd.removeTarget(nil)
            cmd.addTarget { [weak self] _ in
                DispatchQueue.main.async { self?.nulleum(ireum) }
                return .success
            }
        }
        mukgi(c.playCommand, "play")
        mukgi(c.pauseCommand, "play")
        mukgi(c.togglePlayPauseCommand, "play")
        mukgi(c.stopCommand, "play")
        mukgi(c.nextTrackCommand, "next")
        mukgi(c.previousTrackCommand, "prev")
        mukgi(c.seekForwardCommand, "next")
        mukgi(c.seekBackwardCommand, "prev")
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: jemok,
            MPMediaItemPropertyArtist: "길눈 — 한국시각장애인현장영상해설협회",
            MPNowPlayingInfoPropertyPlaybackRate: 1.0
        ]
        // 받아쓰기나 통화로 소리 자리가 바뀌었으면 5초 안에 다시 잡음
        sigye?.invalidate()
        sigye = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in self?.jikigi() }
        Girok.shared.namgi("ieopon_danchu", ["on": true])
    }

    private func kkeugi(dollyeojugi: Bool) {
        sigye?.invalidate()
        sigye = nil
        player?.stop()
        danchuPulgi()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        if dollyeojugi && !BangsongEngine.shared.itda && !SoriEngine.shared.bappeum && !MalDeutgi.shared.dolgoItda && GinGeup.shared.sangtae == .eopseum {
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        }
        Girok.shared.namgi("ieopon_danchu", ["on": false])
    }

    /// 재생 전용·다른 소리와 섞지 않음(그래야 이어폰 단추가 길눈으로 옴)
    func sesyeonJapgi() {
        let s = AVAudioSession.sharedInstance()
        try? s.setCategory(.playback, mode: .spokenAudio, options: [])
        try? s.setActive(true)
    }

    private func jikigi() {
        guard kyeojim, GinGeup.shared.sangtae == .eopseum else { return }
        if MalDeutgi.shared.dolgoItda { return }   // 말로 하기가 듣는 중이면 그대로
        if AVAudioSession.sharedInstance().category != .playback { sesyeonJapgi() }
        if !(player?.isPlaying ?? false) { player?.play() }
    }

    private func nulleum(_ ireum: String) {
        Girok.shared.namgi("ieopon", ["d": ireum])
        switch ireum {
        case "play":
            if SinhogiEngine.shared.apeIssna() {
                SinhogiEngine.shared.ulligi(2)
            } else {
                SoriEngine.shared.dasiDeutgi()
            }
        case "next":
            if AnnaeEngine.shared.geonneunJung {
                SoriEngine.shared.mal(AnnaeEngine.shared.daeumGalrimMal(), .annae)
            } else {
                AnnaeEngine.shared.jigeumJari()
            }
        default:
            SoriEngine.shared.apDeutgi()
        }
    }
}
