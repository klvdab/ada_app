// 자봉 교육 — 점지도 일곱 가지 약속과 확인 문제 아홉 개
// 2.10.0 (빌드 261007-I1, 2026-10-07 이사장님 승인) 요령 다섯 가지·문제 세 개를 바꿈.
// 약속은 서버 점검 일꾼(lvd-jeomgeom)의 잣대와 똑같이 맞춤 — 배운 대로 걸으면 점검을 통과하게.
// 교육 판(pan)을 올리면 이미 등록한 봉사자도 다음에 앱을 열 때 새 교육을 한 번 다시 듣습니다.
import SwiftUI

enum JbGyoyuk {
    /// 교육 판 — 1은 예전 요령 다섯 가지, 2는 일곱 가지 약속
    static let pan = 2

    struct Yaksok { let jemok: String; let jul: [String]; let geurim: String }

    static let meorimal = "여러분이 그리는 점지도는 시각장애인이 흰지팡이를 쥐고 그대로 따라 걷는 길입니다. 한 걸음, 표시 하나가 빠지면 그분은 길을 잃거나 다칠 수 있습니다. 그래서 길눈은 일곱 가지 약속을 모두 지킨 점지도만 씁니다."

    static let yaksok: [Yaksok] = [
        Yaksok(jemok: "약속 하나, 시작과 끝은 문 앞에서", jul: [
            "출발하는 건물의 문 앞에서, 문을 등지고 서서 걷기 시작을 누릅니다.",
            "도착하는 곳의 문 앞에서, 문을 마주하고 서서 다 걸었습니다를 누릅니다.",
            "문에서 몇 걸음 떨어진 곳에서 누르면, 시각장애인은 그만큼 문을 찾아 헤맵니다."
        ], geurim: "door.left.hand.open"),
        Yaksok(jemok: "약속 둘, 걸음을 끊지 않기", jul: [
            "걷는 동안 걸음이 고르게 이어져야 합니다.",
            "신호를 기다리거나 전화를 받느라 멈춰야 하면 잠깐 멈춤을 누르고, 다시 걸을 때 다시 걷기를 누릅니다.",
            "차에 타거나 남의 도움으로 옮겨진 구간은 그리지 않습니다.",
            "있었던 일: 걸음이 50걸음에서 185걸음으로 건너뛴 길이 있었습니다. 그 사이 135걸음을 시각장애인은 알 길이 없어 쓸 수 없었습니다."
        ], geurim: "figure.walk"),
        Yaksok(jemok: "약속 셋, 폰은 가슴 앞에, 걷는 쪽으로", jul: [
            "폰을 가슴 앞에 세워 들고, 폰 윗머리가 걷는 쪽을 향하게 합니다.",
            "주머니나 가방에 넣고 걷지 않습니다.",
            "그래야 걸음마다 방향이 바르게 남습니다."
        ], geurim: "iphone"),
        Yaksok(jemok: "약속 넷, 꺾이는 그 자리에서 바로 표시", jul: [
            "오른쪽이나 왼쪽으로 꺾는 바로 그 자리에서 꺾임을 누르거나 말로 남깁니다.",
            "지나고 나서 누르면, 시각장애인은 그만큼 지나쳐서 꺾게 됩니다.",
            "폰이 꺾임을 알아채고 여쭈면, 맞을 때 네라고 답합니다.",
            "있었던 일: 길이 크게 꺾였는데 표시가 없는 점지도가 여럿 있었습니다. 시각장애인은 그 자리에서 곧장 걸어가 버리게 됩니다."
        ], geurim: "arrow.turn.up.right"),
        Yaksok(jemok: "약속 다섯, 짝 표시는 시작과 끝을 함께", jul: [
            "계단, 건널목, 에스컬레이터는 시작과 끝을 반드시 둘 다 남깁니다.",
            "건널목은 차도에 첫발을 내딛는 자리에서 시작, 건너편 인도에 두 발이 올라선 자리에서 끝을 남깁니다.",
            "계단은 첫 칸 바로 앞에서 시작, 마지막 칸을 딛고 내려선 자리에서 끝을 남깁니다.",
            "있었던 일: 건널목 끝을 빠뜨린 점지도가 있었습니다. 시각장애인은 어디서 건너기가 끝나는지 알 수 없습니다."
        ], geurim: "stairs"),
        Yaksok(jemok: "약속 여섯, 보폭은 걷기 전에", jul: [
            "걷기 전에 보폭을 잽니다. 보폭이 있어야 걸음이 거리가 됩니다.",
            "신발이 바뀌거나 무거운 짐을 들어 걸음이 달라지면 다시 잽니다.",
            "있었던 일: 보폭이 0으로 남아 걸음을 거리로 바꿀 수 없는 점지도가 다섯 개나 있었습니다. 모두 쓸 수 없었습니다."
        ], geurim: "ruler"),
        Yaksok(jemok: "약속 일곱, 표시마다 짧게 말로 남기기", jul: [
            "표시를 누르면 그 자리부터 폰이 짧게 귀를 엽니다. 그 자리 모습을 한두 마디로 말해 주십시오. 예를 들면 「오른쪽에 화단 턱이 있고, 그 끝에서 오른쪽으로 꺾습니다」.",
            "말이 멈추면 저절로 끊기고, 길어도 10초에서 끊깁니다. 길게 이어 말하지 않습니다.",
            "이 토막은 그 걸음 자리에 묶여, 시각장애인이 그 자리에 닿기 몇 걸음 앞에서 여러분 목소리 그대로 들려 드립니다. 빨리 걸으셨든 천천히 걸으셨든 듣는 분의 걸음에 맞춰집니다.",
            "말한 방향과 표시가 같아야 합니다. 「왼쪽으로 꺾습니다」라고 말하고 오른쪽 꺾임을 누르면 점검에서 걸립니다.",
            "말로 표시를 찍으면 폰이 되묻습니다. 맞을 때만 네라고 답해 주십시오."
        ], geurim: "mic.fill")
    ]

    static let maejeummal = "다 걸으시면 앱이 일곱 가지를 바로 점검합니다. 하나라도 빠지면 몇 걸음째인지 알려 드리니, 그 구간만 다시 걸어 채워 주십시오. 일곱 가지가 모두 맞아야 길눈님께 갑니다."

    /// 확인 문제 — 넷 가운데 하나, 다 맞혀야 통과. 틀리면 풀이를 듣고 다시 고름
    static let munje: [(q: String, d: [String], a: Int, h: String)] = [
        ("걷기 시작은 어디서 누릅니까?",
         ["집을 나와 길에 들어선 뒤", "출발하는 건물의 문 앞", "처음 꺾는 곳에서", "아무 데서나"], 1,
         "출발하는 건물의 문 앞에서, 문을 등지고 서서 누릅니다. 문에서 떨어진 곳에서 누르면 시각장애인은 그만큼 문을 찾아 헤맵니다."),
        ("신호를 기다리느라 멈춰야 합니다. 어떻게 합니까?",
         ["그냥 서 있는다", "잠깐 멈춤을 누르고, 다시 걸을 때 다시 걷기를 누른다", "다 걸었습니다를 누른다", "앱을 끈다"], 1,
         "잠깐 멈춤을 눌러야 멈춘 동안이 걸음으로 잘못 남지 않고, 다시 걷기로 걸음이 끊기지 않고 이어집니다."),
        ("걷는 동안 폰은 어떻게 듭니까?",
         ["주머니에 넣는다", "가방에 넣는다", "가슴 앞에 세워 들고 윗머리가 걷는 쪽을 향하게 한다", "편한 대로 든다"], 2,
         "가슴 앞에 세워 들고 윗머리가 걷는 쪽을 향해야 걸음마다 방향이 바르게 남습니다."),
        ("꺾임 표시는 언제 남깁니까?",
         ["꺾기 다섯 걸음 전에", "꺾는 바로 그 자리에서", "꺾고 열 걸음 지나서", "다 걸은 뒤 한꺼번에"], 1,
         "꺾는 바로 그 자리에서 남겨야 시각장애인이 지나치지 않고 정확한 자리에서 꺾습니다."),
        ("건널목 표시는 어떻게 남깁니까?",
         ["시작만 남긴다", "끝만 남긴다", "첫발을 내딛는 자리에서 시작, 건너편 인도에 올라선 자리에서 끝", "남기지 않는다"], 2,
         "시작과 끝이 짝을 이루어야 어디서 건너기 시작하고 어디서 끝나는지 알려 드릴 수 있습니다."),
        ("계단을 다 내려왔습니다. 무엇을 남깁니까?",
         ["아무것도 남기지 않는다", "계단 끝", "계단 시작을 한 번 더", "다 걸었습니다"], 1,
         "마지막 칸을 딛고 내려선 자리에서 계단 끝을 남겨야 계단 시작과 짝이 맞습니다."),
        ("보폭은 언제 잽니까?",
         ["걷기 전에 재고, 신발이나 걸음이 바뀌면 다시 잰다", "다 걸은 뒤에", "재지 않아도 된다", "일 년에 한 번"], 0,
         "보폭이 있어야 걸음이 거리가 됩니다. 걸음이 달라지면 다시 재야 맞습니다."),
        ("다 걸은 뒤 점검에서 「건널목 끝이 빠졌습니다」가 나왔습니다. 어떻게 합니까?",
         ["그대로 올린다", "그 건널목 구간을 다시 걸어 채운다", "길을 통째로 지운다", "말로 설명만 남긴다"], 1,
         "흠이 있는 점지도는 길눈님께 가지 않습니다. 알려 드린 그 구간을 다시 걸어 채워 주십시오."),
        ("꺾임 표시를 누른 뒤에는 무엇을 합니까?",
         ["그 자리 모습을 한두 마디로 짧게 말한다", "아무 말도 하지 않는다", "다음 꺾임까지 쉬지 않고 길게 이야기한다", "집에 와서 한꺼번에 녹음한다"], 0,
         "표시를 누르면 그 자리부터 짧게 녹음되고, 말이 멈추면 끊깁니다. 그 토막이 그 걸음 자리에 묶여 길눈님이 닿을 때 들려 드립니다.")
    ]

    static let beonho = ["가", "나", "다", "라"]

    /// 소리로 읽을 차례 — 머리말, 약속 일곱, 맺음말
    static var sorijul: [String] {
        var s = [meorimal]
        for y in yaksok { s.append(y.jemok + ". " + y.jul.joined(separator: " ")) }
        s.append(maejeummal)
        return s
    }
}

/// 약속 한 장 — 누르면 그 약속만 다시 읽음
struct YaksokKadeu: View {
    let y: JbGyoyuk.Yaksok
    var body: some View {
        Button {
            SoriEngine.shared.modu_geodugi()
            SoriEngine.shared.mal(y.jemok + ". " + y.jul.joined(separator: " "))
        } label: {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: y.geurim)
                    .font(.system(size: 30, weight: .bold)).foregroundColor(Saek.nam)
                    .frame(width: 44).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 8) {
                    Text(y.jemok).font(.title2.bold()).foregroundColor(Saek.nam)
                    ForEach(Array(y.jul.enumerated()), id: \.offset) { _, j in
                        Text(j).font(.title3)
                            .foregroundColor(j.hasPrefix("있었던 일") ? Color(red: 0.7, green: 0.1, blue: 0.1) : .primary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 16).fill(Saek.norang.opacity(0.18)))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Saek.nam, lineWidth: 2))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(y.jemok + ". " + y.jul.joined(separator: " "))
        .accessibilityHint("누르시면 이 약속을 소리로 다시 읽습니다")
    }
}

/// 알림·설정 탭에서 언제든 약속 다시 보기
struct YaksokBogiView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Button("일곱 가지 약속 모두 듣기") {
                    SoriEngine.shared.modu_geodugi()
                    for t in JbGyoyuk.sorijul { SoriEngine.shared.mal(t) }
                }.buttonStyle(KeunDanchu())
                Text(JbGyoyuk.meorimal).font(.title3).fixedSize(horizontal: false, vertical: true)
                ForEach(Array(JbGyoyuk.yaksok.enumerated()), id: \.offset) { _, y in YaksokKadeu(y: y) }
                Text(JbGyoyuk.maejeummal).font(.title3).fixedSize(horizontal: false, vertical: true)
            }
            .padding()
        }
        .navigationTitle("점지도 일곱 가지 약속")
        .navigationBarTitleDisplayMode(.inline)
    }
}
