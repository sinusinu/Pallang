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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

public class NoteButtonWidget extends AppWidgetProvider {
    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        PallangNote note = NoteButtonWidgetConfigureActivity.loadNotePref(context, appWidgetId);

        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.note_button_widget);
        if (note == null) {
            views.setTextViewText(R.id.tvwNbwTitle, context.getString(R.string.widget_note_not_found));
            Intent changeNote = new Intent(context, NoteButtonWidgetConfigureActivity.class);
            changeNote.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            PendingIntent piChangeNote = PendingIntent.getActivity(context, appWidgetId, changeNote, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            views.setOnClickPendingIntent(R.id.flNbwWidget, piChangeNote);
        } else {
            Intent openNote = new Intent(context, NoteActivity.class);
            openNote.putExtra("noteId", note.noteId);
            openNote.putExtra("openInEditMode", false);
            PendingIntent piOpenNote = PendingIntent.getActivity(context, appWidgetId, openNote, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            views.setOnClickPendingIntent(R.id.flNbwWidget, piOpenNote);
            views.setTextViewText(R.id.tvwNbwTitle, note.noteHead);
        }

        String theme = PreferenceManager.getDefaultSharedPreferences(context).getString("theme", "auto");
        if (theme.equals("light")) {
            views.setInt(R.id.tvwNbwTitle, "setBackgroundColor", context.getResources().getColor(R.color.colorWidgetBackgroundLight));
            views.setInt(R.id.tvwNbwTitle, "setTextColor", context.getResources().getColor(R.color.colorWidgetTextLight));
        } else if (theme.equals("dark")) {
            views.setInt(R.id.tvwNbwTitle, "setBackgroundColor", context.getResources().getColor(R.color.colorWidgetBackgroundDark));
            views.setInt(R.id.tvwNbwTitle, "setTextColor", context.getResources().getColor(R.color.colorWidgetTextDark));
        } else {
            switch (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) {
                case Configuration.UI_MODE_NIGHT_NO:
                case Configuration.UI_MODE_NIGHT_UNDEFINED:
                    views.setInt(R.id.tvwNbwTitle, "setBackgroundColor", context.getResources().getColor(R.color.colorWidgetBackgroundLight));
                    views.setInt(R.id.tvwNbwTitle, "setTextColor", context.getResources().getColor(R.color.colorWidgetTextLight));
                    break;
                case Configuration.UI_MODE_NIGHT_YES:
                    views.setInt(R.id.tvwNbwTitle, "setBackgroundColor", context.getResources().getColor(R.color.colorWidgetBackgroundDark));
                    views.setInt(R.id.tvwNbwTitle, "setTextColor", context.getResources().getColor(R.color.colorWidgetTextDark));
                    break;
            }
        }

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // When the user deletes the widget, delete the preference associated with it.
        for (int appWidgetId : appWidgetIds) {
            NoteButtonWidgetConfigureActivity.deleteNotePref(context, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

