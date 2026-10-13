#!/usr/bin/env python3
"""Generates the launcher icon, adaptive icon foreground and TV banner resources
from the source artwork (appIcon.png) for Kempny Kino.

The artwork may either be transparent around the motif or sit on an opaque, flat
background. In the latter case the background colour is sampled from the corners and
reused for every generated canvas so no visible edges appear."""

import os
import re
from PIL import Image, ImageChops

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, "appIcon.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")
COLORS_XML = os.path.join(RES, "values", "cinema_colors.xml")

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

# Relative padding kept around the motif after trimming.
MOTIF_PADDING = 0.06


def background_color(image):
    """Returns the flat background colour (corner sample) or None if transparent."""
    corners = [(0, 0), (image.width - 1, 0), (0, image.height - 1), (image.width - 1, image.height - 1)]
    samples = [image.getpixel(c) for c in corners]
    if any(s[3] < 250 for s in samples):
        return None
    return tuple(sum(s[i] for s in samples) // len(samples) for i in range(3)) + (255,)


def trim(image, background):
    """Crops the margin around the motif (transparent or flat background)."""
    if background is None:
        bbox = image.getchannel("A").getbbox()
    else:
        diff = ImageChops.difference(image.convert("RGB"), Image.new("RGB", image.size, background[:3]))
        # Ignore compression noise / soft gradients in the background.
        mask = diff.convert("L").point(lambda v: 255 if v > 28 else 0)
        bbox = mask.getbbox()
    if not bbox:
        return image

    pad = int(max(bbox[2] - bbox[0], bbox[3] - bbox[1]) * MOTIF_PADDING)
    bbox = (
        max(bbox[0] - pad, 0),
        max(bbox[1] - pad, 0),
        min(bbox[2] + pad, image.width),
        min(bbox[3] + pad, image.height),
    )
    return image.crop(bbox)


def save(image, folder, name):
    target = os.path.join(RES, folder)
    os.makedirs(target, exist_ok=True)
    path = os.path.join(target, name)
    image.save(path, "PNG", optimize=True)
    print("wrote", os.path.relpath(path, ROOT), image.size)


def square(image, size, fill=(0, 0, 0, 0)):
    """Fits the artwork into a square canvas without distortion."""
    canvas = Image.new("RGBA", (size, size), fill)
    art = image.copy()
    art.thumbnail((size, size), Image.LANCZOS)
    canvas.paste(art, ((size - art.width) // 2, (size - art.height) // 2), art)
    return canvas


def configured_background_color():
    """Reads app_icon_background_color so transparent artwork matches the adaptive icon."""
    with open(COLORS_XML, encoding="utf-8") as f:
        match = re.search(r'<color name="app_icon_background_color">#(?:[0-9A-Fa-f]{2})?([0-9A-Fa-f]{6})</color>', f.read())
    if not match:
        return None
    value = match.group(1)
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def update_background_color(color):
    """Keeps the adaptive icon background in sync with the artwork background."""
    hex_color = "#FF%02X%02X%02X" % color[:3]
    with open(COLORS_XML, encoding="utf-8") as f:
        xml = f.read()
    updated = re.sub(
        r'(<color name="app_icon_background_color">)[^<]*(</color>)',
        r"\g<1>" + hex_color + r"\g<2>",
        xml,
    )
    if updated != xml:
        with open(COLORS_XML, "w", encoding="utf-8") as f:
            f.write(updated)
        print("updated app_icon_background_color ->", hex_color)


def main():
    source = Image.open(SRC).convert("RGBA")
    bg = background_color(source)
    fill = bg or configured_background_color() or (10, 13, 18, 255)
    art = trim(source, bg)
    print("source", source.size, "-> trimmed", art.size, "background", fill)

    if bg is not None:
        update_background_color(bg)

    for density, scale in DENSITIES.items():
        # Legacy launcher icon: full artwork including its own background.
        size = int(ICON_BASE * scale)
        save(square(art, size, fill), f"mipmap-{density}", "app_icon.png")

        # Adaptive icon foreground: the motif is placed inside the 72dp safe zone of the
        # 108dp canvas so system masks never clip the clapperboard or the wordmark.
        canvas_size = int(FOREGROUND_BASE * scale)
        safe = int(canvas_size * 72 / 108)
        foreground = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        inner = square(art, safe)
        offset = (canvas_size - safe) // 2
        foreground.paste(inner, (offset, offset), inner)
        save(foreground, f"mipmap-{density}", "app_icon_fg.png")

        # Android TV banner: motif centred on the artwork background.
        bw, bh = int(BANNER_BASE[0] * scale), int(BANNER_BASE[1] * scale)
        banner = Image.new("RGBA", (bw, bh), fill)
        logo = square(art, int(bh * 0.92))
        banner.paste(logo, ((bw - logo.width) // 2, (bh - logo.height) // 2), logo)
        save(banner, f"mipmap-{density}", "app_banner.png")

    # Logo used inside the app (splash screen).
    save(square(art, 512, fill), "drawable-nodpi", "kempnys_logo.png")

    # Transparent header logo for the home screen: light artwork for the dark theme and an
    # ink-coloured variant (accent colour preserved) for the light theme.
    header = square(art, 512)
    save(header, "drawable-nodpi", "kempnys_logo_header.png")
    save(recolor_neutrals(header, HEADER_INK_LIGHT_THEME), "drawable-nodpi", "kempnys_logo_header_light.png")


# Text colour of the light cinema palette (CinemaLightPalette.text).
HEADER_INK_LIGHT_THEME = (14, 22, 32)


def recolor_neutrals(image, ink):
    """Replaces the light, unsaturated parts of the artwork with [ink], keeping coloured
    accents (the orange play button and wordmark) and the alpha channel untouched."""
    out = image.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            if max(r, g, b) - min(r, g, b) < 60:
                pixels[x, y] = ink + (a,)
    return out


if __name__ == "__main__":
    main()

