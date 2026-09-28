from pathlib import Path

p = Path('app/src/main/java/com/openai/rippledemo/MainActivity.java')
s = p.read_text(encoding='utf-8')

s = s.replace('drawSurfaceTensionClock(canvas,w,h,now);', 'drawClassicStackClock(canvas,w,h,now);')

start = s.index('        private void drawSurfaceTensionClock')
end = s.index('        private void drawSettingsEdgeControl', start)

replacement = '''        private void drawClassicStackClock(Canvas canvas,float w,float h,long now) {
            Calendar c = Calendar.getInstance();
            String hh = String.format(Locale.US,"%02d",c.get(Calendar.HOUR_OF_DAY));
            String mm = String.format(Locale.US,"%02d",c.get(Calendar.MINUTE));
            String date = dateString(c);

            float tint = clockTintAmount(now);
            int clockColor = blend(Color.rgb(242,242,240), Color.rgb(255,178,177), tint*.80f);
            int dateColor = blend(Color.rgb(220,221,218), Color.rgb(255,168,168), tint*.68f);

            float cx = w / 2f;
            float hourSize = dp(55f);
            float minuteSize = dp(55f);
            float hourBase = dp(95f);
            float minuteBase = dp(150f);
            float dateBase = dp(184f);

            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(android.graphics.Typeface.create("sans-serif-light",0));
            text.setShadowLayer(dp(3.2f),0,dp(1.2f),Color.argb(38,0,0,0));

            text.setTextSize(hourSize);
            text.setColor(clockColor);
            canvas.drawText(hh,cx,hourBase,text);

            text.setTextSize(minuteSize);
            canvas.drawText(mm,cx,minuteBase,text);

            // Match the entire date width to the rendered width of a two-digit minute ("00").
            float targetWidth = text.measureText("00");
            text.setTypeface(android.graphics.Typeface.create("sans-serif",0));
            float probeSize = dp(24f);
            text.setTextSize(probeSize);
            float dateWidth = Math.max(dp(1f), text.measureText(date));
            float dateSize = clampF(probeSize * targetWidth / dateWidth, dp(16f), dp(29f));
            text.setTextSize(dateSize);
            text.setColor(dateColor);
            canvas.drawText(date,cx,dateBase,text);
            text.clearShadowLayer();
        }

        private String dateString(Calendar c) {
            if(language==LANG_KR){
                String[] d={"일","월","화","수","목","금","토"};
                return String.format(Locale.KOREA,"%d/%d%s",c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH),d[c.get(Calendar.DAY_OF_WEEK)-1]);
            }
            String[] d={"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
            return String.format(Locale.US,"%d/%d%s",c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH),d[c.get(Calendar.DAY_OF_WEEK)-1]);
        }

'''

s = s[:start] + replacement + s[end:]
p.write_text(s, encoding='utf-8')
