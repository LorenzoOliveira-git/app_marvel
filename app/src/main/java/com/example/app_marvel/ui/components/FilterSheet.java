package com.example.app_marvel.ui.components;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.view.ContextThemeWrapper;
import com.example.app_marvel.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

/** Temporary filter choices; only the primary action commits the draft. */
public final class FilterSheet {
    private FilterSheet() { }
    public static BottomSheetDialog show(Context context, String title, View choices, Runnable apply, Runnable clear) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View content = android.view.LayoutInflater.from(context).inflate(R.layout.component_filter_sheet, null, false);
        int pad = Math.round(20 * context.getResources().getDisplayMetrics().density);
        ((TextView) content.findViewById(R.id.filter_heading)).setText(title);
        ((android.widget.FrameLayout) content.findViewById(R.id.filter_choices)).addView(choices);
        content.findViewById(R.id.filter_reset).setOnClickListener(v -> clear.run());
        content.findViewById(R.id.filter_apply).setOnClickListener(v -> { apply.run(); dialog.dismiss(); });
        dialog.setContentView(content);
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            int bottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom;
            int keyboard = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
            view.setPadding(pad, pad, pad, pad + bottom);
            // Shrink the scrolling choices instead of adding a keyboard-sized blank area.
            view.post(() -> {
                int usable = context.getResources().getDisplayMetrics().heightPixels - keyboard
                        - insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top - bottom;
                android.widget.ListView list = findList(choices);
                if (list != null && list.getAdapter() != null) {
                    int occupied = content.getHeight() - list.getHeight();
                    int row = Math.round(52 * context.getResources().getDisplayMetrics().density);
                    android.view.ViewGroup.LayoutParams params = list.getLayoutParams();
                    params.height = Math.max(row, Math.min(Math.min(row * list.getAdapter().getCount(), row * 6), usable - occupied));
                    if (list.getHeight() != params.height) list.setLayoutParams(params);
                }
            });
            return insets;
        });
        return dialog;
    }
    private static android.widget.ListView findList(View view) {
        if (view instanceof android.widget.ListView) return (android.widget.ListView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i=0; i<group.getChildCount(); i++) {
                android.widget.ListView found = findList(group.getChildAt(i)); if (found != null) return found;
            }
        }
        return null;
    }
}
