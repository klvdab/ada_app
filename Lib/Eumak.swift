// AI점자도서관 — 음악 듣기(음원 서비스 앱 열기) 0.9.0 빌드 261127-L33 (도클, 이사장님 결정 2026-11-27 0시 43분·승인 1시 4분 「1」)
// 나스 음악은 저작권 때문에 도서관이 직접 들려드리지 않고(서버 enter.php 0.6, 261109부터 닫힘), 이용자가 쓰시는 음원 서비스 앱으로 연결만 해 드린다.
// 앱스토어 번호는 앱스토어 공식 화면(apps.apple.com/kr)으로 확인한 것만 넣음(261127).
// 앱을 바로 여는 주소는 애플·스포티파이가 밝힌 것(music://, spotify:)만 쓰고, 나머지는 앱스토어의 그 앱 화면을 연다(깔려 있으면 「열기」, 없으면 「받기」).
import SwiftUI
import UIKit

struct Eumwon: Identifiable {
    let id: String          // 앱스토어 번호
    let ireum: String
    let barojuso: String?   // 앱을 바로 여는 주소(공식으로 밝혀진 것만)
}

enum EumwonMok {
    static let all: [Eumwon] = [
        Eumwon(id: "415597317", ireum: "멜론", barojuso: nil),
        Eumwon(id: "858266085", ireum: "지니뮤직", barojuso: nil),
        Eumwon(id: "1129048043", ireum: "플로", barojuso: nil),
        Eumwon(id: "1338631589", ireum: "바이브", barojuso: nil),
        Eumwon(id: "348555322", ireum: "벅스", barojuso: nil),
        Eumwon(id: "1017492454", ireum: "유튜브 뮤직", barojuso: nil),
        Eumwon(id: "324684580", ireum: "스포티파이", barojuso: "spotify:"),
        Eumwon(id: "1108187390", ireum: "애플 뮤직", barojuso: "music://"),
    ]
    static let annae = "음악은 저작권 때문에 도서관이 직접 들려드리지 않고, 쓰시는 음원 서비스로 연결해 드립니다. 이용권은 각 서비스에서 따로 필요합니다."
    /// 받침이 있으면 「이」, 없으면 「가」
    static func iga(_ s: String) -> String {
        guard let c = s.unicodeScalars.last?.value, c >= 0xAC00, c <= 0xD7A3 else { return "이" }
        return (c - 0xAC00) % 28 == 0 ? "가" : "이"
    }
    static let iphoneMal = "아이폰에서는 앱스토어의 그 앱 화면이 열립니다. 이미 깔려 있으면 열기, 깔려 있지 않으면 받기를 누르십시오. 스포티파이와 애플 뮤직은 깔려 있으면 바로 열립니다."
}

struct EumakView: View {
    @State private var eopseum: Eumwon? = nil
    @AccessibilityFocusState private var cheot: Bool

    var body: some View {
        List {
            Section {
                Text(EumwonMok.annae).accessibilityFocused($cheot)
                Text(EumwonMok.iphoneMal).font(.subheadline).foregroundStyle(.secondary)
            }
            Section("음원 서비스") {
                ForEach(EumwonMok.all) { e in
                    Button { yeolgi(e) } label: { Text(e.ireum).foregroundStyle(.primary) }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Saek.bada)
        .tint(Saek.ganjo)
        .navigationTitle("음악 듣기")
        .navigationBarTitleDisplayMode(.inline)
        .alert(eopseum.map { "\($0.ireum)\(EumwonMok.iga($0.ireum)) 설치되어 있지 않습니다" } ?? "", isPresented: Binding(get: { eopseum != nil }, set: { if !$0 { eopseum = nil } })) {
            Button("앱스토어에서 받기") { if let e = eopseum { gage(e) }; eopseum = nil }
            Button("닫기", role: .cancel) { eopseum = nil }
        } message: { Text("앱스토어에서 받으시겠습니까?") }
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { cheot = true } }
    }

    func yeolgi(_ e: Eumwon) {
        if let b = e.barojuso, let u = URL(string: b) {
            UIApplication.shared.open(u) { ok in if !ok { eopseum = e } }
        } else {
            gage(e)
        }
    }
    func gage(_ e: Eumwon) {
        if let u = URL(string: "https://apps.apple.com/kr/app/id\(e.id)") { UIApplication.shared.open(u) }
    }
}
