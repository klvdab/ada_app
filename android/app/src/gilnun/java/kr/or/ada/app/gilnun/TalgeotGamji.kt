package kr.or.ada.app.gilnun

// 2.9.0 (261003-T1, 이사장님 지시 "지하철을 타면 자동으로 걷는지 버스인지 자동차인지 지하철인지 알아챌 수 있는 능력은 되는 거 아니니")
// 탈것 저절로 알아채기 — 아이폰 TalgeotGamji.swift 와 같은 판단. 위성 빠르기에만 기대지 않음
//   걸음 센서: 15초 동안 걸음이 이어지면 걸음(묵은 "차" 판단을 바로 지움)
//   가속도: 걸음 없이 흔들리며 움직임이 20초 이어지면 탈것
//   기압계: 90초 안에 3.5미터 넘게 내려가면 땅속(계단·에스컬레이터)
//   탈것이면 — 땅속이거나 위성이 30초 넘게 끊겼거나 역 200미터 안에서 탔으면 지하철, 버스 정류장 25미터 안에서 두 번 넘게 섰다 떠나면 버스, 아니면 차
//   2.31.0 역 200미터는 위성이 좋지 않을 때만, 위성 끊김은 땅 위에서 차로 알아채기 전에만, 콜 배차 뒤에는 차로 못 박음
//   2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 땅속은 내려가는 동안 걸음이 늘어야(승강기 빼냄), 30분 넘게 위성·탈것 없으면 땅속 판단 지움,
//          역 근처 판단은 이번에 타고 처음 판단할 때만, 서서 폰을 만지는 흔들림을 탈것으로 보지 않음, 여정 끝·하던 일 멈춤 때 처음부터(saeroSijak),
//          바로잡으신 탈것을 받음(barojapgi)

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.math.sqrt

object TalgeotGamji : SensorEventListener {
    private val main = Handler(Looper.getMainLooper())
    private var sm: SensorManager? = null
    private var dolgo = false

    var chujeong: Talgeot = Talgeot.GEOREUM
        private set
    /** 땅속에 있음(기압으로 내려간 뒤 아직 땅 위로 나오지 않음) */
    var jiha = false
        private set
    /** 땅속으로 내려가기 직전의 땅 위 자리 */
    var jisangJari: Jari? = null
        private set
    var chaSijakJari: Jari? = null
        private set
    /** 지금 움직임: 걸음, 탈것, 멈춤, 모름 */
    var jigeumUmjigim = "모름"
        private set
    /** 탈것 안에서 섰다 떠남(초) — 지하철 역 세기 */
    var seotdaTteonam: ((Double) -> Unit)? = null
    /** 땅속에 들어감 */
    var jihaJinip: (() -> Unit)? = null

    private val chang = ArrayList<Double>()
    private var heundeulim = false
    private val georeumGirok = ArrayList<Pair<Long, Int>>()   // (때, 걸음 누적)
    private var georeumSijak = 0L
    private var chaSijak = 0L
    private var meomchumSijak = 0L
    private var yeokGeuncheo = false
    private var jeongryujangSeom = 0
    private val gido = ArrayList<Pair<Long, Double>>()
    private var jihaMin = 0.0
    // 2.30.0 (261009-A18, 이사장님 승인) 마지막으로 걸음이 늘어난 때 — 걸어서 내려간 때만 땅속으로 봄
    private var majimakGeoreumTtae = 0L
    private var jeonGeoreumSu = -1
    /** 2.31.0 마지막으로 탈것이 움직인 때 — 새 목적지를 정할 때 묵은 판단을 지울지 가림 */
    var majimakTalgeot = 0L
        private set
    /** 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 땅속에 들어간 때 — 30분 넘게 위성·탈것이 없으면 땅속 판단을 지움 */
    private var jihaTtae = 0L
    /** 2.32.0 기압을 받을 때마다 남기는 걸음 — 내려가는 동안 걸음이 늘었는지 봄(승강기는 걸음이 없음) */
    private val gidoGeoreum = ArrayList<Pair<Long, Int>>()
    private var georeumNeuneunTtae = 0L
    private var gidoJeonGeoreum = -1
    /** 2.32.0 센 흔들림(표준편차 0.08g 넘게)이 이어진 때 — 폰을 만지작거리는 잔 흔들림을 탈것으로 보지 않게 */
    private var ganghanSijak = 0L
    private var ganghanMajimak = 0L

    val wiseongJoeum: Boolean
        get() {
            val t = Wichi.majimakWiseongTtae
            return t > 0 && System.currentTimeMillis() - t < 15000 && (Wichi.jigeum?.ochae ?: 999.0) <= 30
        }
    private val wiseongEopseum: Boolean
        get() { val t = Wichi.majimakWiseongTtae; return t == 0L || System.currentTimeMillis() - t > 30000 }

    fun sijak(c: Context) {
        if (dolgo) return
        dolgo = true
        val s = c.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sm = s
        s.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { s.registerListener(this, it, 100000, main) }   // 0.1초
        s.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { s.registerListener(this, it, 1000000, main) }       // 1초
        main.postDelayed(tik, 5000)
    }

    private val tik = object : Runnable {
        override fun run() { pandan(); main.postDelayed(this, 5000) }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = e.values[0].toDouble(); val y = e.values[1].toDouble(); val z = e.values[2].toDouble()
                chang.add(sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH)
                if (chang.size > 30) chang.removeAt(0)
                if (chang.size >= 20) {
                    val p = chang.sum() / chang.size
                    val pc = sqrt(chang.sumOf { (it - p) * (it - p) } / chang.size)
                    heundeulim = pc > 0.04
                    // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 센 흔들림이 이어지는지(2초 넘게 끊기면 처음부터)
                    val now = System.currentTimeMillis()
                    if (pc > 0.08) {
                        if (ganghanSijak == 0L || now - ganghanMajimak > 2000) ganghanSijak = now
                        ganghanMajimak = now
                    }
                }
            }
            Sensor.TYPE_PRESSURE -> gidoBatda(e.values[0].toDouble())
        }
    }

    // MARK: 5초마다 판단

    private fun pandan() {
        val now = System.currentTimeMillis()
        if (!jiha && wiseongJoeum) jisangJari = Wichi.jigeum
        georeumGirok.add(now to Wichi.georeumSu)
        if (jeonGeoreumSu >= 0 && Wichi.georeumSu > jeonGeoreumSu) majimakGeoreumTtae = now
        jeonGeoreumSu = Wichi.georeumSu
        georeumGirok.removeAll { now - it.first > 25000 }
        val georeum15 = georeumGirok.lastOrNull()!!.second - (georeumGirok.firstOrNull { now - it.first <= 16000 }?.second ?: Wichi.georeumSu)
        val georeum20 = georeumGirok.lastOrNull()!!.second - (georeumGirok.firstOrNull()?.second ?: Wichi.georeumSu)
        val w = Wichi.jigeum
        val ppareum = w != null && wiseongJoeum && w.sokdo * 3.6 > 15
        // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 위성이 약한 데서 서서 폰을 만지는 흔들림을 탈것으로 보던 것 —
        //   흔들림만으로 탈것으로 보려면 위성 빠르기를 모르고(위성이 좋으면 빠르기로만 봄), 센 흔들림(0.08g 넘게)이 10초 넘게 이어지고, 20초 동안 걸음이 하나도 없어야 함.
        //   위성이 좋고 시속 5킬로미터 아래면 흔들림과 상관없이 멈춤
        val wiseongSokdo = w != null && wiseongJoeum
        val ganghanJisok = ganghanSijak > 0 && now - ganghanMajimak <= 2000 && now - ganghanSijak >= 10000
        val heundeulimCha = !wiseongSokdo && ganghanJisok && georeum20 == 0

        if (georeum15 >= 12) {
            jigeumUmjigim = "걸음"
            chaSijak = 0L; meomchumSijak = 0L
            if (georeumSijak == 0L) georeumSijak = now
            if (now - georeumSijak >= 15000 && chujeong != Talgeot.GEOREUM) {
                jeongryujangSeom = 0; yeokGeuncheo = false
                bakkugi(Talgeot.GEOREUM, "걷기 15초")
            }
            jihaHwagin()
        } else if (georeum20 <= 3 && (ppareum || heundeulimCha)) {
            jigeumUmjigim = "탈것"
            majimakTalgeot = now
            georeumSijak = 0L
            if (meomchumSijak > 0) {
                val t = (now - meomchumSijak) / 1000.0
                meomchumSijak = 0L
                if (t in 5.0..120.0) meomchumKkeut(t)
            }
            if (chaSijak == 0L) { chaSijak = now; chaSijakJari = Wichi.jigeum; yeokGeuncheoBoda() }
            if (now - chaSijak >= 20000) chongPandan("탈것 20초")
        } else if (georeum20 <= 3) {
            jigeumUmjigim = "멈춤"
            // 2.25.0 오래(1분 넘게) 멈춰 있으면 띄엄띄엄 만지작거린 흔들림이 탈것 20초로 쌓이지 않게 처음부터
            if (chaSijak > 0 && meomchumSijak > 0 && now - meomchumSijak > 60000 && chujeong == Talgeot.GEOREUM) chaSijak = 0L
            if ((chujeong != Talgeot.GEOREUM || chaSijak > 0) && meomchumSijak == 0L) meomchumSijak = now
        }
    }

    private fun meomchumKkeut(t: Double) {
        seotdaTteonam?.invoke(t)
        if (chujeong == Talgeot.JIHACHEOL || jiha || !wiseongJoeum) return
        val w = Wichi.jigeum ?: return
        Beoseu.gakkaun(w.lat, w.lon) { l ->
            if (l != null && l.any { Wichi.geori(w.lat, w.lon, it.lat, it.lon) <= 25 }) jeongryujangSeom += 1
            if (chujeong == Talgeot.CHA && jeongryujangSeom >= 2) bakkugi(Talgeot.BEOSEU, "정류장 ${jeongryujangSeom}번 섬")
        }
    }

    private fun yeokGeuncheoBoda() {
        yeokGeuncheo = false
        val w = (if (jiha) jisangJari else null) ?: chaSijakJari ?: jisangJari ?: return
        JihacheolEngine.gakkaunYeokGeori(w.lat, w.lon) { d -> yeokGeuncheo = (d ?: 9999.0) <= 200 }
    }

    // 2.31.0 (261009-A19, 이사장님 승인 2026-10-09 남산 — 약수역 위 댁 앞에서 복지콜을 탔는데 "지하철을 타신 것 같습니다", 아이폰과 같은 뜻)
    //   ① 콜을 불러 배차된 뒤(3시간 안, 아직 내리지 않음)에는 땅속으로 내려가지 않는 한 차로 못 박음
    //   ② 역 200미터 안에서 탔다는 것만으로는 지하철로 보지 않음 — 위성이 좋지 않을 때만 셈
    //   ③ 땅 위에서 차로 알아챈 뒤에는 터널·가방 속처럼 위성만 끊겨도 지하철로 바꾸지 않음(땅속으로 내려갔을 때만)
    private fun chongPandan(kkadak: String) {
        // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 콜 차 못 박기는 땅속이라는 것만으로는 풀지 않고, 땅속(위성 없음)에서 탈것이 실제로 20초 넘게 움직일 때(지하철) 풂
        //   (11층 댁에서 승강기로 지하 사무실에 내려가 몇 걸음 걸으신 것만으로 콜 차 못 박기가 풀리던 일, 아이폰과 같음)
        if (jiha && !wiseongJoeum && ChaBureugi.chaGojeong) ChaBureugi.kolPulgi("jiha_talgeot")
        val kolCha = ChaBureugi.chaGojeong && !jiha
        // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 역 근처에서 탔다는 것은 이번에 타고 처음 판단할 때만 — 차·버스로 본 뒤에는(터널 등) 다시 쓰지 않음
        val yeok = yeokGeuncheo && !wiseongJoeum && chujeong != Talgeot.CHA && chujeong != Talgeot.BEOSEU
        val wiseongMan = wiseongEopseum && chujeong != Talgeot.CHA && chujeong != Talgeot.BEOSEU
        val t = when {
            kolCha -> Talgeot.CHA
            jiha || wiseongMan || yeok -> Talgeot.JIHACHEOL
            jeongryujangSeom >= 2 || chujeong == Talgeot.BEOSEU -> Talgeot.BEOSEU
            else -> Talgeot.CHA
        }
        if (t != chujeong) bakkugi(t, kkadak + (if (jiha) ", 땅속" else "") + (if (yeok) ", 역 근처에서 탐" else "") + (if (kolCha) ", 콜 배차" else ""))
    }

    /** 2.31.0 새 목적지를 정할 때 — 30초 안에 탈것이 움직이지 않았으면 묵은 탈것 판단(지하철 등)을 지움
     *  (2026-10-09 남산: 25분 전 잘못 본 "지하철"이 남아 길 위에서 목적지를 정할 때마다 "열차가 움직이는 것 같습니다") */
    fun saeYeojeong() {
        val umjigimNa = majimakTalgeot > 0 && System.currentTimeMillis() - majimakTalgeot < 30_000
        if (umjigimNa || jiha || chujeong == Talgeot.GEOREUM) return
        if (chujeong == Talgeot.CHA && ChaBureugi.chaGojeong) return   // 콜 차 안에서 신호 대기 중일 수 있음
        chaSijak = 0L; meomchumSijak = 0L
        yeokGeuncheo = false; jeongryujangSeom = 0
        bakkugi(Talgeot.GEOREUM, "새 목적지 — 묵은 판단 지움")
    }

    /** 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 여정을 끝내거나 하던 일을 멈출 때 — 묵은 탈것 판단을 모두 지우고 처음부터
     *  (땅속 판단은 위성이 잘 잡힐 때만 지움) */
    fun saeroSijak() {
        chaSijak = 0L; meomchumSijak = 0L
        yeokGeuncheo = false; jeongryujangSeom = 0
        if (jiha && wiseongJoeum) {
            jiha = false
            gido.clear()
            Girok.namgi("jiha_naom", mapOf("wiseong" to true, "kkadak" to "새로 시작"))
        }
        if (chujeong != Talgeot.GEOREUM) bakkugi(Talgeot.GEOREUM, "새로 시작 — 여정 끝·하던 일 멈춤")
    }

    /** 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 이용자가 탈것을 바로잡으심 — 판단만 그 탈것으로 맞춤(안내 바꾸기는 AnnaeEngine 이 이미 함) */
    fun barojapgi(t: Talgeot) {
        if (t == Talgeot.GEOREUM) {
            chaSijak = 0L; meomchumSijak = 0L
            yeokGeuncheo = false; jeongryujangSeom = 0
        }
        if (t != chujeong) bakkugi(t, "이용자가 바로잡음")
    }

    /** 2.32.0 15초 넘게 걷고 있는가(앱을 다시 켠 뒤 지하철 안내를 마칠지 볼 때) */
    val georeum15cho: Boolean
        get() = jigeumUmjigim == "걸음" && georeumSijak > 0 && System.currentTimeMillis() - georeumSijak >= 15000

    private fun bakkugi(t: Talgeot, kkadak: String) {
        chujeong = t
        Girok.namgi("talgeot_gamji", mapOf("talgeot" to t.raw, "kkadak" to kkadak))   // 2.30.0 칸 이름 "t" 가 기록 시각 칸을 덮어써 시각이 지워지던 것 고침
        YeojeongEngine.gamjiBatda(t)
    }

    // MARK: 기압 — 1헥토파스칼 ≈ 8.3미터

    // 2.25.0 (전체 점검) 갤럭시 탭 기압계가 잘게 흔들려 가만히 둔 폰이 밤새 「땅속 들어감·나옴」을 백 번 넘게 되풀이함(나스 기록).
    // 아이폰 고도계처럼 10초 평균으로 고르고, 내려간 채로 10초 넘게 머물러야 땅속으로 봄
    private val gidoNal = ArrayList<Pair<Long, Double>>()
    private var naeryeogaTtae = 0L

    private fun gidoBatda(hpa: Double) {
        val now = System.currentTimeMillis()
        gidoNal.add(now to (-hpa * 8.3))
        gidoNal.removeAll { now - it.first > 10000 }
        val h = gidoNal.sumOf { it.second } / gidoNal.size   // 높을수록 큰 값(상대 높이), 10초 평균
        gido.add(now to h)
        gido.removeAll { now - it.first > 120000 }
        // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 높이와 함께 걸음도 남김
        val georeumSu = Wichi.georeumSu
        if (gidoJeonGeoreum >= 0 && georeumSu > gidoJeonGeoreum) georeumNeuneunTtae = now
        gidoJeonGeoreum = georeumSu
        gidoGeoreum.add(now to georeumSu)
        gidoGeoreum.removeAll { now - it.first > 120000 }
        if (!jiha) {
            val chang90 = gido.filter { now - it.first <= 90000 }
            val jeonMax = chang90.maxOfOrNull { it.second } ?: h
            // 2.30.0 (261009-A18, 이사장님 승인) 밤새 집 안에 놓인 폰이 20분마다 땅속 들어감·나옴을 되풀이하고(10/9 기록),
            // 차를 타고 내리막·터널을 지날 때도 땅속으로 봄 — 땅속은 사람이 걸어서(계단·에스컬레이터) 내려갈 때만, 지금 탈것을 타고 있지 않을 때
            // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 승강기를 땅속으로 잘못 보던 것 — 가장 높던 때부터 지금까지 걸음이 여섯 걸음 넘게 늘었고
            //   지금도 걸음이 늘고 있어야(10초 안) 땅속으로 봄(아이폰과 같음)
            // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09 — 11층 댁에서 승강기로 지하 사무실에 내려간 뒤 여섯 걸음 걸으시면 땅속으로 보던 일, 아이폰과 같음)
            //   걸음은 내려가는 동안(가장 높던 때부터 바닥에 닿은 때까지)에 늘어난 것만 셈 — 승강기는 내려가는 동안 걸음이 없음.
            //   내려간 채로 10초 넘게 머물러야 땅속으로 보는 것은 그대로
            val maxTtae = chang90.lastOrNull { it.second >= jeonMax }?.first ?: now
            val dwi = chang90.filter { it.first >= maxTtae }
            val badak = dwi.minOfOrNull { it.second } ?: h
            val badakTtae = dwi.firstOrNull { it.second <= badak + 0.5 }?.first ?: now
            val maxGeoreum = gidoGeoreum.firstOrNull { it.first >= maxTtae }?.second ?: georeumSu
            val badakGeoreum = gidoGeoreum.lastOrNull { it.first <= badakTtae }?.second ?: georeumSu
            val naeryeoGaneunGeoreum = badakGeoreum - maxGeoreum
            // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 에스컬레이터에 가만히 서서 내려가면 걸음이 없어 땅속으로 못 보던 일 —
            //   내려간 높이를 걸린 때(가장 높던 때부터 바닥에 닿은 때까지)로 나눈 빠르기가 초속 0.6미터보다 느리고 3미터 넘게 내려갔으면 땅속.
            //   승강기는 초속 1~2미터, 에스컬레이터·계단은 초속 0.3~0.5미터(아이폰과 같음). 높이를 10초 평균으로 고르므로
            //   평균이 늘여 놓은 10초를 빼고 셈(짧은 승강기를 느리게 내려간 것으로 잘못 보지 않게)
            val naeryeoNopi = jeonMax - badak
            // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 승강기가 중간 층에 서며 내려가면 평균 빠르기가 낮아져 땅속으로 잘못 보던 것 —
            //   걸린 때는 실제로 높이가 줄고 있던 때(초속 0.1미터 넘게 내려가던 사이)만 더해 셈, 10초 평균이 늘인 10초는 그대로 뺌(아이폰과 같음)
            var umjikMs = 0L
            val naeryeoJeom = dwi.filter { it.first <= badakTtae }
            for (i in 1 until naeryeoJeom.size) {
                val dt = naeryeoJeom[i].first - naeryeoJeom[i - 1].first
                val dh = naeryeoJeom[i - 1].second - naeryeoJeom[i].second
                if (dt > 0 && dh / (dt / 1000.0) > 0.1) umjikMs += dt
            }
            val naeryeoSigan = umjikMs / 1000.0 - 10.0
            val neurinNaeryeogam = naeryeoNopi >= 3 && naeryeoSigan > 0 && naeryeoNopi / naeryeoSigan < 0.6
            val georeoNaeryeogam = (naeryeoGaneunGeoreum >= 6 || neurinNaeryeogam) && jigeumUmjigim != "탈것"
            if (jeonMax - h >= 3.5 && !wiseongJoeum && georeoNaeryeogam) {
                if (naeryeogaTtae == 0L) naeryeogaTtae = now
                if (now - naeryeogaTtae < 10000) return
                naeryeogaTtae = 0L
                jiha = true
                jihaTtae = now
                jihaMin = h
                Girok.namgi("jiha_jinip", mapOf("naeryeogam" to ((jeonMax - h) * 10).toInt(), "georeum" to naeryeoGaneunGeoreum,
                    "ppareugi" to (if (naeryeoSigan > 0) (naeryeoNopi / naeryeoSigan * 100).toInt() else -1)))   // 2.32.0 초속 센티미터(아이폰과 같음)
                jihaJinip?.invoke()
                // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 땅속이라는 것만으로는 콜 차 못 박기를 풀지 않음 — 땅속에서 탈것이 움직일 때(chongPandan) 풂(아이폰과 같음)
            } else naeryeogaTtae = 0L
        } else {
            if (h < jihaMin) jihaMin = h
            jihaHwagin()
            jihaSumyeong(now)
        }
    }

    /** 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 땅속 판단의 수명 — 위성도 없고 탈것도 움직이지 않은 채 30분이 넘으면 지움
     *  (지하 사무실·지하 주차장에 오래 머물 때 땅속 판단이 하루 종일 남던 일, 아이폰과 같음) */
    private fun jihaSumyeong(now: Long) {
        if (!jiha || wiseongJoeum || jihaTtae == 0L) return
        val gijun = maxOf(jihaTtae, majimakTalgeot)
        if (now - gijun <= 30 * 60_000L) return
        jiha = false
        gido.clear()
        Girok.namgi("jiha_sumyeong", mapOf("bun" to ((now - jihaTtae) / 60_000L).toInt()))
    }

    /** 땅 위로 나왔는가 — 3.5미터 넘게 올라오고 걷거나, 위성이 다시 잡혀야 함 */
    private fun jihaHwagin() {
        if (!jiha) return
        val h = gido.lastOrNull()?.second ?: jihaMin
        if (wiseongJoeum || (h - jihaMin >= 3.5 && jigeumUmjigim == "걸음")) {
            jiha = false
            jisangJari = Wichi.jigeum
            gido.clear()   // 2.30.0 나온 뒤 묵은 높이로 곧바로 다시 들어감을 막음(1초 간격 들락날락)
            Girok.namgi("jiha_naom", mapOf("wiseong" to wiseongJoeum))
        }
    }
}
