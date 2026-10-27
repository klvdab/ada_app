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

    /// 2.118.0 날씨를 말로 묻기 — 물으신 날·곳·것에 맞춰 대답(NalssiMal). 자리를 못 잡으면 서울로 알려 드림
    func mureum(_ t: String) async -> String {
        let g = NalssiMal.got(t)
        let w = WichiEngine.shared.jigeum
        let la = g?.1 ?? w?.lat ?? 37.5665
        let lo = g?.2 ?? w?.lon ?? 126.978
        let seoul = g == nil && w == nil
        async let f = Nalssi.batgi(NalssiMal.juso(la, lo))
        var m: [String: Any]? = nil
        if NalssiMal.nalDwi(t, Date()) == 0 { m = await Nalssi.batgi(NalssiMal.meonjiJuso(la, lo)) }
        guard let fw = await f else { return "" }
        let d = NalssiMal.dap(t, fw, m, Date(), g?.0 ?? "")
        Girok.shared.namgi("nalssi_mureum", ["got": g != nil, "ok": !d.isEmpty])
        return (!d.isEmpty && seoul) ? "지금 자리를 잡지 못해 서울 날씨로 알려 드립니다. " + d : d
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

// MARK: - 2.118.0 (261027-I72, 이사장님 승인 2026-10-27 「1」 공개 자료로 넓히기) 날씨를 말로 묻기
// 웹 길눈 말로 하기 2.5의 날씨 묻기를 앱으로 옮기고 공개 자료(아마존 MASSIVE 한국어 날씨 855문장)로 시험해 넓힘. 안드로이드 NalssiMal.kt 와 같은 셈, 같은 말.

enum NalssiMal {
    /// 웹 길눈과 같은 도시 52곳에 공개 자료에 나온 곳을 더함
    static let GOT: [(String, Double, Double)] = [
        ("서울", 37.5665, 126.978), ("부산", 35.1796, 129.0756), ("대구", 35.8714, 128.6014), ("인천", 37.4563, 126.7052),
        ("광주", 35.1595, 126.8526), ("대전", 36.3504, 127.3845), ("울산", 35.5384, 129.3114), ("세종", 36.48, 127.289),
        ("수원", 37.2636, 127.0286), ("성남", 37.4201, 127.1269), ("고양", 37.6584, 126.832), ("용인", 37.2411, 127.1776),
        ("부천", 37.5034, 126.766), ("안산", 37.3219, 126.8309), ("안양", 37.3943, 126.9568), ("남양주", 37.636, 127.2165),
        ("화성", 37.1995, 126.8312), ("평택", 36.9921, 127.1129), ("의정부", 37.7381, 127.0337), ("파주", 37.76, 126.78),
        ("김포", 37.6153, 126.7156), ("여주", 37.2983, 127.6374), ("이천", 37.2723, 127.435), ("춘천", 37.8813, 127.7298),
        ("원주", 37.3422, 127.9202), ("강릉", 37.7519, 128.8761), ("속초", 38.207, 128.5918), ("홍천", 37.6969, 127.8886),
        ("정선", 37.3807, 128.6609), ("청주", 36.6424, 127.489), ("충주", 36.991, 127.9259), ("천안", 36.8151, 127.1139),
        ("아산", 36.7898, 127.0019), ("공주", 36.4465, 127.119), ("전주", 35.8242, 127.148), ("군산", 35.9676, 126.7369),
        ("익산", 35.9483, 126.9577), ("목포", 34.8118, 126.3922), ("여수", 34.7604, 127.6622), ("순천", 34.9506, 127.4872),
        ("구례", 35.2025, 127.4629), ("포항", 36.019, 129.3435), ("경주", 35.8562, 129.2247), ("안동", 36.5684, 128.7294),
        ("구미", 36.1195, 128.3446), ("창원", 35.2279, 128.6811), ("진주", 35.18, 128.1076), ("김해", 35.2285, 128.8894),
        ("통영", 34.8544, 128.4331), ("거제", 34.8806, 128.6211), ("제주", 33.4996, 126.5312), ("서귀포", 33.2541, 126.56),
        ("용산", 37.5326, 126.9905), ("강남", 37.4979, 127.0276), ("보령", 36.3333, 126.6128), ("삼척", 37.4499, 129.1652),
        ("동해", 37.5247, 129.1143), ("태백", 37.164, 128.9856), ("광명", 37.4786, 126.8646), ("시흥", 37.3799, 126.8031),
        ("하남", 37.5393, 127.2148), ("양평", 37.4917, 127.4876), ("가평", 37.8315, 127.5095), ("포천", 37.8949, 127.2003),
        ("서산", 36.7848, 126.4503), ("당진", 36.8898, 126.6458), ("논산", 36.1872, 127.0987), ("제천", 37.1326, 128.191),
        ("남원", 35.4164, 127.3904), ("정읍", 35.5699, 126.856), ("나주", 35.0159, 126.7108), ("해남", 34.5733, 126.5993),
        ("양산", 35.335, 129.0373), ("밀양", 35.5038, 128.7464), ("사천", 35.0037, 128.0642), ("남해", 34.8376, 127.8924),
        ("울릉", 37.4844, 130.9057), ("독도", 37.2426, 131.8669)
    ]

    static func rx(_ p: String, _ s: String) -> Bool {
        guard let r = try? NSRegularExpression(pattern: p) else { return false }
        return r.firstMatch(in: s, range: NSRange(location: 0, length: (s as NSString).length)) != nil
    }

    static let BAEJE = "노래|음악|틀어|곡|방송|라디오|재생|듣고|듣자|뉴스|기사|이메일|메일|문자|일정|약속|캘린더|달력|알람|트윗|페이스북|게시|리뷰|정의|뜻이|영화|오븐|굽|튀기|요리|레시피"
    static let KKOK = "날씨|기온|온도|미세\\s*먼지|초미세|황사|우산|우비|비옷|일기\\s*예보|기상|예보|체감|습도|우박|일교차|(^|[^상])영하|뇌우|폭풍|태풍|소나기|장마|천둥|번개|자외선|선크림|선로션|썬크림|영상\\s*(일|이|인|으로)"
    static let NUN = "(^|[^길])눈\\s*(이\\s*)?(와|오|올|내리|내릴|내려|소식|쌓|예보)"
    static let BI = "(^|[^가-힣])비\\s*(가\\s*)?(와|오[^\\s]|올|오나|오니|내리|내릴|내려|소식|예보)|이슬비|빗"
    static let TTAE = "(오늘|내일|모레|밖|지금|날|바깥|아침|저녁|밤|낮|오후|오전|얼마나|어때|어떠|주말|이번\\s*주|요일|나중|이따)"
    static let OS = "코트|재킷|자켓|외투|패딩|스웨터|반팔|긴팔|겉옷|따뜻한\\s*옷|두꺼운|장갑|목도리|선글라스|덧신|양말"

    /// 날씨를 묻는 말인가
    static func mutneunInga(_ t: String) -> Bool {
        if rx(BAEJE, t) { return false }
        if rx(KKOK, t) { return true }
        if rx(NUN, t) || rx(BI, t) { return true }
        if rx("추워|추운|춥|추울|더워|더운|덥|더울|쌀쌀|포근|따뜻|따듯|선선|시원할|후덥|무더", t) &&
            (rx(TTAE, t) || rx("까|니|나|야|지|요$", t.trimmingCharacters(in: .whitespaces))) { return true }
        if rx("몇\\s*도", t) && rx("밖|바깥|오늘|내일|모레|지금|현재|요일|기온|온도", t) { return true }
        if rx("맑을|맑아|맑니|맑나|맑겠|맑은\\s*날|흐릴|흐려|흐리|해가\\s*(비칠|날|뜰|나)|해\\s*(나와|날까|뜰|뜨)", t) && rx("오늘|내일|모레|날|하늘|주말|요일|나중|까|니|나", t) { return true }
        if rx("(밖|바깥)\\s*(은|에)?\\s*(어때|어떠)", t) { return true }
        if rx("바람\\s*(이\\s*)?(불|세|많|부|강)", t) { return true }
        if rx(OS, t) && rx("입어|입을|가져|챙겨|챙길|필요|신을|껴야|써야|해야|할까|될까", t) { return true }
        return false
    }

    /// 며칠 뒤 — 오늘 0, 내일 1, 모레 2, 글피 3, 요일이면 가장 가까운 그 요일(오늘 포함)
    static func nalDwi(_ t: String, _ now: Date) -> Int {
        if t.contains("글피") { return 3 }
        if t.contains("모레") { return 2 }
        if t.contains("내일") || t.contains("낼") { return 1 }
        guard let m = IljeongMal.maj("([일월화수목금토])요일", t), let ch = m[1].first,
              let i = Array("일월화수목금토").firstIndex(of: ch) else { return 0 }
        let oneul = Calendar.current.component(.weekday, from: now)
        return (i + 1 - oneul + 7) % 7
    }

    static func got(_ t: String) -> (String, Double, Double)? { GOT.first { t.contains($0.0) } }

    static func jugan(_ t: String) -> Bool { rx("이번\\s*주|주간|일주일|다음\\s*주|주말|며칠|앞으로|오일\\s*간|5일\\s*간", t) }

    static func haneul(_ c: Int?) -> String {
        guard let c = c else { return "" }
        switch c {
        case 0: return "맑음"
        case 1: return "대체로 맑음"
        case 2: return "구름 조금"
        case 3: return "흐림"
        case 45, 48: return "안개"
        case 51...57: return "이슬비"
        case 61: return "약한 비"
        case 63: return "비"
        case 65: return "강한 비"
        case 66, 67: return "어는 비"
        case 71: return "약한 눈"
        case 73: return "눈"
        case 75: return "많은 눈"
        case 77: return "싸락눈"
        case 80...82: return "소나기"
        case 85, 86: return "눈 소나기"
        default: return c >= 95 ? "천둥 번개" : ""
        }
    }

    private static func meonji(_ p10: Double?, _ p25: Double?) -> String {
        let a: Int = p10.map { $0 <= 30 ? 0 : ($0 <= 80 ? 1 : ($0 <= 150 ? 2 : 3)) } ?? -1
        let b: Int = p25.map { $0 <= 15 ? 0 : ($0 <= 35 ? 1 : ($0 <= 75 ? 2 : 3)) } ?? -1
        let d = max(a, b)
        guard d >= 0 else { return "" }
        return "미세먼지 " + ["좋음", "보통", "나쁨", "매우 나쁨"][d] + (d >= 2 ? ", 마스크를 쓰시는 것이 좋습니다" : "")
    }

    private static func usan(_ p: Int?) -> String {
        guard let p = p else { return "" }
        return p >= 60 ? "우산을 꼭 챙기십시오" : (p >= 30 ? "우산을 챙기시면 좋겠습니다" : "우산은 없어도 되겠습니다")
    }

    private static func osMal(_ hi: Double?) -> String {
        guard let h = hi else { return "" }
        if h <= 5 { return "두꺼운 외투와 장갑을 챙기십시오" }
        if h <= 12 { return "외투를 입으시는 것이 좋겠습니다" }
        if h <= 19 { return "가벼운 겉옷을 챙기시면 좋겠습니다" }
        if h <= 25 { return "얇은 긴팔 정도면 되겠습니다" }
        return "가볍게 입으셔도 되겠습니다"
    }

    private static func d(_ o: [String: Any]?, _ k: String, _ i: Int) -> Double? {
        guard let a = o?[k] as? [Any], i < a.count else { return nil }
        return Chatgi.su(a[i])
    }
    private static func r(_ x: Double) -> Int { Int(x.rounded()) }

    static func juso(_ la: Double, _ lo: Double) -> String {
        "https://api.open-meteo.com/v1/forecast?latitude=" + String(format: "%.3f", la) + "&longitude=" + String(format: "%.3f", lo) +
        "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max,uv_index_max,snowfall_sum" +
        "&hourly=precipitation_probability&wind_speed_unit=ms&timezone=Asia%2FSeoul&forecast_days=7"
    }
    static func meonjiJuso(_ la: Double, _ lo: Double) -> String {
        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + String(format: "%.3f", la) + "&longitude=" + String(format: "%.3f", lo) + "&current=pm10,pm2_5&timezone=Asia%2FSeoul"
    }

    /// 대답 — 물으신 것을 먼저. w 는 예보, q 는 미세먼지(없어도 됨), gotIreum 은 도시 이름(내 자리면 빈 글)
    static func dap(_ t: String, _ w: [String: Any], _ q: [String: Any]?, _ now: Date, _ gotIreum: String) -> String {
        let dy = w["daily"] as? [String: Any]
        let cu = w["current"] as? [String: Any]
        let ap = gotIreum.isEmpty ? "" : "\(gotIreum) 날씨입니다. "
        let yoil = ["일", "월", "화", "수", "목", "금", "토"]
        let cal = Calendar.current
        if jugan(t), let dy = dy {
            let jumal = t.contains("주말")
            let n = (dy["time"] as? [Any])?.count ?? 0
            var jul: [String] = []
            for i in 0..<n {
                let nalI = cal.date(byAdding: .day, value: i, to: now) ?? now
                let yo = cal.component(.weekday, from: nalI) - 1
                if jumal && yo != 0 && yo != 6 { continue }
                var s = (i == 0 ? "오늘 " : (i == 1 ? "내일 " : "")) + yoil[yo] + "요일 " + haneul(d(dy, "weather_code", i).map { Int($0) })
                if let hi = d(dy, "temperature_2m_max", i) { s += ", 최고 \(r(hi))도" }
                if let lo = d(dy, "temperature_2m_min", i) { s += ", 최저 \(r(lo))도" }
                if let pb = d(dy, "precipitation_probability_max", i) { s += ", 비 올 확률 \(r(pb))퍼센트" }
                jul.append(s)
            }
            if !jul.isEmpty { return ap + (jumal ? "주말" : "이번 주") + " 날씨입니다. " + jul.joined(separator: ". ") + "." }
        }
        let nal = min(6, max(0, nalDwi(t, now)))
        let nm: String
        switch nal {
        case 0: nm = "오늘"
        case 1: nm = "내일"
        case 2: nm = "모레"
        default: nm = yoil[cal.component(.weekday, from: cal.date(byAdding: .day, value: nal, to: now) ?? now) - 1] + "요일"
        }
        let hi = d(dy, "temperature_2m_max", nal), lo = d(dy, "temperature_2m_min", nal)
        var pb = d(dy, "precipitation_probability_max", nal).map { r($0) }
        var namun = false
        if nal == 0, let hr = w["hourly"] as? [String: Any], let ht = hr["time"] as? [String], let hp = hr["precipitation_probability"] as? [Any],
           let jg0 = cu?["time"] as? String, jg0.count >= 13 {
            let jg = String(jg0.prefix(13))
            var mx: Int? = nil
            for (i, s) in ht.enumerated() where i < hp.count && s.prefix(10) == jg.prefix(10) && String(s.prefix(13)) >= jg {
                if let v = Chatgi.su(hp[i]) { let vi = Int(v); if mx == nil || vi > mx! { mx = vi } }
            }
            if let m = mx { pb = m; namun = true }
        }
        let codeNal = d(dy, "weather_code", nal).map { Int($0) }
        let hnNal = haneul(codeNal)
        let hn = nal == 0 ? haneul(Chatgi.su(cu?["weather_code"]).map { Int($0) } ?? codeNal) : hnNal
        let bm = nal == 0 ? Chatgi.su(cu?["wind_speed_10m"]) : d(dy, "wind_speed_10m_max", nal)
        let uv = d(dy, "uv_index_max", nal)
        let nunOl = (d(dy, "snowfall_sum", nal) ?? 0) > 0 || hnNal.contains("눈")
        let mutBi = rx("비|우산|우비|비옷|소나기|장마|뇌우|폭풍|태풍", t)
        let mutNun = rx("(^|[^길])눈", t)
        let mutMeonji = rx("먼지|황사|마스크", t)
        let mutOndo = rx("추워|추운|춥|추울|더워|더운|덥|더울|쌀쌀|포근|기온|온도|몇\\s*도|체감|영하|영상|따뜻|선선|시원", t)
        let mutBaram = t.contains("바람")
        let mutOs = rx(OS, t) || t.contains("옷")
        let mutHaet = rx("자외선|선크림|선로션|썬크림|선글라스|햇볕|햇빛", t)
        let mutSeup = t.contains("습도") || t.contains("습해") || t.contains("눅눅")

        let biMal = pb.map { "\(nm)\(namun ? " 남은 시간" : "") 비 올 확률은 \($0)퍼센트입니다. " + usan($0) } ?? ""
        var ondoMal = ""
        if nal == 0, let on = Chatgi.su(cu?["temperature_2m"]) {
            ondoMal = "지금 기온은 \(r(on))도"
            if let che = Chatgi.su(cu?["apparent_temperature"]), abs(che - on) >= 2 { ondoMal += ", 체감 온도는 \(r(che))도" }
        }
        let choeMal = (hi != nil && lo != nil) ? "\(nm) 최고 \(r(hi!))도, 최저 \(r(lo!))도" : ""
        let hnMal = nal == 0 ? (hn.isEmpty ? "" : "지금 하늘은 \(hn)" + (!hnNal.isEmpty && hnNal != hn ? ", 오늘 하루는 \(hnNal)" : ""))
            : (hnNal.isEmpty ? "" : "\(nm)\(IljeongMal.eun(nm)) \(hnNal)")
        var baramMal = ""
        if let b = bm {
            if b >= 7 { baramMal = "바람이 셉니다, 초속 \(r(b))미터" } else if mutBaram { baramMal = "바람은 초속 \(r(b))미터로 " + (b >= 4 ? "조금 붑니다" : "약합니다") }
        }
        let qc = q?["current"] as? [String: Any]
        let mjMal = nal == 0 ? meonji(Chatgi.su(qc?["pm10"]), Chatgi.su(qc?["pm2_5"])) : ""
        var uvMal = ""
        if let u = uv {
            uvMal = "\(nm) 자외선 지수는 \(r(u)), " + (u >= 8 ? "매우 강합니다. 선크림과 모자를 꼭 챙기십시오" : (u >= 6 ? "강합니다. 선크림을 바르시는 것이 좋겠습니다" : (u >= 3 ? "보통입니다" : "약합니다")))
        }
        let seupMal = (nal == 0 ? Chatgi.su(cu?["relative_humidity_2m"]) : nil).map { "지금 습도는 \(r($0))퍼센트" } ?? ""

        var apL: [String] = [], dwi: [String] = []
        if mutMeonji {
            apL.append(mjMal.isEmpty ? (nal == 0 ? "미세먼지 정보를 받지 못했습니다" : "미세먼지는 오늘 것만 알려 드립니다") : mjMal)
            dwi += [hnMal, ondoMal, choeMal]
        } else if mutBi || mutNun {
            if mutNun { apL.append(nunOl ? "\(nm) 눈 소식이 있습니다" : "\(nm) 눈 소식은 없습니다") }
            apL.append(biMal)
            dwi += [hnMal, ondoMal, choeMal, mjMal]
        } else if mutHaet {
            apL.append(uvMal); dwi += [hnMal, choeMal]
        } else if mutBaram, let b = bm {
            apL.append(b >= 7 ? "\(nm) 바람이 셉니다, 초속 \(r(b))미터" : "\(nm) 바람은 초속 \(r(b))미터로 " + (b >= 4 ? "조금 붑니다" : "약합니다"))
            dwi += [hnMal, choeMal]
        } else if mutOs {
            apL += [ondoMal, choeMal, osMal(hi)]; dwi += [hnMal, (pb ?? 0) >= 30 ? biMal : ""]
        } else if mutSeup {
            apL.append(seupMal); dwi += [hnMal, ondoMal]
        } else if mutOndo {
            apL += [ondoMal, choeMal]; dwi += [hnMal, (pb ?? 0) >= 30 ? biMal : "", mjMal]
        } else {
            apL += [hnMal, ondoMal, choeMal]; dwi += [pb != nil ? biMal : "", mjMal]
        }
        if !baramMal.isEmpty && !(mutBaram && bm != nil) { dwi.append(baramMal) }
        let mal = (apL + dwi).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.joined(separator: ". ")
        if mal.isEmpty { return "" }
        return (ap + mal + ".").replacingOccurrences(of: "..", with: ".")
    }
}
