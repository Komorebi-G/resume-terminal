/* Added for Resume Terminal, 2026-10-03.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.termux.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TerminalSessionClient;
import com.termux.terminal.TextStyle;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Pixel checks using the same Latin and CJK fonts shipped by the app. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TerminalRendererTest {
    private Typeface typeface;

    @Before public void setUp() throws Exception {
        File fonts = new File("../app/src/main/res/font");
        if (!fonts.isDirectory()) fonts = new File("app/src/main/res/font");
        Font primary = new Font.Builder(new File(fonts, "jetbrains_mono.ttf")).build();
        Font fallback = new Font.Builder(new File(fonts, "noto_sans_sc.otf")).build();
        typeface = new Typeface.CustomFallbackBuilder(new FontFamily.Builder(primary).build())
            .addCustomFallback(new FontFamily.Builder(fallback).build()).build();
    }

    private TerminalEmulator emulator(String text) {
        TerminalOutput output = new TerminalOutput() {
            public void write(byte[] data, int offset, int count) {}
            public void titleChanged(String oldTitle, String newTitle) {}
            public void onCopyTextToClipboard(String text) {}
            public void onPasteTextFromClipboard() {}
            public void onBell() {}
            public void onColorsChanged() {}
        };
        TerminalSessionClient client = (TerminalSessionClient) Proxy.newProxyInstance(
            TerminalSessionClient.class.getClassLoader(), new Class<?>[]{TerminalSessionClient.class},
            (proxy, method, args) -> method.getReturnType() == int.class ? 0 : null);
        TerminalEmulator emulator = new TerminalEmulator(output, 12, 4, 8, 16, 200, client);
        emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = Color.WHITE;
        emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND] = Color.BLACK;
        byte[] bytes = ("\u001b[?25l" + text).getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);
        return emulator;
    }

    private Bitmap blank(TerminalRenderer renderer) {
        Bitmap bitmap = Bitmap.createBitmap((int) Math.ceil(renderer.mFontWidth * 12),
            renderer.mFontLineSpacing * 4 + 30, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLACK);
        return bitmap;
    }

    private Bitmap render(TerminalRenderer renderer, String text) {
        Bitmap bitmap = blank(renderer);
        renderer.render(emulator(text), new Canvas(bitmap), 0, -1, -1, -1, -1);
        return bitmap;
    }

    @Test public void letterSpacingChangesTheTerminalGrid() {
        TerminalRenderer normal = new TerminalRenderer(32, typeface, 1f, 0f);
        TerminalRenderer wide = new TerminalRenderer(32, typeface, 1f, 0.15f);
        TerminalRenderer tight = new TerminalRenderer(32, typeface, 1f, -0.15f);
        assertEquals(normal.mFontWidth + 32 * 0.15f, wide.mFontWidth, 0.01f);
        assertEquals(normal.mFontWidth - 32 * 0.15f, tight.mFontWidth, 0.01f);
    }

    @Test public void chineseGlyphKeepsItsProportionsAndTwoColumns() {
        TerminalRenderer renderer = new TerminalRenderer(32, typeface);
        Bitmap actual = render(renderer, "中");
        Bitmap expected = blank(renderer);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(typeface);
        paint.setTextSize(32);
        paint.setColor(Color.WHITE);
        float left = (2 * renderer.mFontWidth - paint.measureText("中")) / 2f;
        new Canvas(expected).drawText("中", left, renderer.mFontLineSpacing, paint);
        assertTrue("CJK must be centered without horizontal stretching", actual.sameAs(expected));
    }

    private float inkCenter(Bitmap bitmap, TerminalRenderer renderer, int column) {
        double sum = 0, weights = 0;
        for (int x = (int) (column * renderer.mFontWidth); x < (column + 1) * renderer.mFontWidth; x++) {
            for (int y = 0; y < renderer.mFontLineSpacing + renderer.mFontLineSpacingAndAscent; y++) {
                int weight = Color.red(bitmap.getPixel(x, y));
                sum += x * weight;
                weights += weight;
            }
        }
        assertTrue("expected visible glyph", weights > 0);
        return (float) (sum / weights);
    }

    @Test public void ansiColorBoundariesDoNotShiftSpacedText() {
        TerminalRenderer renderer = new TerminalRenderer(32, typeface, 1f, 0.15f);
        Bitmap plain = render(renderer, "XXXX");
        Bitmap split = render(renderer, "X\u001b[38;2;255;255;255mX\u001b[0mX\u001b[38;2;255;255;255mX");
        for (int col = 0; col < 4; col++) {
            assertEquals("same column despite style boundary", inkCenter(plain, renderer, col),
                inkCenter(split, renderer, col), 1f);
        }
    }

    @Test public void verticalBordersHaveNoGapsAtCustomSpacing() {
        for (float lineSpacing : new float[]{0.7f, 1f, 1.3f}) {
            for (float letterSpacing : new float[]{-0.15f, 0f, 0.15f}) {
                TerminalRenderer renderer = new TerminalRenderer(32, typeface, lineSpacing, letterSpacing);
                Bitmap bitmap = render(renderer, "│\r\n│\r\n│");
                int x = (int) (renderer.mFontWidth / 2f);
                int top = Math.max(0, renderer.mFontLineSpacingAndAscent);
                int bottom = renderer.mFontLineSpacingAndAscent + 3 * renderer.mFontLineSpacing;
                for (int y = top; y < bottom; y++) {
                    assertTrue("border gap at spacing " + lineSpacing + "/" + letterSpacing + ", row " + y,
                        Color.red(bitmap.getPixel(x, y)) > 0);
                }
            }
        }
    }

    @Test public void horizontalBordersHaveNoGapsBetweenCells() {
        TerminalRenderer renderer = new TerminalRenderer(32, typeface, 1.3f, 0.15f);
        Bitmap bitmap = render(renderer, "────");
        int y = renderer.mFontLineSpacingAndAscent + renderer.mFontLineSpacing / 2;
        // Only sample pixel centers inside the last cell's fractional right edge.
        for (int x = 0; x + 0.5f < 4 * renderer.mFontWidth; x++) {
            assertTrue("horizontal border gap at " + x, Color.red(bitmap.getPixel(x, y)) > 0);
        }
    }

    @Test public void roundedCornersMeetTheirNeighboringCells() {
        TerminalRenderer renderer = new TerminalRenderer(32, typeface, 1.3f, 0.15f);
        Bitmap bitmap = render(renderer, "╭──╮\r\n│  │\r\n╰──╯");
        int y = renderer.mFontLineSpacingAndAscent + renderer.mFontLineSpacing / 2;
        for (int column = 1; column < 4; column++) {
            int x = (int) (column * renderer.mFontWidth);
            assertTrue("rounded corner must meet the horizontal border", Color.red(bitmap.getPixel(x, y)) > 0);
        }
        int x = (int) (renderer.mFontWidth / 2);
        for (int row = 1; row < 3; row++) {
            y = renderer.mFontLineSpacingAndAscent + row * renderer.mFontLineSpacing;
            assertTrue("rounded corner must meet the vertical border", Color.red(bitmap.getPixel(x, y)) > 0);
        }
    }

    @Test public void backgroundStillFillsTheSameCellBoundsAsText() {
        TerminalRenderer renderer = new TerminalRenderer(32, typeface, 1.3f, 0.15f);
        Bitmap bitmap = render(renderer, " \u001b[48;2;17;51;85m中\u001b[0m");
        int y = renderer.mFontLineSpacingAndAscent + 1;
        assertEquals(Color.BLACK, bitmap.getPixel((int) (renderer.mFontWidth / 2), y));
        assertEquals(Color.rgb(17, 51, 85), bitmap.getPixel((int) (renderer.mFontWidth * 1.5f), y));
        assertEquals(Color.rgb(17, 51, 85), bitmap.getPixel((int) (renderer.mFontWidth * 2.5f), y));
        assertEquals(Color.BLACK, bitmap.getPixel((int) (renderer.mFontWidth * 3.5f), y));
    }

    @Test public void barCursorDoesNotShrinkTheCharacterUnderIt() {
        TerminalRenderer renderer = new TerminalRenderer(32, typeface);
        Bitmap withoutCursor = render(renderer, "X");
        TerminalEmulator terminal = emulator("X\u001b[1;1H\u001b[6 q\u001b[?25h");
        terminal.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = Color.RED;
        assertEquals(TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR, terminal.getCursorStyle());
        Bitmap withCursor = blank(renderer);
        renderer.render(terminal, new Canvas(withCursor), 0, -1, -1, -1, -1);
        // The red bar contributes no green, so this compares just the white glyph.
        for (int y = 0; y < withoutCursor.getHeight(); y++) {
            for (int x = 0; x < withoutCursor.getWidth(); x++) {
                assertEquals("cursor must not change glyph geometry", Color.green(withoutCursor.getPixel(x, y)),
                    Color.green(withCursor.getPixel(x, y)));
            }
        }
    }
}
