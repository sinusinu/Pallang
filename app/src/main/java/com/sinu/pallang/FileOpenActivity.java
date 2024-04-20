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

import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SpinnerAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;
import androidx.preference.PreferenceManager;

import com.sinu.pallang.databinding.ActivityFileOpenBinding;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class FileOpenActivity extends AppCompatActivity {
    ActivityFileOpenBinding binding;
    PallangNoteDatabase db;
    SharedPreferences sp;

    int newNoteId;
    String newNoteHead;
    String newNoteBody;

    boolean isFileShared;
    boolean isSingle;
    ArrayList<Uri> uris;
    int uriIndex;

    String[] notesTitle;
    int[] notesId;
    int overwriteNoteIndex = 0;
    ArrayAdapter<String> aa;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFileOpenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sp = PreferenceManager.getDefaultSharedPreferences(this);
        db = PallangNoteDBHolder.getDatabase(this);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if ((action.equals(Intent.ACTION_SEND) || action.equals(Intent.ACTION_SEND_MULTIPLE)) && type.equals("text/plain")) {
            newNoteBody = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (newNoteBody != null && newNoteBody.trim().length() > 0) {
                // text
                isFileShared = false;
                isSingle = true;
                newNoteHead = getString(R.string.main_new_note_title);
                setOpenDialogState(newNoteBody);
            } else if (intent.getClipData() != null) {
                // files
                isFileShared = true;
                ClipData cd = intent.getClipData();
                uris = new ArrayList<>();
                uriIndex = 0;
                for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
                isSingle = uris.size() == 1;
                setOpenDialogState(uriIndex);
            } else {
                Toast.makeText(this, R.string.open_error_invalid_call, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        } else {
            Toast.makeText(this, R.string.open_error_invalid_call, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        new Thread(() -> {
            if (db.noteDao().getNoteCount() == 0) {
                runOnUiThread(() -> {
                    binding.spiFileNotes.setVisibility(View.GONE);
                    binding.rdoFileNewNote.setChecked(true);
                    binding.rdoFileOverwrite.setChecked(false);
                    binding.rdoFileOverwrite.setEnabled(false);
                });
            } else {
                ArrayList<PallangNote> notes = (ArrayList<PallangNote>)db.noteDao().getNotes(sp.getInt("sort_mode", 0), sp.getBoolean("sort_asc", false));
                notesId = new int[notes.size()];
                notesTitle = new String[notes.size()];
                for (int i = 0; i < notes.size(); i++) {
                    notesId[i] = notes.get(i).noteId;
                    notesTitle[i] = notes.get(i).noteHead;
                }

                runOnUiThread(() -> {
                    aa = new ArrayAdapter<>(FileOpenActivity.this, android.R.layout.simple_spinner_dropdown_item, notesTitle);
                    binding.spiFileNotes.setAdapter(aa);
                    binding.spiFileNotes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { overwriteNoteIndex = notesId[position]; }
                        @Override public void onNothingSelected(AdapterView<?> parent) { overwriteNoteIndex = -1; }
                    });
                });
            }
        }).start();

        binding.btnFileOpen.setOnClickListener((view) -> {
            if (binding.rdoFileNewNote.isChecked()) {
                Thread thrCreateNewNote = new Thread(() -> {
                    long currentTime = System.currentTimeMillis();
                    newNoteId = db.noteDao().getLastNoteId() + 1;

                    PallangNote n = new PallangNote();
                    n.noteId = newNoteId;
                    n.noteHead = newNoteHead;
                    n.noteBody = newNoteBody;
                    n.createTime = currentTime;
                    n.lastModTime = currentTime;
                    n.noteStyle = 0;
                    n.isPinned = false;
                    db.noteDao().insertNote(n);
                });

                thrCreateNewNote.start();
                try {
                    thrCreateNewNote.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), e.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (isSingle) {
                    // launch NoteActivity with created note
                    Intent ni = new Intent(FileOpenActivity.this, NoteActivity.class);
                    ni.putExtra("noteId", newNoteId);
                    ni.putExtra("openInEditMode", true);
                    startActivity(ni, ActivityOptionsCompat.makeCustomAnimation(FileOpenActivity.this, R.anim.slide_in_right, R.anim.no_anim).toBundle());
                    finish();
                } else {
                    if (uris.size() - 1 != uriIndex) {
                        // show next file
                        uriIndex++;
                        setOpenDialogState(uriIndex);
                    } else {
                        Toast.makeText(this, R.string.open_multiple_finished, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            } else if (binding.rdoFileOverwrite.isChecked()) {
                if (overwriteNoteIndex == -1) return;

                Thread thrOverwriteNote = new Thread(() -> {
                    PallangNote noteToOverwrite = db.noteDao().getNote(overwriteNoteIndex);
                    long currentTime = System.currentTimeMillis();

                    noteToOverwrite.noteBody = newNoteBody;
                    noteToOverwrite.lastModTime = currentTime;

                    db.noteDao().updateNote(noteToOverwrite);
                });

                thrOverwriteNote.start();
                try {
                    thrOverwriteNote.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), e.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (isSingle) {
                    // launch NoteActivity with created note
                    Intent ni = new Intent(FileOpenActivity.this, NoteActivity.class);
                    ni.putExtra("noteId", overwriteNoteIndex);
                    ni.putExtra("openInEditMode", true);
                    startActivity(ni, ActivityOptionsCompat.makeCustomAnimation(FileOpenActivity.this, R.anim.slide_in_right, R.anim.no_anim).toBundle());
                    finish();
                } else {
                    if (uris.size() - 1 != uriIndex) {
                        // show next file
                        uriIndex++;
                        setOpenDialogState(uriIndex);
                    } else {
                        Toast.makeText(this, R.string.open_multiple_finished, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            }
        });

        binding.rdoFileOverwrite.setOnCheckedChangeListener(((buttonView, isChecked) -> {
            binding.spiFileNotes.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        }));
    }

    private void setOpenDialogState(int index) {
        try {
            InputStream is = getContentResolver().openInputStream(uris.get(index));
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();

            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            newNoteBody = sb.toString();

            br.close();
            isr.close();
            is.close();
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), R.string.open_error_file_read_error, Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            finish();
            return;
        }

        newNoteHead = getFileName(uris.get(index));
        if(newNoteHead.endsWith(".txt")) newNoteHead = newNoteHead.substring(0, newNoteHead.lastIndexOf('.'));

        binding.rdoFileNewNote.setChecked(true);
        binding.rdoFileOverwrite.setChecked(false);

        binding.tvwFileTitle.setText(newNoteHead);
        binding.tvwFilePreview.setText(newNoteBody);
        binding.tvwFileDesc.setText(getString(R.string.open_desc, getString(R.string.open_desc_file)));

        binding.btnFileCancel.setText((uris.size() > 1) ? R.string.open_skip_this_file : R.string.cancel);
        binding.btnFileCancel.setOnClickListener((view) -> {
            if (uris.size() - 1 != uriIndex) {
                // show next file
                uriIndex++;
                setOpenDialogState(uriIndex);
            } else {
                finish();
            }
        });
    }

    private void setOpenDialogState(String text) {
        newNoteBody = text;

        binding.tvwFileTitle.setText(R.string.open_title_text);
        binding.tvwFilePreview.setText(newNoteBody);
        binding.tvwFileDesc.setText(getString(R.string.open_desc, getString(R.string.open_desc_text)));

        binding.btnFileCancel.setOnClickListener((view) -> {
            finish();
        });
    }

    // from https://stackoverflow.com/questions/5568874/how-to-extract-the-file-name-from-uri-returned-from-intent-action-get-content
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}