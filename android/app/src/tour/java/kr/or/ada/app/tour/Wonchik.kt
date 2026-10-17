// LVD투어스튜디오 안드로이드 — 원칙 점검·내보내기·셈 (1.2.0판, 빌드 261017-T4, 방클) — 아이폰 Tour/Wonchik.swift 와 같은 줄
// 웹 lvdts.html 의 같은 이름 함수를 그대로 옮김: findLR·findTouchWord·findBunmyeon·clockOrderBad·hasDirection·textFb·
//   openCheck·closeCheck·headMissing·scriptIssues·issueLines·askLines·principleTable·nextTodo·
//   guideText·noticeText·planText·mapJson·renderTimeSum·pmText·offDist·photoQuality·calcWalk·SEEDS·TTKAN·DIRWORDS
// 번호는 작성원칙 통합본 16개(2026-10-12 이사장님 확정)
package kr.or.ada.app.tour

import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Wonchik {
    val LRBAN = listOf("왼쪽", "오른쪽", "이쪽", "저쪽", "왼편", "오른편")
    val DIRWORDS = listOf("12시 방향", "1시 방향", "2시 방향", "3시 방향", "4시 방향", "5시 방향", "6시 방향",
        "7시 방향", "8시 방향", "9시 방향", "10시 방향", "11시 방향",
        "바로 앞", "뒤쪽", "왼쪽", "오른쪽", "발밑", "머리 위", "스무 걸음 앞", "팔을 뻗으면 닿는 자리")
    data class Kan(val id: String, val nm: String, val ex: String)
    val TTKAN = listOf(
        Kan("sori", "들리는 소리", "예: 오른쪽에서 두부 써는 칼 소리, 멀리 아이들 소리"),
        Kan("naem", "나는 냄새", "예: 참기름 볶는 냄새가 3시 방향에서 옵니다"),
        Kan("bal", "발밑 느낌", "예: 시멘트에서 흙으로 바뀝니다. 밟으면 자박자박합니다"),
        Kan("son", "손에 닿는 것", "예: 허리 높이 나무 난간, 결이 세로로 지나갑니다"),
        Kan("byeot", "볕과 바람", "예: 처마 아래로 들어서면 볕이 끊기고 서늘해집니다"),
        Kan("saram", "사람의 기척", "예: 좌우로 사람이 스쳐 지나갑니다. 말소리가 낮습니다"),
        Kan("gieok", "현장영상해설사의 기억·이야기", "예: 제가 어릴 때 이 골목에서"))
    data class Seed(val name: String, val place: String, val field: String, val meet: String, val note: String, val spots: List<String>)
    val SEEDS = listOf(
        Seed("경복궁 코스", "경복궁", "문화유산 분야", "광화문 앞 매표소 옆", "조선의 으뜸 궁궐을 흥례문에서 향원정까지 걸으며 현장영상해설하는 코스입니다.", listOf("흥례문 앞", "근정문", "근정전 앞마당", "사정전", "경회루 앞", "향원정", "국립민속박물관 앞")),
        Seed("창덕궁 코스", "창덕궁", "문화유산 분야", "돈화문 앞", "", listOf("돈화문", "인정전", "선정전", "낙선재")),
        Seed("덕수궁 코스", "덕수궁", "문화유산 분야", "대한문 앞", "", listOf("대한문", "중화전", "석조전 앞")),
        Seed("창경궁 코스", "창경궁", "문화유산 분야", "홍화문 앞", "", listOf("홍화문", "명정전", "춘당지")),
        Seed("종묘 코스", "종묘", "문화유산 분야", "종묘 정문 앞", "", listOf("외대문", "정전 앞", "영녕전")),
        Seed("태릉 코스", "태릉", "문화유산 분야", "태릉 매표소 앞", "", listOf("홍살문", "정자각", "능침 앞")),
        Seed("남산 코스", "남산", "자연·경관 분야", "남산케이블카 승강장 앞", "", listOf("케이블카 승강장", "팔각정", "전망대")),
        Seed("서울공예박물관 코스", "서울공예박물관", "박물관·전시 분야", "안내동 앞", "", listOf("안내동", "전시1동", "전시3동")),
        Seed("국립중앙박물관 코스", "국립중앙박물관", "박물관·전시 분야", "으뜸홀", "", listOf("으뜸홀", "선사고대관", "사유의 방")),
        Seed("항공박물관 코스", "국립항공박물관", "박물관·전시 분야", "1층 로비", "", listOf("1층 로비", "항공역사관", "항공산업관")))

    fun has(t: String, re: String) = Regex(re).containsMatchIn(t)
    fun bin(t: String) = t.isBlank()

    // ---- 글 한 덩이 살피기 ----
    fun findBanned(t: String): List<String> = Yeonseup.geumji(t)
    fun findLR(t: String): List<String> {
        val out = LRBAN.filter { t.contains(it) }.toMutableList()
        if (has(t, "[좌우]측(?!\\s*(상단|하단))")) out.add("좌측·우측")
        return out
    }
    fun findTouchWord(t: String) = t.contains("더듬")
    fun findBunmyeon(t: String) = t.contains("분면")
    fun clockOrderBad(t: String): List<String> {
        val bad = mutableListOf<String>()
        val re = Regex("(\\d{1,2})\\s*시\\s*방향")
        for (sen in t.split(Regex("[.!?\\n]"))) {
            val ms = re.findAll(sen).toList()
            val hs = ms.mapNotNull { m -> m.groupValues[1].toIntOrNull()?.takeIf { it in 1..12 }?.let { it % 12 } }
            if (hs.size < 2) continue
            val before0 = if (ms.isNotEmpty()) sen.substring(0, ms[0].range.first) else ""
            val had12 = has(before0, "12\\s*시")
            if (hs.size >= 3 && hs[0] != 0 && !had12) { bad.add("12시부터 시작하지 않음"); continue }
            for (i in 1 until hs.size) if (hs[i] < hs[i - 1]) { bad.add("차례가 되돌아감"); break }
        }
        return bad
    }
    /** 9번에 따라 왼쪽·오른쪽은 방향을 짚은 것으로 세지 않는다 */
    fun hasDirection(t: String) = has(t, "\\d+시 방향|바로 앞|발밑|머리 위|걸음|위로|아래로")

    /** 글을 치는 동안 알려 주는 한 줄 — 웹 textFb */
    fun textFb(k: Kos, t: String, roomMin: Double, wantDir: Boolean): String {
        val sec = k.readSec(t)
        val s = StringBuilder("${t.count { !it.isWhitespace() }}자, 읽는 데 ${sec}초쯤 걸립니다.")
        if (wantDir && !bin(t) && !hasDirection(t)) s.append(" 방향을 짚어 주십시오. 12시·3시·6시·9시 시계 방향으로요.")
        val lr = findLR(t); if (lr.isNotEmpty()) s.append(" 상대 방향 표현이 들었습니다(" + lr.joinToString(", ") + "). 시계 방향으로 바꾸십시오.")
        if (findTouchWord(t)) s.append(" 더듬다는 쓰지 않습니다. 만져보시면으로 바꾸십시오.")
        if (findBunmyeon(t)) s.append(" 분면이라는 말은 현장영상해설문에 내놓지 않습니다. 좌측 상단처럼 풀어 쓰십시오.")
        val co = clockOrderBad(t); if (co.isNotEmpty()) s.append(" 시계 방향 차례가 어긋납니다(" + co[0] + "). 12시에서 시작해 3시·6시·9시로 나아가야 합니다.")
        if (roomMin > 0) { val room = (roomMin * 60).toInt(); s.append(if (sec > room) " 머무는 시간(${roomMin.toInt()}분)보다 ${sec - room}초 깁니다." else " 머무는 시간 안에 들어갑니다.") }
        val bad = findBanned(t); if (bad.isNotEmpty()) s.append(" 금지어: " + bad.joinToString(", "))
        return s.toString()
    }

    // ---- 코스 전체 살피기 ----
    fun openCheck(k: Kos, meName: String): List<String> {
        val t = k.s("opening"); val miss = mutableListOf<String>()
        if (!has(t, "협회|센터|소속")) miss.add("소속")
        if (!(meName.isNotEmpty() && t.contains(meName)) && !has(t, "입니다\\s*$|이라고 합니다|저는")) miss.add("현장영상해설사 이름")
        if (k.s("place").isNotEmpty() && !t.contains(k.s("place"))) miss.add("장소 이름")
        if (!has(t, "안녕|반갑|모시|인사")) miss.add("인사말")
        if (!has(t, "코스|곳을 돌|순서로|동선")) miss.add("오늘의 동선 고지(4번)")
        if (!has(t, "시간|분쯤|분 정도|소요")) miss.add("전체 걸리는 시간(4번)")
        return miss
    }
    fun closeCheck(k: Kos, meName: String): List<String> {
        val t = k.s("closing"); val miss = mutableListOf<String>()
        if (!has(t, "오늘|지금까지|살펴본|함께한|돌아본")) miss.add("오늘 내용 정리")
        if (!(meName.isNotEmpty() && t.contains(meName))) miss.add("현장영상해설사 이름")
        if (!has(t, "고맙|감사|안녕|살펴 가|건강")) miss.add("마무리 인사")
        if (!has(t, "협회|센터|소속")) miss.add("소속 다시 밝히기(10번)")
        if (!has(t, "소감|어떠셨|말씀해 주|물어|여쭈")) miss.add("소감 여쭙기(10번)")
        return miss
    }
    fun headMissing(k: Kos): List<String> {
        val m = mutableListOf<String>()
        if (bin(k.s("first"))) m.add("1번 첫 만남 인사말")
        if (bin(k.s("history"))) m.add("3번 장소 역사")
        val ac = k.s("access").trim()
        if (ac.isEmpty()) m.add("3번 위치와 찾아오는 길")
        else {
            if (!has(ac, "전철|지하철|열차")) m.add("3번 전철 안내")
            if (!has(ac, "버스")) m.add("3번 버스 안내")
            if (!has(ac, "택시")) m.add("3번 택시 안내")
            if (!has(ac, "\\d+시 방향")) m.add("3번 출구에서 오는 길을 시계 방향으로")
        }
        if (bin(k.s("scale"))) m.add("3번 규모와 형태")
        if (bin(k.s("points"))) m.add("3번 전체 포인트")
        if (bin(k.s("tactile"))) m.add("5번 촉각지도 안내")
        if (bin(k.s("safety"))) m.add("6번 출발 전 안전 고지")
        if (bin(k.s("refs"))) m.add("16번 참고문헌")
        return m
    }

    class Iss {
        var open = listOf<String>(); var close = listOf<String>(); val head = mutableListOf<String>()
        val noDir = mutableListOf<Int>(); val noText = mutableListOf<Int>(); val overLen = mutableListOf<Int>(); val banned = mutableListOf<Int>()
        val lr = mutableListOf<Int>(); val touch = mutableListOf<Int>(); val bun = mutableListOf<Int>(); val clock = mutableListOf<Int>()
        val noStay = mutableListOf<Int>(); val noGpos = mutableListOf<Int>(); var noTo = listOf<String>()
    }
    fun scriptIssues(k: Kos, meName: String): Iss {
        val o = Iss()
        o.open = openCheck(k, meName); o.close = closeCheck(k, meName); o.head.addAll(headMissing(k))
        k.spots.forEachIndexed { i, s ->
            val n = i + 1
            if ((s.n("stay") ?: 0.0) <= 0) o.noStay.add(n)
            if (bin(s.s("gpos"))) o.noGpos.add(n)
            val t = s.s("text").trim()
            if (t.isEmpty()) { o.noText.add(n); return@forEachIndexed }
            if (!hasDirection(t)) o.noDir.add(n)
            if (findBanned(t).isNotEmpty()) o.banned.add(n)
            if (findLR(t).isNotEmpty()) o.lr.add(n)
            if (findTouchWord(t)) o.touch.add(n)
            if (findBunmyeon(t)) o.bun.add(n)
            if (clockOrderBad(t).isNotEmpty()) o.clock.add(n)
            val st = s.n("stay"); if (st != null && st > 0 && k.readSec(t) > st * 60) o.overLen.add(n)
        }
        o.noTo = k.allSpots.filter { bin(it.s("to")) }.map { k.spotLab(it) }
        for (key in listOf("history", "access", "points", "tactile", "safety")) {
            val t = k.s(key); if (t.isEmpty()) continue
            if (findBanned(t).isNotEmpty() && !o.head.contains("앞머리에 시각 전제 표현")) o.head.add("앞머리에 시각 전제 표현")
            if (findLR(t).isNotEmpty() && !o.head.contains("앞머리에 상대 방향 표현")) o.head.add("앞머리에 상대 방향 표현")
            if (clockOrderBad(t).isNotEmpty() && !o.head.contains("앞머리에 시계 방향 차례 어긋남")) o.head.add("앞머리에 시계 방향 차례 어긋남")
        }
        return o
    }
    fun ji(a: List<Int>) = a.joinToString("지점, ") + "지점"
    fun issueLines(x: Iss): List<String> {
        val L = mutableListOf<String>()
        if (x.head.isNotEmpty()) L.add("앞머리에 빠진 것: " + x.head.joinToString(", "))
        if (x.open.isNotEmpty()) L.add("오프닝에 빠진 것: " + x.open.joinToString(", "))
        if (x.close.isNotEmpty()) L.add("클로징에 빠진 것: " + x.close.joinToString(", "))
        if (x.noText.isNotEmpty()) L.add("대본이 빈 지점: " + ji(x.noText))
        if (x.noStay.isNotEmpty()) L.add("코스별 소요시간이 없는 지점(4번): " + ji(x.noStay))
        if (x.noTo.isNotEmpty()) L.add("어디까지를 안 적은 지점(7번): " + x.noTo.joinToString(", "))
        if (x.noGpos.isNotEmpty()) L.add("현장영상해설사 위치를 안 적은 지점(8번): " + ji(x.noGpos))
        if (x.noDir.isNotEmpty()) L.add("방향을 짚지 않은 지점(11번): " + ji(x.noDir))
        if (x.lr.isNotEmpty()) L.add("상대 방향 표현이 든 지점(11번): " + ji(x.lr))
        if (x.clock.isNotEmpty()) L.add("시계 방향 차례가 어긋난 지점(11번): " + ji(x.clock))
        if (x.bun.isNotEmpty()) L.add("분면이라는 말이 든 지점(9번): " + ji(x.bun))
        if (x.touch.isNotEmpty()) L.add("더듬다가 든 지점(13번): " + ji(x.touch))
        if (x.banned.isNotEmpty()) L.add("시각 전제 표현이 든 지점(12번): " + ji(x.banned))
        if (x.overLen.isNotEmpty()) L.add("머무는 시간보다 긴 지점: " + ji(x.overLen))
        return L
    }
    val askLines = listOf(
        "9번 — 전체에서 부분, 다시 세부로 가는 차례로 쓰셨습니까.",
        "8번 — 참여자와 마주보고 서지 않으셨습니까. 걸을 때는 참여자의 11시에서 1시 방향 반 보 앞, 멈춰 현장영상해설할 때는 참여자를 12시에 세우고 현장영상해설사는 3시나 9시에 섭니다.",
        "9번 — 얼굴이나 동물처럼 둥근 것을 억지로 넷으로 쪼개지 않으셨습니까. 전체 인상을 먼저 말하셨습니까.",
        "14번 — 소리를 소리로만 적지 않고 그 소리가 나는 까닭과 형편을 이어 적으셨습니까.",
        "15번 — 색을 이름만 말하지 않고 온도감과 밝기와 변화까지 적으셨습니까.")
    /** 원칙 16개 점검표(통합본) — 줄과 표(ok·bad·person) */
    fun principleTable(k: Kos, x: Iss): List<Pair<String, String>> {
        val spots = k.spots; val head = x.head
        fun headHas(p: String) = head.filter { it.startsWith(p) }
        val out = mutableListOf<Pair<String, String>>()
        fun line(n: Int, name: String, st: String, code: String? = null) { out.add("${n}번 $name: $st" to (code ?: if (st == "됨") "ok" else "bad")) }
        val PERSON = "사람이 읽어 판단 (기계는 못 봄)"
        line(1, "참여자 첫 만남", if (headHas("1번").isEmpty()) "됨" else "빠짐")
        val opn = k.s("opening").trim()
        val o2 = x.open.filter { !it.contains("(4번)") }
        line(2, "오프닝 인사", if (opn.isEmpty()) "없음" else if (o2.isEmpty()) "됨" else "빠진 것: " + o2.joinToString(", "))
        val h3 = headHas("3번")
        line(3, "장소 소개 (역사·위치와 접근 교통편·규모와 형태·전체 포인트)", if (h3.isEmpty()) "됨" else "빠진 것: " + h3.joinToString(", ") { it.replace("3번 ", "") })
        val o4 = x.open.filter { it.contains("(4번)") }
        val t4 = mutableListOf<String>()
        if (opn.isEmpty()) t4.add("오프닝 없음") else if (o4.isNotEmpty()) t4.add("오프닝에 빠진 것: " + o4.joinToString(", ") { it.replace("(4번)", "") })
        if (x.noStay.isNotEmpty()) t4.add("정지 현장영상해설 시간이 없는 지점 " + ji(x.noStay))
        line(4, "오늘의 동선과 코스별 소요시간", if (spots.isEmpty()) "지점 없음" else if (t4.isEmpty()) "됨" else t4.joinToString(". "))
        line(5, "촉각지도 및 손바닥 지도 안내", if (headHas("5번").isEmpty()) "됨" else "빠짐")
        line(6, "출발 전 안전 고지", if (headHas("6번").isEmpty()) "됨" else "빠짐")
        line(7, "지점 경계와 코스별 소요시간", if (x.noTo.isEmpty()) "됨" else "어디까지를 안 적은 곳 " + x.noTo.joinToString(", "))
        line(8, "현장영상해설사 위치 및 현장영상해설 순서", (if (x.noGpos.isEmpty()) "적음. " else "위치를 안 적은 지점 " + ji(x.noGpos) + ". ") + "마주 서지 않았는지는 " + PERSON, if (x.noGpos.isEmpty()) "person" else "bad")
        line(9, "동선별 현장영상해설과 대상물 관찰 순서", (if (x.bun.isEmpty()) "\"분면\" 말 없음. " else "\"분면\"이라는 말이 든 지점 " + ji(x.bun) + ". ") + "전체에서 부분으로 갔는지, 둥근 것은 전체 인상을 먼저 말했는지는 " + PERSON, if (x.bun.isEmpty()) "person" else "bad")
        val cls = k.s("closing").trim()
        line(10, "클로징", if (cls.isEmpty()) "없음" else if (x.close.isEmpty()) "됨" else "빠진 것: " + x.close.joinToString(", "))
        val dir = mutableListOf<String>()
        if (x.noDir.isNotEmpty()) dir.add("방향을 짚지 않은 지점 " + ji(x.noDir))
        if (x.lr.isNotEmpty()) dir.add("왼쪽·오른쪽 같은 상대 방향이 든 지점 " + ji(x.lr))
        if (x.clock.isNotEmpty()) dir.add("12시부터 차례가 어긋난 지점 " + ji(x.clock))
        if (head.contains("앞머리에 상대 방향 표현")) dir.add("앞머리에 상대 방향 표현")
        if (head.contains("앞머리에 시계 방향 차례 어긋남")) dir.add("앞머리에 시계 방향 차례 어긋남")
        line(11, "시계 방향 표기", if (dir.isEmpty()) "됨" else dir.joinToString(". "))
        val b12 = x.banned.map { "${it}지점" }.toMutableList(); if (head.contains("앞머리에 시각 전제 표현")) b12.add(0, "앞머리")
        line(12, "시각 전제 표현 절대 금지", if (b12.isEmpty()) "됨" else "든 곳: " + b12.joinToString(", "))
        val touched = spots.count { has(it.s("text"), "만져\\s*보") }
        line(13, "촉각 체험 안내", (if (x.touch.isEmpty()) "" else "\"더듬다\"가 든 지점 " + ji(x.touch) + ". ") + "\"만져보시면\" 안내가 있는 지점 ${touched}곳" + (if (touched > 0) "" else " (한 곳도 없음)"), if (x.touch.isNotEmpty() || touched == 0) "bad" else "ok")
        val snd = spots.count { has(it.s("text"), "소리|들리|들려|울리|지저귀") }
        line(14, "소리 현장영상해설", "소리 낱말이 든 지점 ${snd}곳. 소리의 원인과 상황을 이었는지는 $PERSON", "person")
        val col = spots.count { has(it.s("text"), "빨간|붉|파란|푸른|노란|노랗|초록|검은|검정|하얀|흰|회색|갈색|보라|주황|분홍|금빛|은빛|색") }
        line(15, "색상 표현", "색 낱말이 든 지점 ${col}곳. 온도감·명도·채도·변화·반영까지 적었는지는 $PERSON", "person")
        val refs = k.s("refs").trim(); val r16 = mutableListOf<String>()
        if (refs.isEmpty()) r16.add("없음")
        else {
            if (has(refs, "https?://|www\\.|\\.go\\.kr|\\.or\\.kr|\\.com|\\.net") && !has(refs, "접속|열람|확인|20\\d\\d[.\\-년 ]")) r16.add("누리집 자료에 접속일이 없음")
            if (!has(refs, "\\d")) r16.add("쪽수나 항목 번호가 없음")
        }
        line(16, "참고문헌 기록", if (r16.isEmpty()) "됨" else r16.joinToString(", "))
        return out
    }
    fun summary(t: List<Pair<String, String>>) = Triple(t.count { it.second == "ok" }, t.count { it.second == "bad" }, t.count { it.second == "person" })
    /** 다음 할 일 — 웹 nextTodo */
    fun nextTodo(k: Kos, meName: String): String {
        val s = k.spots
        if (k.s("place").isEmpty() || k.s("meet").isEmpty()) return "장소와 집합 자리를 적어 주십시오."
        if (s.isEmpty()) return "현장에서 지점을 잡아 주십시오. 아직 한 곳도 없습니다."
        val noShot = s.count { it.shots.length() == 0 }
        if (noShot > 0) return "지점 ${s.size}곳 가운데 ${noShot}곳에 답사 사진이 없습니다."
        val noStay = s.count { (it.n("stay") ?: 0.0) <= 0 }
        if (noStay > 0) return "지점 ${s.size}곳 가운데 ${noStay}곳에 머무는 시간이 없습니다."
        val noTo = k.allSpots.filter { bin(it.s("to")) }
        if (noTo.isNotEmpty()) return "어디까지를 안 적은 지점이 ${noTo.size}곳 있습니다(" + noTo.take(3).joinToString(", ") { k.spotLab(it) } + (if (noTo.size > 3) " 등" else "") + "). 지점 목록에서 열어 적어 주십시오."
        if (bin(k.s("opening"))) return "오프닝 인사말을 써 주십시오. 소속과 이름, 장소와 목적이 들어갑니다."
        val noText = s.count { bin(it.s("text")) }
        if (noText > 0) return "지점 ${s.size}곳 가운데 ${noText}곳에 대본이 비어 있습니다."
        if (bin(k.s("closing"))) return "클로징 인사말을 써 주십시오."
        val L = issueLines(scriptIssues(k, meName))
        if (L.isNotEmpty()) return "대본에서 살펴볼 것이 있습니다. " + L[0]
        if (!k.d.optBoolean("practiced", false)) return "한 번 읽어 보시면서 시간을 재 보십시오."
        if (!k.d.optBoolean("exported", false)) return "대본집과 점자 대본을 만들면 코스가 다 됩니다."
        return "이 코스는 다 되었습니다."
    }
    fun finalBadge(f: JSONObject?): String {
        f ?: return ""
        val v = (f.su("ver") ?: 0.0).toInt()
        return when (f.geul("status")) {
            "approved" -> "협회 승인 완료 · 승인번호 " + f.geul("no") + " (${v}판)"
            "pending" -> "협회 승인 기다리는 중 (${v}판, 협회 DB에 저장됨)"
            "returned" -> "협회에서 돌려보냄 (${v}판) — " + f.geul("reason")
            else -> ""
        }
    }
    fun finalStatus(f: JSONObject?): String {
        f ?: return "아직 협회에 승인을 요청하지 않았습니다."
        val v = (f.su("ver") ?: 0.0).toInt()
        val at = f.geul("at").replace("T", " ").take(16)
        return when (f.geul("status")) {
            "pending" -> "협회 DB에 저장되어 승인을 기다리고 있습니다. ${v}판, $at. 코스를 더 고치셨으면 새 판으로 다시 요청하실 수 있습니다."
            "approved" -> "협회 승인 완료. 승인번호 " + f.geul("no") + ", ${v}판, $at. 현장영상해설 화면에서 쓰실 수 있습니다. 코스를 더 고치셨으면 새 판으로 다시 요청하십시오."
            "returned" -> "협회에서 돌려보냈습니다 (${v}판). 까닭: " + f.geul("reason") + " — 고치신 뒤 다시 요청해 주십시오."
            else -> "아직 협회에 승인을 요청하지 않았습니다."
        }
    }

    // ---- 테마투어 ----
    fun isTheme(k: Kos) = k.s("field").contains("테마투어")
    fun ttLines(s: Spot): List<String> {
        val tt = s.d.optJSONObject("tt") ?: return listOf()
        return TTKAN.mapNotNull { kk -> val v = tt.geul(kk.id).trim(); if (v.isEmpty()) null else kk.nm + ": " + v }
    }

    // ---- 내보내기 (웹 guideText·noticeText·planText·mapJson 과 같은 글) ----
    fun guideText(k: Kos): String {
        val L = mutableListOf<String>()
        L.add("[" + k.name + "] 현장영상해설사용 대본집")
        L.add("장소: " + k.s("place") + " / 집합: " + k.s("meet"))
        L.add("참가 ${(k.d.su("people") ?: 0.0).toInt()}명 / 현장영상해설사 ${(k.d.su("guides") ?: 1.0).toInt()}명")
        L.add(RULE_TXT); L.add("")
        for ((t, key) in listOf("장소 소개 — 역사 (3번)" to "history", "장소 소개 — 위치와 접근 교통편 (3번)" to "access", "장소 소개 — 전체 포인트 (3번)" to "points", "촉각지도·손바닥 지도 안내 (5번)" to "tactile", "출발 전 안전 고지 (6번)" to "safety")) {
            L.add("== $t =="); L.add(k.s(key).ifEmpty { "(비어 있음)" }); L.add("")
        }
        for (s in k.allSpots) {
            L.add("== " + k.spotFull(s) + " — " + k.spotMinTxt(s) + " ==")
            L.add("[어디서부터] " + k.spotFrom(s))
            L.add("[어디까지] " + (if (bin(s.s("to"))) "(아직 적지 않음)" else s.s("to").trim()))
            if (s.s("access").isNotEmpty()) L.add("[이동 구간 안내] " + s.s("access"))
            if (s.s("touch").isNotEmpty()) L.add("[만져 볼 것] " + s.s("touch"))
            if (s.s("safe").isNotEmpty()) L.add("[조심할 것] " + s.s("safe"))
            if (s.s("gpos").isNotEmpty()) L.add("[현장영상해설사 위치] " + s.s("gpos"))
            for (x in ttLines(s)) L.add("[" + x.replaceFirst(": ", "] "))
            val t = k.spotText(s); L.add(t.ifEmpty { "(대본 비어 있음)" }); L.add("")
        }
        L.add("== 참고문헌 (16번) =="); L.add(k.s("refs").ifEmpty { "(비어 있음)" }); L.add("")
        L.add("== 자료 출처 표시 ==")
        if (has(k.s("history") + k.s("refs"), "국가유산청|관광공사")) L.add("장소 지식 자료: 국가유산청 국가유산포털(www.khs.go.kr)·한국관광공사 관광정보(api.visitkorea.or.kr), 공공누리 제1유형(출처 표시). 국가유산청 음성 해설은 참고용으로만 들었고 대본에 옮겨 적지 않았습니다.")
        if (k.d.has("pos") || k.spots.any { it.lat != null }) L.add("지도·위치: 오픈스트리트맵 기여자(ODbL, openstreetmap.org/copyright), 장소 찾기 노미나팀.")
        L.add("낱말 뜻을 찾아 쓴 곳이 있으면: 국립국어원 한국어기초사전(CC BY-SA)·표준국어대사전.")
        return L.joinToString("\n")
    }
    fun noticeText(k: Kos): String {
        val L = mutableListOf<String>()
        L.add("[" + k.name + "] 참가자 안내"); L.add("")
        L.add("장소: " + k.s("place")); L.add("집합 장소: " + k.s("meet"))
        L.add("걸리는 시간: 모두 " + fmtMin(k.courseMin()) + " 쯤")
        L.add("오프닝 지점, 지점 ${k.spots.size}곳, 클로징 지점을 차례로 걸으며 현장영상해설을 듣습니다.")
        if (k.s("note").isNotEmpty()) { L.add(""); L.add(k.s("note")) }
        L.add(""); L.add("들르는 자리")
        for (s in k.allSpots) L.add("- " + k.spotFull(s) + " — " + k.spotMinTxt(s))
        val touch = k.spots.filter { !bin(it.s("touch")) }
        if (touch.isNotEmpty()) { L.add(""); L.add("만져 보실 수 있는 것"); for (s in touch) L.add("- " + s.name + ": " + s.s("touch")) }
        if (k.s("rain").isNotEmpty()) { L.add(""); L.add("비가 오면: " + k.s("rain")) }
        L.add(""); L.add("문의: 사단법인 한국시각장애인현장영상해설협회")
        return L.joinToString("\n")
    }
    fun planText(k: Kos): String {
        val L = mutableListOf<String>()
        L.add("[" + k.name + "] 파견 운영표")
        L.add("장소 " + k.s("place") + " / 집합 " + k.s("meet") + " / 참가 ${(k.d.su("people") ?: 0.0).toInt()}명 / 현장영상해설사 ${(k.d.su("guides") ?: 1.0).toInt()}명")
        L.add(""); L.add("지점\t어디서부터\t어디까지\t이동\t정지 현장영상해설\t이동 구간 안내\t조심할 것")
        fun one(t: String) = t.replace("\n", " ")
        for (s in k.allSpots) L.add(k.spotFull(s) + "\t" + one(k.spotFrom(s)) + "\t" + one(s.s("to")) + "\t${(s.n("walkIn") ?: 0.0).toInt()}분\t${k.stayOf(s).roundToInt()}분\t" + one(s.s("access")) + "\t" + one(s.s("safe")))
        L.add(""); L.add("모두 " + fmtMin(k.courseMin()))
        if (k.s("rain").isNotEmpty()) { L.add(""); L.add("비 올 때 바꿀 동선: " + k.s("rain")) }
        return L.joinToString("\n")
    }
    fun mapJson(k: Kos): String {
        val a = JSONArray()
        k.spots.forEachIndexed { i, s ->
            a.put(JSONObject().put("n", i + 1).put("name", s.name).put("lat", s.lat ?: JSONObject.NULL).put("lng", s.lng ?: JSONObject.NULL)
                .put("stay", (s.n("stay") ?: 0.0).toInt()).put("access", s.s("access")).put("touch", s.s("touch")))
        }
        return JSONObject().put("name", k.name).put("place", k.s("place")).put("field", k.s("field")).put("meet", k.s("meet")).put("note", k.s("note"))
            .put("minutes", k.courseMin().roundToInt()).put("spots", a).toString(2)
    }
    /** 전체 시간표 — 웹 renderTimeSum */
    fun timeSum(k: Kos): List<String> {
        var stay = 0.0; var move = 0.0; var read = 0.0
        for (s in k.allSpots) { stay += k.stayOf(s); move += s.n("walkIn") ?: 0.0; if (s.kind.isEmpty()) read += (s.n("real") ?: k.readSec(s.s("text")).toDouble()) / 60 }
        return listOf("정지 현장영상해설 시간 (오프닝·클로징 포함): " + fmtMin(stay), "이동 시간: " + fmtMin(move), "현장영상해설 읽는 시간(어림): " + fmtMin(read), "모두: " + fmtMin(stay + move)) +
            k.allSpots.map { k.spotFull(it) + ": " + k.spotMinTxt(it) }
    }

    // ---- 방향과 거리 (평면도·답사 지도·벗어남·따라가기) ----
    fun brg(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val r = Math.PI / 180
        val y = sin((b.second - a.second) * r) * cos(b.first * r)
        val x = cos(a.first * r) * sin(b.first * r) - sin(a.first * r) * cos(b.first * r) * cos((b.second - a.second) * r)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
    fun meter(a: Pair<Double, Double>, b: Pair<Double, Double>) = (metersBetween(a, b) ?: 0.0).toInt()
    fun clock(deg: Double): String { val c = (deg / 30).roundToInt() % 12; return "${if (c == 0) 12 else c}시" }
    fun pos(k: Kos): Pair<Double, Double>? { val p = k.d.optJSONObject("pos") ?: return null; val la = p.su("la") ?: return null; val lo = p.su("lo") ?: return null; return la to lo }
    data class Pt(val i: Int, val s: Spot, val p: Pair<Double, Double>)
    fun spotPts(k: Kos): List<Pt> = k.spots.mapIndexedNotNull { i, s -> val la = s.lat; val lo = s.lng; if (la != null && lo != null) Pt(i, s, la to lo) else null }
    /** 장소 평면도 글 — 웹 pmText */
    fun pmText(k: Kos): String {
        val sp = spotPts(k)
        var c = pos(k); var nm0 = k.s("posName")
        if (c == null && sp.isNotEmpty()) { c = sp[0].p; nm0 = sp[0].s.name }
        c ?: return "아직 자리를 모릅니다. 장소 자리 찾기나 지금 자리로 잡기를 눌러 주십시오. 장소 지식 들이기로 뼈대를 짜면 지점 위치가 함께 들어옵니다."
        val lines = sp.map { p -> val m = meter(c, p.p); "${p.i + 1}번 ${p.s.name} — " + (if (m < 3) "기준 자리" else clock(brg(c, p.p)) + " 방향 ${m}미터") }
        val warn = mutableListOf<String>()
        if (sp.size >= 3) for (i in 2 until sp.size) {
            val a = sp[i - 2]; val b = sp[i - 1]; val d = sp[i]
            if (meter(b.p, d.p) < 15) continue
            val turn = abs(((brg(b.p, d.p) - brg(a.p, b.p)) + 540) % 360 - 180)
            if (turn > 150) warn.add("${b.i + 1}번 ${b.s.name}에서 ${d.i + 1}번 ${d.s.name}(으)로 가는 길은 왔던 방향으로 되돌아갑니다.")
        }
        val nm = nm0.ifEmpty { k.s("place").ifEmpty { k.name } }
        return "북쪽이 위이고 12시입니다. 기준 자리: " + nm + String.format(java.util.Locale.US, " (위도 %.5f, 경도 %.5f).", c.first, c.second) +
            (if (lines.isEmpty()) " 아직 위치가 적힌 지점이 없습니다." else " " + lines.joinToString(" / ")) +
            (if (warn.isEmpty()) "" else " ★ " + warn.joinToString(" ") + " 지점 차례는 지점 한 화면의 한 칸 앞으로·뒤로로 고치실 수 있습니다.") + " (지도: 오픈스트리트맵 기여자, ODbL. 장소 찾기: 노미나팀)"
    }
    /** 답사 지도 글 — 웹 drawSurveyMap 의 글 */
    fun surveyText(k: Kos): List<String> = k.spots.mapIndexed { i, s ->
        val dirs = (0 until s.shots.length()).map { s.shots.optJSONObject(it)?.geul("dir") ?: "" }.filter { it.isNotEmpty() }
        "${i + 1}번 ${s.name} — " + (if (s.lat != null) "위치 있음" else "위치 없음") + (if (dirs.isEmpty()) ", 사진 없음" else ", 사진 " + dirs.joinToString("·"))
    }
    /** 코스 선에서 떨어진 거리 — 웹 offDist */
    fun offDist(me: Pair<Double, Double>, pts: List<Pair<Double, Double>>): Pair<Int, Int?> {
        var best = 1e9; var near: Int? = null
        val kx = 111320 * cos(Math.toRadians(me.first)); val ky = 110540.0
        fun xy(p: Pair<Double, Double>) = (p.second - me.second) * kx to (p.first - me.first) * ky
        pts.forEachIndexed { i, p -> val d = meter(me, p).toDouble(); if (d < best) { best = d; near = i } }
        for (j in 1 until pts.size) {
            val a = xy(pts[j - 1]); val b = xy(pts[j]); val dx = b.first - a.first; val dy = b.second - a.second; val L2 = dx * dx + dy * dy
            if (L2 == 0.0) continue
            val t = max(0.0, min(1.0, -(a.first * dx + a.second * dy) / L2))
            val px = a.first + t * dx; val py = a.second + t * dy
            val d2 = sqrt(px * px + py * py); if (d2 < best) best = d2
        }
        return best.roundToInt() to near
    }
    /** 위치로 걷는 시간 다시 어림 — 웹 calcWalk */
    fun calcWalk(k: Kos): String {
        val a = k.allSpots; var done = 0; var miss = 0
        for (i in 1 until a.size) {
            val m = metersBetween(k.posOf(a[i - 1]), k.posOf(a[i]))
            if (m == null) { miss++; continue }
            a[i].d.put("distIn", m).put("walkIn", k.walkMinutes(m)); done++
        }
        k.syncMove()
        return if (done > 0) "${done}곳의 이동 시간(앞 지점을 떠나 그 지점까지)을 위치로 어림했습니다." + (if (miss > 0) " 위치가 없는 ${miss}곳은 그대로 두었습니다." else "")
        else "위치가 적힌 지점이 모자라 어림하지 못했습니다. 현장에서 사진을 담으시면 그 지점 위치가 함께 적힙니다."
    }

    // ---- 사진 ----
    /** 밝기와 또렷함 — 웹 photoQuality 와 같은 잣대(가로 80점으로 줄여 잼) */
    fun photoQuality(bm: Bitmap): JSONObject {
        return try {
            val w = 80; val h = max(1, (bm.height * 80.0 / max(1, bm.width)).toInt())
            val sm = Bitmap.createScaledBitmap(bm, w, h, true)
            val px = IntArray(w * h); sm.getPixels(px, 0, w, 0, 0, w, h)
            val g = DoubleArray(w * h); var sum = 0.0
            for (k in px.indices) { val c = px[k]; val v = 0.299 * ((c shr 16) and 255) + 0.587 * ((c shr 8) and 255) + 0.114 * (c and 255); g[k] = v; sum += v }
            val bright = sum / (w * h)
            var lap = 0.0; var n = 0
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                val c = g[y * w + x]
                val l = abs(4 * c - g[y * w + x - 1] - g[y * w + x + 1] - g[(y - 1) * w + x] - g[(y + 1) * w + x]); lap += l * l; n++
            }
            val sharp = if (n > 0) sqrt(lap / n) else 0.0
            val warn = when { bright < 45 -> "너무 어둡습니다."; bright > 225 -> "너무 밝아 하얗게 날아갔습니다."; sharp < 6 -> "흐릿합니다. 폰을 잠시 멈추고 다시 담아 주십시오."; else -> "" }
            JSONObject().put("bright", bright.roundToInt()).put("sharp", (sharp * 10).roundToInt() / 10.0).put("warn", warn)
        } catch (e: Exception) { JSONObject().put("bright", -1).put("sharp", -1).put("warn", "") }
    }
    /** 담는 모양 — 원본이 아니면 가운데를 그 비율로 잘라 냄(넓이가 늘지는 않음) */
    val MOYANG = listOf("won" to "원본 그대로", "garo" to "가로 (16대9)", "sero" to "세로 (9대16)", "jeong" to "정사각")
    fun jareugi(bm: Bitmap, mo: String): Bitmap {
        if (mo == "won") return bm
        val w = bm.width.toDouble(); val h = bm.height.toDouble()
        val bi = when (mo) { "garo" -> 16.0 / 9.0; "sero" -> 9.0 / 16.0; else -> 1.0 }
        var sw = w; var sh = h
        if (sw / sh > bi) sw = sh * bi else sh = sw / bi
        return Bitmap.createBitmap(bm, ((w - sw) / 2).toInt(), ((h - sh) / 2).toInt(), sw.toInt(), sh.toInt())
    }
    fun moyangMal(w: Int, h: Int) = (if (w > h) "가로" else if (w < h) "세로" else "정사각") + " 사진으로 담겼습니다. $w 곱하기 ${h}입니다."
}
