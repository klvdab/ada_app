// 나눔 탭과 설정 나머지의 알맹이 — 앱 2.9.0 (빌드 260928-11), 이사장님 승인(가1·나1).
// 나눔 마당(mulnanum.php), 걸음 나눔(nanum.php), 길 부탁하기(butak.php), 알림(gongji.php) — 웹 길눈과 같은 자료 창고, 나스는 그대로.
// 국가지점번호는 폰이 스스로 셈(통신 없이), 흔들면 긴급통화 또는 자리 번호, 서류 보관함은 폰 안에만.
import Foundation
import UIKit
import CoreMotion
import UserNotifications
import Combine

// MARK: 나스에 묻기 — 값은 글자·숫자 밖을 모두 퍼센트로

enum Nas {
    static func juso(_ pail: String, _ q: [(String, String)]) -> URL? {
        var hm = CharacterSet.alphanumerics
        hm.insert(charactersIn: "-._~")
        let s = (q + [("_", String(Int(Date().timeIntervalSince1970 * 1000)))])
            .map { "\($0.0)=\($0.1.addingPercentEncoding(withAllowedCharacters: hm) ?? "")" }.joined(separator: "&")
        return URL(string: "https://lvd.ada.or.kr/jeom/" + pail + "?" + s)
    }

    static func get(_ pail: String, _ q: [(String, String)]) async -> [String: Any]? {
        guard let u = juso(pail, q) else { return nil }
        var r = URLRequest(url: u)
        r.cachePolicy = .reloadIgnoringLocalCacheData
        r.timeoutInterval = 20
        guard let dr = try? await URLSession.shared.data(for: r),
              (dr.1 as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return (try? JSONSerialization.jsonObject(with: dr.0)) as? [String: Any]
    }

    static func postJson(_ pail: String, _ q: [(String, String)], _ bonmun: [String: Any]) async -> [String: Any]? {
        guard let u = juso(pail, q), let b = try? JSONSerialization.data(withJSONObject: bonmun) else { return nil }
        var r = URLRequest(url: u)
        r.httpMethod = "POST"
        r.httpBody = b
        r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        r.timeoutInterval = 20
        guard let dr = try? await URLSession.shared.data(for: r),
              (dr.1 as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return (try? JSONSerialization.jsonObject(with: dr.0)) as? [String: Any]
    }

    static func gul(_ v: Any?) -> String {
        if let s = v as? String { return s }
        if let n = v as? NSNumber { return n.stringValue }
        return ""
    }

    static func nal(_ v: Any?) -> String {
        guard let t = Chatgi.su(v), t > 0 else { return "" }
        let c = Calendar.current.dateComponents([.month, .day], from: Date(timeIntervalSince1970: t))
        return "\(c.month ?? 0)월 \(c.day ?? 0)일"
    }
}

// MARK: 나눔 마당

struct MulGeul: Identifiable, Hashable {
    let id: String
    let mul: String
    let got: String
    let mal: String
    let nugu: String
    let yeon: String
    let nal: String
    var julMal: String { mul + (got.isEmpty ? "" : " — " + got) + (mal.isEmpty ? "" : ". " + mal) + " · " + nal }
    var jaseMal: String { mul + ". " + mal + " 올리신 분은 " + (nugu.isEmpty ? "이름을 밝히지 않으셨습니다" : nugu) + ". 연락은 " + yeon + "." }
    var jeonhwa: String? {
        let b = yeon.filter { $0.isNumber || $0 == "+" }
        return b.count >= 8 ? b : nil
    }
}

enum Mulnanum {
    static func mok(_ jong: String) async -> [MulGeul]? {
        guard let j = await Nas.get("mulnanum.php", [("a", "list"), ("jong", jong)]) else { return nil }
        return ((j["rows"] as? [[String: Any]]) ?? []).map { x in
            MulGeul(id: Nas.gul(x["id"]), mul: Nas.gul(x["mul"]), got: Nas.gul(x["got"]), mal: Nas.gul(x["mal"]),
                    nugu: Nas.gul(x["nugu"]), yeon: Nas.gul(x["yeon"]), nal: Nas.nal(x["ttae"]))
        }
    }

    static func naerigi(_ id: String) async -> Bool {
        await Nas.get("mulnanum.php", [("a", "gam"), ("id", id)]) != nil
    }

    /// 올리기 — nil 이면 올림, 아니면 까닭
    static func olligi(jong: String, mul: String, mal: String, got: String, nugu: String, yeon: String) async -> String? {
        let j = await Nas.get("mulnanum.php", [("a", "put"), ("jong", jong), ("mul", mul), ("mal", mal), ("got", got), ("nugu", nugu), ("yeon", yeon)])
        if (j?["ok"] as? Bool) == true { return nil }
        return (j?["error"] as? String) ?? "올리지 못했습니다. 다시 해 보십시오."
    }
}

// MARK: 걸음 나눔

struct NanumDat: Hashable {
    let byeol: String
    let nugu: String
    let nal: String
    let geul: String
}

struct NanumGeul: Identifiable, Hashable {
    let id: String
    let byeol: String
    let nugu: String   // 자원봉사자·시각장애인
    let nal: String
    let geul: String
    let dat: [NanumDat]
    var meori: String { "\(byeol) · \(nugu) · \(nal)" }
}

enum GeoreumNanum {
    static var byeol: String {
        get { UserDefaults.standard.string(forKey: "gn.nanumByeol") ?? "" }
        set { UserDefaults.standard.set(newValue, forKey: "gn.nanumByeol") }
    }

    private static func nugu(_ m: String) -> String { m == "jabong" ? "자원봉사자" : "시각장애인" }

    /// 다섯 개씩 — (글, 모두 몇 개)
    static func mok(_ bu: Int) async -> ([NanumGeul], Int)? {
        guard let j = await Nas.get("nanum.php", [("a", "list"), ("bu", String(bu)), ("myeot", "5"), ("bo", "iyong")]),
              (j["ok"] as? Bool) == true else { return nil }
        let ls = ((j["rows"] as? [[String: Any]]) ?? []).map { x -> NanumGeul in
            let d = ((x["dat"] as? [[String: Any]]) ?? []).map { dd -> NanumDat in
                let m = Nas.gul(dd["mun"])
                return NanumDat(byeol: Nas.gul(dd["byeol"]), nugu: m == "kl" ? "길눈" : nugu(m), nal: Nas.gul(dd["nal"]), geul: Nas.gul(dd["geul"]))
            }
            return NanumGeul(id: Nas.gul(x["id"]), byeol: Nas.gul(x["byeol"]), nugu: nugu(Nas.gul(x["mun"])), nal: Nas.gul(x["nal"]),
                             geul: Nas.gul(x["geul"]), dat: d)
        }
        return (ls, Int(Chatgi.su(j["modu"]) ?? Double(ls.count)))
    }

    static func sseugi(byeol: String, geul: String, jam: String) async -> String? {
        let j = await Nas.postJson("nanum.php", [("a", "sseugi")], ["byeol": byeol, "geul": geul, "jam": jam, "mun": "iyong"])
        if (j?["ok"] as? Bool) == true { return nil }
        return (j?["msg"] as? String) ?? "올리지 못했습니다."
    }

    static func dat(id: String, byeol: String, geul: String, jam: String) async -> String? {
        let j = await Nas.postJson("nanum.php", [("a", "dat")], ["id": id, "byeol": byeol, "geul": geul, "jam": jam, "mun": "iyong"])
        if (j?["ok"] as? Bool) == true { return nil }
        return (j?["msg"] as? String) ?? "올리지 못했습니다."
    }

    /// 가리기 — (됨, 알림 말)
    static func garigi(id: String, jam: String) async -> (Bool, String) {
        guard let j = await Nas.get("nanum.php", [("a", "jiugi"), ("id", id), ("jam", jam)]) else { return (false, "닿지 못했습니다.") }
        return ((j["ok"] as? Bool) == true, (j["msg"] as? String) ?? "")
    }
}

// MARK: 길 부탁하기

struct ButakDat: Hashable {
    let id: String
    let mal: String
    let nugu: String
}

struct Butak: Identifiable, Hashable {
    let id: String
    let sin: String
    let min: String
    let mlat: Double
    let mlon: Double
    let ttae: String
    let mal: String
    let nugu: String
    let geori: Double
    let eonje: String
    let doen: Bool
    let gil: String
    let daetgeul: [ButakDat]

    var julMal: String {
        var t = "\(sin)에서 \(min)까지"
        if geori > 0 { t += " · 약 " + (geori >= 1000 ? String(format: "%.1f킬로미터", geori / 1000) : "\(Int(geori))미터") }
        if doen { t += gil.isEmpty ? " · 다 그려졌습니다" : " · 길눈님이 그려 주셨습니다" }
        return t + " · " + eonje
    }

    var jaseMal: String {
        var t = "\(sin)에서 \(min)까지. "
        if !ttae.isEmpty { t += "언제쯤 : \(ttae). " }
        if !mal.isEmpty { t += mal + ". " }
        if !nugu.isEmpty { t += "부탁하신 분 별명은 \(nugu). " }
        return t + "댓글 \(daetgeul.count)개."
    }
}

enum GilButak {
    static func mok() async -> [Butak]? {
        var q: [(String, String)] = [("a", "list")]
        if let w = WichiEngine.shared.jigeum { q += [("lat", String(format: "%.6f", w.lat)), ("lon", String(format: "%.6f", w.lon))] }
        guard let j = await Nas.get("butak.php", q) else { return nil }
        return ((j["rows"] as? [[String: Any]]) ?? []).map { x in
            let d = ((x["daetgeul"] as? [[String: Any]]) ?? []).map { ButakDat(id: Nas.gul($0["id"]), mal: Nas.gul($0["mal"]), nugu: Nas.gul($0["nugu"])) }
            return Butak(id: Nas.gul(x["id"]), sin: Nas.gul(x["sin"]), min: Nas.gul(x["min"]),
                         mlat: Chatgi.su(x["mlat"]) ?? 0, mlon: Chatgi.su(x["mlon"]) ?? 0,
                         ttae: Nas.gul(x["ttae"]), mal: Nas.gul(x["mal"]), nugu: Nas.gul(x["nugu"]),
                         geori: Chatgi.su(x["geori"]) ?? 0, eonje: Nas.nal(x["eonje"]),
                         doen: (x["doen"] as? Bool) ?? false, gil: Nas.gul(x["gil"]), daetgeul: d)
        }
    }

    static func olligi(chul: Jangso?, chulMal: String, mok: Jangso?, mokMal: String, ttae: String, mal: String, nugu: String) async -> String? {
        var q: [(String, String)] = [("a", "put"), ("sin", chul?.ireum ?? chulMal)]
        if let c = chul { q += [("slat", String(c.lat)), ("slon", String(c.lon))] }
        q.append(("min", mok?.ireum ?? mokMal))
        if let m = mok { q += [("mlat", String(m.lat)), ("mlon", String(m.lon))] }
        q += [("ttae", ttae), ("mal", mal), ("nugu", nugu)]
        let j = await Nas.get("butak.php", q)
        if (j?["ok"] as? Bool) == true { return nil }
        return (j?["error"] as? String) ?? "올리지 못했습니다."
    }

    static func hagi(_ a: String, _ id: String, _ deo: [(String, String)] = []) async -> Bool {
        await Nas.get("butak.php", [("a", a), ("id", id)] + deo) != nil
    }
}

// MARK: 알림(공지)

struct Gongji: Identifiable, Hashable {
    let id: String
    let jemok: String
    let naeyong: String
    let gingeup: Bool
    let nalMal: String
    var julMal: String { (gingeup ? "긴급 공지 · " : "") + jemok + (nalMal.isEmpty ? "" : " · " + nalMal) }
}

final class GongjiEngine: ObservableObject {
    static let shared = GongjiEngine()
    @Published private(set) var mok: [Gongji] = []
    @Published private(set) var gingeupSae: Gongji?     // 아직 읽지 않은 긴급 공지 — 첫 화면 맨 위
    @Published private(set) var batneunJung = false
    @Published private(set) var mot = false
    private var sigye: Timer?
    private var ilgeun: Set<String> {
        get { Set(UserDefaults.standard.stringArray(forKey: "gn.gongjiIlgeum") ?? []) }
        set { UserDefaults.standard.set(Array(newValue.suffix(300)), forKey: "gn.gongjiIlgeum") }
    }
    private var allin: Set<String> {
        get { Set(UserDefaults.standard.stringArray(forKey: "gn.gongjiAllin") ?? []) }
        set { UserDefaults.standard.set(Array(newValue.suffix(300)), forKey: "gn.gongjiAllin") }
    }

    func sijak() {
        batgi()
        sigye?.invalidate()
        sigye = Timer.scheduledTimer(withTimeInterval: 900, repeats: true) { [weak self] _ in self?.batgi() }
        NotificationCenter.default.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { [weak self] _ in
            self?.batgi()
        }
    }

    func batgi() {
        guard !batneunJung else { return }
        batneunJung = true
        Task {
            let j = await Nas.get("gongji.php", [("a", "mok"), ("app", "gilnun")])
            await MainActor.run {
                self.batneunJung = false
                guard let j = j, (j["ok"] as? Bool) == true else { self.mot = true; return }
                self.mot = false
                self.mok = ((j["gongji"] as? [[String: Any]]) ?? []).compactMap { g in
                    let id = Nas.gul(g["id"])
                    guard !id.isEmpty, (g["garim"] as? Bool) != true else { return nil }
                    return Gongji(id: id, jemok: Nas.gul(g["jemok"]), naeyong: Nas.gul(g["naeyong"]),
                                  gingeup: Nas.gul(g["deunggeup"]) == "gingeup", nalMal: Nas.gul(g["nalMal"]))
                }
                self.gingeupBoda()
            }
        }
    }

    private func gingeupBoda() {
        let il = ilgeun
        gingeupSae = mok.first { $0.gingeup && !il.contains($0.id) }
        guard let g = gingeupSae, !allin.contains(g.id) else { return }
        var a = allin
        a.insert(g.id)
        allin = a
        if UIApplication.shared.applicationState == .active {
            SoriEngine.shared.mal("긴급 공지. \(g.jemok). 길 찾기 첫 화면 맨 위에서 들으실 수 있습니다.")
        } else {
            let c = UNMutableNotificationContent()
            c.title = "길눈 긴급 공지"
            c.body = g.jemok
            c.sound = .default
            UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "gongji-" + g.id, content: c, trigger: nil))
        }
        Girok.shared.namgi("gongji_gingeup", [:])
    }

    /// 읽음 — 긴급 공지는 읽으면 첫 화면에서 내려감
    func ilgeumPyosi(_ g: Gongji) {
        var s = ilgeun
        s.insert(g.id)
        ilgeun = s
        if gingeupSae?.id == g.id { gingeupSae = mok.first { $0.gingeup && !s.contains($0.id) } }
    }

    func ilgeotna(_ g: Gongji) -> Bool { ilgeun.contains(g.id) }

    /// 폰 알림 허락 — 알림 화면의 단추에서만 여쭘
    static func heorakMutgi(_ f: @escaping (Bool) -> Void) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { ok, _ in
            DispatchQueue.main.async { f(ok) }
        }
    }
}

// MARK: 국가지점번호 — 폰이 스스로 셈(웹 길눈 jijeom.js 와 같은 셈)

enum Jijeom {
    private static let a = 6378137.0
    private static let f = 1 / 298.257222101
    private static let e2 = f * (2 - f)
    private static let ep2 = e2 / (1 - e2)
    private static let ja = Array("가나다라마바사아자차카타파하")
    private static let sut = ["영", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"]

    private static func m(_ phi: Double) -> Double {
        let a0 = 1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256
        let a2 = 3.0 / 8 * (e2 + e2 * e2 / 4 + 15 * e2 * e2 * e2 / 128)
        let a4 = 15.0 / 256 * (e2 * e2 + 3 * e2 * e2 * e2 / 4)
        let a6 = 35 * e2 * e2 * e2 / 3072
        return a * (a0 * phi - a2 * sin(2 * phi) + a4 * sin(4 * phi) - a6 * sin(6 * phi))
    }

    private static func utmk(_ lat: Double, _ lon: Double) -> (Double, Double) {
        let k0 = 0.9996, lat0 = 38 * Double.pi / 180, lon0 = 127.5 * Double.pi / 180
        let phi = lat * Double.pi / 180, lam = lon * Double.pi / 180
        let n = a / sqrt(1 - e2 * sin(phi) * sin(phi))
        let t = tan(phi) * tan(phi)
        let c = ep2 * cos(phi) * cos(phi)
        let aa = (lam - lon0) * cos(phi)
        let a2 = aa * aa, a3 = a2 * aa, a4 = a3 * aa, a5 = a4 * aa, a6 = a5 * aa
        let x = k0 * n * (aa + (1 - t + c) * a3 / 6 + (5 - 18 * t + t * t + 72 * c - 58 * ep2) * a5 / 120) + 1_000_000
        let y1 = m(phi) - m(lat0)
        let y2 = n * tan(phi) * (a2 / 2 + (5 - t + 9 * c + 4 * c * c) * a4 / 24 + (61 - 58 * t + t * t + 600 * c - 330 * ep2) * a6 / 720)
        return (x, k0 * (y1 + y2) + 2_000_000)
    }

    /// (한글 두 자, 동쪽 넉 자, 북쪽 넉 자) — 우리나라 밖이면 nil
    static func gyesan(_ lat: Double, _ lon: Double) -> (String, String, String)? {
        let p = utmk(lat, lon)
        let e = p.0 - 700_000, n = p.1 - 1_300_000
        guard e >= 0, n >= 0 else { return nil }
        let ei = Int(e / 100_000), ni = Int(n / 100_000)
        guard ei < ja.count, ni < ja.count else { return nil }
        let e4 = Int(e.truncatingRemainder(dividingBy: 100_000) / 10)
        let n4 = Int(n.truncatingRemainder(dividingBy: 100_000) / 10)
        return (String(ja[ei]) + String(ja[ni]), String(format: "%04d", e4), String(format: "%04d", n4))
    }

    /// 소리로 — 숫자는 한 자씩(119에 불러 드릴 때 헷갈리지 않게)
    static func mal(_ lat: Double, _ lon: Double) -> String? {
        guard let j = gyesan(lat, lon) else { return nil }
        func han(_ s: String) -> String { s.compactMap { $0.wholeNumberValue }.map { sut[$0] }.joined(separator: " ") }
        let g = Array(j.0)
        return "국가지점번호 \(g[0]) \(g[1]), \(han(j.1)), \(han(j.2))"
    }

    static func geul(_ lat: Double, _ lon: Double) -> String? {
        guard let j = gyesan(lat, lon) else { return nil }
        return "\(j.0) \(j.1) \(j.2)"
    }
}

// MARK: 흔들면 — 긴급통화 열기 또는 자리 번호 말하기(설정에서 켬, 처음에는 꺼 둠)

final class Heundeul {
    static let shared = Heundeul()
    private let mm = CMMotionManager()
    private var majimak = Date.distantPast
    private var cheotHeundeul: Date?
    private var ssak = Set<AnyCancellable>()

    func sijak() {
        Seoljeong.shared.$heundeulKyeojim
            .receive(on: DispatchQueue.main)
            .sink { [weak self] on in if on { self?.kyeogi() } else { self?.kkeugi() } }
            .store(in: &ssak)
    }

    private func kyeogi() {
        guard mm.isAccelerometerAvailable, !mm.isAccelerometerActive else { return }
        mm.accelerometerUpdateInterval = 0.1
        mm.startAccelerometerUpdates(to: .main) { [weak self] d, _ in
            guard let s = self, let a = d?.acceleration else { return }
            let g = sqrt(a.x * a.x + a.y * a.y + a.z * a.z)
            guard g > 2.6 else { return }
            let now = Date()
            // 세게 두 번(1.2초 안) 흔드셨을 때만 — 걷다가 한 번 튀는 것은 넘김
            if let c = s.cheotHeundeul, now.timeIntervalSince(c) > 0.25, now.timeIntervalSince(c) < 1.2 {
                s.cheotHeundeul = nil
                guard now.timeIntervalSince(s.majimak) > 6 else { return }
                s.majimak = now
                s.hagi()
            } else if s.cheotHeundeul == nil || now.timeIntervalSince(s.cheotHeundeul!) >= 1.2 {
                s.cheotHeundeul = now
            }
        }
    }

    private func kkeugi() {
        if mm.isAccelerometerActive { mm.stopAccelerometerUpdates() }
    }

    private func hagi() {
        Girok.shared.namgi("heundeul", ["il": Seoljeong.shared.heundeulIl])
        if Seoljeong.shared.heundeulIl == "jari" {
            Heundeul.jariBeonhoMal()
        } else {
            guard GinGeup.shared.sangtae == .eopseum else { return }
            SoriEngine.shared.mal("흔드셨습니다. 긴급통화서비스를 엽니다.", .gyeonggo)
            TabGil.shared.tab = 0
            GilGil.shared.cheotHwamyeon()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.gingeup) }
        }
    }

    static func jariBeonhoMal() {
        guard let w = WichiEngine.shared.jigeum else {
            SoriEngine.shared.mal("아직 자리를 잡는 중입니다. 잠시 뒤 다시 흔들어 주십시오.")
            return
        }
        let m = Jijeom.mal(w.lat, w.lon) ?? "이곳은 국가지점번호를 셈할 수 없는 곳입니다."
        SoriEngine.shared.mal(m + ". 위성 오차 약 \(Int(w.ochae))미터.", .gyeonggo)
    }
}

// MARK: 내 서류 보관함 — 폰 안에만, 아이클라우드 백업에서도 뺌

enum Seoryuham {
    static let jongryu: [(String, String)] = [("bokji_ap", "복지카드 앞면"), ("bokji_dwi", "복지카드 뒷면"), ("sinbun", "신분증"),
                                             ("jangae", "장애정도 결정 통지서"), ("geubak", "그 밖의 서류")]

    static var got: URL {
        var u = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("gilnun_seoryu", isDirectory: true)
        if !FileManager.default.fileExists(atPath: u.path) {
            try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
            var rv = URLResourceValues()
            rv.isExcludedFromBackup = true
            try? u.setResourceValues(rv)
        }
        return u
    }

    static func pail(_ k: String) -> URL { got.appendingPathComponent(k + ".jpg") }

    static func itna(_ k: String) -> Bool { FileManager.default.fileExists(atPath: pail(k).path) }

    static func damgi(_ k: String, _ img: UIImage) -> Bool {
        let kk = 1600 / max(img.size.width, img.size.height)
        let sz = kk < 1 ? CGSize(width: img.size.width * kk, height: img.size.height * kk) : img.size
        let r = UIGraphicsImageRenderer(size: sz)
        guard let d = r.image(actions: { _ in img.draw(in: CGRect(origin: .zero, size: sz)) }).jpegData(compressionQuality: 0.8) else { return false }
        var u = pail(k)
        do {
            try d.write(to: u, options: [.atomic, .completeFileProtection])
            var rv = URLResourceValues()
            rv.isExcludedFromBackup = true
            try? u.setResourceValues(rv)
            return true
        } catch {
            return false
        }
    }

    static func jiugi(_ k: String) { try? FileManager.default.removeItem(at: pail(k)) }

    // 적어 두기 — 키체인에(폰 안에만)
    static let jeokgi: [(String, String)] = [("ireum", "이름"), ("saengil", "생년월일 (여덟 자리)"), ("yeonrak", "연락처"), ("juso", "주소")]

    static func jeokeun(_ k: String) -> String { Yeolsoe.ilgi("seoryu_" + k) ?? "" }
    static func jeokgi(_ k: String, _ v: String) { Yeolsoe.sseugi("seoryu_" + k, v) }

    static var jeokeunGeul: String {
        var t: [String] = []
        for x in jeokgi {
            let v = jeokeun(x.0)
            if !v.isEmpty { t.append("\(x.1.components(separatedBy: " (").first ?? x.1): \(v)") }
        }
        return t.joined(separator: "\n")
    }
}
