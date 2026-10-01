"""Extract decorative artwork only; coordinates are in the supplied original PNGs.
No text, fields, buttons, selection indicators, or device chrome are included.
"""
from pathlib import Path
from PIL import Image, ImageDraw
root = Path(__file__).resolve().parents[3]
src = root / 'docs/design'
out = root / 'android/app/src/main/res/drawable-nodpi'
regions = {
 'intro_art': ('ride-saathi-opening-screen.png', (35, 625, 957, 1260)),
 'language_art': ('ride-saathi-choose-your-language-screen.png', (35, 216, 957, 650)),
 'name_art': ('ride-saathi-user-name-enter-screen.png', (34, 200, 958, 595)),
 'home_art': ('ride-saathi-home-saving-screen.png', (31, 213, 914, 665)),
 'address_art': ('ride-saathi-save-address-screen.png', (31, 213, 914, 550)),
 'saved_places_art': ('saved-places-screen.png', (84, 181, 858, 532)),
 'brand_car': ('ride-saathi-choose-your-language-screen.png', (340, 133, 414, 190)),
}
for name, (filename, box) in regions.items():
 im = Image.open(src / filename).crop(box).convert('RGB')
 im.save(out / (name + '.webp'), quality=95)
# Only retain the two decorative sides of the booking scene. Everything in the
# central microphone and greeting regions is transparent and rendered by Compose.
im = Image.open(src / 'booking-screen.png').convert('RGBA')
mask = Image.new('L', im.size)
d = ImageDraw.Draw(mask)
d.polygon([(585,205),(950,205),(950,806),(686,716),(683,587),(625,498),(553,463),(553,386),(585,355)], fill=255)
d.polygon([(38,354),(102,370),(111,465),(222,488),(310,552),(303,664),(38,761)], fill=255)
im.putalpha(mask)
im.crop((38,205,953,806)).save(out / 'booking_art.webp', quality=95)
# Standalone decorative badge artwork, excluding the row label and chevron.
for name, box in {
 'place_home': (144, 830, 236, 921),
 'place_work': (144, 964, 236, 1055),
 'place_hospital': (144, 1098, 236, 1189),
 'place_temple': (144, 1232, 236, 1323),
}.items():
 Image.open(src / 'saved-places-screen.png').crop(box).save(out / (name + '.webp'), quality=95)
