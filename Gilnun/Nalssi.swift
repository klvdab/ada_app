// 날씨 — 앱 2.13.0 (빌드 260929-8, 이사장님 승인 1). 웹 길눈 nalssi.js 1.2판과 같은 자료, 같은 말.
// 열쇠가 필요 없는 오픈메테오(날씨·미세먼지)에서 받습니다. 10분 안이거나 3킬로미터 넘게 옮기지 않았으면 다시 묻지 않습니다.
// 걸음에 영향을 주는 것(비·눈·안개, 센 바람, 영하, 나쁜 미세먼지)을 앞세워 한 줄로 말합니다.
// 쓰는 곳: 지금 내 자리 듣기 끝, 말로 하기 "날씨 어때", 앱을 켠 뒤 한 번.
import Foundation

final class Nalssi {
    static let shared = Nalssi()

    private var jangdok = ""
    private var ttae = Date.distantPast
    private var ttaeJari: (Double, Double)?
    private var doneun = false
    private(set) var cheotMalHaet = false
    /// 마지막으로 받은 하늘 번호(음악을 날씨에 맞춰 틀 때 씀)
    private(set) var haneulBeonho: Int?

    /// 날씨 한 줄 — 받지 못하면 빈 글
    func mal() async -> String {
        guard let w = WichiEngine.shared.jigeum else { return jangdok }
        if !jangdok.isEmpty, Date().timeIntervalSince(ttae) < 600,
           let j = ttaeJari, WichiEngine.geori(j.0, j.1, w.lat, w.lon) < 3000 {
            return jangdok
        }
        if doneun { return jangdok }
        doneun = true
        defer { doneun = false }
        let la = String(format: "%.3f", w.lat), lo = String(format: "%.3f", w.lon)
        let a = "https://api.open-meteo.com/v1/forecast?latitude=\(la)&longitude=\(lo)&current=temperature_2m,weather_code,wind_speed_10m&wind_speed_unit=ms&timezone=Asia%2FSeoul"
        let b = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=\(la)&longitude=\(lo)&current=pm10,pm2_5&timezone=Asia%2FSeoul"
        async let na = Nalssi.batgi(a)
        async let mo = Nalssi.batgi(b)
        let (n, m) = await (na, mo)
        guard let nc = n?["current"] as? [String: Any] else { return jangdok }
        let mc = m?["current"] as? [String: Any]
        haneulBeonho = Chatgi.su(nc["weather_code"]).map { Int($0) }
        let t = Nalssi.joripda(on: Chatgi.su(nc["temperature_2m"]),
                               code: Chatgi.su(nc["weather_code"]).map { Int($0) },
                               baram: Chatgi.su(nc["wind_speed_10m"]),
                               pm10: Chatgi.su(mc?["pm10"]), pm25: Chatgi.su(mc?["pm2_5"]))
        if !t.isEmpty {
            jangdok = t
            ttae = Date()
            ttaeJari = (w.lat, w.lon)
            Girok.shared.namgi("nalssi", [:])
        }
        return jangdok
    }

    /// 2.13.0 날씨에 어울리는 음악 주제(나스 음악 찾기의 주제 이름) — 밤이면 밤
    func eumakJuje() async -> (String, String) {
        _ = await mal()
        let h = Calendar.current.component(.hour, from: Date())
        let haneul = Nalssi.haneulMal(haneulBeonho)
        if haneul.contains("비") || haneul.contains("소나기") || haneul.contains("천둥") { return ("비 오는 날", haneul) }
        if haneul.contains("눈") { return ("눈 오는 날", haneul) }
        if haneul.contains("안개") { return ("안개 낀 날", haneul) }
        if h >= 21 || h < 5 { return ("밤", haneul.isEmpty ? "밤" : haneul + ", 밤") }
        if haneul.contains("흐림") { return ("흐린 날", haneul) }
        return ("맑은 날", haneul.isEmpty ? "맑음" : haneul)
    }

    /// 앱을 켠 뒤 한 번 — 자리가 잡히면 "날씨는 …" 하고 알려 드림(알려 드리는 말이라 안내가 바쁘면 먼저 버림)
    func cheotMal() {
        guard !cheotMalHaet else { return }
        cheotMalHaet = true
        Task {
            for _ in 0..<20 {
                if WichiEngine.shared.jigeum != nil { break }
                try? await Task.sleep(nanoseconds: 3_000_000_000)
            }
            let t = await mal()
            guard !t.isEmpty else { return }
            await MainActor.run { SoriEngine.shared.mal("날씨는 " + t + ".", .jeongbo) }
        }
    }

    private static func batgi(_ s: String) async -> [String: Any]? {
        guard let u = URL(string: s) else { return nil }
        var r = URLRequest(url: u)
        r.timeoutInterval = 12
        guard let dr = try? await URLSession.shared.data(for: r),
              (dr.1 as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return (try? JSONSerialization.jsonObject(with: dr.0)) as? [String: Any]
    }

    static func haneulMal(_ c: Int?) -> String {
        guard let c = c else { return "" }
        if c == 0 { return "맑음" }
        if c <= 2 { return "구름 조금" }
        if c == 3 { return "흐림" }
        if c == 45 || c == 48 { return "안개" }
        if c >= 51 && c <= 57 { return "이슬비" }
        if c >= 61 && c <= 65 { return "비" }
        if c >= 66 && c <= 67 { return "얼어붙는 비" }
        if c >= 71 && c <= 77 { return "눈" }
        if c >= 80 && c <= 82 { return "소나기" }
        if c >= 85 && c <= 86 { return "눈 소나기" }
        if c >= 95 { return "천둥 번개" }
        return ""
    }

    static func meonjiMal(_ pm10: Double?, _ pm25: Double?) -> (String, Bool) {
        if pm10 == nil && pm25 == nil { return ("", false) }
        func deung(_ v: Double?, _ a: Double, _ b: Double, _ c: Double) -> Int {
            guard let v = v else { return -1 }
            if v <= a { return 0 }
            if v <= b { return 1 }
            if v <= c { return 2 }
            return 3
        }
        let d = max(deung(pm10, 30, 80, 150), deung(pm25, 15, 35, 75))
        guard d >= 0 else { return ("", false) }
        let ireum = ["좋음", "보통", "나쁨", "매우 나쁨"][d]
        var s = "미세먼지 " + ireum
        if let p = pm10 { s += ", 농도 \(Int(p.rounded())), 초미세먼지 " + (pm25.map { String(Int($0.rounded())) } ?? "모름") }
        if d >= 2 { s += ". 마스크를 쓰시는 것이 좋습니다" }
        return (s, d >= 2)
    }

    /// 걸음에 영향을 주는 것을 앞에 둡니다
    static func joripda(on: Double?, code: Int?, baram: Double?, pm10: Double?, pm25: Double?) -> String {
        var ap: [String] = [], dwi: [String] = []
        let h = haneulMal(code)
        let jeojeun = ["비", "눈", "소나기", "천둥", "안개"].contains { h.contains($0) }
        if jeojeun { ap.append(h + "입니다. 바닥이 미끄러울 수 있습니다") }
        if let b = baram, b >= 7 { ap.append("바람이 셉니다, 초속 \(Int(b.rounded()))미터") }
        if let o = on, o <= 0 { ap.append("영하 \(Int(abs(o).rounded()))도, 얼어붙은 곳을 조심하십시오") }
        let (m, nappeum) = meonjiMal(pm10, pm25)
        if !m.isEmpty { if nappeum { ap.append(m) } else { dwi.append(m) } }
        if let o = on { dwi.append("기온 \(Int(o.rounded()))도") }
        if !h.isEmpty && !jeojeun { dwi.append(h) }
        return (ap + dwi).joined(separator: ". ")
    }
}
