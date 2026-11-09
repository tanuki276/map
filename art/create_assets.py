"""Fablewind original mesh library. Run: blender -b -t 2 --python art/create_assets.py
All geometry, atlas UVs and material choices are authored here in meters. Blender Z-up;
glTF export is Y-up. The complete editable source is saved before delivery export.
"""
import bpy, bmesh, math, random, json, os
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'app/src/main/assets/models';OUT.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
random.seed(276)
image=bpy.data.images.load(str(ROOT/'app/src/main/assets/textures/fable_atlas.png'))
image.name='Fablewind_Original_Material_Atlas';image.pack()
names=['plaster','terracotta','timber','oak','limestone','iron','brass','foliage','amber','verdigris','blue_cloth','wine_cloth','russet_fur','cream','moss','starlight']
mats=[]
for i,name in enumerate(names):
    m=bpy.data.materials.new(name);m.use_nodes=True;p=m.node_tree.nodes.get('Principled BSDF')
    tex=m.node_tree.nodes.new('ShaderNodeTexImage');tex.image=image
    m.node_tree.links.new(tex.outputs['Color'],p.inputs['Base Color']);p.inputs['Roughness'].default_value=.82 if i not in (5,6) else .35
    p.inputs['Metallic'].default_value=.75 if i in (5,6) else 0
    if i==15:p.inputs['Emission Color'].default_value=(.22,.6,.48,1);p.inputs['Emission Strength'].default_value=.9
    mats.append(m)
firemat=bpy.data.materials.new('fire_emission');firemat.use_nodes=True
fp=firemat.node_tree.nodes.get('Principled BSDF');ft=firemat.node_tree.nodes.new('ShaderNodeTexImage');ft.image=image
firemat.node_tree.links.new(ft.outputs['Color'],fp.inputs['Base Color'])
fp.inputs['Emission Color'].default_value=(1,.3,.02,1);fp.inputs['Emission Strength'].default_value=1
mats.append(firemat)
class Mesh:
    def __init__(self,name):self.name=name;self.v=[];self.faces=[];self.mi=[];self.uv=[];self.smooth=[]
    def face(self,points,mat,uv=None,smooth=False):
        first=len(self.v);self.v+=points;self.faces.append(tuple(range(first,first+len(points))));self.mi.append(mat);self.smooth.append(smooth)
        if uv is None:uv=[(0,0),(1,0),(1,1),(0,1)][:len(points)]
        self.uv.append(uv)
    def box(self,c,s,mat=2,rot=0):
        x,y,z=c;a,b,h=[q/2 for q in s];ca,sa=math.cos(rot),math.sin(rot)
        vs=[(x+xx*ca-yy*sa,y+xx*sa+yy*ca,z+zz) for xx,yy,zz in [(-a,-b,-h),(a,-b,-h),(a,b,-h),(-a,b,-h),(-a,-b,h),(a,-b,h),(a,b,h),(-a,b,h)]]
        for f in [(0,3,2,1),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7),(4,5,6,7)]:self.face([vs[k] for k in f],mat)
    def ellipsoid(self,c,s,mat=7,seg=16,rings=9):
        x,y,z=c;rx,ry,rz=s
        def pt(a,b):return (x+rx*math.sin(b)*math.cos(a),y+ry*math.sin(b)*math.sin(a),z+rz*math.cos(b))
        for j in range(rings):
            b0=math.pi*j/rings;b1=math.pi*(j+1)/rings
            for i in range(seg):
                a0=math.tau*i/seg;a1=math.tau*(i+1)/seg
                pts=[pt(a0,b0),pt(a0,b1),pt(a1,b1),pt(a1,b0)]
                uv=[(i/seg,j/rings),(i/seg,(j+1)/rings),((i+1)/seg,(j+1)/rings),((i+1)/seg,j/rings)]
                if j==0:pts=pts[1:];uv=uv[1:]
                elif j==rings-1:pts=[pts[0],pts[1],pts[3]];uv=[uv[0],uv[1],uv[3]]
                self.face(pts,mat,uv,True)
    def tube(self,a,b,r,mat=2,segments=10,r2=None):
        a,b=Vector(a),Vector(b);axis=(b-a).normalized();u=axis.cross(Vector((0,0,1)))
        if u.length<.01:u=axis.cross(Vector((0,1,0)))
        u.normalize();v=axis.cross(u);r2=r if r2 is None else r2
        ca=[];cb=[]
        for i in range(segments):
            off=u*math.cos(i*math.tau/segments)+v*math.sin(i*math.tau/segments)
            ca.append(tuple(a+off*r));cb.append(tuple(b+off*r2))
        self.face(list(reversed(ca)),mat,[(.5+.5*math.cos(i*math.tau/segments),.5+.5*math.sin(i*math.tau/segments)) for i in reversed(range(segments))])
        self.face(cb,mat,[(.5+.5*math.cos(i*math.tau/segments),.5+.5*math.sin(i*math.tau/segments)) for i in range(segments)])
        for i in range(segments):
            j=(i+1)%segments;self.face([ca[i],ca[j],cb[j],cb[i]],mat,[(i/segments,0),(j/segments,0),(j/segments,1),(i/segments,1)],True)
    def lathe(self,c,profile,mat=4,segments=24):
        x,y,z=c
        for j in range(len(profile)-1):
            r0,z0=profile[j];r1,z1=profile[j+1]
            for i in range(segments):
                a=i*math.tau/segments;b=(i+1)*math.tau/segments
                self.face([(x+r0*math.cos(a),y+r0*math.sin(a),z+z0),(x+r0*math.cos(b),y+r0*math.sin(b),z+z0),(x+r1*math.cos(b),y+r1*math.sin(b),z+z1),(x+r1*math.cos(a),y+r1*math.sin(a),z+z1)],mat,[(i/segments,j/(len(profile)-1)),((i+1)/segments,j/(len(profile)-1)),((i+1)/segments,(j+1)/(len(profile)-1)),(i/segments,(j+1)/(len(profile)-1))],True)
    def arch(self,c,width,height,depth,mat=4):
        x,y,z=c;r=width/2;th=.16
        self.box((x-r-th/2,y,z+(height-r)/2),(th,depth,height-r),mat)
        self.box((x+r+th/2,y,z+(height-r)/2),(th,depth,height-r),mat)
        for i in range(12):
            a=math.pi*i/12;b=math.pi*(i+1)/12
            pts=[]
            for yy in (y-depth/2,y+depth/2):
                pts.extend([(x+rr*math.cos(t),yy,z+height-r+rr*math.sin(t)) for rr,t in [(r,a),(r,b),(r+th,b),(r+th,a)]])
            for face in [(0,1,2,3),(4,7,6,5),(0,4,5,1),(3,2,6,7)]:self.face([pts[k] for k in face],mat)
    def object(self):
        mesh=bpy.data.meshes.new(self.name);mesh.from_pydata(self.v,[],self.faces);mesh.update()
        obj=bpy.data.objects.new(self.name,mesh);bpy.context.collection.objects.link(obj)
        for m in mats:mesh.materials.append(m)
        uv=mesh.uv_layers.new(name='AtlasUV')
        for i,poly in enumerate(mesh.polygons):
            poly.material_index=self.mi[i];poly.use_smooth=self.smooth[i];row,col=divmod(self.mi[i] if self.mi[i]<16 else 8,4)
            for k,loop in enumerate(poly.loop_indices):
                u,v=self.uv[i][k];uv.data[loop].uv=((col+.035+u*.93)/4,(3-row+.035+v*.93)/4)
        bm=bmesh.new();bm.from_mesh(mesh);bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=.000001);bm.to_mesh(mesh);bm.free();mesh.update()
        return obj
models={}
def model(name,fn):
    m=Mesh(name);fn(m);obj=m.object();models[name]=[obj];return obj

def window(m,x,y,z,w=.58,h=.72):
    m.box((x,y,z),(w,.07,h),2);m.box((x,y-.045,z),(w-.1,.03,h-.1),15)
    m.box((x,y-.075,z),(.035,.035,h-.09),6);m.box((x,y-.075,z),(w-.08,.035,.035),6)
    for side in (-1,1):
        m.box((x+side*(w*.5+.13),y-.03,z),(.18,.08,h+.06),9)
        for j in range(3):m.box((x+side*(w*.5+.13),y-.08,z-.22+j*.2),(.17,.02,.025),2)
    m.box((x,y-.1,z-h*.5-.08),(w+.2,.25,.12),3)

def roof(m,x,y,z,w,d,pitch=1,mat=1):
    # Curved overlapping clay tiles follow the two pitched planes.
    for side in (-1,1):
        for row in range(7):
            t0=row/7;t1=min(1,(row+1.15)/7)
            for col in range(11):
                y0=y-d/2+col*d/11;y1=y0+d/11*.97
                x0=x+side*w*.5*t0;x1=x+side*w*.5*t1
                z0=z+pitch*(1-t0);z1=z+pitch*(1-t1)
                curve=.045
                for k in range(3):
                    v0=k/3;v1=(k+1)/3
                    m.face([(x0,y0+(y1-y0)*v0,z0+curve*math.sin(v0*math.pi)),(x1,y0+(y1-y0)*v0,z1+curve*math.sin(v0*math.pi)),(x1,y0+(y1-y0)*v1,z1+curve*math.sin(v1*math.pi)),(x0,y0+(y1-y0)*v1,z0+curve*math.sin(v1*math.pi))],mat)
        m.tube((x,y-d/2,z+pitch),(x+side*w*.52,y-d/2,z-.02),.075,2)
        m.tube((x,y+d/2,z+pitch),(x+side*w*.52,y+d/2,z-.02),.075,2)
    m.tube((x,y-d*.52,z+pitch+.03),(x,y+d*.52,z+pitch+.03),.09,mat,12)

def casa(m,variant=0):
    w=3.4;d=2.6;h=2.4
    m.box((0,0,.12),(w+.25,d+.2,.24),4)
    m.box((-w/2,0,h/2),(.15,d,h),0);m.box((w/2,0,h/2),(.15,d,h),0)
    m.box((0,d/2,h/2),(w,.15,h),0)
    # A real front opening, lintel and arched door frame.
    m.box((-1.1,-d/2,h/2),(1.2,.17,h),0);m.box((1.1,-d/2,h/2),(1.2,.17,h),0)
    m.box((0,-d/2,2.06),(.95,.17,.7),0);m.arch((0,-d/2-.08,.15),.84,1.8,.2,4)
    m.box((0,-d/2+.07,1),(.78,.08,1.65),3)
    for j in range(6):m.box((-.32+j*.128,-d/2+.01,1),(.018,.02,1.6),2)
    for zz in (.5,1.42):m.box((0,-d/2-.025,zz),(.78,.05,.07),5)
    m.ellipsoid((.24,-d/2-.08,1),(.045,.04,.045),6,10,5)
    for xx in (-1.18,1.18):window(m,xx,-d/2-.12,1.45,.55,.75)
    for xx in (-1.65,1.65):m.box((xx,-d/2-.13,1.28),(.14,.12,2.4),2)
    m.box((0,-d/2-.13,2.3),(w,.13,.15),2)
    for yy in (-.9,.9):m.box((w/2+.09,yy,1.5),(.05,.45,.8),9)
    roof(m,0,0,2.42,3.9,3.2,1.05)
    m.box((.9,.45,3.05),(.38,.42,1.3),4);m.box((.9,.45,3.72),(.49,.53,.14),4)
    m.box((1.18,-1.5,.92),(.75,.28,.21),3)
    for i in range(7):m.ellipsoid((.9+i*.09,-1.52,1.1+random.random()*.1),(.1,.12,.1),7,8,4)
    if variant:
        m.box((-1.9,-1.5,1.9),(.13,.13,1.4),2);m.tube((-1.9,-1.5,2.55),(-1.9,-2.2,2.55),.05,5)
        m.box((-1.9,-2.13,2.2),(.5,.1,.5),3);m.ellipsoid((-1.9,-2.2,2.2),(.15,.04,.16),6,12,6)

def tree(m,style):
    leaf=8 if style=='maple' else 9 if style in ('glow_tree','willow') else 7
    if style=='cypress':
        m.tube((0,0,0),(0,0,3.5),.2,2,12,.08)
        for j in range(7):m.ellipsoid((math.sin(j)*.08,0,1.2+j*.4),(.5-j*.045,.48-j*.04,.75),7,14,8)
    elif style=='pine':
        m.tube((0,0,0),(0,0,3.6),.19,2,12,.04)
        for j in range(5):m.lathe((0,0,.6+j*.5),[(0,0),(.95-j*.15,0),(0,1.65)],7,16)
    elif style=='acacia':
        m.tube((0,0,0),(.3,0,2.2),.23,2,12,.13)
        for i in range(5):
            a=i*math.tau/5;end=(math.cos(a)*1.25,math.sin(a),2.6+random.random()*.3)
            m.tube((.1,0,1.6),end,.12,2,9,.035);m.ellipsoid(end,(1,.7,.4),7,14,7)
    elif style=='willow':
        m.tube((0,0,0),(.2,0,2.6),.27,2,12,.09)
        for i in range(8):
            a=i*math.tau/8;end=(math.cos(a)*1.15,math.sin(a)*1.15,2.5)
            m.tube((.1,0,1.8),end,.1,2,8,.025)
            m.ellipsoid(end,(.65,.65,.65),leaf,14,8)
            for j in range(3):
                xx=end[0]+(j-1)*.2;yy=end[1]
                m.tube((xx,yy,2.5),(xx*.95,yy*.95,.8+random.random()*.5),.025,leaf,6,.005)
    else:
        m.tube((0,0,0),(.2,.1,2.6),.28,2,12,.09)
        for i in range(7):
            a=i*2.4;end=(math.cos(a)*(.9 if style!='crystal_tree' else .6),math.sin(a)*.8,2+random.random()*1.2)
            m.tube((.1,0,1.1+i*.15),end,.1,2,9,.03)
            if style=='crystal_tree':crystal(m,*end,.7)
            else:m.ellipsoid(end,(.8,.75,.7),leaf,16,9)
        if style=='olive':
            for i in range(16):
                a=i*2.4;m.ellipsoid((math.cos(a)*.85,math.sin(a)*.7,2+(i%3)*.3),(.07,.07,.1),12,8,4)
        if style=='glow_tree':
            for i in range(5):
                a=i*1.4;xx=math.cos(a)*1.0;yy=math.sin(a)*1.0
                m.tube((xx,yy,2.4),(xx,yy,1.6),.012,6,6);m.ellipsoid((xx,yy,1.5),(.12,.12,.2),15,10,6)
    for i in range(5):
        a=i*math.tau/5;m.tube((0,0,.2),(math.cos(a)*.6,math.sin(a)*.6,.03),.1,2,8,.025)

def crystal(m,x=0,y=0,z=0,scale=1):
    for i in range(3):
        xx=x+(i-1)*.22*scale;yy=y+(i%2)*.14*scale;hh=(.7 if i!=1 else 1.15)*scale
        m.lathe((xx,yy,z),[(.12*scale,0),(.16*scale,hh*.7),(0,hh)],15 if i==1 else 9,6)

def mushroom(m,giant=False):
    h=1.4 if giant else .45;r=.8 if giant else .28
    m.lathe((0,0,0),[(.13,0),(.09,h*.8),(.18,h)],13,16)
    m.lathe((0,0,h),[(0,-.09),(r,-.09),(r*.92,.08),(r*.65,r*.42),(0,r*.5)],9 if giant else 11,24)
    for i in range(9):
        a=i*2.4;rr=r*(.25+(i%3)*.2);m.ellipsoid((math.cos(a)*rr,math.sin(a)*rr,h+r*.4*(1-rr/r)),(.06,.06,.025),15 if giant else 13,8,4)

def tower(m,kind='castle'):
    if kind=='lighthouse':
        m.lathe((0,0,0),[(.85,0),(.78,.2),(.5,4.5),(.7,4.55),(.7,4.7),(.55,4.72),(.55,5.35),(.72,5.4),(0,6)],0,32)
        for i in range(8):
            a=i*math.tau/8;m.tube((math.cos(a)*.57,math.sin(a)*.57,4.7),(math.cos(a)*.57,math.sin(a)*.57,5.35),.035,5,8)
        m.ellipsoid((0,0,5),(.3,.3,.35),15,20,10)
        m.arch((0,-.81,.05),.62,1.35,.2,4);m.box((0,-.73,.65),(.55,.08,1.2),2)
    else:
        for xx,yy,height in [(-2,-1.3,4.8),(2,-1.3,5.4),(-2,1.3,5.1),(2,1.3,4.7),(0,.5,6.5)]:
            m.lathe((xx,yy,0),[(.7,0),(.7,height-.4),(.85,height-.35),(.85,height),(.7,height)],4,24)
            m.lathe((xx,yy,height),[(.95,0),(.8,.15),(.14,2),(0,2.15)],11,24)
            m.tube((xx,yy,height+2),(xx,yy,height+2.8),.025,5,8)
            m.face([(xx,yy,height+2.8),(xx+.65,yy,height+2.55),(xx,yy,height+2.3)],6,[(0,1),(1,.5),(0,0)])
            window(m,xx,yy-.71,2.8,.36,.9)
        m.box((0,-1.25,1.75),(3.8,.65,3.5),4);m.arch((0,-1.63,0),1.15,2.4,.23,4)
        m.box((0,-1.62,1),(.98,.12,1.9),2)
        for j in range(8):m.box((-1.85+j*.53,-1.25,3.7),(.3,.65,.55),4)
        for xx in (-1.8,1.8):m.tube((xx,-1.6,.2),(xx,-1.6,2.7),.075,14,8)

def windmill(m):
    m.lathe((0,0,0),[(1,0),(.8,3.5),(.65,3.7)],0,24)
    m.lathe((0,0,3.5),[(1,0),(.1,1.3),(0,1.4)],1,24);m.arch((0,-1,.05),.6,1.2,.18,4)
def sails(m):
    for i in range(4):
        a=i*math.pi/2
        dx,dz=math.sin(a),math.cos(a)
        m.tube((0,-.9,3.5),(dx*2.2,-.9,3.5+dz*2.2),.05,2,8)
        for j in range(7):
            t=.8+j*.19;w=.42
            p=(dx*t,-.94,3.5+dz*t)
            m.face([(p[0]-dz*w,p[1],p[2]+dx*w),(p[0]+dz*w,p[1],p[2]-dx*w),(p[0]+dx*.16+dz*w,p[1],p[2]+dz*.16-dx*w),(p[0]+dx*.16-dz*w,p[1],p[2]+dz*.16+dx*w)],13)
    m.ellipsoid((0,-1,3.5),(.17,.12,.17),5,16,8)

def pumpkin(m,x=0,y=0,z=0,s=1):
    for i in range(8):
        a=i*math.tau/8;m.ellipsoid((x+math.cos(a)*s*.22,y+math.sin(a)*s*.22,z+s*.3),(s*.24,s*.24,s*.32),8,12,7)
    m.tube((x,y,z+s*.56),(x+.07*s,y,z+s*.8),s*.05,14,8)

def props(m,kind):
    if kind=='boulder':
        for i in range(3):m.ellipsoid(((i-1)*.28,(i%2)*.18,.2+(i%2)*.12),(.43,.35,.32+(i%2)*.12),4,7,4)
        m.ellipsoid((0,-.1,.39),(.3,.23,.05),14,10,5)
    elif kind in ('berry_bush','rose_bush','herb'):
        for i in range(6):
            a=i*2.4;xx=math.cos(a)*.3;yy=math.sin(a)*.3;m.tube((0,0,0),(xx,yy,.45),.025,14,6)
            m.ellipsoid((xx,yy,.38),(.2,.15,.16),7,10,5)
            if kind!='herb':m.ellipsoid((xx,yy-.06,.5),(.075,.075,.08),11 if kind=='rose_bush' else 12,10,6)
    elif kind in ('reed','wheat','garden'):
        for i in range(18 if kind!='garden' else 45):
            xx=random.uniform(-.5,.5) if kind!='garden' else random.uniform(-1,1);yy=random.uniform(-.5,.5);hh=random.uniform(.45,1)
            m.tube((xx,yy,0),(xx+.09,yy,hh),.012,14 if kind=='reed' else 3,5)
            m.ellipsoid((xx+.09,yy,hh),(.035,.04,.15),8 if kind!='reed' else 2,7,5)
        if kind=='garden':
            for yy in (-.6,.6):m.box((0,yy,.07),(2.2,.1,.14),3)
            for xx in (-1.05,1.05):m.box((xx,0,.07),(.1,1.2,.14),3)
    elif kind=='well':
        m.lathe((0,0,0),[(.7,0),(.7,.7),(.5,.7),(.5,.1)],4,24)
        for xx in (-.72,.72):m.box((xx,0,1.05),(.13,.13,2.1),2)
        roof(m,0,0,2.1,1.9,1.4,.55);m.tube((-.7,0,1.5),(.7,0,1.5),.055,3,12)
        m.tube((0,0,1.5),(0,0,.5),.015,3,6);m.lathe((0,0,.38),[(.15,0),(.21,.3),(.18,.3),(.14,.05)],3,16)
    elif kind=='market':
        m.box((0,0,.55),(2.2,.9,1),3)
        for xx in (-1.1,1.1):m.box((xx,.3,1.3),(.1,.1,2.6),2)
        for i in range(8):m.face([(-1.2+i*.3,-.8,1.85),(-.9+i*.3,-.8,1.85),(-.9+i*.3,.6,2.6),(-1.2+i*.3,.6,2.6)],11 if i%2==0 else 13)
        for i in range(18):m.ellipsoid((random.uniform(-.9,.9),random.uniform(-.3,.3),1.1),(.1,.1,.11),8 if i%2 else 7,8,5)
    elif kind=='lamp':
        m.tube((0,0,0),(0,0,2),.045,5,10);m.tube((0,0,2),(.4,0,2.15),.04,5,10)
        m.box((.4,0,1.85),(.22,.22,.4),15)
        for xx in (.28,.52):m.box((xx,0,1.85),(.025,.25,.45),5)
        m.lathe((.4,0,2.1),[(.23,0),(0,.2)],5,8)
    elif kind=='campfire':
        for i in range(10):
            a=i*math.tau/10;m.ellipsoid((math.cos(a)*.52,math.sin(a)*.52,.12),(.16,.13,.13),4,8,5)
        for a in (0,1.4,2.8):m.tube((math.cos(a)*.42,math.sin(a)*.42,.15),(-math.cos(a)*.42,-math.sin(a)*.42,.22),.09,2,10)
        for xx in (-.4,.4):m.tube((xx,0,.2),(0,0,1.2),.035,5,8)
        m.lathe((0,0,.55),[(.25,0),(.3,.13),(.24,.36),(.28,.38)],5,20)
    elif kind=='workbench':
        m.box((0,0,.9),(1.65,.9,.15),3)
        for xx in (-.65,.65):
            for yy in (-.3,.3):m.box((xx,yy,.45),(.12,.12,.9),2)
        m.box((.4,0,1.02),(.3,.3,.13),5);m.tube((-.4,0,1.02),(-.2,0,1.02),.055,3,10)
        m.box((-.43,0,1.06),(.12,.3,.15),5)
    elif kind=='furnace':
        m.lathe((0,0,0),[(.65,0),(.65,.8),(.45,1.3),(.2,1.4),(.2,2.2),(.32,2.25)],4,20)
        m.arch((0,-.61,.1),.6,.75,.18,5);m.box((0,-.65,.4),(.44,.03,.4),15)
    elif kind=='cannon':
        for xx in (-.55,.55):
            # Wheel axis is X. The bore is an actual open tube, not a capped cylinder.
            m.tube((xx-.07,0,.4),(xx+.07,0,.4),.42,2,20)
            m.tube((xx-.08,0,.4),(xx+.08,0,.4),.14,5,12)
        m.box((0,0,.45),(1.1,1.25,.22),3)
        start=len(m.v);length=math.sqrt(1.5**2+.14**2)
        m.lathe((0,0,0),[(.24,0),(.3,length),(.22,length),(.2,length-.42),(0,length-.42)],5,24)
        axis=Vector((0,-1.5,.14)).normalized();u=Vector((1,0,0));v=axis.cross(u);origin=Vector((0,.5,.85))
        for index in range(start,len(m.v)):
            xx,yy,zz=m.v[index];m.v[index]=tuple(origin+u*xx+v*yy+axis*zz)
        m.tube((-.55,0,.45),(.55,0,.45),.08,5,10)
        for yy in (-.55,-.85):m.tube((0,yy,.85),(0,yy-.04,.855),.31,6,24)
    elif kind=='chest':
        m.box((0,0,.3),(.85,.55,.6),3);m.lathe((0,0,0),[(.001,0),(.001,.001)],3,4)
        for xx in (-.28,.28):m.box((xx,0,.33),(.07,.59,.67),5)
        m.box((0,-.3,.35),(.15,.08,.22),6)
        for i in range(7):
            a=math.pi*i/6;m.box((0,math.cos(a)*.27,.6+math.sin(a)*.22),(.86,.075,.065),3)
    elif kind=='bucket':m.lathe((0,0,0),[(.15,0),(.22,.4),(.19,.4),(.13,.05)],3,16)
    elif kind=='sword':
        m.box((0,0,.12),(.08,.07,.24),2);m.box((0,0,.28),(.34,.1,.07),6)
        m.face([(-.09,-.025,.31),(.09,-.025,.31),(0,-.025,1.15)],5,[(0,0),(1,0),(.5,1)])
        m.face([(.09,.025,.31),(-.09,.025,.31),(0,.025,1.15)],5,[(0,0),(1,0),(.5,1)])
        m.tube((0,0,.31),(0,0,.95),.025,6,6)
    elif kind=='shield':
        for i in range(12):
            a=i*math.tau/12;b=(i+1)*math.tau/12
            m.face([(0,-.1,.55),(.42*math.cos(a),0,.55+.55*math.sin(a)),(.42*math.cos(b),0,.55+.55*math.sin(b))],3,[(.5,.5),(.5+.5*math.cos(a),.5+.5*math.sin(a)),(.5+.5*math.cos(b),.5+.5*math.sin(b))])
            m.tube((.42*math.cos(a),0,.55+.55*math.sin(a)),(.42*math.cos(b),0,.55+.55*math.sin(b)),.035,5,6)
        m.ellipsoid((0,-.14,.55),(.13,.06,.13),6,12,6)

def hero_part(name,fn):
    m=Mesh(name);fn(m);return m.object()
def hero():
    body=Mesh('hero_body')
    body.ellipsoid((0,0,.75),(.24,.16,.34),10,20,12)
    # Flared cloak built from a curved open shell.
    for i in range(16):
        a=.1+math.pi*i/16;b=.1+math.pi*(i+1)/16
        body.face([(.18*math.cos(a),.12+.1*math.sin(a),1.01),(.18*math.cos(b),.12+.1*math.sin(b),1.01),(.39*math.cos(b),.1+.21*math.sin(b),.42),(.39*math.cos(a),.1+.21*math.sin(a),.42)],11)
    body.ellipsoid((0,-.02,1.2),(.27,.22,.27),12,24,14)
    body.ellipsoid((0,-.2,1.12),(.18,.065,.11),13,16,9)
    for xx in (-.105,.105):
        body.ellipsoid((xx,-.208,1.24),(.07,.03,.083),13,14,8)
        body.ellipsoid((xx,-.234,1.24),(.024,.012,.052),7,12,7)
        body.ellipsoid((xx,-.244,1.24),(.01,.006,.04),5,10,5)
        body.lathe((xx*1.7,0,1.32),[(.1,0),(.08,.2),(0,.3)],12,3)
    body.ellipsoid((0,-.26,1.14),(.036,.025,.027),2,10,6)
    # Wide brim, pinched crown, stitched band and sculpted feather.
    body.ellipsoid((0,.015,1.43),(.45,.32,.035),11,32,6)
    body.lathe((0,.025,1.44),[(.25,0),(.2,.26),(.14,.32),(.04,.33)],11,24)
    body.lathe((0,.025,1.45),[(.25,0),(.235,.07)],6,24)
    body.tube((.2,.02,1.65),(.47,.1,2),.012,6,6)
    for i in range(7):
        t=i/7;body.ellipsoid((.23+.24*t,.04,.70+1.02+t*.28),(.09*(1-t*.5),.025,.08),13,10,5)
    body.box((0,-.15,.62),(.43,.07,.075),2);body.box((0,-.2,.62),(.1,.04,.1),6)
    for side in (-1,1):
        for j in range(3):body.tube((side*.1,-.25,1.13-j*.025),(side*(.29+j*.025),-.23,1.12-j*.035),.003,13,5)
    objs=[body.object()]
    for side in (-1,1):
        leg=Mesh('leg_left' if side<0 else 'leg_right')
        leg.ellipsoid((side*.13,0,.35),(.1,.1,.28),2,14,8)
        leg.ellipsoid((side*.13,-.08,.09),(.13,.2,.11),2,16,8)
        leg.lathe((side*.13,0,.25),[(.12,0),(.13,.16),(.15,.18)],3,14)
        leg.box((side*.13,-.095,.32),(.18,.025,.04),6);objs.append(leg.object())
        arm=Mesh('arm_left' if side<0 else 'arm_right')
        arm.tube((side*.22,0,.96),(side*.31,-.03,.65),.075,10,12)
        arm.ellipsoid((side*.31,-.04,.61),(.08,.07,.09),12,12,7);objs.append(arm.object())
    tail=Mesh('tail');tail.tube((0,.12,.55),(.4,.32,.35),.11,12,12,.07);tail.tube((.4,.32,.35),(.55,.3,.56),.07,13,12,.02);objs.append(tail.object())
    models['hero']=objs

def animal(m,kind):
    if kind=='rabbit':
        m.ellipsoid((0,0,.28),(.25,.36,.27),13,18,10);m.ellipsoid((0,-.3,.47),(.18,.17,.18),13,16,9)
        for xx in (-.09,.09):
            m.ellipsoid((xx,-.24,.78),(.055,.065,.24),13,12,8);m.ellipsoid((xx,-.4,.51),(.02,.012,.025),5,8,5)
            m.ellipsoid((xx,-.12,.08),(.08,.15,.08),13,10,6)
    elif kind=='goat':
        m.ellipsoid((0,0,.65),(.28,.48,.3),13,20,10);m.ellipsoid((0,-.43,.95),(.19,.2,.23),3,16,9)
        for xx in (-.18,.18):
            for yy in (-.28,.28):m.tube((xx,yy,.55),(xx,yy,.05),.055,2,10)
            m.tube((xx*.6,-.4,1.1),(xx*.8,-.2,1.42),.045,4,10,.015)
        for xx in (-.09,.09):m.ellipsoid((xx,-.59,1),(.018,.015,.025),5,8,5)
    elif kind=='heron':
        m.ellipsoid((0,0,.85),(.18,.33,.22),13,18,10);m.tube((0,-.2,.85),(0,-.35,1.4),.065,13,12)
        m.ellipsoid((0,-.36,1.4),(.1,.13,.11),13,14,8);m.tube((0,-.44,1.4),(0,-.75,1.38),.06,6,8,0)
        for xx in (-.08,.08):m.tube((xx,0,.75),(xx,-.02,.03),.018,5,7)
    elif kind=='firefly':
        m.ellipsoid((0,0,.7),(.11,.22,.1),15,16,10)
        for side in (-1,1):m.ellipsoid((side*.24,0,.75),(.24,.13,.015),9,16,8)
    elif kind=='wisp':
        m.lathe((0,0,0),[(0,.1),(.25,.3),(.35,.65),(.2,.9),(0,1.2)],9,24)
        for xx in (-.1,.1):m.ellipsoid((xx,-.28,.7),(.04,.025,.07),15,12,8)
        for i in range(3):m.ellipsoid((math.sin(i)*.4,0,.15+i*.2),(.07,.07,.1),15,10,6)
    elif kind=='golem':
        m.ellipsoid((0,0,1.1),(.55,.35,.65),4,9,6);m.ellipsoid((0,-.05,1.8),(.3,.3,.32),4,8,5)
        for xx in (-.44,.44):
            m.tube((xx,0,1.5),(xx*1.6,-.04,.65),.18,4,8)
            m.tube((xx*.55,0,.65),(xx*.7,0,.05),.19,4,8)
        for xx in (-.1,.1):m.ellipsoid((xx,-.31,1.85),(.055,.02,.035),15,10,5)
        crystal(m,0,.15,1.9,.5)
    else:
        m.ellipsoid((0,0,.7),(.28,.2,.4),14,14,8);m.ellipsoid((0,-.03,1.2),(.25,.2,.25),2,16,9)
        for side in (-1,1):
            m.tube((side*.2,0,.9),(side*.46,-.04,.5),.06,2,10,.025)
            m.tube((side*.14,0,.45),(side*.19,-.03,.03),.075,2,10,.04)
            m.tube((side*.17,0,1.35),(side*.4,.05,1.7),.08,2,9,0)
            m.ellipsoid((side*.1,-.21,1.22),(.045,.025,.055),15,12,6)
        for i in range(5):m.lathe((random.uniform(-.25,.25),0,.5+i*.14),[(.08,0),(0,.26)],14,4)

model('house',lambda m:casa(m));model('inn',lambda m:casa(m,1))
model('castle',tower);model('lighthouse',lambda m:tower(m,'lighthouse'));model('windmill',windmill)
rotor=Mesh('windmill_sails');sails(rotor);models['windmill'].append(rotor.object())
for name in ['olive','cypress','acacia','willow','crystal_tree','glow_tree','maple','pine']:model(name,lambda m,n=name:tree(m,n))
for name in ['boulder','berry_bush','rose_bush','herb','reed','wheat','garden','well','market','lamp','campfire','workbench','furnace','cannon','chest','bucket','sword','shield']:model(name,lambda m,n=name:props(m,n))
model('mushroom',mushroom);model('giant_mushroom',lambda m:mushroom(m,True));model('crystal',crystal);model('pumpkin',pumpkin)
for name in ['rabbit','goat','heron','firefly','thornling','wisp','golem']:model(name,lambda m,n=name:animal(m,n))
hero()
# Region-specific buildings with different foundations, silhouettes and architectural details.
def stilt(m):
    for xx in (-1.3,1.3):
        for yy in (-1,1):m.tube((xx,yy,-.65),(xx,yy,.5),.11,2,12)
    casa(m);m.box((0,-1.9,.05),(2.3,1.3,.15),3)
    for i in range(5):m.box((0,-2.3-i*.22,-.08-i*.14),(1.2,.24,.12),3)
model('stilt_hut',stilt)
def manor(m):
    casa(m);m.lathe((1.8,.5,0),[(.65,0),(.65,3.5)],4,24);m.lathe((1.8,.5,3.5),[(.85,0),(.07,1.7)],11,24)
    for j in range(3):
        m.arch((-1+j*.9,-1.6,.2),.6,1.9,.16,4)
        m.tube((-1+j*.9,-1.7,.1),(-1+j*.9,-1.7,2),.05,14,8)
        for i in range(6):m.ellipsoid((-1+j*.9+math.sin(i)*.2,-1.76,.4+i*.22),(.09,.06,.09),11,8,5)
model('rose_manor',manor)
def pumpkin_house(m):
    m.lathe((0,0,0),[(1.45,0),(1.6,1.3),(1.2,2.1)],0,32)
    m.lathe((0,0,2.05),[(1.8,0),(1.1,.65),(.55,1.3),(.12,1.7)],8,32)
    m.tube((.1,0,3.65),(.4,0,4.1),.12,14,12,.05)
    m.arch((0,-1.58,.1),.75,1.65,.2,3);window(m,-.9,-1.35,1.1,.48,.62);window(m,.9,-1.35,1.1,.48,.62)
    for xx in (-1.5,1.5):pumpkin(m,xx,-1.4,0,.6)
model('pumpkin_house',pumpkin_house)
def treehouse(m):
    m.tube((0,0,0),(.2,0,4.5),.55,2,18,.28)
    m.box((0,0,2),(3.8,2.6,.2),3)
    for xx in (-1.5,1.5):m.tube((0,0,.7),(xx,0,2),.13,2,12)
    # Cabin sits above the fork; its deck and ladder are real geometry.
    m.box((0,.4,3),(2.6,1.8,1.8),3);roof(m,0,.4,3.9,3.3,2.6,.9,9)
    for i in range(10):
        h=.2+i*.18;m.tube((-.35,-1.5,h),(.35,-1.5,h),.035,3,8)
    for xx in (-.4,.4):m.tube((xx,-1.5,0),(xx,-1.5,2.1),.05,2,10)
    for i in range(6):
        a=i*1.8;m.ellipsoid((math.cos(a)*1.1,math.sin(a)*1,4.8+random.random()),(1.2,1,.8),7,16,9)
    window(m,0,-.55,3,.6,.65)
model('treehouse',treehouse)
def observatory(m):
    m.lathe((0,0,0),[(1.7,0),(1.7,.2),(1.45,.25),(1.45,2.7),(1.6,2.8)],4,32)
    m.lathe((0,0,2.8),[(1.65,0),(1.5,.65),(1.1,1.2),(.45,1.55),(0,1.6)],9,32)
    m.tube((0,-.8,3.3),(.25,-2,3.7),.17,6,20,.25)
    m.arch((0,-1.48,0),.8,1.8,.22,4)
    for i in range(8):
        a=i*math.tau/8;m.ellipsoid((math.cos(a)*1.52,math.sin(a)*1.52,2.1),(.06,.06,.12),6,8,5)
model('observatory',observatory)
def ruin(m):
    for xx,yy,hh in [(-1.5,0,2.7),(1.5,0,2.4),(-1,1.5,1.5)]:
        m.lathe((xx,yy,0),[(.4,0),(.4,.16),(.24,.2),(.24,hh),(.4,hh)],4,16)
        m.ellipsoid((xx,yy,hh),(.4,.32,.06),14,12,6)
    m.arch((0,0,0),2.5,3.1,.5,4);crystal(m,0,0,.1,.7)
    for i in range(8):m.box((random.uniform(-2,2),random.uniform(-1,2),.1),(.4,.3,.2),4,random.random())
model('ruins',ruin)
def wishing(m):
    m.tube((0,0,0),(.1,0,6),.7,2,20,.35)
    for i in range(9):
        a=i*2.4;end=(math.cos(a)*2.6,math.sin(a)*2.2,4.8+random.random()*1.7)
        m.tube((0,0,2.5+i*.25),end,.22,2,12,.045);m.ellipsoid(end,(1.7,1.5,1.2),9,20,12)
        m.tube((end[0],end[1],end[2]),(end[0],end[1],end[2]-1.5),.018,6,7)
        m.ellipsoid((end[0],end[1],end[2]-1.6),(.15,.15,.24),15,14,8)
    m.arch((0,-.6,.1),.7,1.5,.15,6)
    for i in range(6):
        a=i*math.tau/6;m.tube((0,0,.35),(math.cos(a)*1.9,math.sin(a)*1.9,.01),.25,2,12,.025)
model('wishing_tree',wishing)
def warp(m):
    m.lathe((0,0,0),[(1.15,0),(1.15,.12),(.92,.15),(0,.15)],4,32)
    m.arch((0,0,.15),1.45,2.6,.32,4)
    for i in range(7):
        a=math.pi*i/6;m.ellipsoid((math.cos(a)*.8,-.2,1.85+math.sin(a)*.8),(.045,.025,.07),15,10,5)
    m.lathe((0,0,.16),[(.8,0),(.76,.02),(.68,.02)],6,32)
model('warp',warp)
def shrine(m):
    m.lathe((0,0,0),[(1.1,0),(1.1,.15),(.8,.2),(.8,.35),(.4,.4),(.4,1)],4,24)
    crystal(m,0,0,1,.65)
    for i in range(7):
        a=i*math.tau/7;m.ellipsoid((math.cos(a)*.7,math.sin(a)*.7,.5),(.08,.08,.12),6,10,6)
model('shrine',shrine)
def interior(m):
    m.box((0,0,-.12),(7,8,.24),3)
    m.box((0,4,1.55),(7,.18,3.1),0)
    for xx in (-3.5,3.5):m.box((xx,0,1.55),(.18,8,3.1),0)
    # Cutaway front; no roof blocks the camera. Door lies at glTF z=+3.8.
    for xx in (-2.2,2.2):m.box((xx,-4,.8),(2.6,.18,1.6),0)
    m.arch((0,-4,0),1.4,2.4,.23,2)
    for xx in (-3.3,0,3.3):m.box((xx,3.85,1.6),(.13,.1,3.2),2)
    # Hearth, alcove bed, table, shelves, rug and tools.
    m.box((-2.4,2.8,.8),(1.5,1,1.6),4);m.arch((-2.4,2.25,.1),.8,1,.18,2)
    m.box((-2.4,2.28,.5),(.6,.04,.6),15)
    m.box((2.2,2.5,.35),(1.5,2.2,.6),2);m.box((2.2,2.5,.72),(1.4,2.05,.2),10)
    m.box((2.2,3.15,.88),(1.1,.45,.18),13)
    m.box((0,.5,.8),(1.6,1,.12),3)
    for xx in (-.65,.65):
        for yy in (.15,.85):m.box((xx,yy,.4),(.1,.1,.8),2)
    m.box((0,-.8,.01),(2.5,2.3,.025),11)
    for j in range(3):
        m.box((-3.28,.5,1+j*.5),(.3,2,.1),3)
        for i in range(6):m.box((-3.25,-.3+i*.3,1.2+j*.5),(.25,.18,.3),10 if i%2 else 11)
    m.tube((0,0,3.5),(0,0,2.4),.02,5,8);m.ellipsoid((0,0,2.3),(.18,.18,.3),15,16,9)
model('interior',interior)
def flame(m):
    for x,y,h,r in [(0,0,1.1,.24),(.18,.05,.65,.16),(-.15,.04,.8,.18)]:
        m.lathe((x,y,0),[(r*.4,0),(r,.2),(r*.55,h*.6),(0,h)],16,16)
model('flame',flame)
# Preserve the authored meshes/materials in a conventional, self-contained native scene.
for name,objs in models.items():
    col=bpy.data.collections.new(name);bpy.context.scene.collection.children.link(col)
    for obj in objs:
        for old in list(obj.users_collection):old.objects.unlink(obj)
        col.objects.link(obj)
    col['asset_role']='Fablewind original game model; meter scale; atlas materials'
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'art/fablewind-assets.blend'),compress=True)
manifest={}
for name,objs in models.items():
    bpy.ops.object.select_all(action='DESELECT')
    for obj in objs:obj.select_set(True)
    bpy.context.view_layer.objects.active=objs[0]
    bpy.ops.export_scene.gltf(filepath=str(OUT/(name+'.glb')),export_format='GLB',use_selection=True,export_yup=True,export_apply=True,export_animations=False,export_cameras=False,export_lights=False,export_extras=True)
    tris=sum(sum(len(p.vertices)-2 for p in o.data.polygons) for o in objs)
    points=[v.co for o in objs for v in o.data.vertices]
    manifest[name]={'triangles':tris,'parts':len(objs),'bytes':(OUT/(name+'.glb')).stat().st_size,'bounds_min':[min(v[i] for v in points) for i in range(3)],'bounds_max':[max(v[i] for v in points) for i in range(3)]}
(OUT/'manifest.json').write_text(json.dumps({'author':'Fablewind / original procedural Blender geometry','license':'Project original assets; no external models or textures','units':'meters','source':'art/create_assets.py','assets':manifest},indent=2))
print('ASSET_BUILD_COMPLETE',len(manifest),sum(x['triangles'] for x in manifest.values()),sum(x['bytes'] for x in manifest.values()))
