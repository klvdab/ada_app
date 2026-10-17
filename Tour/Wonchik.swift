// LVD투어스튜디오 앱 — 원칙 점검·내보내기·셈 (1.2.0판, 빌드 261017-T4, 방클, 이사장님 지시 「웹에 있는 모든 기능을 겉과 속이 같게」)
// 웹 lvdts.html 의 같은 이름 함수를 그대로 옮김: findLR·findTouchWord·findBunmyeon·clockOrderBad·hasDirection·textFb·
//   openCheck·closeCheck·headMissing·scriptIssues·issueLines·askLines·principleTable·nextTodo·
//   guideText·noticeText·planText·mapJson·renderTimeSum·pmText·offDist·photoQuality·calcWalk·SEEDS·TTKAN·DIRWORDS
// 번호는 작성원칙 통합본 16개(2026-10-12 이사장님 확정)
import Foundation
import UIKit

enum Wonchik {
    static let BANNED = Yeonseup.BANNED
    static let LRBAN = ["왼쪽", "오른쪽", "이쪽", "저쪽", "왼편", "오른편"]
    /// 방향 도우미 — 웹 DIRWORDS 와 같은 스무 낱말
    static let DIRWORDS = ["12시 방향", "1시 방향", "2시 방향", "3시 방향", "4시 방향", "5시 방향", "6시 방향",
                           "7시 방향", "8시 방향", "9시 방향", "10시 방향", "11시 방향",
                           "바로 앞", "뒤쪽", "왼쪽", "오른쪽", "발밑", "머리 위", "스무 걸음 앞", "팔을 뻗으면 닿는 자리"]
    /// 테마투어 느낌 칸 여섯과 기억 칸 — 웹 TTKAN
    static let TTKAN: [(id: String, nm: String, ex: String)] = [
        ("sori", "들리는 소리", "예: 오른쪽에서 두부 써는 칼 소리, 멀리 아이들 소리"),
        ("naem", "나는 냄새", "예: 참기름 볶는 냄새가 3시 방향에서 옵니다"),
        ("bal", "발밑 느낌", "예: 시멘트에서 흙으로 바뀝니다. 밟으면 자박자박합니다"),
        ("son", "손에 닿는 것", "예: 허리 높이 나무 난간, 결이 세로로 지나갑니다"),
        ("byeot", "볕과 바람", "예: 처마 아래로 들어서면 볕이 끊기고 서늘해집니다"),
        ("saram", "사람의 기척", "예: 좌우로 사람이 스쳐 지나갑니다. 말소리가 낮습니다"),
        ("gieok", "현장영상해설사의 기억·이야기", "예: 제가 어릴 때 이 골목에서")
    ]
    /// 이미 운영 중인 코스 — 웹 SEEDS
    static let SEEDS: [(name: String, place: String, field: String, meet: String, note: String, spots: [String])] = [
        ("경복궁 코스", "경복궁", "문화유산 분야", "광화문 앞 매표소 옆", "조선의 으뜸 궁궐을 흥례문에서 향원정까지 걸으며 현장영상해설하는 코스입니다.", ["흥례문 앞", "근정문", "근정전 앞마당", "사정전", "경회루 앞", "향원정", "국립민속박물관 앞"]),
        ("창덕궁 코스", "창덕궁", "문화유산 분야", "돈화문 앞", "", ["돈화문", "인정전", "선정전", "낙선재"]),
        ("덕수궁 코스", "덕수궁", "문화유산 분야", "대한문 앞", "", ["대한문", "중화전", "석조전 앞"]),
        ("창경궁 코스", "창경궁", "문화유산 분야", "홍화문 앞", "", ["홍화문", "명정전", "춘당지"]),
        ("종묘 코스", "종묘", "문화유산 분야", "종묘 정문 앞", "", ["외대문", "정전 앞", "영녕전"]),
        ("태릉 코스", "태릉", "문화유산 분야", "태릉 매표소 앞", "", ["홍살문", "정자각", "능침 앞"]),
        ("남산 코스", "남산", "자연·경관 분야", "남산케이블카 승강장 앞", "", ["케이블카 승강장", "팔각정", "전망대"]),
        ("서울공예박물관 코스", "서울공예박물관", "박물관·전시 분야", "안내동 앞", "", ["안내동", "전시1동", "전시3동"]),
        ("국립중앙박물관 코스", "국립중앙박물관", "박물관·전시 분야", "으뜸홀", "", ["으뜸홀", "선사고대관", "사유의 방"]),
        ("항공박물관 코스", "국립항공박물관", "박물관·전시 분야", "1층 로비", "", ["1층 로비", "항공역사관", "항공산업관"])
    ]

    static func has(_ t: String, _ re: String) -> Bool { t.range(of: re, options: .regularExpression) != nil }
    static func bin(_ t: String) -> Bool { t.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    // ---- 글 한 덩이 살피기 ----
    static func findBanned(_ t: String) -> [String] { Yeonseup.geumji(t) }
    static func findLR(_ t: String) -> [String] {
        var out = LRBAN.filter { t.contains($0) }
        if has(t, "[좌우]측(?!\\s*(상단|하단))") { out.append("좌측·우측") }
        return out
    }
    static func findTouchWord(_ t: String) -> Bool { t.contains("더듬") }
    static func findBunmyeon(_ t: String) -> Bool { t.contains("분면") }
    static func clockOrderBad(_ t: String) -> [String] {
        var bad: [String] = []
        let re = try! NSRegularExpression(pattern: "(\\d{1,2})\\s*시\\s*방향")
        for sen in t.components(separatedBy: CharacterSet(charactersIn: ".!?\n")) {
            let ns = sen as NSString
            let ms = re.matches(in: sen, range: NSRange(location: 0, length: ns.length))
            var hs: [Int] = []
            for m in ms { if let h = Int(ns.substring(with: m.range(at: 1))), h >= 1 && h <= 12 { hs.append(h % 12) } }
            if hs.count < 2 { continue }
            let before0 = ms.first.map { ns.substring(to: $0.range.location) } ?? ""
            let had12 = has(before0, "12\\s*시")
            if hs.count >= 3 && hs[0] != 0 && !had12 { bad.append("12시부터 시작하지 않음"); continue }
            for i in 1..<hs.count where hs[i] < hs[i - 1] { bad.append("차례가 되돌아감"); break }
        }
        return bad
    }
    /// 9번에 따라 왼쪽·오른쪽은 방향을 짚은 것으로 세지 않는다
    static func hasDirection(_ t: String) -> Bool { has(t, "\\d+시 방향|바로 앞|발밑|머리 위|걸음|위로|아래로") }

    /// 글을 치는 동안 알려 주는 한 줄 — 웹 textFb
    static func textFb(_ k: Kos, _ t: String, roomMin: Double, wantDir: Bool) -> String {
        let sec = k.readSec(t)
        var s = "\(t.filter { !$0.isWhitespace }.count)자, 읽는 데 \(sec)초쯤 걸립니다."
        if wantDir && !bin(t) && !hasDirection(t) { s += " 방향을 짚어 주십시오. 12시·3시·6시·9시 시계 방향으로요." }
        let lr = findLR(t); if !lr.isEmpty { s += " 상대 방향 표현이 들었습니다(" + lr.joined(separator: ", ") + "). 시계 방향으로 바꾸십시오." }
        if findTouchWord(t) { s += " 더듬다는 쓰지 않습니다. 만져보시면으로 바꾸십시오." }
        if findBunmyeon(t) { s += " 분면이라는 말은 현장영상해설문에 내놓지 않습니다. 좌측 상단처럼 풀어 쓰십시오." }
        let co = clockOrderBad(t); if !co.isEmpty { s += " 시계 방향 차례가 어긋납니다(" + co[0] + "). 12시에서 시작해 3시·6시·9시로 나아가야 합니다." }
        if roomMin > 0 {
            let room = Int(roomMin * 60)
            s += sec > room ? " 머무는 시간(\(Int(roomMin))분)보다 \(sec - room)초 깁니다." : " 머무는 시간 안에 들어갑니다."
        }
        let bad = findBanned(t); if !bad.isEmpty { s += " 금지어: " + bad.joined(separator: ", ") }
        return s
    }

    // ---- 코스 전체 살피기 ----
    static func openCheck(_ k: Kos, _ meName: String) -> [String] {
        let t = k.s("opening")
        var miss: [String] = []
        if !has(t, "협회|센터|소속") { miss.append("소속") }
        if !(!meName.isEmpty && t.contains(meName)) && !has(t, "입니다\\s*$|이라고 합니다|저는") { miss.append("현장영상해설사 이름") }
        if !k.s("place").isEmpty && !t.contains(k.s("place")) { miss.append("장소 이름") }
        if !has(t, "안녕|반갑|모시|인사") { miss.append("인사말") }
        if !has(t, "코스|곳을 돌|순서로|동선") { miss.append("오늘의 동선 고지(4번)") }
        if !has(t, "시간|분쯤|분 정도|소요") { miss.append("전체 걸리는 시간(4번)") }
        return miss
    }
    static func closeCheck(_ k: Kos, _ meName: String) -> [String] {
        let t = k.s("closing")
        var miss: [String] = []
        if !has(t, "오늘|지금까지|살펴본|함께한|돌아본") { miss.append("오늘 내용 정리") }
        if !(!meName.isEmpty && t.contains(meName)) { miss.append("현장영상해설사 이름") }
        if !has(t, "고맙|감사|안녕|살펴 가|건강") { miss.append("마무리 인사") }
        if !has(t, "협회|센터|소속") { miss.append("소속 다시 밝히기(10번)") }
        if !has(t, "소감|어떠셨|말씀해 주|물어|여쭈") { miss.append("소감 여쭙기(10번)") }
        return miss
    }
    static func headMissing(_ k: Kos) -> [String] {
        var m: [String] = []
        if bin(k.s("first")) { m.append("1번 첫 만남 인사말") }
        if bin(k.s("history")) { m.append("3번 장소 역사") }
        let ac = k.s("access").trimmingCharacters(in: .whitespacesAndNewlines)
        if ac.isEmpty { m.append("3번 위치와 찾아오는 길") }
        else {
            if !has(ac, "전철|지하철|열차") { m.append("3번 전철 안내") }
            if !has(ac, "버스") { m.append("3번 버스 안내") }
            if !has(ac, "택시") { m.append("3번 택시 안내") }
            if !has(ac, "\\d+시 방향") { m.append("3번 출구에서 오는 길을 시계 방향으로") }
        }
        if bin(k.s("scale")) { m.append("3번 규모와 형태") }
        if bin(k.s("points")) { m.append("3번 전체 포인트") }
        if bin(k.s("tactile")) { m.append("5번 촉각지도 안내") }
        if bin(k.s("safety")) { m.append("6번 출발 전 안전 고지") }
        if bin(k.s("refs")) { m.append("16번 참고문헌") }
        return m
    }

    struct Iss {
        var open: [String] = [], close: [String] = [], head: [String] = []
        var noDir: [Int] = [], noText: [Int] = [], overLen: [Int] = [], banned: [Int] = []
        var lr: [Int] = [], touch: [Int] = [], bun: [Int] = [], clock: [Int] = [], noStay: [Int] = [], noGpos: [Int] = []
        var noTo: [String] = []
    }
    static func scriptIssues(_ k: Kos, _ meName: String) -> Iss {
        var o = Iss()
        o.open = openCheck(k, meName); o.close = closeCheck(k, meName); o.head = headMissing(k)
        for (i, s) in k.spots.enumerated() {
            let n = i + 1
            if (s.n("stay") ?? 0) <= 0 { o.noStay.append(n) }
            if bin(s.s("gpos")) { o.noGpos.append(n) }
            let t = s.s("text").trimmingCharacters(in: .whitespacesAndNewlines)
            if t.isEmpty { o.noText.append(n); continue }
            if !hasDirection(t) { o.noDir.append(n) }
            if !findBanned(t).isEmpty { o.banned.append(n) }
            if !findLR(t).isEmpty { o.lr.append(n) }
            if findTouchWord(t) { o.touch.append(n) }
            if findBunmyeon(t) { o.bun.append(n) }
            if !clockOrderBad(t).isEmpty { o.clock.append(n) }
            if let st = s.n("stay"), st > 0, Double(k.readSec(t)) > st * 60 { o.overLen.append(n) }
        }
        o.noTo = k.allSpots.filter { bin($0.s("to")) }.map { k.spotLab($0) }
        for key in ["history", "access", "points", "tactile", "safety"] {
            let t = k.s(key); if t.isEmpty { continue }
            if !findBanned(t).isEmpty && !o.head.contains("앞머리에 시각 전제 표현") { o.head.append("앞머리에 시각 전제 표현") }
            if !findLR(t).isEmpty && !o.head.contains("앞머리에 상대 방향 표현") { o.head.append("앞머리에 상대 방향 표현") }
            if !clockOrderBad(t).isEmpty && !o.head.contains("앞머리에 시계 방향 차례 어긋남") { o.head.append("앞머리에 시계 방향 차례 어긋남") }
        }
        return o
    }
    static func ji(_ a: [Int]) -> String { a.map(String.init).joined(separator: "지점, ") + "지점" }
    static func issueLines(_ x: Iss) -> [String] {
        var L: [String] = []
        if !x.head.isEmpty { L.append("앞머리에 빠진 것: " + x.head.joined(separator: ", ")) }
        if !x.open.isEmpty { L.append("오프닝에 빠진 것: " + x.open.joined(separator: ", ")) }
        if !x.close.isEmpty { L.append("클로징에 빠진 것: " + x.close.joined(separator: ", ")) }
        if !x.noText.isEmpty { L.append("대본이 빈 지점: " + ji(x.noText)) }
        if !x.noStay.isEmpty { L.append("코스별 소요시간이 없는 지점(4번): " + ji(x.noStay)) }
        if !x.noTo.isEmpty { L.append("어디까지를 안 적은 지점(7번): " + x.noTo.joined(separator: ", ")) }
        if !x.noGpos.isEmpty { L.append("현장영상해설사 위치를 안 적은 지점(8번): " + ji(x.noGpos)) }
        if !x.noDir.isEmpty { L.append("방향을 짚지 않은 지점(11번): " + ji(x.noDir)) }
        if !x.lr.isEmpty { L.append("상대 방향 표현이 든 지점(11번): " + ji(x.lr)) }
        if !x.clock.isEmpty { L.append("시계 방향 차례가 어긋난 지점(11번): " + ji(x.clock)) }
        if !x.bun.isEmpty { L.append("분면이라는 말이 든 지점(9번): " + ji(x.bun)) }
        if !x.touch.isEmpty { L.append("더듬다가 든 지점(13번): " + ji(x.touch)) }
        if !x.banned.isEmpty { L.append("시각 전제 표현이 든 지점(12번): " + ji(x.banned)) }
        if !x.overLen.isEmpty { L.append("머무는 시간보다 긴 지점: " + ji(x.overLen)) }
        return L
    }
    /// 사람이 읽어야 아는 것 — 기계는 판정하지 못하므로 여쭙기만
    static let askLines = [
        "9번 — 전체에서 부분, 다시 세부로 가는 차례로 쓰셨습니까.",
        "8번 — 참여자와 마주보고 서지 않으셨습니까. 걸을 때는 참여자의 11시에서 1시 방향 반 보 앞, 멈춰 현장영상해설할 때는 참여자를 12시에 세우고 현장영상해설사는 3시나 9시에 섭니다.",
        "9번 — 얼굴이나 동물처럼 둥근 것을 억지로 넷으로 쪼개지 않으셨습니까. 전체 인상을 먼저 말하셨습니까.",
        "14번 — 소리를 소리로만 적지 않고 그 소리가 나는 까닭과 형편을 이어 적으셨습니까.",
        "15번 — 색을 이름만 말하지 않고 온도감과 밝기와 변화까지 적으셨습니까."
    ]
    /// 원칙 16개 점검표(통합본) — 줄과 표(ok·bad·person)
    static func principleTable(_ k: Kos, _ x: Iss) -> [(String, String)] {
        let spots = k.spots, head = x.head
        func headHas(_ p: String) -> [String] { head.filter { $0.hasPrefix(p) } }
        var out: [(String, String)] = []
        func line(_ n: Int, _ name: String, _ st: String, _ code: String? = nil) { out.append(("\(n)번 \(name): \(st)", code ?? (st == "됨" ? "ok" : "bad"))) }
        let PERSON = "사람이 읽어 판단 (기계는 못 봄)"
        line(1, "참여자 첫 만남", headHas("1번").isEmpty ? "됨" : "빠짐")
        let opn = k.s("opening").trimmingCharacters(in: .whitespacesAndNewlines)
        let o2 = x.open.filter { !$0.contains("(4번)") }
        line(2, "오프닝 인사", opn.isEmpty ? "없음" : (o2.isEmpty ? "됨" : "빠진 것: " + o2.joined(separator: ", ")))
        let h3 = headHas("3번")
        line(3, "장소 소개 (역사·위치와 접근 교통편·규모와 형태·전체 포인트)", h3.isEmpty ? "됨" : "빠진 것: " + h3.map { $0.replacingOccurrences(of: "3번 ", with: "") }.joined(separator: ", "))
        let o4 = x.open.filter { $0.contains("(4번)") }
        var t4: [String] = []
        if opn.isEmpty { t4.append("오프닝 없음") } else if !o4.isEmpty { t4.append("오프닝에 빠진 것: " + o4.map { $0.replacingOccurrences(of: "(4번)", with: "") }.joined(separator: ", ")) }
        if !x.noStay.isEmpty { t4.append("정지 현장영상해설 시간이 없는 지점 " + ji(x.noStay)) }
        line(4, "오늘의 동선과 코스별 소요시간", spots.isEmpty ? "지점 없음" : (t4.isEmpty ? "됨" : t4.joined(separator: ". ")))
        line(5, "촉각지도 및 손바닥 지도 안내", headHas("5번").isEmpty ? "됨" : "빠짐")
        line(6, "출발 전 안전 고지", headHas("6번").isEmpty ? "됨" : "빠짐")
        line(7, "지점 경계와 코스별 소요시간", x.noTo.isEmpty ? "됨" : "어디까지를 안 적은 곳 " + x.noTo.joined(separator: ", "))
        line(8, "현장영상해설사 위치 및 현장영상해설 순서", (x.noGpos.isEmpty ? "적음. " : "위치를 안 적은 지점 " + ji(x.noGpos) + ". ") + "마주 서지 않았는지는 " + PERSON, x.noGpos.isEmpty ? "person" : "bad")
        line(9, "동선별 현장영상해설과 대상물 관찰 순서", (x.bun.isEmpty ? "\"분면\" 말 없음. " : "\"분면\"이라는 말이 든 지점 " + ji(x.bun) + ". ") + "전체에서 부분으로 갔는지, 둥근 것은 전체 인상을 먼저 말했는지는 " + PERSON, x.bun.isEmpty ? "person" : "bad")
        let cls = k.s("closing").trimmingCharacters(in: .whitespacesAndNewlines)
        line(10, "클로징", cls.isEmpty ? "없음" : (x.close.isEmpty ? "됨" : "빠진 것: " + x.close.joined(separator: ", ")))
        var dir: [String] = []
        if !x.noDir.isEmpty { dir.append("방향을 짚지 않은 지점 " + ji(x.noDir)) }
        if !x.lr.isEmpty { dir.append("왼쪽·오른쪽 같은 상대 방향이 든 지점 " + ji(x.lr)) }
        if !x.clock.isEmpty { dir.append("12시부터 차례가 어긋난 지점 " + ji(x.clock)) }
        if head.contains("앞머리에 상대 방향 표현") { dir.append("앞머리에 상대 방향 표현") }
        if head.contains("앞머리에 시계 방향 차례 어긋남") { dir.append("앞머리에 시계 방향 차례 어긋남") }
        line(11, "시계 방향 표기", dir.isEmpty ? "됨" : dir.joined(separator: ". "))
        var b12 = x.banned.map { "\($0)지점" }; if head.contains("앞머리에 시각 전제 표현") { b12.insert("앞머리", at: 0) }
        line(12, "시각 전제 표현 절대 금지", b12.isEmpty ? "됨" : "든 곳: " + b12.joined(separator: ", "))
        let touched = spots.filter { has($0.s("text"), "만져\\s*보") }.count
        line(13, "촉각 체험 안내", (x.touch.isEmpty ? "" : "\"더듬다\"가 든 지점 " + ji(x.touch) + ". ") + "\"만져보시면\" 안내가 있는 지점 \(touched)곳" + (touched > 0 ? "" : " (한 곳도 없음)"), (!x.touch.isEmpty || touched == 0) ? "bad" : "ok")
        let snd = spots.filter { has($0.s("text"), "소리|들리|들려|울리|지저귀") }.count
        line(14, "소리 현장영상해설", "소리 낱말이 든 지점 \(snd)곳. 소리의 원인과 상황을 이었는지는 " + PERSON, "person")
        let col = spots.filter { has($0.s("text"), "빨간|붉|파란|푸른|노란|노랗|초록|검은|검정|하얀|흰|회색|갈색|보라|주황|분홍|금빛|은빛|색") }.count
        line(15, "색상 표현", "색 낱말이 든 지점 \(col)곳. 온도감·명도·채도·변화·반영까지 적었는지는 " + PERSON, "person")
        let refs = k.s("refs").trimmingCharacters(in: .whitespacesAndNewlines)
        var r16: [String] = []
        if refs.isEmpty { r16.append("없음") }
        else {
            if has(refs, "https?://|www\\.|\\.go\\.kr|\\.or\\.kr|\\.com|\\.net") && !has(refs, "접속|열람|확인|20\\d\\d[.\\-년 ]") { r16.append("누리집 자료에 접속일이 없음") }
            if !has(refs, "\\d") { r16.append("쪽수나 항목 번호가 없음") }
        }
        line(16, "참고문헌 기록", r16.isEmpty ? "됨" : r16.joined(separator: ", "))
        return out
    }
    static func summary(_ t: [(String, String)]) -> (ok: Int, bad: Int, person: Int) {
        (t.filter { $0.1 == "ok" }.count, t.filter { $0.1 == "bad" }.count, t.filter { $0.1 == "person" }.count)
    }
    /// 다음 할 일 — 웹 nextTodo
    static func nextTodo(_ k: Kos, _ meName: String) -> String {
        let s = k.spots
        if k.s("place").isEmpty || k.s("meet").isEmpty { return "장소와 집합 자리를 적어 주십시오." }
        if s.isEmpty { return "현장에서 지점을 잡아 주십시오. 아직 한 곳도 없습니다." }
        let noShot = s.filter { $0.shots.isEmpty }.count
        if noShot > 0 { return "지점 \(s.count)곳 가운데 \(noShot)곳에 답사 사진이 없습니다." }
        let noStay = s.filter { ($0.n("stay") ?? 0) <= 0 }.count
        if noStay > 0 { return "지점 \(s.count)곳 가운데 \(noStay)곳에 머무는 시간이 없습니다." }
        let noTo = k.allSpots.filter { bin($0.s("to")) }
        if !noTo.isEmpty { return "어디까지를 안 적은 지점이 \(noTo.count)곳 있습니다(" + noTo.prefix(3).map { k.spotLab($0) }.joined(separator: ", ") + (noTo.count > 3 ? " 등" : "") + "). 지점 목록에서 열어 적어 주십시오." }
        if bin(k.s("opening")) { return "오프닝 인사말을 써 주십시오. 소속과 이름, 장소와 목적이 들어갑니다." }
        let noText = s.filter { bin($0.s("text")) }.count
        if noText > 0 { return "지점 \(s.count)곳 가운데 \(noText)곳에 대본이 비어 있습니다." }
        if bin(k.s("closing")) { return "클로징 인사말을 써 주십시오." }
        let L = issueLines(scriptIssues(k, meName))
        if let f = L.first { return "대본에서 살펴볼 것이 있습니다. " + f }
        if !((k.d["practiced"] as? Bool) ?? false) { return "한 번 읽어 보시면서 시간을 재 보십시오." }
        if !((k.d["exported"] as? Bool) ?? false) { return "대본집과 점자 대본을 만들면 코스가 다 됩니다." }
        return "이 코스는 다 되었습니다."
    }
    static func finalBadge(_ f: [String: Any]?) -> String {
        guard let f else { return "" }
        let v = Int(sutja(f["ver"]) ?? 0)
        switch geulja(f["status"]) {
        case "approved": return "협회 승인 완료 · 승인번호 " + geulja(f["no"]) + " (\(v)판)"
        case "pending": return "협회 승인 기다리는 중 (\(v)판, 협회 DB에 저장됨)"
        case "returned": return "협회에서 돌려보냄 (\(v)판) — " + geulja(f["reason"])
        default: return ""
        }
    }
    static func finalStatus(_ f: [String: Any]?) -> String {
        guard let f else { return "아직 협회에 승인을 요청하지 않았습니다." }
        let v = Int(sutja(f["ver"]) ?? 0)
        let at = String(geulja(f["at"]).replacingOccurrences(of: "T", with: " ").prefix(16))
        switch geulja(f["status"]) {
        case "pending": return "협회 DB에 저장되어 승인을 기다리고 있습니다. \(v)판, \(at). 코스를 더 고치셨으면 새 판으로 다시 요청하실 수 있습니다."
        case "approved": return "협회 승인 완료. 승인번호 " + geulja(f["no"]) + ", \(v)판, \(at). 현장영상해설 화면에서 쓰실 수 있습니다. 코스를 더 고치셨으면 새 판으로 다시 요청하십시오."
        case "returned": return "협회에서 돌려보냈습니다 (\(v)판). 까닭: " + geulja(f["reason"]) + " — 고치신 뒤 다시 요청해 주십시오."
        default: return "아직 협회에 승인을 요청하지 않았습니다."
        }
    }

    // ---- 테마투어 ----
    static func isTheme(_ k: Kos) -> Bool { k.s("field").contains("테마투어") }
    static func ttLines(_ s: Spot) -> [String] {
        guard let tt = s.d["tt"] as? [String: Any] else { return [] }
        return TTKAN.compactMap { kk in let v = geulja(tt[kk.id]).trimmingCharacters(in: .whitespacesAndNewlines); return v.isEmpty ? nil : kk.nm + ": " + v }
    }

    // ---- 내보내기 (웹 guideText·noticeText·planText·mapJson 과 같은 글) ----
    static func guideText(_ k: Kos) -> String {
        var L: [String] = []
        L.append("[" + k.name + "] 현장영상해설사용 대본집")
        L.append("장소: " + k.s("place") + " / 집합: " + k.s("meet"))
        L.append("참가 \(Int(sutja(k.d["people"]) ?? 0))명 / 현장영상해설사 \(Int(sutja(k.d["guides"]) ?? 1))명")
        L.append(RULE_TXT); L.append("")
        for (t, key) in [("장소 소개 — 역사 (3번)", "history"), ("장소 소개 — 위치와 접근 교통편 (3번)", "access"), ("장소 소개 — 전체 포인트 (3번)", "points"), ("촉각지도·손바닥 지도 안내 (5번)", "tactile"), ("출발 전 안전 고지 (6번)", "safety")] {
            L.append("== " + t + " =="); L.append(k.s(key).isEmpty ? "(비어 있음)" : k.s(key)); L.append("")
        }
        for s in k.allSpots {
            L.append("== " + k.spotFull(s) + " — " + k.spotMinTxt(s) + " ==")
            L.append("[어디서부터] " + k.spotFrom(s))
            L.append("[어디까지] " + (bin(s.s("to")) ? "(아직 적지 않음)" : s.s("to").trimmingCharacters(in: .whitespacesAndNewlines)))
            if !s.s("access").isEmpty { L.append("[이동 구간 안내] " + s.s("access")) }
            if !s.s("touch").isEmpty { L.append("[만져 볼 것] " + s.s("touch")) }
            if !s.s("safe").isEmpty { L.append("[조심할 것] " + s.s("safe")) }
            if !s.s("gpos").isEmpty { L.append("[현장영상해설사 위치] " + s.s("gpos")) }
            for x in ttLines(s) { if let r = x.range(of: ": ") { L.append("[" + x.replacingCharacters(in: r, with: "] ")) } }
            let t = k.spotText(s); L.append(t.isEmpty ? "(대본 비어 있음)" : t); L.append("")
        }
        L.append("== 참고문헌 (16번) =="); L.append(k.s("refs").isEmpty ? "(비어 있음)" : k.s("refs")); L.append("")
        L.append("== 자료 출처 표시 ==")
        if has(k.s("history") + k.s("refs"), "국가유산청|관광공사") { L.append("장소 지식 자료: 국가유산청 국가유산포털(www.khs.go.kr)·한국관광공사 관광정보(api.visitkorea.or.kr), 공공누리 제1유형(출처 표시). 국가유산청 음성 해설은 참고용으로만 들었고 대본에 옮겨 적지 않았습니다.") }
        if k.d["pos"] != nil || k.spots.contains(where: { $0.lat != nil }) { L.append("지도·위치: 오픈스트리트맵 기여자(ODbL, openstreetmap.org/copyright), 장소 찾기 노미나팀.") }
        L.append("낱말 뜻을 찾아 쓴 곳이 있으면: 국립국어원 한국어기초사전(CC BY-SA)·표준국어대사전.")
        return L.joined(separator: "\n")
    }
    static func noticeText(_ k: Kos) -> String {
        var L: [String] = []
        L.append("[" + k.name + "] 참가자 안내"); L.append("")
        L.append("장소: " + k.s("place")); L.append("집합 장소: " + k.s("meet"))
        L.append("걸리는 시간: 모두 " + fmtMin(k.courseMin()) + " 쯤")
        L.append("오프닝 지점, 지점 \(k.spots.count)곳, 클로징 지점을 차례로 걸으며 현장영상해설을 듣습니다.")
        if !k.s("note").isEmpty { L.append(""); L.append(k.s("note")) }
        L.append(""); L.append("들르는 자리")
        for s in k.allSpots { L.append("- " + k.spotFull(s) + " — " + k.spotMinTxt(s)) }
        let touch = k.spots.filter { !bin($0.s("touch")) }
        if !touch.isEmpty { L.append(""); L.append("만져 보실 수 있는 것"); for s in touch { L.append("- " + s.name + ": " + s.s("touch")) } }
        if !k.s("rain").isEmpty { L.append(""); L.append("비가 오면: " + k.s("rain")) }
        L.append(""); L.append("문의: 사단법인 한국시각장애인현장영상해설협회")
        return L.joined(separator: "\n")
    }
    static func planText(_ k: Kos) -> String {
        var L: [String] = []
        L.append("[" + k.name + "] 파견 운영표")
        L.append("장소 " + k.s("place") + " / 집합 " + k.s("meet") + " / 참가 \(Int(sutja(k.d["people"]) ?? 0))명 / 현장영상해설사 \(Int(sutja(k.d["guides"]) ?? 1))명")
        L.append(""); L.append("지점\t어디서부터\t어디까지\t이동\t정지 현장영상해설\t이동 구간 안내\t조심할 것")
        func one(_ t: String) -> String { t.replacingOccurrences(of: "\n", with: " ") }
        for s in k.allSpots {
            L.append(k.spotFull(s) + "\t" + one(k.spotFrom(s)) + "\t" + one(s.s("to")) + "\t\(Int(s.n("walkIn") ?? 0))분\t\(Int(k.stayOf(s).rounded()))분\t" + one(s.s("access")) + "\t" + one(s.s("safe")))
        }
        L.append(""); L.append("모두 " + fmtMin(k.courseMin()))
        if !k.s("rain").isEmpty { L.append(""); L.append("비 올 때 바꿀 동선: " + k.s("rain")) }
        return L.joined(separator: "\n")
    }
    static func mapJson(_ k: Kos) -> String {
        let o: [String: Any] = ["name": k.name, "place": k.s("place"), "field": k.s("field"), "meet": k.s("meet"), "note": k.s("note"),
            "minutes": Int(k.courseMin().rounded()),
            "spots": k.spots.enumerated().map { (i, s) -> [String: Any] in
                ["n": i + 1, "name": s.name, "lat": s.lat.map { $0 as Any } ?? NSNull(), "lng": s.lng.map { $0 as Any } ?? NSNull(), "stay": Int(s.n("stay") ?? 0), "access": s.s("access"), "touch": s.s("touch")] }]
        guard let d = try? JSONSerialization.data(withJSONObject: o, options: [.prettyPrinted]) else { return "{}" }
        return String(data: d, encoding: .utf8) ?? "{}"
    }
    /// 전체 시간표 — 웹 renderTimeSum
    static func timeSum(_ k: Kos) -> [String] {
        var stay = 0.0, move = 0.0, read = 0.0
        for s in k.allSpots {
            stay += k.stayOf(s); move += s.n("walkIn") ?? 0
            if s.kind.isEmpty { read += (s.n("real") ?? Double(k.readSec(s.s("text")))) / 60 }
        }
        var L = ["정지 현장영상해설 시간 (오프닝·클로징 포함): " + fmtMin(stay), "이동 시간: " + fmtMin(move), "현장영상해설 읽는 시간(어림): " + fmtMin(read), "모두: " + fmtMin(stay + move)]
        L += k.allSpots.map { k.spotFull($0) + ": " + k.spotMinTxt($0) }
        return L
    }

    // ---- 방향과 거리 (평면도·답사 지도·벗어남·따라가기) ----
    static func brg(_ a: (Double, Double), _ b: (Double, Double)) -> Double {
        let r = Double.pi / 180
        let y = sin((b.1 - a.1) * r) * cos(b.0 * r)
        let x = cos(a.0 * r) * sin(b.0 * r) - sin(a.0 * r) * cos(b.0 * r) * cos((b.1 - a.1) * r)
        return (atan2(y, x) * 180 / Double.pi + 360).truncatingRemainder(dividingBy: 360)
    }
    static func meter(_ a: (Double, Double), _ b: (Double, Double)) -> Int { Int(metersBetween(a, b) ?? 0) }
    static func clock(_ deg: Double) -> String { let c = Int((deg / 30).rounded()) % 12; return "\(c == 0 ? 12 : c)시" }
    static func pos(_ k: Kos) -> (Double, Double)? {
        guard let p = k.d["pos"] as? [String: Any], let la = sutja(p["la"]), let lo = sutja(p["lo"]) else { return nil }
        return (la, lo)
    }
    static func spotPts(_ k: Kos) -> [(i: Int, s: Spot, p: (Double, Double))] {
        k.spots.enumerated().compactMap { (i, s) in
            guard let la = s.lat, let lo = s.lng else { return nil }
            return (i, s, (la, lo))
        }
    }
    /// 장소 평면도 글 — 웹 pmText
    static func pmText(_ k: Kos) -> String {
        let sp = spotPts(k)
        var c = pos(k); var nm0 = k.s("posName")
        if c == nil, let f = sp.first { c = f.p; nm0 = f.s.name }
        guard let c else { return "아직 자리를 모릅니다. 장소 자리 찾기나 지금 자리로 잡기를 눌러 주십시오. 장소 지식 들이기로 뼈대를 짜면 지점 위치가 함께 들어옵니다." }
        let lines = sp.map { p -> String in let m = meter(c, p.p); return "\(p.i + 1)번 \(p.s.name) — " + (m < 3 ? "기준 자리" : clock(brg(c, p.p)) + " 방향 \(m)미터") }
        var warn: [String] = []
        if sp.count >= 3 {
            for i in 2..<sp.count {
                let a = sp[i - 2], b = sp[i - 1], d = sp[i]
                if meter(b.p, d.p) < 15 { continue }
                let turn = abs(((brg(b.p, d.p) - brg(a.p, b.p)) + 540).truncatingRemainder(dividingBy: 360) - 180)
                if turn > 150 { warn.append("\(b.i + 1)번 \(b.s.name)에서 \(d.i + 1)번 \(d.s.name)(으)로 가는 길은 왔던 방향으로 되돌아갑니다.") }
            }
        }
        let nm = nm0.isEmpty ? (k.s("place").isEmpty ? k.name : k.s("place")) : nm0
        return "북쪽이 위이고 12시입니다. 기준 자리: " + nm + String(format: " (위도 %.5f, 경도 %.5f).", c.0, c.1) +
            (lines.isEmpty ? " 아직 위치가 적힌 지점이 없습니다." : " " + lines.joined(separator: " / ")) +
            (warn.isEmpty ? "" : " ★ " + warn.joined(separator: " ") + " 지점 차례는 지점 한 화면의 한 칸 앞으로·뒤로로 고치실 수 있습니다.") + " (지도: 오픈스트리트맵 기여자, ODbL. 장소 찾기: 노미나팀)"
    }
    /// 답사 지도 글 — 웹 drawSurveyMap 의 글
    static func surveyText(_ k: Kos) -> [String] {
        k.spots.enumerated().map { (i, s) in
            let dirs = s.shots.map { geulja($0["dir"]) }.filter { !$0.isEmpty }
            return "\(i + 1)번 \(s.name) — " + (s.lat != nil ? "위치 있음" : "위치 없음") + (dirs.isEmpty ? ", 사진 없음" : ", 사진 " + dirs.joined(separator: "·"))
        }
    }
    /// 코스 선에서 떨어진 거리 — 웹 offDist
    static func offDist(_ me: (Double, Double), _ pts: [(Double, Double)]) -> (d: Int, near: Int?) {
        var best = 1e9, near: Int? = nil
        let kx = 111320 * cos(me.0 * Double.pi / 180), ky = 110540.0
        func xy(_ p: (Double, Double)) -> (Double, Double) { ((p.1 - me.1) * kx, (p.0 - me.0) * ky) }
        for (i, p) in pts.enumerated() { let d = Double(meter(me, p)); if d < best { best = d; near = i } }
        if pts.count > 1 {
            for j in 1..<pts.count {
                let a = xy(pts[j - 1]), b = xy(pts[j]); let dx = b.0 - a.0, dy = b.1 - a.1, L2 = dx * dx + dy * dy
                if L2 == 0 { continue }
                let t = max(0, min(1, -(a.0 * dx + a.1 * dy) / L2))
                let px = a.0 + t * dx, py = a.1 + t * dy
                let d2 = (px * px + py * py).squareRoot(); if d2 < best { best = d2 }
            }
        }
        return (Int(best.rounded()), near)
    }
    /// 위치로 걷는 시간 다시 어림 — 웹 calcWalk
    static func calcWalk(_ k: Kos) -> String {
        let a = k.allSpots; var done = 0, miss = 0
        if a.count > 1 {
            for i in 1..<a.count {
                guard let m = metersBetween(k.posOf(a[i - 1]), k.posOf(a[i])) else { miss += 1; continue }
                a[i].d["distIn"] = m; a[i].d["walkIn"] = k.walkMinutes(m); done += 1
            }
        }
        k.syncMove()
        return done > 0 ? "\(done)곳의 이동 시간(앞 지점을 떠나 그 지점까지)을 위치로 어림했습니다." + (miss > 0 ? " 위치가 없는 \(miss)곳은 그대로 두었습니다." : "")
            : "위치가 적힌 지점이 모자라 어림하지 못했습니다. 현장에서 사진을 담으시면 그 지점 위치가 함께 적힙니다."
    }

    // ---- 사진 ----
    /// 밝기와 또렷함 — 웹 photoQuality 와 같은 잣대(가로 80점으로 줄여 잼)
    static func photoQuality(_ im: UIImage) -> [String: Any] {
        guard let cg = im.cgImage else { return ["bright": -1, "sharp": -1, "warn": ""] }
        let w = 80, h = max(1, Int(Double(cg.height) * 80.0 / Double(max(1, cg.width))))
        var px = [UInt8](repeating: 0, count: w * h * 4)
        guard let cx = CGContext(data: &px, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return ["bright": -1, "sharp": -1, "warn": ""] }
        cx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        var g = [Double](repeating: 0, count: w * h); var sum = 0.0
        for k in 0..<(w * h) { let v = 0.299 * Double(px[k * 4]) + 0.587 * Double(px[k * 4 + 1]) + 0.114 * Double(px[k * 4 + 2]); g[k] = v; sum += v }
        let bright = sum / Double(w * h)
        var lap = 0.0, n = 0.0
        if h > 2 && w > 2 {
            for y in 1..<(h - 1) { for x in 1..<(w - 1) {
                let c = g[y * w + x]
                let l = abs(4 * c - g[y * w + x - 1] - g[y * w + x + 1] - g[(y - 1) * w + x] - g[(y + 1) * w + x])
                lap += l * l; n += 1 } }
        }
        let sharp = n > 0 ? (lap / n).squareRoot() : 0
        var warn = ""
        if bright < 45 { warn = "너무 어둡습니다." } else if bright > 225 { warn = "너무 밝아 하얗게 날아갔습니다." } else if sharp < 6 { warn = "흐릿합니다. 폰을 잠시 멈추고 다시 담아 주십시오." }
        return ["bright": Int(bright.rounded()), "sharp": (sharp * 10).rounded() / 10, "warn": warn]
    }
    /// 담는 모양 — 원본이 아니면 가운데를 그 비율로 잘라 냄(넓이가 늘지는 않음)
    static let MOYANG: [(String, String)] = [("won", "원본 그대로"), ("garo", "가로 (16대9)"), ("sero", "세로 (9대16)"), ("jeong", "정사각")]
    static var moyang: String { UserDefaults.standard.string(forKey: "damneunMoyang") ?? "won" }
    static func jareugi(_ im: UIImage) -> UIImage {
        let mo = moyang
        guard mo != "won" else { return im }
        let up = im.barojaba()
        guard let cg = up.cgImage else { return im }
        let w = Double(cg.width), h = Double(cg.height)
        let bi = mo == "garo" ? 16.0 / 9.0 : (mo == "sero" ? 9.0 / 16.0 : 1.0)
        var sw = w, sh = h
        if sw / sh > bi { sw = sh * bi } else { sh = sw / bi }
        let r = CGRect(x: (w - sw) / 2, y: (h - sh) / 2, width: sw, height: sh).integral
        guard let c2 = cg.cropping(to: r) else { return im }
        return UIImage(cgImage: c2, scale: 1, orientation: .up)
    }
    static func moyangMal(_ im: UIImage) -> String {
        let w = Int(im.size.width * im.scale), h = Int(im.size.height * im.scale)
        return (w > h ? "가로" : (w < h ? "세로" : "정사각")) + " 사진으로 담겼습니다. \(w) 곱하기 \(h)입니다."
    }
}

extension UIImage {
    /// 폰이 적어 둔 돌림을 그림에 실제로 반영(자르기 전에)
    func barojaba() -> UIImage {
        if imageOrientation == .up { return self }
        let f = UIGraphicsImageRendererFormat(); f.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: size.width * scale, height: size.height * scale), format: f).image { _ in
            draw(in: CGRect(x: 0, y: 0, width: size.width * scale, height: size.height * scale))
        }
    }
}
