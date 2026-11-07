package tv.home.app;
import android.accessibilityservice.AccessibilityService; import android.content.*; import android.os.*; import android.util.Log; import android.view.KeyEvent;
import android.view.accessibility.*; import java.util.*;
/** Catches Home and the TLDR key, returns to our screen when Google's home appears, and reads the words Google hears. */
public class HomeService extends AccessibilityService {
  static final String T="TVHOME", PREFS="home"; private String last=""; private long lastAt; private boolean listening; private final Handler h=new Handler(Looper.getMainLooper());
  static boolean takeover(Context c){ return c.getSharedPreferences(PREFS,0).getBoolean("takeover",true); }
  static void setTakeover(Context c,boolean on){ c.getSharedPreferences(PREFS,0).edit().putBoolean("takeover",on).apply(); }
  private void open(String mode,String text){ Intent i=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT).putExtra("mode",mode); if(text!=null) i.putExtra("text",text); startActivity(i); }
  @Override protected boolean onKeyEvent(KeyEvent e){ int k=e.getKeyCode(); if(!takeover(this)) return false; if(k==KeyEvent.KEYCODE_HOME||k==KeyEvent.KEYCODE_BUTTON_13){ if(e.getAction()==KeyEvent.ACTION_UP) open("home",null); return true; } return false; }
  private void read(AccessibilityNodeInfo n,Set<String> out,int d){ if(n==null||d>14) return; if(n.getText()!=null&&n.getText().length()>0) out.add(n.getText().toString()); for(int i=0;i<n.getChildCount();i++) read(n.getChild(i),out,d+1); }
  private final Runnable finish=new Runnable(){ public void run(){ if(!listening||last.isEmpty()) return; if(SystemClock.elapsedRealtime()-lastAt<SILENCE){ h.postDelayed(this,200); return; } hand(); } };
  /** Google ends speech itself when it's sure, so this is only the fallback for when it doesn't. People pause while thinking. */
  private static final int SILENCE=3200;
  /** Google's own prompts sit in the same window as the words it hears. */
  private static final java.util.regex.Pattern PROMPT=java.util.regex.Pattern.compile("(?i)^\\s*(hold (the|down)|press (and|the)|listening|speak now|say something|try (saying|asking)|ask (me|gemini|google)|how can i help|what can i|hi,|hello,|talk to|tap |use your voice)");
  private long quietUntil, guardUntil, wokeAt;
  /** Power on and waking from the screensaver always land on our screen, with the splash. */
  private void wake(long guardMs){ long now=SystemClock.elapsedRealtime(); guardUntil=now+guardMs; if(now-wokeAt<3000||!takeover(this)) return; wokeAt=now; Log.i(T,"wake"); open("wake",null); }
  private final BroadcastReceiver power=new BroadcastReceiver(){ @Override public void onReceive(Context c,Intent i){ wake(12000); } };
  private void hand(){ if(!listening) return; listening=false; String said=last; last=""; quietUntil=SystemClock.elapsedRealtime()+4000; Log.i(T,"heard: "+said); performGlobalAction(GLOBAL_ACTION_BACK); h.postDelayed(()->open("ask",said),120); }
  private String lastDump="";
  @Override public void onAccessibilityEvent(AccessibilityEvent e){ if(!takeover(this)) return; CharSequence p=e.getPackageName(); if(p==null) return; String pkg=p.toString(), cls=String.valueOf(e.getClassName());
    if(pkg.contains("katniss")){ if(SystemClock.elapsedRealtime()<quietUntil) return; AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null||!String.valueOf(root.getPackageName()).contains("katniss")) return; Set<String> o=new LinkedHashSet<>(); read(root,o,0); String best=""; for(String s:o) if(s.length()>best.length()&&!PROMPT.matcher(s).find()) best=s; if(best.length()>2&&!best.equals(last)){ last=best; lastAt=SystemClock.elapsedRealtime(); if(!listening){ listening=true; h.postDelayed(finish,SILENCE); } } return; }
    if(e.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
    if(pkg.contains("launcherx")){ if(cls.contains("gemini")){ if(listening) hand(); else if(!last.isEmpty()){ listening=true; hand(); } return; } long now=SystemClock.elapsedRealtime(); if(listening||now<quietUntil) return;
      // Google's launcher comes up late after a boot and covers us, so for a short while we keep taking the screen back.
      if(now<guardUntil){ Log.i(T,"front over "+cls); open("front",null); return; } if(cls.contains("HomeActivity")||cls.contains("MainActivity")) open("home",null); } }
  @Override public void onInterrupt(){} @Override protected void onServiceConnected(){ Log.i(T,"service connected"); IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_ON); f.addAction(Intent.ACTION_DREAMING_STOPPED); registerReceiver(power,f,Context.RECEIVER_EXPORTED); if(SystemClock.elapsedRealtime()<300000) wake(45000); }
  @Override public void onDestroy(){ try{ unregisterReceiver(power); }catch(Exception ignored){} super.onDestroy(); }
}
