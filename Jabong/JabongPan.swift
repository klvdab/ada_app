// 자봉 앱 판번호와 고친 기록 — 대표님 지시: 클이 만드는 모든 프로그램에 판번호·빌드번호를 붙이고 고칠 때마다 기록
import Foundation

enum JabongPan {
    static let pan = "2.0.0"
    static let bild = "261001-7"
    static var appBild: String { (Bundle.main.infoDictionary?["CFBundleVersion"] as? String) ?? "" }

    struct Gochim: Identifiable { let id = UUID(); let pan: String; let bild: String; let nal: String; let naeyong: [String] }
    static let girok: [Gochim] = [
        Gochim(pan: "2.0.0", bild: "261001-7", nal: "2026-10-01", naeyong: [
            "자봉 앱을 속까지 앱으로 다시 지음(대표님 승인) — 웹을 띄우던 껍데기를 버리고 모든 화면을 앱으로. 설계도 자봉앱_설계도_261001",
            "2단계 기초판: 탭 넷(봉사, 나눔, 내 기록, 알림·설정), 처음 등록(이름·연락처·지역·네 자리 숫자·1365 아이디, 요령 다섯 가지, 확인 문제 세 개, 자봉 번호, 보폭 재기)",
            "길눈 부품을 함께 씀: 위치, 걸음, 말소리(선희 목소리 포함), 기록, 나스 통신. 1분마다 저절로 저장",
            "등록은 웹 자봉과 같은 나스 창고(deung.php)에 모임 — 웹에서 등록하신 분은 자봉 번호와 네 자리 숫자로 이어서 씀"
        ])
    ]
}
