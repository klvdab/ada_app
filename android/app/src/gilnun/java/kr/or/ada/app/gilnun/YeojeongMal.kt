// 안드로이드 길눈 — 말로 하기에서 여정 다루기(묶음 b1, 아이폰 MalHagi.swift 의 여정·탈것·즐겨찾기 부분을 같은 말로)
// MalHagi.kt 는 함께 쓰는 파일이라 여기서 고치지 않고, 이 부품을 MalHagi.cheori 가 부르게 합니다(통합 쪽지 _tonghap/b1_yeojeong.md).
//   meonjeo      — 여정 끝·도착, "점지도로 걸을까요"의 대답(MalHagi 0번 하던 일 멈추기 바로 뒤에서 부름)
//   myeongryeong — 내렸어·탔어·얼마나 걸려·지금 가는 길·즐겨찾기·탈것만 말씀하심·어떻게 가실지의 대답
//   gagi         — 찾은 곳으로 탈것에 맞춰 안내 시작(걸어서면 맞는 점지도를 먼저 여쭘)
// 아이폰과 다른 점: 차를 요청할까요 묻기 대신, 차로 가실 때 "복지콜에 전화해 줘"로 이 지역 센터를 부를 수 있다고 알려 드림(2.8.0 교통편 부르기)
package kr.or.ada.app.gilnun

object YeojeongMal {
    private enum class Mureum { EOPSEUM, JEOM, BANGSIK }

    private var mureum = Mureum.EOPSEUM
    private var mureumTtae = 0L
    /** 말씀하신 가실 곳(아직 여정이 없을 때) */
    var mok: Jangso? = null
        private set
    /** 탈것만 먼저 말씀하셨을 때 기억 */
    private var talgeotDaegi: Talgeot? = null

    /** 하던 일 멈추기 때 — 묻던 말과 기다리던 목적지·탈것을 모두 비움 */
    fun mureumChoGihwa() {
        mureum = Mureum.EOPSEUM
        mok = null
        talgeotDaegi = null
    }

    private fun yeoJjum(m: Mureum) {
        mureum = m
        mureumTtae = System.currentTimeMillis()
    }

    private fun hwalseong(): GilnunActivity? = MalHagi.hwalseong?.get()

    /** 여정 끝·도착, 점지도로 걸을까요의 대답 — 하면 참 */
    fun meonjeo(alts: List<String>, z: String, dap: (String, Boolean) -> Unit): Boolean {
        if (System.currentTimeMillis() - mureumTtae > 180000) mureum = Mureum.EOPSEUM
        val s = MalSajeon
        val y = YeojeongEngine.jigeum
        // 여정 끝내기(여정이 있을 때만 — 없으면 MalHagi 의 따라 걷기 끝으로 넘김)
        if (y != null && s.itda(alts, "yeojeong_kkeut")) {
            mureum = Mureum.EOPSEUM
            dap("", false)
            AnnaeEngine.kkeut()
            return true
        }
        // 도착 — "도착", "도착했어", "다 왔어"를 목적지 이름으로 찾지 않고 여정 끝내기로(이사장님 승인 1)
        val dochakMal = setOf("도착", "도착했어", "도착했다", "도착했어요", "도착했습니다", "도착이야", "도착했네", "다왔어", "다왔다", "다왔어요", "다왔습니다", "다왔네", "도착완료", "여기도착")
        if (y != null && (dochakMal.contains(z) || (z.startsWith("도착") && z.length <= 6 && !z.contains("까지") && !z.contains("시간")))) {
            mureum = Mureum.EOPSEUM
            dap("도착하셨습니다. 여정을 마칩니다.", false)
            AnnaeEngine.kkeut()
            return true
        }
        // 점지도로 걸을까요의 대답
        if (JeomEngine.muleum != null) {
            val ye0 = s.tteut(alts, "ye") != null
            val ani0 = s.tteut(alts, "ani") != null
            if (z.contains("위성") || (mureum == Mureum.JEOM && ani0 && !ye0 && z.length <= 10)) {
                mureum = Mureum.EOPSEUM
                dap("위성으로 걷습니다.", false)
                JeomEngine.muleumDap(false)
                return true
            }
            if (z.contains("점지도") || (mureum == Mureum.JEOM && ye0 && !ani0 && z.length <= 10)) {
                mureum = Mureum.EOPSEUM
                dap("점지도로 걷습니다.", false)
                JeomEngine.muleumDap(true)
                return true
            }
        }
        return false
    }

    /** 여정 명령 — 하면 참 */
    fun myeongryeong(alts: List<String>, z: String, dap: (String, Boolean) -> Unit): Boolean {
        val s = MalSajeon
        val y = YeojeongEngine.jigeum
        val tg = talgeotChatgi(z)
        // 어떻게 가실지 여쭌 말의 대답 — "지하철로", "버스로"
        val m0 = mok
        if (mureum == Mureum.BANGSIK && m0 != null && tg != null && z.length <= 10) {
            mureum = Mureum.EOPSEUM
            gagi(m0, tg, dap)
            return true
        }
        if (s.itda(alts, "naerim")) {
            if (y != null && (y.danggye == Danggye.TANEUN_JUNG || y.danggye == Danggye.TANEUN_GOT_KKAJI)) {
                dap("", false)
                AnnaeEngine.naeryeotda()
                hwalseong()?.cheotHwamyeonEuro(null)
            } else {
                dap("지금 타고 가시는 여정이 없습니다. 가실 곳을 말씀해 주시면 안내하겠습니다.", false)
            }
            return true
        }
        if (s.itda(alts, "tatda")) {
            val m = mok
            if (y != null) {
                dap("", false)
                when {
                    y.jiha != null -> JihacheolEngine.tatda(false)
                    y.beoseu != null -> AnnaeEngine.beoseuTatda()
                    else -> AnnaeEngine.chaTatda()
                }
                hwalseong()?.cheotHwamyeonEuro(null)
            } else if (m != null) {
                dap("", false)
                AnnaeEngine.chaTagi(m)
                hwalseong()?.cheotHwamyeonEuro(null)
            } else {
                talgeotDaegi = Talgeot.CHA
                dap("차 안 안내를 하겠습니다. ${Seoljeong.ho}, 어디로 가십니까?", true)
            }
            return true
        }
        if (s.itda(alts, "sigan")) {
            if (y == null) dap("아직 가시는 곳이 없습니다. ${Seoljeong.ho}, 어디로 가실까요?", true)
            else dap(geollineunMal(), false)
            return true
        }
        if (s.itda(alts, "jigeum_gil") || s.itda(alts, "charye")) {
            if (y != null) {
                dap("", false)
                AnnaeEngine.hyeonhwang()
            } else {
                dap("지금 가시는 길이 없습니다. ${Seoljeong.ho}, 어디로 가실까요?", true)
            }
            return true
        }
        if (s.itda(alts, "jeulgyeo_mok")) {
            val l = Jeulgyeo.mokrok.take(5).map { it.ireum }
            if (l.isEmpty()) {
                dap("즐겨찾기가 비어 있습니다. 가시는 곳에서 즐겨찾기에 담아 줘라고 말씀하시면 담아 둡니다.", false)
            } else {
                dap("즐겨찾기에 담긴 곳은 ${l.joinToString(", ")}입니다. ${Seoljeong.ho}, 어디로 가실까요?", true)
            }
            return true
        }
        if (s.itda(alts, "jeulgyeo_dam")) {
            val j: Jangso? = y?.mokjeok?.jangso ?: mok
            if (j != null) {
                if (Jeulgyeo.damgi(j)) dap("${j.ireum}${MalHagi.eul(j.ireum)} 즐겨찾기에 담았습니다.", false)
                else dap("${j.ireum}${MalHagi.eun(j.ireum)} 이미 즐겨찾기에 있습니다.", false)
            } else {
                dap("담을 곳이 없습니다. 먼저 가실 곳을 말씀해 주십시오.", false)
            }
            return true
        }
        // 탈것만 말씀하심 — "걸어가자", "지하철로 가자"
        if (tg != null && z.length <= 8) {
            val m = mok
            if (y != null) {
                if (tg == Talgeot.GEOREUM) {
                    dap("", false)
                    if (y.danggye == Danggye.TANEUN_JUNG) AnnaeEngine.naeryeotda() else AnnaeEngine.georeoGagi()
                    hwalseong()?.cheotHwamyeonEuro(null)
                } else {
                    gagi(y.mokjeok.jangso, tg, dap)
                }
            } else if (m != null) {
                gagi(m, tg, dap)
            } else {
                talgeotDaegi = tg
                dap("${Seoljeong.ho}, 어디로 가실지 먼저 말씀해 주십시오.", true)
            }
            return true
        }
        return false
    }

    /** 즐겨찾기 이름이 말 속에 있으면 그곳(긴 이름부터) — MalHagi 가 가실 곳을 찾기 전에 볼 수 있음 */
    fun jeulgyeoChatgi(z: String): Jangso? {
        val l = Jeulgyeo.mokrok.sortedByDescending { it.ireum.length }
        return l.firstOrNull { val nz = MalSajeon.ttuk(it.ireum); nz.length >= 2 && z.contains(nz) }?.jangso
    }

    /** 탈것에 맞춰 안내 시작 — 대답을 먼저 하고 안내 엔진을 움직임(말 차례가 맞게). tg0 이 없으면 말 속에서 찾은 것이나 기억한 것 */
    fun gagi(j: Jangso, tg0: Talgeot?, dap: (String, Boolean) -> Unit, apMal: String = "") {
        mok = j
        Jeulgyeo.sseum(j)
        val w = Wichi.jigeum
        val d: Double? = if (w != null) Wichi.geori(w.lat, w.lon, j.lat, j.lon) else null
        val yj = YeojeongEngine
        val yy = yj.jigeum
        val taneunJung = yj.sokdoChujeong != Talgeot.GEOREUM || (yy != null && yy.danggye == Danggye.TANEUN_JUNG && yy.jiha == null)
        var tg = tg0 ?: talgeotDaegi
        talgeotDaegi = null
        if (tg == null) {
            if (taneunJung) tg = if (yj.sokdoChujeong == Talgeot.JIHACHEOL) Talgeot.JIHACHEOL else Talgeot.CHA   // 2.9.0 땅속에서 타고 가는 중이면 지하철로
            else if (d == null || d <= 2000) tg = Talgeot.GEOREUM
        }
        val mk = Mokjeok(j.ireum, j.lat, j.lon, j.juso)
        val a = hwalseong()
        if (tg == null) {
            yj.jeonghagi(mk)
            a?.cheotHwamyeonEuro(null)
            yeoJjum(Mureum.BANGSIK)
            dap(apMal + "걸어가시기에는 먼 곳입니다. ${Seoljeong.ho}, 차로, 지하철로, 버스로 가운데 어떻게 가실까요?", true)
            return
        }
        when (tg) {
            Talgeot.GEOREUM -> {
                // 맞는 점지도가 있으면 "점지도로 걸을까요, 위성으로 걸을까요" 한 번 여쭘(네 하시면 점지도)
                a?.cheotHwamyeonEuro(null)
                AnnaeEngine.georeoGagiBoda(j) { q ->
                    if (q != null) {
                        yeoJjum(Mureum.JEOM)
                        dap(apMal + q, true)
                    } else {
                        dap(apMal, false)
                    }
                }
            }
            Talgeot.CHA, Talgeot.GICHA, Talgeot.GOSOKBEOSEU -> {
                if (taneunJung) {
                    dap(apMal + "타고 가시는 중이니 차 안 안내로 잇습니다.", false)
                    AnnaeEngine.chaTagi(j)
                } else {
                    yj.jeonghagi(mk)
                    dap(apMal + "차를 부르시려면 복지콜에 전화해 줘라고 말씀하십시오. 이 지역 센터로 걸어 드립니다. 차에 타시면 차에 탔어라고 말씀해 주십시오. 그때부터 차 안 안내를 합니다.", false)
                }
                a?.cheotHwamyeonEuro(null)
            }
            Talgeot.JIHACHEOL -> {
                // 2.9.0 이미 열차를 타고 가는 중이면 역 입구 안내를 건너뛰고 곧장 역 알림(아이폰 2.40.0과 같음)
                if (TalgeotGamji.chujeong == Talgeot.JIHACHEOL || (TalgeotGamji.jiha && TalgeotGamji.jigeumUmjigim != "걸음")) {
                    Sori.mal(apMal + "타고 가시는 중이니 지하철 길을 찾아 곧장 역 알림을 시작합니다.", MalGeup.JEONGBO)
                    JihacheolEngine.jungganSijak(j) { ok, mal ->
                        hwalseong()?.cheotHwamyeonEuro(null)
                        dap(if (ok) mal else "$mal 잠시 뒤 다시 지하철로 가자고 말씀해 주십시오.", false)
                    }
                    return
                }
                Sori.mal(apMal + "지하철 길을 찾는 중입니다.", MalGeup.JEONGBO)
                JihacheolEngine.gilChatgi(j) { gg, k ->
                    if (gg != null) {
                        dap("${gg.mal} 들어갈 곳은 ${gg.ipgu.ireum}입니다.", false)
                        AnnaeEngine.jihacheolGagi(j, gg)
                        hwalseong()?.cheotHwamyeonEuro(null)
                    } else {
                        yj.jeonghagi(mk)
                        yeoJjum(Mureum.BANGSIK)
                        dap("$k 걸어서, 차로, 버스로 가운데 어떻게 가실까요?", true)
                    }
                }
            }
            Talgeot.BEOSEU -> {
                if (w == null) {
                    dap(apMal + "아직 위치를 잡는 중이라 가까운 정류장을 찾지 못했습니다. 잠시 뒤 다시 말씀해 주십시오.", false)
                    return
                }
                Sori.mal(apMal + "가까운 정류장을 찾는 중입니다.", MalGeup.JEONGBO)
                Beoseu.gakkaun(w.lat, w.lon) { r ->
                    val jr = r?.firstOrNull()
                    if (jr != null) {
                        dap("가장 가까운 정류장은 ${Beoseu.julMal(jr)}입니다.", false)
                        AnnaeEngine.beoseuGagi(j, jr)
                        hwalseong()?.cheotHwamyeonEuro(null)
                    } else {
                        yj.jeonghagi(mk)
                        yeoJjum(Mureum.BANGSIK)
                        dap("가까운 정류장을 찾지 못했습니다. 걸어서, 차로, 지하철로 가운데 어떻게 가실까요?", true)
                    }
                }
            }
        }
    }

    /** 말 속의 탈것(아이폰 talgeotChatgi) — z 는 MalSajeon.ttuk 한 말 */
    fun talgeotChatgi(z: String): Talgeot? {
        if (z.contains("지하철") || z.contains("전철")) return Talgeot.JIHACHEOL
        if (z.contains("버스") && !z.contains("버스터미널") && !z.contains("고속버스")) return Talgeot.BEOSEU
        if (z.contains("걸어") || z.contains("도보") || z.contains("걷자")) return Talgeot.GEOREUM
        if (z.contains("택시") || z.contains("차타고") || z.startsWith("차로") || z.contains("차로가") || z.contains("타고가")) return Talgeot.CHA
        return null
    }

    /** 남은 거리와 대략 걸리는 시간(길 막힘은 넣지 못한 셈) */
    fun geollineunMal(): String {
        val y = YeojeongEngine.jigeum ?: return "아직 가시는 곳이 없습니다."
        val w = Wichi.jigeum ?: return "아직 위치를 잡는 중입니다. 잠시 뒤 다시 물어 주십시오."
        val d = Wichi.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon)
        val t = YeojeongEngine.talgeot
        val (sokdo, doragam) = when (t) {
            Talgeot.GEOREUM -> 1.1 to 1.3
            Talgeot.JIHACHEOL -> 8.5 to 1.3
            Talgeot.BEOSEU -> 5.0 to 1.4
            Talgeot.GICHA -> 25.0 to 1.2
            Talgeot.GOSOKBEOSEU -> 20.0 to 1.2
            Talgeot.CHA -> 7.0 to 1.35
        }
        val bun = maxOf(1, Math.round(d * doragam / sokdo / 60).toInt())
        val bunMal = if (bun < 60) "${bun}분" else (if (bun % 60 == 0) "${bun / 60}시간" else "${bun / 60}시간 ${bun % 60}분")
        val tal = if (t == Talgeot.GEOREUM) "걸어서" else "${t.ireum}${ro(t.ireum)}"
        return "${y.mokjeok.ireum}까지 남은 거리는 ${Annae.geoMal(d)}, $tal 대략 $bunMal 걸립니다. 길 막힘은 넣지 못한 셈입니다."
    }

    /** 으로·로 — 받침이 없거나 ㄹ 받침이면 로 */
    private fun ro(w: String): String {
        val ch = w.lastOrNull() ?: return "로"
        val c = ch.code - 0xAC00
        if (c < 0 || c >= 11172) return "로"
        val j = c % 28
        return if (j == 0 || j == 8) "로" else "으로"
    }
}
