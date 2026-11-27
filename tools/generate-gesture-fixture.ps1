$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $projectRoot 'app/src/main/java/com/joelau/notifyisland/NativeIsland.java')
$start = $source.IndexOf('            @Override public boolean dispatchTouchEvent(MotionEvent ev){')
$end = $source.IndexOf('            @Override protected void onMeasure', $start)
if ($start -lt 0 -or $end -lt 0) { throw 'Touch handler extraction failed' }
$handlers = $source.Substring($start, $end - $start)
$prefix = @'
package com.joelau.notifyisland;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.LinearLayout;

/** Generated fixture: event methods below are copied verbatim from NativeIsland.card().
 * Only OEM rendering/service dependencies are replaced by counters. This is not an OEM runtime. */
class AuditedCard extends LinearLayout {
    final CardPullGesture pull;
    final AuditMotion motion=new AuditMotion();
    final AuditHost host=new AuditHost();
    final Object e=new Object();
    boolean settling,cancelTouch;
    AuditedCard(Context ctx) {
        super(ctx);
        float density=ctx.getResources().getDisplayMetrics().density;
        pull=new CardPullGesture(Math.round(CardPullGesture.COMMIT_DISTANCE_DP*density), Math.round(64*density), ViewConfiguration.get(ctx).getScaledTouchSlop());
        setClickable(true);
    }
    static class AuditMotion {
        int drags,returns;
        void drag(float x,float y){drags++;}
        void springBack(){returns++;}
    }
    static class AuditHost {
        final Handler main=new Handler(Looper.getMainLooper());
        int launches;
        void launch(Object entry,boolean small){if(small)launches++;}
    }
'@
$output = $prefix + "`n" + $handlers + "`n}`n"
[System.IO.File]::WriteAllText((Join-Path $projectRoot 'app/src/test/java/com/joelau/notifyisland/AuditedCard.java'), $output, [System.Text.UTF8Encoding]::new($false))
Write-Output 'Generated verbatim card event fixture (rendering/service calls replaced by counters).'
