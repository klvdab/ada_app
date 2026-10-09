package kr.or.ada.app.gilnun

// 2.30.0 (261009-A18, 이사장님 승인 2026-10-09 "매일 아침에 길눈과 자봉이 사용된 기록을 볼 수 있도록") 아침 기록 — 관리자
// 나스 jeom/achim.php 가 지난 24시간 앱 기록을 모아 정리한 글을 받아 한 줄씩 보여 드림(아이폰 AchimGirok.swift 와 같은 말).
// 살펴볼 일이 있으면 맨 첫 줄에. 같은 글이 매일 아침 이사장님 메일로도 감. 화면을 열면 커서가 요약 줄로. 줄에 번호 없음.

class AchimGirokHwamyeon : Hwamyeon("아침 기록") {
    private var jul: List<String> = emptyList()
    private var badneun = false
    private var batham = false
    private var mot = false

    override fun chaeugi(t: GilnunActivity) {
        if (!batham && !badneun) batgi(t)
        when {
            badneun -> t.chojeomJul(t.geul("아침 기록을 받는 중입니다."))
            mot -> t.chojeomJul(t.geul("아침 기록을 받지 못했습니다. 인터넷을 확인하신 뒤 설정의 새로고침을 누르시고 다시 열어 주십시오."))
            else -> for ((i, s) in jul.withIndex()) {
                if (s.isEmpty()) continue
                val g = if (s.startsWith("[")) t.geul(s.trim('[', ']'), true) else t.geul(s)
                if (i == 1) t.chojeomJul(g)
            }
        }
    }

    private fun batgi(t: GilnunActivity) {
        badneun = true
        Tongsin.json("achim.php", mapOf("f" to "json"), 20000) { o ->
            val a = o?.optJSONArray("jul")
            jul = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
            mot = jul.isEmpty()
            badneun = false
            batham = true
            Girok.namgi("achim_girok", mapOf("ok" to !mot))
            if (t.wiHwamyeon === this) t.dasiGeurigi()
        }
    }
}
