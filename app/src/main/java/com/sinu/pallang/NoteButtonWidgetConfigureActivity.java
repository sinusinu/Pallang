/*
 * Copyright (c) 2020-2024 Woohyun Shin (sinusinu)
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
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.sinu.pallang.databinding.NoteButtonWidgetConfigureBinding;

import java.util.ArrayList;
import java.util.List;

public class NoteButtonWidgetConfigureActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "com.sinu.pallang.widgets.notebutton";
    private static final String PREF_PREFIX_KEY = "widget_";
    private static final String PREF_POSTFIX_CDATE = "_cd";

    NoteButtonWidgetConfigureBinding binding;
    SharedPreferences sp;
    PallangNoteDatabase db;

    ArrayList<PallangNote> notes;
    WidgetNoteListAdapter adapter;

    int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    int lastNoteId = -1;

    View.OnClickListener rvItemClickListener = (view) -> {
        int i = binding.rvNbwNotes.getChildAdapterPosition(view) - 1;

        if (i == -1) {
            final Context context = NoteButtonWidgetConfigureActivity.this;

            Thread tFetchLastId = new Thread(() -> { lastNoteId = (notes.size() == 0) ? -1 : db.noteDao().getLastNoteId(); });
            tFetchLastId.start();
            try { tFetchLastId.join(); } catch (InterruptedException ignored) {}

            View llProps = getLayoutInflater().inflate(R.layout.dialog_properties, null);
            AlertDialog adProps;

            AlertDialog.Builder abNewNoteProp = new AlertDialog.Builder(this);
            abNewNoteProp.setTitle(R.string.main_new_note);
            llProps.findViewById(R.id.llPropsInfo).setVisibility(View.GONE);
            abNewNoteProp.setView(llProps);
            abNewNoteProp.setPositiveButton(R.string.cancel, null);
            // to override dialog closing behavior, OnClickListener for button must be set when dialog is shown
            abNewNoteProp.setPositiveButton(R.string.prop_save_changes, null);
            adProps = abNewNoteProp.create();

            ((EditText)llProps.findViewById(R.id.edtPropsTitle)).setText(R.string.main_new_note_title);
            ((CheckBox)llProps.findViewById(R.id.cbxPropsPinned)).setChecked(false);
            adProps.show();
            adProps.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.prop_create_note);
            adProps.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener((v) -> {
                // stop if title is empty
                final String newNoteTitle = ((EditText)llProps.findViewById(R.id.edtPropsTitle)).getText().toString();
                if (newNoteTitle.trim().length() == 0) {
                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), R.string.note_error_title_empty, Toast.LENGTH_SHORT).show());
                    return;
                }

                // insert new note into database
                final int newNoteId = lastNoteId + 1;
                final boolean isNewNotePinned = ((CheckBox)llProps.findViewById(R.id.cbxPropsPinned)).isChecked();
                final boolean enableNewNoteMarkdown = ((CheckBox)llProps.findViewById(R.id.cbxPropsMarkdown)).isChecked();

                long currentTime = System.currentTimeMillis();
                PallangNote n = new PallangNote();
                n.noteId = newNoteId;
                n.noteHead = newNoteTitle;
                n.noteBody = "";
                n.createTime = currentTime;
                n.lastModTime = currentTime;
                n.noteStyle = 0;
                n.isPinned = isNewNotePinned;
                n.enableMarkdown = enableNewNoteMarkdown;

                Thread thrCreateNewNote = new Thread(() -> db.noteDao().insertNote(n));

                thrCreateNewNote.start();
                try { thrCreateNewNote.join(); } catch (InterruptedException ignored) {}

                adProps.dismiss();

                saveNotePref(context, widgetId, n);

                // It is the responsibility of the configuration activity to update the app widget
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                NoteButtonWidget.updateAppWidget(context, appWidgetManager, widgetId);

                // Make sure we pass back the original appWidgetId
                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                setResult(RESULT_OK, resultValue);
                finish();
            });
        } else {
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
        }
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
        adapter = new WidgetNoteListAdapter(this, notes, rvItemClickListener);
        binding.rvNbwNotes.setAdapter(adapter);

        updateNotes();
    }

    private void updateNotes() {
        Thread tUpdateNotes = new Thread(() -> {
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
        });
        tUpdateNotes.start();
        try { tUpdateNotes.join(); } catch (InterruptedException ignored) {}
    }
}

