package com.openai.rippledemo;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
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
    private RippleLockView lockView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        lockView = new RippleLockView();
        setContentView(lockView);
        applyLockscreenMode(lockView.isLockscreenMode());
        hideSystemBars();
    }

    private void hideSystemBars() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void applyLockscreenMode(boolean enabled) {
        setShowWhenLocked(enabled);
        setTurnScreenOn(enabled);
    }

    private void requestSystemDismiss() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null || !km.isKeyguardLocked()) {
            lockView.onSystemDismissed();
            return;
        }
        km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
            @Override public void onDismissSucceeded() {
                lockView.onSystemDismissed();
                finishAndRemoveTask();
            }
            @Override public void onDismissCancelled() { lockView.onSystemDismissFailed(); }
            @Override public void onDismissError() { lockView.onSystemDismissFailed(); }
        });
    }

    private final class RippleLockView extends View {
        private static final int COLS = 2;
        private static final int ROWS = 5;
        private static final long CLASSIC_RIPPLE_MS = 1350L;
        private static final long TOUCH_FIELD_MS = 1900L;
        private static final long UNLOCK_MS = 920L;
        private static final String PREFS = "ripple_code_v3";
        private static final String KEY_CODE = "code";
        private static final String KEY_STYLE = "style";
        private static final String KEY_LOCK_MODE = "lock_mode";

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ui = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<ClassicRipple> classicRipples = new ArrayList<>();
        private final List<TouchPoint> touchPoints = new ArrayList<>();
        private final List<Integer> enrolled = new ArrayList<>();
        private final List<Integer> current = new ArrayList<>();
        private final List<StyleProfile> styles = new ArrayList<>();
        private final SharedPreferences prefs;
        private final float density;
        private final RuntimeShader oilShader;

        private Drawable wallpaperDrawable;
        private float downX, downY, lastTrailX, lastTrailY;
        private long lastTrailAt;
        private boolean moved, settingsOpen, lockscreenMode;
        private int selectedStyle;
        private String flash = "";
        private long flashUntil;
        private boolean flashGood;
        private boolean unlocking, unlocked, dismissRequested;
        private long unlockStartedAt;

        private static final String OIL_SHADER =
                "uniform float2 uResolution;\n" +
                "uniform float uTime;\n" +
                "uniform float3 uTop;\n" +
                "uniform float3 uMid;\n" +
                "uniform float3 uBottom;\n" +
                "uniform float uOilStrength;\n" +
                "uniform float uClearStyle;\n" +
                "uniform float uInertia;\n" +
                "uniform float4 uTouch0;\n" +
                "uniform float4 uTouch1;\n" +
                "uniform float4 uTouch2;\n" +
                "uniform float4 uTouch3;\n" +
                "float hash21(float2 p){p=fract(p*float2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);}\n" +
                "float noise(float2 p){float2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);float a=hash21(i),b=hash21(i+float2(1,0)),c=hash21(i+float2(0,1)),d=hash21(i+float2(1,1));return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);}\n" +
                "float fbm(float2 p){float v=0.0;v+=noise(p)*0.52;p=p*2.03+7.1;v+=noise(p)*0.25;p=p*2.01-3.7;v+=noise(p)*0.13;p=p*2.04+2.4;v+=noise(p)*0.07;return v;}\n" +
                "float2 warpTouch(float2 uv,float4 t){float life=clamp(1.0-t.w,0.0,1.0);float2 d=uv-t.xy;float r=max(length(d),0.002);float fall=exp(-r*r*13.0);float wave=sin(r*42.0-t.w*20.0);float2 radial=d/r*(0.065+0.055*wave);float2 tangent=float2(-d.y,d.x)/r*0.030*sin(t.w*5.0+r*17.0);return (radial+tangent)*t.z*fall*pow(life,1.25);}\n" +
                "float rippleGlow(float2 uv,float4 t){float life=clamp(1.0-t.w,0.0,1.0);float r=length(uv-t.xy);float ring=exp(-pow((r-(0.035+t.w*0.17))*28.0,2.0));return ring*t.z*life;}\n" +
                "float3 palette(float x){float3 bronze=float3(0.78,0.50,0.31),purple=float3(0.48,0.30,0.60),blue=float3(0.24,0.50,0.67),teal=float3(0.24,0.67,0.59),mag=float3(0.67,0.34,0.56),gold=float3(0.78,0.64,0.34);x=fract(x);if(x<0.17)return mix(bronze,purple,x/0.17);if(x<0.34)return mix(purple,blue,(x-0.17)/0.17);if(x<0.52)return mix(blue,teal,(x-0.34)/0.18);if(x<0.70)return mix(teal,mag,(x-0.52)/0.18);if(x<0.86)return mix(mag,gold,(x-0.70)/0.16);return mix(gold,bronze,(x-0.86)/0.14);}\n" +
                "half4 main(float2 frag){\n" +
                "float2 uv=frag/uResolution;float aspect=uResolution.x/uResolution.y;float2 p=float2(uv.x*aspect,uv.y);float t=uTime;\n" +
                "p.y-=uInertia*(0.045+0.020*noise(p*2.0+float2(3.2,t*0.1)));\n" +
                "p+=warpTouch(uv,uTouch0)+warpTouch(uv,uTouch1)+warpTouch(uv,uTouch2)+warpTouch(uv,uTouch3);\n" +
                "float2 flow=float2(0.020*t,-0.012*t);float large=fbm(p*1.52+flow);float2 curl=float2(noise(p*2.5+float2(0.0,t*0.020)),noise(p*2.7+float2(6.0,-t*0.017)))-0.5;\n" +
                "float thick=fbm((p+curl*0.18)*2.75-flow*0.65)+0.42*fbm((p-curl*0.12)*5.4+flow*0.37);thick+=sin(t*0.31+large*6.283)*0.028;\n" +
                "float film=smoothstep(0.29,0.63,large+0.18*noise(p*3.6-flow));float band=fract(thick*1.95+large*0.26);float3 oil=palette(band);\n" +
                "float y=clamp(uv.y,0.0,1.0);float3 base=mix(uTop,uMid,smoothstep(0.0,0.58,y));base=mix(base,uBottom,smoothstep(0.50,1.0,y));\n" +
                "float water=0.5+0.5*sin(uv.y*9.0+fbm(p*1.9)*2.4+t*0.42);base*=0.945+0.055*water;\n" +
                "float spec=pow(clamp(1.0-abs(noise(p*2.4+flow*0.2)-0.52)*3.2,0.0,1.0),2.4);float3 silver=float3(0.80,0.91,0.90);\n" +
                "float strength=uOilStrength*film*(0.48+0.66*spec);strength*=mix(1.0,0.86,uClearStyle);float3 col=mix(base,oil,clamp(strength,0.0,0.72));col=mix(col,silver,spec*film*0.11);\n" +
                "float rg=rippleGlow(uv,uTouch0)+rippleGlow(uv,uTouch1)+rippleGlow(uv,uTouch2)+rippleGlow(uv,uTouch3);col=mix(col,float3(0.76,0.93,0.92),clamp(rg*0.10,0.0,0.11));\n" +
                "return half4(half3(clamp(col,0.0,1.0)),1.0);}";

        RippleLockView() {
            super(MainActivity.this);
            density = getResources().getDisplayMetrics().density;
            prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            initStyles();
            selectedStyle = Math.max(0, Math.min(styles.size() - 1, prefs.getInt(KEY_STYLE, 1)));
            lockscreenMode = prefs.getBoolean(KEY_LOCK_MODE, false);
            loadCode();
            loadWallpaper();
            oilShader = new RuntimeShader(OIL_SHADER);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            setFocusable(true);
        }

        boolean isLockscreenMode() { return lockscreenMode; }

        private void initStyles() {
            styles.add(new StyleProfile("Classic", "Galaxy water ripple",10,31,46,11,74,86,4,29,47,0f,false));
            styles.add(new StyleProfile("Oil Sheen", "strong oil-film interference",31,58,68,60,96,104,87,118,127,0.43f,true));
            styles.add(new StyleProfile("Oil Sheen Clear", "cleaner water · vivid sheen",68,104,114,101,137,143,136,160,162,0.34f,true));
            styles.add(new StyleProfile("Oil Sheen Night", "deep blue · violet / cyan",14,27,43,24,49,66,39,69,82,0.39f,true));
            styles.add(new StyleProfile("Getura Oil", "premium cyan / violet oil",73,104,113,98,127,124,120,117,142,0.40f,true));
        }

        private void loadCode() {
            enrolled.clear();
            String saved = prefs.getString(KEY_CODE, "");
            if (saved == null || saved.isEmpty()) return;
            for (String part : saved.split(",")) try { enrolled.add(Integer.parseInt(part)); } catch (NumberFormatException ignored) {}
        }

        private void saveCode() {
            StringBuilder sb = new StringBuilder();
            for (int i=0;i<enrolled.size();i++){ if(i>0)sb.append(','); sb.append(enrolled.get(i)); }
            prefs.edit().putString(KEY_CODE,sb.toString()).apply();
        }

        private void clearCode(){ enrolled.clear(); current.clear(); prefs.edit().remove(KEY_CODE).apply(); }
        private void loadWallpaper(){ try{wallpaperDrawable=WallpaperManager.getInstance(getContext()).getDrawable();}catch(Throwable ignored){wallpaperDrawable=null;} }
        private StyleProfile style(){ return styles.get(selectedStyle); }

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            int w=getWidth(),h=getHeight(); long now=SystemClock.uptimeMillis(); pruneTouches(now);
            drawWallpaper(canvas,w,h,now);
            float unlockP=getUnlockProgress(now);
            if(!unlocked||unlocking)drawLockCard(canvas,w,h,now,unlockP);else drawUnlockedHint(canvas,w,h);
            if(now<flashUntil&&!flash.isEmpty()&&unlockP<0.70f)drawFlash(canvas,w,h,now);
            if(unlocking||settingsOpen||now<flashUntil||style().oil||!touchPoints.isEmpty()||!classicRipples.isEmpty())postInvalidateOnAnimation();
        }

        private void drawWallpaper(Canvas canvas,int w,int h,long now){
            if(wallpaperDrawable!=null){wallpaperDrawable.setBounds(0,0,w,h);wallpaperDrawable.draw(canvas);}else{
                paint.setShader(new LinearGradient(0,0,0,h,new int[]{Color.rgb(35,44,77),Color.rgb(80,91,140),Color.rgb(152,119,154)},new float[]{0f,.57f,1f},Shader.TileMode.CLAMP));
                canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
            }
            float t=now/1000f;
            paint.setShader(new RadialGradient(w*(.70f+.03f*(float)Math.sin(t*.3f)),h*.20f,Math.max(w,h)*.36f,new int[]{Color.argb(26,255,255,255),Color.argb(0,255,255,255)},null,Shader.TileMode.CLAMP));
            canvas.drawCircle(w*.7f,h*.2f,Math.max(w,h)*.36f,paint);paint.setShader(null);
        }

        private void drawLockCard(Canvas canvas,int screenW,int screenH,long now,float p){
            float e=easeOut(p);
            float top=screenH*1.02f*e;
            float radius=dp(26f)*(float)Math.sin(Math.PI*Math.min(1f,p));
            float inertia=(float)Math.pow(Math.sin(Math.PI*Math.min(1f,p)),1.15);

            shadow.setColor(Color.TRANSPARENT);
            shadow.setShadowLayer(dp(26)*Math.max(.12f,1f-e),0,-dp(3),Color.argb((int)(110*(1f-e)),0,0,0));
            RectF card=new RectF(-dp(10),top,screenW+dp(10),top+screenH+dp(20));
            canvas.drawRoundRect(card,radius,radius,shadow);
            shadow.clearShadowLayer();

            canvas.save();
            canvas.translate(0,top);
            drawSurfaceVisual(canvas,screenW,screenH,now,inertia);
            drawUI(canvas,screenW,screenH);
            if(settingsOpen&&!unlocking)drawSettings(canvas,screenW,screenH);
            canvas.restore();
        }

        private void drawSurfaceVisual(Canvas canvas,float w,float h,long now,float inertia){
            if(style().oil)drawOilSurface(canvas,w,h,now,inertia);else drawClassicSurface(canvas,w,h,now);
        }

        private void drawOilSurface(Canvas canvas,float w,float h,long now,float inertia){
            StyleProfile s=style();
            oilShader.setFloatUniform("uResolution",w,h);
            oilShader.setFloatUniform("uTime",now/1000f);
            oilShader.setFloatUniform("uTop",s.tr/255f,s.tg/255f,s.tb/255f);
            oilShader.setFloatUniform("uMid",s.mr/255f,s.mg/255f,s.mb/255f);
            oilShader.setFloatUniform("uBottom",s.br/255f,s.bg/255f,s.bb/255f);
            oilShader.setFloatUniform("uOilStrength",s.oilStrength);
            oilShader.setFloatUniform("uClearStyle",selectedStyle==2?1f:0f);
            oilShader.setFloatUniform("uInertia",inertia);
            setTouchUniforms(now,w,h);
            paint.setShader(oilShader);canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
        }

        private void setTouchUniforms(long now,float w,float h){
            for(int slot=0;slot<4;slot++){
                int idx=touchPoints.size()-1-slot;
                if(idx>=0){TouchPoint tp=touchPoints.get(idx);float age=Math.min(1f,(now-tp.startedAt)/(float)TOUCH_FIELD_MS);oilShader.setFloatUniform("uTouch"+slot,tp.x/Math.max(1f,w),tp.y/Math.max(1f,h),tp.strength,age);}
                else oilShader.setFloatUniform("uTouch"+slot,-10f,-10f,0f,1f);
            }
        }

        private void drawClassicSurface(Canvas canvas,float w,float h,long now){
            paint.setShader(new LinearGradient(0,0,0,h,new int[]{Color.rgb(10,31,46),Color.rgb(11,74,86),Color.rgb(4,29,47)},new float[]{0f,.55f,1f},Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
            line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(1.2f));
            for(int i=0;i<9;i++){float y=h*(.19f+i*.075f);line.setColor(Color.argb(18,205,245,255));canvas.drawArc(new RectF(-w*.15f,y-dp(16),w*1.15f,y+dp(26)),184,172,false,line);}
            for(int i=classicRipples.size()-1;i>=0;i--){ClassicRipple r=classicRipples.get(i);float q=(now-r.startedAt)/(float)CLASSIC_RIPPLE_MS;if(q>=1f){classicRipples.remove(i);continue;}float rr=dp(16)+Math.min(w,h)*.33f*(float)Math.pow(q,.62);int a=(int)(150*(1-q)*(1-q));for(int k=0;k<4;k++){float rk=rr-dp(k*24);if(rk<=0)continue;line.setColor(Color.argb(Math.max(0,a-k*22),220,249,255));line.setStrokeWidth(dp(k==0?2.1f:1.25f));canvas.drawCircle(r.x,r.y,rk,line);}}
            line.setStyle(Paint.Style.FILL);
        }

        private void drawUI(Canvas canvas,float w,float h){
            text.setTextAlign(Paint.Align.LEFT);text.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.NORMAL));text.setTextSize(dp(28));text.setColor(Color.argb(240,246,250,252));canvas.drawText("Ripple Code",dp(26),dp(68),text);
            text.setTextSize(dp(13));text.setColor(Color.argb(184,230,241,248));String mode=enrolled.isEmpty()?"등록 모드":"잠금 테스트";canvas.drawText(mode+"  ·  탭 순서 + 마지막 스와이프",dp(27),dp(94),text);
            if(!unlocking){drawPill(canvas,resetRect(w),"초기화");drawPill(canvas,settingsRect(w),"설정");}
            text.setTextAlign(Paint.Align.CENTER);text.setTextSize(dp(17));text.setColor(Color.argb(214,243,248,251));
            if(enrolled.isEmpty()){canvas.drawText("원하는 위치를 원하는 만큼 탭",w/2,h*.46f,text);text.setTextSize(dp(14));text.setColor(Color.argb(158,232,244,251));canvas.drawText("마지막에 어느 방향으로든 쭉 스와이프",w/2,h*.46f+dp(29),text);}else{canvas.drawText("등록한 위치를 같은 순서로 탭",w/2,h*.46f,text);text.setTextSize(dp(14));text.setColor(Color.argb(158,232,244,251));canvas.drawText("Swipe 방향은 인증값에 포함되지 않음",w/2,h*.46f+dp(29),text);}
            text.setTextSize(dp(13));text.setColor(Color.argb(188,240,250,255));canvas.drawText(style().name+(lockscreenMode?"  ·  LOCKSCREEN ARMED":""),w/2,h-dp(98),text);text.setTextSize(dp(15));canvas.drawText("현재 입력  "+current.size()+"회",w/2,h-dp(72),text);text.setTextSize(dp(12));text.setColor(Color.argb(135,235,248,252));canvas.drawText("숨은 판정 영역 2 × 5",w/2,h-dp(46),text);
        }

        private void drawPill(Canvas canvas,RectF r,String label){ui.setColor(Color.argb(46,255,255,255));canvas.drawRoundRect(r,dp(18),dp(18),ui);text.setTextAlign(Paint.Align.LEFT);text.setTextSize(dp(13));text.setColor(Color.argb(225,248,252,255));canvas.drawText(label,r.left+dp(17),r.centerY()+dp(5),text);}

        private void drawSettings(Canvas canvas,float w,float h){
            RectF sheet=settingsSheetRect(w,h);ui.setColor(Color.argb(122,3,8,14));canvas.drawRect(0,0,w,h,ui);ui.setColor(Color.argb(235,22,30,42));canvas.drawRoundRect(sheet,dp(28),dp(28),ui);
            line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(1));line.setColor(Color.argb(54,255,255,255));canvas.drawRoundRect(sheet,dp(28),dp(28),line);line.setStyle(Paint.Style.FILL);
            text.setTextAlign(Paint.Align.LEFT);text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);text.setTextSize(dp(21));text.setColor(Color.WHITE);canvas.drawText("설정",sheet.left+dp(22),sheet.top+dp(34),text);text.setTypeface(android.graphics.Typeface.DEFAULT);text.setTextSize(dp(12));text.setColor(Color.argb(170,225,237,245));canvas.drawText("Style",sheet.left+dp(22),sheet.top+dp(56),text);
            for(int i=0;i<styles.size();i++){RectF item=styleItemRect(i,w,h);StyleProfile s=styles.get(i);boolean active=i==selectedStyle;ui.setColor(active?Color.argb(83,170,222,240):Color.argb(32,255,255,255));canvas.drawRoundRect(item,dp(18),dp(18),ui);RectF sw=new RectF(item.left+dp(9),item.top+dp(9),item.left+dp(48),item.bottom-dp(9));paint.setShader(new LinearGradient(sw.left,sw.top,sw.right,sw.bottom,new int[]{Color.rgb(s.tr,s.tg,s.tb),Color.rgb(s.mr,s.mg,s.mb),Color.rgb(s.br,s.bg,s.bb)},null,Shader.TileMode.CLAMP));canvas.drawRoundRect(sw,dp(12),dp(12),paint);paint.setShader(null);text.setTextAlign(Paint.Align.LEFT);text.setTextSize(dp(14));text.setColor(Color.argb(240,248,251,255));canvas.drawText(s.name,item.left+dp(58),item.top+dp(21),text);text.setTextSize(dp(10.5f));text.setColor(Color.argb(158,219,232,242));canvas.drawText(s.desc,item.left+dp(58),item.top+dp(38),text);}
            RectF lock=lockModeRect(w,h);ui.setColor(lockscreenMode?Color.argb(76,154,222,188):Color.argb(34,255,255,255));canvas.drawRoundRect(lock,dp(18),dp(18),ui);text.setTextAlign(Paint.Align.LEFT);text.setTextSize(dp(14));text.setColor(Color.argb(240,248,251,255));canvas.drawText("Lockscreen overlay",lock.left+dp(15),lock.top+dp(21),text);text.setTextSize(dp(10.5f));text.setColor(Color.argb(158,219,232,242));canvas.drawText(lockscreenMode?"ARMED · 시스템 잠금 위에 표시":"OFF · 일반 데모",lock.left+dp(15),lock.top+dp(39),text);text.setTextAlign(Paint.Align.RIGHT);text.setTextSize(dp(12));text.setColor(Color.WHITE);canvas.drawText(lockscreenMode?"ON":"OFF",lock.right-dp(15),lock.centerY()+dp(4),text);
            text.setTextAlign(Paint.Align.LEFT);text.setTextSize(dp(10));text.setColor(Color.argb(132,220,231,240));canvas.drawText("보안 잠금 해제는 Android 시스템 Keyguard가 최종 확인",sheet.left+dp(22),sheet.bottom-dp(18),text);
        }

        private void drawFlash(Canvas canvas,int w,int h,long now){int a=(int)(255*Math.min(1,(flashUntil-now)/900f+.15f));paint.setColor(flashGood?Color.argb(Math.min(a,84),106,235,184):Color.argb(Math.min(a,74),248,157,142));canvas.drawRect(0,0,w,h,paint);text.setTextAlign(Paint.Align.CENTER);text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);text.setTextSize(dp(30));text.setColor(Color.argb(a,255,255,255));canvas.drawText(flash,w/2,h*.67f,text);}
        private void drawUnlockedHint(Canvas canvas,int w,int h){if(lockscreenMode)return;text.setTextAlign(Paint.Align.CENTER);text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);text.setTextSize(dp(18));text.setColor(Color.argb(215,255,255,255));canvas.drawText("UNLOCKED",w/2,h-dp(78),text);text.setTypeface(android.graphics.Typeface.DEFAULT);text.setTextSize(dp(12));canvas.drawText("화면을 탭하면 다시 잠금",w/2,h-dp(52),text);}

        @Override public boolean onTouchEvent(MotionEvent e){
            if(unlocking)return true;
            if(unlocked){if(e.getActionMasked()==MotionEvent.ACTION_DOWN&&!lockscreenMode){unlocked=false;dismissRequested=false;invalidate();}return true;}
            float x=e.getX(),y=e.getY();
            if(settingsOpen){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(!settingsSheetRect(getWidth(),getHeight()).contains(x,y)){settingsOpen=false;invalidate();return true;}int hit=styleHitIndex(x,y,getWidth(),getHeight());if(hit>=0){selectedStyle=hit;prefs.edit().putInt(KEY_STYLE,hit).apply();settingsOpen=false;performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);invalidate();return true;}if(lockModeRect(getWidth(),getHeight()).contains(x,y)){lockscreenMode=!lockscreenMode;prefs.edit().putBoolean(KEY_LOCK_MODE,lockscreenMode).apply();applyLockscreenMode(lockscreenMode);performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);invalidate();return true;}}return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(resetRect(getWidth()).contains(x,y)){clearCode();flash("등록값 초기화",false);performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);invalidate();return true;}if(settingsRect(getWidth()).contains(x,y)){settingsOpen=true;performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);invalidate();return true;}downX=x;downY=y;lastTrailX=x;lastTrailY=y;lastTrailAt=SystemClock.uptimeMillis();moved=false;addTouch(x,y,1.28f);if(!style().oil)classicRipples.add(new ClassicRipple(x,y,SystemClock.uptimeMillis()));performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);postInvalidateOnAnimation();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=x-downX,dy=y-downY;if(dx*dx+dy*dy>swipeThreshold()*swipeThreshold())moved=true;long now=SystemClock.uptimeMillis();float tx=x-lastTrailX,ty=y-lastTrailY;if(now-lastTrailAt>30&&(tx*tx+ty*ty)>dp(8)*dp(8)){addTouch(x,y,.70f);lastTrailX=x;lastTrailY=y;lastTrailAt=now;}return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP){float dx=x-downX,dy=y-downY;boolean swipe=moved||dx*dx+dy*dy>swipeThreshold()*swipeThreshold();if(swipe)finalizeSequence();else{current.add(zoneFor(x,y));performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);}invalidate();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){moved=false;return true;}return true;
        }

        private void addTouch(float x,float y,float strength){touchPoints.add(new TouchPoint(x,y,strength,SystemClock.uptimeMillis()));while(touchPoints.size()>14)touchPoints.remove(0);}
        private void pruneTouches(long now){for(int i=touchPoints.size()-1;i>=0;i--)if(now-touchPoints.get(i).startedAt>TOUCH_FIELD_MS)touchPoints.remove(i);}
        private void finalizeSequence(){performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);if(current.isEmpty()){flash("먼저 한 번 이상 탭",false);return;}if(enrolled.isEmpty()){enrolled.addAll(current);saveCode();int n=current.size();current.clear();flash("Ripple Code 저장 · "+n+"회",true);return;}boolean match=enrolled.equals(current);current.clear();if(match){flash("MATCH",true);performHapticFeedback(HapticFeedbackConstants.CONFIRM);beginUnlock();}else{flash("NO MATCH",false);performHapticFeedback(HapticFeedbackConstants.REJECT);}}
        private void beginUnlock(){settingsOpen=false;unlocking=true;unlocked=false;dismissRequested=false;unlockStartedAt=SystemClock.uptimeMillis();postInvalidateOnAnimation();}
        private float getUnlockProgress(long now){if(!unlocking)return unlocked?1f:0f;float p=Math.min(1f,(now-unlockStartedAt)/(float)UNLOCK_MS);if(p>=1f){unlocking=false;unlocked=true;if(lockscreenMode&&!dismissRequested){dismissRequested=true;post(MainActivity.this::requestSystemDismiss);}return 1f;}return p;}
        void onSystemDismissed(){unlocking=false;unlocked=true;invalidate();}
        void onSystemDismissFailed(){unlocking=false;unlocked=false;dismissRequested=false;flash("SYSTEM LOCK",false);invalidate();}

        private int zoneFor(float x,float y){int col=Math.max(0,Math.min(COLS-1,(int)(x/Math.max(1f,getWidth())*COLS)));int row=Math.max(0,Math.min(ROWS-1,(int)(y/Math.max(1f,getHeight())*ROWS)));return row*COLS+col;}
        private int styleHitIndex(float x,float y,float w,float h){for(int i=0;i<styles.size();i++)if(styleItemRect(i,w,h).contains(x,y))return i;return -1;}
        private RectF styleItemRect(int i,float w,float h){RectF s=settingsSheetRect(w,h);float top=s.top+dp(69),ih=dp(48);return new RectF(s.left+dp(14),top+i*(ih+dp(6)),s.right-dp(14),top+i*(ih+dp(6))+ih);}
        private RectF lockModeRect(float w,float h){RectF s=settingsSheetRect(w,h);float y=s.top+dp(69)+styles.size()*dp(54)+dp(8);return new RectF(s.left+dp(14),y,s.right-dp(14),y+dp(50));}
        private RectF resetRect(float w){float r=w-dp(110);return new RectF(r-dp(82),dp(37),r,dp(77));}
        private RectF settingsRect(float w){float r=w-dp(20);return new RectF(r-dp(82),dp(37),r,dp(77));}
        private RectF settingsSheetRect(float w,float h){return new RectF(dp(14),h*.10f,w-dp(14),h*.91f);}
        private float swipeThreshold(){return Math.max(dp(72),Math.min(getWidth(),getHeight())*.115f);}
        private float easeOut(float x){x=Math.max(0,Math.min(1,x));return 1f-(float)Math.pow(1f-x,3f);}
        private void flash(String s,boolean good){flash=s;flashGood=good;flashUntil=SystemClock.uptimeMillis()+900;postInvalidateOnAnimation();}
        private float dp(float v){return v*density;}

        private final class TouchPoint{final float x,y,strength;final long startedAt;TouchPoint(float x,float y,float strength,long t){this.x=x;this.y=y;this.strength=strength;this.startedAt=t;}}
        private final class ClassicRipple{final float x,y;final long startedAt;ClassicRipple(float x,float y,long t){this.x=x;this.y=y;this.startedAt=t;}}
        private final class StyleProfile{final String name,desc;final int tr,tg,tb,mr,mg,mb,br,bg,bb;final float oilStrength;final boolean oil;StyleProfile(String n,String d,int tr,int tg,int tb,int mr,int mg,int mb,int br,int bg,int bb,float os,boolean oil){name=n;desc=d;this.tr=tr;this.tg=tg;this.tb=tb;this.mr=mr;this.mg=mg;this.mb=mb;this.br=br;this.bg=bg;this.bb=bb;oilStrength=os;this.oil=oil;}}
    }
}
