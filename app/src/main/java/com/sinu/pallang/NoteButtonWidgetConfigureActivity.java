/*
 * Copyright (c) 2020-2022 Woohyun Shin (sinusinu)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.sinu.pallang;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sinu.pallang.databinding.NoteButtonWidgetConfigureBinding;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NoteButtonWidgetConfigureActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "com.sinu.pallang.widgets.notebutton";
    private static final String PREF_PREFIX_KEY = "widget_";
    private static final String PREF_POSTFIX_CDATE = "_cd";

    NoteButtonWidgetConfigureBinding binding;
    SharedPreferences sp;
    PallangNoteDatabase db;

    ArrayList<PallangNote> notes;
    PallangNoteListForWidgetAdapter adapter;

    int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    View.OnClickListener rvItemClickListener = (view) -> {
        int i = binding.rvNbwNotes.getChildAdapterPosition(view);

        final Context context = NoteButtonWidgetConfigureActivity.this;

        saveNotePref(context, widgetId, notes.get(i));

        // It is the responsibility of the configuration activity to update the app widget
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        NoteButtonWidget.updateAppWidget(context, appWidgetManager, widgetId);

        // Make sure we pass back the original appWidgetId
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    };

    public NoteButtonWidgetConfigureActivity() {
        super();
    }

    static void saveNotePref(Context context, int widgetId, PallangNote note) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putInt(PREF_PREFIX_KEY + widgetId, note.noteId);
        prefs.putLong(PREF_PREFIX_KEY + widgetId + PREF_POSTFIX_CDATE, note.createTime);
        prefs.apply();
    }

    static PallangNote loadNotePref(Context context, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        final int noteId = prefs.getInt(PREF_PREFIX_KEY + widgetId, -1);
        final long noteCreationDate = prefs.getLong(PREF_PREFIX_KEY + widgetId + PREF_POSTFIX_CDATE, 0);
        final PallangNote[] ret = new PallangNote[1];
        Thread thrDbCheck = new Thread(() -> {
            PallangNoteDatabase db = PallangNoteDBHolder.getDatabase(context);
            PallangNote note = db.noteDao().getNote(noteId);
            if (note == null) ret[0] = null;
            else if (note.createTime != noteCreationDate) ret[0] = null;
            else ret[0] = note;
        });
        thrDbCheck.start();
        try {
            thrDbCheck.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return ret[0];
    }

    static void deleteNotePref(Context context, int widgetId) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.remove(PREF_PREFIX_KEY + widgetId);
        prefs.remove(PREF_PREFIX_KEY + widgetId + PREF_POSTFIX_CDATE);
        prefs.apply();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        binding = NoteButtonWidgetConfigureBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sp = PreferenceManager.getDefaultSharedPreferences(this);
        db = PallangNoteDBHolder.getDatabase(getApplicationContext());

        // Find the widget id from the intent.
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        notes = new ArrayList<>();

        binding.rvNbwNotes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PallangNoteListForWidgetAdapter(notes);
        binding.rvNbwNotes.setAdapter(adapter);

        new Thread(() -> {
            notes.clear();
            List<PallangNote> dbNotes = db.noteDao().getNotes(sp.getInt("sort_mode", 0), sp.getBoolean("sort_asc", false));
            notes.addAll(dbNotes);
            runOnUiThread(() -> {
                if (notes.size() == 0) {
                    Toast.makeText(NoteButtonWidgetConfigureActivity.this, R.string.note_error_note_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    adapter.notifyDataSetChanged();
                }
            });
        }).start();
    }

    private class PallangNoteListForWidgetAdapter extends RecyclerView.Adapter<PallangNoteListForWidgetAdapter.PallangNoteListForWidgetViewHolder> {
        List<PallangNote> notes;

        public class PallangNoteListForWidgetViewHolder extends RecyclerView.ViewHolder {
            public LinearLayout v;

            public PallangNoteListForWidgetViewHolder(LinearLayout v) {
                super(v);
                this.v = v;
            }
        }

        public PallangNoteListForWidgetAdapter(List<PallangNote> notes) {
            this.notes = notes;
        }

        @NonNull
        @Override
        public PallangNoteListForWidgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout v = (LinearLayout) LayoutInflater.from(parent.getContext()).inflate(R.layout.list_note, parent, false);
            v.setOnClickListener(rvItemClickListener);
            return new PallangNoteListForWidgetViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PallangNoteListForWidgetViewHolder holder, int position) {
            ((TextView)holder.v.findViewById(R.id.tvwNoteListTitle)).setText(notes.get(position).noteHead);
            long lastModTime = notes.get(position).lastModTime;
            String lastModTimeFormat = DateFormat.getMediumDateFormat(NoteButtonWidgetConfigureActivity.this).format(new Date(notes.get(position).lastModTime));
            ((TextView)holder.v.findViewById(R.id.tvwNoteListTime)).setText(lastModTimeFormat);
            ((CheckBox)holder.v.findViewById(R.id.cbxNoteSelect)).setVisibility(View.GONE);
            ((CheckBox)holder.v.findViewById(R.id.cbxNoteSelect)).setChecked(false);
            ((ImageView)holder.v.findViewById(R.id.ivwNotePin)).setVisibility(notes.get(position).isPinned ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getItemCount() {
            return notes.size();
        }
    }
}

