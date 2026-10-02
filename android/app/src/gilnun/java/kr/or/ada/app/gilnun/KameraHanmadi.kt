// 안드로이드 길눈 — 인터넷 없이 한마디 설명(묶음 b4_dulreo, 아이폰 Hanmadi.swift 2.23.0 을 같은 말로 옮김)
// 카메라에 잡힌 것을 폰 안에서 대강 한마디로 알려 드립니다(보기: "컵, 책상인 듯합니다. 글자가 보입니다").
// 아이폰은 애플 사진 갈래(Vision), 사람 찾기, 글자 찾기를 씀. 안드로이드는 ML Kit 사진 갈래(앱 안 기본 모델, 영어 갈래 이름 →
// 아래 우리말 표로 옮김 — 표에 없는 갈래는 말하지 않음), 얼굴 찾기(얼굴이 보이는 사람만 셈), 한글 글자 알아보기(b3 GeulIlgi)를 씁니다.
// 인터넷과 요금이 들지 않고, 사진은 담지도 보내지도 않습니다. 자세한 설명은 사진 읽어 주기가 맡습니다.
package kr.or.ada.app.gilnun

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.Locale

object Hanmadi : KameraNunBupum {
    override val ireum = "한마디 설명"
    override var kyeojim = false
        private set
    var sangtae = ""
        private set
    var majimak = ""
        private set
    var byeonhwa: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var galla: ImageLabeler? = null
    private var eolgul: FaceDetector? = null
    // 아래는 화면 줄에서만
    private var apMal = ""        // 앞 장의 한마디(두 장 잇달아 같아야 알림)
    private var malHan = ""
    private var malT = 0L
    private var saeT = 0L
    private val sigye = object : Runnable {
        override fun run() {
            if (!kyeojim) return
            salpigi()
            if (kyeojim) main.postDelayed(this, 10000)
        }
    }

    init { Kamera.deungrok(this) }

    // MARK: 켜기·끄기

    fun kyeogi(a: GilnunActivity? = null, bogi: PreviewView? = null) {
        val t = a ?: Kamera.hwalseongEotgi() ?: return
        if (kyeojim) { if (bogi != null) Kamera.bogiDalgi(bogi); return }
        Kamera.heorak(t, this, "한마디 설명을") { sijak(t, bogi) }
    }

    private fun sijak(t: GilnunActivity, bogi: PreviewView?) {
        val gl = galla ?: ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build()).also { galla = it }
        val eg = eolgul ?: FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.08f)
                .build()
        ).also { eolgul = it }
        apMal = ""
        Kamera.sijak(t, this, bogi, 1000L, { jang -> bunseok(gl, eg, jang) }) { ok ->
            if (ok) {
                kyeojim = true
                malHan = ""
                saeT = System.currentTimeMillis()
                sangtae = "살피는 중입니다."
                mal("한마디 설명을 시작합니다. 폰 뒤쪽 카메라를 궁금한 쪽으로 향해 주십시오.", sseuGi = true)
                Girok.namgi("hanmadi", mapOf("kyeogi" to true))
                main.removeCallbacks(sigye)
                main.postDelayed(sigye, 10000)
                byeonhwa?.invoke()
            }
        }
    }

    override fun kkeugi(malHagi: Boolean) {
        Kamera.kkeugi(this)
        main.removeCallbacks(sigye)
        if (!kyeojim) return
        kyeojim = false
        sangtae = ""
        if (malHagi) Sori.mal("한마디 설명을 멈췄습니다.", MalGeup.JEONGBO)
        byeonhwa?.invoke()
    }

    fun dasiDeutgi() {
        if (majimak.isNotEmpty()) Sori.mal(majimak)
        else Sori.mal(if (kyeojim) "살피는 중입니다." else "한마디 설명이 꺼져 있습니다.")
    }

    /** 5분 동안 새로 알린 것이 없으면 카메라를 끔(배터리) */
    private fun salpigi() {
        if (!kyeojim || System.currentTimeMillis() - saeT <= 300000) return
        kkeugi(false)
        mal("5분 동안 새로 알려 드릴 것이 없어 카메라를 껐습니다.", sseuGi = true)
    }

    // MARK: 한 장마다 — 1초에 한 번(사진 갈래 → 얼굴 → 글자 차례로, 다 보면 장을 놓음)

    private fun bunseok(gl: ImageLabeler, eg: FaceDetector, jang: KameraJang) {
        val img = jang.inputImage()
        gl.process(img).addOnCompleteListener { lt ->
            val labels: List<ImageLabel> = if (lt.isSuccessful) (lt.result ?: emptyList()) else emptyList()
            eg.process(img).addOnCompleteListener { ft ->
                val faces: List<Face> = if (ft.isSuccessful) (ft.result ?: emptyList()) else emptyList()
                GeulIlgi.ilgiTeul(img, jang.garo, jang.sero) { jul ->
                    jang.kkeut()
                    val geulItda = jul.size >= 2 || jul.any { it.mal.trim().contains(' ') }
                    val m = salpyeoBogi(labels, faces.size, geulItda)
                    // 두 장 잇달아 같은 한마디일 때만(흔들려 헛보인 것 줄이기)
                    val hwakjeong = if (m == apMal) m else ""
                    apMal = m
                    if (hwakjeong.isNotEmpty()) allida(hwakjeong)
                }
            }
        }
    }

    /** 사진 갈래 셋까지 + 사람 수 + 글자 여부를 한마디로 */
    fun salpyeoBogi(labels: List<ImageLabel>, su: Int, geulItda: Boolean): String {
        val ireum = ArrayList<String>()
        for (o in labels.sortedByDescending { it.confidence }) {
            if (o.confidence < 0.5f) continue
            val k = urimalChatgi(o.text) ?: continue
            if (ireum.contains(k)) continue
            ireum.add(k)
            if (ireum.size >= 3) break
        }
        val jogak = ArrayList<String>()
        if (su == 1) jogak.add("사람 한 명이 보입니다")
        else if (su > 1) jogak.add("사람 ${su}명이 보입니다")
        if (ireum.isNotEmpty()) {
            // 사람을 따로 셌으면 갈래의 사람은 뺌
            val nam = if (su > 0) ireum.filter { it != "사람" && it != "어린이" } else ireum
            if (nam.isNotEmpty()) jogak.add(nam.joinToString(", ") + "인 듯합니다")
        }
        if (geulItda) jogak.add("글자가 보입니다")
        return jogak.joinToString(". ")
    }

    private fun urimalChatgi(t: String): String? {
        val k = t.trim().lowercase(Locale.ROOT)
        return urimal[k] ?: urimal[k.replace(' ', '_')] ?: urimal[k.replace('_', ' ')]
    }

    // MARK: 알리기(화면 줄)

    private fun allida(m: String) {
        if (!kyeojim) return
        val now = System.currentTimeMillis()
        // 바뀌었을 때만, 4초에 한 번까지
        if (m == malHan || now - malT < 4000) return
        malT = now
        saeT = now
        malHan = m
        majimak = "$m."
        sangtae = majimak
        Sori.mal(majimak, MalGeup.ANNAE)   // 기다리는 대답이므로 말소리를 꺼도 알림
        byeonhwa?.invoke()
    }

    private fun mal(t: String, g: MalGeup = MalGeup.ANNAE, sseuGi: Boolean = false) = KameraNun.mal(t, g, sseuGi)

    /**
     * 사진 갈래 이름을 우리말로 — 아이폰 표(애플 갈래)를 그대로 싣고(작은 글자, 밑줄은 띄어쓰기로도 찾음),
     * ML Kit 기본 갈래에만 있는 이름을 덧붙임. 여기에 없는 갈래는 말하지 않음(뜻이 넓거나 헷갈리는 것은 일부러 뺌)
     */
    val urimal: Map<String, String> = mapOf(
        // 사람·동물
        "people" to "사람", "adult" to "사람", "child" to "어린이", "baby" to "아기", "crowd" to "사람들",
        "dog" to "개", "cat" to "고양이", "bird" to "새", "fish" to "물고기", "horse" to "말", "cow" to "소",
        "chicken_animal" to "닭", "insect" to "벌레", "butterfly" to "나비", "pigeon" to "비둘기", "duck" to "오리",
        // 먹을 것
        "food" to "먹을 것", "fruit" to "과일", "apple" to "사과", "banana" to "바나나", "orange" to "귤", "grape" to "포도",
        "strawberry" to "딸기", "watermelon" to "수박", "vegetable" to "채소", "bread" to "빵", "cake" to "케이크",
        "cookie" to "과자", "dessert" to "후식", "rice" to "밥", "noodles" to "국수", "soup" to "국", "pizza" to "피자",
        "hamburger" to "햄버거", "sandwich" to "샌드위치", "sushi" to "초밥", "egg" to "달걀", "meat" to "고기",
        "salad" to "샐러드", "coffee" to "커피", "tea_drink" to "차", "juice" to "주스", "beer" to "맥주", "wine" to "포도주",
        "drink" to "마실 것", "ice_cream" to "아이스크림", "chocolate" to "초콜릿", "candy" to "사탕", "kimchi" to "김치",
        // 그릇·부엌
        "cup" to "컵", "mug" to "머그잔", "bottle" to "병", "plate" to "접시", "bowl" to "그릇", "utensil" to "수저",
        "spoon" to "숟가락", "fork" to "포크", "knife" to "칼", "chopsticks" to "젓가락", "kettle" to "주전자",
        "refrigerator" to "냉장고", "microwave" to "전자레인지", "oven" to "오븐", "stove" to "가스레인지", "sink" to "개수대",
        "kitchen_room" to "부엌", "kitchen" to "부엌",
        // 집·가구
        "chair" to "의자", "table" to "탁자", "desk" to "책상", "sofa" to "소파", "couch" to "소파", "bed" to "침대",
        "pillow" to "베개", "blanket" to "이불", "shelf" to "선반", "bookshelf" to "책장", "cabinet" to "장",
        "door" to "문", "window" to "창문", "stairs" to "계단", "curtain" to "커튼", "lamp" to "전등", "chandelier" to "샹들리에",
        "mirror" to "거울", "clock" to "시계", "toilet" to "변기", "bathroom_room" to "화장실", "bathroom" to "화장실",
        "bathtub" to "욕조", "shower" to "샤워기", "towel" to "수건", "carpet" to "깔개", "fan" to "선풍기",
        "bedroom" to "침실", "living_room" to "거실", "office" to "사무실", "classroom" to "교실", "restaurant" to "식당",
        "store" to "가게", "supermarket" to "마트",
        // 물건
        "book" to "책", "document" to "서류", "paper" to "종이", "newspaper" to "신문", "magazine" to "잡지",
        "pen" to "펜", "pencil" to "연필", "scissors" to "가위", "bag" to "가방", "handbag" to "손가방", "backpack" to "배낭",
        "wallet" to "지갑", "money" to "돈", "coin" to "동전", "key" to "열쇠", "umbrella" to "우산", "glasses" to "안경",
        "sunglasses" to "선글라스", "hat" to "모자", "shoes" to "신발", "sneaker" to "운동화", "clothing" to "옷",
        "shirt" to "셔츠", "jacket" to "외투", "coat" to "외투", "pants" to "바지", "jeans" to "청바지", "dress" to "원피스",
        "sock" to "양말", "glove" to "장갑", "watch" to "손목시계", "jewelry" to "장신구", "necklace" to "목걸이",
        "toy" to "장난감", "ball" to "공", "doll" to "인형", "teddy_bear" to "곰 인형", "box" to "상자", "basket" to "바구니",
        "flower_pot" to "화분", "vase" to "꽃병", "candle" to "양초", "medicine" to "약", "pill" to "알약",
        "cane" to "지팡이", "wheelchair" to "휠체어", "trash_can" to "쓰레기통",
        // 전자 기기
        "computer" to "컴퓨터", "laptop" to "노트북", "computer_keyboard" to "자판", "keyboard" to "자판",
        "computer_mouse" to "마우스", "monitor" to "모니터", "television" to "텔레비전", "phone" to "전화기",
        "cellphone" to "휴대전화", "smartphone" to "휴대전화", "tablet" to "태블릿", "headphones" to "헤드폰",
        "speaker" to "스피커", "camera" to "카메라", "remote_control" to "리모컨", "printer" to "프린터",
        // 바깥
        "sky" to "하늘", "cloud" to "구름", "sun" to "해", "moon" to "달", "tree" to "나무", "grass" to "풀밭", "flower" to "꽃",
        "plant" to "식물", "leaf" to "나뭇잎", "forest" to "숲", "mountain" to "산", "hill" to "언덕", "rock" to "바위",
        "river" to "강", "lake" to "호수", "sea" to "바다", "ocean" to "바다", "beach" to "바닷가", "sand" to "모래",
        "snow" to "눈", "rain" to "비", "water" to "물", "fountain" to "분수", "park" to "공원", "garden" to "정원",
        "road" to "길", "street" to "거리", "sidewalk" to "보도", "crosswalk" to "횡단보도", "bridge" to "다리",
        "building" to "건물", "house" to "집", "apartment" to "아파트", "skyscraper" to "높은 건물", "tower" to "탑",
        "temple" to "절", "church" to "교회", "palace" to "궁궐", "castle" to "성", "fence" to "울타리", "wall" to "벽",
        "signboard" to "간판", "sign" to "표지판", "traffic_light" to "신호등", "streetlight" to "가로등",
        "bench" to "벤치", "parking_lot" to "주차장", "station" to "역", "airport" to "공항", "stadium" to "경기장",
        // 탈것
        "car" to "자동차", "bus" to "버스", "truck" to "트럭", "taxi" to "택시", "motorcycle" to "오토바이",
        "bicycle" to "자전거", "train" to "기차", "subway" to "지하철", "airplane" to "비행기", "boat" to "배", "ship" to "배",
        "scooter" to "킥보드",
        // 그 밖
        "painting" to "그림", "drawing" to "그림", "sculpture" to "조각", "statue" to "동상", "poster" to "포스터",
        "musical_instrument" to "악기", "piano" to "피아노", "guitar" to "기타", "drum" to "북",
        "fire" to "불", "smoke" to "연기",
        // ── ML Kit 기본 갈래에만 있는 이름(아이폰 표에 없던 것) ──
        "mobile phone" to "휴대전화", "musical instrument" to "악기", "watercraft" to "배", "shoe" to "신발",
        "sweater" to "스웨터", "shorts" to "반바지", "denim" to "청바지", "cushion" to "방석", "bento" to "도시락",
        "cheeseburger" to "햄버거", "pumpkin" to "호박", "aquarium" to "어항", "tractor" to "트랙터",
        "bonfire" to "모닥불", "flag" to "깃발", "wheel" to "바퀴", "tire" to "타이어", "waterfall" to "폭포",
        "pier" to "부두", "pool" to "수영장", "swimming pool" to "수영장", "cave" to "동굴", "iceberg" to "빙산",
        "skateboard" to "스케이트보드", "surfboard" to "서핑보드", "balloon" to "풍선", "gift" to "선물",
        "porcelain" to "도자기", "jar" to "단지", "lunch" to "점심 밥상", "meal" to "밥상", "pasta" to "파스타",
        "cola" to "콜라", "tableware" to "식기", "countertop" to "조리대", "shelves" to "선반",
        "factory" to "공장", "skyline" to "건물들", "dam" to "댐", "farm" to "농장", "ranch" to "목장",
        "christmas" to "성탄 장식", "fireworks" to "불꽃놀이", "concert" to "공연", "museum" to "박물관",
        "cattle" to "소", "sheep" to "양", "rabbit" to "토끼", "turtle" to "거북", "squirrel" to "다람쥐",
        "storm" to "폭풍", "lightning" to "번개", "rainbow" to "무지개", "sunset" to "해 질 녘",
        "hand" to "손", "toothbrush" to "칫솔", "helmet" to "헬멧", "scarf" to "목도리", "belt" to "허리띠",
        "screen" to "화면", "cabinetry" to "장", "wood" to "나무", "leather" to "가죽"
    )
}

/** 한마디 설명 화면 — 둘러보기 탭 */
class HanmadiHwamyeon : KameraHwamyeon("한마디 설명") {
    override val bupum: KameraNunBupum get() = Hanmadi
    override fun kyeogi(t: GilnunActivity) = Hanmadi.kyeogi(t, bogi)

    override fun juljul(t: GilnunActivity): View {
        val g = Hanmadi
        val kb = t.danchu(kGeul()) { if (g.kyeojim) g.kkeugi(true) else g.kyeogi(t, bogi) }
        g.byeonhwa = {
            if (t.wiHwamyeon === this) {
                val n = kGeul()
                if (kb.text.toString() != n) kb.text = n
            }
        }
        val dd = t.danchu("방금 한마디 다시 듣기") { g.dasiDeutgi() }
        bogiNeoki(t, dd)
        malsoriDanchu(t)
        alaDuSil(t, "화면을 여시면 바로 시작합니다. 폰 뒤쪽 카메라를 궁금한 쪽으로 향하시면, 보이는 것을 컵, 책상인 듯합니다처럼 대강 한마디로 알려 드립니다. 얼굴이 보이는 사람이 있으면 몇 명인지, 글자가 있으면 글자가 보입니다라고 덧붙입니다. 보이는 것이 바뀔 때만, 4초에 한 번까지 말씀드립니다. 인터넷 없이 폰 안에서만 짐작하는 것이라 틀릴 수 있으니 참고로만 들으십시오. 자세한 설명이 필요하시면 둘러보기 탭의 사진 읽어 주기를, 글자를 읽으시려면 즉석 글자 읽기를 쓰십시오. 방금 한마디 다시 듣기를 누르시면 마지막 한마디를 다시 들려 드립니다. 5분 동안 새로 알릴 것이 없거나 이 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 이게 뭐야, 뭐가 보여라고 하셔도 열립니다. 사진은 담지도 보내지도 않습니다.")
        return kb
    }

    private fun kGeul() = if (Hanmadi.kyeojim) "멈추기 — 앞을 살피는 중" else "이어 살피기 — 카메라를 켜고 한마디 설명"
}
