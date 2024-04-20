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
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import androidx.annotation.NonNull;
import org.commonmark.node.Link;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.SpanFactory;

public class MarkdownDisableLinkPlugin extends AbstractMarkwonPlugin {
    Context context;

    private MarkdownDisableLinkPlugin(Context context) {
        this.context = context;
    }

    public static MarkdownDisableLinkPlugin create(Context context) {
        return new MarkdownDisableLinkPlugin(context);
    }

    @Override
    public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
        // do nothing when clicking links
        builder.linkResolver((view, link) -> {});
    }

    @Override
    public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
        // hide link styles (underline, accent color)
        final SpanFactory original = builder.getFactory(Link.class);
        if (original != null) {
            builder.setFactory(Link.class, (configuration, props) -> new Object[] {
                    new DisabledLinkSpan(context),
                    original.getSpans(configuration, props)
            });
        }
    }

    public static class DisabledLinkSpan extends CharacterStyle implements UpdateAppearance {
        Context context;

        public DisabledLinkSpan(Context context) {
            this.context = context;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setUnderlineText(false);
            tp.setColor(context.getResources().getColor(R.color.colorText));
        }
    }
}
