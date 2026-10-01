// 자봉 앱 판번호와 고친 기록 — 대표님 지시: 클이 만드는 모든 프로그램에 판번호·빌드번호를 붙이고 고칠 때마다 기록
import Foundation

enum JabongPan {
    static let pan = "2.1.0"
    static let bild = "261001-9"
    static var appBild: String { (Bundle.main.infoDictionary?["CFBundleVersion"] as? String) ?? "" }

    struct Gochim: Identifiable { let id = UUID(); let pan: String; let bild: String; let nal: String; let naeyong: [String] }
    static let girok: [Gochim] = [
        Gochim(pan: "2.1.0", bild: "261001-9", nal: "2026-10-01", naeyong: [
            "긴급통화 받기(대표님 승인 1) — 자봉 앱을 길눈님 전화기로. 봉사 탭 맨 위",
            "함께하겠습니다 한 번에: 나스 rel.php 대기 등록(늘 켜 둠, 자원봉사자는 수료 번호·해설사는 협회 전화번호로 확인), 카메라·마이크 허락, 폰 알림 주소를 나스 apns.php 에",
            "길손님이 도움을 청하면 나스가 애플 알림(VoIP)을 보내고 폰은 일반 전화처럼 울림(CallKit) — 잠겨 있어도. 받으면 곧바로 화상통화(길손님 영상 보기, 서로 말소리)",
            "다른 길눈님이 먼저 받으면 벨을 멈추고 다른 분께 연결되었습니다. 감사합니다. 15초 넘는 통화 끝에 고맙다는 말. 별명만, 수고 기록은 나스에",
            "도움말 긴급통화 받기 항목 더함"
        ]),
        Gochim(pan: "2.0.0", bild: "261001-7", nal: "2026-10-01", naeyong: [
            "자봉 앱을 속까지 앱으로 다시 지음(대표님 승인) — 웹을 띄우던 껍데기를 버리고 모든 화면을 앱으로. 설계도 자봉앱_설계도_261001",
            "2단계 기초판: 탭 넷(봉사, 나눔, 내 기록, 알림·설정), 처음 등록(이름·연락처·지역·네 자리 숫자·1365 아이디, 요령 다섯 가지, 확인 문제 세 개, 자봉 번호, 보폭 재기)",
            "길눈 부품을 함께 씀: 위치, 걸음, 말소리(선희 목소리 포함), 기록, 나스 통신. 1분마다 저절로 저장",
            "등록은 웹 자봉과 같은 나스 창고(deung.php)에 모임 — 웹에서 등록하신 분은 자봉 번호와 네 자리 숫자로 이어서 씀"
        ])
    ]
}
