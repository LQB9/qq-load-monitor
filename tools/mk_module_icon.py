"""Module icon: QQ Enhancer's pink/white style with a distinct load waveform."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "mod" / "res"
SIZE = 2048
TOP, BOTTOM = (255, 61, 127), (255, 163, 203)


def icon():
    gradient = Image.new("RGB", (1, SIZE))
    for y in range(SIZE):
        t = y / (SIZE - 1)
        gradient.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM)))
    gradient = gradient.resize((SIZE, SIZE))
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, SIZE - 1, SIZE - 1), radius=round(SIZE * .235), fill=255)
    image = Image.new("RGBA", (SIZE, SIZE))
    image.paste(gradient, (0, 0), mask)
    symbol = Image.new("RGBA", (SIZE, SIZE))
    draw = ImageDraw.Draw(symbol)
    draw.rounded_rectangle(tuple(round(v * SIZE) for v in (.225, .225, .775, .775)),
                           radius=round(SIZE * .10), outline="white", width=round(SIZE * .055))
    points = [(x * SIZE, y * SIZE) for x, y in [(.325, .52), (.40, .52), (.475, .385),
              (.53, .64), (.595, .48), (.675, .48)]]
    width = round(SIZE * .043)
    draw.line(points, fill="white", width=width, joint="curve")
    for x, y in points:
        draw.ellipse((x - width / 2, y - width / 2, x + width / 2, y + width / 2), fill="white")
    symbol = symbol.rotate(14, resample=Image.Resampling.BICUBIC)
    image.alpha_composite(symbol)
    return image


if __name__ == "__main__":
    master = icon()
    for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        target = ROOT / ("mipmap-" + density)
        target.mkdir(parents=True, exist_ok=True)
        master.resize((size, size), Image.Resampling.LANCZOS).save(target / "ic_launcher.png")
    print("Module launcher icons generated: 5 densities")
