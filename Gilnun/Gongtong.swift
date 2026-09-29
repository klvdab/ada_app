// 화면의 공통 틀 — 이사장님이 정해 두신 화면 규칙을 여기 한 곳에 담아, 모든 화면이 이 틀을 쓰게 합니다.
// ① 속 화면의 뒤로 단추는 위에만(아래는 탭 바) ② 보이스오버 두 손가락 문지르기로 뒤로 ③ 손가락으로 왼쪽 끝을 밀어도 뒤로
// ④ 목록은 다섯 개씩, 그 아래 더 보기, 그 아래 이전 보기, 목록이 나오면 초점을 첫 줄로
// ⑥ 2.12.1 탭을 고르거나 속 화면이 열리면 커서를 그 화면 첫 줄로(매번)
// ⑤ 큰 단추 — 저시력 이용자도 누르기 쉽게, 대비를 크게
import SwiftUI
import UIKit

enum Saek {
    static let nam = Color(red: 18 / 255, green: 52 / 255, blue: 110 / 255)       // 짙은 파랑(아이콘과 같은 색)
    static let norang = Color(red: 255 / 255, green: 204 / 255, blue: 0 / 255)     // 노란 줄
}

/// 큰 단추
struct KeunDanchu: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.title3.weight(.semibold))
            .multilineTextAlignment(.leading)
            .frame(maxWidth: .infinity, minHeight: 60, alignment: .leading)
            .padding(.horizontal, 18)
            .padding(.vertical, 8)
            .foregroundColor(.white)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(Saek.nam.opacity(configuration.isPressed ? 0.75 : 1))
            )
            .overlay(alignment: .leading) {
                RoundedRectangle(cornerRadius: 2).fill(Saek.norang).frame(width: 6).padding(.vertical, 10)
            }
            .contentShape(Rectangle())
    }
}

/// 속 화면 — 위에 뒤로 단추 하나, 두 손가락 문지르기로도 뒤로
struct SokHwamyeon: ViewModifier {
    @Environment(\.dismiss) private var dismiss
    let jemok: String
    /// 2.12.1 속 화면이 열리면 커서를 내용 첫 줄로(이사장님 지시 2026-09-29)
    @AccessibilityFocusState private var naeyong: Bool

    func body(content: Content) -> some View {
        content
            .navigationTitle(jemok)
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarBackButtonHidden(true)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("뒤로") { dismiss() }
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityFocused($naeyong)
            .accessibilityAction(.escape) { dismiss() }
            .onAppear {
                naeyong = false
                // 먼저 화면이 바뀌었다고 알려 커서를 탭 바에서 떼어 화면 맨 위로, 이어서 내용 첫 줄로
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { UIAccessibility.post(notification: .screenChanged, argument: nil) }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) { naeyong = true }
            }
    }
}

extension View {
    func sokHwamyeon(_ jemok: String) -> some View { modifier(SokHwamyeon(jemok: jemok)) }
}

// 뒤로 단추를 바꾸어도 손가락으로 왼쪽 끝에서 밀면 뒤로 가게 합니다
extension UINavigationController: UIGestureRecognizerDelegate {
    override open func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        viewControllers.count > 1
    }
}

/// 다섯 개씩 끊어 보여 주는 목록 — 줄에는 번호를 붙이지 않고, 나오면 초점을 첫 줄로
struct Mokrok5<Hang: Identifiable, Jul: View>: View {
    let hangdeul: [Hang]
    let jul: (Hang) -> Jul
    @State private var sijak = 0
    @AccessibilityFocusState private var cheotJul: Bool

    init(_ hangdeul: [Hang], @ViewBuilder jul: @escaping (Hang) -> Jul) {
        self.hangdeul = hangdeul
        self.jul = jul
    }

    var body: some View {
        let kkeut = min(sijak + 5, hangdeul.count)
        VStack(alignment: .leading, spacing: 10) {
            if sijak < kkeut {
                ForEach(Array(hangdeul[sijak..<kkeut].enumerated()), id: \.element.id) { i, h in
                    if i == 0 {
                        jul(h).accessibilityFocused($cheotJul)
                    } else {
                        jul(h)
                    }
                }
            }
            if kkeut < hangdeul.count {
                Button("더 보기") { sijak += 5; chojeom() }.buttonStyle(KeunDanchu())
            }
            if sijak > 0 {
                Button("이전 보기") { sijak = max(0, sijak - 5); chojeom() }.buttonStyle(KeunDanchu())
            }
        }
        .onAppear { chojeom() }
    }

    private func chojeom() {
        cheotJul = false   // 2.12.1 두 번째부터도 첫 줄로 가게(이미 참이면 움직이지 않던 것)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { cheotJul = true }
    }
}
