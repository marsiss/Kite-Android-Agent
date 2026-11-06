"""Derive the shipping app icons from the chosen raw logo candidate.

This is the design-time script that produced play/icon-512.png and
app/src/main/res/drawable-nodpi/ic_launcher_foreground.png.
It is kept for provenance — you do NOT need it to build or run Kite.

Run: python logo/make_ai_icon.py   (requires Pillow)
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parent.parent
SRC = OUT / "logo" / "raw_v3_negative_space.png"

src = Image.open(SRC).convert("RGB")
# crop inside the generated rounded-square frame, around the car
crop = src.crop((90, 250, 934, 730)).convert("RGBA")

# black background -> transparent, preserve red/chrome/white and glow
pixels = crop.load()
w, h = crop.size
for y in range(h):
    for x in range(w):
        r, g, b, _ = pixels[x, y]
        lum = max(r, g, b)
        if lum < 10:
            pixels[x, y] = (0, 0, 0, 0)
        else:
            alpha = min(255, int((lum - 8) * 1.6))
            pixels[x, y] = (r, g, b, alpha)


def radial_bg(size, strength=55):
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = size // 2, int(size * 0.44)
    for i in range(20, 0, -1):
        rad = int(size * (0.10 + i * 0.022))
        alpha = int(strength * (1 - i / 21) ** 1.5)
        d.ellipse([cx - rad, cy - rad * 0.55, cx + rad, cy + rad * 0.55],
                  fill=(255, 59, 48, alpha))
    return layer.filter(ImageFilter.GaussianBlur(size // 30))


def fit(img, box_w, box_h):
    ratio = min(box_w / img.width, box_h / img.height)
    return img.resize((max(1, int(img.width * ratio)), max(1, int(img.height * ratio))), Image.LANCZOS)


# Play / legacy icon
icon = Image.new("RGBA", (512, 512), (8, 10, 14, 255))
icon = Image.alpha_composite(icon, radial_bg(512))
car = fit(crop, 430, 300)
icon.alpha_composite(car, ((512 - car.width) // 2, (512 - car.height) // 2 + 4))
icon.convert("RGB").save(OUT / "play" / "icon-512.png", "PNG")
print("play icon", car.size)

# Adaptive foreground: keep it inside the 66/108 safe zone.
fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
car2 = fit(crop, 320, 220)
fg.alpha_composite(car2, ((432 - car2.width) // 2, (432 - car2.height) // 2 + 8))
fg.save(OUT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "ic_launcher_foreground.png", "PNG")
print("adaptive", car2.size)

# Feature graphic uses the same mark
base = Image.open(OUT / "play" / "feature-1024x500.png").convert("RGB")
mark = fit(crop, 150, 100)
base.paste(mark, (62, 96), mark)
base.save(OUT / "play" / "feature-1024x500.png", "PNG")
print("feature ok")
