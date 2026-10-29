package com.chattingplus;

import com.chattingplus.WhisperStore.Conversation;
import com.chattingplus.WhisperStore.Message;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 왼쪽은 채널과 대화 상대 목록, 오른쪽은 메신저 형태의 대화창 */
public class WhisperScreen extends Screen {
	private static final int LIST_W = 130;
	/** 목록 한 칸 높이의 최솟값 (글자를 키우면 더 높아진다) */
	private static final int MIN_ITEM_H = 24;
	private static final int PAD = 6;
	private static final int HEADER_H = 26;
	private static final int INPUT_H = 18;
	/** 아래 띠(왼쪽 버튼줄, 오른쪽 입력줄) 높이. 위 띠는 HEADER_H 로 양쪽이 같아 경계선이 한 줄로 이어진다 */
	private static final int FOOTER_H = 28;
	/** 목록의 "채널", "대화" 구역 이름 줄 높이의 최솟값 */
	private static final int MIN_SECTION_H = 12;

	private static final int COLOR_STAR = 0xFFFFC83D;

	private static final DateTimeFormatter LIST_DATE = DateTimeFormatter.ofPattern("M/d");

	/** 창을 다시 열었을 때 마지막으로 보던 대화를 보여주기 위해 기억 */
	private static String lastSelectedName;

	private int left, top, right, bottom;
	private TextFieldWidget searchField;
	private TextFieldWidget inputField;
	private ButtonWidget sendButton;
	private ButtonWidget emoteButton;
	private ButtonWidget favoriteButton;
	private ButtonWidget popupButton;
	private ButtonWidget deleteButton;
	private ButtonWidget channelHideButton;
	private ConversationView view;
	private final EmotePicker picker = new EmotePicker();

	private Conversation selected;
	/** 위쪽 channelCount 개는 채널, 그 아래는 귓속말 대화 */
	private List<Conversation> visible = List.of();
	private int channelCount;
	private int seenVersion = -1;
	private double listScroll;

	public WhisperScreen() {
		super(Text.translatable("chattingplus.title"));
	}

	public boolean isViewing(String name) {
		return selected != null && selected.name.equalsIgnoreCase(name);
	}

	@Override
	protected void init() {
		if (view == null) view = new ConversationView(textRenderer);
		picker.close();
		int panelW = Math.min(width - 20, 520);
		int panelH = Math.min(height - 20, 320);
		left = (width - panelW) / 2;
		top = (height - panelH) / 2;
		right = left + panelW;
		bottom = top + panelH;

		// 창 크기가 바뀌면 init 이 다시 불리므로 입력 중이던 글자를 유지한다
		String prevSearch = searchField != null ? searchField.getText() : "";
		String prevInput = inputField != null ? inputField.getText() : "";

		searchField = new UiTextField(textRenderer, left + PAD, top + (HEADER_H - 16) / 2, LIST_W - PAD * 2 - 18, 16, Text.translatable("chattingplus.search"));
		searchField.setMaxLength(32);
		searchField.setPlaceholder(Text.translatable("chattingplus.search").formatted(Formatting.DARK_GRAY));
		searchField.setText(prevSearch);
		searchField.setChangedListener(s -> refreshList());
		addDrawableChild(searchField);

		addDrawableChild(UiButton.create(Text.translatable("chattingplus.new_chat"), b -> startChatFromSearch())
				.dimensions(left + LIST_W - PAD - 16, top + (HEADER_H - 16) / 2, 16, 16).icon("add")
				.build());

		// 왼쪽 아래: [채팅창 귓속말 숨김] [⚙]
		addDrawableChild(UiButton.create(hideLabel(), b -> {
					ModConfig.get().hideInChat = !ModConfig.get().hideInChat;
					ModConfig.save();
					b.setMessage(hideLabel());
				})
				.dimensions(left + PAD, bottom - FOOTER_H + (FOOTER_H - 16) / 2, LIST_W - PAD * 2 - 18, 16).small()
				.build());
		addDrawableChild(UiButton.create(Text.translatable("chattingplus.settings"), b -> openSettings())
				.dimensions(left + LIST_W - PAD - 16, bottom - FOOTER_H + (FOOTER_H - 16) / 2, 16, 16).icon("settings")
				.build());

		// 아래 입력줄: [입력칸] [☺] [전송]
		int chatX = left + LIST_W + PAD;
		int inputY = bottom - FOOTER_H + (FOOTER_H - INPUT_H) / 2;
		inputField = new UiTextField(textRenderer, chatX, inputY, right - PAD - chatX - 44 - 20, INPUT_H, Text.translatable("chattingplus.input"));
		inputField.setPlaceholder(Text.translatable("chattingplus.input").formatted(Formatting.DARK_GRAY));
		inputField.setMaxLength(256);
		inputField.setText(prevInput);
		addDrawableChild(inputField);

		emoteButton = addDrawableChild(UiButton.create(Text.translatable("chattingplus.emotes"), b -> toggleEmotes())
				.dimensions(right - PAD - 40 - 2 - 18, inputY, 18, INPUT_H).icon("emote")
				.build());
		sendButton = addDrawableChild(UiButton.create(Text.translatable("chattingplus.send"), b -> send())
				.dimensions(right - PAD - 40, inputY, 40, INPUT_H)
				.build());

		// 머리글 오른쪽: [★] [팝업] [삭제]
		int headerY = top + (HEADER_H - 16) / 2;
		deleteButton = addDrawableChild(UiButton.create(Text.translatable("chattingplus.delete"), b -> confirmDelete())
				.dimensions(right - PAD - 34, headerY, 34, 16).small()
				.build());
		popupButton = addDrawableChild(UiButton.create(Text.translatable("chattingplus.popup"), b -> openPopup())
				.dimensions(right - PAD - 34 - 2 - 34, headerY, 34, 16).small()
				.build());
		favoriteButton = addDrawableChild(UiButton.create(Text.literal("☆"), b -> toggleFavorite())
				.dimensions(right - PAD - 34 - 2 - 34 - 2 - 16, headerY, 16, 16)
				.build());
		// 채널 대화에서는 ★ 대신 "기본 채팅창에서 숨길지" 버튼
		channelHideButton = addDrawableChild(UiButton.create(Text.empty(), b -> toggleChannelHide())
				.dimensions(right - PAD - 34 - 2 - 34 - 2 - 72, headerY, 72, 16).small()
				.build());

		refreshList();
		select(WhisperStore.get().find(lastSelectedName));
		setInitialFocus(selected != null ? inputField : searchField);
	}

	private Text hideLabel() {
		return Text.translatable(ModConfig.get().hideInChat ? "chattingplus.hide.on" : "chattingplus.hide.off");
	}

	private void openSettings() {
		if (client != null) client.setScreen(new SettingsScreen(this));
	}

	private void refreshList() {
		String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
		List<Conversation> list = new ArrayList<>();
		for (Conversation c : WhisperStore.get().channels()) {
			if (query.isEmpty() || c.displayName().toLowerCase(Locale.ROOT).contains(query)) list.add(c);
		}
		channelCount = list.size();
		for (Conversation c : WhisperStore.get().sorted()) {
			if (query.isEmpty() || c.name.toLowerCase(Locale.ROOT).contains(query)) list.add(c);
		}
		visible = list;
		seenVersion = WhisperStore.get().version();
		listScroll = MathHelper.clamp(listScroll, 0, maxListScroll());
		updateHeaderButtons();
	}

	private void select(Conversation c) {
		selected = c;
		lastSelectedName = c == null ? null : c.name;
		view.resetScroll();
		picker.close();
		boolean has = c != null;
		inputField.setEditable(has);
		sendButton.active = has;
		emoteButton.active = has;
		if (has) {
			WhisperStore.get().markRead(c);
			inputField.setMaxLength(WhisperSender.maxTextLength(c.name));
		}
		updateHeaderButtons();
	}

	private void updateHeaderButtons() {
		if (deleteButton == null) return;
		boolean has = selected != null;
		Channel channel = has ? selected.channel() : null;
		deleteButton.visible = has;
		popupButton.visible = has;
		favoriteButton.visible = has && channel == null;
		channelHideButton.visible = channel != null;
		if (favoriteButton.visible) favoriteButton.setMessage(selected.favorite ? Text.literal("★").withColor(COLOR_STAR) : Text.literal("☆"));
		if (channel != null) {
			channelHideButton.setMessage(Text.translatable(channel.settings().hideInChat ? "chattingplus.channel.hide.on" : "chattingplus.channel.hide.off"));
		}
	}

	private void toggleChannelHide() {
		Channel channel = selected == null ? null : selected.channel();
		if (channel == null) return;
		channel.settings().hideInChat = !channel.settings().hideInChat;
		ModConfig.save();
		updateHeaderButtons();
	}

	private void toggleFavorite() {
		if (selected == null || selected.channel() != null) return;
		WhisperStore.get().toggleFavorite(selected);
		refreshList();
	}

	private void toggleEmotes() {
		if (picker.isOpen()) picker.close();
		else picker.openAbove(emoteButton.getX() + emoteButton.getWidth(), emoteButton.getY(), width, height);
	}

	private void openPopup() {
		if (selected == null) return;
		PopupChat.open(selected.name);
		close();
	}

	/** 내 이름 (채널 채팅에서 알아낸 닉네임 또는 계정 이름) */
	static boolean isMe(String name) {
		String me = ModConfig.get().myName;
		return name.equalsIgnoreCase(me) || name.equalsIgnoreCase(net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername());
	}

	/** 말풍선 위 이름을 누르면: 그 사람과의 귓속말 대화를 연다 (없으면 만든다) */
	private void openWhisperWith(String name) {
		Conversation c = WhisperStore.get().getOrCreate(name);
		refreshList();
		select(c);
		setFocused(inputField);
	}

	private void startChatFromSearch() {
		String name = WhisperParser.cleanName(searchField.getText());
		if (name.isEmpty()) {
			setFocused(searchField);
			return;
		}
		Conversation c = WhisperStore.get().getOrCreate(name);
		searchField.setText("");
		refreshList();
		select(c);
		setFocused(inputField);
	}

	/** 검색창 Enter: 일치하는 상대가 있으면 열고, 없으면 새 대화 */
	private void submitSearch() {
		String query = searchField.getText().trim();
		if (query.isEmpty()) return;
		Conversation match = WhisperStore.get().find(query);
		if (match == null && visible.size() == 1) match = visible.get(0);
		if (match == null) {
			startChatFromSearch();
			return;
		}
		searchField.setText("");
		refreshList();
		select(match);
		setFocused(inputField);
	}

	private void send() {
		if (selected == null) return;
		if (WhisperSender.send(selected.name, inputField.getText())) {
			inputField.setText("");
			view.resetScroll();
		}
	}

	private void confirmDelete() {
		if (selected == null || client == null) return;
		Conversation target = selected;
		client.setScreen(new ConfirmScreen(ok -> {
			if (ok) {
				WhisperStore.get().remove(target);
				lastSelectedName = null;
			}
			client.setScreen(this);
		}, Text.translatable("chattingplus.delete.title"), Text.translatable(
				target.channel() != null ? "chattingplus.delete.channel" : "chattingplus.delete.message", target.displayName())));
	}

	@Override
	public void tick() {
		if (WhisperStore.get().version() != seenVersion) {
			refreshList();
			if (selected != null && WhisperStore.get().find(selected.name) != selected) select(null);
		}
		if (selected != null && selected.unread > 0) WhisperStore.get().markRead(selected);
	}

	// ---------------------------------------------------------------- 입력

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && picker.isOpen()) {
			picker.close();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			if (getFocused() == inputField) {
				send();
				return true;
			}
			if (getFocused() == searchField) {
				submitSearch();
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (picker.isOpen()) {
			if (picker.contains(mouseX, mouseY)) {
				Emotes.Emote emote = picker.click(mouseX, mouseY);
				// 카카오톡 이모티콘처럼 고르면 바로 보낸다
				if (emote != null && selected != null && WhisperSender.send(selected.name, emote.token())) {
					picker.close();
					view.resetScroll();
				}
				return true;
			}
			if (!emoteButton.isMouseOver(mouseX, mouseY)) picker.close();
		}
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		String name = selected != null ? view.nameAt(mouseX, mouseY) : null;
		if (name != null && !isMe(name)) {
			openWhisperWith(name);
			return true;
		}
		int index = listIndexAt(mouseX, mouseY);
		if (index >= 0) {
			select(visible.get(index));
			setFocused(inputField);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (picker.scroll(mouseX, mouseY, verticalAmount)) return true;
		if (inList(mouseX, mouseY)) {
			listScroll = MathHelper.clamp(listScroll - verticalAmount * itemH(), 0, maxListScroll());
			return true;
		}
		if (selected != null && mouseX >= chatLeft() && mouseX < right && mouseY >= msgTop() && mouseY < msgBottom()) {
			view.scroll(verticalAmount, msgBottom() - msgTop());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ---------------------------------------------------------------- 영역 계산

	private int listTop() {
		return top + HEADER_H + 1;
	}

	private int listBottom() {
		return bottom - FOOTER_H - 1;
	}

	private int chatLeft() {
		return left + LIST_W + 1;
	}

	private int msgTop() {
		return top + HEADER_H + 1;
	}

	private int msgBottom() {
		return bottom - FOOTER_H - 1;
	}

	private boolean inList(double x, double y) {
		return x >= left && x < left + LIST_W && y >= listTop() && y < listBottom();
	}

	// ---------------------------------------------------------------- 목록 글자 크기

	private static ModConfig.Sizes sizes() {
		return ModConfig.get().sizes;
	}

	/** 목록의 이름 글자 */
	private static float nameScale() {
		return sizes().listNameScale;
	}

	/** 목록의 미리보기, 시간, 배지, 구역 이름 글자 */
	private static float subScale() {
		return UiFont.small() * sizes().listSubScale;
	}

	/** 목록 한 칸: 위 여백 + 이름 + 줄 간격 + 미리보기 + 아래 여백 */
	private static int itemH() {
		return Math.max(MIN_ITEM_H, 4 + Math.round(9 * nameScale()) + 3 + UiFont.height(subScale()) + 5);
	}

	private static int sectionH() {
		return Math.max(MIN_SECTION_H, UiFont.height(subScale()) + 6);
	}

	/** 대화창 제목 글자 (머리글 높이를 넘지 않게) */
	private static float titleScale() {
		return Math.min(UiFont.title() * sizes().titleScale, (HEADER_H - 6) / 8f);
	}

	private boolean hasDms() {
		return visible.size() > channelCount;
	}

	/** 목록 안에서 i 번째 항목의 위쪽 y (스크롤 전). "채널", "대화" 구역 이름 줄만큼 밀린다. */
	private int itemOffset(int i) {
		int headers = (channelCount > 0 ? 1 : 0) + (i >= channelCount && hasDms() ? 1 : 0);
		return i * itemH() + headers * sectionH();
	}

	private int listIndexAt(double x, double y) {
		if (!inList(x, y)) return -1;
		double ly = y - listTop() + listScroll;
		for (int i = 0; i < visible.size(); i++) {
			int start = itemOffset(i);
			if (ly >= start && ly < start + itemH()) return i;
		}
		return -1;
	}

	private double maxListScroll() {
		int content = visible.isEmpty() ? 0 : itemOffset(visible.size() - 1) + itemH();
		return Math.max(0, content - (listBottom() - listTop()));
	}

	// ---------------------------------------------------------------- 그리기

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		Theme theme = Theme.get();
		// 창 모서리 둥글기: 네 귀퉁이에 닿는 칸만 그 모서리를 둥글게 그린다
		int r = ModConfig.get().sizes.cornerRadius;
		Shapes.fill(context, left, top, right, bottom, theme.windowBg, r);
		Shapes.fill(context, chatLeft(), top, right, bottom, theme.chatBg, r, false, true, false, true);

		// 위아래 띠(검색줄, 아래 버튼줄, 머리글, 입력줄)는 바탕보다 살짝 다른 색 + 경계선으로 구역을 나눈다
		int listBand = band(theme.windowBg, theme);
		Shapes.fill(context, left, top, left + LIST_W, top + HEADER_H, listBand, r, true, false, false, false);
		context.fill(left, top + HEADER_H, left + LIST_W, top + HEADER_H + 1, theme.divider);
		Shapes.fill(context, left, bottom - FOOTER_H, left + LIST_W, bottom, listBand, r, false, false, true, false);
		context.fill(left, bottom - FOOTER_H - 1, left + LIST_W, bottom - FOOTER_H, theme.divider);

		Shapes.fill(context, chatLeft(), top, right, top + HEADER_H, theme.headerBg, r, false, true, false, false);
		context.fill(chatLeft(), top + HEADER_H, right, top + HEADER_H + 1, theme.divider);
		Shapes.fill(context, chatLeft(), bottom - FOOTER_H, right, bottom, band(theme.chatBg, theme), r, false, false, false, true);
		context.fill(chatLeft(), bottom - FOOTER_H - 1, right, bottom - FOOTER_H, theme.divider);

		context.fill(left + LIST_W, top, chatLeft(), bottom, theme.divider);
		Shapes.ring(context, left - 1, top - 1, right + 1, bottom + 1, theme.divider, r, true, true, true, true);
	}

	/** 띠 색: 바탕에 글자색을 아주 조금 섞는다 (어두운 테마는 밝게, 밝은 테마는 어둡게) */
	private static int band(int bg, Theme theme) {
		return Theme.mix(bg, theme.text, 0.05f);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		renderList(context, mouseX, mouseY);
		renderChat(context, mouseX, mouseY);
		picker.render(context, textRenderer, mouseX, mouseY);
	}

	private void renderList(DrawContext context, int mouseX, int mouseY) {
		Theme theme = Theme.get();
		int x0 = left, x1 = left + LIST_W, y0 = listTop(), y1 = listBottom();

		if (visible.isEmpty()) {
			String key = searchField.getText().isBlank() ? "chattingplus.empty" : "chattingplus.empty.search";
			UiFont.drawCentered(context, textRenderer, Text.translatable(key).getString(), (x0 + x1) / 2, y0 + 10, theme.mutedText);
			return;
		}

		float sub = subScale();
		int hovered = picker.isOpen() ? -1 : listIndexAt(mouseX, mouseY);
		context.enableScissor(x0, y0, x1, y1);
		int scrollY = y0 - (int) listScroll;
		if (channelCount > 0) sectionLabel(context, theme, "chattingplus.list.channels", channelCount, x0, x1, scrollY);
		if (hasDms()) sectionLabel(context, theme, "chattingplus.list.whispers", visible.size() - channelCount, x0, x1, scrollY + itemOffset(channelCount) - sectionH());

		for (int i = 0; i < visible.size(); i++) {
			int y = scrollY + itemOffset(i);
			if (y + itemH() < y0 || y > y1) continue;
			Conversation c = visible.get(i);

			if (c == selected) {
				context.fill(x0, y, x1, y + itemH(), theme.selected());
				context.fill(x0, y, x0 + 2, y + itemH(), theme.selection()); // 고른 대화 왼쪽 표시
			} else if (i == hovered) {
				context.fill(x0, y, x1, y + itemH(), theme.hover());
			}

			boolean isChannel = c.channel() != null;
			float name = nameScale();
			int nameY = y + 4;
			int nameH = Math.round(9 * name);
			int subH = UiFont.height(sub);
			int subY = nameY + nameH + 3;
			int nameX = x0 + PAD;
			if (c.favorite) {
				UiFont.drawText(context, textRenderer, Text.literal("★"), nameX, nameY, COLOR_STAR, name, false);
				nameX += Math.round(textRenderer.getWidth("★") * name) + 2;
			}

			// 첫 줄: 이름 + 오른쪽 끝 시간 (시간은 이름 글자 아래쪽에 맞춘다)
			Message last = c.last();
			String time = last != null ? listTime(c.lastTime) : "";
			int timeW = UiFont.width(textRenderer, time, sub);
			int nameMax = x1 - PAD - timeW - 4 - nameX;
			// 귓속말 상대는 채널 채팅에서 본 이름표(등급, 닉네임 색)가 있으면 그 모양으로
			Text label = isChannel ? null : WhisperStore.get().label(c.name);
			if (label != null) {
				UiFont.drawStyled(context, textRenderer, label, nameX, nameY, nameMax, theme.text | 0xFF000000, name);
			} else {
				String text = UiFont.trim(textRenderer, c.displayName(), nameMax, name);
				int nameColor = c.unread > 0 ? theme.accent : isChannel ? theme.channelColor(c.channel()) : theme.text;
				UiFont.draw(context, textRenderer, text, nameX, nameY, nameColor, name);
			}
			UiFont.drawPlain(context, textRenderer, time, x1 - PAD - timeW, nameY + Math.round(8 * name) - subH, theme.mutedText, sub);

			// 둘째 줄: 미리보기 + 오른쪽 끝 안 읽음 배지
			int badgeW = c.unread > 0 ? drawBadge(context, theme, c.unread, x1 - PAD, subY, sub) : 0;
			if (last != null) {
				String who = last.outgoing ? "나: " : last.sender != null ? last.sender + ": " : "";
				// 이모티콘만 보낸 메시지는 미리보기에 "(이모티콘)"
				String body = Emotes.stickers(last.text) != null ? Text.translatable("chattingplus.emote.preview").getString() : last.text;
				String preview = UiFont.trim(textRenderer, who + body, LIST_W - PAD * 2 - badgeW - 4, sub);
				UiFont.drawPlain(context, textRenderer, preview, x0 + PAD, subY, theme.mutedText, sub);
			}
		}
		context.disableScissor();
	}

	/**
	 * 안 읽음 배지. 숫자 글자(7픽셀 높이)를 기준으로 위아래 여백을 똑같이 두고, 한 자리 수는 정사각형에 가깝게.
	 * textY 줄(미리보기 줄)과 같은 높이에 숫자가 오도록 그린다. 그린 폭을 돌려준다.
	 */
	private int drawBadge(DrawContext context, Theme theme, int unread, int right, int textY, float scale) {
		String count = unread > 99 ? "99+" : String.valueOf(unread);
		int digitH = Math.max(5, Math.round(7 * scale));
		int textW = UiFont.width(textRenderer, count, scale);
		int h = digitH + 4;
		int w = Math.max(h, textW + 6);
		int top = textY - 2;
		int left = right - w;
		UiButton.fillRounded(context, left, top, right, top + h, theme.accent | 0xFF000000);
		UiFont.drawPlain(context, textRenderer, count, left + (w - textW + 1) / 2, textY, 0xFFFFFFFF, scale);
		return w;
	}

	/** 구역 이름 줄: "채널 3 ───" (작은 글자 + 옅은 선) */
	private void sectionLabel(DrawContext context, Theme theme, String key, int count, int x0, int x1, int y) {
		float sub = subScale();
		String label = Text.translatable(key).getString() + " " + count;
		int w = UiFont.width(textRenderer, label, sub);
		int textY = y + (sectionH() - UiFont.height(sub)) / 2;
		UiFont.drawPlain(context, textRenderer, label, x0 + PAD, textY, theme.mutedText, sub);
		int lineY = textY + UiFont.height(sub) / 2;
		context.fill(x0 + PAD + w + 4, lineY, x1 - PAD, lineY + 1, theme.divider);
	}

	private void renderChat(DrawContext context, int mouseX, int mouseY) {
		Theme theme = Theme.get();
		int x0 = chatLeft() + PAD, x1 = right - PAD;
		int cx = (chatLeft() + right) / 2;

		if (selected == null) {
			UiFont.drawCentered(context, textRenderer, Text.translatable("chattingplus.pick").getString(), cx, (top + bottom) / 2 - 4, theme.mutedText);
			return;
		}

		boolean isChannel = selected.channel() != null;
		int nameMax = (isChannel ? channelHideButton : favoriteButton).getX() - 4 - x0;
		float title = titleScale();
		int titleY = top + (HEADER_H - UiFont.height(title)) / 2;
		Text label = isChannel ? null : WhisperStore.get().label(selected.name);
		if (label != null) {
			UiFont.drawStyled(context, textRenderer, label, x0, titleY, nameMax, theme.text | 0xFF000000, title);
		} else {
			String name = UiFont.trim(textRenderer, selected.displayName(), nameMax, title);
			// 굵게(BOLD) 그리면 한글 글리프가 번져 보이므로 굵게 대신 제목 크기로
			UiFont.draw(context, textRenderer, name, x0, titleY, isChannel ? theme.channelColor(selected.channel()) : theme.text, title);
		}

		int y0 = msgTop(), y1 = msgBottom();
		view.setMouse(picker.isOpen() ? -1 : mouseX, picker.isOpen() ? -1 : mouseY);
		view.render(context, selected, x0, y0, x1 - 4, y1);
		if (view.isEmpty()) {
			String key = isChannel ? "chattingplus.no_messages.channel" : "chattingplus.no_messages";
			UiFont.drawCentered(context, textRenderer, Text.translatable(key, selected.displayName()).getString(), cx, (y0 + y1) / 2 - 4, theme.mutedText);
		}
	}

	private static String listTime(long millis) {
		ZonedDateTime when = Instant.ofEpochMilli(millis).atZone(ConversationView.ZONE);
		return when.toLocalDate().equals(LocalDate.now(ConversationView.ZONE)) ? ConversationView.TIME.format(when) : LIST_DATE.format(when);
	}
}
