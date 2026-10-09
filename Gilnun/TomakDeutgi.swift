// 목소리 따라 걷기 — 자봉 목소리 토막 들려 드리기 (길눈 2.55.0, 빌드 261007-I9, 대장클, 이사장님 확정 방식 2026-10-06)
// 점지도를 따라 걸을 때, 표시에 닿기 몇 걸음 앞에서(길눈 안내 말 바로 뒤) 그 자리에 자봉이 남긴 짧은 말을 그대로 들려 드림.
// 녹음한 시간이 아니라 걸음 자리에 묶여 있어 빨리 걷든 천천히 걷든 듣는 분의 걸음에 맞춤. 지나친 토막은 건너뜀.
// 협회 서버가 받아쓰기·mp3 바꾸기를 거친 토막만 받음(나스 jeom/sori.php). 목소리와 점지도가 어긋나면 점지도 안내가 먼저.
// 설정에서 끌 수 있음(기본 켬).
import AVFoundation
import Foundation

final class TomakDeutgi: NSObject, AVAudioPlayerDelegate {
    static let shared = TomakDeutgi()
    static var kyeojim: Bool {
        get { UserDefaults.standard.object(forKey: "gn.tomakDeutgi") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "gn.tomakDeutgi") }
    }
    private var player: AVAudioPlayer?
    private var badeun: [String: Data] = [:]

    /// pail 은 sori/<길 번호>/<001>.mp3
    func deutgi(_ pail: String) {
        guard TomakDeutgi.kyeojim else { return }
        let p = pail.hasPrefix("sori/") ? String(pail.dropFirst(5)) : pail
        guard let u = URL(string: "https://lvd.ada.or.kr/jeom/sori.php?p=" + (p.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? p)) else { return }
        Task {
            var d = badeun[p]
            if d == nil, let res = try? await URLSession.shared.data(from: u), (res.1 as? HTTPURLResponse)?.statusCode == 200 { d = res.0; badeun[p] = res.0 }
            guard let data = d else { return }
            await MainActor.run {
                SoriEngine.shared.kkeutnamyeon { [weak self] in self?.teulgi(data) }   // 길눈 안내 말이 끝난 뒤
            }
        }
    }

    /// 2.59.0 전화가 오거나 걸면 곧바로 멈춤
    func meomchugi() {
        DispatchQueue.main.async {
            self.player?.stop()
            self.player = nil
        }
    }

    private func teulgi(_ d: Data) {
        guard !SoriEngine.shared.malAnham else { return }   // 2.59.0 화상통화·전화 중에는 틀지 않음
        guard let p = try? AVAudioPlayer(data: d) else { return }
        try? AVAudioSession.sharedInstance().setActive(true)
        p.delegate = self
        p.volume = 1
        player = p
        p.play()
        Girok.shared.namgi("gn_tomak_deutgi", [:])
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) { self.player = nil }
}
