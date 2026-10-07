// 협회 로고 — 길눈·자봉이 함께 씀 (판 1.0.0, 빌드 261007-I4, 대장클)
// 이사장님 지시 2026-10-07 「우리가 작성하는 모든 프로그램에 그 로고를 꼭 넣어서」 — AI점자도서관 앱(Lib/Theme.swift LogoView)과 같은 그림.
// 점자 lvd(위 줄) + 빨강 L·노랑 V·초록 D(아래 줄). 원본과 같은 자리·크기로 그림. 낭독기는 로고 이름 한 번만 읽음.
import SwiftUI

struct HyeophoeLogo: View {
    var jeomSaek: Color = Color(red: 0.969, green: 0.906, blue: 0.706)   // 금빛 점(남색 바탕 위)
    private let ppalgang = Color(red: 0.843, green: 0.086, blue: 0.094)
    private let norang = Color(red: 0.957, green: 0.780, blue: 0.125)
    private let chorok = Color(red: 0.231, green: 0.702, blue: 0.365)
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
            cx.fill(l, with: .color(ppalgang))
            var v = Path(); v.move(to: P(66,80)); v.addLine(to: P(79,80)); v.addLine(to: P(94.5,126)); v.addLine(to: P(111,80)); v.addLine(to: P(124,80)); v.addLine(to: P(101,142)); v.addLine(to: P(88,142)); v.closeSubpath()
            cx.fill(v, with: .color(norang))
            var d = Path()
            d.move(to: P(135,80)); d.addLine(to: P(155,80)); d.addArc(center: P(155,111), radius: 31 * s, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: false); d.addLine(to: P(135,142)); d.closeSubpath()
            d.move(to: P(146,91)); d.addLine(to: P(155,91)); d.addArc(center: P(155,111), radius: 20 * s, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: false); d.addLine(to: P(146,131)); d.closeSubpath()
            cx.fill(d, with: .color(chorok), style: FillStyle(eoFill: true))
        }
        .aspectRatio(205.0 / 162.0, contentMode: .fit)
        .accessibilityElement()
        .accessibilityLabel("사단법인 한국시각장애인현장영상해설협회 로고, 점자 lvd와 영문 LVD")
        .accessibilityAddTraits(.isImage)
    }
}

/// 첫 화면 머리 — 남색 띠 위에 로고와 앱 이름(도서관 앱 머리와 같은 꼴). 글자가 커져도 줄이 바뀌어 겹치지 않음
struct HyeophoeMeori: View {
    let ireum: String
    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            HyeophoeLogo().frame(width: 84)
            VStack(alignment: .leading, spacing: 4) {
                Text(ireum).font(.title2.weight(.bold)).foregroundStyle(.white).fixedSize(horizontal: false, vertical: true)
                Text("주관 사단법인 한국시각장애인현장영상해설협회").font(.footnote)
                    .foregroundStyle(Color(red: 0.969, green: 0.906, blue: 0.706)).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 18).fill(Saek.nam))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(ireum). 주관 사단법인 한국시각장애인현장영상해설협회")
        .accessibilityAddTraits(.isHeader)
    }
}
