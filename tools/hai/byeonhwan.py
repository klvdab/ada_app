# 하이 길눈 감지기 기르기 — 여러 사람 목소리로 바꾸기 (판 1.0, 빌드 261001-H2, 대표님 승인 2026-10-01)
# 마이크로소프트 목소리 셋만으로는 실제 사람들(어르신, 낮은 목소리, 높은 목소리)을 다 담지 못합니다.
# 그래서 공개된 한국어 말소리 모음(제로스 한국어, 크리에이티브 커먼즈 저작자 표시)에 담긴 실제 사람 100여 명의 목소리를 빌려
#   ① "하이 길눈" 연습 소리를 그 사람들 목소리로 바꾸고(kNN-VC, MIT 공개 부품 — 말은 그대로, 목소리 빛깔만 바꿈)
#   ② 그 사람들이 실제로 한 평소 말은 그대로 "틀린 소리"로 씁니다(실제 사람 말이라 헛들음 막기에 가장 좋음).
# 쓰는 법: python3 byeonhwan.py 조각번호 조각수 맞는소리폴더 내보낼폴더
import io, json, os, random, sys, zlib
import numpy as np, soundfile as sf, torch
from huggingface_hub import list_repo_files, hf_hub_download
import pyarrow.parquet as pq
from scipy.signal import resample_poly

JO, JOSU, MAJA, OUT = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3], sys.argv[4]
random.seed(261001 + JO)
os.makedirs(OUT + "/maja_vc", exist_ok=True); os.makedirs(OUT + "/teul_saram", exist_ok=True); os.makedirs(OUT + "/ref", exist_ok=True)

REPOS = ["kresnik/zeroth_korean", "Bingsu/zeroth-korean"]
pf, repo = [], None
for r in REPOS:
    try:
        fs = [f for f in list_repo_files(r, repo_type="dataset") if f.endswith(".parquet") and "train" in f]
        if fs: pf, repo = sorted(fs), r; break
    except Exception as e:
        print("못 엶", r, e)
print("모음", repo, "파일", len(pf))
mine = [f for i, f in enumerate(pf) if i % JOSU == JO]
print("이 조각 파일", mine)

saram = {}   # 사람 → [소리들]
for f in mine:
    p = hf_hub_download(repo, f, repo_type="dataset")
    t = pq.read_table(p)
    cols = t.column_names
    print(f, t.num_rows, cols)
    rows = t.to_pylist()
    for i, row in enumerate(rows):
        a = row.get("audio")
        if not a: continue
        b = a.get("bytes") if isinstance(a, dict) else None
        if b is None: continue
        try:
            x, sr = sf.read(io.BytesIO(b), dtype="float32")
        except Exception:
            continue
        if x.ndim > 1: x = x.mean(1)
        if sr != 16000: x = resample_poly(x, 16000, sr).astype(np.float32)
        spk = row.get("speaker_id")
        if spk is None:
            iid = str(row.get("id") or row.get("path") or (a.get("path") if isinstance(a, dict) else "") or "")
            spk = iid.split("_")[0] if "_" in iid else f"{os.path.basename(f)}_{i // 25}"
        saram.setdefault(str(spk), []).append(x)
print("사람 수", len(saram))

knn = torch.hub.load("bshall/knn-vc", "knn_vc", prematched=True, trust_repo=True, pretrained=True, device="cpu")
majas = sorted(os.listdir(MAJA))
mok = []
for spk, xs in sorted(saram.items()):
    random.shuffle(xs)
    ref, gil = [], 0.0
    for x in xs:
        if gil >= 60: break
        ref.append(x); gil += len(x) / 16000
    if gil < 15: continue
    rp = []
    for k, x in enumerate(ref):
        q = f"{OUT}/ref/{spk}_{k}.wav"; sf.write(q, x, 16000); rp.append(q)
    try:
        ms = knn.get_matching_set(rp)
    except Exception as e:
        print("짝 못 만듦", spk, e); continue
    for f in random.sample(majas, min(14, len(majas))):
        try:
            q = knn.get_features(os.path.join(MAJA, f))
            y = knn.match(q, ms, topk=4).cpu().numpy().astype(np.float32)
            o = f"maja_vc/{spk}_{f}"
            sf.write(f"{OUT}/{o}", y / (np.max(np.abs(y)) + 1e-9) * 0.8, 16000); mok.append(dict(gal="maja_vc", saram=spk, won=f, f=os.path.basename(o)))
        except Exception as e:
            print("바꾸기 못 함", spk, f, e)
    for k, x in enumerate(xs[len(ref):len(ref) + 6]):
        o = f"teul_saram/{spk}_{k}.wav"; sf.write(f"{OUT}/{o}", x, 16000); mok.append(dict(gal="teul_saram", saram=spk, f=os.path.basename(o)))
    for q in rp: os.remove(q)
    print("사람", spk, "끝", len(mok), flush=True)
json.dump(mok, open(f"{OUT}/mokrok_{JO}.json", "w"), ensure_ascii=False)
print("이 조각 끝", len(mok))
