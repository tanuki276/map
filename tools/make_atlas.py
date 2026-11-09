"""Original material swatches; no downloaded textures or third-party model data."""
from pathlib import Path
import numpy as np
from PIL import Image
rng=np.random.default_rng(276)
size=512;cell=size//4
palette=[(218,196,152),(171,78,49),(72,45,32),(151,105,59),(154,157,142),(71,83,91),(196,153,66),(68,119,62),(186,105,39),(57,132,119),(46,91,137),(117,48,75),(181,111,53),(232,213,164),(86,112,73),(160,222,201)]
y,x=np.mgrid[:cell,:cell];arr=np.zeros((size,size,3),dtype=np.uint8)
for i,base in enumerate(palette):
    noise=rng.normal(0,2.0,(cell,cell))
    if i in (2,3):noise+=5*np.sin(x*.36+np.sin(y*.07)*2)+2*np.sin(x*1.5+y*.02)
    elif i==4:noise+=np.where((y%32<2)|((x+(y//32%2)*32)%64<2),-26,0)+2*np.sin(y*.22+x*.15)
    elif i==1:noise+=5*np.cos(x*.2)+np.where(y%40<2,-18,0)
    elif i in (10,11):noise+=1.8*((x+y)%3==0)-1.5*(x%3==0)
    elif i==12:noise+=3*np.sin(y*.6+x*.2)
    elif i in (7,8,9,14):noise+=4*np.sin(x*.2)*np.cos(y*.24)
    elif i in (5,6):noise+=3*np.sin(y*.07)
    else:noise+=1.8*np.sin(x*.09+y*.12)
    patch=np.clip(np.array(base)[None,None,:]+noise[:,:,None],0,255).astype(np.uint8)
    row,col=divmod(i,4);arr[row*cell:(row+1)*cell,col*cell:(col+1)*cell]=patch
out=Path(__file__).resolve().parents[1]/'app/src/main/assets/textures/fable_atlas.png';out.parent.mkdir(parents=True,exist_ok=True)
Image.fromarray(arr).quantize(colors=192).save(out,optimize=True)
print(out, out.stat().st_size)
