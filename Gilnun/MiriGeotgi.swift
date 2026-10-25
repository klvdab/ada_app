// 미리 걸어 보기 — 2.95.0 (261017-I49, 이사장님 승인 2026-10-17 「그러자」, 웹 길눈 mirigeot.html 1.0에서 옮김)
// 점지도를 나서기 전에 한 마디씩 들어 봅니다(가상체험, 2026-09-08 이사장님 발안).
//   ① 저장된 길의 표시를 걸음 마디로 풀어 「곧게 몇 걸음 갑니다」, 「3시 방향으로 꺾습니다」처럼 한 마디씩
//   ② 거꾸로(돌아오는 길로) 걸어 보기 — 따라 걷기와 같은 뜻 뒤집기(JeomGil.dwit)
//   ③ 요약만 듣기 — 꺾임·계단·건널목·턱·승강기·문만
//   방향은 늘 시계 방향으로 말함. 걸음 수는 내 보폭으로(재지 않으셨으면 미터로)
import SwiftUI

struct MiriMadi {
    let jong: String     // gan, pyo, kkeut
    let geul: String
    let m: Double
    let jungyo: Bool
}

enum MiriGeotgi {
    /// 내 보폭 — 지금 쓰는 쪽, 없으면 다른 쪽. 재지 않으셨으면 nil
    static var bopok: Double? {
        let s = Seoljeong.shared
        let a = s.bopokMode == "dongban" ? s.bopokDongban : s.bopokHonja
        let b = s.bopokMode == "dongban" ? s.bopokHonja : s.bopokDongban
        let v = a > 0.2 ? a : b
        return (v > 0.2 && v < 1.5) ? v : nil
    }

    static func georiMal(_ m: Double) -> String {
        if let b = bopok { return "\(max(1, Int((m / b).rounded())))걸음, 약 \(max(1, Int(m.rounded())))미터" }
        return "약 \(max(1, Int(m.rounded())))미터"
    }

    /// 꺾임 이름 → 시계 방향(「3시 방향으로 꺾임」과 옛 이름 모두)
    static func jjok(_ nm: String) -> String {
        if let r = nm.range(of: "(1[0-2]|[1-9])시", options: .regularExpression) { return String(nm[r]) }
        return (nm.contains("오른") || nm.contains("우회전")) ? "3시" : "9시"
    }

    static func jungyo(_ nm: String) -> Bool {
        nm.range(of: "꺾임|굽은 길 시작|계단|횡단보도|건널목|오름턱|내림턱|엘리베이터|에스컬레이터|문|입구|개찰구|되돌아가는 곳|낙상", options: .regularExpression) != nil
    }

    static func pyoMal(_ p: JeomPyo, _ kilyi: Double?) -> String {
        let nm = p.ireum
        let kkori = (kilyi ?? 0) > 1 ? " " + georiMal(kilyi!) + "입니다." : ""
        let cnt = (p.cnt ?? 0) > 0 ? " \(p.cnt!)칸입니다." : ""
        if nm.contains("꺾임") { return "\(jjok(nm)) 방향으로 꺾습니다." }
        // 2.109.0 (261025-I63) 자봉의 새 표시 굽은 길 — 몇 걸음에 걸쳐 둥글게 돌아가는 길
        if nm.contains("굽은 길 시작") {
            let si = nm.range(of: "(1[0-2]|[1-9])시", options: .regularExpression).map { String(nm[$0]) + " 방향으로 " } ?? ""
            return "\(si)천천히 굽은 길입니다." + ((p.cnt ?? 0) > 0 ? " 약 \(p.cnt!)걸음 이어집니다." : "")
        }
        if nm == "굽은 길 끝" { return "굽은 길이 끝납니다." }
        if nm.contains("올라가는 계단") { return "계단을 올라갑니다." + cnt }
        if nm.contains("내려가는 계단") { return "계단을 내려갑니다." + cnt }
        if nm.contains("계단") { return "계단입니다." + cnt }
        if nm.contains("신호등 없는 횡단보도") { return "신호등 없는 건널목을 건넙니다." + kkori }
        if nm.contains("횡단보도") || nm.contains("건널목") { return "건널목을 건넙니다." + kkori }
        if nm.contains("낙상 주의") {
            let k = nm.range(of: "(1[0-2]|[1-9])시", options: .regularExpression).map { " " + String(nm[$0]) + " 쪽을 조심하십시오." } ?? ""
            return "낙상 주의 구간입니다." + k + kkori
        }
        if nm.contains("오름턱") { return "올라서는 턱입니다. 발을 올리십시오." }
        if nm.contains("내림턱") { return "내려서는 턱입니다. 발을 내리십시오." }
        if nm.contains("점자블록 끊김") { return "여기서 점자블록이 끊깁니다." }
        if nm.contains("점자블록 있음") { return "여기서부터 점자블록이 있습니다." }
        if nm.contains("점자블록 다시 이어짐") { return "여기서 점자블록이 다시 이어집니다." }
        if nm.contains("조심할 곳") { return "조심하실 곳입니다." + ((p.mal ?? "").isEmpty ? "" : " " + p.mal!) }
        if nm == "길 폭 좁음" { return "여기부터 길 폭이 좁습니다." }
        if nm == "길 폭 보통" { return "여기부터 길 폭이 보통입니다." }
        if nm == "길 폭 넓음" { return "여기부터 길 폭이 넓습니다." }
        if nm == "되돌아가는 곳" { return "6시 방향으로 돌아섭니다." }
        if nm.contains("엘리베이터 올라감") { return "엘리베이터를 타고 올라갑니다." }
        if nm.contains("엘리베이터 내려감") { return "엘리베이터를 타고 내려갑니다." }
        if nm.contains("엘리베이터") { return "엘리베이터입니다." }
        if nm.contains("에스컬레이터 올라감") { return "에스컬레이터로 올라갑니다." }
        if nm.contains("에스컬레이터 내려감") { return "에스컬레이터로 내려갑니다." }
        if nm.contains("에스컬레이터") { return "에스컬레이터입니다." }
        if nm.contains("버스 정류장") { return "버스 정류장을 지납니다." }
        if nm.contains("개찰구") { return "지하철 개찰구입니다." }
        if nm.hasPrefix("지하철 탐") { return "지하철을 탑니다." }
        if nm.hasPrefix("지하철 내림") { return "지하철에서 내립니다." }
        if nm.hasPrefix("버스 탐") { return "버스를 탑니다." }
        if nm.hasPrefix("버스 내림") { return "버스에서 내립니다." }
        if nm.hasPrefix("문") || nm.contains("출입문") || nm.contains("입구") { return "문입니다." }
        return nm + "입니다." + ((p.mal ?? "").isEmpty ? "" : " " + p.mal!)
    }

    /// 저장된 길을 걸음 마디로 풂 — 자리점이 너무 적으면 nil
    static func madi(_ g: JeomGil) -> [MiriMadi]? {
        let pts = g.pts
        guard pts.count >= 3 else { return nil }
        var marks: [(p: JeomPyo, i: Int)] = g.marks.compactMap { m in
            guard let la = m.lat, let lo = m.lon, la != 0, lo != 0 else { return nil }
            var b = 0, bd = 1e9
            for (k, p) in pts.enumerated() {
                let d = WichiEngine.geori(la, lo, p.lat, p.lon)
                if d < bd { bd = d; b = k }
            }
            return (m, b)
        }
        marks.sort { $0.i < $1.i }
        func sai(_ a: Int, _ b: Int) -> Double {
            var s = 0.0
            var k = a
            while k < b && k < pts.count - 1 { s += WichiEngine.geori(pts[k].lat, pts[k].lon, pts[k + 1].lat, pts[k + 1].lon); k += 1 }
            return s
        }
        var out: [MiriMadi] = []
        var jeon = 0, i = 0
        while i < marks.count {
            let m = marks[i]
            var kkeutI = m.i
            var kilyi: Double?
            // 시작과 끝이 짝인 표시는 한 마디로
            if m.p.ireum.contains("시작") {
                // 같은 갈래의 끝(「횡단보도 건너기 시작」 ↔ 「횡단보도 건너기 끝」)을 찾음
                let meori = (m.p.ireum.components(separatedBy: "시작").first ?? "").trimmingCharacters(in: .whitespaces)
                var j = i + 1
                while j < marks.count {
                    let n2 = marks[j].p.ireum
                    if n2.contains("끝") && (meori.isEmpty || n2.hasPrefix(meori)) {
                        kkeutI = marks[j].i
                        kilyi = (m.p.dist ?? 0) > 0 ? m.p.dist! : sai(m.i, kkeutI)
                        i = j
                        break
                    }
                    j += 1
                }
            } else if m.p.ireum.contains("끝") {
                // 짝 없는 끝(앞에서 이미 묶였거나 시작이 빠진 것)은 따로 말하지 않음
                i += 1
                continue
            }
            let gan = sai(jeon, m.i)
            if gan >= 2 { out.append(MiriMadi(jong: "gan", geul: "곧게 " + georiMal(gan) + " 갑니다.", m: gan, jungyo: false)) }
            out.append(MiriMadi(jong: "pyo", geul: pyoMal(m.p, kilyi), m: 0, jungyo: jungyo(m.p.ireum)))
            jeon = kkeutI
            i += 1
        }
        let kkeutGan = sai(jeon, pts.count - 1)
        if kkeutGan >= 2 { out.append(MiriMadi(jong: "gan", geul: "곧게 " + georiMal(kkeutGan) + " 갑니다.", m: kkeutGan, jungyo: false)) }
        out.append(MiriMadi(jong: "kkeut", geul: "다 오셨습니다. " + (g.to.isEmpty ? "도착지" : g.to) + "입니다.", m: 0, jungyo: false))
        return out
    }

    static func yoyak(_ madi: [MiriMadi]) -> [String] {
        var nuk = 0.0
        var jul: [String] = []
        for x in madi {
            if x.jong == "gan" { nuk += x.m; continue }
            if x.jong == "kkeut" {
                jul.append(nuk < 1.5 ? "그리고 곧 다 오십니다." : "그리고 \(georiMal(nuk)) 더 가시면 다 오십니다.")
                continue
            }
            guard x.jungyo else { continue }
            jul.append((nuk < 1.5 ? "곧바로 " : georiMal(nuk) + " 뒤 ") + x.geul)
            nuk = 0
        }
        if jul.count > 8 { jul = Array(jul.prefix(7)) + ["그 뒤로도 몇 가지가 더 있습니다. 미리 걸어 보기로 하나씩 들어 보십시오."] }
        return jul
    }
}

/// 미리 걸어 보기 화면 — 길 고르기에서 들어옴
struct MiriGeotgiView: View {
    let j: JeomMok
    @State var dwit: Bool
    @State private var gil: JeomGil?
    @State private var madi: [MiriMadi] = []
    @State private var georum = -1          // -1 채비, 0~ 마디, -2 요약
    @State private var mot = ""
    @State private var jadong: Timer?
    @AccessibilityFocusState private var cheot: Bool

    private var gilIreum: String { dwit ? "\(j.to)에서 \(j.from)까지" : "\(j.from)에서 \(j.to)까지" }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !mot.isEmpty {
                    Text(mot).font(.title3).accessibilityFocused($cheot)
                } else if gil == nil {
                    Text("길을 불러오는 중입니다.").font(.title3)
                } else if georum == -2 {
                    yoyakHwamyeon
                } else if georum >= 0 && georum < madi.count {
                    madiHwamyeon
                } else {
                    chaebiHwamyeon
                }
            }
            .padding()
        }
        .sokHwamyeon("미리 걸어 보기")
        .task { if gil == nil { await bulleo() } }
        .onDisappear { jadongKkeugi() }
    }

    // MARK: 채비

    private var moduGeori: Double {
        let gan: Double = madi.filter { $0.jong == "gan" }.reduce(0.0) { $0 + $1.m }
        let d: Double = gil?.dist ?? 0
        return d > 0 ? d : gan
    }

    private var jjalbeunMal: String {
        let pyo = madi.filter { $0.jong == "pyo" }.count
        return "\(gilIreum). 모두 \(MiriGeotgi.georiMal(moduGeori))입니다. 길에서 만나실 것이 \(pyo)가지입니다." + (dwit ? " 돌아오는 길입니다." : "")
    }

    private var chaebiHwamyeon: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button("미리 걸어 보기 시작 — 한 마디씩") { ganda(0) }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused($cheot)
            Text(jjalbeunMal).font(.title3)
            Button("요약만 듣기 — 꺾임과 계단, 건널목만") { yoyakBoigi() }
                .buttonStyle(KeunDanchu())
            Button(dwit ? "가는 길로 미리 걸어 보기 — \(j.from)에서 \(j.to)까지" : "돌아오는 길로 미리 걸어 보기 — \(j.to)에서 \(j.from)까지") {
                dwit.toggle()
                Task { await bulleo() }
            }
            .buttonStyle(KeunDanchu())
            Text("실제로 나서실 때는 공사나 세워 둔 물건으로 달라진 곳이 있을 수 있습니다.").font(.body)
        }
    }

    // MARK: 한 마디씩

    private var madiHwamyeon: some View {
        let x = madi[georum]
        return VStack(alignment: .leading, spacing: 12) {
            if x.jong == "kkeut" {
                Button("처음부터 다시 걸어 보기") { ganda(0) }
                    .buttonStyle(KeunDanchu())
                    .accessibilityFocused($cheot)
                Text(x.geul).font(.title2)
                Button(dwit ? "가는 길로 미리 걸어 보기" : "돌아오는 길로 미리 걸어 보기") {
                    dwit.toggle()
                    Task { await bulleo() }
                }
                .buttonStyle(KeunDanchu())
                Button("요약만 듣기") { yoyakBoigi() }.buttonStyle(KeunDanchu())
            } else {
                Button("다음 — \(georum + 2)번째 마디로") { ganda(georum + 1) }
                    .buttonStyle(KeunDanchu())
                    .accessibilityFocused($cheot)
                Text("\(georum + 1)번째 마디. " + x.geul).font(.title2)
                Button("방금 마디 다시 듣기") { SoriEngine.shared.mal(x.geul, .jeongbo) }
                    .buttonStyle(KeunDanchu())
                if georum > 0 {
                    Button("한 마디 뒤로") { ganda(georum - 1) }.buttonStyle(KeunDanchu())
                }
                DisclosureGroup("그 밖에 펼치기") {
                    VStack(alignment: .leading, spacing: 10) {
                        Button(jadong != nil ? "저절로 넘기기 멈추기" : "저절로 넘기기 — 5초마다") { jadongBakkugi() }
                            .buttonStyle(KeunDanchu())
                        Button("처음부터 다시") { ganda(0) }.buttonStyle(KeunDanchu())
                        Button("요약만 듣기") { yoyakBoigi() }.buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
            }
        }
    }

    // MARK: 요약

    private var yoyakHwamyeon: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button("한 마디씩 미리 걸어 보기") { ganda(0) }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused($cheot)
            Text("요약. " + gilIreum).font(.title3)
            ForEach(Array(MiriGeotgi.yoyak(madi).enumerated()), id: \.offset) { _, t in
                Text(t).font(.body)
            }
        }
    }

    // MARK: 움직임

    private func chojeom() {
        cheot = false
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { cheot = true }
    }

    private func bulleo() async {
        await MainActor.run { jadongKkeugi(); gil = nil; mot = ""; georum = -1 }
        guard let g0 = await Jeomjido.bulleoogi(j.id) else {
            await MainActor.run {
                mot = "길을 불러오지 못했습니다. 통신을 확인해 주십시오."
                SoriEngine.shared.mal(mot)
                chojeom()
            }
            return
        }
        let g = dwit ? g0.dwit() : g0
        await MainActor.run {
            guard let md = MiriGeotgi.madi(g) else {
                mot = "이 길에는 자리점이 너무 적어 미리 걸어 볼 수 없습니다."
                SoriEngine.shared.mal(mot)
                chojeom()
                return
            }
            gil = g
            madi = md
            georum = -1
            SoriEngine.shared.mal(jjalbeunMal, .jeongbo)
            Girok.shared.namgi("mirigeot", ["id": j.id, "dwit": dwit])
            chojeom()
        }
    }

    private func ganda(_ n: Int) {
        guard n >= 0 && n < madi.count else { jadongKkeugi(); return }
        georum = n
        let x = madi[n]
        let mal: String = x.jong == "kkeut" ? x.geul + " 모두 " + MiriGeotgi.georiMal(moduGeori) + "입니다." : x.geul
        SoriEngine.shared.mal(mal, .jeongbo)
        if x.jong == "kkeut" { jadongKkeugi() }
        chojeom()
    }

    private func yoyakBoigi() {
        jadongKkeugi()
        georum = -2
        SoriEngine.shared.mal("요약입니다. " + MiriGeotgi.yoyak(madi).joined(separator: " "), .jeongbo)
        chojeom()
    }

    private func jadongBakkugi() {
        if jadong != nil {
            jadongKkeugi()
            SoriEngine.shared.mal("저절로 넘기기를 멈췄습니다.", .jeongbo)
            return
        }
        SoriEngine.shared.mal("저절로 넘깁니다.", .jeongbo)
        jadong = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { _ in
            DispatchQueue.main.async { ganda(georum + 1) }
        }
    }

    private func jadongKkeugi() {
        jadong?.invalidate()
        jadong = nil
    }
}
