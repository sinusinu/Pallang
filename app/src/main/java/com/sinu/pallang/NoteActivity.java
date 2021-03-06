/*
 * Copyright (c) 2020 sinu
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
import android.text.InputType;
import android.text.Layout;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.method.KeyListener;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Date;

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
    View viewProps;
    View viewShare;

    // the note that this instance is editing
    PallangNote note;

    // variable for storing EditText's default KeyListener
    KeyListener kl;

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
                    if (!isChangeMade) ab.setTitle("*" + ab.getTitle());
                    isChangeMade = true;
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
                                             getString(R.string.note_menu_style_serif),
                                             getString(R.string.note_menu_style_monospace) },
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

        updateNoteStyle();

        setDoubleTapListener();

        String textSize = sp.getString("text_size", "medium");
        float textSizeF;
        switch (textSize) {
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
        }
        binding.edtNoteBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeF);

        // display update has been moved to onResume
        /*
        ab.setTitle(note.noteHead);
        if (openInEditMode) ab.setTitle("*" + ab.getTitle());
        binding.edtNoteBody.setText(note.noteBody);
        String lastModTimeFormat = DateFormat.getLongDateFormat(NoteActivity.this).format(new Date(note.lastModTime))
                           + " " + DateFormat.getTimeFormat(NoteActivity.this).format(new Date(note.lastModTime));
        binding.tvwNoteDateDataDisp.setText(getString(R.string.note_last_mod, lastModTimeFormat));
        */
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
                ab.setTitle((isChangeMade ? "*" : "") + note.noteHead);
                binding.edtNoteBody.setText(note.noteBody);
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
        switch (item.getItemId()) {
            case android.R.id.home:
                checkClose(true);
                break;
            case R.id.mnuNoteViewEdit:
                Layout layout = binding.edtNoteBody.getLayout();
                binding.edtNoteBody.setSelection(layout.getOffsetForHorizontal(layout.getLineForVertical(binding.svNoteScroller.getScrollY()), 0));
                setEditMode(true);
                break;
            case R.id.mnuNoteEditSave:
                if (isChangeMade) saveChanges();
                setEditMode(false);
                break;
            case R.id.mnuNoteEditProps:
                // populate props dialog
                ((EditText)viewProps.findViewById(R.id.edtPropsTitle)).setText(note.noteHead);
                ((CheckBox)viewProps.findViewById(R.id.cbxPropsPinned)).setChecked(note.isPinned);
                ((TextView)viewProps.findViewById(R.id.tvwPropsSize)).setText(getString(R.string.prop_len_format,
                        Util.getGraphemeLength(binding.edtNoteBody.getText().toString())));
                String creationTimeFormat = DateFormat.getLongDateFormat(NoteActivity.this).format(new Date(note.createTime))
                        + " " + DateFormat.getTimeFormat(NoteActivity.this).format(new Date(note.createTime));
                ((TextView)viewProps.findViewById(R.id.tvwPropsCreation)).setText(creationTimeFormat);
                String lastModTimeFormat = DateFormat.getLongDateFormat(NoteActivity.this).format(new Date(note.lastModTime))
                        + " " + DateFormat.getTimeFormat(NoteActivity.this).format(new Date(note.lastModTime));
                ((TextView)viewProps.findViewById(R.id.tvwPropsLastMod)).setText(lastModTimeFormat);

                adProps.show();
                adProps.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener((view) -> {
                    if (((EditText)viewProps.findViewById(R.id.edtPropsTitle)).getText().toString().trim().length() == 0) {
                        runOnUiThread(() -> Toast.makeText(getApplicationContext(), R.string.note_error_title_empty, Toast.LENGTH_SHORT).show());
                        return;
                    }
                    new Thread(() -> {
                        // if user changed the title, update last mod time (pinning shouldn't update that)
                        if (!note.noteHead.equals(((EditText)viewProps.findViewById(R.id.edtPropsTitle)).getText().toString())) note.lastModTime = System.currentTimeMillis();
                        note.noteHead = ((EditText)viewProps.findViewById(R.id.edtPropsTitle)).getText().toString();
                        note.isPinned = ((CheckBox)viewProps.findViewById(R.id.cbxPropsPinned)).isChecked();
                        db.noteDao().updateNote(note);
                        runOnUiThread(() -> {
                            ab.setTitle((isChangeMade ? "*" : "") + note.noteHead);
                            adProps.dismiss();
                        });
                    }).start();
                });
                break;
            case R.id.mnuNoteViewDelete:
            case R.id.mnuNoteEditDelete:
                adDelete.show();
                break;
            case R.id.mnuNoteEditStyle:
                adStyle.show();
                break;
            case R.id.mnuNoteViewShare:
                if (sp.getBoolean("enable_file_share", false)) {
                    adShare.show();
                } else {
                    shareAsText();
                }
                break;
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
        if (which < 0 || which > 2) return;
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
                binding.edtNoteBody.setTypeface(Typeface.SANS_SERIF);
                break;
            case 1:
                binding.edtNoteBody.setTypeface(Typeface.SERIF);
                break;
            case 2:
                binding.edtNoteBody.setTypeface(Typeface.MONOSPACE);
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

    private void setEditMode(boolean editMode) {
        isInEditMode = editMode;
        binding.edtNoteBody.setKeyListener(editMode ? kl : null);
        binding.edtNoteBody.setTextIsSelectable(true);
        if (editMode) {
            binding.edtNoteBody.requestFocus();
            InputMethodManager imm = (InputMethodManager)getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.showSoftInput(binding.edtNoteBody, InputMethodManager.SHOW_IMPLICIT);
        } else {
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
                    finish();
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
}