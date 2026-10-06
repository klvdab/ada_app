// AI점자도서관 앱 — 목소리 열 가지 고르기와 미리 듣기, 재생 위치 막대 (판 0.4.0, 빌드 261006-4)
// 이사장님 지시(2026-10-06): 수퍼톤 목소리 열 가지(여자 1~5, 남자 1~5)를 모두 넣어 이용자가 골라 듣게(처음 값 여자 1).
// 재생 위치 막대: 보이스오버로 한 손가락 위로 쓸면 앞으로, 아래로 쓸면 뒤로(1퍼센트나 5퍼센트씩), 전체 시간·지금 시간·퍼센트를 읽어 줌.
import SwiftUI
import AVFoundation

enum Moksori {
    static let names = ["여자 1", "여자 2", "여자 3", "여자 4", "여자 5", "남자 1", "남자 2", "남자 3", "남자 4", "남자 5"]
    static func name(_ v: Int) -> String { (0..<10).contains(v) ? names[v] : names[0] }
}

@MainActor
final class Miri: ObservableObject {
    static let shared = Miri()
    private var p: AVAudioPlayer?
    private var tk = 0
    func deutgi(_ v: Int) {
        tk += 1; let my = tk
        Reader.shared.pause()
        p?.stop()
        Task { @MainActor in
            let t = "안녕하십니까. " + Moksori.name(v) + " 목소리입니다. 이 목소리로 책을 읽어 드립니다."
            guard let y = try? await API.yocheong(t, voice: v) else { Store.shared.say("미리 듣기를 받지 못했습니다. 인터넷을 확인해 주십시오."); return }
            let hh = (y.h as String?) ?? ""
            for _ in 0..<80 {
                if my != self.tk { return }
                if let d = try? await API.sori(hh) {
                    try? AVAudioSession.sharedInstance().setActive(true)
                    self.p = try? AVAudioPlayer(data: d); self.p?.play(); return
                }
                try? await Task.sleep(nanoseconds: 500_000_000)
            }
            Store.shared.say("미리 듣기를 받지 못했습니다.")
        }
    }
    func meomchum() { tk += 1; p?.stop() }
}

// 설정 — 목소리 고르기(누르면 고르고 바로 미리 듣기)
struct MoksoriSection: View {
    @EnvironmentObject var store: Store
    var body: some View {
        Section("목소리 고르기, 지금 " + Moksori.name(store.voice)) {
            ForEach(0..<10, id: \.self) { v in
                Button((store.voice == v ? "고름, " : "") + Moksori.name(v) + ", 누르면 고르고 미리 듣기") {
                    store.voice = v; store.save(); Miri.shared.deutgi(v)
                }
            }
        }
    }
}

// 설정 — 재생 위치 막대 한 번에 움직이는 양
struct JaesaengSeoljeong: View {
    @AppStorage("pctStep") private var step = 5
    var body: some View {
        Section("재생 위치 막대") {
            Picker("한 번에 움직이는 양", selection: $step) {
                Text("5퍼센트").tag(5)
                Text("1퍼센트").tag(1)
            }
        }
    }
}

// 책 읽는 화면 — 재생 위치 막대(조절 가능 항목)
struct JaesaengWichi: View {
    @EnvironmentObject var r: Reader
    @AppStorage("pctStep") private var step = 5
    var body: some View {
        Text("재생 위치, " + r.wichiPeosenteuMal)
            .accessibilityElement()
            .accessibilityLabel("재생 위치")
            .accessibilityValue(r.wichiPeosenteuMal)
            .accessibilityHint("한 손가락으로 위로 쓸면 앞으로, 아래로 쓸면 뒤로 " + String(step) + "퍼센트씩 갑니다")
            .accessibilityAdjustableAction { d in
                switch d {
                case .increment: r.gaPeosenteu(r.peosenteu + Double(step))
                case .decrement: r.gaPeosenteu(r.peosenteu - Double(step))
                @unknown default: break
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { UIAccessibility.post(notification: .announcement, argument: r.wichiPeosenteuMal) }
            }
    }
}
