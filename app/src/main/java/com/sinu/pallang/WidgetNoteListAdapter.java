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

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Date;
import java.util.List;

public class WidgetNoteListAdapter extends RecyclerView.Adapter<WidgetNoteListAdapter.PallangNoteListForWidgetViewHolder> {
    Context context;
    List<PallangNote> notes;
    View.OnClickListener onClickListener;

    public static class PallangNoteListForWidgetViewHolder extends RecyclerView.ViewHolder {
        public LinearLayout v;

        public PallangNoteListForWidgetViewHolder(LinearLayout v) {
            super(v);
            this.v = v;
        }
    }

    public WidgetNoteListAdapter(Context context, List<PallangNote> notes, View.OnClickListener onClickListener) {
        this.context = context;
        this.notes = notes;
        this.onClickListener = onClickListener;
    }

    @NonNull
    @Override
    public PallangNoteListForWidgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout v = (LinearLayout) LayoutInflater.from(parent.getContext()).inflate(R.layout.list_note, parent, false);
        v.setOnClickListener(onClickListener);
        return new PallangNoteListForWidgetViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PallangNoteListForWidgetViewHolder holder, int position) {
        if (position == 0) {
            ((TextView) holder.v.findViewById(R.id.tvwNoteListTitle)).setText(R.string.widget_new_note);
            ((TextView) holder.v.findViewById(R.id.tvwNoteListTime)).setText("");
            ((CheckBox) holder.v.findViewById(R.id.cbxNoteSelect)).setVisibility(View.GONE);
            ((ImageView) holder.v.findViewById(R.id.ivwNotePin)).setVisibility(View.GONE);
        } else {
            ((TextView) holder.v.findViewById(R.id.tvwNoteListTitle)).setText(notes.get(position - 1).noteHead);
            long lastModTime = notes.get(position - 1).lastModTime;
            String lastModTimeFormat = DateFormat.getMediumDateFormat(context).format(new Date(notes.get(position - 1).lastModTime));
            ((TextView) holder.v.findViewById(R.id.tvwNoteListTime)).setText(lastModTimeFormat);
            ((CheckBox) holder.v.findViewById(R.id.cbxNoteSelect)).setVisibility(View.GONE);
            ((CheckBox) holder.v.findViewById(R.id.cbxNoteSelect)).setChecked(false);
            ((ImageView) holder.v.findViewById(R.id.ivwNotePin)).setVisibility(notes.get(position - 1).isPinned ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return notes.size() + 1;
    }
}
