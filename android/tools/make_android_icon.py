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
    size = 400 if korean else 360
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


if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    for galrae, (bg, text, korean) in GALRAE.items():
        if which not in ("all", galrae):
            continue
        im = make(bg, text, korean)
        save(im, galrae)
        print("아이콘을 만들었습니다:", galrae, text)
