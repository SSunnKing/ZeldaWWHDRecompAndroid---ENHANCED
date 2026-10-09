package org.wwhdrecomp.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * The HUD editor (Graphics › HUD layout), over the running game like the touch layout editor: each
 * part of the game's own HUD (hearts and magic, rupees, item buttons, small keys, wind compass) gets
 * a frame where the game draws it (aspect::hud_bounds). Touch to select, drag to move, drag the
 * corner handle or pinch to resize; the bar on top resets everything, hides or shows the selected
 * part, hides or shows the whole HUD, and closes. Changes apply at once (aspect.cpp moves, scales
 * and hides the parts' panes).
 */
final class HudEditor extends View {
    interface Host {
        int hudOffset(int part, int axis);
        int hudScale(int part);
        boolean hudHidden(int part);
        void setHud(int part, int dx, int dy, int scale, boolean hidden);
        RectF tvPicture();
        void closeHudEditor();
    }

    private static final int N = MainActivity.HUD_PARTS;
    // where the parts usually are (layout units, centre 0, y up), until the game has drawn them
    private static final float[][] DEFAULT = {{-500, 300}, {560, -319}, {520, 240}, {546, -281}, {-563, -259}};

    private final Host host;
    private final String[] names;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            dashed = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TouchIcons icons;
    private final float dp;
    private float[] bounds = new float[N * 4 + 2];
    private final RectF[] boxes = new RectF[N];
    private int sel = -1;
    private final RectF bReset = new RectF(), bEye = new RectF(), bAll = new RectF(), bDone = new RectF();

    HudEditor(Context c, Host h) {
        super(c);
        host = h;
        names = c.getResources().getStringArray(R.array.hud_parts);
        icons = new TouchIcons(c);
        dp = c.getResources().getDisplayMetrics().density;
        for (int i = 0; i < N; i++) boxes[i] = new RectF();
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.5f * dp);
        dashed.setStyle(Paint.Style.STROKE);
        dashed.setStrokeWidth(2f * dp);
        dashed.setPathEffect(new DashPathEffect(new float[] {8 * dp, 6 * dp}, 0));
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        java.util.Arrays.fill(bounds, Float.NaN);
        post(poll);
    }

    // the game draws the parts every frame: their frames follow (also while one is dragged)
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (getWindowToken() == null) return;
            float[] b = Native.hudBounds();
            if (b != null && b.length == bounds.length) {
                for (int p = 0; p < N; p++)
                    if (!Float.isNaN(b[p * 4]) || Float.isNaN(bounds[p * 4]))  // a hidden part keeps its last frame
                        System.arraycopy(b, p * 4, bounds, p * 4, 4);
                bounds[N * 4] = b[N * 4];
                bounds[N * 4 + 1] = b[N * 4 + 1];
            }
            invalidate();
            postDelayed(this, 50);
        }
    };

    private float kx() { float k = bounds[N * 4]; return Float.isNaN(k) || k <= 0 ? 1f : k; }
    private float ky() { float k = bounds[N * 4 + 1]; return Float.isNaN(k) || k <= 0 ? 1f : k; }

    // layout units -> screen
    private float sx(float lx) { RectF t = host.tvPicture(); return t.centerX() + lx / (640f * kx()) * t.width() / 2f; }
    private float sy(float ly) { RectF t = host.tvPicture(); return t.centerY() - ly / (360f * ky()) * t.height() / 2f; }
    // screen distances -> layout units (x right, y down, as the offsets)
    private float lx(float dx) { RectF t = host.tvPicture(); return dx / (t.width() / 2f) * 640f * kx(); }
    private float ly(float dy) { RectF t = host.tvPicture(); return dy / (t.height() / 2f) * 360f * ky(); }

    private void layoutBoxes() {
        float min = 40 * dp;
        for (int p = 0; p < N; p++) {
            RectF r = boxes[p];
            if (!Float.isNaN(bounds[p * 4])) {
                r.set(sx(bounds[p * 4]), sy(bounds[p * 4 + 3]), sx(bounds[p * 4 + 2]), sy(bounds[p * 4 + 1]));
            } else {  // not drawn yet: its usual place, moved by the player's offset
                float x = DEFAULT[p][0], y = DEFAULT[p][1];
                x = Math.signum(x) * (640f * kx() - (640f - Math.abs(x)));
                float cx = sx(x) + (sx(host.hudOffset(p, 0)) - sx(0)), cy = sy(y) + (sy(0) - sy(host.hudOffset(p, 1)));
                float half = 60 * dp * host.hudScale(p) / 100f;
                r.set(cx - half, cy - half / 2, cx + half, cy + half / 2);
            }
            if (r.width() < min) r.inset(-(min - r.width()) / 2, 0);
            if (r.height() < min) r.inset(0, -(min - r.height()) / 2);
        }
    }

    private boolean allHidden() {
        for (int p = 0; p < N; p++) if (!host.hudHidden(p)) return false;
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        layoutBoxes();
        fill.setColor(0x55000000);
        c.drawRect(0, 0, getWidth(), getHeight(), fill);
        text.setTextSize(13 * dp);
        for (int p = 0; p < N; p++) {
            RectF r = boxes[p];
            boolean hidden = host.hudHidden(p), on = p == sel;
            fill.setColor(on ? 0x55FFCC26 : 0x339FD8FF);
            c.drawRoundRect(r, 8 * dp, 8 * dp, fill);
            Paint line = hidden ? dashed : stroke;
            line.setColor(on ? 0xFFFFCC26 : hidden ? 0xFFB0B0B0 : 0xFF9FD8FF);
            c.drawRoundRect(r, 8 * dp, 8 * dp, line);
            text.setColor(0xFFFFFFFF);
            String label = names[p] + (hidden ? " (" + getContext().getString(R.string.hud_hidden) + ")" : "")
                    + (host.hudScale(p) != 100 ? "  " + host.hudScale(p) + "%" : "");
            float ty = r.top - 6 * dp < 70 * dp ? r.bottom + 16 * dp : r.top - 6 * dp;
            ty = Math.min(ty, getHeight() - 6 * dp);
            float half = text.measureText(label) / 2 + 6 * dp;  // the label stays on the screen
            float tx = Math.max(half, Math.min(getWidth() - half, r.centerX()));
            c.drawText(label, tx, ty, text);
            if (on && !hidden) icons.draw(c, "editor_resize_handle", 0xFFFFFFFF, "↘", r.right, r.bottom, 14 * dp, 255, false);
        }
        drawBar(c);
    }

    private void drawBar(Canvas c) {
        float r = 26 * dp, cy = 40 * dp, cx = getWidth() / 2f, step = 70 * dp;
        bReset.set(cx - 1.5f * step - r, cy - r, cx - 1.5f * step + r, cy + r);
        bEye.set(cx - 0.5f * step - r, cy - r, cx - 0.5f * step + r, cy + r);
        bAll.set(cx + 0.5f * step - r, cy - r, cx + 0.5f * step + r, cy + r);
        bDone.set(cx + 1.5f * step - r, cy - r, cx + 1.5f * step + r, cy + r);
        icons.draw(c, "editor_reset", TouchIcons.BONE, "↺", bReset.centerX(), cy, r, 255, false);
        boolean selHidden = sel >= 0 && host.hudHidden(sel);
        icons.draw(c, selHidden ? "editor_add" : "editor_remove", selHidden ? TouchIcons.SKY : TouchIcons.RED, selHidden ? "+" : "🗑",
                bEye.centerX(), cy, r, sel >= 0 ? 255 : 90, false);
        icons.draw(c, "", allHidden() ? TouchIcons.SKY : TouchIcons.VIOLET, allHidden() ? "◉" : "◌", bAll.centerX(), cy, r, 255, false);
        icons.draw(c, "editor_done", TouchIcons.GREEN, "✓", bDone.centerX(), cy, r, 255, false);
        text.setColor(0xFFFFFFFF);
        text.setTextSize(12 * dp);
        c.drawText(getContext().getString(allHidden() ? R.string.hud_show_all : R.string.hud_hide_all), bAll.centerX(), cy + r + 14 * dp, text);
        c.drawText(getContext().getString(R.string.hud_edit_hint), cx, cy + r + 32 * dp, text);
    }

    // ---- touch: select, move, resize (handle or pinch)
    private int mode;  // 0 none, 1 move, 2 handle resize, 3 pinch
    private float downX, downY, startScale, startDist, startOx, startOy;

    private void apply(int p, int dx, int dy, int scale, boolean hidden) {
        float maxX = 640f * kx(), maxY = 360f * ky();
        dx = (int) Math.max(-maxX * 2, Math.min(maxX * 2, dx));
        dy = (int) Math.max(-maxY * 2, Math.min(maxY * 2, dy));
        host.setHud(p, dx, dy, Math.max(25, Math.min(300, scale)), hidden);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mode = 0;
                if (bDone.contains(x, y)) { host.closeHudEditor(); return true; }
                if (bReset.contains(x, y)) {
                    for (int p = 0; p < N; p++) apply(p, 0, 0, 100, false);
                    return true;
                }
                if (bEye.contains(x, y)) {
                    if (sel >= 0) apply(sel, host.hudOffset(sel, 0), host.hudOffset(sel, 1), host.hudScale(sel), !host.hudHidden(sel));
                    return true;
                }
                if (bAll.contains(x, y)) {
                    boolean hide = !allHidden();
                    for (int p = 0; p < N; p++) apply(p, host.hudOffset(p, 0), host.hudOffset(p, 1), host.hudScale(p), hide);
                    return true;
                }
                layoutBoxes();
                if (sel >= 0 && !host.hudHidden(sel) && Math.hypot(x - boxes[sel].right, y - boxes[sel].bottom) < 26 * dp) {
                    mode = 2;
                } else {
                    sel = -1;
                    for (int p = N - 1; p >= 0; p--) if (boxes[p].contains(x, y)) { sel = p; break; }
                    mode = sel >= 0 ? 1 : 0;
                }
                if (sel >= 0) {
                    downX = x;
                    downY = y;
                    startOx = host.hudOffset(sel, 0);
                    startOy = host.hudOffset(sel, 1);
                    startScale = host.hudScale(sel);
                    startDist = (float) Math.hypot(x - boxes[sel].centerX(), y - boxes[sel].centerY());
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                if (sel >= 0 && e.getPointerCount() == 2) {
                    mode = 3;
                    startScale = host.hudScale(sel);
                    startDist = Math.max(1f, (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)));
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (sel < 0) return true;
                if (mode == 1) {
                    apply(sel, Math.round(startOx + lx(x - downX)), Math.round(startOy + ly(y - downY)), host.hudScale(sel), host.hudHidden(sel));
                } else if (mode == 2) {
                    float d = (float) Math.hypot(x - boxes[sel].centerX(), y - boxes[sel].centerY());
                    if (startDist > 1) apply(sel, host.hudOffset(sel, 0), host.hudOffset(sel, 1), Math.round(startScale * d / startDist), false);
                } else if (mode == 3 && e.getPointerCount() >= 2) {
                    float d = (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
                    apply(sel, host.hudOffset(sel, 0), host.hudOffset(sel, 1), Math.round(startScale * d / startDist), host.hudHidden(sel));
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (mode == 3) mode = 0;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mode = 0;
                return true;
        }
        return true;
    }
}
