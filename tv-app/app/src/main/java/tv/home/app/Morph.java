package tv.home.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import androidx.dynamicanimation.animation.*;

/** A card growing into the whole screen, and back into the exact place it came from. */
final class Morph extends View {
  private final Paint art = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), full = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Matrix m = new Matrix(); private final RectF from = new RectF(), now = new RectF();
  private final SpringAnimation spring; private float p; private Bitmap a, b; private BitmapShader sa, sb; private float radius; private Runnable arrived; private boolean open;

  Morph(Context c) {
    super(c); setVisibility(GONE);
    FloatValueHolder h = new FloatValueHolder(0); spring = new SpringAnimation(h);
    spring.setSpring(new SpringForce(0).setStiffness(Tokens.SPRING_MORPH_STIFFNESS).setDampingRatio(Tokens.SPRING_MORPH_DAMPING)); spring.setMinimumVisibleChange(.002f);
    spring.addUpdateListener((x, v, s) -> { p = v; invalidate(); if (arrived != null && (open ? p > .97f : p < .03f)) { Runnable r = arrived; arrived = null; r.run(); } });
    spring.addEndListener((x, cancel, v, s) -> { if (!open && !cancel) setVisibility(GONE); });
  }

  boolean isOpen() { return open; }

  void open(Bitmap card, Bitmap backdrop, RectF rect, float r, Runnable done) {
    a = card; b = backdrop != null ? backdrop : card; sa = new BitmapShader(a, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP); sb = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    art.setShader(sa); full.setShader(sb); from.set(rect); radius = r; arrived = done; open = true; spring.cancel(); spring.setStartValue(p = 0); setVisibility(VISIBLE); spring.animateToFinalPosition(1f);
  }

  void close(RectF rect, Runnable done) { if (rect != null) from.set(rect); open = false; arrived = done; spring.animateToFinalPosition(0f); }

  void hide() { spring.cancel(); open = false; arrived = null; p = 0; setVisibility(GONE); }

  private void cover(BitmapShader s, Bitmap bmp) { float k = Math.max(now.width() / bmp.getWidth(), now.height() / bmp.getHeight()); m.setScale(k, k); m.postTranslate(now.centerX() - bmp.getWidth() * k / 2, now.centerY() - bmp.getHeight() * k / 2); s.setLocalMatrix(m); }

  @Override protected void onDraw(Canvas c) {
    if (a == null) return; float W = getWidth(), H = getHeight();
    now.set(from.left * (1 - p), from.top * (1 - p), from.right + (W - from.right) * p, from.bottom + (H - from.bottom) * p); float r = radius * Math.max(0, 1 - p);
    cover(sa, a); art.setAlpha(255); c.drawRoundRect(now, r, r, art);
    float mix = Math.min(1, Math.max(0, (p - .15f) / .6f)); if (mix > 0) { cover(sb, b); full.setAlpha(Math.round(mix * 255)); c.drawRoundRect(now, r, r, full); }
  }
}
