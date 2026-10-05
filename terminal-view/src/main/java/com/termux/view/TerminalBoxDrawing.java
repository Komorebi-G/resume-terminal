/* Added for Resume Terminal, 2026-10-03.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.termux.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Common TUI borders follow cell edges even when fonts or spacing change. */
final class TerminalBoxDrawing {
    private static final int UP = 1, RIGHT = 2, DOWN = 4, LEFT = 8;

    private TerminalBoxDrawing() {}

    static boolean supports(int codePoint) {
        return directions(codePoint) != 0;
    }

    private static int directions(int codePoint) {
        switch (codePoint) {
            case '─': case '━': return LEFT | RIGHT;
            case '│': case '┃': return UP | DOWN;
            case '┌': case '┏': case '╭': return RIGHT | DOWN;
            case '┐': case '┓': case '╮': return LEFT | DOWN;
            case '└': case '┗': case '╰': return RIGHT | UP;
            case '┘': case '┛': case '╯': return LEFT | UP;
            case '├': case '┣': return UP | RIGHT | DOWN;
            case '┤': case '┫': return UP | LEFT | DOWN;
            case '┬': case '┳': return LEFT | RIGHT | DOWN;
            case '┴': case '┻': return LEFT | RIGHT | UP;
            case '┼': case '╋': return UP | RIGHT | DOWN | LEFT;
            case '╴': case '╸': return LEFT;
            case '╵': case '╹': return UP;
            case '╶': case '╺': return RIGHT;
            case '╷': case '╻': return DOWN;
            default: return 0;
        }
    }

    static void draw(Canvas canvas, Paint paint, int codePoint, float left, float top,
                     float right, float bottom, int textSize, boolean bold) {
        int directions = directions(codePoint);
        float x = (left + right) / 2f, y = (top + bottom) / 2f;
        boolean heavy = "━┃┏┓┗┛┣┫┳┻╋╸╹╺╻".indexOf(codePoint) >= 0;
        Paint.Style style = paint.getStyle();
        float stroke = paint.getStrokeWidth();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, textSize * 0.06f) * (heavy ? 2f : bold ? 1.25f : 1f));
        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        if (codePoint >= '╭' && codePoint <= '╰') {
            float radius = Math.min(right - left, bottom - top) * 0.25f;
            boolean toRight = (directions & RIGHT) != 0;
            boolean toDown = (directions & DOWN) != 0;
            float dx = toRight ? radius : -radius;
            float dy = toDown ? radius : -radius;
            Path path = new Path();
            path.moveTo(toRight ? right : left, y);
            path.lineTo(x + dx, y);
            path.quadTo(x, y, x, y + dy);
            path.lineTo(x, toDown ? bottom : top);
            canvas.drawPath(path, paint);
        } else {
            Path path = new Path();
            if ((directions & UP) != 0) { path.moveTo(x, y); path.lineTo(x, top); }
            if ((directions & RIGHT) != 0) { path.moveTo(x, y); path.lineTo(right, y); }
            if ((directions & DOWN) != 0) { path.moveTo(x, y); path.lineTo(x, bottom); }
            if ((directions & LEFT) != 0) { path.moveTo(x, y); path.lineTo(left, y); }
            canvas.drawPath(path, paint);
        }
        canvas.restore();
        paint.setStyle(style);
        paint.setStrokeWidth(stroke);
    }
}
