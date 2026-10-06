// 말뜻 풀이 — 애플 인텔리전스의 폰 안 인공지능(Foundation Models, iOS 26) — 앱 2.29.0 (빌드 261001-6, 대표님 승인 1)
// 사전으로 알아듣지 못한 말만, 폰 안의 인공지능에게 "이 말이 길눈의 어느 명령이냐"를 물어 길눈이 아는 말투로 바꿉니다.
// 대표님 원칙 — 쓸 때마다 돈 드는 방식은 택하지 않음: 폰 안에서 돌아 요금이 없고 인터넷도 필요 없음.
// 애플 인텔리전스가 켜진 폰(아이폰 15 프로 이후)에서만 되고, 그 밖의 폰은 지금처럼 사전으로만 알아들음.
// 4초 안에 답이 없으면 기다리지 않음. 말로 하기 설정에서 끌 수 있음.
import Foundation
#if canImport(FoundationModels)
import FoundationModels
#endif

enum MalAI {
    /// 지금 쓸 수 있는가
    static var sseulSuItda: Bool {
        guard Seoljeong.shared.malAI else { return false }
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            if case .available = SystemLanguageModel.default.availability { return true }
        }
        #endif
        return false
    }

    /// 2.51.0 말벗 견주기 — 설정과 상관없이 이 폰에서 폰 안 인공지능이 도는가
    static var daehwaGaneung: Bool {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            if case .available = SystemLanguageModel.default.availability { return true }
        }
        #endif
        return false
    }

    /// 2.51.0 말벗 견주기 — 폰 안 인공지능에게 대화로 묻기(서버 말벗과 같은 지침, 20초 안에 답이 없으면 nil)
    static func daehwa(_ t: String) async -> String? {
        await withTaskGroup(of: String?.self) { g in
            g.addTask { await MalAI.daehwaMutgi(t) }
            g.addTask { try? await Task.sleep(nanoseconds: 20_000_000_000); return nil }
            let d = await g.next() ?? nil
            g.cancelAll()
            return d
        }
    }

    private static func daehwaMutgi(_ t: String) async -> String? {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            let jisi = """
            너는 시각장애인의 길 안내 앱 길눈의 말벗이다. 반드시 우리말 존댓말로, 세 문장 안으로 짧게 대답한다. 표, 기호, 영어를 쓰지 않는다.
            방향은 시계 방향(정면이 12시)으로 말한다. 너는 사용자의 주변을 볼 수 없다. 날씨, 주변 모습, 위험, 버스 도착, 가게 영업처럼 지금 상황은 지어내지 말고 모른다고 말한다.
            길을 건너도 되는지처럼 안전이 걸린 판단은 대신하지 않는다.
            알아 둘 사실: 현장영상해설은 교육받은 현장영상해설사가 관광지, 공연, 행사, 일상의 현장에서 시각장애인 곁에서 보이는 것을 말로 실시간 풀어 드리는 해설이다.
            2011년 박광재 이사장이 처음 세웠고, 사단법인 한국시각장애인현장영상해설협회가 2015년부터 현장영상해설사를 길러 파견한다. 길눈은 이 협회가 만든 앱이다.
            """
            do {
                let s = LanguageModelSession(instructions: jisi)
                let r = try await s.respond(to: t)
                let d = r.content.trimmingCharacters(in: .whitespacesAndNewlines)
                return d.isEmpty ? nil : d
            } catch {
                return nil
            }
        }
        #endif
        return nil
    }

    /// 사용자의 말을 길눈이 알아듣는 말 한 줄로 — 맞는 것이 없으면 nil
    static func puri(_ t: String) async -> String? {
        await withTaskGroup(of: String?.self) { g in
            g.addTask { await MalAI.mutgi(t) }
            g.addTask { try? await Task.sleep(nanoseconds: 4_000_000_000); return nil }
            let d = await g.next() ?? nil
            g.cancelAll()
            return d
        }
    }

    private static func mutgi(_ t: String) async -> String? {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            let jisi = """
            너는 시각장애인 길 안내 앱 길눈의 말 풀이 도우미다. 사용자가 한 말을 길눈이 알아듣는 말 한 줄로 바꾼다.
            길눈이 알아듣는 말의 보기: \(MalHagi.doumalMal)
            규칙:
            1. 보기 가운데 뜻이 같은 말이 있으면 그 말을 보기 말투 그대로 한 줄만 답한다.
            2. 가고 싶은 곳이나 찾는 곳을 말했으면 장소 이름 찾아 줘 꼴로 답한다. 장소 이름은 사용자가 말한 그대로 쓴다.
            3. 어느 것에도 맞지 않으면 모름 이라고만 답한다.
            4. 설명, 따옴표, 마침표 없이 답만 쓴다.
            """
            do {
                let s = LanguageModelSession(instructions: jisi)
                let r = try await s.respond(to: t)
                let d = r.content.replacingOccurrences(of: "\"", with: "")
                let han = (d.components(separatedBy: .newlines).first ?? "")
                    .trimmingCharacters(in: CharacterSet(charactersIn: " .。").union(.whitespaces))
                if han.isEmpty || han.contains("모름") || han.count > 40 { return nil }
                return han
            } catch {
                return nil
            }
        }
        #endif
        return nil
    }
}
