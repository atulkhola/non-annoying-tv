package tv.home.app;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.provider.Settings;
import android.util.Log;
import java.util.*;

/** UI sounds. Pan follows progress along a row and pitch drops a little with each row, so moving has a place in the room. */
final class Sound {
  private static final Map<String, Float> GAIN = new HashMap<>();
  static {
    GAIN.put("focus", Tokens.SOUND_FOCUS_GAIN); GAIN.put("row", Tokens.SOUND_ROW_GAIN); GAIN.put("edge", Tokens.SOUND_EDGE_GAIN); GAIN.put("select", Tokens.SOUND_SELECT_GAIN);
    GAIN.put("back", Tokens.SOUND_BACK_GAIN); GAIN.put("home", Tokens.SOUND_HOME_GAIN); GAIN.put("listenEnd", Tokens.SOUND_LISTEN_END_GAIN);
    GAIN.put("picks", Tokens.SOUND_PICKS_GAIN); GAIN.put("boot", Tokens.SOUND_BOOT_GAIN); GAIN.put("build", Tokens.SOUND_BUILD_GAIN); GAIN.put("handoff", Tokens.SOUND_HANDOFF_GAIN); GAIN.put("error", Tokens.SOUND_ERROR_GAIN);
  }
  private final SoundPool pool; private final Map<String, Integer> ids = new HashMap<>(); private final Context app; private long last;

  Sound(Context c) {
    app = c.getApplicationContext();
    pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
    for (String k : GAIN.keySet()) try (AssetFileDescriptor f = app.getAssets().openFd("sound/" + k + ".wav")) { ids.put(k, pool.load(f, 1)); } catch (Exception e) { Log.w(MainActivity.T, "sound " + k, e); }
  }

  void play(String name) { play(name, .5f, 0); }

  /** place runs 0 to 1 along the row; depth is the row number. */
  void play(String name, float place, int depth) {
    Integer id = ids.get(name); long now = android.os.SystemClock.uptimeMillis();
    if (id == null || now - last < Tokens.SOUND_MIN_GAP_MS) return;
    if (Settings.System.getInt(app.getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 1) == 0) return;
    last = now;
    float v = Tokens.SOUND_MASTER * GAIN.get(name), pan = (place * 2 - 1) * Tokens.SOUND_PAN;
    float rate = (float) Math.max(.5, Math.pow(Tokens.SOUND_ROW_PITCH, depth));
    pool.play(id, v * Math.min(1f, 1f - pan), v * Math.min(1f, 1f + pan), 1, 0, rate);
  }

  void release() { pool.release(); }
}
