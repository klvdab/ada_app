// 설정 탭에 더한 것 — 앱 2.9.0 (빌드 260928-11), 이사장님 승인(가1·나1).
// 알림(공지), 현 위치정보 말할 내용, 흔들면 자리 번호, 여기서 점검, 내 서류 보관함(콜 등록).
import SwiftUI
import UIKit
import PhotosUI
import Speech
import AVFoundation
import UserNotifications

// MARK: 알림

struct GongjiView: View {
    @ObservedObject private var e = GongjiEngine.shared
    @State private var heorakMal = ""
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("길눈을 만드는 곳에서 드리는 알림입니다.").font(.body)
                if e.mok.isEmpty {
                    Text(e.batneunJung ? "알림을 받아 오는 중입니다." : (e.mot ? "알림을 받아 오지 못했습니다. 신호를 확인해 주십시오." : "지금 올라온 알림이 없습니다."))
                        .font(.title3)
                        .accessibilityFocused($chojeom)
                } else {
                    Mokrok5(e.mok) { g in
                        NavigationLink { GongjiBonView(g: g) } label: {
                            Text(g.julMal + (e.ilgeotna(g) ? "" : " · 새 알림"))
                        }
                        .buttonStyle(KeunDanchu())
                    }
                    .id(e.mok.first?.id ?? "")
                }
                Button("긴급 공지를 폰 알림으로 받기 — 길눈이 켜져 있는 동안") {
                    GongjiEngine.heorakMutgi { ok in
                        heorakMal = ok ? "폰 알림을 켰습니다. 길눈이 켜져 있는 동안 긴급 공지가 오면 폰 알림으로 알려 드립니다."
                                       : "폰 알림이 꺼져 있습니다. 아이폰 설정의 길눈, 알림에서 켜 주십시오."
                        SoriEngine.shared.mal(heorakMal)
                    }
                }
                .buttonStyle(KeunDanchu())
                if !heorakMal.isEmpty { Text(heorakMal).font(.body) }
            }
            .padding()
        }
        .sokHwamyeon("알림")
        .onAppear {
            e.batgi()
            chojeom = false   // 2.12.1 매번 첫 줄로
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true }
        }
    }
}

struct GongjiBonView: View {
    let g: Gongji
    @Environment(\.dismiss) private var dismiss
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text((g.gingeup ? "긴급 공지 · " : "") + g.jemok)
                    .font(.title2.weight(.bold))
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityFocused($chojeom)
                if !g.nalMal.isEmpty { Text(g.nalMal).font(.footnote) }
                ForEach(Array(g.naeyong.components(separatedBy: "\n").filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.enumerated()), id: \.offset) { _, p in
                    Text(p).font(.title3)
                }
                Button("알림 목록으로") { dismiss() }.buttonStyle(KeunDanchu())
            }
            .padding()
        }
        .sokHwamyeon("알림")
        .onAppear {
            GongjiEngine.shared.ilgeumPyosi(g)
            chojeom = false   // 2.12.1 매번 첫 줄로
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true }
        }
    }
}

/// 길 찾기 첫 화면 맨 위 — 읽지 않은 긴급 공지(안내와 단추를 한 자리에)
struct GingeupGongjiJul: View {
    @ObservedObject private var e = GongjiEngine.shared
    var body: some View {
        if let g = e.gingeupSae {
            Button("긴급 공지 — \(g.jemok). 두드리면 읽어 드리고 내립니다") {
                // 2.12.0 길눈 말소리를 꺼 두셨어도 긴급 공지는 보이스오버로 읽어 드림
                let t = "긴급 공지. \(g.jemok). \(g.naeyong)"
                if Seoljeong.shared.malKyeojim { SoriEngine.shared.mal(t) } else { UIAccessibility.post(notification: .announcement, argument: t) }
                e.ilgeumPyosi(g)
            }
            .buttonStyle(KeunDanchu())
        }
    }
}

// MARK: 말하기 설정 — 얼마나, 무엇을 말할지(웹 길눈과 같은 이름과 기본값)

struct MalSeolDeoView: View {
    @ObservedObject private var s = Seoljeong.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button("얼마나 자세히 — 지금 \(Seoljeong.sangIreum[max(0, min(2, s.malSang))]) (누르면 바뀝니다)") {
                s.malSang = s.malSang >= 2 ? 1 : (s.malSang == 1 ? 0 : 2)
                SoriEngine.shared.mal("이제 \(Seoljeong.sangIreum[s.malSang])로 말씀드립니다.")
            }
            .buttonStyle(KeunDanchu())
            Button("꺾이는 곳 알리기 — " + (s.kkeokOn ? "켜져 있음 (누르면 끕니다)" : "꺼져 있음 (누르면 켭니다)")) {
                s.kkeokOn.toggle()
                SoriEngine.shared.mal(s.kkeokOn ? "꺾이는 곳을 알려 드립니다." : "꺾이는 곳을 알리지 않습니다.")
            }
            .buttonStyle(KeunDanchu())
            Button("몇 초 앞에서 알릴지 — 지금 \(s.kkeokCho)초 (누르면 바뀝니다)") {
                s.kkeokCho = s.kkeokCho == 5 ? 8 : (s.kkeokCho == 8 ? 12 : 5)
                SoriEngine.shared.mal("\(s.kkeokCho)초 앞에서 알려 드립니다. 걸으실 때는 \(Int((Double(s.kkeokCho) * 1.3).rounded()))미터쯤 앞입니다.")
            }
            .buttonStyle(KeunDanchu())
            Button("되풀이 사이 시간 — 지금 \(s.doepul)초 (누르면 바뀝니다)") {
                s.doepul = s.doepul == 4 ? 6 : (s.doepul == 6 ? 10 : 4)
                SoriEngine.shared.mal("한 번 말한 뒤 \(s.doepul)초 동안은 다시 말하지 않습니다.")
            }
            .buttonStyle(KeunDanchu())
            Button("지나는 곳 안내 — " + (s.gilOn ? "켜져 있음 (누르면 끕니다)" : "꺼져 있음 (누르면 켭니다)")) {
                s.gilOn.toggle()
                SoriEngine.shared.mal(s.gilOn ? "지나는 길을 알려 드립니다." : "지나는 길을 알리지 않습니다.")
            }
            .buttonStyle(KeunDanchu())
            Button("차 안에서 지나는 곳 말하는 간격 — 지금 \(s.gilGap)초 (누르면 바뀝니다)") {
                s.gilGap = s.gilGap == 30 ? 60 : (s.gilGap == 60 ? 120 : 30)
                SoriEngine.shared.mal("\(s.gilGap)초에 한 번까지 지금 달리는 길을 말씀드립니다.")
            }
            .buttonStyle(KeunDanchu())
            // 2.25.0 차 안 간판 알림
            Button("차 안 간판 알림 — " + (s.ganpanOn ? "켜져 있음 (누르면 끕니다)" : "꺼져 있음 (누르면 켭니다)")) {
                s.ganpanOn.toggle()
                SoriEngine.shared.mal(s.ganpanOn ? "달릴 때 지나는 가게와 건물을 왼쪽 오른쪽으로 알려 드립니다." : "지나는 가게와 건물을 알리지 않습니다.")
            }
            .buttonStyle(KeunDanchu())
            Button("서 있을 때 창밖 간판 읽기(카메라) — " + (s.ganpanKamera ? "켜져 있음 (누르면 끕니다)" : "꺼져 있음 (누르면 켭니다)")) {
                s.ganpanKamera.toggle()
                SoriEngine.shared.mal(s.ganpanKamera ? "차가 서 있거나 천천히 갈 때 카메라로 창밖 간판을 읽어 드립니다. 폰 뒤쪽 카메라를 창밖으로 향해 주십시오." : "창밖 간판을 카메라로 읽지 않습니다.")
            }
            .buttonStyle(KeunDanchu())
            Button("말 자르고 새로 말하기 — " + (s.malJaru ? "켜져 있음 (누르면 끕니다)" : "꺼져 있음 (누르면 켭니다)")) {
                s.malJaru.toggle()
                SoriEngine.shared.mal(s.malJaru ? "새 안내가 하던 말을 끊고 바로 나옵니다." : "하던 말을 다 마친 뒤에 새 안내가 나옵니다.")
            }
            .buttonStyle(KeunDanchu())
            Button("걸을 때 확신음 — " + (s.hwaksinEum ? "켜져 있음 (누르면 끕니다)" : "꺼져 있음 (누르면 켭니다)")) {
                s.hwaksinEum.toggle()
                if s.hwaksinEum { SoriEngine.shared.sori(.hwaksin) }
                SoriEngine.shared.mal(s.hwaksinEum ? "확신음을 켭니다. 제대로 가고 계시면 짧은 맑은 소리가 납니다." : "확신음을 끕니다. 방향이 틀어졌을 때의 말은 그대로 나옵니다.")
            }
            .buttonStyle(KeunDanchu())
            // 2.10.0 점지도 따라 걷기 — "제대로 가고 있습니다"를 몇 미터마다
            Button("제대로 가고 있다는 말 — 점지도에서 \(s.hwaksinGan)미터마다 (누르면 바뀝니다)") {
                s.hwaksinGan = s.hwaksinGan == 5 ? 10 : (s.hwaksinGan == 10 ? 20 : 5)
                SoriEngine.shared.mal("점지도를 따라 걸으실 때 \(s.hwaksinGan)미터마다 제대로 가고 있다고 말씀드립니다.")
            }
            .buttonStyle(KeunDanchu())
            Button("지금 설정으로 들어 보기") {
                let m: String
                switch s.malSang {
                case 0: m = "사거리 백삼십 미터. 두 시 우회전 왕산로."
                case 1: m = "백삼십 미터 앞 사거리. 곧장 다산로. 두 시 우회전 왕산로, 열 시 좌회전 정릉천동로."
                default: m = "백삼십 미터 앞이 사거리입니다. 곧장 가면 다산로입니다. 두 시 방향 우회전은 왕산로, 열 시 방향 좌회전은 정릉천동로입니다."
                }
                SoriEngine.shared.mal("이렇게 들으십니다. " + m)
            }
            .buttonStyle(KeunDanchu())
        }
    }
}

// MARK: 현 위치정보 말할 내용

struct JariSeoljeongView: View {
    @ObservedObject private var s = Seoljeong.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("지금 내 자리 듣기에서 무엇을 말할지 정합니다.").font(.body)
                jul("도로명주소 말하기", $s.jariJuso)
                jul("가까운 곳 이름 말하기", $s.jariGot)
                jul("지번주소 말하기", $s.jariJibeon)
                jul("국가지점번호 말하기", $s.jariJijeom)
                jul("위성 오차 말하기", $s.jariOcha)
                Button("지금 설정으로 내 자리 듣기") { AnnaeEngine.shared.jigeumJari() }.buttonStyle(KeunDanchu())
                Text("도심에서는 위성 오차가 5미터에서 20미터쯤 됩니다. 건물 이름이 옆 건물로 나올 수 있어 오차를 함께 말씀드립니다.").font(.body)
            }
            .padding()
        }
        .sokHwamyeon("현 위치정보 말할 내용")
    }

    private func jul(_ ireum: String, _ b: Binding<Bool>) -> some View {
        Button("\(ireum) — " + (b.wrappedValue ? "켜짐 (누르면 끕니다)" : "꺼짐 (누르면 켭니다)")) {
            b.wrappedValue.toggle()
            SoriEngine.shared.mal(ireum + "를 " + (b.wrappedValue ? "켰습니다." : "껐습니다."))
        }
        .buttonStyle(KeunDanchu())
    }
}

// MARK: 흔들면

struct HeundeulView: View {
    @ObservedObject private var s = Seoljeong.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button("폰 흔들기 — " + (s.heundeulKyeojim ? "켜져 있음 (누르면 끕니다)" : "꺼져 있음 (누르면 켭니다)")) {
                    s.heundeulKyeojim.toggle()
                    SoriEngine.shared.mal(s.heundeulKyeojim ? "폰을 세게 두 번 흔드시면 \(s.heundeulIl == "jari" ? "지금 자리 번호를 말씀드립니다" : "긴급통화서비스가 열립니다")."
                                                            : "폰 흔들기를 껐습니다.")
                }
                .buttonStyle(KeunDanchu())
                Button("흔들면 하는 일 — 지금은 " + (s.heundeulIl == "jari" ? "내 자리 번호 말하기" : "긴급통화 열기") + " (누르면 바뀝니다)") {
                    s.heundeulIl = s.heundeulIl == "jari" ? "gingeup" : "jari"
                    SoriEngine.shared.mal(s.heundeulIl == "jari" ? "폰을 세게 흔드시면 지금 자리의 국가지점번호를 말해 드립니다."
                                                                 : "폰을 세게 흔드시면 긴급통화서비스가 곧바로 열립니다.")
                }
                .buttonStyle(KeunDanchu())
                Button("지금 자리 번호 듣기") { Heundeul.jariBeonhoMal() }.buttonStyle(KeunDanchu())
                Text("국가지점번호는 산과 강과 바닷가처럼 도로명주소가 없는 곳에서 119가 쓰는 위치 번호입니다. 폰이 스스로 셈하므로 통신이 끊겨도 됩니다. 걷다가 한 번 튀는 것은 흔들기로 치지 않고, 세게 두 번 흔드셔야 합니다.")
                    .font(.body)
            }
            .padding()
        }
        .sokHwamyeon("흔들면 자리 번호")
    }
}

// MARK: 여기서 점검

struct YeogiJeomgeomView: View {
    @State private var jul: [String] = []
    @State private var doneun = false
    @State private var kkeutna = false
    @State private var bonaen = ""
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("아래 단추를 한 번 누르시면 지금 이 자리에서 길눈이 제대로 도는지 하나하나 살펴 읽어 드립니다.").font(.body)
                Button(doneun ? "점검하는 중입니다" : "점검 시작") { Task { await jeomgeom() } }
                    .buttonStyle(KeunDanchu())
                if kkeutna {
                    Button("점검 결과 클에게 보내기") { Task { await bonaegi() } }.buttonStyle(KeunDanchu())
                    if !bonaen.isEmpty { Text(bonaen).font(.body) }
                }
                ForEach(Array(jul.enumerated()), id: \.offset) { i, t in
                    if i == jul.count - 1 && kkeutna {
                        Text(t).font(.title3).accessibilityFocused($chojeom)
                    } else {
                        Text(t).font(.body)
                    }
                }
            }
            .padding()
        }
        .sokHwamyeon("여기서 점검")
    }

    @MainActor private func jeogi(_ t: String) { jul.append(t) }

    private func seobeo(_ pail: String, _ q: [(String, String)], _ ireum: String) async -> String {
        let t0 = Date()
        guard let j = await Nas.get(pail, q) else { return ireum + " 닿지 않음" }
        if (j["ok"] as? Bool) == false { return ireum + " 실패, " + ((j["error"] as? String) ?? "까닭 모름") }
        return ireum + " 정상, \(Int(Date().timeIntervalSince(t0) * 1000))밀리초"
    }

    private func jeomgeom() async {
        guard !doneun else { return }
        await MainActor.run { doneun = true; kkeutna = false; jul = []; bonaen = "" }
        SoriEngine.shared.mal("점검을 시작합니다.", .jeongbo)
        await jeogi("길눈 \(Pan.pan)판, 빌드 \(Pan.bild)")
        await jeogi(Tongsin.shared.yeongyeol ? "인터넷 이어져 있음" : "인터넷이 끊겨 있습니다")
        let w = WichiEngine.shared.jigeum
        if let w = w {
            await jeogi("위치 잡힘, 오차 약 \(Int(w.ochae))미터" + (w.georeumChu ? ", 위성이 흐려 걸음으로 이어 셈하는 중" : ""))
        } else {
            await jeogi("위치를 아직 잡지 못했습니다. 하늘이 트인 곳으로 나가 보십시오")
        }
        let la = String(format: "%.6f", w?.lat ?? 37.5665), lo = String(format: "%.6f", w?.lon ?? 126.9780)
        await jeogi(await seobeo("jeom.php", [("a", "jimyeong"), ("lat", la), ("lon", lo)], "지금 자리 이름"))
        await jeogi(await seobeo("jeom.php", [("a", "jangso"), ("q", "서울역"), ("lat", la), ("lon", lo)], "목적지 찾기"))
        await jeogi(await seobeo("jeom.php", [("a", "find"), ("lat", la), ("lon", lo)], "가까운 점지도"))
        await jeogi(await seobeo("jic.php", [("a", "yeok"), ("lat", la), ("lon", lo)], "지하철 역 찾기"))
        await jeogi(await seobeo("dulle.php", [("a", "yakguk"), ("lat", la), ("lon", lo)], "둘레 찾기"))
        await jeogi(await seobeo("gabolgot.php", [("a", "gabol"), ("lat", la), ("lon", lo)], "가 볼 곳"))
        let malOk = SFSpeechRecognizer.authorizationStatus() == .authorized && AVAudioSession.sharedInstance().recordPermission == .granted
        await jeogi(malOk ? "말로 하기 허락 받음" : "말로 하기 허락이 없습니다. 말로 하기를 한 번 두드려 허락해 주십시오")
        await jeogi(Seoljeong.shared.malKyeojim ? "길눈 말소리 켜짐" : "길눈 말소리가 꺼져 있습니다. 경고만 말합니다")
        let an = await UNUserNotificationCenter.current().notificationSettings()
        await jeogi(an.authorizationStatus == .authorized ? "폰 알림 허락 받음" : "폰 알림 허락이 없습니다. 알림 화면에서 켜실 수 있습니다")
        let mot = await MainActor.run { jul.filter { $0.contains("막힘") || $0.contains("실패") || $0.contains("닿지") || $0.contains("없습니다") || $0.contains("끊겨") || $0.contains("못") } }
        let kkeut = mot.isEmpty ? "점검 끝. 모두 정상입니다." : "점검 끝. 걸린 곳이 \(mot.count)군데입니다. " + mot.joined(separator: ". ")
        await jeogi(kkeut)
        SoriEngine.shared.mal(kkeut)
        Girok.shared.namgi("yeogi_jeomgeom", ["mot": mot.count])
        await MainActor.run {
            doneun = false
            kkeutna = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { chojeom = true }
        }
    }

    private func bonaegi() async {
        let j = await Nas.postJson("jeomgeom.php", [("a", "put")], ["jul": jul, "gigi": "길눈 앱 \(Pan.pan) (\(Pan.bild)) iOS \(UIDevice.current.systemVersion)", "jjok": "app"])
        await MainActor.run {
            bonaen = j != nil ? "보냈습니다. 클이 나스에서 그대로 읽습니다." : "보내지 못했습니다. 신호를 확인해 주십시오."
            SoriEngine.shared.mal(bonaen)
        }
    }
}

// MARK: 내 서류 보관함 — 복지카드와 신분증, 콜 등록

struct SajinGoreugi: UIViewControllerRepresentable {
    let kkeut: (UIImage?) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(kkeut) }
    func makeUIViewController(context: Context) -> PHPickerViewController {
        var c = PHPickerConfiguration()
        c.filter = .images
        c.selectionLimit = 1
        let p = PHPickerViewController(configuration: c)
        p.delegate = context.coordinator
        return p
    }
    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}
    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let kkeut: (UIImage?) -> Void
        init(_ k: @escaping (UIImage?) -> Void) { kkeut = k }
        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            guard let p = results.first?.itemProvider, p.canLoadObject(ofClass: UIImage.self) else { kkeut(nil); return }
            p.loadObject(ofClass: UIImage.self) { o, _ in
                DispatchQueue.main.async { self.kkeut(o as? UIImage) }
            }
        }
    }
}

struct SeoryuhamView: View {
    @State private var goreun = Seoryuham.jongryu[0].0
    @State private var kamera = false
    @State private var sajinham = false
    @State private var gaeng = 0
    @State private var jeok: [String: String] = [:]
    @State private var jiulGeot: String?

    private var kolDeul: [KolBeonho] { MalSajeon.shared.kolDeul() }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("여기에 담긴 것은 이 폰 안에만 있습니다. 서버로 올라가지 않고 아이클라우드 백업에도 넣지 않습니다. 폰을 바꾸시면 다시 담으셔야 합니다.")
                    .font(.body)
                DisclosureGroup("복지콜·교통약자 콜 번호 펼치기 — 지금 계신 지역") {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(Array(kolDeul.enumerated()), id: \.offset) { _, k in
                            Button("\(k.ireum) 전화 걸기" + (k.bigo.isEmpty ? "" : " — \(k.bigo)")) {
                                if let u = URL(string: "tel:" + k.jeonhwa) { UIApplication.shared.open(u) }
                            }
                            .buttonStyle(KeunDanchu())
                        }
                        Text("가입할 때 서류를 내라고 하면 아래 담아 둔 서류에서 보내기를 누르십시오. 문자나 메일로 보내는 창이 열리고 받는 곳은 직접 고르십니다.")
                            .font(.body)
                    }
                }
                .font(.title3)
                Text("담아 둔 서류").font(.title3.weight(.bold)).accessibilityAddTraits(.isHeader)
                let damgin = Seoryuham.jongryu.filter { Seoryuham.itna($0.0) }
                if damgin.isEmpty {
                    Text("아직 담아 둔 서류가 없습니다.").font(.body)
                } else {
                    ForEach(damgin, id: \.0) { x in
                        ShareLink(item: Seoryuham.pail(x.0), preview: SharePreview(x.1)) {
                            Text("\(x.1) 보내기")
                        }
                        .buttonStyle(KeunDanchu())
                        .accessibilityAction(named: "이 서류 빼기") { jiulGeot = x.0 }
                        .contextMenu { Button("이 서류 빼기") { jiulGeot = x.0 } }
                    }
                    if damgin.count > 1 {
                        ShareLink(items: damgin.map { Seoryuham.pail($0.0) }) { Text("담아 둔 서류 모두 보내기") }
                            .buttonStyle(KeunDanchu())
                    }
                }
                DisclosureGroup("서류 담기 펼치기 — 사진 찍기, 사진에서 고르기") {
                    VStack(alignment: .leading, spacing: 10) {
                        Picker("어떤 서류입니까", selection: $goreun) {
                            ForEach(Seoryuham.jongryu, id: \.0) { Text($0.1).tag($0.0) }
                        }
                        .pickerStyle(.menu)
                        Button("사진 찍기") {
                            if UIImagePickerController.isSourceTypeAvailable(.camera) { kamera = true }
                            else { SoriEngine.shared.mal("이 기기에서는 카메라를 쓸 수 없습니다.") }
                        }
                        .buttonStyle(KeunDanchu())
                        Button("이미 찍어 둔 사진에서 고르기") { sajinham = true }.buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
                DisclosureGroup("적어 두기 펼치기 — 이름, 생년월일, 연락처, 주소") {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("가입신청서에 늘 적게 되는 것들입니다. 한 번 적어 두시면 다음부터 다시 적지 않으셔도 됩니다.").font(.body)
                        ForEach(Seoryuham.jeokgi, id: \.0) { x in
                            TextField(x.1, text: Binding(get: { jeok[x.0] ?? "" }, set: { jeok[x.0] = $0 }))
                                .textFieldStyle(.roundedBorder)
                                .font(.title3)
                        }
                        Button("적은 것 담기") {
                            for (k, _) in Seoryuham.jeokgi { Seoryuham.jeokgi(k, (jeok[k] ?? "").trimmingCharacters(in: .whitespaces)) }
                            SoriEngine.shared.mal("적은 것을 담았습니다. 이 폰 안에만 있습니다.")
                            gaeng += 1
                        }
                        .buttonStyle(KeunDanchu())
                        if !Seoryuham.jeokeunGeul.isEmpty {
                            ShareLink(item: Seoryuham.jeokeunGeul) { Text("적어 둔 것 보내기") }.buttonStyle(KeunDanchu())
                        }
                    }
                }
                .font(.title3)
            }
            .padding()
            .id(gaeng)
        }
        .sokHwamyeon("내 서류 보관함")
        .onAppear {
            for (k, _) in Seoryuham.jeokgi { jeok[k] = Seoryuham.jeokeun(k) }
        }
        .fullScreenCover(isPresented: $kamera) {
            KameraJjikgi { img in
                kamera = false
                damgi(img)
            }
            .ignoresSafeArea()
        }
        .sheet(isPresented: $sajinham) {
            SajinGoreugi { img in
                sajinham = false
                damgi(img)
            }
        }
        .confirmationDialog("이 서류를 뺄까요?", isPresented: Binding(get: { jiulGeot != nil }, set: { if !$0 { jiulGeot = nil } }), titleVisibility: .visible) {
            Button("빼기", role: .destructive) {
                if let k = jiulGeot { Seoryuham.jiugi(k) }
                jiulGeot = nil
                gaeng += 1
                SoriEngine.shared.mal("서류를 뺐습니다.")
            }
            Button("그만두기", role: .cancel) { jiulGeot = nil }
        }
    }

    private func damgi(_ img: UIImage?) {
        guard let img = img else { return }
        let nm = Seoryuham.jongryu.first { $0.0 == goreun }?.1 ?? "서류"
        if Seoryuham.damgi(goreun, img) {
            SoriEngine.shared.mal("\(nm)\(MalHagi.eul(nm)) 담았습니다. 이 폰 안에만 있습니다.")
            Girok.shared.namgi("seoryu_damgi", [:])
        } else {
            SoriEngine.shared.mal("담지 못했습니다. 다시 해 주십시오.")
        }
        gaeng += 1
    }
}
