"""可复现地生成大厅字体子集与原创试听音；已提交产物，日常构建不需要 Python。"""
from pathlib import Path
import hashlib
import io
import math
import struct
import wave

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / '.cache/font-source/NotoSansSC-variable.ttf'
EXPECTED = 'a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da'
if not SOURCE.exists() or hashlib.sha256(SOURCE.read_bytes()).hexdigest() != EXPECTED:
    raise SystemExit('Expected verified NotoSansSC source; see opensource_application/NotoSansSC/使用说明.md')

characters = set(chr(code) for code in range(32, 127))
for module in ('client', 'game-tank', 'game-fight'):
    for source in (ROOT / f'{module}/src/main/java').rglob('*.java'):
        characters.update(char for char in source.read_text(encoding='utf-8') if char.isprintable())
characters.discard('\ufeff')
text = ''.join(sorted(characters))
font = instantiateVariableFont(TTFont(SOURCE), {'wght': 450}, inplace=True)
font.recalcTimestamp = False
options = subset.Options()
options.name_IDs = ['*']
subsetter = subset.Subsetter(options=options)
subsetter.populate(text=text)
subsetter.subset(font)
missing = sorted(set(map(ord, text)) - set(font.getBestCmap()))
if missing:
    raise SystemExit(f'Source font missing codepoints: {missing}')
for entry in font['name'].names:
    names = {1: 'GameCore UI Sans', 2: 'Regular', 3: 'GameCoreUISans-Regular',
             4: 'GameCore UI Sans Regular', 6: 'GameCoreUISans-Regular',
             16: 'GameCore UI Sans', 17: 'Regular'}
    if entry.nameID in names:
        entry.string = names[entry.nameID].encode(entry.getEncoding())
assets = ROOT / 'assets'
(assets / 'fonts').mkdir(parents=True, exist_ok=True)
# 先完成临时产物再替换，避免写入中断破坏可运行的旧字体。
# 按内容命名，避免 Windows 上正在运行的客户端映射旧字体时无法扩展；相同内容不重写。
font_temporary = assets / 'fonts/GameCoreUISans-subset.tmp.ttf'
font.save(font_temporary)
font_hash = hashlib.sha256(font_temporary.read_bytes()).hexdigest()
font_target = assets / 'fonts' / f'GameCoreUISans-{font_hash[:16]}.ttf'
if font_target.exists():
    if hashlib.sha256(font_target.read_bytes()).hexdigest() != font_hash:
        raise SystemExit('Font name hash collision; refusing to replace existing asset')
    font_temporary.unlink()
else:
    font_temporary.replace(font_target)
characters_target = assets / 'fonts/characters.txt'
characters_temporary = characters_target.with_suffix('.tmp.txt')
characters_temporary.write_text(text, encoding='utf-8')
characters_temporary.replace(characters_target)
font_manifest = assets / 'fonts/font-file.txt'
manifest_temporary = font_manifest.with_suffix('.tmp.txt')
manifest_temporary.write_text(font_target.name, encoding='utf-8')
manifest_temporary.replace(font_manifest)
# 格斗展示字沿用同源 OFL，改为 900 字重并独立改名；正文清单和现有字号不受影响。
display = instantiateVariableFont(TTFont(SOURCE), {'wght': 900}, inplace=True)
display.recalcTimestamp = False
display_options = subset.Options()
display_options.name_IDs = ['*']
display_subsetter = subset.Subsetter(options=display_options)
display_subsetter.populate(text=text)
display_subsetter.subset(display)
display_names = {1: 'GameCore Display', 2: 'Heavy', 3: 'GameCoreDisplay-Heavy',
                 4: 'GameCore Display Heavy', 6: 'GameCoreDisplay-Heavy', 16: 'GameCore Display', 17: 'Heavy'}
for entry in display['name'].names:
    if entry.nameID in display_names:
        entry.string = display_names[entry.nameID].encode(entry.getEncoding())
display_temporary = assets / 'fonts/GameCoreDisplay.tmp.ttf'
display.save(display_temporary)
display_hash = hashlib.sha256(display_temporary.read_bytes()).hexdigest()
display_target = assets / 'fonts' / f'GameCoreDisplay-{display_hash[:16]}.ttf'
if display_target.exists():
    if hashlib.sha256(display_target.read_bytes()).hexdigest() != display_hash:
        raise SystemExit('Display font hash collision')
    display_temporary.unlink()
else:
    display_temporary.replace(display_target)
display_manifest = assets / 'fonts/display-font-file.txt'
display_manifest_temporary = display_manifest.with_suffix('.tmp.txt')
display_manifest_temporary.write_text(display_target.name, encoding='utf-8')
display_manifest_temporary.replace(display_manifest)
(assets / 'audio').mkdir(parents=True, exist_ok=True)
rate = 22050
audio_buffer = io.BytesIO()
with wave.open(audio_buffer, 'wb') as output:
    output.setparams((1, 2, rate, 0, 'NONE', 'not compressed'))
    for index in range(int(rate * .28)):
        time = index / rate
        envelope = min(1, time / .012) * max(0, 1 - time / .28) ** 2
        sample = .25 * envelope * (math.sin(2 * math.pi * 660 * time) + .4 * math.sin(2 * math.pi * 990 * time))
        output.writeframesraw(struct.pack('<h', round(sample * 32767)))
audio_target = assets / 'audio/preview.wav'
if not audio_target.exists() or audio_target.read_bytes() != audio_buffer.getvalue():
    audio_temporary = audio_target.with_suffix('.tmp.wav')
    audio_temporary.write_bytes(audio_buffer.getvalue())
    audio_temporary.replace(audio_target)
print(f'Generated {len(characters)} glyphs, {font_target.stat().st_size} font bytes and original WAV')
