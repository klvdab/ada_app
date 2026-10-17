// LVD투어스튜디오 앱 — 1.3.0판 (빌드 261018-T5, 방클, 이사장님 승인 「앱만 할 수 있는 것」 1·2·8번)
// ① 화면을 안 보고 답사하기 — 이어폰 단추(한 번: 현장 메모 녹음 시작·끝, 두 번: 사진, 세 번: 이동 구간 재기 시작·끝),
//    나침반 카메라 안에서는 음량 단추로도 찍음(iOS 17.2부터)
// ② 걸으며 저절로 재기 — 걸음 수·꺾은 방향(시계 방향)·계단 칸 수·오르막과 내리막을 재어 이동 구간 안내 초안을 씀
// ⑧ 판 기록 — 이 폰의 판·빌드·기기를 협회에 알리고, 최고관리자는 누가 어떤 판을 쓰는지 봄
// 웹에는 없는 것들입니다(폰 센서와 단추를 씀). 안드로이드 Sae130.kt 와 같은 잣대.
import SwiftUI
import UIKit
import AVFoundation
import MediaPlayer
import CoreMotion
import CoreLocation

// MARK: - 걸음 재기 셈 (안드로이드 GeoleumSem 과 같음)
struct GeoleumSem {
    struct Gul { var dir: Double?; var startS: Int; var startAlt: Double?; var endS = 0; var endAlt: Double? = nil; var turn = 0 }
    var guls: [Gul] = [Gul(dir: nil, startS: 0, startAlt: nil)]
    var candH: Double? = nil
    var candS = 0
    var candAlt: Double? = nil
    var lastS = 0
    var lastAlt: Double? = nil
    let sijak = Date()

    /// 두 방위의 차이(-180~180, 시계 방향이 +)
    static func cha(_ a: Double, _ b: Double) -> Double {
        var d = (a - b).truncatingRemainder(dividingBy: 360)
        if d > 180 { d -= 360 }
        if d < -180 { d += 360 }
        return d
    }
    /// 꺾은 각도를 시계 방향 시각으로 (90도 → 3시, -90도 → 9시, 180도 → 6시)
    static func sigak(_ d: Double) -> Int {
        var c = Int((d / 30).rounded())
        if c <= 0 { c += 12 }
        return c > 12 ? c - 12 : c
    }
    mutating func geoleum(_ s: Int) { if s > lastS { lastS = s } }
    mutating func nopi(_ a: Double) {
        lastAlt = a
        if guls[guls.count - 1].startAlt == nil { guls[guls.count - 1].startAlt = a }
    }
    /// 방위가 들어올 때마다 — 50도 넘게 돌아서 네 걸음 넘게 그 쪽으로 가면 꺾은 것으로 셈
    mutating func bang(_ h: Double) {
        var g = guls[guls.count - 1]
        guard let d0 = g.dir else { g.dir = h; guls[guls.count - 1] = g; return }
        let d = GeoleumSem.cha(h, d0)
        if abs(d) < 50 {
            candH = nil
            g.dir = (d0 + d * 0.1 + 360).truncatingRemainder(dividingBy: 360)
            guls[guls.count - 1] = g
            return
        }
        guard let ch = candH else { candH = h; candS = lastS; candAlt = lastAlt; return }
        if abs(GeoleumSem.cha(h, ch)) >= 40 { candH = h; candS = lastS; candAlt = lastAlt; return }
        if lastS - candS >= 4 {
            g.endS = candS; g.endAlt = candAlt
            guls[guls.count - 1] = g
            var n = Gul(dir: h, startS: candS, startAlt: candAlt)
            n.turn = GeoleumSem.sigak(GeoleumSem.cha(h, d0))
            guls.append(n)
            candH = nil
        }
    }
    var bun: Int { max(1, Int((Date().timeIntervalSince(sijak) / 60).rounded())) }
    static func nopiMal(_ a: Double?, _ b: Double?, _ n: Int) -> String {
        guard let a, let b else { return "" }
        let dz = b - a
        if abs(dz) < 1.0 { return "" }
        if abs(dz) / Double(max(n, 1)) >= 0.08 { return ", 그 사이 계단 약 \(Int((abs(dz) / 0.16).rounded()))칸 " + (dz > 0 ? "오름" : "내림") }
        return ", " + (dz > 0 ? "오르막" : "내리막") + " 약 \(Int(abs(dz).rounded()))미터"
    }
    func geul() -> String {
        var all = guls
        all[all.count - 1].endS = lastS
        all[all.count - 1].endAlt = lastAlt
        var L: [String] = []
        for (i, g) in all.enumerated() {
            let n = max(0, g.endS - g.startS)
            var t: String
            if i == 0 { t = "출발할 때 바라본 쪽(12시 방향)으로 약 \(n)걸음" }
            else if g.turn == 6 { t = "6시 방향으로 되돌아서 약 \(n)걸음" }
            else { t = "\(g.turn)시 방향으로 꺾어 약 \(n)걸음" }
            t += GeoleumSem.nopiMal(g.startAlt, g.endAlt, n)
            L.append(t)
        }
        return "[걸으며 잰 초안 — 걸음과 계단 칸 수는 어림이니 현장에서 확인해 고치십시오] " + L.joined(separator: ". ") + ". 모두 약 \(lastS)걸음, \(bun)분 걸림."
    }
}

// MARK: - 걸으며 저절로 재기
@MainActor
final class GeoleumJaegi: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = GeoleumJaegi()
    @Published var jung = false
    @Published var jul = ""
    @Published var jeoljeoro: Bool = UserDefaults.standard.object(forKey: "geoleumAuto") as? Bool ?? true {
        didSet { UserDefaults.standard.set(jeoljeoro, forKey: "geoleumAuto") }
    }
    private(set) var spotId = ""
    private var sem = GeoleumSem()
    private let ped = CMPedometer()
    private let alt = CMAltimeter()
    private let lm = CLLocationManager()

    func sijak(_ s: Spot) {
        if jung { kkeut(neoki: true) }
        guard CMPedometer.isStepCountingAvailable() else { TourStore.shared.say("이 폰은 걸음을 셀 수 없습니다."); return }
        sem = GeoleumSem()
        spotId = s.id
        jung = true
        jul = "재는 중 — 0걸음"
        ped.startUpdates(from: Date()) { d, _ in
            guard let d else { return }
            let n = d.numberOfSteps.intValue
            Task { @MainActor in GeoleumJaegi.shared.geoleumOm(n) }
        }
        if CMAltimeter.isRelativeAltitudeAvailable() {
            alt.startRelativeAltitudeUpdates(to: .main) { d, _ in
                guard let d else { return }
                let v = d.relativeAltitude.doubleValue
                Task { @MainActor in GeoleumJaegi.shared.nopiOm(v) }
            }
        }
        lm.delegate = self
        lm.headingOrientation = .portrait
        if CLLocationManager.headingAvailable() { lm.headingFilter = 5; lm.startUpdatingHeading() }
        let k = TourStore.shared.kos
        Danchu.shared.malhae((k?.spotLab(s) ?? "") + " 이동 구간을 재기 시작합니다. 폰을 손에 들고 걸으십시오.")
    }
    func geoleumOm(_ n: Int) { guard jung else { return }; sem.geoleum(n); jul = "재는 중 — 약 \(n)걸음, 꺾은 곳 \(sem.guls.count - 1)군데" }
    func nopiOm(_ v: Double) { guard jung else { return }; sem.nopi(v) }
    func bangOm(_ h: Double) { guard jung else { return }; sem.bang(h) }
    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateHeading h: CLHeading) {
        let v = h.trueHeading >= 0 ? h.trueHeading : h.magneticHeading
        Task { @MainActor in GeoleumJaegi.shared.bangOm(v) }
    }
    nonisolated func locationManagerShouldDisplayHeadingCalibration(_ manager: CLLocationManager) -> Bool { false }

    /// 재기 끝 — neoki 면 잰 지점의 이동 구간 안내 칸 끝에 초안을 붙임
    func kkeut(neoki: Bool) {
        guard jung else { return }
        ped.stopUpdates()
        alt.stopRelativeAltitudeUpdates()
        lm.stopUpdatingHeading()
        jung = false
        jul = ""
        guard neoki else { TourStore.shared.say("재기를 그만두었습니다."); return }
        let st = TourStore.shared
        guard let k = st.kos, let s = k.spot(spotId) else { return }
        if sem.lastS < 5 { Danchu.shared.malhae("걸음이 거의 없어 넣지 않았습니다."); return }
        let t = sem.geul()
        let x = s.s("access")
        s.d["access"] = x.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? t : x + "\n" + t
        if Int(s.n("walkIn") ?? 0) == 0 { s.d["walkIn"] = sem.bun; k.syncMove() }
        st.bakkwim()
        Danchu.shared.malhae(k.spotLab(s) + " 이동 구간 안내에 잰 것을 넣었습니다. 약 \(sem.lastS)걸음, \(sem.bun)분입니다.")
        st.allim = k.spotLab(s) + " 이동 구간 안내에 넣었습니다. " + t
    }
}

// MARK: - 화면을 안 보고 답사하기 (이어폰 단추)
@MainActor
final class Danchu: ObservableObject {
    static let shared = Danchu()
    @Published var on: Bool = UserDefaults.standard.bool(forKey: "danchuOn")
    @Published var kameraYeolgi = false
    weak var kamera: JeoljeoroGi? = nil
    private var player: AVAudioPlayer?
    private var nul: [Any] = []
    private let syn = AVSpeechSynthesizer()
    private var nureum = 0
    private var nureumTask: Task<Void, Never>? = nil

    func kyeogi(_ v: Bool) {
        on = v
        UserDefaults.standard.set(v, forKey: "danchuOn")
        if v { buchigi(); malhae("이어폰 단추를 켰습니다. 한 번은 메모 녹음, 두 번은 사진, 세 번은 이동 구간 재기입니다.") }
        else { tteigi(); TourStore.shared.say("이어폰 단추를 껐습니다.") }
    }
    /// 앱이 「지금 재생 중」이 되어야 이어폰 단추가 이 앱으로 옴 — 소리 없는 소리를 되풀이해 틂
    func buchigi() {
        guard on else { return }
        UIApplication.shared.beginReceivingRemoteControlEvents()
        let s = AVAudioSession.sharedInstance()
        if !Nokeumgi.shared.jung { try? s.setCategory(.playback, mode: .spokenAudio) }
        try? s.setActive(true)
        if player == nil { player = try? AVAudioPlayer(data: Danchu.goyo()); player?.numberOfLoops = -1; player?.volume = 0.01 }
        player?.play()
        let c = MPRemoteCommandCenter.shared()
        for x in nul { c.togglePlayPauseCommand.removeTarget(x); c.playCommand.removeTarget(x); c.pauseCommand.removeTarget(x); c.nextTrackCommand.removeTarget(x); c.previousTrackCommand.removeTarget(x) }
        nul.removeAll()
        nul.append(c.togglePlayPauseCommand.addTarget { _ in Task { @MainActor in Danchu.shared.nulleum(1) }; return .success })
        nul.append(c.playCommand.addTarget { _ in Task { @MainActor in Danchu.shared.nulleum(1) }; return .success })
        nul.append(c.pauseCommand.addTarget { _ in Task { @MainActor in Danchu.shared.nulleum(1) }; return .success })
        nul.append(c.nextTrackCommand.addTarget { _ in Task { @MainActor in Danchu.shared.hagi(2) }; return .success })
        nul.append(c.previousTrackCommand.addTarget { _ in Task { @MainActor in Danchu.shared.hagi(3) }; return .success })
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [MPMediaItemPropertyTitle: "투어스튜디오 답사 단추", MPMediaItemPropertyArtist: "한 번 메모, 두 번 사진, 세 번 재기"]
    }
    func tteigi() {
        player?.stop()
        let c = MPRemoteCommandCenter.shared()
        for x in nul { c.togglePlayPauseCommand.removeTarget(x); c.playCommand.removeTarget(x); c.pauseCommand.removeTarget(x); c.nextTrackCommand.removeTarget(x); c.previousTrackCommand.removeTarget(x) }
        nul.removeAll()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }
    /// 한 번 누름을 0.6초 기다려 모음(폰에 따라 두 번·세 번이 한 번씩 따로 오는 경우)
    func nulleum(_ n: Int) {
        nureum += n
        nureumTask?.cancel()
        nureumTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 600_000_000)
            if Task.isCancelled { return }
            let m = Danchu.shared.nureum
            Danchu.shared.nureum = 0
            Danchu.shared.hagi(min(m, 3))
        }
    }
    /// 1: 메모 녹음 시작·끝, 2: 사진, 3: 이동 구간 재기 시작·끝
    func hagi(_ n: Int) {
        let st = TourStore.shared
        guard let k = st.kos, let s = st.cur, !k.isGyobon else { malhae("먼저 코스를 열고 지점을 골라 주십시오."); return }
        switch n {
        case 2:
            if let g = kamera { g.jjikgi(0) }
            else { malhae("나침반 카메라를 엽니다. " + k.spotLab(s) + "입니다."); kameraYeolgi = true }
        case 3:
            let gj = GeoleumJaegi.shared
            if gj.jung { gj.kkeut(neoki: true) } else { gj.sijak(s) }
        default:
            memo(k, s)
        }
    }
    private func memo(_ k: Kos, _ s: Spot) {
        let st = TourStore.shared
        let rec = Nokeumgi.shared
        if rec.jung && rec.galle == "memo" {
            guard let r = rec.stop() else { return }
            let w = rec.where_, sid = rec.spotId
            Gwan.ppi(); Gwan.deureul()
            Task { @MainActor in
                if let sp = st.kos?.spot(sid) { await st.memoMal(sp, where_: w, wav: r.0, sec: r.1) }
                Danchu.shared.buchigi()
                Danchu.shared.malhae("메모를 담았습니다. \(r.1)초.")
            }
            return
        }
        if rec.jung { malhae("연습 녹음 중입니다. 먼저 끝내 주십시오."); return }
        let kan = UserDefaults.standard.string(forKey: "memoKan") ?? "access"
        malhae(k.spotLab(s) + " " + MemoKan.ireum(kan) + ". 삐 소리 뒤에 말씀하십시오.")
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            Gwan.ppi()
            if let e = await rec.start(galle: "memo", spotId: s.id, where_: kan) { Danchu.shared.malhae(e) }
            else { st.allim = "녹음 중입니다. 다 말씀하시면 이어폰 단추를 한 번 누르십시오." }
        }
    }
    /// 화면을 안 보고 쓸 때는 폰이 소리 내어 알림(말소리를 켜 둔 경우)
    func malhae(_ t: String) {
        let st = TourStore.shared
        st.say(t)
        guard on, st.malsori, !Nokeumgi.shared.jung, !UIAccessibility.isVoiceOverRunning else { return }
        syn.stopSpeaking(at: .immediate)
        let u = AVSpeechUtterance(string: t)
        u.voice = AVSpeechSynthesisVoice(language: "ko-KR")
        syn.speak(u)
    }
    /// 소리 없는 1초짜리 wav
    static func goyo() -> Data {
        let rate: UInt32 = 8000, n: UInt32 = 8000
        var d = Data()
        func u32(_ v: UInt32) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 4)) }
        func u16(_ v: UInt16) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 2)) }
        d.append("RIFF".data(using: .ascii)!); u32(36 + n * 2); d.append("WAVE".data(using: .ascii)!)
        d.append("fmt ".data(using: .ascii)!); u32(16); u16(1); u16(1); u32(rate); u32(rate * 2); u16(2); u16(16)
        d.append("data".data(using: .ascii)!); u32(n * 2); d.append(Data(count: Int(n * 2)))
        return d
    }
}

/// 답사 탭에 붙는 1.3.0 묶음 — 이어폰 단추, 걸으며 저절로 재기
struct Danchu130Section: View {
    @EnvironmentObject var store: TourStore
    @ObservedObject private var d = Danchu.shared
    @ObservedObject private var gj = GeoleumJaegi.shared
    var body: some View {
        Section("화면 안 보고 답사하기 — 이어폰 단추") {
            Toggle("이어폰 단추로 답사하기", isOn: Binding(get: { d.on }, set: { d.kyeogi($0) }))
            Text("이어폰 단추를 한 번 누르면 현장 메모 녹음을 시작하고, 다시 한 번 누르면 끝납니다. 두 번 누르면 나침반 카메라가 열리고, 열린 뒤 두 번 누르면 찍습니다. 세 번 누르면 이동 구간 재기를 시작하거나 끝냅니다. 나침반 카메라 안에서는 음량 단추로도 찍습니다. 켜 두는 동안 다른 앱의 음악은 멈춥니다.")
                .font(.footnote)
        }
        Section("걸으며 저절로 재기 — 이동 구간 안내 초안") {
            Toggle("경계를 적으면 저절로 재기 시작", isOn: $gj.jeoljeoro)
            if gj.jung {
                Text(gj.jul).font(.headline)
                Button("도착 — 잰 것을 이동 구간 안내에 넣기") { gj.kkeut(neoki: true) }.font(.headline)
                Button("재기 그만두기 (넣지 않음)") { gj.kkeut(neoki: false) }
            } else if let s = store.cur {
                Button("여기부터 " + (store.kos?.spotLab(s) ?? "") + " 이동 구간 재기 시작") { gj.sijak(s) }
            }
            Text("걸음 수, 꺾은 방향(출발할 때 바라본 쪽을 12시로), 계단 칸 수, 오르막과 내리막을 재어 지금 지점의 이동 구간 안내 칸 끝에 초안으로 붙입니다. 폰을 손에 들고 걸으십시오. 걸음과 계단은 어림이니 현장에서 확인해 고치십시오.")
                .font(.footnote)
        }
    }
}

// MARK: - 판 기록
enum PanGirok {
    static func gigi() -> String {
        var u = utsname()
        uname(&u)
        let m = Mirror(reflecting: u.machine).children.compactMap { ($0.value as? Int8).flatMap { $0 == 0 ? nil : String(UnicodeScalar(UInt8(bitPattern: $0))) } }.joined()
        return m.isEmpty ? UIDevice.current.model : m
    }
    /// 들어온 뒤 한 번 — 이 폰의 판을 알림(지난번에 난 오류 한 줄도 함께)
    @MainActor static func alrigi() async {
        guard TourStore.shared.loggedIn else { return }
        let oryu = UserDefaults.standard.string(forKey: "oryuJul") ?? ""
        let j = await TourAPI.call("pan_ping", ["app": "ios", "pan": Pan.pan, "bildeu": Pan.bildeu,
                                                "os": "iOS " + UIDevice.current.systemVersion, "gigi": gigi(), "oryu": oryu])
        if TourAPI.ok(j) && !oryu.isEmpty { UserDefaults.standard.removeObject(forKey: "oryuJul") }
    }
    static func oryu(_ action: String, _ t: String) {
        if action == "pan_ping" || action == "ping" { return }
        let f = DateFormatter(); f.dateFormat = "MM-dd HH:mm"; f.locale = Locale(identifier: "ko_KR")
        UserDefaults.standard.set(f.string(from: Date()) + " " + action + ": " + String(t.prefix(120)), forKey: "oryuJul")
    }
    /// "1.2.10" 과 "1.3.0" 견주기
    static func deoSae(_ a: String, _ b: String) -> Bool {
        let x = a.split(separator: ".").map { Int($0) ?? 0 }, y = b.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(x.count, y.count) { let p = i < x.count ? x[i] : 0, q = i < y.count ? y[i] : 0; if p != q { return p > q } }
        return false
    }
}

struct PanJul: Identifiable { let id: String; let geul: String; let yet: Bool }

/// 최고관리자 — 누가 어떤 판을 쓰나
struct PanGirokView: View {
    @State private var rows: [PanJul]? = nil
    @State private var yoyak = ""
    var body: some View {
        List {
            Section { Button("다시 받기") { Task { await bulreogi() } } }
            if let rows {
                Section(yoyak) {
                    if rows.isEmpty { Text("아직 판을 알린 사람이 없습니다. 1.3.0판부터 들어오면 저절로 알립니다.") }
                    Jang(items: rows) { r in Text(r.geul).fixedSize(horizontal: false, vertical: true) }
                }
            } else { Text("불러오는 중입니다.") }
        }
        .navigationTitle("판 기록")
        .task { await bulreogi() }
    }
    static func appIreum(_ a: String) -> String { a == "ios" ? "아이폰" : a == "android" ? "안드로이드" : "웹" }
    static func ttae(_ s: String) -> String { String(s.replacingOccurrences(of: "T", with: " ").dropFirst(5).prefix(11)) }
    private func bulreogi() async {
        let j = await TourAPI.call("pan_list")
        guard TourAPI.ok(j) else { TourStore.shared.say(TourAPI.err(j)); rows = []; return }
        let L = (j["list"] as? [[String: Any]]) ?? []
        var choesin: [String: String] = [:]
        for o in L { for (a, v) in ((o["apps"] as? [String: Any]) ?? [:]) { let p = geulja((v as? [String: Any])?["pan"]); if PanGirok.deoSae(p, choesin[a] ?? "0") { choesin[a] = p } } }
        var out: [(String, PanJul)] = []
        var yet = 0, ios = 0, an = 0
        for o in L {
            let nm = geulja(o["name"]).isEmpty ? geulja(o["id"]) : geulja(o["name"])
            for (a, v) in ((o["apps"] as? [String: Any]) ?? [:]) {
                guard let x = v as? [String: Any] else { continue }
                let p = geulja(x["pan"])
                let old = PanGirok.deoSae(choesin[a] ?? "0", p)
                if old { yet += 1 }
                if a == "ios" { ios += 1 } else if a == "android" { an += 1 }
                var t = nm + " — " + PanGirokView.appIreum(a) + " " + p + "판 (빌드 " + geulja(x["bildeu"]) + ")"
                if old { t += " ★옛 판" }
                t += ", " + geulja(x["os"]) + ", " + geulja(x["gigi"]) + ", 마지막 " + PanGirokView.ttae(geulja(x["last"]))
                if !geulja(x["oryu"]).isEmpty { t += "\n최근 오류: " + geulja(x["oryu"]) }
                out.append((geulja(x["last"]), PanJul(id: geulja(o["id"]) + a, geul: t, yet: old)))
            }
        }
        rows = out.sorted { $0.0 > $1.0 }.map { $0.1 }
        yoyak = "아이폰 \(ios)명, 안드로이드 \(an)명" + (yet > 0 ? ", 옛 판을 쓰는 분 \(yet)명" : ", 모두 새 판")
        TourStore.shared.say(yoyak + "입니다.")
    }
}
