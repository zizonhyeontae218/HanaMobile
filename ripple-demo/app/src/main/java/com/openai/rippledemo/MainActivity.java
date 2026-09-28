package com.openai.rippledemo;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
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
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private RippleLockView lockView;
    private boolean launchedOverKeyguard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        launchedOverKeyguard = km != null && km.isKeyguardLocked();

        lockView = new RippleLockView();
        setContentView(lockView);
        applyLockscreenMode(lockView.isLockscreenMode());
        hideSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lockView != null) lockView.invalidate();
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
            @Override public void onDismissSucceeded() { lockView.onSystemDismissed(); }
            @Override public void onDismissCancelled() { lockView.onSystemDismissFailed(); }
            @Override public void onDismissError() { lockView.onSystemDismissFailed(); }
        });
    }

    private final class RippleLockView extends View {
        private static final int COLS = 2;
        private static final int ROWS = 5;
        private static final long CLASSIC_RIPPLE_MS = 1050L;
        private static final long TOUCH_FIELD_MS = 1700L;
        private static final long FAIL_SHAKE_MS = 820L;
        private static final long CLOCK_TINT_MS = 1150L;
        private static final long UNLOCK_MS = 2400L;
        private static final long SETTINGS_ANIM_MS = 340L;

        private static final String PREFS = "ripple_code_v4";
        private static final String KEY_CODE = "code";
        private static final String KEY_STYLE = "style";
        private static final String KEY_LOCK_MODE = "lock_mode";
        private static final String KEY_LANG = "lang";
        private static final String KEY_FEEDBACK = "feedback";
        private static final String KEY_DEBUG = "debug";

        private static final int LANG_KR = 0;
        private static final int LANG_EN = 1;
        private static final int FEEDBACK_HAPTIC = 0;
        private static final int FEEDBACK_VISUAL = 1;

        private static final int PICK_NONE = -1;
        private static final int PICK_SURFACE = 0;
        private static final int PICK_LANGUAGE = 1;
        private static final int PICK_FEEDBACK = 2;
        private static final int PICK_DEBUG = 3;

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
        private boolean moved;
        private boolean settingsOpen;
        private boolean setupOpen;
        private boolean lockscreenMode;
        private boolean debugMode;
        private int pickerType = PICK_NONE;
        private int selectedStyle;
        private int language;
        private int failFeedback;
        private boolean unlocking;
        private boolean unlocked;
        private boolean dismissRequested;
        private boolean systemDismissed;
        private boolean animationFinished;
        private long unlockStartedAt;
        private long failShakeStartedAt = -1L;
        private long clockRedUntil = 0L;
        private long settingsOpenedAt = 0L;

        private static final String OIL_SHADER =
                "uniform float2 uResolution;\n" +
                "uniform float uTime;\n" +
                "uniform float3 uTop;\n" +
                "uniform float3 uMid;\n" +
                "uniform float3 uBottom;\n" +
                "uniform float uOilStrength;\n" +
                "uniform float uClearStyle;\n" +
                "uniform float uInertia;\n" +
                "uniform float uFail;\n" +
                "uniform float4 uTouch0;\n" +
                "uniform float4 uTouch1;\n" +
                "uniform float4 uTouch2;\n" +
                "uniform float4 uTouch3;\n" +
                "float hash21(float2 p){p=fract(p*float2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);}\n" +
                "float noise(float2 p){float2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);float a=hash21(i),b=hash21(i+float2(1,0)),c=hash21(i+float2(0,1)),d=hash21(i+float2(1,1));return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);}\n" +
                "float fbm(float2 p){float v=0.0;v+=noise(p)*0.52;p=p*2.03+7.1;v+=noise(p)*0.25;p=p*2.01-3.7;v+=noise(p)*0.13;p=p*2.04+2.4;v+=noise(p)*0.07;return v;}\n" +
                "float2 metricDelta(float2 d,float aspect){return float2(d.x*aspect,d.y);}\n" +
                "float2 warpTouch(float2 uv,float4 q,float aspect){float life=clamp(1.0-q.w,0.0,1.0);float2 d=uv-q.xy;float2 md=metricDelta(d,aspect);float r=max(length(md),0.0015);float fall=exp(-r*r*38.0);float wave=sin(r*62.0-q.w*18.0);float2 dir=float2(md.x/max(aspect,0.001),md.y)/r;float2 tan=float2(-dir.y,dir.x);return (dir*(0.045+0.030*wave)+tan*0.016*sin(q.w*5.2+r*21.0))*q.z*fall*pow(life,1.32);}\n" +
                "float rippleGlow(float2 uv,float4 q,float aspect){float life=clamp(1.0-q.w,0.0,1.0);float2 d=metricDelta(uv-q.xy,aspect);float r=length(d);float target=0.012+q.w*0.078;float ring=exp(-pow((r-target)*62.0,2.0));return ring*q.z*life;}\n" +
                "float3 palette(float x){float3 bronze=float3(0.82,0.52,0.30),purple=float3(0.52,0.30,0.66),blue=float3(0.22,0.53,0.72),teal=float3(0.22,0.70,0.61),mag=float3(0.72,0.34,0.60),gold=float3(0.82,0.67,0.34);x=fract(x);if(x<0.17)return mix(bronze,purple,x/0.17);if(x<0.34)return mix(purple,blue,(x-0.17)/0.17);if(x<0.52)return mix(blue,teal,(x-0.34)/0.18);if(x<0.70)return mix(teal,mag,(x-0.52)/0.18);if(x<0.86)return mix(mag,gold,(x-0.70)/0.16);return mix(gold,bronze,(x-0.86)/0.14);}\n" +
                "half4 main(float2 frag){\n" +
                "float2 uv=frag/uResolution;float aspect=uResolution.x/uResolution.y;float2 p=float2(uv.x*aspect,uv.y);float t=uTime;\n" +
                "p.y+=uInertia*(0.070+0.028*noise(p*2.1+float2(2.7,t*0.08)));\n" +
                "float2 failJ=float2(noise(p*3.1+float2(t*7.6,1.2)),noise(p*2.7+float2(4.4,-t*6.9)))-0.5;p+=failJ*(0.125*uFail);\n" +
                "p+=warpTouch(uv,uTouch0,aspect)+warpTouch(uv,uTouch1,aspect)+warpTouch(uv,uTouch2,aspect)+warpTouch(uv,uTouch3,aspect);\n" +
                "float2 flow=float2(0.020*t,-0.012*t);float large=fbm(p*1.52+flow);float2 curl=float2(noise(p*2.5+float2(0.0,t*0.020)),noise(p*2.7+float2(6.0,-t*0.017)))-0.5;\n" +
                "float thick=fbm((p+curl*0.18)*2.75-flow*0.65)+0.42*fbm((p-curl*0.12)*5.4+flow*0.37);thick+=sin(t*0.31+large*6.283)*0.028+uFail*0.11*noise(p*8.0+t*2.3);\n" +
                "float film=smoothstep(0.25,0.60,large+0.21*noise(p*3.6-flow));float band=fract(thick*2.03+large*0.28);float3 oil=palette(band);\n" +
                "float y=clamp(uv.y,0.0,1.0);float3 base=mix(uTop,uMid,smoothstep(0.0,0.58,y));base=mix(base,uBottom,smoothstep(0.50,1.0,y));\n" +
                "float water=0.5+0.5*sin(uv.y*9.0+fbm(p*1.9)*2.4+t*0.42);base*=0.942+0.058*water;\n" +
                "float spec=pow(clamp(1.0-abs(noise(p*2.4+flow*0.2)-0.52)*3.0,0.0,1.0),2.2);float3 silver=float3(0.80,0.91,0.90);\n" +
                "float strength=uOilStrength*film*(0.52+0.74*spec);strength*=mix(1.0,0.88,uClearStyle);float3 col=mix(base,oil,clamp(strength,0.0,0.82));col=mix(col,silver,spec*film*0.12);\n" +
                "float rg=rippleGlow(uv,uTouch0,aspect)+rippleGlow(uv,uTouch1,aspect)+rippleGlow(uv,uTouch2,aspect)+rippleGlow(uv,uTouch3,aspect);col=mix(col,float3(0.78,0.94,0.93),clamp(rg*0.070,0.0,0.075));\n" +
                "return half4(half3(clamp(col,0.0,1.0)),1.0);}";

        RippleLockView() {
            super(MainActivity.this);
            density = getResources().getDisplayMetrics().density;
            prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            initStyles();
            selectedStyle = clamp(prefs.getInt(KEY_STYLE, 1),0,styles.size()-1);
            lockscreenMode = prefs.getBoolean(KEY_LOCK_MODE, false);
            language = clamp(prefs.getInt(KEY_LANG, LANG_KR),LANG_KR,LANG_EN);
            failFeedback = clamp(prefs.getInt(KEY_FEEDBACK, FEEDBACK_HAPTIC),FEEDBACK_HAPTIC,FEEDBACK_VISUAL);
            debugMode = prefs.getBoolean(KEY_DEBUG, false);
            loadCode();
            loadWallpaper();
            oilShader = new RuntimeShader(OIL_SHADER);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            setFocusable(true);
        }

        boolean isLockscreenMode() { return lockscreenMode; }

        private void initStyles() {
            styles.add(new StyleProfile("Classic",10,31,46,11,74,86,4,29,47,0f,false));
            styles.add(new StyleProfile("Oil Sheen",30,56,67,58,94,103,86,117,126,0.50f,true));
            styles.add(new StyleProfile("Oil Clear",68,104,114,101,137,143,136,160,162,0.40f,true));
            styles.add(new StyleProfile("Oil Night",14,27,43,24,49,66,39,69,82,0.46f,true));
            styles.add(new StyleProfile("Getura Oil",73,104,113,98,127,124,120,117,142,0.47f,true));
        }

        private void loadCode(){
            enrolled.clear();String saved=prefs.getString(KEY_CODE,"");if(saved==null||saved.isEmpty())return;
            for(String part:saved.split(","))try{enrolled.add(Integer.parseInt(part));}catch(NumberFormatException ignored){}
        }

        private void saveCode(){
            StringBuilder sb=new StringBuilder();for(int i=0;i<enrolled.size();i++){if(i>0)sb.append(',');sb.append(enrolled.get(i));}prefs.edit().putString(KEY_CODE,sb.toString()).apply();
        }

        private void clearCode(){enrolled.clear();current.clear();prefs.edit().remove(KEY_CODE).apply();}
        private void loadWallpaper(){try{wallpaperDrawable=WallpaperManager.getInstance(getContext()).getDrawable();}catch(Throwable ignored){wallpaperDrawable=null;}}
        private StyleProfile style(){return styles.get(selectedStyle);}

        private boolean keyguardShowing(){KeyguardManager km=(KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);return km!=null&&km.isKeyguardLocked();}
        private boolean settingsAllowed(){return !launchedOverKeyguard&&!keyguardShowing();}

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            int w=getWidth(),h=getHeight();long now=SystemClock.uptimeMillis();pruneTouches(now);
            drawWallpaper(canvas,w,h,now);
            float unlockP=getUnlockProgress(now);
            if(!unlocked||unlocking)drawLockCard(canvas,w,h,now,unlockP);else if(!lockscreenMode&&debugMode)drawUnlockedDebug(canvas,w,h);
            boolean animate=unlocking||settingsOpen||style().oil||!touchPoints.isEmpty()||!classicRipples.isEmpty()||isFailShaking(now)||now<clockRedUntil;
            if(animate)postInvalidateOnAnimation();else postInvalidateDelayed(1000L);
        }

        private void drawWallpaper(Canvas canvas,int w,int h,long now){
            if(wallpaperDrawable!=null){wallpaperDrawable.setBounds(0,0,w,h);wallpaperDrawable.draw(canvas);}else{
                paint.setShader(new LinearGradient(0,0,0,h,new int[]{Color.rgb(35,44,77),Color.rgb(80,91,140),Color.rgb(152,119,154)},new float[]{0f,.57f,1f},Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
            }
            float t=now/1000f;paint.setShader(new RadialGradient(w*(.70f+.03f*(float)Math.sin(t*.3f)),h*.20f,Math.max(w,h)*.36f,new int[]{Color.argb(24,255,255,255),Color.argb(0,255,255,255)},null,Shader.TileMode.CLAMP));canvas.drawCircle(w*.7f,h*.2f,Math.max(w,h)*.36f,paint);paint.setShader(null);
        }

        private void drawLockCard(Canvas canvas,int screenW,int screenH,long now,float p){
            float motion=(float)Math.pow(clamp01(p),2.42);
            float top=screenH*1.08f*motion;
            float inertia=(float)Math.pow(clamp01(p),1.30);
            float radius=dp(24f)*(float)Math.sin(Math.PI*clamp01(p));
            RectF card=new RectF(-dp(8),top,screenW+dp(8),top+screenH+dp(16));
            shadow.setColor(Color.TRANSPARENT);shadow.setShadowLayer(dp(24)*Math.max(.12f,1f-motion),0,-dp(2),Color.argb((int)(92*(1f-motion)),0,0,0));canvas.drawRoundRect(card,radius,radius,shadow);shadow.clearShadowLayer();
            canvas.save();canvas.translate(0,top);drawSurfaceVisual(canvas,screenW,screenH,now,inertia);drawMinimalUI(canvas,screenW,screenH,now);if(settingsOpen&&settingsAllowed()&&!unlocking)drawSettings(canvas,screenW,screenH,now);canvas.restore();
        }

        private void drawSurfaceVisual(Canvas canvas,float w,float h,long now,float inertia){if(style().oil)drawOilSurface(canvas,w,h,now,inertia);else drawClassicSurface(canvas,w,h,now);}

        private void drawOilSurface(Canvas canvas,float w,float h,long now,float inertia){
            StyleProfile s=style();oilShader.setFloatUniform("uResolution",w,h);oilShader.setFloatUniform("uTime",now/1000f);oilShader.setFloatUniform("uTop",s.tr/255f,s.tg/255f,s.tb/255f);oilShader.setFloatUniform("uMid",s.mr/255f,s.mg/255f,s.mb/255f);oilShader.setFloatUniform("uBottom",s.br/255f,s.bg/255f,s.bb/255f);oilShader.setFloatUniform("uOilStrength",s.oilStrength);oilShader.setFloatUniform("uClearStyle",selectedStyle==2?1f:0f);oilShader.setFloatUniform("uInertia",inertia);oilShader.setFloatUniform("uFail",failAmount(now));setTouchUniforms(now,w,h);paint.setShader(oilShader);canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
        }

        private void setTouchUniforms(long now,float w,float h){
            for(int slot=0;slot<4;slot++){int idx=touchPoints.size()-1-slot;if(idx>=0){TouchPoint tp=touchPoints.get(idx);float age=Math.min(1f,(now-tp.startedAt)/(float)TOUCH_FIELD_MS);oilShader.setFloatUniform("uTouch"+slot,tp.x/Math.max(1f,w),tp.y/Math.max(1f,h),tp.strength,age);}else oilShader.setFloatUniform("uTouch"+slot,-10f,-10f,0f,1f);}
        }

        private void drawClassicSurface(Canvas canvas,float w,float h,long now){
            paint.setShader(new LinearGradient(0,0,0,h,new int[]{Color.rgb(10,31,46),Color.rgb(11,74,86),Color.rgb(4,29,47)},new float[]{0f,.55f,1f},Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
            line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(1.1f));
            for(int i=0;i<7;i++){float y=h*(.24f+i*.082f);line.setColor(Color.argb(14,205,245,255));canvas.drawArc(new RectF(-w*.12f,y-dp(10),w*1.12f,y+dp(16)),184,172,false,line);}
            for(int i=classicRipples.size()-1;i>=0;i--){ClassicRipple r=classicRipples.get(i);float q=(now-r.startedAt)/(float)CLASSIC_RIPPLE_MS;if(q>=1f){classicRipples.remove(i);continue;}float rr=dp(10)+Math.min(w,h)*.22f*(float)Math.pow(q,.64);int a=(int)(112*(1-q)*(1-q));for(int k=0;k<3;k++){float rk=rr-dp(k*17);if(rk<=0)continue;line.setColor(Color.argb(Math.max(0,a-k*28),220,249,255));line.setStrokeWidth(dp(k==0?1.65f:1.0f));canvas.drawCircle(r.x,r.y,rk,line);}}
            line.setStyle(Paint.Style.FILL);
        }

        private void drawMinimalUI(Canvas canvas,float w,float h,long now){
            drawSurfaceClock(canvas,w,h,now);
            if(settingsAllowed())drawSettingsBlobButton(canvas,gearRect(w));
            if(enrolled.isEmpty())drawEnrollmentHint(canvas,w,h);
            if(debugMode)drawDebug(canvas,w,h);
        }

        private void drawSurfaceClock(Canvas canvas,float w,float h,long now){
            Calendar c=Calendar.getInstance();String time=String.format(Locale.US,"%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE));
            float tint=clockTintAmount(now);int cr=clamp((int)(246+5*tint),0,255),cg=clamp((int)(249-78*tint),0,255),cb=clamp((int)(252-74*tint),0,255);int clockColor=Color.rgb(cr,cg,cb);
            float cx=w/2f,cy=dp(128),mainW=Math.min(w-dp(84),dp(286)),mainH=dp(96);RectF main=new RectF(cx-mainW/2,cy-mainH/2,cx+mainW/2,cy+mainH/2);
            float bubbleR=dp(29),bubbleCx=main.right-dp(26),bubbleCy=main.bottom+dp(13);
            ui.setShadowLayer(dp(20),0,dp(8),Color.argb(50,0,0,0));ui.setColor(Color.argb(34,255,255,255));canvas.drawRoundRect(main,dp(42),dp(42),ui);canvas.drawCircle(bubbleCx,bubbleCy,bubbleR,ui);ui.clearShadowLayer();
            ui.setColor(Color.argb(30,255,255,255));canvas.drawOval(new RectF(main.right-dp(62),main.bottom-dp(18),bubbleCx+dp(10),bubbleCy+dp(13)),ui);
            line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(1));line.setColor(Color.argb((int)(70+45*tint),255,(int)(255-95*tint),(int)(255-90*tint)));canvas.drawRoundRect(main,dp(42),dp(42),line);canvas.drawCircle(bubbleCx,bubbleCy,bubbleR,line);line.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0,main.top,0,main.bottom,new int[]{Color.argb(52,255,255,255),Color.argb(7,255,255,255)},null,Shader.TileMode.CLAMP));canvas.drawRoundRect(new RectF(main.left+dp(3),main.top+dp(3),main.right-dp(3),main.centerY()+dp(3)),dp(39),dp(39),paint);paint.setShader(null);
            text.setTextAlign(Paint.Align.CENTER);text.setTypeface(android.graphics.Typeface.create("sans-serif-light",0));text.setTextSize(dp(52));text.setColor(clockColor);canvas.drawText(time,cx,cy+dp(18),text);
            String date=dateString(c);text.setTypeface(android.graphics.Typeface.create("sans",0));text.setTextSize(dp(10.5f));text.setColor(Color.argb(190,cr,cg,cb));canvas.drawText(date,bubbleCx,bubbleCy+dp(4),text);
        }

        private String dateString(Calendar c){
            if(language==LANG_KR){String[] d={"일","월","화","수","목","금","토"};return String.format(Locale.KOREA,"%d/%d %s",c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH),d[c.get(Calendar.DAY_OF_WEEK)-1]);}
            String[] d={"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};return String.format(Locale.US,"%s %d/%d",d[c.get(Calendar.DAY_OF_WEEK)-1],c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH));
        }

        private void drawSettingsBlobButton(Canvas canvas,RectF r){
            float cx=r.centerX(),cy=r.centerY();ui.setShadowLayer(dp(12),0,dp(4),Color.argb(48,0,0,0));ui.setColor(Color.argb(38,255,255,255));canvas.drawCircle(cx,cy,dp(19),ui);canvas.drawCircle(cx-dp(17),cy+dp(14),dp(7),ui);ui.clearShadowLayer();ui.setColor(Color.argb(32,255,255,255));canvas.drawOval(new RectF(cx-dp(21),cy+dp(7),cx-dp(4),cy+dp(18)),ui);
            line.setStyle(Paint.Style.STROKE);line.setStrokeCap(Paint.Cap.ROUND);line.setStrokeWidth(dp(1.7f));line.setColor(Color.argb(215,247,251,253));canvas.drawCircle(cx,cy,dp(7.5f),line);canvas.drawCircle(cx,cy,dp(2.3f),line);for(int i=0;i<6;i++){double a=Math.PI*2*i/6;canvas.drawLine(cx+(float)Math.cos(a)*dp(9),cy+(float)Math.sin(a)*dp(9),cx+(float)Math.cos(a)*dp(12),cy+(float)Math.sin(a)*dp(12),line);}line.setStrokeCap(Paint.Cap.BUTT);line.setStyle(Paint.Style.FILL);
        }

        private void drawEnrollmentHint(Canvas canvas,float w,float h){
            text.setTextAlign(Paint.Align.CENTER);text.setTypeface(android.graphics.Typeface.create("sans",0));text.setColor(Color.argb(205,242,248,251));text.setTextSize(dp(16));canvas.drawText(language==LANG_KR?"Ripple Code 설정":"Set Ripple Code",w/2,h*.49f,text);text.setTextSize(dp(12.5f));text.setColor(Color.argb(150,232,242,248));canvas.drawText(language==LANG_KR?"위치를 탭한 뒤 길게 스와이프":"Tap positions, then finish with a long swipe",w/2,h*.49f+dp(26),text);
        }

        private void drawDebug(Canvas canvas,float w,float h){
            line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(.8f));line.setColor(Color.argb(70,255,255,255));for(int i=1;i<COLS;i++)canvas.drawLine(w*i/COLS,0,w*i/COLS,h,line);for(int i=1;i<ROWS;i++)canvas.drawLine(0,h*i/ROWS,w,h*i/ROWS,line);line.setStyle(Paint.Style.FILL);text.setTextAlign(Paint.Align.LEFT);text.setTypeface(android.graphics.Typeface.MONOSPACE);text.setTextSize(dp(10));text.setColor(Color.argb(210,235,255,240));canvas.drawText("DEBUG · "+style().name+" · input="+current.size()+" · keyguard="+keyguardShowing(),dp(12),h-dp(16),text);
        }

        private void drawUnlockedDebug(Canvas canvas,float w,float h){text.setTextAlign(Paint.Align.CENTER);text.setTypeface(android.graphics.Typeface.MONOSPACE);text.setTextSize(dp(11));text.setColor(Color.argb(180,255,255,255));canvas.drawText("UNLOCKED · tap to relock demo",w/2,h-dp(28),text);}

        private void drawSettings(Canvas canvas,float w,float h,long now){
            float p=clamp01((now-settingsOpenedAt)/(float)SETTINGS_ANIM_MS);float e=1f-(float)Math.pow(1f-p,3f);RectF target=settingsSheetRect(w,h);RectF b=gearRect(w);float cx=lerp(b.centerX(),target.centerX(),e),cy=lerp(b.centerY(),target.centerY(),e),bw=lerp(dp(42),target.width(),e),bh=lerp(dp(42),target.height(),e);RectF sheet=new RectF(cx-bw/2,cy-bh/2,cx+bw/2,cy+bh/2);
            ui.setColor(Color.argb((int)(92*e),2,8,14));canvas.drawRect(0,0,w,h,ui);ui.setShadowLayer(dp(24)*e,0,dp(8)*e,Color.argb((int)(64*e),0,0,0));ui.setColor(Color.argb((int)(235*e),22,31,43));canvas.drawRoundRect(sheet,dp(34),dp(34),ui);ui.clearShadowLayer();
            if(e<.72f)return;
            float alpha=clamp01((e-.72f)/.28f);int a=(int)(255*alpha);text.setTextAlign(Paint.Align.LEFT);text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);text.setTextSize(dp(20));text.setColor(Color.argb(a,248,251,253));canvas.drawText(language==LANG_KR?"설정":"Settings",target.left+dp(22),target.top+dp(38),text);text.setTypeface(android.graphics.Typeface.DEFAULT);
            if(setupOpen){drawSetupPage(canvas,target,w,h,a);return;}
            String[] labels=language==LANG_KR?new String[]{"표면","언어","실패 피드백","디버그 모드","잠금화면 설정","Ripple Code 초기화"}:new String[]{"Surface","Language","Failure feedback","Debug mode","Lockscreen setup","Reset Ripple Code"};
            String[] values=new String[]{style().name,language==LANG_KR?"한국어":"English",feedbackName(),debugMode?"ON":"OFF",setupStatus(),""};
            for(int i=0;i<labels.length;i++){RectF row=settingsRowRect(i,w,h);ui.setColor(i==5?Color.argb((int)(28*alpha),255,120,120):Color.argb((int)(28*alpha),255,255,255));canvas.drawRoundRect(row,dp(19),dp(19),ui);text.setTextAlign(Paint.Align.LEFT);text.setTextSize(dp(14));text.setColor(Color.argb(a,246,250,252));canvas.drawText(labels[i],row.left+dp(16),row.centerY()+dp(5),text);if(i<5){text.setTextAlign(Paint.Align.RIGHT);text.setTextSize(dp(12));text.setColor(Color.argb((int)(180*alpha),220,235,244));canvas.drawText(values[i]+"  ›",row.right-dp(15),row.centerY()+dp(4),text);}}
            if(pickerType!=PICK_NONE)drawPicker(canvas,w,h,a);
        }

        private void drawPicker(Canvas canvas,float w,float h,int alpha){
            String[] items=pickerItems();if(items==null)return;RectF box=pickerRect(w,h,items.length);ui.setShadowLayer(dp(22),0,dp(7),Color.argb(80,0,0,0));ui.setColor(Color.argb(246,30,39,52));canvas.drawRoundRect(box,dp(26),dp(26),ui);ui.clearShadowLayer();
            for(int i=0;i<items.length;i++){RectF r=pickerItemRect(i,w,h,items.length);boolean active=pickerSelection()==i;ui.setColor(active?Color.argb(62,170,225,240):Color.argb(20,255,255,255));canvas.drawRoundRect(r,dp(16),dp(16),ui);text.setTextAlign(Paint.Align.LEFT);text.setTypeface(android.graphics.Typeface.DEFAULT);text.setTextSize(dp(14));text.setColor(Color.argb(alpha,247,250,252));canvas.drawText(items[i],r.left+dp(15),r.centerY()+dp(5),text);if(active){text.setTextAlign(Paint.Align.RIGHT);text.setColor(Color.argb(alpha,196,237,245));canvas.drawText("✓",r.right-dp(15),r.centerY()+dp(5),text);}}
        }

        private void drawSetupPage(Canvas canvas,RectF sheet,float w,float h,int a){
            text.setTextAlign(Paint.Align.LEFT);text.setTypeface(android.graphics.Typeface.DEFAULT);text.setTextSize(dp(12));text.setColor(Color.argb((int)(175*(a/255f)),220,235,244));canvas.drawText(language==LANG_KR?"권한과 시스템 상태를 확인합니다":"Check permissions and system state",sheet.left+dp(22),sheet.top+dp(62),text);
            String[] labels=language==LANG_KR?new String[]{"잠금화면 오버레이","다른 앱 위에 표시","배터리 최적화","시스템 화면 잠금","뒤로"}:new String[]{"Lockscreen overlay","Display over apps","Battery optimization","System screen lock","Back"};
            String[] values=new String[]{lockscreenMode?"ON":"OFF",Settings.canDrawOverlays(MainActivity.this)?"ALLOWED":"SETUP",batteryStatus(),deviceSecure()?"SECURE":"NONE",""};
            for(int i=0;i<labels.length;i++){RectF r=setupRowRect(i,w,h);ui.setColor(Color.argb(28,255,255,255));canvas.drawRoundRect(r,dp(18),dp(18),ui);text.setTextAlign(Paint.Align.LEFT);text.setTextSize(dp(13.5f));text.setColor(Color.argb(a,246,250,252));canvas.drawText(labels[i],r.left+dp(15),r.centerY()+dp(5),text);if(i<4){text.setTextAlign(Paint.Align.RIGHT);text.setTextSize(dp(11.5f));text.setColor(Color.argb((int)(180*(a/255f)),220,235,244));canvas.drawText(values[i]+"  ›",r.right-dp(14),r.centerY()+dp(4),text);}}
            text.setTextAlign(Paint.Align.LEFT);text.setTextSize(dp(9.5f));text.setColor(Color.argb((int)(125*(a/255f)),214,227,237));canvas.drawText(language==LANG_KR?"Android 보안 Keyguard는 시스템이 최종 해제합니다":"Android still performs the final secure Keyguard unlock",sheet.left+dp(22),sheet.bottom-dp(17),text);
        }

        private String feedbackName(){return failFeedback==FEEDBACK_HAPTIC?(language==LANG_KR?"진동":"Vibration"):(language==LANG_KR?"시계 틴트":"Clock tint");}
        private String setupStatus(){return lockscreenMode?(language==LANG_KR?"사용 중":"Enabled"):(language==LANG_KR?"설정 필요":"Setup");}
        private String batteryStatus(){PowerManager pm=(PowerManager)getSystemService(Context.POWER_SERVICE);return pm!=null&&pm.isIgnoringBatteryOptimizations(getPackageName())?"UNRESTRICTED":"SETUP";}
        private boolean deviceSecure(){KeyguardManager km=(KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);return km!=null&&km.isDeviceSecure();}

        @Override public boolean onTouchEvent(MotionEvent e){
            if(unlocking)return true;
            if(unlocked){if(e.getActionMasked()==MotionEvent.ACTION_DOWN&&!lockscreenMode){unlocked=false;dismissRequested=false;systemDismissed=false;animationFinished=false;invalidate();}return true;}
            float x=e.getX(),y=e.getY();
            if(settingsOpen&&settingsAllowed()){
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
                    if(pickerType!=PICK_NONE){String[] items=pickerItems();if(items!=null){for(int i=0;i<items.length;i++)if(pickerItemRect(i,getWidth(),getHeight(),items.length).contains(x,y)){applyPickerSelection(i);pickerType=PICK_NONE;invalidate();return true;}}if(!pickerRect(getWidth(),getHeight(),items==null?1:items.length).contains(x,y)){pickerType=PICK_NONE;invalidate();}return true;}
                    if(setupOpen){for(int i=0;i<5;i++)if(setupRowRect(i,getWidth(),getHeight()).contains(x,y)){handleSetupRow(i);return true;}return true;}
                    if(!settingsSheetRect(getWidth(),getHeight()).contains(x,y)){settingsOpen=false;invalidate();return true;}
                    for(int i=0;i<6;i++)if(settingsRowRect(i,getWidth(),getHeight()).contains(x,y)){handleSettingRow(i);return true;}
                }
                return true;
            }
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
                if(settingsAllowed()&&gearRect(getWidth()).contains(x,y)){settingsOpen=true;setupOpen=false;pickerType=PICK_NONE;settingsOpenedAt=SystemClock.uptimeMillis();invalidate();return true;}
                downX=x;downY=y;lastTrailX=x;lastTrailY=y;lastTrailAt=SystemClock.uptimeMillis();moved=false;addTouch(x,y,1.18f);if(!style().oil)classicRipples.add(new ClassicRipple(x,y,SystemClock.uptimeMillis()));postInvalidateOnAnimation();return true;
            }
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=x-downX,dy=y-downY;if(dx*dx+dy*dy>swipeThreshold()*swipeThreshold())moved=true;long now=SystemClock.uptimeMillis();float tx=x-lastTrailX,ty=y-lastTrailY;if(now-lastTrailAt>34&&(tx*tx+ty*ty)>dp(9)*dp(9)){addTouch(x,y,.66f);lastTrailX=x;lastTrailY=y;lastTrailAt=now;}return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP){float dx=x-downX,dy=y-downY;boolean swipe=moved||dx*dx+dy*dy>swipeThreshold()*swipeThreshold();if(swipe)finalizeSequence();else current.add(zoneFor(x,y));invalidate();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){moved=false;return true;}return true;
        }

        private void handleSettingRow(int i){
            if(i==0)pickerType=PICK_SURFACE;
            else if(i==1)pickerType=PICK_LANGUAGE;
            else if(i==2)pickerType=PICK_FEEDBACK;
            else if(i==3)pickerType=PICK_DEBUG;
            else if(i==4){setupOpen=true;pickerType=PICK_NONE;}
            else if(i==5){clearCode();settingsOpen=false;}
            invalidate();
        }

        private void handleSetupRow(int i){
            if(i==0){lockscreenMode=!lockscreenMode;prefs.edit().putBoolean(KEY_LOCK_MODE,lockscreenMode).apply();applyLockscreenMode(lockscreenMode);}
            else if(i==1)openSystemSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            else if(i==2)openSystemSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            else if(i==3)openSystemSettings(Settings.ACTION_SECURITY_SETTINGS);
            else if(i==4)setupOpen=false;
            invalidate();
        }

        private void openSystemSettings(String action){
            try{Intent i=new Intent(action);if(Settings.ACTION_MANAGE_OVERLAY_PERMISSION.equals(action)&&android.os.Build.VERSION.SDK_INT<30)i.setData(Uri.parse("package:"+getPackageName()));startActivity(i);}catch(Throwable ignored){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Throwable ignored2){}}
        }

        private String[] pickerItems(){
            if(pickerType==PICK_SURFACE){String[] r=new String[styles.size()];for(int i=0;i<styles.size();i++)r[i]=styles.get(i).name;return r;}
            if(pickerType==PICK_LANGUAGE)return new String[]{"한국어","English"};
            if(pickerType==PICK_FEEDBACK)return language==LANG_KR?new String[]{"진동","시계 틴트"}:new String[]{"Vibration","Clock tint"};
            if(pickerType==PICK_DEBUG)return new String[]{"OFF","ON"};
            return null;
        }

        private int pickerSelection(){if(pickerType==PICK_SURFACE)return selectedStyle;if(pickerType==PICK_LANGUAGE)return language;if(pickerType==PICK_FEEDBACK)return failFeedback;if(pickerType==PICK_DEBUG)return debugMode?1:0;return 0;}

        private void applyPickerSelection(int i){
            if(pickerType==PICK_SURFACE){selectedStyle=clamp(i,0,styles.size()-1);prefs.edit().putInt(KEY_STYLE,selectedStyle).apply();}
            else if(pickerType==PICK_LANGUAGE){language=clamp(i,0,1);prefs.edit().putInt(KEY_LANG,language).apply();}
            else if(pickerType==PICK_FEEDBACK){failFeedback=clamp(i,0,1);prefs.edit().putInt(KEY_FEEDBACK,failFeedback).apply();}
            else if(pickerType==PICK_DEBUG){debugMode=i==1;prefs.edit().putBoolean(KEY_DEBUG,debugMode).apply();}
        }

        private void finalizeSequence(){
            if(current.isEmpty())return;
            if(enrolled.isEmpty()){enrolled.addAll(current);saveCode();current.clear();vibrateSuccess();return;}
            boolean match=enrolled.equals(current);current.clear();if(match)beginUnlock();else triggerFailure();
        }

        private void triggerFailure(){
            long now=SystemClock.uptimeMillis();failShakeStartedAt=now;if(failFeedback==FEEDBACK_HAPTIC)vibrateFailure();else clockRedUntil=now+CLOCK_TINT_MS;postInvalidateOnAnimation();
        }

        private void vibrateFailure(){
            try{VibratorManager vm=(VibratorManager)getSystemService(Context.VIBRATOR_MANAGER_SERVICE);Vibrator v=vm==null?null:vm.getDefaultVibrator();if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createWaveform(new long[]{0,52,48,78},new int[]{0,125,0,185},-1));}catch(Throwable ignored){}
        }

        private void vibrateSuccess(){
            try{VibratorManager vm=(VibratorManager)getSystemService(Context.VIBRATOR_MANAGER_SERVICE);Vibrator v=vm==null?null:vm.getDefaultVibrator();if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createOneShot(28,80));}catch(Throwable ignored){}
        }

        private boolean isFailShaking(long now){return failShakeStartedAt>0&&now-failShakeStartedAt<FAIL_SHAKE_MS;}
        private float failAmount(long now){if(!isFailShaking(now))return 0f;float p=(now-failShakeStartedAt)/(float)FAIL_SHAKE_MS;return (float)(Math.sin(Math.PI*p)*Math.pow(1f-p,.42));}
        private float clockTintAmount(long now){if(now>=clockRedUntil)return 0f;float p=1f-(clockRedUntil-now)/(float)CLOCK_TINT_MS;return (float)Math.sin(Math.PI*clamp01(p))*.72f;}

        private void beginUnlock(){settingsOpen=false;pickerType=PICK_NONE;setupOpen=false;unlocking=true;unlocked=false;dismissRequested=false;systemDismissed=false;animationFinished=false;unlockStartedAt=SystemClock.uptimeMillis();postInvalidateOnAnimation();}

        private float getUnlockProgress(long now){
            if(!unlocking)return unlocked?1f:0f;float p=Math.min(1f,(now-unlockStartedAt)/(float)UNLOCK_MS);
            if(lockscreenMode&&p>=.72f&&!dismissRequested){dismissRequested=true;post(MainActivity.this::requestSystemDismiss);}
            if(p>=1f){unlocking=false;unlocked=true;animationFinished=true;if(lockscreenMode){if(!dismissRequested){dismissRequested=true;post(MainActivity.this::requestSystemDismiss);}else if(systemDismissed)post(MainActivity.this::finishAndRemoveTask);}return 1f;}return p;
        }

        void onSystemDismissed(){systemDismissed=true;if(animationFinished&&lockscreenMode)post(MainActivity.this::finishAndRemoveTask);}
        void onSystemDismissFailed(){systemDismissed=false;dismissRequested=false;unlocking=false;unlocked=false;animationFinished=false;triggerFailure();invalidate();}

        private void addTouch(float x,float y,float strength){touchPoints.add(new TouchPoint(x,y,strength,SystemClock.uptimeMillis()));while(touchPoints.size()>12)touchPoints.remove(0);}
        private void pruneTouches(long now){for(int i=touchPoints.size()-1;i>=0;i--)if(now-touchPoints.get(i).startedAt>TOUCH_FIELD_MS)touchPoints.remove(i);}
        private int zoneFor(float x,float y){int col=clamp((int)(x/Math.max(1f,getWidth())*COLS),0,COLS-1);int row=clamp((int)(y/Math.max(1f,getHeight())*ROWS),0,ROWS-1);return row*COLS+col;}
        private float swipeThreshold(){return Math.max(dp(70),Math.min(getWidth(),getHeight())*.11f);}

        private RectF gearRect(float w){return new RectF(w-dp(66),dp(28),w-dp(18),dp(76));}
        private RectF settingsSheetRect(float w,float h){return new RectF(dp(18),h*.10f,w-dp(18),h*.91f);}
        private RectF settingsRowRect(int i,float w,float h){RectF s=settingsSheetRect(w,h);float top=s.top+dp(68)+i*dp(58);return new RectF(s.left+dp(14),top,s.right-dp(14),top+dp(49));}
        private RectF setupRowRect(int i,float w,float h){RectF s=settingsSheetRect(w,h);float top=s.top+dp(80)+i*dp(59);return new RectF(s.left+dp(14),top,s.right-dp(14),top+dp(50));}
        private RectF pickerRect(float w,float h,int count){float boxW=Math.min(w-dp(68),dp(300)),itemH=dp(49),boxH=dp(24)+count*itemH;return new RectF(w/2-boxW/2,h/2-boxH/2,w/2+boxW/2,h/2+boxH/2);}
        private RectF pickerItemRect(int i,float w,float h,int count){RectF b=pickerRect(w,h,count);float itemH=dp(49);return new RectF(b.left+dp(10),b.top+dp(10)+i*itemH,b.right-dp(10),b.top+dp(10)+(i+1)*itemH-dp(4));}
        private float lerp(float a,float b,float t){return a+(b-a)*t;}
        private float dp(float v){return v*density;}
        private float clamp01(float v){return Math.max(0f,Math.min(1f,v));}
        private int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}

        private final class TouchPoint{final float x,y,strength;final long startedAt;TouchPoint(float x,float y,float strength,long t){this.x=x;this.y=y;this.strength=strength;this.startedAt=t;}}
        private final class ClassicRipple{final float x,y;final long startedAt;ClassicRipple(float x,float y,long t){this.x=x;this.y=y;this.startedAt=t;}}
        private final class StyleProfile{final String name;final int tr,tg,tb,mr,mg,mb,br,bg,bb;final float oilStrength;final boolean oil;StyleProfile(String n,int tr,int tg,int tb,int mr,int mg,int mb,int br,int bg,int bb,float os,boolean oil){name=n;this.tr=tr;this.tg=tg;this.tb=tb;this.mr=mr;this.mg=mg;this.mb=mb;this.br=br;this.bg=bg;this.bb=bb;oilStrength=os;this.oil=oil;}}
    }
}
