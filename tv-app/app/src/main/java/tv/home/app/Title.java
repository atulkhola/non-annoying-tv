package tv.home.app;
import org.json.*; import java.util.*;
/** One title as sent by the engine. */
final class Title {
  String id,title,type,overview,poster,backdrop,logo,trailer,why,service,link,stremio,note; int year,runtime,seasons; double rating; boolean subscribed; long leavingAt; List<String> genres=new ArrayList<>(),languages=new ArrayList<>();
  String system; // set on More-row items: a package to open, or "about"
  static Title system(String name,String action,String why){ Title t=new Title(); t.title=name; t.system=action; t.why=why; return t; }
  static Title from(JSONObject o){ Title t=new Title(); t.id=o.optString("id"); t.title=o.optString("title"); t.type=o.optString("type"); t.overview=o.optString("overview"); t.poster=s(o,"poster"); t.backdrop=s(o,"backdrop"); t.logo=s(o,"logo"); t.trailer=s(o,"trailer"); t.why=s(o,"why"); t.stremio=o.optString("stremio");
    t.year=o.optInt("year"); t.runtime=o.optInt("runtime"); t.seasons=o.optInt("seasons"); t.rating=o.optDouble("rating",0); t.leavingAt=o.optLong("leaving_at",0);
    JSONObject p=o.optJSONObject("open"); if(p!=null){ t.service=p.optString("service"); t.link=p.optString("link"); t.subscribed=p.optBoolean("subscribed"); t.note=s(p,"note"); }
    arr(o.optJSONArray("genres"),t.genres); arr(o.optJSONArray("languages"),t.languages); return t; }
  private static String s(JSONObject o,String k){ return o.isNull(k)?null:o.optString(k,null); }
  private static void arr(JSONArray a,List<String> out){ if(a!=null) for(int i=0;i<a.length();i++) out.add(a.optString(i)); }
  /** A true poster for tall rows. */
  String tall(){ return id!=null&&id.startsWith("tt")?"https://images.metahub.space/poster/medium/"+id+"/img":null; }
  String serviceName(){ if(service==null) return ""; switch(service){ case "netflix": return "Netflix"; case "prime": return "Prime Video"; case "hotstar": return "JioHotstar"; case "sonyliv": return "SonyLIV"; default: return "Stremio"; } }
  String pkg(){ if(service==null) return "com.stremio.one"; switch(service){ case "netflix": return "com.netflix.ninja"; case "prime": return "com.amazon.amazonvideo.livingroom"; case "hotstar": return "in.startv.hotstar"; case "sonyliv": return "com.sonyliv"; default: return "com.stremio.one"; } }
  String meta(){ List<String> m=new ArrayList<>(); if(rating>0) m.add(String.format(Locale.US,"IMDb %.1f",rating)); if(year>0) m.add(""+year); if("series".equals(type)&&seasons>0) m.add(seasons+(seasons>1?" seasons":" season")); else if(runtime>0) m.add(runtime/60+"h "+String.format(Locale.US,"%02d",runtime%60)+"m"); if(!genres.isEmpty()) m.add(cap(genres.get(0))); int n=0; for(String[] l:LANGS) if(languages.contains(l[0])&&n<2){ m.add(l[1]); n++; } return String.join(" · ",m); }
  private static final String[][] LANGS={{"hin","Hindi"},{"eng","English"},{"tam","Tamil"},{"tel","Telugu"},{"mal","Malayalam"},{"kan","Kannada"}};
  private static String cap(String s){ return s.isEmpty()?s:s.substring(0,1).toUpperCase(Locale.US)+s.substring(1); }
  String under(){ if(leavingAt>0) return "Leaves "+new java.text.SimpleDateFormat("d MMM",Locale.US).format(new Date(leavingAt*1000)); return subscribed?serviceName():"Stremio"; }
}
