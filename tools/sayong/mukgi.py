#!/usr/bin/env python3
# 길눈 사용법 52편 한 묶음 만들기 (빌드 261018-4, 이사장님 지시 2026-10-18 14:40 「전체를 하나의 파일로 압축해서 받게」)
# 동영상(sayong-dongyeongsang 갈래)과 글(sayong-bunseok 갈래 geul/)을 아이폰·안드로이드 폴더로 나눠 zip 하나로 묶음.
# 압축 속 한글 이름은 윈도우 한글판이 읽는 CP949로 넣음(이사장님 2026-09-27 당부). 글은 EUC-KR(CP949)+CRLF.
# 클라우드플레어 한 번 100메가 한도 때문에 90메가 조각으로도 나눔(나스에서 이어 붙임).
import zipfile, re, sys, os
dong, geul, nae = sys.argv[1], sys.argv[2], sys.argv[3]
nums = [str(i) for i in range(1, 17)] + [str(i) for i in range(21, 30)] + ["210"]
def bunho(n):
    p, s = ("2", n[1:]) if n.startswith("2") and len(n) > 1 else ("1", n)
    return f"{p}-{int(s):02d}"
def jemok(n):
    l = open(f"{geul}/ti{n}.txt", encoding="utf-8").read().split("\n")[2].strip()
    t = re.sub(r"^\d+-\d+\s*", "", l)
    return re.sub(r'[\\/:*?"<>|]', "", t).replace(" - ", " ").replace("·", "_").strip()
z = zipfile.ZipFile(nae, "w")
def neogi(name, data, mp4):
    zi = zipfile.ZipInfo(name.encode("cp949").decode("cp437"), date_time=(2026, 10, 18, 14, 0, 0))
    zi.compress_type = zipfile.ZIP_STORED if mp4 else zipfile.ZIP_DEFLATED
    zi.external_attr = 0o644 << 16
    z.writestr(zi, data)
for n in nums:
    b, t = bunho(n), jemok(n)
    for p, pn in (("i", "아이폰"), ("a", "안드로이드")):
        neogi(f"{pn}/{b} {t}_{pn}.mp4", open(f"{dong}/{p}{n}.mp4", "rb").read(), True)
        g = open(f"{geul}/t{p}{n}.txt", encoding="utf-8").read().replace("\r", "").replace("\n", "\r\n")
        neogi(f"{pn}/{b} {t}_{pn}_글.txt", g.encode("cp949", errors="replace"), False)
z.close()
z = zipfile.ZipFile(nae); assert z.testzip() is None; print(len(z.namelist()), "개", os.path.getsize(nae), "바이트")
