"""Generate Google Play store assets (icon + feature graphic) for Kite.

Run from anywhere:  python tools/make-play-assets.py
Output goes to <repo>/play/.

Requires Pillow and a CJK-capable font. On Linux: sudo apt install fonts-noto-cjk
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
PLAY = ROOT / "play"
PLAY.mkdir(parents=True, exist_ok=True)

INK = (8, 10, 14)
PANEL = (17, 21, 27)
PANEL2 = (24, 29, 37)
RED = (255, 59, 48)
RED_SOFT = (255, 122, 110)
PAPER = (244, 239, 230)
CHROME = (198, 206, 218)
MUTED = (138, 143, 153)
LINE = (37, 43, 53)
MINT = (159, 232, 196)
AMBER = (255, 233, 201)

# First existing path wins. Add your own if none of these match.
FONT_BOLD_CANDIDATES = [
    r"C:\Windows\Fonts\msyhbd.ttc",           # Windows: Microsoft YaHei Bold
    "/System/Library/Fonts/PingFang.ttc",      # macOS
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",  # Linux: Noto CJK
    "/usr/share/fonts/truetype/noto/NotoSansCJK-Bold.ttc",
]
FONT_REG_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    "/System/Library/Fonts/PingFang.ttc",
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
    "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
]


def _first_existing(candidates, label):
    for candidate in candidates:
        if Path(candidate).exists():
            return candidate
    raise SystemExit(
        f"No {label} font found. Tried:\n  "
        + "\n  ".join(candidates)
        + "\nInstall a CJK font or add its path to FONT_*_CANDIDATES."
    )


FONT_BOLD = _first_existing(FONT_BOLD_CANDIDATES, "bold CJK")
FONT_REG = _first_existing(FONT_REG_CANDIDATES, "regular CJK")


def font(path, size):
    return ImageFont.truetype(path, size, index=0)


def radial_glow(size, color=RED, strength=80):
    w, h = size
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = w // 2, int(h * 0.42)
    for i in range(18, 0, -1):
        r = int(w * (0.12 + i * 0.024))
        alpha = int(strength * (1 - i / 19) ** 1.6)
        d.ellipse([cx - r, cy - r * 0.62, cx + r, cy + r * 0.62],
                  fill=(color[0], color[1], color[2], alpha))
    return layer.filter(ImageFilter.GaussianBlur(w // 28))


def car_mark(w, h):
    """Sleek original KITT-inspired front: low wedge, chrome edge, red scanner."""
    scale = 4
    W, H = w * scale, h * scale
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def pt(x, y):
        return (int(W * x), int(H * y))

    body = [pt(0.06, 0.66), pt(0.22, 0.38), pt(0.45, 0.30), pt(0.55, 0.30),
            pt(0.78, 0.38), pt(0.94, 0.66), pt(0.88, 0.83), pt(0.12, 0.83)]

    # glow behind the scanner
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([pt(0.10, 0.47)[0], pt(0.10, 0.47)[1], pt(0.95, 0.68)[0], pt(0.95, 0.68)[1]],
               fill=(255, 59, 48, 55))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(int(W * 0.035))))

    # body gradient
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).polygon(body, fill=255)
    grad = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for y in range(H):
        t = y / max(1, H - 1)
        r = int(43 + (10 - 43) * t)
        g = int(50 + (13 - 50) * t)
        b = int(61 + (18 - 61) * t)
        ImageDraw.Draw(grad).line([(0, y), (W, y)], fill=(r, g, b, 255))
    img.paste(grad, (0, 0), mask)
    d.line(body + [body[0]], fill=CHROME + (255,), width=max(2, int(W * 0.006)), joint="curve")

    # hood highlight
    d.polygon([pt(0.25, 0.37), pt(0.45, 0.30), pt(0.55, 0.30), pt(0.55, 0.46), pt(0.19, 0.53)],
              fill=(255, 255, 255, 16))

    # windshield
    d.polygon([pt(0.31, 0.40), pt(0.46, 0.33), pt(0.60, 0.33), pt(0.76, 0.40),
               pt(0.81, 0.51), pt(0.27, 0.51)], fill=(7, 9, 13, 255))

    # scanner
    d.rounded_rectangle([pt(0.24, 0.53)[0], pt(0.24, 0.53)[1],
                         pt(0.84, 0.61)[0], pt(0.84, 0.61)[1]],
                        radius=int(H * 0.012), fill=RED + (255,))
    d.rounded_rectangle([pt(0.42, 0.555)[0], pt(0.42, 0.555)[1],
                         pt(0.66, 0.585)[0], pt(0.66, 0.585)[1]],
                        radius=int(H * 0.006), fill=RED_SOFT + (255,))

    # headlights
    d.polygon([pt(0.13, 0.635), pt(0.30, 0.635), pt(0.28, 0.668), pt(0.14, 0.668)],
              fill=(228, 234, 242, 225))
    d.polygon([pt(0.87, 0.635), pt(0.70, 0.635), pt(0.72, 0.668), pt(0.86, 0.668)],
              fill=(228, 234, 242, 225))

    # bumper / lower intake
    d.line([pt(0.20, 0.775), pt(0.80, 0.775)], fill=CHROME + (70,), width=max(1, int(H * 0.006)))
    d.rounded_rectangle([pt(0.30, 0.74)[0], pt(0.30, 0.74)[1], pt(0.70, 0.755)[0], pt(0.70, 0.755)[1]],
                        radius=int(H * 0.004), fill=(5, 7, 10, 255))

    return img.resize((w, h), Image.LANCZOS)


def make_icon():
    size = 512
    img = Image.new("RGB", (size, size), INK)
    img = Image.alpha_composite(img.convert("RGBA"), radial_glow((size, size), strength=95)).convert("RGB")
    mark = car_mark(470, 360)
    img.paste(mark, ((size - mark.width) // 2, (size - mark.height) // 2 - 4), mark)
    img.save(PLAY / "icon-512.png", "PNG")
    print("icon ok")


def make_feature():
    w, h = 1024, 500
    img = Image.new("RGB", (w, h), INK)
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, w, 10], fill=RED)
    glow = radial_glow((w, h), strength=45)
    img = Image.alpha_composite(img.convert("RGBA"), glow).convert("RGB")
    draw = ImageDraw.Draw(img)

    mark = car_mark(150, 110)
    img.paste(mark, (62, 100), mark)
    draw.text((236, 112), "KITE", font=font(FONT_BOLD, 64), fill=PAPER)
    draw.text((238, 186), "MOBILE AGENT", font=font(FONT_REG, 12), fill=RED)
    draw.text((72, 252), "把想法交给它。", font=font(FONT_BOLD, 40), fill=PAPER)
    draw.text((74, 318), "读文件 · 查网页 · 跑命令", font=font(FONT_REG, 20), fill=MUTED)
    draw.text((74, 362), "Android AI Agent · MCP", font=font(FONT_REG, 16), fill=RED)

    px, py, pw, ph = 706, 48, 258, 404
    draw.rounded_rectangle([px, py, px + pw, py + ph], radius=34, fill=PANEL, outline=LINE, width=2)
    draw.rounded_rectangle([px + 10, py + 10, px + pw - 10, py + ph - 10], radius=26, fill=INK)
    draw.rounded_rectangle([px + 10, py + 10, px + pw - 10, py + 22], radius=6, fill=RED)

    draw.text((px + 22, py + 38), "KITE", font=font(FONT_BOLD, 10), fill=PAPER)
    draw.text((px + 62, py + 38), "· device online", font=font(FONT_REG, 8), fill=MUTED)

    draw.rounded_rectangle([px + 92, py + 74, px + 232, py + 120], radius=14, fill=PANEL2)
    draw.rounded_rectangle([px + 106, py + 87, px + 216, py + 93], radius=3, fill=MUTED)
    draw.rounded_rectangle([px + 106, py + 101, px + 180, py + 107], radius=3, fill=MUTED)

    draw.rounded_rectangle([px + 22, py + 146, px + 224, py + 152], radius=3, fill=PAPER)
    draw.rounded_rectangle([px + 22, py + 162, px + 198, py + 168], radius=3, fill=MUTED)

    for i, label in enumerate(["device_info", "web_search", "mcp__echo"]):
        y = py + 188 + i * 38
        draw.rounded_rectangle([px + 22, y, px + 238, y + 30], radius=10, fill=PANEL, outline=LINE, width=1)
        draw.rectangle([px + 32, y + 9, px + 44, y + 21], outline=RED, width=2)
        draw.text((px + 52, y + 8), label, font=font(FONT_REG, 9), fill=PAPER)
        draw.ellipse([px + 204, y + 11, px + 209, y + 16], fill=MINT)
        draw.line([(px + 212, y + 13), (px + 216, y + 17), (px + 222, y + 9)],
                  fill=MINT, width=2)

    draw.rounded_rectangle([px + 22, py + 306, px + 226, py + 312], radius=3, fill=PAPER)
    draw.rounded_rectangle([px + 22, py + 322, px + 204, py + 328], radius=3, fill=MUTED)
    draw.rounded_rectangle([px + 22, py + 338, px + 216, py + 344], radius=3, fill=MUTED)

    draw.rounded_rectangle([px + 16, py + 354, px + pw - 16, py + 386], radius=14,
                           fill=PANEL, outline=LINE, width=1)
    draw.rounded_rectangle([px + 30, py + 366, px + 148, py + 372], radius=3, fill=MUTED)
    draw.ellipse([px + pw - 54, py + 356, px + pw - 28, py + 382], fill=RED)
    draw.line([(px + pw - 41, py + 369), (px + pw - 41, py + 361)], fill=INK, width=2)
    draw.polygon([(px + pw - 41, py + 359), (px + pw - 35, py + 367),
                  (px + pw - 41, py + 367)], fill=INK)

    img.save(PLAY / "feature-1024x500.png", "PNG")
    print("feature ok")


make_icon()
make_feature()
