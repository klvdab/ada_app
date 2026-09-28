// 판번호와 빌드번호, 고친 기록 — 고칠 때마다 맨 위에 한 줄씩 더합니다.
import Foundation

struct Gochim: Identifiable {
    let id = UUID()
    let pan: String
    let bild: String
    let nal: String
    let naeyong: [String]
}

enum Pan {
    static let pan = "2.0.0"
    static let bild = "260928-1"
    /// 앱스토어에 올라간 짓기 번호(연월일시분)
    static var appBild: String {
        (Bundle.main.infoDictionary?["CFBundleVersion"] as? String) ?? ""
    }
    static let girok: [Gochim] = [
        Gochim(pan: "2.0.0", bild: "260928-1", nal: "2026-09-28", naeyong: [
            "길눈을 속까지 앱으로 다시 짓기 시작함 — 1단계 기초판. 웹을 띄우지 않음",
            "앱의 틀: 탭 바 다섯 개(모든 속 화면에도 보임), 뒤로 단추는 위에만, 두 손가락 문질러 뒤로",
            "소리 엔진: 모든 말을 한 줄로 세워 차례대로, 겹치지 않게, 음악이 나와도 안내가 들리게 음악 소리를 잠시 낮춤, 경고는 끌 수 없음",
            "위치 엔진: 폰이 잠겨도 주머니 속에서도 계속, 위성이 흐리면 걸음으로 이어 셈",
            "여정 엔진: 목적지와 지금 차례와 탈것을 한 곳에서, 이용자가 바로잡은 탈것이 가장 앞섬, 앱을 껐다 켜도 이어짐",
            "통신과 저장: 나스 통로 하나, 끊기면 받아 둔 자료로 버팀, 1분마다 저절로 저장",
            "기록: 폰이 겪은 일을 나스에 남김, 통신이 끊겨도 쌓아 두었다가 보냄",
            "기초 시험: 30분 동안 1분마다 상태를 말하고 나스에 남김",
            "말하기 설정, 도움말(찾기), 길눈 정보, 새로고침"
        ])
    ]
}
