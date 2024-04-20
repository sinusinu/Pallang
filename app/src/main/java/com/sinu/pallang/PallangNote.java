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

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class PallangNote {
    @PrimaryKey @ColumnInfo(name = "note_id")
    public int noteId;

    @ColumnInfo(name = "note_head")
    public String noteHead;

    @ColumnInfo(name = "note_body")
    public String noteBody;

    @ColumnInfo(name = "create_time")
    public long createTime;

    @ColumnInfo(name = "last_mod_time")
    public long lastModTime;

    /**
     * 0 = Sans-serif, 1 = Serif, 2 = Monospace
     */
    @ColumnInfo(name = "note_style")
    public int noteStyle;

    @ColumnInfo(name = "is_pinned")
    public boolean isPinned;

    @ColumnInfo(name = "enable_markdown")
    public boolean enableMarkdown;

    /** Unused */
    @ColumnInfo(name = "is_recycled")
    public boolean isRecycled;
}
