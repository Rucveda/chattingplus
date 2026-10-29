package com.chattingplus;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * 이모티콘 고르는 창. 위쪽은 팩 탭, 가운데는 이모티콘 격자, 아래는 가리킨 이모티콘 이름.
 * 메인 창과 팝업이 같이 쓴다. 고르면 그 이모티콘을 돌려주고, 보내는 건 부르는 쪽이 한다.
 */
final class EmotePicker {
	private static final int CELL = 34;
	private static final int ICON = 30;
	private static final int COLS = 6;
	private static final int ROWS = 3;
	private static final int TAB_H = 14;
	private static final int FOOTER_H = 12;
	private static final int PAD = 4;
	static final int WIDTH = PAD * 2 + COLS * CELL;
	static final int HEIGHT = TAB_H + PAD * 2 + ROWS * CELL + FOOTER_H;

	/** 마지막으로 보던 팩은 창을 다시 열어도 유지 */
	private static int packIndex;

	private boolean open;
	private int x, y;
	private double scroll;

	boolean isOpen() {
		return open;
	}

	void close() {
		open = false;
	}

	/** 기준점(보통 이모티콘 버튼의 오른쪽 위) 위쪽에 연다. 화면 밖으로 나가지 않게 맞춘다. */
	void openAbove(int anchorRight, int anchorTop, int screenW, int screenH) {
		open = true;
		x = MathHelper.clamp(anchorRight - WIDTH, 0, Math.max(0, screenW - WIDTH));
		y = anchorTop - HEIGHT - 2;
		if (y < 0) y = Math.min(screenH - HEIGHT, anchorTop + 16);
		scroll = 0;
	}

	boolean contains(double mx, double my) {
		return open && mx >= x && mx < x + WIDTH && my >= y && my < y + HEIGHT;
	}

	int x() {
		return x;
	}

	int y() {
		return y;
	}

	private static Emotes.Pack pack() {
		List<Emotes.Pack> packs = Emotes.packs();
		if (packs.isEmpty()) return null;
		packIndex = MathHelper.clamp(packIndex, 0, packs.size() - 1);
		return packs.get(packIndex);
	}

	private int gridTop() {
		return y + TAB_H + PAD;
	}

	private int maxScroll(Emotes.Pack pack) {
		int rows = (pack.emotes().size() + COLS - 1) / COLS;
		return Math.max(0, (rows - ROWS) * CELL);
	}

	private Emotes.Emote emoteAt(double mx, double my) {
		Emotes.Pack pack = pack();
		if (pack == null) return null;
		int gx = (int) mx - (x + PAD), gy = (int) (my - gridTop() + scroll);
		if (gx < 0 || gx >= COLS * CELL || my < gridTop() || my >= gridTop() + ROWS * CELL) return null;
		int index = (gy / CELL) * COLS + gx / CELL;
		return index >= 0 && index < pack.emotes().size() ? pack.emotes().get(index) : null;
	}

	/** 클릭한 이모티콘. 탭을 누르거나 빈 곳을 누르면 null */
	Emotes.Emote click(double mx, double my) {
		if (!contains(mx, my)) return null;
		if (my < y + TAB_H) {
			List<Emotes.Pack> packs = Emotes.packs();
			if (packs.isEmpty()) return null;
			int index = (int) ((mx - x) / tabWidth(packs.size()));
			if (index >= 0 && index < packs.size()) {
				packIndex = index;
				scroll = 0;
			}
			return null;
		}
		return emoteAt(mx, my);
	}

	boolean scroll(double mx, double my, double amount) {
		if (!contains(mx, my)) return false;
		Emotes.Pack pack = pack();
		if (pack != null) scroll = MathHelper.clamp(scroll - amount * CELL, 0, maxScroll(pack));
		return true;
	}

	private static int tabWidth(int count) {
		return WIDTH / Math.max(1, count);
	}

	void render(DrawContext context, TextRenderer tr, int mouseX, int mouseY) {
		if (!open) return;
		// 글자는 모아서 나중에 그려지므로, 먼저 그린 것을 내보내고 앞쪽(z)에 그려야 뒤 대화 글자가 비치지 않는다
		context.draw();
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 300);
		renderPanel(context, tr, mouseX, mouseY);
		context.getMatrices().pop();
	}

	private void renderPanel(DrawContext context, TextRenderer tr, int mouseX, int mouseY) {
		Theme theme = Theme.get();
		int bg = theme.chatBg | 0xFF000000;
		context.fill(x, y, x + WIDTH, y + HEIGHT, bg);
		context.drawBorder(x - 1, y - 1, WIDTH + 2, HEIGHT + 2, theme.divider);

		// 팩 탭
		List<Emotes.Pack> packs = Emotes.packs();
		Emotes.Pack pack = pack();
		int tw = tabWidth(packs.size());
		for (int i = 0; i < packs.size(); i++) {
			int tx = x + i * tw;
			int w = i == packs.size() - 1 ? x + WIDTH - tx : tw;
			context.fill(tx, y, tx + w, y + TAB_H, i == packIndex ? theme.headerBg : theme.tab(i));
			String label = UiFont.trim(tr, packs.get(i).name(), w - 6, UiFont.small());
			UiFont.drawPlain(context, tr, label, tx + (w - UiFont.width(tr, label, UiFont.small())) / 2, y + 4, i == packIndex ? theme.text : theme.mutedText, UiFont.small());
		}
		if (pack == null) return;

		// 격자
		int top = gridTop();
		Emotes.Emote hovered = emoteAt(mouseX, mouseY);
		context.enableScissor(x, top, x + WIDTH, top + ROWS * CELL);
		List<Emotes.Emote> emotes = pack.emotes();
		for (int i = 0; i < emotes.size(); i++) {
			int cx = x + PAD + (i % COLS) * CELL;
			int cy = top + (i / COLS) * CELL - (int) scroll;
			if (cy + CELL < top || cy > top + ROWS * CELL) continue;
			Emotes.Emote emote = emotes.get(i);
			if (emote == hovered) context.fill(cx, cy, cx + CELL, cy + CELL, theme.hover());
			int off = (CELL - ICON) / 2;
			SmoothTextures.drawEmote(context, emote, cx + off, cy + off, ICON);
		}
		context.disableScissor();

		// 아래: 가리킨 이모티콘 이름과 채팅에 보이는 글자
		String footer = hovered != null ? hovered.name() + "  " + hovered.token() : pack.name() + (pack.author().isEmpty() ? "" : " · " + pack.author());
		UiFont.drawPlain(context, tr, UiFont.trim(tr, footer, WIDTH - PAD * 2, UiFont.small()), x + PAD, y + HEIGHT - FOOTER_H + 3, theme.mutedText, UiFont.small());
	}
}
