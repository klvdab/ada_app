// 알아듣기 사전 — 웹 길눈과 같은 나스 사전(jeom/malsajeon.json)을 그대로 씁니다.
// 사전을 키우면 웹과 앱에 함께 반영되고, 앱을 고치지 않아도 더 잘 알아듣습니다.
// 통신이 끊겨도 한 번 받아 둔 사전으로 버티고, 처음부터 못 받으면 앱 안의 기본 말투로 알아듣습니다.
// 콜 번호도 웹과 같은 나스 자료(jeom/call.json)를 지금 계신 시·도에 맞춰 씁니다.
import Foundation

enum TteutGyeol { case gatda, bitseut }

struct KolBeonho {
    let ireum: String
    let jeonhwa: String
    let bigo: String
}

final class MalSajeon {
    static let shared = MalSajeon()

    private(set) var mokrok: [String: [String]] = MalSajeon.gibonTteut
    private(set) var kkeunmal: [String] = MalSajeon.gibonKkeunmal
    private(set) var ppaegi: [String] = MalSajeon.gibonPpaegi
    private(set) var pan = "앱 안 기본"
    private var kol: [String: Any]?

    /// 나스에서 사전과 콜 번호를 받음(받아 둔 것이 있으면 통신이 끊겨도 그것으로)
    func bureogi() {
        Task {
            if let d = try? await Tongsin.shared.get("malsajeon.json"),
               let o = try? JSONSerialization.jsonObject(with: d.data) as? [String: Any] {
                DispatchQueue.main.async { self.batda(o) }
            }
            if let d = try? await Tongsin.shared.get("call.json"),
               let o = try? JSONSerialization.jsonObject(with: d.data) as? [String: Any] {
                DispatchQueue.main.async { self.kol = o }
            }
        }
    }

    private func batda(_ o: [String: Any]) {
        if let t = o["tteut"] as? [String: Any] {
            var m = MalSajeon.gibonTteut
            for (k, v) in t {
                if let l = v as? [String], !l.isEmpty { m[k] = l }
            }
            mokrok = m
        }
        if let k = o["kkeunmal"] as? [String], !k.isEmpty { kkeunmal = k }
        if let p = o["ppaegi"] as? [String], !p.isEmpty { ppaegi = p }
        pan = (o["pan"] as? String) ?? pan
        Girok.shared.namgi("malsajeon", ["pan": pan])
    }

    // MARK: 뜻 찾기

    /// 띄어쓰기와 문장부호를 뺀 말
    static func ttuk(_ t: String) -> String {
        String(t.filter { !" \t\n.,!?~·'\"".contains($0) })
    }

    private static let CHO = Array("ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ")
    private static let JUNG = Array("ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ")
    private static let JONG = Array(" ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ")

    /// 한글을 자모로 풀기 — 발음이 비슷한 말을 알아듣는 데 씀
    static func jamo(_ t: String) -> [Character] {
        var o: [Character] = []
        for u in t.unicodeScalars {
            let c = Int(u.value) - 0xAC00
            if c >= 0 && c < 11172 {
                o.append(CHO[c / 588])
                o.append(JUNG[(c % 588) / 28])
                if c % 28 != 0 { o.append(JONG[c % 28]) }
            } else {
                o.append(Character(u))
            }
        }
        return o
    }

    /// 두 자모 줄 사이의 거리(몇 자를 고치면 같아지는가)
    static func geori(_ a: [Character], _ b: [Character]) -> Int {
        if a.isEmpty { return b.count }
        if b.isEmpty { return a.count }
        var ap = Array(0...b.count)
        for i in 1...a.count {
            var jigeum = [i] + Array(repeating: 0, count: b.count)
            for j in 1...b.count {
                let bakkum = ap[j - 1] + (a[i - 1] == b[j - 1] ? 0 : 1)
                jigeum[j] = min(ap[j] + 1, jigeum[j - 1] + 1, bakkum)
            }
            ap = jigeum
        }
        return ap[b.count]
    }

    /// 알아들은 말들(alts) 가운데 뜻 k 의 말투가 있으면 그대로(gatda) 또는 발음이 비슷(bitseut)
    func tteut(_ alts: [String], _ k: String) -> TteutGyeol? {
        let l = mokrok[k] ?? []
        for a in alts {
            let tx = MalSajeon.ttuk(a)
            if tx.isEmpty { continue }
            for p in l {
                let pp = MalSajeon.ttuk(p)
                if !pp.isEmpty && tx.contains(pp) { return .gatda }
            }
        }
        for a in alts {
            let jt = MalSajeon.jamo(MalSajeon.ttuk(a))
            if jt.isEmpty { continue }
            for p in l {
                let jp = MalSajeon.jamo(MalSajeon.ttuk(p))
                if jp.count < 6 { continue }
                let heo = jp.count / 6
                var s = 0
                while s < jt.count && s + jp.count - 1 <= jt.count + 1 {
                    let e = min(jt.count, s + jp.count)
                    if MalSajeon.geori(Array(jt[s..<e]), jp) <= heo { return .bitseut }
                    s += 1
                }
            }
        }
        return nil
    }

    /// 그대로 들어 있을 때만
    func itda(_ alts: [String], _ k: String) -> Bool { tteut(alts, k) == .gatda }

    // MARK: 목적지 말 떼어 내기

    /// "오늘은 집으로 가자" → ("집", 가자는 말이 붙어 있었는가, 뗀 말이 "로"로 시작했는가 — 종로·을지로처럼 이름 끝의 로를 되살리는 데 씀)
    func mokjeokMal(_ t: String) -> (q: String, gagi: Bool, ro: Bool) {
        let pp = Set(ppaegi.map { MalSajeon.ttuk($0) })
        var ws = t.split(separator: " ").map { String($0).trimmingCharacters(in: CharacterSet(charactersIn: ".,!?~")) }
        ws = ws.filter { !$0.isEmpty && !pp.contains(MalSajeon.ttuk($0)) }
        var q = ws.joined(separator: " ")
        var gagi = false
        var ro = false
        let kl = kkeunmal.sorted { MalSajeon.ttuk($0).count > MalSajeon.ttuk($1).count }
        for km in kl {
            let kz = MalSajeon.ttuk(km)
            let qz = MalSajeon.ttuk(q)
            if kz.isEmpty || qz.count <= kz.count || !qz.hasSuffix(kz) { continue }
            var n = kz.count
            var qq = q
            while n > 0 && !qq.isEmpty {
                if qq.last != " " { n -= 1 }
                qq.removeLast()
            }
            q = qq.trimmingCharacters(in: .whitespaces)
            gagi = kz.count >= 2
            ro = kz.hasPrefix("로")
            break
        }
        return (q, gagi, ro)
    }

    // MARK: 콜 번호

    /// 지금 계신 시·도의 콜 번호(못 받았으면 서울)
    func kolDeul() -> [KolBeonho] {
        var l: [KolBeonho] = []
        if let o = kol, let jy = o["지역"] as? [[String: Any]], !jy.isEmpty {
            var got = jy[0]
            if let w = WichiEngine.shared.jigeum {
                for g in jy {
                    if let s = g["상자"] as? [Any], s.count == 4,
                       let a0 = Chatgi.su(s[0]), let a1 = Chatgi.su(s[1]), let o0 = Chatgi.su(s[2]), let o1 = Chatgi.su(s[3]),
                       w.lat >= a0 && w.lat <= a1 && w.lon >= o0 && w.lon <= o1 {
                        got = g
                        break
                    }
                }
            }
            var kk = (got["콜"] as? [[String: Any]]) ?? []
            kk += (o["전국"] as? [[String: Any]]) ?? []
            for k in kk {
                let nm = (k["이름"] as? String) ?? ""
                let tel = ((k["전화"] as? String) ?? "").filter { $0.isNumber }
                if !nm.isEmpty && !tel.isEmpty { l.append(KolBeonho(ireum: nm, jeonhwa: tel, bigo: (k["비고"] as? String) ?? "")) }
            }
        }
        if l.isEmpty {
            l = [KolBeonho(ireum: "복지콜", jeonhwa: "0220920000", bigo: "시각·신장 장애인 전용"),
                 KolBeonho(ireum: "장애인콜택시", jeonhwa: "15884388", bigo: "서울시설공단"),
                 KolBeonho(ireum: "나비콜(바우처택시)", jeonhwa: "18001133", bigo: "바우처택시 이용등록을 마친 뒤 이용")]
        }
        return l
    }

    /// 콜 한 가지 찾기 — bokji, jangaein, nabi
    func kolChatgi(_ jong: String) -> KolBeonho? {
        let l = kolDeul()
        switch jong {
        case "bokji": return l.first { $0.ireum.contains("복지") }
        case "jangaein": return l.first { $0.ireum.contains("장애인") || $0.ireum.contains("교통약자") }
        default: return l.first { $0.ireum.contains("나비") || $0.ireum.contains("바우처") }
        }
    }

    /// 전화번호를 한 자씩 — 공 이 구 이 …
    static func beonhoMal(_ n: String) -> String {
        let su = Array("공일이삼사오육칠팔구")
        return n.map { c -> String in
            if let d = c.wholeNumberValue, d >= 0 && d < 10 { return String(su[d]) }
            return String(c)
        }.joined(separator: " ")
    }

    // MARK: 앱 안 기본 말투(나스 사전을 한 번도 못 받았을 때)

    static let gibonPpaegi = ["오늘은", "지금", "길눈아", "길눈", "헤이", "하이", "좀", "우리", "저기", "음", "어"]

    static let gibonKkeunmal = ["으로 가자", "로 가자", "에 가자", "까지 가자", "으로 가 줘", "로 가 줘", "가자", "가 줘", "갈래",
                                "가고 싶어", "데려다 줘", "안내해 줘", "가는 길 알려 줘", "가는 길", "찾아 줘", "까지", "으로", "로", "에"]

    static let gibonTteut: [String: [String]] = [
        "ye": ["네", "예", "응", "그래", "좋아", "맞아", "맞습니다", "그렇게 해", "부탁해", "오케이", "해 줘"],
        "ani": ["아니", "아니요", "아니오", "아뇨", "아냐", "싫어", "됐어", "괜찮아", "말고", "다른 곳", "다른 데", "틀렸어"],
        "geotgi": ["걸어", "걷자", "걸어가자", "걸어서", "도보"],
        "cha": ["차로", "차 타고", "택시", "콜택시", "타고 가자", "차로 가자"],
        "daejung": ["지하철", "전철", "버스", "대중교통", "기차", "열차"],
        "kol_bokji": ["복지콜", "복지 콜"],
        "kol_jangaein": ["장애인콜", "장애인 콜택시", "장애인택시", "교통약자"],
        "kol_nabi": ["나비콜", "나비 콜", "바우처택시", "바우처 택시"],
        "tatda": ["탔어", "탔습니다", "차에 탔어", "승차", "타고 있어"],
        "naerim": ["내렸어", "내렸습니다", "하차", "차에서 내렸"],
        "jigeum_gil": ["지금 가는 길", "경로 알려", "남은 길", "어떻게 가야"],
        "sigan": ["얼마나 걸려", "얼마나 걸리", "얼마나 남았", "언제 도착", "몇 분"],
        "eodi": ["여기가 어디", "지금 어디", "내 위치", "어디쯤"],
        "cheoeum": ["처음으로", "처음부터", "다시 시작"],
        "dasi": ["다시 말해", "뭐라고", "한 번 더", "못 들었어"],
        "geuman": ["그만", "취소", "멈춰", "조용히 해"],
        "doum": ["도와줘", "도와주세요", "도움 요청", "긴급통화", "사람 불러", "해설사 요청", "호출해 줘", "살려 줘"],
        "doumal": ["도움말", "무슨 말 할 수", "할 수 있는 말"],
        "kol_beonho": ["전화번호", "번호 알려", "연락처"],
        "sigan_now": ["몇 시", "지금 시간", "오늘 며칠", "무슨 요일", "날짜"],
        "charye": ["할 차례", "이제 뭐 해", "다음 할 일"],
        "yeojeong_kkeut": ["여정 끝", "안내 끝", "길 안내 그만", "목적지 취소", "여정 취소"],
        "bareuge": ["빠르게", "빨리 말해"],
        "neurige": ["느리게", "천천히 말해"],
        "annae_kkeum": ["말소리 꺼", "안내 음성 꺼"],
        "annae_kyeom": ["말소리 켜", "안내 음성 켜"],
        "mok_bakkum": ["목소리 바꿔", "다른 목소리"],
        "hoching": ["호칭 바꿔", "부르는 이름", "나를 뭐라고"],
        "saerogochim": ["새로고침", "새로 고침"],
        "jeulgyeo_dam": ["즐겨찾기에 넣어", "즐겨찾기 담아", "담아 줘", "저장해 줘"],
        "jeulgyeo_mok": ["즐겨찾기 목록", "즐겨찾기 알려", "담아 둔 곳"]
    ]
}
