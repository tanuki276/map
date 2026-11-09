package jp.tanuki.fablewind;

import java.io.IOException;
import java.util.*;

/** Small bounded JSON reader for our offline glTF files; no network or script evaluation. */
public final class Json {
    private final String text;private int at,depth;
    private Json(String text){this.text=text;}
    public static Object parse(String text)throws IOException{
        if(text.length()>16_000_000)throw new IOException("JSON too large");
        Json p=new Json(text);Object value=p.value();p.space();if(p.at!=text.length())throw p.error();return value;
    }
    private IOException error(){return new IOException("Invalid JSON at "+at);}
    private void space(){while(at<text.length() && Character.isWhitespace(text.charAt(at)))at++;}
    private Object value()throws IOException{
        space();if(at>=text.length() || ++depth>64)throw error();
        char c=text.charAt(at);Object result;
        if(c=='{'){
            at++;Map<String,Object> m=new LinkedHashMap<>();space();
            if(at<text.length() && text.charAt(at)=='}'){at++;result=m;}
            else {while(true){space();if(at>=text.length()||text.charAt(at)!='"')throw error();String key=string();space();if(at>=text.length()||text.charAt(at++)!=':')throw error();m.put(key,value());space();if(at>=text.length())throw error();char end=text.charAt(at++);if(end=='}')break;if(end!=',')throw error();}result=m;}
        }else if(c=='['){
            at++;List<Object> l=new ArrayList<>();space();
            if(at<text.length() && text.charAt(at)==']'){at++;result=l;}
            else {while(true){l.add(value());space();if(at>=text.length())throw error();char end=text.charAt(at++);if(end==']')break;if(end!=',')throw error();}result=l;}
        }else if(c=='"')result=string();
        else if(text.startsWith("true",at)){at+=4;result=Boolean.TRUE;}
        else if(text.startsWith("false",at)){at+=5;result=Boolean.FALSE;}
        else if(text.startsWith("null",at)){at+=4;result=null;}
        else {
            int start=at;while(at<text.length() && "-+0123456789.eE".indexOf(text.charAt(at))>=0)at++;
            try{double n=Double.parseDouble(text.substring(start,at));if(!Double.isFinite(n))throw error();result=n;}catch(NumberFormatException e){throw error();}
        }
        depth--;return result;
    }
    private String string()throws IOException{
        if(text.charAt(at++)!='"')throw error();StringBuilder s=new StringBuilder();
        while(at<text.length()){
            char c=text.charAt(at++);if(c=='"')return s.toString();if(c<32)throw error();
            if(c=='\\'){
                if(at>=text.length())throw error();char e=text.charAt(at++);
                switch(e){case '"':case '\\':case '/':c=e;break;case 'b':c='\b';break;case 'f':c='\f';break;case 'n':c='\n';break;case 'r':c='\r';break;case 't':c='\t';break;case 'u':
                    if(at+4>text.length())throw error();try{c=(char)Integer.parseInt(text.substring(at,at+4),16);}catch(NumberFormatException ex){throw error();}at+=4;break;
                    default:throw error();
                }
            }
            s.append(c);
        }
        throw error();
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> obj(Object value){return value instanceof Map?(Map<String,Object>)value:Collections.emptyMap();}
    @SuppressWarnings("unchecked") public static List<Object> list(Object value){return value instanceof List?(List<Object>)value:Collections.emptyList();}
    public static int integer(Object value,int fallback){return value instanceof Number?((Number)value).intValue():fallback;}
    public static float number(Object value,float fallback){return value instanceof Number?((Number)value).floatValue():fallback;}
}
