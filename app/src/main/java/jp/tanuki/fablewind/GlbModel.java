package jp.tanuki.fablewind;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** glTF 2.0 GLB triangle reader. Applies node hierarchy/TRS, accessor offsets and strides.
 * The shipped originals use one embedded atlas, metallic/roughness materials and no compression.
 * Unsupported required extensions, sparse data and primitive modes fail explicitly.
 */
public final class GlbModel {
    public static final int FLOATS=19;
    public final float[] vertices;public final int triangles;
    private final Map<String,Object> root;private final ByteBuffer bin;
    private final List<Object> accessors,views,nodes,meshes,materials;
    private final FloatList output=new FloatList();
    private GlbModel(Map<String,Object> root,ByteBuffer bin)throws IOException{
        this.root=root;this.bin=bin;accessors=Json.list(root.get("accessors"));views=Json.list(root.get("bufferViews"));
        nodes=Json.list(root.get("nodes"));meshes=Json.list(root.get("meshes"));materials=Json.list(root.get("materials"));
        for(Object required:Json.list(root.get("extensionsRequired")))if(!required.equals("KHR_materials_emissive_strength"))throw new IOException("Unsupported GLB extension "+required);
        List<Object> scenes=Json.list(root.get("scenes"));int scene=Json.integer(root.get("scene"),0);
        if(scene<0||scene>=scenes.size())throw new IOException("Missing GLB scene");
        for(Object n:Json.list(Json.obj(scenes.get(scene)).get("nodes")))visit(Json.integer(n,-1),identity(),0,new HashSet<>());
        if(output.size==0)throw new IOException("Empty GLB");vertices=Arrays.copyOf(output.data,output.size);triangles=vertices.length/FLOATS/3;
    }
    public static GlbModel load(InputStream stream)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] chunk=new byte[16384];int n;
        while((n=stream.read(chunk))!=-1){out.write(chunk,0,n);if(out.size()>24_000_000)throw new IOException("GLB too large");}
        byte[] bytes=out.toByteArray();if(bytes.length<28)throw new IOException("Truncated GLB");
        ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if(b.getInt()!=0x46546c67 || b.getInt()!=2 || b.getInt()!=bytes.length)throw new IOException("Invalid GLB header");
        Map<String,Object> json=null;ByteBuffer binary=null;
        while(b.remaining()>=8){int length=b.getInt(),type=b.getInt();if(length<0||length>b.remaining()||length%4!=0)throw new IOException("Invalid GLB chunk");
            byte[] data=new byte[length];b.get(data);
            if(type==0x4e4f534a)json=Json.obj(Json.parse(new String(data,StandardCharsets.UTF_8).trim()));
            if(type==0x004e4942)binary=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        }
        if(json==null||binary==null||b.hasRemaining())throw new IOException("Missing GLB chunks");
        return new GlbModel(json,binary);
    }
    private void visit(int index,float[] parent,int inheritedRig,Set<Integer> stack)throws IOException{
        if(index<0||index>=nodes.size()||!stack.add(index))throw new IOException("Invalid GLB node graph");
        Map<String,Object> node=Json.obj(nodes.get(index));float[] matrix=multiply(parent,transform(node));
        String name=String.valueOf(node.get("name"));int rig=name.startsWith("leg_left")?1:name.startsWith("leg_right")?2:name.startsWith("arm_left")?3:name.startsWith("arm_right")?4:name.startsWith("tail")?5:name.startsWith("windmill_sails")?6:inheritedRig;
        int mesh=Json.integer(node.get("mesh"),-1);
        if(mesh>=0){if(mesh>=meshes.size())throw new IOException("Invalid mesh index");for(Object p:Json.list(Json.obj(meshes.get(mesh)).get("primitives")))primitive(Json.obj(p),matrix,rig);}
        for(Object child:Json.list(node.get("children")))visit(Json.integer(child,-1),matrix,rig,stack);
        stack.remove(index);
    }
    private void primitive(Map<String,Object> p,float[] matrix,int rig)throws IOException{
        if(Json.integer(p.get("mode"),4)!=4)throw new IOException("Non-triangle GLB primitive");
        Map<String,Object> attrs=Json.obj(p.get("attributes"));Accessor pos=new Accessor(Json.integer(attrs.get("POSITION"),-1));
        Accessor normal=attrs.containsKey("NORMAL")?new Accessor(Json.integer(attrs.get("NORMAL"),-1)):null;
        Accessor uv=attrs.containsKey("TEXCOORD_0")?new Accessor(Json.integer(attrs.get("TEXCOORD_0"),-1)):null;
        Accessor color=attrs.containsKey("COLOR_0")?new Accessor(Json.integer(attrs.get("COLOR_0"),-1)):null;
        Accessor indices=p.containsKey("indices")?new Accessor(Json.integer(p.get("indices"),-1)):null;
        Map<String,Object> material=Collections.emptyMap();int mi=Json.integer(p.get("material"),-1);
        if(mi>=0){if(mi>=materials.size())throw new IOException("Invalid material");material=Json.obj(materials.get(mi));}
        Map<String,Object> pbr=Json.obj(material.get("pbrMetallicRoughness"));List<Object> base=Json.list(pbr.get("baseColorFactor"));
        float[] tint={1,1,1,1};for(int i=0;i<Math.min(4,base.size());i++)tint[i]=Json.number(base.get(i),1);
        float rough=Json.number(pbr.get("roughnessFactor"),1),metal=Json.number(pbr.get("metallicFactor"),1),emit=0;
        for(Object e:Json.list(material.get("emissiveFactor")))emit=Math.max(emit,Json.number(e,0));
        float[] normalMatrix=normalMatrix(matrix);int count=indices==null?pos.count:indices.count;
        if(count%3!=0 || count>2_000_000)throw new IOException("Invalid GLB index count");
        for(int i=0;i<count;i++){
            int v=indices==null?i:indices.index(i);if(v<0||v>=pos.count)throw new IOException("GLB index outside positions");
            float px=pos.get(v,0),py=pos.get(v,1),pz=pos.get(v,2);
            output.add(matrix[0]*px+matrix[4]*py+matrix[8]*pz+matrix[12],matrix[1]*px+matrix[5]*py+matrix[9]*pz+matrix[13],matrix[2]*px+matrix[6]*py+matrix[10]*pz+matrix[14]);
            float nx=normal==null?0:normal.get(v,0),ny=normal==null?1:normal.get(v,1),nz=normal==null?0:normal.get(v,2);
            float ax=normalMatrix[0]*nx+normalMatrix[3]*ny+normalMatrix[6]*nz,ay=normalMatrix[1]*nx+normalMatrix[4]*ny+normalMatrix[7]*nz,az=normalMatrix[2]*nx+normalMatrix[5]*ny+normalMatrix[8]*nz;
            float len=Math.max(.0001f,(float)Math.sqrt(ax*ax+ay*ay+az*az));output.add(ax/len,ay/len,az/len);
            output.add(uv==null?.125f:uv.get(v,0),uv==null?.875f:uv.get(v,1));
            for(int j=0;j<4;j++)output.add(tint[j]*(color==null||j>=color.components?1:color.get(v,j)));
            output.add(rough,metal,emit);
            float pivotX=rig==1?-.13f:rig==2?.13f:rig==3?-.22f:rig==4?.22f:0;
            output.add(pivotX,rig==6?3.5f:rig<=2?.52f:rig==5?.55f:.96f,rig==6?.9f:0,rig);
        }
    }
    private final class Accessor {
        final int count,components,componentType,offset,stride,size,end;final boolean normalized;
        Accessor(int index)throws IOException{
            if(index<0||index>=accessors.size())throw new IOException("Invalid accessor");Map<String,Object>a=Json.obj(accessors.get(index));
            if(a.containsKey("sparse"))throw new IOException("Sparse accessor unsupported by original asset profile");
            int vi=Json.integer(a.get("bufferView"),-1);if(vi<0||vi>=views.size())throw new IOException("Invalid buffer view");Map<String,Object>v=Json.obj(views.get(vi));
            if(Json.integer(v.get("buffer"),0)!=0)throw new IOException("External GLB buffer");
            componentType=Json.integer(a.get("componentType"),0);size=componentType==5126||componentType==5125?4:componentType==5123||componentType==5122?2:componentType==5121||componentType==5120?1:0;
            String type=String.valueOf(a.get("type"));components=type.equals("SCALAR")?1:type.equals("VEC2")?2:type.equals("VEC3")?3:type.equals("VEC4")?4:0;
            count=Json.integer(a.get("count"),0);int start=Json.integer(v.get("byteOffset"),0),length=Json.integer(v.get("byteLength"),0),local=Json.integer(a.get("byteOffset"),0);
            offset=start+local;stride=Json.integer(v.get("byteStride"),size*components);end=start+length;normalized=Boolean.TRUE.equals(a.get("normalized"));
            long last=(long)offset+(long)(count-1)*stride+(long)size*components;
            if(size==0||components==0||count<=0||offset<0||local<0||stride<size*components||last>end||end>bin.limit())throw new IOException("Accessor exceeds buffer");
        }
        int index(int i)throws IOException{if(components!=1 || !(componentType==5121||componentType==5123||componentType==5125))throw new IOException("Invalid index type");return (int)get(i,0);}
        float get(int i,int c)throws IOException{
            if(i<0||i>=count||c<0||c>=components)throw new IOException("Accessor outside range");int at=offset+i*stride+c*size;float value;
            switch(componentType){case 5126:value=bin.getFloat(at);break;case 5125:value=bin.getInt(at)&0xffffffffL;break;
                case 5123:value=bin.getShort(at)&65535;if(normalized)value/=65535;break;
                case 5122:value=bin.getShort(at);if(normalized)value=Math.max(-1,value/32767);break;
                case 5121:value=bin.get(at)&255;if(normalized)value/=255;break;
                default:value=bin.get(at);if(normalized)value=Math.max(-1,value/127);}
            if(!Float.isFinite(value))throw new IOException("Non-finite mesh data");return value;
        }
    }
    static float[] identity(){float[] m=new float[16];m[0]=m[5]=m[10]=m[15]=1;return m;}
    static float[] multiply(float[] a,float[] b){float[]m=new float[16];for(int c=0;c<4;c++)for(int r=0;r<4;r++)for(int k=0;k<4;k++)m[c*4+r]+=a[k*4+r]*b[c*4+k];return m;}
    private static float[] transform(Map<String,Object> node){
        List<Object> values=Json.list(node.get("matrix"));if(values.size()==16){float[]m=new float[16];for(int i=0;i<16;i++)m[i]=Json.number(values.get(i),0);return m;}
        List<Object> t=Json.list(node.get("translation")),r=Json.list(node.get("rotation")),s=Json.list(node.get("scale"));float[]m=identity();
        float qx=r.size()==4?Json.number(r.get(0),0):0,qy=r.size()==4?Json.number(r.get(1),0):0,qz=r.size()==4?Json.number(r.get(2),0):0,qw=r.size()==4?Json.number(r.get(3),1):1;
        m[0]=1-2*qy*qy-2*qz*qz;m[1]=2*qx*qy+2*qz*qw;m[2]=2*qx*qz-2*qy*qw;
        m[4]=2*qx*qy-2*qz*qw;m[5]=1-2*qx*qx-2*qz*qz;m[6]=2*qy*qz+2*qx*qw;
        m[8]=2*qx*qz+2*qy*qw;m[9]=2*qy*qz-2*qx*qw;m[10]=1-2*qx*qx-2*qy*qy;
        for(int c=0;c<3;c++){float scale=s.size()==3?Json.number(s.get(c),1):1;for(int row=0;row<3;row++)m[c*4+row]*=scale;}
        if(t.size()==3)for(int i=0;i<3;i++)m[12+i]=Json.number(t.get(i),0);return m;
    }
    private static float[] normalMatrix(float[] m)throws IOException{
        float a=m[0],b=m[4],c=m[8],d=m[1],e=m[5],f=m[9],g=m[2],h=m[6],i=m[10];float det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        if(Math.abs(det)<1e-8)throw new IOException("Singular node transform");
        return new float[]{(e*i-f*h)/det,(c*h-b*i)/det,(b*f-c*e)/det,(f*g-d*i)/det,(a*i-c*g)/det,(c*d-a*f)/det,(d*h-e*g)/det,(b*g-a*h)/det,(a*e-b*d)/det};
    }
    private static final class FloatList {
        float[] data=new float[65536];int size;
        void add(float... v){if(size+v.length>data.length)data=Arrays.copyOf(data,Math.max(data.length*2,size+v.length));System.arraycopy(v,0,data,size,v.length);size+=v.length;}
    }
}
