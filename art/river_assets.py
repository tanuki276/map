"""Original riverworks kit. Blender Z-up, metres; uses the project's authored atlas.
Registered from create_assets.py; no external mesh, texture, or provider input.
"""
import math

def register(model):
    def bridge(m):
        for x in (-.65,.65):m.tube((x,-2,.42),(x,2,.42),.10,2,8)
        for i in range(19):m.box((0,-1.95+i*.215,.55+(i%3)*.008),(1.7,.19,.13),3,math.sin(i)*.015)
        for x in (-.79,.79):
            for y in (-1.9,0,1.9):m.tube((x,y,0),(x,y,1.4),.065,2,8)
            m.tube((x,-2,1.35),(x,2,1.35),.05,3,8)
    def ferry(m):
        bottom=[(-.36,-1.1,.04),(0,-1.6,.04),(.36,-1.1,.04),(.36,1.1,.04),(0,1.6,.04),(-.36,1.1,.04)]
        top=[(-.68,-1.3,.58),(0,-1.95,.58),(.68,-1.3,.58),(.68,1.3,.58),(0,1.95,.58),(-.68,1.3,.58)]
        inner=[(x*.91,y*.96,z) for x,y,z in top]
        m.face(list(reversed(bottom)),2,[(x/2+.5,y/4+.5) for x,y,z in reversed(bottom)]);m.face([(x,y,z+.07) for x,y,z in bottom],3,[(x/2+.5,y/4+.5) for x,y,z in bottom])
        for i in range(6):
            j=(i+1)%6
            m.face([bottom[i],bottom[j],top[j],top[i]],2)
            m.face([inner[i],inner[j],(bottom[j][0],bottom[j][1],.11),(bottom[i][0],bottom[i][1],.11)],3)
            m.face([top[i],top[j],inner[j],inner[i]],3)
            m.tube(top[i],top[j],.035,3,6)
        for y in (-.8,0,.8):m.box((0,y,.49),(1.2,.23,.08),3)
        m.tube((-.5,-.8,.64),(.6,1,.71),.028,3,8)
        m.box((.66,1.10,.72),(.22,.55,.05),3,-.55)
        m.tube((0,-1.9,.65),(0,-1.9,.84),.035,5,8)
    def gauge(m):
        m.box((0,0,.1),(.7,.6,.2),4);m.box((0,0,1.55),(.24,.18,2.9),13)
        for i in range(14):m.box((.02,-.105,.26+i*.19),(.17 if i%3==0 else .08,.025,.025),11)
        m.box((-.3,0,1.9),(.38,.32,.5),9);m.box((-.3,-.175,1.9),(.26,.02,.30),13)
        m.tube((0,0,2.65),(.7,0,2.65),.045,5,8);m.tube((.65,0,.4),(.65,0,2.65),.018,6,6)
        m.ellipsoid((.65,0,.65),(.20,.20,.15),8,12,6)
    def sluice(m):
        for x in (-1.23,1.23):
            m.box((x,0,1.15),(.55,.85,2.3),4)
            for z in (.35,.9,1.45,2):m.box((x,-.433,z),(.48,.018,.028),14)
        for i in range(8):m.box((0,0,.19+i*.25),(1.9,.16,.22),3)
        for x in (-.71,.71):m.box((x,-.12,1.05),(.10,.06,2.05),5)
        m.box((0,0,2.4),(3.1,.5,.2),2);m.tube((0,0,1.6),(0,0,3.15),.045,6,8)
        m.lathe((0,0,3.0),[(.36,0),(.43,0),(.43,.08),(.36,.08),(.36,0)],5,20)
        for i in range(6):
            a=i*math.tau/6;m.tube((0,0,3.04),(.39*math.cos(a),.39*math.sin(a),3.04),.022,5,6)
    def wheel(m):
        for x in (-.43,.43):
            m.tube((x,0,1.35),(x,0,1.351),.01,5,6) # hub detail
            # Vertical rings via short segments; hollow center remains visible.
            for i in range(20):
                a=i*math.tau/20;b=(i+1)*math.tau/20
                m.tube((x,math.cos(a)*1.15,1.35+math.sin(a)*1.15),(x,math.cos(b)*1.15,1.35+math.sin(b)*1.15),.065,2,6)
            for i in range(8):
                a=i*math.tau/8;m.tube((x,0,1.35),(x,math.cos(a)*1.1,1.35+math.sin(a)*1.1),.045,3,6)
        for i in range(16):
            a=i*math.tau/16;m.box((0,math.cos(a)*1.14,1.35+math.sin(a)*1.14),(.95,.15,.18),3)
        m.tube((-1.1,0,1.35),(1.1,0,1.35),.11,5,12)
        for x in (-.93,.93):m.box((x,0,.62),(.3,.5,1.24),4)
    def dock(m):
        for x in (-.8,.8):
            for y in (-1.35,1.35):m.tube((x,y,0),(x,y,1.25),.10,2,9)
            m.tube((x,-1.5,.48),(x,1.5,.48),.085,2,8)
        for i in range(14):m.box((0,-1.43+i*.22,.6),(1.9,.19,.13),3)
        for y in (-1.15,1.15):m.tube((.72,y,.7),(.72,y,.98),.10,2,8)
        for i in range(3):m.lathe((.72,-1.15,.76+i*.04),[(.10,0),(.14,0),(.14,.025),(.10,.025)],3,12)
    def post(m):
        m.tube((0,0,0),(.04,0,2.15),.08,2,8)
        m.box((.15,0,1.92),(1.2,.14,.26),9,.035);m.box((-.2,0,1.53),(1.1,.14,.24),8,-.06)
        for z in (1.53,1.92):
            for x in (-.23,0,.23):m.box((x,-.085,z),(.045,.018,.12),13)
        m.lathe((0,0,0),[(.28,0),(.22,.22),(.13,.27)],4,8)
    def cart(m):
        m.box((0,0,.58),(1.25,1.75,.14),3)
        for x in (-.65,.65):
            for i in range(3):m.box((x,0,.78+i*.19),(.08,1.8,.14),3)
            for y in (-.82,.82):m.box((x,y,.92),(.10,.10,.95),2)
        for y in (-.88,.88):m.box((0,y,.88),(1.3,.08,.50),3)
        m.tube((-.95,.15,.45),(.95,.15,.45),.07,5,8)
        for x in (-.85,.85):
            for i in range(16):
                a=i*math.tau/16;b=(i+1)*math.tau/16
                m.tube((x,.15+.42*math.cos(a),.45+.42*math.sin(a)),(x,.15+.42*math.cos(b),.45+.42*math.sin(b)),.045,5,6)
            for i in range(8):
                a=i*math.tau/8;m.tube((x,.15,.45),(x,.15+.39*math.cos(a),.45+.39*math.sin(a)),.025,3,6)
        for x in (-.45,.45):m.tube((x,-.8,.55),(x,-2.1,.83),.045,2,8)
    def fishrack(m):
        for x in (-.8,.8):
            for y in (-.35,.35):m.tube((x,y,0),(x,0,1.8),.055,2,7)
        for z in (.8,1.6):m.tube((-.9,0,z),(.9,0,z),.035,3,7)
        for i in range(5):
            x=-.62+i*.31;m.tube((x,0,1.6),(x,0,1.35),.012,3,5)
            m.ellipsoid((x,0,1.13),(.095,.055,.23),9,8,5)
            m.face([(x-.10,0,1.39),(x+.10,0,1.39),(x,0,1.29)],9)
            m.face([(x,0,1.29),(x+.10,0,1.39),(x-.10,0,1.39)],9)
    def tent(m):
        for side in (-1,1):
            p=[(0,-1.1,1.45),(side*.95,-1.1,.06),(side*.95,1.1,.06),(0,1.1,1.45)]
            m.face(p,10);m.face(list(reversed(p)),10)
        for y in (-1.12,1.12):
            m.tube((0,y,0),(0,y,1.48),.04,2,7)
            for side in (-1,1):m.tube((0,y,1.42),(side*1.3,y*1.25,.03),.011,3,5)
        m.box((0,.15,.12),(.68,1.4,.20),11);m.ellipsoid((0,.65,.27),(.29,.2,.10),13,10,6)
    def station(m):
        m.box((0,0,.15),(2.4,1.8,.3),2)
        for x in (-1,1):
            for y in (-.7,.7):m.tube((x,y,0),(x,y,2.5),.07,2,8)
        m.box((0,.76,1.36),(2,.1,1.9),3)
        for side in (-1,1):
            p=[(0,-1.1,2.9),(side*1.4,-1.1,2.45),(side*1.4,1.1,2.45),(0,1.1,2.9)]
            m.face(p,9);m.face(list(reversed(p)),9)
        m.box((0,0,1.02),(1.85,1.05,.12),3)
        for x in (-.75,.75):
            for y in (-.35,.35):m.box((x,y,.6),(.08,.08,.8),2)
        m.box((0,-.07,1.095),(1.25,.65,.02),13)
        for i in range(5):m.box((-.45+i*.22,-.07,1.109),(.018,.52,.008),9)
        for i in range(3):m.box((0,-.26+i*.20,1.11),(1.05,.018,.008),9)
        m.box((0,.69,1.85),(1.4,.035,.57),13)
        m.tube((-1.5,0,0),(-1.5,0,3.5),.045,2,7);m.box((-1.18,0,3.22),(.62,.025,.35),8)
    def milestone(m):
        m.lathe((0,0,0),[(.4,0),(.34,.16),(.26,1.0),(.20,1.22),(.05,1.29)],4,8)
        m.box((0,-.26,.73),(.28,.03,.27),9)
        for x in (-.07,.07):m.box((x,-.281,.73),(.035,.015,.15),13)
    entries=[('plank_bridge',bridge),('reed_ferry',ferry),('river_gauge',gauge),('sluice_gate',sluice),
                    ('waterwheel',wheel),('landing_dock',dock),('route_post',post),('handcart',cart),
                    ('fish_rack',fishrack),('canvas_tent',tent),('survey_shelter',station),('mile_stone',milestone)]
    for name,fn in entries:model(name,fn)
    return {name for name,fn in entries}
