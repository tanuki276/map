attribute vec3 aPosition;
attribute vec3 aNormal;
attribute vec2 aUV;
attribute vec4 aColor;
attribute vec3 aSurface;
attribute vec4 aRig;
uniform mat4 uModel;
uniform mat4 uViewProjection;
uniform vec3 uMotion;
varying vec3 vWorld;
varying vec3 vNormal;
varying vec2 vUV;
varying vec4 vColor;
varying vec3 vSurface;
void main(){
    vec3 position=aPosition;vec3 normal=aNormal;
    if(aRig.w>0.5){
        float angle=0.0;
        if(aRig.w<2.5)angle=sin(uMotion.x*10.0+(aRig.w-1.0)*3.14159)*uMotion.y*.52;
        else if(aRig.w<4.5)angle=-sin(uMotion.x*10.0+(aRig.w-3.0)*3.14159)*uMotion.y*.38;
        else if(aRig.w<5.5)angle=sin(uMotion.x*2.0)*.2;
        else angle=uMotion.x*.4;
        if(aRig.w>3.5 && aRig.w<4.5)angle-=uMotion.z*2.2;
        float c=cos(angle),s=sin(angle);vec3 q=position-aRig.xyz;
        if(aRig.w>5.5){position=aRig.xyz+vec3(q.x*c-q.y*s,q.x*s+q.y*c,q.z);normal=vec3(normal.x*c-normal.y*s,normal.x*s+normal.y*c,normal.z);}
        else {position=aRig.xyz+vec3(q.x,q.y*c-q.z*s,q.y*s+q.z*c);normal=vec3(normal.x,normal.y*c-normal.z*s,normal.y*s+normal.z*c);}
    }
    vec4 world=uModel*vec4(position,1.0);
    vWorld=world.xyz;vNormal=normalize(mat3(uModel)*normal);vUV=aUV;vColor=aColor;vSurface=aSurface;
    gl_Position=uViewProjection*world;
}
