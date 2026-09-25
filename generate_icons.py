#!/usr/bin/env python3
"""Generates the launcher icon, adaptive icon foreground and TV banner resources
from the source artwork (appIcon.png) for Kempny's Cinema."""

import os
from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, "appIcon.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")

# Density buckets and their scale factor relative to mdpi.
DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

# Matches the sizes that were already checked into the repository.
ICON_BASE = 80        # mdpi px -> 80/120/160/240/320
FOREGROUND_BASE = 108  # adaptive icon canvas is 108dp
BANNER_BASE = (160, 90)

BACKGROUND = (10, 13, 18, 255)


def trim(image):
    """Crops the fully transparent margin around the artwork."""
    bbox = image.getchannel("A").getbbox()
    return image.crop(bbox) if bbox else image


def save(image, folder, name):
    target = os.path.join(RES, folder)
    os.makedirs(target, exist_ok=True)
    path = os.path.join(target, name)
    image.save(path, "PNG", optimize=True)
    print("wrote", os.path.relpath(path, ROOT), image.size)


def square(image, size):
    """Fits the artwork into a square canvas without distortion."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    art = image.copy()
    art.thumbnail((size, size), Image.LANCZOS)
    canvas.paste(art, ((size - art.width) // 2, (size - art.height) // 2), art)
    return canvas


def main():
    source = Image.open(SRC).convert("RGBA")
    art = trim(source)
    print("source", source.size, "-> trimmed", art.size)

    for density, scale in DENSITIES.items():
        # Legacy launcher icon: the artwork already carries its own rounded background.
        size = int(ICON_BASE * scale)
        save(square(art, size), f"mipmap-{density}", "app_icon.png")

        # Adaptive icon foreground: the artwork is placed inside the 72dp safe zone of the
        # 108dp canvas so system masks never clip the clapperboard or the wordmark.
        canvas_size = int(FOREGROUND_BASE * scale)
        safe = int(canvas_size * 72 / 108)
        foreground = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        inner = square(art, safe)
        offset = (canvas_size - safe) // 2
        foreground.paste(inner, (offset, offset), inner)
        save(foreground, f"mipmap-{density}", "app_icon_fg.png")

        # Android TV banner: artwork centred on the brand background.
        bw, bh = int(BANNER_BASE[0] * scale), int(BANNER_BASE[1] * scale)
        banner = Image.new("RGBA", (bw, bh), BACKGROUND)
        logo = square(art, int(bh * 0.86))
        banner.paste(logo, ((bw - logo.width) // 2, (bh - logo.height) // 2), logo)
        save(banner, f"mipmap-{density}", "app_banner.png")

    # Logo used inside the app (cinema header, splash screen).
    save(square(art, 512), "drawable-nodpi", "kempnys_logo.png")


if __name__ == "__main__":
    main()


