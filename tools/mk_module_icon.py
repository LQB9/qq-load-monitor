from pathlib import Path
from xml.sax.saxutils import escape
import argparse
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "mod" / "res"
TOP = '#19303B'
BOTTOM = '#0B141C'
SHAPES = [{'path': [('M', 43, 28), ('L', 43, 34)], 'stroke': '#57DEC3', 'width': 3.3}, {'path': [('M', 43, 74), ('L', 43, 80)], 'stroke': '#57DEC3', 'width': 3.3}, {'path': [('M', 28, 43), ('L', 34, 43)], 'stroke': '#57DEC3', 'width': 3.3}, {'path': [('M', 74, 43), ('L', 80, 43)], 'stroke': '#57DEC3', 'width': 3.3}, {'path': [('M', 54, 28), ('L', 54, 34)], 'stroke': '#57DEC3', 'width': 3.3}, {'path': [('M', 54, 74), ('L', 54, 80)], 'stroke': '#57DEC3', 'width': 3.3}, {'path': [('M', 28, 54), ('L', 34, 54)], 'stroke': '#57DEC3', 'width': 3.3}, {'path': [('M', 74, 54), ('L', 80, 54)], 'stroke': '#57DEC3', 'width': 3.3}, {'path': [('M', 65, 28), ('L', 65, 34)], 'stroke': '#57DEC3', 'width': 3.3}, {'path': [('M', 65, 74), ('L', 65, 80)], 'stroke': '#57DEC3', 'width': 3.3}, {'path': [('M', 28, 65), ('L', 34, 65)], 'stroke': '#57DEC3', 'width': 3.3}, {'path': [('M', 74, 65), ('L', 80, 65)], 'stroke': '#57DEC3', 'width': 3.3}, {'path': [('M', 42, 34), ('L', 66, 34), ('C', 70.4, 34, 74, 37.6, 74, 42), ('L', 74, 66), ('C', 74, 70.4, 70.4, 74, 66, 74), ('L', 42, 74), ('C', 37.6, 74, 34, 70.4, 34, 66), ('L', 34, 42), ('C', 34, 37.6, 37.6, 34, 42, 34), ('Z',)], 'fill': '#203941', 'stroke': '#57DEC3', 'width': 3.3}, {'path': [('M', 41, 58), ('L', 47, 58), ('L', 53, 46), ('L', 59, 64), ('L', 64, 53), ('L', 68, 53)], 'stroke': '#D1FF74', 'width': 3.8}]
MONO = [{'path': [('M', 43, 28), ('L', 43, 34)], 'stroke': '#FFFFFF', 'width': 3.3}, {'path': [('M', 43, 74), ('L', 43, 80)], 'stroke': '#FFFFFF', 'width': 3.3}, {'path': [('M', 28, 43), ('L', 34, 43)], 'stroke': '#FFFFFF', 'width': 3.3}, {'path': [('M', 74, 43), ('L', 80, 43)], 'stroke': '#FFFFFF', 'width': 3.3}, {'path': [('M', 54, 28), ('L', 54, 34)], 'stroke': '#FFFFFF', 'width': 3.3}, {'path': [('M', 54, 74), ('L', 54, 80)], 'stroke': '#FFFFFF', 'width': 3.3}, {'path': [('M', 28, 54), ('L', 34, 54)], 'stroke': '#FFFFFF', 'width': 3.3}, {'path': [('M', 74, 54), ('L', 80, 54)], 'stroke': '#FFFFFF', 'width': 3.3}, {'path': [('M', 65, 28), ('L', 65, 34)], 'stroke': '#FFFFFF', 'width': 3.3}, {'path': [('M', 65, 74), ('L', 65, 80)], 'stroke': '#FFFFFF', 'width': 3.3}, {'path': [('M', 28, 65), ('L', 34, 65)], 'stroke': '#FFFFFF', 'width': 3.3}, {'path': [('M', 74, 65), ('L', 80, 65)], 'stroke': '#FFFFFF', 'width': 3.3}, {'path': [('M', 42, 34), ('L', 66, 34), ('C', 70.4, 34, 74, 37.6, 74, 42), ('L', 74, 66), ('C', 74, 70.4, 70.4, 74, 66, 74), ('L', 42, 74), ('C', 37.6, 74, 34, 70.4, 34, 66), ('L', 34, 42), ('C', 34, 37.6, 37.6, 34, 42, 34), ('Z',)], 'stroke': '#FFFFFF', 'width': 3.3}, {'path': [('M', 41, 58), ('L', 47, 58), ('L', 53, 46), ('L', 59, 64), ('L', 64, 53), ('L', 68, 53)], 'stroke': '#FFFFFF', 'width': 3.8}]
DENSITIES = [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]

def path_text(commands):
    return ' '.join(op + ' ' + ' '.join(f'{n:g}' for n in nums) for op, *nums in commands)

def sampled(commands):
    result = []
    last = start = None
    for op, *v in commands:
        if op == 'M':
            last = start = (v[0], v[1])
            result.append(last)
        elif op == 'L':
            last = (v[0], v[1])
            result.append(last)
        elif op == 'C':
            x0, y0 = last
            x1, y1, x2, y2, x3, y3 = v
            for i in range(1, 49):
                t = i / 48
                u = 1 - t
                result.append((u**3*x0 + 3*u*u*t*x1 + 3*u*t*t*x2 + t**3*x3,
                               u**3*y0 + 3*u*u*t*y1 + 3*u*t*t*y2 + t**3*y3))
            last = (x3, y3)
        elif op == 'Z':
            result.append(start)
            last = start
        else:
            raise ValueError(op)
    return result

def rgb(value):
    return tuple(int(value[i:i+2], 16) for i in (1, 3, 5))

def render(size=1024, mask='squircle', monochrome=False):
    scale = 12
    side = 108 * scale
    if monochrome:
        canvas = Image.new('RGBA', (side, side))
    else:
        strip = Image.new('RGB', (1, side))
        a, b = rgb(TOP), rgb(BOTTOM)
        strip.putdata([tuple(round(x + (y-x)*row/(side-1)) for x,y in zip(a,b)) for row in range(side)])
        canvas = strip.resize((side, side)).convert('RGBA')
    draw = ImageDraw.Draw(canvas)
    for shape in (MONO if monochrome else SHAPES):
        points = [(x*scale, y*scale) for x,y in sampled(shape['path'])]
        if shape.get('fill'):
            draw.polygon(points, fill=shape['fill'])
        if shape.get('stroke'):
            width = round(shape.get('width', 3.5) * scale)
            draw.line(points, fill=shape['stroke'], width=width, joint='curve')
            if shape.get('round', True):
                # Android and SVG round joins and endpoints use the same radius.
                r = width / 2
                for x,y in points:
                    draw.ellipse((x-r,y-r,x+r,y+r), fill=shape['stroke'])
    # AdaptiveIconDrawable expands both 108dp layers by 25% on each side.
    # Its visible viewport is the central 72dp, not the entire foreground layer.
    canvas = canvas.crop((18*scale,18*scale,90*scale,90*scale))
    side = 72 * scale
    if mask != 'square':
        alpha = Image.new('L', (side,side))
        md = ImageDraw.Draw(alpha)
        if mask == 'circle':
            md.ellipse((0,0,side-1,side-1), fill=255)
        else:
            md.rounded_rectangle((0,0,side-1,side-1), radius=16*scale, fill=255)
        if monochrome:
            from PIL import ImageChops
            alpha = ImageChops.multiply(alpha, canvas.getchannel('A'))
        canvas.putalpha(alpha)
    return canvas.resize((size,size), Image.Resampling.LANCZOS)

def vector(shapes):
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             '    android:width="108dp" android:height="108dp"',
             '    android:viewportWidth="108" android:viewportHeight="108">']
    for shape in shapes:
        attrs = [f'android:pathData="{escape(path_text(shape["path"]))}"',
                 f'android:fillColor="{shape.get("fill") or "#00000000"}"']
        if shape.get('stroke'):
            attrs += [f'android:strokeColor="{shape["stroke"]}"',
                      f'android:strokeWidth="{shape.get("width", 3.5)}"',
                      'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
        lines.append('    <path ' + '\n        '.join(attrs) + ' />')
    return '\n'.join(lines + ['</vector>', ''])

def svg():
    parts = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72">',
             f'<defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop stop-color="{TOP}"/><stop offset="1" stop-color="{BOTTOM}"/></linearGradient><clipPath id="mask"><rect x="18" y="18" width="72" height="72" rx="16"/></clipPath></defs>',
             '<g clip-path="url(#mask)"><rect width="108" height="108" fill="url(#bg)"/>']
    for shape in SHAPES:
        attrs = [f'd="{path_text(shape["path"])}"', f'fill="{shape.get("fill") or "none"}"']
        if shape.get('stroke'):
            attrs += [f'stroke="{shape["stroke"]}"', f'stroke-width="{shape.get("width",3.5)}"',
                      'stroke-linecap="round"', 'stroke-linejoin="round"']
        parts.append('<path ' + ' '.join(attrs) + '/>')
    return '\n'.join(parts + ['</g></svg>', ''])

def generate(preview):
    drawable = ROOT / 'drawable'
    drawable.mkdir(parents=True, exist_ok=True)
    (drawable / 'ic_launcher_foreground.xml').write_text(vector(SHAPES), encoding='utf-8')
    (drawable / 'ic_launcher_monochrome.xml').write_text(vector(MONO), encoding='utf-8')
    background = ('<?xml version="1.0" encoding="utf-8"?>\n'
                  '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
                  f'    <gradient android:angle="270" android:startColor="{TOP}" android:endColor="{BOTTOM}" />\n'
                  '</shape>\n')
    (drawable / 'ic_launcher_background.xml').write_text(background, encoding='utf-8')
    for version in [26,33]:
        folder = ROOT / f'mipmap-anydpi-v{version}'
        folder.mkdir(parents=True, exist_ok=True)
        text = ('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@drawable/ic_launcher_background" />\n'
                '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n')
        if version == 33:
            text += '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
        (folder / 'ic_launcher.xml').write_text(text + '</adaptive-icon>\n', encoding='utf-8')
    for density,size in DENSITIES:
        folder = ROOT / ('mipmap-' + density)
        folder.mkdir(parents=True, exist_ok=True)
        render(size).save(folder / 'ic_launcher.png')
    if preview:
        preview.mkdir(parents=True, exist_ok=True)
        (preview / 'master.svg').write_text(svg(), encoding='utf-8')
        for mask in ['squircle','circle','square']:
            render(1024,mask).save(preview / (mask + '.png'))
        render(1024,monochrome=True).save(preview / 'monochrome.png')
    print(f'Generated native adaptive/monochrome vectors and 5 PNG densities in {ROOT}')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--preview', type=Path)
    generate(parser.parse_args().preview)
