#!/usr/bin/env python3
"""
Builds every density of the MOD icon and banner from two master images.

    branding-src/icon_master.png    square, >= 320x320 recommended (YouTube play symbol)
    branding-src/banner_master.png  16:9,   >= 640x360 recommended (YouTube logo)

Output (bundled into the .mpp by Gradle):
    patches/src/main/resources/tizentube/branding/icon/<density>.png
    patches/src/main/resources/tizentube/branding/banner/<density>.png

Usage:  pip install pillow && python3 branding-src/make_branding.py
Options: --no-badge  copy the masters as they are (if your artwork already carries the MOD badge)
"""
import argparse
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "branding-src"
OUT = ROOT / "patches/src/main/resources/tizentube/branding"

# Sizes match the bitmaps shipped by TizenTube Cobalt 2.0.x (mipmap ic_app / drawable app_banner).
ICON_SIZES = {"mdpi": 80, "hdpi": 120, "xhdpi": 160, "xxhdpi": 240, "xxxhdpi": 320}
BANNER_SIZES = {"mdpi": (160, 90), "hdpi": (240, 135), "xhdpi": (320, 180),
                "xxhdpi": (480, 270), "xxxhdpi": (640, 360)}

RED = (230, 0, 18, 255)
WHITE = (255, 255, 255, 255)
DARK = (20, 20, 20, 235)

FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
]


def font(size):
    for path in FONT_CANDIDATES:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def pill(draw, box, text, fill, text_fill, outline=WHITE):
    """Rounded pill with centered bold text. box = (x0, y0, x1, y1)."""
    x0, y0, x1, y1 = box
    h = y1 - y0
    border = max(2, h // 10)
    draw.rounded_rectangle(box, radius=h // 2, fill=outline)
    inner = (x0 + border, y0 + border, x1 - border, y1 - border)
    draw.rounded_rectangle(inner, radius=(h - 2 * border) // 2, fill=fill)
    f = font(int(h * 0.58))
    tw = draw.textlength(text, font=f)
    l, t, r, b = draw.textbbox((0, 0), text, font=f)
    draw.text(((x0 + x1 - tw) / 2, (y0 + y1) / 2 - (t + b) / 2), text, font=f, fill=text_fill)


def make_icon(master, badge):
    size = 1024
    img = master.convert("RGBA").resize((size, size), Image.LANCZOS)
    if badge:
        d = ImageDraw.Draw(img)
        # MOD pill, bottom-right, ~44% wide: stays readable down to 80 px.
        w, h = int(size * 0.46), int(size * 0.22)
        x1, y1 = size - int(size * 0.03), size - int(size * 0.04)
        pill(d, (x1 - w, y1 - h, x1, y1), "MOD", RED, WHITE)
    return img


def make_banner(master, badge):
    W, H = 1280, 720
    m = master.convert("RGBA")
    # Center-crop to 16:9, then scale.
    ratio = W / H
    if m.width / m.height > ratio:
        nw = int(m.height * ratio); m = m.crop(((m.width - nw) // 2, 0, (m.width + nw) // 2, m.height))
    else:
        nh = int(m.width / ratio); m = m.crop((0, (m.height - nh) // 2, m.width, (m.height + nh) // 2))
    img = m.resize((W, H), Image.LANCZOS)
    if badge:
        d = ImageDraw.Draw(img)
        mh = int(H * 0.17)
        # MOD pill, top-left.
        pill(d, (int(W * 0.03), int(H * 0.05), int(W * 0.03) + int(mh * 2.3), int(H * 0.05) + mh), "MOD", RED, WHITE)
        # Small TizenTube tag, top-right.
        th = int(H * 0.11)
        tw = int(th * 4.6)
        x1 = W - int(W * 0.03)
        pill(d, (x1 - tw, int(H * 0.06), x1, int(H * 0.06) + th), "TizenTube", DARK, WHITE)
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-badge", action="store_true")
    args = ap.parse_args()
    badge = not args.no_badge

    icon = make_icon(Image.open(SRC / "icon_master.png"), badge)
    banner = make_banner(Image.open(SRC / "banner_master.png"), badge)

    (OUT / "icon").mkdir(parents=True, exist_ok=True)
    (OUT / "banner").mkdir(parents=True, exist_ok=True)

    for density, s in ICON_SIZES.items():
        icon.resize((s, s), Image.LANCZOS).save(OUT / "icon" / f"{density}.png", optimize=True)
    for density, (w, h) in BANNER_SIZES.items():
        # Banners are drawn opaque by the TV launcher: flatten on black.
        flat = Image.new("RGB", (w, h), (0, 0, 0))
        b = banner.resize((w, h), Image.LANCZOS)
        flat.paste(b, mask=b.split()[3])
        flat.save(OUT / "banner" / f"{density}.png", optimize=True)

    # Previews for a quick visual check.
    icon.resize((320, 320), Image.LANCZOS).save(SRC / "preview_icon.png")
    banner.resize((640, 360), Image.LANCZOS).save(SRC / "preview_banner.png")
    print("Branding written to", OUT)


if __name__ == "__main__":
    main()
