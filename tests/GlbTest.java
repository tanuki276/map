package jp.tanuki.fablewind;
import java.io.*;import java.nio.file.*;import java.util.*;
public final class GlbTest{
 public static void main(String[] args)throws Exception{
  int files=0,triangles=0;Path root=Paths.get("app/src/main/assets/models");
  Map<String,Object> assets=Json.obj(Json.obj(Json.parse(new String(Files.readAllBytes(root.resolve("manifest.json")),"UTF-8"))).get("assets"));
  Set<String> seen=new HashSet<>();
  try(java.util.stream.Stream<Path> stream=Files.list(root)){
   for(Path p:(Iterable<Path>)stream.filter(x->x.toString().endsWith(".glb"))::iterator){
    GlbModel m;try(InputStream in=Files.newInputStream(p)){m=GlbModel.load(in);}
    if(m.triangles<10)throw new AssertionError("empty model "+p);
    for(int at=0;at<m.vertices.length;at+=GlbModel.FLOATS){
     for(int j=0;j<GlbModel.FLOATS;j++)if(!Float.isFinite(m.vertices[at+j]))throw new AssertionError("nonfinite "+p);
     float nx=m.vertices[at+3],ny=m.vertices[at+4],nz=m.vertices[at+5],len=nx*nx+ny*ny+nz*nz;
     if(len<.95 || len>1.05)throw new AssertionError("normal "+p+" "+len);
     if(m.vertices[at+6]<0 || m.vertices[at+6]>1 || m.vertices[at+7]<0 || m.vertices[at+7]>1)throw new AssertionError("UV "+p);
    }
    String name=p.getFileName().toString().replace(".glb","");
    if(!assets.containsKey(name))throw new AssertionError("unregistered model "+p);
    Map<String,Object> meta=Json.obj(assets.get(name));
    if(Json.integer(meta.get("triangles"),-1)!=m.triangles)throw new AssertionError("triangle manifest mismatch "+p);
    if(Json.integer(meta.get("bytes"),-1)!=Files.size(p))throw new AssertionError("byte manifest mismatch "+p);
    seen.add(name);files++;triangles+=m.triangles;
   }
  }
  if(!seen.equals(assets.keySet()))throw new AssertionError("manifest/file mismatch");
  if(files<66)throw new AssertionError("missing original models: "+files);
  try{GlbModel.load(new ByteArrayInputStream(new byte[32]));throw new AssertionError("bad header accepted");}catch(IOException expected){}
  System.out.println("PASS GLB: "+files+" originals / "+triangles+" triangles / normals / UVs / decoder bounds");
 }
}
