package com.chattingplus;

import net.minecraft.client.gui.DrawContext;

/** 모서리가 둥근 사각형 (메인 창, 팝업). 마인크래프트 GUI 는 픽셀 단위라 줄마다 안쪽으로 들여서 그린다. */
public final class Shapes {
	private Shapes() {}

	/** 위에서 i 번째 줄이 모서리에서 얼마나 안쪽으로 들어가는지 (반지름 r 원) */
	private static int inset(int r, int i) {
		double d = r - i - 0.5;
		return r - (int) Math.round(Math.sqrt(r * r - d * d));
	}

	private static int clampRadius(int x0, int y0, int x1, int y1, int r) {
		return Math.max(0, Math.min(r, Math.min((x1 - x0) / 2, (y1 - y0) / 2)));
	}

	/** 채운 사각형. tl/tr/bl/br 로 둥글게 할 모서리를 고른다 */
	public static void fill(DrawContext context, int x0, int y0, int x1, int y1, int color, int r,
							boolean tl, boolean tr, boolean bl, boolean br) {
		r = clampRadius(x0, y0, x1, y1, r);
		if (r == 0) {
			context.fill(x0, y0, x1, y1, color);
			return;
		}
		for (int i = 0; i < r; i++) {
			int in = inset(r, i);
			context.fill(x0 + (tl ? in : 0), y0 + i, x1 - (tr ? in : 0), y0 + i + 1, color);
			context.fill(x0 + (bl ? in : 0), y1 - i - 1, x1 - (br ? in : 0), y1 - i, color);
		}
		context.fill(x0, y0 + r, x1, y1 - r, color);
	}

	public static void fill(DrawContext context, int x0, int y0, int x1, int y1, int color, int r) {
		fill(context, x0, y0, x1, y1, color, r, true, true, true, true);
	}

	/** 한 줄이 모서리에서 얼마나 들어가는지: 위(위쪽 모서리) 또는 아래(아래쪽 모서리)에서 반지름 안쪽이면 곡선, 아니면 0 */
	private static int rowInset(int r, int fromTop, int fromBottom, boolean top, boolean bottom) {
		if (top && fromTop < r) return inset(r, fromTop);
		if (bottom && fromBottom < r) return inset(r, fromBottom);
		return 0;
	}

	/**
	 * 1픽셀 테두리. (x0,y0)-(x1,y1) 이 바깥 경계이고, 안쪽 (x0+1,y0+1)-(x1-1,y1-1) 은
	 * {@link #fill} 로 반지름 r 인 모양을 칠한다고 보고, 그 둘 사이만 줄마다 정확히 칠한다.
	 * 같은 곡선 계산을 쓰므로 안쪽 칠과 테두리 사이에 빈 픽셀이 생기지 않는다.
	 */
	public static void ring(DrawContext context, int x0, int y0, int x1, int y1, int color, int r,
							boolean tl, boolean tr, boolean bl, boolean br) {
		int inner = clampRadius(x0 + 1, y0 + 1, x1 - 1, y1 - 1, r);
		if (inner == 0) {
			context.drawBorder(x0, y0, x1 - x0, y1 - y0, color);
			return;
		}
		int outer = inner + 1;
		int ih0 = y0 + 1, ih1 = y1 - 1;
		for (int yy = y0; yy < y1; yy++) {
			int ot = yy - y0, ob = y1 - 1 - yy;
			int oL = rowInset(outer, ot, ob, tl, bl), oR = rowInset(outer, ot, ob, tr, br);
			if (yy < ih0 || yy >= ih1) {
				// 맨 위/아래 줄: 바깥 모양 전체
				context.fill(x0 + oL, yy, x1 - oR, yy + 1, color);
				continue;
			}
			// 곡선이 없는 가운데 줄들은 양옆 1픽셀씩 한 번에
			if (ot >= outer && ob >= outer) {
				int end = Math.min(ih1, y1 - outer);
				context.fill(x0, yy, x0 + 1, end, color);
				context.fill(x1 - 1, yy, x1, end, color);
				yy = end - 1;
				continue;
			}
			int it = yy - ih0, ib = ih1 - 1 - yy;
			int iL = 1 + rowInset(inner, it, ib, tl, bl), iR = 1 + rowInset(inner, it, ib, tr, br);
			if (iL > oL) context.fill(x0 + oL, yy, x0 + iL, yy + 1, color);
			if (iR > oR) context.fill(x1 - iR, yy, x1 - oR, yy + 1, color);
		}
	}

}
