// 음악·방송 탭 — 앱 2.8.0 (빌드 260928-10), 이사장님 승인(가1·나1).
// 2.12.1 (빌드 260929-1) 기사 화면을 떠나면 기사 읽기를 멈춤(음악·라디오·TV는 그대로 이어짐)
// 첫 화면은 단추 넷(길 위의 음악, 라디오 듣기, TV 보기, 지금 세상 이야기). 무엇이 나오고 있으면 맨 위에 "그만 듣기" 한 줄.
// 소리는 BangsongEngine 이 쥐고 있어 화면을 옮겨도 끊기지 않습니다.
import SwiftUI
import AVFoundation
import UIKit

enum BangsongHwamyeon: Hashable {
    case eumak
    case radio
    case tv
    case sesang
    case gisaMok(String)
    case gisa
    case dongyeong   // 2.34.0 동영상 틀기
}

final class BangsongGil: ObservableObject {
    static let shared = BangsongGil()
    @Published var path = NavigationPath()
}

struct BangsongTab: View {
    @ObservedObject private var gil = BangsongGil.shared
    var body: some View {
        NavigationStack(path: $gil.path) {
            BangsongCheot()
                .navigationDestination(for: BangsongHwamyeon.self) { h in
                    switch h {
                    case .eumak: EumakView()
                    case .radio: ChaeneolView(kind: "radio")
                    case .tv: ChaeneolView(kind: "tv")
                    case .sesang: SesangView()
                    case .gisaMok(let j): GisaMokView(jemok: j)
                    case .gisa: GisaView()
                    case .dongyeong: DongyeongView()
                    }
                }
        }
    }
}

/// 지금 나오는 것 — 안내와 단추를 한 줄에
struct JigeumNaoneun: View {
    @ObservedObject private var b = BangsongEngine.shared
    var body: some View {
        // 2.89.0 / 2.59.0 (261017-I43 / 261017-A47, 이사장님 승인 2026-10-17) 멈춰 두셨을 때는 「멈춰 둔 것」
        Button("\(b.meomchum ? "멈춰 둔 것" : "지금 나오는 것") — \(b.jemok.isEmpty ? "연결 중" : b.jemok). 그만 듣기") { b.geuman() }
            .buttonStyle(KeunDanchu())
    }
}

struct BangsongCheot: View {
    @ObservedObject private var b = BangsongEngine.shared
    @ObservedObject private var s = Seoljeong.shared   // 2.59.0
    @AccessibilityFocusState private var chojeom: Bool
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // 2.59.0 통화로 멈춘 방송 — 맨 위 한 줄, 누르면 이어 들음(이사장님 승인 2026-10-09)
                if b.itda && b.jeonhwaDwi {
                    Button("방송 이어 듣기 — \(b.jemok.isEmpty ? "듣던 방송" : b.jemok)") { b.ieoDeutgi() }
                        .buttonStyle(KeunDanchu())
                }
                if b.itda {
                    JigeumNaoneun().accessibilityFocused($chojeom)
                    NavigationLink(value: BangsongHwamyeon.eumak) { Text("길 위의 음악") }.buttonStyle(KeunDanchu())
                } else {
                    NavigationLink(value: BangsongHwamyeon.eumak) { Text("길 위의 음악") }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($chojeom)
                }
                NavigationLink(value: BangsongHwamyeon.radio) { Text("라디오 듣기") }.buttonStyle(KeunDanchu())
                NavigationLink(value: BangsongHwamyeon.tv) { Text("TV 보기") }.buttonStyle(KeunDanchu())
                NavigationLink(value: BangsongHwamyeon.sesang) { Text("지금 세상 이야기") }.buttonStyle(KeunDanchu())
                NavigationLink(value: BangsongHwamyeon.dongyeong) { Text("동영상 틀기") }.buttonStyle(KeunDanchu())   // 2.34.0
                // 2.59.0 통화 뒤 저절로 이어 듣기(처음 꺼짐)
                // 2.89.0 / 2.59.0 (261017-I43 / 261017-A47, 이사장님 승인 2026-10-17) 켜고 끌 때 새 상태를 말함(안드로이드와 같은 말)
                Toggle(isOn: Binding(get: { s.tonghwaDwiIeum }, set: { v in
                    s.tonghwaDwiIeum = v
                    SoriEngine.shared.mal(v ? "통화가 끝나면 방송을 저절로 이어 드립니다." : "통화가 끝나도 방송을 저절로 틀지 않습니다. 맨 위 방송 이어 듣기를 누르시면 이어집니다.")
                })) { Text("통화 뒤 방송 저절로 이어 듣기").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
            }
            .padding()
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { chojeom = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true } }   // 2.12.1 매번 첫 줄로
    }
}

// MARK: 길 위의 음악

struct EumakView: View {
    @ObservedObject private var b = BangsongEngine.shared
    @ObservedObject private var s = Seoljeong.shared
    @State private var pw = ""
    @State private var galrae = "가요"
    @State private var tema = ""
    @State private var temaDeul: [String] = []
    @State private var gojangQ = ""
    @State private var deureoganeun = false
    @State private var gokQ = ""            // 2.89.0 / 2.59.0 (261017-I43 / 261017-A47, 이사장님 승인 2026-10-17)
    @State private var gokChatneun = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if b.jong == .eumak || b.jong == .nuguna {
                    Text("지금 곡 — \(b.jemok)").font(.title3)
                    if b.jong == .nuguna && !b.nugunaPyosi.isEmpty {
                        Text(b.nugunaPyosi).font(.footnote)
                    }
                    Button("다음 곡") { b.daeum() }.buttonStyle(KeunDanchu())
                    if b.jong == .eumak {
                        Button("이전 곡") { b.ijeon() }.buttonStyle(KeunDanchu())
                    }
                    Button(b.meomchum ? "이어서 틀기" : "멈춤") { b.meomchumTogeul() }.buttonStyle(KeunDanchu())
                    Button("그만 듣기") { b.geuman() }.buttonStyle(KeunDanchu())
                }
                if b.eumakDeureom {
                    // 2.89.0 / 2.59.0 (261017-I43 / 261017-A47, 이사장님 승인 2026-10-17) 펼침 메뉴 대신 단추 + 다섯 개씩 고르는 화면(안드로이드와 같게)
                    NavigationLink {
                        BsGoreugiView(jemok: "갈래 고르기", mok: b.galraeDeul, jigeum: galrae) { g in galrae = g }
                    } label: { Text("갈래 — \(galrae). 누르면 고르기") }
                    .buttonStyle(KeunDanchu())
                    NavigationLink {
                        BsGoreugiView(jemok: "테마 고르기", mok: ["이 갈래 통째로"] + temaDeul, jigeum: tema.isEmpty ? "이 갈래 통째로" : tema) { x in
                            tema = x == "이 갈래 통째로" ? "" : x
                        }
                    } label: { Text("테마 — \(tema.isEmpty ? "이 갈래 통째로" : tema). 누르면 고르기") }
                    .buttonStyle(KeunDanchu())
                    Button("\(galrae)\(tema.isEmpty ? "" : " " + tema) 이어서 틀기") {
                        Task { await b.galraeTeulgi(galrae, tema) }
                    }
                    .buttonStyle(KeunDanchu())
                    // 2.89.0 / 2.59.0 (261017-I43 / 261017-A47, 이사장님 승인 2026-10-17) 가수나 곡 이름으로 찾기(말로 하기와 같은 찾기)
                    TextField("가수나 곡 이름으로 찾기", text: $gokQ)
                        .textFieldStyle(.roundedBorder)
                        .font(.title3)
                        .submitLabel(.search)
                        .onSubmit { gokChatgi() }
                    Button(gokChatneun ? "찾는 중입니다" : "찾기") { gokChatgi() }.buttonStyle(KeunDanchu())
                }
                // 2.19.0 고장 노래는 열쇠 없이 모든 분께 — 모든 시각장애인에게 드리는 선물(이사장님 지시 2)
                Button(b.jadoKyeojim ? "지나는 고장 노래 저절로 틀기 그만" : "지나는 고장 노래 저절로 틀기") {
                    if b.jadoKyeojim { b.jadoKkeugi() } else { b.jadoKyeogi() }
                }
                .buttonStyle(KeunDanchu())
                if !b.jadoJul.isEmpty { Text(b.jadoJul).font(.body) }
                // 2.12.7 차에 타면 저절로(이사장님 승인 1)
                // 2.89.0 / 2.59.0 (261017-I43 / 261017-A47, 이사장님 승인 2026-10-17) 켜고 끌 때 새 상태를 말함
                Toggle(isOn: Binding(get: { s.gojangNorae }, set: { v in
                    s.gojangNorae = v
                    SoriEngine.shared.mal(v ? "차에 타면 지나는 고장 노래를 저절로 틀어 드립니다." : "차에 타도 고장 노래를 저절로 틀지 않습니다.")
                })) { Text("차에 타면 지나는 고장 노래 저절로 틀기").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                TextField("고장 이름으로 찾기 — 시·군 이름", text: $gojangQ)
                    .textFieldStyle(.roundedBorder)
                    .font(.title3)
                    .submitLabel(.search)
                    .onSubmit { gojangChatgi() }
                Button("그 고장 노래 틀기") { gojangChatgi() }.buttonStyle(KeunDanchu())
                if !b.gokMok.isEmpty {
                    DisclosureGroup("지금 목록 보기 펼치기") {
                        Mokrok5(b.gokMok) { g in
                            Button(g.ireum) {
                                if let i = b.gokMok.firstIndex(of: g) { b.gokTeulgi(i) }
                            }
                            .buttonStyle(KeunDanchu())
                        }
                        .id(b.gokMok.first?.f ?? "")
                    }
                    .font(.title3)
                }
                if !b.eumakDeureom {
                    // 음악 전체(갈래·테마, 가수와 곡 찾기)는 열쇠를 넣으신 분만
                    // 2.89.0 / 2.59.0 (261017-I43 / 261017-A47, 이사장님 승인 2026-10-17) 폰에서 열쇠를 새로 정하지 않음 — 열쇠는 협회에서 받음
                    Text(b.cheoumIra ? "나스 음악 열쇠는 협회에서 받으십시오."
                                     : "지나는 고장 노래는 열쇠 없이 누구나 들으십니다. 갈래 틀기와 가수나 곡 이름으로 찾기 같은 나스 음악 전체는 열쇠를 넣으신 분만 쓰실 수 있습니다. 나스 음악 열쇠는 협회에서 받으십시오. 한 번 넣으시면 폰에 담아 두어 다시 묻지 않습니다.")
                        .font(.body)
                    SecureField("나스 음악 열쇠", text: $pw)
                        .textFieldStyle(.roundedBorder)
                        .font(.title3)
                        .submitLabel(.go)
                        .onSubmit { deureogagi() }
                    Button(deureoganeun ? "들어가는 중입니다" : "들어가기") { deureogagi() }.buttonStyle(KeunDanchu())
                }
                DisclosureGroup("누구나 음악 펼치기 — 공유마당 곡") {
                    VStack(alignment: .leading, spacing: 10) {
                        Button("잔잔한 음악 틀기") { Task { await b.nugunaTeulgi("jan", "잔잔한 음악을 틉니다.") } }.buttonStyle(KeunDanchu())
                        Button("밝은 음악 틀기") { Task { await b.nugunaTeulgi("bal", "밝은 음악을 틉니다.") } }.buttonStyle(KeunDanchu())
                        Button("모두 섞어 틀기") { Task { await b.nugunaTeulgi("", "누구나 음악을 섞어 틉니다.") } }.buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
                // 2.13.0 기분과 날씨에 맞춰 틀기(열쇠가 없으면 누구나 음악으로)
                DisclosureGroup("기분과 날씨로 틀기 펼치기") {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(BangsongEngine.Gibun.allCases) { g in
                            Button(g.danchu) {
                                Task {
                                    let m = await b.gibunTeulgi(g)
                                    if !m.isEmpty { await MainActor.run { SoriEngine.shared.mal(m, .jeongbo) } }
                                }
                            }
                            .buttonStyle(KeunDanchu())
                        }
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("길 위의 음악")
        .task {
            await b.eumakJunbi()
            // 2.89.0 / 2.59.0 (261017-I43 / 261017-A47, 이사장님 승인 2026-10-17) 고른 갈래가 목록에 없으면 첫 갈래로
            if let g = b.galraeDeul.first, !b.galraeDeul.contains(galrae) { galrae = g }
            if b.eumakDeureom { temaDeul = await b.temaDeul(galrae) }
        }
        .onChange(of: galrae) { g in
            tema = ""
            Task { temaDeul = await b.temaDeul(g) }
        }
    }

    private func deureogagi() {
        let p = pw.trimmingCharacters(in: .whitespaces)
        guard !p.isEmpty, !deureoganeun else {
            if p.isEmpty { SoriEngine.shared.mal("열쇠를 넣어 주십시오.") }
            return
        }
        deureoganeun = true
        Task {
            let e = await b.yeolsoeNeoki(p)
            var t: [String] = []
            if b.eumakDeureom { t = await b.temaDeul(galrae) }
            await MainActor.run {
                deureoganeun = false
                pw = ""
                if let e = e {
                    SoriEngine.shared.mal(e)
                } else {
                    temaDeul = t
                    if let g = b.galraeDeul.first, !b.galraeDeul.contains(galrae) { galrae = g }
                    SoriEngine.shared.mal("들어오셨습니다. 갈래와 테마를 고르고 이어서 틀기를 누르십시오.")
                }
            }
        }
    }

    /// 2.89.0 / 2.59.0 (261017-I43 / 261017-A47, 이사장님 승인 2026-10-17) 가수나 곡 이름으로 찾기 — 말로 하기와 같은 찾기(찾으면 곧바로 틂)
    private func gokChatgi() {
        let q = gokQ.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { SoriEngine.shared.mal("가수나 곡 이름을 적어 주십시오."); return }
        guard !gokChatneun else { return }
        gokChatneun = true
        SoriEngine.shared.mal("\(q) 노래를 찾고 있습니다.", .jeongbo)
        b.biseutQ = nil
        Task {
            let m = await b.malChatgi(q)
            await MainActor.run {
                gokChatneun = false
                if let bq = b.biseutQ {
                    // 비슷한 곡을 권하면 찾는 칸에 그 이름을 넣어 두고, 한 번 더 찾기를 누르시면 틂
                    b.biseutQ = nil
                    gokQ = bq
                    SoriEngine.shared.mal(m + " 들으시려면 찾기를 한 번 더 누르십시오.")
                } else {
                    SoriEngine.shared.mal(m, .jeongbo)
                }
            }
        }
    }

    private func gojangChatgi() {
        let q = gojangQ.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { SoriEngine.shared.mal("고장 이름을 적어 주십시오."); return }
        Task { await b.gojangChatgi(q) }
    }
}

/// 2.89.0 / 2.59.0 (261017-I43 / 261017-A47, 이사장님 승인 2026-10-17) 고르기 — 갈래·테마처럼 하나를 고르면 앞 화면으로(다섯 개씩, 지금 고른 것에 「, 지금 고른 것」)
struct BsGoreugiView: View {
    let jemok: String
    let mok: [String]
    let jigeum: String
    let gorum: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if mok.isEmpty {
                    Text("고를 것이 없습니다.").font(.title3)
                } else {
                    Mokrok5(mok.map { GaraeHang(id: $0, ireum: $0 == jigeum ? "\($0), 지금 고른 것" : $0) },
                            sijak0: mok.firstIndex(of: jigeum) ?? 0) { h in
                        Button(h.ireum) {
                            gorum(h.id)
                            dismiss()
                        }
                        .buttonStyle(KeunDanchu())
                    }
                }
            }
            .padding()
        }
        .sokHwamyeon(jemok)
    }
}

// MARK: 라디오·TV

/// TV 화면 — 폰을 잠그거나 앱이 뒤로 가면 화면만 떼어 소리는 이어지게
final class BideoUIView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    private var pl: AVPlayerLayer? { layer as? AVPlayerLayer }
    var player: AVPlayer? { didSet { pl?.player = (jeoncheYeollim && !jeoncheYong) ? nil : player } }
    /// 2.107.0 전체화면 쪽 화면인지 — 전체화면이 열려 있는 동안 작은 화면은 영상을 내려놓아 영상이 전체화면 한 곳에만 나오게 함
    var jeoncheYong = false
    static var jeoncheYeollim = false
    static let jeoncheBakkwim = Notification.Name("gilnun.tv.jeonche")
    private var jeoncheYeollim: Bool { BideoUIView.jeoncheYeollim }

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        pl?.videoGravity = .resizeAspect
        NotificationCenter.default.addObserver(self, selector: #selector(dwiro), name: UIApplication.didEnterBackgroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(apeuro), name: UIApplication.willEnterForegroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(jeoncheBakkwim), name: BideoUIView.jeoncheBakkwim, object: nil)
    }

    required init?(coder: NSCoder) { nil }

    @objc private func dwiro() { pl?.player = nil }
    @objc private func apeuro() { pl?.player = (jeoncheYeollim && !jeoncheYong) ? nil : player }
    @objc private func jeoncheBakkwim() { if !jeoncheYong { pl?.player = jeoncheYeollim ? nil : player } }
}

struct BideoView: UIViewRepresentable {
    let player: AVPlayer
    var jeoncheYong = false
    func makeUIView(context: Context) -> BideoUIView {
        let v = BideoUIView(frame: .zero)
        v.jeoncheYong = jeoncheYong
        v.player = player
        return v
    }
    func updateUIView(_ uiView: BideoUIView, context: Context) {}
    static func dismantleUIView(_ uiView: BideoUIView, coordinator: ()) { uiView.player = nil }
}

/// 2.106.0 (261025-I60) TV 전체화면 — 영상만 크게, 맨 아래 「전체화면 닫기」 하나. 같은 플레이어라 소리·방송은 그대로 이어짐
/// 2.107.0 (261025-I61, 이사장님 지적 2026-10-25 「반응을 전혀 안 한다」) 길눈은 세로 화면만 쓰므로 가로 영상을 세로 화면에 그대로 두면
/// 작은 화면과 크기가 같아 커지지 않았음 → 영상을 90도 돌려 폰 화면 긴 쪽 가득 채움(폰을 눕혀 보시면 됨), 작은 화면은 그동안 영상을 내려놓음
struct TVJeoncheView: View {
    let player: AVPlayer
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.ignoresSafeArea()
            GeometryReader { g in
                BideoView(player: player, jeoncheYong: true)
                    .frame(width: g.size.height, height: g.size.width)
                    .rotationEffect(.degrees(90))
                    .position(x: g.size.width / 2, y: g.size.height / 2)
            }
            .ignoresSafeArea()
            .accessibilityElement()
            .accessibilityLabel("TV 전체화면. 폰을 눕혀서 보십시오")
            Button("전체화면 닫기") { dismiss() }
                .buttonStyle(KeunDanchu())
                .padding()
        }
        .onAppear {
            BideoUIView.jeoncheYeollim = true
            NotificationCenter.default.post(name: BideoUIView.jeoncheBakkwim, object: nil)
            Girok.shared.namgi("tv_jeonche_yeolgi", ["pan": "2.107"])
        }
        .onDisappear {
            BideoUIView.jeoncheYeollim = false
            NotificationCenter.default.post(name: BideoUIView.jeoncheBakkwim, object: nil)
            Girok.shared.namgi("tv_jeonche_datgi", [:])
        }
    }
}

struct ChaeneolView: View {
    let kind: String
    @ObservedObject private var b = BangsongEngine.shared
    @State private var mot = false
    @State private var jeonche = false   // 2.106.0 전체화면으로 보기

    private var nawa: Bool { (kind == "tv" && b.jong == .tv) || (kind == "radio" && b.jong == .radio) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if nawa {
                    Text("지금 — \(b.jemok)" + (b.sangtaeMal.isEmpty ? "" : ". " + b.sangtaeMal)).font(.title3)
                    if kind == "tv" && !b.yeongsangKkeum {
                        BideoView(player: b.player)
                            .frame(maxWidth: .infinity)
                            .aspectRatio(16 / 9, contentMode: .fit)
                            .accessibilityLabel("TV 화면")
                        // 2.106.0 (261025-I60, 이사장님 승인 2026-10-25 「그래」) 전체화면 — 같은 플레이어를 크게 보여 소리·방송은 끊기지 않음
                        Button("전체화면으로 보기") { jeonche = true }.buttonStyle(KeunDanchu())
                    }
                    Button(b.meomchum ? "다시 틀기" : "멈춤") { b.meomchumTogeul() }.buttonStyle(KeunDanchu())
                    Button(kind == "tv" ? "TV 그만 보기" : "라디오 그만 듣기") { b.geuman() }.buttonStyle(KeunDanchu())
                    if kind == "tv" && !b.chaeneolNote.isEmpty { Text(b.chaeneolNote).font(.footnote) }
                }
                if kind == "tv" {
                    // 2.89.0 / 2.59.0 (261017-I43 / 261017-A47, 이사장님 승인 2026-10-17) 켜고 끌 때 새 상태를 말함
                    Toggle(isOn: Binding(get: { b.yeongsangKkeum }, set: { v in
                        b.yeongsangKkeum = v
                        SoriEngine.shared.mal(v ? "영상을 끄고 소리만 듣습니다. 데이터를 아낍니다." : "영상을 다시 켭니다.", .jeongbo)
                    })) {
                        Text("영상 끄기 — 소리만 듣고 데이터 아끼기").font(.title3.weight(.semibold))
                    }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
                }
                let ls = b.kindChaeneol(kind)
                if ls.isEmpty {
                    Text(mot ? "채널 목록을 받지 못했습니다." : "채널 목록을 받는 중입니다.").font(.title3)
                    if mot {
                        Button("다시 받기") { batgi() }.buttonStyle(KeunDanchu())
                    }
                } else {
                    Mokrok5(ls) { c in
                        Button(c.julMal) { b.chaeneolTeulgi(c) }.buttonStyle(KeunDanchu())
                    }
                }
            }
            .padding()
        }
        .sokHwamyeon(kind == "tv" ? "TV 보기" : "라디오 듣기")
        .onAppear { if b.chaeneolDeul.isEmpty { batgi() } }
        .fullScreenCover(isPresented: $jeonche) { TVJeoncheView(player: b.player) }
        .onChange(of: b.jong) { j in if j != .tv { jeonche = false } }
        .onChange(of: b.yeongsangKkeum) { k in if k { jeonche = false } }
    }

    private func batgi() {
        mot = false
        Task {
            await b.chaeneolBatgi()
            await MainActor.run { mot = b.chaeneolDeul.isEmpty }
        }
    }
}

// MARK: 지금 세상 이야기

struct GaraeHang: Identifiable {
    let id: String
    let ireum: String
}

struct SesangView: View {
    @ObservedObject private var b = BangsongEngine.shared
    @ObservedObject private var s = Seoljeong.shared
    @State private var q = ""
    @State private var batneun = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if b.jong == .gisa {
                    NavigationLink(value: BangsongHwamyeon.gisa) { Text("읽고 있는 기사로 — \(b.jemok)") }.buttonStyle(KeunDanchu())
                }
                TextField("기사 찾기 — 찾을 말", text: $q)
                    .textFieldStyle(.roundedBorder)
                    .font(.title3)
                    .submitLabel(.search)
                    .onSubmit { chatgi() }
                Button("찾기") { chatgi() }.buttonStyle(KeunDanchu())
                Mokrok5(BangsongEngine.garae.map { GaraeHang(id: $0.0, ireum: $0.1) }) { g in
                    Button(g.ireum) { yeolgi(g.id, g.ireum, sae: false) }.buttonStyle(KeunDanchu())
                }
                Button("소식 새로 받아 오기") { yeolgi("all", "두루 소식", sae: true) }.buttonStyle(KeunDanchu())
                DisclosureGroup("목소리 고르기 펼치기") {
                    VStack(alignment: .leading, spacing: 10) {
                        Button("읽어 줄 목소리 — \(SoriEngine.moksoriIreum(BangsongEngine.gisaMoksori())). 누르면 다음 목소리") { daeumMoksori() }
                            .buttonStyle(KeunDanchu())
                        Button("말하는 빠르기 — \(Seoljeong.bbareugiIreum[max(0, min(4, s.gisaBbareugiDan))]). 누르면 다음 빠르기") {
                            s.gisaBbareugiDan = (s.gisaBbareugiDan + 1) % Seoljeong.bbareugiIreum.count
                            b.moksoriDeureoboki()
                        }
                        .buttonStyle(KeunDanchu())
                        Button("이 목소리로 들어 보기") { b.moksoriDeureoboki() }.buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("지금 세상 이야기")
    }

    private func daeumMoksori() {
        let ls = SoriEngine.hangukMoksori()
        guard !ls.isEmpty else { return }
        let jigeum = BangsongEngine.gisaMoksori()?.identifier ?? ""
        let i = ls.firstIndex { $0.identifier == jigeum } ?? -1
        s.gisaMoksoriId = ls[(i + 1) % ls.count].identifier
        b.moksoriDeureoboki()
    }

    private func yeolgi(_ g: String, _ nm: String, sae: Bool) {
        guard !batneun else { return }
        batneun = true
        SoriEngine.shared.mal("\(nm) 받아 오는 중입니다.", .jeongbo)
        Task {
            let ls = await b.gisaBatgi(g, sae: sae)
            await MainActor.run {
                batneun = false
                guard let ls = ls else { SoriEngine.shared.mal("받아 오지 못했습니다. 잠시 뒤 다시 해 주십시오."); return }
                if ls.isEmpty { SoriEngine.shared.mal("지금 \(nm)에는 새 기사가 없습니다."); return }
                BangsongGil.shared.path.append(BangsongHwamyeon.gisaMok(nm))
                SoriEngine.shared.mal("\(nm), 최신 순으로 \(ls.count)건입니다. 기사를 고르시면 원문을 읽어 드립니다.", .jeongbo)
            }
        }
    }

    private func chatgi() {
        let w = q.trimmingCharacters(in: .whitespaces)
        guard !w.isEmpty else { SoriEngine.shared.mal("찾을 말을 적어 주십시오."); return }
        guard !batneun else { return }
        batneun = true
        SoriEngine.shared.mal("\(w)\(MalHagi.eul(w)) 찾는 중입니다.", .jeongbo)
        Task {
            let ls = await b.gisaChatgi(w)
            await MainActor.run {
                batneun = false
                guard let ls = ls else { SoriEngine.shared.mal("찾지 못했습니다. 통신을 확인해 주십시오."); return }
                if ls.isEmpty { SoriEngine.shared.mal("\(w)\(MalHagi.i(w)) 든 기사가 없습니다."); return }
                BangsongGil.shared.path.append(BangsongHwamyeon.gisaMok("찾은 기사 — \(w)"))
                SoriEngine.shared.mal("\(w), \(ls.count)건을 찾았습니다.", .jeongbo)
            }
        }
    }
}

struct GisaMokView: View {
    let jemok: String
    @ObservedObject private var b = BangsongEngine.shared
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Mokrok5(b.gisaMok) { g in
                    Button(g.julMal) {
                        if let i = b.gisaMok.firstIndex(of: g) {
                            b.gisaYeolgi(i)
                            BangsongGil.shared.path.append(BangsongHwamyeon.gisa)
                        }
                    }
                    .buttonStyle(KeunDanchu())
                }
                .id(b.gisaMok.first?.id ?? "")
            }
            .padding()
        }
        .sokHwamyeon(jemok)
    }
}

struct GisaView: View {
    /// 2.12.1 기사 화면이 떠 있는가 — 화면을 떠나면 기사 읽기를 멈추려고(이사장님 지시 2026-09-29)
    static var boineunSu = 0
    @ObservedObject private var b = BangsongEngine.shared
    @ObservedObject private var s = Seoljeong.shared
    @Environment(\.dismiss) private var dismiss
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if b.gisaI >= 0 && b.gisaI < b.gisaMok.count {
                    Text(b.gisaMok[b.gisaI].jemok)
                        .font(.title2.weight(.bold))
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityFocused($chojeom)
                    Text(b.gisaMeori).font(.footnote)
                    if s.malKyeojim && b.jong == .gisa && b.gisaIlkneun {
                        Button(b.meomchum ? "이어 읽기" : "읽기 멈춤") { b.meomchumTogeul() }.buttonStyle(KeunDanchu())
                    }
                    Button("다음 기사") { b.gisaYeolgi(b.gisaI + 1) }.buttonStyle(KeunDanchu())
                    if s.malKyeojim {
                        Button("처음부터 다시 읽기") {
                            if b.jong == .gisa { b.gisaCheoeumButeo() } else { b.gisaYeolgi(b.gisaI) }
                        }
                        .buttonStyle(KeunDanchu())
                    }
                    Button("기사 목록으로") { dismiss() }.buttonStyle(KeunDanchu())
                    ForEach(Array(b.gisaBon.enumerated()), id: \.offset) { _, p in
                        Text(p).font(.title3)
                    }
                } else {
                    Text("읽고 있는 기사가 없습니다.").font(.title3)
                }
            }
            .padding()
        }
        .sokHwamyeon("기사")
        .onAppear {
            GisaView.boineunSu += 1
            chojeom = false
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true }
        }
        .onDisappear {
            GisaView.boineunSu = max(0, GisaView.boineunSu - 1)
            // 목록으로·뒤로·다른 탭으로 떠나시면 읽기를 멈춤. 폰을 잠그시는 것은 떠나는 것이 아님.
            // 말로 하기가 화면을 새로 여는 틈에는 멈추지 않게 조금 기다렸다가 봄
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                if GisaView.boineunSu == 0 && BangsongEngine.shared.jong == .gisa {
                    BangsongEngine.shared.geuman(malHagi: false)
                }
            }
        }
        .onChange(of: b.gisaI) { _ in DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { chojeom = true } }
    }
}
