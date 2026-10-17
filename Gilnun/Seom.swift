// 섬·때·혼자 타기 — 2.93.0 (261017-I47, 이사장님 승인 2026-10-17 「그러자」)
// 웹 길눈 seom.js 0.7(2026-09-08 이사장님 지시)을 그대로 옮김.
//   목적지가 육지 길로 이어지지 않는 섬이면 육지 교통편을 내놓지 않고 타는 공항·항구를 알려 드림(독도는 울릉도를 거쳐).
//   지하철이 다니는 고장이 아니면 지하철로 가기를 내놓지 않음. 지금 시각에 끊겼을 만한 것은 「지금은 끊겼을 수 있습니다」.
//   목적지가 공항·항구면 「타기 전에 챙길 것」.
import Foundation

struct SeomGot { let ireum: String; let lat: Double; let lon: Double }

struct SeomJeongbo {
    let ireum: String
    let la1: Double, la2: Double, lo1: Double, lo2: Double
    let bihaeng: Bool
    let bae: [String]
    let mal: String
    var gyeongyu: String? = nil
    var baeGeochyeo: [String]? = nil
    var malGeochyeo: String? = nil
}

enum Seom {
    static let gonghang: [SeomGot] = [
        SeomGot(ireum: "인천국제공항", lat: 37.4602, lon: 126.4407), SeomGot(ireum: "김포공항", lat: 37.5583, lon: 126.7906),
        SeomGot(ireum: "김해국제공항", lat: 35.1795, lon: 128.9382), SeomGot(ireum: "대구국제공항", lat: 35.8940, lon: 128.6586),
        SeomGot(ireum: "청주국제공항", lat: 36.7166, lon: 127.4991), SeomGot(ireum: "무안국제공항", lat: 34.9914, lon: 126.3828),
        SeomGot(ireum: "광주공항", lat: 35.1264, lon: 126.8089), SeomGot(ireum: "여수공항", lat: 34.8423, lon: 127.6169),
        SeomGot(ireum: "사천공항", lat: 35.0886, lon: 128.0704), SeomGot(ireum: "울산공항", lat: 35.5936, lon: 129.3517),
        SeomGot(ireum: "포항경주공항", lat: 35.9878, lon: 129.4204), SeomGot(ireum: "군산공항", lat: 35.9038, lon: 126.6158),
        SeomGot(ireum: "원주공항", lat: 37.4381, lon: 127.9600), SeomGot(ireum: "양양국제공항", lat: 38.0613, lon: 128.6690)
    ]
    static let jejuGonghang = SeomGot(ireum: "제주국제공항", lat: 33.5113, lon: 126.4930)
    static let hanggu: [String: SeomGot] = [
        "incheon": SeomGot(ireum: "인천 연안여객터미널", lat: 37.4517, lon: 126.5977),
        "mokpo": SeomGot(ireum: "목포 연안여객선터미널", lat: 34.7856, lon: 126.3822),
        "wando": SeomGot(ireum: "완도항 여객선터미널", lat: 34.3117, lon: 126.7550),
        "yeosu": SeomGot(ireum: "여수 연안여객선터미널", lat: 34.7378, lon: 127.7460),
        "nokdong": SeomGot(ireum: "고흥 녹동신항 여객선터미널", lat: 34.5289, lon: 127.1289),
        "busan": SeomGot(ireum: "부산항 연안여객터미널", lat: 35.0996, lon: 129.0400),
        "pohang": SeomGot(ireum: "포항 여객선터미널", lat: 36.0424, lon: 129.3897),
        "gangneung": SeomGot(ireum: "강릉항 여객터미널", lat: 37.7736, lon: 128.9503),
        "mukho": SeomGot(ireum: "묵호항 여객선터미널", lat: 37.5486, lon: 129.1155),
        "hupo": SeomGot(ireum: "후포항 여객선터미널", lat: 36.6790, lon: 129.4576),
        "tongyeong": SeomGot(ireum: "통영항 여객선터미널", lat: 34.8412, lon: 128.4266),
        "gyeokpo": SeomGot(ireum: "부안 격포항 여객선터미널", lat: 35.6300, lon: 126.4650),
        "gunsan": SeomGot(ireum: "군산 연안여객선터미널", lat: 35.9857, lon: 126.7128),
        "jejuhang": SeomGot(ireum: "제주항 연안여객터미널", lat: 33.5197, lon: 126.5424),
        "uldodong": SeomGot(ireum: "울릉 도동항", lat: 37.4870, lon: 130.9046)
    ]
    static let seomdeul: [SeomJeongbo] = [
        SeomJeongbo(ireum: "제주도", la1: 33.05, la2: 33.65, lo1: 126.05, lo2: 127.00, bihaeng: true, bae: ["mokpo", "wando", "yeosu", "nokdong", "busan"], mal: "제주는 육지에서 이어진 길이 없습니다. 비행기나 배로 가셔야 합니다."),
        SeomJeongbo(ireum: "추자도", la1: 33.88, la2: 34.02, lo1: 126.24, lo2: 126.42, bihaeng: false, bae: ["wando", "jejuhang", "mokpo"], mal: "추자도는 배로만 갈 수 있습니다."),
        SeomJeongbo(ireum: "울릉도", la1: 37.40, la2: 37.62, lo1: 130.70, lo2: 131.00, bihaeng: false, bae: ["pohang", "gangneung", "mukho", "hupo"], mal: "울릉도는 배로만 갈 수 있습니다. 울릉공항은 아직 문을 열지 않았습니다."),
        SeomJeongbo(ireum: "독도", la1: 37.20, la2: 37.30, lo1: 131.80, lo2: 131.95, bihaeng: false, bae: ["pohang", "gangneung", "mukho", "hupo"], mal: "독도는 울릉도까지 배로 가신 뒤 다시 배를 타셔야 합니다.",
                    gyeongyu: "울릉도", baeGeochyeo: ["uldodong"], malGeochyeo: "울릉도에 계십니다. 독도로 가는 배는 도동항에서 뜹니다. 날씨가 궂으면 배가 뜨고도 독도에 내리지 못하는 날이 있습니다."),
        SeomJeongbo(ireum: "백령도와 대청도", la1: 37.68, la2: 38.02, lo1: 124.50, lo2: 124.95, bihaeng: false, bae: ["incheon"], mal: "백령도와 대청도는 배로만 갈 수 있습니다."),
        SeomJeongbo(ireum: "연평도", la1: 37.58, la2: 37.70, lo1: 125.60, lo2: 125.80, bihaeng: false, bae: ["incheon"], mal: "연평도는 배로만 갈 수 있습니다."),
        SeomJeongbo(ireum: "인천 앞바다 섬", la1: 37.00, la2: 37.30, lo1: 125.95, lo2: 126.35, bihaeng: false, bae: ["incheon"], mal: "이곳은 배로만 갈 수 있습니다."),
        SeomJeongbo(ireum: "흑산도와 홍도", la1: 34.00, la2: 34.80, lo1: 125.00, lo2: 125.55, bihaeng: false, bae: ["mokpo"], mal: "이곳은 배로만 갈 수 있습니다."),
        SeomJeongbo(ireum: "거문도", la1: 34.00, la2: 34.15, lo1: 127.25, lo2: 127.40, bihaeng: false, bae: ["yeosu"], mal: "거문도는 배로만 갈 수 있습니다."),
        SeomJeongbo(ireum: "청산도", la1: 34.14, la2: 34.25, lo1: 126.78, lo2: 126.95, bihaeng: false, bae: ["wando"], mal: "청산도는 배로만 갈 수 있습니다."),
        SeomJeongbo(ireum: "욕지도와 그 둘레 섬", la1: 34.55, la2: 34.72, lo1: 128.15, lo2: 128.45, bihaeng: false, bae: ["tongyeong"], mal: "이곳은 배로만 갈 수 있습니다."),
        SeomJeongbo(ireum: "위도", la1: 35.55, la2: 35.70, lo1: 126.22, lo2: 126.38, bihaeng: false, bae: ["gyeokpo"], mal: "위도는 배로만 갈 수 있습니다."),
        SeomJeongbo(ireum: "어청도", la1: 36.00, la2: 36.20, lo1: 125.90, lo2: 126.15, bihaeng: false, bae: ["gunsan"], mal: "어청도는 배로만 갈 수 있습니다.")
    ]

    static func jarilo(_ lat: Double, _ lon: Double) -> SeomJeongbo? {
        seomdeul.first { lat >= $0.la1 && lat <= $0.la2 && lon >= $0.lo1 && lon <= $0.lo2 }
    }
    static func chatgi(_ j: Jangso) -> SeomJeongbo? {
        if let s = jarilo(j.lat, j.lon) { return s }
        let t = j.ireum + " " + j.juso
        if ["제주특별자치도", "제주시", "서귀포"].contains(where: { t.contains($0) }) { return seomdeul[0] }
        if ["울릉군", "울릉읍", "울릉도"].contains(where: { t.contains($0) }) { return seomdeul[2] }
        return nil
    }
    /// 이미 그 섬(또는 거쳐 가는 섬) 안에 계신가
    static func anIn(_ s: SeomJeongbo, _ w: Wichi?) -> Bool {
        guard let w = w, let na = jarilo(w.lat, w.lon) else { return false }
        return na.ireum == s.ireum
    }
    static func geochyeoAnIn(_ s: SeomJeongbo, _ w: Wichi?) -> Bool {
        guard let g = s.gyeongyu, let w = w, let na = jarilo(w.lat, w.lon) else { return false }
        return na.ireum == g
    }
    static func malGoreugi(_ s: SeomJeongbo, _ w: Wichi?) -> String {
        geochyeoAnIn(s, w) ? (s.malGeochyeo ?? s.mal) : s.mal
    }
    /// 타는 곳 — 가까운 차례
    static func taneungot(_ s: SeomJeongbo, _ w: Wichi?, bihaeng: Bool) -> [(SeomGot, Double)] {
        var mok: [SeomGot] = []
        if bihaeng { if s.bihaeng { mok = gonghang } }
        else { mok = ((geochyeoAnIn(s, w) ? s.baeGeochyeo : nil) ?? s.bae).compactMap { hanggu[$0] } }
        let l = mok.map { g in (g, w.map { WichiEngine.geori($0.lat, $0.lon, g.lat, g.lon) } ?? 0) }
        return w == nil ? l : l.sorted { $0.1 < $1.1 }
    }
    /// 이 이름이 공항인가 항구인가 — 「타기 전에 챙길 것」
    static func taneungosinga(_ ireum: String) -> String? {
        let t = ireum.trimmingCharacters(in: .whitespaces)
        if gonghang.contains(where: { $0.ireum == t }) || t == jejuGonghang.ireum || t.hasSuffix("공항") { return "bihaenggi" }
        if hanggu.values.contains(where: { $0.ireum == t }) || t.contains("여객터미널") || t.contains("여객선터미널") || t.contains("선착장") { return "bae" }
        return nil
    }
    static let chaenggim: [String: [String]] = [
        "bihaenggi": [
            "표를 끊으실 때 항공사에 시각장애가 있다고 미리 알리십시오. 저비용 항공사는 대개 출발 마흔여덟 시간 전까지, 아시아나는 출발 이레 전까지 예약센터로 신청하도록 하고 있습니다. 당일 공항에서 말씀하시면 도움이 늦어질 수 있습니다.",
            "공항에서는 체크인 카운터까지 안내를 받으실 수 있습니다. 카운터에서 탑승구까지는 항공사에 따로 요청하셔야 합니다."
        ],
        "bae": [
            "표를 끊으실 때 선사에 시각장애가 있다고 미리 알리시는 것이 좋습니다.",
            "배를 타실 때는 승선 신고서를 쓰고 신분증을 보여야 합니다. 신분증을 꼭 챙기십시오.",
            "바람이 세면 배가 뜨지 않습니다. 나서시기 전에 여객터미널이나 선사에 전화로 뜨는지 확인하십시오."
        ]
    ]
    static let incheonAnsim = "인천공항은 제1여객터미널 3층 가운데에 장애인 안심여행센터가 있습니다. 나가시는 분만 쓰실 수 있고 복지카드를 지니셔야 합니다. 비행기 뜨기 세 시간 전까지 예약하시면 안심여행센터에서 체크인 카운터까지 전동차로 모셔다 드립니다. 전화는 아침 일곱 시부터 저녁 일곱 시까지 받습니다. 전화번호는 공 삼 이, 칠 사 일, 이 공 팔 이입니다."

    // MARK: 지하철이 다니는 고장
    private static let jicheol: [(Double, Double, Double, Double)] = [
        (37.00, 38.20, 126.20, 127.60), (36.72, 36.98, 126.85, 127.28), (37.78, 37.96, 127.58, 127.86),
        (35.03, 35.45, 128.72, 129.32), (35.74, 36.06, 128.32, 128.88), (35.07, 35.27, 126.72, 126.98), (36.23, 36.47, 127.26, 127.52)
    ]
    static func jicheolItna(_ lat: Double, _ lon: Double) -> Bool {
        jicheol.contains { lat >= $0.0 && lat <= $0.1 && lon >= $0.2 && lon <= $0.3 }
    }

    // MARK: 무엇이 몇 시부터 몇 시까지 — 대중적인 첫차·막차 어림(분)
    private static let ttae: [String: (Int, Int)] = ["jicheol": (330, 1440), "beoseu": (300, 1410), "sioe": (360, 1350), "gicha": (300, 1380), "bihaeng": (360, 1290), "bae": (420, 1080)]
    static func ttaeKkori(_ jong: String) -> String {
        guard let t = ttae[jong] else { return "" }
        let c = Calendar.current.dateComponents([.hour, .minute], from: Date())
        let bun = (c.hour ?? 0) * 60 + (c.minute ?? 0)
        if bun < t.0 || bun >= t.1 { return " — 지금은 끊겼을 수 있습니다" }
        if t.1 - bun <= 60 { return " — 막차가 곧 끊깁니다" }
        return ""
    }

    /// 그곳 바람(오픈메테오, 열쇠 없음) — 배가 뜨는지 가늠하시라고
    static func baramMal(_ lat: Double, _ lon: Double) async -> String? {
        guard let u = URL(string: String(format: "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=wind_speed_10m&wind_speed_unit=ms", lat, lon)),
              let r = try? await URLSession.shared.data(from: u),
              let o = try? JSONSerialization.jsonObject(with: r.0) as? [String: Any],
              let c = o["current"] as? [String: Any], let ms = Chatgi.su(c["wind_speed_10m"]) else { return nil }
        let b = Int(ms.rounded())
        if b >= 12 { return "지금 그곳 바람이 초속 \(b)미터입니다. 배가 뜨지 않을 수 있으니 나서시기 전에 꼭 전화로 확인하십시오." }
        if b >= 8 { return "지금 그곳 바람이 초속 \(b)미터입니다. 배가 흔들릴 만한 바람이니 미리 확인하시는 것이 좋습니다." }
        return "지금 그곳 바람은 초속 \(b)미터입니다."
    }
}
