// 말로 그린 길(실내) — 앱 2.14.0 (빌드 260929-9, 이사장님 승인). 웹 길눈 malgil.html 0.1판과 같은 자료, 같은 흐름.
// 이사장님이 몸으로 익혀 말로 적어 두신 실내 길(보기: 사무실 ↔ 집)을 손대지 않고 따라 걷게 합니다.
// ① 폰이 걸음을 세고(움직임 감지, 곧바로) 방향을 보며(나침반) 정해진 걸음에 닿으면 다음 안내를 스스로 말함
// ② 세 걸음 앞에서 다음 할 일을 미리, 방향이 75도 넘게 틀어지면 멈추라고, 예상보다 많이 걸으면 확인하라고 알림
// ③ 엘리베이터는 8초 넘게 멈췄다가 다시 두 걸음 걸으면 내린 것으로 봄. 문처럼 걸음 수가 없는 대목은 네 걸음 걸으면 다음으로
// ④ 나스 음악 열쇠가 있는 폰에만 내줌(malgil.php, 지금은 이사장님 한 분). 말로는 "다시", "그만"
import SwiftUI
import CoreMotion
import Combine

struct MalgilMok: Identifiable, Hashable {
    let id: String
    let ireum: String
    let gagiSu: Int
    let ogiSu: Int
}

final class MalgilEngine: ObservableObject {
    static let shared = MalgilEngine()

    struct Dangye {
        let mal: String
        let juui: String
        var n: Int
        var jong: String     // georeum, gyedan, elev, meomchum, dochak, georeum_dochak
        let gak: Double?     // "3시 방향"이면 90, 오른쪽이 +
        var tan = false      // 엘리베이터를 탄 뒤(멈춘 것을 봄)
    }

    @Published private(set) var mok: [MalgilMok] = []
    @Published private(set) var geotneun = false
    @Published private(set) var jemok = ""
    @Published private(set) var jul = ""

    private var S: [Dangye] = []
    private var si = -1
    private var geol = 0
    private var ddo = false
    private var gidaeHeading: Double?
    private var bangMal = Date.distantPast
    private var majimak = ""

    private let mm = CMMotionManager()
    private var sm = 9.8, base = 9.8, wi = false
    private var lastStep = Date.distantPast
    private var meomchumSijak: Date?
    private var sigye: Timer?

    private var tk: String { Yeolsoe.ilgi("eumakTk") ?? "" }
    var yeolsoeItda: Bool { !tk.isEmpty }

    // MARK: 받기

    /// 목록 — 거짓이면 열쇠가 없거나 닿지 못함
    @discardableResult
    func mokBatgi() async -> Bool {
        guard yeolsoeItda, let u = BangsongEngine.juso("/jeom/malgil.php", [("a", "mok"), ("tk", tk)]),
              let dr = try? await URLSession.shared.data(from: u),
              let o = (try? JSONSerialization.jsonObject(with: dr.0)) as? [String: Any], (o["ok"] as? Bool) == true else { return false }
        let ls = ((o["list"] as? [[String: Any]]) ?? []).compactMap { r -> MalgilMok? in
            guard let id = r["id"] as? String else { return nil }
            return MalgilMok(id: id, ireum: (r["ireum"] as? String) ?? id,
                             gagiSu: Int(Chatgi.su(r["gagi"]) ?? 0), ogiSu: Int(Chatgi.su(r["ogi"]) ?? 0))
        }
        await MainActor.run { self.mok = ls }
        return true
    }

    // MARK: 걷기

    func sijak(_ m: MalgilMok, ogi: Bool) {
        Task {
            guard let u = BangsongEngine.juso("/jeom/malgil.php", [("a", "get"), ("id", m.id), ("tk", tk)]),
                  let dr = try? await URLSession.shared.data(from: u),
                  let o = (try? JSONSerialization.jsonObject(with: dr.0)) as? [String: Any],
                  let g = o["gil"] as? [String: Any],
                  let dan = g[ogi ? "ogi" : "gagi"] as? [[String: Any]], !dan.isEmpty else {
                await MainActor.run { SoriEngine.shared.mal("길을 받아 오지 못했습니다. 통신과 나스 음악 열쇠를 확인해 주십시오.") }
                return
            }
            await MainActor.run {
                self.S = dan.map { self.pulgi($0) }
                self.jemok = m.ireum + (ogi ? " — 오는 길" : " — 가는 길")
                self.geotneun = true
                WichiEngine.shared.sijak()
                self.umjikKyeogi()
                Girok.shared.namgi("malgil", ["id": m.id, "ogi": ogi])
                SoriEngine.shared.mal("\(self.jemok), 모두 \(self.S.count)단계로 안내합니다. 이제 손대실 일이 없습니다. 다시라고 하시면 지금 안내를 다시 들려 드립니다.")
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { self.dangye(0) }
            }
        }
    }

    func geuman(malHagi: Bool = true) {
        guard geotneun else { return }
        umjikKkeugi()
        geotneun = false
        si = -1
        jul = ""
        if malHagi { SoriEngine.shared.mal("말로 그린 길 안내를 그만둡니다.") }
    }

    func dasiDeutgi() {
        SoriEngine.shared.mal(majimak.isEmpty ? "들려 드릴 안내가 없습니다." : majimak)
    }

    // MARK: 단계 풀기(웹과 같은 규칙)

    private func pulgi(_ s: [String: Any]) -> Dangye {
        let t = (s["mal"] as? String) ?? ""
        var d = Dangye(mal: t, juui: (s["juui"] as? String) ?? "", n: 0, jong: "georeum", gak: MalgilEngine.sigak(t))
        let bocok: Double = { let b = Seoljeong.shared.bopok; return (b > 0.3 && b < 1.2) ? b : 0.7 }()
        if let g = Chatgi.su(s["georeum"]), g > 0 { d.n = Int(g) }
        else if let m = Chatgi.su(s["meter"]), m > 0 { d.n = Int((m / bocok).rounded()) }
        if t.contains("계단") && d.n == 0 { d.n = 3; d.jong = "gyedan" }
        let cheung = t.range(of: "층\\s*$|층 단추|단추로\\s*\\d+층|지하\\s*1층", options: .regularExpression) != nil
        if cheung && (t.contains("엘리베이터") || t.contains("단추")) && Chatgi.su(s["georeum"]) == nil { d.jong = "elev" }
        else if d.n == 0 { d.jong = "meomchum" }
        if (s["pyosi"] as? String) == "도착" { d.jong = d.n > 0 ? "georeum_dochak" : "dochak" }
        return d
    }

    /// "9시 방향", "10시 30분 방향" → 몸 기준 도(오른쪽 +)
    static func sigak(_ t: String) -> Double? {
        guard let r = t.range(of: "(\\d{1,2})\\s*시(\\s*30\\s*분)?\\s*방향", options: .regularExpression) else { return nil }
        let s = String(t[r])
        let h = Double(s.prefix { $0.isNumber }) ?? 12
        let ban = s.contains("30") ? 0.5 : 0
        let d = (h.truncatingRemainder(dividingBy: 12) + ban) * 30
        return d > 180 ? d - 360 : d
    }

    // MARK: 흐름

    private func dangye(_ i: Int) {
        guard geotneun, i < S.count else { return }
        si = i; geol = 0; ddo = false
        let k = S[i]
        var tt = (k.juui.isEmpty ? "" : k.juui.trimmingCharacters(in: CharacterSet(charactersIn: ". ")) + ". ") + k.mal
        if k.jong == "elev" { tt += ". 엘리베이터에서 내리셔서 걷기 시작하시면 다음을 알려 드리겠습니다." }
        mal(tt)
        jul = "\(i + 1)단계 — \(k.mal)"
        let h = WichiEngine.shared.nachimban
        if let g = k.gak, h >= 0 { gidaeHeading = (h + g + 360).truncatingRemainder(dividingBy: 360) }
        else if k.gak == nil { gidaeHeading = nil }
        if k.jong == "dochak" { kkeutnaegi(); return }
        meomchumSijak = nil
    }

    private func daeum() { if si + 1 < S.count { dangye(si + 1) } else { kkeutnaegi() } }

    private func kkeutnaegi() {
        umjikKkeugi()
        geotneun = false
        jul = ""
        Girok.shared.namgi("malgil_kkeut", [:])
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { SoriEngine.shared.mal("닿으셨습니다. 말로 그린 길 안내를 마칩니다.") }
    }

    private func georeum() {
        guard geotneun, si >= 0, si < S.count else { return }
        let k = S[si]
        geol += 1
        if k.jong == "georeum" || k.jong == "gyedan" || k.jong == "georeum_dochak" {
            if k.n >= 6 && geol == k.n - 3 && si + 1 < S.count {
                let dm = S[si + 1].mal.components(separatedBy: ",")[0].components(separatedBy: ".")[0]
                mal("세 걸음 뒤, " + dm)
            }
            let h = WichiEngine.shared.nachimban
            if let gd = gidaeHeading, h >= 0, geol >= 3, geol % 2 == 1, abs(DoeEngine.chai(h, gd)) > 75,
               Date().timeIntervalSince(bangMal) > 8 {
                bangMal = Date()
                mal("방향이 다릅니다. 멈추십시오. 가실 쪽을 다시 잡아 주십시오.")
            }
            if geol >= k.n {
                if k.jong == "georeum_dochak" { kkeutnaegi(); return }
                daeum()
            } else if Double(geol) > Double(k.n) * 1.5 + 3 && !ddo {
                ddo = true
                mal("예상보다 많이 걸으셨습니다. 멈추고 확인하십시오.")
            }
        } else if k.jong == "meomchum" {
            if geol >= 4 { daeum() }
        }
    }

    /// 엘리베이터처럼 멈췄다가 다시 걷는 대목 — 1초마다
    private func dolgi() {
        guard geotneun, si >= 0, si < S.count else { return }
        if S[si].jong == "elev" {
            if !S[si].tan, let m = meomchumSijak, Date().timeIntervalSince(m) > 8 { S[si].tan = true; geol = 0 }
            if S[si].tan && geol >= 2 { daeum() }
        }
    }

    // MARK: 걸음 감지 — 가속도(웹과 같은 셈, 곧바로)

    private func umjikKyeogi() {
        sm = 9.8; base = 9.8; wi = false; meomchumSijak = nil
        if mm.isAccelerometerAvailable {
            mm.accelerometerUpdateInterval = 1.0 / 50
            mm.startAccelerometerUpdates(to: .main) { [weak self] d, _ in
                guard let self = self, let a = d?.acceleration else { return }
                let m = sqrt(a.x * a.x + a.y * a.y + a.z * a.z) * 9.81
                let now = Date()
                self.sm = self.sm * 0.75 + m * 0.25
                self.base = self.base * 0.98 + m * 0.02
                if !self.wi && self.sm > self.base + 1.1 && now.timeIntervalSince(self.lastStep) > 0.33 {
                    self.wi = true; self.lastStep = now; self.georeum()
                } else if self.wi && self.sm < self.base + 0.3 { self.wi = false }
                if abs(self.sm - self.base) < 0.35 {
                    if self.meomchumSijak == nil { self.meomchumSijak = now }
                } else if now.timeIntervalSince(self.lastStep) < 1.5 { self.meomchumSijak = nil }
            }
        }
        sigye?.invalidate()
        sigye = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.dolgi() }
    }

    private func umjikKkeugi() {
        mm.stopAccelerometerUpdates()
        sigye?.invalidate(); sigye = nil
    }

    private func mal(_ t: String) {
        majimak = t
        SoriEngine.shared.mal(t)
    }
}

struct MalgilView: View {
    @ObservedObject private var e = MalgilEngine.shared
    @State private var sangtae = 0   // 0 받는 중, 1 받음, 2 못 받음
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if e.geotneun {
                    Button("다시 듣기 — \(e.jul)") { e.dasiDeutgi() }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($chojeom)
                    Button("안내 그만 — \(e.jemok)") { e.geuman() }.buttonStyle(KeunDanchu())
                } else if !e.yeolsoeItda {
                    Text("말로 그린 길은 나스 음악 열쇠를 넣은 폰에서만 쓰실 수 있습니다. 음악·방송 탭의 길 위의 음악에서 열쇠를 한 번 넣어 주십시오.")
                        .font(.title3)
                        .accessibilityFocused($chojeom)
                } else if sangtae == 0 {
                    Text("말로 그린 길을 받는 중입니다.").font(.title3)
                } else if e.mok.isEmpty {
                    Button("말로 그린 길을 받지 못했습니다 — 다시 받기") { batgi() }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($chojeom)
                } else {
                    ForEach(e.mok) { m in
                        Text(m.ireum).font(.title3.weight(.semibold))
                        Button("가는 길 따라 걷기 — \(m.gagiSu)단계") { e.sijak(m, ogi: false) }.buttonStyle(KeunDanchu())
                        if m.ogiSu > 0 {
                            Button("오는 길 따라 걷기 — \(m.ogiSu)단계") { e.sijak(m, ogi: true) }.buttonStyle(KeunDanchu())
                        }
                    }
                }
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("몸으로 익혀 말로 적어 둔 실내 길을 손대지 않고 따라 걷게 해 드립니다. 한 번 두드리시면 그 뒤로는 폰이 걸음을 세고 방향을 보며, 정해진 걸음에 닿으면 다음 안내를 스스로 말씀드립니다. 세 걸음 앞에서 다음 할 일을 미리 알려 드리고, 방향이 크게 틀어지면 멈추시라고, 예상보다 많이 걸으시면 확인하시라고 알려 드립니다. 엘리베이터는 멈췄다가 다시 걷기 시작하시면 내리신 것으로 봅니다. 말로 하기에서 다시라고 하시면 지금 안내를 다시, 그만이라고 하시면 멈춥니다. 폰을 몸에 지니고 평소처럼 걸으십시오.")
                        .font(.body)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("말로 그린 길")
        .onAppear {
            if e.yeolsoeItda && e.mok.isEmpty { batgi() } else { sangtae = 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { chojeom = true }
        }
    }

    private func batgi() {
        sangtae = 0
        Task {
            await e.mokBatgi()
            await MainActor.run { sangtae = 1 }
        }
    }
}
