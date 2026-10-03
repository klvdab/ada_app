# 협회 안드로이드 앱 아이콘 만들기 (1.0.0판, 빌드 260923-1)
# 짓기 직전에 돌려 갈래(길눈·자봉·배프)에 맞는 아이콘을 만듭니다.
# 아이폰과 같은 꼴: 짙은 바탕에 흰 글자, 아래 노란 줄. 대비를 크게 했습니다.
import os
import sys

from PIL import Image, ImageDraw, ImageFont

S = 1024
FG = (255, 255, 255)
BAR = (255, 204, 0)
GALRAE = {
    "gilnun": ((18, 52, 110), "LVD", False),
    "jabong": ((12, 96, 60), "자봉", True),
    "bfb": ((122, 20, 70), "배프", True),
    "byod": ((18, 52, 110), "BYOD", False),
}
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

FONTS = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
]
FONTS_KO = [
    "/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf",
    "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
    "/usr/share/fonts/truetype/noto/NotoSansCJK-Bold.ttc",
]


def font(size, korean=False):
    paths = (FONTS_KO + FONTS) if korean else FONTS
    for p in paths:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    return ImageFont.load_default()


def can_draw(f, t):
    try:
        d = ImageDraw.Draw(Image.new("RGB", (10, 10)))
        x0, y0, x1, y1 = d.textbbox((0, 0), t, font=f)
        return (x1 - x0) > 0 and (y1 - y0) > 0
    except Exception:
        return False


def make(bg, text, korean):
    img = Image.new("RGB", (S, S), bg)
    d = ImageDraw.Draw(img)
    size = 400 if korean else (360 if len(text) <= 3 else 250)
    f = font(size, korean=korean)
    t = text
    if not can_draw(f, t):
        t = "LVD"
        f = font(360)
    x0, y0, x1, y1 = d.textbbox((0, 0), t, font=f)
    w, h = x1 - x0, y1 - y0
    d.text(((S - w) / 2 - x0, (S - h) / 2 - y0 - 60), t, font=f, fill=FG)
    d.rounded_rectangle((252, 700, 772, 748), radius=24, fill=BAR)
    return img


def save(img, galrae):
    base = os.path.join("app", "src", galrae, "res")
    for name, px in SIZES.items():
        folder = os.path.join(base, "mipmap-" + name)
        os.makedirs(folder, exist_ok=True)
        small = img.resize((px, px), Image.LANCZOS)
        small.save(os.path.join(folder, "ic_launcher.png"))
        small.save(os.path.join(folder, "ic_launcher_round.png"))


def make_lib():
    """261002-L1 AI점자도서관 — 남색 바탕에 법인 로고(점자 lvd 금빛 + 빨강·노랑·초록 LVD), 아이폰 아이콘과 같음"""
    img = Image.new("RGB", (S, S), (20, 33, 61))
    d = ImageDraw.Draw(img)
    s = 4.6
    tx, ty = 512 - 105.75 * s, 512 - 81 * s
    P = lambda x, y: (tx + x * s, ty + y * s)
    for (x, y) in [(33.9, 28.5), (33.9, 46.7), (33.9, 65.0), (88.8, 28.5), (88.8, 46.7), (88.7, 65.0), (107.9, 65.0), (148.6, 28.6), (167.6, 28.5), (167.6, 46.8)]:
        cx, cy = P(x, y); r = 8.4 * s
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(247, 231, 180))
    d.polygon([P(26, 80), P(37, 80), P(37, 131), P(67, 131), P(67, 142), P(26, 142)], fill=(215, 22, 24))
    d.polygon([P(66, 80), P(79, 80), P(94.5, 126), P(111, 80), P(124, 80), P(101, 142), P(88, 142)], fill=(244, 199, 32))
    g = (59, 179, 93)
    x0, y0 = P(124, 80); x1, y1 = P(186, 142)
    d.pieslice((x0, y0, x1, y1), -90, 90, fill=g)
    d.rectangle((*P(135, 80), *P(155, 142)), fill=g)
    ix0, iy0 = P(135, 91); ix1, iy1 = P(175, 131)
    d.pieslice((ix0, iy0, ix1, iy1), -90, 90, fill=(20, 33, 61))
    d.rectangle((*P(146, 91), *P(155, 131)), fill=(20, 33, 61))
    return img


if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    if which in ("all", "lib"):
        save(make_lib(), "lib")
        print("아이콘을 만들었습니다: lib AI점자도서관")
    for galrae, (bg, text, korean) in GALRAE.items():
        if which not in ("all", galrae):
            continue
        im = make(bg, text, korean)
        save(im, galrae)
        print("아이콘을 만들었습니다:", galrae, text)
