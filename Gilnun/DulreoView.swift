// 둘러보기 탭 — 앱 2.7.0 (빌드 260928-9), 이사장님 승인(1번).
// 첫 화면은 급한 것부터: 둘레 찾기, 사진 읽어 주기, 안면인식. 나머지는 "가는 김에 펼치기" 안에.
// 목록은 다섯 개씩, 번호 없이, 나오면 초점을 첫 줄로. 이름을 두드리면 그곳까지 안내, 전화는 같은 줄의 동작(위아래로 쓸기)으로.
import SwiftUI
import UIKit
import Vision

enum DulreoHwamyeon: Hashable {
    case dulle
    case mokrok(GotCheo)
    case bapjip
    case daepiso
    case gabol(String)
    case gojang
    case sajin
    case anmyeon
    case miri
    case masil
    case masilGo(String)
}

/// 목록을 어디서 받아 오는가
enum GotCheo: Hashable {
    case dulle(DulleJong)
    case bapjip(BapjipGalae)
    case daepiso(String, String)
    case gabol(String)

    var jemok: String {
        switch self {
        case .dulle(let j): return j.ireum.components(separatedBy: " — ").first ?? j.ireum
        case .bapjip(let g): return g.ireum
        case .daepiso(let i, _): return i
        case .gabol(let a): return DulreoGabol.ireum(a)
        }
    }
}

enum DulreoGabol {
    static func ireum(_ a: String) -> String {
        switch a {
        case "chukje": return "축제"
        case "mujangae": return "무장애 여행 정보"
        default: return "가 볼 곳"
        }
    }
}

/// 탭 고르기 — 둘러보기에서 고른 곳으로 안내를 시작하면 길 찾기 탭으로 넘어감
final class TabGil: ObservableObject {
    static let shared = TabGil()
    @Published var tab = 0
}

final class DulreoGil: ObservableObject {
    static let shared = DulreoGil()
    @Published var path = NavigationPath()
}

/// 고른 곳으로 — 2킬로미터 안이면 곧바로 걷는 안내, 멀면 길 찾기 탭의 "어떻게 가실지" 화면
func gotEuroGagi(_ j: Jangso) {
    Jeulgyeo.shared.sseum(j)
    var meolda = false
    if let w = WichiEngine.shared.jigeum { meolda = WichiEngine.geori(w.lat, w.lon, j.lat, j.lon) > 2000 }
    TabGil.shared.tab = 0
    if meolda {
        GilGil.shared.cheotHwamyeon()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.mokjeok(j)) }
        SoriEngine.shared.mal("\(j.ireum). 걸어가시기에는 먼 곳이라 어떻게 가실지 고르시는 화면을 엽니다.")
    } else {
        // 2.10.0 맞는 점지도가 있으면 한 번 여쭘
        JeomEngine.shared.georeoGagiBoda(j) { q in if let q = q { SoriEngine.shared.mal(q) } }
        GilGil.shared.cheotHwamyeon()
    }
    Girok.shared.namgi("dulreo_gagi", ["meolda": meolda])
}

struct DulreoTab: View {
    @ObservedObject private var gil = DulreoGil.shared
    var body: some View {
        NavigationStack(path: $gil.path) {
            DulreoCheot()
                .navigationDestination(for: DulreoHwamyeon.self) { h in
                    switch h {
                    case .dulle: DulleView()
                    case .mokrok(let c): GotMokrokView(cheo: c)
                    case .bapjip: BapjipView()
                    case .daepiso: DaepisoView()
                    case .gabol(let a): GotMokrokView(cheo: .gabol(a))
                    case .gojang: GojangView()
                    case .sajin: SajinView()
                    case .anmyeon: AnmyeonView()
                    case .miri: MiriView()
                    case .masil: MasilView()
                    case .masilGo(let id): MasilGoView(id: id)
                    }
                }
        }
    }
}

struct DulreoCheot: View {
    @AccessibilityFocusState private var chojeom: Bool
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                NavigationLink(value: DulreoHwamyeon.dulle) { Text("둘레 찾기 — 식당, 약국, 화장실, 응급실") }
                    .buttonStyle(KeunDanchu())
                    .accessibilityFocused($chojeom)
                NavigationLink(value: DulreoHwamyeon.sajin) { Text("사진 읽어 주기") }
                    .buttonStyle(KeunDanchu())
                NavigationLink(value: DulreoHwamyeon.anmyeon) { Text("안면인식 — 카메라 앞의 사람을 알려 드립니다") }
                    .buttonStyle(KeunDanchu())
                DisclosureGroup("가는 김에 펼치기") {
                    VStack(alignment: .leading, spacing: 10) {
                        NavigationLink(value: DulreoHwamyeon.gabol("gabol")) { Text("가 볼 곳") }.buttonStyle(KeunDanchu())
                        NavigationLink(value: DulreoHwamyeon.gabol("chukje")) { Text("축제") }.buttonStyle(KeunDanchu())
                        NavigationLink(value: DulreoHwamyeon.gabol("mujangae")) { Text("무장애 여행 정보") }.buttonStyle(KeunDanchu())
                        NavigationLink(value: DulreoHwamyeon.gojang) { Text("고장 이야기 — 지나는 고장의 먹을 곳·볼 곳") }.buttonStyle(KeunDanchu())
                        NavigationLink(value: DulreoHwamyeon.miri) { Text("어디서 어디로 — 미리 들어 보기") }.buttonStyle(KeunDanchu())
                        NavigationLink(value: DulreoHwamyeon.masil) { Text("마실 — 앉은자리에서 떠나는 여행") }.buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { chojeom = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true } }   // 2.12.1 매번 첫 줄로
    }
}

// MARK: 둘레 찾기

struct DulleView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(DulleJong.modu.filter { $0.geot }) { j in
                    NavigationLink(value: jariro(j)) { Text(j.ireum) }
                        .buttonStyle(KeunDanchu())
                }
                DisclosureGroup("그 밖에 펼치기") {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(DulleJong.modu.filter { !$0.geot }) { j in
                            NavigationLink(value: jariro(j)) { Text(j.ireum) }
                                .buttonStyle(KeunDanchu())
                        }
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("둘레 찾기")
    }

    private func jariro(_ j: DulleJong) -> DulreoHwamyeon {
        if j.id == "bapjip" { return .bapjip }
        if j.id == "daepiso" { return .daepiso }
        return .mokrok(.dulle(j))
    }
}

struct BapjipView: View {
    @State private var galae: [BapjipGalae]?
    @State private var mot = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let l = galae {
                    Mokrok5(l) { g in
                        NavigationLink(value: DulreoHwamyeon.mokrok(.bapjip(g))) { Text(g.ireum) }
                            .buttonStyle(KeunDanchu())
                    }
                } else if mot {
                    Button("갈래를 받지 못했습니다 — 다시 받기") { bureogi() }.buttonStyle(KeunDanchu())
                } else {
                    Text("밥집 갈래를 받는 중입니다.").font(.title3)
                }
            }
            .padding()
        }
        .sokHwamyeon("식당")
        .task { if galae == nil { bureogi() } }
    }
    private func bureogi() {
        mot = false
        Task {
            let r = await Dulreo.bapjipGalae()
            await MainActor.run { if let r = r { galae = r } else { mot = true } }
        }
    }
}

struct DaepisoView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(DulleJong.daepi, id: \.0) { d in
                    NavigationLink(value: DulreoHwamyeon.mokrok(.daepiso(d.0, d.1))) { Text(d.0) }
                        .buttonStyle(KeunDanchu())
                }
            }
            .padding()
        }
        .sokHwamyeon("대피소")
    }
}

/// 찾은 곳 목록 — 다섯 개씩, 이름을 두드리면 그곳으로, 전화는 같은 줄의 동작
struct GotMokrokView: View {
    let cheo: GotCheo
    @State private var mokrok: [Got]?
    @State private var mot = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let l = mokrok, !l.isEmpty {
                    Mokrok5(l) { g in
                        Button(g.julMal) { gotEuroGagi(g.jangso) }
                            .buttonStyle(KeunDanchu())
                            .accessibilityAction(named: "전화 걸기") { jeonhwa(g) }
                            .accessibilityAction(named: "주소 듣기") { SoriEngine.shared.mal(g.juso.isEmpty ? "주소가 없습니다." : g.juso) }
                            .contextMenu {
                                if !g.jeonhwa.isEmpty { Button("전화 걸기 — \(g.jeonhwa)") { jeonhwa(g) } }
                                Button("주소 듣기") { SoriEngine.shared.mal(g.juso) }
                            }
                    }
                    Text("이름을 두드리시면 그곳까지 안내합니다. 전화와 주소는 이름에서 위아래로 쓸어 고르십시오.").font(.body)
                } else if !mot.isEmpty {
                    Text(mot).font(.title3)
                    Button("다시 찾기") { chatgi() }.buttonStyle(KeunDanchu())
                } else {
                    Text("\(cheo.jemok)을 찾는 중입니다.").font(.title3)
                }
            }
            .padding()
        }
        .sokHwamyeon(cheo.jemok)
        .task { if mokrok == nil { chatgi() } }
    }

    private func jeonhwa(_ g: Got) {
        let b = g.jeonhwa.filter { $0.isNumber }
        guard !b.isEmpty, let u = URL(string: "tel:" + b) else { SoriEngine.shared.mal("이곳은 전화번호가 없습니다."); return }
        UIApplication.shared.open(u)
    }

    private func chatgi() {
        mot = ""
        guard WichiEngine.shared.jigeum != nil else {
            mot = "아직 위치를 잡는 중입니다. 잠시 뒤 다시 찾기를 눌러 주십시오."
            SoriEngine.shared.mal(mot)
            return
        }
        Task {
            let r: [Got]?
            switch cheo {
            case .dulle(let j): r = await Dulreo.dulle(j)
            case .bapjip(let g): r = await Dulreo.bapjip(g.id)
            case .daepiso(_, let n): r = await Dulreo.daepiso(n)
            case .gabol(let a): r = await Dulreo.gabol(a)
            }
            await MainActor.run {
                guard let r = r else {
                    mot = "찾지 못했습니다. 통신이 끊겼을 수 있습니다."
                    SoriEngine.shared.mal(mot)
                    return
                }
                if r.isEmpty {
                    if case .daepiso(let i, _) = cheo {
                        mot = "\(i) 자료를 아직 받아 오지 못했습니다. 행정안전부 대피소 자료가 들어오는 대로 이 자리에서 알려 드립니다. 급하실 때는 가까운 주민센터나 학교 운동장으로 가시고, 둘레 찾기의 공공기관에서 주민센터를 찾으실 수 있습니다."
                    } else {
                        mot = "가까이에서 \(cheo.jemok)을 찾지 못했습니다."
                    }
                    SoriEngine.shared.mal(mot)
                    return
                }
                mokrok = r
                SoriEngine.shared.mal("\(cheo.jemok) \(r.count)곳을 가까운 차례로 찾았습니다.", .jeongbo)
                Girok.shared.namgi("dulreo_mokrok", ["cheo": cheo.jemok, "su": r.count])
            }
        }
    }
}

// MARK: 고장 이야기

struct GojangView: View {
    @ObservedObject private var s = Seoljeong.shared
    @State private var mal = ""
    @State private var batneun = false
    @AccessibilityFocusState private var malChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button(batneun ? "알아보는 중입니다" : "지금 이 고장 이야기 듣기") { deutgi() }
                    .buttonStyle(KeunDanchu())
                if !mal.isEmpty {
                    Text(mal).font(.title3).accessibilityFocused($malChojeom)
                }
                Toggle(isOn: $s.gojangJadong) { Text("차 안에서 고장이 바뀌면 들려 주기").font(.title3.weight(.semibold)) }
                    .padding(.horizontal, 4)
                    .frame(minHeight: 60)
            }
            .padding()
        }
        .sokHwamyeon("고장 이야기")
    }

    private func deutgi() {
        guard !batneun else { return }
        guard WichiEngine.shared.jigeum != nil else { SoriEngine.shared.mal("아직 위치를 잡는 중입니다."); return }
        batneun = true
        Task {
            let r = await Dulreo.gojang()
            await MainActor.run {
                batneun = false
                mal = r?.0 ?? "나스에 닿지 못했습니다. 통신을 확인해 주십시오."
                SoriEngine.shared.mal(mal)
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { malChojeom = true }
            }
        }
    }
}

// MARK: 사진 읽어 주기

struct SajinView: View {
    @State private var kamera = false
    @State private var sajin: UIImage?
    @State private var geul = ""
    @State private var ilneun = false
    @AccessibilityFocusState private var geulChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button(ilneun ? "사진을 읽고 있습니다" : (sajin == nil ? "사진 찍어 읽어 주기" : "다시 찍기")) {
                    guard !ilneun else { return }
                    if UIImagePickerController.isSourceTypeAvailable(.camera) { kamera = true }
                    else { SoriEngine.shared.mal("이 기기에서는 카메라를 쓸 수 없습니다.") }
                }
                .buttonStyle(KeunDanchu())
                if !geul.isEmpty {
                    Text(geul).font(.title3).accessibilityFocused($geulChojeom)
                }
                if sajin != nil && !ilneun {
                    Button("더 자세히 읽어 주기") { bonaegi("jasehi") }.buttonStyle(KeunDanchu())
                    Button("글씨만 읽어 주기 — 폰 안에서") { geulssi() }.buttonStyle(KeunDanchu())
                }
                Button("사람에게 물어보기 — 긴급통화서비스") {
                    TabGil.shared.tab = 0
                    GilGil.shared.cheotHwamyeon()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.gingeup) }
                }
                .buttonStyle(KeunDanchu())
            }
            .padding()
        }
        .sokHwamyeon("사진 읽어 주기")
        .fullScreenCover(isPresented: $kamera) {
            KameraJjikgi { img in
                kamera = false
                if let img = img {
                    sajin = img
                    bonaegi("boki")
                }
            }
            .ignoresSafeArea()
        }
    }

    private func allim(_ t: String) {
        geul = t
        SoriEngine.shared.mal(t)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { geulChojeom = true }
    }

    private func bonaegi(_ mode: String) {
        guard let img = sajin, let jpeg = SajinView.jureogi(img) else { return }
        ilneun = true
        SoriEngine.shared.mal("사진을 읽고 있습니다. 잠시만 기다려 주십시오.", .jeongbo)
        Girok.shared.namgi("sajin", ["mode": mode])
        Task {
            let (_, mal) = await Dulreo.sajin(jpeg, mode)
            await MainActor.run {
                ilneun = false
                allim(mal)
            }
        }
    }

    /// 글씨만 — 아이폰 자체 글자 읽기(폰 안, 통신 없이)
    private func geulssi() {
        guard let cg = sajin?.cgImage else { return }
        ilneun = true
        let req = VNRecognizeTextRequest { r, _ in
            let t = ((r.results as? [VNRecognizedTextObservation]) ?? []).compactMap { $0.topCandidates(1).first?.string }
            DispatchQueue.main.async {
                ilneun = false
                allim(t.isEmpty ? "사진에서 글씨를 찾지 못했습니다. 글씨에 더 가까이 대고 다시 찍어 보십시오." : t.joined(separator: " "))
            }
        }
        req.recognitionLanguages = ["ko-KR", "en-US"]
        req.recognitionLevel = .accurate
        req.usesLanguageCorrection = true
        let o = SajinView.banghyang(sajin?.imageOrientation ?? .up)
        DispatchQueue.global(qos: .userInitiated).async {
            try? VNImageRequestHandler(cgImage: cg, orientation: o, options: [:]).perform([req])
        }
        Girok.shared.namgi("sajin", ["mode": "geulja_pon"])
    }

    /// 긴 쪽 1600픽셀로 줄여 JPEG
    static func jureogi(_ img: UIImage) -> Data? {
        let k = 1600 / max(img.size.width, img.size.height)
        guard k < 1 else { return img.jpegData(compressionQuality: 0.7) }
        let sz = CGSize(width: img.size.width * k, height: img.size.height * k)
        let r = UIGraphicsImageRenderer(size: sz)
        return r.image { _ in img.draw(in: CGRect(origin: .zero, size: sz)) }.jpegData(compressionQuality: 0.7)
    }

    static func banghyang(_ o: UIImage.Orientation) -> CGImagePropertyOrientation {
        switch o {
        case .up: return .up
        case .down: return .down
        case .left: return .left
        case .right: return .right
        case .upMirrored: return .upMirrored
        case .downMirrored: return .downMirrored
        case .leftMirrored: return .leftMirrored
        case .rightMirrored: return .rightMirrored
        @unknown default: return .up
        }
    }
}

/// 아이폰 카메라로 한 장 찍기
struct KameraJjikgi: UIViewControllerRepresentable {
    let kkeut: (UIImage?) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(kkeut) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let p = UIImagePickerController()
        p.sourceType = .camera
        p.cameraCaptureMode = .photo
        p.delegate = context.coordinator
        return p
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let kkeut: (UIImage?) -> Void
        init(_ k: @escaping (UIImage?) -> Void) { kkeut = k }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            kkeut(info[.originalImage] as? UIImage)
        }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { kkeut(nil) }
    }
}

// MARK: 어디서 어디로 — 미리 들어 보기

struct MiriView: View {
    @State private var chulMal = ""
    @State private var mokMal = ""
    @State private var chul: Jangso?
    @State private var mok: Jangso?
    @State private var hubo: [Jangso] = []
    @State private var huboChul = false
    @State private var geul = ""
    @State private var batneun = false
    @AccessibilityFocusState private var geulChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !hubo.isEmpty {
                    Text(huboChul ? "출발지를 고르십시오." : "목적지를 고르십시오.").font(.title3)
                    Mokrok5(hubo) { j in
                        Button(j.ireum + (j.juso.isEmpty ? "" : " — " + j.juso)) { goreugi(j) }
                            .buttonStyle(KeunDanchu())
                    }
                } else {
                    TextField("가실 곳 — 이름이나 주소를 넣고 엔터", text: $mokMal)
                        .textFieldStyle(.roundedBorder)
                        .font(.title3)
                        .submitLabel(.search)
                        .onSubmit { chatgi(mokMal, chul: false) }
                    if let m = mok {
                        Button(batneun ? "미리 들어 보는 중입니다" : "\(m.ireum) — 미리 들어 보기") { deutgi() }
                            .buttonStyle(KeunDanchu())
                    }
                    if !geul.isEmpty {
                        Text(geul).font(.title3).accessibilityFocused($geulChojeom)
                        Button("다시 듣기") { SoriEngine.shared.mal(geul) }.buttonStyle(KeunDanchu())
                        if chul == nil, let m = mok {
                            Button("이 길로 가기 — \(m.ireum)") { gotEuroGagi(m) }.buttonStyle(KeunDanchu())
                        }
                    }
                    DisclosureGroup("출발지 바꾸기 — 지금은 \(chul?.ireum ?? "지금 내 자리")") {
                        VStack(alignment: .leading, spacing: 10) {
                            TextField("출발지 — 이름이나 주소를 넣고 엔터", text: $chulMal)
                                .textFieldStyle(.roundedBorder)
                                .font(.title3)
                                .submitLabel(.search)
                                .onSubmit { chatgi(chulMal, chul: true) }
                            if chul != nil {
                                Button("출발지를 지금 내 자리로") { chul = nil; geul = "" }.buttonStyle(KeunDanchu())
                            }
                        }
                    }
                    .font(.title3)
                }
            }
            .padding()
        }
        .sokHwamyeon("어디서 어디로")
    }

    private func chatgi(_ q0: String, chul c: Bool) {
        let q = q0.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        Task {
            let r = await Chatgi.jangso(q)
            await MainActor.run {
                guard let r = r, !r.isEmpty else {
                    SoriEngine.shared.mal(r == nil ? "찾지 못했습니다. 통신을 확인해 주십시오." : "\(q)을 찾지 못했습니다. 다른 이름으로 찾아 보십시오.")
                    return
                }
                huboChul = c
                hubo = r
            }
        }
    }

    private func goreugi(_ j: Jangso) {
        if huboChul { chul = j } else { mok = j }
        hubo = []
        geul = ""
        if mok != nil { deutgi() }
    }

    private func deutgi() {
        guard let m = mok, !batneun else { return }
        let c: Jangso
        if let x = chul {
            c = x
        } else if let w = WichiEngine.shared.jigeum {
            c = Jangso(ireum: "지금 내 자리", juso: "", lat: w.lat, lon: w.lon)
        } else {
            SoriEngine.shared.mal("아직 위치를 잡는 중입니다. 출발지를 바꾸셔도 됩니다.")
            return
        }
        batneun = true
        SoriEngine.shared.mal("미리 들어 보는 중입니다. 잠시만 기다려 주십시오.", .jeongbo)
        Task {
            let t = await Dulreo.miri(c, chul == nil, m)
            await MainActor.run {
                batneun = false
                geul = t ?? "미리 들어 보기를 셈하지 못했습니다. 다시 해 주십시오."
                SoriEngine.shared.mal(geul)
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { geulChojeom = true }
                Girok.shared.namgi("miri", [:])
            }
        }
    }
}

// MARK: 마실

struct MasilView: View {
    @ObservedObject private var m = Masil.shared
    @State private var mot = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !m.gojang.isEmpty {
                    Mokrok5(m.gojang) { g in
                        NavigationLink(value: DulreoHwamyeon.masilGo(g.id)) { Text(g.ireum) }
                            .buttonStyle(KeunDanchu())
                    }
                    Text("앉은자리에서 다녀오는 여행입니다. 가는 길과 그 고장의 것을 차례로 들으시고, 가끔은 겪는 일도 만나십니다.").font(.body)
                } else if mot {
                    Button("마실 이야기를 받지 못했습니다 — 다시 받기") { bureogi() }.buttonStyle(KeunDanchu())
                } else {
                    Text("마실 이야기를 받는 중입니다.").font(.title3)
                }
            }
            .padding()
        }
        .sokHwamyeon("마실")
        .task { if m.gojang.isEmpty { bureogi() } }
    }
    private func bureogi() {
        mot = false
        Task {
            let ok = await Masil.shared.bureogi()
            await MainActor.run { mot = !ok }
        }
    }
}

struct MasilGoView: View {
    let id: String
    @ObservedObject private var m = Masil.shared
    @State private var na = 0
    @State private var jog = ""
    @State private var quizDap: String?
    @AccessibilityFocusState private var chojeom: Bool

    private var go: MasilGojang? { m.gojang.first { $0.id == id } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let g = go {
                    if na >= g.madi.count {
                        Text("오늘 \(g.jjalb.isEmpty ? g.ireum : g.jjalb)을 다녀오셨습니다. 같은 곳을 다시 떠나셔도 만나시는 일이 달라집니다.")
                            .font(.title3)
                            .accessibilityFocused($chojeom)
                        Button("한 번 더 떠나기") { na = 0; madiMal() }.buttonStyle(KeunDanchu())
                    } else {
                        let md = g.madi[na]
                        if md.quiz {
                            if let d = quizDap {
                                Text(d).font(.title3).accessibilityFocused($chojeom)
                                daeumDanchu(g)
                            } else {
                                Text("여쭈어 봅니다. \(md.mut)").font(.title3).accessibilityFocused($chojeom)
                                ForEach(Array(md.bogi.enumerated()), id: \.offset) { i, t in
                                    Button(t) {
                                        let d = (i == md.dap) ? "맞았습니다. " + md.matda : "이렇습니다. " + md.teulida
                                        quizDap = d
                                        SoriEngine.shared.mal(d)
                                    }
                                    .buttonStyle(KeunDanchu())
                                }
                            }
                        } else {
                            Text(md.t + ". " + md.mal + (jog.isEmpty ? "" : " " + jog))
                                .font(.title3)
                                .accessibilityFocused($chojeom)
                            daeumDanchu(g)
                        }
                    }
                } else {
                    Text("이 마실 이야기를 찾지 못했습니다.").font(.title3)
                }
            }
            .padding()
        }
        .sokHwamyeon(go?.jjalb ?? "마실")
        .onAppear {
            if let g = go { SoriEngine.shared.mal("\(g.ireum). \(g.han). 떠납니다.") }
            madiMal()
        }
    }

    @ViewBuilder
    private func daeumDanchu(_ g: MasilGojang) -> some View {
        let kkeutin = na >= g.madi.count - 1
        Button(kkeutin ? "다녀왔습니다 — 마무리" : "다음으로 (\(na + 2)번째 마디)") {
            na += 1
            madiMal()
        }
        .buttonStyle(KeunDanchu())
        Text("모두 \(g.madi.count)마디 가운데 \(na + 1)번째입니다.").font(.body)
    }

    private func madiMal() {
        quizDap = nil
        jog = ""
        guard let g = go else { return }
        if na >= g.madi.count {
            SoriEngine.shared.mal("다녀오셨습니다.")
            Girok.shared.namgi("masil_kkeut", ["id": id])
        } else {
            let md = g.madi[na]
            if md.quiz {
                SoriEngine.shared.mal("여쭈어 봅니다. " + md.mut)
            } else {
                jog = m.hanjogak(md.gyeokda)
                SoriEngine.shared.mal(md.mal + (jog.isEmpty ? "" : " " + jog))
            }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { chojeom = true }
    }
}
