// 동영상 받아 틀기 — 길눈 2.34.0 (빌드 261002-2, 대표님 승인 2026-10-02)
// ★대표님: 유튜브를 비롯해 동영상이 수시로 오는데 애플워치에는 동영상 재생 기능이 없어 불편하다. 길눈 워치에서 틀어 달라.
//   ① 카톡·문자·사진에서 받은 동영상 파일을 "공유 → 길눈"으로 넘기면 길눈이 받아,
//      워치가 있으면 워치로 보내 워치에서 화면과 소리를 냄(워치는 재생·멈춤·10초 앞뒤 조절, 두 번 집기로 재생·멈춤)
//      워치가 없거나 "워치로 보내기"를 끄셨으면 폰 길눈에서 틂
//   ② 동영상 주소는 복사한 뒤 음악·방송 탭의 "동영상 틀기"에서 "복사한 동영상 주소 틀기"로 틂(같은 갈래)
//   ③ 유튜브 주소는 약관상 길눈이 빼내 틀지 않고 아이폰 유튜브 앱으로 열어 줌 — 워치의 "재생 중"으로 멈춤·소리 크기를 조절
//   길눈을 고르지 않으시면 아이폰이 원래대로 처리합니다.
import SwiftUI
import AVKit
import UIKit

struct DongyeongGeot: Identifiable {
    let id = UUID()
    let url: URL
}

final class Dongyeong: ObservableObject {
    static let shared = Dongyeong()
    /// 폰에서 틀 것(나오면 온 화면 재생기가 뜸)
    @Published var ponJaesaeng: DongyeongGeot?
    /// 마지막으로 받은 것 — 다시 틀기
    @Published private(set) var majimak: URL?

    private var bogwanHam: URL {
        let d = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("dongyeong", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    /// 다른 앱이 "공유 → 길눈"으로 넘긴 것(파일이나 주소)
    func batda(_ u: URL) {
        if u.isFileURL {
            let jabeum = u.startAccessingSecurityScopedResource()
            defer { if jabeum { u.stopAccessingSecurityScopedResource() } }
            // 받은 상자(Inbox)는 아이폰이 지울 수 있어 길눈 보관함으로 옮김 — 앞서 받은 것은 하나만 남김
            if let ett = try? FileManager.default.contentsOfDirectory(at: bogwanHam, includingPropertiesForKeys: nil) {
                for f in ett { try? FileManager.default.removeItem(at: f) }
            }
            let mok = bogwanHam.appendingPathComponent("badeun_" + Int(Date().timeIntervalSince1970).description + "." + (u.pathExtension.isEmpty ? "mp4" : u.pathExtension))
            do { try FileManager.default.copyItem(at: u, to: mok) } catch {
                Girok.shared.namgi("dongyeong_oryu", ["e": error.localizedDescription])
                SoriEngine.shared.mal("동영상을 받지 못했습니다. 한 번 더 길눈으로 보내 주십시오.")
                return
            }
            Girok.shared.namgi("dongyeong_batda", ["kind": "file", "ext": u.pathExtension])
            teulgi(mok)
        } else if u.scheme == "http" || u.scheme == "https" {
            Girok.shared.namgi("dongyeong_batda", ["kind": "juso"])
            teulgi(u)
        }
    }

    /// 복사해 둔 동영상 주소 틀기
    func boksaJusoTeulgi() {
        let p = UIPasteboard.general
        var u: URL? = p.url
        if u == nil, let s = p.string?.trimmingCharacters(in: .whitespacesAndNewlines) {
            // 글 속에 주소가 섞여 있어도 첫 주소를 찾음
            let det = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue)
            u = det?.firstMatch(in: s, range: NSRange(s.startIndex..., in: s))?.url
        }
        guard let url = u, url.scheme == "http" || url.scheme == "https" else {
            SoriEngine.shared.mal("복사해 둔 동영상 주소가 없습니다. 카톡이나 문자에서 동영상 주소를 길게 눌러 복사한 뒤 다시 눌러 주십시오.")
            return
        }
        teulgi(url)
    }

    func dasiTeulgi() {
        guard let u = majimak else { SoriEngine.shared.mal("아직 받은 동영상이 없습니다."); return }
        teulgi(u)
    }

    static func yutyubeuInga(_ u: URL) -> Bool {
        let h = (u.host ?? "").lowercased()
        return h.hasSuffix("youtube.com") || h == "youtu.be" || h.hasSuffix("youtube-nocookie.com")
    }

    /// 어디서 틀지 정해 틂
    func teulgi(_ u: URL) {
        majimak = u
        if Dongyeong.yutyubeuInga(u) {
            // 유튜브 앱이 있으면 그 앱으로, 없으면 사파리로 — 아이폰이 알아서 고름
            UIApplication.shared.open(u)
            SoriEngine.shared.mal("유튜브 앱에서 엽니다. 애플워치의 재생 중 화면에서 멈춤과 소리 크기를 조절하실 수 있습니다.")
            Girok.shared.namgi("dongyeong_teulgi", ["eodi": "youtube"])
            return
        }
        if Seoljeong.shared.dongyeongWatch && WatchLink.shared.watchItda {
            WatchLink.shared.dongyeongBonae(u)
            SoriEngine.shared.mal(u.isFileURL ? "동영상을 워치로 보냅니다. 받는 대로 워치에서 틀어 드립니다. 큰 동영상은 몇십 초 걸릴 수 있습니다."
                                              : "동영상 주소를 워치로 보냅니다. 워치에서 곧 틀어 드립니다.")
            Girok.shared.namgi("dongyeong_teulgi", ["eodi": "watch", "file": u.isFileURL])
            return
        }
        ponJaesaeng = DongyeongGeot(url: u)
        Girok.shared.namgi("dongyeong_teulgi", ["eodi": "pon", "file": u.isFileURL])
    }
}

// MARK: 폰에서 틀기 — 온 화면 재생기

struct DongyeongJaesaengView: View {
    let url: URL
    @Environment(\.dismiss) private var dwiro
    @State private var player: AVPlayer? = nil
    @State private var naoneunJung = false
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        VStack(spacing: 12) {
            Button("뒤로 — 동영상 닫기") { player?.pause(); dwiro() }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused($chojeom)
            if let p = player {
                VideoPlayer(player: p)
                    .frame(maxWidth: .infinity, minHeight: 220)
                    .accessibilityLabel("동영상 화면")
            }
            Button(naoneunJung ? "멈춤" : "재생") { jaesaengMeomchum() }.buttonStyle(KeunDanchu())
            HStack(spacing: 12) {
                Button("10초 뒤로") { olgigi(-10) }.buttonStyle(KeunDanchu())
                Button("10초 앞으로") { olgigi(10) }.buttonStyle(KeunDanchu())
            }
        }
        .padding()
        .onAppear {
            try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
            try? AVAudioSession.sharedInstance().setActive(true)
            let p = AVPlayer(url: url)
            player = p
            p.play(); naoneunJung = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true }
        }
        .onDisappear { player?.pause() }
        // 2.59.0 전화가 오거나 걸면 동영상도 멈춤(저절로 다시 틀지 않음 — 재생 단추로)
        .onReceive(JeonhwaGamsi.shared.$jeonhwaJung) { j in if j { player?.pause(); naoneunJung = false } }
        .accessibilityAction(.escape) { player?.pause(); dwiro() }
    }

    private func jaesaengMeomchum() {
        guard let p = player else { return }
        if naoneunJung { p.pause() } else { p.play() }
        naoneunJung.toggle()
    }

    private func olgigi(_ cho: Double) {
        guard let p = player else { return }
        let t = max(0, p.currentTime().seconds + cho)
        p.seek(to: CMTime(seconds: t, preferredTimescale: 600))
    }
}

// MARK: 음악·방송 탭의 "동영상 틀기" 화면

struct DongyeongView: View {
    @ObservedObject private var d = Dongyeong.shared
    @ObservedObject private var s = Seoljeong.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Button("복사한 동영상 주소 틀기") { d.boksaJusoTeulgi() }
                    .buttonStyle(KeunDanchu())
                if d.majimak != nil {
                    Button("받은 동영상 다시 틀기") { d.dasiTeulgi() }.buttonStyle(KeunDanchu())
                }
                Toggle(isOn: $s.dongyeongWatch) { Text("애플워치가 있으면 워치에서 틀기").font(.title3.weight(.semibold)) }
                Text("카톡이나 문자, 사진에서 받은 동영상은 공유를 누른 뒤 길눈을 고르시면 길눈이 받아 틉니다. 유튜브는 유튜브 앱에서 열어 드립니다.")
                    .font(.body)
            }
            .padding()
        }
        .sokHwamyeon("동영상 틀기")
    }
}
