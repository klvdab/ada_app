// 설정 탭 — 새로고침은 이 한 곳에만 둡니다.
import SwiftUI
import AVFoundation

struct SeoljeongView: View {
    @State private var saerogochimMal = ""
    @AccessibilityFocusState private var malChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                NavigationLink { GichoSiheomView() } label: { Text("기초 시험") }
                    .buttonStyle(KeunDanchu())
                NavigationLink { MalSeoljeongView() } label: { Text("말하기 설정 — 켜기와 끄기, 빠르기, 목소리") }
                    .buttonStyle(KeunDanchu())
                NavigationLink { DoumalView() } label: { Text("도움말") }
                    .buttonStyle(KeunDanchu())
                NavigationLink { GilnunJeongboView() } label: { Text("길눈 정보 — 판과 빌드, 고친 기록") }
                    .buttonStyle(KeunDanchu())
                Button("새로고침 — 받아 둔 자료를 다시 받습니다. 내 설정과 여정은 그대로입니다") {
                    Tongsin.shared.gamchumBiugi()
                    saerogochimMal = "새로고침을 마쳤습니다. 다음에 쓰실 때 나스에서 새 자료를 받습니다."
                    SoriEngine.shared.mal(saerogochimMal)
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { malChojeom = true }
                }
                .buttonStyle(KeunDanchu())
                if !saerogochimMal.isEmpty {
                    Text(saerogochimMal).accessibilityFocused($malChojeom)
                }
            }
            .padding()
        }
        .navigationTitle("설정")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// 말하기 설정 — 화면 글자와 보이스오버가 읽는 것은 건드리지 않고 길눈의 말소리만 다룹니다.
struct MalSeoljeongView: View {
    @ObservedObject private var s = Seoljeong.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Toggle(isOn: $s.malKyeojim) { Text("길눈 말소리").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                Button("빠르기 — \(Seoljeong.bbareugiIreum[s.bbareugiDan]). 누르면 바꿉니다") {
                    s.bbareugiDan = (s.bbareugiDan + 1) % Seoljeong.bbareugiIreum.count
                    SoriEngine.shared.mal("\(Seoljeong.bbareugiIreum[s.bbareugiDan]) 말씀드립니다.")
                }
                .buttonStyle(KeunDanchu())
                Button("목소리 — \(SoriEngine.moksoriIreum(SoriEngine.shared.moksori())). 누르면 바꿉니다") {
                    let l = SoriEngine.hangukMoksori()
                    guard !l.isEmpty else { return }
                    let jigeum = SoriEngine.shared.moksori()?.identifier ?? ""
                    let i = l.firstIndex { $0.identifier == jigeum } ?? -1
                    s.moksoriId = l[(i + 1) % l.count].identifier
                    SoriEngine.shared.mal("이 목소리로 말씀드립니다.")
                }
                .buttonStyle(KeunDanchu())
                Text("경고는 안전을 위해 말소리를 꺼도 늘 말씀드립니다. 더 좋은 목소리는 아이폰 설정의 손쉬운 사용, 읽기 및 말하기, 음성에서 한국어 목소리를 내려받으시면 여기에 나타납니다.")
                    .font(.body)
            }
            .padding()
        }
        .sokHwamyeon("말하기 설정")
    }
}

/// 길눈 정보 — 판과 빌드, 고친 기록
struct GilnunJeongboView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("길눈 \(Pan.pan)판, 빌드 \(Pan.bild), 앱 짓기 번호 \(Pan.appBild). 만든 곳 사단법인 한국시각장애인현장영상해설협회.")
                    .font(.title3)
                Mokrok5(Pan.girok) { g in
                    NavigationLink {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 10) {
                                ForEach(g.naeyong, id: \.self) { Text($0) }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding()
                        }
                        .sokHwamyeon("\(g.pan)판 고친 내용")
                    } label: {
                        Text("\(g.nal) \(g.pan)판, 빌드 \(g.bild)")
                    }
                    .buttonStyle(KeunDanchu())
                }
            }
            .padding()
        }
        .sokHwamyeon("길눈 정보")
    }
}
