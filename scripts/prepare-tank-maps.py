"""用标准库生成原创 TMX 示例和几何图块；运行时无需 Python，可在 Tiled 中继续编辑。"""
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'assets/maps/tank'
ROOT.mkdir(parents=True, exist_ok=True)
WIDTH, HEIGHT, TILE = 24, 18, 32


def palette():
    rows = []
    colors = [(18, 34, 47), (71, 89, 108), (31, 67, 76)]
    for y in range(TILE):
        row = bytearray([0])
        for x in range(TILE * 3):
            tile, local = divmod(x, TILE)
            color = colors[tile]
            if local in (0, 31) or y in (0, 31):
                color = tuple(max(0, c - 12) for c in color)
            elif tile == 1 and (y == 16 or (local == 16 and y < 16)):
                color = (42, 57, 73)
            elif tile == 1 and y == 3:
                color = (112, 129, 144)
            elif tile == 2 and (local in (14, 15, 16, 17) or y in (14, 15, 16, 17)):
                color = (63, 119, 124)
            row.extend((*color, 255))
        rows.append(bytes(row))

    def chunk(kind, data):
        return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data))

    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!2I5B', TILE * 3, TILE, 8, 6, 0, 0, 0))
    data += chunk(b'IDAT', zlib.compress(b''.join(rows), 9)) + chunk(b'IEND', b'')
    (ROOT / 'arena-tiles.png').write_bytes(data)


def arena(name, interior):
    walls = {(x, y) for x in range(WIDTH) for y in range(HEIGHT)
             if x in (0, WIDTH - 1) or y in (0, HEIGHT - 1)} | set(interior)

    def layer(index, label, kind, visible=True):
        rows = []
        for y in range(HEIGHT - 1, -1, -1):
            values = []
            for x in range(WIDTH):
                value = 1 if kind == 'floor' else 2 if (x, y) in walls and kind in ('walls', 'collision') else 0
                if kind == 'decorations' and x in (11, 12) and y in (8, 9):
                    value = 3
                values.append(str(value))
            rows.append(','.join(values))
        csv = ',\n'.join(rows)
        return f'<layer id="{index}" name="{label}" width="{WIDTH}" height="{HEIGHT}" visible="{int(visible)}"><data encoding="csv">\n{csv}\n</data></layer>'

    xml = ['<?xml version="1.0" encoding="UTF-8"?>',
           '<map version="1.10" orientation="orthogonal" renderorder="right-down" width="24" height="18" tilewidth="32" tileheight="32" infinite="0" nextlayerid="7" nextobjectid="4">',
           f'<properties><property name="arenaId" value="{name}"/></properties>',
           '<tileset firstgid="1" name="arena" tilewidth="32" tileheight="32" tilecount="3" columns="3"><image source="arena-tiles.png" width="96" height="32"/></tileset>']
    for i, kind in enumerate(('floor', 'walls', 'decorations', 'collision'), 1):
        xml.append(layer(i, kind, kind, kind != 'collision'))
    xml.append('<objectgroup id="5" name="spawns" visible="0">')
    for i, x, y, heading in [(1, 2.5, 2.5, 0), (2, 21.5, 15.5, 180)]:
        xml.append(f'<object id="{i}" name="p{i}" x="{x * TILE}" y="{(HEIGHT - y) * TILE}"><properties><property name="heading" type="float" value="{heading}"/></properties><point/></object>')
    xml.extend(['</objectgroup>', '<objectgroup id="6" name="gameplay" visible="0"><object id="3" name="central-repair" type="repair" x="352" y="256" width="64" height="64"/></objectgroup>', '</map>'])
    (ROOT / f'{name}.tmx').write_text('\n'.join(xml) + '\n', encoding='utf-8')


palette()
crossfire = {(6, y) for y in range(3, 8)} | {(17, y) for y in range(10, 15)}
crossfire |= {(x, 12) for x in range(4, 11)} | {(x, 5) for x in range(13, 20)}
crossfire |= {(11, 4), (12, 13), (17, 7), (6, 10)}
ruins = {(x, y) for x in (5, 18) for y in range(4, 14) if y not in (8, 9)}
ruins |= {(x, y) for y in (5, 12) for x in range(8, 16) if x not in (11, 12)}
ruins |= {(9, 8), (14, 9), (3, 12), (20, 5)}
arena('crossfire', crossfire)
arena('ruins', ruins)
print('Generated two original 24x18 TMX arenas and 96x32 palette')
