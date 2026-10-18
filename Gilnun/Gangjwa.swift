// 동영상 강의 — 길눈 사용법 26편을 앱 안에서 틂 (2.100.0, 빌드 261018-I54, 이사장님 승인 2026-10-18 「1」)
// ★이사장님: 시각장애인이 새 홈에 가입하고 몇 단계를 찾아와 듣기는 어렵다. 새 홈에 자리를 잡고, 길눈 앱 안에서 필요한 강의가 나오게.
//   ① 강의 차례는 나스(jeom/gangjwa.php a=mokrok)에서 받음 — 새 홈 온라인강좌의 「길눈 사용법 — 아이폰」과 같은 차례·같은 영상
//      영상을 바꾸면 앱을 고치지 않아도 새 영상이 나옴. 받은 차례는 폰에 담아 두어 인터넷이 잠깐 끊겨도 차례는 보임
//   ② 도움말 맨 위 찾기 칸 아래에 「동영상 강의」, 도움말 한 항목을 열면 그 기능의 강의 한 편으로 가는 단추(나스가 준 낱말로 맞춤)
//   ③ 조회수 — 틀었다·끝까지 보았다·도중에 닫았다를 이름 없이 폰 번호(Girok.dev)로 나스에 남김. 새 홈 「강좌 조회수」에서 봄
import SwiftUI
import AVKit

struct GangjwaPyeon: Identifiable, Codable, Equatable {
    let id: String
    let title: String
    let url: String
    let gin: [String]
}

final class Gangjwa: ObservableObject {
    static let shared = Gangjwa()
    /// 새 홈 온라인강좌의 강좌 번호 — 아이폰 길눈은 아이폰 강좌
    static let gj = "gilnun_i"

    @Published private(set) var pyeon: [GangjwaPyeon] = []
    @Published private(set) var badeum = false
    private let k = "gangjwaPyeon_" + Gangjwa.gj
    private var batneunJung = false

    init() {
        if let d = UserDefaults.standard.data(forKey: k),
           let a = try? JSONDecoder().decode([GangjwaPyeon].self, from: d) {
            pyeon = a
        }
    }

    /// 나스에서 차례를 새로 받음(못 받으면 담아 둔 차례 그대로)
    func batgi() async {
        if batneunJung { return }
        batneunJung = true
        defer { batneunJung = false }
        guard let j = await Nas.get("gangjwa.php", [("a", "mokrok"), ("gj", Gangjwa.gj)]),
              (j["ok"] as? Bool) == true,
              let vs = j["videos"] as? [[String: Any]] else {
            await MainActor.run { badeum = true }
            return
        }
        let a: [GangjwaPyeon] = vs.compactMap { v in
            guard let id = v["id"] as? String, let t = v["title"] as? String, let u = v["url"] as? String else { return nil }
            return GangjwaPyeon(id: id, title: t, url: u, gin: (v["gin"] as? [String]) ?? [])
        }
        await MainActor.run { pyeon = a; badeum = true }
        if let d = try? JSONEncoder().encode(a) { UserDefaults.standard.set(d, forKey: k) }
    }

    /// 도움말 제목에 맞는 강의 한 편
    func matneun(_ jemok: String) -> GangjwaPyeon? {
        pyeon.first { p in p.gin.contains { !$0.isEmpty && jemok.contains($0) } }
    }

    func daeum(_ p: GangjwaPyeon) -> GangjwaPyeon? {
        guard let i = pyeon.firstIndex(of: p), i + 1 < pyeon.count else { return nil }
        return pyeon[i + 1]
    }

    /// 조회수 남기기 — e: si(틀었다) kkeut(끝까지) dat(도중에 닫음)
    func seda(_ p: GangjwaPyeon, _ e: String, _ cho: Int) {
        Girok.shared.namgi("gangjwa_" + e, ["v": p.id, "cho": cho])
        let pan = Pan.pan
        let dev = Girok.shared.dev
        Task.detached {
            _ = await Nas.get("gangjwa.php", [("a", "seda"), ("gj", Gangjwa.gj), ("v", p.id), ("e", e), ("dev", dev),
                                              ("src", "app"), ("cho", String(cho)), ("pan", pan), ("os", "i")])
        }
    }
}

// MARK: 동영상 강의 차례 — 다섯 편씩

struct GangjwaView: View {
    @ObservedObject private var g = Gangjwa.shared
    @State private var teul: GangjwaPyeon?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("길눈 사용법 동영상 강의입니다. 편 이름을 누르시면 앱 안에서 바로 틀어 드립니다.")
                    .font(.title3)
                if g.pyeon.isEmpty {
                    Text(g.badeum ? "강의 차례를 받지 못했습니다. 인터넷이 이어진 뒤 이 화면을 다시 열어 주십시오." : "강의 차례를 받는 중입니다.")
                        .font(.title3)
                } else {
                    Mokrok5(g.pyeon) { p in
                        Button(p.title) { teul = p }
                            .buttonStyle(KeunDanchu())
                    }
                }
            }
            .padding()
        }
        .sokHwamyeon("동영상 강의")
        .task { await g.batgi() }
        .fullScreenCover(item: $teul) { p in GangjwaJaesaengView(cheot: p) }
    }
}

// MARK: 한 편 틀기 — 온 화면

struct GangjwaJaesaengView: View {
    let cheot: GangjwaPyeon
    @Environment(\.dismiss) private var dwiro
    @State private var p: GangjwaPyeon? = nil
    @State private var player: AVPlayer? = nil
    @State private var naoneunJung = false
    @State private var kkeutBoam = false
    @State private var kkeutGamsi: NSObjectProtocol? = nil
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        VStack(spacing: 12) {
            Button("뒤로 — 강의 닫기") { datgi() }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused($chojeom)
            Text(p?.title ?? cheot.title)
                .font(.title3.weight(.semibold))
                .frame(maxWidth: .infinity, alignment: .leading)
            if let pl = player {
                VideoPlayer(player: pl)
                    .frame(maxWidth: .infinity, minHeight: 220)
                    .accessibilityLabel("동영상 화면")
            }
            Button(naoneunJung ? "멈춤" : "재생") { jaesaengMeomchum() }.buttonStyle(KeunDanchu())
            HStack(spacing: 12) {
                Button("10초 뒤로") { olgigi(-10) }.buttonStyle(KeunDanchu())
                Button("10초 앞으로") { olgigi(10) }.buttonStyle(KeunDanchu())
            }
            if let n = p.flatMap({ Gangjwa.shared.daeum($0) }) {
                Button("다음 편 — \(n.title)") { teulgi(n) }.buttonStyle(KeunDanchu())
            }
        }
        .padding()
        .onAppear {
            try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
            try? AVAudioSession.sharedInstance().setActive(true)
            teulgi(cheot)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true }
        }
        .onDisappear { geuman() }
        // 전화가 오거나 걸면 강의도 멈춤(저절로 다시 틀지 않음 — 재생 단추로)
        .onReceive(JeonhwaGamsi.shared.$jeonhwaJung) { j in if j { player?.pause(); naoneunJung = false } }
        .accessibilityAction(.escape) { datgi() }
    }

    private func cho() -> Int {
        guard let s = player?.currentTime().seconds, s.isFinite else { return 0 }
        return Int(s)
    }

    /// 보던 편을 그만둠 — 끝까지 안 보았으면 도중에 닫음으로 남김
    private func geuman() {
        player?.pause()
        if let o = kkeutGamsi { NotificationCenter.default.removeObserver(o); kkeutGamsi = nil }
        if let a = p, !kkeutBoam { Gangjwa.shared.seda(a, "dat", cho()) }
        p = nil
    }

    private func datgi() {
        geuman()
        dwiro()
    }

    private func teulgi(_ a: GangjwaPyeon) {
        guard let u = URL(string: a.url) else {
            SoriEngine.shared.mal("이 강의의 주소가 잘못되었습니다.")
            return
        }
        geuman()
        p = a
        kkeutBoam = false
        let item = AVPlayerItem(url: u)
        kkeutGamsi = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { _ in
            guard !kkeutBoam else { return }
            kkeutBoam = true
            naoneunJung = false
            Gangjwa.shared.seda(a, "kkeut", cho())
            if let n = Gangjwa.shared.daeum(a) {
                SoriEngine.shared.mal("이 편을 다 보셨습니다. 다음 편은 \(n.title)입니다. 다음 편 단추를 누르시면 이어서 틀어 드립니다.")
            } else {
                SoriEngine.shared.mal("이 편을 다 보셨습니다. 마지막 편입니다.")
            }
        }
        if let pl = player { pl.replaceCurrentItem(with: item) } else { player = AVPlayer(playerItem: item) }
        player?.play()
        naoneunJung = true
        Gangjwa.shared.seda(a, "si", 0)
    }

    private func jaesaengMeomchum() {
        guard let pl = player else { return }
        if naoneunJung { pl.pause() } else {
            if kkeutBoam { pl.seek(to: .zero); kkeutBoam = false; if let a = p { Gangjwa.shared.seda(a, "si", 0) } }
            pl.play()
        }
        naoneunJung.toggle()
    }

    private func olgigi(_ c: Double) {
        guard let pl = player else { return }
        let t = max(0, pl.currentTime().seconds + c)
        pl.seek(to: CMTime(seconds: t, preferredTimescale: 600))
    }
}

// MARK: 도움말 한 항목 — 내용과 그 기능의 강의 한 편

struct DoumalNaeyongView: View {
    let d: Doumal
    @ObservedObject private var g = Gangjwa.shared
    @State private var teul: GangjwaPyeon?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(d.naeyong)
                    .font(.title3)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let p = g.matneun(d.jemok) {
                    Button("이 기능 동영상 강의 보기 — \(p.title)") { teul = p }
                        .buttonStyle(KeunDanchu())
                }
            }
            .padding()
        }
        .sokHwamyeon(d.jemok)
        .fullScreenCover(item: $teul) { p in GangjwaJaesaengView(cheot: p) }
    }
}
