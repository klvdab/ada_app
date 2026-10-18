// AI점자도서관 앱 — 회원 등록과 회원 열쇠 (판 0.1.0, 빌드 260930-2)
// 0.8.2 (261019-L21) 실적용 회원 정보 보강 — 장애 유형·지역·연령대·이용 약관 동의(등록 때, 이미 등록한 분은 켤 때 한 번 여쭘. 이사장님 승인 2026-10-18 「1」)
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
    @Published var bogangPilyo: Bool      // 0.8.2 회원 정보 보강을 여쭐 때인가
    var bogangNajung = false              // 0.8.2 「다음에 하기」를 누르면 이번에 켠 동안은 다시 묻지 않음

    private init() {
        deungrokdoem = !(UserDefaults.standard.string(forKey: "hoewonTk") ?? "").isEmpty
        bogangPilyo = !UserDefaults.standard.bool(forKey: "hoewonBogang")
    }

    // 0.8.2 고르는 값 — 서버(hoewon.php hw_bogang_mok)와 같아야 함
    static let jangaeMok = ["시각장애(전맹)", "시각장애(저시력)", "그 밖의 독서장애", "장애 없음(가족·봉사자 등)", "밝히지 않음"]
    static let jiyeokMok = ["서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주", "해외", "밝히지 않음"]
    static let naiMok = ["10대 이하", "20대", "30대", "40대", "50대", "60대", "70대", "80대 이상", "밝히지 않음"]
    static let yakgwan = "AI점자도서관 이용 약관: 저작권이 있는 책은 저작권법 제33조에 따라 시각장애인 등 회원 본인만 듣고, 복사하거나 남에게 넘기지 않습니다. 협회는 이름과 전화번호를 회원 관리에만 쓰고, 이용 기록은 이름 없이 숫자로만 도서관 실적에 씁니다."

    struct SangDap: Decodable { let ok: Bool; let bogang: Bool? }
    /// 0.8.2 켤 때 서버에 보강했는지 물음 — 했으면 다시 묻지 않음
    func bogangHwakin() async {
        guard deungrokdoem, !UserDefaults.standard.bool(forKey: "hoewonBogang") else { return }
        var c = URLComponents(string: API.base + "hoewon.php")!
        c.queryItems = [URLQueryItem(name: "a", value: "sangtae"), URLQueryItem(name: "tk", value: yeolsoe)]
        guard let u = c.url, let res = try? await URLSession.shared.data(for: API.request(u, timeout: 15)),
              let dap = try? JSONDecoder().decode(SangDap.self, from: res.0), dap.ok else { return }
        if dap.bogang == true { ud.set(true, forKey: "hoewonBogang"); await MainActor.run { self.bogangPilyo = false } }
        else { await MainActor.run { self.bogangPilyo = true } }
    }

    /// 0.8.2 회원 정보 보강 보내기. 성공하면 nil, 실패하면 알릴 말
    func bogang(jangae: String, jiyeok: String, nai: String) async -> String? {
        var r = API.request(URL(string: API.base + "hoewon.php?a=bogang")!)
        r.httpMethod = "POST"
        let b = "hw\(UUID().uuidString)"
        r.setValue("multipart/form-data; boundary=\(b)", forHTTPHeaderField: "Content-Type")
        var body = Data()
        for (k, v) in [("tk", yeolsoe), ("jangae", jangae), ("jiyeok", jiyeok), ("nai", nai), ("dongui", "1")] {
            body.append("--\(b)\r\nContent-Disposition: form-data; name=\"\(k)\"\r\n\r\n\(v)\r\n".data(using: .utf8)!)
        }
        body.append("--\(b)--\r\n".data(using: .utf8)!)
        r.httpBody = body
        do {
            let (d, _) = try await URLSession.shared.data(for: r)
            let dap = try JSONDecoder().decode(Dap.self, from: d)
            guard dap.ok else { return dap.error ?? "보내지 못했습니다. 잠시 뒤 다시 해 주십시오." }
            ud.set(true, forKey: "hoewonBogang")
            await MainActor.run { self.bogangPilyo = false }
            return nil
        } catch {
            return "도서관에 닿지 못했습니다. 인터넷을 확인한 뒤 다시 눌러 주십시오."
        }
    }

    struct Dap: Decodable { let ok: Bool; let tk: String?; let heo: Bool?; let mal: String?; let error: String?; let bogang: Bool? }

    /// 회원 등록. 성공하면 nil, 실패하면 알릴 말을 돌려준다.
    func deungrok(ireum: String, jeonhwa: String, jangae: String = "", jiyeok: String = "", nai: String = "", dongui: Bool = false) async -> String? {
        let ir = ireum.trimmingCharacters(in: .whitespacesAndNewlines)
        let jh = jeonhwa.filter { $0.isNumber }
        var r = API.request(URL(string: API.base + "hoewon.php?a=deungrok")!)
        r.httpMethod = "POST"
        let b = "hw\(UUID().uuidString)"
        r.setValue("multipart/form-data; boundary=\(b)", forHTTPHeaderField: "Content-Type")
        var body = Data()
        for (k, v) in [("ireum", ir), ("jeonhwa", jh), ("jangae", jangae), ("jiyeok", jiyeok), ("nai", nai), ("dongui", dongui ? "1" : "")] {   // 0.8.2 보강 항목도 함께
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
            if dap.bogang == true { ud.set(true, forKey: "hoewonBogang") }   // 0.8.2
            let bp = dap.bogang != true
            await MainActor.run { self.bogangPilyo = bp }
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
    @State private var jangae = ""   // 0.8.2
    @State private var jiyeok = ""
    @State private var nai = ""
    @State private var dongui = false
    @AccessibilityFocusState private var malFocus: Bool
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("AI점자도서관을 처음 쓰실 때 한 번만 회원 등록을 합니다. 이름과 휴대전화 번호를 적고, 아래 세 가지를 고르고, 약관에 동의한 뒤 등록을 눌러 주십시오.")
                }
                Section {
                    TextField("이름", text: $ireum)
                        .textContentType(.name)
                    TextField("휴대전화 번호", text: $jeonhwa)
                        .keyboardType(.phonePad)
                        .textContentType(.telephoneNumber)
                }
                BogangGorugi(jangae: $jangae, jiyeok: $jiyeok, nai: $nai, dongui: $dongui)   // 0.8.2
                Section {
                    Button(gidarim ? "등록하는 중입니다" : "등록") {
                        if jangae.isEmpty || jiyeok.isEmpty || nai.isEmpty || !dongui { mal = "장애 유형, 사시는 지역, 연령대를 고르고 이용 약관에 동의해 주십시오."; malFocus = true; return }
                        gidarim = true; mal = ""
                        Task {
                            let e = await hw.deungrok(ireum: ireum, jeonhwa: jeonhwa, jangae: jangae, jiyeok: jiyeok, nai: nai, dongui: dongui)
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
                    Text("적어 주신 이름과 번호는 도서관 회원 관리에만 씁니다. 같은 번호로 다시 등록하면 예전 폰의 열쇠는 쓸 수 없게 됩니다. 앱이 잘 듣는지 살피려고 앱 판과 막힌 일(소리를 오래 기다림 등)을 협회 서버에 남깁니다. 이름, 전화번호, 듣는 책은 남기지 않습니다.")
                }
            }
            .navigationTitle("회원 등록")
        }
    }
}

// MARK: 0.8.2 회원 정보 보강 — 고르는 칸(등록 화면과 보강 화면이 함께 씀)
struct BogangGorugi: View {
    @Binding var jangae: String
    @Binding var jiyeok: String
    @Binding var nai: String
    @Binding var dongui: Bool
    var body: some View {
        Section("도서관 실적에 숫자로만 쓰는 정보") {
            Picker("장애 유형", selection: $jangae) {
                Text("고르십시오").tag("")
                ForEach(Hoewon.jangaeMok, id: \.self) { Text($0).tag($0) }
            }
            Picker("사시는 지역", selection: $jiyeok) {
                Text("고르십시오").tag("")
                ForEach(Hoewon.jiyeokMok, id: \.self) { Text($0).tag($0) }
            }
            Picker("연령대", selection: $nai) {
                Text("고르십시오").tag("")
                ForEach(Hoewon.naiMok, id: \.self) { Text($0).tag($0) }
            }
            Text(Hoewon.yakgwan).font(.footnote)
            Toggle("이용 약관에 동의합니다", isOn: $dongui)
        }
    }
}

/// 이미 등록한 회원께 켤 때 한 번 여쭙는 화면
struct BogangView: View {
    @ObservedObject var hw = Hoewon.shared
    @State private var jangae = ""
    @State private var jiyeok = ""
    @State private var nai = ""
    @State private var dongui = false
    @State private var mal = ""
    @State private var gidarim = false
    @AccessibilityFocusState private var cheot: Bool
    @AccessibilityFocusState private var malFocus: Bool
    var kkeut: () -> Void
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("도서관 실적을 내려고 세 가지를 여쭙니다. 이름 없이 숫자로만 셉니다. 한 번만 고르시면 다시 묻지 않습니다.")
                        .accessibilityFocused($cheot)
                }
                BogangGorugi(jangae: $jangae, jiyeok: $jiyeok, nai: $nai, dongui: $dongui)
                Section {
                    Button(gidarim ? "보내는 중입니다" : "보내기") {
                        if jangae.isEmpty || jiyeok.isEmpty || nai.isEmpty || !dongui { mal = "세 가지를 모두 고르고 이용 약관에 동의해 주십시오."; malFocus = true; return }
                        gidarim = true; mal = ""
                        Task {
                            let e = await hw.bogang(jangae: jangae, jiyeok: jiyeok, nai: nai)
                            gidarim = false
                            if let e { mal = e; malFocus = true } else { Store.shared.say("고맙습니다. 도서관으로 갑니다."); kkeut() }
                        }
                    }
                    .disabled(gidarim)
                    Button("다음에 하기") { hw.bogangNajung = true; kkeut() }
                }
                if !mal.isEmpty { Section { Text(mal).accessibilityFocused($malFocus) } }
            }
            .navigationTitle("회원 정보 여쭙기")
            .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { cheot = true } }
        }
    }
}
