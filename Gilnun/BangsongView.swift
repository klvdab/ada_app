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
                    }
                }
        }
    }
}

/// 지금 나오는 것 — 안내와 단추를 한 줄에
struct JigeumNaoneun: View {
    @ObservedObject private var b = BangsongEngine.shared
    var body: some View {
        Button("지금 나오는 것 — \(b.jemok.isEmpty ? "연결 중" : b.jemok). 그만 듣기") { b.geuman() }
            .buttonStyle(KeunDanchu())
    }
}

struct BangsongCheot: View {
    @ObservedObject private var b = BangsongEngine.shared
    @AccessibilityFocusState private var chojeom: Bool
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
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
    @State private var pw = ""
    @State private var galrae = "가요"
    @State private var tema = ""
    @State private var temaDeul: [String] = []
    @State private var gojangQ = ""
    @State private var deureoganeun = false

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
                    Picker("갈래", selection: $galrae) {
                        ForEach(b.galraeDeul, id: \.self) { Text($0).tag($0) }
                    }
                    .pickerStyle(.menu)
                    .font(.title3)
                    Picker("테마", selection: $tema) {
                        Text("이 갈래 통째로").tag("")
                        ForEach(temaDeul, id: \.self) { Text($0).tag($0) }
                    }
                    .pickerStyle(.menu)
                    .font(.title3)
                    Button("\(galrae)\(tema.isEmpty ? "" : " " + tema) 이어서 틀기") {
                        Task { await b.galraeTeulgi(galrae, tema) }
                    }
                    .buttonStyle(KeunDanchu())
                    Button(b.jadoKyeojim ? "지나는 고장 노래 저절로 틀기 그만" : "지나는 고장 노래 저절로 틀기") {
                        if b.jadoKyeojim { b.jadoKkeugi() } else { b.jadoKyeogi() }
                    }
                    .buttonStyle(KeunDanchu())
                    if !b.jadoJul.isEmpty { Text(b.jadoJul).font(.body) }
                    TextField("고장 이름으로 찾기", text: $gojangQ)
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
                } else {
                    Text(b.cheoumIra ? "처음이십니다. 나스 음악에 쓰실 열쇠를 여섯 자 넘게 정해 주십시오."
                                     : "나스 음악은 열쇠를 넣으신 분만 쓰실 수 있습니다. 한 번 넣으시면 폰에 담아 두어 다시 묻지 않습니다.")
                        .font(.body)
                    SecureField("나스 음악 열쇠", text: $pw)
                        .textFieldStyle(.roundedBorder)
                        .font(.title3)
                        .submitLabel(.go)
                        .onSubmit { deureogagi() }
                    Button(deureoganeun ? "들어가는 중입니다" : "들어가기") { deureogagi() }.buttonStyle(KeunDanchu())
                }
                DisclosureGroup("누구나 음악 펼치기 — 열쇠 없이") {
                    VStack(alignment: .leading, spacing: 10) {
                        Button("잔잔한 음악 틀기") { Task { await b.nugunaTeulgi("jan", "잔잔한 음악을 틉니다.") } }.buttonStyle(KeunDanchu())
                        Button("밝은 음악 틀기") { Task { await b.nugunaTeulgi("bal", "밝은 음악을 틉니다.") } }.buttonStyle(KeunDanchu())
                        Button("모두 섞어 틀기") { Task { await b.nugunaTeulgi("", "누구나 음악을 섞어 틉니다.") } }.buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("길 위의 음악")
        .task {
            await b.eumakJunbi()
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

    private func gojangChatgi() {
        let q = gojangQ.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { SoriEngine.shared.mal("고장 이름을 적어 주십시오."); return }
        Task { await b.gojangChatgi(q) }
    }
}

// MARK: 라디오·TV

/// TV 화면 — 폰을 잠그거나 앱이 뒤로 가면 화면만 떼어 소리는 이어지게
final class BideoUIView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    private var pl: AVPlayerLayer? { layer as? AVPlayerLayer }
    var player: AVPlayer? { didSet { pl?.player = player } }

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        pl?.videoGravity = .resizeAspect
        NotificationCenter.default.addObserver(self, selector: #selector(dwiro), name: UIApplication.didEnterBackgroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(apeuro), name: UIApplication.willEnterForegroundNotification, object: nil)
    }

    required init?(coder: NSCoder) { nil }

    @objc private func dwiro() { pl?.player = nil }
    @objc private func apeuro() { pl?.player = player }
}

struct BideoView: UIViewRepresentable {
    let player: AVPlayer
    func makeUIView(context: Context) -> BideoUIView {
        let v = BideoUIView(frame: .zero)
        v.player = player
        return v
    }
    func updateUIView(_ uiView: BideoUIView, context: Context) {}
    static func dismantleUIView(_ uiView: BideoUIView, coordinator: ()) { uiView.player = nil }
}

struct ChaeneolView: View {
    let kind: String
    @ObservedObject private var b = BangsongEngine.shared
    @State private var mot = false

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
                    }
                    Button(b.meomchum ? "다시 틀기" : "멈춤") { b.meomchumTogeul() }.buttonStyle(KeunDanchu())
                    Button(kind == "tv" ? "TV 그만 보기" : "라디오 그만 듣기") { b.geuman() }.buttonStyle(KeunDanchu())
                    if kind == "tv" && !b.chaeneolNote.isEmpty { Text(b.chaeneolNote).font(.footnote) }
                }
                if kind == "tv" {
                    Toggle(isOn: $b.yeongsangKkeum) {
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
