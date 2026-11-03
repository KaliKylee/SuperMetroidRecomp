package com.snesrecomp.supermetroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Controles de toque estilo SNES. Cada botão é desenhado e testado individualmente;
 * os ids são os índices de botão do SDL_GameController (veja sm_touch_android.c).
 */
public class TouchOverlay extends View {
    static native void nativeSetButton(int button, boolean down);
    static native boolean nativeTryAttach();

    // Índices do SDL_GameControllerButton
    static final int SDL_A = 0, SDL_B = 1, SDL_X = 2, SDL_Y = 3, SDL_BACK = 4, SDL_START = 6,
            SDL_LSH = 9, SDL_RSH = 10, SDL_UP = 11, SDL_DOWN = 12, SDL_LEFT = 13, SDL_RIGHT = 14;

    private static class Btn {
        final int id; final String label; final boolean round;
        float cx, cy, hw, hh; // centro e meia-largura/altura
        Btn(int id, String label, boolean round) { this.id = id; this.label = label; this.round = round; }
        boolean hit(float x, float y) {
            if (round) { float dx = x - cx, dy = y - cy; return dx * dx + dy * dy <= hw * hw * 1.25f; }
            return Math.abs(x - cx) <= hw * 1.1f && Math.abs(y - cy) <= hh * 1.3f;
        }
    }

    // Posições do diamante SNES: X em cima, A à direita, B embaixo, Y à esquerda.
    // Mapeadas por posição física para os botões Xbox (Y/B/A/X) que o GamepadMap padrão usa.
    private final Btn bTop = new Btn(SDL_Y, "X", true);
    private final Btn bRight = new Btn(SDL_B, "A", true);
    private final Btn bBottom = new Btn(SDL_A, "B", true);
    private final Btn bLeft = new Btn(SDL_X, "Y", true);
    private final Btn bL = new Btn(SDL_LSH, "L", false);
    private final Btn bR = new Btn(SDL_RSH, "R", false);
    private final Btn bSelect = new Btn(SDL_BACK, "SELECT", false);
    private final Btn bStart = new Btn(SDL_START, "START", false);
    private final Btn[] simple = { bTop, bRight, bBottom, bLeft, bL, bR, bSelect, bStart };

    private float dpCx, dpCy, dpR;
    private int current = 0; // bitmask por índice de botão (0..14)

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final float dp;

    public TouchOverlay(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * dp);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        setFocusable(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float m = 16 * dp;
        dpR = 70 * dp; dpCx = m + dpR + 20 * dp; dpCy = h - m - dpR - 10 * dp;

        float fr = 30 * dp, off = 46 * dp;
        float fcx = w - m - off - fr - 20 * dp, fcy = h - m - off - fr - 10 * dp;
        place(bTop, fcx, fcy - off, fr, fr);
        place(bBottom, fcx, fcy + off, fr, fr);
        place(bLeft, fcx - off, fcy, fr, fr);
        place(bRight, fcx + off, fcy, fr, fr);

        place(bL, m + 50 * dp, m + 24 * dp, 50 * dp, 24 * dp);
        place(bR, w - m - 50 * dp, m + 24 * dp, 50 * dp, 24 * dp);
        place(bSelect, w / 2f - 48 * dp, h - m - 14 * dp, 40 * dp, 14 * dp);
        place(bStart, w / 2f + 48 * dp, h - m - 14 * dp, 40 * dp, 14 * dp);
    }

    private static void place(Btn b, float cx, float cy, float hw, float hh) {
        b.cx = cx; b.cy = cy; b.hw = hw; b.hh = hh;
    }

    private int maskAt(float x, float y) {
        int m = 0;
        for (Btn b : simple) if (b.hit(x, y)) m |= 1 << b.id;
        float dx = x - dpCx, dy = y - dpCy;
        if (dx * dx + dy * dy <= dpR * dpR * 1.5f) {
            float t = dpR * 0.34f;
            if (dx < -t) m |= 1 << SDL_LEFT;
            if (dx > t) m |= 1 << SDL_RIGHT;
            if (dy < -t) m |= 1 << SDL_UP;
            if (dy > t) m |= 1 << SDL_DOWN;
        }
        return m;
    }

    private boolean hitAny(float x, float y) { return maskAt(x, y) != 0; }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        // Toque fora de qualquer botão passa para a superfície do SDL (launcher/menus ImGui).
        if (action == MotionEvent.ACTION_DOWN && !hitAny(e.getX(), e.getY())) return false;

        int mask = 0;
        boolean ending = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        if (!ending) {
            int skip = action == MotionEvent.ACTION_POINTER_UP ? e.getActionIndex() : -1;
            for (int i = 0; i < e.getPointerCount(); i++) {
                if (i == skip) continue;
                mask |= maskAt(e.getX(i), e.getY(i));
            }
        }
        apply(mask);
        return true;
    }

    private void apply(int mask) {
        int diff = mask ^ current;
        if (diff == 0) return;
        for (int id = 0; id < 15; id++) {
            if ((diff & (1 << id)) == 0) continue;
            boolean down = (mask & (1 << id)) != 0;
            try { nativeSetButton(id, down); } catch (UnsatisfiedLinkError ignored) { }
            if (down) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        current = mask;
        invalidate();
    }

    private boolean on(int id) { return (current & (1 << id)) != 0; }

    private void style(boolean pressed) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(pressed ? 0xA0FFFFFF : 0x40FFFFFF);
        stroke.setColor(pressed ? 0xE0FFFFFF : 0x90FFFFFF);
    }

    @Override
    protected void onDraw(Canvas c) {
        // D-pad: quatro braços
        float arm = dpR * 0.34f, len = dpR;
        drawArm(c, SDL_UP, dpCx - arm, dpCy - len, dpCx + arm, dpCy - arm * 0.2f);
        drawArm(c, SDL_DOWN, dpCx - arm, dpCy + arm * 0.2f, dpCx + arm, dpCy + len);
        drawArm(c, SDL_LEFT, dpCx - len, dpCy - arm, dpCx - arm * 0.2f, dpCy + arm);
        drawArm(c, SDL_RIGHT, dpCx + arm * 0.2f, dpCy - arm, dpCx + len, dpCy + arm);

        for (Btn b : simple) {
            style(on(b.id));
            if (b.round) {
                c.drawCircle(b.cx, b.cy, b.hw, fill);
                c.drawCircle(b.cx, b.cy, b.hw, stroke);
                text.setTextSize(b.hw * 0.8f);
            } else {
                rf.set(b.cx - b.hw, b.cy - b.hh, b.cx + b.hw, b.cy + b.hh);
                float r = b.hh;
                c.drawRoundRect(rf, r, r, fill);
                c.drawRoundRect(rf, r, r, stroke);
                text.setTextSize(b.hh * (b.label.length() > 1 && b.hh < 20 * dp ? 0.85f : 1.0f));
            }
            text.setColor(0xF0FFFFFF);
            c.drawText(b.label, b.cx, b.cy + text.getTextSize() * 0.35f, text);
        }
    }

    private void drawArm(Canvas c, int id, float l, float t, float r, float b) {
        style(on(id));
        rf.set(l, t, r, b);
        float rad = 8 * dp;
        c.drawRoundRect(rf, rad, rad, fill);
        c.drawRoundRect(rf, rad, rad, stroke);
    }
}
