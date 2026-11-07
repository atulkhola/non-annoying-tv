package tv.home.app;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.*;

/**
 * The screen draws itself. A ring of dots opens into the grid, the real layout appears as a blueprint, and a scan
 * line resolves it into the interface. The real UI is fully laid out underneath: this view only covers what the
 * scan line hasn't reached. Everything is two batched draws plus the line, so it stays light on this GPU.
 */
final class Blueprint extends View {
  static final class Part { final RectF box; final String label; final float radius; final boolean text; int at;
    Part(RectF box, String label, float radius, boolean text) { this.box = box; this.label = label; this.radius = radius; this.text = text; } }
  interface Source { List<Part> parts(); }

  private final float u; private final float[] field; private float far;
  private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), mark = new Paint(Paint.ANTI_ALIAS_FLAG), word = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(), fade = new Paint();
  private final Matrix shift = new Matrix(); private final LinearGradient edge;
  private boolean active, brief; private long began; private long go = -1; private Source source; private Runnable done, onGo; private List<Part> parts; private float[] segs = new float[0]; private int segCount;

  Blueprint(Context c, float unit, Typeface mono) {
    super(c); u = unit; setVisibility(GONE);
    List<float[]> p = new ArrayList<>(); int gap = Tokens.BOOT_DOT_GAP, W = Tokens.CANVAS_WIDTH, H = Tokens.CANVAS_HEIGHT;
    for (int y = gap / 2; y < H; y += gap) for (int x = gap / 2; x < W; x += gap) p.add(new float[]{x * u, y * u, (float) Math.hypot(x - W / 2f, y - H / 2f)});
    p.sort((a, b) -> Float.compare(a[2], b[2])); far = p.get(p.size() - 1)[2]; field = new float[p.size() * 2]; for (int i = 0; i < p.size(); i++) { field[i * 2] = p.get(i)[0]; field[i * 2 + 1] = p.get(i)[1]; }
    int ink = Tokens.COLOR_INK & 0xffffff;
    dot.setColor(0x47000000 | ink); dot.setStrokeWidth(2 * u); dot.setStrokeCap(Paint.Cap.SQUARE);
    line.setColor(0xb8000000 | ink); line.setStrokeWidth(1.5f * u); mark.setColor(Tokens.COLOR_INK); mark.setStrokeWidth(2 * u); mark.setStyle(Paint.Style.STROKE);
    word.setColor(0xcc000000 | ink); word.setTextSize(Tokens.CARD_LABEL_SIZE * u); word.setTypeface(mono); fill.setColor(Tokens.COLOR_ROOM);
    int room = Tokens.COLOR_ROOM & 0xffffff; edge = new LinearGradient(0, 0, 0, Tokens.BOOT_FEATHER * u, room, 0xff000000 | room, Shader.TileMode.CLAMP); fade.setShader(edge);
  }

  boolean active() { return active; }

  /** The press that woke the TV can arrive just after the splash starts, so it mustn't count as a skip. */
  boolean settled() { return SystemClock.uptimeMillis() - began > 1200; }

  /** Full sequence. The ring holds until go() says the screen underneath is ready. */
  void start(Source s, Runnable onGo, Runnable done) { begin(false, s, onGo, done); go = -1; }

  /** A short pass over the lower screen, for picks. */
  void brief(Source s, Runnable done) { begin(true, s, null, done); go = began; }

  void go() { if (active && go < 0) { go = Math.max(SystemClock.uptimeMillis(), began + Tokens.BOOT_RING_MS); if (onGo != null) onGo.run(); } }

  private void begin(boolean b, Source s, Runnable g, Runnable d) { brief = b; source = s; onGo = g; done = d; parts = null; segCount = 0; active = true; began = SystemClock.uptimeMillis(); setVisibility(VISIBLE); invalidate(); }

  void stop() { if (!active) return; active = false; setVisibility(GONE); parts = null; Runnable d = done; done = null; if (d != null) d.run(); }

  private static float ease(float x) { x = Math.min(Math.max(x, 0), 1); return 1 - (1 - x) * (1 - x) * (1 - x); }
  private static float inOut(float x) { x = Math.min(Math.max(x, 0), 1); return x < .5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2; }

  private void trace() {
    parts = source.parts(); List<float[]> s = new ArrayList<>(); float gap = Tokens.BOOT_LINE_GAP * u;
    for (Part p : parts) {
      p.at = s.size(); RectF b = p.box;
      if (p.text) { float y = b.centerY(); for (float x = b.left; x < b.right; x += gap) s.add(new float[]{x, y, x + 1.5f * u, y}); continue; }
      float r = Math.min(p.radius, b.height() / 2);
      run(s, b.left + r, b.top, b.right - r, b.top, gap); arc(s, b.right - r, b.top + r, r, -90); run(s, b.right, b.top + r, b.right, b.bottom - r, gap); arc(s, b.right - r, b.bottom - r, r, 0);
      run(s, b.right - r, b.bottom, b.left + r, b.bottom, gap); arc(s, b.left + r, b.bottom - r, r, 90); run(s, b.left, b.bottom - r, b.left, b.top + r, gap); arc(s, b.left + r, b.top + r, r, 180);
    }
    segCount = s.size(); segs = new float[segCount * 4]; for (int i = 0; i < segCount; i++) System.arraycopy(s.get(i), 0, segs, i * 4, 4);
  }

  private static void run(List<float[]> s, float x0, float y0, float x1, float y1, float gap) { int n = Math.max(1, Math.round((float) Math.hypot(x1 - x0, y1 - y0) / gap)); for (int i = 0; i < n; i++) { float a = i / (float) n, b = (i + .45f) / n; s.add(new float[]{x0 + (x1 - x0) * a, y0 + (y1 - y0) * a, x0 + (x1 - x0) * b, y0 + (y1 - y0) * b}); } }

  private static void arc(List<float[]> s, float cx, float cy, float r, float deg) { if (r <= 0) return; for (int i = 0; i < 4; i++) { double a = Math.toRadians(deg + i * 22.5), b = a + Math.PI / 14; s.add(new float[]{cx + r * (float) Math.cos(a), cy + r * (float) Math.sin(a), cx + r * (float) Math.cos(b), cy + r * (float) Math.sin(b)}); } }

  @Override protected void onDraw(Canvas c) {
    if (!active) return;
    long now = SystemClock.uptimeMillis(); float W = getWidth(), H = getHeight();
    int ring = Tokens.BOOT_RING_MS, fieldMs = Tokens.BOOT_FIELD_MS, mapMs = brief ? Tokens.BOOT_SHORT_MS / 2 : Tokens.BOOT_MAP_MS, scanMs = brief ? Tokens.BOOT_SHORT_MS : Tokens.BOOT_SCAN_MS;
    float t = go < 0 ? Math.min(now - began, ring) : ring + now - go - (brief ? ring : 0) + 0f;
    if (go >= 0 && !brief) t = ring + (now - go);
    float lead = brief ? 200 : ring + fieldMs / 2f, scanAt = lead + mapMs + (brief ? 0 : Tokens.BOOT_HOLD_MS);
    if (t > scanAt + scanMs) { stop(); return; }
    if (parts == null && t >= lead) trace();
    float top = brief ? (Tokens.HOME_INFO_BOTTOM - 400) * u : 0, y = top + inOut((t - scanAt) / scanMs) * (H - top), f = Tokens.BOOT_FEATHER * u;
    c.drawRect(0, y, W, H, fill);
    if (y > top) { shift.setTranslate(0, y - f); edge.setLocalMatrix(shift); c.drawRect(0, y - f, W, y, fade); }
    int save = c.save(); c.clipRect(0, y, W, H);
    if (!brief) {
      float spin = (now - began) / 2600f, open = 1 - ease((t - ring) / (fieldMs * .7f));
      if (open > 0) { float r = Tokens.BOOT_RING_RADIUS * u * (1 + (1 - open) * 1.2f) * ease(Math.max(now - began, 1) / (ring / 1.4f)); for (int i = 0; i < Tokens.BOOT_RING_DOTS; i++) { double a = i * 2 * Math.PI / Tokens.BOOT_RING_DOTS + spin; boolean big = i % 9 == 0; mark.setStyle(Paint.Style.FILL); mark.setAlpha(Math.round(open * (big ? 230 : 128))); c.drawCircle(W / 2 + r * (float) Math.cos(a), H / 2 + r * (float) Math.sin(a), (big ? 3.5f : 2f) * u, mark); } mark.setStyle(Paint.Style.STROKE); mark.setAlpha(255); }
      float wave = ease((t - ring * .8f) / fieldMs) * 1.02f; int n = Math.round(field.length / 2f * wave * wave); if (n > 0) c.drawPoints(field, 0, Math.min(n, field.length / 2) * 2, dot);
    }
    if (parts != null && segCount > 0) {
      int n = Math.min(segCount, Math.round(ease((t - lead) / mapMs) * segCount)); if (n > 0) c.drawLines(segs, 0, n * 4, line);
      if (!brief) { Part cur = null; for (Part p : parts) if (p.label != null && p.at <= n) cur = p;
        if (cur != null) { float m = 10 * u, e = 14 * u, x0 = cur.box.left - m, y0 = cur.box.top - m, x1 = cur.box.right + m, y1 = cur.box.bottom + m;
          float[][] k = {{x0, y0, 1, 1}, {x1, y0, -1, 1}, {x0, y1, 1, -1}, {x1, y1, -1, -1}}; for (float[] q : k) { c.drawLine(q[0], q[1], q[0] + q[2] * e, q[1], mark); c.drawLine(q[0], q[1], q[0], q[1] + q[3] * e, mark); }
          c.drawText(cur.label + " " + Math.round(cur.box.width() / u) + "×" + Math.round(cur.box.height() / u), Math.min(x0, W - 340 * u), Math.max(y0 - 12 * u, 30 * u), word); } }
    }
    c.restoreToCount(save);
    if (t >= scanAt) { for (float x = 0; x < W; x += 10 * u) c.drawLine(x, y, x + 3 * u, y, mark); mark.setStyle(Paint.Style.FILL); c.drawCircle(Tokens.SAFE_LEFT * u, y, 4 * u, mark); c.drawCircle(W - Tokens.SAFE_RIGHT * u, y, 4 * u, mark); mark.setStyle(Paint.Style.STROKE); }
    invalidate();
  }
}
