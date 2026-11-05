// LVD 송출 아이폰 — 플린트 2 준비하기 (1.16.0판, 빌드 261105-BI20, 새방클, 이사장님 지시 2026-11-05 「아이폰에도 넣어 줘」)
// 안드로이드 FlintJunbiActivity 와 같은 일을 합니다. 앱이 공유기 관리 주소(192.168.8.1)에 관리자 비밀번호로 들어가(SSH),
// 앱 안에 든 두 파일을 올리고 준비 파일을 돌립니다. 비밀번호는 아이폰에 남기지 않습니다.
// 공유기의 SSH(dropbear)는 명령 한 번에 약 9천 바이트까지만 받으므로, 파일을 줄 단위로 6천 바이트씩 나누어 이어 붙이고 크기로 확인합니다.
import SwiftUI
import Citadel

enum FlintJunbiIl {
    private static func hanbeon(_ c: SSHClient, _ m: String) async throws -> String {
        let b = try await c.executeCommand(m, mergeStreams: true)
        return b.getString(at: b.readerIndex, length: b.readableBytes) ?? ""
    }

    /// 파일 하나 올리기 — 줄이 끊기지 않게 나누어 cat 으로 이어 붙임
    private static func olligi(_ c: SSHClient, _ ireum: String, _ gil: String) async throws {
        let d = ireum.split(separator: ".").map(String.init)
        guard d.count == 2, let u = Bundle.main.url(forResource: d[0], withExtension: d[1]),
              let t = try? String(contentsOf: u, encoding: .utf8) else { throw NSError(domain: "앱 안에 \(ireum) 파일이 없습니다", code: 1) }
        _ = try await hanbeon(c, "rm -f \(gil)")
        var jogak = ""
        var gil0 = 0
        func bonaegi() async throws {
            if jogak.isEmpty { return }
            _ = try await hanbeon(c, "cat >> \(gil) <<'BYOD_KKEUT_JUL'\n\(jogak)BYOD_KKEUT_JUL")
            jogak = ""; gil0 = 0
        }
        for jul in t.components(separatedBy: "\n").dropLast() {
            let n = jul.utf8.count + 1
            if gil0 + n > 6000 { try await bonaegi() }
            jogak += jul + "\n"; gil0 += n
        }
        try await bonaegi()
        let dap = try await hanbeon(c, "wc -c < \(gil)").trimmingCharacters(in: .whitespacesAndNewlines)
        let mok = t.hasSuffix("\n") ? t.utf8.count : t.components(separatedBy: "\n").dropLast().joined(separator: "\n").utf8.count + 1
        if dap != String(mok) { throw NSError(domain: "올린 크기가 다릅니다(\(ireum))", code: 2) }
    }

    /// 준비 전체. mal 은 메인에서 부름
    static func sijak(_ pw: String, mal: @escaping @MainActor (String) -> Void) async {
        var c: SSHClient?
        do {
            await mal("공유기에 들어가고 있습니다.")
            let k = try await SSHClient.connect(host: "192.168.8.1", port: 22,
                                                authenticationMethod: .passwordBased(username: "root", password: pw),
                                                hostKeyValidator: .acceptAnything(), reconnect: .never, algorithms: .all)
            c = k
            await mal("공유기에 들어갔습니다. 방송 서버 파일을 올립니다. 1분쯤 걸립니다.")
            try await olligi(k, "byod_flint.py", "/tmp/byod_flint.py")
            try await olligi(k, "byod_seolchi.sh", "/tmp/byod_seolchi.sh")
            let st = try await k.executeCommandStream("sh /tmp/byod_seolchi.sh 2>&1")
            var nam = ""
            var kkeut = false
            do {
                for try await o in st {
                    guard case .stdout(let b) = o else { continue }
                    nam += b.getString(at: b.readerIndex, length: b.readableBytes) ?? ""
                    while let r = nam.firstIndex(of: "\n") {
                        let l = String(nam[..<r]); nam = String(nam[nam.index(after: r)...])
                        if l.hasPrefix("BYOD: ") { await mal(String(l.dropFirst(6))) }
                        if l.trimmingCharacters(in: .whitespaces) == "BYOD-KKEUT" { kkeut = true }
                    }
                }
            } catch { if !kkeut { throw error } }   // 공유기가 다시 켜지며 끊기는 것은 괜찮음
            if kkeut { await mal("플린트 2 준비를 마쳤습니다. 공유기가 다시 켜집니다. 1분쯤 뒤 아이폰 설정의 와이파이에서 \(Flint.SSID) 에 붙이십시오.") }
            else { await mal("준비를 끝까지 마치지 못했습니다. 위의 마지막 안내를 보시고 다시 눌러 주십시오.") }
        } catch {
            let m = String(describing: error)
            if m.lowercased().contains("auth") { await mal("비밀번호가 맞지 않습니다. 공유기 관리자 비밀번호를 다시 넣어 주십시오.") }
            else if m.lowercased().contains("connect") || m.lowercased().contains("timeout") || m.contains("Refused") { await mal("공유기에 닿지 않습니다. 아이폰이 공유기 와이파이에 붙었는지 보십시오.") }
            else { await mal("준비하지 못했습니다. " + m) }
        }
        try? await c?.close()
    }
}

struct FlintJunbiView: View {
    let dwiro: () -> Void
    @State private var pw = ""
    @State private var allim = ""
    @State private var girok: [String] = []
    @State private var dolgo = false
    @AccessibilityFocusState private var wiChojeom: Bool

    private func mal(_ t: String) {
        allim = t; girok.append(t)
        UIAccessibility.post(notification: .announcement, argument: t)
    }
    private func sijak() {
        if dolgo { mal("준비하고 있습니다. 끝날 때까지 기다려 주십시오."); return }
        if pw.isEmpty { mal("공유기 관리자 비밀번호를 넣어 주십시오."); return }
        dolgo = true; girok = []
        let p = pw
        UIApplication.shared.isIdleTimerDisabled = true
        Task { @MainActor in
            await FlintJunbiIl.sijak(p) { t in mal(t) }
            dolgo = false
            UIApplication.shared.isIdleTimerDisabled = false
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Danchu(geul: "뒤로") { dwiro() }.accessibilityFocused($wiChojeom)
                Geul("플린트 2 준비하기", gulgeum: true)
                Geul("플린트 2에 방송 서버를 넣습니다. 새 공유기는 처음 한 번, 그 뒤로는 방송 서버를 새 판으로 바꿀 때 합니다. 방송이 없을 때 하십시오.")
                Geul("준비 1. 공유기의 인터넷 단자에 사무실 인터넷 선을 꽂고 전원을 켭니다.")
                Geul("준비 2. 공유기를 처음 켰다면, 관리자 비밀번호를 정하는 화면을 앞을 보는 분이 한 번 마쳐 주십시오.")
                Geul("준비 3. 이 아이폰을 공유기 와이파이에 붙입니다. 이미 준비한 공유기는 \(Flint.SSID) 입니다.")
                Text("공유기 관리자 비밀번호").font(.body).foregroundStyle(Saek.yeonhan).accessibilityHidden(true)
                SecureField("공유기 관리자 비밀번호", text: $pw)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .font(.title3)
                    .padding(10)
                    .foregroundStyle(Saek.geom)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.white))
                    .accessibilityLabel("공유기 관리자 비밀번호")
                Danchu(geul: "플린트 2 준비 시작", keun: true, norang: true) { sijak() }
                if !allim.isEmpty { Geul(allim) }
                ForEach(Array(girok.enumerated()), id: \.offset) { _, l in Geul(l, saek: Saek.yeonhan).accessibilityHidden(true) }
                Danchu(geul: "뒤로") { dwiro() }
            }
            .padding(20)
        }
        .accessibilityAction(.escape) { dwiro() }
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { wiChojeom = true } }
    }
}
