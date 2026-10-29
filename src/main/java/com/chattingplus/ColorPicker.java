package com.chattingplus;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.function.IntConsumer;

/**
 * 색 고르는 창. 왼쪽 네모에서 채도(가로)와 밝기(세로), 가운데 막대에서 색상, 오른쪽 막대에서 투명도를
 * 눌러서(또는 끌어서) 고른다. 아래에 고른 색과 색 코드가 보인다.
 */
final class ColorPicker {
	private static final int SV = 96;
	private static final int BAR = 10;
	private static final int GAP = 6;
	private static final int PAD = 6;
	private static final int FOOTER = 16;
	static final int WIDTH = PAD * 2 + SV + GAP + BAR + GAP + BAR;
	static final int HEIGHT = PAD * 2 + SV + FOOTER;

	private enum Target { NONE, SV, HUE, ALPHA }

	private boolean open;
	private int x, y;
	private float hue, saturation, value;
	private int alpha;
	private IntConsumer onChange;
	private Target dragging = Target.NONE;

	boolean isOpen() {
		return open;
	}

	void close() {
		open = false;
		dragging = Target.NONE;
	}

	/** 기준점(색 견본) 아래에 연다. 화면 밖으로 나가지 않게 맞춘다. */
	void open(int color, int anchorX, int anchorBottom, int screenW, int screenH, IntConsumer onChange) {
		this.onChange = onChange;
		open = true;
		x = MathHelper.clamp(anchorX, 0, Math.max(0, screenW - WIDTH));
		y = anchorBottom + 2;
		if (y + HEIGHT > screenH) y = Math.max(0, anchorBottom - 14 - HEIGHT);
		alpha = (color >>> 24) & 0xFF;
		float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
		value = max;
		saturation = max == 0 ? 0 : d / max;
		if (d == 0) hue = 0;
		else if (max == r) hue = (((g - b) / d) % 6 + 6) % 6 / 6f;
		else if (max == g) hue = ((b - r) / d + 2) / 6f;
		else hue = ((r - g) / d + 4) / 6f;
	}

	int color() {
		return (alpha << 24) | (MathHelper.hsvToRgb(hue, saturation, value) & 0xFFFFFF);
	}

	boolean contains(double mx, double my) {
		return open && mx >= x && mx < x + WIDTH && my >= y && my < y + HEIGHT;
	}

	/** 창 안을 누르면 true. 네모나 막대를 눌렀으면 그 자리 색으로 바꾸고 끌기 시작 */
	boolean mouseClicked(double mx, double my) {
		if (!contains(mx, my)) return false;
		int top = y + PAD;
		if (my >= top && my < top + SV) {
			if (mx >= svX() && mx < svX() + SV) dragging = Target.SV;
			else if (mx >= hueX() && mx < hueX() + BAR) dragging = Target.HUE;
			else if (mx >= alphaX() && mx < alphaX() + BAR) dragging = Target.ALPHA;
		}
		mouseDragged(mx, my);
		return true;
	}

	boolean mouseDragged(double mx, double my) {
		if (dragging == Target.NONE) return false;
		float fy = MathHelper.clamp((float) (my - (y + PAD)) / (SV - 1), 0, 1);
		switch (dragging) {
			case SV -> {
				saturation = MathHelper.clamp((float) (mx - svX()) / (SV - 1), 0, 1);
				value = 1 - fy;
			}
			case HUE -> hue = Math.min(fy, 0.999f);
			case ALPHA -> alpha = Math.round((1 - fy) * 255);
			default -> {}
		}
		onChange.accept(color());
		return true;
	}

	/** 끌기를 마쳤으면 true (이때 설정을 저장한다) */
	boolean mouseReleased() {
		boolean was = dragging != Target.NONE;
		dragging = Target.NONE;
		return was;
	}

	private int svX() {
		return x + PAD;
	}

	private int hueX() {
		return svX() + SV + GAP;
	}

	private int alphaX() {
		return hueX() + BAR + GAP;
	}

	void render(DrawContext context, TextRenderer tr) {
		if (!open) return;
		Theme theme = Theme.get();
		// 뒤 글자가 비치지 않게 먼저 그린 것을 내보내고 앞쪽(z)에 그린다
		context.draw();
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 500);

		UiButton.fillRounded(context, x, y, x + WIDTH, y + HEIGHT, theme.windowBg | 0xFF000000);
		context.drawBorder(x, y, WIDTH, HEIGHT, theme.divider | 0xFF000000);
		int top = y + PAD;

		// 채도(가로) x 밝기(세로) 네모: 세로줄마다 위(밝음)에서 아래(검정)로
		for (int i = 0; i < SV; i++) {
			int topColor = 0xFF000000 | MathHelper.hsvToRgb(hue, i / (float) (SV - 1), 1f);
			context.fillGradient(svX() + i, top, svX() + i + 1, top + SV, topColor, 0xFF000000);
		}
		int mx = svX() + Math.round(saturation * (SV - 1)), my = top + Math.round((1 - value) * (SV - 1));
		context.drawBorder(mx - 2, my - 2, 5, 5, 0xFFFFFFFF);
		context.drawBorder(mx - 3, my - 3, 7, 7, 0xFF000000);

		// 색상 막대
		for (int k = 0; k < 6; k++) {
			int y0 = top + k * SV / 6, y1 = top + (k + 1) * SV / 6;
			int c0 = 0xFF000000 | MathHelper.hsvToRgb(k / 6f, 1f, 1f);
			int c1 = 0xFF000000 | MathHelper.hsvToRgb(k == 5 ? 0f : (k + 1) / 6f, 1f, 1f);
			context.fillGradient(hueX(), y0, hueX() + BAR, y1, c0, c1);
		}
		marker(context, hueX(), top + Math.round(hue * (SV - 1)));

		// 투명도 막대: 체크무늬 위에 불투명 → 투명
		for (int cy = 0; cy < SV; cy += 5) {
			for (int cx = 0; cx < BAR; cx += 5) {
				int checker = ((cx + cy) / 5) % 2 == 0 ? 0xFFFFFFFF : 0xFFBBBBBB;
				context.fill(alphaX() + cx, top + cy, alphaX() + Math.min(BAR, cx + 5), top + Math.min(SV, cy + 5), checker);
			}
		}
		int rgb = color() & 0xFFFFFF;
		context.fillGradient(alphaX(), top, alphaX() + BAR, top + SV, 0xFF000000 | rgb, rgb);
		marker(context, alphaX(), top + Math.round((1 - alpha / 255f) * (SV - 1)));

		// 아래: 고른 색 견본 + 색 코드
		int fy = top + SV + 4;
		context.fill(x + PAD, fy, x + PAD + 22, fy + 9, color());
		context.drawBorder(x + PAD - 1, fy - 1, 24, 11, 0xFF000000);
		String hex = alpha == 0xFF ? String.format("#%06X", rgb) : String.format("#%08X", color());
		UiFont.drawPlain(context, tr, hex, x + PAD + 28, fy + 2, theme.text | 0xFF000000, UiFont.small());

		context.getMatrices().pop();
	}

	/** 막대 위 현재 위치 표시 */
	private static void marker(DrawContext context, int bx, int my) {
		context.fill(bx - 2, my - 1, bx + BAR + 2, my + 2, 0xFF000000);
		context.fill(bx - 1, my, bx + BAR + 1, my + 1, 0xFFFFFFFF);
	}
}
