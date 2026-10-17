// LVD투어스튜디오 앱 — 1.2.0판 화면 ① 들어오기·관리 (빌드 261017-T4, 방클, 이사장님 지시 「웹에 있는 모든 기능을 겉과 속이 같게」)
// 가입 신청, 비밀번호 바꾸기, 알림, 누구의 자리를 볼지, 가입 신청 함, 명부 관리, 봐 달라는 코스,
// 협회 승인 보관소(승인·돌려보내기·공개·내려받기·판 비교), 반 관리, 수업 진행판, 수업 채점표, 클에게 대본 맡기기
import SwiftUI
import UIKit

struct Mok: Identifiable { let id: String; let j: [String: Any] }
func ttae(_ s: String, _ n: Int = 16) -> String { String(s.replacingOccurrences(of: "T", with: " ").prefix(n)) }

// MARK: - 가입 신청 (들어오기 화면에서)
struct SignupView: View {
    @State private var name = ""
    @State private var tel = ""
    @State private var pw = ""
    @State private var pw2 = ""
    @State private var note = ""
    @State private var mal = ""
    @AccessibilityFocusState private var malF: Bool
    var body: some View {
        Form {
            Section {
                Text("이름, 전화번호, 비밀번호를 적어 보내시면 협회에서 확인한 뒤 열어 드립니다. 전화번호가 곧 아이디가 됩니다.")
                TextField("이름", text: $name)
                TextField("전화번호 (아이디가 됩니다)", text: $tel).keyboardType(.phonePad)
                SecureField("비밀번호 (일곱 글자 이상)", text: $pw)
                SecureField("비밀번호 한 번 더", text: $pw2)
                TextField("협회에 전할 말 (없어도 됩니다)", text: $note, axis: .vertical)
                Button("가입 신청 보내기") { Task { await bonae() } }
                if !mal.isEmpty { Text(mal).accessibilityFocused($malF) }
            }
        }
        .navigationTitle("가입 신청")
    }
    private func bonae() async {
        let t = tel.filter { $0.isNumber }
        if name.trimmingCharacters(in: .whitespaces).isEmpty { alri("이름을 적어 주십시오."); return }
        if t.count < 9 || t.count > 12 { alri("전화번호를 정확히 적어 주십시오."); return }
        if pw.count < 7 { alri("비밀번호는 여섯 글자보다 길게 정해 주십시오."); return }
        if pw != pw2 { alri("비밀번호 두 칸이 서로 다릅니다."); return }
        let j = await TourAPI.call("signup", ["id": t, "name": name.trimmingCharacters(in: .whitespaces), "pw": pw, "tel": tel.trimmingCharacters(in: .whitespaces), "note": note])
        if TourAPI.ok(j) { pw = ""; pw2 = ""; alri("신청을 보냈습니다. 협회에서 확인한 뒤 열어 드립니다." + ((j["again"] as? Bool) == true ? " 앞서 넣으신 신청은 이번 것으로 바뀝니다." : "") + " 열리면 아이디 " + t + "로 들어오십시오.") }
        else { alri(TourAPI.err(j)) }
    }
    private func alri(_ t: String) { mal = t; DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { malF = true } }
}

// MARK: - 비밀번호 바꾸기 (처음 들어오면 꼭)
struct PwView: View {
    @EnvironmentObject var store: TourStore
    var gangje = false
    @State private var old = ""
    @State private var new1 = ""
    @State private var new2 = ""
    @State private var mal = ""
    var body: some View {
        Form {
            Section {
                if gangje { Text("처음 들어오셨습니다. 쓰실 비밀번호로 바꿔 주십시오.").font(.headline) }
                SecureField("지금 비밀번호", text: $old)
                SecureField("새 비밀번호 (일곱 글자 이상)", text: $new1)
                SecureField("새 비밀번호 한 번 더", text: $new2)
                Button("비밀번호 바꾸기") {
                    Task {
                        if new1 != new2 { mal = "새 비밀번호 두 칸이 서로 다릅니다."; store.say(mal); return }
                        let j = await TourAPI.call("change_pw", ["old": old, "new": new1])
                        if TourAPI.ok(j) {
                            TourAPI.token = geulja(j["token"]); old = ""; new1 = ""; new2 = ""
                            store.me?["mustChange"] = false
                            mal = "비밀번호를 바꿨습니다."; store.say(mal)
                        } else { mal = TourAPI.err(j); store.say(mal) }
                    }
                }
                if !mal.isEmpty { Text(mal) }
            }
        }
        .navigationTitle("비밀번호 바꾸기")
    }
}

// MARK: - 코스 탭의 관리 묶음 (최고관리자만 알림·자리·도구, 모두에게 내 도구)
struct GwanriSection: View {
    @EnvironmentObject var store: TourStore
    @ObservedObject private var g = Gwan.shared
    var body: some View {
        if store.isMaster {
            Section("알림 — " + g.alertLine) {
                if g.signups > 0 { NavigationLink("기다리는 가입 신청 \(g.signups)건 보기") { SignupsView().dwiro() } }
                if g.reviews > 0 { NavigationLink("봐 달라는 코스 \(g.reviews)건 보기") { ReviewsView().dwiro() } }
                if g.finals > 0 { NavigationLink("승인 기다리는 시나리오 \(g.finals)건 보기") { FinalsView().dwiro() } }
                Button("알림 다시 살피기") { Task { await g.loadAlerts(tell: true) } }
            }
            Section("누구의 자리를 볼지") {
                if !g.asWho.isEmpty {
                    Text("지금 " + g.asName + " 님 자리를 보고 있습니다.").font(.headline)
                    Button("내 자리로") { Task { await g.boda("", "") } }
                }
                NavigationLink("다른 사람 자리 고르기") { AsPickView().dwiro() }
            }
            Section("최고관리자 도구") {
                NavigationLink("가입 신청 함") { SignupsView().dwiro() }
                NavigationLink("명부 관리") { UsersView().dwiro() }
                NavigationLink("반 관리") { ClassesView().dwiro() }
                NavigationLink("수업 진행판") { BoardView().dwiro() }
                NavigationLink("수업 채점표") { GradeView().dwiro() }
                NavigationLink("협회 승인 보관소") { FinalsView().dwiro() }
                NavigationLink("봐 달라는 코스") { ReviewsView().dwiro() }
            }
        }
        Section("도구") {
            NavigationLink("장소 지식 들이기 (코스 뼈대 짜기)") { JisikView().dwiro() }
            NavigationLink("우리가 확인한 것 채워 넣기") { UrijaryoView().dwiro() }
            NavigationLink("현장영상해설 화면 (협회 승인본)") { FinalsView(hyeonjang: true).dwiro() }
            NavigationLink("비밀번호 바꾸기") { PwView().dwiro() }
            NavigationLink("점검 (카메라·위치·마이크·나스)") { DiagView().dwiro() }
        }
    }
}

struct AsPickView: View {
    @EnvironmentObject var store: TourStore
    @ObservedObject private var g = Gwan.shared
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        List {
            Section("명부 \(g.users.count)명") {
                Button("내 자리") { Task { await g.boda("", ""); dismiss() } }
                Jang(items: g.users.map { Mok(id: geulja($0["id"]), j: $0) }) { u in
                    Button(geulja(u.j["name"]) + " (" + u.id + ")" + (u.id == g.asWho ? " — 지금 보는 자리" : "")) {
                        Task { await g.boda(u.id, geulja(u.j["name"])); dismiss() }
                    }
                }
            }
        }
        .navigationTitle("누구의 자리를 볼지")
        .task { await g.loadUsers() }
    }
}

// MARK: - 가입 신청 함
struct SignupsView: View {
    @EnvironmentObject var store: TourStore
    @ObservedObject private var g = Gwan.shared
    @State private var L: [[String: Any]] = []
    @State private var mal = "훑는 중입니다."
    @State private var neolBan = ""
    @State private var algil = ""
    @State private var algilWho = ""
    @State private var geojeol: [String: Any]? = nil
    var body: some View {
        List {
            AllimJul()
            Section { Text(mal) }
            if !algil.isEmpty {
                Section("그분께 알릴 말 — " + algilWho) {
                    Text(algil).textSelection(.enabled)
                    Button("글 복사하기") { UIPasteboard.general.string = algil; store.say("복사했습니다. 문자나 카카오톡에 붙여 보내십시오.") }
                    ShareLink("문자·카톡으로 보내기", item: algil)
                }
            }
            if !L.isEmpty {
                Section("받아들이면서 넣을 반") {
                    Picker("넣을 반", selection: $neolBan) {
                        Text("넣지 않음").tag("")
                        ForEach(g.classes.map { Mok(id: geulja($0["id"]), j: $0) }) { c in Text(geulja(c.j["name"])).tag(c.id) }
                    }
                    Button("기다리는 신청 \(L.count)건 모두 받아들이기") { Task { await moduBatgi() } }
                }
                Section("기다리는 신청 \(L.count)건") {
                    Jang(items: L.map { Mok(id: geulja($0["id"]), j: $0) }) { x in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(geulja(x.j["name"]) + " (" + x.id + ")").font(.headline)
                            Text("신청 " + ttae(geulja(x.j["at"])) + (geulja(x.j["tel"]).isEmpty ? "" : " / 연락처 " + geulja(x.j["tel"]))).font(.footnote)
                            if !geulja(x.j["note"]).isEmpty { Text(geulja(x.j["note"])) }
                            Button("받아들이기") { Task { await cheori("approve_signup", x.j) } }.buttonStyle(.borderless)
                            Button("거절하기") { geojeol = x.j }.buttonStyle(.borderless)
                        }
                    }
                }
            }
        }
        .navigationTitle("가입 신청 함")
        .task { await g.loadClasses(); await load() }
        .alert(geulja(geojeol?["id"]) + " 님의 신청을 거절할까요?", isPresented: Binding(get: { geojeol != nil }, set: { if !$0 { geojeol = nil } })) {
            Button("거절하기", role: .destructive) { let w = geojeol; geojeol = nil; if let w { Task { await cheori("reject_signup", w) } } }
            Button("그만두기", role: .cancel) { geojeol = nil }
        }
    }
    private func load() async {
        let j = await TourAPI.call("list_signups")
        guard TourAPI.ok(j) else { mal = TourAPI.err(j); return }
        L = (j["list"] as? [[String: Any]]) ?? []
        mal = L.isEmpty ? "기다리는 신청이 없습니다." : "기다리는 신청 \(L.count)건입니다."
        store.say(mal)
    }
    static func tellText(_ who: [String: Any], _ yes: Bool) -> String {
        let name = (geulja(who["name"]).isEmpty ? geulja(who["id"]) : geulja(who["name"])) + " 님"
        if yes {
            return name + ", 안녕하세요. 사단법인 한국시각장애인현장영상해설협회입니다.\n신청하신 LVD투어스튜디오 가입이 열렸습니다.\n\n주소: https://lvd.ada.or.kr/lvdts/lvdts.html\n아이디: " + geulja(who["id"]) + "\n비밀번호: 신청하실 때 정하신 그대로입니다.\n\n휴대전화로 여시면 현장에서 사진을 담으실 수 있습니다. 앱(투어스튜디오)으로도 같은 아이디로 들어오실 수 있습니다.\n처음이시면 도움말을 먼저 보십시오."
        }
        return name + ", 안녕하세요. 사단법인 한국시각장애인현장영상해설협회입니다.\n신청해 주셔서 고맙습니다. 다만 이번에는 가입을 열어 드리지 못했습니다.\n자세한 사정은 협회로 연락 주시면 말씀드리겠습니다."
    }
    private func cheori(_ a: String, _ who: [String: Any]) async {
        let id = geulja(who["id"])
        let j = await TourAPI.call(a, ["id": id])
        guard TourAPI.ok(j) else { store.say(TourAPI.err(j)); return }
        if a == "approve_signup" && !neolBan.isEmpty { _ = await TourAPI.call("save_class", ["id": neolBan, "add": [id]]) }
        algil = SignupsView.tellText(who, a == "approve_signup")
        algilWho = (geulja(who["name"])) + " (" + id + ")" + (geulja(who["tel"]).isEmpty ? " / 연락처가 적혀 있지 않습니다" : " / 연락처 " + geulja(who["tel"]))
        store.say((a == "approve_signup" ? "받아들였습니다. " : "거절했습니다. ") + "그분께 보낼 글을 만들어 두었습니다.")
        await load(); await g.loadAlerts(tell: false)
    }
    private func moduBatgi() async {
        var ok = 0
        for w in L {
            let j = await TourAPI.call("approve_signup", ["id": geulja(w["id"])])
            if TourAPI.ok(j) { ok += 1; if !neolBan.isEmpty { _ = await TourAPI.call("save_class", ["id": neolBan, "add": [geulja(w["id"])]]) } }
        }
        store.say("\(ok)건을 받아들였습니다.")
        await load(); await g.loadAlerts(tell: false)
    }
}

// MARK: - 명부 관리
struct UsersView: View {
    @EnvironmentObject var store: TourStore
    @ObservedObject private var g = Gwan.shared
    @State private var saeId = ""
    @State private var saeName = ""
    @State private var saePw = ""
    @State private var pwWho: String? = nil
    @State private var pwSae = ""
    var body: some View {
        List {
            AllimJul()
            Section("사람 넣기") {
                TextField("아이디 (영문 소문자·숫자 또는 전화번호)", text: $saeId).textInputAutocapitalization(.never).autocorrectionDisabled()
                TextField("이름", text: $saeName)
                SecureField("처음 비밀번호 (여섯 글자보다 길게)", text: $saePw)
                Button("명부에 넣기") {
                    Task {
                        let j = await TourAPI.call("add_user", ["id": saeId.trimmingCharacters(in: .whitespaces), "name": saeName.trimmingCharacters(in: .whitespaces), "pw": saePw])
                        if TourAPI.ok(j) { store.say(saeName + " 님을 넣었습니다. 처음 들어오면 비밀번호를 바꾸게 됩니다."); saeId = ""; saeName = ""; saePw = ""; await g.loadUsers() }
                        else { store.say(TourAPI.err(j)) }
                    }
                }
            }
            Section("모두 \(g.users.count)명") {
                Jang(items: g.users.map { Mok(id: geulja($0["id"]), j: $0) }) { u in
                    let locked = (u.j["locked"] as? Bool) == true
                    VStack(alignment: .leading, spacing: 6) {
                        Text(geulja(u.j["name"]) + " (" + u.id + ") — " + (geulja(u.j["role"]) == "master" ? "최고관리자" : "현장영상해설사") + (locked ? " / 막힘" : "")).font(.headline)
                        Button("비밀번호 다시 주기") { pwWho = u.id; pwSae = "" }.buttonStyle(.borderless)
                        Button(locked ? "다시 열기" : "못 들어오게 막기") {
                            Task {
                                let j = await TourAPI.call("set_lock", ["id": u.id, "lock": !locked])
                                store.say(TourAPI.ok(j) ? "바꿨습니다." : TourAPI.err(j)); await g.loadUsers()
                            }
                        }.buttonStyle(.borderless)
                    }
                }
            }
        }
        .navigationTitle("명부 관리")
        .task { await g.loadUsers() }
        .alert((pwWho ?? "") + " 님의 새 비밀번호", isPresented: Binding(get: { pwWho != nil }, set: { if !$0 { pwWho = nil } })) {
            SecureField("여섯 글자보다 길게", text: $pwSae)
            Button("주기") {
                let id = pwWho ?? ""; pwWho = nil
                Task { let j = await TourAPI.call("reset_pw", ["id": id, "pw": pwSae]); store.say(TourAPI.ok(j) ? "새 비밀번호를 주었습니다. 그분은 첫 로그인 때 다시 바꾸게 됩니다." : TourAPI.err(j)) }
            }
            Button("그만두기", role: .cancel) { pwWho = nil }
        }
    }
}

// MARK: - 봐 달라는 코스
struct ReviewsView: View {
    @EnvironmentObject var store: TourStore
    @EnvironmentObject var nav: Nav
    @State private var L: [[String: Any]] = []
    @State private var mal = "훑는 중입니다."
    var body: some View {
        List {
            AllimJul()
            Section { Text(mal) }
            Section {
                Jang(items: L.enumerated().map { Mok(id: "\($0.offset)", j: $0.element) }) { x in
                    VStack(alignment: .leading, spacing: 6) {
                        Text((geulja(x.j["ownerName"]).isEmpty ? geulja(x.j["owner"]) : geulja(x.j["ownerName"])) + " — " + geulja(x.j["name"])).font(.headline)
                        Text(geulja(x.j["place"]) + " / 지점 \(Int(sutja(x.j["spots"]) ?? 0))곳, 대본 \(Int(sutja(x.j["texts"]) ?? 0))곳 / " + ttae(geulja(x.j["at"]))).font(.footnote)
                        if !geulja(x.j["msg"]).isEmpty { Text(geulja(x.j["msg"])) }
                        Button("이 코스 열어 보기") {
                            Task {
                                await Gwan.shared.boda(geulja(x.j["owner"]), geulja(x.j["ownerName"]))
                                if await store.open(geulja(x.j["id"])) { store.say(geulja(x.j["name"]) + " 코스를 열었습니다. " + geulja(x.j["ownerName"]) + " 님 자리입니다."); nav.tab = 2 }
                            }
                        }.buttonStyle(.borderless)
                        Button("확인함 (내리기)") {
                            Task {
                                let j = await TourAPI.call("drop_review", ["owner": geulja(x.j["owner"]), "c": geulja(x.j["id"])])
                                store.say(TourAPI.ok(j) ? "내렸습니다." : TourAPI.err(j)); await load(); await Gwan.shared.loadAlerts(tell: false)
                            }
                        }.buttonStyle(.borderless)
                    }
                }
            }
        }
        .navigationTitle("봐 달라는 코스")
        .task { await load() }
    }
    private func load() async {
        let j = await TourAPI.call("list_reviews")
        guard TourAPI.ok(j) else { mal = TourAPI.err(j); return }
        L = (j["list"] as? [[String: Any]]) ?? []
        mal = L.isEmpty ? "봐 달라는 코스가 없습니다." : "봐 달라는 코스 \(L.count)건입니다."
        store.say(mal)
    }
}

// MARK: - 협회 승인 보관소 (모두: 내 판과 공개 승인본, 최고관리자: 전부)
struct FinalsView: View {
    @EnvironmentObject var store: TourStore
    var hyeonjang = false          // 현장영상해설 화면으로 쓸 승인본만
    @State private var L: [[String: Any]] = []
    @State private var master = false
    @State private var mal = "훑는 중입니다."
    @State private var sendUrl: URL? = nil
    @State private var seungin: [String: Any]? = nil
    @State private var dolyeo: [String: Any]? = nil
    @State private var kkadak = ""
    @State private var mukgi = ""
    var body: some View {
        List {
            AllimJul()
            Section { Text(mal) }
            if let u = sendUrl { Section { ShareLink("받은 파일 보내기·저장하기 — " + u.lastPathComponent, item: u) } }
            if master && !hyeonjang {
                Section("제출된 최종본 모두 내려받기") {
                    Button("제출된 최종본 모두 내려받기 (워드와 소리 묶음)") { Task { await moduNaeryeo() } }
                    if !mukgi.isEmpty { Text(mukgi) }
                }
            }
            let rows = hyeonjang ? L.filter { geulja($0["status"]) == "approved" } : L
            Section(hyeonjang ? "현장영상해설에 쓸 수 있는 승인본 \(rows.count)건" : "협회 DB에 저장된 판 \(rows.count)건") {
                Jang(items: rows.map { Mok(id: geulja($0["owner"]) + "|" + geulja($0["cid"]) + "|" + geulja($0["ver"]), j: $0) }) { x in
                    let v = Int(sutja(x.j["ver"]) ?? 0)
                    let prev = L.filter { geulja($0["owner"]) == geulja(x.j["owner"]) && geulja($0["cid"]) == geulja(x.j["cid"]) && Int(sutja($0["ver"]) ?? 0) < v }.map { Int(sutja($0["ver"]) ?? 0) }.max()
                    VStack(alignment: .leading, spacing: 6) {
                        Text((geulja(x.j["ownerName"]).isEmpty ? geulja(x.j["owner"]) : geulja(x.j["ownerName"])) + " — " + geulja(x.j["name"]) + " (\(v)판)").font(.headline)
                        Text(geulja(x.j["place"]) + " / 지점 \(Int(sutja(x.j["spots"]) ?? 0))곳, 대본 \(Int(sutja(x.j["texts"]) ?? 0))곳 / 사진 \(Int(sutja(x.j["photos"]) ?? 0))장, 소리 \(Int(sutja(x.j["audio"]) ?? 0))개 / 요청 " + ttae(geulja(x.j["requestedAt"]))).font(.footnote)
                        Text(FinalsView.st(x.j)).font(.subheadline.weight(.semibold))
                        if !geulja(x.j["msg"]).isEmpty { Text("전한 말: " + geulja(x.j["msg"])).font(.footnote) }
                        NavigationLink("이 판 현장영상해설 화면으로 보기") { HyeonjangView(entry: x.j).dwiro() }
                        if master && !hyeonjang {
                            Button("이 시나리오 내려받기 (워드와 소리 묶음)") { Task { await naeryeo(x.j) } }.buttonStyle(.borderless)
                            if let pv = prev { NavigationLink("앞 판(\(pv)판)과 비교") { CompareView(owner: geulja(x.j["owner"]), cid: geulja(x.j["cid"]), vA: pv, vB: v).dwiro() } }
                            if geulja(x.j["status"]) == "pending" {
                                Button("최종 승인") { seungin = x.j }.buttonStyle(.borderless)
                                Button("돌려보내기 (까닭 적기)") { Task { await dolyeoJunbi(x.j) } }.buttonStyle(.borderless)
                            }
                            if geulja(x.j["status"]) == "approved" {
                                let pub = (x.j["public"] as? Bool) == true
                                Button(pub ? "공개 거두기" : "현장영상해설사 모두에게 공개") { Task { await gonggae(x.j, !pub) } }.buttonStyle(.borderless)
                            }
                        } else if !master, let pv = prev, !hyeonjang {
                            NavigationLink("앞 판(\(pv)판)과 비교") { CompareView(owner: geulja(x.j["owner"]), cid: geulja(x.j["cid"]), vA: pv, vB: v).dwiro() }
                        }
                    }
                }
            }
        }
        .navigationTitle(hyeonjang ? "현장영상해설 화면" : "협회 승인 보관소")
        .task { await load() }
        .alert("이 판을 협회의 최종 승인본으로 확정합니다. 승인번호가 붙고 작성자에게 알려집니다. 승인할까요?", isPresented: Binding(get: { seungin != nil }, set: { if !$0 { seungin = nil } })) {
            Button("최종 승인") { let x = seungin; seungin = nil; if let x { Task { await bakkugi("approve_final", x, [:]) } } }
            Button("그만두기", role: .cancel) { seungin = nil }
        }
        .alert("돌려보내는 까닭 — 작성자에게 그대로 보입니다. 검사에서 걸린 것을 미리 넣어 두었습니다.", isPresented: Binding(get: { dolyeo != nil }, set: { if !$0 { dolyeo = nil } })) {
            TextField("까닭", text: $kkadak, axis: .vertical)
            Button("돌려보내기") { let x = dolyeo; dolyeo = nil; if let x { Task { await bakkugi("return_final", x, ["reason": kkadak]) } } }
            Button("그만두기", role: .cancel) { dolyeo = nil }
        }
    }
    static func st(_ x: [String: Any]) -> String {
        switch geulja(x["status"]) {
        case "approved": return "승인 완료 · " + geulja(x["no"]) + ((x["public"] as? Bool) == true ? " · 현장영상해설사 모두에게 공개" : " · 작성자만")
        case "pending": return "승인 기다리는 중"
        case "returned": return "돌려보냄 — " + geulja(x["reason"])
        case "superseded": return "새 판으로 대체됨 (" + geulja(x["no"]) + ")"
        default: return "앞 요청 (새 판으로 대체됨)"
        }
    }
    private func q(_ x: [String: Any]) -> [String: Any] { ["owner": geulja(x["owner"]), "c": geulja(x["cid"]), "v": Int(sutja(x["ver"]) ?? 0)] }
    private func load() async {
        let j = await TourAPI.call("list_final")
        guard TourAPI.ok(j) else { mal = TourAPI.err(j); return }
        L = (j["list"] as? [[String: Any]]) ?? []; master = (j["master"] as? Bool) == true
        mal = L.isEmpty ? "협회 DB에 저장된 판이 없습니다." : (hyeonjang ? "승인본을 고르시면 현장영상해설 화면이 열립니다." : "협회 DB에 저장된 판 \(L.count)건입니다.")
        store.say(mal)
    }
    private func bakkugi(_ a: String, _ x: [String: Any], _ more: [String: Any]) async {
        var b = q(x); for (k, v) in more { b[k] = v }
        let j = await TourAPI.call(a, b)
        guard TourAPI.ok(j) else { store.say(TourAPI.err(j)); return }
        store.say(a == "approve_final" ? "최종 승인했습니다. 승인번호 " + geulja(j["no"]) + "." : "돌려보냈습니다.")
        await load(); await Gwan.shared.loadAlerts(tell: false); await store.loadCourses()
    }
    private func dolyeoJunbi(_ x: [String: Any]) async {
        let g = await TourAPI.call("final_get", q(x))
        kkadak = ""
        if TourAPI.ok(g), let c = g["course"] as? [String: Any] {
            let k = Kos(c)
            kkadak = Wonchik.issueLines(Wonchik.scriptIssues(k, geulja((g["entry"] as? [String: Any])?["ownerName"]))).joined(separator: " / ")
        }
        dolyeo = x
    }
    private func gonggae(_ x: [String: Any], _ on: Bool) async {
        var b = q(x); b["on"] = on ? 1 : 0
        let j = await TourAPI.call("set_final_public", b)
        store.say(TourAPI.ok(j) ? (on ? "현장영상해설사 모두에게 공개했습니다." : "공개를 거두었습니다.") : TourAPI.err(j)); await load()
    }
    private func naeryeo(_ x: [String: Any]) async {
        let tk = await TourAPI.needTicket()
        store.say("내려받는 중입니다. 소리가 많으면 몇 분 걸릴 수 있습니다.")
        guard let u = Gwan.juso(["action": "final_down", "tk": tk, "o": geulja(x["owner"]), "c": geulja(x["cid"]), "v": geulja(x["ver"])]),
              let f = await Gwan.naeryeogi(u, ireum: geulja(x["ownerName"]) + "_" + geulja(x["name"]) + "_" + geulja(x["ver"]) + "판.zip") else { store.say("내려받지 못했습니다. 다시 눌러 주십시오."); return }
        sendUrl = f
        store.say("받았습니다. 압축 파일 안에 워드 파일(대본과 사진)과 소리 폴더가 있습니다. 위의 받은 파일 보내기·저장하기를 누르십시오.")
    }
    private func moduNaeryeo() async {
        var said = -1
        store.say("제출된 최종본을 모두 묶기 시작합니다. 몇 분 걸립니다. 이 화면을 그대로 두십시오.")
        while true {
            let r = await TourAPI.call("final_pack", [:], timeout: 120)
            guard TourAPI.ok(r) else { mukgi = TourAPI.err(r); store.say(mukgi); return }
            if (r["ready"] as? Bool) == true {
                let tk = await TourAPI.needTicket()
                mukgi = "다 묶었습니다. 코스 \(Int(sutja(r["n"]) ?? 0))건, " + geulja(r["mb"]) + "MB. 이 폰으로 받는 중입니다."
                store.say(mukgi)
                guard let u = Gwan.juso(["action": "final_pack_get", "tk": tk, "key": geulja(r["key"])]),
                      let f = await Gwan.naeryeogi(u, ireum: geulja(r["name"]).isEmpty ? "제출최종본.zip" : geulja(r["name"])) else { mukgi = "묶었으나 받지 못했습니다."; store.say(mukgi); return }
                sendUrl = f; mukgi = "받았습니다. 위의 받은 파일 보내기·저장하기를 누르십시오. 목록은 00_목록.txt 에 있습니다."; store.say(mukgi)
                return
            }
            let done = Int(sutja(r["done"]) ?? 0)
            mukgi = "\(Int(sutja(r["n"]) ?? 0))건 가운데 \(done)건 묶었습니다."
            if done >= said + 5 { said = done; if done > 0 { store.say(mukgi) } }
            try? await Task.sleep(nanoseconds: 4_000_000_000)
        }
    }
}

// MARK: - 판 비교 (웹 compareFinal)
struct CompareView: View {
    @EnvironmentObject var store: TourStore
    let owner: String, cid: String, vA: Int, vB: Int
    @State private var out: [(label: String, a: String, b: String, kind: String)] = []
    @State private var mal = "두 판을 견주는 중입니다."
    var body: some View {
        List {
            Section { Text(mal).font(.headline) }
            Section("바뀐 것") {
                Jang(items: out.enumerated().map { Jul(id: "\($0.offset)", t: "") }) { x in
                    let o = out[Int(x.id) ?? 0]
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\((Int(x.id) ?? 0) + 1). " + o.label + " — " + o.kind).font(.subheadline.weight(.semibold))
                        if o.a.count > 60 || o.b.count > 60 {
                            DisclosureGroup("앞 판과 새 판 글 보기") {
                                Text("\(vA)판: " + (o.a.isEmpty ? "(없음)" : o.a)).font(.footnote)
                                Text("\(vB)판: " + (o.b.isEmpty ? "(없음)" : o.b)).font(.footnote)
                            }
                        } else { Text((o.a.isEmpty ? "(없음)" : o.a) + " → " + (o.b.isEmpty ? "(없음)" : o.b)).font(.footnote) }
                    }
                }
            }
        }
        .navigationTitle("판 비교 — \(vA)판 → \(vB)판")
        .task { await gyeonju() }
    }
    private func gyeonju() async {
        let ra = await TourAPI.call("final_get", ["owner": owner, "c": cid, "v": vA])
        let rb = await TourAPI.call("final_get", ["owner": owner, "c": cid, "v": vB])
        guard let A = ra["course"] as? [String: Any], let B = rb["course"] as? [String: Any] else { mal = TourAPI.err(TourAPI.ok(ra) ? rb : ra); return }
        var o: [(String, String, String, String)] = []
        func cmp(_ label: String, _ a0: Any?, _ b0: Any?) {
            let a = geulja(a0), b = geulja(b0); if a == b { return }
            let num = Double(a) != nil && Double(b) != nil
            o.append((label, a, b, a.isEmpty ? "새로 들어감" : (b.isEmpty ? "지워짐" : (num ? "바뀜" : "바뀜 (\(a.count)자 → \(b.count)자)"))))
        }
        for (k, nm) in [("name", "코스 이름"), ("place", "장소"), ("meet", "집합 장소"), ("note", "코스 소개"), ("rain", "비 올 때 동선"), ("opening", "오프닝"), ("first", "1번 첫 만남 인사말"), ("history", "3번 장소 역사"), ("access", "3번 위치와 접근 교통편"), ("scale", "3번 규모와 형태"), ("points", "3번 전체 포인트"), ("tactile", "5번 촉각지도"), ("safety", "6번 안전 고지"), ("refs", "16번 참고문헌"), ("closing", "클로징")] { cmp(nm, A[k], B[k]) }
        let sa = (A["spots"] as? [[String: Any]]) ?? [], sb = (B["spots"] as? [[String: Any]]) ?? []
        for i in 0..<max(sa.count, sb.count) {
            let x = i < sa.count ? sa[i] : nil, y = i < sb.count ? sb[i] : nil
            let nm = "\(i + 1)번 " + (geulja(y?["name"]).isEmpty ? geulja(x?["name"]) : geulja(y?["name"]))
            guard let x else { o.append((nm, "", geulja(y?["text"]), "지점이 새로 생김")); continue }
            guard let y else { o.append((nm, geulja(x["text"]), "", "지점이 없어짐")); continue }
            for (k, l) in [("name", " 이름"), ("stay", " 머무는 시간"), ("move", " 걷는 시간"), ("text", " 대본"), ("to", " 어디까지"), ("access", " 이동 구간 안내"), ("touch", " 만져 볼 것"), ("safe", " 조심할 것"), ("gpos", " 현장영상해설사 위치")] { cmp(nm + l, x[k], y[k]) }
            let na = ((x["shots"] as? [Any]) ?? []).count, nb = ((y["shots"] as? [Any]) ?? []).count
            if na != nb { o.append((nm + " 사진", "\(na)장", "\(nb)장", "장수 바뀜")) }
        }
        out = o.map { (label: $0.0, a: $0.1, b: $0.2, kind: $0.3) }
        mal = "\(vA)판과 \(vB)판을 견주었습니다. 바뀐 것 \(out.count)가지입니다."
        store.say(mal + (out.isEmpty ? "" : " " + out.prefix(5).map { $0.label + " " + $0.kind }.joined(separator: ", ") + (out.count > 5 ? " 등" : "")))
    }
}

// MARK: - 반 관리
struct ClassesView: View {
    @EnvironmentObject var store: TourStore
    @ObservedObject private var g = Gwan.shared
    @State private var sae = ""
    var body: some View {
        List {
            AllimJul()
            Section("새 반") {
                TextField("새 반 이름", text: $sae)
                Button("새 반 만들기") {
                    Task {
                        let j = await TourAPI.call("save_class", ["name": sae.trimmingCharacters(in: .whitespaces), "cur": g.classes.isEmpty ? 1 : 0])
                        if TourAPI.ok(j) { store.say(sae + " 반을 만들었습니다. 반을 열어 학생을 넣으십시오."); sae = ""; await g.loadClasses() } else { store.say(TourAPI.err(j)) }
                    }
                }
            }
            Section("반 \(g.classes.count)개" + (g.curClassObj.map { ", 지금 수업 반은 " + geulja($0["name"]) } ?? ", 지금 수업 반은 아직 없습니다")) {
                Jang(items: g.classes.map { Mok(id: geulja($0["id"]), j: $0) }) { c in
                    NavigationLink(geulja(c.j["name"]) + " — \(((c.j["members"] as? [String]) ?? []).count)명" + (c.id == g.curClass ? ", 지금 수업 반" : "")) { ClassOneView(id: c.id).dwiro() }
                }
            }
        }
        .navigationTitle("반 관리")
        .task { await g.loadClasses(); await g.loadUsers() }
    }
}

struct ClassOneView: View {
    @EnvironmentObject var store: TourStore
    @ObservedObject private var g = Gwan.shared
    @Environment(\.dismiss) private var dismiss
    let id: String
    @State private var irum = ""
    @State private var gari = false
    var body: some View {
        let c = g.classes.first { geulja($0["id"]) == id } ?? [:]
        let mem = (c["members"] as? [String]) ?? []
        let users = g.users.filter { geulja($0["role"]) != "master" }.sorted { a, b in
            let ia = mem.contains(geulja(a["id"])) ? 0 : 1, ib = mem.contains(geulja(b["id"])) ? 0 : 1
            return ia != ib ? ia < ib : geulja(a["name"]) < geulja(b["name"])
        }
        List {
            AllimJul()
            Section(geulja(c["name"])) {
                Text("학생 \(mem.count)명이 이 반에 있습니다. 명부 \(users.count)명 가운데 반에 있는 사람이 먼저 옵니다.")
                Button(id == g.curClass ? "지금 수업 반입니다 (누르면 풀기)" : "이 반을 지금 수업 반으로") {
                    Task {
                        let on = id != g.curClass
                        let j = await TourAPI.call("save_class", ["id": id, "cur": on ? 1 : 0])
                        if TourAPI.ok(j) { await g.loadClasses(); store.say(on ? geulja(c["name"]) + " 반이 지금 수업 반이 되었습니다. 진행판과 채점표가 이 반만 봅니다." : "지금 수업 반을 풀었습니다.") } else { store.say(TourAPI.err(j)) }
                    }
                }
                TextField("반 이름 바꾸기", text: $irum)
                Button("이름 바꾸기") { Task { let j = await TourAPI.call("save_class", ["id": id, "name": irum]); if TourAPI.ok(j) { await g.loadClasses(); store.say("이름을 바꿨습니다.") } else { store.say(TourAPI.err(j)) } } }
                Button("이 반 가리기 (지우지 않고 목록에서만 감춤)") { gari = true }
            }
            Section("학생 넣고 빼기") {
                Jang(items: users.map { Mok(id: geulja($0["id"]), j: $0) }) { u in
                    let inC = mem.contains(u.id)
                    Button(geulja(u.j["name"]) + " (" + u.id + ") — " + (inC ? "이 반에 있음, 누르면 빼기" : "반에 없음, 누르면 넣기")) {
                        Task {
                            let j = await TourAPI.call("save_class", inC ? ["id": id, "remove": [u.id]] : ["id": id, "add": [u.id]])
                            if TourAPI.ok(j) { await g.loadClasses(); store.say((inC ? "뺐습니다. " : "넣었습니다. ") + "이 반 \(((g.classes.first { geulja($0["id"]) == id }?["members"] as? [String]) ?? []).count)명.") } else { store.say(TourAPI.err(j)) }
                        }
                    }
                }
            }
        }
        .navigationTitle("반 — " + geulja(c["name"]))
        .onAppear { irum = geulja(c["name"]) }
        .alert(geulja(c["name"]) + " 반을 가릴까요? 지우는 것이 아니라 목록에서만 감춥니다.", isPresented: $gari) {
            Button("가리기", role: .destructive) { Task { let j = await TourAPI.call("save_class", ["id": id, "on": 0]); if TourAPI.ok(j) { await g.loadClasses(); store.say("가렸습니다."); dismiss() } } }
            Button("그만두기", role: .cancel) {}
        }
    }
}

// MARK: - 수업 진행판 (웹 class_board)
struct BoardView: View {
    @EnvironmentObject var store: TourStore
    @EnvironmentObject var nav: Nav
    @ObservedObject private var g = Gwan.shared
    @State private var cls = "today"
    @State private var rows: [[String: Any]] = []
    @State private var sum = "살피는 중입니다."
    @State private var lastSum = ""
    @State private var auto = false
    @State private var timer: Timer? = nil
    static let SCNAME = ["scLogin": "들어오기", "scList": "코스 목록", "scWork": "코스", "scVer": "판 되돌리기", "scDiag": "점검", "scHelp": "도움말", "scSignup": "가입 신청", "scSignups": "가입 신청 함", "scUsers": "명부 관리", "scPw": "비밀번호 바꾸기", "scBoard": "수업 진행판", "scGrade": "수업 채점표", "scClass": "반 관리"]
    static func ago(_ s: Int) -> String { s < 0 ? "아직 소식 없음" : (s < 60 ? "방금" : (s < 3600 ? "\(s / 60)분 전" : "\(s / 3600)시간 전")) }
    static func w(_ x: [String: Any]) -> Int { ((sutja(x["help"]) ?? 0) > 0 ? 4 : 0) + ((sutja(x["fails"]) ?? 0) > 0 ? 2 : 0) + (Int(sutja(x["ago"]) ?? -1) >= 300 ? 1 : 0) }
    static func line(_ r: [String: Any]) -> String {
        var p: [String] = []
        let ag = Int(sutja(r["ago"]) ?? -1)
        if (sutja(r["help"]) ?? 0) > 0 { p.append("★도움 요청" + (geulja(r["helpMsg"]).isEmpty ? "" : "(" + geulja(r["helpMsg"]) + ")")) }
        if (sutja(r["fails"]) ?? 0) > 0 { p.append("★저장 실패 \(Int(sutja(r["fails"]) ?? 0))회") }
        if ag >= 300 { p.append("★멈춤 " + BoardView.ago(ag)) }
        let where0 = ag < 0 ? "아직 들어오지 않음" : (geulja(r["sc"]) == "scWork" ? "\(Int(sutja(r["stage"]) ?? 0))단계" : (SCNAME[geulja(r["sc"])] ?? "다른 화면"))
        var s = geulja(r["name"]) + " — " + where0
        if !geulja(r["cname"]).isEmpty { s += ", " + geulja(r["cname"]) }
        if !geulja(r["cid"]).isEmpty { s += ", 지점 \(Int(sutja(r["spots"]) ?? 0))곳 대본 \(Int(sutja(r["texts"]) ?? 0))곳 사진 \(Int(sutja(r["photos"]) ?? 0))장" }
        if !geulja(r["last"]).isEmpty && ag >= 0 { s += ", 마지막: " + geulja(r["last"]) + " (" + BoardView.ago(ag) + ")" }
        if !geulja(r["err"]).isEmpty { s += ", 오류: " + geulja(r["err"]) }
        return (p.isEmpty ? "" : p.joined(separator: " ") + " ") + s
    }
    var body: some View {
        List {
            AllimJul()
            Section("누구를 볼지") {
                Picker("누구를 볼지", selection: $cls) {
                    if !g.curClass.isEmpty { Text("지금 수업 반 — " + geulja(g.curClassObj?["name"])).tag(g.curClass) }
                    Text("오늘 들어온 사람만").tag("today")
                    ForEach(g.classes.filter { geulja($0["id"]) != g.curClass }.map { Mok(id: geulja($0["id"]), j: $0) }) { c in Text(geulja(c.j["name"]) + " 반").tag(c.id) }
                    Text("명부 모두").tag("all")
                }
                .onChange(of: cls) { _, _ in Task { await load(true) } }
                Button("다시 살피기") { Task { await load(true) } }
                Toggle("저절로 살피기 (30초마다, 바뀌었을 때만 읽음)", isOn: $auto)
                    .onChange(of: auto) { _, v in
                        timer?.invalidate(); timer = nil
                        if v { timer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { _ in Task { @MainActor in await load(false) } } }
                    }
                Text(sum)
            }
            Section("학생 \(rows.count)명 — 줄을 누르면 그 학생 코스가 열립니다") {
                Jang(items: rows.map { Mok(id: geulja($0["id"]), j: $0) }) { r in
                    Button(BoardView.line(r.j)) {
                        let c = geulja(r.j["cid"])
                        if c.isEmpty { store.say("이 학생은 아직 연 코스가 없습니다."); return }
                        auto = false
                        Task { await g.boda(r.id, geulja(r.j["name"])); if await store.open(c) { store.say(geulja(r.j["name"]) + " 님의 " + store.kos!.name + " 코스를 열었습니다."); nav.tab = 2 } }
                    }
                }
            }
        }
        .navigationTitle("수업 진행판")
        .task { await g.loadClasses(); if !g.curClass.isEmpty { cls = g.curClass }; await load(true) }
        .onDisappear { timer?.invalidate(); timer = nil }
    }
    private func load(_ sayIt: Bool) async {
        let j = await TourAPI.call("class_board", ["cls": cls])
        guard TourAPI.ok(j) else { sum = TourAPI.err(j); return }
        rows = ((j["rows"] as? [[String: Any]]) ?? []).sorted { a, b in
            let wa = BoardView.w(a), wb = BoardView.w(b); if wa != wb { return wa > wb }
            let aa = Int(sutja(a["ago"]) ?? -1), bb = Int(sutja(b["ago"]) ?? -1)
            return (aa < 0 ? 1_000_000_000 : aa) < (bb < 0 ? 1_000_000_000 : bb)
        }
        let act = rows.filter { let a = Int(sutja($0["ago"]) ?? -1); return a >= 0 && a < 300 }.count
        let help = rows.filter { (sutja($0["help"]) ?? 0) > 0 }.count
        let fail = rows.filter { (sutja($0["fails"]) ?? 0) > 0 }.count
        let idle = rows.filter { Int(sutja($0["ago"]) ?? -1) >= 300 }.count
        let s = "학생 \(rows.count)명 가운데 5분 안에 움직인 사람 \(act)명" + (help > 0 ? ", 도움 요청 \(help)명" : "") + (fail > 0 ? ", 저장 실패 \(fail)명" : "") + (idle > 0 ? ", 5분 넘게 멈춤 \(idle)명" : "") + "."
        let changed = s != lastSum; lastSum = s
        let f = DateFormatter(); f.dateFormat = "HH:mm"
        sum = s + " " + f.string(from: Date()) + " 기준."
        if sayIt || changed { store.say(s) }
    }
}

// MARK: - 수업 채점표 (웹 runGrade)
struct GradeView: View {
    @EnvironmentObject var store: TourStore
    @EnvironmentObject var nav: Nav
    @ObservedObject private var g = Gwan.shared
    struct Row: Identifiable { let id: String; let uid: String; let uname: String; let cid: String; let cname: String; let head: String; let lines: [String] }
    @State private var rows: [Row] = []
    @State private var mal = "훑기를 누르면 학생마다 코스를 열어 검사합니다."
    @State private var geul = ""
    var body: some View {
        List {
            AllimJul()
            Section {
                Button("학생 코스 모두 훑기") { Task { await hulgi() } }
                Text(mal)
                if !geul.isEmpty {
                    Button("결과를 글자로 복사하기") { UIPasteboard.general.string = geul; store.say("복사했습니다.") }
                    ShareLink("결과 보내기·저장하기", item: geul)
                }
            }
            Section("결과 — 사람이 읽어야 아는 8·9·14·15번의 판단은 기계가 세지 않습니다") {
                Jang(items: rows) { r in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(r.head).font(.subheadline.weight(.semibold))
                        if !r.lines.isEmpty { DisclosureGroup("걸린 것 \(r.lines.count)가지 — 펼치기") { ForEach(Array(r.lines.enumerated()), id: \.offset) { Text($0.element).font(.footnote) } } }
                        if !r.cid.isEmpty {
                            Button("이 코스 열어 보기") { Task { await g.boda(r.uid, r.uname); if await store.open(r.cid) { store.say(r.uname + " 님의 " + r.cname + " 코스를 열었습니다."); nav.tab = 2 } } }.buttonStyle(.borderless)
                        }
                    }
                }
            }
        }
        .navigationTitle("수업 채점표")
        .task { await g.loadClasses() }
    }
    static func stat(_ f: [String: Any]?) -> String {
        guard let f else { return "요청 전" }
        switch geulja(f["status"]) { case "approved": return "승인 " + geulja(f["no"]); case "pending": return "승인 기다림"; case "returned": return "돌려보냄"; default: return "" }
    }
    private func hulgi() async {
        rows = []; geul = ""
        mal = "학생 명부를 읽는 중입니다."
        await g.loadUsers()
        var users = g.users.filter { geulja($0["role"]) != "master" }
        if let cc = g.curClassObj { let m = (cc["members"] as? [String]) ?? []; users = users.filter { m.contains(geulja($0["id"])) }; mal = "지금 수업 반 \"" + geulja(cc["name"]) + "\" 학생 \(users.count)명을 훑습니다." }
        if users.isEmpty { mal = "학생이 없습니다."; store.say(mal); return }
        var out: [Row] = [], T: [String] = []
        for (n, u) in users.enumerated() {
            let uid = geulja(u["id"]), un = geulja(u["name"])
            mal = "\(n + 1) / \(users.count) — " + un + " 훑는 중"
            let lc = await TourAPI.call("list_courses", ["as": uid])
            let cs = ((lc["courses"] as? [[String: Any]]) ?? []).filter { ($0["gyobon"] as? Bool) != true && geulja($0["id"]) != "GYOBON" }
            if cs.isEmpty { out.append(Row(id: uid + "|", uid: uid, uname: un, cid: "", cname: "", head: un + " — 만든 코스 없음", lines: [])); T.append(un + " — 만든 코스 없음"); continue }
            for x in cs {
                let gc = await TourAPI.call("get_course", ["id": geulja(x["id"]), "as": uid])
                guard TourAPI.ok(gc), let c = gc["course"] as? [String: Any] else { out.append(Row(id: uid + "|" + geulja(x["id"]), uid: uid, uname: un, cid: "", cname: geulja(x["name"]), head: un + " — " + geulja(x["name"]) + " 읽지 못함", lines: [])); continue }
                let k = Kos(c)
                let L = Wonchik.issueLines(Wonchik.scriptIssues(k, un))
                let noText = k.spots.filter { Wonchik.bin($0.s("text")) }.count
                let head = un + " — " + k.name + " / 지점 \(k.spots.count)곳, 대본 \(k.spots.count - noText)곳 / 걸린 것 \(L.count)가지 / " + GradeView.stat(c["final"] as? [String: Any])
                out.append(Row(id: uid + "|" + k.id, uid: uid, uname: un, cid: k.id, cname: k.name, head: head, lines: L))
                T.append(head); T += L.map { " · " + $0 }
            }
        }
        rows = out
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd HH:mm"
        geul = "수업 채점표 " + f.string(from: Date()) + "\n" + T.joined(separator: "\n")
        mal = "학생 \(users.count)명, 코스 \(out.filter { !$0.cid.isEmpty }.count)개를 훑었습니다. 학생마다 걸린 것 몇 가지인지 읽어 드립니다."
        store.say(mal)
    }
}

// MARK: - 클에게 대본 맡기기 (최고관리자, 웹 askClaudeNas·checkClaude·applyScript)
struct ClaudeView: View {
    @EnvironmentObject var store: TourStore
    @State private var a = 1
    @State private var b = 1
    @State private var mal = ""
    @State private var buchyeo = ""
    @State private var chogan: [String: Any]? = nil
    var body: some View {
        Form {
            AllimJul()
            if let k = store.kos {
                Section("맡길 범위") {
                    Stepper("몇 번부터 — \(a)번", value: $a, in: 1...max(1, k.spots.count))
                    Stepper("몇 번까지 — \(b)번", value: $b, in: 1...max(1, k.spots.count))
                    Text("이 코스의 지점은 1번부터 \(k.spots.count)번까지 모두 \(k.spots.count)곳입니다. 대본이 빈 곳은 \(k.spots.filter { Wonchik.bin($0.s("text")) }.count)곳입니다. 고른 범위는 \(min(a, b))번부터 \(max(a, b))번까지입니다.")
                    Button("전체로") { a = 1; b = max(1, k.spots.count) }
                }
                Section("나스로 클에게 맡기기 (내려받기 없이)") {
                    Button("나스로 클에게 맡기기") { Task { await matgi(k) } }
                    Button("클 초안 왔는지 보기") { Task { await bogi(k, tell: true) } }
                    if let c = chogan {
                        Button("클 초안 넣기") { Task { neoki(k, buchyeo, fromAI: true); _ = await TourAPI.call("drop_claude", ["id": geulja(c["id"])]); chogan = nil } }
                        Button("이 요청 내리기") { Task { _ = await TourAPI.call("drop_claude", ["id": geulja(c["id"])]); chogan = nil; store.say("내렸습니다.") } }
                    }
                    if !mal.isEmpty { Text(mal) }
                }
                Section("받은 대본 붙여넣기 — 「1번」 줄 다음에 그 지점 글") {
                    TextField("붙여넣기", text: $buchyeo, axis: .vertical).lineLimit(6...30)
                    Button("넣기 (금지어가 있는 지점은 넣지 않음)") { neoki(k, buchyeo, fromAI: false) }
                }
            } else { KosEopseum() }
        }
        .navigationTitle("클에게 대본 맡기기")
        .onAppear { if let k = store.kos { b = max(1, k.spots.count) }; if let k = store.kos { Task { await bogi(k, tell: false) } } }
    }
    static func askText(_ k: Kos, _ a: Int, _ b: Int) -> String {
        let cps = Int(sutja(k.d["cps"]) ?? 330)
        var L = ["현장 투어 현장영상해설 대본을 부탁드립니다.", "코스: " + k.name + " / 장소: " + k.s("place") + " / 분야: " + (k.s("field").isEmpty ? "적지 않음" : k.s("field")), "집합 장소: " + k.s("meet")]
        if !k.s("note").isEmpty { L.append("코스 소개: " + k.s("note")) }
        L += ["", "지켜 주실 것", "1. 시각장애인을 위한 현장영상해설입니다. 방향은 시계 방향으로 짚어 주십시오.", "2. 눈으로 보는 것을 전제한 말(보인다·보시면 등)은 쓰지 마십시오. 놓여 있다·자리한다·이어진다처럼 쓰십시오.", "3. 전체를 먼저 말한 뒤 부분으로 좁혀 가십시오.", "4. 지점마다 머무는 시간이 있습니다. 그 시간 안에 읽을 길이로 써 주십시오(1분에 \(cps)자쯤).", "5. 답은 아래 형식으로만 주십시오.", " 1번", " 현장영상해설: (현장영상해설 글)", "", "지점 \(a)번부터 \(b)번까지입니다. 사진 파일 이름 앞의 번호가 지점 번호이고, 뒤에 몇 시 방향인지 적혀 있습니다."]
        for i in (a - 1)...(b - 1) where i >= 0 && i < k.spots.count {
            let s = k.spots[i]; let st = s.n("stay") ?? 0
            var line = "\(i + 1)번 / " + s.name + " / 머무는 시간 \(Int(st))분 / 넣을 수 있는 글자 수 \(Int((st * Double(cps)).rounded()))자"
            if !s.shots.isEmpty { line += " / 사진 " + s.shots.map { geulja($0["dir"]) }.joined(separator: ", ") }
            if !s.s("access").isEmpty { line += "\n 이동 구간 안내: " + s.s("access") }
            if !s.s("touch").isEmpty { line += "\n 만져 볼 것: " + s.s("touch") }
            let mm = ((s.d["memos"] as? [[String: Any]]) ?? []).map { geulja($0["text"]) }.filter { !$0.isEmpty }.joined(separator: " ")
            if !mm.isEmpty { line += "\n 현장 메모: " + mm }
            let tl = Wonchik.ttLines(s); if !tl.isEmpty { line += "\n 느낌·기억(테마투어): " + tl.joined(separator: " / ") }
            if !Wonchik.bin(s.s("text")) { line += "\n 지금 써 둔 글: " + s.s("text") }
            L.append(line)
        }
        return L.joined(separator: "\n")
    }
    private func matgi(_ k: Kos) async {
        if k.spots.isEmpty { store.say("먼저 지점을 잡아 주십시오."); return }
        let lo = min(a, b), hi = max(a, b)
        var shots: [[String: Any]] = []
        for i in (lo - 1)...(hi - 1) where i < k.spots.count { for p in k.spots[i].shots where !geulja(p["f"]).isEmpty { shots.append(["n": i + 1, "f": geulja(p["f"]), "dir": geulja(p["dir"])]) } }
        mal = "나스 요청함에 남기는 중입니다."
        await store.save(quiet: true)
        let z = await TourAPI.call("ask_claude", ["c": k.id, "a": lo, "b": hi, "ask": ClaudeView.askText(k, lo, hi), "shots": shots])
        guard TourAPI.ok(z) else { mal = TourAPI.err(z); store.say(mal); return }
        mal = "나스 요청함에 남겼습니다. 지점 \(lo)번부터 \(hi)번까지, 사진 \(Int(sutja(z["shots"]) ?? 0))장. 이제 대화창의 클에게 「투어스튜디오 요청함을 봐 달라」고 한마디만 하시면 클이 나스에서 열어 초안을 써 둡니다. 초안이 오면 클 초안 왔는지 보기를 누르십시오."
        store.say(mal)
    }
    private func bogi(_ k: Kos, tell: Bool) async {
        let z = await TourAPI.call("list_claude", ["c": k.id])
        guard TourAPI.ok(z) else { if tell { store.say(TourAPI.err(z)) }; return }
        let L = (z["list"] as? [[String: Any]]) ?? []
        if L.isEmpty { mal = ""; if tell { store.say("이 코스로 남긴 요청이 없습니다.") }; return }
        if let d = L.first(where: { geulja($0["status"]) == "done" }) {
            buchyeo = geulja(d["answer"]); chogan = d
            mal = "클 초안이 와 있습니다 (\(Int(sutja(d["from"]) ?? 0))번부터 \(Int(sutja(d["to"]) ?? 0))번까지, " + ttae(geulja(d["answeredAt"])) + "). 붙여넣기 칸에 넣어 두었으니 클 초안 넣기를 누르십시오."
            store.say(mal)
        } else if let w = L.first {
            mal = "요청이 나스 요청함에 있습니다 (\(Int(sutja(w["from"]) ?? 0))번부터 \(Int(sutja(w["to"]) ?? 0))번까지, " + ttae(geulja(w["at"])) + "). 아직 클 초안이 오지 않았습니다. 대화창의 클에게 요청함을 봐 달라고 하십시오."
            if tell { store.say(mal) }
        }
    }
    /// 웹 applyScript — 「1번」 줄 아래 글을 그 지점 대본으로. 금지어가 있는 지점은 넣지 않음
    private func neoki(_ k: Kos, _ txt: String, fromAI: Bool) {
        var cur = -1, buf: [Int: String] = [:]
        for ln in txt.components(separatedBy: .newlines) {
            if let r = ln.range(of: "^\\s*(\\d+)\\s*번", options: .regularExpression) {
                cur = (Int(ln[r].filter { $0.isNumber }) ?? 0) - 1; buf[cur] = buf[cur] ?? ""; continue
            }
            let t = ln.replacingOccurrences(of: "^\\s*(현장영상해설|설명)\\s*[:：]\\s*", with: "", options: .regularExpression).trimmingCharacters(in: .whitespaces)
            if cur >= 0 && !t.isEmpty { buf[cur] = (buf[cur].map { $0.isEmpty ? "" : $0 + " " } ?? "") + t }
        }
        var got = 0, bad: [String] = []
        for (i, t) in buf.sorted(by: { $0.key < $1.key }) where i >= 0 && i < k.spots.count {
            let b0 = Wonchik.findBanned(t)
            if !b0.isEmpty { bad.append("\(i + 1)번(" + b0.joined(separator: ", ") + ")"); continue }
            k.spots[i].d["text"] = t; if fromAI { k.spots[i].d["draft"] = true }; got += 1
        }
        if !bad.isEmpty { store.say("금지어가 있는 지점은 넣지 않았습니다. " + bad.joined(separator: " / ")) }
        else { store.say("\(got)곳에 대본을 넣었습니다." + (fromAI ? " 초안으로 표시해 두었으니 살펴보시고 손보십시오." : "")); if fromAI { buchyeo = "" } }
        store.bakkwim()
    }
}
