"""Pair source PNG app content (without OS chrome) with actual Compose renders.
Run after ./gradlew :app:updateDebugScreenshotTest. Never feeds images into UI.
"""
from pathlib import Path
from PIL import Image, ImageDraw
root = Path(__file__).resolve().parents[3]
refs = root / 'android/app/build/outputs/screenshotTest-results/preview/debug/rendered/com/ridesaathi/app/preview/ReferenceScreensKt'
out = root / 'docs/design/comparisons'
out.mkdir(exist_ok=True)
for name, source, crop in [
 ('Intro','ride-saathi-opening-screen.png',(35,100,957,1560)),
 ('Language','ride-saathi-choose-your-language-screen.png',(35,100,957,1560)),
 ('Name','ride-saathi-user-name-enter-screen.png',(34,100,958,1105)),
 ('SaveHome','ride-saathi-home-saving-screen.png',(31,95,914,1650)),
 ('Address','ride-saathi-save-address-screen.png',(31,95,914,1650)),
 ('Settings','saved-places-screen.png',(84,90,858,1650)),
 ('Home','booking-screen.png',(38,100,953,1562)),
]:
 source = Image.open(root/'docs/design'/source).crop(crop).convert('RGB')
 rendered = Image.open(max(refs.glob(name+'Reference_*.png'), key=lambda p: p.stat().st_mtime)).convert('RGB')
 ims = [im.resize((390,round(im.height*390/im.width))) for im in [source,rendered]]
 result = Image.new('RGB',(800,max(im.height for im in ims)+28),'#e6eff6')
 d = ImageDraw.Draw(result)
 d.text((8,8),'REFERENCE — app content',fill='black')
 d.text((408,8),'COMPOSE — actual render',fill='black')
 for i,im in enumerate(ims): result.paste(im,(i*400,28))
 result.save(out/(name+'.png'))
