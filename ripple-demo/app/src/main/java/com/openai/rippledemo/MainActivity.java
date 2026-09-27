package com.openai.rippledemo;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        setContentView(new RippleCodeView());

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private final class RippleCodeView extends View {
        private static final int COLS = 2;
        private static final int ROWS = 5;
        private static final long RIPPLE_MS = 1350L;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Ripple> ripples = new ArrayList<>();
        private final List<Integer> enrolled = new ArrayList<>();
        private final List<Integer> current = new ArrayList<>();

        private float downX, downY;
        private boolean moved;
        private final float density;
        private String flash = "";
        private long flashUntil = 0L;
        private boolean matchedFlash = false;

        RippleCodeView() {
            super(MainActivity.this);
            density = getResources().getDisplayMetrics().density;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            long now = SystemClock.uptimeMillis();

            paint.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{Color.rgb(10,31,46), Color.rgb(11,74,86), Color.rgb(4,29,47)},
                    new float[]{0f,0.55f,1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,w,h,paint);
            paint.setShader(null);

            line.setStrokeWidth(dp(1.3f));
            line.setStyle(Paint.Style.STROKE);
            for (int i=0;i<9;i++) {
                float y = h * (0.19f + i * 0.075f);
                line.setColor(Color.argb(18,205,245,255));
                canvas.drawArc(new RectF(-w*0.15f, y-dp(16), w*1.15f, y+dp(26)),184,172,false,line);
            }
            line.setStyle(Paint.Style.FILL);

            boolean animate = false;
            for (int i=ripples.size()-1;i>=0;i--) {
                Ripple r = ripples.get(i);
                float t = (now-r.startedAt)/(float)RIPPLE_MS;
                if (t >= 1f) { ripples.remove(i); continue; }
                animate = true;
                drawRipple(canvas,r,t);
            }

            text.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
            text.setColor(Color.argb(238,240,250,255));
            text.setTextSize(dp(28));
            canvas.drawText("Ripple Code",dp(26),dp(68),text);

            text.setTextSize(dp(14));
            text.setColor(Color.argb(178,226,243,248));
            String mode = enrolled.isEmpty() ? "등록 모드" : "테스트 모드";
            canvas.drawText(mode + "  ·  탭 순서 + 마지막 스와이프",dp(27),dp(94),text);

            RectF reset = resetRect();
            paint.setColor(Color.argb(42,255,255,255));
            canvas.drawRoundRect(reset,dp(18),dp(18),paint);
            text.setTextSize(dp(13));
            text.setColor(Color.argb(220,245,252,255));
            canvas.drawText("초기화",reset.left+dp(17),reset.centerY()+dp(5),text);

            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(dp(17));
            text.setColor(Color.argb(205,238,249,252));
            if (enrolled.isEmpty()) {
                canvas.drawText("원하는 위치를 원하는 만큼 탭",w/2f,h*0.46f,text);
                text.setTextSize(dp(14));
                text.setColor(Color.argb(142,230,247,251));
                canvas.drawText("마지막에 어느 방향으로든 한 번 쭉 스와이프",w/2f,h*0.46f+dp(29),text);
            } else {
                canvas.drawText("같은 위치를 같은 순서로 탭",w/2f,h*0.46f,text);
                text.setTextSize(dp(14));
                text.setColor(Color.argb(142,230,247,251));
                canvas.drawText("스와이프 방향은 비교하지 않음",w/2f,h*0.46f+dp(29),text);
            }

            text.setTextSize(dp(15));
            text.setColor(Color.argb(195,240,250,255));
            canvas.drawText("현재 입력  " + current.size() + "회",w/2f,h-dp(72),text);
            text.setTextSize(dp(12));
            text.setColor(Color.argb(120,235,248,252));
            canvas.drawText("숨은 판정 영역: 2 × 5  ·  총 10개 영역",w/2f,h-dp(46),text);
            text.setTextAlign(Paint.Align.LEFT);

            if (now < flashUntil && !flash.isEmpty()) {
                float age = (flashUntil-now)/900f;
                int alpha = (int)(255*Math.min(1f,age+0.15f));
                paint.setColor(matchedFlash ? Color.argb(Math.min(alpha,96),117,255,194)
                        : Color.argb(Math.min(alpha,72),255,184,160));
                canvas.drawRect(0,0,w,h,paint);
                text.setTextAlign(Paint.Align.CENTER);
                text.setTextSize(dp(31));
                text.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
                text.setColor(Color.argb(alpha,255,255,255));
                canvas.drawText(flash,w/2f,h*0.67f,text);
                text.setTextAlign(Paint.Align.LEFT);
                animate = true;
            }

            if (animate) postInvalidateOnAnimation();
        }

        private void drawRipple(Canvas canvas, Ripple r, float t) {
            float eased = (float)Math.pow(t,0.62);
            float radius = dp(16) + Math.min(getWidth(),getHeight()) * 0.33f * eased;
            int alpha = (int)(150*(1f-t)*(1f-t));
            paint.setShader(new RadialGradient(r.x,r.y,Math.max(dp(1),radius),
                    new int[]{Color.argb(0,255,255,255),Color.argb(Math.min(alpha,42),180,241,255),Color.argb(0,128,220,255)},
                    new float[]{0.42f,0.77f,1f},Shader.TileMode.CLAMP));
            canvas.drawCircle(r.x,r.y,radius,paint);
            paint.setShader(null);

            line.setStyle(Paint.Style.STROKE);
            for (int i=0;i<4;i++) {
                float rr = radius-dp(i*24f);
                if (rr<=0) continue;
                int a = Math.max(0,alpha-i*22);
                line.setColor(Color.argb(a,220,249,255));
                line.setStrokeWidth(dp(i==0 ? 2.1f : 1.25f));
                canvas.drawCircle(r.x,r.y,rr,line);
            }
            line.setStyle(Paint.Style.FILL);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX(), y=e.getY();
            if (e.getActionMasked()==MotionEvent.ACTION_DOWN) {
                if (resetRect().contains(x,y)) {
                    enrolled.clear(); current.clear();
                    flash("등록값 초기화",false);
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                    invalidate(); return true;
                }
                downX=x; downY=y; moved=false;
                ripples.add(new Ripple(x,y,SystemClock.uptimeMillis()));
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                postInvalidateOnAnimation(); return true;
            }
            if (e.getActionMasked()==MotionEvent.ACTION_MOVE) {
                float dx=x-downX, dy=y-downY;
                if (dx*dx+dy*dy > swipeThreshold()*swipeThreshold()) moved=true;
                return true;
            }
            if (e.getActionMasked()==MotionEvent.ACTION_UP) {
                float dx=x-downX, dy=y-downY;
                boolean swipe = moved || dx*dx+dy*dy > swipeThreshold()*swipeThreshold();
                if (swipe) finalizeSequence();
                else {
                    current.add(zoneFor(x,y));
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                invalidate(); return true;
            }
            if (e.getActionMasked()==MotionEvent.ACTION_CANCEL) { moved=false; return true; }
            return true;
        }

        private void finalizeSequence() {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (current.isEmpty()) { flash("먼저 한 번 이상 탭",false); return; }
            if (enrolled.isEmpty()) {
                enrolled.addAll(current);
                int n=current.size(); current.clear();
                flash("데모 코드 저장 · " + n + "회",true); return;
            }
            boolean match=enrolled.equals(current);
            current.clear();
            flash(match ? "MATCH" : "NO MATCH",match);
            performHapticFeedback(match ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.REJECT);
        }

        private int zoneFor(float x,float y) {
            int col=Math.max(0,Math.min(COLS-1,(int)(x/Math.max(1f,getWidth())*COLS)));
            int row=Math.max(0,Math.min(ROWS-1,(int)(y/Math.max(1f,getHeight())*ROWS)));
            return row*COLS+col;
        }

        private float swipeThreshold() { return Math.max(dp(76),Math.min(getWidth(),getHeight())*0.12f); }
        private RectF resetRect() {
            float right=getWidth()-dp(20);
            return new RectF(right-dp(82),dp(37),right,dp(77));
        }
        private void flash(String s,boolean good) {
            flash=s; matchedFlash=good; flashUntil=SystemClock.uptimeMillis()+900L;
            postInvalidateOnAnimation();
        }
        private float dp(float v) { return v*density; }

        private final class Ripple {
            final float x,y; final long startedAt;
            Ripple(float x,float y,long startedAt) { this.x=x; this.y=y; this.startedAt=startedAt; }
        }
    }
}
