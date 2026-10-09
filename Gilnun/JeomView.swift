// 점지도 화면 — 앱 2.10.0 (빌드 260928-12)
//   길 찾기 탭 맨 위: 점지도로 걸을까요 여쭘, 따라 걷는 동안의 단추(지금 어디, 다음에 무엇, 여기 문제 있어요, 도움 청하기, 그만 걷기)
//   길 찾기 그 밖에: 가까운 점지도(다섯씩)
//   설정: 나만의 점지도, 보폭 재기, 걸음 오차 재기 / 기기 설정: 리모컨 배우기
import SwiftUI
import UIKit
import CoreMotion
import Combine

// MARK: 길 찾기 탭 맨 위 — 점지도로 걸을까요

struct JeomMuleumPan: View {
    let m: JeomMuleum
    var chojeom: AccessibilityFocusState<Bool>.Binding
    @ObservedObject private var jeom = JeomEngine.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button("점지도로 걷기 — 권합니다. \(m.julMal)") {
                jeom.muleumDap(jeom: true)
            }
            .buttonStyle(KeunDanchu())
            .accessibilityFocused(chojeom)
            Button("위성으로 걷기 — \(m.mok.ireum)까지 방향 따라") { jeom.muleumDap(jeom: false) }
                .buttonStyle(KeunDanchu())
        }
    }
}

// MARK: 길 찾기 탭 맨 위 — 따라 걷는 동안

struct TtaraPan: View {
    let g: JeomGil
    var chojeom: AccessibilityFocusState<Bool>.Binding
    @ObservedObject private var jeom = JeomEngine.shared
    @ObservedObject private var s = Seoljeong.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if jeom.dochakHam {
                Button("목적지에 닿았습니다 — 되돌아가기, 이 길을 거꾸로 걷기") { jeom.doedoragagi() }
                    .buttonStyle(KeunDanchu())
                    .accessibilityFocused(chojeom)
                Button("따라 걷기 마치기 — 여정 끝내기") { jeom.geuman() }
                    .buttonStyle(KeunDanchu())
            } else {
                Button("\(jeom.dwit ? "되돌아가는 중" : "점지도 따라 걷는 중") — 지금 어디입니까") { jeom.jigeumEodi() }
                    .buttonStyle(KeunDanchu())
                    .accessibilityFocused(chojeom)
                if jeom.munOn && jeom.munSu > 1 {
                    Button("다른 문으로 — 문이 \(jeom.munSu)곳 있습니다") { jeom.dareunMun() }
                        .buttonStyle(KeunDanchu())
                }
                Button("다음에 무엇이 있습니까") { jeom.daeumMuot() }
                    .buttonStyle(KeunDanchu())
                NavigationLink(value: GilHwamyeon.munje) { Text("여기 문제 있어요 — 점자블록 없어짐, 공사 등 알리기") }
                    .buttonStyle(KeunDanchu())
                NavigationLink(value: GilHwamyeon.gingeup) { Text("도움 청하기 — 긴급통화서비스") }
                    .buttonStyle(KeunDanchu())
                DisclosureGroup("따라 걷기 다른 할 일 펼치기") {
                    VStack(alignment: .leading, spacing: 10) {
                        Button("여기 걸렸어요 — 다음 분께 알려 주기") { jeom.geollimNamgigi() }
                            .buttonStyle(KeunDanchu())
                        // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 점지도의 탈것 구간에서 내리셨을 때
                        Button("탈것에서 내렸습니다 — 여기서부터 다시 걸음 안내") {
                            if !jeom.naeryeotda() { SoriEngine.shared.mal("지금은 탈것 구간을 지나는 중이 아닙니다. 걸음 안내를 이어 갑니다.") }
                        }
                        .buttonStyle(KeunDanchu())
                        Button("지금 내 자리 듣기") { AnnaeEngine.shared.jigeumJari() }
                            .buttonStyle(KeunDanchu())
                        Button("이 길목은 어떻게 생겼습니까") { jeom.gilmok() }
                            .buttonStyle(KeunDanchu())
                        Button("가까운 버스 정류장") { jeom.beoseuJeongryujang() }
                            .buttonStyle(KeunDanchu())
                        Button(jeom.hamkkeBunho.map { "함께 시험 끝내기 — 번호 " + $0.map { String($0) }.joined(separator: " ") } ?? "함께 시험 번호 받기 — 곁의 자봉과 함께 시험") {
                            jeom.hamkkeNureum()
                        }
                        .buttonStyle(KeunDanchu())
                        Button("그만 걷기 — 따라 걷기와 여정을 마칩니다") { jeom.geuman() }
                            .buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
            }
            if !s.rimo.isEmpty {
                RimoBatgi { k in
                    guard let jari = s.rimo.first(where: { $0.value == k })?.key else { return }
                    switch jari {
                    case "1": jeom.jigeumEodi()
                    case "2": jeom.daeumMuot()
                    default: SoriEngine.shared.dasiDeutgi()
                    }
                }
                .frame(width: 1, height: 1)
            }
        }
    }
}

/// 가까운 점지도 — 다섯씩, 누르면 따라 걷기, 보이스오버 동작으로 되돌아가기
struct GakkaunJeomView: View {
    @State private var mok: [JeomMok]?
    @State private var ieum: [JeomIeum]?
    @State private var mot = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let m = mok {
                    if m.isEmpty {
                        Text("올라온 점지도가 없습니다.").font(.title3)
                    } else {
                        Mokrok5(m) { j in
                            Button(j.julMal) { JeomEngine.shared.bulleoGeotgi(j.id, dwit: false) }
                                .buttonStyle(KeunDanchu())
                                .accessibilityAction(named: "되돌아가기로 걷기 — \(j.to)에서 \(j.from)까지") {
                                    JeomEngine.shared.bulleoGeotgi(j.id, dwit: true)
                                }
                                .contextMenu {
                                    Button("되돌아가기로 걷기") { JeomEngine.shared.bulleoGeotgi(j.id, dwit: true) }
                                }
                        }
                    }
                } else if mot {
                    Button("목록을 받지 못했습니다. 통신이나 위치를 확인하신 뒤 — 다시 받기") { batgi() }
                        .buttonStyle(KeunDanchu())
                } else {
                    Text("가까운 점지도를 찾는 중입니다.").font(.title3)
                }
                Text("누르시면 그 길을 따라 걷습니다. 거꾸로 걸으시려면 보이스오버로 위아래로 쓸어 되돌아가기로 걷기를 고르십시오.")
                    .font(.body)
                // 2.11.0 여러 점지도를 이어 걷기 — 한 구간을 마치면 저절로 다음 구간
                DisclosureGroup("이어서 갈 수 있는 곳 펼치기 — 점지도 여러 개를 이어 걷기") {
                    VStack(alignment: .leading, spacing: 10) {
                        if let ie = ieum {
                            if ie.isEmpty {
                                Text("이어 둔 길이 없습니다.").font(.title3)
                            } else {
                                Mokrok5(ie) { x in
                                    Button(x.julMal) { JeomEngine.shared.ieumGeotgi(x.gugan, mok: nil) }
                                        .buttonStyle(KeunDanchu())
                                }
                            }
                        } else {
                            Text("이어진 길을 찾는 중입니다.").font(.title3)
                        }
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("가까운 점지도")
        .task {
            if mok == nil { batgi() }
            if ieum == nil { let r = await Jeomjido.ieumMok(); await MainActor.run { ieum = r ?? [] } }
        }
    }

    private func batgi() {
        mot = false
        guard let w = WichiEngine.shared.jigeum else {
            WichiEngine.shared.sijak()
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                if WichiEngine.shared.jigeum != nil { batgi() } else { mot = true; SoriEngine.shared.mal("아직 위치를 잡는 중입니다.") }
            }
            return
        }
        Task {
            let r = await Jeomjido.gakkaun(w.lat, w.lon)
            await MainActor.run {
                if let r = r { mok = r.sorted { ($0.near < 0 ? 1e9 : $0.near) < ($1.near < 0 ? 1e9 : $1.near) } } else { mot = true }
            }
        }
    }
}

/// 여기 문제 있어요 — 여섯 가지 가운데 고르고, 한마디는 적어도 되고 안 적어도 됨
struct MunjeView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var hanmadi = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                TextField("한마디 더 — 적지 않으셔도 됩니다", text: $hanmadi)
                    .textFieldStyle(.roundedBorder)
                    .font(.title3)
                ForEach(Jeomjido.MUNJE, id: \.self) { k in
                    Button(k) {
                        let m = hanmadi.trimmingCharacters(in: .whitespaces)
                        let w = WichiEngine.shared.jigeum
                        SoriEngine.shared.mal("적는 중입니다.", .jeongbo)
                        Task {
                            let r = await Jeomjido.munje(k, m, w)
                            await MainActor.run { SoriEngine.shared.mal(r); dismiss() }
                        }
                    }
                    .buttonStyle(KeunDanchu())
                }
            }
            .padding()
        }
        .sokHwamyeon("여기 문제 있어요")
    }
}

// MARK: 설정 — 나만의 점지도, 보폭 재기, 걸음 오차 재기

struct JeomSeoljeongView: View {
    @ObservedObject private var s = Seoljeong.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                NavigationLink { NaeGilView() } label: { Text("나만의 점지도 — 늘 다니는 길을 걸어서 그려 두기") }
                    .buttonStyle(KeunDanchu())
                NavigationLink { BopokView() } label: {
                    Text("보폭 재기 — 지금 \(Seoljeong.bopokModeIreum(s.bopokMode)) " + (s.bopokJaem ? String(format: "%.2f미터", s.bopok) : "아직 재지 않음"))
                }
                .buttonStyle(KeunDanchu())
                NavigationLink { JaegiView() } label: { Text("걸음 오차 재기 — 걸음으로 잰 거리와 실제 거리 견주기") }
                    .buttonStyle(KeunDanchu())
                NavigationLink { NaeMunView() } label: { Text("내 문 — 지금 선 자리를 내 문으로 담아 두기") }
                    .buttonStyle(KeunDanchu())
            }
            .padding()
        }
        .sokHwamyeon("점지도와 걸음")
    }
}

/// 나만의 점지도를 그리는 일 — 화면을 떠나도 그리기는 이어짐
final class NaeGeurigi: ObservableObject {
    static let shared = NaeGeurigi()
    @Published private(set) var geurineun = false
    @Published private(set) var damgilGeot = false
    private(set) var pts: [JeomJeom] = []
    private(set) var marks: [JeomPyo] = []
    private var t0 = Date()
    private var majimak = Date.distantPast
    private var ssak: AnyCancellable?

    var gilLen: Double {
        var d = 0.0
        if pts.count > 1 { for i in 1..<pts.count { d += WichiEngine.geori(pts[i - 1].lat, pts[i - 1].lon, pts[i].lat, pts[i].lon) } }
        return d
    }

    func sijak() {
        pts = []; marks = []; t0 = Date(); majimak = .distantPast
        WichiEngine.shared.sijak()
        ssak = WichiEngine.shared.saeWichi.receive(on: DispatchQueue.main).sink { [weak self] w in
            guard let self = self, !w.georeumChu else { return }
            let now = Date()
            if now.timeIntervalSince(self.majimak) < 0.9 { return }
            self.majimak = now
            self.pts.append(JeomJeom(lat: w.lat, lon: w.lon, acc: (w.ochae * 10).rounded() / 10, h: w.banghyang >= 0 ? w.banghyang : 0,
                                     t: now.timeIntervalSince(self.t0).rounded()))
        }
        geurineun = true
        damgilGeot = false
        SoriEngine.shared.mal("그리기 시작했습니다. 평소대로 걸으십시오.")
    }

    func kkeut() {
        ssak = nil
        geurineun = false
        damgilGeot = pts.count >= 3
        SoriEngine.shared.mal(pts.count < 3 ? "점이 너무 적어 담을 수 없습니다. 다시 걸어 주십시오."
                                             : "걷기를 마쳤습니다. \(Int(gilLen.rounded()))미터, 표시 \(marks.count)개입니다. 이 길을 어떻게 할까요. 이름을 적고 나만 쓰기나 모두가 쓰도록 점지도에 올리기를 고르십시오.")
    }

    func sangtae() {
        guard let p = pts.last else { SoriEngine.shared.mal("아직 그리지 않았습니다."); return }
        SoriEngine.shared.mal("지금까지 \(Int(gilLen.rounded()))미터, 점 \(pts.count)개, 표시 \(marks.count)개입니다. 위치 오차는 \(Int((p.acc ?? 0).rounded()))미터입니다.")
    }

    func pyo(_ nm: String, _ mal: String = "") {
        guard let p = pts.last else { SoriEngine.shared.mal("지금 자리를 잡는 중입니다. 잠시만 기다려 주십시오."); return }
        marks.append(JeomPyo(lat: p.lat, lon: p.lon, name: nm, kind: nil, cnt: nil, mal: mal, t: p.t, acc: p.acc, dist: nil))
        SoriEngine.shared.mal(nm + (mal.isEmpty ? "" : ", " + mal) + " 남겼습니다. 모두 \(marks.count)개입니다.")
    }

    func gilMandeulgi(_ ireum: String) -> JeomGil {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "yyyy-MM-dd HH:mm"
        let id = "nae_" + { let x = DateFormatter(); x.dateFormat = "yyyyMMddHHmmss"; return x.string(from: Date()) }()
        let nm = ireum.isEmpty ? "내 길 " + f.string(from: Date()) : ireum
        return JeomGil(id: id, title: nm, from: nm, to: "", who: "본인", made: f.string(from: Date()), dist: gilLen.rounded(),
                       pts: pts, marks: marks, nae: true)
    }

    func biugi() { damgilGeot = false }
}

struct NaeGilView: View {
    @ObservedObject private var gr = NaeGeurigi.shared
    @ObservedObject private var nae = NaeGil.shared
    @State private var ireum = ""
    @State private var hanmadi = ""
    @State private var jamgeum = ""
    @State private var allim = ""
    @AccessibilityFocusState private var allimChojeom: Bool

    private let PYO = [("오름턱", "오름턱 — 올라서는 턱"), ("내림턱", "내림턱 — 내려서는 턱"), ("계단", "계단"),
                       ("문·입구", "문·입구"), ("조심할 곳", "조심할 곳")]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !allim.isEmpty { Text(allim).font(.title3).accessibilityFocused($allimChojeom) }
                if gr.geurineun {
                    Button("걷기 끝 — 그리기 마치기") { gr.kkeut() }.buttonStyle(KeunDanchu())
                    Button("지금 상태 알려 주기") { gr.sangtae() }.buttonStyle(KeunDanchu())
                    Text("그 자리에 닿았을 때 누르십시오.").font(.body)
                    ForEach(0..<PYO.count, id: \.self) { i in
                        Button(PYO[i].1) { gr.pyo(PYO[i].0) }.buttonStyle(KeunDanchu())
                    }
                    TextField("여기 남길 한마디 — 받아쓰기로 말씀하셔도 됩니다", text: $hanmadi)
                        .textFieldStyle(.roundedBorder).font(.title3)
                    Button("여기 한마디 남기기") {
                        let m = hanmadi.trimmingCharacters(in: .whitespaces)
                        guard !m.isEmpty else { SoriEngine.shared.mal("남길 말을 먼저 적어 주십시오."); return }
                        gr.pyo("한마디", m)
                        hanmadi = ""
                    }
                    .buttonStyle(KeunDanchu())
                } else if gr.damgilGeot {
                    TextField("이 길 이름 — 예: 우리 집에서 마을버스까지", text: $ireum)
                        .textFieldStyle(.roundedBorder).font(.title3)
                    Button("나만 쓰기 — 이 기기에만 담기") {
                        let g = gr.gilMandeulgi(ireum.trimmingCharacters(in: .whitespaces))
                        nae.damgi(g)
                        gr.biugi()
                        alrigi("담았습니다. \(g.title), \(Int(g.dist))미터입니다. 이 기기 안에 있습니다.")
                        ireum = ""
                    }
                    .buttonStyle(KeunDanchu())
                    Button("모두가 쓰도록 점지도에 올리기") {
                        let nm = ireum.trimmingCharacters(in: .whitespaces)
                        guard !nm.isEmpty else { alrigi("이 길 이름을 먼저 적어 주십시오."); return }
                        guard gr.pts.count >= 5 else { alrigi("점이 적어 올릴 수 없습니다. 나만 쓰기로 담아 주십시오."); return }
                        let g = gr.gilMandeulgi(nm)
                        alrigi("협회 점지도에 보내는 중입니다.")
                        Task {
                            let (ok, m) = await Jeomjido.olligi(g)
                            await MainActor.run {
                                if ok {
                                    var x = g
                                    x.ollim = true
                                    nae.damgi(x)
                                    gr.biugi()
                                    ireum = ""
                                    alrigi("보탰습니다. \(nm)에서 \(m)까지 \(Int(g.dist))미터입니다. 이제 따라 걷기에서 이 길이 나오고, 되돌아가기도 함께 나옵니다.")
                                } else {
                                    alrigi(m)
                                }
                            }
                        }
                    }
                    .buttonStyle(KeunDanchu())
                    Button("담지 않고 버리기") { gr.biugi(); alrigi("그린 길을 버렸습니다.") }
                        .buttonStyle(KeunDanchu())
                } else {
                    Button("걷기 시작 — 그리기 시작") { gr.sijak() }.buttonStyle(KeunDanchu())
                    Text("우리 집 앞이나 늘 다니는 길을 손수 그려 두는 자리입니다. 다 걸으신 뒤에 나만 쓸지, 모두가 쓰도록 점지도에 올릴지 고르실 수 있습니다. 올리시면 길과 표시와 길 이름만 올라가고 전화번호 같은 것은 가지 않습니다.")
                        .font(.body)
                }
                Text("내가 그린 길 — \(nae.mokrok.count)개").font(.title3.weight(.semibold))
                if nae.mokrok.isEmpty {
                    Text("아직 그려 두신 길이 없습니다.").font(.body)
                } else {
                    Mokrok5(nae.mokrok) { g in
                        NavigationLink { NaeGilSangseView(id: g.id) } label: {
                            Text("\(g.title) · \(Int(g.dist))미터 · 표시 \(g.marks.count)개 · \(g.made)" + (g.matgim ? " · 맡겨 둠" : " · 이 기기에만") + (g.ollim ? " · 모두와 나눔" : ""))
                        }
                        .buttonStyle(KeunDanchu())
                    }
                }
                DisclosureGroup("맡겨 둔 길 찾아오기 펼치기 — 잠금말") {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("맡기기는 잠금말을 걸어 협회 서버에 두는 것입니다. 잠금말은 서버로 가지 않으며, 잊으시면 맡긴 길을 다시 풀 수 없습니다. 폰을 바꾸셨을 때 같은 잠금말로 찾아오십시오.")
                            .font(.body)
                        SecureField(nae.jamgeum.isEmpty ? "잠금말" : "잠금말 — 정해 두셨습니다. 바꾸시려면 새로 적으십시오", text: $jamgeum)
                            .textFieldStyle(.roundedBorder).font(.title3)
                        Button("이 잠금말로 정하기") {
                            let j = jamgeum.trimmingCharacters(in: .whitespaces)
                            guard !j.isEmpty else { alrigi("잠금말을 먼저 적어 주십시오."); return }
                            nae.jamgeum = j
                            jamgeum = ""
                            alrigi("잠금말을 정했습니다. 이 폰의 열쇠 칸에만 담깁니다.")
                        }
                        .buttonStyle(KeunDanchu())
                        Button("맡겨 둔 길 찾아오기") {
                            let j = jamgeum.trimmingCharacters(in: .whitespaces).isEmpty ? nae.jamgeum : jamgeum.trimmingCharacters(in: .whitespaces)
                            guard !j.isEmpty else { alrigi("잠금말을 먼저 적어 주십시오."); return }
                            alrigi("찾아오는 중입니다.")
                            Task {
                                let r = await nae.chajaogi(j)
                                await MainActor.run {
                                    guard let r = r else { alrigi("찾아오지 못했습니다. 통신을 확인해 주십시오."); return }
                                    alrigi("맡겨 둔 길 \(r.0)개를 찾아왔습니다." + (r.1 > 0 ? " \(r.1)개는 잠금말이 달라 풀지 못했습니다." : ""))
                                }
                            }
                        }
                        .buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("나만의 점지도")
    }

    private func alrigi(_ t: String) {
        allim = t
        SoriEngine.shared.mal(t)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { allimChojeom = true }
    }
}

struct NaeGilSangseView: View {
    let id: String
    @ObservedObject private var nae = NaeGil.shared
    @Environment(\.dismiss) private var dismiss
    @State private var jiulGeot = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let g = nae.chatgi(id) {
                    Button("이 길 따라 걷기 — \(g.title)") { JeomEngine.shared.sijak(g, dwit: false) }
                        .buttonStyle(KeunDanchu())
                    Button("되돌아가기로 걷기 — 끝에서 처음으로") { JeomEngine.shared.sijak(g, dwit: true) }
                        .buttonStyle(KeunDanchu())
                    if g.ollim {
                        Text("이미 모두와 나눈 길입니다.").font(.title3)
                    } else {
                        Button("모두가 쓰도록 점지도에 올리기") {
                            guard g.pts.count >= 5 else { SoriEngine.shared.mal("이 길은 자리가 적어 올릴 수 없습니다."); return }
                            SoriEngine.shared.mal("올리는 중입니다.")
                            Task {
                                let (ok, m) = await Jeomjido.olligi(g)
                                await MainActor.run {
                                    if ok {
                                        var x = g
                                        x.ollim = true
                                        nae.damgi(x)
                                        SoriEngine.shared.mal("\(g.title)\(MalHagi.eul(g.title)) 모두가 쓰도록 올렸습니다. 이제 다른 분들도 이 길을 따라 걸을 수 있습니다.")
                                    } else {
                                        SoriEngine.shared.mal(m)
                                    }
                                }
                            }
                        }
                        .buttonStyle(KeunDanchu())
                    }
                    Button(g.matgim ? "다시 맡기기 — 잠금말을 걸어 서버에" : "맡기기 — 잠금말을 걸어 서버에") {
                        let lock = nae.jamgeum
                        guard !lock.isEmpty else { SoriEngine.shared.mal("잠금말이 없어 맡기지 못했습니다. 나만의 점지도 화면의 맡겨 둔 길 찾아오기 펼치기에서 잠금말을 먼저 정해 주십시오."); return }
                        SoriEngine.shared.mal("맡기는 중입니다.")
                        Task {
                            let ok = await nae.matgigi(g, lock)
                            await MainActor.run { SoriEngine.shared.mal(ok ? "\(g.title) 맡겼습니다." : "맡기지 못했습니다. 통신을 확인해 주십시오.") }
                        }
                    }
                    .buttonStyle(KeunDanchu())
                    Button("이 길 지우기") { jiulGeot = true }
                        .buttonStyle(KeunDanchu())
                } else {
                    Text("길을 찾지 못했습니다.").font(.title3)
                }
            }
            .padding()
        }
        .sokHwamyeon("내가 그린 길")
        .confirmationDialog("이 길을 지울까요? 맡겨 둔 것도 함께 지웁니다.", isPresented: $jiulGeot, titleVisibility: .visible) {
            Button("지우기", role: .destructive) {
                nae.matgimJiugi(id)
                SoriEngine.shared.mal("지웠습니다.")
                dismiss()
            }
            Button("그만두기", role: .cancel) {}
        }
    }
}

// MARK: 보폭 재기

final class GeoreumSem: ObservableObject {
    private let pedo = CMPedometer()
    @Published private(set) var doneun = false
    private(set) var t0 = Date()

    func sijak() { t0 = Date(); doneun = true }

    /// 시작부터 지금까지 걸음 수(폰 걸음 센서) — 못 세면 nil
    func kkeut(_ f: @escaping (Int?) -> Void) {
        doneun = false
        guard CMPedometer.isStepCountingAvailable() else { f(nil); return }
        pedo.queryPedometerData(from: t0, to: Date()) { d, _ in
            DispatchQueue.main.async { f(d?.numberOfSteps.intValue) }
        }
    }
}

struct BopokView: View {
    @ObservedObject private var s = Seoljeong.shared
    @StateObject private var sem = GeoreumSem()
    @State private var geori = "20"
    @State private var allim = ""
    @AccessibilityFocusState private var allimChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !allim.isEmpty { Text(allim).font(.title3).accessibilityFocused($allimChojeom) }
                Text(s.bopokJaem ? "\(Seoljeong.bopokModeIreum(s.bopokMode)) 보폭은 " + String(format: "%.2f", s.bopok) + "미터입니다. 백 걸음이면 \(Int((s.bopok * 100).rounded()))미터입니다."
                                 : "\(Seoljeong.bopokModeIreum(s.bopokMode)) 보폭은 아직 재지 않았습니다. 지금은 " + String(format: "%.2f", s.bopok) + "미터로 셉니다.")
                    .font(.title3)
                Button("재는 쪽 — 지금 \(Seoljeong.bopokModeIreum(s.bopokMode)) (누르면 바뀝니다)") {
                    s.bopokModeBakkugi(s.bopokMode == "honja" ? "dongban" : "honja")
                    alrigi("\(Seoljeong.bopokModeIreum(s.bopokMode)) 보폭으로 바꾸었습니다.")
                }
                .buttonStyle(KeunDanchu())
                if sem.doneun {
                    Button("다 걸었습니다") { majim() }.buttonStyle(KeunDanchu())
                } else {
                    TextField("잴 거리 (미터) — 스무 걸음 넘게 걸을 만한 거리", text: $geori)
                        .keyboardType(.decimalPad)
                        .textFieldStyle(.roundedBorder).font(.title3)
                    Button("보폭 재기 시작") {
                        guard let m = Double(geori.trimmingCharacters(in: .whitespaces)), m > 2 else {
                            alrigi("잴 거리를 미터로 적어 주십시오. 스무 걸음 넘게 걸을 만한 거리가 좋습니다."); return
                        }
                        sem.sijak()
                        alrigi("\(Int(m))미터를 평소대로 걸으신 뒤 다 걸었습니다를 눌러 주십시오. 지금부터 셉니다.")
                    }
                    .buttonStyle(KeunDanchu())
                }
                Text("한 번만 재 두면 이 기기가 기억합니다. 혼자 걸을 때와 동반자와 걸을 때를 따로 재 둘 수 있습니다. 점지도를 따라 걸을 때 걸음 수를 이 보폭으로 알려 드립니다.")
                    .font(.body)
            }
            .padding()
        }
        .sokHwamyeon("보폭 재기")
    }

    private func majim() {
        let m = Double(geori.trimmingCharacters(in: .whitespaces)) ?? 0
        sem.kkeut { n0 in
            guard let n = n0 else { alrigi("이 폰에서 걸음을 셀 수 없습니다. 설정 앱의 개인정보 보호에서 동작 및 피트니스를 허용해 주십시오."); return }
            if n < 5 { alrigi("걸음이 \(n)밖에 잡히지 않았습니다. 휴대전화를 손에 들거나 주머니에 넣고 다시 해 주십시오."); return }
            let b = m / Double(n)
            if b < 0.2 || b > 1.5 { alrigi("보폭이 " + String(format: "%.2f", b) + "미터로 나와 이상합니다. 거리를 다시 확인하고 한 번 더 해 주십시오."); return }
            if s.bopokMode == "dongban" { s.bopokDongban = b } else { s.bopokHonja = b }
            s.bopok = b
            Jeomjido.bopokNamgigi(m, n, b, s.bopokMode)
            var deo = ""
            if b < 0.35 { deo = " 다만 보폭이 짧게 나왔습니다. 걸으신 거리가 적어 두신 것보다 짧았거나, 걸음이 두 번씩 세어졌을 수 있습니다. 한 번 더 해 보시면 좋겠습니다." }
            else if b > 0.95 { deo = " 다만 보폭이 길게 나왔습니다. 걸으신 거리가 적어 두신 것보다 길었을 수 있습니다." }
            alrigi("\(Int(m))미터를 \(n)걸음에 걸으셨습니다. 보폭은 " + String(format: "%.2f", b) + "미터입니다. 이 기기가 기억합니다." + deo)
        }
    }

    private func alrigi(_ t: String) {
        allim = t
        SoriEngine.shared.mal(t)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { allimChojeom = true }
    }
}

// MARK: 걸음 오차 재기

struct JaegiView: View {
    @StateObject private var sem = GeoreumSem()
    @State private var sil = "100"
    @State private var bopokCm = String(Int((Seoljeong.shared.bopok * 100).rounded()))
    @State private var sokdo = "보통"
    @State private var jipangi = "씀"
    @State private var wiGil = 0.0
    @State private var wiJeon: (Double, Double)?
    @State private var ssak: AnyCancellable?
    @State private var gyeolgwa: [String] = []
    @State private var mal: [(String, String)] = []
    @State private var mok: [Jeomjido.JaegiJul]?
    @State private var allim = ""
    @AccessibilityFocusState private var allimChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !allim.isEmpty { Text(allim).font(.title3).accessibilityFocused($allimChojeom) }
                if sem.doneun {
                    Button("다 걸었습니다") { majim() }.buttonStyle(KeunDanchu())
                } else if !gyeolgwa.isEmpty {
                    ForEach(gyeolgwa, id: \.self) { Text($0).font(.title3) }
                    Button("이 결과 담기") {
                        let m = mal
                        alrigi("담는 중입니다.")
                        Task {
                            let r = await Jeomjido.jaegiDamgi(m)
                            await MainActor.run { alrigi(r); mokrokBatgi() }
                        }
                    }
                    .buttonStyle(KeunDanchu())
                    Button("한 번 더 재기") { gyeolgwa = []; alrigi("다시 잽니다. 준비되시면 재기 시작을 누르십시오.") }
                        .buttonStyle(KeunDanchu())
                } else {
                    TextField("미리 재 둔 실제 거리 (미터)", text: $sil)
                        .keyboardType(.decimalPad).textFieldStyle(.roundedBorder).font(.title3)
                    TextField("내 보폭 (센티미터)", text: $bopokCm)
                        .keyboardType(.decimalPad).textFieldStyle(.roundedBorder).font(.title3)
                    Button("걷는 빠르기 — \(sokdo) (누르면 바뀝니다)") {
                        sokdo = sokdo == "보통" ? "천천히" : (sokdo == "천천히" ? "빠르게" : "보통")
                        SoriEngine.shared.mal(sokdo)
                    }
                    .buttonStyle(KeunDanchu())
                    Button("흰지팡이 — \(jipangi == "씀" ? "씁니다" : "안 씁니다") (누르면 바뀝니다)") {
                        jipangi = jipangi == "씀" ? "안씀" : "씀"
                        SoriEngine.shared.mal(jipangi == "씀" ? "씁니다" : "안 씁니다")
                    }
                    .buttonStyle(KeunDanchu())
                    Button("재기 시작") { sijak() }.buttonStyle(KeunDanchu())
                }
                DisclosureGroup("지금까지 잰 것 보기 펼치기") {
                    VStack(alignment: .leading, spacing: 10) {
                        if let m = mok {
                            if m.isEmpty { Text("아직 잰 것이 없습니다.") } else { Mokrok5(m) { j in Text(j.mal).font(.title3) } }
                        } else {
                            Text("불러오는 중입니다.")
                        }
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("걸음 오차 재기")
        .task { if mok == nil { mokrokBatgi() } }
        .onDisappear { ssak = nil }
    }

    private func sijak() {
        guard let s = Double(sil.trimmingCharacters(in: .whitespaces)), s > 0 else { alrigi("실제 거리를 넣어 주십시오."); return }
        _ = s
        wiGil = 0
        wiJeon = nil
        WichiEngine.shared.sijak()
        ssak = WichiEngine.shared.saeWichi.receive(on: DispatchQueue.main).sink { w in
            guard !w.georeumChu, w.ochae < 25 else { return }
            if let j = wiJeon {
                let d = WichiEngine.geori(j.0, j.1, w.lat, w.lon)
                if d > 1.5 { wiGil += d; wiJeon = (w.lat, w.lon) }
            } else {
                wiJeon = (w.lat, w.lon)
            }
        }
        sem.sijak()
        alrigi("재고 있습니다. 끝까지 걸으신 뒤 다 걸었습니다를 누르십시오.")
    }

    private func majim() {
        ssak = nil
        let s = Double(sil) ?? 0, bp = Double(bopokCm) ?? 70
        let cho = Date().timeIntervalSince(sem.t0)
        sem.kkeut { n0 in
            let n = n0 ?? 0
            let chu = Double(n) * bp / 100
            let og = s > 0 ? abs(chu - s) / s * 100 : 0
            var r = ["걸음 \(n)걸음, 걸린 시간 \(Int(cho.rounded()))초. 한 걸음을 \(Int(bp))센티미터로 치면 " + String(format: "%.1f", chu) + "미터입니다.",
                     "실제 \(Int(s))미터와 견주면 걸음 오차는 " + String(format: "%.1f", og) + "퍼센트입니다."]
            if s > 0 && wiGil > 0 {
                r.append("위성으로 잰 거리는 " + String(format: "%.1f", wiGil) + "미터, 오차는 " + String(format: "%.1f", abs(wiGil - s) / s * 100) + "퍼센트입니다.")
            } else {
                r.append("위성으로는 재지 못했습니다.")
            }
            if n0 == nil { r.insert("이 폰에서 걸음을 셀 수 없었습니다. 설정 앱에서 동작 및 피트니스를 허용해 주십시오.", at: 0) }
            gyeolgwa = r
            mal = [("sil", String(s)), ("geoleum", String(n)), ("chujeong", String(format: "%.2f", chu)), ("wui", String(format: "%.2f", wiGil)),
                   ("cho", String(format: "%.1f", cho)), ("sokdo", sokdo), ("jipangi", jipangi)]
            alrigi("다 재었습니다. " + r.joined(separator: " "))
        }
    }

    private func mokrokBatgi() {
        Task {
            let r = await Jeomjido.jaegiMokrok()
            await MainActor.run { mok = r ?? [] }
        }
    }

    private func alrigi(_ t: String) {
        allim = t
        SoriEngine.shared.mal(t)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { allimChojeom = true }
    }
}

// MARK: 리모컨 — 블루투스 리모컨이 보내는 글쇠를 받음(화면이 켜져 있을 때만, 아이폰이 허락하는 만큼)

enum RimoDanchu {
    static let IREUM = ["1": "지금 어디입니까", "2": "다음에 무엇이 있습니까", "3": "다시 말해 주기"]

    static func ireum(_ k: UIKey) -> String {
        switch k.keyCode {
        case .keyboardUpArrow: return "위 화살표"
        case .keyboardDownArrow: return "아래 화살표"
        case .keyboardLeftArrow: return "왼쪽 화살표"
        case .keyboardRightArrow: return "오른쪽 화살표"
        case .keyboardReturnOrEnter, .keypadEnter: return "엔터"
        case .keyboardSpacebar: return "스페이스"
        case .keyboardPageUp: return "페이지 업"
        case .keyboardPageDown: return "페이지 다운"
        case .keyboardEscape: return "이에스시"
        case .keyboardTab: return "탭"
        default:
            let c = k.charactersIgnoringModifiers.trimmingCharacters(in: .whitespacesAndNewlines)
            return c.isEmpty ? "단추 \(k.keyCode.rawValue)" : c
        }
    }
}

final class RimoBatneunUIView: UIView {
    var batgi: ((String) -> Void)?
    override var canBecomeFirstResponder: Bool { true }
    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil { DispatchQueue.main.async { _ = self.becomeFirstResponder() } }
    }
    override func pressesBegan(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        var batam = false
        for p in presses {
            if let k = p.key {
                let nm = RimoDanchu.ireum(k)
                if nm == "탭" { continue }
                batam = true
                batgi?(nm)
            }
        }
        if !batam { super.pressesBegan(presses, with: event) }
    }
}

struct RimoBatgi: UIViewRepresentable {
    let batgi: (String) -> Void
    func makeUIView(context: Context) -> RimoBatneunUIView {
        let v = RimoBatneunUIView()
        v.batgi = batgi
        v.isAccessibilityElement = false
        v.accessibilityElementsHidden = true
        return v
    }
    func updateUIView(_ v: RimoBatneunUIView, context: Context) { v.batgi = batgi }
}

struct RimoView: View {
    @ObservedObject private var s = Seoljeong.shared
    @State private var baeuneun: String?
    @State private var siheom = false
    @State private var allim = ""
    @State private var gidarim: DispatchWorkItem?
    @AccessibilityFocusState private var allimChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !allim.isEmpty { Text(allim).font(.title3).accessibilityFocused($allimChojeom) }
                ForEach(["1", "2", "3"], id: \.self) { jari in
                    Button("\(jari)번 자리 익히기 — \(RimoDanchu.IREUM[jari] ?? ""). 지금 " + (s.rimo[jari].map { "\($0) 단추" } ?? "익히지 않음")) {
                        baeugi(jari)
                    }
                    .buttonStyle(KeunDanchu())
                }
                Button(siheom ? "눌러 보기 멈추기" : "익힌 단추 눌러 보기") {
                    if siheom { siheom = false; alrigi("눌러 보기를 멈췄습니다."); return }
                    guard !s.rimo.isEmpty else { alrigi("아직 익힌 단추가 없습니다."); return }
                    baeuneun = nil
                    siheom = true
                    alrigi("이제 리모컨 단추를 눌러 보십시오. 어느 자리인지 말씀드리겠습니다.")
                }
                .buttonStyle(KeunDanchu())
                Button("담긴 것 모두 지우기") { s.rimo = [:]; alrigi("담긴 것을 모두 지웠습니다.") }
                    .buttonStyle(KeunDanchu())
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("리모컨 가운데는 소리 크기 단추만 보내는 것이 있습니다. 그런 리모컨은 아무 반응이 없습니다. 글자판처럼 움직이는 리모컨, 흔히 전자책 페이지 넘김 리모컨이라 부르는 것들이 잘 맞습니다. 익힌 단추는 점지도 따라 걷기 화면이 켜져 있을 때 쓰입니다. 폰이 잠겨 있을 때는 이어폰 단추를 쓰십시오. 보이스오버의 화살표 키 탐색이 켜져 있으면 화살표 단추를 보이스오버가 먼저 가져갈 수 있습니다.")
                        .font(.body)
                }
                .font(.title3)
                if baeuneun != nil || siheom {
                    RimoBatgi { k in batda(k) }.frame(width: 1, height: 1)
                }
            }
            .padding()
        }
        .sokHwamyeon("리모컨 배우기")
        .onDisappear { gidarim?.cancel() }
    }

    private func baeugi(_ jari: String) {
        siheom = false
        baeuneun = jari
        alrigi("\(jari)번 자리를 익힙니다. 지금 리모컨 단추를 한 번 눌러 주십시오. 십 초 안에 눌러 주십시오.")
        gidarim?.cancel()
        let w = DispatchWorkItem {
            if baeuneun == jari {
                baeuneun = nil
                alrigi("아무 단추도 들어오지 않았습니다. 이 리모컨은 소리 크기 단추만 보내는 것일 수 있습니다.")
            }
        }
        gidarim = w
        DispatchQueue.main.asyncAfter(deadline: .now() + 10, execute: w)
    }

    private func batda(_ k: String) {
        if let jari = baeuneun {
            gidarim?.cancel()
            var m = s.rimo
            for (a, b) in m where b == k && a != jari { m[a] = nil }
            m[jari] = k
            s.rimo = m
            baeuneun = nil
            alrigi("\(jari)번 자리에 \(k) 단추를 담았습니다. \(RimoDanchu.IREUM[jari] ?? "")에 쓰입니다.")
            return
        }
        if siheom {
            if let jari = s.rimo.first(where: { $0.value == k })?.key {
                SoriEngine.shared.mal("\(jari)번, \(RimoDanchu.IREUM[jari] ?? "") 입니다.")
            } else {
                SoriEngine.shared.mal("\(k) 단추는 익히지 않은 단추입니다.")
            }
        }
    }

    private func alrigi(_ t: String) {
        allim = t
        SoriEngine.shared.mal(t)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { allimChojeom = true }
    }
}

// MARK: 2.11.0 내 문 — 문까지 안내에서 가장 먼저 씀(폰 안에만)

struct NaeMunView: View {
    @ObservedObject private var nae = NaeMun.shared
    @State private var ireum = ""
    @State private var allim = ""
    @State private var jiulGeot: NaeMunHang?
    @State private var cheot: (Wichi, Double, Date)?   // 2.15.0 두 번 찍기 — 문 앞에서 찍은 자리, 보던 쪽, 때
    @AccessibilityFocusState private var allimChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !allim.isEmpty { Text(allim).font(.title3).accessibilityFocused($allimChojeom) }
                TextField("문 이름 — 예: 우리 집 현관, 사무실 정문", text: $ireum)
                    .textFieldStyle(.roundedBorder).font(.title3)
                // 2.15.0 두 번 찍기(이사장님 약속 — 문 두 걸음 앞에서 한 번, 문을 지나 두 걸음 들어가서 한 번)
                if cheot == nil {
                    Button("문 앞에서 한 번 찍기 — 문 두 걸음 앞에서 문 쪽을 보고") { cheotJjikgi() }
                        .buttonStyle(KeunDanchu())
                } else {
                    Button("문을 지나 한 번 더 찍기 — 두 걸음 들어가서") { dulJjikgi() }
                        .buttonStyle(KeunDanchu())
                    Button("찍기 그만두기") {
                        cheot = nil
                        _ = MunChatgi.shared.jjikgiKkeut()
                        alrigi("찍기를 그만두었습니다.")
                    }
                    .buttonStyle(KeunDanchu())
                }
                Text("문 두 걸음 앞에서 한 번, 문을 지나 두 걸음 들어가서 한 번 더 찍으시면 두 자리 사이를 문으로, 들어가신 쪽을 들어가는 쪽으로 담습니다. 문 찾기를 쓸 수 있는 폰은 첫 번째 찍을 때 카메라가 문 둘레 글자(호수, 출입구 같은 것)를 함께 읽어 담아 두었다가, 다음에 찾아가실 때 찍어 두신 문인지, 옆 문인지 알려 드립니다. 사진은 담지 않습니다. 담아 두신 문은 점지도 끝 60미터 안에 있으면 문까지 안내에서 가장 먼저 씁니다. 이 폰 안에만 담깁니다. 지우시려면 그 줄에서 보이스오버로 위아래로 쓸어 지우기를 고르십시오.")
                    .font(.body)
                if !nae.mokrok.isEmpty {
                    Mokrok5(nae.mokrok) { h in
                        Button("\(h.ireum) · \(h.made)" + ((h.geul ?? []).isEmpty ? "" : " · 글자 \((h.geul ?? []).joined(separator: ", "))")) {
                            SoriEngine.shared.mal("\(h.ireum), \(h.made)에 \(h.jjak == true ? "두 번 찍어 " : "")담은 문입니다." + ((h.geul ?? []).isEmpty ? "" : " 문 둘레 글자는 \((h.geul ?? []).joined(separator: ", "))입니다."))
                        }
                            .buttonStyle(KeunDanchu())
                            .accessibilityAction(named: "이 문 지우기") { jiulGeot = h }
                            .contextMenu { Button("이 문 지우기") { jiulGeot = h } }
                    }
                }
            }
            .padding()
        }
        .sokHwamyeon("내 문")
        .confirmationDialog("이 문을 지울까요?", isPresented: Binding(get: { jiulGeot != nil }, set: { if !$0 { jiulGeot = nil } }), titleVisibility: .visible) {
            Button("지우기", role: .destructive) {
                if let h = jiulGeot { nae.jiugi(h.id); alrigi("\(h.ireum)을 지웠습니다.") }
                jiulGeot = nil
            }
            Button("그만두기", role: .cancel) { jiulGeot = nil }
        }
    }

    /// 2.15.0 첫 번째 찍기 — 문 두 걸음 앞. 문 찾기를 쓸 수 있는 폰은 카메라로 문 둘레 글자를 모으기 시작
    private func cheotJjikgi() {
        WichiEngine.shared.sijak()
        guard let w = WichiEngine.shared.jigeum, Date().timeIntervalSince(w.ttae) < 20 else { alrigi("아직 위치를 잡는 중입니다. 잠시 뒤에 다시 눌러 주십시오."); return }
        if w.ochae > 20 { alrigi("지금은 위성이 흐려 자리가 \(Int(w.ochae))미터쯤 어긋날 수 있습니다. 문 바로 앞 밖에서 다시 눌러 주십시오."); return }
        cheot = (w, WichiEngine.shared.nachimban, Date())
        var t = "한 번 찍었습니다. 문을 지나 두 걸음 들어가신 뒤 한 번 더 찍어 주십시오."
        if MunChatgi.gigiGaneung {
            MunChatgi.shared.kyeogi("jjikgi")
            t += " 그동안 카메라가 문 둘레 글자를 읽습니다. 폰을 문 쪽으로 들어 주십시오."
        }
        alrigi(t)
    }

    /// 2.15.0 두 번째 찍기 — 문을 지나 두 걸음. 두 자리 사이를 문으로
    private func dulJjikgi() {
        guard let c = cheot else { return }
        let geul = MunChatgi.shared.jjikgiKkeut()
        cheot = nil
        if Date().timeIntervalSince(c.2) > 120 { alrigi("첫 번째 찍은 지 2분이 넘어 다시 찍어야 합니다. 문 두 걸음 앞에서 한 번 찍기부터 다시 해 주십시오."); return }
        guard let w = WichiEngine.shared.jigeum, Date().timeIntervalSince(w.ttae) < 20 else { alrigi("위치를 받지 못해 담지 못했습니다. 다시 찍어 주십시오."); return }
        let d = WichiEngine.geori(c.0.lat, c.0.lon, w.lat, w.lon)
        if d > 8 { alrigi("두 자리가 \(Int(d))미터나 떨어져 문으로 보기 어렵습니다. 위성이 흔들린 것 같습니다. 다시 찍어 주십시오."); return }
        let n = WichiEngine.shared.nachimban
        var bang: Double? = n >= 0 ? n : (c.1 >= 0 ? c.1 : nil)
        if bang == nil && d >= 1.5 { bang = WichiEngine.bangwi(c.0.lat, c.0.lon, w.lat, w.lon) }
        let nm = ireum.trimmingCharacters(in: .whitespaces)
        let m = nae.jjakDamgi(ireum: nm, lat: (c.0.lat + w.lat) / 2, lon: (c.0.lon + w.lon) / 2, bang: bang, geul: geul)
        ireum = ""
        Girok.shared.namgi("naemun_jjak", ["geul": geul.count])
        alrigi(m + (bang != nil ? " 들어가신 쪽을 들어가는 쪽으로 적었습니다." : ""))
    }

    private func damgi() {
        WichiEngine.shared.sijak()
        guard let w = WichiEngine.shared.jigeum, Date().timeIntervalSince(w.ttae) < 20 else { alrigi("아직 위치를 잡는 중입니다. 잠시 뒤에 다시 눌러 주십시오."); return }
        if w.ochae > 20 { alrigi("지금은 위성이 흐려 자리가 \(Int(w.ochae))미터쯤 어긋날 수 있습니다. 문 바로 앞 밖에서 다시 눌러 주십시오."); return }
        let nm = ireum.trimmingCharacters(in: .whitespaces)
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "M월 d일"
        let n = WichiEngine.shared.nachimban
        nae.damgi(NaeMunHang(id: UUID().uuidString, ireum: nm.isEmpty ? "내 문" : nm, lat: w.lat, lon: w.lon,
                             bang: n >= 0 ? n : nil, made: f.string(from: Date())))
        ireum = ""
        alrigi("\(nm.isEmpty ? "내 문" : nm)을 담았습니다." + (n >= 0 ? " 지금 보고 계신 쪽을 들어가는 쪽으로 적었습니다." : ""))
    }

    private func alrigi(_ t: String) {
        allim = t
        SoriEngine.shared.mal(t)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { allimChojeom = true }
    }
}
