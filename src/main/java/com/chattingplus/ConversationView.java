package com.chattingplus;

import com.chattingplus.WhisperStore.Conversation;
import com.chattingplus.WhisperStore.Message;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 말풍선 형태의 대화 내용. 메인 창, 팝업, 설정 미리보기가 같이 쓴다.
 * 이모티콘 글자로만 된 메시지는 말풍선 없이 큰 이모티콘으로 그린다.
 *
 * 글자 크기는 두 가지: 말풍선 글자(설정의 "말풍선 글자")와 그 밖의 시간/날짜/이름표/서버 안내("시간·이름표 글자").
 * 팝업처럼 영역 전체를 키우거나 줄일 때는 setAreaScale 로 둘 다에 곱한다.
 */
final class ConversationView {
	static final int LINE_H = 10;
	/** 이름 줄과 말풍선 사이 간격 */
	private static final int SENDER_GAP = 3;
	private static final int STICKER_GAP = 2;
	/** 맨 위/맨 아래 메시지와 영역 경계(머리글, 입력줄) 사이 여백 */
	private static final int EDGE = 6;

	static final ZoneId ZONE = ZoneId.systemDefault();
	static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy년 M월 d일 E", Locale.KOREAN);

	/**
	 * width/lines 는 배율을 곱하기 전 값.
	 * sender: 채널 채팅에서 말풍선 위에 보이는 이름 (말한 사람이 바뀔 때만)
	 * stickers: 이모티콘으로만 된 메시지면 그 이모티콘들
	 */
	private record Row(List<OrderedText> lines, int width, int height, boolean outgoing, boolean system,
					   String time, String day, String sender, Text label, List<Emotes.Emote> stickers) {}

	/** 말풍선 위 이름이 그려진 자리. 누르면 그 사람과의 귓속말로 넘어간다 */
	private record NameHit(int x0, int y0, int x1, int y1, String name) {
		boolean contains(double mx, double my) {
			return mx >= x0 && mx < x1 && my >= y0 && my < y1;
		}
	}

	private final TextRenderer textRenderer;
	private final List<Row> rows = new ArrayList<>();
	private Conversation layoutFor;
	private int layoutCount = -1;
	private int layoutWidth = -1;
	/** 크기나 글꼴이 바뀌면 줄바꿈을 다시 해야 한다 */
	private String layoutStyle = "";
	private int contentHeight;
	/** 맨 아래에서부터 위로 스크롤한 픽셀 수 */
	private double scroll;
	/** 영역 전체 글자 배율 (팝업은 설정의 "팝업 글자") */
	private float areaScale = 1f;

	private final List<NameHit> nameHits = new ArrayList<>();
	private int viewTop, viewBottom;
	private int mouseX = -1, mouseY = -1;

	ConversationView(TextRenderer textRenderer) {
		this.textRenderer = textRenderer;
	}

	void setAreaScale(float areaScale) {
		this.areaScale = areaScale;
	}

	void resetScroll() {
		scroll = 0;
	}

	/** 위로 굴리면(양수) 예전 메시지 쪽으로 */
	void scroll(double amount, int viewHeight) {
		scroll = MathHelper.clamp(scroll + amount * LINE_H * 3, 0, maxScroll(viewHeight));
	}

	boolean isEmpty() {
		return rows.isEmpty();
	}

	/** 그리기 전에 마우스 위치를 알려 주면 가리킨 이름에 밑줄을 긋는다 (모르면 -1) */
	void setMouse(int mouseX, int mouseY) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
	}

	/** 이 자리에 그려진 이름 (마지막으로 그린 화면 기준). 없으면 null */
	String nameAt(double mx, double my) {
		if (my < viewTop || my >= viewBottom) return null;
		for (NameHit hit : nameHits) if (hit.contains(mx, my)) return hit.name;
		return null;
	}

	// ---------------------------------------------------------------- 크기

	private static ModConfig.Sizes sizes() {
		return ModConfig.get().sizes;
	}

	/** 말풍선 글자 */
	private float bubbleScale() {
		return sizes().textScale * areaScale;
	}

	/** 시간, 날짜, 이름(서버 모양이 아닐 때), 서버 안내 */
	private float metaScale() {
		return UiFont.small() * sizes().metaScale * areaScale;
	}

	/** 서버 모양 이름표: 등급 아이콘이 있어서 기본 크기를 바탕으로 */
	private float labelScale() {
		return sizes().metaScale * areaScale;
	}

	private int lineHeight() {
		return Math.max(6, Math.round(LINE_H * bubbleScale()));
	}

	private int noteLineHeight() {
		return Math.max(6, Math.round(LINE_H * metaScale()));
	}

	/** 날짜 칩 줄 높이 */
	private int dayHeight() {
		return UiFont.height(metaScale()) + 9;
	}

	/** 말풍선 위 이름 줄 높이: 글자 높이 + 밑줄 자리 + 간격 */
	private int senderHeight(boolean styled) {
		int textH = styled ? Math.round(9 * labelScale()) : UiFont.height(metaScale());
		return textH + 2 + SENDER_GAP;
	}

	/** 영역 폭에 맞춰 이모티콘 크기를 줄인다 (작은 팝업에서 너무 크지 않게) */
	private static int stickerSize(int areaWidth) {
		return Math.max(16, Math.min(sizes().stickerSize, areaWidth / 3));
	}

	private double maxScroll(int viewHeight) {
		return Math.max(0, contentHeight - viewHeight + EDGE * 2);
	}

	// ---------------------------------------------------------------- 그리기

	void render(DrawContext context, Conversation c, int x0, int y0, int x1, int y1) {
		Theme theme = Theme.get();
		layout(c, x1 - x0);
		int viewH = y1 - y0;
		scroll = MathHelper.clamp(scroll, 0, maxScroll(viewH));
		nameHits.clear();
		viewTop = y0;
		viewBottom = y1;
		if (rows.isEmpty()) return;

		context.enableScissor(x0, y0, x1 + 4, y1);
		int y = y1 - EDGE - contentHeight + (int) scroll;
		for (Row row : rows) {
			if (y + row.height >= y0 && y <= y1) drawRow(context, row, x0, x1, y, theme);
			y += row.height + sizes().messageGap;
		}
		context.disableScissor();

		if (contentHeight > viewH) {
			int barH = Math.max(12, viewH * viewH / contentHeight);
			double ratio = 1 - scroll / maxScroll(viewH);
			int barY = y0 + (int) ((viewH - barH) * ratio);
			context.fill(x1 + 2, barY, x1 + 4, barY + barH, Theme.withAlpha(theme.text, 0x80));
		}
	}

	private void drawRow(DrawContext context, Row row, int x0, int x1, int y, Theme theme) {
		int cx = (x0 + x1) / 2;
		float meta = metaScale();
		int metaH = UiFont.height(meta);
		if (row.day != null) {
			// 날짜 구분: 작은 글자를 둥근 칩 안에
			int w = UiFont.width(textRenderer, row.day, meta);
			UiButton.fillRounded(context, cx - w / 2 - 6, y + 2, cx + w / 2 + 6, y + metaH + 6, theme.dayChip());
			UiFont.drawPlain(context, textRenderer, row.day, cx - w / 2, y + 4, theme.mutedText, meta);
			y += dayHeight();
		}

		if (row.system) {
			// 서버 안내: 가운데에 작은 회색 글자
			for (OrderedText line : row.lines) {
				int w = Math.round(textRenderer.getWidth(line) * meta);
				UiFont.drawText(context, textRenderer, line, cx - w / 2, y, theme.mutedText, meta, false);
				y += noteLineHeight();
			}
			return;
		}

		if (row.sender != null) {
			// 이름표: 서버가 보낸 모양(등급 아이콘, 칭호, 닉네임 색)이 있으면 그대로, 없으면 작은 회색 이름
			int nameW;
			int nameH;
			if (row.label != null) {
				float scale = labelScale();
				nameW = UiFont.drawStyled(context, textRenderer, row.label, x0 + 1, y + 1, x1 - x0, theme.text | 0xFF000000, scale);
				nameH = Math.round(9 * scale);
			} else {
				String name = UiFont.trim(textRenderer, row.sender, x1 - x0, meta);
				UiFont.drawPlain(context, textRenderer, name, x0 + 1, y + 1, Theme.mix(theme.text, theme.mutedText, 0.4f), meta);
				nameW = UiFont.width(textRenderer, name, meta);
				nameH = metaH;
			}
			// 누르면 그 사람과의 귓속말로 (가리키면 밑줄)
			NameHit hit = new NameHit(x0, y, x0 + 2 + nameW, y + nameH + 2, row.sender);
			nameHits.add(hit);
			if (hit.contains(mouseX, mouseY)) context.fill(x0 + 1, y + nameH + 1, x0 + 1 + nameW, y + nameH + 2, theme.selection() | 0xFF000000);
			y += senderHeight(row.label != null);
		}

		int timeW = UiFont.width(textRenderer, row.time, meta);

		if (row.stickers != null) {
			int size = stickerSize(x1 - x0);
			int total = row.stickers.size() * (size + STICKER_GAP) - STICKER_GAP;
			int sx = row.outgoing ? x1 - total : x0;
			for (Emotes.Emote emote : row.stickers) {
				SmoothTextures.drawEmote(context, emote, sx, y, size);
				sx += size + STICKER_GAP;
			}
			int timeX = row.outgoing ? x1 - total - 3 - timeW : x0 + total + 3;
			UiFont.drawPlain(context, textRenderer, row.time, timeX, y + size - metaH, theme.mutedText, meta);
			return;
		}

		float scale = bubbleScale();
		int lineH = lineHeight();
		int pad = sizes().bubblePadding;
		int bw = Math.round(row.width * scale) + pad * 2;
		int bh = row.lines.size() * lineH + pad * 2 - 1;
		int bx = row.outgoing ? x1 - bw : x0;
		int color = row.outgoing ? theme.mineBubble : theme.theirsBubble;
		// 모서리를 한 픽셀 깎아서 둥근 말풍선처럼
		context.fill(bx + 1, y, bx + bw - 1, y + bh, color);
		context.fill(bx, y + 1, bx + bw, y + bh - 1, color);

		int textColor = row.outgoing ? theme.mineText : theme.theirsText;
		int ty = y + pad;
		for (OrderedText line : row.lines) {
			UiFont.drawText(context, textRenderer, line, bx + pad, ty, textColor, scale, false);
			ty += lineH;
		}

		int timeX = row.outgoing ? bx - 3 - timeW : bx + bw + 3;
		UiFont.drawPlain(context, textRenderer, row.time, timeX, y + bh - metaH, theme.mutedText, meta);
	}


	/** 줄바꿈은 매 프레임 계산하지 않고 대화, 메시지 수, 폭, 크기/글꼴이 바뀔 때만 다시 한다 */
	private void layout(Conversation c, int areaWidth) {
		String style = bubbleScale() + "/" + metaScale() + "/" + labelScale() + "/" + sizes().bubblePadding + "/"
				+ sizes().stickerSize + "/" + ModConfig.get().font;
		if (layoutFor == c && layoutCount == c.messages.size() && layoutWidth == areaWidth && layoutStyle.equals(style)) return;
		layoutFor = c;
		layoutCount = c.messages.size();
		layoutWidth = areaWidth;
		layoutStyle = style;
		rows.clear();
		contentHeight = 0;

		float scale = bubbleScale();
		int lineH = lineHeight();
		int pad = sizes().bubblePadding;
		// 줄바꿈 폭은 배율을 곱하기 전 단위로 계산한다
		int maxBubbleText = Math.max(20, (int) ((areaWidth * 7 / 10 - pad * 2) / scale));
		int maxSystemText = Math.max(20, (int) (areaWidth / metaScale()));
		int sticker = stickerSize(areaWidth);

		LocalDate prevDay = null;
		String prevSender = null;
		for (Message m : c.messages) {
			ZonedDateTime when = Instant.ofEpochMilli(m.time).atZone(ZONE);
			String day = null;
			if (!when.toLocalDate().equals(prevDay)) {
				prevDay = when.toLocalDate();
				day = DAY.format(when);
			}
			// 같은 사람이 연달아 말하면 이름은 첫 말풍선에만
			String senderLabel = null;
			if (m.sender != null && !m.outgoing && !m.system && (day != null || !m.sender.equals(prevSender))) senderLabel = m.sender;
			prevSender = m.outgoing || m.system ? null : m.sender;

			List<Emotes.Emote> stickers = m.system ? null : Emotes.stickers(m.text);
			List<OrderedText> lines = List.of();
			int w = 0;
			int h;
			if (stickers != null) {
				h = sticker;
			} else {
				lines = textRenderer.wrapLines(UiFont.text(m.text), m.system ? maxSystemText : maxBubbleText);
				if (lines.isEmpty()) lines = List.of(OrderedText.EMPTY);
				for (OrderedText line : lines) w = Math.max(w, textRenderer.getWidth(line));
				h = m.system ? lines.size() * noteLineHeight() : lines.size() * lineH + pad * 2 - 1;
			}
			if (day != null) h += dayHeight();
			Text label = senderLabel != null ? NameLabels.fromJson(m.senderLabel) : null;
			if (senderLabel != null) h += senderHeight(label != null);
			rows.add(new Row(lines, w, h, m.outgoing, m.system, TIME.format(when), day, senderLabel, label, stickers));
			contentHeight += h + sizes().messageGap;
		}
		if (!rows.isEmpty()) contentHeight -= sizes().messageGap;
	}
}
