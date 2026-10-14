// LVD투어스튜디오 앱 — 설정 탭과 도움말 (판 1.0.0, 빌드 261012-T1)
// 새로고침은 여기 하나. 그 아래 업데이트, 말소리, 도움말 찾기, 바뀐 것, 나가기.
import SwiftUI

struct Doum: Identifiable { let id: String; let t: String; let d: String }

enum DoumMok {
    static let items: [Doum] = [
        Doum(id: "a1", t: "지점 경계 규칙", d: RULE_TXT + " 지점 사이를 걷는 이동 시간은 다음 지점의 몫입니다. 작성원칙 통합본 7번입니다."),
        Doum(id: "a2", t: "탭 다섯", d: "아래 탭은 코스, 답사, 지점, 현장영상해설, 설정 다섯입니다. 지금 탭을 한 번 더 누르면 그 탭 첫 화면 맨 위로 갑니다. 뒤로 단추는 화면 위 왼쪽에 있고, 두 손가락 문지르기로도 뒤로 갑니다."),
        Doum(id: "a3", t: "코스 열기와 새 코스", d: "코스 탭의 코스 목록에서 코스 이름을 두드리면 열립니다. 목록은 다섯 줄씩 보이고 더 보기와 이전 보기로 넘깁니다. 새 코스는 맨 아래 새 코스 만들기에서 이름과 장소를 적고 만듭니다. 새 코스에는 오프닝 지점과 클로징 지점이 들어 있습니다."),
        Doum(id: "a4", t: "교본", d: "교본은 읽기만 됩니다. 코스 탭의 교본을 내 코스로 복사를 누르면 내 코스가 되어 고칠 수 있습니다."),
        Doum(id: "a5", t: "답사 — 여기서부터 다음 지점 시작", d: "현장에서 앞 지점의 현장영상해설을 마치고 출발하는 자리에 서면 답사 탭의 여기서부터 다음 지점 시작을 누르십시오. 앞 지점의 끝과 다음 지점의 시작이 이 자리와 이 시각으로 적힙니다. 앞 지점의 어디까지 칸에 그 자리를 말로 적어 두십시오."),
        Doum(id: "a6", t: "답사 — 여기서부터 새 지점 시작", d: "아직 없는 지점을 현장에서 만들 때 씁니다. 지금 지점 바로 뒤에 새 지점이 들어가고, 이 자리가 앞 지점의 끝이자 새 지점의 시작이 됩니다."),
        Doum(id: "a7", t: "답사 — 사진 찍기", d: "정지 현장영상해설 자리에서 참여자가 바라볼 쪽을 12시로 두고, 12시, 3시, 6시, 9시 방향 차례로 찍습니다. 한 장을 올리면 다음 방향이 저절로 골라집니다. 첫 사진 때 지점 자리가 적히고, 앞 지점에서 걸어온 거리로 이동 시간을 잡습니다."),
        Doum(id: "a8", t: "지점 한 화면", d: "지점 탭에서 지점을 두드리면 그 지점의 어디서부터, 어디까지, 시간, 이동 구간 안내, 만져 볼 수 있는 것, 조심할 것, 현장영상해설사 위치, 대본을 한 화면에서 고칩니다. 이동 시간과 정지 현장영상해설 시간은 위아래로 쓸어 바꿉니다. 맨 아래에 앞 지점, 다음 지점, 한 칸 앞으로, 한 칸 뒤로, 지금 저장이 있습니다."),
        Doum(id: "a9", t: "오프닝 지점과 클로징 지점의 대본", d: "오프닝 지점의 대본은 웹 4단계의 오프닝 칸과 같은 글이고, 클로징 지점의 대본은 클로징 칸과 같은 글입니다. 정지 현장영상해설 시간을 0으로 두면 대본 읽는 시간으로 셉니다."),
        Doum(id: "a10", t: "방향 도우미", d: "지점 한 화면의 방향 도우미에서 12시 방향부터 11시 방향까지 골라 대본 끝에 붙입니다. 방향은 늘 시계 방향으로 말합니다."),
        Doum(id: "a11", t: "저장", d: "고친 뒤 2초가 지나면 저절로 저장합니다. 인터넷이 끊겨 저장하지 못하면 폰에 남겨 두고, 설정의 폰에 남은 것 올리기로 올립니다. 다른 기기에서 먼저 저장된 경우에는 지금 고친 것으로 맞추고, 앞 것은 웹의 판 되돌리기에 남습니다."),
        Doum(id: "a12", t: "원칙 16개 점검", d: "코스 탭의 원칙 16개 점검에서 어디까지를 안 적은 지점, 현장영상해설사 위치가 없는 지점, 시계 방향으로 짚지 않은 지점, 시각 전제 표현 같은 고칠 곳을 다섯 줄씩 듣습니다. 번호는 작성원칙 통합본 16개의 번호입니다."),
        Doum(id: "a13", t: "현장영상해설 탭", d: "현장에서 지금 지점의 대본을 크게 보여 줍니다. 다음 지점 단추로 차례대로 넘기고, 연습할 때는 폰 목소리로 듣기를 씁니다."),
        Doum(id: "a14", t: "협회 승인 요청", d: "코스 탭의 협회 승인 요청 보내기를 누르면 지금 코스를 저장하고 협회에 승인을 요청합니다. 승인되면 코스 목록에 표시됩니다."),
        Doum(id: "a15", t: "새로고침과 업데이트", d: "설정 맨 위의 새로고침은 코스 목록과 지금 코스를 서버에서 다시 받습니다. 그 아래 업데이트는 새 판이 있는지 살핍니다."),
        Doum(id: "a16", t: "말소리", d: "설정의 앱 안내 말소리를 끄면 앱이 스스로 말하는 안내가 꺼지고, 안내는 화면 위 안내 줄에 남습니다."),
        Doum(id: "a17", t: "웹 투어스튜디오와 함께 쓰기", d: "앱과 웹은 같은 서버의 같은 코스를 씁니다. 대본집 내려받기, 워드 내려받기, 관리자 일은 웹 투어스튜디오에서 하십시오. 설정에 웹 투어스튜디오 열기가 있습니다.")
    ]
}

struct SeoljeongTab: View {
    @EnvironmentObject var store: TourStore
    @State private var malsori = UserDefaults.standard.object(forKey: "malsori") as? Bool ?? true
    @State private var q = ""
    @AccessibilityFocusState private var gyeolgwa: Bool
    var body: some View {
        List {
            Section {
                Button("새로고침") {
                    Task {
                        await store.loadCourses()
                        if let id = store.kos?.id { _ = await store.open(id) }
                        store.say("새로 받았습니다. 코스 \(store.courses.count)개입니다.")
                    }
                }
                TourOllimDanchu()
                Toggle("앱 안내 말소리", isOn: $malsori)
                    .onChange(of: malsori) { _, v in UserDefaults.standard.set(v, forKey: "malsori") }
                if store.kos != nil { Button("폰에 남은 것 올리기") { Task { await store.draftOllim() } } }
                AllimJul()
            }
            Section("도움말 찾기") {
                TextField("찾을 낱말 (예: 경계, 사진, 저장)", text: $q)
                    .onSubmit { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { gyeolgwa = true } }
                let L = DoumMok.items.filter { q.trimmingCharacters(in: .whitespaces).isEmpty || ($0.t + $0.d).contains(q.trimmingCharacters(in: .whitespaces)) }
                if L.isEmpty { Text("찾지 못했습니다. 다른 낱말로 찾아 보십시오.").accessibilityFocused($gyeolgwa) }
                Jang(items: L) { x in
                    NavigationLink(x.t) { DoumView(x: x).dwiro() }
                }
            }
            Section("이 앱") {
                Text(deureoon)
                Link("웹 투어스튜디오 열기", destination: URL(string: "https://lvd.ada.or.kr/lvdts/lvdts.html")!)
                NavigationLink("무엇이 바뀌었나") { BakkwinView().dwiro() }
                Text(Pan.ireum).font(.footnote)
                Button("나가기") { store.naga(mal: "나갔습니다.") }
            }
            Section { LogoView().frame(height: 80).frame(maxWidth: .infinity) }
        }
        .navigationTitle("설정")
    }
    private var deureoon: String {
        if store.meName.isEmpty { return "들어온 사람 없음" }
        return store.meName + " 님이 들어와 있습니다" + (store.isMaster ? " (최고관리자)" : "")
    }
}

struct DoumView: View {
    let x: Doum
    var body: some View {
        List {
            Text(x.t).font(.title3.weight(.bold)).accessibilityAddTraits(.isHeader)
            Text(x.d).fixedSize(horizontal: false, vertical: true)
        }
        .navigationTitle("도움말")
    }
}

struct BakkwinView: View {
    var body: some View {
        List {
            Section("1.0.0판 (빌드 261012-T1, 2026-10-12)") {
                Text("웹 투어스튜디오 1.12판을 아이폰 앱으로 처음 지었습니다(이사장님 승인).")
                Text("탭 다섯: 코스, 답사, 지점, 현장영상해설, 설정.")
                Text("지점은 어디서부터 어디까지로 정합니다. 오프닝 지점과 클로징 지점도 지점이고, 지점 사이의 이동은 다음 지점의 몫입니다.")
                Text("답사: 여기서부터 다음 지점 시작, 여기서부터 새 지점 시작, 시계 방향 네 쪽 사진.")
                Text("지점 한 화면, 방향 도우미, 원칙 16개 점검(작성원칙 통합본), 협회 승인 요청, 저절로 저장과 폰에 남겨 두기.")
                Text("앱 안의 말은 모두 현장영상해설과 현장영상해설사로 맞췄습니다.")
            }
        }
        .navigationTitle("무엇이 바뀌었나")
    }
}
