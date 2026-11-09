"""Reimport the delivered GLBs and render a contact sheet (Blender + Pillow).
Run from the repository: blender -b -t 2 --python art/preview_river_assets.py
"""
import bpy, math, json
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
NAMES=['plank_bridge','reed_ferry','river_gauge','sluice_gate','waterwheel','landing_dock',
       'route_post','handcart','fish_rack','canvas_tent','survey_shelter','mile_stone']
OUT=ROOT/'art/previews';OUT.mkdir(exist_ok=True)
scene=bpy.context.scene
scene.render.engine='CYCLES';scene.cycles.samples=16;scene.cycles.use_denoising=False
scene.render.resolution_x=384;scene.render.resolution_y=384;scene.render.resolution_percentage=100
scene.world.color=(.35,.35,.35)
for name in NAMES:
    bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
    bpy.ops.import_scene.gltf(filepath=str(ROOT/'app/src/main/assets/models'/(name+'.glb')))
    objs=[o for o in scene.objects if o.type=='MESH'];bpy.context.view_layer.update()
    pts=[o.matrix_world@Vector(p) for o in objs for p in o.bound_box]
    low=Vector([min(p[i] for p in pts) for i in range(3)]);high=Vector([max(p[i] for p in pts) for i in range(3)])
    size=max(high-low);target=(low+high)/2
    bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,low.z-.02))
    mat=bpy.data.materials.new('Studio slate');mat.diffuse_color=(.065,.095,.105,1);bpy.context.object.data.materials.append(mat)
    bpy.ops.object.camera_add(location=target+Vector((size*1.2,-size*1.7,size*1.05)))
    cam=bpy.context.object;cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler()
    cam.data.type='ORTHO';cam.data.ortho_scale=size*1.5;scene.camera=cam
    for delta,energy in [((3,-4,7),950),((-4,-1,4),700),((1,5,5),1100)]:
        bpy.ops.object.light_add(type='AREA',location=target+Vector(delta));o=bpy.context.object
        o.data.energy=energy;o.data.shape='DISK';o.data.size=5
        o.rotation_euler=(target-o.location).to_track_quat('-Z','Y').to_euler()
    scene.view_settings.view_transform='Standard'
    scene.render.filepath=str(OUT/(name+'.png'));bpy.ops.render.render(write_still=True)
print('PREVIEWS_COMPLETE')
