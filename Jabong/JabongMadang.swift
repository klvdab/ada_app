// 자봉 앱 — 교육 마당(2.22.0, 빌드 261014-I16, 대장클, 이사장님 승인 2026-10-14)
// ★이사장님: 앱을 깔고 처음에는 가볍게. 처음부터 문제를 풀라 하면 봉사하려는 마음에 찬물을 끼얹는다.
//   조금 해 보게 해서 의욕이 생기게 하고, 더 잘하고 싶으면 「이걸 배워 보세요」로 센스 있게.
// 그래서: 등록은 이름·연락처·네 자리 숫자만 → 「첫 길 그려 보기」 → 처음엔 세 가지만 →
//   첫 길을 올리면 큰 박수 → 봉사 탭에 「더 잘 그리고 싶으시면 2편」 → 내 기록 탭 교육 마당(동영상 넷, 약속, 실력 다지기, 배지)
// 동영상은 협회 나스(https://lvd.ada.or.kr/jabong/yeongsang/)에서 받아 앱 안에서 틉니다. 목록(mokrok.json)이 안 오면 앱에 든 목록으로.
import SwiftUI
import AVKit

struct JbDonghwa: Identifiable, Codable, Hashable {
    var beonho: Int
    var jemok: String
    var gil: String
    var pail: String
    var id: Int { beonho }
    var url: URL? { URL(string: JbMadang.jeojangso + pail) }
    var ireum: String { "\(beonho)편 \(jemok)" }
}

final class JbMadang: ObservableObject {
    static let shared = JbMadang()
    static let jeojangso = "https://lvd.ada.or.kr/jabong/yeongsang/"
    private let d = UserDefaults.standard

    /// 앱에 든 목록 — 나스 목록이 안 올 때
    static let gibon: [JbDonghwa] = [
        JbDonghwa(beonho: 1, jemok: "설치와 처음 등록", gil: "약 2분", pail: "jabong_1.mp4"),
        JbDonghwa(beonho: 2, jemok: "점지도 그리기", gil: "약 2분 20초", pail: "jabong_2.mp4"),
        JbDonghwa(beonho: 3, jemok: "부탁 길과 AI 밑그림", gil: "약 1분 50초", pail: "jabong_3.mp4"),
        JbDonghwa(beonho: 4, jemok: "점검과 보완", gil: "약 1분 10초", pail: "jabong_4.mp4")
    ]

    @Published private(set) var mok: [JbDonghwa] = JbMadang.gibon
    @Published private(set) var bon: Set<Int>          // 끝까지 보신 편
    @Published private(set) var cheotGil: Bool         // 첫 점지도를 올리심
    private var bateum = false

    private init() {
        bon = Set((d.array(forKey: "jb.donghwaBon") as? [Int]) ?? [])
        cheotGil = d.bool(forKey: "jb.cheotGil")
    }

    /// 이미 그려 올린 길이 있는 분(예전 판에서 쓰시던 분)은 첫 길을 마치신 것으로
    func cheotGilSalpigi() {
        guard !cheotGil else { return }
        if JabongNae.shared.geurinSu > 0 || JeomGeurigi.shared.geurinGil.contains(where: { $0.olim }) {
            cheotGil = true; d.set(true, forKey: "jb.cheotGil")
        }
    }

    /// 첫 길을 올리셨을 때 한 번만 참 — 큰 박수와 축하는 부르는 쪽에서
    func cheotGilMachim() -> Bool {
        guard !cheotGil else { return false }
        cheotGil = true; d.set(true, forKey: "jb.cheotGil")
        Girok.shared.namgi("jb_cheotgil", [:])
        return true
    }

    func boatda(_ n: Int) {
        guard !bon.contains(n) else { return }
        bon.insert(n)
        d.set(Array(bon).sorted(), forKey: "jb.donghwaBon")
    }

    func pyeon(_ n: Int) -> JbDonghwa? { mok.first { $0.beonho == n } }

    /// 나스 목록 받기 — 한 번 켤 때 한 번
    @MainActor func mokrokBatgi() async {
        guard !bateum, let u = URL(string: JbMadang.jeojangso + "mokrok.json") else { return }
        bateum = true
        var r = URLRequest(url: u, timeoutInterval: 10)
        r.cachePolicy = .reloadIgnoringLocalCacheData
        struct Dap: Decodable { var mok: [JbDonghwa] }
        guard let dr = try? await URLSession.shared.data(for: r),
              let j = try? JSONDecoder().decode(Dap.self, from: dr.0), !j.mok.isEmpty else { bateum = false; return }
        mok = j.mok.sorted { $0.beonho < $1.beonho }
    }

    /// 받은 배지 — 해 본 만큼 늘어남
    var baeji: [String] {
        var b: [String] = []
        if cheotGil { b.append("첫 점지도") }
        if JabongNae.shared.gyoyukDoem { b.append("약속 일곱 익힘") }
        if !mok.isEmpty && mok.allSatisfy({ bon.contains($0.beonho) }) { b.append("동영상 다 보심") }
        if JabongNae.shared.geurinSu >= 10 { b.append("길 열 개") }
        return b
    }

    /// 협회 점검의 고칠 곳 말에 맞는 동영상 — 걸음·꺾임·짝·문·목소리는 2편, 나머지는 4편
    static func doumPyeon(_ heum: [String]) -> Int {
        let t = heum.joined(separator: " ")
        for k in ["꺾", "걸음", "끊", "보폭", "짝", "끝", "문", "목소리", "토막", "계단", "건널목", "횡단보도"] where t.contains(k) { return 2 }
        return 4
    }
}

// MARK: 처음 걷는 분께 — 세 가지만(문제 없이)
struct CheotGilKan: View {
    static let sesgaji: [(String, String)] = [
        ("하나, 보폭부터", "내 보폭 재기로 한 번만 재 두시면 됩니다."),
        ("둘, 문 앞에서 시작", "출발하는 건물 문 앞에서 걷기 시작을, 도착한 곳 문 앞에서 다 걸었습니다를 누릅니다."),
        ("셋, 꺾이는 그 자리에서", "3시나 9시 방향으로 꺾는 바로 그 자리에서 꺾임을 누릅니다.")
    ]
    static var mal: String { "처음이시군요. 세 가지만 기억하시면 됩니다. " + sesgaji.map { "\($0.0). \($0.1)" }.joined(separator: " ") + " 나머지는 걸으시면서 폰이 알려 드립니다." }
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("처음이시군요 — 세 가지만 기억하세요").font(.title2.bold()).foregroundColor(Saek.nam)
            ForEach(CheotGilKan.sesgaji, id: \.0) { s in
                VStack(alignment: .leading, spacing: 4) {
                    Text(s.0).font(.title3.bold())
                    Text(s.1).font(.title3).fixedSize(horizontal: false, vertical: true)
                }.accessibilityElement(children: .combine)
            }
            Text("나머지는 걸으시면서 폰이 알려 드립니다. 서툴러도 괜찮습니다. 협회가 함께 손봅니다.")
                .font(.body).fixedSize(horizontal: false, vertical: true)
            Button("세 가지 말로 듣기") { SoriEngine.shared.modu_geodugi(); SoriEngine.shared.mal(CheotGilKan.mal) }.buttonStyle(KeunDanchu())
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(Saek.norang.opacity(0.18)))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Saek.nam, lineWidth: 2))
    }
}

// MARK: 내 기록 탭의 교육 마당
struct GyoyukMadangKan: View {
    @ObservedObject private var m = JbMadang.shared
    @ObservedObject private var nae = JabongNae.shared
    var body: some View {
        Text("교육 마당").font(.title2.bold()).accessibilityAddTraits(.isHeader).padding(.top, 8)
        Text("더 잘 그리고 싶으실 때 편하게 보세요. 한 편이 2분 안팎입니다.").font(.body).fixedSize(horizontal: false, vertical: true)
        ForEach(m.mok) { p in
            NavigationLink { JbDonghwaView(beonho: p.beonho) } label: {
                Text("\(p.ireum) — \(p.gil)" + (m.bon.contains(p.beonho) ? ", 다 보심" : ""))
            }.buttonStyle(KeunDanchu())
        }
        NavigationLink { YaksokBogiView() } label: { Text("점지도 일곱 가지 약속 보기") }.buttonStyle(KeunDanchu())
        NavigationLink { SilryeokView() } label: {
            Text(nae.gyoyukDoem ? "실력 다지기 — 아홉 문제, 다 맞히셨습니다" : "실력 다지기 — 아홉 문제, 하고 싶으실 때")
        }.buttonStyle(KeunDanchu())
        let b = m.baeji
        Text(b.isEmpty ? "내 배지 — 아직 없습니다. 첫 길을 올리시면 첫 배지를 드립니다." : "내 배지 \(b.count)개 — " + b.joined(separator: ", "))
            .font(.title3).fixedSize(horizontal: false, vertical: true)
    }
}

// MARK: 동영상 틀기 — 앱 안에서
struct JbDonghwaView: View {
    @State var beonho: Int
    @ObservedObject private var m = JbMadang.shared
    @State private var player: AVPlayer? = nil
    @State private var naoneunJung = false
    @State private var kkeutNam = false
    @State private var kkeutGamsi: NSObjectProtocol? = nil
    @AccessibilityFocusState private var chojeom: Bool

    private var p: JbDonghwa? { m.pyeon(beonho) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(p?.ireum ?? "동영상").font(.title2.bold()).accessibilityAddTraits(.isHeader).accessibilityFocused($chojeom)
                if kkeutNam {
                    Text("다 보셨습니다. 고맙습니다.").font(.title3.bold()).foregroundColor(Saek.nam)
                }
                if let pl = player {
                    VideoPlayer(player: pl)
                        .frame(maxWidth: .infinity, minHeight: 220).aspectRatio(16.0 / 9.0, contentMode: .fit)
                        .accessibilityLabel("동영상 화면")
                }
                Button(naoneunJung ? "멈춤" : "재생") { jaesaengMeomchum() }.buttonStyle(KeunDanchu())
                HStack(spacing: 12) {
                    Button("10초 뒤로") { olgigi(-10) }.buttonStyle(KeunDanchu())
                    Button("10초 앞으로") { olgigi(10) }.buttonStyle(KeunDanchu())
                }
                Button("처음부터 다시 보기") { teulgi() }.buttonStyle(KeunDanchu())
                if let n = m.mok.first(where: { $0.beonho > beonho }) {
                    Button("다음 편 — \(n.ireum)") { beonho = n.beonho; teulgi() }.buttonStyle(KeunDanchu())
                }
            }
            .padding()
        }
        .sokHwamyeon("교육 동영상")
        .onAppear {
            SoriEngine.shared.modu_geodugi()
            teulgi()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true }
        }
        .onDisappear {
            player?.pause()
            if let k = kkeutGamsi { NotificationCenter.default.removeObserver(k) }
        }
        .onReceive(JeonhwaGamsi.shared.$jeonhwaJung) { j in if j { player?.pause(); naoneunJung = false } }
    }

    private func teulgi() {
        player?.pause()
        if let k = kkeutGamsi { NotificationCenter.default.removeObserver(k); kkeutGamsi = nil }
        kkeutNam = false
        guard let u = p?.url else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        try? AVAudioSession.sharedInstance().setActive(true)
        let pl = AVPlayer(url: u)
        let n = beonho
        kkeutGamsi = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: pl.currentItem, queue: .main) { _ in
            naoneunJung = false
            kkeutNam = true
            m.boatda(n)
            Girok.shared.namgi("jb_donghwa_bon", ["pyeon": n])
            chojeom = true
        }
        player = pl
        pl.play(); naoneunJung = true
        Girok.shared.namgi("jb_donghwa_teum", ["pyeon": n])
    }

    private func jaesaengMeomchum() {
        guard let pl = player else { return }
        if naoneunJung { pl.pause() } else { pl.play() }
        naoneunJung.toggle()
    }

    private func olgigi(_ cho: Double) {
        guard let pl = player else { return }
        let t = max(0, pl.currentTime().seconds + cho)
        pl.seek(to: CMTime(seconds: t, preferredTimescale: 600))
    }
}

// MARK: 실력 다지기 — 하고 싶을 때만, 부드럽게
struct SilryeokView: View {
    @ObservedObject private var nae = JabongNae.shared
    @ObservedObject private var seol = Seoljeong.shared
    @State private var mi = 0
    @State private var allim = ""
    @State private var machim = false
    @State private var boneunJung = false
    @AccessibilityFocusState private var allimChojeom: Bool

    var body: some View {
        let m = JbGyoyuk.munje[min(mi, JbGyoyuk.munje.count - 1)]
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if !allim.isEmpty {
                    Text(allim).font(.title3.weight(.semibold)).foregroundColor(Saek.nam)
                        .fixedSize(horizontal: false, vertical: true).accessibilityFocused($allimChojeom)
                }
                if machim {
                    Text("약속 일곱 익힘 배지를 받으셨습니다.").font(.title2.bold()).foregroundColor(Saek.nam)
                    Button("처음부터 다시 풀기") { mi = 0; machim = false; munjeIlgi("다시 풀어 봅니다. ") }.buttonStyle(KeunDanchu())
                } else {
                    Text("문제 \(mi + 1) / \(JbGyoyuk.munje.count)").font(.headline).foregroundColor(.secondary).accessibilityHidden(true)
                    Text(m.q)
                        .font(.title.bold()).foregroundColor(Saek.nam).fixedSize(horizontal: false, vertical: true)
                        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Saek.norang.opacity(0.18)))
                    ForEach(Array(m.d.enumerated()), id: \.offset) { i, d in
                        Button("\(JbGyoyuk.beonho[i]). \(d)") { goreum(i) }.buttonStyle(KeunDanchu()).disabled(boneunJung)
                    }
                    Button("문제 다시 듣기") { munjeIlgi() }.buttonStyle(KeunDanchu())
                    NavigationLink { YaksokBogiView() } label: { Text("약속 먼저 보기") }.buttonStyle(KeunDanchu())
                }
            }
            .padding()
        }
        .sokHwamyeon("실력 다지기")
        .onAppear { munjeIlgi("실력 다지기입니다. 틀려도 괜찮습니다. 풀이를 들려 드리니 편하게 골라 보세요. ") }
    }

    private func allyeo(_ t: String) {
        allim = t
        allimChojeom = false
        if seol.malKyeojim {
            SoriEngine.shared.modu_geodugi()
            SoriEngine.shared.mal(t)
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { allimChojeom = true }
        }
    }

    private func munjeIlgi(_ apmal: String = "") {
        let m = JbGyoyuk.munje[min(mi, JbGyoyuk.munje.count - 1)]
        var t = apmal + "\(mi + 1)번 문제. " + m.q
        for (k, d) in m.d.enumerated() { t += " \(JbGyoyuk.beonho[k]), \(d)." }
        allyeo(t)
    }

    private func goreum(_ i: Int) {
        let m = JbGyoyuk.munje[mi]
        guard i == m.a else {
            allyeo("아쉽네요. " + m.h + " 한 번 더 골라 보세요.")
            return
        }
        Baksu.chigi()
        Jindong.hagi("arrive")
        if mi + 1 < JbGyoyuk.munje.count {
            mi += 1
            let ap = ["맞습니다! ", "잘하셨습니다! ", "역시! "][mi % 3]
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { munjeIlgi(ap) }
            return
        }
        machim = true
        boneunJung = true
        Baksu.chigi(keuge: true)
        allyeo("아홉 문제를 모두 맞히셨습니다! 점지도 박사님이십니다. 약속 일곱 익힘 배지를 드립니다.")
        Task {
            await nae.gyoyukMachim()
            await MainActor.run { boneunJung = false }
        }
    }
}
