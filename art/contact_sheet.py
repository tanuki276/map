"""Combine Blender's GLB read-back previews with names and triangle budgets."""
from pathlib import Path
import json
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1]
names=['plank_bridge','reed_ferry','river_gauge','sluice_gate','waterwheel','landing_dock','route_post','handcart','fish_rack','canvas_tent','survey_shelter','mile_stone']
meta=json.loads((ROOT/'app/src/main/assets/models/manifest.json').read_text())['assets']
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',19)
small=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',15)
im=Image.new('RGB',(1536,1400),(18,28,31));d=ImageDraw.Draw(im)
d.text((24,17),'RIVERWORKS / 12 ORIGINAL GLB ASSETS',font=font,fill=(238,219,178))
d.text((24,48),'Actual exported GLBs reimported in Blender / shared original atlas / metre scale',font=small,fill=(164,191,184))
for i,name in enumerate(names):
    x=(i%4)*384;y=88+(i//4)*432
    im.paste(Image.open(ROOT/'art/previews'/(name+'.png')).convert('RGB'),(x,y))
    d.text((x+16,y+384),name.replace('_',' ').upper(),font=font,fill=(238,219,178))
    d.text((x+16,y+410),str(meta[name]['triangles'])+' triangles',font=small,fill=(164,191,184))
im.save(ROOT/'art/previews/riverworks-sheet.jpg',quality=90)
