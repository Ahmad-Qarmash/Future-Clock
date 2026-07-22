"""Render adaptive and legacy launcher assets from the approved source artwork."""

from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "design" / "brand" / "future-clock-icon-source.png"


def normalized_source():
    image = Image.open(SOURCE).convert("RGB")
    black = Image.new("RGB", image.size, (0, 0, 0))
    difference = ImageChops.difference(image, black).convert("L")
    content = difference.point(lambda value: 255 if value > 12 else 0).getbbox()
    if content is None:
        raise ValueError("The launcher source artwork has no visible content")

    left, top, right, bottom = content
    width, height = right - left, bottom - top
    side = max(width, height)
    center_x = (left + right) / 2
    center_y = (top + bottom) / 2
    # Keep a small black breathing area around the outer glow and rounded frame.
    side = min(int(side * 1.035), min(image.size))
    left = max(0, round(center_x - side / 2))
    top = max(0, round(center_y - side / 2))
    right = min(image.width, left + side)
    bottom = min(image.height, top + side)
    return image.crop((left, top, right, bottom))


def render(source, size, round_icon=False):
    icon = source.resize((size, size), Image.Resampling.LANCZOS).convert("RGBA")
    if not round_icon:
        return icon
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    icon.putalpha(mask)
    return icon


def main():
    source = normalized_source()
    artwork = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "ic_launcher_artwork.png"
    artwork.parent.mkdir(parents=True, exist_ok=True)
    render(source, 432).save(artwork)

    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for density, size in densities.items():
        out_dir = ROOT / "app" / "src" / "main" / "res" / f"mipmap-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        render(source, size).save(out_dir / "ic_launcher.png")
        render(source, size, round_icon=True).save(out_dir / "ic_launcher_round.png")


if __name__ == "__main__":
    main()
