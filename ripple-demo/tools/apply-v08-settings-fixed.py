from pathlib import Path

src = Path('tools/apply-v08-settings.py').read_text(encoding='utf-8')
old = "btw('        @Override public boolean onTouchEvent(MotionEvent e){','        private void handleSettingRow',touch,'touch block')"
new = "btw('        @Override public boolean onTouchEvent(MotionEvent e){','        private boolean handleSettingsOrbGesture',touch,'touch block')"
if old not in src:
    raise SystemExit('missing touch range marker in settings patch')
src = src.replace(old, new, 1)
exec(compile(src, 'apply-v08-settings.py', 'exec'))
