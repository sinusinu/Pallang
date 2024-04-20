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

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PallangNoteDao {
    // wow this is atrocious
    @Query("SELECT * FROM " +
               "(SELECT * FROM notes " +
                   "ORDER BY " +
                       "CASE :asc " +
                           "WHEN 0 THEN " +
                               "CASE :sortBy " +
                                   "WHEN 0 THEN last_mod_time " +
                                   "WHEN 1 THEN create_time " +
                                   "WHEN 2 THEN note_head " +
                               "END " +
                       "END DESC, " +
                       "CASE :asc " +
                           "WHEN 1 THEN " +
                               "CASE :sortBy " +
                                   "WHEN 0 THEN last_mod_time " +
                                   "WHEN 1 THEN create_time " +
                                   "WHEN 2 THEN note_head " +
                               "END " +
                       "END ASC" +
               ") " +
               "ORDER BY " +
                   "is_pinned DESC")
    List<PallangNote> getNotes(int sortBy, boolean asc);

    @Query("SELECT * FROM notes WHERE note_id = :noteId")
    PallangNote getNote(int noteId);

    @Query("SELECT count(*) FROM notes")
    int getNoteCount();

    @Query("SELECT MAX(note_id) FROM notes")
    int getLastNoteId();

    @Insert
    void insertNote(PallangNote note);

    @Delete
    void deleteNote(PallangNote note);

    @Update
    void updateNote(PallangNote note);
}
