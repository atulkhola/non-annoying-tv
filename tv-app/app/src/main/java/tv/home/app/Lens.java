package tv.home.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import androidx.dynamicanimation.animation.*;

/**
 * The selection, a pane of frosted glass. One object that travels: the edge it moves toward leads and the far edge follows, so it stretches
 * in flight and settles with a little give. It sits under the focused item and takes that item's shape, so moving
 * from a card to a pill changes it from one to the other instead of switching.
 */
final class Lens extends View {
  private final float[] now = new float[5];
  private final SpringAnimation[] edge = new SpringAnimation[5];
  private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Glass glass;
  private final RectF box = new RectF();
  private boolean placed;

  Lens(Context c, Glass glass, float unit) {
    super(c); this.glass = glass; glass.add(this); ink.setColor(Tokens.GLASS_LENS); line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(2 * unit); line.setColor(Tokens.GLASS_LENSEDGE);
    for (int i = 0; i < 5; i++) { final int k = i; FloatValueHolder h = new FloatValueHolder(0); SpringAnimation a = new SpringAnimation(h); a.setSpring(new SpringForce(0)); a.setMinimumVisibleChange(.5f); a.addUpdateListener((x, v, s) -> { now[k] = v; invalidate(); }); edge[i] = a; }
  }

  private void go(int i, float to, boolean lead) {
    edge[i].getSpring().setStiffness(lead ? Tokens.SPRING_LEAD_STIFFNESS : Tokens.SPRING_TRAIL_STIFFNESS).setDampingRatio(lead ? Tokens.SPRING_LEAD_DAMPING : Tokens.SPRING_TRAIL_DAMPING);
    edge[i].animateToFinalPosition(to);
  }

  void to(RectF r, float radius) {
    if (!placed) { snap(r, radius); return; }
    boolean right = r.right > now[2], down = r.bottom > now[3];
    go(0, r.left, !right); go(2, r.right, right); go(1, r.top, !down); go(3, r.bottom, down); go(4, radius, true);
  }

  void snap(RectF r, float radius) {
    float[] v = {r.left, r.top, r.right, r.bottom, radius};
    for (int i = 0; i < 5; i++) { edge[i].cancel(); edge[i].setStartValue(v[i]); edge[i].getSpring().setFinalPosition(v[i]); now[i] = v[i]; }
    placed = true; invalidate();
  }

  /** The selection has nowhere to go: it leans that way and comes back. */
  void lean(float dx, float dy) {
    if (dx != 0) { edge[0].setStartVelocity(dx); edge[2].setStartVelocity(dx); go(0, edge[0].getSpring().getFinalPosition(), false); go(2, edge[2].getSpring().getFinalPosition(), false); }
    if (dy != 0) { edge[1].setStartVelocity(dy); edge[3].setStartVelocity(dy); go(1, edge[1].getSpring().getFinalPosition(), false); go(3, edge[3].getSpring().getFinalPosition(), false); }
  }

  void forget() { placed = false; }

  @Override protected void onDraw(Canvas c) { if (!placed) return; box.set(now[0], now[1], now[2], now[3]); glass.paint(c, this, box, now[4]); c.drawRoundRect(box, now[4], now[4], ink); c.drawRoundRect(box, now[4], now[4], line); }
}
