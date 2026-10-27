// 날씨를 말로 묻기 (안드로이드 2.83.0, 빌드 261027-A71, 대클 — 이사장님 승인 2026-10-27 「1」 공개 자료로 넓히기)
// 웹 길눈 말로 하기 2.5(260926)의 날씨 묻기를 앱으로 옮기고, 공개 자료(아마존 MASSIVE 한국어 날씨 855문장)로 시험해 말을 넓힘.
// 「내일 비 와」, 「우산 가져가야 돼」, 「화요일 기온 어때」, 「코트 입어야 할까」, 「인천 날씨」, 「이번 주 날씨」 — 물으신 것을 먼저 말씀드림.
// 열쇠 없는 무료 오픈메테오에서 받으므로 쓰시는 분께 비용이 들지 않음. 아이폰 Nalssi.swift 의 NalssiMal 과 같은 셈, 같은 말.
// 이 파일은 안드로이드 부품에 기대지 않는 순수 셈이라 따로 시험함(받아 오기는 MalHagi.kt 의 Nalssi.mureum).
package kr.or.ada.app.gilnun

import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

object NalssiMal {
    /** 웹 길눈과 같은 도시 52곳 — 「인천 날씨」처럼 말씀하시면 그 도시 */
    val GOT = listOf(
        Triple("서울", 37.5665, 126.978), Triple("부산", 35.1796, 129.0756), Triple("대구", 35.8714, 128.6014), Triple("인천", 37.4563, 126.7052),
        Triple("광주", 35.1595, 126.8526), Triple("대전", 36.3504, 127.3845), Triple("울산", 35.5384, 129.3114), Triple("세종", 36.48, 127.289),
        Triple("수원", 37.2636, 127.0286), Triple("성남", 37.4201, 127.1269), Triple("고양", 37.6584, 126.832), Triple("용인", 37.2411, 127.1776),
        Triple("부천", 37.5034, 126.766), Triple("안산", 37.3219, 126.8309), Triple("안양", 37.3943, 126.9568), Triple("남양주", 37.636, 127.2165),
        Triple("화성", 37.1995, 126.8312), Triple("평택", 36.9921, 127.1129), Triple("의정부", 37.7381, 127.0337), Triple("파주", 37.76, 126.78),
        Triple("김포", 37.6153, 126.7156), Triple("여주", 37.2983, 127.6374), Triple("이천", 37.2723, 127.435), Triple("춘천", 37.8813, 127.7298),
        Triple("원주", 37.3422, 127.9202), Triple("강릉", 37.7519, 128.8761), Triple("속초", 38.207, 128.5918), Triple("홍천", 37.6969, 127.8886),
        Triple("정선", 37.3807, 128.6609), Triple("청주", 36.6424, 127.489), Triple("충주", 36.991, 127.9259), Triple("천안", 36.8151, 127.1139),
        Triple("아산", 36.7898, 127.0019), Triple("공주", 36.4465, 127.119), Triple("전주", 35.8242, 127.148), Triple("군산", 35.9676, 126.7369),
        Triple("익산", 35.9483, 126.9577), Triple("목포", 34.8118, 126.3922), Triple("여수", 34.7604, 127.6622), Triple("순천", 34.9506, 127.4872),
        Triple("구례", 35.2025, 127.4629), Triple("포항", 36.019, 129.3435), Triple("경주", 35.8562, 129.2247), Triple("안동", 36.5684, 128.7294),
        Triple("구미", 36.1195, 128.3446), Triple("창원", 35.2279, 128.6811), Triple("진주", 35.18, 128.1076), Triple("김해", 35.2285, 128.8894),
        Triple("통영", 34.8544, 128.4331), Triple("거제", 34.8806, 128.6211), Triple("제주", 33.4996, 126.5312), Triple("서귀포", 33.2541, 126.56),
        // 2.83.0 공개 자료에 나온 곳 더함
        Triple("용산", 37.5326, 126.9905), Triple("강남", 37.4979, 127.0276), Triple("보령", 36.3333, 126.6128), Triple("삼척", 37.4499, 129.1652),
        Triple("동해", 37.5247, 129.1143), Triple("태백", 37.164, 128.9856), Triple("광명", 37.4786, 126.8646), Triple("시흥", 37.3799, 126.8031),
        Triple("하남", 37.5393, 127.2148), Triple("양평", 37.4917, 127.4876), Triple("가평", 37.8315, 127.5095), Triple("포천", 37.8949, 127.2003),
        Triple("서산", 36.7848, 126.4503), Triple("당진", 36.8898, 126.6458), Triple("논산", 36.1872, 127.0987), Triple("제천", 37.1326, 128.191),
        Triple("남원", 35.4164, 127.3904), Triple("정읍", 35.5699, 126.856), Triple("나주", 35.0159, 126.7108), Triple("해남", 34.5733, 126.5993),
        Triple("양산", 35.335, 129.0373), Triple("밀양", 35.5038, 128.7464), Triple("사천", 35.0037, 128.0642), Triple("남해", 34.8376, 127.8924),
        Triple("울릉", 37.4844, 130.9057), Triple("독도", 37.2426, 131.8669)
    )

    private val BAEJE = Regex("노래|음악|틀어|곡|방송|라디오|재생|듣고|듣자|뉴스|기사|이메일|메일|문자|일정|약속|캘린더|달력|알람|트윗|페이스북|게시|리뷰|정의|뜻이|영화|오븐|굽|튀기|요리|레시피")
    private val KKOK = Regex("날씨|기온|온도|미세\\s*먼지|초미세|황사|우산|우비|비옷|일기\\s*예보|기상|예보|체감|습도|우박|일교차|(^|[^상])영하|뇌우|폭풍|태풍|소나기|장마|천둥|번개|자외선|선크림|선로션|썬크림|영상\\s*(일|이|인|으로)")
    private val NUN = Regex("(^|[^길])눈\\s*(이\\s*)?(와|오|올|내리|내릴|내려|소식|쌓|예보)")
    private val BI = Regex("(^|[^가-힣])비\\s*(가\\s*)?(와|오[^\\s]|올|오나|오니|내리|내릴|내려|소식|예보)|이슬비|빗")
    private val TTAE = Regex("(오늘|내일|모레|밖|지금|날|바깥|아침|저녁|밤|낮|오후|오전|얼마나|어때|어떠|주말|이번\\s*주|요일|나중|이따)")
    private val OS = Regex("코트|재킷|자켓|외투|패딩|스웨터|반팔|긴팔|겉옷|따뜻한\\s*옷|두꺼운|장갑|목도리|선글라스|덧신|양말")

    /** 날씨를 묻는 말인가 */
    fun mutneunInga(t: String): Boolean {
        if (BAEJE.containsMatchIn(t)) return false
        if (KKOK.containsMatchIn(t)) return true
        if (NUN.containsMatchIn(t) || BI.containsMatchIn(t)) return true
        if (Regex("추워|추운|춥|추울|더워|더운|덥|더울|쌀쌀|포근|따뜻|따듯|선선|시원할|후덥|무더").containsMatchIn(t) && (TTAE.containsMatchIn(t) || Regex("까|니|나|야|지|요$").containsMatchIn(t.trim()))) return true
        if (Regex("몇\\s*도").containsMatchIn(t) && Regex("밖|바깥|오늘|내일|모레|지금|현재|요일|기온|온도").containsMatchIn(t)) return true
        if (Regex("맑을|맑아|맑니|맑나|맑겠|맑은\\s*날|흐릴|흐려|흐리|해가\\s*(비칠|날|뜰|나)|해\\s*(나와|날까|뜰|뜨)").containsMatchIn(t) && Regex("오늘|내일|모레|날|하늘|주말|요일|나중|까|니|나").containsMatchIn(t)) return true
        if (Regex("(밖|바깥)\\s*(은|에)?\\s*(어때|어떠)").containsMatchIn(t)) return true
        if (Regex("바람\\s*(이\\s*)?(불|세|많|부|강)").containsMatchIn(t)) return true
        if (OS.containsMatchIn(t) && Regex("입어|입을|가져|챙겨|챙길|필요|신을|껴야|써야|해야|할까|될까").containsMatchIn(t)) return true
        return false
    }

    /** 며칠 뒤 — 오늘 0, 내일 1, 모레 2, 글피 3, 요일이면 가장 가까운 그 요일(오늘 포함) */
    fun nalDwi(t: String, now: Long): Int {
        if (t.contains("글피")) return 3
        if (t.contains("모레")) return 2
        if (t.contains("내일") || t.contains("낼")) return 1
        val m = Regex("([일월화수목금토])요일").find(t) ?: return 0
        val yo = "일월화수목금토".indexOf(m.groupValues[1][0]) + 1
        val oneul = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)
        return (yo - oneul + 7) % 7
    }

    fun got(t: String): Triple<String, Double, Double>? = GOT.firstOrNull { t.contains(it.first) }

    fun jugan(t: String) = Regex("이번\\s*주|주간|일주일|다음\\s*주|주말|며칠|앞으로|오일\\s*간|5일\\s*간").containsMatchIn(t)

    fun haneul(c: Int?): String = when {
        c == null -> ""
        c == 0 -> "맑음"; c == 1 -> "대체로 맑음"; c == 2 -> "구름 조금"; c == 3 -> "흐림"
        c == 45 || c == 48 -> "안개"; c in 51..57 -> "이슬비"
        c == 61 -> "약한 비"; c == 63 -> "비"; c == 65 -> "강한 비"; c == 66 || c == 67 -> "어는 비"
        c == 71 -> "약한 눈"; c == 73 -> "눈"; c == 75 -> "많은 눈"; c == 77 -> "싸락눈"
        c in 80..82 -> "소나기"; c == 85 || c == 86 -> "눈 소나기"; c >= 95 -> "천둥 번개"
        else -> ""
    }

    private fun meonji(p10: Double?, p25: Double?): String {
        val a = if (p10 == null) -1 else if (p10 <= 30) 0 else if (p10 <= 80) 1 else if (p10 <= 150) 2 else 3
        val b = if (p25 == null) -1 else if (p25 <= 15) 0 else if (p25 <= 35) 1 else if (p25 <= 75) 2 else 3
        val d = maxOf(a, b)
        if (d < 0) return ""
        return "미세먼지 " + listOf("좋음", "보통", "나쁨", "매우 나쁨")[d] + (if (d >= 2) ", 마스크를 쓰시는 것이 좋습니다" else "")
    }

    private fun usan(p: Int?): String = when {
        p == null -> ""
        p >= 60 -> "우산을 꼭 챙기십시오"
        p >= 30 -> "우산을 챙기시면 좋겠습니다"
        else -> "우산은 없어도 되겠습니다"
    }

    private fun osMal(hi: Double?): String = when {
        hi == null -> ""
        hi <= 5 -> "두꺼운 외투와 장갑을 챙기십시오"
        hi <= 12 -> "외투를 입으시는 것이 좋겠습니다"
        hi <= 19 -> "가벼운 겉옷을 챙기시면 좋겠습니다"
        hi <= 25 -> "얇은 긴팔 정도면 되겠습니다"
        else -> "가볍게 입으셔도 되겠습니다"
    }

    private fun d(o: JSONObject?, k: String, i: Int): Double? {
        val a = o?.optJSONArray(k) ?: return null
        return if (i < a.length() && !a.isNull(i)) a.optDouble(i) else null
    }
    private fun c(o: JSONObject?, k: String): Double? = if (o == null || !o.has(k) || o.isNull(k)) null else o.optDouble(k)

    /** 받아 쓸 주소(예보 7일, 오늘 남은 시간의 비 올 확률, 자외선) */
    fun juso(la: Double, lo: Double): String = "https://api.open-meteo.com/v1/forecast?latitude=" + String.format(java.util.Locale.US, "%.3f", la) +
        "&longitude=" + String.format(java.util.Locale.US, "%.3f", lo) +
        "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max,uv_index_max,snowfall_sum" +
        "&hourly=precipitation_probability&wind_speed_unit=ms&timezone=Asia%2FSeoul&forecast_days=7"
    fun meonjiJuso(la: Double, lo: Double): String = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + String.format(java.util.Locale.US, "%.3f", la) +
        "&longitude=" + String.format(java.util.Locale.US, "%.3f", lo) + "&current=pm10,pm2_5&timezone=Asia%2FSeoul"

    /** 대답 — 물으신 것을 먼저. w 는 예보, q 는 미세먼지(없어도 됨), gotIreum 은 도시 이름(내 자리면 빈 글) */
    fun dap(t: String, w: JSONObject, q: JSONObject?, now: Long, gotIreum: String): String {
        val dy = w.optJSONObject("daily")
        val cu = w.optJSONObject("current")
        val ap = if (gotIreum.isEmpty()) "" else "$gotIreum 날씨입니다. "
        // 이번 주·주말 — 날마다 한 줄씩
        if (jugan(t) && dy != null) {
            val jumal = t.contains("주말")
            val times = dy.optJSONArray("time")
            val jul = ArrayList<String>()
            val yoil = listOf("일", "월", "화", "수", "목", "금", "토")
            for (i in 0 until (times?.length() ?: 0)) {
                val cal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_MONTH, i) }
                val yo = cal.get(Calendar.DAY_OF_WEEK) - 1
                if (jumal && yo != 0 && yo != 6) continue
                val hi = d(dy, "temperature_2m_max", i); val lo = d(dy, "temperature_2m_min", i); val pb = d(dy, "precipitation_probability_max", i)
                jul.add((if (i == 0) "오늘 " else if (i == 1) "내일 " else "") + yoil[yo] + "요일 " + haneul(d(dy, "weather_code", i)?.toInt()) +
                    (if (hi != null) ", 최고 ${hi.roundToInt()}도" else "") + (if (lo != null) ", 최저 ${lo.roundToInt()}도" else "") +
                    (if (pb != null) ", 비 올 확률 ${pb.roundToInt()}퍼센트" else ""))
            }
            if (jul.isNotEmpty()) return ap + (if (jumal) "주말" else "이번 주") + " 날씨입니다. " + jul.joinToString(". ") + "."
        }
        val nal = nalDwi(t, now).coerceIn(0, 6)
        val nm = when (nal) { 0 -> "오늘"; 1 -> "내일"; 2 -> "모레"; else -> listOf("일", "월", "화", "수", "목", "금", "토")[Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_MONTH, nal) }.get(Calendar.DAY_OF_WEEK) - 1] + "요일" }
        val hi = d(dy, "temperature_2m_max", nal); val lo = d(dy, "temperature_2m_min", nal)
        var pb = d(dy, "precipitation_probability_max", nal)?.roundToInt()
        // 오늘은 지난 시간을 빼고 남은 시간의 비 올 확률
        var namun = false
        if (nal == 0) {
            val hr = w.optJSONObject("hourly"); val ht = hr?.optJSONArray("time"); val hp = hr?.optJSONArray("precipitation_probability")
            val jg = cu?.optString("time")?.take(13) ?: ""
            if (ht != null && hp != null && jg.length == 13) {
                var mx: Int? = null
                for (i in 0 until ht.length()) { val s = ht.optString(i); if (s.take(10) == jg.take(10) && s.take(13) >= jg && !hp.isNull(i)) { val v = hp.optInt(i); if (mx == null || v > mx) mx = v } }
                if (mx != null) { pb = mx; namun = true }
            }
        }
        val codeNal = d(dy, "weather_code", nal)?.toInt()
        val hnNal = haneul(codeNal)
        val hn = if (nal == 0) haneul(c(cu, "weather_code")?.toInt() ?: codeNal) else hnNal
        val bm = if (nal == 0) c(cu, "wind_speed_10m") else d(dy, "wind_speed_10m_max", nal)
        val uv = d(dy, "uv_index_max", nal)
        val nunOl = (d(dy, "snowfall_sum", nal) ?: 0.0) > 0.0 || hnNal.contains("눈")
        val mutBi = Regex("비|우산|우비|비옷|소나기|장마|뇌우|폭풍|태풍").containsMatchIn(t)
        val mutNun = Regex("(^|[^길])눈").containsMatchIn(t)
        val mutMeonji = Regex("먼지|황사|마스크").containsMatchIn(t)
        val mutOndo = Regex("추워|추운|춥|추울|더워|더운|덥|더울|쌀쌀|포근|기온|온도|몇\\s*도|체감|영하|영상|따뜻|선선|시원").containsMatchIn(t)
        val mutBaram = t.contains("바람")
        val mutOs = OS.containsMatchIn(t) || Regex("옷").containsMatchIn(t)
        val mutHaet = Regex("자외선|선크림|선로션|썬크림|선글라스|햇볕|햇빛").containsMatchIn(t)
        val mutSeup = t.contains("습도") || t.contains("습해") || t.contains("눅눅")

        val biMal = if (pb != null) "$nm${if (namun) " 남은 시간" else ""} 비 올 확률은 ${pb}퍼센트입니다. " + usan(pb) else ""
        val ondoMal = if (nal == 0 && c(cu, "temperature_2m") != null) {
            val on = c(cu, "temperature_2m")!!; val che = c(cu, "apparent_temperature")
            "지금 기온은 ${on.roundToInt()}도" + (if (che != null && abs(che - on) >= 2) ", 체감 온도는 ${che.roundToInt()}도" else "")
        } else ""
        val choeMal = if (hi != null && lo != null) "$nm 최고 ${hi.roundToInt()}도, 최저 ${lo.roundToInt()}도" else ""
        val hnMal = if (nal == 0) (if (hn.isNotEmpty()) "지금 하늘은 $hn" + (if (hnNal.isNotEmpty() && hnNal != hn) ", 오늘 하루는 $hnNal" else "") else "")
            else if (hnNal.isNotEmpty()) "${nm}${IljeongMal.eun(nm)} $hnNal" else ""
        val baramMal = if (bm != null && bm >= 7) "바람이 셉니다, 초속 ${bm.roundToInt()}미터" else if (mutBaram && bm != null) "바람은 초속 ${bm.roundToInt()}미터로 " + (if (bm >= 4) "조금 붑니다" else "약합니다") else ""
        val mjMal = if (nal == 0) meonji(c(q?.optJSONObject("current"), "pm10"), c(q?.optJSONObject("current"), "pm2_5")) else ""
        val uvMal = if (uv == null) "" else "$nm 자외선 지수는 ${uv.roundToInt()}, " + when { uv >= 8 -> "매우 강합니다. 선크림과 모자를 꼭 챙기십시오"; uv >= 6 -> "강합니다. 선크림을 바르시는 것이 좋겠습니다"; uv >= 3 -> "보통입니다"; else -> "약합니다" }
        val seupMal = if (nal == 0 && c(cu, "relative_humidity_2m") != null) "지금 습도는 ${c(cu, "relative_humidity_2m")!!.roundToInt()}퍼센트" else ""

        val apL = ArrayList<String>(); val dwi = ArrayList<String>()
        when {
            mutMeonji -> { apL.add(mjMal.ifEmpty { if (nal == 0) "미세먼지 정보를 받지 못했습니다" else "미세먼지는 오늘 것만 알려 드립니다" }); dwi.addAll(listOf(hnMal, ondoMal, choeMal)) }
            mutBi || mutNun -> { if (mutNun) apL.add(if (nunOl) "$nm 눈 소식이 있습니다" else "$nm 눈 소식은 없습니다"); apL.add(biMal); dwi.addAll(listOf(hnMal, ondoMal, choeMal, mjMal)) }
            mutHaet -> { apL.add(uvMal); dwi.addAll(listOf(hnMal, choeMal)) }
            mutBaram && bm != null -> { apL.add(if (bm >= 7) "$nm 바람이 셉니다, 초속 ${bm.roundToInt()}미터" else "$nm 바람은 초속 ${bm.roundToInt()}미터로 " + (if (bm >= 4) "조금 붑니다" else "약합니다")); dwi.addAll(listOf(hnMal, choeMal)) }
            mutOs -> { apL.addAll(listOf(ondoMal, choeMal, osMal(hi))); dwi.addAll(listOf(hnMal, if (pb != null && pb >= 30) biMal else "")) }
            mutSeup -> { apL.add(seupMal); dwi.addAll(listOf(hnMal, ondoMal)) }
            mutOndo -> { apL.addAll(listOf(ondoMal, choeMal)); dwi.addAll(listOf(hnMal, if (pb != null && pb >= 30) biMal else "", mjMal)) }
            else -> { apL.addAll(listOf(hnMal, ondoMal, choeMal)); dwi.addAll(listOf(if (pb != null) biMal else "", mjMal)) }
        }
        if (baramMal.isNotEmpty() && !(mutBaram && bm != null)) dwi.add(baramMal)
        val mal = (apL + dwi).filter { it.isNotBlank() }.joinToString(". ")
        if (mal.isBlank()) return ""
        return (ap + mal + ".").replace("..", ".")
    }
}
