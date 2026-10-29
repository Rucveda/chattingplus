package com.chattingplus;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 스티커 고르는 창. 이모티콘 팩 탭들 다음에 "내 그림" 탭(stickers 폴더의 그림)이 있다.
 * 고르면 그 스티커의 source 를 돌려주고, 창에 붙이는 건 부르는 쪽이 한다.
 * 스티커 편집 중에는 계속 열려 있고, 아래 줄에 편집 방법을 보여준다.
 */
final class StickerPicker {
	private static final int CELL = 34;
	private static final int ICON = 30;
	private static final int COLS = 6;
	private static final int ROWS = 3;
	private static final int TAB_H = 14;
	private static final int FOOTER_H = 12;
	private static final int PAD = 4;
	static final int WIDTH = PAD * 2 + COLS * CELL;
	static final int HEIGHT = TAB_H + PAD * 2 + ROWS * CELL + FOOTER_H;

	/** 마지막으로 보던 탭은 다시 열어도 유지 */
	private static int tabIndex;

	private record Item(String source, String name) {}

	private boolean open;
	private int x, y;
	private double scroll;
	private List<String> files = List.of();

	boolean isOpen() {
		return open;
	}

	void close() {
		open = false;
	}

	/** (x, y) 를 왼쪽 위로 열되 화면 밖으로 나가지 않게 */
	void openAt(int x, int y, int screenW, int screenH) {
		open = true;
		this.x = MathHelper.clamp(x, 0, Math.max(0, screenW - WIDTH));
		this.y = MathHelper.clamp(y, 0, Math.max(0, screenH - HEIGHT));
		scroll = 0;
		files = Stickers.fileNames();
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

	private static int tabCount() {
		return Emotes.packs().size() + 1;
	}

	private boolean onFileTab() {
		return tabIndex == tabCount() - 1;
	}

	private String tabName(int index) {
		List<Emotes.Pack> packs = Emotes.packs();
		return index < packs.size() ? packs.get(index).name() : Text.translatable("chattingplus.sticker.my").getString();
	}

	private List<Item> items() {
		tabIndex = MathHelper.clamp(tabIndex, 0, tabCount() - 1);
		List<Item> list = new ArrayList<>();
		if (onFileTab()) {
			for (String file : files) list.add(new Item(Stickers.FILE + file, file));
		} else {
			for (Emotes.Emote e : Emotes.packs().get(tabIndex).emotes()) list.add(new Item(Stickers.EMOTE + e.token(), e.name()));
		}
		return list;
	}

	private int gridTop() {
		return y + TAB_H + PAD;
	}

	private int maxScroll(int count) {
		int rows = (count + COLS - 1) / COLS;
		return Math.max(0, (rows - ROWS) * CELL);
	}

	private Item itemAt(List<Item> items, double mx, double my) {
		int gx = (int) mx - (x + PAD), gy = (int) (my - gridTop() + scroll);
		if (gx < 0 || gx >= COLS * CELL || my < gridTop() || my >= gridTop() + ROWS * CELL) return null;
		int index = (gy / CELL) * COLS + gx / CELL;
		return index >= 0 && index < items.size() ? items.get(index) : null;
	}

	/** 아래 줄 "내 그림" 탭의 [폴더 열기] [새로고침]: 왼쪽 절반 / 오른쪽 절반 */
	private boolean inFooter(double my) {
		return my >= y + HEIGHT - FOOTER_H;
	}

	/** 클릭한 스티커의 source. 탭, 빈 곳, 아래 줄 버튼을 누르면 null */
	String click(double mx, double my) {
		if (!contains(mx, my)) return null;
		if (my < y + TAB_H) {
			int index = (int) ((mx - x) / tabWidth());
			if (index >= 0 && index < tabCount()) {
				tabIndex = index;
				scroll = 0;
			}
			return null;
		}
		if (onFileTab() && inFooter(my)) {
			if (mx < x + WIDTH / 2) {
				Stickers.openFolder();
			} else {
				Stickers.reload();
				files = Stickers.fileNames();
			}
			return null;
		}
		Item item = itemAt(items(), mx, my);
		return item != null ? item.source : null;
	}

	boolean scroll(double mx, double my, double amount) {
		if (!contains(mx, my)) return false;
		scroll = MathHelper.clamp(scroll - amount * CELL, 0, maxScroll(items().size()));
		return true;
	}

	private static int tabWidth() {
		return WIDTH / tabCount();
	}

	void render(DrawContext context, TextRenderer tr, int mouseX, int mouseY) {
		if (!open) return;
		// 앞에 그린 글자가 비치지 않게 먼저 내보내고 앞쪽(z)에 그린다
		context.draw();
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 300);
		renderPanel(context, tr, mouseX, mouseY);
		context.getMatrices().pop();
	}

	private void renderPanel(DrawContext context, TextRenderer tr, int mouseX, int mouseY) {
		Theme theme = Theme.get();
		float small = UiFont.small();
		context.fill(x, y, x + WIDTH, y + HEIGHT, theme.chatBg | 0xFF000000);
		context.drawBorder(x - 1, y - 1, WIDTH + 2, HEIGHT + 2, theme.divider);

		List<Item> items = items();
		int count = tabCount(), tw = tabWidth();
		for (int i = 0; i < count; i++) {
			int tx = x + i * tw;
			int w = i == count - 1 ? x + WIDTH - tx : tw;
			context.fill(tx, y, tx + w, y + TAB_H, i == tabIndex ? theme.headerBg : theme.tab(i));
			String label = UiFont.trim(tr, tabName(i), w - 6, small);
			UiFont.drawPlain(context, tr, label, tx + (w - UiFont.width(tr, label, small)) / 2, y + 4, i == tabIndex ? theme.text : theme.mutedText, small);
		}

		int top = gridTop();
		Item hovered = itemAt(items, mouseX, mouseY);
		if (items.isEmpty() && onFileTab()) {
			int ty = top + 6;
			for (var line : tr.wrapLines(Text.translatable("chattingplus.sticker.empty"), (int) ((WIDTH - PAD * 4) / small))) {
				UiFont.drawText(context, tr, line, x + PAD * 2, ty, theme.mutedText, small, false);
				ty += 9;
			}
		}
		context.enableScissor(x, top, x + WIDTH, top + ROWS * CELL);
		for (int i = 0; i < items.size(); i++) {
			int cx = x + PAD + (i % COLS) * CELL;
			int cy = top + (i / COLS) * CELL - (int) scroll;
			if (cy + CELL < top || cy > top + ROWS * CELL) continue;
			Item item = items.get(i);
			if (item == hovered) context.fill(cx, cy, cx + CELL, cy + CELL, theme.hover());
			// 비율을 지켜 칸 가운데에
			float aspect = Stickers.aspect(item.source);
			int w = aspect >= 1 ? ICON : Math.round(ICON * aspect), h = aspect >= 1 ? Math.round(ICON / aspect) : ICON;
			Stickers.draw(context, item.source, cx + (CELL - w) / 2, cy + (CELL - h) / 2, w, h, 0xFFFFFFFF);
		}
		context.disableScissor();

		// 아래 줄: 내 그림 탭이면 [폴더 열기] [새로고침], 아니면 가리킨 이름이나 편집 방법
		int fy = y + HEIGHT - FOOTER_H;
		if (onFileTab()) {
			boolean onFooter = mouseY >= fy && mouseY < y + HEIGHT && mouseX >= x && mouseX < x + WIDTH;
			footerButton(context, tr, theme, "chattingplus.sticker.folder", x, x + WIDTH / 2, fy, onFooter && mouseX < x + WIDTH / 2);
			footerButton(context, tr, theme, "chattingplus.sticker.reload", x + WIDTH / 2, x + WIDTH, fy, onFooter && mouseX >= x + WIDTH / 2);
		} else {
			String footer = hovered != null ? hovered.name : Text.translatable("chattingplus.sticker.help").getString();
			UiFont.drawPlain(context, tr, UiFont.trim(tr, footer, WIDTH - PAD * 2, small), x + PAD, fy + 3, theme.mutedText, small);
		}
	}

	private static void footerButton(DrawContext context, TextRenderer tr, Theme theme, String key, int x0, int x1, int y0, boolean hover) {
		if (hover) context.fill(x0, y0, x1, y0 + FOOTER_H, theme.hover());
		String label = Text.translatable(key).getString();
		float small = UiFont.small();
		UiFont.drawPlain(context, tr, label, (x0 + x1 - UiFont.width(tr, label, small)) / 2, y0 + 3, hover ? theme.text : theme.mutedText, small);
	}
}
