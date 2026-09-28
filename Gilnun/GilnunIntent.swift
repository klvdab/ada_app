// 시리로 길눈 부르기 — 화면이 잠긴 채로도 "시리야, 길눈에게 말하기" 하고 할 일을 말씀하시면
// 시리가 받아 적어 길눈에게 넘기고, 길눈의 대답을 시리가 읽어 드립니다. 안내는 길눈이 이어서 합니다.
import AppIntents

struct GilnunMalIntent: AppIntent {
    static var title: LocalizedStringResource = "길눈에게 말하기"
    static var description = IntentDescription("화면이 잠긴 채로 길눈에게 가실 곳이나 할 일을 말합니다.")
    static var openAppWhenRun: Bool = false
    static var authenticationPolicy: IntentAuthenticationPolicy = .alwaysAllowed

    @Parameter(title: "할 말", requestValueDialog: IntentDialog("무엇을 할까요?"))
    var malsseum: String

    func perform() async throws -> some IntentResult & ProvidesDialog {
        await MainActor.run { Bonche.shared.sijak() }
        let dap = await MalHagi.shared.siriCheori(malsseum)
        return .result(dialog: IntentDialog(stringLiteral: dap))
    }
}

struct GilnunShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: GilnunMalIntent(), phrases: [
            "\(.applicationName)에게 말하기",
            "\(.applicationName) 불러 줘",
            "\(.applicationName)"
        ])
    }
}
