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

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.GestureDetectorCompat;
import androidx.preference.PreferenceManager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.method.KeyListener;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.sinu.pallang.databinding.ActivityNoteBinding;

import org.commonmark.node.Link;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.LinkResolver;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.SoftBreakAddsNewLinePlugin;
import io.noties.markwon.SpanFactory;
import io.noties.markwon.core.spans.LinkSpan;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;

public class NoteActivity extends AppCompatActivity {
    ActivityNoteBinding binding;
    SharedPreferences sp;
    PallangNoteDatabase db;

    ActionBar ab;

    int noteId;
    boolean openInEditMode;
    boolean shouldCheckNewNoteImmediateClose;
    boolean isInEditMode;
    boolean isChangeMade;

    AlertDialog adProps;
    AlertDialog adStyle;
    AlertDialog adDelete;
    AlertDialog adShare;
    AlertDialog adDiscard;
    View viewProps;
    View viewShare;

    // the note that this instance is editing
    PallangNote note;

    // variable for storing EditText's default KeyListener
    KeyListener kl;

    // markwon instance (do not use directly; use getMarkwon instead)
    Markwon markwon = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNoteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.tbrNoteToolbar);
        ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);
        ab.setHomeAsUpIndicator(R.drawable.ic_back_24_ctrlcolor);

        noteId = getIntent().getIntExtra("noteId", -1);
        openInEditMode = getIntent().getBooleanExtra("openInEditMode", false);
        shouldCheckNewNoteImmediateClose = openInEditMode;
        if (noteId == -1) { Toast.makeText(getApplicationContext(), getString(R.string.note_error_invalid_id), Toast.LENGTH_SHORT).show(); finish(); return; }

        sp = PreferenceManager.getDefaultSharedPreferences(this);
        db = PallangNoteDBHolder.getDatabase(getApplicationContext());

        Log.d("Pallang", "Looking up for note " + noteId);
        Thread thrGetNote = new Thread(() -> {
            note = db.noteDao().getNote(noteId);
        });
        thrGetNote.start();
        try {
            thrGetNote.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), getString(R.string.note_error_note_not_found), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (note == null) {
            Toast.makeText(getApplicationContext(), getString(R.string.note_error_note_not_found), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // get reference of default KeyListener before setting it to null
        kl = binding.edtNoteBody.getKeyListener();

        setEditMode(openInEditMode);
        isChangeMade = openInEditMode;

        binding.edtNoteBody.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable editable) {
                if (isInEditMode) {
                    markChanges(true);
                }
            }
        });

        viewProps = getLayoutInflater().inflate(R.layout.dialog_properties, null);

        AlertDialog.Builder abProps = new AlertDialog.Builder(this);
        abProps.setTitle(R.string.note_menu_props);
        abProps.setView(viewProps);
        abProps.setNegativeButton(R.string.cancel, null);
        // to override dialog closing behavior, OnClickListener for button must be set when dialog is shown
        abProps.setPositiveButton(R.string.prop_save_changes, null);
        adProps = abProps.create();

        AlertDialog.Builder abStyle = new AlertDialog.Builder(this);
        abStyle.setTitle(R.string.note_menu_style_title);
        abStyle.setItems(new CharSequence[]{ getString(R.string.note_menu_style_sansserif),
                                             getString(R.string.note_menu_style_serif) },
                         (dialog, which) -> setNoteStyle(which)
        );
        adStyle = abStyle.create();

        AlertDialog.Builder abDelete = new AlertDialog.Builder(this);
        abDelete.setMessage(R.string.note_delete_confirm);
        abDelete.setNegativeButton(R.string.no, null);
        abDelete.setPositiveButton(R.string.yes, (dialog, which) -> {
            new Thread(() -> {
                db.noteDao().updateNote(note);
                db.noteDao().deleteNote(note);
                runOnUiThread(() -> {
                    Toast.makeText(getApplicationContext(), R.string.note_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }).start();
        });
        adDelete = abDelete.create();

        viewShare = getLayoutInflater().inflate(R.layout.dialog_share, null);
        viewShare.findViewById(R.id.llShareAsText).setOnClickListener((view) -> {
            shareAsText();
        });
        viewShare.findViewById(R.id.llShareAsFile).setOnClickListener((view) -> {
            adShare.dismiss();
            String fileName = note.noteHead.replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt";
            String fileCont = note.noteBody;

            switch (sp.getString("lf_type", "windows")) {
                case "windows":
                default:
                    fileCont = fileCont.replace("\r\n", "\n").replace("\n", "\r\n");
                    break;
                case "unix":
                    fileCont = fileCont.replace("\r\n", "\n");
                    break;
            }

            File cachePath = new File(getCacheDir(), "notes");
            cachePath.mkdirs();
            File f = new File(cachePath, fileName);

            try {
                FileOutputStream fos = new FileOutputStream(f);
                PrintWriter pw = new PrintWriter(fos);
                pw.write(fileCont);
                pw.close();
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, R.string.note_error_share_file_fail, Toast.LENGTH_SHORT).show();
                return;
            }

            Uri uriShare = FileProvider.getUriForFile(this, "com.sinu.pallang.fileprovider", f);

            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            sendIntent.setDataAndType(uriShare, getContentResolver().getType(uriShare));
            sendIntent.putExtra(Intent.EXTRA_STREAM, uriShare);
            startActivity(Intent.createChooser(sendIntent, null));
        });

        AlertDialog.Builder abShare = new AlertDialog.Builder(this);
        abShare.setTitle(R.string.note_share_desc);
        abShare.setView(viewShare);
        adShare = abShare.create();

        AlertDialog.Builder abDiscard = new AlertDialog.Builder(this);
        abDiscard.setMessage(R.string.note_discard_changes_confirm);
        abDiscard.setNegativeButton(R.string.no, null);
        abDiscard.setPositiveButton(R.string.yes, (dialog, which) -> {
            new Thread(() -> {
                note = db.noteDao().getNote(note.noteId);
                runOnUiThread(() -> {
                    ab.setTitle(note.noteHead);
                    binding.edtNoteBody.setText(note.noteBody);
                    String lastModTimeFormat = DateFormat.getLongDateFormat(NoteActivity.this).format(new Date(note.lastModTime))
                            + " " + DateFormat.getTimeFormat(NoteActivity.this).format(new Date(note.lastModTime));
                    binding.tvwNoteDateDataDisp.setText(getString(R.string.note_last_mod, lastModTimeFormat));
                    updateNoteStyle();
                    isChangeMade = false;
                    Toast.makeText(getApplicationContext(), R.string.note_discard_changes_done, Toast.LENGTH_SHORT).show();
                });
            }).start();
        });
        adDiscard = abDiscard.create();

        updateNoteStyle();

        setDoubleTapListener();

        String textSize = sp.getString("text_size", "medium");
        float textSizeF;
        switch (textSize) {
            case "xsmall":
                textSizeF = 16f;
                break;
            case "small":
                textSizeF = 20f;
                break;
            case "medium":
            default:
                textSizeF = 24f;
                break;
            case "large":
                textSizeF = 30f;
                break;
            case "xlarge":
                textSizeF = 36f;
                break;
        }
        binding.edtNoteBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeF);

        // display update has been moved to onResume
    }

    // code detached from llShareAsText's click listener
    private void shareAsText() {
        if (adShare.isShowing()) adShare.dismiss();
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, note.noteBody);
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, null);
        startActivity(shareIntent);
    }

    @Override
    protected void onResume() {
        // update display on onResume in case of note being updated outside (e.g. overwritten with shared text)
        new Thread(() -> {
            note = db.noteDao().getNote(noteId);
            runOnUiThread(() -> {
                markChanges(isChangeMade);
                if (note.enableMarkdown) {
                    binding.edtNoteBody.setTextIsSelectable(false);
                    getMarkwon().setMarkdown(binding.edtNoteBody, note.noteBody);
                } else {
                    binding.edtNoteBody.setTextIsSelectable(true);
                    binding.edtNoteBody.setText(note.noteBody);
                }
                String lastModTimeFormat = DateFormat.getLongDateFormat(NoteActivity.this).format(new Date(note.lastModTime))
                        + " " + DateFormat.getTimeFormat(NoteActivity.this).format(new Date(note.lastModTime));
                binding.tvwNoteDateDataDisp.setText(getString(R.string.note_last_mod, lastModTimeFormat));
            });
        }).start();
        super.onResume();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setDoubleTapListener() {
        binding.edtNoteBody.setOnTouchListener(new View.OnTouchListener() {
            private GestureDetectorCompat gestureDetectorCompat = new GestureDetectorCompat(NoteActivity.this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (!isInEditMode) {
                        setEditMode(true);
                        return true;
                    } else return false;
                }
            });
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) { return gestureDetectorCompat.onTouchEvent(motionEvent); }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isInEditMode) getMenuInflater().inflate(R.menu.menu_note_edit, menu);
        else getMenuInflater().inflate(R.menu.menu_note_view, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            checkClose(true);
        } else if (itemId == R.id.mnuNoteViewEdit) {
            Layout layout = binding.edtNoteBody.getLayout();
            binding.edtNoteBody.setSelection(layout.getOffsetForHorizontal(layout.getLineForVertical(binding.svNoteScroller.getScrollY()), 0));
            setEditMode(true);
        } else if (itemId == R.id.mnuNoteEditSave) {
            if (isChangeMade) saveChanges();
            setEditMode(false);
        } else if (itemId == R.id.mnuNoteEditProps) {
            // populate props dialog
            ((EditText) viewProps.findViewById(R.id.edtPropsTitle)).setText(note.noteHead);
            ((CheckBox) viewProps.findViewById(R.id.cbxPropsPinned)).setChecked(note.isPinned);
            ((CheckBox) viewProps.findViewById(R.id.cbxPropsMarkdown)).setChecked(note.enableMarkdown);
            ((TextView) viewProps.findViewById(R.id.tvwPropsSize)).setText(getString(R.string.prop_len_format,
                    Util.getGraphemeLength(binding.edtNoteBody.getText().toString())));
            String creationTimeFormat = DateFormat.getLongDateFormat(NoteActivity.this).format(new Date(note.createTime))
                    + " " + DateFormat.getTimeFormat(NoteActivity.this).format(new Date(note.createTime));
            ((TextView) viewProps.findViewById(R.id.tvwPropsCreation)).setText(creationTimeFormat);
            String lastModTimeFormat = DateFormat.getLongDateFormat(NoteActivity.this).format(new Date(note.lastModTime))
                    + " " + DateFormat.getTimeFormat(NoteActivity.this).format(new Date(note.lastModTime));
            ((TextView) viewProps.findViewById(R.id.tvwPropsLastMod)).setText(lastModTimeFormat);

            adProps.show();
            adProps.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener((view) -> {
                if (((EditText) viewProps.findViewById(R.id.edtPropsTitle)).getText().toString().trim().length() == 0) {
                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), R.string.note_error_title_empty, Toast.LENGTH_SHORT).show());
                    return;
                }
                new Thread(() -> {
                    // if user changed the title, update last mod time (pinning shouldn't update that)
                    if (!note.noteHead.equals(((EditText) viewProps.findViewById(R.id.edtPropsTitle)).getText().toString()))
                        note.lastModTime = System.currentTimeMillis();
                    note.noteHead = ((EditText) viewProps.findViewById(R.id.edtPropsTitle)).getText().toString();
                    note.isPinned = ((CheckBox) viewProps.findViewById(R.id.cbxPropsPinned)).isChecked();
                    note.enableMarkdown = ((CheckBox) viewProps.findViewById(R.id.cbxPropsMarkdown)).isChecked();
                    db.noteDao().updateNote(note);
                    runOnUiThread(() -> {
                        markChanges(isChangeMade);
                        if (!isInEditMode) setEditMode(false);  // if user changed markdown option, this will update that
                        adProps.dismiss();
                    });
                }).start();
            });
        } else if (itemId == R.id.mnuNoteViewDelete || itemId == R.id.mnuNoteEditDelete) {
            adDelete.show();
        } else if (itemId == R.id.mnuNoteEditStyle) {
            adStyle.show();
        } else if (itemId == R.id.mnuNoteViewShare) {
            if (sp.getBoolean("enable_file_share", false)) {
                adShare.show();
            } else {
                shareAsText();
            }
        } else if (itemId == R.id.mnuNoteEditDiscard) {
            if (isChangeMade) adDiscard.show();
            else
                Toast.makeText(this, R.string.note_discard_changes_no_need, Toast.LENGTH_SHORT).show();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        // update widgets
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getApplicationContext());
        int[] nbids = appWidgetManager.getAppWidgetIds(new ComponentName(getApplicationContext(), NoteButtonWidget.class));
        int[] npids = appWidgetManager.getAppWidgetIds(new ComponentName(getApplicationContext(), NotePreviewWidget.class));

        if (nbids.length > 0) {
            Intent ubw = new Intent(getApplicationContext(), NoteButtonWidget.class);
            ubw.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            ubw.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, nbids);
            sendBroadcast(ubw);
        }

        if (npids.length > 0) {
            Intent upw = new Intent(getApplicationContext(), NotePreviewWidget.class);
            upw.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            upw.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, npids);
            sendBroadcast(upw);
        }

        ab.setTitle(note.noteHead);

        checkClose(false);
        super.onPause();
    }

    private void setNoteStyle(int which) {
        if (which < 0 || which > 1) return;
        if (note.noteStyle != which) {
            ab.setTitle("*" + note.noteHead);
            isChangeMade = true;
        }
        note.noteStyle = which;
        updateNoteStyle();
    }

    private void updateNoteStyle() {
        switch (note.noteStyle) {
            case 0:
            case 2:
                binding.edtNoteBody.setTypeface(Typeface.SANS_SERIF);
                break;
            case 1:
                binding.edtNoteBody.setTypeface(Typeface.SERIF);
                break;
        }
    }

    private void saveChanges() {
        // save text
        shouldCheckNewNoteImmediateClose = false;
        note.noteBody = binding.edtNoteBody.getText().toString();
        note.lastModTime = System.currentTimeMillis();
        new Thread(() -> {
            db.noteDao().updateNote(note);
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), getString(R.string.note_saved), Toast.LENGTH_SHORT).show());
        }).start();
        isChangeMade = false;
        ab.setTitle(note.noteHead);
        String lastModTimeFormat = DateFormat.getLongDateFormat(NoteActivity.this).format(new Date(note.lastModTime))
                + " " + DateFormat.getTimeFormat(NoteActivity.this).format(new Date(note.lastModTime));
        binding.tvwNoteDateDataDisp.setText(getString(R.string.note_last_mod, lastModTimeFormat));
    }

    private void markChanges(boolean isChanged) {
        if (isChanged) {
            ab.setTitle("*" + note.noteHead);
            isChangeMade = true;
        } else {
            ab.setTitle(note.noteHead);
            isChangeMade = false;
        }
    }

    private void setEditMode(boolean editMode) {
        isInEditMode = editMode;
        binding.edtNoteBody.setKeyListener(editMode ? kl : null);
        if (editMode) {
            binding.edtNoteBody.setTextIsSelectable(true);
            binding.edtNoteBody.setText(note.noteBody);
            isChangeMade = false;
            ab.setTitle(note.noteHead);
            binding.edtNoteBody.requestFocus();
            InputMethodManager imm = (InputMethodManager)getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.showSoftInput(binding.edtNoteBody, InputMethodManager.SHOW_IMPLICIT);
        } else {
            if (note.enableMarkdown) {
                binding.edtNoteBody.setTextIsSelectable(false);
                getMarkwon().setMarkdown(binding.edtNoteBody, note.noteBody);
            } else {
                binding.edtNoteBody.setTextIsSelectable(true);
                binding.edtNoteBody.setText(note.noteBody);
            }
            InputMethodManager imm = (InputMethodManager)getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(binding.edtNoteBody.getWindowToken(), 0);
        }
        binding.edtNoteBody.setCursorVisible(editMode);
        invalidateOptionsMenu();
    }

    private void checkClose(boolean closeAfter) {
        if (isInEditMode) {
            if (shouldCheckNewNoteImmediateClose) {
                if (binding.edtNoteBody.getText().length() == 0) {
                    if (closeAfter) finish();
                    return;
                }
                shouldCheckNewNoteImmediateClose = false;
            }
            if (isChangeMade) saveChanges();
            setEditMode(false);
        } else {
            if (closeAfter) finish();
        }
    }

    @Override
    public void onBackPressed() {
        checkClose(true);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right);
    }

    private Markwon getMarkwon() {
        if (markwon == null) {
            markwon = Markwon.builder(this)
                    .usePlugin(SoftBreakAddsNewLinePlugin.create())
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(TablePlugin.create(this))
                    .usePlugin(TaskListPlugin.create(this))
                    .usePlugin(MarkdownDisableLinkPlugin.create(this))
                    .build();
        }
        return markwon;
    }
}