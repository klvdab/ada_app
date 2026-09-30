// AI점자도서관 앱 — 회원 등록과 회원 열쇠 (판 0.1.0, 빌드 260930-2)
// 앱 코드에는 나스 열쇠를 적지 않는다. 처음 켤 때 이름과 휴대전화 번호로 회원 등록을 하면
// 나스가 이 폰만의 회원 열쇠를 내주고, 그 열쇠로 도서관과 독서기만 열린다.
import Foundation
import SwiftUI

final class Hoewon: ObservableObject {
    static let shared = Hoewon()
    private let ud = UserDefaults.standard
    private let kTk = "hoewonTk", kIreum = "hoewonIreum"

    /// 나스에 보내는 회원 열쇠. 아무 스레드에서나 읽는다(UserDefaults는 스레드에 안전).
    var yeolsoe: String { ud.string(forKey: kTk) ?? "" }
    var ireum: String { ud.string(forKey: kIreum) ?? "" }
    @Published var deungrokdoem: Bool

    private init() { deungrokdoem = !(UserDefaults.standard.string(forKey: "hoewonTk") ?? "").isEmpty }

    struct Dap: Decodable { let ok: Bool; let tk: String?; let heo: Bool?; let mal: String?; let error: String? }

    /// 회원 등록. 성공하면 nil, 실패하면 알릴 말을 돌려준다.
    func deungrok(ireum: String, jeonhwa: String) async -> String? {
        let ir = ireum.trimmingCharacters(in: .whitespacesAndNewlines)
        let jh = jeonhwa.filter { $0.isNumber }
        var r = API.request(URL(string: API.base + "hoewon.php?a=deungrok")!)
        r.httpMethod = "POST"
        let b = "hw\(UUID().uuidString)"
        r.setValue("multipart/form-data; boundary=\(b)", forHTTPHeaderField: "Content-Type")
        var body = Data()
        for (k, v) in [("ireum", ir), ("jeonhwa", jh)] {
            body.append("--\(b)\r\nContent-Disposition: form-data; name=\"\(k)\"\r\n\r\n\(v)\r\n".data(using: .utf8)!)
        }
        body.append("--\(b)--\r\n".data(using: .utf8)!)
        r.httpBody = body
        do {
            let (d, _) = try await URLSession.shared.data(for: r)
            let dap = try JSONDecoder().decode(Dap.self, from: d)
            guard dap.ok, let tk = dap.tk, !tk.isEmpty else {
                return dap.error ?? "회원 등록을 하지 못했습니다. 잠시 뒤 다시 해 주십시오."
            }
            ud.set(tk, forKey: kTk); ud.set(ir, forKey: kIreum)
            await MainActor.run { self.deungrokdoem = true }
            return nil
        } catch {
            return "도서관에 닿지 못했습니다. 인터넷을 확인한 뒤 다시 등록을 눌러 주십시오."
        }
    }

    /// 나스가 열쇠를 받아 주지 않을 때(403) 부른다. 등록 화면이 다시 나온다.
    func ilheo() {
        ud.removeObject(forKey: kTk)
        DispatchQueue.main.async { self.deungrokdoem = false }
    }
}

struct DeungrokView: View {
    @ObservedObject var hw = Hoewon.shared
    @State private var ireum = ""
    @State private var jeonhwa = ""
    @State private var mal = ""
    @State private var gidarim = false
    @AccessibilityFocusState private var malFocus: Bool
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("AI점자도서관을 처음 쓰실 때 한 번만 회원 등록을 합니다. 이름과 휴대전화 번호를 적고 등록을 눌러 주십시오.")
                }
                Section {
                    TextField("이름", text: $ireum)
                        .textContentType(.name)
                    TextField("휴대전화 번호", text: $jeonhwa)
                        .keyboardType(.phonePad)
                        .textContentType(.telephoneNumber)
                    Button(gidarim ? "등록하는 중입니다" : "등록") {
                        gidarim = true; mal = ""
                        Task {
                            let e = await hw.deungrok(ireum: ireum, jeonhwa: jeonhwa)
                            gidarim = false
                            if let e {
                                mal = e; malFocus = true
                            } else {
                                Store.shared.say("회원 등록을 마쳤습니다. 이제 도서관을 쓰실 수 있습니다.")
                            }
                        }
                    }
                    .disabled(gidarim)
                }
                if !mal.isEmpty {
                    Section { Text(mal).accessibilityFocused($malFocus) }
                }
                Section {
                    Text("적어 주신 이름과 번호는 도서관 회원 관리에만 씁니다. 같은 번호로 다시 등록하면 예전 폰의 열쇠는 쓸 수 없게 됩니다.")
                }
            }
            .navigationTitle("회원 등록")
        }
    }
}
