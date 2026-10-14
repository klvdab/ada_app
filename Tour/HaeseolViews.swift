// LVD투어스튜디오 앱 — 현장영상해설 탭 (판 1.0.0, 빌드 261012-T1)
// 현장에서 대본을 지점 차례대로 꺼내 보는 화면. 연습할 때는 폰 목소리로 들을 수 있습니다.
import SwiftUI
import AVFoundation

final class Moksori: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    static let shared = Moksori()
    private let syn = AVSpeechSynthesizer()
    @Published var malham = false
    @Published var ppareugi: Float = UserDefaults.standard.object(forKey: "ppareugi") as? Float ?? 0.5
    override init() { super.init(); syn.delegate = self }
    func ilkgi(_ t: String) {
        syn.stopSpeaking(at: .immediate)
        let u = AVSpeechUtterance(string: t)
        u.voice = AVSpeechSynthesisVoice(language: "ko-KR")
        u.rate = ppareugi
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
        try? AVAudioSession.sharedInstance().setActive(true)
        syn.speak(u)
        malham = true
    }
    func meomchum() { syn.stopSpeaking(at: .immediate); malham = false }
    func speechSynthesizer(_ s: AVSpeechSynthesizer, didFinish u: AVSpeechUtterance) { DispatchQueue.main.async { self.malham = false } }
    func speechSynthesizer(_ s: AVSpeechSynthesizer, didCancel u: AVSpeechUtterance) { DispatchQueue.main.async { self.malham = false } }
}

struct HaeseolTab: View {
    @EnvironmentObject var store: TourStore
    @ObservedObject private var mok = Moksori.shared
    @AccessibilityFocusState private var meori: Bool
    var body: some View {
        List {
            AllimJul()
            if let k = store.kos, let s = store.cur {
                Section {
                    Text(k.spotFull(s)).font(.title2.weight(.bold)).accessibilityAddTraits(.isHeader).accessibilityFocused($meori)
                    Text("어디서부터: " + k.spotFrom(s)).fixedSize(horizontal: false, vertical: true)
                    Text("어디까지: " + (s.s("to").isEmpty ? "적지 않음" : s.s("to")))
                    Text("시간: " + k.spotMinTxt(s))
                    if !s.s("gpos").isEmpty { Text("현장영상해설사 위치: " + s.s("gpos")) }
                    if !s.s("access").isEmpty { Text("이동 구간 안내: " + s.s("access")) }
                    if !s.s("safe").isEmpty { Text("조심할 것: " + s.s("safe")) }
                }
                Section("대본") {
                    let t = k.spotText(s)
                    Text(t.isEmpty ? "대본이 아직 없습니다." : t)
                        .font(.title3)
                        .textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
                    if !t.isEmpty {
                        Button(mok.malham ? "멈추기" : "폰 목소리로 듣기 (연습용)") { mok.malham ? mok.meomchum() : mok.ilkgi(t) }
                    }
                }
                Section {
                    if let n = k.spotNext(s) { Button("다음 지점 — " + k.spotFull(n)) { mok.meomchum(); store.go(n); jjik() }.font(.headline) }
                    if let p = k.spotPrev(s) { Button("앞 지점 — " + k.spotFull(p)) { mok.meomchum(); store.go(p); jjik() } }
                }
                Section("지점 차례") {
                    Jang(items: k.allSpots) { x in
                        Button(k.spotFull(x) + ", " + k.spotMinTxt(x) + (x.id == s.id ? " (지금)" : "")) { mok.meomchum(); store.go(x); jjik() }
                    }
                }
            } else { KosEopseum() }
        }
        .navigationTitle("현장영상해설")
        .onDisappear { mok.meomchum() }
    }
    private func jjik() { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { meori = true } }
}
