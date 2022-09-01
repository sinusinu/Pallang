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
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.sinu.pallang.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    final int REQ_SETTINGS = 1;

    ActivityMainBinding binding;
    PallangNoteDatabase db;
    SharedPreferences sp;

    ActionBar ab;

    ArrayList<PallangNote> notes;
    PallangNoteListAdapter adapter;
    int lastNoteId = -1;

    Runnable rUpdateNotes;
    boolean isUpdatingNotes = false;

    View llProps;
    AlertDialog adProps;

    // user is selecting notes if this list is not empty
    ArrayList<PallangNote> selectedNotes = new ArrayList<>();

    // 0 - by last modified date, 1 - by created date, 2 - by name
    int selectedSortMode;

    final View.OnClickListener rvItemClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            int i = binding.rvMainNotes.getChildAdapterPosition(view);
            if (selectedNotes.size() > 0) {
                if (selectedNotes.contains(notes.get(i))) selectedNotes.remove(notes.get(i));
                else selectedNotes.add(notes.get(i));
                if (selectedNotes.size() == 0) {
                    ab.setTitle(getString(R.string.main_title));
                } else {
                    ab.setTitle(getString(R.string.main_select_title, selectedNotes.size()));
                }
                invalidateOptionsMenu();
                adapter.notifyDataSetChanged();
            } else {
                Intent intent = new Intent(MainActivity.this, NoteActivity.class);
                intent.putExtra("noteId", notes.get(i).noteId);
                intent.putExtra("openInEditMode", false);
                startActivity(intent, ActivityOptionsCompat.makeCustomAnimation(MainActivity.this, R.anim.slide_in_right, R.anim.no_anim).toBundle());
            }
        }
    };

    final View.OnLongClickListener rvItemLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            if (selectedNotes.size() == 0) {
                int i = binding.rvMainNotes.getChildAdapterPosition(view);
                selectedNotes.add(notes.get(i));
                ab.setTitle(getString(R.string.main_select_title, selectedNotes.size()));
                invalidateOptionsMenu();
                adapter.notifyDataSetChanged();
            }
            return true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sp = PreferenceManager.getDefaultSharedPreferences(this);
        db = PallangNoteDBHolder.getDatabase(getApplicationContext());

        setThemeByPref();

        notes = new ArrayList<>();

        setSupportActionBar(binding.tbrMainToolbar);
        ab = getSupportActionBar();

        View llProps = getLayoutInflater().inflate(R.layout.dialog_properties, null);

        binding.rvMainNotes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PallangNoteListAdapter(notes);
        binding.rvMainNotes.setAdapter(adapter);

        TooltipCompat.setTooltipText(binding.fabMainNewNote, getString(R.string.main_new_note));
        binding.fabMainNewNote.setOnClickListener(view -> {
            if (selectedNotes.size() > 0) {
                selectedNotes.clear();
                ab.setTitle(getString(R.string.main_title));
                invalidateOptionsMenu();
                adapter.notifyDataSetChanged();
            }

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

                Thread thrCreateNewNote = new Thread(() -> {
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
                    db.noteDao().insertNote(n);
                });

                thrCreateNewNote.start();
                try {
                    thrCreateNewNote.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), e.getClass().getSimpleName(), Toast.LENGTH_SHORT) .show();
                    return;
                }

                // launch NoteActivity with created note
                Intent intent = new Intent(MainActivity.this, NoteActivity.class);
                intent.putExtra("noteId", newNoteId);
                intent.putExtra("openInEditMode", true);
                startActivity(intent, ActivityOptionsCompat.makeCustomAnimation(MainActivity.this, R.anim.slide_in_right, R.anim.no_anim).toBundle());

                adProps.dismiss();
            });
        });

        AlertDialog.Builder abNewNoteProp = new AlertDialog.Builder(this);
        abNewNoteProp.setTitle(R.string.main_new_note);
        llProps.findViewById(R.id.llPropsInfo).setVisibility(View.GONE);
        abNewNoteProp.setView(llProps);
        abNewNoteProp.setPositiveButton(R.string.cancel, null);
        // to override dialog closing behavior, OnClickListener for button must be set when dialog is shown
        abNewNoteProp.setPositiveButton(R.string.prop_save_changes, null);
        adProps = abNewNoteProp.create();

        rUpdateNotes = () -> {
            notes.clear();
            List<PallangNote> dbNotes = db.noteDao().getNotes(sp.getInt("sort_mode", 0), sp.getBoolean("sort_asc", false));
            notes.addAll(dbNotes);
            lastNoteId = (notes.size() == 0) ? -1 : db.noteDao().getLastNoteId();
            runOnUiThread(() -> {
                binding.tvwMainNoNotes.setVisibility((notes.size() == 0) ? View.VISIBLE : View.GONE);
                adapter.notifyDataSetChanged();
            });
            isUpdatingNotes = false;
        };
    }

    @Override
    public void onBackPressed() {
        if (selectedNotes.size() > 0) {
            selectedNotes.clear();
            ab.setTitle(getString(R.string.main_title));
            invalidateOptionsMenu();
            adapter.notifyDataSetChanged();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (selectedNotes.size() > 0) {
            getMenuInflater().inflate(R.menu.menu_main_select, menu);
            boolean isEverythingPinned = isEveryNoteSelectedIsPinned();
            MenuItem mnuMainSelectPin = menu.findItem(R.id.mnuMainSelectPin);
            if (isEverythingPinned) {
                mnuMainSelectPin.setIcon(R.drawable.ic_unpin_24_ctrlcolor);
                mnuMainSelectPin.setTitle(R.string.main_menu_unpin);
            } else {
                mnuMainSelectPin.setIcon(R.drawable.ic_pin_24_ctrlcolor);
                mnuMainSelectPin.setTitle(R.string.main_menu_pin);
            }
        } else {
            getMenuInflater().inflate(R.menu.menu_main, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.mnuMainSelectDelete) {
            AlertDialog.Builder abDeleteConfirm = new AlertDialog.Builder(this);
            abDeleteConfirm.setMessage(R.string.main_delete_confirm);
            abDeleteConfirm.setNegativeButton(R.string.no, null);
            abDeleteConfirm.setPositiveButton(R.string.yes, (dialog, which) -> {
                new Thread(() -> {
                    for (PallangNote n : selectedNotes) {
                        db.noteDao().deleteNote(n);
                    }
                    selectedNotes.clear();
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), getString(R.string.main_deleted), Toast.LENGTH_SHORT).show();
                        ab.setTitle(getString(R.string.main_title));
                        invalidateOptionsMenu();
                        updateNotes();
                    });
                }).start();
            });
            abDeleteConfirm.show();
        } else if (itemId == R.id.mnuMainSettings) {
            startActivityForResult(
                    new Intent(this, SettingsActivity.class),
                    REQ_SETTINGS,
                    ActivityOptionsCompat.makeCustomAnimation(MainActivity.this, R.anim.slide_in_right, R.anim.no_anim).toBundle()
            );
        } else if (itemId == R.id.mnuMainSort) {
            selectedSortMode = sp.getInt("sort_mode", 0);
            AlertDialog.Builder abSort = new AlertDialog.Builder(this);
            abSort.setTitle(R.string.main_menu_sort);
            abSort.setSingleChoiceItems(new CharSequence[]{
                    getString(R.string.main_sort_last_mod_time),
                    getString(R.string.main_sort_create_time),
                    getString(R.string.main_sort_title)
            }, selectedSortMode, (dialog, which) -> {
                selectedSortMode = which;
            });
            abSort.setPositiveButton(R.string.main_sort_asc, (dialog, which) -> {
                sp.edit().putInt("sort_mode", selectedSortMode)
                        .putBoolean("sort_asc", true)
                        .apply();
                updateNotes();
            });
            abSort.setNegativeButton(R.string.main_sort_desc, (dialog, which) -> {
                sp.edit().putInt("sort_mode", selectedSortMode)
                        .putBoolean("sort_asc", false)
                        .apply();
                updateNotes();
            });
            abSort.show();
        } else if (itemId == R.id.mnuMainSelectPin) {
            boolean isEverythingPinned = isEveryNoteSelectedIsPinned();
            if (isEverythingPinned) {
                // unpin all
                new Thread(() -> {
                    for (PallangNote n : selectedNotes) {
                        n.isPinned = false;
                        db.noteDao().updateNote(n);
                    }
                    selectedNotes.clear();
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), getString(R.string.main_unpinned), Toast.LENGTH_SHORT).show();
                        ab.setTitle(getString(R.string.main_title));
                        invalidateOptionsMenu();
                        updateNotes();
                    });
                }).start();
            } else {
                // pin all
                new Thread(() -> {
                    for (PallangNote n : selectedNotes) {
                        n.isPinned = true;
                        db.noteDao().updateNote(n);
                    }
                    selectedNotes.clear();
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), getString(R.string.main_pinned), Toast.LENGTH_SHORT).show();
                        ab.setTitle(getString(R.string.main_title));
                        invalidateOptionsMenu();
                        updateNotes();
                    });
                }).start();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        // update widgets
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getApplicationContext());
        int[] nbIds = appWidgetManager.getAppWidgetIds(new ComponentName(getApplicationContext(), NoteButtonWidget.class));
        int[] npIds = appWidgetManager.getAppWidgetIds(new ComponentName(getApplicationContext(), NotePreviewWidget.class));

        if (nbIds.length > 0) {
            Intent ubw = new Intent(getApplicationContext(), NoteButtonWidget.class);
            ubw.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            ubw.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, nbIds);
            sendBroadcast(ubw);
        }

        if (npIds.length > 0) {
            Intent upw = new Intent(getApplicationContext(), NotePreviewWidget.class);
            upw.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            upw.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, npIds);
            sendBroadcast(upw);
        }

        super.onPause();
    }

    private boolean isEveryNoteSelectedIsPinned() {
        if (selectedNotes.size() == 0) return false;
        boolean ret = true;
        for (PallangNote n : selectedNotes) {
            if (!n.isPinned) {
                ret = false;
                break;
            }
        }
        return ret;
    }

    private void setThemeByPref() {
        String newTheme = sp.getString("theme", "auto");
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotes();
    }

    private void updateNotes() {
        if (isUpdatingNotes) return;
        isUpdatingNotes = true;
        new Thread(rUpdateNotes).start();
    }

    public class PallangNoteListAdapter extends RecyclerView.Adapter<PallangNoteListAdapter.PallangNoteListViewHolder> {
        List<PallangNote> notes;

        public class PallangNoteListViewHolder extends RecyclerView.ViewHolder {
            public LinearLayout v;

            public PallangNoteListViewHolder(LinearLayout v) {
                super(v);
                this.v = v;
            }
        }

        public PallangNoteListAdapter(List<PallangNote> notes) {
            this.notes = notes;
        }

        @NonNull @Override
        public PallangNoteListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout v = (LinearLayout) LayoutInflater.from(parent.getContext()).inflate(R.layout.list_note, parent, false);
            v.setOnClickListener(rvItemClickListener);
            v.setOnLongClickListener(rvItemLongClickListener);
            return new PallangNoteListViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PallangNoteListViewHolder holder, int position) {
            ((TextView)holder.v.findViewById(R.id.tvwNoteListTitle)).setText(notes.get(position).noteHead);
            long lastModTime = notes.get(position).lastModTime;
            String lastModTimeFormat = DateFormat.getMediumDateFormat(MainActivity.this).format(new Date(notes.get(position).lastModTime));
            ((TextView)holder.v.findViewById(R.id.tvwNoteListTime)).setText(lastModTimeFormat);
            if (selectedNotes.size() > 0) {
                ((CheckBox)holder.v.findViewById(R.id.cbxNoteSelect)).setVisibility(View.VISIBLE);
                ((CheckBox)holder.v.findViewById(R.id.cbxNoteSelect)).setChecked(selectedNotes.contains(notes.get(position)));
            } else {
                ((CheckBox)holder.v.findViewById(R.id.cbxNoteSelect)).setVisibility(View.GONE);
                ((CheckBox)holder.v.findViewById(R.id.cbxNoteSelect)).setChecked(false);
            }
            ((ImageView)holder.v.findViewById(R.id.ivwNotePin)).setVisibility(notes.get(position).isPinned ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getItemCount() {
            return notes.size();
        }
    }
}