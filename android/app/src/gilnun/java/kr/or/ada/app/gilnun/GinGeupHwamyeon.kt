// 안드로이드 길눈 — 긴급통화서비스 화면(2.6.0, 빌드 261002-A9, 대표님 지시: 아이폰 길눈의 긴급통화서비스를 안드로이드에도 똑같이)
// 아이폰 GinGeupView.swift(2.4.1~2.12.1)를 GilnunActivity 화면 틀로 옮겼습니다.
//   세 갈래 — 가족·지인(한 분을 고르십시오), 자원봉사자, 전문 현장영상해설사. 한 줄에 단추 하나. 한마디 먼저 남기기
//   요청 중·통화 중에는 첫 줄에 지금 형편과 「요청 그만두기」·「통화 끊기」 단추를 한 자리에, 커서도 그리로
//   형편 글이 바뀌면 화면을 다시 그리지 않고 그 단추 글자만 바꿈(톡백 커서가 흔들리지 않게). 상태가 바뀔 때만 다시 그림
//   가족·지인 명단과 내 이름은 설계도대로 설정 탭에(아이폰 2.4.1). 명단이 비었을 때만 고르기 화면에서 명단 화면으로 곧장
//   명단 줄은 이름 하나, 단추 하나 — 누르시면 초대 주소 다시 보내기와 명단에서 빼기를 고르는 화면(아이폰의 보이스오버 동작 대신)
package kr.or.ada.app.gilnun

import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged

class GinGeupHwamyeon : Hwamyeon("긴급통화서비스") {
    private var gurin: GinGeupSangtae? = null
    private var cheotDanchu: Button? = null
    private var geulView: TextView? = null

    override fun chaeugi(t: GilnunActivity) {
        val g = GinGeup
        gurin = g.sangtae
        geulView = null
        when (g.sangtae) {
            GinGeupSangtae.EOPSEUM -> {
                // 2.11.0 가족·지인이 받지 않으셨으면 — 안내를 읽은 바로 그 자리에 단추
                val neom = if (g.neomgilkka) t.danchu("${g.geul} — 예, 요청합니다") { g.yocheong(GinGeupGalrae.DOWUM) } else null
                if (g.neomgilkka) t.danchu("아니요, 그만둡니다") { g.neomgilkka = false; t.dasiGeurigi() }
                cheotDanchu = t.danchu("가족·지인에게 화상통화 요청 — 한 분을 고르십시오") { t.yeolgi(JiinGoreugiHwamyeon()) }
                if (neom != null) cheotDanchu = neom
                t.danchu("자원봉사자에게 화상통화 요청") { g.yocheong(GinGeupGalrae.HAEBONG) }
                t.danchu("전문 현장영상해설사에게 화상통화 요청") { g.yocheong(GinGeupGalrae.HAESEOLSA) }
                val e = t.ipryeok("한마디 먼저 남기기 — 받는 분 화면에 뜹니다", false)
                e.setText(g.malHan)
                e.doAfterTextChanged { g.malHan = it?.toString() ?: "" }
                if (g.geul.isNotEmpty() && !g.neomgilkka) geulView = t.geul(g.geul, true)
            }
            else -> {
                val b = t.danchu(kkeunkiGeul()) { g.geumanhagi() }
                b.minHeight = t.dp(96)
                b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                cheotDanchu = b
                val s = g.saram
                if (g.sangtae != GinGeupSangtae.TONGHWA && g.galrae == GinGeupGalrae.JIIN && s != null && Jiin.tel(s.id).isNotEmpty()) {
                    t.danchu("${s.name} 님께 전화 걸기") { g.jeonhwa(t) }
                }
            }
        }
        cheotDanchu?.let { t.chojeomJul(it) }   // 매번 첫 줄로(뒤로 단추가 아니라 이 화면의 첫 단추)
        GinGeup.byeonhwa = {
            if (t.wiHwamyeon === this) {
                if (gurin != GinGeup.sangtae) {
                    t.dasiGeurigi()
                    cheotDanchu?.let { t.chojeomOmgigi(it) }
                } else if (GinGeup.sangtae == GinGeupSangtae.EOPSEUM) {
                    geulView?.text = GinGeup.geul
                } else {
                    val n = kkeunkiGeul()
                    cheotDanchu?.let { if (it.text.toString() != n) it.text = n }
                }
            }
        }
    }

    private fun kkeunkiGeul(): String =
        if (GinGeup.sangtae == GinGeupSangtae.YOCHEONG) "${GinGeup.geul} — 요청 그만두기" else "${GinGeup.geul} — 통화 끊기"
}

/** 가족·지인 고르기 — 이름을 누르시면 그 한 분께만 요청이 갑니다. 다섯씩 */
class JiinGoreugiHwamyeon : Hwamyeon("가족·지인 고르기") {
    private var batneun = false
    private var batum = false
    private var mot = false
    private var sijak = 0
    private var chojeomHal = false
    private var cheotGyeolgwa: View? = null

    override fun chaeugi(t: GilnunActivity) {
        cheotGyeolgwa = null
        if (!batum && !batneun) {
            batneun = true
            Jiin.bureogi { ok ->
                batneun = false
                batum = true
                mot = !ok
                chojeomHal = true
                if (t.wiHwamyeon === this) t.dasiGeurigi()
            }
        }
        val l = Jiin.mokrok
        if (!Jiin.bulreoom && !mot) {
            t.geul("명단을 받는 중입니다.", true)
        } else if (l.isEmpty()) {
            cheotGyeolgwa = t.danchu(if (mot) "명단을 받지 못했습니다. 통신이 끊겼을 수 있습니다 — 명단 화면 열기"
                else "명단이 비어 있습니다 — 명단 화면을 열어 등록하기. 설정 탭의 가족·지인 명단과 같은 화면입니다") {
                t.yeolgi(JiinMyeongdanHwamyeon())
            }
        } else {
            if (sijak >= l.size) sijak = 0
            val kkeut = minOf(sijak + 5, l.size)
            for (n in sijak until kkeut) {
                val s = l[n]
                val b = t.danchu(if (s.badeum) s.name else "${s.name} (아직 받겠다고 안 하심)") {
                    GinGeup.yocheong(GinGeupGalrae.JIIN, s)
                    t.dwiro()
                }
                if (n == sijak) cheotGyeolgwa = b
            }
            if (kkeut < l.size) t.danchu("더 보기") { sijak += 5; chojeomHal = true; t.dasiGeurigi() }
            if (sijak > 0) t.danchu("이전 보기") { sijak = maxOf(0, sijak - 5); chojeomHal = true; t.dasiGeurigi() }
        }
    }

    override fun boilttae(t: GilnunActivity) {
        if (!chojeomHal) return
        chojeomHal = false
        cheotGyeolgwa?.let { t.chojeomOmgigi(it) }
    }
}

/** 가족·지인 명단 — 2.11.0 이음 번호로 등록(자봉 앱). 등록된 분을 누르시면 빼기. 예전 초대 주소 방식은 감춤(yetBangsik) */
class JiinMyeongdanHwamyeon : Hwamyeon("가족·지인 명단") {
    private var ireum = ""
    private var tel = ""
    private var allim = ""
    private var saero: JiinSaram? = null
    private var saeroTel = ""
    private var bureum = false
    private var mandeuneun = false
    private var sijak = 0
    private var chojeomHal = false
    private var chojeomJul: View? = null
    private var beonho = ""
    private var beonhoTtae = 0L
    private var bonIds = setOf<String>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var salpimR: Runnable? = null

    private fun beonhoMal(): String =
        "이음 번호 ${beonho.toList().joinToString(" ")}. 30분 안에 가족이나 지인에게 불러 주십시오. 그분이 자봉 앱의 봉사 탭, 긴급통화 받기, 가족·지인으로 받기에서 이 번호와 부르실 이름을 넣으시면 등록됩니다"

    override fun chaeugi(t: GilnunActivity) {
        chojeomJul = null
        if (!bureum) {
            bureum = true
            Jiin.bureogi { bonIds = Jiin.mokrok.map { it.id }.toSet(); if (t.wiHwamyeon === this) t.dasiGeurigi() }
        }
        if (beonho.isNotEmpty() && System.currentTimeMillis() - beonhoTtae < 1800_000L) {
            val b = t.danchu(beonhoMal() + " — 다시 듣기") { Sori.mal(beonhoMal() + ".") }
            if (chojeomHal) chojeomJul = b
            t.danchu("새 이음 번호 받기") { beonhoBatgi(t) }
        } else {
            t.danchu("이음 번호 받기 — 가족·지인이 자봉 앱에 넣을 여섯 자리") { beonhoBatgi(t) }
        }
        if (allim.isNotEmpty()) t.geul(allim, true)
        if (yetBangsik) yetHwamyeon(t)
        val l = Jiin.mokrok
        if (l.isNotEmpty()) {
            t.geul("등록된 분 — 이름을 누르시면 명단에서 빼기를 고르실 수 있습니다.")
            if (sijak >= l.size) sijak = 0
            val kkeut = minOf(sijak + 5, l.size)
            for (n in sijak until kkeut) {
                val s = l[n]
                val b = t.danchu("${s.name} — ${if (s.badeum) "받음" else "아직 받겠다고 안 하심"}") { t.yeolgi(JiinSaramHwamyeon(s)) }
                if (n == sijak && chojeomHal && chojeomJul == null) chojeomJul = b
            }
            if (kkeut < l.size) t.danchu("더 보기") { sijak += 5; chojeomHal = true; t.dasiGeurigi() }
            if (sijak > 0) t.danchu("이전 보기") { sijak = maxOf(0, sijak - 5); chojeomHal = true; t.dasiGeurigi() }
        }
    }

    /** 예전 초대 주소 방식 — 지우지 않고 감춤(이사장님 2026-10-04 "주소를 보내 연결하는 건 어려운 일") */
    private fun yetHwamyeon(t: GilnunActivity) {
        val s0 = saero
        if (s0 != null) {
            t.geul("${s0.name} 님을 만들었습니다. 아래 초대 주소를 그 분께 보내십시오.", true)
            if (saeroTel.isNotEmpty()) t.danchu("문자로 초대 주소 보내기 — ${s0.name} 님께") { munja(t, s0, saeroTel) }
            t.danchu("다른 앱으로 초대 주소 보내기 — 카카오톡 등") { nanugi(t, s0) }
        }
        val ei = t.ipryeok("이름", false)
        ei.setText(ireum)
        ei.doAfterTextChanged { ireum = it?.toString() ?: "" }
        val et = t.ipryeok("전화번호 — 폰 안에만 담깁니다", false)
        et.inputType = InputType.TYPE_CLASS_PHONE
        et.setText(tel)
        et.doAfterTextChanged { tel = it?.toString() ?: "" }
        t.danchu(if (mandeuneun) "만들고 있습니다" else "이 사람 만들기") { mandeulgi(t) }
    }

    /** 이음 번호 받기 — 받은 뒤 30분 동안 5초마다 명단을 살펴 새로 등록하신 분을 알려 드림 */
    private fun beonhoBatgi(t: GilnunActivity) {
        allim = "이음 번호를 받고 있습니다."
        t.dasiGeurigi()
        Jiin.ieumBeonho { b, e ->
            if (b == null) { allim = e; Sori.mal(e); if (t.wiHwamyeon === this) t.dasiGeurigi(); return@ieumBeonho }
            beonho = b; beonhoTtae = System.currentTimeMillis(); allim = ""
            bonIds = Jiin.mokrok.map { it.id }.toSet()
            Sori.mal(beonhoMal() + ".")
            chojeomHal = true
            if (t.wiHwamyeon === this) t.dasiGeurigi()
            salpimR?.let { main.removeCallbacks(it) }
            val r = object : Runnable {
                override fun run() {
                    if (System.currentTimeMillis() - beonhoTtae > 1800_000L) return
                    Jiin.bureogi { ok ->
                        if (!ok) { main.postDelayed(this, 5000); return@bureogi }
                        val sae = Jiin.mokrok.filter { it.id !in bonIds }
                        bonIds = Jiin.mokrok.map { it.id }.toSet()
                        if (sae.isNotEmpty()) {
                            for (p in sae) { Sori.mal("${p.name} 님이 가족·지인으로 등록하셨습니다."); Girok.namgi("jiin_ieum_deungrok") }
                            beonho = ""
                            allim = sae.joinToString(" ") { "${it.name} 님이 등록하셨습니다." }
                            val a = GinGeup.hwalseong?.get()
                            if (a != null && a.wiHwamyeon === this@JiinMyeongdanHwamyeon) a.dasiGeurigi()
                        } else main.postDelayed(this, 5000)
                    }
                }
            }
            salpimR = r
            main.postDelayed(r, 5000)
        }
    }

    override fun boilttae(t: GilnunActivity) {
        if (!chojeomHal) return
        chojeomHal = false
        chojeomJul?.let { t.chojeomOmgigi(it) }
    }

    private fun mandeulgi(t: GilnunActivity) {
        if (mandeuneun) return
        val nm = ireum.trim()
        if (nm.isEmpty()) { allim = "이름을 적어 주십시오."; Sori.mal(allim); t.dasiGeurigi(); return }
        allim = ""
        mandeuneun = true
        val tl = tel
        t.dasiGeurigi()
        Sori.mal("만들고 있습니다.")
        Jiin.mandeulgi(nm, tl) { s, k ->
            mandeuneun = false
            if (s != null) {
                saero = s
                saeroTel = tl.filter { "0123456789+".indexOf(it) >= 0 }
                ireum = ""
                tel = ""
                allim = ""
                Sori.mal("${s.name} 님을 만들었습니다. 초대 주소를 보내십시오.")
            } else {
                allim = k
                Sori.mal(k)
            }
            if (t.wiHwamyeon === this) {
                t.dasiGeurigi()
                if (saero != null) chojeomJul?.let { t.chojeomOmgigi(it) }
            }
        }
    }

    companion object {
        /** 예전 초대 주소 방식을 보일지 — 감춤 */
        const val yetBangsik = false

        /** 문자 앱 열기 — 번호와 초대 글을 넣은 채로 */
        fun munja(t: GilnunActivity, s: JiinSaram, beon: String) {
            try {
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$beon"))
                i.putExtra("sms_body", Jiin.chodaeGeul(s))
                t.startActivity(i)
            } catch (e: Exception) {
                nanugi(t, s)
            }
        }

        /** 다른 앱으로 보내기 — 카카오톡 등 */
        fun nanugi(t: GilnunActivity, s: JiinSaram) {
            try {
                val i = Intent(Intent.ACTION_SEND)
                i.type = "text/plain"
                i.putExtra(Intent.EXTRA_TEXT, Jiin.chodaeGeul(s))
                t.startActivity(Intent.createChooser(i, "초대 주소 보내기"))
            } catch (e: Exception) {
                Sori.mal("보낼 앱을 열지 못했습니다.")
            }
        }
    }
}

/** 등록된 한 분 — 초대 주소 다시 보내기, 명단에서 빼기 */
class JiinSaramHwamyeon(private val s: JiinSaram) : Hwamyeon(s.name) {
    override fun chaeugi(t: GilnunActivity) {
        t.geul(if (s.badeum) "받겠다고 하셨습니다." else "아직 받겠다고 하지 않으셨습니다.")
        if (JiinMyeongdanHwamyeon.yetBangsik) t.danchu("초대 주소 다시 보내기") {
            val beon = Jiin.tel(s.id)
            if (beon.isNotEmpty()) JiinMyeongdanHwamyeon.munja(t, s, beon) else JiinMyeongdanHwamyeon.nanugi(t, s)
        }
        t.danchu("명단에서 빼기 — ${s.name} 님") {
            Jiin.jiugi(s) {
                val a = GinGeup.hwalseong?.get()
                if (a != null && a.wiHwamyeon is JiinMyeongdanHwamyeon) a.dasiGeurigi()
            }
            Sori.mal("${s.name} 님을 명단에서 뺐습니다.")
            t.dwiro()
        }
    }
}
