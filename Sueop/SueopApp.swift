// LVD 수업 아이폰 — 시작과 화면 (0.1.0판, 빌드 261020-SI1, 방클, 이사장님 승인 2026-10-20 「단독으로 가자」)
// 이사장님이 노트북의 「수업 이끌기」(lvd.ada.or.kr/sueop/ikkeulgi.html)에서 넘기는 자료를 교육생 폰에서 그대로 보는 앱.
// 웹 「수업 보기」(sueop/index.html 0.2판)의 기능을 하나도 빼지 않고 담고, 앱이라 더 좋게 한 것을 더함.
//   웹과 같은 것: 방 번호 네 자리 넣기, 지금 장의 그림과 글, 빨간 테두리 가리키기, 프로그램 화면, 협회 로고, 화면 꺼짐 막기, 뒤로 가기 지킴(나가기는 한 번 더 여쭘), 수업 마침 알림, 끊김 알림과 다시 잇기
//   앱에서 더한 것: 바뀐 일만 한 번 알림(보이스오버·말소리), 가리키는 곳의 말, 그림 크게 보기(손가락으로 키우기), 글자 크기, 지난번 방으로 다시 들어가기, 도움말 찾기, 새 판 알림, 이용 기록
// 첫 화면: 협회 로고 머리 → (새 판 한 줄) → 방 번호 칸(네 자리를 넣으면 저절로 들어감) → 들어가기 → (지난번 방) → 알림 한 줄 → 도움말 → 프로그램 말소리 → 업데이트 → 판 정보
import SwiftUI
import UIKit
import AVFoundation

enum Pan {
    static let PAN = "0.2.0"   // 0.2.0(261028-SI3) 수업 화면 안에 「함께하기 칸」 — 웹과 같은 질문 손들기·예상문제 풀이·검정 시험·화상 수업(방클, 이사장님 승인 2026-10-28 「1」).  0.1.1(261021-SI2) 도움말 「번호가 틀리면」을 실제 알림 말과 맞춤(새방클, 이사장님 승인 2026-10-21 「1」). 0.1.0(261020-SI1) LVD 수업 아이폰 첫 판 — 웹 수업 보기 0.2판의 기능 모두와 바뀐 일만 알림·그림 크게 보기·글자 크기·지난번 방·도움말·새 판 알림·이용 기록(방클, 이사장님 승인 2026-10-20 「단독으로 가자」)
    static let BILD = "261028-SI3"
}

@main
struct SueopApp: App {
    var body: some Scene {
        WindowGroup {
            Bburi()
                .preferredColorScheme(.dark)
                .onAppear { SueopOllim.shared.sijak(); Girok.shared.sijak() }
        }
    }
}

// MARK: 꾸밈 — 법인 짙은 남색 바탕, 흰 단추에 노란 테, 큰 단추는 노랑(LVD 방송·LVD 송출과 같은 색)

enum Saek {
    static let bada = Color(red: 18 / 255, green: 52 / 255, blue: 110 / 255)
    static let nam = Color(red: 11 / 255, green: 33 / 255, blue: 76 / 255)
    static let norang = Color(red: 1, green: 204 / 255, blue: 0)
    static let geom = Color(red: 17 / 255, green: 17 / 255, blue: 17 / 255)
    static let ppalgang = Color(red: 1, green: 40 / 255, blue: 40 / 255)
}

struct Danchu: View {
    let geul: String
    var keun = false
    var norang = false
    let f: () -> Void
    init(_ geul: String, keun: Bool = false, norang: Bool = false, f: @escaping () -> Void) {
        self.geul = geul; self.keun = keun; self.norang = norang; self.f = f
    }
    var body: some View {
        Button(action: f) {
            Text(geul)
                .font(keun ? .title.weight(.bold) : .title3.weight(.bold))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, minHeight: keun ? 90 : 52)
                .padding(.vertical, keun ? 16 : 10)
                .padding(.horizontal, 14)
                .foregroundStyle(norang ? Saek.geom : Saek.bada)
                .background(RoundedRectangle(cornerRadius: 16).fill(norang ? Saek.norang : Color.white))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(norang ? Saek.geom : Saek.norang, lineWidth: 3))
        }
        .buttonStyle(.plain)
    }
}

struct Geul: View {
    let t: String
    var gulgeum = false
    var saek: Color = .white
    init(_ t: String, gulgeum: Bool = false, saek: Color = .white) { self.t = t; self.gulgeum = gulgeum; self.saek = saek }
    var body: some View {
        Text(t)
            .font(gulgeum ? .title3.weight(.bold) : .body)
            .foregroundStyle(saek)
            .lineSpacing(4)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// 프로그램 말소리 — 켜고 끌 수 있음(교실에서 소리가 나지 않게 처음엔 꺼 둠). 화면 글과 보이스오버는 그대로
final class Malsori {
    static let shared = Malsori()
    private let synth = AVSpeechSynthesizer()
    var kyeojim: Bool {
        get { UserDefaults.standard.object(forKey: "malsori") as? Bool ?? false }
        set { UserDefaults.standard.set(newValue, forKey: "malsori") }
    }
    func mal(_ t: String) {
        guard kyeojim else { return }
        if synth.isSpeaking { synth.stopSpeaking(at: .immediate) }
        let u = AVSpeechUtterance(string: t)
        u.voice = AVSpeechSynthesisVoice(language: "ko-KR")
        synth.speak(u)
    }
}

/// 한 번 알릴 말 — 말소리가 켜져 있으면 말로, 꺼져 있으면 보이스오버에(두 번 겹쳐 듣지 않게)
func allim(_ t: String) {
    if Malsori.shared.kyeojim { Malsori.shared.mal(t) } else { UIAccessibility.post(notification: .announcement, argument: t) }
}

// MARK: 뿌리 — 수업에 들어가 있으면 수업 화면, 아니면 첫 화면

struct Bburi: View {
    @ObservedObject private var bang = Bang.shared
    @State private var doumal = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            Saek.bada.ignoresSafeArea()
            if doumal {
                DoumalHwamyeon { doumal = false }
            } else if bang.deureoom {
                SueopHwamyeon(doumalYeolgi: { doumal = true })
            } else {
                CheotHwamyeon(doumalYeolgi: { doumal = true })
            }
        }
        .onChange(of: scenePhase) { p in
            if p == .active { Bang.shared.dorawatda() }
        }
    }
}

// MARK: 첫 화면

struct CheotHwamyeon: View {
    let doumalYeolgi: () -> Void
    @ObservedObject private var bang = Bang.shared
    @ObservedObject private var ollim = SueopOllim.shared
    @State private var beonho = ""
    @State private var malsori = Malsori.shared.kyeojim
    @FocusState private var kanCursor: Bool
    @AccessibilityFocusState private var meoriCursor: Bool

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                HyeophoeMeori(ireum: "LVD 수업").accessibilityFocused($meoriCursor)
                if let p = ollim.saePan {
                    Geul("LVD 수업 새 판 \(p)이 나왔습니다.", gulgeum: true, saek: Saek.norang)
                    Danchu("새 판 받기", norang: true) { ollim.yeolgi() }
                }
                Geul("강사님이 알려 준 방 번호 네 자리를 넣어 주십시오.", gulgeum: true)
                TextField("방 번호 네 자리", text: $beonho)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .font(.system(size: 40, weight: .bold, design: .monospaced))
                    .multilineTextAlignment(.center)
                    .padding(12)
                    .foregroundStyle(Saek.geom)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                    .focused($kanCursor)
                    .accessibilityLabel("방 번호 네 자리")
                    .onChange(of: beonho) { v in
                        let n = String(v.filter(\.isNumber).prefix(4))
                        if n != v { beonho = n; return }   // 고친 값으로 한 번 더 불림
                        if n.count == 4 { kanCursor = false; bang.deureogagi(n) }
                    }
                Danchu("들어가기", keun: true, norang: true) { kanCursor = false; bang.deureogagi(beonho) }
                if let j = bang.jinanBeonho, j != beonho {
                    Danchu("지난번 방(\(j))으로 다시 들어가기") { bang.deureogagi(j) }
                }
                if !bang.chatMal.isEmpty { Geul(bang.chatMal, gulgeum: true, saek: Saek.norang) }
                Danchu("도움말") { doumalYeolgi() }
                Danchu(malsori ? "프로그램 말소리 — 켜짐. 누르면 끕니다" : "프로그램 말소리 — 꺼짐. 누르면 켭니다") {
                    malsori.toggle(); Malsori.shared.kyeojim = malsori
                    allim(malsori ? "프로그램 말소리를 켰습니다." : "프로그램 말소리를 껐습니다.")
                }
                Danchu(ollim.seoljeongGeul) { ollim.seoljeongNureum() }
                Geul("LVD 수업 아이폰 \(Pan.PAN)판, 빌드 \(Pan.BILD). 사단법인 한국시각장애인현장영상해설협회", saek: Color.white.opacity(0.85))
            }
            .padding(20)
        }
        .onAppear {
            beonho = ""
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { meoriCursor = true }
        }
    }
}

// MARK: 수업 화면

struct SueopHwamyeon: View {
    let doumalYeolgi: () -> Void
    @ObservedObject private var bang = Bang.shared
    @ObservedObject private var bu = BuKan.shared   // 0.2.0 함께하기 칸 높이
    @State private var nagalkka = false
    @State private var keuge = false
    @State private var geulKeugi: Double = UserDefaults.standard.object(forKey: "geul_keugi") as? Double ?? 22
    @AccessibilityFocusState private var meoriCursor: Bool

    private var jangGeul: String {
        let s = bang.s
        if s.kkeut != 0 { return "수업을 마쳤습니다" }
        if s.jang <= 0 { return "곧 시작합니다" }
        if s.mode == 4 { return "프로그램 화면" }
        return s.su > 0 ? "\(s.jang)장 / \(s.su)장" : "\(s.jang)장"
    }

    var body: some View {
        let s = bang.s
        ScrollView {
            VStack(spacing: 12) {
                meori.accessibilityFocused($meoriCursor)
                if bang.kkeunkim {
                    Geul("수업 화면과 잠시 끊겼습니다. 저절로 다시 잇습니다.", gulgeum: true, saek: Saek.norang)
                }
                // 0.2.0 함께하기 칸(질문 손들기·예상문제 풀이·검정 시험·화상 수업) — 웹 수업 보기처럼 맨 위쪽에
                BuKanView()
                    .frame(height: bu.nopi)
                    .onAppear { BuKan.shared.yeolgi(bang.beonho) }
                if s.kkeut != 0 {
                    Geul("수업을 마쳤습니다. 수고하셨습니다.", gulgeum: true, saek: Saek.norang)
                } else if s.jang <= 0 {
                    Geul("수업이 곧 시작됩니다. 잠시 기다려 주십시오.", gulgeum: true)
                } else {
                    if s.mode != 2 { geurimKan }
                    if s.gariBoim && !s.gmal.isEmpty {
                        Geul("가리키는 곳: \(s.gmal)", gulgeum: true, saek: Saek.norang)
                    }
                    if s.mode != 1 && s.mode != 4 && !s.geul.isEmpty {
                        Text(s.geul)
                            .font(.system(size: UIFontMetrics(forTextStyle: .body).scaledValue(for: geulKeugi)))   // 아이폰 설정의 글자 크기도 따라감
                            .foregroundStyle(Saek.geom)
                            .lineSpacing(6)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(14)
                            .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                            .textSelection(.enabled)
                        HStack(spacing: 10) {
                            Danchu("글자 크게") { geulBakkugi(+3) }
                            Danchu("글자 작게") { geulBakkugi(-3) }
                        }
                    }
                }
                Danchu("도움말") { doumalYeolgi() }
                Danchu("나가기") { nagalkka = true }
            }
            .padding(16)
        }
        .accessibilityAction(.escape) { nagalkka = true }
        .alert("수업에서 나갈까요?", isPresented: $nagalkka) {
            Button("나가기", role: .destructive) { Bang.shared.nagagi() }
            Button("수업에 남기", role: .cancel) { }
        } message: {
            Text("나가셔도 같은 방 번호로 언제든 다시 들어오실 수 있습니다.")
        }
        .fullScreenCover(isPresented: $keuge) { KeugeBogi { keuge = false } }
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { meoriCursor = true } }
    }

    private var meori: some View {
        HStack(alignment: .center, spacing: 12) {
            HyeophoeLogo().frame(width: 56)
            VStack(alignment: .leading, spacing: 2) {
                Text(bang.s.ireum.isEmpty ? "LVD 수업" : bang.s.ireum)
                    .font(.title3.weight(.bold)).foregroundStyle(.white).fixedSize(horizontal: false, vertical: true)
                Text("방 \(bang.beonho) · \(jangGeul)")
                    .font(.headline).foregroundStyle(Saek.norang).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 16).fill(Saek.nam))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(bang.s.ireum.isEmpty ? "LVD 수업" : bang.s.ireum). 방 \(bang.beonho). \(jangGeul)")
        .accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder private var geurimKan: some View {
        if let im = bang.geurim {
            Image(uiImage: im)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(maxWidth: .infinity)
                .overlay(GariTeduri(s: bang.s))
                .background(Color.black)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .accessibilityElement()
                .accessibilityLabel(bang.geurimIreum + (bang.s.gariBoim ? ", 빨간 테두리로 가리키는 곳이 있습니다" : ""))
                .accessibilityAddTraits(.isImage)
                .onTapGesture(count: 2) { keuge = true }
            Danchu("그림 크게 보기") { keuge = true }
        } else {
            Geul("자료 그림을 받고 있습니다.", saek: Color.white.opacity(0.85))
        }
    }

    private func geulBakkugi(_ d: Double) {
        let n = min(44, max(16, geulKeugi + d))
        geulKeugi = n
        UserDefaults.standard.set(n, forKey: "geul_keugi")
        allim(d > 0 ? "글자를 키웠습니다." : "글자를 줄였습니다.")
    }
}

/// 빨간 테두리 — 그림 크기에 맞춰(서버 값은 그림 가로·세로를 1000으로 본 자리)
struct GariTeduri: View {
    let s: Sangtae
    var body: some View {
        GeometryReader { g in
            if s.gariBoim {
                let w = g.size.width * CGFloat(s.gw) / 1000, h = g.size.height * CGFloat(s.gh) / 1000
                Rectangle()
                    .stroke(Saek.ppalgang, lineWidth: 4)
                    .frame(width: w, height: h)
                    .position(x: g.size.width * CGFloat(s.gx) / 1000 + w / 2, y: g.size.height * CGFloat(s.gy) / 1000 + h / 2)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// 그림 크게 보기 — 화면 가득, 두 손가락으로 키우고 줄이기, 두 번 두드리면 두 배·제자리, 끌어서 옮기기
struct KeugeBogi: View {
    let dachigi: () -> Void
    @ObservedObject private var bang = Bang.shared
    @State private var bae: CGFloat = 1
    @State private var jeonBae: CGFloat = 1
    @State private var olm: CGSize = .zero
    @State private var jeonOlm: CGSize = .zero

    var body: some View {
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()
            if let im = bang.geurim {
                Image(uiImage: im)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .overlay(GariTeduri(s: bang.s))
                    .scaleEffect(bae)
                    .offset(olm)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .gesture(MagnificationGesture()
                        .onChanged { v in bae = min(6, max(1, jeonBae * v)) }
                        .onEnded { _ in jeonBae = bae; if bae <= 1 { olm = .zero; jeonOlm = .zero } })
                    .simultaneousGesture(DragGesture()
                        .onChanged { v in if bae > 1 { olm = CGSize(width: jeonOlm.width + v.translation.width, height: jeonOlm.height + v.translation.height) } }
                        .onEnded { _ in jeonOlm = olm })
                    .onTapGesture(count: 2) {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            if bae > 1 { bae = 1; olm = .zero } else { bae = 2 }
                            jeonBae = bae; jeonOlm = olm
                        }
                    }
                    .accessibilityElement()
                    .accessibilityLabel(bang.geurimIreum + ", 크게 보기")
                    .accessibilityAddTraits(.isImage)
            } else {
                Geul("보여 줄 그림이 없습니다.", gulgeum: true).padding(.top, 120).padding(.horizontal, 20)
            }
            HStack {
                Text(bang.s.mode == 4 ? "프로그램 화면" : (bang.s.jang > 0 ? "\(bang.s.jang)장" : ""))
                    .font(.headline).foregroundStyle(Saek.norang)
                Spacer()
                Button(action: dachigi) {
                    Text("닫기").font(.title3.weight(.bold)).foregroundStyle(Saek.geom)
                        .padding(.horizontal, 22).padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Saek.norang))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16).padding(.top, 8)
        }
        .accessibilityAction(.escape) { dachigi() }
        .onChange(of: bang.s.bogiKi) { _ in bae = 1; jeonBae = 1; olm = .zero; jeonOlm = .zero }
        .onChange(of: bang.deureoom) { d in if !d { dachigi() } }
    }
}

// MARK: 도움말 — 찾기 칸, 다섯 개씩, 맨 위와 맨 아래 뒤로 단추

struct DoumalHwamyeon: View {
    let dwiro: () -> Void
    @State private var chatgi = ""
    @State private var jjok = 0
    @State private var yeollin: String? = nil
    @AccessibilityFocusState private var cheotCursor: Bool

    var body: some View {
        let l = Doumal.chatgi(chatgi)
        let sijak = min(jjok * 5, max(0, (l.count - 1) / 5 * 5))
        let boil = Array(l[min(sijak, l.count)..<min(sijak + 5, l.count)])
        ScrollView {
            VStack(spacing: 12) {
                Danchu("뒤로") { dwiro() }.accessibilityFocused($cheotCursor)
                TextField("도움말 찾기 (낱말을 넣고 엔터)", text: $chatgi)
                    .font(.title3).padding(10).foregroundStyle(Saek.geom)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.white))
                    .onSubmit { jjok = 0 }
                    .onChange(of: chatgi) { _ in jjok = 0 }
                    .accessibilityLabel("도움말 찾기")
                if l.isEmpty { Geul("찾는 낱말이 든 도움말이 없습니다. 다른 낱말로 찾아보십시오.") }
                ForEach(boil) { h in
                    Danchu(h.jemok) { yeollin = (yeollin == h.jemok) ? nil : h.jemok }
                    if yeollin == h.jemok { Geul(h.naeyong) }
                }
                if sijak + 5 < l.count { Danchu("더 보기") { jjok = sijak / 5 + 1 } }
                if sijak > 0 { Danchu("이전 보기") { jjok = max(0, sijak / 5 - 1) } }
                Danchu("뒤로") { dwiro() }
            }
            .padding(20)
        }
        .accessibilityAction(.escape) { dwiro() }
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { cheotCursor = true } }
    }
}
