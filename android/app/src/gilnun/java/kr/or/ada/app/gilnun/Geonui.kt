// 건의와 소감 말하기 — 안드로이드 길눈 2.106.0 (빌드 261126-A96, 대장클, 이사장님 승인 2026-11-26 「이번 판에 꼭 넣어서 통과 받고 앱 공개하자」, 아이폰 2.141.0과 같음)
// 「길눈아, 건의할게」·「소감 말할게」 → 「네, 말씀하십시오. 다 하시면 끝이라고 해 주십시오」 → 말씀 도중 쉬셔도 이어 받아 적고(2분까지) →
//   「이렇게 받아 적었습니다 … 보낼까요?」 → 네면 보냄, 다시면 처음부터, 아니요면 버림.
// 누가(설정의 내 이름·폰 번호표), 어디서(시·구까지만), 어떤 폰·어떤 판, 언제, 어떤 상황(하던 일·바로 앞 기록)을 함께 남김.
// 처음 한 번만 이름과 사는 구를 함께 보내도 되는지 여쭘. 보내는 길은 앱 기록(applog)과 같아 통신이 끊겨도 쌓였다 감.
package kr.or.ada.app.gilnun

import android.content.Context
import android.os.Build
import org.json.JSONArray

object GeonuiEngine {
    enum class Dangye { EOPSEUM, DONGUI, BATNEUN, HWAGIN }
    var dangye = Dangye.EOPSEUM
        private set
    private val jul = ArrayList<String>()
    private var sijakTtae = 0L
    private var majimakTtae = 0L
    private var sanghwang: Map<String, Any?> = emptyMap()
    private var ctx: Context? = null

    fun junbi(c: Context) { ctx = c.applicationContext }

    private var dongui: Boolean?
        get() {
            val p = ctx?.getSharedPreferences("gilnun_geonui", Context.MODE_PRIVATE) ?: return null
            return if (p.contains("dongui")) p.getBoolean("dongui", false) else null
        }
        set(v) { ctx?.getSharedPreferences("gilnun_geonui", Context.MODE_PRIVATE)?.edit()?.apply { if (v == null) remove("dongui") else putBoolean("dongui", v) }?.apply() }

    /** 지금 건의를 주고받는 중인가(3분 넘게 말씀이 없으면 저절로 풂) */
    val jinhaengJung: Boolean
        get() {
            if (dangye != Dangye.EOPSEUM && System.currentTimeMillis() - majimakTtae > 180_000) chogihwa()
            return dangye != Dangye.EOPSEUM
        }

    fun sijakMal(z: String): Boolean {
        if (z.length > 24) return false
        val k = listOf("건의할게", "건의할께", "건의하고싶", "건의사항", "건의좀", "건의할래", "건의해도", "건의하자", "건의하기",
            "소감말할", "소감을말", "소감말하", "소감할게", "후기말할", "후기남길", "사용후기", "평가할게", "의견말할", "의견드릴",
            "길눈에바라", "길눈한테바라", "고쳐달라고말", "고칠것말할", "하고싶은말있")
        return k.any { z.contains(it) }
    }

    fun sijak(): Pair<String, Boolean> {
        chogihwa()
        sanghwang = sanghwangMoeugi()
        majimakTtae = System.currentTimeMillis()
        Girok.namgi("geonui_sijak")
        if (dongui == null) {
            dangye = Dangye.DONGUI
            return "건의를 보내실 때 설정에 적어 두신 내 이름, 사시는 구, 폰 기종을 함께 보냅니다. 괜찮으시면 네, 이름 없이 보내시려면 아니요라고 말씀해 주십시오." to true
        }
        dangye = Dangye.BATNEUN
        sijakTtae = System.currentTimeMillis()
        return "네, 말씀하십시오. 다 하시면 끝이라고 해 주십시오." to true
    }

    /** 대답: first 가 null 이면 소리 없이 다시 들음(딩동), 아니면 (할 말, 이어 들을지) */
    fun deureum(alts: List<String>): Pair<String, Boolean>? {
        majimakTtae = System.currentTimeMillis()
        val t = (alts.firstOrNull() ?: "").trim()
        val z = MalSajeon.ttuk(t)
        when (dangye) {
            Dangye.EOPSEUM -> return "" to false
            Dangye.DONGUI -> {
                when {
                    ne(z) -> dongui = true
                    aniyo(z) -> dongui = false
                    else -> return "네 또는 아니요로 말씀해 주십시오. 이름과 사시는 구를 함께 보내도 될까요?" to true
                }
                dangye = Dangye.BATNEUN
                sijakTtae = System.currentTimeMillis()
                return (if (dongui == true) "고맙습니다. " else "이름 없이 보내겠습니다. ") + "말씀하십시오. 다 하시면 끝이라고 해 주십시오." to true
            }
            Dangye.BATNEUN -> {
                if (kkeutMal(z)) return if (jul.isEmpty()) chwiso("말씀하신 것이 없어 건의를 마칩니다.") else hwaginYeojum()
                if (t.isNotEmpty()) jul.add(t)
                if (System.currentTimeMillis() - sijakTtae > 120_000) return hwaginYeojum()
                return null
            }
            Dangye.HWAGIN -> {
                if (dasiMal(z)) {
                    jul.clear(); dangye = Dangye.BATNEUN; sijakTtae = System.currentTimeMillis()
                    return "처음부터 다시 받아 적겠습니다. 말씀하십시오. 다 하시면 끝이라고 해 주십시오." to true
                }
                if (aniyo(z) || z.contains("취소") || z.contains("보내지마")) return chwiso("보내지 않고 지웠습니다.")
                if (ne(z) || z.contains("보내")) {
                    bonaegi()
                    return "보냈습니다. 협회에서 잘 살펴 길눈을 고쳐 가겠습니다. 고맙습니다." to false
                }
                if (z.contains("더") && z.length <= 8) {
                    dangye = Dangye.BATNEUN
                    return "네, 이어서 말씀하십시오. 다 하시면 끝이라고 해 주십시오." to true
                }
                return "보내시려면 네, 처음부터 다시 하시려면 다시, 보내지 않으시려면 아니요라고 말씀해 주십시오." to true
            }
        }
        @Suppress("UNREACHABLE_CODE")
        return null
    }

    /** 듣는 동안 말씀이 끊겼을 때 */
    fun chimmuk(): Pair<String, Boolean> {
        majimakTtae = System.currentTimeMillis()
        return when (dangye) {
            Dangye.BATNEUN -> if (jul.isEmpty()) chwiso("말씀이 들리지 않아 건의를 마칩니다. 다시 하시려면 길눈아, 건의할게라고 불러 주십시오.") else hwaginYeojum()
            Dangye.DONGUI, Dangye.HWAGIN -> chwiso("대답이 없어 건의를 보내지 않았습니다. 다시 하시려면 길눈아, 건의할게라고 불러 주십시오.")
            Dangye.EOPSEUM -> "" to false
        }
    }

    private fun hwaginYeojum(): Pair<String, Boolean> {
        dangye = Dangye.HWAGIN
        return "이렇게 받아 적었습니다. ${jul.joinToString(" ")}. 보낼까요? 네, 다시, 아니요 가운데 말씀해 주십시오. 덧붙이시려면 더라고 하십시오." to true
    }

    private fun chwiso(m: String): Pair<String, Boolean> {
        Girok.namgi("geonui_chwiso", mapOf("jul" to jul.size))
        chogihwa()
        return m to false
    }

    private fun chogihwa() { dangye = Dangye.EOPSEUM; jul.clear() }

    private fun bonaegi() {
        val d = HashMap<String, Any?>(sanghwang)
        d["mal"] = jul.joinToString(" ").take(2000)
        d["cho"] = ((System.currentTimeMillis() - sijakTtae) / 1000).toInt()
        d["dongui"] = dongui ?: false
        if (dongui == true) {
            d["ireum"] = Seoljeong.naIrum
            d["hoching"] = Seoljeong.hoching
            KolJiyeok.majimak?.let { j -> d["gu"] = (listOf(j.sido) + j.sigungu.take(1)).joinToString(" ") }   // 시·도와 시·군·구까지만
        }
        Girok.namgi("geonui", d)
        Girok.bonaegi()
        chogihwa()
    }

    private fun sanghwangMoeugi(): Map<String, Any?> {
        val d = HashMap<String, Any?>()
        d["gijong"] = Build.MANUFACTURER + " " + Build.MODEL
        d["android_pan"] = Build.VERSION.RELEASE
        d["app_pan"] = Pan.pan
        YeojeongEngine.jigeum?.let { y -> d["yeojeong"] = y.mokjeok.ireum + " / " + y.danggye.name }
        if (MalgilEngine.geotneun) d["malgil"] = MalgilEngine.jemok
        if (Bangsong.itda) d["bangsong"] = true
        d["apgirok"] = JSONArray(Girok.choegeun())
        return d
    }

    fun ne(z: String) = z in setOf("네", "예", "응", "좋아", "괜찮아", "그래", "네네", "보내", "보내줘", "네보내", "그래요", "좋아요", "괜찮아요") || z.startsWith("네") || z.startsWith("예")
    fun aniyo(z: String) = z.startsWith("아니") || z.startsWith("싫") || z == "안돼" || z == "아뇨"
    fun dasiMal(z: String) = z == "다시" || z.startsWith("다시할") || z.startsWith("다시말") || z == "처음부터"
    fun kkeutMal(z: String) = z in setOf("끝", "끝이야", "끝이에요", "끝입니다", "끝났어", "다했어", "다했어요", "이상이야", "이상입니다", "그게다야", "여기까지", "끝끝") ||
        (z.length <= 6 && z.endsWith("끝"))
}
