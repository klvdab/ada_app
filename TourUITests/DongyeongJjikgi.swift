// LVD투어스튜디오 사용법 동영상 화면 찍기 (빌드 261118-DY2 — 모든 종류에서 이름으로 찾음(글 칸이 Other 로 잡혀 시험이 멈추던 것), 아이디 칸은 「아이디」로도 찾음) (빌드 261117-DY1, 새방클 — 이사장님 승인 2026-11-17 「1,2」, 대클 형님 의견대로)
// 맥 일꾼(tour-dongyeong.yml)에서만 돌립니다. 단추 이름으로 차례로 눌러 가며 화면마다 사진을 남깁니다.
// 한 장면이 막혀도 시험을 멈추지 않고 다음 장면으로 갑니다(찍은 것만이라도 남기게).
// 사진 이름: 장면 번호_설명 — 새방클이 동영상 대본의 장면과 맞춥니다.
import XCTest

final class DongyeongJjikgi: XCTestCase {
    var app: XCUIApplication!
    var gil: [String] = []   // 막힌 장면 기록

    override func setUp() {
        continueAfterFailure = true
    }

    func ttuiugi(nagagi: Bool = false) {
        let e = ProcessInfo.processInfo.environment
        app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(ko)", "-AppleLocale", "ko_KR"]
        if nagagi {
            app.launchEnvironment["SAJIN_NAGAGI"] = "1"
        } else {
            for k in ["SAJIN_ID", "SAJIN_PW", "SAJIN_KOS", "SAJIN_TK"] { if let v = e[k], !v.isEmpty { app.launchEnvironment[k] = v } }
        }
        app.launch()
        sleep(nagagi ? 6 : 14)
    }

    func jjik(_ ireum: String) {
        sleep(2)
        let a = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        a.name = ireum
        a.lifetime = .keepAlways
        add(a)
    }

    /// 이름이 이 글로 시작하는 것(단추·칸·글)을 찾음
    func chatgi(_ geul: String) -> XCUIElement? {
        let p = NSPredicate(format: "label BEGINSWITH %@", geul)
        for q in [app.buttons, app.cells, app.links, app.switches] {
            let el = q.matching(p).firstMatch
            if el.exists { return el }
        }
        // DY2: 글은 종류를 묻지 않고 찾음 — StaticText 로 물으면 「Other 로 잡힘」 오류로 시험 전체가 멈췄음
        let el = app.descendants(matching: .any).matching(p).firstMatch
        if el.exists { return el }
        let t = app.textFields.matching(NSPredicate(format: "placeholderValue BEGINSWITH %@", geul)).firstMatch
        if t.exists { return t }
        return nil
    }

    /// 보일 때까지 아래로 밀며 찾음
    @discardableResult
    func boyeo(_ geul: String, mil: Int = 12) -> XCUIElement? {
        for _ in 0...mil {
            // DY3: 머리말(Other)에 isHittable 을 물으면 시험 전체가 멈춤 — 화면 안에 있는지만 자리로 봄
            if let el = chatgi(geul) {
                let f = el.frame, a = app.frame
                if !f.isEmpty && f.midY > a.minY + 60 && f.midY < a.maxY - 100 { return el }
            }
            app.swipeUp(velocity: .slow)
            usleep(400_000)
        }
        gil.append("못 찾음: " + geul)
        return nil
    }

    @discardableResult
    func nureugi(_ geul: String) -> Bool {
        guard let el = boyeo(geul) else { return false }
        el.tap()
        sleep(3)
        return true
    }

    /// 탭으로 가서 맨 위로(같은 탭을 한 번 더 누르면 첫 화면 맨 위)
    func tab(_ ireum: String) {
        let b = app.tabBars.buttons[ireum]
        if b.exists { b.tap(); usleep(600_000); b.tap(); sleep(2) } else { gil.append("탭 없음: " + ireum) }
    }

    func dwiro() {
        let b = app.navigationBars.buttons.element(boundBy: 0)
        if b.exists { b.tap(); sleep(2) }
    }

    /// 제목 글이 화면 위쪽에 오게 밀어 올림
    func wiro(_ geul: String) {
        if let el = boyeo(geul) {
            var n = 0
            while el.frame.minY > app.frame.height * 0.35 && n < 4 { app.swipeUp(velocity: .slow); usleep(400_000); n += 1 }
        }
    }

    func test1_Deureogagi() {
        ttuiugi(nagagi: true)
        jjik("101_들어오기화면")
        if let id = chatgi("전화번호") ?? chatgi("아이디") ?? (app.textFields.firstMatch.exists ? app.textFields.firstMatch : nil) { id.tap(); sleep(1); jjik("102_아이디칸") }
        if chatgi("가입 신청하기") != nil { jjik("103_가입신청하기") }
    }

    func test2_Hwamyeon() {
        ttuiugi()
        tab("코스"); jjik("201_코스탭_첫화면")
        wiro("코스 목록"); jjik("202_코스목록")
        tab("코스"); if nureugi("코스 정보") { jjik("203_코스정보"); app.swipeUp(); jjik("204_코스정보_아래"); dwiro() }
        tab("코스"); if nureugi("장소 지식 들이기") { jjik("205_장소지식들이기"); dwiro() }
        tab("코스"); if nureugi("기능 찾기") { jjik("206_기능찾기"); dwiro() }
        tab("설정"); jjik("207_설정탭")
        if nureugi("무엇이 바뀌었나") { jjik("208_무엇이바뀌었나"); dwiro() }
        tab("설정"); wiro("도움말 찾기"); jjik("209_도움말찾기")
    }

    func test3_Kos() {
        ttuiugi()
        tab("코스"); wiro("지금 연 코스"); jjik("301_지금연코스")
        wiro("다음 할 일"); jjik("302_다음할일")
        tab("코스"); if nureugi("대본 살펴보기") { jjik("303_대본살펴보기"); wiro("원칙 16개"); jjik("304_원칙16개점검표"); dwiro() }
        tab("코스"); if nureugi("내보내기") { jjik("305_내보내기"); wiro("협회 최종 승인"); jjik("306_협회최종승인"); dwiro() }
        tab("코스"); wiro("새 코스 만들기"); jjik("307_새코스만들기")
        tab("코스"); wiro("휴지통"); jjik("308_휴지통")
    }

    func test4_Dapsa() {
        ttuiugi()
        tab("답사"); jjik("401_답사탭_첫화면")
        wiro("경계 적기"); jjik("402_경계적기")
        wiro("사진 찍기"); jjik("403_사진찍기")
        wiro("담긴 사진 보기"); jjik("404_담긴사진")
        wiro("화면 안 보고 답사하기"); jjik("405_이어폰단추")
        wiro("걸으며 저절로 재기"); jjik("406_걸으며저절로재기")
        wiro("지점 옮겨 보기"); jjik("407_지점옮겨보기")
    }

    func test5_Jijeom() {
        ttuiugi()
        tab("지점"); jjik("501_지점탭_첫화면")
        wiro("지점 목록"); jjik("502_지점목록")
        // 오프닝 다음 첫 지점을 엶
        let p = NSPredicate(format: "label CONTAINS %@", "1지점")
        let c = app.buttons.matching(p).firstMatch
        if c.exists { c.tap(); sleep(3) } else if !nureugi("1지점") { gil.append("1지점 못 엶") }
        jjik("503_지점한화면")
        wiro("경계와 시간"); jjik("504_경계와시간")
        wiro("말로 대본 쓰기"); jjik("505_말로대본쓰기")
        if let b = chatgi("넣을 칸") { _ = b; jjik("506_넣을칸") }
        app.swipeUp(); jjik("507_대본칸")
        wiro("방향 도우미"); jjik("508_방향도우미")
        wiro("현장 메모"); jjik("509_현장메모")
        wiro("사진"); jjik("510_사진")
        dwiro()
        tab("지점"); wiro("지점 넣기"); jjik("511_지점넣기")
    }

    func test6_Haeseol() {
        ttuiugi()
        tab("현장영상해설"); jjik("601_현장영상해설탭")
        wiro("지점 차례"); jjik("602_지점차례")
        wiro("현장에서"); jjik("603_현장에서_승인본")
        tab("코스"); if nureugi("내보내기") { wiro("협회 최종 승인"); jjik("604_협회최종승인요청"); app.swipeUp(); jjik("605_승인요청_아래"); dwiro() }
    }

    override func tearDown() {
        if !gil.isEmpty {
            let a = XCTAttachment(string: gil.joined(separator: "\n"))
            a.name = "막힌장면_" + name
            a.lifetime = .keepAlways
            add(a)
        }
        app?.terminate()
    }
}
