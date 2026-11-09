precision highp float;
uniform sampler2D uAtlas;
uniform vec3 uEye;
uniform vec3 uFog;
uniform float uDay;
uniform float uTime;
uniform float uAlpha;
uniform float uMode;
uniform vec4 uPointLights[3];
varying vec3 vWorld;
varying vec3 vNormal;
varying vec2 vUV;
varying vec4 vColor;
varying vec3 vSurface;
void main(){
    if(uMode>0.5 && uMode<1.5){
        float radius=length(vUV-.5)*2.0;
        if(radius>=1.0)discard;
        gl_FragColor=vec4(.025,.04,.035,(1.0-radius*radius)*uAlpha);return;
    }
    vec3 base=pow(texture2D(uAtlas,vUV).rgb,vec3(2.2))*vColor.rgb;
    vec3 n=normalize(vNormal);vec3 light=normalize(vec3(-.5,.85,.35));
    vec3 view=normalize(uEye-vWorld);vec3 halfDir=normalize(light+view);
    float lambert=max(dot(n,light),0.0);
    float hemi=.5+.5*n.y;
    float rough=clamp(vSurface.x,.12,1.0),metal=vSurface.y;
    float spec=pow(max(dot(n,halfDir),0.0),mix(100.0,5.0,rough));
    vec3 ambient=mix(vec3(.08,.13,.24),vec3(.26,.32,.25),uDay)*(.6+.4*hemi);
    vec3 color=base*(ambient+vec3(1.0,.88,.67)*lambert*uDay*.87);
    color+=mix(vec3(.04),base,metal)*spec*(.15+.7*uDay)*(1.0-rough*.6);
    color+=base*max(0.0,vSurface.z)*1.8;
    for(int i=0;i<3;i++){
        vec3 delta=uPointLights[i].xyz-vWorld;
        float distanceToLight=length(delta);
        float falloff=max(0.0,1.0-distanceToLight/4.5);
        float diffuse=.25+.75*max(dot(n,normalize(delta+vec3(.0001))),0.0);
        color+=base*vec3(1.0,.58,.23)*falloff*falloff*diffuse*uPointLights[i].w*(1.0-uDay*.65);
    }
    if(vSurface.z<-.5 || uMode>1.5){
        float wave=sin(vWorld.x*3.0+uTime*1.3)*sin(vWorld.z*2.2-uTime*.8);
        color+=vec3(.04,.08,.08)*(.5+.5*wave);
        color+=pow(1.0-max(dot(view,n),0.0),3.0)*vec3(.12,.21,.23);
    }
    float distance=length(uEye-vWorld);
    float fog=smoothstep(18.0,35.0,distance);
    color=color/(color+vec3(.65));color=pow(color,vec3(1.0/2.2));
    gl_FragColor=vec4(mix(color,uFog,fog),vColor.a*uAlpha);
}
