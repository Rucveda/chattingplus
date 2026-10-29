package com.chattingplus;

import com.chattingplus.WhisperStore.Conversation;
import com.chattingplus.WhisperStore.Message;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * 설정 화면. 왼쪽은 위아래로 스크롤되는 목록이고 테마 / 크기 / 글꼴 구역이 각각 카드로 묶여 있다. 오른쪽은 미리보기.
 * 테마와 글꼴은 누르면 아래로 펼쳐지는 목록에서 고르고, 색은 견본을 눌러 색 고르는 창에서 고른다.
 * 미리 만든 테마는 바꿀 수 없고, 복사해서 새 테마를 만들어 고친다.
 */
public class SettingsScreen extends Screen {
	private static final int PAD = 8;
	private static final int LEFT_W = 250;
	private static final int TOP = 24;
	/** 색 한 칸: 이름(작은 글자) + 견본 */
	private static final int ROW_H = 22;
	private static final int STEP = 20;
	private static final int OPTION_H = 14;
	/** 카드 안쪽 여백, 카드 머리글 높이, 카드 사이 간격 */
	private static final int CARD_PAD = 5;
	private static final int CARD_HEADER = 18;
	private static final int CARD_GAP = 6;
	/** 색 견본은 정사각형. 오른쪽에 이름, 그 아래 색 코드 */
	private static final int SWATCH_W = 16;
	private static final int SWATCH_H = 16;
	private static final int LABEL_GAP = 6;
	/** 견본과 이름까지 눌리는 범위 */
	private static final int SWATCH_HIT_W = SWATCH_W + LABEL_GAP + 64;
	/** 글자 종류. 색은 그릴 때 지금 테마에서 정한다 (색을 바꾸면 바로 반영되게) */
	private static final int ROLE_SECTION = 0;
	private static final int ROLE_LABEL = 1;
	private static final int ROLE_HINT = 2;

	private record ColorField(String key, ToIntFunction<Theme> get, ObjIntConsumer<Theme> set) {}

	/** 색 설정 묶음 (창 / 글자 / 말풍선 / 채널 이름) */
	private record ColorGroup(String key, List<ColorField> fields) {}

	private static final List<ColorGroup> COLOR_GROUPS = List.of(
			new ColorGroup("window", List.of(
					new ColorField("window_bg", t -> t.windowBg, (t, v) -> t.windowBg = v),
					new ColorField("chat_bg", t -> t.chatBg, (t, v) -> t.chatBg = v),
					new ColorField("header_bg", t -> t.headerBg, (t, v) -> t.headerBg = v),
					new ColorField("divider", t -> t.divider, (t, v) -> t.divider = v))),
			new ColorGroup("text", List.of(
					new ColorField("text", t -> t.text, (t, v) -> t.text = v),
					new ColorField("muted", t -> t.mutedText, (t, v) -> t.mutedText = v),
					new ColorField("accent", t -> t.accent, (t, v) -> t.accent = v),
					new ColorField("selection", Theme::selection, (t, v) -> t.selectAccent = v))),
			new ColorGroup("bubble", List.of(
					new ColorField("mine_bubble", t -> t.mineBubble, (t, v) -> t.mineBubble = v),
					new ColorField("mine_text", t -> t.mineText, (t, v) -> t.mineText = v),
					new ColorField("theirs_bubble", t -> t.theirsBubble, (t, v) -> t.theirsBubble = v),
					new ColorField("theirs_text", t -> t.theirsText, (t, v) -> t.theirsText = v))),
			new ColorGroup("channel", List.of(
					new ColorField("channel_global", t -> t.channelColor(Channel.GLOBAL), (t, v) -> t.setChannelColor(Channel.GLOBAL, v)),
					new ColorField("channel_local", t -> t.channelColor(Channel.LOCAL), (t, v) -> t.setChannelColor(Channel.LOCAL, v)),
					new ColorField("channel_village", t -> t.channelColor(Channel.VILLAGE), (t, v) -> t.setChannelColor(Channel.VILLAGE, v)))));

	/** 묶음 이름 옆의 얇은 선 */
	private record Rule(int x0, int x1, int baseY) {}

	/** 스크롤되는 목록 안의 위젯과 글자. baseY 는 목록 맨 위 기준 */
	private record Placed(ClickableWidget widget, int baseY) {}

	private record Label(OrderedText text, int x, int baseY, int role, float scale) {}

	private record Swatch(ColorField field, int x, int baseY) {}

	/** 구역 카드: 머리글 글자와 위아래 범위 */
	private record Card(String titleKey, int baseTop, int baseBottom) {}

	/** 펼쳐진 목록의 한 줄 */
	private record Option(String label, boolean current, Runnable pick, Runnable delete) {
		Option(String label, boolean current, Runnable pick) {
			this(label, current, pick, null);
		}
	}

	/** 목록 줄 오른쪽 끝의 지우기(×) 칸 너비 */
	private static final int DELETE_W = 14;

	/** 다시 열거나 테마를 바꿔 화면을 새로 그려도 스크롤 위치 유지 */
	private static double scroll;

	private final Screen parent;
	private int left, top, right, bottom;
	private ConversationView preview;
	private Conversation sample;

	private final List<Placed> placed = new ArrayList<>();
	private final List<Label> labels = new ArrayList<>();
	private final List<Swatch> swatches = new ArrayList<>();
	private final List<Rule> rules = new ArrayList<>();
	private final List<Card> cards = new ArrayList<>();
	private final List<ClickableWidget> fixed = new ArrayList<>();
	private int contentHeight;

	private ButtonWidget dropdownAnchor;
	private List<Option> dropdownOptions;
	private final ColorPicker colorPicker = new ColorPicker();

	/** 대화 하나의 배경만 정하는 화면이면 그 대화 (채널이나 즐겨찾기 귓속말). 전체 설정이면 null */
	private final Conversation target;
	private ButtonWidget resetBackground;

	public SettingsScreen(Screen parent) {
		this(parent, null);
	}

	/** target 이 있으면 그 대화만의 배경을 정하는 화면 (배경 카드만 있고, 미리보기는 그 대화) */
	public SettingsScreen(Screen parent, Conversation target) {
		super(target == null ? Text.translatable("chattingplus.settings")
				: Text.translatable("chattingplus.background.title", target.displayName()));
		this.parent = parent;
		this.target = target;
	}

	// ---------------------------------------------------------------- 영역

	private int viewTop() {
		return top + TOP;
	}

	private int viewBottom() {
		return bottom - PAD;
	}

	private int cardLeft() {
		return left + PAD - 2;
	}

	private int cardRight() {
		return left + LEFT_W - 2;
	}

	private int viewRight() {
		return left + LEFT_W;
	}

	private boolean inView(double mx, double my) {
		return mx >= left && mx < viewRight() + 4 && my >= viewTop() && my < viewBottom();
	}

	private int previewLeft() {
		return viewRight() + PAD + 4;
	}

	private int screenY(int baseY) {
		return viewTop() + baseY - (int) scroll;
	}

	// ---------------------------------------------------------------- 만들기

	@Override
	protected void init() {
		if (preview == null) preview = new ConversationView(textRenderer);
		if (sample == null) sample = target != null ? target : sampleConversation();
		int panelW = Math.min(width - 12, 480);
		int panelH = Math.min(height - 12, 300);
		left = (width - panelW) / 2;
		top = (height - panelH) / 2;
		right = left + panelW;
		bottom = top + panelH;

		placed.clear();
		resetBackground = null;
		labels.clear();
		swatches.clear();
		rules.clear();
		cards.clear();
		fixed.clear();
		dropdownOptions = null;
		dropdownAnchor = null;
		colorPicker.close();

		// 카드 안쪽 내용의 왼쪽과 폭
		int x = cardLeft() + CARD_PAD, w = cardRight() - CARD_PAD - x;
		int y = 0;
		if (target != null) {
			y = card("chattingplus.settings.section.background", y, cy -> buildBackground(x, w, cy));
		} else {
			y = card("chattingplus.settings.section.theme", y, cy -> buildTheme(x, w, cy));
			y = card("chattingplus.settings.section.sizes", y + CARD_GAP, cy -> buildSizes(x, w, cy));
			y = card("chattingplus.settings.section.font", y + CARD_GAP, cy -> buildFont(x, w, cy));
			y = card("chattingplus.settings.section.background", y + CARD_GAP, cy -> buildBackground(x, w, cy));
			y = card("chattingplus.settings.section.share", y + CARD_GAP, cy -> buildShare(x, w, cy));
		}
		contentHeight = y + 2;

		ButtonWidget done = UiButton.create(Text.translatable("gui.done"), b -> close())
				.dimensions(right - PAD - 90, bottom - PAD - 16, 90, 16).build();
		addDrawableChild(done);
		fixed.add(done);

		applyScroll();
	}

	private interface Section {
		int build(int contentTop);
	}

	/** 구역 하나를 카드로 감싼다. 돌려주는 값은 카드 아래 끝 */
	private int card(String titleKey, int baseTop, Section section) {
		int end = section.build(baseTop + CARD_HEADER) + CARD_PAD;
		cards.add(new Card(titleKey, baseTop, end));
		return end;
	}

	private <T extends ClickableWidget> T place(T widget, int baseY) {
		addSelectableChild(widget);
		placed.add(new Placed(widget, baseY));
		return widget;
	}

	private int hint(String key, int x, int w, int y) {
		for (OrderedText line : textRenderer.wrapLines(Text.translatable(key), (int) (w / UiFont.small()))) {
			labels.add(new Label(line, x, y, ROLE_HINT, UiFont.small()));
			y += 8;
		}
		return y;
	}

	/** 테마: [테마 목록 ▾] / 이름·삭제 또는 복사 버튼 / 글자 그림자, 버튼 모양 / 색 11가지 */
	private int buildTheme(int x, int w, int y) {
		Theme theme = Theme.get();

		ButtonWidget dropdown = place(UiButton.create(Text.literal(theme.name + "  ▾"), b -> toggleDropdown(b, themeOptions()))
				.dimensions(x, 0, w, 16).build(), y);
		y += STEP;

		if (theme.editable()) {
			TextFieldWidget name = place(new UiTextField(textRenderer, x, 0, w - 52, 16, Text.translatable("chattingplus.settings.theme.name")), y);
			name.setMaxLength(24);
			name.setText(theme.name);
			name.setChangedListener(s -> {
				if (s.isBlank()) return;
				theme.name = s.trim();
				ModConfig.save();
				dropdown.setMessage(Text.literal(theme.name + "  ▾"));
			});
			place(UiButton.create(Text.translatable("chattingplus.delete"), b -> confirmDeleteTheme(theme))
					.dimensions(x + w - 48, 0, 48, 16).small().build(), y);
		} else {
			place(UiButton.create(Text.translatable("chattingplus.settings.theme.copy"), b -> {
						Theme.create(theme, newThemeName());
						ModConfig.save();
						clearAndInit();
					})
					.dimensions(x, 0, w, 16)
					.build(), y);
		}
		y += STEP;

		// 세부 설정은 한 줄에 둘, 글자는 한 단계 작게
		int half = (w - 2) / 2;
		ButtonWidget shadow = place(UiButton.create(shadowLabel(), b -> {
			theme.textShadow = !theme.textShadow;
			ModConfig.save();
			b.setMessage(shadowLabel());
		}).dimensions(x, 0, half, 14).small().build(), y);
		shadow.active = theme.editable();
		ButtonWidget widgets = place(UiButton.create(widgetLabel(), b -> {
			theme.vanillaWidgets = !theme.vanillaWidgets;
			ModConfig.save();
			b.setMessage(widgetLabel());
		}).dimensions(x + half + 2, 0, half, 14).small().build(), y);
		widgets.active = theme.editable();
		y += 20;

		// 색은 묶음마다 "이름 ───" 줄 아래에 두 칸씩
		int colW = (w + 4) / 2;
		float small = UiFont.small();
		for (ColorGroup group : COLOR_GROUPS) {
			y = groupHeading(group.key, x, w, y);
			List<ColorField> fields = group.fields;
			for (int i = 0; i < fields.size(); i++) {
				ColorField field = fields.get(i);
				int cx = x + (i % 2) * colW;
				int cy = y + (i / 2) * ROW_H;
				labels.add(new Label(Text.translatable("chattingplus.settings.color." + field.key).asOrderedText(), cx + SWATCH_W + LABEL_GAP, cy, ROLE_LABEL, small));
				swatches.add(new Swatch(field, cx, cy));
			}
			y += ((fields.size() + 1) / 2) * ROW_H + 2;
		}
		y = hint(theme.editable() ? "chattingplus.settings.theme.pick_hint" : "chattingplus.settings.theme.locked", x, w, y);
		return y;
	}

	/** 묶음 이름 줄: "이름 ───" (작은 글자 + 옅은 선). 다음 줄의 y 를 돌려준다 */
	private int groupHeading(String key, int x, int w, int y) {
		float small = UiFont.small();
		String title = Text.translatable("chattingplus.settings.group." + key).getString();
		labels.add(new Label(UiFont.text(title).asOrderedText(), x, y, ROLE_HINT, small));
		rules.add(new Rule(x + UiFont.width(textRenderer, title, small) + 4, x + w, y + UiFont.height(small) / 2));
		return y + 11;
	}

	/** 크기: 영역별로 묶어서 (목록 / 대화창 / 팝업 / 창) */
	private int buildSizes(int x, int w, int y) {
		ModConfig.Sizes s = ModConfig.get().sizes;
		DoubleFunction<String> times = v -> String.format(Locale.ROOT, "%.2f", v) + "x";
		DoubleFunction<String> px = v -> (int) v + "px";

		y = groupHeading("list", x, w, y);
		slider(x, y, w, "list_name", 0.75, 1.5, 0.125, s.listNameScale, v -> s.listNameScale = (float) v, times);
		slider(x, y += STEP, w, "list_sub", 0.75, 1.5, 0.125, s.listSubScale, v -> s.listSubScale = (float) v, times);
		y += STEP + 4;

		y = groupHeading("chat", x, w, y);
		slider(x, y, w, "title", 0.75, 1.5, 0.125, s.titleScale, v -> s.titleScale = (float) v, times);
		slider(x, y += STEP, w, "text_scale", 0.5, 2.0, 0.125, s.textScale, v -> s.textScale = (float) v, times);
		slider(x, y += STEP, w, "meta", 0.75, 1.5, 0.125, s.metaScale, v -> s.metaScale = (float) v, times);
		slider(x, y += STEP, w, "padding", 1, 10, 1, s.bubblePadding, v -> s.bubblePadding = (int) v, px);
		slider(x, y += STEP, w, "gap", 0, 12, 1, s.messageGap, v -> s.messageGap = (int) v, px);
		slider(x, y += STEP, w, "sticker", 24, 96, 4, s.stickerSize, v -> s.stickerSize = (int) v, px);
		y += STEP + 4;

		y = groupHeading("popup", x, w, y);
		slider(x, y, w, "popup_scale", 0.75, 1.5, 0.125, s.popupScale, v -> s.popupScale = (float) v, times);
		slider(x, y += STEP, w, "popup_opacity", 10, 100, 5, s.popupOpacity, v -> s.popupOpacity = (int) v, v -> (int) v + "%");
		y += STEP + 4;

		y = groupHeading("frame", x, w, y);
		slider(x, y, w, "corner_radius", 0, 10, 1, s.cornerRadius, v -> s.cornerRadius = (int) v, px);
		return y + 16;
	}

	/** 공유: [설정 코드 복사] [코드 붙여넣기] */
	private int buildShare(int x, int w, int y) {
		int half = (w - 2) / 2;
		place(UiButton.create(Text.translatable("chattingplus.settings.share.copy"), b -> {
			if (client == null) return;
			client.keyboard.setClipboard(SettingsShare.export());
			toast(Text.translatable("chattingplus.settings.share.copied"));
		}).dimensions(x, 0, half, 16).build(), y);
		place(UiButton.create(Text.translatable("chattingplus.settings.share.paste"), b -> {
			if (client == null) return;
			try {
				SettingsShare.Imported imported = SettingsShare.apply(client.keyboard.getClipboard());
				toast(Text.translatable("chattingplus.settings.share.imported", imported.themeName()));
				clearAndInit();
			} catch (IllegalArgumentException e) {
				toast(Text.translatable("chattingplus.settings.share.invalid"));
			}
		}).dimensions(x + half + 2, 0, half, 16).build(), y);
		y += STEP;
		return y - 2;
	}

	private void toast(Text message) {
		if (client == null) return;
		SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION, Text.translatable("chattingplus.settings"), message);
	}

	/**
	 * 배경 그림: [그림 목록 ▾] / [폴더 열기] [새로고침] / 진하기·크기·위치.
	 * 전체 설정이면 아래에 [메인 창] [팝업], 대화 하나의 배경이면 [기본 설정 따르기]
	 */
	private int buildBackground(int x, int w, int y) {
		ModConfig cfg = ModConfig.get();
		ModConfig.Background bg = background();
		place(UiButton.create(Text.literal(backgroundName() + "  ▾"), b -> toggleDropdown(b, backgroundOptions()))
				.dimensions(x, 0, w, 16)
				.build(), y);
		y += STEP;
		int half = (w - 2) / 2;
		place(UiButton.create(Text.translatable("chattingplus.settings.background.folder"), b -> ChatBackground.openFolder())
				.dimensions(x, 0, half, 14).small()
				.build(), y);
		place(UiButton.create(Text.translatable("chattingplus.settings.background.reload"), b -> {
					ChatBackground.reload();
					toast(Text.translatable("chattingplus.settings.background.loaded", ChatBackground.files().size()));
					clearAndInit();
				})
				.dimensions(x + half + 2, 0, half, 14).small()
				.build(), y);
		y += 18;
		backgroundSlider(x, y, w, "background.opacity", 10, 100, bg.opacity, (b, v) -> b.opacity = v);
		backgroundSlider(x, y += STEP, w, "background.zoom", 30, 300, bg.zoom, (b, v) -> b.zoom = v);
		backgroundSlider(x, y += STEP, w, "background.x", 0, 100, bg.x, (b, v) -> b.x = v);
		backgroundSlider(x, y += STEP, w, "background.y", 0, 100, bg.y, (b, v) -> b.y = v);
		y += STEP;
		if (target != null) {
			// 대화 하나의 배경: 따로 정한 게 있으면 지우고 기본 배경으로 돌아가는 버튼
			// (눌러지는지는 그릴 때마다 맞춘다: 슬라이더를 처음 움직이면 그때 따로 정한 배경이 생긴다)
			resetBackground = place(UiButton.create(Text.translatable("chattingplus.background.reset"), b -> {
				WhisperStore.get().setBackground(target, null);
				clearAndInit();
			}).dimensions(x, 0, w, 14).small().build(), y);
			y += 18;
			return y - 2;
		}
		place(UiButton.create(backgroundToggle("main", cfg.backgroundMain), b -> {
			cfg.backgroundMain = !cfg.backgroundMain;
			ModConfig.save();
			b.setMessage(backgroundToggle("main", cfg.backgroundMain));
		}).dimensions(x, 0, half, 14).small().build(), y);
		place(UiButton.create(backgroundToggle("popup", cfg.backgroundPopup), b -> {
			cfg.backgroundPopup = !cfg.backgroundPopup;
			ModConfig.save();
			b.setMessage(backgroundToggle("popup", cfg.backgroundPopup));
		}).dimensions(x + half + 2, 0, half, 14).small().build(), y);
		y += 18;
		return y - 2;
	}

	/** 지금 고치는 배경: 대화 하나의 화면이면 그 대화에 쓰는 배경(아직 따로 없으면 기본 배경), 아니면 기본 배경 */
	private ModConfig.Background background() {
		return target != null ? ChatBackground.of(target) : ModConfig.get().background;
	}

	/**
	 * 배경 설정을 바꾼다. 대화 하나의 화면에서 처음 바꿀 때는 기본 배경을 복사해 그 대화만의 배경을 만든다
	 * (그래서 기본 배경은 그대로 남는다)
	 */
	private void editBackground(java.util.function.Consumer<ModConfig.Background> change) {
		if (target == null) {
			change.accept(ModConfig.get().background);
			ModConfig.save();
			return;
		}
		ModConfig.Background own = target.background != null ? target.background : ModConfig.get().background.copy();
		change.accept(own);
		WhisperStore.get().setBackground(target, own);
	}

	private void backgroundSlider(int x, int y, int w, String key, int min, int max, int current, ObjIntConsumer<ModConfig.Background> set) {
		place(new ValueSlider(x, 0, w, Text.translatable("chattingplus.settings." + key), min, max, 5, current,
				v -> editBackground(b -> set.accept(b, (int) v)), v -> (int) v + "%"), y);
	}

	private String backgroundName() {
		String name = background().image;
		return name.isEmpty() ? Text.translatable("chattingplus.settings.background.none").getString() : name;
	}

	private static Text backgroundToggle(String where, boolean on) {
		return Text.translatable("chattingplus.settings.background." + where,
				Text.translatable(on ? "chattingplus.settings.on" : "chattingplus.settings.off"));
	}

	/** 글꼴: [글꼴 목록 ▾] / [폴더 열기] [새로고침] / 안내 */
	private int buildFont(int x, int w, int y) {
		place(UiButton.create(Text.literal(UiFont.nameOf(ModConfig.get().font) + "  ▾"), b -> toggleDropdown(b, fontOptions()))
				.dimensions(x, 0, w, 16)
				.build(), y);
		y += STEP;
		int half = (w - 2) / 2;
		place(UiButton.create(Text.translatable("chattingplus.settings.font.folder"), b -> UiFont.openFontsFolder())
				.dimensions(x, 0, half, 14).small()
				.build(), y);
		place(UiButton.create(Text.translatable("chattingplus.settings.font.reload"), b -> {
					int count = UiFont.syncAndReload();
					if (client != null) {
						SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
								Text.translatable("chattingplus.settings"), Text.translatable("chattingplus.settings.font.loaded", count));
					}
				})
				.dimensions(x + half + 2, 0, half, 14).small()
				.build(), y);
		y += 18;
		return hint("chattingplus.settings.font.hint", x, w, y);
	}

	private void slider(int x, int y, int w, String key, double min, double max, double step, double current,
						DoubleConsumer apply, DoubleFunction<String> format) {
		place(new ValueSlider(x, 0, w, Text.translatable("chattingplus.settings." + key), min, max, step, current, v -> {
			apply.accept(v);
			ModConfig.save();
		}, format), y);
	}

	private Text shadowLabel() {
		return Text.translatable(Theme.get().textShadow ? "chattingplus.settings.shadow.on" : "chattingplus.settings.shadow.off");
	}

	private Text widgetLabel() {
		return Text.translatable(Theme.get().vanillaWidgets ? "chattingplus.settings.widgets.vanilla" : "chattingplus.settings.widgets.themed");
	}

	private static int roleColor(Theme theme, int role) {
		return switch (role) {
			case ROLE_SECTION, ROLE_LABEL -> theme.text | 0xFF000000;
			default -> theme.mutedText | 0xFF000000;
		};
	}

	private static String newThemeName() {
		return Text.translatable("chattingplus.settings.theme.new_name", ModConfig.get().customThemes.size() + 1).getString();
	}

	private void confirmDeleteTheme(Theme theme) {
		if (client == null) return;
		client.setScreen(new ConfirmScreen(ok -> {
			if (ok) Theme.delete(theme);
			client.setScreen(this);
		}, Text.translatable("chattingplus.settings.theme.delete.title"), Text.translatable("chattingplus.settings.theme.delete.message", theme.name)));
	}

	// ---------------------------------------------------------------- 펼쳐지는 목록

	private List<Option> themeOptions() {
		List<Option> options = new ArrayList<>();
		Theme current = Theme.get();
		for (Theme t : Theme.all()) {
			// 직접 만든 테마는 줄 끝 ×로 바로 지울 수 있다
			options.add(new Option(t.name, t == current, () -> {
				Theme.select(t.id);
				clearAndInit();
			}, t.editable() ? () -> confirmDeleteTheme(t) : null));
		}
		options.add(new Option(Text.translatable("chattingplus.settings.theme.add").getString(), false, () -> {
			Theme.create(Theme.get(), newThemeName());
			ModConfig.save();
			clearAndInit();
		}));
		return options;
	}

	private List<Option> fontOptions() {
		List<Option> options = new ArrayList<>();
		for (UiFont.Choice c : UiFont.choices()) {
			options.add(new Option(c.name(), c.id().equals(ModConfig.get().font), () -> {
				ModConfig.get().font = c.id();
				ModConfig.save();
				clearAndInit();
			}));
		}
		return options;
	}

	private List<Option> backgroundOptions() {
		List<Option> options = new ArrayList<>();
		String current = background().image;
		options.add(new Option(Text.translatable("chattingplus.settings.background.none").getString(), current.isEmpty(), () -> pickBackground("")));
		for (String file : ChatBackground.files()) {
			options.add(new Option(file, file.equals(current), () -> pickBackground(file)));
		}
		return options;
	}

	private void pickBackground(String file) {
		editBackground(b -> b.image = file);
		clearAndInit();
	}

	private void toggleDropdown(ButtonWidget anchor, List<Option> options) {
		if (dropdownAnchor == anchor) {
			dropdownAnchor = null;
			dropdownOptions = null;
		} else {
			dropdownAnchor = anchor;
			dropdownOptions = options;
		}
	}

	private static boolean onDeleteSlot(ButtonWidget anchor, double mx) {
		return mx >= anchor.getX() + anchor.getWidth() - DELETE_W;
	}

	/** 펼친 목록의 위쪽 y. 아래 공간이 모자라면 버튼 위로 펼친다. */
	private int dropdownTop() {
		int h = dropdownOptions.size() * OPTION_H + 2;
		int below = dropdownAnchor.getY() + dropdownAnchor.getHeight();
		return below + h <= height ? below : dropdownAnchor.getY() - h;
	}

	private int dropdownIndexAt(double mx, double my) {
		if (dropdownOptions == null) return -1;
		int y0 = dropdownTop() + 1;
		if (mx < dropdownAnchor.getX() || mx >= dropdownAnchor.getX() + dropdownAnchor.getWidth() || my < y0) return -1;
		int index = (int) ((my - y0) / OPTION_H);
		return index < dropdownOptions.size() ? index : -1;
	}

	// ---------------------------------------------------------------- 색 견본

	private Swatch swatchAt(double mx, double my) {
		if (!inView(mx, my)) return null;
		for (Swatch s : swatches) {
			int sy = screenY(s.baseY);
			if (mx >= s.x && mx < s.x + SWATCH_HIT_W && my >= sy && my < sy + SWATCH_H) return s;
		}
		return null;
	}

	private void openColorPicker(Swatch s) {
		Theme theme = Theme.get();
		if (!theme.editable()) return;
		colorPicker.open(s.field.get.applyAsInt(theme), s.x, screenY(s.baseY) + SWATCH_H, width, height,
				color -> s.field.set.accept(theme, color));
	}

	// ---------------------------------------------------------------- 스크롤

	private int maxScroll() {
		return Math.max(0, contentHeight - (viewBottom() - viewTop()));
	}

	/** 스크롤 위치에 맞춰 위젯을 옮기고, 보이는 영역 밖이면 숨긴다 */
	private void applyScroll() {
		scroll = MathHelper.clamp(scroll, 0, maxScroll());
		for (Placed p : placed) {
			ClickableWidget widget = p.widget;
			int y = screenY(p.baseY);
			widget.setY(y);
			widget.visible = y + widget.getHeight() > viewTop() && y < viewBottom();
		}
	}

	// ---------------------------------------------------------------- 입력

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (colorPicker.isOpen()) {
			if (colorPicker.mouseClicked(mouseX, mouseY)) return true;
			colorPicker.close();
			ModConfig.save();
			Swatch other = swatchAt(mouseX, mouseY);
			if (other != null) openColorPicker(other);
			return true;
		}
		if (dropdownOptions != null) {
			int index = dropdownIndexAt(mouseX, mouseY);
			boolean onAnchor = dropdownAnchor.isMouseOver(mouseX, mouseY);
			List<Option> options = dropdownOptions;
			ButtonWidget anchor = dropdownAnchor;
			dropdownOptions = null;
			dropdownAnchor = null;
			if (index >= 0) {
				Option option = options.get(index);
				if (option.delete != null && onDeleteSlot(anchor, mouseX)) option.delete.run();
				else option.pick.run();
			}
			if (index >= 0 || onAnchor) return true;
		}
		Swatch swatch = swatchAt(mouseX, mouseY);
		if (swatch != null) {
			openColorPicker(swatch);
			return true;
		}
		if (inView(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
		// 스크롤 영역 밖에서는 잘려서 안 보이는 위젯이 눌리지 않게 고정 버튼만 받는다
		for (ClickableWidget widget : fixed) {
			if (widget.mouseClicked(mouseX, mouseY, button)) return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (colorPicker.mouseDragged(mouseX, mouseY)) return true;
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (colorPicker.mouseReleased()) ModConfig.save();
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (dropdownOptions != null || colorPicker.isOpen()) return true;
		if (mouseX >= left && mouseX < viewRight() + 4) {
			scroll -= verticalAmount * 24;
			applyScroll();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && (dropdownOptions != null || colorPicker.isOpen())) {
			dropdownOptions = null;
			dropdownAnchor = null;
			if (colorPicker.isOpen()) {
				colorPicker.close();
				ModConfig.save();
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	// ---------------------------------------------------------------- 그리기

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		// 설정 화면도 지금 테마 색으로 (고른 테마가 어떻게 보일지 바로 알 수 있게)
		Theme theme = Theme.get();
		context.fill(left, top, right, bottom, theme.windowBg | 0xF0000000);
		context.fill(left, top, right, top + TOP - 4, Theme.mix(theme.windowBg | 0xFF000000, theme.text, 0.05f));
		context.fill(left, top + TOP - 4, right, top + TOP - 3, theme.divider | 0xFF000000);
		context.drawBorder(left - 1, top - 1, right - left + 2, bottom - top + 2, theme.divider);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		Theme theme = Theme.get();
		UiFont.drawText(context, textRenderer, getTitle(), left + PAD, top + (TOP - 4 - UiFont.height(UiFont.title())) / 2 + 1,
				theme.text | 0xFF000000, UiFont.title(), theme.textShadow);

		// 펼친 목록이나 색 고르는 창이 있으면 뒤 위젯에 마우스가 올라간 것처럼 보이지 않게
		boolean overlay = dropdownOptions != null || colorPicker.isOpen();
		int mx = overlay ? -1 : mouseX, my = overlay ? -1 : mouseY;
		context.enableScissor(left, viewTop(), viewRight() + 4, viewBottom());
		for (Card card : cards) renderCard(context, theme, card);
		if (resetBackground != null) resetBackground.active = target != null && target.background != null;
		int ruleColor = Theme.mix(theme.windowBg | 0xFF000000, theme.text, 0.15f);
		for (Rule rule : rules) {
			int ry = screenY(rule.baseY);
			context.fill(rule.x0, ry, rule.x1, ry + 1, ruleColor);
		}
		for (Label label : labels) {
			UiFont.drawText(context, textRenderer, label.text, label.x, screenY(label.baseY), roleColor(theme, label.role), label.scale, false);
		}
		Swatch hoveredSwatch = overlay ? null : swatchAt(mouseX, mouseY);
		for (Swatch s : swatches) renderSwatch(context, theme, s, s == hoveredSwatch);
		for (Placed p : placed) {
			if (p.widget.visible) p.widget.render(context, mx, my, delta);
		}
		context.disableScissor();

		// 스크롤바
		int viewH = viewBottom() - viewTop();
		if (contentHeight > viewH) {
			int barH = Math.max(16, viewH * viewH / contentHeight);
			int barY = viewTop() + (int) ((viewH - barH) * (scroll / maxScroll()));
			context.fill(viewRight() + 1, barY, viewRight() + 3, barY + barH, Theme.withAlpha(theme.text, 0x80));
		}

		renderPreview(context, theme);
		renderDropdown(context, mouseX, mouseY);
		colorPicker.render(context, textRenderer);
	}


	/** 구역 카드: 바탕보다 살짝 다른 색의 둥근 판 + 왼쪽 강조 막대가 붙은 구역 이름 */
	private void renderCard(DrawContext context, Theme theme, Card card) {
		int y0 = screenY(card.baseTop), y1 = screenY(card.baseBottom);
		if (y1 < viewTop() || y0 > viewBottom()) return;
		int base = theme.windowBg | 0xFF000000;
		UiButton.fillRounded(context, cardLeft(), y0, cardRight(), y1, Theme.mix(base, theme.text, 0.045f));
		context.fill(cardLeft() + 1, y0 + CARD_HEADER - 3, cardRight() - 1, y0 + CARD_HEADER - 2, Theme.mix(base, theme.text, 0.1f));
		context.fill(cardLeft() + CARD_PAD, y0 + 4, cardLeft() + CARD_PAD + 2, y0 + 12, theme.selection() | 0xFF000000);
		UiFont.drawText(context, textRenderer, Text.translatable(card.titleKey), cardLeft() + CARD_PAD + 6, y0 + 4,
				roleColor(theme, ROLE_SECTION), UiFont.NORMAL, false);
	}

	/** 색 견본: 체크무늬 위에 색을 칠해 투명도도 보이게, 옆에 작은 색 코드 */
	private void renderSwatch(DrawContext context, Theme theme, Swatch s, boolean hovered) {
		int sx = s.x, sy = screenY(s.baseY);
		int color = s.field.get.applyAsInt(theme);
		for (int cx = 0; cx < SWATCH_W; cx += 4) {
			for (int cy = 0; cy < SWATCH_H; cy += 4) {
				int checker = ((cx + cy) / 4) % 2 == 0 ? 0xFFFFFFFF : 0xFFBBBBBB;
				context.fill(sx + cx, sy + cy, sx + Math.min(SWATCH_W, cx + 4), sy + Math.min(SWATCH_H, cy + 4), checker);
			}
		}
		context.fill(sx, sy, sx + SWATCH_W, sy + SWATCH_H, color);
		int border = hovered && theme.editable() ? theme.selection() | 0xFF000000 : Theme.mix(theme.divider | 0xFF000000, theme.text, 0.3f);
		context.drawBorder(sx - 1, sy - 1, SWATCH_W + 2, SWATCH_H + 2, border);
		String hex = (color >>> 24) == 0xFF ? String.format("%06X", color & 0xFFFFFF) : String.format("%08X", color);
		UiFont.drawPlain(context, textRenderer, hex, sx + SWATCH_W + LABEL_GAP, sy + 9, theme.mutedText | 0xFF000000, UiFont.small());
	}

	private void renderDropdown(DrawContext context, int mouseX, int mouseY) {
		if (dropdownOptions == null) return;
		int x = dropdownAnchor.getX(), w = dropdownAnchor.getWidth();
		int y = dropdownTop();
		int h = dropdownOptions.size() * OPTION_H + 2;
		// 글자는 모아서 나중에 그려지므로, 먼저 그린 것을 내보내고 앞쪽(z)에 그려야 뒤 버튼 글자가 비치지 않는다
		context.draw();
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 400);
		Theme theme = Theme.get();
		context.fill(x, y, x + w, y + h, theme.windowBg | 0xFF000000);
		context.drawBorder(x, y, w, h, theme.divider | 0xFF000000);
		int hovered = dropdownIndexAt(mouseX, mouseY);
		for (int i = 0; i < dropdownOptions.size(); i++) {
			Option option = dropdownOptions.get(i);
			int oy = y + 1 + i * OPTION_H;
			if (i == hovered) context.fill(x + 1, oy, x + w - 1, oy + OPTION_H, theme.hover());
			String label = (option.current ? "✔ " : "   ") + option.label;
			context.drawText(textRenderer, label, x + 5, oy + 3, (option.current ? theme.selection() : theme.text) | 0xFF000000, false);
			if (option.delete != null) {
				boolean onX = i == hovered && onDeleteSlot(dropdownAnchor, mouseX);
				int dx = x + w - DELETE_W;
				if (onX) context.fill(dx, oy, x + w - 1, oy + OPTION_H, Theme.withAlpha(theme.accent, 0x50));
				context.drawText(textRenderer, "×", dx + (DELETE_W - textRenderer.getWidth("×")) / 2, oy + 3,
						(onX ? theme.accent : theme.mutedText) | 0xFF000000, false);
			}
		}
		context.getMatrices().pop();
	}

	/** 오른쪽 미리보기: 머리글 + 말풍선 */
	private void renderPreview(DrawContext context, Theme theme) {
		int x0 = previewLeft(), x1 = right - PAD;
		int y0 = viewTop(), y1 = bottom - PAD - 16 - 6;
		if (x1 - x0 < 80) return;
		context.fill(x0, y0, x1, y1, theme.chatBg);
		// 미리보기에는 메인 창/팝업 켜고 끄기와 상관없이 고른 그림을 보여준다
		ModConfig.Background bg = background();
		if (!bg.image.isEmpty()) ChatBackground.draw(context, bg, x0, y0 + 19, x1, y1, 0, false, false, false, false, 1f);
		context.fill(x0, y0, x1, y0 + 18, theme.headerBg);
		context.fill(x0, y0 + 18, x1, y0 + 19, theme.divider);
		context.drawBorder(x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, theme.divider);
		UiFont.draw(context, textRenderer, sample.displayName(), x0 + 6, y0 + 5, theme.text);
		preview.render(context, sample, x0 + 6, y0 + 22, x1 - 10, y1 - 4);
	}

	/** 미리보기 대화의 예시 상대 이름 (실제 사용자 이름과 상관없이 고정) */
	private static final String SAMPLE_NAME = "백령";

	/** 미리보기 대화: 실제 채팅처럼 상대 말풍선 위에 이름이 붙는다 */
	private static Conversation sampleConversation() {
		Conversation c = new Conversation(SAMPLE_NAME);
		long now = System.currentTimeMillis();
		c.messages.add(sample(false, SAMPLE_NAME, "안녕하세요! 오늘 같이 사냥 갈래요?", now - 60000));
		c.messages.add(sample(true, null, "좋아요 :)", now - 50000));
		c.messages.add(sample(false, SAMPLE_NAME, ":웃음:", now - 40000));
		c.messages.add(sample(true, null, "어디서 만날까요? 마을 광장 어때요", now - 30000));
		return c;
	}

	private static Message sample(boolean outgoing, String sender, String text, long time) {
		Message m = new Message(outgoing, false, text, time);
		m.sender = sender;
		return m;
	}

	// ---------------------------------------------------------------- 슬라이더

	/** min~max 를 step 단위로 고르는 슬라이더 */
	private static final class ValueSlider extends SliderWidget {
		private final Text label;
		private final double min, max, step;
		private final DoubleConsumer apply;
		private final DoubleFunction<String> format;

		ValueSlider(int x, int y, int w, Text label, double min, double max, double step, double current,
					DoubleConsumer apply, DoubleFunction<String> format) {
			super(x, y, w, 16, Text.empty(), (MathHelper.clamp(current, min, max) - min) / (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.step = step;
			this.apply = apply;
			this.format = format;
			updateMessage();
		}

		private double current() {
			return min + Math.round((max - min) * value / step) * step;
		}

		@Override
		protected void updateMessage() {
			setMessage(label.copy().append(": " + format.apply(current())));
		}

		@Override
		public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			Theme theme = Theme.get();
			if (theme.vanillaWidgets) {
				super.renderWidget(context, mouseX, mouseY, delta);
				return;
			}
			// 테마 색 슬라이더: 채워진 부분은 조금 진하게, 손잡이는 글자색
			int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
			int base = Theme.mix(theme.headerBg | 0xFF000000, theme.windowBg | 0xFF000000, 0.3f);
			UiButton.fillRounded(context, x0, y0, x1, y1, base);
			int handleX = x0 + (int) (value * (width - 4));
			UiButton.fillRounded(context, x0, y0, handleX + 2, y1, Theme.mix(base, theme.text, isSelected() ? 0.2f : 0.12f));
			// 손잡이는 얇고 중간 밝기라 위에 겹친 글자도 읽힌다
			UiButton.fillRounded(context, handleX, y0, handleX + 4, y1, Theme.mix(base, theme.text, 0.45f));
			UiButton.drawLabel(context, this, getMessage(), theme.text | 0xFF000000, UiFont.NORMAL, theme.textShadow);
		}

		@Override
		protected void applyValue() {
			apply.accept(current());
		}
	}

	@Override
	public void close() {
		if (client != null) client.setScreen(parent);
	}
}
