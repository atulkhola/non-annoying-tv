package tv.home.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import androidx.dynamicanimation.animation.*;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.*;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import java.util.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class MainActivity extends Activity {
  static final String T = "TVHOME";
  static final int ROOM = Tokens.COLOR_ROOM, INK = Tokens.COLOR_INK, SOFT = Tokens.COLOR_SOFT, VOICE = Tokens.COLOR_VOICE;
  static final int H0 = Tokens.HOME_ROW_TITLE_Y, STEP = Tokens.ROW_STEP, CW = Tokens.CARD_WIDTH, CH = Tokens.CARD_HEIGHT, GAP = Tokens.GRID_GUTTER, LEFT = Tokens.SAFE_LEFT;
  static final int TRACK_Y = Tokens.ROW_TITLE_HEIGHT + Tokens.ROW_TITLE_GAP, INFO_SHIFT = Tokens.HOME_INFO_BOTTOM - Tokens.BROWSE_INFO_BOTTOM;

  enum Mode { HOME, ASK, RESULT, DETAIL }
  static final int WIDE = 0, TALL = 1, SPOT = 2, APPS = 3;
  static final float SPOT_SMALL = (float) CW / Tokens.FORM_SPOT_WIDTH;
  static final class Row {
    int first, at, form, y; boolean system, grid; String name, section, note; List<Title> items = new ArrayList<>(); ViewGroup track; TextView head; View band; FrameLayout box; List<View> cards = new ArrayList<>();
    int w() { return form == TALL ? Tokens.FORM_TALL_WIDTH : form == SPOT ? Tokens.FORM_SPOT_WIDTH : form == APPS ? Tokens.FORM_TILE_WIDTH : CW; }
    int h() { return form == TALL ? Tokens.FORM_TALL_HEIGHT : form == SPOT ? Tokens.FORM_SPOT_HEIGHT : form == APPS ? Tokens.FORM_TILE_HEIGHT : CH; }
    int cols() { return form == WIDE ? Tokens.LENS_COLUMNS : 6; }
    int height() { return grid ? Tokens.FORM_GRID_STEP : TRACK_Y + h() + Tokens.FORM_ROW_GAP; }
  }

  float u; Mode mode = Mode.HOME; final Handler h = new Handler(Looper.getMainLooper());
  FrameLayout stage, rowsBox, askBox, detail; SurfaceView video; ImageView bgA, bgB, logo, dLogo; View dim, soft; boolean flip; Glass glass; Sound sound; Glass.Sheen sheen = new Glass.Sheen(); Glass.Surface veilGlass; Blueprint plan; long stoppedAt; Lens lens, btnLens; Morph morph; ImageView morphed; RectF morphRect; float travel; boolean comeBack; final Map<View, Map<DynamicAnimation.ViewProperty, SpringAnimation>> springs = new WeakHashMap<>(); com.bumptech.glide.request.target.CustomTarget<Bitmap> small; float soundVolume = .4f;
  Ticker ticker; LinearLayout info, dock, btns, top; TextView name, meta, why, where, clock, hint, said, veil, dName, dMeta, dText;
  List<Row> rows = new ArrayList<>(), home = new ArrayList<>(); int r; Title cur; ExoPlayer player; int vidW, vidH; boolean videoOn; Runnable dwell, idle; int ask; int btn; Mode before = Mode.HOME; long downAt; boolean longDone; String heardText = "";
  Typeface[] faces = new Typeface[10];

  int px(float v) { return Math.round(v * u); }

  Typeface font(int w) {
    int i = w / 100;
    if (faces[i] == null) faces[i] = new Typeface.Builder(getAssets(), "geist-sans.ttf").setFontVariationSettings("'wght' " + w).build();
    return faces[i];
  }

  Typeface serif, pixel, pixelCircle, pixelLine; Typeface[] monos = new Typeface[10];
  Typeface serif() { if (serif == null) serif = Typeface.createFromAsset(getAssets(), "geist-serif.ttf"); return serif; }
  Typeface pixel() { if (pixel == null) pixel = Typeface.createFromAsset(getAssets(), "geist-pixel.ttf"); return pixel; }
  /** The typeface a type token names, so a font change in Figma reaches the TV without touching this file. */
  Typeface face(String family, int weight) { switch (family) { case "serif": return serif(); case "mono": return mono(weight); case "pixel": return pixel(); case "pixelCircle": return pixelCircle(); case "pixelLine": return pixelLine(); default: return font(weight); } }

  Typeface pixelCircle() { if (pixelCircle == null) pixelCircle = Typeface.createFromAsset(getAssets(), "geist-pixel-circle.ttf"); return pixelCircle; }
  Typeface pixelLine() { if (pixelLine == null) pixelLine = Typeface.createFromAsset(getAssets(), "geist-pixel-line.ttf"); return pixelLine; }
  Typeface mono(int w) { int i = w / 100; if (monos[i] == null) monos[i] = new Typeface.Builder(getAssets(), "geist-mono.ttf").setFontVariationSettings("'wght' " + w).build(); return monos[i]; }

  TextView text(float size, int weight, int color) {
    TextView t = new TextView(this);
    t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size)); t.setTypeface(font(weight)); t.setTextColor(color); t.setIncludeFontPadding(false);
    return t;
  }

  /** A text view set from one type token: size, line height and tracking. */
  TextView type(float size, float line, float tracking, Typeface face, int color) {
    TextView t = new TextView(this); t.setIncludeFontPadding(false); t.setTypeface(face); t.setTextColor(color); fit(t, size, line, tracking); return t;
  }

  void fit(TextView t, float size, float line, float tracking) { t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size)); t.setLineHeight(px(line)); t.setLetterSpacing(tracking); }

  void clamp(TextView t, int lines) { t.setMaxLines(lines); t.setEllipsize(TextUtils.TruncateAt.END); }

  /** Retargets from wherever the view is, keeping its speed, so a held key never makes motion restart. */
  void spring(View v, DynamicAnimation.ViewProperty p, float to, float k, float d) {
    Map<DynamicAnimation.ViewProperty, SpringAnimation> m = springs.computeIfAbsent(v, x -> new HashMap<>()); SpringAnimation a = m.get(p);
    if (a == null) { a = new SpringAnimation(v, p); a.setSpring(new SpringForce(to)); m.put(p, a); }
    a.getSpring().setStiffness(k).setDampingRatio(d); a.animateToFinalPosition(to);
  }

  void slide(View v, DynamicAnimation.ViewProperty p, float to) { spring(v, p, to, Tokens.SPRING_SLIDE_STIFFNESS, Tokens.SPRING_SLIDE_DAMPING); }

  void cue(String name) { sound.play(name); if (videoOn && !"focus".equals(name) && !"row".equals(name)) { player.setVolume(soundVolume * Tokens.SOUND_VIDEO_DUCK); h.postDelayed(() -> player.setVolume(soundVolume), Tokens.SOUND_DUCK_MS * 2L); } }

  FrameLayout.LayoutParams at(int w, int hgt, float x, float y) {
    FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, hgt); p.leftMargin = px(x); p.topMargin = px(y); return p;
  }

  void round(View v, float radius) {
    v.setOutlineProvider(new ViewOutlineProvider() { public void getOutline(View x, Outline o) { o.setRoundRect(0, 0, x.getWidth(), x.getHeight(), px(radius)); } });
    v.setClipToOutline(true);
  }

  GradientDrawable fade(GradientDrawable.Orientation o, int... c) { return new GradientDrawable(o, c); }

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    Point size = new Point(); getWindowManager().getDefaultDisplay().getRealSize(size); u = size.x / 1920f;
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); getWindow().setDecorFitsSystemWindows(false); getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
    glass = new Glass(u); sound = new Sound(this);
    stage = new FrameLayout(this); stage.setBackgroundColor(ROOM); stage.setClipChildren(false);
    video = new SurfaceView(this); stage.addView(video, new FrameLayout.LayoutParams(size.x, size.y, Gravity.CENTER));
    bgA = new ImageView(this); bgB = new ImageView(this);
    for (ImageView i : new ImageView[]{bgA, bgB}) { i.setScaleType(ImageView.ScaleType.CENTER_CROP); i.setAlpha(0f); stage.addView(i, new FrameLayout.LayoutParams(-1, -1)); }
    soft = new View(this); soft.setBackground(new Glass.Surface(glass, soft, 0, 0)); soft.setAlpha(0f); soft.setVisibility(View.GONE); stage.addView(soft, new FrameLayout.LayoutParams(-1, -1));
    Bitmap sb = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888); Canvas sc = new Canvas(sb);
    Drawable g1 = fade(GradientDrawable.Orientation.BOTTOM_TOP, ROOM, 0xe607080b, 0x4707080b, 0x0007080b), g2 = fade(GradientDrawable.Orientation.LEFT_RIGHT, 0xcc07080b, 0x5c07080b, 0x0007080b); g1.setBounds(0, 0, 480, 270); g2.setBounds(0, 0, 480, 270); g1.draw(sc); g2.draw(sc);
    View s1 = new View(this); s1.setBackground(new BitmapDrawable(getResources(), sb)); stage.addView(s1, new FrameLayout.LayoutParams(-1, -1));
    dim = new View(this); dim.setBackgroundColor(ROOM); dim.setAlpha(0f); stage.addView(dim, new FrameLayout.LayoutParams(-1, -1));

    info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setGravity(Gravity.BOTTOM);
    logo = new ImageView(this); logo.setAdjustViewBounds(true); logo.setMaxWidth(px(Tokens.LOGO_HOME_WIDTH)); logo.setMaxHeight(px(Tokens.LOGO_HOME_HEIGHT)); logo.setScaleType(ImageView.ScaleType.FIT_START);
    logo.addOnLayoutChangeListener((v, l1, t1, r1, b1, l0, t0, r0, b0) -> { v.setPivotX(0); v.setPivotY(v.getHeight()); });
    name = type(Tokens.TITLE_SIZE, Tokens.TITLE_LINE, Tokens.TITLE_TRACKING, face(Tokens.TITLE_FAMILY, 400), INK); meta = type(Tokens.META_SIZE, Tokens.META_LINE, Tokens.META_TRACKING, face(Tokens.META_FAMILY, 400), SOFT); why = type(Tokens.REASON_SIZE, Tokens.REASON_LINE, Tokens.REASON_TRACKING, face(Tokens.REASON_FAMILY, 400), INK); where = type(Tokens.SERVICE_SIZE, Tokens.SERVICE_LINE, Tokens.SERVICE_TRACKING, face(Tokens.SERVICE_FAMILY, 400), SOFT);
    meta.setAllCaps(Tokens.META_UPPER); where.setAllCaps(Tokens.SERVICE_UPPER); clamp(name, Tokens.CLAMP_TITLE); clamp(why, Tokens.CLAMP_REASON); name.setMaxWidth(px(Tokens.PANEL_TEXT_WIDTH)); why.setMaxWidth(px(Tokens.PANEL_TEXT_WIDTH));
    info.addView(logo, new LinearLayout.LayoutParams(-2, -2)); info.addView(name);
    LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(-2, -2); m.topMargin = px(Tokens.SPACE_S); info.addView(meta, m);
    LinearLayout.LayoutParams w = new LinearLayout.LayoutParams(-2, -2); w.topMargin = px(Tokens.SPACE_XS); info.addView(why, w);
    LinearLayout.LayoutParams wh = new LinearLayout.LayoutParams(-2, -2); wh.topMargin = px(Tokens.SPACE_XS); info.addView(where, wh);
    FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(px(Tokens.GRID_CONTENT), -2, Gravity.BOTTOM); ip.leftMargin = px(LEFT); ip.bottomMargin = px(Tokens.CANVAS_HEIGHT - Tokens.HOME_INFO_BOTTOM); stage.addView(info, ip);

    lens = new Lens(this, glass, u); stage.addView(lens, new FrameLayout.LayoutParams(-1, -1));
    rowsBox = new FrameLayout(this); rowsBox.setClipChildren(false); stage.addView(rowsBox, new FrameLayout.LayoutParams(-1, -1));
    morph = new Morph(this); stage.addView(morph, new FrameLayout.LayoutParams(-1, -1));

    dock = new LinearLayout(this); dock.setOrientation(LinearLayout.HORIZONTAL); stage.addView(dock, at(-2, -2, LEFT, Tokens.SAFE_TOP));

    top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(px(Tokens.CHIP_PAD_X), 0, px(Tokens.CHIP_PAD_X), 0); top.setBackground(new Glass.Surface(glass, top, px(Tokens.CHIP_RADIUS), Tokens.GLASS_TINT));
    hint = type(Tokens.PROMPT_SIZE, Tokens.PROMPT_LINE, Tokens.PROMPT_TRACKING, face(Tokens.PROMPT_FAMILY, 400), Tokens.COLOR_PROMPT); hint.setText(Tokens.COPY_PROMPT); clock = type(Tokens.CLOCK_SIZE, Tokens.CLOCK_LINE, Tokens.CLOCK_TRACKING, face(Tokens.CLOCK_FAMILY, 400), INK);
    ticker = new Ticker(this, hint, clock, u, this::centreChip); top.setClipChildren(false); top.addView(ticker, new LinearLayout.LayoutParams(ticker.soloWidth(), -1)); top.addView(clock);
    FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, px(Tokens.CHIP_HEIGHT), Gravity.END); tp.rightMargin = px(Tokens.SAFE_RIGHT); tp.topMargin = px(Tokens.SAFE_TOP); stage.addView(top, tp);

    askBox = new FrameLayout(this); askBox.setVisibility(View.GONE); said = type(Tokens.SAID_SIZE, Tokens.SAID_LINE, Tokens.SAID_TRACKING, face(Tokens.SAID_FAMILY, 400), INK); said.setMaxWidth(px(Tokens.GRID_CONTENT)); clamp(said, Tokens.CLAMP_SAID);
    int listenGravity = Tokens.LISTEN_CENTERED ? Gravity.CENTER_HORIZONTAL : Gravity.START; said.setGravity(listenGravity);
    FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(px(Tokens.GRID_CONTENT), -2, Gravity.CENTER_VERTICAL | (Tokens.LISTEN_CENTERED ? Gravity.CENTER_HORIZONTAL : Gravity.START)); if (!Tokens.LISTEN_CENTERED) ap.leftMargin = px(LEFT); askBox.addView(said, ap); stage.addView(askBox, new FrameLayout.LayoutParams(-1, -1));

    detail = new FrameLayout(this); detail.setVisibility(View.GONE);
    LinearLayout dl = new LinearLayout(this); dl.setOrientation(LinearLayout.VERTICAL); int pad = px(Tokens.PANEL_PAD); dl.setPadding(pad, pad, pad, pad); dl.setBackground(new Glass.Surface(glass, dl, px(Tokens.PANEL_RADIUS), Tokens.GLASS_PANEL)); dl.setClipChildren(false); dl.setClipToPadding(false);
    dLogo = new ImageView(this); dLogo.setAdjustViewBounds(true); dLogo.setMaxWidth(px(Tokens.LOGO_HOME_WIDTH)); dLogo.setMaxHeight(px(Tokens.LOGO_HOME_HEIGHT)); dLogo.setScaleType(ImageView.ScaleType.FIT_START);
    dName = type(Tokens.TITLE_SIZE, Tokens.TITLE_LINE, Tokens.TITLE_TRACKING, face(Tokens.TITLE_FAMILY, 400), INK); clamp(dName, Tokens.CLAMP_TITLE); dMeta = type(Tokens.META_SIZE, Tokens.META_LINE, Tokens.META_TRACKING, face(Tokens.META_FAMILY, 400), SOFT); dMeta.setAllCaps(Tokens.META_UPPER); dText = type(Tokens.OVERVIEW_SIZE, Tokens.OVERVIEW_LINE, Tokens.OVERVIEW_TRACKING, face(Tokens.OVERVIEW_FAMILY, 400), INK); dText.setMaxWidth(px(Tokens.PANEL_TEXT_WIDTH)); clamp(dText, Tokens.CLAMP_OVERVIEW);
    btns = new LinearLayout(this); btns.setClipChildren(false); btns.setClipToPadding(false);
    FrameLayout btnWrap = new FrameLayout(this); btnWrap.setClipChildren(false); btnWrap.setClipToPadding(false); btnLens = new Lens(this, glass, u); btnWrap.addView(btnLens, new FrameLayout.LayoutParams(-1, -1)); btnWrap.addView(btns, new FrameLayout.LayoutParams(-2, -2));
    dl.addView(dLogo, new LinearLayout.LayoutParams(-2, -2)); dl.addView(dName);
    LinearLayout.LayoutParams a1 = new LinearLayout.LayoutParams(-2, -2); a1.topMargin = px(Tokens.SPACE_S); dl.addView(dMeta, a1);
    LinearLayout.LayoutParams a2 = new LinearLayout.LayoutParams(-2, -2); a2.topMargin = px(Tokens.SPACE_XS); dl.addView(dText, a2);
    LinearLayout.LayoutParams a3 = new LinearLayout.LayoutParams(-2, -2); a3.topMargin = px(Tokens.SPACE_L); dl.addView(btnWrap, a3);
    FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(px(Tokens.PANEL_WIDTH), -2, Gravity.BOTTOM); dp.leftMargin = px(LEFT); dp.bottomMargin = px(Tokens.SAFE_BOTTOM); detail.addView(dl, dp); stage.addView(detail, new FrameLayout.LayoutParams(-1, -1));

    veil = type(Tokens.HANDOFF_SIZE, Tokens.HANDOFF_LINE, Tokens.HANDOFF_TRACKING, face(Tokens.HANDOFF_FAMILY, 400), INK); veilGlass = new Glass.Surface(glass, veil, 0, Tokens.GLASS_VEIL); veil.setBackground(veilGlass); veil.setPadding(px(LEFT), 0, px(LEFT), 0); veil.setGravity(Gravity.CENTER); veil.setAlpha(0f); veil.setVisibility(View.GONE); stage.addView(veil, new FrameLayout.LayoutParams(-1, -1));
    plan = new Blueprint(this, u, mono(400)); stage.addView(plan, new FrameLayout.LayoutParams(-1, -1)); plan.start(() -> parts(false), () -> sound.play("boot"), null);
    setContentView(stage);
    WindowInsetsController wc = getWindow().getInsetsController(); if (wc != null) { wc.hide(WindowInsets.Type.systemBars()); wc.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }

    player = new ExoPlayer.Builder(this).build(); player.setVideoSurfaceView(video); player.setVolume(0.4f); player.setRepeatMode(Player.REPEAT_MODE_ONE);
    player.addListener(new Player.Listener() {
      @Override public void onVideoSizeChanged(VideoSize s) { if (s.width > 0) { vidW = s.width; vidH = s.height; cover(1f); } }
      @Override public void onRenderedFirstFrame() { h.postDelayed(MainActivity.this::measureBars, 700); }
      @Override public void onPlayerError(PlaybackException e) { Log.w(T, "player: " + e.getErrorCodeName()); stopVideo(); }
    });
    tick(); String cached = getSharedPreferences("home", 0).getString("feed", null);
    if (cached != null) showFeed(cached);
    Api.feed(new Api.Done<String>() {
      public void ok(String s) { getSharedPreferences("home", 0).edit().putString("feed", s).apply(); if (home.isEmpty() || mode == Mode.HOME && r == 0 && rowAt().at == 0) showFeed(s); }
      public void fail(String why2) { Log.w(T, "feed: " + why2); if (home.isEmpty()) { plan.stop(); showMessage("Can't reach your catalog. Check the Wi-Fi and press OK to try again."); } }
    });
    handle(getIntent());
  }

  /** Every open gets its own feed. */
  void freshFeed() { Api.feed(new Api.Done<String>() { public void ok(String s) { getSharedPreferences("home", 0).edit().putString("feed", s).apply(); if (mode == Mode.HOME) showFeed(s); } public void fail(String why2) { Log.w(T, "feed: " + why2); } }); }

  void showMessage(String m) { veil.setBackgroundColor(ROOM); veil.setText(m); veil.setVisibility(View.VISIBLE); veil.setAlpha(1f); cue("error"); }

  void tick() {
    Calendar c = Calendar.getInstance(); int hr = c.get(Calendar.HOUR); clock.setText((hr == 0 ? 12 : hr) + ":" + String.format(Locale.US, "%02d", c.get(Calendar.MINUTE)) + (c.get(Calendar.AM_PM) == 0 ? " AM" : " PM"));
    h.postDelayed(this::tick, 15000);
  }

  void showFeed(String raw) {
    try {
      home = new ArrayList<>();
      for (Api.Row a : Api.parseFeed(raw)) { Row x = new Row(); x.name = a.name; x.items = a.items; x.section = a.section; x.note = a.note; x.form = "tall".equals(a.form) ? TALL : "spot".equals(a.form) ? SPOT : WIDE; home.add(x); }
      Row more = new Row(); more.name = "More"; more.system = true; more.form = APPS;
      more.items.add(Title.system("Google TV home", "google", "Go back to the standard Google TV screen."));
      more.items.add(Title.system("Netflix", "com.netflix.ninja", "Open Netflix.")); more.items.add(Title.system("Prime Video", "com.amazon.amazonvideo.livingroom", "Open Prime Video."));
      more.items.add(Title.system("JioHotstar", "in.startv.hotstar", "Open JioHotstar.")); more.items.add(Title.system("Stremio", "com.stremio.one", "Open Stremio."));
      more.items.add(Title.system("YouTube", "com.google.android.youtube.tv", "Open YouTube.")); more.items.add(Title.system("About", "about", "Where the information here comes from."));
      home.add(more); rows = home; r = 0; mode = Mode.HOME; cur = null; veil.setBackground(veilGlass); veil.setVisibility(View.GONE); build(false); focus(0); h.postDelayed(plan::go, 3000);
    } catch (Exception e) { Log.w(T, "bad feed", e); }
  }

  Row rowAt() { return rows.get(r); }

  void build(boolean fresh) {
    lit = null; litRow = null; prevRow = -1; rowsBox.removeAllViews(); int y = H0;
    for (int i = 0; i < rows.size(); i++) { Row x = rows.get(i); x.first = 0; if (x.section != null) y += Tokens.FORM_BREAKER; x.y = y; makeRow(x, fresh); y += x.height(); }
  }

  void makeRow(Row x, boolean fresh) {
    x.box = new FrameLayout(this); x.box.setClipChildren(false); x.cards.clear();
    x.head = type(Tokens.ROW_TITLE_SIZE, Tokens.ROW_TITLE_LINE, Tokens.ROW_TITLE_TRACKING, font(600), SOFT); x.head.setText(x.name); x.head.setSingleLine(true); x.head.setEllipsize(TextUtils.TruncateAt.END); x.head.setMaxWidth(px(Tokens.GRID_CONTENT)); x.box.addView(x.head, at(-2, -2, LEFT, 0));
    x.track = x.form == SPOT ? new FrameLayout(this) : new LinearLayout(this) { @Override public boolean hasOverlappingRendering() { return false; } }; x.track.setClipChildren(false); x.track.setClipToPadding(false);
    x.box.addView(x.track, at(-2, -2, LEFT, TRACK_Y));
    rowsBox.addView(x.box, at(-1, px(x.height()), 0, x.y));
    if (x.section != null) {
      LinearLayout band = new LinearLayout(this); band.setOrientation(LinearLayout.VERTICAL);
      TextView big = type(Tokens.TITLE_SM_SIZE, Tokens.TITLE_SM_LINE, Tokens.TITLE_SM_TRACKING, face(Tokens.TITLE_SM_FAMILY, 400), INK); big.setText(x.section); band.addView(big);
      TextView note = type(Tokens.REASON_SM_SIZE, Tokens.REASON_SM_LINE, Tokens.REASON_SM_TRACKING, face(Tokens.REASON_SM_FAMILY, 400), SOFT); note.setText(x.note); band.addView(note);
      rowsBox.addView(band, at(px(Tokens.GRID_CONTENT), px(Tokens.FORM_BREAKER), LEFT, x.y - Tokens.FORM_BREAKER + Tokens.SPACE_S)); x.band = band;
    }
    int n = 0; for (Title t : x.items) addCard(x, t, fresh ? n++ : -1);
    if (x.form == SPOT) placeSpot(x, true);
  }

  /** Adds one card. A wave of zero or more makes it rise in, later ones after earlier ones. */
  void addCard(Row x, Title t, int wave) {
    View c = card(t, x); int label = Tokens.CARD_LABEL_GAP + Tokens.CARD_LABEL_HEIGHT;
    if (x.form == SPOT) { x.track.addView(c, new FrameLayout.LayoutParams(px(x.w()), px(x.h() + label))); View art = ((ViewGroup) c).getChildAt(0); art.setPivotX(0); art.setPivotY(px(x.h())); }
    else { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(px(x.w()), px(x.h() + label)); p.rightMargin = px(GAP); x.track.addView(c, p); }
    x.cards.add(c);
    if (wave >= 0) { c.setAlpha(0f); c.setTranslationY(px(Tokens.SPACE_XL)); long wait = 120 + wave * 60L; c.animate().alpha(1f).setStartDelay(wait).setDuration(Tokens.MOTION_FADE_MS).start(); h.postDelayed(() -> spring(c, DynamicAnimation.TRANSLATION_Y, 0, Tokens.SPRING_REVEAL_STIFFNESS, Tokens.SPRING_REVEAL_DAMPING), wait); }
  }

  /** Spotlight rows: the selected card is big and its neighbours are small, and they trade as you move. It fits one big and two small on the grid exactly. */
  float spotAt(Row x) { int step = CW + GAP, n = x.items.size(); return x.at == 0 ? 0 : x.at == n - 1 && n > 2 ? 2 * step : step; }

  void placeSpot(Row x, boolean snap) {
    int step = CW + GAP; float base = spotAt(x);
    for (int i = 0; i < x.cards.size(); i++) {
      float pos = i < x.at ? base - (x.at - i) * step : i == x.at ? base : base + x.w() + GAP + (i - x.at - 1) * step; View c = x.cards.get(i);
      if (snap) { c.setTranslationX(px(pos)); View art = ((ViewGroup) c).getChildAt(0); float k = i == x.at ? 1f : SPOT_SMALL; art.setScaleX(k); art.setScaleY(k); } else spring(c, DynamicAnimation.TRANSLATION_X, px(pos), Tokens.SPRING_MORPH_STIFFNESS, Tokens.SPRING_MORPH_DAMPING);
    }
  }

  View pill(String label) { TextView t = type(Tokens.PILL_SIZE, Tokens.PILL_LINE, Tokens.PILL_TRACKING, font(600), INK); t.setText(label); t.setGravity(Gravity.CENTER); t.setPadding(px(Tokens.PILL_PAD_X), 0, px(Tokens.PILL_PAD_X), 0); t.setBackground(new Glass.Surface(glass, t, px(Tokens.PILL_RADIUS), Tokens.GLASS_TINT)); return t; }

  /** Apps show their own TV banner, so the logo is always the real one. */
  Drawable banner(Title t) {
    if ("about".equals(t.system)) return null; String pkg = "google".equals(t.system) ? "com.google.android.apps.tv.launcherx" : t.system;
    try { Drawable d = getPackageManager().getApplicationBanner(pkg); return d != null ? d : getPackageManager().getApplicationIcon(pkg); } catch (Exception e) { return null; }
  }

  View card(Title t, Row x) {
    int w = x.w(), hgt = x.h(); FrameLayout c = new FrameLayout(this); c.setClipChildren(false);
    ImageView i = new ImageView(this); i.setScaleType(ImageView.ScaleType.CENTER_CROP); i.setBackgroundColor(Tokens.COLOR_WELL); round(i, Tokens.CARD_RADIUS);
    c.addView(i, new FrameLayout.LayoutParams(px(w), px(hgt)));
    TextView s = type(Tokens.CARD_LABEL_SIZE, Tokens.CARD_LABEL_LINE, Tokens.CARD_LABEL_TRACKING, face(Tokens.CARD_LABEL_FAMILY, 400), SOFT); s.setAllCaps(Tokens.CARD_LABEL_UPPER); s.setText(x.system ? "" : t.under()); s.setSingleLine(true); s.setVisibility(View.GONE); FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(px(w), -2); p.topMargin = px(hgt + Tokens.CARD_LABEL_GAP); c.addView(s, p);
    TextView plain = type(Tokens.REASON_SM_SIZE, Tokens.REASON_SM_LINE, Tokens.REASON_SM_TRACKING, face(Tokens.REASON_SM_FAMILY, 400), INK); plain.setText(t.title); clamp(plain, Tokens.CLAMP_TITLE); plain.setVisibility(View.GONE); FrameLayout.LayoutParams q = new FrameLayout.LayoutParams(px(w - 2 * Tokens.SPACE_S), -2, Gravity.BOTTOM); q.leftMargin = px(Tokens.SPACE_S); q.bottomMargin = px(Tokens.CARD_LABEL_GAP + Tokens.CARD_LABEL_HEIGHT + Tokens.SPACE_S); c.addView(plain, q);
    if (x.system) { Drawable d = banner(t); if (d != null) i.setImageDrawable(d); else plain.setVisibility(View.VISIBLE); return c; }
    String flat = t.poster != null ? t.poster : t.backdrop, art = x.form == TALL && t.tall() != null ? t.tall() : flat;
    if (art == null) plain.setVisibility(View.VISIBLE);
    else Glide.with(this).load(art).override(px(w), px(hgt)).format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565).dontAnimate().error(Glide.with(this).load(flat).override(px(w), px(hgt))).listener(new RequestListener<Drawable>() {
      public boolean onLoadFailed(GlideException e, Object m, Target<Drawable> z, boolean f) { if (flat == null) plain.setVisibility(View.VISIBLE); return false; }
      public boolean onResourceReady(Drawable d, Object m, Target<Drawable> z, DataSource ds, boolean f) { return false; }
    }).into(i);
    return c;
  }

  void liftPill(View c, boolean on) {
    float k = on ? Tokens.PILL_FOCUS_SCALE : 1f, st = on ? Tokens.SPRING_FOCUS_STIFFNESS : Tokens.SPRING_UNFOCUS_STIFFNESS, da = on ? Tokens.SPRING_FOCUS_DAMPING : Tokens.SPRING_UNFOCUS_DAMPING; spring(c, DynamicAnimation.SCALE_X, k, st, da); spring(c, DynamicAnimation.SCALE_Y, k, st, da);
    c.setElevation(on ? px(Tokens.SPACE_S) : 0); ((Glass.Surface) c.getBackground()).clear = on; c.invalidate();
  }

  void lift(View c, boolean on, Row x) {
    ImageView art = (ImageView) ((ViewGroup) c).getChildAt(0); boolean spot = x.form == SPOT; float k = spot ? (on ? 1f : SPOT_SMALL) : on ? Tokens.CARD_FOCUS_SCALE : 1f;
    float st = spot ? Tokens.SPRING_MORPH_STIFFNESS : on ? Tokens.SPRING_FOCUS_STIFFNESS : Tokens.SPRING_UNFOCUS_STIFFNESS, da = spot ? Tokens.SPRING_MORPH_DAMPING : on ? Tokens.SPRING_FOCUS_DAMPING : Tokens.SPRING_UNFOCUS_DAMPING; spring(art, DynamicAnimation.SCALE_X, k, st, da); spring(art, DynamicAnimation.SCALE_Y, k, st, da);
    art.setElevation(on ? px(Tokens.SPACE_S) : 0);
    View label = ((ViewGroup) c).getChildAt(1); ((TextView) label).setTextColor(on ? INK : SOFT); label.setTranslationY(on && !spot ? px(x.h() * (Tokens.CARD_FOCUS_SCALE - 1f) / 2f) : 0);
    if (on) { art.setForeground(sheen); sheen.sweep(Tokens.MOTION_FOCUS_MS * 2); } else if (art.getForeground() == sheen) art.setForeground(null);
  }

  View lit; int prevRow = -1; boolean posture;

  void focus(int dx) {
    wake();
    Row now = rowAt();
    if (r != prevRow) {
      for (int i = 0; i < rows.size(); i++) {
        Row x = rows.get(i); boolean on = i == r; x.head.setTextColor(on ? INK : SOFT); float a = i < r ? 0f : 1f; if (x.box.getAlpha() != a) x.box.setAlpha(a); x.track.setAlpha(on ? 1f : .46f); if (!x.system) for (View k : x.cards) ((ViewGroup) k).getChildAt(1).setVisibility(on && (i > 0 || mode == Mode.RESULT) ? View.VISIBLE : View.GONE);
        if (x.band != null) { x.band.animate().alpha(i > r ? 1f : 0f).setDuration(Tokens.MOTION_FOCUS_MS).start(); }
      }
      prevRow = r;
    }
    View c = now.cards.get(now.at);
    if (c != lit) { if (lit != null && (litRow == now || litRow.form != SPOT)) lift(lit, false, litRow); lift(c, true, now); lit = c; litRow = now; }
    if (dx != 0) travel = dx;
    if (now.form == SPOT) placeSpot(now, false); else slide(now.track, DynamicAnimation.TRANSLATION_X, -px(rowScroll(now)));
    moveLens(now);
    boolean deep = deep();
    slide(rowsBox, DynamicAnimation.TRANSLATION_Y, rowY());
    if (deep != posture) {
      posture = deep; slide(info, DynamicAnimation.TRANSLATION_Y, deep ? -px(INFO_SHIFT) : 0);
      float k = deep ? (float) Tokens.LOGO_BROWSE_HEIGHT / Tokens.LOGO_HOME_HEIGHT : 1f; logo.setPivotX(0); logo.setPivotY(logo.getHeight()); slide(logo, DynamicAnimation.SCALE_X, k); slide(logo, DynamicAnimation.SCALE_Y, k);
      if (deep) { why.setTypeface(face(Tokens.REASON_SM_FAMILY, 400)); name.setTypeface(face(Tokens.TITLE_SM_FAMILY, 400)); fit(why, Tokens.REASON_SM_SIZE, Tokens.REASON_SM_LINE, Tokens.REASON_SM_TRACKING); fit(name, Tokens.TITLE_SM_SIZE, Tokens.TITLE_SM_LINE, Tokens.TITLE_SM_TRACKING); } else { why.setTypeface(face(Tokens.REASON_FAMILY, 400)); name.setTypeface(face(Tokens.TITLE_FAMILY, 400)); fit(why, Tokens.REASON_SIZE, Tokens.REASON_LINE, Tokens.REASON_TRACKING); fit(name, Tokens.TITLE_SIZE, Tokens.TITLE_LINE, Tokens.TITLE_TRACKING); }
      why.setMaxWidth(px(deep ? Tokens.GRID_CONTENT : Tokens.PANEL_TEXT_WIDTH)); clamp(why, deep ? Tokens.CLAMP_REASON_BROWSE : Tokens.CLAMP_REASON); clamp(name, deep ? Tokens.CLAMP_TITLE_BROWSE : Tokens.CLAMP_TITLE);
    }
    rack(deep && !videoOn);
    float d = now.system ? .84f : deep ? .46f : 0f; if (dim.getAlpha() != d) { dim.setVisibility(View.VISIBLE); dim.animate().alpha(d).setDuration(300).withEndAction(() -> { if (dim.getAlpha() == 0f) dim.setVisibility(View.GONE); }).start(); }
    Title t = now.items.get(now.at);
    if (t != cur) { cur = t; h.removeCallbacks(showNext); showNext = () -> { if (cur == t) show(t); }; h.postDelayed(showNext, 140); }
    List<Title> it = now.items; int at = now.at;
    h.postDelayed(() -> { if (cur == t) for (int k = 1; k <= 2 && at + k < it.size(); k++) Trailer.prefetch(it.get(at + k).trailer); }, 1400);
  }

  Row litRow; Runnable showNext = () -> { };

  /** Rows ride high while browsing, and results always do, so the next rows show below. */
  boolean deep() { return r > 0 || mode == Mode.RESULT; }

  float rowY() { return deep() ? -px(rowAt().y - Tokens.BROWSE_ROW_TITLE_Y) : 0; }

  /** The focused card rides the left margin until the row's end reaches the right margin, then focus walks to it. */
  int rowScroll(Row x) {
    int step = x.w() + GAP, n = x.items.size(), vis = x.cols();
    if (x.at < x.first) x.first = x.at; if (x.at > x.first + vis - 1) x.first = x.at - vis + 1;
    return Math.min(x.first * step, Math.max(0, n * step - GAP - Tokens.GRID_CONTENT));
  }

  /** Where the selection belongs, worked out from the grid so it never chases a moving view. */
  void moveLens(Row x) {
    float top = (deep() ? Tokens.BROWSE_ROW_TITLE_Y : H0) + TRACK_Y, e = Tokens.LENS_EDGE; int w = x.w(), hgt = x.h();
    if (x.form == SPOT) { float left = LEFT + spotAt(x); lens.to(new RectF(px(left - e), px(top - e), px(left + w + e), px(top + hgt + e)), px(Tokens.CARD_RADIUS + e)); return; }
    float left = LEFT + x.at * (w + GAP) - rowScroll(x), gx = w * (Tokens.CARD_FOCUS_SCALE - 1) / 2 + e, gy = hgt * (Tokens.CARD_FOCUS_SCALE - 1) / 2 + e;
    lens.to(new RectF(px(left - gx), px(top - gy), px(left + w + gx), px(top + hgt + gy)), px(Tokens.CARD_RADIUS * Tokens.CARD_FOCUS_SCALE + e));
  }

  void moveBtnLens(boolean snap) { View c = btns.getChildAt(btn); if (c == null) return; if (c.getWidth() == 0) { c.post(() -> moveBtnLens(snap)); return; } float gw = c.getWidth() * (Tokens.PILL_FOCUS_SCALE - 1) / 2, gh = c.getHeight() * (Tokens.PILL_FOCUS_SCALE - 1) / 2; RectF q = new RectF(c.getLeft() - gw, c.getTop() - gh, c.getRight() + gw, c.getBottom() + gh); if (snap) btnLens.snap(q, px(Tokens.PILL_RADIUS * Tokens.PILL_FOCUS_SCALE)); else btnLens.to(q, px(Tokens.PILL_RADIUS * Tokens.PILL_FOCUS_SCALE)); }

  static Bitmap bits(Drawable d) { return d instanceof BitmapDrawable ? ((BitmapDrawable) d).getBitmap() : null; }

  /** The focused card grows until it is the screen. The rest of the page steps back as it does. */
  boolean grow(Runnable then) {
    Row x = rowAt(); if (x.system || mode == Mode.DETAIL || x.at >= x.cards.size()) return false;
    ImageView art = (ImageView) ((ViewGroup) x.cards.get(x.at)).getChildAt(0); Bitmap a = bits(art.getDrawable()); if (a == null) return false;
    int[] at = new int[2]; art.getLocationOnScreen(at); float w = art.getWidth() * art.getScaleX(), hh = art.getHeight() * art.getScaleY();
    quiet(); morphRect = new RectF(at[0], at[1], at[0] + w, at[1] + hh); morphed = art; art.setAlpha(0f);
    for (View v : new View[]{rowsBox, info, lens, dock}) v.animate().alpha(0f).setDuration(Tokens.MOTION_FOCUS_MS).start();
    morph.open(a, bits((flip ? bgA : bgB).getDrawable()), morphRect, px(Tokens.CARD_RADIUS * Tokens.CARD_FOCUS_SCALE), then); cue("select"); return true;
  }

  /** And back: it shrinks into the exact place it came from. */
  void shrink(Runnable then) {
    for (View v : new View[]{rowsBox, info, lens, dock}) { v.setVisibility(View.VISIBLE); v.animate().alpha(1f).setDuration(Tokens.MOTION_FADE_MS).start(); }
    ImageView art = morphed; morph.close(morphRect, () -> { if (art != null) art.setAlpha(1f); morphed = null; if (then != null) then.run(); });
  }

  /** Nowhere further to go, so the row gives a little and springs back. */
  void lean(float dx, float dy) {
    float v = Tokens.LENS_NUDGE * u; lens.lean(dx * v, dy * v); Row x = rowAt();
    if (dx != 0 && !x.system) { Map<DynamicAnimation.ViewProperty, SpringAnimation> m = springs.get(x.track); SpringAnimation a = m == null ? null : m.get(DynamicAnimation.TRANSLATION_X); if (a != null) { a.setStartVelocity(-dx * v); a.animateToFinalPosition(a.getSpring().getFinalPosition()); } }
    if (dy != 0) { Map<DynamicAnimation.ViewProperty, SpringAnimation> m = springs.get(rowsBox); SpringAnimation a = m == null ? null : m.get(DynamicAnimation.TRANSLATION_Y); if (a != null) { a.setStartVelocity(-dy * v); a.animateToFinalPosition(a.getSpring().getFinalPosition()); } }
  }


  /** The live screen, as blueprint parts in reading order. */
  List<Blueprint.Part> parts(boolean brief) {
    List<Blueprint.Part> out = new ArrayList<>(); int[] at = new int[2];
    if (!brief) { top.getLocationOnScreen(at); out.add(new Blueprint.Part(new RectF(at[0], at[1], at[0] + top.getWidth(), at[1] + top.getHeight()), "CHIP", px(Tokens.CHIP_RADIUS), false)); }
    if (logo.getVisibility() == View.VISIBLE && logo.getDrawable() != null) { logo.getLocationOnScreen(at); out.add(new Blueprint.Part(new RectF(at[0], at[1], at[0] + logo.getWidth(), at[1] + logo.getHeight()), "LOGO", 0, false)); }
    for (TextView t : new TextView[]{name, meta, why, where}) textParts(t, out);
    for (Row x : rows) {
      x.head.getLocationOnScreen(at); if (x.box.getAlpha() == 0f || at[1] > stage.getHeight()) continue; textParts(x.head, out);
      for (View c : x.cards) { View a = ((ViewGroup) c).getChildAt(0); a.getLocationOnScreen(at); float w = a.getWidth() * a.getScaleX(), hh = a.getHeight() * a.getScaleY(); if (at[0] > stage.getWidth() || at[0] + w < 0 || at[1] > stage.getHeight()) continue; out.add(new Blueprint.Part(new RectF(at[0], at[1], at[0] + w, at[1] + hh), x.system ? "APP" : "CARD", px(Tokens.CARD_RADIUS), false)); }
    }
    return out;
  }

  void textParts(TextView t, List<Blueprint.Part> out) {
    Layout l = t.getLayout(); if (l == null || t.getVisibility() != View.VISIBLE || t.length() == 0) return; int[] at = new int[2]; t.getLocationOnScreen(at);
    for (int i = 0; i < l.getLineCount(); i++) out.add(new Blueprint.Part(new RectF(at[0] + l.getLineLeft(i), at[1] + l.getLineTop(i), at[0] + l.getLineRight(i), at[1] + l.getLineBottom(i)), null, 0, true));
  }

  /** Runs the whole power-on sequence again when the TV has been away from this screen for a while. */
  void reboot() { Log.i(T, "splash"); stoppedAt = 0; goHome(); freshFeed(); plan.start(() -> parts(false), () -> sound.play("boot"), null); h.postDelayed(plan::go, 3000); }

  /** Rack focus: the picture goes soft while you browse and comes sharp again when a trailer takes over. */
  void rack(boolean on) {
    float to = on ? 1f : 0f; if (soft.getAlpha() == to && (on || soft.getVisibility() == View.GONE)) return;
    soft.setVisibility(View.VISIBLE); soft.animate().cancel(); soft.animate().alpha(to).setDuration(Tokens.MOTION_FADE_MS).withEndAction(() -> { if (!on) soft.setVisibility(View.GONE); }).start();
  }

  /** While a trailer plays, the glass follows it at low resolution. */
  final Bitmap[] frame = new Bitmap[1];
  final Runnable pump = new Runnable() { public void run() {
    if (!videoOn || !player.isPlaying()) return;
    if (frame[0] == null) frame[0] = Bitmap.createBitmap(Glass.W, Glass.H, Bitmap.Config.ARGB_8888);
    try { PixelCopy.request(video, frame[0], res -> { if (res == PixelCopy.SUCCESS) glass.feed(frame[0], 0); }, h); } catch (Exception ignored) { }
    h.postDelayed(this, 500);
  } };

  void feedGlass(String url) {
    if (small != null) Glide.with(this).clear(small);
    small = new com.bumptech.glide.request.target.CustomTarget<Bitmap>(Glass.W, Glass.H) {
      @Override public void onResourceReady(Bitmap b, com.bumptech.glide.request.transition.Transition<? super Bitmap> x) { glass.feed(b, Tokens.MOTION_FADE_MS); h.postDelayed(plan::go, 500); }
      @Override public void onLoadCleared(Drawable d) { }
    };
    Glide.with(this).asBitmap().load(url).centerCrop().into(small);
  }

  void show(Title t) {
    stopVideo();
    boolean sys = t.system != null;
    Glide.with(this).clear(logo); logo.setVisibility(View.GONE); name.setVisibility(View.GONE); logo.clearColorFilter();
    meta.setVisibility(sys ? View.GONE : View.VISIBLE); where.setVisibility(sys ? View.GONE : View.VISIBLE);
    if (sys) { name.setText(t.title); name.setVisibility(View.VISIBLE); why.setText(t.why); return; }
    name.setText(t.title); name.setVisibility(View.VISIBLE); logo.setImageDrawable(null); if (t.logo != null) Glide.with(this).load(t.logo).override(px(Tokens.LOGO_HOME_WIDTH), px(Tokens.LOGO_HOME_HEIGHT)).fitCenter().listener(logoCheck(logo)).into(logo);
    meta.setText(t.meta()); why.setText(t.why != null ? t.why : shortText(t.overview)); info.setAlpha(0f); info.animate().alpha(1f).setDuration(Tokens.MOTION_FOCUS_MS).start(); info.setTranslationX(px(Tokens.SPACE_L) * travel); slide(info, DynamicAnimation.TRANSLATION_X, 0);
    SpannableStringBuilder s = new SpannableStringBuilder();
    if (t.subscribed) { s.append("On "); int a = s.length(); s.append(t.serviceName()); s.setSpan(new ForegroundColorSpan(INK), a, s.length(), 0); if (t.leavingAt > 0) s.append(" · ").append(t.under()); }
    else { s.append("Not on your services · Opens in "); int a = s.length(); s.append("Stremio"); s.setSpan(new ForegroundColorSpan(INK), a, s.length(), 0); }
    if (t.note != null) s.append(" · ").append(t.note);
    where.setText(s);
    flip = !flip; ImageView in = flip ? bgA : bgB, out = flip ? bgB : bgA;
    in.bringToFront(); stageOrder();
    Glide.with(this).load(t.backdrop).override(px(1280), px(720)).format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565).centerCrop().dontAnimate().into(in);
    feedGlass(t.backdrop);
    in.animate().cancel(); out.animate().cancel(); in.setVisibility(View.VISIBLE); in.setAlpha(0f); in.animate().alpha(1f).setDuration(Tokens.MOTION_FADE_MS).start(); out.animate().alpha(0f).setDuration(Tokens.MOTION_FADE_MS).withEndAction(() -> out.setVisibility(View.INVISIBLE)).start();
    if (t.trailer != null) { dwell = () -> { if (cur == t) playTrailer(t); }; h.postDelayed(dwell, Tokens.MOTION_DWELL_MS + (plan.active() ? Tokens.BOOT_RING_MS + Tokens.BOOT_FIELD_MS + Tokens.BOOT_MAP_MS + Tokens.BOOT_SCAN_MS : 0)); }
  }

  void stageOrder() { for (View v : new View[]{stage.getChildAt(0)}) { } List<View> keep = new ArrayList<>(); for (int i = 0; i < stage.getChildCount(); i++) { View v = stage.getChildAt(i); if (v != video && v != bgA && v != bgB) keep.add(v); } for (View v : keep) v.bringToFront(); }

  String shortText(String o) { if (o == null) return ""; if (o.length() <= 96) return o; String c = o.substring(0, 96); int i = c.lastIndexOf(' '); return (i > 0 ? c.substring(0, i) : c) + "…"; }

  RequestListener<Drawable> logoCheck(ImageView v) {
    return new RequestListener<Drawable>() {
      public boolean onLoadFailed(GlideException e, Object m, Target<Drawable> t, boolean f) { Log.w(T, "logo failed " + m); return false; }
      public boolean onResourceReady(Drawable d, Object m, Target<Drawable> t, DataSource s, boolean f) {
        if (v == logo) { logo.setVisibility(View.VISIBLE); name.setVisibility(View.GONE); }
        Log.i(T, "logo ok " + d.getIntrinsicWidth() + "x" + d.getIntrinsicHeight() + " view " + v.getWidth() + "x" + v.getHeight()); try { Bitmap b = Bitmap.createBitmap(48, 24, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b); Drawable k = d.getConstantState().newDrawable().mutate(); k.setBounds(0, 0, 48, 24); k.draw(c); long sum = 0; int n = 0;
          for (int y = 0; y < 24; y++) for (int x = 0; x < 48; x++) { int p = b.getPixel(x, y); if (Color.alpha(p) > 120) { sum += Math.max(Color.red(p), Math.max(Color.green(p), Color.blue(p))); n++; } }
          if (n > 0 && sum / n < 70) v.setColorFilter(INK, PorterDuff.Mode.SRC_IN);
        } catch (Exception ignored) { }
        return false;
      }
    };
  }

  /** Every request for a trailer gets a number. Leaving changes the number, so one that arrives late can't start playing behind another app. */
  int trailerRun; boolean visible;

  void quiet() { trailerRun++; player.setVolume(0f); }

  void playTrailer(Title t) {
    int me = ++trailerRun;
    Trailer.get(t.trailer, s -> {
      if (s == null || me != trailerRun || !visible || cur != t || mode == Mode.ASK || mode == Mode.DETAIL) return; player.setVolume(soundVolume);
      DefaultHttpDataSource.Factory f = new DefaultHttpDataSource.Factory().setUserAgent(Trailer.UA); ProgressiveMediaSource.Factory m = new ProgressiveMediaSource.Factory(f);
      MediaSource v = m.createMediaSource(MediaItem.fromUri(Uri.parse(s.video)));
      player.setMediaSource(s.audio != null ? new MergingMediaSource(v, m.createMediaSource(MediaItem.fromUri(Uri.parse(s.audio)))) : v);
      player.seekTo(9000); player.prepare(); player.setPlayWhenReady(true);
    });
  }

  void cover(float zoom) {
    if (vidW == 0) return; float sw = stage.getWidth(), sh = stage.getHeight(); if (sw == 0) return;
    float k = Math.max(sw / vidW, sh / vidH) * zoom; FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) video.getLayoutParams(); p.width = Math.round(vidW * k); p.height = Math.round(vidH * k); p.gravity = Gravity.CENTER; video.setLayoutParams(p);
  }

  void measureBars() {
    if (!player.isPlaying()) { reveal(); return; }
    int W = 96, H = 54; Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    try {
      PixelCopy.request(video, b, res -> {
        float zoom = 1f;
        if (res == PixelCopy.SUCCESS) { int top = 0; for (int y = 0; y < H / 3; y++) { long sum = 0; for (int x = 0; x < W; x++) { int p = b.getPixel(x, y); sum += Color.red(p) + Color.green(p) + Color.blue(p); } if (sum / (W * 3) < 10) top++; else break; } if (top >= 3 && top < H / 3) zoom = 1.015f * H / (H - 2f * top); Log.i(T, "letterbox rows " + top + " zoom " + zoom); }
        cover(zoom); reveal();
      }, h);
    } catch (Exception e) { reveal(); }
  }

  void reveal() {
    if (videoOn || mode == Mode.ASK || mode == Mode.DETAIL) return; videoOn = true;
    for (ImageView b : new ImageView[]{bgA, bgB}) b.animate().alpha(0f).setDuration(900).withEndAction(() -> b.setVisibility(View.INVISIBLE)).start(); rack(false); wake(); h.removeCallbacks(pump); h.postDelayed(pump, 900);
    idle = () -> { if (videoOn && r == 0 && mode == Mode.HOME) sleepUi(true); }; h.postDelayed(idle, Tokens.MOTION_IDLE_MS);
  }

  void sleepUi(boolean on) {
    asleep = on;
    rowsBox.animate().alpha(on ? 0f : 1f).setDuration(Tokens.MOTION_FADE_MS).start(); lens.animate().alpha(on ? 0f : 1f).setDuration(Tokens.MOTION_FADE_MS).start(); slide(rowsBox, DynamicAnimation.TRANSLATION_Y, on ? px(Tokens.CANVAS_HEIGHT - H0) : rowY());
    int hidden = why.getHeight() + where.getHeight() + 2 * px(Tokens.SPACE_XS); slide(info, DynamicAnimation.TRANSLATION_Y, on ? px(Tokens.CANVAS_HEIGHT - Tokens.SAFE_BOTTOM - Tokens.HOME_INFO_BOTTOM) + hidden : (r > 0 && mode == Mode.HOME ? -px(INFO_SHIFT) : 0));
    why.animate().alpha(on ? 0f : 1f).setDuration(Tokens.MOTION_FADE_MS).start(); where.animate().alpha(on ? 0f : 1f).setDuration(Tokens.MOTION_FADE_MS).start();
  }

  boolean asleep;
  void wake() { if (idle != null) h.removeCallbacks(idle); if (asleep) sleepUi(false); if (videoOn) { idle = () -> { if (videoOn && r == 0 && mode == Mode.HOME) sleepUi(true); }; h.postDelayed(idle, Tokens.MOTION_IDLE_MS); } }

  void stopVideo() {
    trailerRun++;
    if (!videoOn && !player.isPlaying() && player.getMediaItemCount() == 0) { if (dwell != null) h.removeCallbacks(dwell); return; }
    if (dwell != null) h.removeCallbacks(dwell); if (idle != null) h.removeCallbacks(idle); h.removeCallbacks(pump); videoOn = false; player.stop(); player.clearMediaItems(); cover(1f);
    ImageView in = flip ? bgA : bgB; in.setVisibility(View.VISIBLE); in.animate().alpha(1f).setDuration(300).start();
  }

  void launch(Title t) {
    if (t.system != null) { if ("about".equals(t.system)) { about(); return; } if ("google".equals(t.system)) { HomeService.setTakeover(this, false); openPkg("com.google.android.apps.tv.launcherx", "Google TV home"); return; } openPkg(t.system, t.title); return; }
    if (mode != Mode.DETAIL && !morph.isOpen() && t == cur && grow(() -> { comeBack = true; launch(t); })) return;
    Api.event(t.id, "open"); Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(t.link)).setPackage(t.pkg()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); if ("netflix".equals(t.service)) i.putExtra("source", "30");
    handoff(t.subscribed ? t.serviceName() : "Stremio", i);
  }

  void openStremio(Title t) { handoff("Stremio", new Intent(Intent.ACTION_VIEW, Uri.parse(t.stremio)).setPackage("com.stremio.one").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }

  void openPkg(String pkg, String label) { Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg); if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg); if (i == null) { Toast.makeText(this, label + " isn't installed", Toast.LENGTH_SHORT).show(); return; } handoff(label, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }

  void handoff(String label, Intent i) {
    stopVideo(); cue("handoff"); veil.setText("Opening " + label + "…"); veil.setVisibility(View.VISIBLE); veil.setAlpha(0f); veil.animate().alpha(1f).setDuration(Tokens.MOTION_FADE_MS).start();
    h.postDelayed(() -> { try { startActivity(i); } catch (Exception e) { Log.w(T, "open failed", e); Toast.makeText(this, label + " couldn't open that title", Toast.LENGTH_LONG).show(); } h.postDelayed(() -> { veil.setVisibility(View.GONE); veil.setAlpha(0f); }, 900); }, 320);
  }

  void openDetail() {
    if (cur == null || cur.system != null) return; Title g = cur; if (!morph.isOpen() && grow(this::openDetail)) return; before = mode; mode = Mode.DETAIL; Title t = g; stopVideo(); info.setVisibility(View.GONE); rowsBox.setVisibility(View.GONE); detail.setVisibility(View.VISIBLE); View panel = ((ViewGroup) detail).getChildAt(0); panel.setAlpha(0f); panel.setPivotX(0); panel.setPivotY(px(600)); panel.setScaleX(.94f); panel.setScaleY(.94f); panel.setTranslationY(px(Tokens.SPACE_XL)); panel.animate().alpha(1f).setDuration(Tokens.MOTION_FADE_MS).start(); spring(panel, DynamicAnimation.TRANSLATION_Y, 0, Tokens.SPRING_MORPH_STIFFNESS, Tokens.SPRING_MORPH_DAMPING); spring(panel, DynamicAnimation.SCALE_X, 1f, Tokens.SPRING_MORPH_STIFFNESS, Tokens.SPRING_MORPH_DAMPING); spring(panel, DynamicAnimation.SCALE_Y, 1f, Tokens.SPRING_MORPH_STIFFNESS, Tokens.SPRING_MORPH_DAMPING);
    dLogo.clearColorFilter(); if (t.logo != null) { dLogo.setVisibility(View.VISIBLE); dName.setVisibility(View.GONE); Glide.with(this).load(t.logo).override(px(Tokens.LOGO_HOME_WIDTH), px(Tokens.LOGO_HOME_HEIGHT)).fitCenter().listener(logoCheck(dLogo)).into(dLogo); } else { dLogo.setVisibility(View.GONE); dName.setVisibility(View.VISIBLE); dName.setText(t.title); }
    dMeta.setText(t.meta()); dText.setText(t.overview); List<String> b = new ArrayList<>(); b.add(t.subscribed ? "Play on " + t.serviceName() : "Open in Stremio"); if (t.subscribed) b.add("Open in Stremio"); b.add("Not for me"); pills(b);
  }

  void about() { before = mode; mode = Mode.DETAIL; stopVideo(); info.setVisibility(View.GONE); rowsBox.setVisibility(View.GONE); detail.setVisibility(View.VISIBLE); dLogo.setVisibility(View.GONE); dName.setVisibility(View.VISIBLE); dName.setText("About"); dMeta.setText(""); dText.setText("Streaming availability information is provided by Streaming Availability API by Movie of the Night. movieofthenight.com/about/api"); pills(Collections.singletonList("Back")); }

  void pills(List<String> labels) { btns.removeAllViews(); btn = 0; for (int i = 0; i < labels.size(); i++) { View p = pill(labels.get(i)); LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, px(Tokens.PILL_HEIGHT)); l.rightMargin = px(Tokens.PILL_GAP); btns.addView(p, l); liftPill(p, i == 0); } btnLens.forget(); moveBtnLens(true); }

  void closeDetail() {
    mode = before; Runnable back = () -> { Title t = cur; cur = null; focus(0); };
    View panel = ((ViewGroup) detail).getChildAt(0); panel.animate().alpha(0f).setDuration(Tokens.MOTION_FOCUS_MS).withEndAction(() -> detail.setVisibility(View.GONE)).start(); spring(panel, DynamicAnimation.TRANSLATION_Y, px(Tokens.SPACE_XL), Tokens.SPRING_MORPH_STIFFNESS, Tokens.SPRING_MORPH_DAMPING);
    if (morph.isOpen()) shrink(back); else { detail.setVisibility(View.GONE); info.setVisibility(View.VISIBLE); rowsBox.setVisibility(View.VISIBLE); back.run(); }
  }

  void pressButton() {
    String x = ((TextView) btns.getChildAt(btn)).getText().toString(); Title t = cur;
    if ("Back".equals(x)) { closeDetail(); return; } if ("Not for me".equals(x)) { Api.event(t.id, "skip"); closeDetail(); return; } if ("Open in Stremio".equals(x)) { openStremio(t); return; } launch(t);
  }

  void talk(String text) {
    morph.hide(); comeBack = false; if (morphed != null) { morphed.setAlpha(1f); morphed = null; } rowsBox.setAlpha(1f); info.setAlpha(1f); lens.setAlpha(1f); dock.setAlpha(1f); thoughtText = "";
    int me = ++ask; mode = Mode.ASK; stopVideo(); cue("listenEnd"); heardText = text; dock.removeAllViews(); info.setVisibility(View.GONE); rowsBox.setVisibility(View.GONE); detail.setVisibility(View.GONE); searching(true); lens.animate().alpha(0f).setDuration(Tokens.MOTION_FOCUS_MS).start(); rack(true); dim.setVisibility(View.VISIBLE); dim.animate().alpha(.5f).setDuration(Tokens.MOTION_FADE_MS).start();
    askBox.setVisibility(View.VISIBLE); said.setAlpha(0f); said.setTranslationY(px(Tokens.SPACE_S)); said.setText(text); said.animate().alpha(1f).setDuration(Tokens.MOTION_FADE_MS).start(); spring(said, DynamicAnimation.TRANSLATION_Y, 0, Tokens.SPRING_REVEAL_STIFFNESS, Tokens.SPRING_REVEAL_DAMPING);
    Api.ask(text, new Api.Ask() {
      public void heart(String words) { if (me == ask) lightUp(words); }
      public void line(String words) { if (me == ask) ticker.say(words); }
      public void reply(String words) { if (me != ask) return; thoughtText = words; if (mode == Mode.RESULT) rows.get(0).head.setText(words); }
      final List<Title> got = new ArrayList<>();
      public void pick(Title t) { if (me != ask) return; got.add(t); if (mode == Mode.RESULT) place(t); else if (got.size() == 1) h.postDelayed(() -> { if (me == ask && mode == Mode.ASK) results(new ArrayList<>(got)); }, 1800); }
      public void picks(List<Title> t) { if (me != ask) return; if (t.isEmpty()) { fail("I couldn't find a good match for that. Try saying it another way."); return; } if (got.isEmpty()) h.postDelayed(() -> { if (me == ask) results(t); }, 650); }
      public void fail(String why2) { if (me != ask) return; Log.w(T, "ask: " + why2); cue("error"); searching(false); said.setText(why2.startsWith("I couldn") ? why2 : "That didn't work. Check the Wi-Fi and try again."); h.postDelayed(() -> { if (me == ask) goHome(); }, 3200); }
    });
  }

  String thoughtText = "";

  /** The words that mattered warm up inside the sentence they said. */
  void lightUp(String words) {
    String all = said.getText().toString(); int at = words == null || words.isEmpty() ? -1 : all.toLowerCase().indexOf(words.toLowerCase()); if (at < 0) return; int end = at + words.length();
    ValueAnimator warm = ValueAnimator.ofArgb(INK, VOICE); warm.setDuration(Tokens.MOTION_FADE_MS);
    warm.addUpdateListener(v -> { SpannableString lit = new SpannableString(all); lit.setSpan(new ForegroundColorSpan((int) v.getAnimatedValue()), at, end, 0); said.setText(lit); }); warm.start();
  }

  /** The chip comes to the middle of the screen and the mark works there, so you can see Claude searching. */
  void searching(boolean on) {
    ticker.think(on); float k = on ? Tokens.TICKER_THINK_SCALE : 1f, st = Tokens.SPRING_MORPH_STIFFNESS, da = Tokens.SPRING_MORPH_DAMPING;
    if (on) centreChip(top.getWidth()); else spring(top, DynamicAnimation.TRANSLATION_X, 0, st, da);
    spring(top, DynamicAnimation.TRANSLATION_Y, on ? px(Tokens.TICKER_THINK_Y - Tokens.SAFE_TOP) : 0, st, da); spring(top, DynamicAnimation.SCALE_X, k, st, da); spring(top, DynamicAnimation.SCALE_Y, k, st, da);
  }

  /** Keeps the chip centred while its width changes. Outside a search it simply stays in its corner. */
  void centreChip(int width) { if (mode == Mode.ASK) spring(top, DynamicAnimation.TRANSLATION_X, -(stage.getWidth() / 2f - px(Tokens.SAFE_RIGHT) - width / 2f), Tokens.SPRING_MORPH_STIFFNESS, Tokens.SPRING_MORPH_DAMPING); }

  void results(List<Title> picks) {
    cue("picks"); searching(false);
    said.animate().alpha(0f).setDuration(320).withEndAction(() -> askBox.setVisibility(View.GONE)).start();
    mode = Mode.RESULT; rows = new ArrayList<>();
    for (int i = 0; i < picks.size(); i += Tokens.LENS_COLUMNS) { Row x = new Row(); x.grid = true; x.items = new ArrayList<>(picks.subList(i, Math.min(i + Tokens.LENS_COLUMNS, picks.size()))); x.name = i == 0 ? (thoughtText.isEmpty() ? "Picks for that" : thoughtText) : ""; rows.add(x); }
    r = 0; cur = null; info.setVisibility(View.VISIBLE); rowsBox.setVisibility(View.VISIBLE); lens.setAlpha(1f); lens.forget(); rack(false); dim.animate().alpha(0f).setDuration(Tokens.MOTION_FADE_MS).start(); build(false); focus(0); sound.play("build"); plan.brief(() -> parts(true), null);
  }

  /** A pick that arrives after the screen is up joins the end of the list without moving anything. */
  void place(Title t) {
    Row last = rows.get(rows.size() - 1);
    if (last.items.size() >= Tokens.LENS_COLUMNS) { Row x = new Row(); x.grid = true; x.name = ""; x.y = last.y + last.height(); rows.add(x); makeRow(x, false); x.track.setAlpha(.46f); last = x; }
    last.items.add(t); addCard(last, t, 0); if (rows.indexOf(last) == r) ((ViewGroup) last.cards.get(last.cards.size() - 1)).getChildAt(1).setVisibility(View.VISIBLE);
  }

  void goHome() { if (mode == Mode.ASK) searching(false); morph.hide(); comeBack = false; if (morphed != null) { morphed.setAlpha(1f); morphed = null; } rowsBox.setAlpha(1f); info.setAlpha(1f); lens.setAlpha(1f); dock.setAlpha(1f); ask++; stopVideo(); mode = Mode.HOME; veil.setVisibility(View.GONE); askBox.setVisibility(View.GONE); detail.setVisibility(View.GONE); dock.removeAllViews(); info.setVisibility(View.VISIBLE); rowsBox.setVisibility(View.VISIBLE);  rack(false); if (home.isEmpty()) return; rows = home; r = 0; for (Row x : rows) x.at = 0; cur = null; build(false); focus(0); }

  /** Speech never matches our own words, so anything that does is the screen being read back by mistake. */
  boolean onScreen(String t) { for (TextView v : new TextView[]{why, name, meta, where, hint, said}) if (t.contentEquals(v.getText())) return v != said || mode == Mode.ASK; return false; }

  long askedAt = -10000;

  void handle(Intent i) { if (i == null) return; String m = i.getStringExtra("mode"); if ("ask".equals(m)) { HomeService.setTakeover(this, true); String t = i.getStringExtra("text"); if (t == null) return; long now = SystemClock.elapsedRealtime(); if (now - askedAt < 4000 || onScreen(t)) { Log.w(T, "ignored second voice text: " + t); return; } askedAt = now; talk(t); } else if ("home".equals(m)) { cue("home"); goHome(); } else if ("wake".equals(m)) { if (!plan.active()) reboot(); } else if ("detail".equals(m)) openDetail(); }

  @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); handle(i); }

  @Override protected void onResume() { super.onResume(); HomeService.setTakeover(this, true); if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > 600000 && mode == Mode.HOME && !home.isEmpty()) { stoppedAt = 0; morph.hide(); reboot(); return; } if (comeBack) { comeBack = false; veil.setVisibility(View.GONE); if (morph.isOpen()) { shrink(() -> { Title t = cur; cur = null; focus(0); }); return; } } if (mode == Mode.HOME && cur != null && !rows.isEmpty()) { Title t = cur; cur = null; focus(0); } }

  @Override protected void onStart() { super.onStart(); visible = true; }

  @Override protected void onStop() { super.onStop(); visible = false; stopVideo(); stoppedAt = SystemClock.elapsedRealtime(); }

  @Override protected void onDestroy() { super.onDestroy(); if (player != null) player.release(); sound.release(); }

  @Override public boolean dispatchKeyEvent(KeyEvent e) {
    if (plan.active()) { if (plan.settled() && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) plan.stop(); return true; }
    int k = e.getKeyCode();
    boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER;
    if (e.getAction() == KeyEvent.ACTION_UP) { if (ok && !longDone) press(); longDone = false; return ok || super.dispatchKeyEvent(e); }
    if (ok) { if (e.getRepeatCount() > 8 && !longDone && (mode == Mode.HOME || mode == Mode.RESULT)) { longDone = true; openDetail(); } return true; }
    if (k == KeyEvent.KEYCODE_BACK) { back(); return true; }
    if (veil.getVisibility() == View.VISIBLE) return true;
    if (mode == Mode.ASK) return true;
    if (mode == Mode.DETAIL) { int n = btns.getChildCount(); if (k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_LEFT) { liftPill(btns.getChildAt(btn), false); btn = (btn + (k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : n - 1)) % n; liftPill(btns.getChildAt(btn), true); moveBtnLens(false); sound.play("focus", n > 1 ? btn / (n - 1f) : .5f, 0); } return true; }
    if (rows.isEmpty()) return super.dispatchKeyEvent(e); Row x = rowAt();
    switch (k) {
      case KeyEvent.KEYCODE_DPAD_RIGHT: if (x.at < x.items.size() - 1) { x.at++; moved("focus"); focus(1); } else bump(1, 0); return true;
      case KeyEvent.KEYCODE_DPAD_LEFT: if (x.at > 0) { x.at--; moved("focus"); focus(-1); } else bump(-1, 0); return true;
      case KeyEvent.KEYCODE_DPAD_DOWN: if (r < rows.size() - 1) { r++; keepColumn(x); moved("row"); focus(0); } else bump(0, 1); return true;
      case KeyEvent.KEYCODE_DPAD_UP: if (r > 0) { r--; keepColumn(x); moved("row"); focus(0); } else bump(0, -1); return true;
      default: return super.dispatchKeyEvent(e);
    }
  }

  /** In the results grid, up and down stay in the same column. */
  void keepColumn(Row from) { Row to = rowAt(); if (to.grid) to.at = Math.min(from.at, to.items.size() - 1); }

  void moved(String s) { Row x = rowAt(); int n = x.items.size(); sound.play(s, n > 1 ? x.at / (n - 1f) : .5f, r); }

  void bump(float dx, float dy) { wake(); moved("edge"); lean(dx, dy); }

  void press() { if (veil.getVisibility() == View.VISIBLE && home.isEmpty()) { recreate(); return; } cue("select"); if (mode == Mode.DETAIL) pressButton(); else if (mode != Mode.ASK && cur != null) launch(cur); }

  void back() { if (mode != Mode.HOME || r > 0) cue("back"); if (mode == Mode.DETAIL) closeDetail(); else if (mode == Mode.ASK || mode == Mode.RESULT) goHome(); else if (r > 0) { r = 0; focus(0); } }
}
