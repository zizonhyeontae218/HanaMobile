from pathlib import Path

p = Path('app/src/main/java/com/openai/rippledemo/MainActivity.java')
s = p.read_text(encoding='utf-8')

def rep(old, new, name):
    global s
    if old not in s:
        raise SystemExit('missing ' + name)
    s = s.replace(old, new, 1)

# v0.9.1: dragging the Surface Tension settings orb only commits when released
# beyond the activation position. A short tap still opens settings immediately.
old_orb = '''        private boolean handleSettingsOrbGesture(MotionEvent e){float x=e.getX(),y=e.getY();RectF hit=settingsOrbHitRect(getWidth(),getHeight());int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN){if(!hit.contains(x,y))return false;orbPointerId=e.getPointerId(0);orbDownX=x;orbDownY=y;orbDownAt=SystemClock.uptimeMillis();orbGestureIsDrag=false;orbState=ORB_ATTACHED_PRESSED;return true;}if(orbPointerId<0)return false;int idx=e.findPointerIndex(orbPointerId);if(idx<0)idx=0;x=e.getX(idx);y=e.getY(idx);if(a==MotionEvent.ACTION_MOVE){float dx=x-orbDownX,dy=y-orbDownY;if(Math.hypot(dx,dy)>dp(6))orbGestureIsDrag=true;if(orbGestureIsDrag){orbPull=clampF(orbDownX-x,0,maxOrbPull());orbState=orbPull>detachThreshold()?ORB_DETACHED_DRAGGING:ORB_STRETCHING;if(orbPull>=openThreshold()){float origin=orbPull;orbPointerId=-1;openSettingsPanel(origin);return true;}invalidate();}return true;}if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){long dur=SystemClock.uptimeMillis()-orbDownAt;boolean tap=a==MotionEvent.ACTION_UP&&!orbGestureIsDrag&&dur<=350;orbPointerId=-1;if(tap){orbPull=0;openSettingsPanel(0);}else startOrbReturn();return true;}return true;}\n\n'''
new_orb = '''        private boolean handleSettingsOrbGesture(MotionEvent e){float x=e.getX(),y=e.getY();RectF hit=settingsOrbHitRect(getWidth(),getHeight());int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN){if(!hit.contains(x,y))return false;orbPointerId=e.getPointerId(0);orbDownX=x;orbDownY=y;orbDownAt=SystemClock.uptimeMillis();orbGestureIsDrag=false;orbState=ORB_ATTACHED_PRESSED;return true;}if(orbPointerId<0)return false;int idx=e.findPointerIndex(orbPointerId);if(idx<0)idx=0;x=e.getX(idx);y=e.getY(idx);if(a==MotionEvent.ACTION_MOVE){float dx=x-orbDownX,dy=y-orbDownY;if(Math.hypot(dx,dy)>dp(6))orbGestureIsDrag=true;if(orbGestureIsDrag){orbPull=clampF(orbDownX-x,0,maxOrbPull());orbState=orbPull>detachThreshold()?ORB_DETACHED_DRAGGING:ORB_STRETCHING;invalidate();}return true;}if(a==MotionEvent.ACTION_UP){long dur=SystemClock.uptimeMillis()-orbDownAt;boolean tap=!orbGestureIsDrag&&dur<=350;boolean commit=orbGestureIsDrag&&orbPull>=openThreshold();float releasePull=orbPull;orbPointerId=-1;if(tap){orbPull=0;openSettingsPanel(0);}else if(commit){openSettingsPanel(releasePull);}else startOrbReturn();return true;}if(a==MotionEvent.ACTION_CANCEL){orbPointerId=-1;startOrbReturn();return true;}return true;}\n\n'''
rep(old_orb, new_orb, 'release-to-open settings orb')

# The setup sub-page must fully replace the main settings list. Previously the
# main page retained 58% opacity at sp=1, causing the photographed overlap.
old_main = '''        private void drawSettingsMain(Canvas canvas,RectF sheet,float w,float h,int a,float sp){int ma=(int)(a*(1f-.42f*sp));canvas.save();canvas.translate(-dp(22)*sp,0);'''
new_main = '''        private void drawSettingsMain(Canvas canvas,RectF sheet,float w,float h,int a,float sp){int ma=(int)(a*(1f-sp));if(ma<=1)return;canvas.save();canvas.translate(-dp(34)*sp,0);'''
rep(old_main, new_main, 'setup page main-list fade')

p.write_text(s, encoding='utf-8')
print('v0.9.1 settings hotfix applied')
