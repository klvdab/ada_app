# 협회 앱 아이콘 만들기 (1.0.0판, 빌드 260923-1)
# 깃허브의 빌린 맥에서 짓기 직전에 돌려 아이폰·워치 아이콘(1024, 투명 없음)을 만듭니다.
# 짙은 파랑 바탕에 흰 글자 LVD, 아래에 노란 줄 — 저시력 이용자도 알아보기 쉽게 대비를 크게 했습니다.
import json
import os

from PIL import Image, ImageDraw, ImageFont

S = 1024
BG = (18, 52, 110)
FG = (255, 255, 255)
BAR = (255, 204, 0)
FONTS = [
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/System/Library/Fonts/Supplemental/Arial Black.ttf",
    "/Library/Fonts/Arial Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]


def font(size):
    for p in FONTS:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    raise SystemExit("글꼴을 찾지 못했습니다: " + ", ".join(FONTS))


def make():
    img = Image.new("RGB", (S, S), BG)
    d = ImageDraw.Draw(img)
    f = font(360)
    t = "LVD"
    x0, y0, x1, y1 = d.textbbox((0, 0), t, font=f)
    w, h = x1 - x0, y1 - y0
    d.text(((S - w) / 2 - x0, (S - h) / 2 - y0 - 60), t, font=f, fill=FG)
    d.rounded_rectangle((252, 700, 772, 748), radius=24, fill=BAR)
    return img


def save(img, folder, platform):
    os.makedirs(folder, exist_ok=True)
    img.save(os.path.join(folder, "icon-1024.png"))
    with open(os.path.join(folder, "Contents.json"), "w") as fp:
        json.dump({"images": [{"filename": "icon-1024.png", "idiom": "universal",
                               "platform": platform, "size": "1024x1024"}],
                   "info": {"author": "xcode", "version": 1}}, fp, indent=2)
    with open(os.path.join(os.path.dirname(folder), "Contents.json"), "w") as fp:
        json.dump({"info": {"author": "xcode", "version": 1}}, fp, indent=2)


if __name__ == "__main__":
    im = make()
    save(im, "App/Assets.xcassets/AppIcon.appiconset", "ios")
    save(im, "Watch/Assets.xcassets/AppIcon.appiconset", "watchos")
    print("아이콘을 만들었습니다:", im.mode, im.size)
