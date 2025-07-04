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

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.sinu.pallang.databinding.ActivitySettingsBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {
    ActivitySettingsBinding binding;

    private final int SAF_IMPORT = 0;
    private final int SAF_EXPORT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setSupportActionBar(binding.tbrSettingsToolbar);
        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);
        ab.setHomeAsUpIndicator(R.drawable.ic_back_24_ctrlcolor);
        ab.setTitle(R.string.settings_title);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.flSettings, new SettingsFragment())
                .commit();
    }

    protected void updateTheme() {
        String newTheme = PreferenceManager.getDefaultSharedPreferences(this).getString("theme", "auto");
        switch (newTheme) {
            case "auto":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }
        recreate();
    }

    public void importNotes() {
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(R.string.settings_import_dialog_title)
                .setMessage(R.string.settings_import_dialog_desc)
                .setPositiveButton(R.string.yes, (di, i) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    startActivityForResult(intent, SAF_IMPORT);
                })
                .setNegativeButton(R.string.no, null)
                .create();
        d.show();
    }

    public void exportNotes() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "pallang_export_" + sdf.format(new Date()) + ".json");
        startActivityForResult(intent, SAF_EXPORT);
    }

    /** @noinspection CallToPrintStackTrace*/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SAF_IMPORT && resultCode == RESULT_OK) {
            Uri uri = null;
            if (data != null) {
                uri = data.getData();
            } else {
                Toast.makeText(this, R.string.settings_import_error_io, Toast.LENGTH_SHORT).show();
                return;
            }

            int error = 0;

            List<PallangNote> notes = new ArrayList<>();
            StringBuilder sb = new StringBuilder();

            try (InputStream inputStream = getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            } catch (IOException e) {
                error = 1;
                e.printStackTrace();
            }

            if (error == 0) {
                try {
                    var jo = new JSONArray(sb.toString());
                    for (int i = 0; i < jo.length(); i++) {
                        var noteJson = jo.getJSONObject(i);
                        var note = new PallangNote();
                        note.noteId = noteJson.getInt("noteId");
                        note.noteHead = new String(Base64.decode(noteJson.getString("noteHead"), Base64.DEFAULT));
                        note.noteBody = new String(Base64.decode(noteJson.getString("noteBody"), Base64.DEFAULT));
                        note.createTime = noteJson.getLong("createTime");
                        note.lastModTime = noteJson.getLong("lastModTime");
                        note.noteStyle = noteJson.getInt("noteStyle");
                        note.isPinned = noteJson.getBoolean("isPinned");
                        note.enableMarkdown = noteJson.getBoolean("enableMarkdown");
                        note.isRecycled = false;
                        notes.add(note);
                    }
                } catch (Exception e) {
                    error = 2;
                    e.printStackTrace();
                }
            }

            final int errorResult = error;

            var t = new Thread(() -> {
                if (errorResult == 0) {
                    PallangNoteDatabase db = PallangNoteDBHolder.getDatabase(getApplicationContext());
                    db.noteDao().clearNotes();
                    for (var note : notes) db.noteDao().insertNote(note);
                }

                runOnUiThread(() -> {
                    if (errorResult == 1) {
                        Toast.makeText(this, R.string.settings_import_error_io, Toast.LENGTH_SHORT).show();
                    } else if (errorResult == 2) {
                        Toast.makeText(this, R.string.settings_import_error_parse, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.settings_import_success, Toast.LENGTH_SHORT).show();
                    }
                });
            });
            t.start();
        }
        if (requestCode == SAF_EXPORT && resultCode == RESULT_OK) {
            Uri uri = null;
            if (data != null) {
                uri = data.getData();
            } else {
                Toast.makeText(this, R.string.settings_export_error_generic, Toast.LENGTH_SHORT).show();
                return;
            }
            final Uri fUri = uri;

            binding.clSettingsWait.setVisibility(View.VISIBLE);

            var t = new Thread(() -> {
                // to give user a visual cue properly
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}

                boolean error = false;

                StringBuilder sb = new StringBuilder();
                try {
                    PallangNoteDatabase db = PallangNoteDBHolder.getDatabase(getApplicationContext());
                    var notes = db.noteDao().getNotes(0, true);
                    sb.append("[");
                    for (var note : notes) {
                        sb.append("{")
                                .append("\"noteId\":").append(note.noteId).append(",")
                                .append("\"noteHead\":\"").append(new String(Base64.encode(note.noteHead.getBytes(), Base64.NO_WRAP))).append("\",")
                                .append("\"noteBody\":\"").append(new String(Base64.encode(note.noteBody.getBytes(), Base64.NO_WRAP))).append("\",")
                                .append("\"createTime\":").append(note.createTime).append(",")
                                .append("\"lastModTime\":").append(note.lastModTime).append(",")
                                .append("\"noteStyle\":").append(note.noteStyle).append(",")
                                .append("\"isPinned\":").append(note.isPinned ? "true" : "false").append(",")
                                .append("\"enableMarkdown\":").append(note.enableMarkdown ? "true" : "false");
                        sb.append("},");
                    }
                    sb.setLength(sb.length() - 1);
                    sb.append("]");
                } catch (Exception e) {
                    error = true;
                    e.printStackTrace();
                }

                if (!error) {
                    try {
                        ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(fUri, "w");
                        FileOutputStream fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
                        fileOutputStream.write(sb.toString().getBytes());
                        fileOutputStream.close();
                        pfd.close();
                    } catch (IOException e) {
                        error = true;
                        e.printStackTrace();
                        return;
                    }
                }

                final boolean errorResult = error;

                runOnUiThread(() -> {
                    if (errorResult) {
                        Toast.makeText(SettingsActivity.this, R.string.settings_export_error_generic, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(SettingsActivity.this, R.string.settings_export_success, Toast.LENGTH_SHORT).show();
                    }
                    binding.clSettingsWait.setVisibility(View.GONE);
                });
            });
            t.start();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return false;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right);
    }
}