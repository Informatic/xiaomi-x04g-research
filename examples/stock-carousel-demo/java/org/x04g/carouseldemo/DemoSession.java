package org.x04g.carouseldemo;

import android.app.Activity;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A local counter only: no storage, service, network, media or OEM asset access. */
final class DemoSession implements AutoCloseable {
    static final String PAGE_ID = "org.x04g.carouseldemo.page";
    private final FrameLayout root;
    private Button counter;
    private boolean active, closed;
    private int count;

    DemoSession(Activity activity) { root = createView(activity); }

    /** Change this view to experiment with content inside the existing OEM page. */
    private FrameLayout createView(Activity activity) {
        FrameLayout page = new FrameLayout(activity);
        page.setBackgroundColor(Color.rgb(24, 24, 24));
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        TextView title = new TextView(activity);
        title.setText("Stock carousel demo");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 38);
        title.setGravity(Gravity.CENTER);
        content.addView(title, new LinearLayout.LayoutParams(-1, 72));
        TextView hint = new TextView(activity);
        hint.setText("A native page in the original launcher\nSwipe to return to the clock");
        hint.setTextColor(Color.LTGRAY);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24);
        hint.setGravity(Gravity.CENTER);
        content.addView(hint, new LinearLayout.LayoutParams(-1, 82));
        counter = new Button(activity);
        counter.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28);
        counter.setAllCaps(false);
        counter.setContentDescription("Increase local demo counter");
        counter.setText("Count: 0");
        counter.setEnabled(false);
        counter.setOnClickListener(view -> {
            if (!active || closed) return;
            count++;
            counter.setText("Count: " + count);
        });
        content.addView(counter, new LinearLayout.LayoutParams(320, 84));
        page.addView(content, new FrameLayout.LayoutParams(-1, -1));
        // No touch interception: the original pager still owns horizontal swipes.
        return page;
    }

    View view() { return root; }
    void selected(String id, boolean visible) {
        active = !closed && visible && PAGE_ID.equals(id);
        counter.setEnabled(active);
    }
    void pageDestroyed(String id) { if (PAGE_ID.equals(id)) selected("", false); }
    void onTrimMemory(int level) { /* No background or decoded resources to release. */ }
    @Override public void close() {
        if (closed) return;
        closed = true;
        active = false;
        counter.setEnabled(false);
        counter.setOnClickListener(null);
    }
}
