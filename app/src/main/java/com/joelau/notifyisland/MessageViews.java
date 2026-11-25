package com.joelau.notifyisland;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.*;
import java.util.function.IntSupplier;

/** Fixed siblings keep the portrait and unread count outside the scrolling text. */
final class MessageViews {
    /** Last logged capsule half measurement, so the log only grows when the pill resizes. */
    private static final java.util.HashMap<String,String> LAST_HALF_SIZE=new java.util.HashMap<>();
    static LinearLayout half(Context context,int width,int height,boolean left,String pkg,
            String sender,String right,Bitmap portrait,MessageDisplay mode,Integer unread)throws Exception{
        return half(context,()->width,()->height,left,pkg,sender,right,portrait,mode,unread);
    }
    static LinearLayout half(Context context,IntSupplier width,IntSupplier heightSource,boolean left,String pkg,
            String sender,String right,Bitmap portrait,MessageDisplay mode,Integer unread)throws Exception{
        int height=heightSource.getAsInt();
        int edge=Math.round(height*.30f),gap=Math.round(height*.12f);
        final boolean badge=!left&&unread!=null;
        LinearLayout row=new LinearLayout(context){
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                int h=Math.max(1,heightSource.getAsInt());
                if(MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED)h=Math.min(h,MeasureSpec.getSize(heightSpec));
                int w=Math.max(1,width.getAsInt());
                if(MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED)w=Math.min(w,MeasureSpec.getSize(widthSpec));
                w=Math.min(w,sideCeiling(context,h));
                int padding=Math.round(h*(badge?.24f:.30f)),spacing=Math.round(h*.12f);
                setPadding(left?padding:0,0,left?0:padding,0);
                for(int i=0;i<getChildCount();i++){
                    View child=getChildAt(i);LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)child.getLayoutParams();
                    p.leftMargin=i==0?0:spacing;
                    if(child instanceof IslandIconView){p.width=Math.round(h*.60f);p.height=p.width;}
                    else if(child instanceof UnreadBadge count){p.height=Math.round(h*.44f);count.resize(h);}
                    else if(child instanceof TextSlot slot)slot.setTextSize(h);
                }
                super.onMeasure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));
                // Take the room the native capsule still offers rather than clipping a glyph:
                // a too tight parent width used to slice the last character in half.
                int needed=padding;
                for(int i=0;i<getChildCount();i++){
                    View child=getChildAt(i);
                    needed+=(i==0?0:spacing)+(child instanceof TextSlot slot
                            ?slot.requiredWidth()
                            :Math.max(child.getMeasuredWidth(),child instanceof IslandIconView?Math.round(h*.60f):0));
                }
                if(needed>w&&MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED){
                    // The parent only knows the native island max width, which reaches across
                    // the status bar. Never grow past our own ceiling: past it the text scrolls
                    // instead, so the clock and the battery/wifi cluster stay uncovered.
                    int cap=Math.min(MeasureSpec.getSize(widthSpec),sideCeiling(context,h));
                    if(cap>w){w=Math.min(needed,cap);
                        super.onMeasure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));}
                }
                for(int i=0;i<getChildCount();i++){
                    View child=getChildAt(i);
                    if(child instanceof TextSlot slot)slot.schedule();
                }
                String size="mode="+MeasureSpec.getMode(widthSpec)+" spec="+MeasureSpec.getSize(widthSpec)
                        +" supplier="+width.getAsInt()+" w="+w+" h="+h+" measured="+getMeasuredWidth();
                if(!size.equals(LAST_HALF_SIZE.get(String.valueOf(left)))){
                    LAST_HALF_SIZE.put(String.valueOf(left),size);
                    android.util.Log.i("NotifyIsland","Pill half left="+left+" "+size);
                }
            }
        };row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setPadding(left?edge:0,0,left?0:edge,0);
        // WRAP_CONTENT lets FrameLayout pass the native max width as AT_MOST.
        // A fixed LayoutParams.width becomes EXACTLY even beyond the parent's cap.
        row.setLayoutParams(new FrameLayout.LayoutParams(-2,-1,Gravity.CENTER));
        if(left){
            IslandIconView icon=portrait==null?new IslandIconView(context,pkg):new IslandIconView(context,portrait);
            int size=Math.round(height*.60f);row.addView(icon,new LinearLayout.LayoutParams(size,size));
        }
        if(left&&!mode.iconOnlyLeft()){
            TextSlot slot=new TextSlot(context,capText(sender),height);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-1,1);params.leftMargin=gap;row.addView(slot,params);
        }else if(!left){
            TextSlot slot=new TextSlot(context,capText(right),height);
            row.addView(slot,new LinearLayout.LayoutParams(0,-1,1));
            if(unread!=null){
                UnreadBadge count=new UnreadBadge(context,unread,height);
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,Math.round(height*.44f));params.leftMargin=gap;row.addView(count,params);
            }
        }
        row.setContentDescription(sender+"，"+right);return row;
    }

    // One-pass scroller driven by the TextView's own scrollX. A vsync-aligned
    // ValueAnimator pans to the end after a 1s dwell and holds; there is no blurred
    // clone, edge mask or transparency layer on the text itself.
    private static final long MARQUEE_DWELL_MS=1000L;
    private static final float MARQUEE_PX_PER_SEC=90f;
    /** Island text is capped to whole characters; anything longer scrolls within this limit. */
    private static final int MAX_TEXT_CHARS=18;
    /** Rounding allowance so the last glyph is never sliced by a 1px weight rounding. */
    private static final int TEXT_SLACK_PX=4;

    static String capText(String value){
        if(value==null)return "";
        if(value.length()<=MAX_TEXT_CHARS)return value;
        int cut=MAX_TEXT_CHARS;
        // Never split a surrogate pair, which would render as a broken glyph.
        if(Character.isHighSurrogate(value.charAt(cut-1))&&Character.isLowSurrogate(value.charAt(cut)))cut--;
        return value.substring(0,cut);
    }

    /** A single plain scrolling text view; the runner pans scrollX, never the text layer. */
    static final class TextSlot extends TextView {
        private MarqueeRunner runner;
        TextSlot(Context context,String value,int height){
            super(context);
            setText(value);setTextColor(Color.WHITE);
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,height*.40f);
            setIncludeFontPadding(false);setSingleLine(true);
            setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            setHorizontallyScrolling(true);setEllipsize(null);setFocusable(false);
        }
        void setTextSize(int h){setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,h*.40f);}
        /** Exact room this line needs, including a small rounding allowance. */
        int requiredWidth(){
            return (int)Math.ceil(android.text.Layout.getDesiredWidth(getText(),getPaint()))+TEXT_SLACK_PX;
        }
        void schedule(){
            int viewW=getMeasuredWidth();
            float desired=android.text.Layout.getDesiredWidth(getText(),getPaint());
            if(viewW<=0||desired<=viewW){
                if(runner!=null){runner.stop();runner=null;}
                setScrollX(0);return;
            }
            if(runner==null)runner=new MarqueeRunner(this);
            runner.begin();
        }
    }

    private static TextView makeText(Context context,String value,int height){
        TextView view=new TextView(context);
        view.setText(value);view.setTextColor(Color.WHITE);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,height*.40f);
        view.setIncludeFontPadding(false);view.setSingleLine(true);
        view.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        view.setHorizontallyScrolling(true);view.setEllipsize(null);view.setFocusable(false);
        return view;
    }

    private static final class MarqueeRunner implements Runnable{
        private final TextSlot slot;
        private ValueAnimator animator;
        private int phase,max; // 0 dwell at start, 1 scrolling, 2 hold at end
        MarqueeRunner(TextSlot slot){this.slot=slot;}
        void begin(){
            if(animator!=null){animator.cancel();animator=null;}
            phase=0;post(MARQUEE_DWELL_MS);
        }
        void stop(){
            if(animator!=null){animator.cancel();animator=null;}
            slot.removeCallbacks(this);
            slot.setScrollX(0);
        }
        private void post(long delay){slot.removeCallbacks(this);slot.postDelayed(this,delay);}
        private int resolveMax(){
            int viewW=slot.getWidth();
            if(viewW<=0)return 0;
            android.text.Layout layout=slot.getLayout();
            float content=layout!=null?layout.getLineWidth(0):android.text.Layout.getDesiredWidth(slot.getText(),slot.getPaint());
            return Math.max(0,Math.round(content)-viewW);
        }
        @Override public void run(){
            if(!slot.isAttachedToWindow())return;
            max=resolveMax();
            if(max<=0){slot.setScrollX(0);phase=2;return;}
            phase=1;
            long duration=Math.round(max*1000L/MARQUEE_PX_PER_SEC);
            animator=ValueAnimator.ofInt(0,max);
            animator.setDuration(duration);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a->slot.setScrollX((Integer)a.getAnimatedValue()));
            animator.addListener(new android.animation.AnimatorListenerAdapter(){
                @Override public void onAnimationEnd(android.animation.Animator a){phase=2;}
                @Override public void onAnimationCancel(android.animation.Animator a){phase=2;}
            });
            animator.start();
        }
    }

    // Fixed expanded capsule matches the native music island width
    // (music pill measured at ~0.378 of screen width on the 1260px baseline).
    private static final float MUSIC_ISLAND_RATIO=476f;
    /**
     * Longest capsule allowed for long messages, on the same 1260px baseline. The official
     * atomic island stops well short of the status-bar icons; this ratio keeps ~20% of the
     * screen free on each side so the clock and the battery/wifi cluster are never touched.
     */
    private static final float MAX_ISLAND_RATIO=756f;
    static int halfWidth(int screen,int cutout,int height,boolean wide){
        return Math.max(height,Math.round(screen*(wide?MAX_ISLAND_RATIO:588f)/1260f-cutout)/2);
    }
    static int fixedIslandWidth(int screen,int cutout,int height){
        return Math.max(height,Math.round(screen*MUSIC_ISLAND_RATIO/1260f-cutout)/2);
    }
    /**
     * Hard per-side ceiling for the collapsed capsule: half of MAX_ISLAND_RATIO, i.e. the whole
     * pill may reach 60% of the screen width (756/1260 baseline), which is the native island's
     * longest length that still leaves the status bar clear. The container happily measures past
     * the status bar, so a long message used to stretch the pill until it squeezed the clock.
     * Text longer than this scrolls inside the capped slot instead of widening the island.
     */
    static int sideCeiling(Context context,int height){
        android.util.DisplayMetrics metrics=context.getResources().getDisplayMetrics();
        int screen=Math.min(metrics.widthPixels,metrics.heightPixels);
        return Math.max(height,Math.round(screen*MAX_ISLAND_RATIO/1260f)/2);
    }
    static int adaptiveHalfWidth(Context context,int screen,int cutout,int height,String sender,String right,
            MessageDisplay mode,Integer unread,boolean wide,boolean left){
        int maximum=halfWidth(screen,cutout,height,true);
        // The saved wide switch now means a fixed expanded capsule. Otherwise each half
        // adapts to its own content under the same ceiling.
        if(wide)return fixedIslandWidth(screen,cutout,height);
        int edge=Math.round(height*.30f),gap=Math.round(height*.12f),icon=Math.round(height*.60f);
        // The badge sits closer to the capsule edge than a plain text row does.
        int badgeEdge=Math.round(height*.24f);
        int leftNeed=edge+icon+(mode.iconOnlyLeft()?0:gap+textWidth(context,capText(sender),height));
        int endNeed=(unread!=null?badgeEdge:edge)+textWidth(context,capText(right),height);
        if(unread!=null){UnreadBadge badge=new UnreadBadge(context,unread,height);badge.measure(0,0);endNeed+=gap+badge.getMeasuredWidth();}
        // Each half used to be sized to max(left, end), so the widest half's need was paid
        // twice: a short avatar half carried the body text's width and the pill ended up at
        // roughly double its content (very visible once a side circle joined it).
        int need=left?leftNeed:endNeed;
        return Math.min(maximum,Math.max(height,need));
    }
    static String unreadLabel(int count){return count>99?"99+":Integer.toString(count);}
    static final class UnreadBadge extends TextView {
        private final GradientDrawable background=new GradientDrawable();
        UnreadBadge(Context context,int count,int height){
            super(context);setText(unreadLabel(count));setContentDescription("未读消息 "+count+" 条");
            setTextColor(Color.WHITE);setIncludeFontPadding(false);setSingleLine(true);setGravity(Gravity.CENTER);
            setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
            background.setColor(0xff333333);setBackground(background);resize(height);
        }
        void resize(int height){
            int size=Math.round(height*.44f),padding=Math.round(height*.12f);
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,height*.24f);setMinWidth(size);
            setPadding(padding,0,padding,0);background.setCornerRadius(size/2f);
        }
    }
    private static int textWidth(Context context,String value,int height){
        TextView text=makeText(context,value,height);
        return (int)Math.ceil(android.text.Layout.getDesiredWidth(value==null?"":value,text.getPaint()))+TEXT_SLACK_PX;
    }
    static LinearLayout header(Context context,String pkg,String sender,Bitmap portrait,int color){
        int size=dp(context,28);
        LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);header.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        View appIcon;try{appIcon=new IslandIconView(context,pkg);}catch(Exception ignored){appIcon=new View(context);}
        // The contact portrait now leads the row and the app glyph closes it: the card's tinted
        // pool sits behind the left corner, so the portrait has to be the first thing the eye meets.
        LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(size,size);
        if(portrait!=null){
            header.addView(new IslandIconView(context,portrait),iconParams);
        }else{
            header.addView(appIcon,iconParams);
        }
        TextView title=new TextView(context);title.setText(sender);title.setTextColor(color);title.setIncludeFontPadding(false);
        title.setTextSize(16);title.setTypeface(null,android.graphics.Typeface.BOLD);title.setPadding(dp(context,12),0,0,0);
        title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);title.setGravity(Gravity.CENTER_VERTICAL);
        title.setAutoSizeTextTypeUniformWithConfiguration(12,16,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        header.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        if(portrait!=null){
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(size,size);params.leftMargin=dp(context,12);
            header.addView(appIcon,params);
        }
        return header;
    }
    private static int dp(Context context,float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
}
