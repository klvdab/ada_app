#!/bin/bash
# 길눈 사용법 동영상 짜임 알아보기 (빌드 261018-1, 이사장님 승인 2026-10-18 「1」 — 동영상 다시 만들기)
# 나스에 있는 지난 동영상을 받아 화면·소리 짜임과 말소리 받아쓰기를 뽑음
set -e
mkdir -p out/geul
# 사용법 글(EUC-KR)도 UTF-8로 받아 둠 — 동영상 대본을 글과 맞추려고
for n in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 21 22 23 24 25 26 27 28 29 210; do for p in ti ta; do
  curl -sSL -A "gilnun-sayong/1.0" "https://lvd.ada.or.kr/gilnun_sayong/$p$n.txt" | iconv -c -f euc-kr -t utf-8 | tr -d '\r' > out/geul/$p$n.txt || true
done; done
wc -c out/geul/* | tail -3
[ "${DONGYEONGSANG:-0}" = 1 ] || exit 0
for f in i2 a2 i13; do
  curl -sSL -A "gilnun-sayong/1.0" -o out/$f.mp4 "https://lvd.ada.or.kr/gilnun_sayong/$f.mp4"
  ls -l out/$f.mp4
  ffprobe -v error -show_streams -show_format out/$f.mp4 > out/$f.probe.txt
  ffmpeg -loglevel error -y -i out/$f.mp4 -vf "fps=1/5,scale=640:-1" out/${f}_%03d.png
  ffmpeg -loglevel error -y -i out/$f.mp4 -ac 1 -ar 16000 out/$f.wav
done
pip install -q faster-whisper soundfile
python3 - <<'P'
from faster_whisper import WhisperModel
import soundfile as sf
m = WhisperModel("small", compute_type="int8")
for f in ["i2", "a2", "i13"]:
    a, _ = sf.read(f"out/{f}.wav", dtype="float32")
    segs, _ = m.transcribe(a, language="ko")
    with open(f"out/{f}.sseugi.txt", "w") as o:
        for s in segs: o.write(f"{s.start:7.1f} {s.end:7.1f}  {s.text.strip()}\n")
P
rm -f out/*.wav out/i2.mp4 out/a2.mp4
