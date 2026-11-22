package com.siddhesh.standby;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Add, tick off and delete to-do items. */
public class TodoActivity extends Activity {
    private Todo todo;
    private LinearLayout list;
    private EditText input;
    private float d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        d = getResources().getDisplayMetrics().density;
        todo = new Todo(this, d);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);
        root.setFitsSystemWindows(true);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText("To-do");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(30);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setPadding(dp(8), dp(8), dp(8), dp(12));
        root.addView(title);

        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        input = new EditText(this);
        input.setHint("Add a task");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF8E8E93);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Todo.MAX_LEN)});
        input.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                addCurrent();
                return true;
            }
            return false;
        });
        addRow.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button add = new Button(this);
        add.setText("Add");
        add.setOnClickListener(v -> addCurrent());
        addRow.addView(add);
        root.addView(addRow);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button clear = new Button(this);
        clear.setText("Clear completed");
        clear.setOnClickListener(v -> {
            todo.clearDone();
            render();
        });
        root.addView(clear);

        setContentView(root);
        render();
        input.requestFocus();
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    private void addCurrent() {
        if (todo.add(input.getText().toString())) {
            input.setText("");
            render();
        }
    }

    private void render() {
        list.removeAllViews();
        for (int i = 0; i < todo.items.size(); i++) {
            final int index = i;
            Todo.Item it = todo.items.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF1C1C1E);
            bg.setCornerRadius(dp(16));
            row.setBackground(bg);
            row.setPadding(dp(12), dp(4), dp(4), dp(4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);

            CheckBox cb = new CheckBox(this);
            cb.setText(it.text);
            cb.setTextSize(16);
            cb.setTextColor(it.done ? 0xFF8E8E93 : 0xFFFFFFFF);
            cb.setChecked(it.done);
            cb.setOnCheckedChangeListener((b, checked) -> {
                todo.toggle(index);
                render();
            });
            row.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button del = new Button(this);
            del.setText("✕");
            del.setContentDescription("Delete task");
            del.setTextColor(0xFF8E8E93);
            del.setBackgroundColor(Color.TRANSPARENT);
            del.setOnClickListener(v -> {
                todo.remove(index);
                render();
            });
            row.addView(del);
            list.addView(row, lp);
        }
        if (todo.items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No tasks yet. They show up in the To-do widget on StandBy.");
            empty.setTextColor(0xFF8E8E93);
            empty.setPadding(dp(8), dp(24), dp(8), dp(8));
            list.addView(empty);
        }
    }
}
