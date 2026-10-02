// AI점자도서관 앱 — 디자인 바탕 (판 0.2.0, 빌드 261002-1, 이사장님 승인 2026-10-02 "멋지게 하자")
// 색: 깊은 남색(밤에 책을 펼치는 고요함) + 따뜻한 금빛(점자의 점·책 읽는 불빛), 법인 로고의 빨강·노랑·초록
// 원칙: 글자와 바탕 대비는 접근성 가장 높은 단계(7:1)를 겨냥, 글씨는 폰 글자 크기를 따라 커짐,
//       색만으로 뜻을 전하지 않음(꾸밈 그림은 낭독기가 읽지 않게 숨기고, 뜻은 글자로)
import SwiftUI
import UIKit

enum Saek {
    static let namsaek = Color(red: 0.078, green: 0.129, blue: 0.239)      // #14213D
    static let geum = Color(red: 0.969, green: 0.906, blue: 0.706)         // #F7E7B4 점자 점
    static let geumJinhan = Color(red: 0.914, green: 0.725, blue: 0.286)   // #E9B949 강조
    static let ppalgang = Color(red: 0.843, green: 0.086, blue: 0.094)     // #D71618 L
    static let norang = Color(red: 0.957, green: 0.780, blue: 0.125)       // #F4C720 V
    static let chorok = Color(red: 0.231, green: 0.702, blue: 0.365)       // #3BB35D D
    /// 화면 바탕 — 밝을 때는 따뜻한 종이색, 어두울 때는 깊은 밤색
    static let bada = Color(UIColor { t in t.userInterfaceStyle == .dark
        ? UIColor(red: 0.055, green: 0.090, blue: 0.188, alpha: 1)
        : UIColor(red: 0.973, green: 0.961, blue: 0.937, alpha: 1) })
    /// 카드 바탕
    static let kadeu = Color(UIColor { t in t.userInterfaceStyle == .dark
        ? UIColor(red: 0.110, green: 0.165, blue: 0.290, alpha: 1)
        : UIColor.white })
    /// 강조 글자 — 밝을 때 남색, 어두울 때 금빛(둘 다 바탕과 대비 7:1 넘음)
    static let ganjo = Color(UIColor { t in t.userInterfaceStyle == .dark
        ? UIColor(red: 0.969, green: 0.906, blue: 0.706, alpha: 1)
        : UIColor(red: 0.078, green: 0.129, blue: 0.239, alpha: 1) })
    /// 갈래마다 고유한 표지 색(흰 글자와 대비 4.5:1 넘는 깊은 색들)
    static let pyoji: [Color] = [
        Color(red: 0.078, green: 0.129, blue: 0.239),  // 남색
        Color(red: 0.459, green: 0.110, blue: 0.161),  // 자주
        Color(red: 0.078, green: 0.329, blue: 0.231),  // 짙은 초록
        Color(red: 0.333, green: 0.192, blue: 0.537),  // 보라
        Color(red: 0.537, green: 0.231, blue: 0.067),  // 밤색
        Color(red: 0.067, green: 0.290, blue: 0.459),  // 바다
        Color(red: 0.239, green: 0.239, blue: 0.290),  // 먹색
        Color(red: 0.420, green: 0.098, blue: 0.318)   // 진분홍
    ]
    static func pyoji(_ key: String) -> Color {
        let h = key.unicodeScalars.reduce(0) { ($0 &* 31 &+ Int($1.value)) & 0x7fffffff }
        return pyoji[h % pyoji.count]
    }
}

/// 법인 로고(점자 lvd + 빨강·노랑·초록 LVD) — 원본 그림과 같은 자리·크기로 그림
struct LogoView: View {
    var jeomSaek: Color = Saek.geum
    var body: some View {
        Canvas { cx, size in
            let s = min(size.width / 205, size.height / 162)
            let ox = (size.width - 205 * s) / 2, oy = (size.height - 162 * s) / 2
            func P(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: ox + x * s, y: oy + y * s) }
            let dots: [(CGFloat, CGFloat)] = [(33.9,28.5),(33.9,46.7),(33.9,65.0),(88.8,28.5),(88.8,46.7),(88.7,65.0),(107.9,65.0),(148.6,28.6),(167.6,28.5),(167.6,46.8)]
            for (x, y) in dots {
                let r = 8.4 * s, c = P(x, y)
                cx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)), with: .color(jeomSaek))
            }
            var l = Path(); l.move(to: P(26,80)); l.addLine(to: P(37,80)); l.addLine(to: P(37,131)); l.addLine(to: P(67,131)); l.addLine(to: P(67,142)); l.addLine(to: P(26,142)); l.closeSubpath()
            cx.fill(l, with: .color(Saek.ppalgang))
            var v = Path(); v.move(to: P(66,80)); v.addLine(to: P(79,80)); v.addLine(to: P(94.5,126)); v.addLine(to: P(111,80)); v.addLine(to: P(124,80)); v.addLine(to: P(101,142)); v.addLine(to: P(88,142)); v.closeSubpath()
            cx.fill(v, with: .color(Saek.norang))
            var d = Path()
            d.move(to: P(135,80)); d.addLine(to: P(155,80)); d.addArc(center: P(155,111), radius: 31 * s, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: false); d.addLine(to: P(135,142)); d.closeSubpath()
            d.move(to: P(146,91)); d.addLine(to: P(155,91)); d.addArc(center: P(155,111), radius: 20 * s, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: false); d.addLine(to: P(146,131)); d.closeSubpath()
            cx.fill(d, with: .color(Saek.chorok), style: FillStyle(eoFill: true))
        }
        .aspectRatio(205.0 / 162.0, contentMode: .fit)
        .accessibilityElement()
        .accessibilityLabel("사단법인 한국시각장애인현장영상해설협회 로고, 점자 lvd와 영문 LVD")
        .accessibilityAddTraits(.isImage)
    }
}

/// 책 표지 — 갈래마다 고유한 색에 점자 무늬와 제목. 꾸밈이므로 낭독기는 읽지 않음(뜻은 줄 글자로)
struct BookCover: View {
    let title: String
    var gal: String = ""
    var keugi: CGFloat = 54
    var body: some View {
        let saek = Saek.pyoji(gal.isEmpty ? title : gal)
        ZStack(alignment: .bottomLeading) {
            RoundedRectangle(cornerRadius: keugi * 0.12).fill(saek)
            // 오른쪽 위 점자 무늬 — 제목에서 뽑은 여섯 점
            let h = title.unicodeScalars.reduce(7) { ($0 &* 33 &+ Int($1.value)) & 0xffff }
            VStack(spacing: keugi * 0.05) {
                ForEach(0..<3, id: \.self) { r in
                    HStack(spacing: keugi * 0.05) {
                        ForEach(0..<2, id: \.self) { c in
                            Circle().fill(Saek.geum.opacity((h >> (r * 2 + c)) & 1 == 1 ? 0.95 : 0.22))
                                .frame(width: keugi * 0.08, height: keugi * 0.08)
                        }
                    }
                }
            }
            .padding(keugi * 0.1)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            Text(String(title.prefix(8)))
                .font(.system(size: keugi * 0.16, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .lineLimit(2)
                .padding(keugi * 0.1)
        }
        .frame(width: keugi, height: keugi * 1.35)
        .shadow(color: .black.opacity(0.18), radius: 3, y: 2)
        .accessibilityHidden(true)
    }
}

/// 첫 화면 머리 — 남색 띠 위에 로고와 이름
struct Meori: View {
    var body: some View {
        HStack(spacing: 14) {
            LogoView().frame(width: 92)
            VStack(alignment: .leading, spacing: 4) {
                Text("AI점자도서관")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.white)
                Text("주관 사단법인 한국시각장애인현장영상해설협회")
                    .font(.footnote)
                    .foregroundStyle(Saek.geum)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 18).fill(Saek.namsaek))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("AI점자도서관. 주관 사단법인 한국시각장애인현장영상해설협회")
        .accessibilityAddTraits(.isHeader)
    }
}
