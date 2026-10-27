// 일정 — 폰 캘린더를 말로 듣고 넣고 고치기, 아침 브리핑, 떠날 시각 알림
// 2.116.0 (빌드 261027-I70, 대클, 이사장님 승인 2026-10-27 「1번으로 하는데 위치를 음악방송이 아니라 설정탭에 넣도록하자」)
// 안드로이드 길눈 2.81.0 Iljeong.kt·IljeongMal.kt 와 같은 약속, 같은 말.
// ① 일정은 아이폰 「캘린더」에 담음 — 길눈이 넣는 일정은 「길눈」 달력 하나에 모음(만들 수 없으면 기본 달력에 표시를 붙여). 폰을 바꿔도 남음
// ② 읽기는 모든 달력, 고치기·지우기는 길눈이 넣은 일정만
// ③ 넣기·고치기·지우기는 반드시 한 번 읽어 드리고 「네」를 들은 뒤에. 지운 일정은 하루 동안 되살림
// ④ 일정 내용은 폰 밖으로 보내지 않음 — 이용 기록에는 「썼다」는 것만
// ⑤ 아이폰은 앱이 스스로 깨어날 수 없어, 길눈이 켜져 있으면 정한 때에 바로 말하고, 꺼져 있으면 알림을 울려 누르시면 말함
// ⑥ 떠날 시각은 지금 자리에서 그곳까지 곧은 거리로 어림(1.5킬로미터 안은 걸음, 그 넘으면 탈것) — 어림이라고 늘 밝힘
import Foundation
import SwiftUI
import UIKit
import EventKit
import UserNotifications

// MARK: - 말 풀기(안드로이드 IljeongMal 과 같은 셈)

final class IljeongPul {
    var jemok = ""
    var jemokMal: [String] = []
    var jangso = ""
    var nal: Date?
    var nalRo: Date?
    var si: Int?
    var bun = 0
    var kkeutSi: Int?
    var kkeutBun = 0
    var sigakRo: (Int, Int)?
    var haruJongil = false
    var banbok = ""      // maeil, pyeongil, jumal, maeju
    var yoil = 0         // 일요일 1 … 토요일 7
    var jusu = 0         // 이번 주 1, 다음 주 2, 이번 주말 3
    var ampmMal = false  // 2.117.0 오전·오후·아침·저녁 같은 말을 하셨는가(「7시로」를 오후로 볼지 가름)
}

enum IljeongMal {
    static let AMPM = ["오전", "오후", "아침", "저녁", "밤", "낮", "새벽"]
    static let YOIL: [Character] = Array("일월화수목금토")
    static let JOSA = ["으로", "부터", "까지", "에는", "에도", "마다", "에", "은", "는", "도", "로", "쯤", "께", "날", "을", "를"]
    static let DONGSA: Set<String> = [
        "넣어", "넣어줘", "넣어주세요", "넣어줄래", "넣어줄래요", "넣자", "넣기", "넣고",
        "추가", "추가해", "추가해줘", "추가해주세요", "추가하기",
        "잡아", "잡아줘", "잡아주세요", "등록", "등록해", "등록해줘", "등록해주세요",
        "적어", "적어줘", "적어둬", "적어놔", "적어주세요", "기록", "기록해", "기록해줘", "저장", "저장해", "저장해줘",
        "바꿔", "바꿔줘", "바꿔주세요", "바꾸자", "옮겨", "옮겨줘", "옮겨주세요", "변경", "변경해", "변경해줘",
        "미뤄", "미뤄줘", "당겨", "당겨줘", "고쳐", "고쳐줘",
        "지워", "지워줘", "지워주세요", "삭제", "삭제해", "삭제해줘", "취소", "취소해", "취소해줘", "빼", "빼줘", "없애", "없애줘",
        "알려", "알려줘", "알려주세요", "읽어", "읽어줘", "들려", "들려줘", "말해", "말해줘", "뭐", "뭐야", "뭐있어", "있어", "있니", "있나", "있어요",
        "줘", "주세요", "해", "해줘", "해주세요", "좀", "하고", "그리고", "그", "이", "방금", "모두", "전부", "다", "한번만", "이번만",
        "일정", "일정에", "일정으로", "일정을", "일정은", "일정이", "일정도", "스케줄", "스케줄에", "달력", "달력에", "캘린더", "캘린더에",
        "시간", "시간에", "시간을", "시간은", "날짜", "날짜를", "날짜는", "장소", "장소는", "장소를", "길눈", "헤이", "하이",
        // 2.117.0 (261027-I71) 더 알아듣는 말
        "일정의", "일정이야", "그거", "아까", "수정", "수정해", "수정해줘", "편집", "편집해", "편집해줘", "검색", "검색해", "검색해줘",
        "조회", "조회해", "조회해줘", "찾아", "찾아줘", "확인", "확인해", "확인해줘", "보여", "보여줘", "예약해", "예약해줘",
        "만들어", "만들어줘", "알려줄래", "있는지", "없어", "어때", "어떻게돼", "자세히"
    ]
    // 2.117.0 (261027-I71, 이사장님 승인 2026-10-27 「순서대로 모두 고쳐」) 시키는 말 갈래 — 안드로이드 IljeongMal 과 같음
    static let NEOKI = ["넣어", "넣자", "넣고", "추가", "잡아", "등록", "적어", "기록해", "저장해", "만들어", "예약해"]
    static let BAKKUGI = ["바꿔", "바꾸", "옮겨", "옮기", "변경", "미뤄", "당겨", "고쳐", "고치", "수정", "편집"]
    static let JIUGI = ["지워", "지우", "삭제", "취소", "빼줘", "빼라", "없애"]
    static let ILGI = ["알려", "뭐", "있어", "있나", "있니", "있냐", "있는지", "읽어", "들려", "말해", "확인", "검색", "조회", "찾아", "보여", "몇개", "어때", "어떻게돼", "자세히"]
    /** 일정과 다른 일을 시키는 말 — 이런 말이 있으면 「오후 2시 복지콜」처럼 움직임 말 없는 일정으로 보지 않음 */
    static let DARUN = ["불러", "호출", "틀어", "꺼", "켜", "깨워", "가자", "가줘", "가고", "안내", "길", "전화", "문자", "보내", "들어", "재생", "멈춰", "그만"]
    /** 읽어 드리는 목록의 번호 말 */
    static let SEOSU = ["첫째", "둘째", "셋째", "넷째", "다섯째", "여섯째", "일곱째", "여덟째", "아홉째", "열째", "열한째", "열두째", "열셋째", "열넷째", "열다섯째"]
    static let GOT_KKEUT = ["극장", "병원", "의원", "치과", "한의원", "센터", "회관", "공원", "사무실", "학교", "구청", "시청", "주민센터",
        "은행", "마트", "시장", "카페", "식당", "호텔", "공항", "터미널", "도서관", "복지관", "협회", "교회", "성당", "박물관", "미술관",
        "백화점", "약국", "역", "청", "관", "원", "점"]
    static let BUTIM: [(String, String)] = [("다음 주", "다음주"), ("이번 주", "이번주"), ("담 주", "다음주"), ("이번주 말", "이번주말"), ("하루 종일", "하루종일"),
        ("매 주", "매주"), ("매 일", "매일"), ("평일 마다", "평일마다"), ("주말 마다", "주말마다"), ("넣어 줘", "넣어줘"), ("추가해 줘", "추가해줘"),
        ("잡아 줘", "잡아줘"), ("바꿔 줘", "바꿔줘"), ("옮겨 줘", "옮겨줘"), ("지워 줘", "지워줘"), ("알려 줘", "알려줘"), ("등록해 줘", "등록해줘"),
        ("적어 줘", "적어줘"), ("삭제해 줘", "삭제해줘"), ("취소해 줘", "취소해줘"), ("읽어 줘", "읽어줘"), ("들려 줘", "들려줘")]

    static func ttuk(_ t: String) -> String { String(t.filter { !" \t\n.,!?~·'\"".contains($0) }) }

    static func josaBbaegi(_ w: String) -> (String, String) {
        for j in JOSA where w.count > j.count && w.hasSuffix(j) { return (String(w.dropLast(j.count)), j) }
        return (w, "")
    }

    static func yoilBeon(_ c: Character) -> Int { (YOIL.firstIndex(of: c) ?? -1) + 1 }

    static func haru(_ now: Date, _ d: Int) -> Date {
        let cal = Calendar.current
        return cal.date(byAdding: .day, value: d, to: cal.startOfDay(for: now)) ?? now
    }

    static func juYoil(_ now: Date, _ yo: Int, _ daeum: Bool) -> Date {
        let oneul = Calendar.current.component(.weekday, from: now)
        let oneulBeon = (oneul + 5) % 7
        let geuBeon = (yo + 5) % 7
        return haru(now, geuBeon - oneulBeon + (daeum ? 7 : 0))
    }

    static func gakkaunYoil(_ now: Date, _ yo: Int) -> Date {
        let oneul = Calendar.current.component(.weekday, from: now)
        return haru(now, (yo - oneul + 7) % 7)
    }

    static func ilWol(_ now: Date, _ wol: Int?, _ il: Int) -> Date? {
        guard il >= 1 && il <= 31 else { return nil }
        let cal = Calendar.current
        let oneul = haru(now, 0)
        var dc = cal.dateComponents([.year, .month], from: oneul)
        if let w = wol {
            guard w >= 1 && w <= 12 else { return nil }
            dc.month = w
        }
        dc.day = il
        guard var d = cal.date(from: dc) else { return nil }
        if wol != nil {
            if d < oneul.addingTimeInterval(-86400) { d = cal.date(byAdding: .year, value: 1, to: d) ?? d }
        } else if d < oneul {
            d = cal.date(byAdding: .month, value: 1, to: d) ?? d
        }
        return d
    }

    // 2.117.0 (261027-I71) 우리말 시각 — 「두시」, 「열한 시 반」, 「두 시 삼십 분」(「한 시간」은 그대로)
    static let U_SU: [String: Int] = ["한": 1, "두": 2, "세": 3, "네": 4, "다섯": 5, "여섯": 6, "일곱": 7, "여덟": 8, "아홉": 9, "열": 10, "열한": 11, "열두": 12]

    /// 한자말 수 「삼십오」 → 35 (1~59)
    static func hanjaSu(_ s: String) -> Int? {
        let il: [Character: Int] = ["일": 1, "이": 2, "삼": 3, "사": 4, "오": 5, "육": 6, "칠": 7, "팔": 8, "구": 9]
        let a = Array(s)
        guard !a.isEmpty else { return nil }
        var n = 0
        if let k = a.firstIndex(of: "십") {
            guard let ap = k == 0 ? 1 : (k == 1 ? il[a[0]] : nil) else { return nil }
            var dwi = 0
            if k < a.count - 1 { guard k == a.count - 2, let d = il[a[k + 1]] else { return nil }; dwi = d }
            n = ap * 10 + dwi
        } else {
            guard a.count == 1, let d = il[a[0]] else { return nil }
            n = d
        }
        return (1...59).contains(n) ? n : nil
    }

    /// 정규식으로 찾은 것마다 바꿈(뒤에서부터)
    static func bakkuGi(_ pat: String, _ s: String, _ f: ([String]) -> String?) -> String {
        guard let r = try? NSRegularExpression(pattern: pat) else { return s }
        var out = s
        let ns = s as NSString
        for m in r.matches(in: s, range: NSRange(location: 0, length: ns.length)).reversed() {
            let g = (0..<m.numberOfRanges).map { i -> String in
                let rg = m.range(at: i)
                return rg.location == NSNotFound ? "" : ns.substring(with: rg)
            }
            guard let sae = f(g), let rg = Range(m.range, in: out) else { continue }
            out.replaceSubrange(rg, with: sae)
        }
        return out
    }

    /// 우리말·한자말 시각을 숫자로 — 「오후 두시에」 → 「오후 2시에」
    static func sutjaRo(_ t0: String) -> String {
        var t = bakkuGi("(^|\\s|오전|오후|아침|저녁|밤|낮|새벽)(열한|열두|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열)\\s*시(?!간)", t0) { g in
            guard let n = U_SU[g[2]] else { return nil }
            let ap = g[1]
            return ap + (!ap.isEmpty && !ap.trimmingCharacters(in: .whitespaces).isEmpty ? " " : "") + "\(n)시"
        }
        t = bakkuGi("(\\d{1,2})\\s*시\\s*((?:[이삼사오]?십)?[일이삼사오육칠팔구]?)\\s*분", t) { g in
            guard let b = hanjaSu(g[2]) else { return nil }
            return "\(g[1])시 \(b)분"
        }
        return t
    }

    /// 말 다듬기 — 띄어 쓴 한 말을 붙이고, 숫자 앞을 띄움
    static func dadeum(_ t0: String) -> String {
        var t = " " + sutjaRo(t0.trimmingCharacters(in: .whitespacesAndNewlines)) + " "
        for (a, b) in BUTIM { t = t.replacingOccurrences(of: a, with: b) }
        var out = ""
        var ap: Character? = nil
        for ch in t {
            if ch.isNumber, let p = ap, !p.isNumber, !p.isWhitespace { out.append(" ") }
            out.append(ch)
            ap = ch
        }
        return out.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
    }

    /// 정규식 — 맞으면 묶음들(0은 전체, 없는 묶음은 빈 글)
    static func maj(_ p: String, _ s: String) -> [String]? {
        guard let r = try? NSRegularExpression(pattern: p) else { return nil }
        let ns = s as NSString
        guard let m = r.firstMatch(in: s, range: NSRange(location: 0, length: ns.length)) else { return nil }
        return (0..<m.numberOfRanges).map { i -> String in
            let rg = m.range(at: i)
            return rg.location == NSNotFound ? "" : ns.substring(with: rg)
        }
    }

    static func pulgi(_ t0: String, _ now: Date, _ jeulgyeo: [String] = []) -> IljeongPul {
        let p = IljeongPul()
        let mal = dadeum(t0).split(separator: " ").map(String.init).filter { !$0.isEmpty }
        var nameun: [String] = []
        var ampm = ""
        var jeonche = ""
        for w in mal {
            let z = ttuk(w)
            for a in AMPM where z.hasPrefix(a) { jeonche = a; break }
        }
        p.ampmMal = !jeonche.isEmpty
        var juMal = 0
        var wolDaegi: Int? = nil
        var sigakDeul: [(Int, Int, Bool)] = []
        var jangsoMoeum = false
        var i = 0
        while i < mal.count {
            let w = mal[i]
            let z0 = ttuk(w)
            i += 1
            if z0.isEmpty { continue }
            if z0 == "장소는" || z0 == "장소" || z0 == "장소가" || z0 == "장소를" { jangsoMoeum = true; p.jangso = ""; continue }
            let (z, josa) = josaBbaegi(z0)
            let gidaMal = jangsoMoeum
            jangsoMoeum = false
            let ro = josa == "로" || josa == "으로"
            var zz = z
            var ap = ""
            for a in AMPM where zz.hasPrefix(a) { ap = a; zz = String(zz.dropFirst(a.count)); break }
            if !ap.isEmpty && zz.isEmpty {
                // 바로 뒤가 시각이면 오전·오후로, 아니면 「아침 운동」처럼 제목의 한 말
                let dwi = i < mal.count ? ttuk(mal[i]) : ""
                if !dwi.isEmpty && (dwi.first!.isNumber || dwi.hasPrefix("정오") || dwi.hasPrefix("자정")) { ampm = ap; continue }
                // 2.117.0 「오전」·「오후」는 제목이 되지 않음 — 「오후 일정 2시에」
                if ap == "오전" || ap == "오후" { ampm = ap; continue }
                nameun.append(w)
                continue
            }
            if zz == "정오" || z == "정오" { sigakDeul.append((12, 0, ro)); continue }
            if zz == "자정" || z == "자정" { sigakDeul.append((0, 0, ro)); continue }
            if z == "하루종일" || z == "종일" || z == "온종일" { p.haruJongil = true; continue }
            if z == "하루" && i < mal.count && ttuk(mal[i]).hasPrefix("종일") { p.haruJongil = true; i += 1; continue }
            if let sm = maj("^(\\d{1,2})시(반|(\\d{1,2})분)?$", zz) {
                var h = Int(sm[1]) ?? 0
                var m = sm[2] == "반" ? 30 : (Int(sm[3]) ?? 0)
                if sm[2].isEmpty && i < mal.count {
                    let nz = josaBbaegi(ttuk(mal[i])).0
                    if let bm = maj("^(\\d{1,2})분$", nz) { m = Int(bm[1]) ?? 0; i += 1 }
                    else if nz == "반" { m = 30; i += 1 }
                }
                if m > 59 || h > 24 { nameun.append(w); continue }
                let apSseul = !ap.isEmpty ? ap : (!ampm.isEmpty ? ampm : jeonche)
                h = ojeonOhu(h, apSseul)
                var roHam = ro
                if !roHam && i < mal.count {
                    let nz = ttuk(mal[i])
                    if nz == "로" || nz == "으로" { roHam = true; i += 1 }
                }
                sigakDeul.append((h, m, roHam))
                ampm = ""
                continue
            }
            if maj("^(\\d{1,2})분$", z) != nil || z == "반" { continue }
            switch z {
            case "오늘": nalNeoki(p, haru(now, 0), ro); continue
            case "내일": nalNeoki(p, haru(now, 1), ro); continue
            case "모레": nalNeoki(p, haru(now, 2), ro); continue
            case "글피": nalNeoki(p, haru(now, 3), ro); continue
            case "이번주": juMal = 1; if p.jusu == 0 { p.jusu = 1 }; continue
            case "다음주": juMal = 2; p.jusu = 2; continue
            case "이번주말", "주말":
                if josa == "마다" || z0 == "주말마다" { p.banbok = "jumal" } else { p.jusu = 3 }
                continue
            case "주말마다": p.banbok = "jumal"; continue
            case "매주": p.banbok = "maeju"; juMal = 3; continue
            case "매일", "날마다", "매일매일": p.banbok = "maeil"; continue
            case "평일", "평일마다", "주중": p.banbok = "pyeongil"; continue
            default: break
            }
            if z0 == "평일마다" || z0 == "주말마다" || z0 == "날마다" {
                p.banbok = z0 == "평일마다" ? "pyeongil" : (z0 == "주말마다" ? "jumal" : "maeil")
                continue
            }
            if let ym = maj("^(이번주|다음주|매주)?([일월화수목금토])요일$", z), let yc = ym[2].first {
                let yo = yoilBeon(yc)
                let ju: Int = ym[1] == "이번주" ? 1 : (ym[1] == "다음주" ? 2 : (ym[1] == "매주" ? 3 : juMal))
                if ju == 3 || josa == "마다" {
                    p.banbok = "maeju"
                    p.yoil = yo
                    nalNeoki(p, gakkaunYoil(now, yo), false)
                } else {
                    let d = ju == 1 ? juYoil(now, yo, false) : (ju == 2 ? juYoil(now, yo, true) : gakkaunYoil(now, yo))
                    nalNeoki(p, d, ro)
                    if ju != 0 && p.jusu != 0 && !ro { p.jusu = 0 }   // 「다음 주 화요일」은 그날 하루
                }
                juMal = 0
                continue
            }
            if let wi = maj("^(\\d{1,2})월(\\d{1,2})일$", z) {
                if let d = ilWol(now, Int(wi[1]), Int(wi[2]) ?? 0) { nalNeoki(p, d, ro) }
                continue
            }
            if let wm = maj("^(\\d{1,2})월$", z) { wolDaegi = Int(wm[1]); continue }
            if let im = maj("^(\\d{1,2})일$", z) {
                if let d = ilWol(now, wolDaegi, Int(im[1]) ?? 0) { nalNeoki(p, d, ro) }
                wolDaegi = nil
                continue
            }
            if DONGSA.contains(z0) || DONGSA.contains(z) { continue }
            if z0.hasSuffix("에서") && z0.count > 2 {
                let g = String(z0.dropLast(2))
                if p.jangso.isEmpty { p.jangso = g }
                nameun.append(g)
                continue
            }
            if gidaMal {   // 장소는 ○○ — 곳 이름에 이어 붙임(제목에는 넣지 않음)
                p.jangso = (p.jangso + " " + josaBbaegi(z0).0).trimmingCharacters(in: .whitespaces)
                jangsoMoeum = true
                continue
            }
            nameun.append(w)
        }
        let bo = sigakDeul.filter { !$0.2 }
        let roS = sigakDeul.last(where: { $0.2 })
        if let b0 = bo.first { p.si = b0.0; p.bun = b0.1 }
        if bo.count >= 2 {
            var kh = bo[1].0
            if kh * 60 + bo[1].1 <= bo[0].0 * 60 + bo[0].1 && kh < 12 { kh += 12 }
            p.kkeutSi = kh
            p.kkeutBun = bo[1].1
        }
        if let r = roS { p.sigakRo = (r.0, r.1) }
        var jm = nameun.map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        if let k = jm.last, k.count >= 2, (k.hasSuffix("을") || k.hasSuffix("를")) { jm[jm.count - 1] = String(k.dropLast()) }
        p.jemok = jm.joined(separator: " ")
        p.jemokMal = jm.map { ttuk(josaBbaegi(ttuk($0)).0) }.filter { !$0.isEmpty }
        if p.jangso.isEmpty {
            if let jg = jeulgyeo.first(where: { g in !g.isEmpty && jm.contains(where: { ttuk($0) == ttuk(g) }) }) {
                p.jangso = jg
            } else if jm.count >= 2 {
                // 「병원」·「은행」처럼 흔한 말 하나만으로는 곳을 정하지 않음 — 「서울대병원」처럼 앞에 이름이 붙어야
                let c = ttuk(jm[0])
                if GOT_KKEUT.contains(where: { s in c.hasSuffix(s) && c.count > s.count && c.count >= 3 }) { p.jangso = jm[0] }
            }
        }
        return p
    }

    static func nalNeoki(_ p: IljeongPul, _ d: Date, _ ro: Bool) {
        if ro { p.nalRo = d } else if p.nal == nil { p.nal = d } else { p.nalRo = d }
    }

    /// 오전·오후가 없으면 1시~6시는 오후로 봄(약속은 대개 낮), 아침·오전·새벽 12시는 0시
    static func ojeonOhu(_ h0: Int, _ ap: String) -> Int {
        var h = h0
        if h == 24 { return 0 }
        switch ap {
        case "오후", "저녁", "밤": if h < 12 { h += 12 }
        case "낮": if h >= 1 && h <= 6 { h += 12 }
        case "오전", "아침", "새벽": if h == 12 { h = 0 }
        default: if h >= 1 && h <= 6 { h += 12 }
        }
        return h
    }

    /// 「1번」, 「두 번째」, 「셋째」
    static func beonho(_ z0: String) -> Int? {
        let z = ttuk(z0)
        if let m = maj("(\\d{1,2})", z) { return Int(m[1]) }
        let su: [(String, Int)] = [("첫", 1), ("한", 1), ("하나", 1), ("일번", 1), ("두", 2), ("둘", 2), ("이번", 2), ("세", 3), ("셋", 3), ("삼번", 3),
                                   ("네", 4), ("넷", 4), ("사번", 4), ("다섯", 5), ("오번", 5)]
        for (k, v) in su where z.hasPrefix(k) && (z.contains("번") || z.contains("째")) { return v }
        return nil
    }

    /// 2.117.0 「첫째」, 「두 번째」, 「2번」 — 읽어 드린 목록의 몇째인가(「2시」, 「2번 출구」는 아님)
    static func mokBeonho(_ t: String) -> Int? {
        let z = ttuk(t)
        if let m = maj("(첫|두|둘|세|셋|네|넷|다섯|여섯|일곱|여덟|아홉|열)(번)?째", z) {
            switch m[1] {
            case "첫": return 1
            case "두", "둘": return 2
            case "세", "셋": return 3
            case "네", "넷": return 4
            case "다섯": return 5
            case "여섯": return 6
            case "일곱": return 7
            case "여덟": return 8
            case "아홉": return 9
            default: return 10
            }
        }
        if let m = maj("(\\d{1,2})번(째)?(?!출|버스|마을|선)", z) { return Int(m[1]) }
        return nil
    }

    /// 「그 일정」, 「방금 넣은 거」 — 바로 앞에 말한 일정을 가리킴
    static func geuMal(_ t: String) -> Bool {
        let z = ttuk(t)
        return ["그일정", "이일정", "방금", "아까", "그거", "그것", "그약속", "그건", "거기에", "거기"].contains { z.contains($0) }
    }

    /// 고칠 칸 — kan: jemok·jangso·naeyong, hal: bakkugi·deohagi·ilgi·jiugi, dae: 칸 이름 앞의 말(어느 일정), gap: 새 값
    struct GochimPul { let kan: String; let hal: String; let dae: String; let gap: String }

    static let KAN: [(String, String)] = [("제목", "jemok"), ("이름", "jemok"), ("장소", "jangso"), ("내용", "naeyong"), ("메모", "naeyong"), ("설명", "naeyong"), ("적은것", "naeyong"), ("적은거", "naeyong")]
    static let GAP_BBAEGI: Set<String> = ["을", "를", "은", "는", "에", "에다", "에다가", "도", "이", "가", "으로", "로", "에는", "좀", "다시", "새로", "새"]
    /// 이것으로 시작하는 낱말은 시키는 말(두 글자 넘는 것만 — 「해운대」가 빠지지 않게)
    static let GAP_DONGSA = ["바꿔", "바꾸", "옮겨", "변경", "고쳐", "수정", "편집", "추가", "넣어", "넣자", "적어", "기록해", "저장", "등록", "달아", "붙여",
                             "지워", "지우", "삭제", "없애", "알려", "읽어", "들려", "말해", "확인", "해줘", "해주", "주세요", "할래", "하자", "해라"]
    /// 통째로 같을 때만 시키는 말
    static let GAP_JEONG: Set<String> = ["해", "줘", "좀", "뭐", "빼", "빼줘", "줄래", "라고", "이라고", "다시", "새로"]

    /// 「2시 일정 제목 복지콜로 바꿔 줘」, 「복지콜 일정에 내용 추가 휠체어 탑승」, 「그 일정 내용 읽어 줘」 — 칸 이름이 없거나 새 일정이면 nil
    static func gochimPulgi(_ t0: String, _ now: Date) -> GochimPul? {
        let t = dadeum(t0)
        var kanIreum = "", kan = ""
        var idx: String.Index? = nil
        for (k, v) in KAN {
            if let r = t.range(of: k), idx == nil || r.lowerBound < idx! { idx = r.lowerBound; kanIreum = k; kan = v }
        }
        guard let i0 = idx else { return nil }
        let dae = String(t[..<i0]).trimmingCharacters(in: .whitespaces)
        let dwi = String(t[t.index(i0, offsetBy: kanIreum.count)...])
        let z = ttuk(t)
        let ilgi = ["알려", "읽어", "들려", "말해", "확인", "뭐야", "뭐였", "뭐라", "뭐있"].contains { z.contains($0) }
        let jiugi = JIUGI.contains { z.contains($0) }
        let deohagi = ["추가", "넣어", "넣자", "적어", "기록", "저장", "등록", "달아", "붙여", "덧붙", "더해"].contains { z.contains($0) }
        let bakkugi = BAKKUGI.contains { z.contains($0) }
        // 「장소는 협회 사무실 내일 3시 회의 넣어 줘」 — 새 일정
        if deohagi && !bakkugi {
            let p = pulgi(t0, now)
            if kan != "naeyong" && (p.si != nil || p.haruJongil) { return nil }
        }
        var hal = ""
        if bakkugi { hal = "bakkugi" } else if jiugi && !deohagi { hal = "jiugi" } else if deohagi { hal = kan == "naeyong" ? "deohagi" : "bakkugi" } else if ilgi { hal = "ilgi" }
        if hal.isEmpty { return kan == "naeyong" && z.count <= 14 ? GochimPul(kan: kan, hal: "ilgi", dae: dae, gap: "") : nil }
        var nameun: [String] = []
        for w0 in dwi.split(separator: " ").map({ String($0).trimmingCharacters(in: .whitespaces) }).filter({ !$0.isEmpty }) {
            var w = w0
            if GAP_BBAEGI.contains(w) { continue }
            if nameun.isEmpty && ["에다가", "에다", "에는", "을", "를", "은", "는", "에", "도", "이", "가"].contains(w) { w = "" }
            if w.isEmpty { continue }
            let wz = ttuk(w)
            if GAP_JEONG.contains(wz) || (GAP_DONGSA.contains { wz.hasPrefix($0) } && wz.count <= 7) { continue }
            nameun.append(w)
        }
        var gap = nameun.joined(separator: " ").trimmingCharacters(in: .whitespaces)
        for k in ["이라고", "라고", "으로", "로"] where gap.count > k.count + 1 && gap.hasSuffix(k) { gap = String(gap.dropLast(k.count)); break }
        if gap.hasPrefix("을 ") || gap.hasPrefix("를 ") { gap = String(gap.dropFirst(2)) }
        if hal == "ilgi" || hal == "jiugi" { gap = "" }
        return GochimPul(kan: kan, hal: hal, dae: dae, gap: gap.trimmingCharacters(in: .whitespaces))
    }

    /// 「무슨 일정인가요」의 대답 — 「복지콜이야」 → 「복지콜」
    static func jemokDap(_ t0: String) -> String {
        var t = t0.trimmingCharacters(in: CharacterSet(charactersIn: " .?!"))
        for k in ["제목은 ", "제목은", "이름은 ", "일정은 "] where t.hasPrefix(k) { t = String(t.dropFirst(k.count)) }
        for k in ["이라고 해 줘", "이라고 해줘", "라고 해 줘", "라고 해줘", "이라고", "라고", "입니다", "이에요", "예요", "이야", "일정이요", "일정", "이요", "야", "요"]
            where t.count > k.count && t.hasSuffix(k) {
            t = String(t.dropLast(k.count)).trimmingCharacters(in: .whitespaces)
            break
        }
        return t.trimmingCharacters(in: .whitespaces)
    }

    private static func batchim(_ w: String) -> (Bool, Bool) {
        guard let ch = w.trimmingCharacters(in: .whitespaces).last else { return (false, false) }
        if ch.isNumber { return ("013678".contains(ch), "178".contains(ch)) }
        guard let v = ch.unicodeScalars.first?.value, v >= 0xAC00, v <= 0xD7A3 else { return (false, false) }
        let j = (v - 0xAC00) % 28
        return (j != 0, j == 8)
    }
    static func eun(_ w: String) -> String { batchim(w).0 ? "은" : "는" }
    static func eul(_ w: String) -> String { batchim(w).0 ? "을" : "를" }
    static func ro(_ w: String) -> String { let b = batchim(w); return b.0 && !b.1 ? "으로" : "로" }
}

// MARK: - 일정 엔진

struct IljeongHang: Identifiable, Hashable {
    let id: String
    let ev: EKEvent
    let jemok: String
    let sijak: Date
    let kkeut: Date
    let haruJongil: Bool
    let jangso: String
    let gilnunGeot: Bool
    let banbok: Bool
    var naeyong: String = ""   // 2.117.0 적어 둔 내용(캘린더 메모)
    static func == (a: IljeongHang, b: IljeongHang) -> Bool { a.id == b.id }
    func hash(into h: inout Hasher) { h.combine(id) }
}

struct SaeIljeong {
    var jemok: String
    var jangso: String
    var sijak: Date
    var kkeut: Date
    var haruJongil: Bool
    var banbok: String
    var yoil: Int
}

enum IljeongMureum {
    case neoki(SaeIljeong)
    case sigak(IljeongPul)
    case goreugi([IljeongHang], String, IljeongPul, Bool)
    case bakkugi(IljeongHang, Date, Date)
    case jiugi(IljeongHang, Bool)
    case annae(String)
    // 2.117.0 (261027-I71, 이사장님 승인 2026-10-27 「순서대로 모두 고쳐」)
    case jemok(IljeongPul)                                  // 무슨 일정인가요
    case goreugiG([IljeongHang], IljeongMal.GochimPul)      // 칸 고칠 일정 고르기
    case gap(IljeongHang, String, String)                   // 새 제목·장소·내용을 여쭘(칸, 할 일)
    case gochim(IljeongHang, String, String, String)        // 고칠까요(칸, 할 일, 값)
    case mueot(IljeongHang)                                 // 무엇을 바꿀까요
}

struct IljeongDdeona: Codable {
    let ki: String
    let jemok: String
    let jangso: String
    let sijak: Date
    let ddeona: Date
    let bun: Int
}

struct JiunIljeong: Codable {
    let jemok: String
    let jangso: String
    let sijak: Date
    let kkeut: Date
    let jongil: Bool
    let bindo: Int        // 되풀이(모두 지웠을 때) — EKRecurrenceFrequency, 없으면 -1
    let yoilDeul: [Int]
    let ttae: Date
}

final class Iljeong: ObservableObject {
    static let shared = Iljeong()
    static let PYOSI_URL = "gilnun://iljeong"
    let store = EKEventStore()

    @Published var gaengsin = 0
    @Published private(set) var ddeonaMok: [IljeongDdeona] = []
    private(set) var mureum: IljeongMureum?
    private var mureumTtae = Date.distantPast
    private(set) var mureumBeon = 0
    private(set) var beuripingJung = false
    private var beuripingBeon = 0
    private var sigye: Timer?
    private var dolgo = false
    private var gyesanTtae = Date.distantPast
    private var gyesanJari: (Double, Double)?
    private var gyesanJung = false
    private var dasiGyesan = false
    private let d = UserDefaults.standard

    // MARK: 설정

    var achimKyeojim: Bool {
        get { d.bool(forKey: "gn.ij.achim") }
        set { objectWillChange.send(); d.set(newValue, forKey: "gn.ij.achim"); if newValue { allimHeorak() }; jaeAllim() }
    }
    var achimSi: Int {
        get { (d.object(forKey: "gn.ij.achimSi") as? Int) ?? 7 }
        set { objectWillChange.send(); d.set(newValue, forKey: "gn.ij.achimSi"); jaeAllim() }
    }
    var achimBun: Int {
        get { (d.object(forKey: "gn.ij.achimBun") as? Int) ?? 0 }
        set { objectWillChange.send(); d.set(newValue, forKey: "gn.ij.achimBun"); jaeAllim() }
    }
    /// maeil 날마다, pyeongil 평일, jumal 주말
    var achimYoil: String {
        get { d.string(forKey: "gn.ij.achimYoil") ?? "maeil" }
        set { objectWillChange.send(); d.set(newValue, forKey: "gn.ij.achimYoil"); jaeAllim() }
    }
    var ddeonaKyeojim: Bool {
        get { (d.object(forKey: "gn.ij.ddeona") as? Bool) ?? true }
        set { objectWillChange.send(); d.set(newValue, forKey: "gn.ij.ddeona"); if newValue { allimHeorak() }; ddeonaGyesan(kkok: true) }
    }
    var yeoyu: Int {
        get { (d.object(forKey: "gn.ij.yeoyu") as? Int) ?? 10 }
        set { objectWillChange.send(); d.set(newValue, forKey: "gn.ij.yeoyu"); ddeonaGyesan(kkok: true) }
    }
    var achimYoilMal: String { achimYoil == "pyeongil" ? "평일마다" : (achimYoil == "jumal" ? "주말마다" : "날마다") }
    var achimMal: String { achimKyeojim ? "\(achimYoilMal) \(JeonggiHochul.sigakMal(achimSi, achimBun))" : "꺼 둠" }

    private func allimHeorak() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in
            DispatchQueue.main.async { self.jaeAllim() }
        }
    }

    // MARK: 시작

    func sijak() {
        guard !dolgo else { return }
        dolgo = true
        if let x = d.data(forKey: "gn.ij.ddeonaMok"), let v = try? JSONDecoder().decode([IljeongDdeona].self, from: x) { ddeonaMok = v }
        sigye = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { [weak self] _ in self?.salpigi() }
        if let s = sigye { RunLoop.main.add(s, forMode: .common) }
        NotificationCenter.default.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { [weak self] _ in
            self?.salpigi()
        }
        NotificationCenter.default.addObserver(forName: .EKEventStoreChanged, object: store, queue: .main) { [weak self] _ in
            self?.gaengsin += 1
            self?.ddeonaGyesan(kkok: true)
        }
        jaeAllim()
        // 자리가 잡힌 뒤 떠날 시각을 셈
        DispatchQueue.main.asyncAfter(deadline: .now() + 20) { self.ddeonaGyesan(kkok: true) }
    }

    // MARK: 허락

    var heorak: Bool {
        let s = EKEventStore.authorizationStatus(for: .event)
        if #available(iOS 17.0, *) { return s == .fullAccess }
        return s == .authorized
    }

    /// 이미 거절하셨거나 쓰기만 허락하셔서 아이폰 설정에서 바꾸셔야 함
    var seoljeongPiryo: Bool {
        let s = EKEventStore.authorizationStatus(for: .event)
        if s == .denied || s == .restricted { return true }
        if #available(iOS 17.0, *) { return s == .writeOnly }
        return false
    }

    func heorakCheong(_ kkeut: @escaping (Bool) -> Void) {
        Girok.shared.namgi("iljeong_heorak_cheong", [:])
        let f: (Bool, Error?) -> Void = { ok, _ in
            DispatchQueue.main.async {
                self.store.reset()
                self.gaengsin += 1
                if ok { self.ddeonaGyesan(kkok: true) }
                Girok.shared.namgi("iljeong_heorak", ["ok": ok])
                kkeut(ok)
            }
        }
        if #available(iOS 17.0, *) {
            store.requestFullAccessToEvents(completion: f)
        } else {
            store.requestAccess(to: .event, completion: f)
        }
    }

    func seoljeongYeolgi() {
        if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) }
    }

    // MARK: 읽기

    private var gilnunDalId: String { d.string(forKey: "gn.ij.dal") ?? "" }

    /// a 부터 b 앞까지의 일정(되풀이는 회마다) — 날 차례, 그날은 하루 종일 먼저
    func ilgi(_ a: Date, _ b: Date) -> [IljeongHang] {
        guard heorak else { return [] }
        let cal = Calendar.current
        let gid = gilnunDalId
        let p = store.predicateForEvents(withStart: a, end: b, calendars: nil)
        let l: [IljeongHang] = store.events(matching: p).compactMap { e -> IljeongHang? in
            guard let s = e.startDate, let k = e.endDate else { return nil }
            if e.status == .canceled { return nil }
            let jm = (e.title ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            let jang = ((e.location ?? "").components(separatedBy: "\n").first ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            let gn = (!gid.isEmpty && e.calendar?.calendarIdentifier == gid) || e.url?.absoluteString == Iljeong.PYOSI_URL
            let id = (e.eventIdentifier ?? UUID().uuidString) + "|" + String(Int(s.timeIntervalSince1970))
            return IljeongHang(id: id, ev: e, jemok: jm.isEmpty ? "제목 없는 일정" : jm,
                               sijak: e.isAllDay ? cal.startOfDay(for: s) : s, kkeut: k, haruJongil: e.isAllDay,
                               jangso: jang, gilnunGeot: gn, banbok: e.hasRecurrenceRules,
                               naeyong: (e.notes ?? "").trimmingCharacters(in: .whitespacesAndNewlines))
        }
        var bon = Set<String>()
        return l.filter { bon.insert($0.id).inserted }.sorted { x, y in
            let dx = cal.startOfDay(for: x.sijak)
            let dy = cal.startOfDay(for: y.sijak)
            if dx != dy { return dx < dy }
            if x.haruJongil != y.haruJongil { return x.haruJongil }
            return x.sijak < y.sijak
        }
    }

    func nalIreum(_ t: Date) -> String {
        let cal = Calendar.current
        let ap = cal.isDateInToday(t) ? "오늘 " : (cal.isDateInTomorrow(t) ? "내일 " : (cal.isDate(t, inSameDayAs: IljeongMal.haru(Date(), 2)) ? "모레 " : ""))
        return ap + JeonggiHochul.nalMal(t)
    }

    func sigakMal(_ t: Date) -> String {
        let c = Calendar.current
        return JeonggiHochul.sigakMal(c.component(.hour, from: t), c.component(.minute, from: t))
    }

    func julMal(_ h: IljeongHang, nal: Bool) -> String {
        var t = ""
        if nal { t += nalIreum(h.sijak) + " " }
        t += h.haruJongil ? "하루 종일" : sigakMal(h.sijak)
        t += ", " + h.jemok
        if !h.jangso.isEmpty { t += ", 장소 " + h.jangso }
        return t
    }

    func beomwiMal(_ a: Date, _ b: Date, _ ireum: String) -> String {
        guard heorak else { return "일정을 읽으려면 캘린더 허락이 필요합니다. 설정 탭의 일정에서 캘린더 허락하기를 눌러 주십시오." }
        let l = ilgi(a, b)
        let yeoreonal = b.timeIntervalSince(a) > 86400 + 3600
        if l.isEmpty { return "\(ireum)\(IljeongMal.eun(ireum)) 잡힌 일정이 없습니다." }
        let bo = Array(l.prefix(15))
        // 2.117.0 읽어 드린 목록에 첫째·둘째 번호를 붙이고 기억함 — 「둘째 일정 제목 바꿔 줘」
        majimakMok = bo
        mokTtae = Date()
        if bo.count == 1 { majimakNeoki(bo[0]) }
        var m = "\(ireum) 일정은 \(l.count)개입니다. " +
            (bo.count == 1 ? julMal(bo[0], nal: yeoreonal) : bo.enumerated().map { "\(IljeongMal.SEOSU[$0.offset]), \(julMal($0.element, nal: yeoreonal))" }.joined(separator: ". ")) + "."
        if l.count > bo.count { m += " 그 밖에 \(l.count - bo.count)개가 더 있습니다. 일정 화면의 다가오는 일정 목록에서 보실 수 있습니다." }
        return m
    }

    func oneulMal() -> String { let o = IljeongMal.haru(Date(), 0); return beomwiMal(o, IljeongMal.haru(Date(), 1), "오늘") }
    func naeilMal() -> String { beomwiMal(IljeongMal.haru(Date(), 1), IljeongMal.haru(Date(), 2), "내일") }
    func ibeonJuMal() -> String {
        let now = Date()
        let w = Calendar.current.component(.weekday, from: now)
        let nameun = (1 - w + 7) % 7 + 1   // 일요일까지
        return beomwiMal(IljeongMal.haru(now, 0), IljeongMal.haru(now, nameun), "이번 주")
    }

    // MARK: 달력 고르기

    /// 길눈이 넣을 달력 — 「길눈」 달력(없으면 만듦), 만들 수 없으면 기본 달력
    private func gilnunDalryeok() -> EKCalendar? {
        if let id = d.string(forKey: "gn.ij.dal"), let c = store.calendar(withIdentifier: id), c.allowsContentModifications { return c }
        if let c = store.calendars(for: .event).first(where: { $0.title == "길눈" && $0.allowsContentModifications }) {
            d.set(c.calendarIdentifier, forKey: "gn.ij.dal")
            return c
        }
        let sae = EKCalendar(for: .event, eventStore: store)
        sae.title = "길눈"
        sae.cgColor = UIColor(red: 0.03, green: 0.12, blue: 0.27, alpha: 1).cgColor
        var hubo: [EKSource] = []
        if let s = store.defaultCalendarForNewEvents?.source { hubo.append(s) }
        hubo += store.sources.filter { $0.sourceType == .calDAV }
        hubo += store.sources.filter { $0.sourceType == .local }
        for s in hubo {
            sae.source = s
            do {
                try store.saveCalendar(sae, commit: true)
                d.set(sae.calendarIdentifier, forKey: "gn.ij.dal")
                Girok.shared.namgi("iljeong_dal_mandeum", [:])
                return sae
            } catch { continue }
        }
        Girok.shared.namgi("iljeong_dal_gibon", [:])
        return store.defaultCalendarForNewEvents
    }

    // MARK: 넣기·고치기·지우기

    static func gyuchik(_ s: SaeIljeong) -> EKRecurrenceRule? {
        func ju(_ w: [Int]) -> EKRecurrenceRule {
            let ds = w.compactMap { EKWeekday(rawValue: $0) }.map { EKRecurrenceDayOfWeek($0) }
            return EKRecurrenceRule(recurrenceWith: .weekly, interval: 1, daysOfTheWeek: ds, daysOfTheMonth: nil, monthsOfTheYear: nil,
                                    weeksOfTheYear: nil, daysOfTheYear: nil, setPositions: nil, end: nil)
        }
        switch s.banbok {
        case "maeil": return EKRecurrenceRule(recurrenceWith: .daily, interval: 1, end: nil)
        case "pyeongil": return ju([2, 3, 4, 5, 6])
        case "jumal": return ju([1, 7])
        case "maeju": return ju([(s.yoil >= 1 && s.yoil <= 7) ? s.yoil : Calendar.current.component(.weekday, from: s.sijak)])
        default: return nil
        }
    }

    /// 되풀이의 첫 회가 맞는 요일에 오게 시작을 앞으로 옮김
    static func banbokMatchum(_ s: inout SaeIljeong) {
        func majneun(_ t: Date) -> Bool {
            let w = Calendar.current.component(.weekday, from: t)
            switch s.banbok {
            case "pyeongil": return w != 1 && w != 7
            case "jumal": return w == 1 || w == 7
            case "maeju": return s.yoil < 1 || s.yoil > 7 || w == s.yoil
            default: return true
            }
        }
        var n = 0
        while !majneun(s.sijak) && n < 7 {
            s.sijak = s.sijak.addingTimeInterval(86400)
            s.kkeut = s.kkeut.addingTimeInterval(86400)
            n += 1
        }
    }

    @discardableResult
    func neoki(_ s: SaeIljeong, gyuchik: EKRecurrenceRule? = nil) -> Bool {
        guard heorak, let c = gilnunDalryeok() else { return false }
        let e = EKEvent(eventStore: store)
        e.calendar = c
        e.title = s.jemok
        if !s.jangso.isEmpty { e.location = s.jangso }
        e.url = URL(string: Iljeong.PYOSI_URL)
        e.isAllDay = s.haruJongil
        e.startDate = s.sijak
        e.endDate = s.haruJongil ? max(s.sijak, s.kkeut.addingTimeInterval(-1)) : s.kkeut
        if !s.haruJongil {
            e.addAlarm(EKAlarm(relativeOffset: -3600))
            e.addAlarm(EKAlarm(relativeOffset: -1800))
        }
        if let r = gyuchik ?? Iljeong.gyuchik(s) { e.addRecurrenceRule(r) }
        do {
            try store.save(e, span: .futureEvents, commit: true)
        } catch {
            Girok.shared.namgi("iljeong_oryu", ["dan": "neoki", "e": String(error.localizedDescription.prefix(80))])
            return false
        }
        Girok.shared.namgi("iljeong_neoki", ["banbok": s.banbok, "jongil": s.haruJongil, "jangso": !s.jangso.isEmpty])
        gaengsin += 1
        ddeonaGyesan(kkok: true)
        return true
    }

    /// 시각·날 바꾸기 — 되풀이 일정은 이 회만
    func bakkugi(_ h: IljeongHang, _ s: Date, _ k: Date) -> Bool {
        guard heorak, h.gilnunGeot else { return false }
        let e = h.ev
        e.startDate = s
        e.endDate = h.haruJongil ? s.addingTimeInterval(max(86400, k.timeIntervalSince(s)) - 1) : k
        do {
            try store.save(e, span: .thisEvent, commit: true)
        } catch {
            Girok.shared.namgi("iljeong_oryu", ["dan": "bakkugi", "e": String(error.localizedDescription.prefix(80))])
            return false
        }
        Girok.shared.namgi("iljeong_bakkugi", ["banbok": h.banbok])
        gaengsin += 1
        ddeonaGyesan(kkok: true)
        return true
    }

    /// 지우기 — 되풀이 일정은 modu 가 아니면 이 회만, modu 면 이 회부터 앞으로 모두. 지운 것은 하루 동안 되살릴 수 있게 적어 둠
    func jiugi(_ h: IljeongHang, modu: Bool) -> Bool {
        guard heorak, h.gilnunGeot else { return false }
        var bindo = -1
        var yoilDeul: [Int] = []
        if h.banbok && modu, let r = h.ev.recurrenceRules?.first {
            bindo = r.frequency.rawValue
            yoilDeul = (r.daysOfTheWeek ?? []).map { $0.dayOfTheWeek.rawValue }
        }
        do {
            try store.remove(h.ev, span: (h.banbok && modu) ? .futureEvents : .thisEvent, commit: true)
        } catch {
            Girok.shared.namgi("iljeong_oryu", ["dan": "jiugi", "e": String(error.localizedDescription.prefix(80))])
            return false
        }
        let j = JiunIljeong(jemok: h.jemok, jangso: h.jangso, sijak: h.sijak, kkeut: h.kkeut, jongil: h.haruJongil, bindo: bindo, yoilDeul: yoilDeul, ttae: Date())
        if let x = try? JSONEncoder().encode(j) { d.set(x, forKey: "gn.ij.jiun") }
        Girok.shared.namgi("iljeong_jiugi", ["modu": modu])
        gaengsin += 1
        ddeonaGyesan(kkok: true)
        return true
    }

    var jiunGeot: JiunIljeong? {
        guard let x = d.data(forKey: "gn.ij.jiun"), let j = try? JSONDecoder().decode(JiunIljeong.self, from: x) else { return nil }
        return Date().timeIntervalSince(j.ttae) > 86400 ? nil : j
    }

    var jiunMal: String {
        guard let j = jiunGeot else { return "" }
        var t = nalIreum(j.sijak) + " " + (j.jongil ? "하루 종일" : sigakMal(j.sijak)) + ", " + j.jemok
        if !j.jangso.isEmpty { t += ", 장소 " + j.jangso }
        if j.bindo >= 0 { t += ", 되풀이 일정" }
        return t
    }

    func doesalligi() -> String {
        guard let j = jiunGeot else { return "되살릴 일정이 없습니다. 지운 지 하루 안의 일정만 되살릴 수 있습니다." }
        var r: EKRecurrenceRule? = nil
        if j.bindo >= 0, let f = EKRecurrenceFrequency(rawValue: j.bindo) {
            let ds = j.yoilDeul.compactMap { EKWeekday(rawValue: $0) }.map { EKRecurrenceDayOfWeek($0) }
            r = EKRecurrenceRule(recurrenceWith: f, interval: 1, daysOfTheWeek: ds.isEmpty ? nil : ds, daysOfTheMonth: nil, monthsOfTheYear: nil,
                                 weeksOfTheYear: nil, daysOfTheYear: nil, setPositions: nil, end: nil)
        }
        let m = jiunMal
        let s = SaeIljeong(jemok: j.jemok, jangso: j.jangso, sijak: j.sijak, kkeut: j.kkeut, haruJongil: j.jongil, banbok: "", yoil: 0)
        guard neoki(s, gyuchik: r) else { return "캘린더에 다시 넣지 못했습니다." }
        d.removeObject(forKey: "gn.ij.jiun")
        Girok.shared.namgi("iljeong_doesalligi", [:])
        return "되살렸습니다. \(m)."
    }

    // MARK: 떠날 시각

    /// 곧은 거리로 어림한 걸리는 시간(분) — 1.5킬로미터 안은 걸음(분당 50미터, 굽는 길 3할 더함), 그 넘으면 탈것(기다림·걷기 15분 + 시속 20킬로미터)
    static func eorimBun(_ m: Double) -> Int {
        m <= 1500 ? max(5, Int((m * 1.3 / 50).rounded(.up))) : Int((15 + m * 1.4 / 333).rounded(.up))
    }

    func jangsoChatgi(_ mal: String) async -> (Double, Double)? {
        var g = (d.dictionary(forKey: "gn.ij.giok") as? [String: [Double]]) ?? [:]
        if let a = g[mal], a.count == 2 { return (a[0], a[1]) }
        guard let l = await Chatgi.jangso(mal), let j = l.first else { return nil }
        g[mal] = [j.lat, j.lon]
        if g.count > 60, let k = g.keys.first { g.removeValue(forKey: k) }
        d.set(g, forKey: "gn.ij.giok")
        return (j.lat, j.lon)
    }

    /// 24시간 안의 장소 있는 일정마다 떠날 시각을 셈 — 10분 안이고 500미터 넘게 옮기지 않았으면 다시 셈하지 않음(kkok 이면 늘)
    func ddeonaGyesan(kkok: Bool) {
        guard ddeonaKyeojim, heorak else {
            if !ddeonaMok.isEmpty { ddeonaMok = []; jeojangDdeona(); jaeAllim() }
            return
        }
        guard let w = WichiEngine.shared.jigeum else { return }
        let now = Date()
        if !kkok, now.timeIntervalSince(gyesanTtae) < 600, let j = gyesanJari, WichiEngine.geori(j.0, j.1, w.lat, w.lon) < 500 { return }
        if gyesanJung { if kkok { dasiGyesan = true }; return }
        gyesanJung = true
        gyesanTtae = now
        gyesanJari = (w.lat, w.lon)
        let l = Array(ilgi(now, now.addingTimeInterval(86400)).filter { !$0.haruJongil && !$0.jangso.isEmpty && $0.sijak > now }.prefix(8))
        let yo = yeoyu
        Task {
            var out: [IljeongDdeona] = []
            for h in l {
                if let jr = await self.jangsoChatgi(h.jangso) {
                    let bun = Iljeong.eorimBun(WichiEngine.geori(w.lat, w.lon, jr.0, jr.1))
                    out.append(IljeongDdeona(ki: h.id, jemok: h.jemok, jangso: h.jangso, sijak: h.sijak,
                                             ddeona: h.sijak.addingTimeInterval(-Double((bun + yo) * 60)), bun: bun))
                } else {
                    Girok.shared.namgi("iljeong_jangso_mot", [:])
                }
            }
            await MainActor.run {
                self.gyesanJung = false
                self.ddeonaMok = out
                self.jeojangDdeona()
                self.jaeAllim()
                if self.dasiGyesan { self.dasiGyesan = false; self.ddeonaGyesan(kkok: true) }
            }
        }
    }

    private func jeojangDdeona() {
        if let x = try? JSONEncoder().encode(ddeonaMok) { d.set(x, forKey: "gn.ij.ddeonaMok") }
    }

    func cheotDdeonaMal() -> String {
        let now = Date()
        guard let x = ddeonaMok.filter({ $0.ddeona > now && Calendar.current.isDateInToday($0.sijak) }).min(by: { $0.sijak < $1.sijak }) else { return "" }
        return "\(x.jemok)에 가시려면 어림잡아 \(sigakMal(x.ddeona))에 떠나시면 됩니다."
    }

    // MARK: 한 번만 하기

    private func han(_ k: String) -> Bool { (d.stringArray(forKey: "gn.ij.han") ?? []).contains(k) }
    private func hanNeoki(_ k: String) {
        var l = d.stringArray(forKey: "gn.ij.han") ?? []
        l.append(k)
        if l.count > 40 { l.removeFirst(l.count - 40) }
        d.set(l, forKey: "gn.ij.han")
    }

    private func oneulAchim(_ now: Date) -> Date? {
        let w = Calendar.current.component(.weekday, from: now)
        if achimYoil == "pyeongil" && (w == 1 || w == 7) { return nil }
        if achimYoil == "jumal" && w != 1 && w != 7 { return nil }
        return Calendar.current.date(bySettingHour: achimSi, minute: achimBun, second: 0, of: now)
    }

    // MARK: 지킴이 — 15초마다(길눈이 켜져 있을 때)

    func salpigi() {
        let now = Date()
        if achimKyeojim, let t = oneulAchim(now), now >= t, now.timeIntervalSince(t) < 120 {
            let k = "achim|" + JeonggiHochul.ymd(now)
            if !han(k) {
                hanNeoki(k)
                beuriping()
                Girok.shared.namgi("iljeong_achim", ["jadong": true])
            }
        }
        if ddeonaKyeojim {
            for x in ddeonaMok {
                for (dan, t) in [("a", x.ddeona.addingTimeInterval(-900)), ("b", x.ddeona)] {
                    let k = x.ki + "|" + dan
                    if han(k) || now < t || now.timeIntervalSince(t) > 300 { continue }
                    hanNeoki(k)
                    ddeonaAllim(x, jigeum: dan == "b")
                }
            }
        }
        ddeonaGyesan(kkok: false)
    }

    private func ddeonaAllim(_ x: IljeongDdeona, jigeum: Bool) {
        let dochak = "\(sigakMal(x.sijak)) \(x.jemok)"
        if !jigeum {
            SoriEngine.shared.mal("15분 뒤에 떠나셔야 \(dochak) 시각에 \(x.jangso)에 닿습니다. 지금 자리에서 어림잡은 시각입니다.", .annae)
            Girok.shared.namgi("iljeong_ddeona", ["dan": "a"])
        } else {
            Girok.shared.namgi("iljeong_ddeona", ["dan": "b"])
            annaeMutgi(x.jangso, "지금 떠나실 시각입니다. \(dochak), \(x.jangso)\(IljeongMal.ro(x.jangso)) 길 안내를 시작할까요?")
        }
    }

    /// 「○○으로 길 안내를 시작할까요?」 — 여쭙고 마이크를 엶
    func annaeMutgi(_ jangso: String, _ q0: String? = nil) {
        let q = q0 ?? "\(jangso)\(IljeongMal.ro(jangso)) 길 안내를 시작할까요?"
        mutgi(.annae(jangso))
        MalHagi.shared.iljeongMutgi(q)
    }

    // MARK: 알림 — 길눈이 꺼져 있거나 잠들어 있을 때

    func jaeAllim() {
        let c = UNUserNotificationCenter.current()
        c.getPendingNotificationRequests { rs in
            let yet = rs.map { $0.identifier }.filter { $0.hasPrefix("iljeong-") }
            c.removePendingNotificationRequests(withIdentifiers: yet)
            DispatchQueue.main.async { self.allimGeolgi() }
        }
    }

    private func allimGeolgi() {
        let c = UNUserNotificationCenter.current()
        if achimKyeojim {
            let ct = UNMutableNotificationContent()
            ct.title = "길눈 아침 브리핑"
            ct.body = "누르시면 오늘 날짜와 날씨, 오늘 일정을 읽어 드립니다."
            ct.sound = .default
            ct.userInfo = ["iljeong": "achim"]
            var nalDeul: [DateComponents] = [DateComponents()]
            if achimYoil == "pyeongil" { nalDeul = (2...6).map { (w: Int) -> DateComponents in var x = DateComponents(); x.weekday = w; return x } }
            if achimYoil == "jumal" { nalDeul = [1, 7].map { (w: Int) -> DateComponents in var x = DateComponents(); x.weekday = w; return x } }
            for (i, dc0) in nalDeul.enumerated() {
                var dc = dc0
                dc.hour = achimSi
                dc.minute = achimBun
                c.add(UNNotificationRequest(identifier: "iljeong-achim-\(i)", content: ct,
                                            trigger: UNCalendarNotificationTrigger(dateMatching: dc, repeats: true)), withCompletionHandler: nil)
            }
        }
        if ddeonaKyeojim {
            let now = Date()
            for (n, x) in ddeonaMok.enumerated() {
                for (dan, t) in [("a", x.ddeona.addingTimeInterval(-900)), ("b", x.ddeona)] where t > now.addingTimeInterval(5) && !han(x.ki + "|" + dan) {
                    let ct = UNMutableNotificationContent()
                    if dan == "a" {
                        ct.title = "15분 뒤 떠나실 시각"
                        ct.body = "\(sigakMal(x.sijak)) \(x.jemok), \(x.jangso). 어림잡은 시각입니다."
                    } else {
                        ct.title = "지금 떠나실 시각입니다"
                        ct.body = "\(sigakMal(x.sijak)) \(x.jemok), \(x.jangso). 누르시면 길 안내를 여쭙니다."
                    }
                    ct.sound = .default
                    ct.userInfo = ["iljeong": "ddeona", "dan": dan, "ki": x.ki, "jangso": x.jangso]
                    let dc = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: t)
                    c.add(UNNotificationRequest(identifier: "iljeong-ddeona-\(n)-\(dan)", content: ct,
                                                trigger: UNCalendarNotificationTrigger(dateMatching: dc, repeats: false)), withCompletionHandler: nil)
                }
            }
        }
    }

    /// 앱이 켜져 있을 때 온 알림 — 띄우지 않고 바로 함(JeonggiHochul 의 알림 대리인이 넘겨 줌)
    func allimOm(_ n: UNNotification) {
        let u = n.request.content.userInfo
        guard let jong = u["iljeong"] as? String else { return }
        if jong == "achim" {
            let k = "achim|" + JeonggiHochul.ymd(Date())
            if !han(k) { hanNeoki(k); beuriping() }
        } else if let ki = u["ki"] as? String, let dan = u["dan"] as? String, let x = ddeonaMok.first(where: { $0.ki == ki }) {
            let k = ki + "|" + dan
            if !han(k) { hanNeoki(k); ddeonaAllim(x, jigeum: dan == "b") }
        }
    }

    /// 알림을 누르셨을 때
    func allimNureum(_ n: UNNotification) {
        let u = n.request.content.userInfo
        guard let jong = u["iljeong"] as? String else { return }
        if jong == "achim" {
            hanNeoki("achim|" + JeonggiHochul.ymd(Date()))
            beuriping()
            Girok.shared.namgi("iljeong_achim", ["jadong": false])
        } else if let jangso = u["jangso"] as? String, let dan = u["dan"] as? String {
            if let ki = u["ki"] as? String { hanNeoki(ki + "|" + dan) }
            if dan == "b" {
                annaeMutgi(jangso, "지금 떠나실 시각입니다. \(jangso)\(IljeongMal.ro(jangso)) 길 안내를 시작할까요?")
            } else if let ki = u["ki"] as? String, let x = ddeonaMok.first(where: { $0.ki == ki }) {
                ddeonaAllim(x, jigeum: false)
            }
        }
    }

    // MARK: 아침 브리핑

    /// 날짜, 날씨, 오늘 일정, 첫 일정 떠날 시각 — 다 말한 뒤 그 무렵 듣기 예약이 있으면 라디오를 틂
    func beuriping() {
        guard !beuripingJung else { return }
        beuripingJung = true
        beuripingBeon += 1
        let beon = beuripingBeon
        let t0 = Date()
        DispatchQueue.main.asyncAfter(deadline: .now() + 180) { if self.beuripingBeon == beon { self.beuripingJung = false } }
        Task {
            let nal = await Nalssi.shared.mal()
            await MainActor.run {
                let now = Date()
                var m = "오늘은 \(JeonggiHochul.nalMal(now))입니다."
                if !nal.isEmpty { m += " 날씨는 \(nal)." }
                m += " " + self.oneulMal()
                let dd = self.cheotDdeonaMal()
                if !dd.isEmpty { m += " " + dd }
                SoriEngine.shared.mal(m, .annae)
                SoriEngine.shared.kkeutnamyeon {
                    if self.beuripingBeon == beon { self.beuripingJung = false }
                    self.radioIeum(t0)
                }
            }
        }
    }

    /// 브리핑 무렵(시작 2분 전부터 지금까지)의 듣기 예약을 이어서 틂
    private func radioIeum(_ t0: Date) {
        let y = DeutgiYeyak.shared
        let now = Date()
        let hubo = y.mok.filter { $0.kyeojim }.compactMap { h -> (DeutgiYeyakHang, Date)? in
            guard let t = DeutgiYeyak.oneulTtae(h, now), t >= t0.addingTimeInterval(-120), t <= now.addingTimeInterval(60) else { return nil }
            return (h, t)
        }.min(by: { $0.1 < $1.1 })
        if let x = hubo { y.teulgi(x.0, t: x.1, jadong: true) }
    }

    // MARK: 말로 하기

    private func mutgi(_ m: IljeongMureum) {
        mureum = m
        mureumTtae = Date()
        mureumBeon += 1
    }

    func mureumBiugi() { mureum = nil }

    var mureumItda: Bool { mureum != nil && Date().timeIntervalSince(mureumTtae) < 120 }

    private static let NAMEUN = ["라디오", "방송", "에프엠", "fm", "정기호출", "정기콜", "복지콜", "나비콜", "콜택시", "듣기예약", "끄기예약"]

    // 2.117.0 (261027-I71, 이사장님 승인 2026-10-27 「순서대로 모두 고쳐」) 바로 앞에 말한 일정과 읽어 드린 목록 — 「그 일정」, 「둘째 일정」
    private var majimakId = ""
    private var majimakSijak = Date.distantPast
    private var majimakTtae = Date.distantPast
    private var majimakMok: [IljeongHang] = []
    private var mokTtae = Date.distantPast
    /// 한 번만 드리는 도움말(길눈을 켤 때마다 한 번)
    private var hintHan = Set<String>()

    private func majimakNeoki(_ h: IljeongHang) {
        majimakId = h.ev.eventIdentifier ?? ""
        majimakSijak = h.sijak
        majimakTtae = Date()
    }

    /// 바로 앞에 말한 일정(30분 안) — 캘린더에서 다시 읽어 지금 모습으로
    private func majimak() -> IljeongHang? {
        guard !majimakId.isEmpty, Date().timeIntervalSince(majimakTtae) < 1800 else { return nil }
        let l = ilgi(IljeongMal.haru(majimakSijak, -1), IljeongMal.haru(majimakSijak, 2))
        return l.first(where: { $0.ev.eventIdentifier == majimakId && $0.sijak == majimakSijak }) ?? l.first(where: { $0.ev.eventIdentifier == majimakId })
    }

    /// 읽어 드린 목록의 몇째(30분 안)
    private func mokHang(_ b: Int) -> IljeongHang? {
        guard Date().timeIntervalSince(mokTtae) < 1800, b >= 1, b <= majimakMok.count else { return nil }
        let h = majimakMok[b - 1]
        let l = ilgi(IljeongMal.haru(h.sijak, -1), IljeongMal.haru(h.sijak, 2))
        return l.first(where: { $0.id == h.id }) ?? l.first(where: { $0.ev.eventIdentifier == h.ev.eventIdentifier })
    }

    private func hint(_ k: String, _ m: String) -> String { hintHan.insert(k).inserted ? " " + m : "" }

    /// 「네」 — 「네 넣어 줘」, 「응 바꿔」도(「네 시」는 아님)
    private func yeMal(_ alts: [String], _ z: String) -> Bool {
        if MalHagi.hwaginDap(alts) == .ye { return true }
        guard z.count <= 8, !z.contains("시"), ["네", "예", "응", "그래", "좋아", "맞아"].contains(where: { z.hasPrefix($0) }) else { return false }
        return (IljeongMal.NEOKI + IljeongMal.BAKKUGI + IljeongMal.JIUGI + ["해", "줘", "요"]).contains { z.contains($0) }
    }

    /// 묻던 것을 그만두는 말
    private func geumanMal(_ z: String) -> Bool {
        z.count <= 8 && ["그만", "됐어", "됐다", "안해", "안할래", "필요없어", "취소", "나중에"].contains { z.contains($0) }
    }

    /// 일정 말로 하기 도움
    func doumMal() -> String {
        "일정에서는 이렇게 말씀하실 수 있습니다. 오늘 일정 알려 줘. 내일 오후 2시 병원 넣어 줘. 첫째 일정 제목 복지콜로 바꿔 줘. " +
        "그 일정 내용 추가해 줘, 휠체어 탑승. 그 일정 내용 읽어 줘. 2시 일정 3시로 바꿔 줘. 둘째 일정 장소 국립극장으로 바꿔 줘. 둘째 일정 지워 줘. 방금 지운 일정 되살려 줘. " +
        "일정을 읽어 드릴 때 붙인 첫째, 둘째 번호로 고르실 수 있습니다."
    }

    /// 일정 말이면 하고 참 — MalHagi.cheori 가 듣기 예약보다 앞에서 부름
    func malCheori(_ alts: [String], _ z: String, _ t: String, _ dap: @escaping (String, Bool) -> Void) -> Bool {
        let now = Date()
        if mureum != nil && now.timeIntervalSince(mureumTtae) > 120 { mureum = nil }
        let iljeongMal = z.contains("일정") || z.contains("약속") || z.contains("스케줄") || z.contains("캘린더") || z.contains("달력")
        let neoki = IljeongMal.NEOKI.contains { z.contains($0) }
        let bakkugi = IljeongMal.BAKKUGI.contains { z.contains($0) }
        let jiugi = IljeongMal.JIUGI.contains { z.contains($0) }
        let ilgiMal = IljeongMal.ILGI.contains { z.contains($0) }
        let kanMal = ["제목", "장소", "내용", "메모"].contains { z.contains($0) }
        // 2.117.0 묻는 중이라도 새로 시키시면 새 말씀부터(10-27 「오늘 일정 알려줘」를 몇 시냐는 물음의 대답으로 받아 새로 넣기만 되풀이함)
        if let m = mureum {
            if geumanMal(z) { mureum = nil; dap("알겠습니다. 그만두었습니다.", false); return true }
            var mutneunJung = false
            var gapJung = false
            switch m { case .jemok, .sigak: mutneunJung = true; case .gap: gapJung = true; default: break }
            let saeMal = iljeongMal && (ilgiMal || neoki || bakkugi || jiugi || kanMal) && !gapJung && !(mutneunJung && neoki && !ilgiMal)
            if saeMal && !yeMal(alts, z) { mureum = nil }
            else if dapCheori(m, alts, z, t, dap) { return true }
        }
        let zl = z.lowercased()
        if z.contains("브리핑") { return beuripingMal(z, dap) }
        let p = IljeongMal.pulgi(t, now, Jeulgyeo.shared.mokrok.prefix(30).map { $0.ireum })
        let nalSi = p.nal != nil || p.si != nil || !p.banbok.isEmpty || p.haruJongil || p.jusu == 3
        let darun = IljeongMal.DARUN.contains { z.contains($0) }
        // 「오늘 오후 2시 복지콜」 — 움직임 말 없이 날·시각과 이름만 말씀하시면 일정으로 넣을지 여쭘
        let umjigimEopseum = !neoki && !bakkugi && !jiugi && !ilgiMal && !darun && !kanMal
        let malmanIljeong = umjigimEopseum && p.si != nil && (p.nal != nil || z.contains("오전") || z.contains("오후")) && !p.jemok.isEmpty && z.count <= 30
        if Iljeong.NAMEUN.contains(where: { zl.contains($0) }) && !iljeongMal && !malmanIljeong { return false }
        // 일정 도움말
        if iljeongMal && ["도움말", "뭐라고", "어떻게말", "어떻게해", "사용법", "쓰는법", "할수있"].contains(where: { z.contains($0) }) {
            dap(doumMal(), false)
            return true
        }
        if (z.contains("되살려") || z.contains("살려") || z.contains("복구") || z.contains("되돌려")) && (iljeongMal || z.contains("지운")) {
            guard heorakBoda(dap) else { return true }
            dap(doesalligi(), false)
            return true
        }
        // 2.117.0 제목·장소·내용 고치기 — 「2시 일정 제목 복지콜로 바꿔 줘」, 「그 일정 내용 추가해 줘」, 「첫째 일정 내용 읽어 줘」
        let mokB = IljeongMal.mokBeonho(t)
        let geu = IljeongMal.geuMal(t)
        if kanMal || z.contains("이름"), let g = IljeongMal.gochimPulgi(t, now) {
            let mokSal = mokB != nil && now.timeIntervalSince(mokTtae) < 1800
            let geuSal = (geu && now.timeIntervalSince(majimakTtae) < 1800) || (g.dae.isEmpty && now.timeIntervalSince(majimakTtae) < 180)
            if iljeongMal || mokSal || geuSal {
                guard heorakBoda(dap) else { return true }
                gochimSijak(g, dap)
                return true
            }
        }
        if neoki && !bakkugi && !jiugi && (iljeongMal || nalSi) {
            guard heorakBoda(dap) else { return true }
            neokiSijak(p, dap)
            return true
        }
        if bakkugi && !jiugi && (iljeongMal || nalSi || mokB != nil || geu) {
            if !heorak { if !iljeongMal { return false }; _ = heorakBoda(dap); return true }
            return chatgoHagi(p, t, "bakkugi", false, iljeongMal, dap)
        }
        if jiugi && (iljeongMal || nalSi || mokB != nil || geu) {
            if !heorak { if !iljeongMal { return false }; _ = heorakBoda(dap); return true }
            let modu = z.contains("모두") || z.contains("전부") || z.contains("되풀이") || z.contains("매주")
            return chatgoHagi(p, t, "jiugi", modu, iljeongMal, dap)
        }
        if malmanIljeong {
            guard heorakBoda(dap) else { return true }
            neokiSijak(p, dap, malman: true)
            return true
        }
        if z.contains("다음일정") || z.contains("다음약속") {
            guard heorakBoda(dap) else { return true }
            let oneul = Calendar.current.startOfDay(for: now)
            guard let h = ilgi(now, now.addingTimeInterval(30 * 86400)).first(where: { $0.haruJongil ? $0.sijak > oneul : $0.sijak > now }) else {
                dap("앞으로 30일 안에 잡힌 일정이 없습니다.", false)
                return true
            }
            majimakNeoki(h)
            var m = "다음 일정은 \(julMal(h, nal: true))입니다."
            if let dd = ddeonaMok.first(where: { $0.ki == h.id }) { m += " 어림잡아 \(sigakMal(dd.ddeona))에 떠나시면 됩니다." }
            dap(m + hint("ilgi1", "고치시려면 그 일정 제목 바꿔 줘, 그 일정 내용 추가해 줘처럼 말씀하시면 됩니다."), false)
            return true
        }
        // 「둘째 일정 알려 줘」 — 그 일정 자세히
        if iljeongMal, let b = mokB, now.timeIntervalSince(mokTtae) < 1800, !neoki, !bakkugi, !jiugi, let h = mokHang(b) {
            majimakNeoki(h)
            dap(jasehiMal(h), false)
            return true
        }
        let nalMuneun = p.nal != nil && !iljeongMal && (z.contains("뭐있") || z.contains("약속있") || z.contains("뭐해")) && z.count <= 16
        if (iljeongMal && !neoki && !bakkugi && !jiugi && (ilgiMal || z.count <= 12)) || nalMuneun {
            guard heorakBoda(dap) else { return true }
            let m = beomwiDeutgi(p, now)
            let yeoreo = majimakMok.count >= 2 && Date().timeIntervalSince(mokTtae) < 5
            dap(m + (yeoreo ? hint("ilgi", "고치시려면 첫째 일정 제목 바꿔 줘, 둘째 일정 내용 추가해 줘처럼 번호로 말씀하시면 됩니다.") : ""), false)
            Girok.shared.namgi("iljeong_deutgi", ["jusu": p.jusu, "nal": p.nal != nil])
            return true
        }
        // 2.117.0 일정 말인데 알아듣지 못하면 말없이 넘기지 않고 하실 수 있는 말을 알려 드림
        if iljeongMal && !darun {
            Girok.shared.namgi("iljeong_mot", ["gil": z.count])
            dap("일정 말씀을 다 알아듣지 못했습니다. " + doumMal(), false)
            return true
        }
        return false
    }

    private func beomwiDeutgi(_ p: IljeongPul, _ now: Date) -> String {
        let w = Calendar.current.component(.weekday, from: now)
        let woryoil = IljeongMal.haru(now, -((w + 5) % 7))
        if p.jusu == 1 { return ibeonJuMal() }
        if p.jusu == 2 {
            return beomwiMal(woryoil.addingTimeInterval(7 * 86400), IljeongMal.haru(woryoil, 14), "다음 주")
        }
        if p.jusu == 3 {
            let to = (w == 1 || w == 7) ? IljeongMal.haru(now, 0) : IljeongMal.haru(now, 7 - w)
            let kkeut = w == 1 ? IljeongMal.haru(now, 1) : IljeongMal.haru(to, 2)
            return beomwiMal(to, kkeut, "이번 주말")
        }
        if let n = p.nal { return beomwiMal(n, IljeongMal.haru(n, 1), nalIreum(n)) }
        return oneulMal()
    }

    /// 허락이 있으면 참. 없으면 여쭙고(처음이면 허락 창, 거절하셨으면 설정 안내) 거짓
    private func heorakBoda(_ dap: @escaping (String, Bool) -> Void) -> Bool {
        if heorak { return true }
        if seoljeongPiryo {
            dap("일정을 쓰려면 캘린더 전체 접근 허락이 필요합니다. 아이폰 설정의 길눈에서 캘린더를 전체 접근으로 바꿔 주십시오. 설정 탭의 일정에서 설정 열기를 누르셔도 됩니다.", false)
        } else {
            heorakCheong { _ in }
            dap("일정을 쓰려면 캘린더 허락이 필요합니다. 허락 창이 뜨면 전체 접근 허용을 눌러 주신 뒤 다시 말씀해 주십시오.", false)
        }
        return false
    }

    private func sigakNal(_ nal: Date, _ si: Int, _ bun: Int) -> Date {
        Calendar.current.date(bySettingHour: si, minute: bun, second: 0, of: nal) ?? nal
    }

    /// 넣기 — 모자란 것을 여쭙고, 다 되면 한 번 읽어 드리고 여쭘. malman 이면 「일정으로 넣을까요?」
    private func neokiSijak(_ p: IljeongPul, _ dap: @escaping (String, Bool) -> Void, malman: Bool = false) {
        let now = Date()
        var nal = p.nal
        if nal == nil && p.jusu == 3 {
            let w = Calendar.current.component(.weekday, from: now)
            nal = (w == 1 || w == 7) ? IljeongMal.haru(now, 0) : IljeongMal.haru(now, 7 - w)
        }
        if nal == nil, let si = p.si {
            nal = sigakNal(now, si, p.bun) > now ? IljeongMal.haru(now, 0) : IljeongMal.haru(now, 1)
        }
        if nal == nil && !p.banbok.isEmpty && p.banbok != "maeju" { nal = IljeongMal.haru(now, 0) }
        if !p.haruJongil && p.si == nil {
            if let n = nal { p.nal = n }
            mutgi(.sigak(p))
            dap(nal == nil ? "언제로 할까요? 내일 오후 2시처럼 말씀해 주십시오." : "\(nalIreum(nal!)) 몇 시로 할까요? 하루 종일이면 하루 종일이라고 말씀해 주십시오.", true)
            return
        }
        guard let n = nal else {
            mutgi(.sigak(p))
            dap("어느 날로 할까요? 내일, 금요일, 11월 3일처럼 말씀해 주십시오.", true)
            return
        }
        p.nal = n
        // 2.117.0 제목이 없으면 꼭 여쭘 — 이름 없는 「일정」은 넣지 않음(10-27 이름 없는 일정 두 개가 들어감)
        if p.jemok.trimmingCharacters(in: .whitespaces).isEmpty {
            mutgi(.jemok(p))
            dap("무슨 일정인가요? 병원, 회의, 복지콜처럼 제목을 말씀해 주십시오.", true)
            return
        }
        let jm = p.jemok
        var s: SaeIljeong
        if p.haruJongil {
            s = SaeIljeong(jemok: jm, jangso: p.jangso, sijak: n, kkeut: IljeongMal.haru(n, 1), haruJongil: true, banbok: p.banbok, yoil: p.yoil)
        } else {
            let st = sigakNal(n, p.si ?? 9, p.bun)
            var kk = st.addingTimeInterval(3600)
            if let ks = p.kkeutSi { let k2 = sigakNal(n, ks, p.kkeutBun); if k2 > st { kk = k2 } }
            s = SaeIljeong(jemok: jm, jangso: p.jangso, sijak: st, kkeut: kk, haruJongil: false, banbok: p.banbok, yoil: p.yoil)
        }
        Iljeong.banbokMatchum(&s)
        if !s.haruJongil && s.banbok.isEmpty && s.sijak < now {
            dap("\(nalIreum(s.sijak)) \(sigakMal(s.sijak))는 이미 지난 시각입니다. 날짜나 시각을 다시 말씀해 주십시오.", false)
            return
        }
        mutgi(.neoki(s))
        dap(hwaginMal(s) + (malman ? " 일정으로 넣을까요?" : " 넣을까요?"), true)
    }

    func banbokMal(_ s: SaeIljeong) -> String {
        switch s.banbok {
        case "maeil": return "날마다 "
        case "pyeongil": return "평일마다 "
        case "jumal": return "주말마다 "
        case "maeju": return "매주 " + JeonggiHochul.yoilIreum[Calendar.current.component(.weekday, from: s.sijak)] + "마다 "
        default: return ""
        }
    }

    func hwaginMal(_ s: SaeIljeong) -> String {
        var t = ""
        if !s.banbok.isEmpty { t += banbokMal(s) + nalIreum(s.sijak) + "부터, " } else { t += nalIreum(s.sijak) + " " }
        if s.haruJongil {
            t += "하루 종일"
        } else {
            t += sigakMal(s.sijak)
            if abs(s.kkeut.timeIntervalSince(s.sijak) - 3600) > 1 { t += "부터 " + sigakMal(s.kkeut) + "까지" }
        }
        t += ", " + s.jemok
        if !s.jangso.isEmpty { t += ", 장소 " + s.jangso }
        return t + "."
    }

    private func neokiHagi(_ s: SaeIljeong, _ dap: @escaping (String, Bool) -> Void) {
        guard neoki(s) else {
            dap("캘린더에 넣지 못했습니다. 아이폰 설정의 길눈에서 캘린더 허락이 전체 접근인지 확인해 주십시오.", false)
            return
        }
        if let h = ilgi(s.sijak.addingTimeInterval(-60), s.sijak.addingTimeInterval(60)).first(where: { $0.gilnunGeot && $0.jemok == s.jemok }) { majimakNeoki(h) }
        var m = "넣었습니다."
        if !s.haruJongil { m += " 한 시간 전과 30분 전에 아이폰 캘린더가 알려 드립니다." }
        if !s.jangso.isEmpty && ddeonaKyeojim { m += " 떠나실 시각도 어림해 미리 알려 드립니다." }
        m += hint("neoki", "적어 둘 내용이 있으시면 그 일정 내용 추가해 줘, 장소는 그 일정 장소 바꿔 줘라고 말씀하시면 됩니다.")
        dap(m, false)
        if !s.jangso.isEmpty && ddeonaKyeojim {
            allimHeorak()
            let jang = s.jangso
            Task {
                let jr = await self.jangsoChatgi(jang)
                if jr == nil {
                    await MainActor.run {
                        SoriEngine.shared.mal("\(jang)\(IljeongMal.eul(jang)) 길 찾기에서 찾지 못해 떠날 시각 알림은 드리지 못합니다. 일정 알림은 그대로 울립니다.", .jeongbo)
                    }
                }
            }
        }
    }

    /// 고치기·지우기 — 맞는 일정을 찾아 하나면 여쭙고, 여럿이면 번호를 여쭘. 못 찾으면 일정이라는 말이 있을 때만 대답
    private func chatgoHagi(_ p: IljeongPul, _ t: String, _ hal: String, _ modu: Bool, _ iljeongMal: Bool, _ dap: @escaping (String, Bool) -> Void) -> Bool {
        if let m = mokEopseum(t) { dap(m, false); return true }
        let hubo = chatgi(p, t)
        if hubo.isEmpty {
            if !iljeongMal { return false }
            dap("말씀하신 일정을 찾지 못했습니다. 일정 이름이나 날짜, 시각을 함께 말씀해 주십시오. 예를 들면 내일 회의 3시로 바꿔 줘, 금요일 점심 약속 지워 줘라고 하시면 됩니다." +
                (majimakMok.isEmpty ? " 먼저 오늘 일정 알려 줘라고 하시면 번호를 붙여 읽어 드립니다. 그 번호로 고르셔도 됩니다." : ""), false)
            return true
        }
        if hubo.count > 1 {
            let l = Array(hubo.prefix(5))
            mutgi(.goreugi(l, hal, p, modu))
            dap("맞는 일정이 \(hubo.count)개 있습니다. " + l.enumerated().map { "\($0.offset + 1)번, \(julMal($0.element, nal: true))" }.joined(separator: ". ") + ". 몇 번입니까?", true)
            return true
        }
        majimakNeoki(hubo[0])
        if hal == "bakkugi" { bakkugiHwagin(hubo[0], p, dap) } else { jiugiHwagin(hubo[0], modu, dap) }
        return true
    }

    /// 말에 맞는 일정 — 「둘째 일정」·「그 일정」이면 그것, 아니면 날이 있으면 그날, 없으면 앞으로 30일. 제목 낱말이 가장 많이 맞는 것들
    private func chatgi(_ p: IljeongPul, _ t: String) -> [IljeongHang] {
        if let b = IljeongMal.mokBeonho(t), let h = mokHang(b) { return [h] }
        let nmal = p.jemokMal.filter { !$0.isEmpty && !IljeongMal.DONGSA.contains($0) }
        if IljeongMal.geuMal(t) || (p.nal == nil && p.si == nil && nmal.isEmpty), let h = majimak() { return [h] }
        let now = Date()
        let l: [IljeongHang]
        if let n = p.nal { l = ilgi(n, IljeongMal.haru(n, 1)) } else { l = ilgi(now.addingTimeInterval(-7200), now.addingTimeInterval(30 * 86400)) }
        let cal = Calendar.current
        let siMat = l.filter { h in
            guard let si = p.si else { return true }
            if h.haruJongil { return false }
            return cal.component(.hour, from: h.sijak) == si && (p.bun == 0 || cal.component(.minute, from: h.sijak) == p.bun)
        }
        if nmal.isEmpty { return (p.nal != nil || p.si != nil) ? siMat : [] }
        let jeom = siMat.map { h -> (IljeongHang, Int) in
            let bon = IljeongMal.ttuk(h.jemok + h.jangso)
            return (h, nmal.filter { bon.contains($0) }.count)
        }.filter { $0.1 > 0 }
        guard let choego = jeom.map({ $0.1 }).max() else { return [] }
        return jeom.filter { $0.1 == choego }.map { $0.0 }
    }

    private func bakkugiHwagin(_ h: IljeongHang, _ p: IljeongPul, _ dap: @escaping (String, Bool) -> Void) {
        guard h.gilnunGeot else {
            dap("\(julMal(h, nal: true))\(IljeongMal.eun(julMal(h, nal: true))) 다른 곳에서 넣은 일정이라 길눈에서는 고치지 않습니다. 아이폰 캘린더 앱에서 고쳐 주십시오.", false)
            return
        }
        if p.nalRo == nil && p.sigakRo == nil {
            // 2.117.0 무엇을 바꿀지 여쭘
            mutgi(.mueot(h))
            dap("\(julMal(h, nal: true)). 무엇을 바꿀까요? 시각, 날짜, 제목, 장소, 내용 가운데 말씀해 주십시오. 3시로, 금요일로처럼 바로 말씀하셔도 됩니다.", true)
            return
        }
        let gil = h.kkeut.timeIntervalSince(h.sijak)
        let geunal = p.nalRo ?? Calendar.current.startOfDay(for: h.sijak)
        let cal = Calendar.current
        let st: Date
        if h.haruJongil {
            st = geunal
        } else {
            var si = p.sigakRo?.0 ?? cal.component(.hour, from: h.sijak)
            // 2.117.0 오전·오후를 말하지 않으시면 본래 일정이 오후일 때 오후로(10-27 시험: 오후 6시 일정을 「7시로」 하니 오전 7시로 옮김)
            if p.sigakRo != nil && !p.ampmMal && (1...11).contains(si) && cal.component(.hour, from: h.sijak) >= 12 { si += 12 }
            st = sigakNal(geunal, si, p.sigakRo?.1 ?? cal.component(.minute, from: h.sijak))
        }
        if !h.haruJongil && !h.banbok && st < Date() {
            dap("\(nalIreum(st)) \(sigakMal(st))는 이미 지난 시각입니다. 다시 말씀해 주십시오.", false)
            return
        }
        mutgi(.bakkugi(h, st, st.addingTimeInterval(gil)))
        let saeMal = h.haruJongil ? nalIreum(st) + " 하루 종일" : nalIreum(st) + " " + sigakMal(st)
        dap("\(julMal(h, nal: true))\(IljeongMal.eul(julMal(h, nal: true))) \(saeMal)\(IljeongMal.ro(saeMal)) 바꿀까요?" + (h.banbok ? " 되풀이 일정이라 이번 한 번만 바꿉니다." : ""), true)
    }

    private func jiugiHwagin(_ h: IljeongHang, _ modu: Bool, _ dap: @escaping (String, Bool) -> Void) {
        guard h.gilnunGeot else {
            dap("\(julMal(h, nal: true))\(IljeongMal.eun(julMal(h, nal: true))) 다른 곳에서 넣은 일정이라 길눈에서는 지우지 않습니다. 아이폰 캘린더 앱에서 지워 주십시오.", false)
            return
        }
        mutgi(.jiugi(h, modu))
        var dwi = ""
        if h.banbok && !modu { dwi = " 되풀이 일정이라 이번 한 번만 지웁니다. 모두 지우시려면 모두 지워 줘라고 말씀해 주십시오." }
        else if h.banbok { dwi = " 이번부터 앞으로의 되풀이를 모두 지웁니다." }
        dap("\(julMal(h, nal: true))\(IljeongMal.eul(julMal(h, nal: true))) 지울까요?" + dwi, true)
    }

    // MARK: 제목·장소·내용 고치기 (2.117.0)

    private func kanIreum(_ kan: String) -> String { kan == "jemok" ? "제목" : (kan == "jangso" ? "장소" : "내용") }

    /// 그 일정 자세히 — 날·시각·제목·장소·적어 둔 내용
    func jasehiMal(_ h: IljeongHang) -> String {
        var m = julMal(h, nal: true) + "."
        if h.banbok { m += " 되풀이 일정입니다." }
        m += h.naeyong.isEmpty ? " 적어 둔 내용은 없습니다." : " 적어 둔 내용은 \(h.naeyong.replacingOccurrences(of: "\n", with: ". "))."
        if !h.gilnunGeot { m += " 다른 곳에서 넣은 일정이라 길눈에서는 읽기만 합니다." }
        return m
    }

    /// 2.117.0 「셋째 일정」인데 읽어 드린 목록이 그보다 짧으면 그 까닭
    private func mokEopseum(_ t: String) -> String? {
        guard let b = IljeongMal.mokBeonho(t) else { return nil }
        if Date().timeIntervalSince(mokTtae) > 1800 || majimakMok.isEmpty { return "번호로 고르시려면 먼저 오늘 일정 알려 줘처럼 일정을 들어 주십시오. 읽어 드릴 때 첫째, 둘째 번호를 붙입니다." }
        if b > majimakMok.count {
            return "방금 읽어 드린 일정은 \(majimakMok.count)개뿐입니다." + (majimakMok.count == 1 ? " 그 일정이라고 말씀해 주십시오." : " 첫째부터 \(IljeongMal.SEOSU[majimakMok.count - 1])까지 가운데 말씀해 주십시오.")
        }
        return nil
    }

    /// 고칠 일정을 찾아 여쭘
    private func gochimSijak(_ g: IljeongMal.GochimPul, _ dap: @escaping (String, Bool) -> Void) {
        if let m = mokEopseum(g.dae) { dap(m, false); return }
        let now = Date()
        let p = IljeongMal.pulgi(g.dae, now, Jeulgyeo.shared.mokrok.prefix(30).map { $0.ireum })
        let nmal = p.jemokMal.filter { !$0.isEmpty && !IljeongMal.DONGSA.contains($0) }
        var hubo: [IljeongHang] = []
        if let b = IljeongMal.mokBeonho(g.dae) {
            if let h = mokHang(b) { hubo = [h] }
        } else if IljeongMal.geuMal(g.dae) || (p.nal == nil && p.si == nil && nmal.isEmpty) {
            if let h = majimak() { hubo = [h] }
        } else {
            hubo = chatgi(p, g.dae)
        }
        if hubo.isEmpty {
            dap("어느 일정의 \(kanIreum(g.kan))인지 알아듣지 못했습니다. 오늘 2시 일정 제목 바꿔 줘, 첫째 일정 내용 읽어 줘처럼 날짜나 시각, 번호를 함께 말씀해 주십시오." +
                (majimakMok.isEmpty ? " 먼저 오늘 일정 알려 줘라고 하시면 번호를 붙여 읽어 드립니다." : ""), false)
            return
        }
        if hubo.count > 1 {
            let l = Array(hubo.prefix(5))
            mutgi(.goreugiG(l, g))
            dap("맞는 일정이 \(hubo.count)개 있습니다. " + l.enumerated().map { "\($0.offset + 1)번, \(julMal($0.element, nal: true))" }.joined(separator: ". ") + ". 몇 번입니까?", true)
            return
        }
        gochimHwagin(hubo[0], g.kan, g.hal, g.gap, dap)
    }

    /// 칸 하나를 고칠지 여쭘 — 값이 없으면 값부터 여쭘
    private func gochimHwagin(_ h: IljeongHang, _ kan: String, _ hal: String, _ gap: String, _ dap: @escaping (String, Bool) -> Void) {
        majimakNeoki(h)
        if hal == "ilgi" { dap(jasehiMal(h), false); return }
        guard h.gilnunGeot else {
            dap("\(julMal(h, nal: true))\(IljeongMal.eun(julMal(h, nal: true))) 다른 곳에서 넣은 일정이라 길눈에서는 고치지 않습니다. 아이폰 캘린더 앱에서 고쳐 주십시오.", false)
            return
        }
        let ireum = kanIreum(kan)
        if hal == "jiugi" {
            if kan == "jemok" { dap("제목은 지울 수 없습니다. 새 제목으로 바꾸시려면 제목 바꿔 줘라고 말씀해 주십시오.", false); return }
            let iteum = kan == "jangso" ? !h.jangso.isEmpty : !h.naeyong.isEmpty
            if !iteum { dap("\(julMal(h, nal: true))에는 지울 \(ireum)\(IljeongMal.eun(ireum)) 없습니다.", false); return }
            mutgi(.gochim(h, kan, hal, ""))
            dap("\(julMal(h, nal: true))의 \(ireum)\(IljeongMal.eul(ireum)) 지울까요?", true)
            return
        }
        if gap.trimmingCharacters(in: .whitespaces).isEmpty {
            mutgi(.gap(h, kan, hal))
            var q = julMal(h, nal: true) + ". "
            switch kan {
            case "jemok": q += "새 제목을 말씀해 주십시오."
            case "jangso": q += "새 장소를 말씀해 주십시오."
            default:
                q += "적어 둘 내용을 말씀해 주십시오."
                if !h.naeyong.isEmpty { q += " 지금 적힌 내용은 \(h.naeyong.replacingOccurrences(of: "\n", with: ". "))입니다." }
            }
            dap(q, true)
            return
        }
        mutgi(.gochim(h, kan, hal, gap))
        let dwi = h.banbok ? " 되풀이 일정이라 이번부터 앞으로 모두 바뀝니다." : ""
        var q: String
        if kan == "naeyong" {
            q = h.naeyong.isEmpty ? "\(julMal(h, nal: true))의 내용으로 「\(gap)」\(IljeongMal.eul(gap)) 적을까요?" : "\(julMal(h, nal: true))의 내용에 「\(gap)」\(IljeongMal.eul(gap)) 더할까요?"
        } else {
            q = "\(julMal(h, nal: true))의 \(ireum)\(IljeongMal.eul(ireum)) 「\(gap)」\(IljeongMal.ro(gap)) 바꿀까요?"
        }
        dap(q + dwi, true)
    }

    /// 칸 고치기 — 되풀이 일정은 이번부터 앞으로 모두
    func kanBakkugi(_ h: IljeongHang, _ kan: String, _ hal: String, _ gap: String) -> Bool {
        guard heorak, h.gilnunGeot else { return false }
        let e = h.ev
        switch kan {
        case "jemok": e.title = gap
        case "jangso": e.location = hal == "jiugi" ? nil : gap
        default:
            let jeon = (e.notes ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            e.notes = hal == "jiugi" ? nil : (jeon.isEmpty ? gap : jeon + "\n" + gap)
        }
        do {
            try store.save(e, span: h.banbok ? .futureEvents : .thisEvent, commit: true)
        } catch {
            Girok.shared.namgi("iljeong_oryu", ["dan": "kan", "e": String(error.localizedDescription.prefix(80))])
            return false
        }
        Girok.shared.namgi("iljeong_kan", ["kan": kan, "hal": hal, "banbok": h.banbok])
        gaengsin += 1
        if kan == "jangso" { ddeonaGyesan(kkok: true) }
        return true
    }

    /// 여쭌 것의 대답 — 받았으면 참
    private func dapCheori(_ m: IljeongMureum, _ alts: [String], _ z: String, _ t: String, _ dap: @escaping (String, Bool) -> Void) -> Bool {
        let hd = MalHagi.hwaginDap(alts)
        let ye = yeMal(alts, z)
        let ani = hd == .ani
        let now = Date()
        switch m {
        case .neoki(var s):
            if ye { mureum = nil; neokiHagi(s, dap); return true }
            if ani && z.count <= 10 { mureum = nil; dap("넣지 않았습니다.", false); return true }
            // 「아니 3시로」처럼 바로잡으시면 고쳐서 다시 여쭘
            let p2 = IljeongMal.pulgi(t, now)
            var si: (Int, Int)? = p2.sigakRo
            if si == nil, let h = p2.si { si = (h, p2.bun) }
            if si != nil || p2.nal != nil || p2.nalRo != nil {
                let gil = s.kkeut.timeIntervalSince(s.sijak)
                let nal = p2.nalRo ?? p2.nal ?? Calendar.current.startOfDay(for: s.sijak)
                if let x = si {
                    // 2.117.0 오전·오후를 말하지 않으시면 여쭌 일정이 오후일 때 오후로
                    let jeonSi = Calendar.current.component(.hour, from: s.sijak)
                    let h2 = (!p2.ampmMal && (1...11).contains(x.0) && jeonSi >= 12 && !s.haruJongil) ? x.0 + 12 : x.0
                    s.haruJongil = false
                    s.sijak = sigakNal(nal, h2, x.1)
                    s.kkeut = s.sijak.addingTimeInterval(gil >= 86400 ? 3600 : gil)
                } else {
                    s.sijak = nal.addingTimeInterval(s.sijak.timeIntervalSince(Calendar.current.startOfDay(for: s.sijak)))
                    s.kkeut = s.sijak.addingTimeInterval(gil)
                }
                mutgi(.neoki(s))
                dap(hwaginMal(s) + " 넣을까요?", true)
                return true
            }
            if ani { mureum = nil; dap("넣지 않았습니다.", false); return true }
            mureum = nil
            return false
        case .sigak(let p):
            if ani && z.count <= 10 { mureum = nil; dap("넣지 않았습니다.", false); return true }
            let p2 = IljeongMal.pulgi(t, now)
            if p2.si == nil && !p2.haruJongil && p2.nal == nil {
                // 2.117.0 시각을 못 알아들으면 놓지 않고 다시 여쭘(짧은 말일 때)
                if z.count <= 12 { mutgi(m); dap("몇 시인지 알아듣지 못했습니다. 오후 2시, 아침 9시 반처럼 말씀해 주십시오. 그만두시려면 그만이라고 말씀해 주십시오.", true); return true }
                mureum = nil
                return false
            }
            if let h = p2.si { p.si = h; p.bun = p2.bun; p.kkeutSi = p2.kkeutSi; p.kkeutBun = p2.kkeutBun }
            if p2.haruJongil { p.haruJongil = true }
            if let n = p2.nal { p.nal = n }
            if p.jemok.isEmpty && !p2.jemok.isEmpty { p.jemok = p2.jemok; if p.jangso.isEmpty { p.jangso = p2.jangso } }
            mureum = nil
            neokiSijak(p, dap)
            return true
        case .jemok(let p):
            if ani && z.count <= 6 { mureum = nil; dap("넣지 않았습니다.", false); return true }
            let p2 = IljeongMal.pulgi(t, now)
            let jm = IljeongMal.jemokDap(p2.jemok.isEmpty ? t : p2.jemok)
            if jm.isEmpty || jm.count > 40 { mutgi(m); dap("제목을 알아듣지 못했습니다. 병원, 회의처럼 짧게 말씀해 주십시오.", true); return true }
            p.jemok = jm
            if p.jangso.isEmpty { p.jangso = IljeongMal.pulgi(jm, now, Jeulgyeo.shared.mokrok.prefix(30).map { $0.ireum }).jangso }
            mureum = nil
            neokiSijak(p, dap)
            return true
        case .goreugi(let hubo, let hal, let p, let modu):
            if ani && z.count <= 10 { mureum = nil; dap("알겠습니다. 그대로 둡니다.", false); return true }
            guard let b = IljeongMal.beonho(t), b >= 1, b <= hubo.count else {
                if z.count <= 8 { mutgi(m); dap("1번부터 \(hubo.count)번 가운데 몇 번인지 말씀해 주십시오.", true); return true }
                mureum = nil
                return false
            }
            mureum = nil
            majimakNeoki(hubo[b - 1])
            if hal == "bakkugi" { bakkugiHwagin(hubo[b - 1], p, dap) } else { jiugiHwagin(hubo[b - 1], modu, dap) }
            return true
        case .goreugiG(let hubo, let g):
            if ani && z.count <= 10 { mureum = nil; dap("알겠습니다. 그대로 둡니다.", false); return true }
            guard let b = IljeongMal.beonho(t), b >= 1, b <= hubo.count else {
                if z.count <= 8 { mutgi(m); dap("1번부터 \(hubo.count)번 가운데 몇 번인지 말씀해 주십시오.", true); return true }
                mureum = nil
                return false
            }
            mureum = nil
            gochimHwagin(hubo[b - 1], g.kan, g.hal, g.gap, dap)
            return true
        case .gap(let h, let kan, let hal):
            if ani && z.count <= 6 { mureum = nil; dap("바꾸지 않았습니다.", false); return true }
            let gap = kan == "naeyong" ? t.trimmingCharacters(in: CharacterSet(charactersIn: " .")) : IljeongMal.jemokDap(t)
            if gap.isEmpty { mureum = nil; return false }
            mureum = nil
            gochimHwagin(h, kan, hal, gap, dap)
            return true
        case .gochim(let h, let kan, let hal, let gap):
            if ye {
                mureum = nil
                if kanBakkugi(h, kan, hal, gap) {
                    let m0 = hal == "jiugi" ? "지웠습니다." : (kan == "naeyong" ? "적었습니다." : "바꿨습니다.")
                    dap(m0 + hint("gochim", "적어 둔 내용은 그 일정 내용 읽어 줘라고 하시면 읽어 드립니다."), false)
                } else {
                    dap("고치지 못했습니다. 아이폰 캘린더 앱에서 고쳐 주십시오.", false)
                }
                return true
            }
            if ani && z.count <= 10 { mureum = nil; dap("바꾸지 않았습니다.", false); return true }
            mureum = nil
            return false
        case .mueot(let h):
            if ani && z.count <= 6 { mureum = nil; dap("바꾸지 않았습니다.", false); return true }
            if let g = IljeongMal.gochimPulgi(t, now) {
                mureum = nil
                gochimHwagin(h, g.kan, (g.hal == "ilgi" && g.kan != "naeyong") ? "bakkugi" : g.hal, g.gap, dap)
                return true
            }
            var kan = ""
            if z.contains("제목") || z.contains("이름") { kan = "jemok" } else if z.contains("장소") { kan = "jangso" } else if z.contains("내용") || z.contains("메모") { kan = "naeyong" }
            if !kan.isEmpty { mureum = nil; gochimHwagin(h, kan, kan == "naeyong" ? "deohagi" : "bakkugi", "", dap); return true }
            let p2 = IljeongMal.pulgi(t, now)
            if p2.sigakRo == nil, let s0 = p2.si { p2.sigakRo = (s0, p2.bun) }
            if p2.nalRo == nil, let n0 = p2.nal { p2.nalRo = n0 }
            if p2.sigakRo != nil || p2.nalRo != nil { mureum = nil; bakkugiHwagin(h, p2, dap); return true }
            if z.contains("시간") || z.contains("시각") { mutgi(m); dap("몇 시로 바꿀까요?", true); return true }
            if z.contains("날짜") || z.contains("날") { mutgi(m); dap("어느 날로 바꿀까요? 내일, 금요일처럼 말씀해 주십시오.", true); return true }
            if z.count <= 8 { mutgi(m); dap("시각, 날짜, 제목, 장소, 내용 가운데 무엇을 바꿀지 말씀해 주십시오.", true); return true }
            mureum = nil
            return false
        case .bakkugi(let h, let s, let k):
            if ye {
                mureum = nil
                dap(bakkugi(h, s, k) ? "바꿨습니다." : "바꾸지 못했습니다. 아이폰 캘린더 앱에서 고쳐 주십시오.", false)
                return true
            }
            if ani { mureum = nil; dap("바꾸지 않았습니다.", false); return true }
            mureum = nil
            return false
        case .jiugi(let h, let modu):
            if ye {
                mureum = nil
                dap(jiugi(h, modu: modu) ? "지웠습니다. 잘못 지우셨으면 하루 안에 방금 지운 일정 되살려 줘라고 말씀해 주십시오." : "지우지 못했습니다. 아이폰 캘린더 앱에서 지워 주십시오.", false)
                return true
            }
            if ani { mureum = nil; dap("지우지 않았습니다.", false); return true }
            mureum = nil
            return false
        case .annae(let jangso):
            if hd == .ye {
                mureum = nil
                Girok.shared.namgi("iljeong_annae", [:])
                MalHagi.shared.cheori(["\(jangso) 가자"], dap)
                return true
            }
            if ani { mureum = nil; dap("알겠습니다. 가실 때 말로 하기에서 \(jangso) 가자라고 말씀해 주십시오.", false); return true }
            mureum = nil
            return false
        }
    }

    /// 「아침 브리핑 7시로 해 줘」, 「아침 브리핑 꺼 줘」, 「아침 브리핑 들려 줘」
    private func beuripingMal(_ z: String, _ dap: @escaping (String, Bool) -> Void) -> Bool {
        if z.contains("꺼") || z.contains("끄") || z.contains("그만") || z.contains("멈춰") {
            achimKyeojim = false
            dap("아침 브리핑을 껐습니다.", false)
            return true
        }
        if let si = JeonggiHochul.sigakDeul(z).first {
            var h = si.0
            if (z.contains("아침") || z.contains("오전") || z.contains("새벽")) && h == 12 { h = 0 }
            if z.contains("평일") { achimYoil = "pyeongil" } else if z.contains("주말") { achimYoil = "jumal" } else if z.contains("매일") || z.contains("날마다") { achimYoil = "maeil" }
            achimSi = h
            achimBun = si.1
            achimKyeojim = true
            dap("아침 브리핑을 \(achimYoilMal) \(JeonggiHochul.sigakMal(h, si.1))로 정했습니다. 그 시각에 날짜, 날씨, 오늘 일정을 읽어 드립니다. 길눈이 꺼져 있으면 알림을 누르시면 읽어 드립니다.", false)
            return true
        }
        if z.contains("켜") {
            achimKyeojim = true
            dap("아침 브리핑을 켰습니다. \(achimMal)에 읽어 드립니다.", false)
            return true
        }
        dap("", false)
        beuriping()
        Girok.shared.namgi("iljeong_achim", ["jadong": false])
        return true
    }
}

// MARK: - 화면

struct IljeongView: View {
    @ObservedObject private var y = Iljeong.shared
    @State private var deureun = ""
    @State private var mok: [IljeongHang] = []
    @State private var achimSigak = Date()
    @AccessibilityFocusState private var deureunChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !y.heorak {
                    Text("일정은 아이폰 캘린더에 담깁니다. 길눈이 캘린더를 읽고 쓰려면 허락이 필요합니다. 아래 단추를 누르고 전체 접근 허용을 눌러 주십시오. 일정 내용은 폰 밖으로 보내지 않습니다.")
                        .font(.title3)
                    if y.seoljeongPiryo {
                        Button("캘린더 허락 — 누르면 아이폰 설정의 길눈 화면을 엽니다. 캘린더에서 전체 접근을 골라 주십시오") { y.seoljeongYeolgi() }
                            .buttonStyle(KeunDanchu())
                    } else {
                        Button("캘린더 허락하기 — 누르면 허락 창이 뜹니다") {
                            y.heorakCheong { ok in
                                SoriEngine.shared.mal(ok ? "캘린더 허락을 받았습니다. 이제 일정을 듣고 넣으실 수 있습니다." : "캘린더 허락이 없어 일정을 쓸 수 없습니다.")
                                bureogi()
                            }
                        }
                        .buttonStyle(KeunDanchu())
                    }
                } else {
                    Button("오늘 일정 듣기") { malHagi(y.oneulMal()) }.buttonStyle(KeunDanchu())
                    Button("내일 일정 듣기") { malHagi(y.naeilMal()) }.buttonStyle(KeunDanchu())
                    Button("이번 주 일정 듣기") { malHagi(y.ibeonJuMal()) }.buttonStyle(KeunDanchu())
                    if !deureun.isEmpty {
                        Text(deureun).font(.title3).fixedSize(horizontal: false, vertical: true).accessibilityFocused($deureunChojeom)
                    }
                    NavigationLink { IljeongSaeView() } label: { Text("일정 넣기") }.buttonStyle(KeunDanchu())
                    if y.jiunGeot != nil {
                        Button("방금 지운 일정 되살리기 — \(y.jiunMal)") { malHagi(y.doesalligi()); bureogi() }.buttonStyle(KeunDanchu())
                    }
                    Text("다가오는 일정 — 앞으로 2주, \(mok.count)개").font(.title3.weight(.semibold))
                    if mok.isEmpty {
                        Text("앞으로 2주 동안 잡힌 일정이 없습니다.").font(.title3)
                    } else {
                        Mokrok5(mok) { h in
                            NavigationLink { IljeongHangView(h: h) } label: {
                                Text(y.julMal(h, nal: true) + (h.gilnunGeot ? "" : " — 다른 곳에서 넣음"))
                            }
                            .buttonStyle(KeunDanchu())
                        }
                    }
                }
                DisclosureGroup("아침 브리핑 펼치기 — 지금 \(y.achimMal)") {
                    VStack(alignment: .leading, spacing: 10) {
                        Toggle(isOn: Binding(get: { y.achimKyeojim }, set: { v in
                            y.achimKyeojim = v
                            SoriEngine.shared.mal(v ? "아침 브리핑을 켰습니다. \(y.achimMal)에 읽어 드립니다." : "아침 브리핑을 껐습니다.")
                        })) { Text("아침 브리핑").font(.title3.weight(.semibold)) }
                            .frame(minHeight: 60)
                        DatePicker("브리핑 시각", selection: Binding(get: { achimSigak }, set: { v in
                            achimSigak = v
                            let c = Calendar.current
                            y.achimSi = c.component(.hour, from: v)
                            y.achimBun = c.component(.minute, from: v)
                        }), displayedComponents: .hourAndMinute)
                            .font(.title3)
                        Picker("요일", selection: Binding(get: { y.achimYoil }, set: { y.achimYoil = $0 })) {
                            Text("날마다").tag("maeil")
                            Text("평일만 — 월요일부터 금요일").tag("pyeongil")
                            Text("주말만 — 토요일과 일요일").tag("jumal")
                        }
                        .font(.title3)
                        Button("지금 들어 보기") { y.beuriping() }.buttonStyle(KeunDanchu())
                        Text("정한 시각에 길눈이 켜져 있으면 바로, 꺼져 있으면 알림을 울려 누르시면 오늘 날짜, 날씨, 오늘 일정, 첫 일정 떠날 시각을 읽어 드립니다. 그 무렵에 듣기 예약으로 라디오를 걸어 두셨으면 브리핑을 마친 뒤 이어서 틉니다.")
                            .font(.body).fixedSize(horizontal: false, vertical: true)
                    }
                }
                .font(.title3)
                DisclosureGroup("떠날 시각 알림 펼치기 — 지금 " + (y.ddeonaKyeojim ? "켜져 있음, 여유 \(y.yeoyu)분" : "꺼져 있음")) {
                    VStack(alignment: .leading, spacing: 10) {
                        Toggle(isOn: Binding(get: { y.ddeonaKyeojim }, set: { v in
                            y.ddeonaKyeojim = v
                            SoriEngine.shared.mal(v ? "떠날 시각 알림을 켰습니다." : "떠날 시각 알림을 껐습니다.")
                        })) { Text("떠날 시각 알림").font(.title3.weight(.semibold)) }
                            .frame(minHeight: 60)
                        Picker("여유", selection: Binding(get: { y.yeoyu }, set: { y.yeoyu = $0 })) {
                            Text("여유 10분").tag(10)
                            Text("여유 20분").tag(20)
                            Text("여유 30분").tag(30)
                        }
                        .font(.title3)
                        Button("지금 자리로 다시 셈하기") { y.ddeonaGyesan(kkok: true); SoriEngine.shared.mal("지금 자리에서 다시 셈합니다.") }
                            .buttonStyle(KeunDanchu())
                        let ap = y.ddeonaMok.filter { $0.sijak > Date() }
                        if ap.isEmpty {
                            Text("앞으로 24시간 안에 장소가 있는 일정이 없거나, 아직 지금 자리를 몰라 셈하지 못했습니다.").font(.body)
                        }
                        ForEach(ap, id: \.ki) { x in
                            Text("\(x.jemok), \(x.jangso) — 걸리는 시간 어림 \(x.bun)분, 떠나실 시각 \(y.nalIreum(x.ddeona)) \(y.sigakMal(x.ddeona))").font(.body)
                        }
                        Text("장소가 있는 일정은 지금 자리에서 그곳까지의 곧은 거리로 걸리는 시간을 어림합니다. 1.5킬로미터 안은 걸어서, 그 넘으면 탈것으로 셈하고 여유를 더합니다. 떠날 시각 15분 전에 한 번, 떠날 시각에 한 번 알려 드리고, 떠날 시각에는 길 안내를 시작할지 여쭙니다. 실제 길과 차편에 따라 다를 수 있는 어림입니다.")
                            .font(.body).fixedSize(horizontal: false, vertical: true)
                    }
                }
                .font(.title3)
                Text(IljeongView.malroMal).font(.body).fixedSize(horizontal: false, vertical: true)
            }
            .padding()
        }
        .sokHwamyeon("일정")
        .onAppear { bureogi() }
        .onChange(of: y.gaengsin) { _ in bureogi() }
    }

    static let malroMal = "말로도 됩니다. 오늘 일정 알려 줘. 내일 뭐 있어. 이번 주 일정. 다음 일정. 내일 오후 2시 국립극장 회의 넣어 줘. 매주 화요일 오전 10시 회의 넣어 줘. 내일 회의 3시로 바꿔 줘. 금요일 점심 약속 지워 줘. 방금 지운 일정 되살려 줘. 아침 브리핑 7시로 해 줘. 아침 브리핑 들려 줘."

    private func bureogi() {
        let now = Date()
        mok = Array(y.ilgi(IljeongMal.haru(now, 0), IljeongMal.haru(now, 14)).prefix(60))
        achimSigak = Calendar.current.date(bySettingHour: y.achimSi, minute: y.achimBun, second: 0, of: now) ?? now
    }

    private func malHagi(_ t: String) {
        deureun = t
        SoriEngine.shared.mal(t)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { deureunChojeom = true }
    }
}

/// 일정 넣기 — 제목, 장소, 날짜, 하루 종일, 시각, 길이, 되풀이
struct IljeongSaeView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var jemok = ""
    @State private var jangso = ""
    @State private var nal = Date()
    @State private var jongil = false
    @State private var sigak = Calendar.current.date(bySettingHour: 14, minute: 0, second: 0, of: Date()) ?? Date()
    @State private var gilBun = 60
    @State private var banbok = ""

    var body: some View {
        Form {
            Section {
                TextField("제목 — 예: 국립극장 회의", text: $jemok).font(.title3)
                TextField("장소 — 비워도 됩니다. 넣으시면 떠날 시각을 알려 드립니다", text: $jangso).font(.title3)
                DatePicker("날짜", selection: $nal, in: Calendar.current.startOfDay(for: Date())..., displayedComponents: .date).font(.title3)
                Toggle(isOn: $jongil) { Text("하루 종일").font(.title3) }
                if !jongil {
                    DatePicker("시각", selection: $sigak, displayedComponents: .hourAndMinute).font(.title3)
                    Picker("길이", selection: $gilBun) {
                        Text("30분").tag(30)
                        Text("1시간").tag(60)
                        Text("2시간").tag(120)
                        Text("3시간").tag(180)
                    }
                    .font(.title3)
                }
                Picker("되풀이", selection: $banbok) {
                    Text("되풀이 안 함").tag("")
                    Text("날마다").tag("maeil")
                    Text("평일마다").tag("pyeongil")
                    Text("매주 같은 요일").tag("maeju")
                }
                .font(.title3)
            }
            Section {
                Button("이대로 넣기") { neoki() }.font(.title3.weight(.semibold))
                Button("그만두기") { dismiss() }.font(.title3)
            }
        }
        .sokHwamyeon("일정 넣기")
    }

    private func neoki() {
        let y = Iljeong.shared
        let cal = Calendar.current
        let j = jemok.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !j.isEmpty else { SoriEngine.shared.mal("제목을 넣어 주십시오."); return }
        let n = cal.startOfDay(for: nal)
        var s: SaeIljeong
        if jongil {
            s = SaeIljeong(jemok: j, jangso: jangso.trimmingCharacters(in: .whitespacesAndNewlines), sijak: n, kkeut: IljeongMal.haru(n, 1), haruJongil: true, banbok: banbok, yoil: 0)
        } else {
            let st = cal.date(bySettingHour: cal.component(.hour, from: sigak), minute: cal.component(.minute, from: sigak), second: 0, of: n) ?? n
            s = SaeIljeong(jemok: j, jangso: jangso.trimmingCharacters(in: .whitespacesAndNewlines), sijak: st, kkeut: st.addingTimeInterval(Double(gilBun * 60)), haruJongil: false, banbok: banbok, yoil: 0)
        }
        if s.banbok == "maeju" { s.yoil = cal.component(.weekday, from: s.sijak) }
        Iljeong.banbokMatchum(&s)
        if !s.haruJongil && s.banbok.isEmpty && s.sijak < Date() { SoriEngine.shared.mal("이미 지난 시각입니다. 날짜나 시각을 다시 골라 주십시오."); return }
        guard y.neoki(s) else { SoriEngine.shared.mal("캘린더에 넣지 못했습니다. 아이폰 설정의 길눈에서 캘린더 허락이 전체 접근인지 확인해 주십시오."); return }
        SoriEngine.shared.mal("넣었습니다. " + y.hwaginMal(s))
        dismiss()
    }
}

/// 일정 하나 — 가는 길, 시각 바꾸기, 지우기(길눈이 넣은 일정만)
struct IljeongHangView: View {
    @Environment(\.dismiss) private var dismiss
    let h: IljeongHang
    @State private var saeNal = Date()
    @State private var saeSigak = Date()
    @State private var jiugiHwagin = false

    var body: some View {
        let y = Iljeong.shared
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(y.julMal(h, nal: true) + "." + (h.haruJongil ? "" : " 끝나는 시각 \(y.sigakMal(h.kkeut)).") + (h.banbok ? " 되풀이 일정입니다." : ""))
                    .font(.title3)
                Text(h.gilnunGeot ? "길눈에서 넣은 일정입니다." : "다른 곳에서 넣은 일정이라 길눈에서는 읽기만 합니다. 고치거나 지우시려면 아이폰 캘린더 앱을 써 주십시오.")
                    .font(.body)
                // 2.117.0 말로 적어 둔 내용
                Text(h.naeyong.isEmpty ? "적어 둔 내용 없음. 말로 하기에서 그 일정 내용 추가해 줘라고 하시면 적어 둡니다." : "적어 둔 내용 — " + h.naeyong.replacingOccurrences(of: "\n", with: ". "))
                    .font(.body)
                if !h.jangso.isEmpty {
                    Button("\(h.jangso) — 가는 길 고르기 화면 열기") { gilYeolgi() }.buttonStyle(KeunDanchu())
                }
                if h.gilnunGeot {
                    DatePicker("새 날짜", selection: $saeNal, displayedComponents: .date).font(.title3)
                    if !h.haruJongil {
                        DatePicker("새 시각", selection: $saeSigak, displayedComponents: .hourAndMinute).font(.title3)
                    }
                    Button("이대로 바꾸기" + (h.banbok ? " — 되풀이 일정은 이번 한 번만" : "")) { bakkugi() }.buttonStyle(KeunDanchu())
                    if !jiugiHwagin {
                        Button("이 일정 지우기") { jiugiHwagin = true; SoriEngine.shared.mal("정말 지울까요? 아래의 정말 지우기를 누르시면 지웁니다.") }
                            .buttonStyle(KeunDanchu())
                    } else {
                        Button("정말 지우기" + (h.banbok ? " — 이번 한 번만" : "")) { jiugi(false) }.buttonStyle(KeunDanchu())
                        if h.banbok {
                            Button("되풀이 일정 이번부터 모두 지우기") { jiugi(true) }.buttonStyle(KeunDanchu())
                        }
                        Button("지우지 않고 그만두기") { jiugiHwagin = false }.buttonStyle(KeunDanchu())
                    }
                }
            }
            .padding()
        }
        .sokHwamyeon("일정 하나")
        .onAppear { saeNal = h.sijak; saeSigak = h.sijak }
    }

    private func gilYeolgi() {
        let jang = h.jangso
        SoriEngine.shared.mal("\(jang)\(IljeongMal.eul(jang)) 찾습니다.")
        Task {
            let l = await Chatgi.jangso(jang)
            await MainActor.run {
                guard let j = l?.first else {
                    SoriEngine.shared.mal("\(jang)\(IljeongMal.eul(jang)) 찾지 못했습니다. 길 찾기 탭에서 이름이나 주소로 찾아 주십시오.")
                    return
                }
                TabGil.shared.tab = 0
                GilGil.shared.cheotHwamyeon()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { GilGil.shared.path.append(GilHwamyeon.mokjeok(j)) }
            }
        }
    }

    private func bakkugi() {
        let y = Iljeong.shared
        let cal = Calendar.current
        let n = cal.startOfDay(for: saeNal)
        let gil = h.kkeut.timeIntervalSince(h.sijak)
        let st = h.haruJongil ? n : (cal.date(bySettingHour: cal.component(.hour, from: saeSigak), minute: cal.component(.minute, from: saeSigak), second: 0, of: n) ?? n)
        if y.bakkugi(h, st, st.addingTimeInterval(gil)) {
            SoriEngine.shared.mal("바꿨습니다. " + y.nalIreum(st) + " " + (h.haruJongil ? "하루 종일" : y.sigakMal(st)) + ", " + h.jemok + ".")
            dismiss()
        } else {
            SoriEngine.shared.mal("바꾸지 못했습니다.")
        }
    }

    private func jiugi(_ modu: Bool) {
        if Iljeong.shared.jiugi(h, modu: modu) {
            SoriEngine.shared.mal(modu ? "되풀이 일정을 이번부터 모두 지웠습니다." : "지웠습니다. 일정 화면의 방금 지운 일정 되살리기로 하루 안에 되살릴 수 있습니다.")
            dismiss()
        } else {
            SoriEngine.shared.mal("지우지 못했습니다.")
        }
    }
}
