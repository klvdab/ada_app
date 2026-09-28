// 도움말 — 새 기능을 넣을 때마다 여기에 항목을 함께 넣습니다. 낱말로 찾을 수 있습니다.
import SwiftUI

struct Doumal: Identifiable {
    let id = UUID()
    let jemok: String
    let naeyong: String

    static let modu: [Doumal] = [
        Doumal(jemok: "이 판은 무엇입니까 — 1단계 기초판",
               naeyong: "길눈을 속까지 앱으로 다시 짓는 첫 판입니다. 웹을 띄우지 않고 아이폰 자체 코드로 돕니다. 이번 판에는 기능 없이 바탕만 세웠습니다. 앱의 틀, 소리 엔진, 위치 엔진, 여정 엔진, 통신과 저장, 기록입니다. 바탕이 잠긴 폰, 주머니 속, 통신 끊김, 음악, 보이스오버에서 모두 버티는지 기초 시험으로 확인한 뒤, 길 찾기부터 기능을 하나씩 답니다. 그동안 웹 길눈은 사파리에서 그대로 쓰실 수 있습니다."),
        Doumal(jemok: "기초 시험 하는 법",
               naeyong: "설정 탭에서 기초 시험을 누르고, 기초 시험 시작을 누르십시오. 30분 동안 1분마다 길눈이 지금 상태를 말씀드립니다. 그동안 화면을 잠그고, 주머니에 넣고, 음악을 틀고, 비행기 모드로 통신을 끊어 보십시오. 1분마다 말이 이어지면 바탕이 버티는 것입니다. 결과는 나스에 저절로 남아 클이 확인합니다. 중간에 그만두시려면 기초 시험 중 그만하기를 누르십시오."),
        Doumal(jemok: "위치를 항상 허락하는 까닭",
               naeyong: "폰이 잠기거나 주머니에 있어도 안내를 이어 가려면 위치를 항상 허락해 주셔야 합니다. 기초 시험 화면 맨 위에 위치를 항상 허락하기 단추가 보이면 눌러 주십시오. 창이 뜨면 항상 허용을 고르시면 됩니다. 위치가 거절되어 있으면 아이폰 설정 열기 단추가 나오며, 설정에서 길눈, 위치, 항상을 고르시면 됩니다."),
        Doumal(jemok: "말하기 설정 — 말소리 끄기, 빠르기, 목소리",
               naeyong: "설정 탭의 말하기 설정에서 길눈 말소리를 켜고 끌 수 있습니다. 화면 글자와 보이스오버가 읽는 것은 그대로이고, 길눈이 스스로 내는 말만 꺼집니다. 다만 경고는 안전을 위해 꺼도 늘 말씀드립니다. 빠르기는 누를 때마다 다섯 단 가운데 다음으로 넘어가며 바로 들려 드립니다. 목소리도 누를 때마다 다음 한국어 목소리로 넘어갑니다."),
        Doumal(jemok: "음악을 들으며 쓰기",
               naeyong: "음악이나 라디오를 틀어 두셔도 됩니다. 길눈이 말할 때만 음악 소리가 잠시 작아졌다가, 말이 끝나면 되돌아옵니다. 전화가 오면 길눈은 말을 멈추고, 전화가 끝나면 이어 말합니다."),
        Doumal(jemok: "뒤로 가기",
               naeyong: "속 화면에서는 화면 맨 위 왼쪽에 뒤로 단추가 있습니다. 보이스오버를 쓰실 때는 두 손가락으로 좌우로 문지르셔도 뒤로 갑니다. 화면 맨 아래 탭 바는 모든 화면에 늘 있습니다."),
        Doumal(jemok: "새로고침",
               naeyong: "설정 탭의 새로고침을 누르면 폰에 받아 둔 자료를 비우고, 다음에 쓰실 때 나스에서 새 자료를 받습니다. 내 설정과 지금 여정은 그대로 남습니다."),
        Doumal(jemok: "길눈 정보 — 판과 빌드, 고친 기록",
               naeyong: "설정 탭의 길눈 정보에서 지금 쓰시는 판번호와 빌드번호, 그리고 판마다 무엇을 고쳤는지 보실 수 있습니다. 무엇이 이상할 때 판번호를 알려 주시면 클이 바로 찾습니다."),
        Doumal(jemok: "통신이 끊겼을 때",
               naeyong: "통신이 끊겨도 위치와 말소리는 그대로 돕니다. 나스에서 받은 자료는 폰에 담아 두었다가 통신이 끊기면 그것을 씁니다. 기록도 폰에 쌓아 두었다가 통신이 이어지면 보냅니다."),
        Doumal(jemok: "저절로 저장",
               naeyong: "길눈은 1분마다, 그리고 앱이 뒤로 갈 때마다 여정과 기록을 폰에 저장합니다. 갑자기 전원이 꺼져도 다시 켜면 이어집니다. 열두 시간 넘게 손대지 않은 여정은 저절로 끝납니다.")
    ]
}

struct DoumalView: View {
    @State private var chatgi = ""
    @State private var gyeolgwa: [Doumal] = Doumal.modu
    @State private var beon = 0

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                TextField("도움말 찾기 — 낱말을 넣고 엔터", text: $chatgi)
                    .textFieldStyle(.roundedBorder)
                    .font(.title3)
                    .submitLabel(.search)
                    .onSubmit { chatgiHagi() }
                if gyeolgwa.isEmpty {
                    Text("찾으신 낱말이 든 도움말이 없습니다. 다른 낱말로 찾아 보십시오.")
                        .font(.title3)
                } else {
                    Mokrok5(gyeolgwa) { d in
                        NavigationLink {
                            ScrollView {
                                Text(d.naeyong)
                                    .font(.title3)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding()
                            }
                            .sokHwamyeon(d.jemok)
                        } label: {
                            Text(d.jemok)
                        }
                        .buttonStyle(KeunDanchu())
                    }
                    .id(beon)
                }
            }
            .padding()
        }
        .sokHwamyeon("도움말")
    }

    private func chatgiHagi() {
        let q = chatgi.trimmingCharacters(in: .whitespaces)
        if q.isEmpty {
            gyeolgwa = Doumal.modu
        } else {
            let natmal = q.split(separator: " ").map(String.init)
            gyeolgwa = Doumal.modu.filter { d in
                natmal.allSatisfy { d.jemok.contains($0) || d.naeyong.contains($0) }
            }
        }
        beon += 1
    }
}
