package tv.home.app;
import android.os.*; import android.util.Log; import java.util.*; import java.util.concurrent.*; import okhttp3.OkHttpClient;
import org.schabi.newpipe.extractor.*; import org.schabi.newpipe.extractor.downloader.*; import org.schabi.newpipe.extractor.localization.*; import org.schabi.newpipe.extractor.stream.*;
/** Finds playable HD streams for a YouTube trailer, live, and keeps recent ones so focus feels instant. */
final class Trailer {
  static final String UA="Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0";
  static final class Streams{ String video,audio; int height; long at; }
  interface Got{ void got(Streams s); }
  private static final OkHttpClient C=new OkHttpClient.Builder().readTimeout(30,TimeUnit.SECONDS).build();
  private static final ExecutorService E=Executors.newFixedThreadPool(2); private static final Handler H=new Handler(Looper.getMainLooper());
  private static final Map<String,Streams> CACHE=Collections.synchronizedMap(new LinkedHashMap<String,Streams>(32,.75f,true){ protected boolean removeEldestEntry(Map.Entry<String,Streams> e){ return size()>30; } });
  private static final Set<String> BUSY=Collections.synchronizedSet(new HashSet<>()); private static boolean ready;
  private static final class D extends Downloader { public Response execute(Request r) throws java.io.IOException {
    okhttp3.RequestBody b=r.dataToSend()==null?null:okhttp3.RequestBody.create(r.dataToSend()); okhttp3.Request.Builder q=new okhttp3.Request.Builder().url(r.url()).method(r.httpMethod(),b).header("User-Agent",UA);
    for(Map.Entry<String,List<String>> e:r.headers().entrySet()){ q.removeHeader(e.getKey()); for(String v:e.getValue()) q.addHeader(e.getKey(),v); }
    try(okhttp3.Response s=C.newCall(q.build()).execute()){ return new Response(s.code(),s.message(),s.headers().toMultimap(),s.body()==null?null:s.body().string(),s.request().url().toString()); } } }
  private static synchronized void init(){ if(!ready){ NewPipe.init(new D(),new Localization("en","IN"),new ContentCountry("IN")); ready=true; } }
  private static Streams fetch(String id) throws Exception { init(); long t=SystemClock.elapsedRealtime(); StreamInfo i=StreamInfo.getInfo(ServiceList.YouTube,"https://www.youtube.com/watch?v="+id); Streams s=new Streams(); int best=0;
    for(VideoStream v:i.getVideoOnlyStreams()){ int h=v.getHeight(); if(v.isUrl()&&h<=1080&&h>best&&String.valueOf(v.getCodec()).startsWith("avc1")){ best=h; s.video=v.getContent(); } }
    for(AudioStream a:i.getAudioStreams()) if(a.isUrl()&&a.getFormat()==MediaFormat.M4A) s.audio=a.getContent();
    s.height=best; s.at=SystemClock.elapsedRealtime(); Log.i("TVHOME","trailer "+id+" "+best+"p in "+(s.at-t)+"ms"); return s.video==null?null:s; }
  private static Streams fresh(String id){ Streams s=CACHE.get(id); return s!=null&&SystemClock.elapsedRealtime()-s.at<3_000_000?s:null; }
  static void prefetch(String id){ if(id==null||fresh(id)!=null||!BUSY.add(id)) return; E.execute(()->{ try{ Streams s=fetch(id); if(s!=null) CACHE.put(id,s); }catch(Exception e){ Log.w("TVHOME","prefetch "+id+": "+e); } BUSY.remove(id); }); }
  static void get(String id,Got g){ Streams c=fresh(id); if(c!=null){ g.got(c); return; } E.execute(()->{ Streams s=null; try{ s=fetch(id); if(s!=null) CACHE.put(id,s); }catch(Exception e){ Log.w("TVHOME","trailer "+id+": "+e); } Streams f=s; H.post(()->g.got(f)); }); }
}
