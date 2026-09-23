// 기기 단추 받기 — 워치·이어폰·리모컨의 재생·다음·이전 단추를 앱이 받아 웹(gigi.js)에 넘깁니다.
// 소리 없는 소리를 틀어 폰이 앱을 "재생 중"으로 알게 하며, 잠긴 뒤에도 앱이 살아 있게 합니다(UIBackgroundModes audio).
import AVFoundation
import MediaPlayer

final class RemoteCommandService {
    static let shared = RemoteCommandService()
    var onCommand: ((String) -> Void)?
    private var player: AVAudioPlayer?
    private var title = "길눈 안내 — 재생은 다시 듣기, 다음은 내 자리, 이전은 앞 안내"

    func setTitle(_ t: String) {
        title = t
        refreshInfo()
    }

    func activate() {
        let s = AVAudioSession.sharedInstance()
        try? s.setCategory(.playback, mode: .spokenAudio, options: [.mixWithOthers, .duckOthers])
        try? s.setActive(true)
        if player == nil, let data = silentWav() {
            player = try? AVAudioPlayer(data: data)
            player?.numberOfLoops = -1
            player?.volume = 0.01
        }
        player?.play()
        let c = MPRemoteCommandCenter.shared()
        let bind: (MPRemoteCommand, String) -> Void = { cmd, name in
            cmd.isEnabled = true
            cmd.removeTarget(nil)
            cmd.addTarget { [weak self] _ in self?.onCommand?(name); return .success }
        }
        bind(c.playCommand, "play")
        bind(c.pauseCommand, "play")
        bind(c.togglePlayPauseCommand, "play")
        bind(c.stopCommand, "play")
        bind(c.nextTrackCommand, "next")
        bind(c.previousTrackCommand, "prev")
        bind(c.seekForwardCommand, "next")
        bind(c.seekBackwardCommand, "prev")
        refreshInfo()
    }

    func deactivate() {
        player?.pause()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    private func refreshInfo() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyArtist: "한국시각장애인현장영상해설협회",
            MPNowPlayingInfoPropertyPlaybackRate: 1.0
        ]
    }

    // 소리 없는 0.5초 wav 를 그 자리에서 만듭니다
    private func silentWav() -> Data? {
        let sr: UInt32 = 8000, n: UInt32 = 4000
        var d = Data()
        func u32(_ v: UInt32) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 4)) }
        func u16(_ v: UInt16) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 2)) }
        d.append("RIFF".data(using: .ascii)!); u32(36 + n); d.append("WAVE".data(using: .ascii)!)
        d.append("fmt ".data(using: .ascii)!); u32(16); u16(1); u16(1); u32(sr); u32(sr); u16(1); u16(8)
        d.append("data".data(using: .ascii)!); u32(n)
        d.append(Data(repeating: 128, count: Int(n)))
        return d
    }
}

// 앱 음성 — 웹 음성이 잠긴 뒤 끊길 때 대비
final class SpeechService {
    static let shared = SpeechService()
    private let synth = AVSpeechSynthesizer()
    func speak(_ t: String, rate: Double) {
        guard !t.isEmpty else { return }
        synth.stopSpeaking(at: .immediate)
        let u = AVSpeechUtterance(string: t)
        u.voice = AVSpeechSynthesisVoice(language: "ko-KR")
        u.rate = Float(min(max(rate, 0.3), 0.7))
        synth.speak(u)
    }
}
