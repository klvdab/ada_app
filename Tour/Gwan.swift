// LVD투어스튜디오 앱 — 1.2.0판 살림 (빌드 261017-T4, 방클, 이사장님 지시 「웹에 있는 모든 기능을 겉과 속이 같게」)
// 최고관리자 일(알림·누구의 자리·가입 신청·명부·반·진행판·채점표·보관소), 수업 진행판으로 가는 지금 상태(ping),
// 내려받기와 보내기, 코스 벗어남 알림, 지금 자리 따라가기
import SwiftUI
import UIKit
import CoreLocation
import AVFoundation
import AudioToolbox

@MainActor
final class Gwan: ObservableObject {
    static let shared = Gwan()

    // ---- 누구의 자리를 볼지 (최고관리자) ----
    @Published var asWho = ""
    @Published var asName = ""
    @Published var users: [[String: Any]] = []
    func boda(_ id: String, _ name: String) async {
        asWho = id; asName = name; TourAPI.asWho = id
        TourStore.shared.kos = nil
        await TourStore.shared.loadCourses()
        TourStore.shared.say(id.isEmpty ? "내 자리로 돌아왔습니다. 코스 \(TourStore.shared.courses.count)개입니다." : name + " 님 자리를 봅니다. 코스 \(TourStore.shared.courses.count)개입니다.")
    }
    func loadUsers() async {
        let j = await TourAPI.call("list_users")
        if TourAPI.ok(j) { users = (j["users"] as? [[String: Any]]) ?? [] }
    }

    // ---- 알림 (최고관리자) ----
    @Published var signups = 0
    @Published var reviews = 0
    @Published var finals = 0
    @Published var claude = 0
    var alertLine: String {
        if signups + reviews + finals + claude == 0 { return "기다리는 것이 없습니다." }
        var a: [String] = []
        if signups > 0 { a.append("기다리는 가입 신청 \(signups)건") }
        if reviews > 0 { a.append("봐 달라는 코스 \(reviews)건") }
        if finals > 0 { a.append("승인 기다리는 시나리오 \(finals)건") }
        if claude > 0 { a.append("클 초안이 온 요청 \(claude)건 (코스를 열어 클에게 대본 맡기기에서 받으십시오)") }
        return a.joined(separator: ", ") + "입니다."
    }
    func loadAlerts(tell: Bool) async {
        let j = await TourAPI.call("alerts")
        guard TourAPI.ok(j) else { return }
        signups = Int(sutja(j["signups"]) ?? 0); reviews = Int(sutja(j["reviews"]) ?? 0)
        finals = Int(sutja(j["finals"]) ?? 0); claude = Int(sutja(j["claude"]) ?? 0)
        if tell && signups + reviews + finals + claude > 0 { TourStore.shared.say(alertLine + " 코스 탭 알림 줄에서 골라 주십시오.") }
    }

    // ---- 반(수업 모둠) ----
    @Published var classes: [[String: Any]] = []
    @Published var curClass = ""
    func loadClasses(all: Bool = false) async {
        let j = await TourAPI.call("list_classes", all ? ["all": 1] : [:])
        if TourAPI.ok(j) { classes = (j["list"] as? [[String: Any]]) ?? []; curClass = geulja(j["cur"]) }
    }
    var curClassObj: [String: Any]? { classes.first { geulja($0["id"]) == curClass } }

    // ---- 수업 진행판으로 보내는 지금 상태 (웹 lvPing) ----
    var tab = 0
    var fails = 0
    var err = ""
    private var pingAt = Date.distantPast
    private var timer: Timer?
    func pingSijak() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { _ in Task { @MainActor in Gwan.shared.ping(force: true) } }
        ping(force: true)
    }
    func ping(force: Bool = false) {
        let st = TourStore.shared
        guard st.loggedIn, !st.isMaster else { return }
        if !force && Date().timeIntervalSince(pingAt) < 20 { return }
        pingAt = Date()
        let k = st.kos
        let stage: Int
        switch tab { case 1: stage = 2; case 2: stage = 3; case 3: stage = 5; default: stage = k == nil ? 0 : 1 }
        let sc = k == nil ? (tab == 4 ? "scHelp" : "scList") : (tab == 4 ? "scHelp" : "scWork")
        let body: [String: Any] = ["sc": sc, "stage": sc == "scWork" ? stage : 0, "cid": k?.id ?? "", "cname": k?.name ?? "",
                                   "last": "앱 " + String(st.allim.prefix(50)), "fails": fails, "err": err]
        Task { _ = await TourAPI.call("ping", body) }
    }

    // ---- 내려받기 (통행표로 연 주소의 파일을 이 폰에 받아 두기) ----
    static func naeryeogi(_ u: URL, ireum: String) async -> URL? {
        do {
            var r = URLRequest(url: u); r.timeoutInterval = 300
            r.setValue(TourAPI.userAgent, forHTTPHeaderField: "User-Agent")
            let (tmp, res) = try await URLSession.shared.download(for: r)
            if let h = res as? HTTPURLResponse, h.statusCode >= 400 { return nil }
            let dir = FileManager.default.temporaryDirectory.appendingPathComponent("naeryeo", isDirectory: true)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let to = dir.appendingPathComponent(ireum.replacingOccurrences(of: "/", with: "_"))
            try? FileManager.default.removeItem(at: to)
            try FileManager.default.moveItem(at: tmp, to: to)
            return to
        } catch { return nil }
    }
    static func juso(_ q: [String: String]) -> URL? {
        var c = URLComponents(string: TourAPI.base)
        c?.queryItems = q.map { URLQueryItem(name: $0.key, value: $0.value) }
        return c?.url
    }
    /// 내보내기 다섯 — 웹 exportOne 과 같음(나스 export 자리에 쓰고 그 파일을 받아 옴)
    static let NAEBO: [(kind: String, f: String, why: String)] = [
        ("guide", "대본집.txt", "현장영상해설사용 대본집"), ("braille", "점자대본.txt", "점자 대본"),
        ("notice", "참가자안내.txt", "참가자 안내문"), ("plan", "운영표.txt", "파견 운영표"), ("mapd", "지도자료.json", "지도 앱에 넣을 자료")]
    func naebonaegi(_ kind: String) async -> URL? {
        let st = TourStore.shared
        guard let k = st.kos, let m = Gwan.NAEBO.first(where: { $0.kind == kind }) else { return nil }
        let t: String
        switch kind { case "notice": t = Wonchik.noticeText(k); case "plan": t = Wonchik.planText(k); case "mapd": t = Wonchik.mapJson(k); default: t = Wonchik.guideText(k) }
        st.say(m.why + "을(를) 만드는 중입니다.")
        let j = await TourAPI.call("save_text", ["c": k.id, "f": m.f, "content": t, "utf8": kind == "mapd" ? 1 : 0])
        guard TourAPI.ok(j) else { st.say(TourAPI.err(j)); return nil }
        k.d["exported"] = true; st.bakkwim()
        let tk = await TourAPI.needTicket()
        guard let u = Gwan.juso(["action": "get_export", "c": k.id, "f": m.f, "dl": "1", "tk": tk]),
              let f = await Gwan.naeryeogi(u, ireum: k.name + "_" + m.f) else { st.say("만들었으나 이 폰으로 받지 못했습니다. 다시 눌러 주십시오."); return nil }
        st.say(m.why + "을(를) 만들어 이 폰에 받았습니다. 아래 보내기·저장하기를 누르면 파일 앱에 저장하거나 문자·카톡으로 보내실 수 있습니다.")
        return f
    }
    /// 코스 통째로 묶기 — 웹 makeBackup
    func tongjaero() async -> URL? {
        let st = TourStore.shared
        guard let k = st.kos else { return nil }
        st.say("코스를 통째로 묶는 중입니다. 사진이 많으면 조금 걸립니다.")
        let j = await TourAPI.call("make_backup", ["c": k.id], timeout: 300)
        guard TourAPI.ok(j) else { st.say(TourAPI.err(j)); return nil }
        let f = geulja(j["file"])
        let tk = await TourAPI.needTicket()
        guard let u = Gwan.juso(["action": "get_export", "c": k.id, "f": f, "dl": "1", "tk": tk]), let to = await Gwan.naeryeogi(u, ireum: f) else { st.say("묶었으나 이 폰으로 받지 못했습니다."); return nil }
        st.say("파일 \(Int(sutja(j["count"]) ?? 0))개, " + geulja(j["mb"]) + "MB 로 묶어 이 폰에 받았습니다. 아래 보내기·저장하기를 누르십시오.")
        return to
    }

    // ---- 코스 벗어남 알림 설정 (웹 OFF 와 같은 기본: 켜짐, 40미터, 20초) ----
    @Published var offOn: Bool = UserDefaults.standard.object(forKey: "offOn") as? Bool ?? true { didSet { UserDefaults.standard.set(offOn, forKey: "offOn") } }
    @Published var offM: Int = UserDefaults.standard.object(forKey: "offM") as? Int ?? 40 { didSet { UserDefaults.standard.set(offM, forKey: "offM") } }
    @Published var offS: Int = UserDefaults.standard.object(forKey: "offS") as? Int ?? 20 { didSet { UserDefaults.standard.set(offS, forKey: "offS") } }

    nonisolated static func ppi() { AudioServicesPlaySystemSound(1057) }
    nonisolated static func deureul() { AudioServicesPlaySystemSound(kSystemSoundID_Vibrate) }
}

/// 지금 자리를 계속 받는 부품 — 코스 벗어남 알림(답사 탭)과 지금 자리 따라가기(현장영상해설 화면)가 함께 씀
final class Jarigi: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let lm = CLLocationManager()
    @Published var jari: CLLocation? = nil
    @Published var heading: Double? = nil
    var bada: ((CLLocation) -> Void)?
    override init() { super.init(); lm.delegate = self; lm.desiredAccuracy = kCLLocationAccuracyBest; lm.distanceFilter = 2 }
    func sijak() {
        if lm.authorizationStatus == .notDetermined { lm.requestWhenInUseAuthorization() }
        lm.startUpdatingLocation()
        if CLLocationManager.headingAvailable() { lm.startUpdatingHeading() }
    }
    func meomchum() { lm.stopUpdatingLocation(); lm.stopUpdatingHeading() }
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let l = locations.last else { return }
        DispatchQueue.main.async { self.jari = l; self.bada?(l) }
    }
    func locationManager(_ manager: CLLocationManager, didUpdateHeading h: CLHeading) {
        let v = h.trueHeading >= 0 ? h.trueHeading : h.magneticHeading
        DispatchQueue.main.async { self.heading = v }
    }
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}
}

/// 코스 벗어남 알림 — 웹 offPos 와 같음(정확도 60미터 넘는 것은 버림, 기준을 정한 시간 넘게 벗어나면 띠·소리·떨림)
@MainActor
final class Beoseonam: ObservableObject {
    static let shared = Beoseonam()
    @Published var tti = ""
    private let jg = Jarigi()
    private var since: Date? = nil
    private var out = false
    private var lastSaid = Date.distantPast
    private var on = false
    func sijak() {
        guard Gwan.shared.offOn, let k = TourStore.shared.kos, !Wonchik.spotPts(k).isEmpty || Wonchik.pos(k) != nil else { return }
        if on { return }
        on = true; since = nil; out = false; tti = ""
        jg.bada = { [weak self] l in Task { @MainActor in self?.bwa(l) } }
        jg.sijak()
    }
    func meomchum() { on = false; jg.meomchum(); since = nil; out = false; tti = "" }
    private func bwa(_ l: CLLocation) {
        guard on, Gwan.shared.offOn, let k = TourStore.shared.kos else { return }
        let acc = l.horizontalAccuracy; if acc < 0 || acc > 60 { return }
        let me = (l.coordinate.latitude, l.coordinate.longitude)
        var pts = Wonchik.spotPts(k).map { $0.p }
        var nm = Wonchik.spotPts(k).map { "\($0.i + 1)번 " + $0.s.name }
        if pts.isEmpty, let p = Wonchik.pos(k) { pts = [p]; nm = [k.s("posName").isEmpty ? "기준 자리" : k.s("posName")] }
        if pts.isEmpty { return }
        let r = Wonchik.offDist(me, pts)
        if Double(r.d) > Double(Gwan.shared.offM) + min(acc, 25) {
            if since == nil { since = Date() }
            if let s0 = since, Date().timeIntervalSince(s0) >= Double(Gwan.shared.offS) && (!out || Date().timeIntervalSince(lastSaid) > 60) {
                out = true; lastSaid = Date()
                var t = "코스에서 \(r.d)미터 벗어났습니다."
                if let n = r.near { t += " 가장 가까운 지점은 " + nm[n] + ", 북쪽을 12시로 " + Wonchik.clock(Wonchik.brg(me, pts[n])) + " 방향 \(Wonchik.meter(me, pts[n]))미터입니다." }
                tti = t; TourStore.shared.say(t); Gwan.deureul(); Gwan.ppi()
            }
        } else {
            since = nil
            if out { out = false; tti = ""; TourStore.shared.say("코스로 돌아왔습니다.") }
        }
    }
}

/// 보내기·저장하기 시트 — 받은 파일을 파일 앱·문자·카톡으로
struct Bonaegi: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
