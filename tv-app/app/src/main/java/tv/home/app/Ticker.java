package tv.home.app;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.dynamicanimation.animation.*;

/**
 * The chip's ticker. The mark works alone for a while, then steps aside so the mic prompt can come in, and the two keep
 * trading places. The chip's width follows on a spring, so it stretches instead of switching.
 */
final class Ticker extends FrameLayout {
  private static final int PX = Tokens.TICKER_PIXEL;
  private final Sprite art; private final TextView words; private final Handler later = new Handler(Looper.getMainLooper());
  private final SpringAnimation width; private final FloatValueHolder now = new FloatValueHolder(0); private final int solo, step, chipGap; private int beside;
  private boolean wanted = true, running, thinking; private final View clock; private final java.util.function.IntConsumer sized; private final int padding; private final int textColour; private int line, reveal = -1; private final int longest; private Runnable settled; private final java.util.List<String> lines = new java.util.ArrayList<>();

  Ticker(Context c, TextView prompt, View time, float density, java.util.function.IntConsumer onWidth) {
    super(c); words = prompt; clock = time; sized = onWidth; padding = Math.round(Tokens.CHIP_PAD_X * density) * 2; textColour = prompt.getCurrentTextColor(); step = Math.round(PX * density); art = new Sprite(c, step); int gap = Math.round(Tokens.TICKER_GAP * density);
    solo = MarkArt.WIDTH * step + gap; longest = Math.round((Tokens.GRID_CONTENT / Tokens.TICKER_THINK_SCALE - Tokens.CHIP_PAD_X * 2 - MarkArt.WIDTH * PX - PX * 4) * density); chipGap = Math.round(Tokens.CHIP_GAP * density);
    addView(art, new LayoutParams(MarkArt.WIDTH * step, MarkArt.HEIGHT * step, Gravity.CENTER_VERTICAL | Gravity.START));
    art.setTranslationY(-4 * step);
    LayoutParams w = new LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.START); beside = 26 * step + gap; w.leftMargin = beside; addView(words, w); words.setAlpha(0f);
    width = new SpringAnimation(now); width.setSpring(new SpringForce(solo).setStiffness(Tokens.SPRING_MORPH_STIFFNESS).setDampingRatio(Tokens.SPRING_MORPH_DAMPING));
    width.addUpdateListener((a, v, s) -> { setWide(v); arrived(v); }); width.addEndListener((a, stopped, v, s) -> arrived(reveal >= 0 ? reveal : v)); now.setValue(solo);
    setClipChildren(false);
  }

  private void setWide(float v) { if (getLayoutParams() == null) return; getLayoutParams().width = Math.max(0, Math.round(v)); requestLayout(); }

  int soloWidth() { return solo; }

  @Override protected void onWindowVisibilityChanged(int v) { super.onWindowVisibilityChanged(v); if (v == View.VISIBLE && wanted) begin(); else halt(); }

  /** It waits until the app has settled, because a busy first few seconds would make it stutter. */
  private void begin() { if (running) return; running = true; art.pose(MarkArt.IDLE); words.animate().alpha(0f).setStartDelay(0).setDuration(Tokens.MOTION_FADE_MS).start(); width.animateToFinalPosition(solo); later.postDelayed(this::alone, 3500); }

  /** Claude is searching. The clock steps out and he works alone until Claude has something real to say. */
  void think(boolean on) {
    if (thinking == on) return; thinking = on; halt(); later.removeCallbacksAndMessages(null); reveal = -1; lines.clear(); line = 0; wanted = true; art.animate().alpha(1f).setDuration(Tokens.MOTION_FADE_MS).start();
    if (on) {
      running = true; steps(MarkArt.WRITE, 0, this::scribble); margin(MarkArt.WIDTH * step + step * 4); words.setSingleLine(true); words.setEllipsize(android.text.TextUtils.TruncateAt.END); words.setMaxWidth(longest);
      words.animate().cancel(); words.setAlpha(0f); words.setText("");
      clock.animate().alpha(0f).setDuration(140).withEndAction(() -> { if (!thinking) return; int w = clock.getWidth(); clock.setVisibility(GONE); jump(now.getValue() + w); resize(MarkArt.WIDTH * step, null); }).start();
      return;
    }
    words.animate().alpha(0f).setStartDelay(0).setDuration(140).withEndAction(() -> {
      if (thinking) return; words.setText(Tokens.COPY_PROMPT); words.setTextColor(textColour); words.setMaxWidth(Integer.MAX_VALUE); margin(beside);
      clock.measure(0, 0); int w = clock.getMeasuredWidth(); sized.accept(solo + w + padding); width.animateToFinalPosition(solo + w);
      later.postDelayed(() -> { if (thinking) return; clock.setVisibility(VISIBLE); clock.setAlpha(0f); clock.animate().alpha(1f).setDuration(Tokens.MOTION_FADE_MS).start(); jump(solo); begin(); }, 520);
    }).start();
  }

  /** A line Claude wrote for this search. */
  void say(String text) { if (!thinking) return; lines.add(text); if (lines.size() == 1) next(); }

  private void next() {
    if (!thinking || lines.isEmpty()) return; String text = lines.get(line++ % lines.size()); if (text.contentEquals(words.getText())) { later.postDelayed(this::next, Tokens.TICKER_LINE_MS); return; }
    words.animate().alpha(0f).setStartDelay(0).setDuration(140).withEndAction(() -> {
      if (!thinking) return; words.setText(text); words.setTextColor(Tokens.COLOR_INK); words.setTranslationX(0); words.measure(0, 0);
      resize(((LayoutParams) words.getLayoutParams()).leftMargin + words.getMeasuredWidth(), this::showWords);
    }).start();
    later.postDelayed(this::next, Tokens.TICKER_LINE_MS);
  }

  private void showWords() { words.animate().alpha(1f).setStartDelay(0).setDuration(Tokens.MOTION_FADE_MS).start(); }

  /** The pill takes its new size first. Whatever it holds only appears once it has arrived, so nothing can overrun the edge. */
  private void resize(int to, Runnable then) {
    reveal = to; settled = then; sized.accept(to + padding); width.animateToFinalPosition(to); arrived(now.getValue());
  }

  private void arrived(float v) { if (reveal >= 0 && Math.abs(v - reveal) < 3) { reveal = -1; Runnable r = settled; settled = null; if (r != null) r.run(); } }

  private void scribble() { steps(MarkArt.WRITE, 0, this::scribble); }

  private void margin(int m) { ((LayoutParams) words.getLayoutParams()).leftMargin = m; words.requestLayout(); }

  private void jump(float v) { width.cancel(); now.setValue(v); width.setStartValue(v); width.getSpring().setFinalPosition(v); setWide(v); }

  private void halt() { running = false; later.removeCallbacksAndMessages(null); }

  private void steps(int[][] s, int i, Runnable done) {
    if (!running) return; if (i == s.length) { done.run(); return; }
    art.pose(s[i][0]); later.postDelayed(() -> steps(s, i + 1, done), s[i][1]);
  }

  private void alone() { steps(MarkArt.ROUTINE, 0, () -> write(android.os.SystemClock.uptimeMillis() + Tokens.TICKER_WRITE_MS)); }

  private void write(long until) {
    if (android.os.SystemClock.uptimeMillis() < until) steps(MarkArt.WRITE, 0, () -> write(until));
    else steps(MarkArt.SETTLE, 0, this::aside);
  }

  /** He moves over with the chip's growing edge, and the words take the room he left. */
  private void aside() {
    words.measure(0, 0); words.setTranslationX(6 * step);
    resize(((LayoutParams) words.getLayoutParams()).leftMargin + words.getMeasuredWidth() + chipGap, () -> words.animate().alpha(1f).translationX(0).setStartDelay(0).setDuration(Tokens.MOTION_FADE_MS).start());
    later.postDelayed(() -> { art.pose(MarkArt.TILT); later.postDelayed(() -> art.pose(MarkArt.IDLE), 370); }, Tokens.TICKER_MIC_MS / 2);
    later.postDelayed(this::back, Tokens.TICKER_MIC_MS);
  }

  private void back() {
    words.animate().alpha(0f).translationX(6 * step).setStartDelay(0).setDuration(Tokens.MOTION_FADE_MS).withEndAction(() -> width.animateToFinalPosition(solo)).start();
    later.postDelayed(this::alone, 1100);
  }

  /** He is drawn from his own pixels, so he stays sharp at any size. */
  private static final class Sprite extends View {
    private final Bitmap[] poses = new Bitmap[MarkArt.POSES.length]; private final Paint paint = new Paint(); private final Rect to = new Rect(); private int at = MarkArt.IDLE; private final int step;

    Sprite(Context c, int step) {
      super(c); this.step = step; paint.setFilterBitmap(false);
      for (int n = 0; n < poses.length; n++) {
        Bitmap b = Bitmap.createBitmap(MarkArt.WIDTH, MarkArt.HEIGHT, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < MarkArt.HEIGHT; y++) for (int x = 0; x < MarkArt.WIDTH; x++) b.setPixel(x, y, colour(MarkArt.POSES[n][y].charAt(x)));
        poses[n] = b;
      }
    }

    private static int colour(char c) { return c == 'R' ? Tokens.COLOR_MARK : c == 'D' ? Tokens.COLOR_MARKDARK : c == 'K' ? Tokens.COLOR_ROOM : c == 'P' ? Tokens.COLOR_INK : 0; }

    void pose(int n) { if (n != at) { at = n; invalidate(); } }

    @Override protected void onDraw(Canvas c) { to.set(0, 0, MarkArt.WIDTH * step, MarkArt.HEIGHT * step); c.drawBitmap(poses[at], null, to, paint); }
  }
}
