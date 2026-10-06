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
        dialog.setContentView(content); dialog.show();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            int bottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
            view.setPadding(pad, pad, pad, pad + bottom); return insets;
        });
        return dialog;
    }
}
