// LVD투어스튜디오 앱 — 앱 살림 (판 1.0.0, 빌드 261012-T1)
// 들어온 사람, 코스 목록, 열린 코스, 저절로 저장, 폰에 남겨 두기(인터넷이 끊겨도 잃지 않음), 앱 안내 말소리
import SwiftUI
import UIKit
import CoreLocation

@MainActor
final class TourStore: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = TourStore()

    @Published var me: [String: Any]? = nil
    @Published var courses: [[String: Any]] = []
    @Published var kos: Kos? = nil
    @Published var curId: String = "sOpen"          // 지금 지점(답사·지점 탭이 함께 씀)
    @Published var gen = 0                          // 코스 자료가 바뀌면 화면을 다시 그림
    @Published var saveMark = ""                    // 「저장함 10:42」 같은 표
    @Published var allim = ""                       // 화면에 남는 마지막 안내
    @Published var bappeum = false
    var base = ""                                   // 서버에 마지막으로 저장된 때(겹침 막기)
    private var lastSaved = ""
    private var saving = false
    private var saveAgain = false
    private var saveTask: Task<Void, Never>? = nil

    var loggedIn: Bool { !TourAPI.token.isEmpty && me != nil }
    var meName: String { geulja(me?["name"]) }
    var isMaster: Bool { geulja(me?["role"]) == "master" }
    var malsori: Bool { UserDefaults.standard.object(forKey: "malsori") as? Bool ?? true }

    // ---- 말소리 ----
    func say(_ t: String) {
        allim = t
        if malsori { UIAccessibility.post(notification: .announcement, argument: t) }
    }

    // ---- 들어오기 ----
    func sijak() async {
        guard !TourAPI.token.isEmpty else { return }
        let j = await TourAPI.call("me")
        if TourAPI.ok(j), let m = j["me"] as? [String: Any] { me = m; await loadCourses() }
        else if (j["offline"] as? Bool) == true { me = ["name": UserDefaults.standard.string(forKey: "meName") ?? "", "role": ""] }   // 인터넷이 없어도 폰에 남긴 코스는 열 수 있게
        else { TourAPI.token = "" }
    }
    func login(id: String, pw: String) async -> String? {
        let j = await TourAPI.call("login", ["id": id, "pw": pw])
        guard TourAPI.ok(j), let tk = j["token"] as? String else { return TourAPI.err(j) }
        TourAPI.token = tk
        me = (j["me"] as? [String: Any]) ?? ["id": id, "name": geulja(j["name"]), "role": geulja(j["role"])]
        UserDefaults.standard.set(meName, forKey: "meName")
        await loadCourses()
        return nil
    }
    func naga(mal: String = "") {
        let tk = TourAPI.token
        if !tk.isEmpty { Task { _ = await TourAPI.call("logout", tk: tk) } }
        TourAPI.token = ""; me = nil; kos = nil; courses = []
        if !mal.isEmpty { say(mal) }
    }

    // ---- 코스 ----
    func loadCourses() async {
        bappeum = true; defer { bappeum = false }
        let j = await TourAPI.call("list_courses")
        if TourAPI.ok(j) { courses = (j["courses"] as? [[String: Any]]) ?? [] }
        else { say(TourAPI.err(j)) }
    }
    func open(_ id: String) async -> Bool {
        bappeum = true; defer { bappeum = false }
        let j = await TourAPI.call("get_course", ["id": id])
        guard TourAPI.ok(j), let c = j["course"] as? [String: Any] else {
            if let dr = draft(id) { setKos(Kos(dr)); say("인터넷이 없어 폰에 남겨 둔 코스를 열었습니다. 인터넷이 되면 저장을 눌러 주십시오."); return true }
            say(TourAPI.err(j)); return false
        }
        base = geulja(c["updated"])
        let k = Kos(c)
        setKos(k)
        lastSaved = snap()
        if var dr = draft(id) {
            dr.removeValue(forKey: "savedAt")
            if TourStore.jsonGeul(Kos(dr).json()) != lastSaved {
                say("폰에 아직 올리지 못한 고친 것이 있습니다. 설정의 「폰에 남은 것 올리기」로 올리실 수 있습니다.")
            } else { dropDraft(id) }
        }
        UserDefaults.standard.set(id, forKey: "lastCourse")
        return true
    }
    private func setKos(_ k: Kos) { kos = k; curId = "sOpen"; gen += 1 }

    func newCourse(name: String, place: String) async -> Bool {
        let j = await TourAPI.call("new_course", ["name": name, "place": place])
        guard TourAPI.ok(j) else { say(TourAPI.err(j)); return false }
        let id = geulja((j["course"] as? [String: Any])?["id"] ?? j["id"])
        await loadCourses()
        return await open(id)
    }
    func copyGyobon() async -> Bool {
        let j = await TourAPI.call("copy_gyobon")
        guard TourAPI.ok(j) else { say(TourAPI.err(j)); return false }
        await loadCourses()
        say("교본을 내 코스로 복사했습니다.")
        return await open(geulja(j["id"]))
    }

    // ---- 저장 ----
    static func jsonGeul(_ o: [String: Any]) -> String {
        guard let d = try? JSONSerialization.data(withJSONObject: o, options: [.sortedKeys]) else { return "" }
        return String(data: d, encoding: .utf8) ?? ""
    }
    private func snap() -> String {
        guard let k = kos else { return "" }
        return TourStore.jsonGeul(k.json())
    }
    /// 고칠 때마다 부름 — 2초 동안 더 고치지 않으면 저장
    func bakkwim() {
        gen += 1
        saveTask?.cancel()
        saveTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            if Task.isCancelled { return }
            _ = await self?.save(quiet: true)
        }
    }
    @discardableResult
    func save(quiet: Bool) async -> Bool {
        guard let k = kos else { return false }
        if k.isGyobon { if !quiet { say("교본은 읽기만 됩니다. 코스 탭에서 내 코스로 복사한 뒤 쓰십시오.") }; return false }
        let s = snap()
        keepDraft(k)
        if quiet && s == lastSaved { return true }
        if saving { saveAgain = true; return true }
        saving = true
        var j = await TourAPI.call("save_course", ["course": k.json(), "base": base])
        if (j["conflict"] as? Bool) == true {
            // 다른 기기에서 먼저 저장된 경우 — 웹과 같이 지금 고친 것으로 맞춤(앞 것은 판으로 남아 판 되돌리기로 꺼낼 수 있음)
            base = geulja(j["updated"]).isEmpty ? base : geulja(j["updated"])
            j = await TourAPI.call("save_course", ["course": k.json(), "base": base, "force": true])
        }
        saving = false
        if TourAPI.ok(j) {
            base = geulja(j["updated"])
            lastSaved = s
            dropDraft(k.id)
            saveMark = "저장함 " + String(base.replacingOccurrences(of: "T", with: " ").dropFirst(11).prefix(5))
            if !quiet { say("저장했습니다.") }
        } else {
            saveMark = "저장 못 함 — 폰에 남겨 두었습니다"
            say("저장하지 못했습니다. 고친 것은 폰에 남겨 두었습니다. " + TourAPI.err(j))
        }
        if saveAgain { saveAgain = false; await save(quiet: true) }
        return TourAPI.ok(j)
    }

    // ---- 폰에 남겨 두기 ----
    private func draftURL(_ id: String) -> URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("draft", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent(id.replacingOccurrences(of: "/", with: "_") + ".json")
    }
    private func keepDraft(_ k: Kos) {
        var o = k.json()
        let f = ISO8601DateFormatter()
        o["savedAt"] = f.string(from: Date())
        if let d = try? JSONSerialization.data(withJSONObject: o) { try? d.write(to: draftURL(k.id), options: .atomic) }
    }
    private func dropDraft(_ id: String) { try? FileManager.default.removeItem(at: draftURL(id)) }
    func draft(_ id: String) -> [String: Any]? {
        guard let d = try? Data(contentsOf: draftURL(id)) else { return nil }
        return try? JSONSerialization.jsonObject(with: d) as? [String: Any]
    }
    func draftOllim() async {
        guard let k = kos, let dr = draft(k.id) else { say("폰에 남은 것이 없습니다."); return }
        var o = dr; o.removeValue(forKey: "savedAt")
        setKos(Kos(o))
        lastSaved = ""
        if await save(quiet: false) { say("폰에 남은 것을 올렸습니다.") }
    }

    // ---- 지금 지점 ----
    var cur: Spot? { kos?.spot(curId) }
    func go(_ s: Spot) { curId = s.id; gen += 1 }

    // ---- 자리(위치) ----
    private let lm = CLLocationManager()
    private var jariKkeut: [(CLLocation?) -> Void] = []
    func jari(_ kkeut: @escaping (CLLocation?) -> Void) {
        lm.delegate = self
        lm.desiredAccuracy = kCLLocationAccuracyBest
        jariKkeut.append(kkeut)
        if lm.authorizationStatus == .notDetermined { lm.requestWhenInUseAuthorization() }
        lm.requestLocation()
    }
    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let l = locations.last
        Task { @MainActor in let k = self.jariKkeut; self.jariKkeut = []; k.forEach { $0(l) } }
    }
    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in let k = self.jariKkeut; self.jariKkeut = []; k.forEach { $0(nil) } }
    }
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in if !self.jariKkeut.isEmpty { manager.requestLocation() } }
    }

    static func r6(_ x: Double) -> Double { (x * 1e6).rounded() / 1e6 }

    /// 여기서부터 다음 지점 — 앞 지점의 끝과 다음 지점의 시작을 이 자리·이 때로 적음(웹 boundGo 와 같음)
    func boundGo() {
        guard let k = kos, let c = cur else { return }
        if k.isGyobon { say("교본은 읽기만 됩니다. 코스 탭에서 내 코스로 복사한 뒤 쓰십시오."); return }
        guard let nx = k.spotNext(c) else { say("클로징 지점이 마지막입니다. 클로징 지점의 어디까지 칸에 해산하는 자리를 적어 주십시오."); return }
        markBound(c, nx)
        go(nx)
        var m = "여기서부터 " + k.spotFull(nx) + "입니다. "
        m += k.spotFull(c) + "은 여기까지입니다."
        if c.s("to").trimmingCharacters(in: .whitespaces).isEmpty { m += " " + k.spotLab(c) + "의 어디까지 칸에 이 자리를 적어 두십시오." }
        m += nx.kind == "close" ? " 해산하는 자리에서 클로징을 마치십시오." : " 정지 현장영상해설 자리에 서시면 사진 찍기를 누르십시오."
        say(m)
        bakkwim()
    }
    /// 여기서부터 새 지점 — 지금 지점 바로 뒤에 넣고 이 자리를 경계로 적음(웹 addSpot 과 같음)
    func addHere(name: String) {
        guard let k = kos else { return }
        if k.isGyobon { say("교본은 읽기만 됩니다. 코스 탭에서 내 코스로 복사한 뒤 지점을 넣으십시오."); return }
        let s = k.addSpot(after: cur, name: name)
        let pv = k.spotPrev(s)
        markBound(pv, s)
        k.syncMove()
        go(s)
        var m = "여기서부터 " + k.spotFull(s) + "입니다. "
        if pv != nil { m += k.spotLab(pv) + "은 여기까지입니다. " }
        m += "지점은 모두 \(k.spots.count)곳입니다."
        if name.isEmpty { m += " 이름은 지점 탭에서 적으십시오." }
        say(m)
        bakkwim()
    }
    private func markBound(_ p: Spot?, _ q: Spot?) {
        let at = ISO8601DateFormatter().string(from: Date())
        p?.d["endAt"] = at; q?.d["startAt"] = at
        jari { [weak self] l in
            guard let l else { return }
            let pos: [String: Any] = ["lat": TourStore.r6(l.coordinate.latitude), "lng": TourStore.r6(l.coordinate.longitude), "at": at]
            p?.d["endPos"] = pos; q?.d["startPos"] = pos
            self?.bakkwim()
        }
    }
    /// 정지 현장영상해설 자리 — 처음 사진을 찍을 때 지점 자리를 적고, 앞 지점에서 걸어온 거리로 이동 시간을 잡음
    func spotPosHere(_ s: Spot, _ l: CLLocation?) {
        guard let k = kos, let l, s.lat == nil else { return }
        s.d["lat"] = TourStore.r6(l.coordinate.latitude); s.d["lng"] = TourStore.r6(l.coordinate.longitude)
        let p = k.spotPrev(s)
        if let m = metersBetween(k.posOf(p), k.posOf(s)) {
            s.d["distIn"] = m; s.d["walkIn"] = k.walkMinutes(m); k.syncMove()
            let w = k.walkMinutes(m)
            say("\(k.spotLab(p))에서 \(Int(m))미터 걸어오셨습니다. \(k.spotLab(s))의 이동 시간을 \(w)분으로 잡았습니다.")
        }
        bakkwim()
    }

    /// 사진 올리기 — 웹과 같은 자리(shots)에 같은 모양으로 적음
    func photo(_ img: UIImage, dir: String) async {
        guard let k = kos, let s = cur else { return }
        if k.isGyobon { say("교본은 읽기만 됩니다."); return }
        let im = img.keugi(1600)
        guard let jpg = im.jpegData(compressionQuality: 0.82) else { return }
        say("사진을 올립니다.")
        let l: CLLocation? = await withCheckedContinuation { c in jari { c.resume(returning: $0) } }
        spotPosHere(s, l)
        var shot: [String: Any] = ["f": "", "dir": dir, "garo": Int(im.size.width * im.scale), "sero": Int(im.size.height * im.scale),
                                   "at": ISO8601DateFormatter().string(from: Date()), "zoom": 1, "app": "ios"]
        if let la = s.lat, let lo = s.lng { shot["lat"] = la; shot["lng"] = lo } else { shot["lat"] = NSNull(); shot["lng"] = NSNull() }
        let j = await TourAPI.call("save_photo", ["c": k.id, "s": s.id, "ext": "jpg", "data": jpg.base64EncodedString()], timeout: 90)
        if TourAPI.ok(j) {
            shot["f"] = geulja(j["file"])
            var a = s.shots; a.append(shot); s.shots = a
            say(k.spotLab(s) + " 사진 \(a.count)번째, " + dir + " 올렸습니다.")
            bakkwim()
        } else {
            say("사진을 올리지 못했습니다. " + TourAPI.err(j))
        }
    }
}

extension UIImage {
    /// 긴 쪽을 n 점으로 줄임(올리는 시간과 나스 자리를 아낌)
    func keugi(_ n: CGFloat) -> UIImage {
        let w = size.width, h = size.height, m = max(w, h)
        guard m > n else { return self }
        let r = n / m, sz = CGSize(width: (w * r).rounded(), height: (h * r).rounded())
        let f = UIGraphicsImageRendererFormat(); f.scale = 1
        return UIGraphicsImageRenderer(size: sz, format: f).image { _ in draw(in: CGRect(origin: .zero, size: sz)) }
    }
}
