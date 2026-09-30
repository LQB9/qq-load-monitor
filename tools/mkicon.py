# -*- coding: utf-8 -*-
"""生成 QQ 看门狗的图标：深色圆角方块 + 橙色心跳线（不依赖 PIL，手写 PNG）"""
import zlib, struct, os, math

OUT = r"D:\deepseek\qq-watchdog\res"
SIZES = {"mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96,
         "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192}


def render(n):
    px = [[(0, 0, 0, 0) for _ in range(n)] for _ in range(n)]
    r = n * 0.22                      # 圆角半径
    for y in range(n):
        for x in range(n):
            # 圆角方块裁剪
            cx = min(max(x, r), n - 1 - r)
            cy = min(max(y, r), n - 1 - r)
            d = math.hypot(x - cx, y - cy)
            if d > r:
                continue
            # 背景：深蓝黑渐变
            t = y / float(n - 1)
            R = int(28 + 18 * t)
            G = int(30 + 12 * t)
            B = int(40 + 22 * t)
            px[y][x] = (R, G, B, 255)

    def dot(fx, fy, rad, col):
        for y in range(n):
            for x in range(n):
                if math.hypot(x - fx, y - fy) <= rad:
                    px[y][x] = col

    # 心电线：一条折线，用密集圆点画出来（比画线简单且抗锯齿靠半径）
    pts = [(0.14, 0.52), (0.30, 0.52), (0.38, 0.30), (0.47, 0.74),
           (0.56, 0.44), (0.64, 0.52), (0.86, 0.52)]
    thick = n * 0.045
    for i in range(len(pts) - 1):
        x0, y0 = pts[i][0] * n, pts[i][1] * n
        x1, y1 = pts[i + 1][0] * n, pts[i + 1][1] * n
        steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 3) + 1
        for s in range(steps + 1):
            k = s / float(steps)
            dot(x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, thick,
                (255, 122, 69, 255))
    return px


def png(px, n, path):
    raw = b""
    for y in range(n):
        raw += b"\x00" + b"".join(struct.pack("BBBB", *px[y][x]) for x in range(n))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    hdr = struct.pack(">IIBBBBB", n, n, 8, 6, 0, 0, 0)
    out = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", hdr) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    d = os.path.dirname(path)
    if not os.path.isdir(d):
        os.makedirs(d)
    with open(path, "wb") as f:
        f.write(out)
    return len(out)


for folder, n in SIZES.items():
    sz = png(render(n), n, os.path.join(OUT, folder, "ic_launcher.png"))
    print("%-16s %3dpx  %6d bytes" % (folder, n, sz))
print("ICON-OK")
