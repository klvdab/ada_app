// 일정 — 말 풀기 (안드로이드 2.81.0, 빌드 261027-A69, 대클, 이사장님 승인 2026-10-27 「1번으로 하는데 위치를 음악방송이 아니라 설정탭에 넣도록하자」)
// 「내일 오후 2시 국립극장 회의 넣어 줘」를 날짜·시각·제목·장소로 가름. 안드로이드 부품에 기대지 않는 순수 셈이라 따로 시험함.
// 아이폰 Iljeong.swift 의 IljeongMal 과 같은 셈, 같은 말.
package kr.or.ada.app.gilnun

import java.util.Calendar

class IljeongPul {
    var jemok = ""
    var jemokMal: List<String> = emptyList()   // 제목 낱말(찾기에 씀)
    var jangso = ""
    var nal: Long? = null        // 그날 0시
    var nalRo: Long? = null      // 「금요일로」 — 옮길 날
    var si: Int? = null
    var bun = 0
    var kkeutSi: Int? = null
    var kkeutBun = 0
    var sigakRo: Pair<Int, Int>? = null   // 「3시로」 — 바꿀 시각
    var haruJongil = false
    var banbok = ""              // maeil 날마다, pyeongil 평일, jumal 주말, maeju 매주
    var yoil = 0                 // 매주일 때 요일(일요일 1 … 토요일 7)
    var jusu = 0                 // 이번 주 1, 다음 주 2, 이번 주말 3
    var ampmMal = false          // 2.82.0 오전·오후·아침·저녁 같은 말을 하셨는가(「7시로」를 오후로 볼지 가름)
}

object IljeongMal {
    private val AMPM = listOf("오전", "오후", "아침", "저녁", "밤", "낮", "새벽")
    private val YOIL = "일월화수목금토"
    private val JOSA = listOf("으로", "부터", "까지", "에는", "에도", "마다", "에", "은", "는", "도", "로", "쯤", "께", "날", "을", "를")
    /** 제목에서 빼는 말 — 시키는 말, 일정이라는 말, 군말 */
    val DONGSA = setOf(
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
        // 2.82.0 (261027-A70) 더 알아듣는 말
        "일정의", "일정이야", "그거", "아까", "수정", "수정해", "수정해줘", "편집", "편집해", "편집해줘", "검색", "검색해", "검색해줘",
        "조회", "조회해", "조회해줘", "찾아", "찾아줘", "확인", "확인해", "확인해줘", "보여", "보여줘", "예약해", "예약해줘",
        "만들어", "만들어줘", "알려줄래", "있는지", "없어", "어때", "어떻게돼", "자세히"
    )

    // 2.82.0 (261027-A70, 이사장님 승인 2026-10-27 「순서대로 모두 고쳐」) 시키는 말 갈래 — 아이폰 IljeongMal 과 같음
    val NEOKI = listOf("넣어", "넣자", "넣고", "추가", "잡아", "등록", "적어", "기록해", "저장해", "만들어", "예약해")
    val BAKKUGI = listOf("바꿔", "바꾸", "옮겨", "옮기", "변경", "미뤄", "당겨", "고쳐", "고치", "수정", "편집")
    val JIUGI = listOf("지워", "지우", "삭제", "취소", "빼줘", "빼라", "없애")
    val ILGI = listOf("알려", "뭐", "있어", "있나", "있니", "있냐", "있는지", "읽어", "들려", "말해", "확인", "검색", "조회", "찾아", "보여", "몇개", "어때", "어떻게돼", "자세히")
    /** 일정과 다른 일을 시키는 말 — 이런 말이 있으면 「오후 2시 복지콜」처럼 움직임 말 없는 일정으로 보지 않음 */
    val DARUN = listOf("불러", "호출", "틀어", "꺼", "켜", "깨워", "가자", "가줘", "가고", "안내", "길", "전화", "문자", "보내", "들어", "재생", "멈춰", "그만")

    /** 읽어 드리는 목록의 번호 말 */
    val SEOSU = listOf("첫째", "둘째", "셋째", "넷째", "다섯째", "여섯째", "일곱째", "여덟째", "아홉째", "열째", "열한째", "열두째", "열셋째", "열넷째", "열다섯째")

    /** 「첫째」, 「두 번째」, 「2번」 — 읽어 드린 목록의 몇째인가(「2시」, 「2번 출구」는 아님) */
    fun mokBeonho(t: String): Int? {
        val z = ttuk(t)
        Regex("(첫|두|둘|세|셋|네|넷|다섯|여섯|일곱|여덟|아홉|열)(번)?째").find(z)?.let { m ->
            return when (m.groupValues[1]) { "첫" -> 1; "두", "둘" -> 2; "세", "셋" -> 3; "네", "넷" -> 4; "다섯" -> 5; "여섯" -> 6; "일곱" -> 7; "여덟" -> 8; "아홉" -> 9; else -> 10 }
        }
        Regex("(\\d{1,2})번(째)?(?!출|버스|마을|선)").find(z)?.let { return it.groupValues[1].toInt() }
        return null
    }

    /** 「그 일정」, 「방금 넣은 거」 — 바로 앞에 말한 일정을 가리킴 */
    fun geuMal(t: String): Boolean {
        val z = ttuk(t)
        return listOf("그일정", "이일정", "방금", "아까", "그거", "그것", "그약속", "그건", "거기에", "거기").any { z.contains(it) }
    }

    /** 고칠 칸 — 제목, 장소, 내용 */
    class GochimPul(val kan: String, val hal: String, val dae: String, val gap: String)

    private val KAN = listOf("제목" to "jemok", "이름" to "jemok", "장소" to "jangso", "내용" to "naeyong", "메모" to "naeyong", "설명" to "naeyong", "적은것" to "naeyong", "적은거" to "naeyong")
    private val GAP_BBAEGI = setOf("을", "를", "은", "는", "에", "에다", "에다가", "도", "이", "가", "으로", "로", "에는", "좀", "다시", "새로", "새")
    /** 이것으로 시작하는 낱말은 시키는 말(두 글자 넘는 것만 — 「해운대」가 빠지지 않게) */
    private val GAP_DONGSA = listOf("바꿔", "바꾸", "옮겨", "변경", "고쳐", "수정", "편집", "추가", "넣어", "넣자", "적어", "기록해", "저장", "등록", "달아", "붙여",
        "지워", "지우", "삭제", "없애", "알려", "읽어", "들려", "말해", "확인", "해줘", "해주", "주세요", "할래", "하자", "해라")
    /** 통째로 같을 때만 시키는 말 */
    private val GAP_JEONG = setOf("해", "줘", "좀", "뭐", "빼", "빼줘", "줄래", "라고", "이라고", "다시", "새로")

    /**
     * 「2시 일정 제목 복지콜로 바꿔 줘」, 「복지콜 일정에 내용 추가 휠체어 탑승」, 「그 일정 내용 읽어 줘」, 「장소를 국립극장으로 수정」
     * kan: jemok·jangso·naeyong, hal: bakkugi(바꿈)·deohagi(더함)·ilgi(읽음)·jiugi(지움), dae: 칸 이름 앞의 말(어느 일정), gap: 새 값
     * 칸 이름이 없거나, 넣는 말에 날짜·시각이 함께 있으면(새 일정) null
     */
    fun gochimPulgi(t0: String, now: Long): GochimPul? {
        val t = dadeum(t0)
        var kanIreum = ""; var kan = ""; var idx = -1
        for ((k, v) in KAN) { val i = t.indexOf(k); if (i >= 0 && (idx < 0 || i < idx)) { idx = i; kanIreum = k; kan = v } }
        if (idx < 0) return null
        val dae = t.substring(0, idx).trim()
        val dwi = t.substring(idx + kanIreum.length)
        val z = ttuk(t)
        val ilgi = listOf("알려", "읽어", "들려", "말해", "확인", "뭐야", "뭐였", "뭐라", "뭐있").any { z.contains(it) }
        val jiugi = JIUGI.any { z.contains(it) }
        val deohagi = listOf("추가", "넣어", "넣자", "적어", "기록", "저장", "등록", "달아", "붙여", "덧붙", "더해").any { z.contains(it) }
        val bakkugi = BAKKUGI.any { z.contains(it) }
        // 「장소는 협회 사무실 내일 3시 회의 넣어 줘」 — 새 일정
        if (deohagi && !bakkugi) {
            val p = pulgi(t0, now)
            if (kan != "naeyong" && (p.si != null || p.haruJongil)) return null
        }
        val hal = when { bakkugi -> "bakkugi"; jiugi && !deohagi -> "jiugi"; deohagi -> if (kan == "naeyong") "deohagi" else "bakkugi"; ilgi -> "ilgi"; else -> "" }
        if (hal.isEmpty()) return if (kan == "naeyong" && z.length <= 14) GochimPul(kan, "ilgi", dae, "") else null
        // 새 값 — 칸 이름 뒤의 말에서 시키는 말과 토씨를 뺌
        val mal = dwi.split(" ").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        val nameun = ArrayList<String>()
        for (w0 in mal) {
            var w = w0
            if (w in GAP_BBAEGI) continue
            if (nameun.isEmpty()) for (j in listOf("에다가", "에다", "에는", "을", "를", "은", "는", "에", "도", "이", "가")) if (w == j) { w = ""; break }
            if (w.isEmpty()) continue
            val wz = ttuk(w)
            if (wz in GAP_JEONG || (GAP_DONGSA.any { wz.startsWith(it) } && wz.length <= 7)) continue
            nameun.add(w)
        }
        var gap = nameun.joinToString(" ").trim()
        // 끝말 다듬기 — 「복지콜로」, 「휠체어 탑승이라고」, 「국립극장으로」
        for (k in listOf("이라고", "라고", "으로", "로")) if (gap.length > k.length + 1 && gap.endsWith(k)) { gap = gap.dropLast(k.length); break }
        if (gap.startsWith("을 ") || gap.startsWith("를 ")) gap = gap.drop(2)
        if (hal == "ilgi" || hal == "jiugi") gap = ""
        return GochimPul(kan, hal, dae, gap.trim())
    }

    /** 「무슨 일정인가요」의 대답 — 「복지콜이야」 → 「복지콜」 */
    fun jemokDap(t0: String): String {
        var t = t0.trim().trimEnd('.', '?', '!', ' ')
        for (k in listOf("제목은 ", "제목은", "이름은 ", "일정은 ")) if (t.startsWith(k)) t = t.drop(k.length)
        for (k in listOf("이라고 해 줘", "이라고 해줘", "라고 해 줘", "라고 해줘", "이라고", "라고", "입니다", "이에요", "예요", "이야", "일정이요", "일정", "이요", "야", "요"))
            if (t.length > k.length && t.endsWith(k)) { t = t.dropLast(k.length).trim(); break }
        return t.trim()
    }
    /** 이름 끝이 이러면 곳 이름으로 봄(제목 첫 낱말일 때) */
    private val GOT_KKEUT = listOf("극장", "병원", "의원", "치과", "한의원", "센터", "회관", "공원", "사무실", "학교", "구청", "시청", "주민센터",
        "은행", "마트", "시장", "카페", "식당", "호텔", "공항", "터미널", "도서관", "복지관", "협회", "교회", "성당", "박물관", "미술관",
        "백화점", "약국", "역", "청", "관", "원", "점")

    fun ttuk(t: String): String = t.filter { it !in " \t\n.,!?~·'\"" }

    private fun josaBbaegi(w: String): Pair<String, String> {
        for (j in JOSA) if (w.length > j.length && w.endsWith(j)) return w.dropLast(j.length) to j
        return w to ""
    }

    private fun yoilBeon(c: Char): Int = YOIL.indexOf(c) + 1   // 일 1 … 토 7

    private fun haru(now: Long, d: Int): Long {
        val c = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        c.add(Calendar.DAY_OF_MONTH, d)
        return c.timeInMillis
    }

    /** 이번 주(월요일 시작) 또는 다음 주의 그 요일 */
    private fun juYoil(now: Long, yo: Int, daeum: Boolean): Long {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val oneul = c.get(Calendar.DAY_OF_WEEK)
        val oneulBeon = (oneul + 5) % 7      // 월 0 … 일 6
        val geuBeon = (yo + 5) % 7
        return haru(now, geuBeon - oneulBeon + (if (daeum) 7 else 0))
    }

    /** 오늘부터 가장 가까운 그 요일(오늘 포함) */
    private fun gakkaunYoil(now: Long, yo: Int): Long {
        val oneul = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)
        return haru(now, (yo - oneul + 7) % 7)
    }

    private fun ilWol(now: Long, wol: Int?, il: Int): Long? {
        if (il !in 1..31) return null
        val c = Calendar.getInstance().apply { timeInMillis = haru(now, 0) }
        val oneul = c.timeInMillis
        if (wol != null) {
            if (wol !in 1..12) return null
            c.set(Calendar.MONTH, wol - 1); c.set(Calendar.DAY_OF_MONTH, il)
            if (c.timeInMillis < oneul - 86_400_000L) c.add(Calendar.YEAR, 1)
        } else {
            c.set(Calendar.DAY_OF_MONTH, il)
            if (c.timeInMillis < oneul) c.add(Calendar.MONTH, 1)
        }
        return c.timeInMillis
    }

    // 2.82.0 (261027-A70) 우리말 시각 — 「두시」, 「열한 시 반」, 「두 시 삼십 분」(「한 시간」은 그대로)
    private val U_SI = Regex("(^|\\s|오전|오후|아침|저녁|밤|낮|새벽)(열한|열두|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열)\\s*시(?!간)")
    private val U_SU = mapOf("한" to 1, "두" to 2, "세" to 3, "네" to 4, "다섯" to 5, "여섯" to 6, "일곱" to 7, "여덟" to 8, "아홉" to 9, "열" to 10, "열한" to 11, "열두" to 12)
    private val H_BUN = Regex("(\\d{1,2})\\s*시\\s*((?:[이삼사오]?십)?[일이삼사오육칠팔구]?)\\s*분")

    /** 한자말 수 「삼십오」 → 35 (1~59) */
    private fun hanjaSu(s: String): Int? {
        if (s.isEmpty()) return null
        val il = mapOf('일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5, '육' to 6, '칠' to 7, '팔' to 8, '구' to 9)
        val k = s.indexOf('십')
        val n = if (k < 0) (if (s.length == 1) il[s[0]] ?: return null else return null)
        else {
            val ap = if (k == 0) 1 else il[s[0]] ?: return null
            val dwi = if (k == s.length - 1) 0 else il[s[k + 1]] ?: return null
            ap * 10 + dwi
        }
        return if (n in 1..59) n else null
    }

    /** 우리말·한자말 시각을 숫자로 — 「오후 두시에」 → 「오후 2시에」 */
    fun sutjaRo(t0: String): String {
        var t = U_SI.replace(t0) { m -> m.groupValues[1] + (if (m.groupValues[1].isNotEmpty() && !m.groupValues[1].isBlank()) " " else "") + U_SU[m.groupValues[2]] + "시" }
        t = H_BUN.replace(t) { m -> val b = hanjaSu(m.groupValues[2]); if (b != null) "${m.groupValues[1]}시 ${b}분" else m.value }
        return t
    }

    /** 말 다듬기 — 띄어 쓴 한 말을 붙이고, 숫자 앞을 띄움 */
    fun dadeum(t0: String): String {
        var t = " " + sutjaRo(t0.trim()) + " "
        for ((a, b) in listOf("다음 주" to "다음주", "이번 주" to "이번주", "담 주" to "다음주", "이번주 말" to "이번주말", "하루 종일" to "하루종일",
            "매 주" to "매주", "매 일" to "매일", "평일 마다" to "평일마다", "주말 마다" to "주말마다", "넣어 줘" to "넣어줘", "추가해 줘" to "추가해줘",
            "잡아 줘" to "잡아줘", "바꿔 줘" to "바꿔줘", "옮겨 줘" to "옮겨줘", "지워 줘" to "지워줘", "알려 줘" to "알려줘", "등록해 줘" to "등록해줘",
            "적어 줘" to "적어줘", "삭제해 줘" to "삭제해줘", "취소해 줘" to "취소해줘", "읽어 줘" to "읽어줘", "들려 줘" to "들려줘")) t = t.replace(a, b)
        val sb = StringBuilder()
        for (i in t.indices) {
            val ch = t[i]
            if (ch.isDigit() && i > 0 && !t[i - 1].isDigit() && !t[i - 1].isWhitespace()) sb.append(' ')
            sb.append(ch)
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    private val SIGAK = Regex("^(\\d{1,2})시(반|(\\d{1,2})분)?$")
    private val BUN = Regex("^(\\d{1,2})분$")
    private val WOL_IL = Regex("^(\\d{1,2})월(\\d{1,2})일$")
    private val WOL = Regex("^(\\d{1,2})월$")
    private val IL = Regex("^(\\d{1,2})일$")
    private val YOIL_RE = Regex("^(이번주|다음주|매주)?([일월화수목금토])요일$")

    fun pulgi(t0: String, now: Long, jeulgyeo: List<String> = emptyList()): IljeongPul {
        val p = IljeongPul()
        val mal = dadeum(t0).split(" ").filter { it.isNotEmpty() }
        val nameun = ArrayList<String>()
        var ampm = ""          // 바로 앞에서 들은 오전·오후
        var jeonche = ""       // 말 전체의 오전·오후
        for (w in mal) { val z = ttuk(w); for (a in AMPM) if (z.startsWith(a)) { jeonche = a; break } }
        p.ampmMal = jeonche.isNotEmpty()
        var juMal = 0          // 바로 앞 낱말이 이번주(1)·다음주(2)
        var wolDaegi: Int? = null
        val sigakDeul = ArrayList<Triple<Int, Int, Boolean>>()   // (시, 분, 로)
        var jangsoMoeum = false   // 「장소는 협회 사무실」 — 다른 말이 나올 때까지 곳 이름으로 모음
        var i = 0
        while (i < mal.size) {
            val w = mal[i]
            val z0 = ttuk(w)
            i++
            if (z0.isEmpty()) continue
            if (z0 == "장소는" || z0 == "장소" || z0 == "장소가" || z0 == "장소를") { jangsoMoeum = true; p.jangso = ""; continue }
            val (z, josa) = josaBbaegi(z0)
            val gidaMal = jangsoMoeum
            jangsoMoeum = false
            val ro = josa == "로" || josa == "으로"
            // 오전·오후(「오후2시」처럼 붙은 것도)
            var zz = z
            var ap = ""
            for (a in AMPM) if (zz.startsWith(a)) { ap = a; zz = zz.substring(a.length); break }
            if (ap.isNotEmpty() && zz.isEmpty()) {
                // 바로 뒤가 시각이면 오전·오후로, 아니면 「아침 운동」처럼 제목의 한 말
                val dwi = if (i < mal.size) ttuk(mal[i]) else ""
                if (dwi.isNotEmpty() && (dwi[0].isDigit() || dwi.startsWith("정오") || dwi.startsWith("자정"))) { ampm = ap; continue }
                // 2.82.0 「오전」·「오후」는 제목이 되지 않음 — 「오후 일정 2시에」
                if (ap == "오전" || ap == "오후") { ampm = ap; continue }
                nameun.add(w)
                continue
            }
            if (zz == "정오" || z == "정오") { sigakDeul.add(Triple(12, 0, ro)); continue }
            if (zz == "자정" || z == "자정") { sigakDeul.add(Triple(0, 0, ro)); continue }
            if (z == "하루종일" || z == "종일" || z == "온종일") { p.haruJongil = true; continue }
            if (z == "하루") { if (i < mal.size && ttuk(mal[i]).startsWith("종일")) { p.haruJongil = true; i++; continue } }
            val sm = SIGAK.find(zz)
            if (sm != null) {
                var h = sm.groupValues[1].toInt()
                var m = if (sm.groupValues[2] == "반") 30 else (sm.groupValues[3].toIntOrNull() ?: 0)
                // 「2시 30분」처럼 띄운 분, 「3시 반」의 반
                if (sm.groupValues[2].isEmpty() && i < mal.size) {
                    val nz = josaBbaegi(ttuk(mal[i]))
                    val bm = BUN.find(nz.first)
                    if (bm != null) { m = bm.groupValues[1].toInt(); i++ }
                    else if (nz.first == "반") { m = 30; i++ }
                }
                if (m > 59 || h > 24) { nameun.add(w); continue }
                val apSseul = if (ap.isNotEmpty()) ap else if (ampm.isNotEmpty()) ampm else jeonche
                h = ojeonOhu(h, apSseul)
                var roHam = ro
                if (!roHam && i < mal.size) { val nz = ttuk(mal[i]); if (nz == "로" || nz == "으로") { roHam = true; i++ } }
                sigakDeul.add(Triple(h, m, roHam))
                ampm = ""
                continue
            }
            if (BUN.find(z) != null || z == "반") continue
            // 날
            when (z) {
                "오늘" -> { nalNeoki(p, haru(now, 0), ro); continue }
                "내일" -> { nalNeoki(p, haru(now, 1), ro); continue }
                "모레" -> { nalNeoki(p, haru(now, 2), ro); continue }
                "글피" -> { nalNeoki(p, haru(now, 3), ro); continue }
                "이번주" -> { juMal = 1; if (p.jusu == 0) p.jusu = 1; continue }
                "다음주" -> { juMal = 2; p.jusu = 2; continue }
                "이번주말", "주말" -> { if (josa == "마다" || z0 == "주말마다") { p.banbok = "jumal" } else { p.jusu = 3 }; continue }
                "주말마다" -> { p.banbok = "jumal"; continue }
                "매주" -> { p.banbok = "maeju"; juMal = 3; continue }
                "매일", "날마다", "매일매일" -> { p.banbok = "maeil"; continue }
                "평일", "평일마다", "주중" -> { p.banbok = "pyeongil"; continue }
            }
            if (z0 == "평일마다" || z0 == "주말마다" || z0 == "날마다") { p.banbok = when (z0) { "평일마다" -> "pyeongil"; "주말마다" -> "jumal"; else -> "maeil" }; continue }
            val ym = YOIL_RE.find(z)
            if (ym != null) {
                val yo = yoilBeon(ym.groupValues[2][0])
                val ap2 = ym.groupValues[1]
                val ju = when (ap2) { "이번주" -> 1; "다음주" -> 2; "매주" -> 3; else -> juMal }
                if (ju == 3 || josa == "마다") {
                    p.banbok = "maeju"; p.yoil = yo
                    nalNeoki(p, gakkaunYoil(now, yo), false)
                } else {
                    val d = when (ju) { 1 -> juYoil(now, yo, false); 2 -> juYoil(now, yo, true); else -> gakkaunYoil(now, yo) }
                    nalNeoki(p, d, ro)
                    if (ju != 0 && p.jusu != 0 && !ro) p.jusu = 0   // 「다음 주 화요일」은 그날 하루
                }
                juMal = 0
                continue
            }
            val wi = WOL_IL.find(z)
            if (wi != null) { ilWol(now, wi.groupValues[1].toInt(), wi.groupValues[2].toInt())?.let { nalNeoki(p, it, ro) }; continue }
            val wm = WOL.find(z)
            if (wm != null) { wolDaegi = wm.groupValues[1].toInt(); continue }
            val im = IL.find(z)
            if (im != null) { ilWol(now, wolDaegi, im.groupValues[1].toInt())?.let { nalNeoki(p, it, ro) }; wolDaegi = null; continue }
            if (z0 in DONGSA || z in DONGSA) continue
            if (z0.endsWith("에서") && z0.length > 2) {
                val g = z0.dropLast(2)
                if (p.jangso.isEmpty()) p.jangso = g
                nameun.add(g)
                continue
            }
            if (gidaMal) {   // 장소는 ○○ — 곳 이름에 이어 붙임(제목에는 넣지 않음)
                p.jangso = (p.jangso + " " + josaBbaegi(z0).first).trim()
                jangsoMoeum = true
                continue
            }
            nameun.add(w)
        }
        // 시각 — 바꿀 시각(「3시로」)과 찾는·넣는 시각
        val bo = sigakDeul.filter { !it.third }
        val roS = sigakDeul.lastOrNull { it.third }
        if (bo.isNotEmpty()) { p.si = bo[0].first; p.bun = bo[0].second }
        if (bo.size >= 2) {
            var kh = bo[1].first
            if (kh * 60 + bo[1].second <= bo[0].first * 60 + bo[0].second && kh < 12) kh += 12
            p.kkeutSi = kh; p.kkeutBun = bo[1].second
        }
        if (roS != null) p.sigakRo = roS.first to roS.second
        // 제목 — 끝의 을·를 떼고
        val jm = nameun.map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (jm.isNotEmpty()) {
            val k = jm.last()
            if (k.length >= 2 && (k.endsWith("을") || k.endsWith("를"))) jm[jm.size - 1] = k.dropLast(1)
        }
        p.jemok = jm.joinToString(" ")
        p.jemokMal = jm.map { ttuk(josaBbaegi(ttuk(it)).first) }.filter { it.length >= 1 }
        // 장소 — 즐겨찾기 이름, 「○○에서」, 첫 낱말이 곳 이름 같으면
        if (p.jangso.isEmpty()) {
            val jg = jeulgyeo.firstOrNull { g -> g.isNotEmpty() && jm.any { ttuk(it) == ttuk(g) } }
            if (jg != null) p.jangso = jg
            else if (jm.size >= 2) {
                val c = ttuk(jm[0])
                // 「병원」·「은행」처럼 흔한 말 하나만으로는 곳을 정하지 않음 — 「서울대병원」처럼 앞에 이름이 붙어야
                if (GOT_KKEUT.any { s -> c.endsWith(s) && c.length > s.length && c.length >= 3 }) p.jangso = jm[0]
            }
        }
        return p
    }

    private fun nalNeoki(p: IljeongPul, d: Long, ro: Boolean) {
        if (ro) p.nalRo = d else if (p.nal == null) p.nal = d else p.nalRo = d
    }

    /** 오전·오후가 없으면 1시~6시는 오후로 봄(약속은 대개 낮), 아침·오전·새벽 12시는 0시 */
    fun ojeonOhu(h0: Int, ap: String): Int {
        var h = h0
        if (h == 24) return 0
        when (ap) {
            "오후", "저녁", "밤" -> { if (h < 12) h += 12 }
            "낮" -> { if (h in 1..6) h += 12 }
            "오전", "아침", "새벽" -> { if (h == 12) h = 0 }
            else -> { if (h in 1..6) h += 12 }
        }
        return h
    }

    /** 받침 — (있는가, ㄹ 받침인가) */
    private fun batchim(w: String): Pair<Boolean, Boolean> {
        val ch = w.trim().lastOrNull() ?: return false to false
        if (ch.isDigit()) return (ch in "013678") to (ch in "178")
        if (ch !in '가'..'힣') return false to false
        val j = (ch - '가') % 28
        return (j != 0) to (j == 8)
    }
    fun eun(w: String) = if (batchim(w).first) "은" else "는"
    fun eul(w: String) = if (batchim(w).first) "을" else "를"
    fun ro(w: String): String { val b = batchim(w); return if (b.first && !b.second) "으로" else "로" }

    /** 「1번」, 「두 번째」, 「셋째」 — 고른 번호 */
    fun beonho(z0: String): Int? {
        val z = ttuk(z0)
        Regex("(\\d{1,2})").find(z)?.let { return it.groupValues[1].toInt() }
        val su = listOf("첫" to 1, "한" to 1, "하나" to 1, "일번" to 1, "두" to 2, "둘" to 2, "이번" to 2, "세" to 3, "셋" to 3, "삼번" to 3,
            "네" to 4, "넷" to 4, "사번" to 4, "다섯" to 5, "오번" to 5)
        for ((k, v) in su) if (z.startsWith(k) && (z.contains("번") || z.contains("째"))) return v
        return null
    }
}
