from pathlib import Path

p = Path('app/src/main/java/com/openai/rippledemo/MainActivity.java')
s = p.read_text(encoding='utf-8')

def rep(a, b, name):
    global s
    if a not in s:
        raise SystemExit('missing ' + name)
    s = s.replace(a, b, 1)

def replace_between(start_marker, end_marker, replacement, name):
    global s
    i = s.find(start_marker)
    j = s.find(end_marker, i)
    if i < 0 or j < 0:
        raise SystemExit('missing ' + name)
    s = s[:i] + replacement + s[j:]

# Activity launch mode: distinguish HOME fallback from the new post-unlock gate.
rep('    private boolean launchedAsHome;\n',
    '    private boolean launchedAsHome;\n    private boolean launchedAsGate;\n',
    'gate activity field')

rep('        launchedAsHome = getIntent()!=null && getIntent().hasCategory(Intent.CATEGORY_HOME);\n',
    '        launchedAsHome = getIntent()!=null && getIntent().hasCategory(Intent.CATEGORY_HOME);\n        launchedAsGate = getIntent()!=null && getIntent().getBooleanExtra("post_unlock_gate", false);\n',
    'gate onCreate')

rep('        launchedAsHome=intent!=null&&intent.hasCategory(Intent.CATEGORY_HOME);\n        if(lockView!=null&&launchedAsHome){if(homeSessionUnlocked)lockView.post(lockView::launchConfiguredHomeAndFinish);else lockView.forceHomeGate();}\n',
    '        launchedAsHome=intent!=null&&intent.hasCategory(Intent.CATEGORY_HOME);\n        launchedAsGate=intent!=null&&intent.getBooleanExtra("post_unlock_gate",false);\n        if(lockView!=null&&launchedAsHome){if(homeSessionUnlocked)lockView.post(lockView::launchConfiguredHomeAndFinish);else lockView.forceHomeGate();}\n        if(lockView!=null&&launchedAsGate)lockView.forceHomeGate();\n',
    'gate onNewIntent')

rep('        private static final String KEY_HOME_TARGET = "home_target";\n',
    '        private static final String KEY_HOME_TARGET = "home_target";\n        private static final String KEY_POST_GATE = "post_unlock_gate";\n',
    'post gate pref')

rep('        private boolean homeLaunchRequested=false;\n',
    '        private boolean homeLaunchRequested=false;\n        private boolean gateFinishRequested=false;\n        private boolean postGateEnabled=false;\n',
    'gate view fields')

rep('            homeTarget=prefs.getString(KEY_HOME_TARGET,""); if(homeTarget==null)homeTarget=""; ensureHomeTarget();\n',
    '            homeTarget=prefs.getString(KEY_HOME_TARGET,""); if(homeTarget==null)homeTarget=""; ensureHomeTarget();\n            postGateEnabled=prefs.getBoolean(KEY_POST_GATE,false);\n',
    'load post gate')

rep('        private boolean settingsAllowed() { return !launchedAsHome && !launchedOverKeyguard && !keyguardShowing(); }\n',
    '        private boolean settingsAllowed() { return !launchedAsGate && !launchedAsHome && !launchedOverKeyguard && !keyguardShowing(); }\n',
    'gate settings lockout')

rep('        private void beginUnlock(){settingsOpen=false;pickerType=PICK_NONE;setupOpen=false;orbState=ORB_ATTACHED_IDLE;orbPull=0;homeLaunchRequested=false;unlocking=true;unlocked=false;dismissRequested=false;systemDismissed=false;animationFinished=false;unlockStartedAt=SystemClock.uptimeMillis();postInvalidateOnAnimation();}\n',
    '        private void beginUnlock(){settingsOpen=false;pickerType=PICK_NONE;setupOpen=false;orbState=ORB_ATTACHED_IDLE;orbPull=0;homeLaunchRequested=false;gateFinishRequested=false;unlocking=true;unlocked=false;dismissRequested=false;systemDismissed=false;animationFinished=false;unlockStartedAt=SystemClock.uptimeMillis();postInvalidateOnAnimation();}\n',
    'gate begin unlock')

rep('            if(launchedAsHome&&p>=.90f&&!homeLaunchRequested){homeLaunchRequested=true;post(this::launchConfiguredHomeAndFinish);}\n',
    '            if(launchedAsGate&&p>=.985f&&!gateFinishRequested){gateFinishRequested=true;post(this::finishGateAndReveal);}\n            if(launchedAsHome&&p>=.90f&&!homeLaunchRequested){homeLaunchRequested=true;post(this::launchConfiguredHomeAndFinish);}\n',
    'gate finish timing')

rep('        private void forceHomeGate(){settingsOpen=false;pickerType=PICK_NONE;setupOpen=false;unlocked=false;unlocking=false;homeLaunchRequested=false;current.clear();invalidate();}\n',
    '        private void finishGateAndReveal(){if(!launchedAsGate)return;MainActivity.this.finish();MainActivity.this.overridePendingTransition(0,0);}\n        private void forceHomeGate(){settingsOpen=false;pickerType=PICK_NONE;setupOpen=false;unlocked=false;unlocking=false;homeLaunchRequested=false;gateFinishRequested=false;current.clear();invalidate();}\n',
    'gate finish helper')

# Setup page: post-unlock gate first, HOME bridge becomes fallback.
setup_method = r'''        private void drawSetupAnimated(Canvas canvas,RectF sheet,float w,float h,int a,float sp){if(sp<=0f)return;int sa=(int)(a*sp);canvas.save();canvas.translate(dp(28)*(1f-sp),0);text.setTextAlign(Paint.Align.LEFT);text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);text.setTextSize(dp(18));text.setColor(Color.argb(sa,Color.red(stInk),Color.green(stInk),Color.blue(stInk)));canvas.drawText(language==LANG_KR?"잠금화면 연결":"Lockscreen integration",sheet.left+dp(22),sheet.top+dp(38),text);text.setTypeface(android.graphics.Typeface.DEFAULT);text.setTextSize(dp(10.2f));text.setColor(Color.argb((int)(190*sp),Color.red(stMuted),Color.green(stMuted),Color.blue(stMuted)));canvas.drawText(language==LANG_KR?"지문/PIN 뒤에 Ripple Code를 한 번 더 표시합니다":"Shows Ripple Code after Android fingerprint/PIN unlock",sheet.left+dp(22),sheet.top+dp(57),text);String[] labels=language==LANG_KR?new String[]{"해제 후 Ripple Gate","접근성 서비스","다른 앱 위에 표시","HOME 브리지 (보조)","뒤에 열 홈 앱","배터리 최적화","시스템 화면 잠금","뒤로"}:new String[]{"Post-unlock Ripple Gate","Accessibility service","Display over apps","HOME bridge (fallback)","Launcher behind","Battery optimization","System screen lock","Back"};String[] values=new String[]{postGateEnabled?(postGateReady()?"READY":"SETUP"):"OFF",accessibilityGateServiceEnabled()?"ON":"SETUP",Settings.canDrawOverlays(MainActivity.this)?"ALLOWED":"SETUP",homeRoleHeld()?"ACTIVE":"OPTIONAL",homeTargetLabel(),batteryStatus(),deviceSecure()?"SECURE":"NONE",""};for(int i=0;i<labels.length;i++){RectF r=setupRowRect(i,w,h);ui.setColor(Color.argb((int)(16*sp),255,255,255));canvas.drawRoundRect(r,dp(14),dp(14),ui);text.setTextAlign(Paint.Align.LEFT);text.setTextSize(dp(12.4f));text.setColor(Color.argb(sa,Color.red(stInk),Color.green(stInk),Color.blue(stInk)));canvas.drawText(labels[i],r.left+dp(14),r.centerY()+dp(4.5f),text);if(i<7){text.setTextAlign(Paint.Align.RIGHT);text.setTextSize(dp(10.0f));text.setColor(Color.argb((int)(195*sp),Color.red(stMuted),Color.green(stMuted),Color.blue(stMuted)));canvas.drawText(values[i]+"  ›",r.right-dp(13),r.centerY()+dp(3.5f),text);}}canvas.restore();}
'''
replace_between('        private void drawSetupAnimated', '        private void drawPicker', setup_method, 'setup renderer')

# Settings status helpers.
old_status = '        private String setupStatus(){return lockscreenMode?(language==LANG_KR?"사용 중":"Enabled"):(language==LANG_KR?"설정":"Setup");}\n'
new_status = r'''        private String setupStatus(){if(!postGateEnabled)return language==LANG_KR?"꺼짐":"Off";return postGateReady()?(language==LANG_KR?"준비됨":"Ready"):(language==LANG_KR?"설정 필요":"Setup");}
        private boolean accessibilityGateServiceEnabled(){String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);if(enabled==null||enabled.isEmpty())return false;String mine=new ComponentName(MainActivity.this,RippleGateAccessibilityService.class).flattenToString();for(String v:enabled.split(":"))if(mine.equalsIgnoreCase(v))return true;return false;}
        private boolean postGateReady(){return postGateEnabled&&accessibilityGateServiceEnabled()&&Settings.canDrawOverlays(MainActivity.this);}
'''
rep(old_status, new_status, 'setup status helper')

# Eight rows in the animated integration page.
rep('for(int i=0;i<7;i++)if(setupRowRect(i,getWidth(),getHeight()).contains(x,y)){handleSetupRow(i);return true;}',
    'for(int i=0;i<8;i++)if(setupRowRect(i,getWidth(),getHeight()).contains(x,y)){handleSetupRow(i);return true;}',
    'setup touch row count')

# Setup actions.
new_handlers = r'''        private void handleSettingRow(int i){if(i==0)openPicker(PICK_SURFACE);else if(i==1)openPicker(PICK_LANGUAGE);else if(i==2)openPicker(PICK_FEEDBACK);else if(i==3)openPicker(PICK_DEBUG);else if(i==4)openSetupPage();else if(i==5){clearCode();closeSettingsPanel();}invalidate();}
        private void handleSetupRow(int i){if(i==0){postGateEnabled=!postGateEnabled;prefs.edit().putBoolean(KEY_POST_GATE,postGateEnabled).apply();}else if(i==1)openSystemSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS);else if(i==2)openSystemSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);else if(i==3)requestHomeRole();else if(i==4)openPicker(PICK_HOME_APP);else if(i==5)openSystemSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);else if(i==6)openSystemSettings(Settings.ACTION_SECURITY_SETTINGS);else if(i==7)closeSetupPage();invalidate();}

'''
replace_between('        private void handleSettingRow', '        private void openSystemSettings', new_handlers, 'setup handlers')

# Compact eight-row geometry.
start = s.find('        private RectF setupRowRect')
end = s.find('        private RectF pickerAnchorRect', start)
if start < 0 or end < 0:
    raise SystemExit('missing setup row geometry')
s = s[:start] + '        private RectF setupRowRect(int i,float w,float h){RectF sh=settingsSheetRect(w,h);float top=sh.top+dp(70)+i*dp(48);return new RectF(sh.left+dp(16),top,sh.right-dp(16),top+dp(40));}\n' + s[end:]

p.write_text(s, encoding='utf-8')
print('v0.9 post-unlock gate applied')
