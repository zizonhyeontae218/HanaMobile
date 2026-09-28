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
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

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
            @Override public void onDismissSucceeded() { lockView.onSystemDismissed(); }
            @Override public void onDismissCancelled() { lockView.onSystemDismissFailed(); }
            @Override public void onDismissError() { lockView.onSystemDismissFailed(); }
        });
    }

    private final class RippleLockView extends View {
        private static final int COLS = 2;
        private static final int ROWS = 5;
        private static final long CLASSIC_RIPPLE_MS = 1100L;
        private static final long TOUCH_FIELD_MS = 1650L;
        private static final long FAIL_SHAKE_MS = 780L;
        private static final long UNLOCK_MS = 2100L;

        private static final String PREFS = "ripple_code_v4";
        private static final String KEY_CODE = "code";
        private static final String KEY_STYLE = "style";
        private static final String KEY_LOCK_MODE = "lock_mode";
        private static final String KEY_LANG = "lang";
        private static final String KEY_CLOCK = "clock";
        private static final String KEY_FEEDBACK = "feedback";
        private static final String KEY_DEBUG = "debug";

        private static final int LANG_KR = 0;
        private static final int LANG_EN = 1;
        private static final int CLOCK_LIQUID = 0;
        private static final int CLOCK_ANALOG = 1;
        private static final int CLOCK_FRUTIGER = 2;
        private static final int FEEDBACK_HAPTIC = 0;
        private static final int FEEDBACK_VISUAL = 1;

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
        private boolean moved, settingsOpen, lockscreenMode, debugMode;
        private int selectedStyle, language, clockStyle, failFeedback;
        private boolean unlocking, unlocked, dismissRequested, systemDismissed, animationFinished;
        private long unlockStartedAt;
        private long failShakeStartedAt = -1L;
        private long clockRedUntil = 0L;

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
                "float2 warpTouch(float2 uv,float4 t,float aspect){float life=clamp(1.0-t.w,0.0,1.0);float2 d=uv-t.xy;float2 md=metricDelta(d,aspect);float r=max(length(md),0.0015);float fall=exp(-r*r*34.0);float wave=sin(r*58.0-t.w*17.0);float2 dir=float2(md.x/max(aspect,0.001),md.y)/r;float2 tan=float2(-dir.y,dir.x);return (dir*(0.046+0.031*wave)+tan*0.015*sin(t.w*5.2+r*21.0))*t.z*fall*pow(life,1.35);}\n" +
                "float rippleGlow(float2 uv,float4 t,float aspect){float life=clamp(1.0-t.w,0.0,1.0);float2 d=metricDelta(uv-t.xy,aspect);float r=length(d);float target=0.014+t.w*0.095;float ring=exp(-pow((r-target)*50.0,2.0));return ring*t.z*life;}\n" +
                "float3 palette(float x){float3 bronze=float3(0.80,0.51,0.30),purple=float3(0.50,0.30,0.64),blue=float3(0.23,0.52,0.70),teal=float3(0.23,0.69,0.60),mag=float3(0.70,0.34,0.58),gold=float3(0.80,0.66,0.34);x=fract(x);if(x<0.17)return mix(bronze,purple,x/0.17);if(x<0.34)return mix(purple,blue,(x-0.17)/0.17);if(x<0.52)return mix(blue,teal,(x-0.34)/0.18);if(x<0.70)return mix(teal,mag,(x-0.52)/0.18);if(x<0.86)return mix(mag,gold,(x-0.70)/0.16);return mix(gold,bronze,(x-0.86)/0.14);}\n" +
                "half4 main(float2 frag){\n" +
                "float2 uv=frag/uResolution;float aspect=uResolution.x/uResolution.y;float2 p=float2(uv.x*aspect,uv.y);float t=uTime;\n" +
                "p.y+=uInertia*(0.060+0.024*noise(p*2.1+float2(2.7,t*0.08)));\n" +
                "float2 failJ=float2(noise(p*3.1+float2(t*7.0,1.2)),noise(p*2.7+float2(4.4,-t*6.3)))-0.5;p+=failJ*(0.095*uFail);\n" +
                "p+=warpTouch(uv,uTouch0,aspect)+warpTouch(uv,uTouch1,aspect)+warpTouch(uv,uTouch2,aspect)+warpTouch(uv,uTouch3,aspect);\n" +
                "float2 flow=float2(0.020*t,-0.012*t);float large=fbm(p*1.52+flow);float2 curl=float2(noise(p*2.5+float2(0.0,t*0.020)),noise(p*2.7+float2(6.0,-t*0.017)))-0.5;\n" +
                "float thick=fbm((p+curl*0.18)*2.75-flow*0.65)+0.42*fbm((p-curl*0.12)*5.4+flow*0.37);thick+=sin(t*0.31+large*6.283)*0.028+uFail*0.08*noise(p*8.0+t*2.0);\n" +
                "float film=smoothstep(0.27,0.62,large+0.20*noise(p*3.6-flow));float band=fract(thick*1.95+large*0.26);float3 oil=palette(band);\n" +
                "float y=clamp(uv.y,0.0,1.0);float3 base=mix(uTop,uMid,smoothstep(0.0,0.58,y));base=mix(base,uBottom,smoothstep(0.50,1.0,y));\n" +
                "float water=0.5+0.5*sin(uv.y*9.0+fbm(p*1.9)*2.4+t*0.42);base*=0.944+0.056*water;\n" +
                "float spec=pow(clamp(1.0-abs(noise(p*2.4+flow*0.2)-0.52)*3.1,0.0,1.0),2.3);float3 silver=float3(0.80,0.91,0.90);\n" +
                "float strength=uOilStrength*film*(0.50+0.70*spec);strength*=mix(1.0,0.88,uClearStyle);float3 col=mix(base,oil,clamp(strength,0.0,0.78));col=mix(col,silver,spec*film*0.12);\n" +
                "float rg=rippleGlow(uv,uTouch0,aspect)+rippleGlow(uv,uTouch1,aspect)+rippleGlow(uv,uTouch2,aspect)+rippleGlow(uv,uTouch3,aspect);col=mix(col,float3(0.78,0.94,0.93),clamp(rg*0.075,0.0,0.085));\n" +
                "return half4(half3(clamp(col,0.0,1.0)),1.0);}";

        RippleLockView() {
            super(MainActivity.this);
            density = getResources().getDisplayMetrics().density;
            prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            initStyles();
            selectedStyle = clamp(prefs.getInt(KEY_STYLE, 1),0,styles.size()-1);
            lockscreenMode = prefs.getBoolean(KEY_LOCK_MODE, false);
            language = clamp(prefs.getInt(KEY_LANG, LANG_KR),LANG_KR,LANG_EN);
            clockStyle = clamp(prefs.getInt(KEY_CLOCK, CLOCK_LIQUID),CLOCK_LIQUID,CLOCK_FRUTIGER);
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
            styles.add(new StyleProfile("Oil Sheen",31,58,68,60,96,104,87,118,127,0.47f,true));
            styles.add(new StyleProfile("Oil Clear",68,104,114,101,137,143,136,160,162,0.38f,true));
            styles.add(new StyleProfile("Oil Night",14,27,43,24,49,66,39,69,82,0.43f,true));
            styles.add(new StyleProfile("Getura Oil",73,104,113,98,127,124,120,117,142,0.44f,true));
        }

        private void loadCode() {
            enrolled.clear();
            String saved=prefs.getString(KEY_CODE,"");
            if(saved==null||saved.isEmpty())return;
            for(String part:saved.split(","))try{enrolled.add(Integer.parseInt(part));}catch(NumberFormatException ignored){}
        }

        private void saveCode() {
            StringBuilder sb=new StringBuilder();
            for(int i=0;i<enrolled.size();i++){if(i>0)sb.append(',');sb.append(enrolled.get(i));}
            prefs.edit().putString(KEY_CODE,sb.toString()).apply();
        }

        private void clearCode(){enrolled.clear();current.clear();prefs.edit().remove(KEY_CODE).apply();}
        private void loadWallpaper(){try{wallpaperDrawable=WallpaperManager.getInstance(getContext()).getDrawable();}catch(Throwable ignored){wallpaperDrawable=null;}}
        private StyleProfile style(){return styles.get(selectedStyle);}

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
            float t=now/1000f;paint.setShader(new RadialGradient(w*(.70f+.03f*(float)Math.sin(t*.3f)),h*.20f,Math.max(w,h)*.36f,new int[]{Color.argb(24,255,255,255),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));canvas.drawCircle(w*.7f,h*.2f,Math.max(w,h)*.36f,paint);paint.setShader(null);
        }

        private void drawLockCard(Canvas canvas,int screenW,int screenH,long now,float p){
            float motion=(float)Math.pow(clamp01(p),2.20);
            float top=screenH*1.075f*motion;
            float inertia=(float)Math.pow(clamp01(p),1.55);
            float radius=dp(24f)*(float)Math.sin(Math.PI*clamp01(p));
            RectF card=new RectF(-dp(8),top,screenW+dp(8),top+screenH+dp(16));
            shadow.setColor(Color.TRANSPARENT);shadow.setShadowLayer(dp(24)*Math.max(.12f,1f-motion),0,-dp(2),Color.argb((int)(92*(1f-motion)),0,0,0));canvas.drawRoundRect(card,radius,radius,shadow);shadow.clearShadowLayer();
            canvas.save();canvas.translate(0,top);drawSurfaceVisual(canvas,screenW,screenH,now,inertia);drawMinimalUI(canvas,screenW,screenH,now);if(settingsOpen&&!unlocking)drawSettings(canvas,screenW,screenH);canvas.restore();
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
            for(int i=classicRipples.size()-1;i>=0;i--){ClassicRipple r=classicRipples.get(i);float q=(now-r.startedAt)/(float)CLASSIC_RIPPLE_MS;if(q>=1f){classicRipples.remove(i);continue;}float rr=dp(10)+Math.min(w,h)*.23f*(float)Math.pow(q,.64);int a=(int)(118*(1-q)*(1-q));for(int k=0;k<3;k++){float rk=rr-dp(k*18);if(rk<=0)continue;line.setColor(Color.argb(Math.max(0,a-k*28),220,249,255));line.setStrokeWidth(dp(k==0?1.7f:1.0f));canvas.drawCircle(r.x,r.y,rk,line);}}
            line.setStyle(Paint.Style.FILL);
        }

        private void drawMinimalUI(Canvas canvas,float w,float h,long now){
            drawClock(canvas,w,h,now);
            drawGear(canvas,gearRect(w));
            if(enrolled.isEmpty())drawEnrollmentHint(canvas,w,h);
            if(debugMode)drawDebug(canvas,w,h);
        }

        private void drawClock(Canvas canvas,float w,float h,long now){
            Calendar c=Calendar.getInstance();int hour=c.get(Calendar.HOUR_OF_DAY),minute=c.get(Calendar.MINUTE),second=c.get(Calendar.SECOND);float red=clockTintAmount(now);int baseR=(int)(245+8*red),baseG=(int)(248-74*red),baseB=(int)(252-68*red);int clockColor=Color.rgb(clamp(baseR,0,255),clamp(baseG,0,255),clamp(baseB,0,255));
            if(clockStyle==CLOCK_ANALOG){drawAnalogClock(canvas,w,h,hour,minute,second,clockColor);return;}
            String time=String.format(Locale.US,"%02d:%02d",hour,minute);
            if(clockStyle==CLOCK_FRUTIGER){drawFrutigerClock(canvas,w,h,time,clockColor);return;}
            float cx=w/2f,top=dp(78),bw=Math.min(w-dp(74),dp(300)),bh=dp(116);RectF glass=new RectF(cx-bw/2,top,cx+bw/2,top+bh);ui.setColor(Color.argb(34,255,255,255));ui.setShadowLayer(dp(20),0,dp(8),Color.argb(52,0,0,0));canvas.drawRoundRect(glass,dp(34),dp(34),ui);ui.clearShadowLayer();line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(.9f));line.setColor(Color.argb(72,255,255,255));canvas.drawRoundRect(glass,dp(34),dp(34),line);line.setStyle(Paint.Style.FILL);paint.setShader(new LinearGradient(0,glass.top,0,glass.bottom,new int[]{Color.argb(48,255,255,255),Color.argb(5,255,255,255)},null,Shader.TileMode.CLAMP));canvas.drawRoundRect(new RectF(glass.left+dp(2),glass.top+dp(2),glass.right-dp(2),glass.centerY()),dp(32),dp(32),paint);paint.setShader(null);text.setTextAlign(Paint.Align.CENTER);text.setTypeface(android.graphics.Typeface.create("sans-serif-light",0));text.setTextSize(dp(54));text.setColor(clockColor);canvas.drawText(time,cx,top+dp(75),text);drawDate(canvas,cx,top+dp(99),clockColor);
        }

        private void drawAnalogClock(Canvas canvas,float w,float h,int hour,int minute,int second,int clockColor){
            float cx=w/2f,cy=dp(145),r=dp(72);ui.setColor(Color.argb(30,255,255,255));ui.setShadowLayer(dp(18),0,dp(7),Color.argb(48,0,0,0));canvas.drawCircle(cx,cy,r,ui);ui.clearShadowLayer();line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(1));line.setColor(Color.argb(76,255,255,255));canvas.drawCircle(cx,cy,r,line);for(int i=0;i<60;i++){double a=Math.PI*2*i/60-Math.PI/2;float r1=r-dp(i%5==0?11:6),r2=r-dp(3);line.setStrokeWidth(dp(i%5==0?1.7f:.7f));line.setColor(i%5==0?clockColor:Color.argb(100,255,255,255));canvas.drawLine(cx+(float)Math.cos(a)*r1,cy+(float)Math.sin(a)*r1,cx+(float)Math.cos(a)*r2,cy+(float)Math.sin(a)*r2,line);}float ha=(hour%12+minute/60f)*30f,ma=(minute+second/60f)*6f,sa=second*6f;drawHand(canvas,cx,cy,r*.48f,ha,dp(3.4f),clockColor);drawHand(canvas,cx,cy,r*.70f,ma,dp(2.4f),clockColor);drawHand(canvas,cx,cy,r*.73f,sa,dp(1.1f),Color.argb(185,190,220,230));line.setStyle(Paint.Style.FILL);line.setColor(clockColor);canvas.drawCircle(cx,cy,dp(3.5f),line);drawDate(canvas,cx,cy+r+dp(27),clockColor);
        }

        private void drawHand(Canvas canvas,float cx,float cy,float len,float degrees,float width,int color){double a=Math.toRadians(degrees-90);line.setStyle(Paint.Style.STROKE);line.setStrokeCap(Paint.Cap.ROUND);line.setStrokeWidth(width);line.setColor(color);canvas.drawLine(cx,cy,cx+(float)Math.cos(a)*len,cy+(float)Math.sin(a)*len,line);line.setStrokeCap(Paint.Cap.BUTT);line.setStyle(Paint.Style.FILL);}

        private void drawFrutigerClock(Canvas canvas,float w,float h,String time,int clockColor){
            float cx=w/2f,top=dp(82),bw=Math.min(w-dp(66),dp(310)),bh=dp(112);RectF bubble=new RectF(cx-bw/2,top,cx+bw/2,top+bh);paint.setShader(new LinearGradient(0,top,0,top+bh,new int[]{Color.argb(120,105,205,245),Color.argb(104,62,172,218),Color.argb(110,80,194,151)},new float[]{0f,.58f,1f},Shader.TileMode.CLAMP));ui.setShadowLayer(dp(18),0,dp(7),Color.argb(55,0,50,90));canvas.drawRoundRect(bubble,dp(36),dp(36),paint);ui.clearShadowLayer();paint.setShader(new RadialGradient(bubble.left+bw*.28f,top+bh*.14f,bw*.36f,new int[]{Color.argb(118,255,255,255),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));canvas.drawOval(new RectF(bubble.left+dp(8),top+dp(5),bubble.right-dp(8),top+bh*.58f),paint);paint.setShader(null);line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(1.2f));line.setColor(Color.argb(120,230,255,255));canvas.drawRoundRect(bubble,dp(36),dp(36),line);line.setStyle(Paint.Style.FILL);text.setTextAlign(Paint.Align.CENTER);text.setTypeface(android.graphics.Typeface.create("sans-serif-light",0));text.setTextSize(dp(53));text.setColor(clockColor);canvas.drawText(time,cx,top+dp(72),text);drawDate(canvas,cx,top+dp(96),clockColor);
        }

        private void drawDate(Canvas canvas,float cx,float baseline,int color){
            Calendar c=Calendar.getInstance();String[] kr={"일","월","화","수","목","금","토"};String[] en={"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};String date;if(language==LANG_KR)date=String.format(Locale.KOREA,"%d월 %d일 %s요일",c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH),kr[c.get(Calendar.DAY_OF_WEEK)-1]);else date=String.format(Locale.US,"%s, %s %d",en[c.get(Calendar.DAY_OF_WEEK)-1],new java.text.DateFormatSymbols(Locale.US).getShortMonths()[c.get(Calendar.MONTH)],c.get(Calendar.DAY_OF_MONTH));text.setTypeface(android.graphics.Typeface.create("sans",0));text.setTextSize(dp(12));text.setColor(Color.argb(176,Color.red(color),Color.green(color),Color.blue(color)));canvas.drawText(date,cx,baseline,text);
        }

        private void drawGear(Canvas canvas,RectF r){float cx=r.centerX(),cy=r.centerY(),rad=dp(9);line.setStyle(Paint.Style.STROKE);line.setStrokeCap(Paint.Cap.ROUND);line.setColor(Color.argb(205,245,250,252));line.setStrokeWidth(dp(2));canvas.drawCircle(cx,cy,rad,line);canvas.drawCircle(cx,cy,dp(3),line);for(int i=0;i<8;i++){double a=Math.PI*2*i/8;canvas.drawLine(cx+(float)Math.cos(a)*dp(11),cy+(float)Math.sin(a)*dp(11),cx+(float)Math.cos(a)*dp(15),cy+(float)Math.sin(a)*dp(15),line);}line.setStrokeCap(Paint.Cap.BUTT);line.setStyle(Paint.Style.FILL);}

        private void drawEnrollmentHint(Canvas canvas,float w,float h){text.setTextAlign(Paint.Align.CENTER);text.setTypeface(android.graphics.Typeface.create("sans",0));text.setColor(Color.argb(205,242,248,251));text.setTextSize(dp(16));canvas.drawText(language==LANG_KR?"Ripple Code 설정":"Set Ripple Code",w/2,h*.48f,text);text.setTextSize(dp(12.5f));text.setColor(Color.argb(150,232,242,248));canvas.drawText(language==LANG_KR?"원하는 위치를 탭한 뒤 길게 스와이프":"Tap any positions, then finish with a long swipe",w/2,h*.48f+dp(26),text);}

        private void drawDebug(Canvas canvas,float w,float h){line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(.8f));line.setColor(Color.argb(72,255,255,255));for(int i=1;i<COLS;i++)canvas.drawLine(w*i/COLS,0,w*i/COLS,h,line);for(int i=1;i<ROWS;i++)canvas.drawLine(0,h*i/ROWS,w,h*i/ROWS,line);line.setStyle(Paint.Style.FILL);text.setTextAlign(Paint.Align.LEFT);text.setTypeface(android.graphics.Typeface.MONOSPACE);text.setTextSize(dp(10));text.setColor(Color.argb(205,235,255,240));canvas.drawText("DEBUG · "+style().name+" · input="+current.size()+" · lock="+lockscreenMode,dp(12),h-dp(16),text);}

        private void drawUnlockedDebug(Canvas canvas,float w,float h){text.setTextAlign(Paint.Align.CENTER);text.setTypeface(android.graphics.Typeface.MONOSPACE);text.setTextSize(dp(11));text.setColor(Color.argb(180,255,255,255));canvas.drawText("UNLOCKED · tap to relock demo",w/2,h-dp(28),text);}

        private void drawSettings(Canvas canvas,float w,float h){
            RectF sheet=settingsSheetRect(w,h);ui.setColor(Color.argb(118,2,8,14));canvas.drawRect(0,0,w,h,ui);ui.setColor(Color.argb(238,20,28,40));canvas.drawRoundRect(sheet,dp(30),dp(30),ui);line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(1));line.setColor(Color.argb(58,255,255,255));canvas.drawRoundRect(sheet,dp(30),dp(30),line);line.setStyle(Paint.Style.FILL);
            text.setTextAlign(Paint.Align.LEFT);text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);text.setTextSize(dp(21));text.setColor(Color.WHITE);canvas.drawText(language==LANG_KR?"설정":"Settings",sheet.left+dp(22),sheet.top+dp(38),text);text.setTypeface(android.graphics.Typeface.DEFAULT);
            String[] labels=language==LANG_KR?new String[]{"표면","시계","언어","실패 피드백","잠금화면","디버그 모드","Ripple Code 초기화"}:new String[]{"Surface","Clock","Language","Failure feedback","Lockscreen","Debug mode","Reset Ripple Code"};
            String[] values=new String[]{style().name,clockName(),language==LANG_KR?"한국어":"English",feedbackName(),lockscreenMode?"ON":"OFF",debugMode?"ON":"OFF",""};
            for(int i=0;i<labels.length;i++){RectF row=settingsRowRect(i,w,h);ui.setColor(i==6?Color.argb(28,255,120,120):Color.argb(25,255,255,255));canvas.drawRoundRect(row,dp(18),dp(18),ui);text.setTextAlign(Paint.Align.LEFT);text.setTextSize(dp(14));text.setColor(Color.argb(235,246,250,252));canvas.drawText(labels[i],row.left+dp(16),row.centerY()+dp(5),text);if(i<6){text.setTextAlign(Paint.Align.RIGHT);text.setTextSize(dp(12));text.setColor(Color.argb(175,220,235,244));canvas.drawText(values[i]+"  ›",row.right-dp(15),row.centerY()+dp(4),text);}}
            text.setTextAlign(Paint.Align.LEFT);text.setTextSize(dp(10));text.setColor(Color.argb(118,214,227,237));canvas.drawText(language==LANG_KR?"보안 잠금 해제는 Android Keyguard가 최종 처리":"Android Keyguard still handles the final secure unlock",sheet.left+dp(22),sheet.bottom-dp(18),text);
        }

        private String clockName(){if(clockStyle==CLOCK_ANALOG)return language==LANG_KR?"아날로그":"Analog";if(clockStyle==CLOCK_FRUTIGER)return "Frutiger Aero";return "Liquid Glass";}
        private String feedbackName(){return failFeedback==FEEDBACK_HAPTIC?(language==LANG_KR?"진동":"Vibration"):(language==LANG_KR?"시계 틴트":"Clock tint");}

        @Override public boolean onTouchEvent(MotionEvent e){
            if(unlocking)return true;
            if(unlocked){if(e.getActionMasked()==MotionEvent.ACTION_DOWN&&!lockscreenMode){unlocked=false;dismissRequested=false;systemDismissed=false;animationFinished=false;invalidate();}return true;}
            float x=e.getX(),y=e.getY();
            if(settingsOpen){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(!settingsSheetRect(getWidth(),getHeight()).contains(x,y)){settingsOpen=false;invalidate();return true;}for(int i=0;i<7;i++){if(settingsRowRect(i,getWidth(),getHeight()).contains(x,y)){handleSettingRow(i);return true;}}}return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(gearRect(getWidth()).contains(x,y)){settingsOpen=true;performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);invalidate();return true;}downX=x;downY=y;lastTrailX=x;lastTrailY=y;lastTrailAt=SystemClock.uptimeMillis();moved=false;addTouch(x,y,1.15f);if(!style().oil)classicRipples.add(new ClassicRipple(x,y,SystemClock.uptimeMillis()));performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);postInvalidateOnAnimation();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=x-downX,dy=y-downY;if(dx*dx+dy*dy>swipeThreshold()*swipeThreshold())moved=true;long now=SystemClock.uptimeMillis();float tx=x-lastTrailX,ty=y-lastTrailY;if(now-lastTrailAt>34&&(tx*tx+ty*ty)>dp(9)*dp(9)){addTouch(x,y,.64f);lastTrailX=x;lastTrailY=y;lastTrailAt=now;}return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP){float dx=x-downX,dy=y-downY;boolean swipe=moved||dx*dx+dy*dy>swipeThreshold()*swipeThreshold();if(swipe)finalizeSequence();else{current.add(zoneFor(x,y));performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);}invalidate();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){moved=false;return true;}return true;
        }

        private void handleSettingRow(int i){
            if(i==0){selectedStyle=(selectedStyle+1)%styles.size();prefs.edit().putInt(KEY_STYLE,selectedStyle).apply();}
            else if(i==1){clockStyle=(clockStyle+1)%3;prefs.edit().putInt(KEY_CLOCK,clockStyle).apply();}
            else if(i==2){language=language==LANG_KR?LANG_EN:LANG_KR;prefs.edit().putInt(KEY_LANG,language).apply();}
            else if(i==3){failFeedback=failFeedback==FEEDBACK_HAPTIC?FEEDBACK_VISUAL:FEEDBACK_HAPTIC;prefs.edit().putInt(KEY_FEEDBACK,failFeedback).apply();}
            else if(i==4){lockscreenMode=!lockscreenMode;prefs.edit().putBoolean(KEY_LOCK_MODE,lockscreenMode).apply();applyLockscreenMode(lockscreenMode);}
            else if(i==5){debugMode=!debugMode;prefs.edit().putBoolean(KEY_DEBUG,debugMode).apply();}
            else if(i==6){clearCode();settingsOpen=false;}
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);invalidate();
        }

        private void finalizeSequence(){
            if(current.isEmpty())return;
            if(enrolled.isEmpty()){enrolled.addAll(current);saveCode();current.clear();performHapticFeedback(HapticFeedbackConstants.CONFIRM);return;}
            boolean match=enrolled.equals(current);current.clear();
            if(match){beginUnlock();}
            else triggerFailure();
        }

        private void triggerFailure(){failShakeStartedAt=SystemClock.uptimeMillis();if(failFeedback==FEEDBACK_HAPTIC)performHapticFeedback(HapticFeedbackConstants.REJECT);else clockRedUntil=SystemClock.uptimeMillis()+680L;postInvalidateOnAnimation();}
        private boolean isFailShaking(long now){return failShakeStartedAt>0&&now-failShakeStartedAt<FAIL_SHAKE_MS;}
        private float failAmount(long now){if(!isFailShaking(now))return 0f;float p=(now-failShakeStartedAt)/(float)FAIL_SHAKE_MS;return (float)(Math.sin(Math.PI*p)*Math.pow(1f-p,.45));}
        private float clockTintAmount(long now){if(now>=clockRedUntil)return 0f;float p=1f-(clockRedUntil-now)/680f;return (float)Math.sin(Math.PI*clamp01(p))*.52f;}

        private void beginUnlock(){settingsOpen=false;unlocking=true;unlocked=false;dismissRequested=false;systemDismissed=false;animationFinished=false;unlockStartedAt=SystemClock.uptimeMillis();postInvalidateOnAnimation();}

        private float getUnlockProgress(long now){
            if(!unlocking)return unlocked?1f:0f;
            float p=Math.min(1f,(now-unlockStartedAt)/(float)UNLOCK_MS);
            if(lockscreenMode&&p>=0.60f&&!dismissRequested){dismissRequested=true;post(MainActivity.this::requestSystemDismiss);}
            if(p>=1f){unlocking=false;unlocked=true;animationFinished=true;if(lockscreenMode){if(!dismissRequested){dismissRequested=true;post(MainActivity.this::requestSystemDismiss);}else if(systemDismissed)post(MainActivity.this::finishAndRemoveTask);}return 1f;}
            return p;
        }

        void onSystemDismissed(){systemDismissed=true;if(animationFinished&&lockscreenMode)post(MainActivity.this::finishAndRemoveTask);}
        void onSystemDismissFailed(){systemDismissed=false;dismissRequested=false;unlocking=false;unlocked=false;animationFinished=false;triggerFailure();invalidate();}

        private void addTouch(float x,float y,float strength){touchPoints.add(new TouchPoint(x,y,strength,SystemClock.uptimeMillis()));while(touchPoints.size()>12)touchPoints.remove(0);}
        private void pruneTouches(long now){for(int i=touchPoints.size()-1;i>=0;i--)if(now-touchPoints.get(i).startedAt>TOUCH_FIELD_MS)touchPoints.remove(i);}
        private int zoneFor(float x,float y){int col=clamp((int)(x/Math.max(1f,getWidth())*COLS),0,COLS-1);int row=clamp((int)(y/Math.max(1f,getHeight())*ROWS),0,ROWS-1);return row*COLS+col;}
        private float swipeThreshold(){return Math.max(dp(70),Math.min(getWidth(),getHeight())*.11f);}

        private RectF gearRect(float w){return new RectF(w-dp(64),dp(24),w-dp(18),dp(70));}
        private RectF settingsSheetRect(float w,float h){return new RectF(dp(16),h*.12f,w-dp(16),h*.90f);}
        private RectF settingsRowRect(int i,float w,float h){RectF s=settingsSheetRect(w,h);float top=s.top+dp(62)+i*dp(58);return new RectF(s.left+dp(14),top,s.right-dp(14),top+dp(49));}
        private float dp(float v){return v*density;}
        private float clamp01(float v){return Math.max(0f,Math.min(1f,v));}
        private int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}

        private final class TouchPoint{final float x,y,strength;final long startedAt;TouchPoint(float x,float y,float strength,long t){this.x=x;this.y=y;this.strength=strength;this.startedAt=t;}}
        private final class ClassicRipple{final float x,y;final long startedAt;ClassicRipple(float x,float y,long t){this.x=x;this.y=y;this.startedAt=t;}}
        private final class StyleProfile{final String name;final int tr,tg,tb,mr,mg,mb,br,bg,bb;final float oilStrength;final boolean oil;StyleProfile(String n,int tr,int tg,int tb,int mr,int mg,int mb,int br,int bg,int bb,float os,boolean oil){name=n;this.tr=tr;this.tg=tg;this.tb=tb;this.mr=mr;this.mg=mg;this.mb=mb;this.br=br;this.bg=bg;this.bb=bb;oilStrength=os;this.oil=oil;}}
    }
}
