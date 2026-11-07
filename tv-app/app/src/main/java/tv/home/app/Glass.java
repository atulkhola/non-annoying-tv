package tv.home.app;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/**
 * Glass without blurring anything per frame. The picture behind the UI is shrunk, saturated and blurred once on a
 * background thread. Each glass surface then paints its own slice of that small bitmap, so a surface costs one
 * shader draw. This TV has no window blur, and a SurfaceView can't be sampled live, so video reaches the bitmap
 * through a low-resolution PixelCopy twice a second.
 */
final class Glass {
  static final int W = Tokens.CANVAS_WIDTH / Tokens.GLASS_SAMPLE, H = Tokens.CANVAS_HEIGHT / Tokens.GLASS_SAMPLE;
  private final ExecutorService work = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private final List<WeakReference<View>> views = new ArrayList<>();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Matrix matrix = new Matrix();
  private final int[] spot = new int[2];
  private final float zoom;
  private BitmapShader now, was;
  private float mix = 1f;
  private ValueAnimator fade;
  private int job;

  Glass(float unit) { zoom = Tokens.GLASS_SAMPLE * unit; }

  void add(View v) { views.add(new WeakReference<>(v)); }

  void refresh() { for (Iterator<WeakReference<View>> i = views.iterator(); i.hasNext(); ) { View v = i.next().get(); if (v == null) i.remove(); else v.invalidate(); } }

  /** Takes a new picture. The bitmap is copied, so the caller may reuse it. */
  void feed(Bitmap src, int fadeMs) {
    int me = ++job;
    work.execute(() -> {
      if (me != job) return;
      Bitmap out = render(src);
      main.post(() -> { if (me == job) swap(out, fadeMs); });
    });
  }

  private static Bitmap render(Bitmap src) {
    Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    ColorMatrix m = new ColorMatrix(); m.setSaturation(Tokens.GLASS_SATURATE);
    Paint p = new Paint(Paint.FILTER_BITMAP_FLAG); p.setColorFilter(new ColorMatrixColorFilter(m));
    new Canvas(out).drawBitmap(src, new Rect(0, 0, src.getWidth(), src.getHeight()), new Rect(0, 0, W, H), p);
    int[] a = new int[W * H], b = new int[W * H]; out.getPixels(a, 0, W, 0, 0, W, H);
    int r = Tokens.GLASS_BLUR / Tokens.GLASS_SAMPLE;
    for (int i = 0; i < 3; i++) { box(a, b, W, H, r); box(b, a, H, W, r); }
    out.setPixels(a, 0, W, 0, 0, W, H);
    return out;
  }

  /** One running-sum box pass. It writes transposed, so two calls cover both axes. */
  private static void box(int[] in, int[] out, int w, int h, int r) {
    float k = 1f / (2 * r + 1);
    for (int y = 0; y < h; y++) {
      int row = y * w, sr = 0, sg = 0, sb = 0;
      for (int i = -r; i <= r; i++) { int p = in[row + Math.min(Math.max(i, 0), w - 1)]; sr += p >> 16 & 255; sg += p >> 8 & 255; sb += p & 255; }
      for (int x = 0; x < w; x++) {
        out[x * h + y] = 0xff000000 | Math.round(sr * k) << 16 | Math.round(sg * k) << 8 | Math.round(sb * k);
        int add = in[row + Math.min(x + r + 1, w - 1)], drop = in[row + Math.max(x - r, 0)];
        sr += (add >> 16 & 255) - (drop >> 16 & 255); sg += (add >> 8 & 255) - (drop >> 8 & 255); sb += (add & 255) - (drop & 255);
      }
    }
  }

  private void swap(Bitmap b, int fadeMs) {
    was = now; now = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    if (fade != null) fade.cancel();
    if (was == null || fadeMs <= 0) { mix = 1f; refresh(); return; }
    mix = 0f; fade = ValueAnimator.ofFloat(0f, 1f).setDuration(fadeMs);
    fade.addUpdateListener(x -> { mix = (float) x.getAnimatedValue(); refresh(); }); fade.start();
  }

  private void lay(Canvas c, BitmapShader s, RectF box, float radius, int alpha) {
    s.setLocalMatrix(matrix); paint.setShader(s); paint.setAlpha(alpha); c.drawRoundRect(box, radius, radius, paint); paint.setShader(null);
  }

  /** Paints the slice of the picture that sits behind this view. */
  void paint(Canvas c, View v, RectF box, float radius) {
    if (now == null) return;
    v.getLocationOnScreen(spot); matrix.setScale(zoom, zoom); matrix.postTranslate(-spot[0], -spot[1]);
    if (was != null && mix < 1f) lay(c, was, box, radius, 255);
    lay(c, now, box, radius, Math.round(mix * 255));
  }

  /** A glass surface: the blurred slice, then tint, then a hairline edge lit from above. */
  static final class Surface extends Drawable {
    final Glass glass; final View view; final float radius; final int tint; boolean solid, clear; int shade;
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG); final RectF box = new RectF();
    Surface(Glass g, View v, float radius, int tint) { glass = g; view = v; this.radius = radius; this.tint = tint; edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(1f); g.add(v); }
    @Override protected void onBoundsChange(Rect b) { box.set(b); edge.setShader(new LinearGradient(0, b.top, 0, b.top + Math.min(b.height(), 2 * radius + 1), Tokens.GLASS_SHEEN, Tokens.GLASS_EDGE, Shader.TileMode.CLAMP)); }
    @Override public void draw(Canvas c) {
      if (clear) return;
      if (solid) { fill.setColor(Tokens.COLOR_INK); c.drawRoundRect(box, radius, radius, fill); return; }
      glass.paint(c, view, box, radius);
      fill.setColor(tint); c.drawRoundRect(box, radius, radius, fill);
      if (shade != 0) { fill.setColor(shade); c.drawRoundRect(box, radius, radius, fill); }
      if (radius > 0) { RectF e = new RectF(box); e.inset(.5f, .5f); c.drawRoundRect(e, radius, radius, edge); }
    }
    @Override public void setAlpha(int a) { } @Override public void setColorFilter(ColorFilter f) { } @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
  }

  /** The light that sweeps a card once as focus lands on it. One instance moves from card to card. */
  static final class Sheen extends Drawable {
    final Paint p = new Paint(); final Matrix m = new Matrix(); float at = 2f; LinearGradient g; ValueAnimator run; int w;
    void sweep(int ms) { if (run != null) run.cancel(); run = ValueAnimator.ofFloat(-1f, 2f).setDuration(ms); run.addUpdateListener(x -> { at = (float) x.getAnimatedValue(); invalidateSelf(); }); run.start(); }
    @Override protected void onBoundsChange(Rect b) { w = b.width(); g = new LinearGradient(0, 0, w * .5f, b.height() * .5f, new int[]{0, 0x38f4f1ea, 0}, null, Shader.TileMode.CLAMP); }
    @Override public void draw(Canvas c) { if (g == null || at >= 2f) return; m.setTranslate(at * w, 0); g.setLocalMatrix(m); p.setShader(g); c.drawRect(getBounds(), p); }
    @Override public void setAlpha(int a) { } @Override public void setColorFilter(ColorFilter f) { } @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
  }
}
