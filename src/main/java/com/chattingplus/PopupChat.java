package com.chattingplus;

import com.chattingplus.ModConfig.PopupWindow;
import com.chattingplus.WhisperStore.Conversation;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 게임 화면 위에 띄워두는 작은 대화창들. 창마다 여러 대화를 탭으로 열어둘 수 있다.
 * 평소에는 내용만 보이고, 채팅(T)을 열면 옮기기, 크기 조절, 탭 전환, 입력, 닫기가 된다.
 * 탭을 탭 줄 밖으로 끌어내면 새 창으로 분리되고, 다른 창의 머리글에 놓으면 그 창으로 합쳐진다.
 * 탭이 하나뿐인 창은 제목줄을 끌어 다른 창 머리글에 놓으면 합쳐진다.
 */
public final class PopupChat {
	/** 제목줄/탭 높이의 최솟값 (팝업 글자를 키우면 더 높아진다) */
	private static final int MIN_TITLE_H = 14;
	private static final int MIN_TAB_H = 13;
	private static final int INPUT_H = 14;
	private static final int PAD = 4;
	private static final int GRIP = 7;
	private static final int MIN_W = 120;
	private static final int MIN_H = 70;
	/** 알림 탭이 한 번 밝아졌다 어두워지는 시간 */
	private static final long PULSE_MS = 1100;
	/** 탭을 이만큼 끌어야 분리/합치기 동작으로 본다 (그 전에는 그냥 클릭) */
	private static final int TEAR_DISTANCE = 6;

	private static final int EMOTE_BUTTON = 14;
	private static final int COLOR_DROP = 0xFFFFC83D;

	private record Tab(String name, int x0, int x1) {}

	/** 창마다 저장하지 않는 화면 상태 */
	private static final class Runtime {
		final ConversationView view;
		TextFieldWidget input;
		final List<Tab> tabs = new ArrayList<>();
		/** 이번 프레임에 그려졌는지 (이 서버에 기록이 없는 탭만 있는 창은 안 그린다) */
		boolean rendered;
		int headerH;

		Runtime(TextRenderer tr) {
			view = new ConversationView(tr);
		}
	}

	private enum Drag { NONE, MOVE, RESIZE, TAB_PENDING, TAB }

	private static final Map<PopupWindow, Runtime> runtimes = new IdentityHashMap<>();
	/** 지금 열려 있는 채팅창(T). 그 사이에 새 창이 생기면 입력칸을 여기에 붙인다. */
	private static Screen attachedScreen;

	/** 이모티콘 고르는 창은 한 번에 하나. 연 팝업에서 보낸다. */
	private static final EmotePicker picker = new EmotePicker();
	private static PopupWindow pickerOwner;
	/** 스티커 편집 중인 팝업 (편집 중에는 스티커 고르는 창이 그 옆에 열려 있다) */
	private static final StickerPicker stickerPicker = new StickerPicker();
	private static final StickerLayer stickerLayer = new StickerLayer();
	private static PopupWindow stickerOwner;

	private static Drag drag = Drag.NONE;
	private static PopupWindow dragWindow;
	private static String dragTab;
	private static int grabX, grabY, pressX, pressY;
	private static int mouseX, mouseY;

	private PopupChat() {}

	static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (controls(screen)) attach(client, screen);
		});
	}

	/** 팝업을 조작할 수 있는 화면: 바닐라 채팅창(T)이나 팝업 전용 화면(팝업 키) */
	private static boolean controls(Screen screen) {
		return screen instanceof ChatScreen || screen instanceof PopupScreen;
	}

	/** 띄워 둔 팝업이 있는지 (팝업 키를 눌렀을 때) */
	public static boolean hasWindows() {
		return !windows().isEmpty();
	}

	private static List<PopupWindow> windows() {
		return ModConfig.get().popups;
	}

	private static Runtime rt(PopupWindow window) {
		return runtimes.computeIfAbsent(window, w -> new Runtime(MinecraftClient.getInstance().textRenderer));
	}

	// ---------------------------------------------------------------- 탭/창 조작

	/** 이미 어느 창에 열려 있으면 그 탭으로, 아니면 맨 위 창에 탭을 추가한다 (창이 없으면 새로 만든다) */
	public static void open(String name) {
		// 처음 보는 상대(말풍선 위 이름을 눌렀을 때 등)면 대화를 만든다. 대화가 없는 탭은 그려지지 않는다.
		WhisperStore.get().getOrCreate(name);
		PopupWindow window = windowOf(name);
		if (window == null) {
			List<PopupWindow> list = windows();
			if (list.isEmpty()) {
				window = new PopupWindow();
				list.add(window);
				attachInput(window);
			} else {
				window = list.get(list.size() - 1);
			}
			window.tabs.add(name);
		}
		bringToFront(window);
		switchTo(window, name);
	}

	private static PopupWindow windowOf(String name) {
		for (PopupWindow w : windows()) if (indexOf(w, name) >= 0) return w;
		return null;
	}

	private static int indexOf(PopupWindow window, String name) {
		for (int i = 0; i < window.tabs.size(); i++) if (window.tabs.get(i).equalsIgnoreCase(name)) return i;
		return -1;
	}

	private static void switchTo(PopupWindow window, String name) {
		window.active = name;
		rt(window).view.resetScroll();
		ModConfig.save();
	}

	private static void bringToFront(PopupWindow window) {
		List<PopupWindow> list = windows();
		if (list.remove(window)) list.add(window);
	}

	/** 탭을 빼낸다. 빈 창은 없앤다. */
	private static void removeTab(PopupWindow window, String name) {
		int index = indexOf(window, name);
		if (index < 0) return;
		window.tabs.remove(index);
		if (name.equalsIgnoreCase(window.active)) {
			// 빼낸 탭 옆에 있던 탭을 보여준다
			window.active = window.tabs.isEmpty() ? null : window.tabs.get(Math.min(index, window.tabs.size() - 1));
			rt(window).view.resetScroll();
		}
		if (window.tabs.isEmpty()) removeWindow(window);
	}

	private static void closeTab(PopupWindow window, String name) {
		removeTab(window, name);
		ModConfig.save();
	}

	private static void removeWindow(PopupWindow window) {
		windows().remove(window);
		Runtime runtime = runtimes.remove(window);
		if (runtime != null && runtime.input != null && attachedScreen != null) {
			if (attachedScreen.getFocused() == runtime.input) focusVanillaChat(attachedScreen);
			Screens.getButtons(attachedScreen).remove(runtime.input);
		}
		if (dragWindow == window) drag = Drag.NONE;
		if (pickerOwner == window) picker.close();
	}

	/** 탭을 다른 창으로 옮긴다 */
	private static void moveTab(PopupWindow from, String name, PopupWindow to) {
		removeTab(from, name);
		if (indexOf(to, name) < 0) to.tabs.add(name);
		bringToFront(to);
		switchTo(to, name);
	}

	/** 탭을 끌어낸 자리에 새 창을 만든다 */
	private static void tearOff(PopupWindow from, String name, int mx, int my) {
		PopupWindow window = new PopupWindow();
		window.tabs.add(name);
		window.active = name;
		window.width = from.width;
		window.height = from.height;
		window.x = mx - 20;
		window.y = my - titleH() / 2;
		removeTab(from, name);
		windows().add(window);
		attachInput(window);
		ModConfig.save();
	}

	/** 창 전체를 다른 창에 합친다 */
	private static void merge(PopupWindow from, PopupWindow to) {
		String active = from.active;
		for (String name : from.tabs) if (indexOf(to, name) < 0) to.tabs.add(name);
		// 합쳐지는 창의 스티커도 옮겨 붙인다
		to.stickers.addAll(from.stickers);
		removeWindow(from);
		bringToFront(to);
		if (active != null) switchTo(to, active);
	}

	/** 이 서버에 기록이 있는 탭들 */
	private static List<Conversation> openConversations(PopupWindow window) {
		List<Conversation> list = new ArrayList<>();
		for (String name : window.tabs) {
			Conversation c = WhisperStore.get().find(name);
			if (c != null) list.add(c);
		}
		return list;
	}

	private static Conversation active(PopupWindow window, List<Conversation> open) {
		for (Conversation c : open) if (c.name.equalsIgnoreCase(window.active)) return c;
		return open.isEmpty() ? null : open.get(0);
	}

	/** 지금 화면에 이 대화가 어느 팝업의 보고 있는 탭으로 보이는지 (다른 탭 뒤에 있으면 false) */
	public static boolean isShowing(String name) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.options.hudHidden || (client.currentScreen != null && !controls(client.currentScreen))) return false;
		for (PopupWindow w : windows()) {
			Conversation c = active(w, openConversations(w));
			if (c != null && c.name.equalsIgnoreCase(name)) return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- 채팅창(T)과 연결

	private static void attach(MinecraftClient client, Screen screen) {
		attachedScreen = screen;
		for (PopupWindow w : windows()) attachInput(w);
		// 팝업 전용 화면이면 바닐라 입력칸이 없으니 맨 앞 팝업의 입력칸에 바로 커서를 둔다
		if (screen instanceof PopupScreen && !windows().isEmpty()) {
			TextFieldWidget input = rt(windows().get(windows().size() - 1)).input;
			if (input != null) screen.setFocused(input);
		}

		ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, button) -> !onClick(s, mx, my, button));
		ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, horizontal, vertical) -> !onScroll(mx, my, vertical));
		ScreenMouseEvents.afterMouseRelease(screen).register((s, mx, my, button) -> {
			finishDrag((int) mx, (int) my);
			stickerLayer.release();
		});
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scancode, modifiers) -> !onKey(s, key));
		ScreenEvents.remove(screen).register(s -> {
			drag = Drag.NONE;
			picker.close();
			stopStickers();
			if (attachedScreen == s) attachedScreen = null;
		});
	}

	/** 창의 입력칸을 지금 열린 채팅창에 붙인다 (입력 중이던 글자는 유지) */
	private static void attachInput(PopupWindow window) {
		if (attachedScreen == null) return;
		Runtime runtime = rt(window);
		String prevText = runtime.input != null ? runtime.input.getText() : "";
		TextFieldWidget input = new UiTextField(MinecraftClient.getInstance().textRenderer, 0, 0, 100, INPUT_H, Text.translatable("chattingplus.input"));
		input.setPlaceholder(Text.translatable("chattingplus.input").formatted(Formatting.DARK_GRAY));
		input.setText(prevText);
		input.visible = false; // 위치는 그릴 때 정한다
		runtime.input = input;
		Screens.getButtons(attachedScreen).add(input);
	}

	private static PopupWindow windowAt(double mx, double my) {
		List<PopupWindow> list = windows();
		for (int i = list.size() - 1; i >= 0; i--) {
			PopupWindow w = list.get(i);
			if (rt(w).rendered && mx >= w.x && mx < w.x + w.width && my >= w.y && my < w.y + w.height) return w;
		}
		return null;
	}

	/** 끌어다 놓으면 합쳐지는 다른 창 (머리글 = 탭 줄 + 제목줄 위에 있을 때) */
	private static PopupWindow dropTargetAt(int mx, int my, PopupWindow exclude) {
		List<PopupWindow> list = windows();
		for (int i = list.size() - 1; i >= 0; i--) {
			PopupWindow w = list.get(i);
			Runtime runtime = rt(w);
			if (w == exclude || !runtime.rendered) continue;
			if (mx >= w.x && mx < w.x + w.width && my >= w.y && my < w.y + runtime.headerH) return w;
		}
		return null;
	}

	private static int emoteButtonX(PopupWindow w) {
		return w.x + w.width - PAD - GRIP - EMOTE_BUTTON;
	}

	private static int inputY(PopupWindow w) {
		return w.y + w.height - INPUT_H - PAD;
	}

	private static boolean onEmoteButton(PopupWindow w, double mx, double my) {
		int bx = emoteButtonX(w), by = inputY(w);
		return mx >= bx && mx < bx + EMOTE_BUTTON && my >= by && my < by + INPUT_H;
	}

	/** 제목줄의 스티커 버튼 (닫기 버튼 왼쪽) */
	private static int stickerButtonX(PopupWindow w) {
		return w.x + w.width - 2 * closeButtonSize() - 3;
	}

	/** 팝업 스티커는 화면 어디에나. 핀으로 고정한 것은 팝업 왼쪽 위 기준이라 팝업을 따라간다 */
	private static StickerLayer.Frame stickerFrame(PopupWindow w) {
		Window window = MinecraftClient.getInstance().getWindow();
		return new StickerLayer.Frame(window.getScaledWidth(), window.getScaledHeight(), w.x, w.y, true);
	}

	/** 스티커 편집 켜기/끄기. 고르는 창은 팝업 오른쪽에 (자리가 없으면 왼쪽에) */
	private static void toggleStickers(PopupWindow w) {
		picker.close();
		if (stickerOwner == w) {
			stopStickers();
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		int screenW = client.getWindow().getScaledWidth(), screenH = client.getWindow().getScaledHeight();
		int px = w.x + w.width + 4;
		if (px + StickerPicker.WIDTH > screenW) px = w.x - StickerPicker.WIDTH - 4;
		stickerOwner = w;
		stickerPicker.openAt(px, w.y, screenW, screenH);
	}

	private static void stopStickers() {
		stickerLayer.release();
		stickerPicker.close();
		stickerOwner = null;
	}

	/** 새 스티커는 팝업 가운데에 (여러 개 붙이면 조금씩 비껴서). 처음에는 고정 안 함 */
	private static void addSticker(PopupWindow w, String source) {
		ModConfig.Sticker sticker = new ModConfig.Sticker(source, 40);
		StickerLayer.Frame frame = stickerFrame(w);
		int shift = (w.stickers.size() % 5) * 10;
		sticker.x = (w.x + w.width / 2f + shift) / Math.max(1, frame.screenW());
		sticker.y = (w.y + w.height / 2f + shift) / Math.max(1, frame.screenH());
		w.stickers.add(sticker);
		ModConfig.save();
	}

	private static boolean onClick(Screen screen, double mx, double my, int button) {
		if (stickerOwner != null) {
			if (stickerPicker.contains(mx, my)) {
				String source = stickerPicker.click(mx, my);
				if (source != null) addSticker(stickerOwner, source);
				return true;
			}
			if (stickerLayer.press(stickerOwner.stickers, stickerFrame(stickerOwner), mx, my, button)) return true;
		}
		if (picker.isOpen()) {
			if (picker.contains(mx, my)) {
				Emotes.Emote emote = picker.click(mx, my);
				PopupWindow owner = pickerOwner;
				Conversation c = owner != null ? active(owner, openConversations(owner)) : null;
				// 메신저 이모티콘처럼 고르면 바로 보낸다
				if (emote != null && c != null && WhisperSender.send(c.name, emote.token())) {
					picker.close();
					rt(owner).view.resetScroll();
				}
				return true;
			}
			boolean toggle = pickerOwner != null && onEmoteButton(pickerOwner, mx, my);
			picker.close();
			if (toggle) return true;
		}
		PopupWindow w = windowAt(mx, my);
		if (w == null) {
			// 팝업 전용 키(Y)로 들어왔으면 입력칸 선택만 푼다. 화면은 닫지 않아서 다른 모드(Together Plus 등)의
			// 화면 요소를 그대로 누를 수 있고, 나갈 때는 Esc 나 Y 를 한 번 더 누른다 (PopupScreen.keyPressed)
			if (screen instanceof PopupScreen) {
				if (isPopupInput(screen.getFocused())) screen.setFocused(null);
				return false;
			}
			// 채팅창(T)에서는 원래 채팅 입력칸으로 돌아간다
			if (isPopupInput(screen.getFocused())) focusVanillaChat(screen);
			return false;
		}
		bringToFront(w);
		Runtime runtime = rt(w);
		Conversation c = active(w, openConversations(w));
		if (c == null) return true;

		boolean left = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
		boolean right = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
		int titleTop = runtime.tabs.isEmpty() ? w.y : w.y + tabH();
		int ix = (int) mx, iy = (int) my;
		// 말풍선 위 이름을 누르면 그 사람과의 귓속말을 이 팝업의 탭으로 연다
		String clickedName = runtime.view.nameAt(mx, my);

		if (my < titleTop) {
			// 탭: 왼쪽 클릭은 전환(끌면 분리), 오른쪽 클릭은 닫기. 탭이 없는 빈 곳은 창 옮기기
			Tab hit = null;
			for (Tab tab : runtime.tabs) if (mx >= tab.x0 && mx < tab.x1) hit = tab;
			if (hit != null) {
				if (left) {
					switchTo(w, hit.name);
					startDrag(Drag.TAB_PENDING, w, ix, iy);
					dragTab = hit.name;
				} else if (right) {
					closeTab(w, hit.name);
				}
			} else if (left) {
				startDrag(Drag.MOVE, w, ix, iy);
			}
		} else if (my < titleTop + titleH()) {
			if (!left) return true;
			if (mx >= w.x + w.width - closeButtonSize() - 2) closeTab(w, c.name);
			else if (mx >= stickerButtonX(w)) toggleStickers(w);
			else startDrag(Drag.MOVE, w, ix, iy);
		} else if (left && clickedName != null && !WhisperScreen.isMe(clickedName)) {
			open(clickedName);
		} else if (left && onEmoteButton(w, mx, my)) {
			stopStickers();
			MinecraftClient client = MinecraftClient.getInstance();
			pickerOwner = w;
			picker.openAbove(emoteButtonX(w) + EMOTE_BUTTON, inputY(w), client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
		} else if (left && mx >= w.x + w.width - GRIP && my >= w.y + w.height - GRIP) {
			startDrag(Drag.RESIZE, w, ix, iy);
			grabX = w.x + w.width - ix;
			grabY = w.y + w.height - iy;
		} else if (left && runtime.input != null && runtime.input.visible && runtime.input.isMouseOver(mx, my)) {
			screen.setFocused(runtime.input);
			runtime.input.mouseClicked(mx, my, button);
		}
		return true; // 팝업 위 클릭은 뒤에 있는 채팅창으로 넘기지 않는다
	}

	private static void startDrag(Drag mode, PopupWindow window, int mx, int my) {
		drag = mode;
		dragWindow = window;
		pressX = mx;
		pressY = my;
		grabX = mx - window.x;
		grabY = my - window.y;
	}

	private static void finishDrag(int mx, int my) {
		if (drag == Drag.NONE) return;
		PopupWindow w = dragWindow;
		Drag mode = drag;
		drag = Drag.NONE;
		dragWindow = null;
		if (w == null || !windows().contains(w)) return;

		if (mode == Drag.MOVE) {
			// 탭이 하나뿐인 창을 다른 창 머리글에 놓으면 합친다
			PopupWindow target = w.tabs.size() == 1 ? dropTargetAt(mx, my, w) : null;
			if (target != null) merge(w, target);
		} else if (mode == Drag.TAB && dragTab != null) {
			PopupWindow target = dropTargetAt(mx, my, w);
			if (target != null) {
				moveTab(w, dragTab, target);
			} else if (onTabRow(w, mx, my)) {
				reorderTab(w, dragTab, mx);
			} else if (my < w.y || my >= w.y + rt(w).headerH || mx < w.x || mx >= w.x + w.width) {
				tearOff(w, dragTab, mx, my);
			}
		}
		dragTab = null;
		ModConfig.save();
	}

	/** 그 창의 탭 줄 위인지 (탭이 2개 이상일 때만 탭 줄이 있다) */
	private static boolean onTabRow(PopupWindow w, int mx, int my) {
		return !rt(w).tabs.isEmpty() && mx >= w.x && mx < w.x + w.width && my >= w.y && my < w.y + tabH();
	}

	/** 탭 줄에서 mx 에 놓으면 들어갈 자리: 가운데가 mx 보다 오른쪽인 첫 탭의 앞 (없으면 맨 끝) */
	private static int insertIndex(Runtime runtime, int mx) {
		for (int i = 0; i < runtime.tabs.size(); i++) {
			Tab tab = runtime.tabs.get(i);
			if (mx < (tab.x0 + tab.x1) / 2) return i;
		}
		return runtime.tabs.size();
	}

	/** 같은 창 탭 줄 안에서 끌어 놓으면 순서를 바꾼다 */
	private static void reorderTab(PopupWindow w, String name, int mx) {
		Runtime runtime = rt(w);
		int at = insertIndex(runtime, mx);
		// 놓을 자리 바로 뒤 탭 이름 (그 앞에 넣는다). 맨 끝이면 null
		String before = at < runtime.tabs.size() ? runtime.tabs.get(at).name : null;
		if (name.equalsIgnoreCase(before)) return;
		int from = indexOf(w, name);
		if (from < 0) return;
		String moved = w.tabs.remove(from);
		int to = before != null ? indexOf(w, before) : w.tabs.size();
		w.tabs.add(to < 0 ? w.tabs.size() : to, moved);
	}

	private static boolean isPopupInput(Element element) {
		if (element == null) return false;
		for (Runtime runtime : runtimes.values()) if (runtime.input == element) return true;
		return false;
	}

	/** ChatScreen 의 입력칸은 private 이라, 화면에 붙은 입력칸 중 팝업 것이 아닌 것을 찾는다 */
	private static void focusVanillaChat(Screen screen) {
		for (Element element : screen.children()) {
			if (element instanceof TextFieldWidget field && !isPopupInput(field)) {
				screen.setFocused(field);
				return;
			}
		}
	}

	private static boolean onScroll(double mx, double my, double amount) {
		if (picker.scroll(mx, my, amount)) return true;
		if (stickerPicker.scroll(mx, my, amount)) return true;
		PopupWindow w = windowAt(mx, my);
		if (w == null) return false;
		rt(w).view.scroll(amount, msgBottom(w) - msgTop(w, rt(w).headerH));
		return true;
	}

	private static boolean onKey(Screen screen, int key) {
		if (key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER) return false;
		for (PopupWindow w : windows()) {
			Runtime runtime = rt(w);
			if (runtime.input == null || screen.getFocused() != runtime.input || !runtime.input.visible) continue;
			// Enter 를 채팅창이 받으면 빈 채팅을 보내고 닫혀버리므로 여기서 가로챈다
			Conversation c = active(w, openConversations(w));
			if (c != null && WhisperSender.send(c.name, runtime.input.getText())) {
				runtime.input.setText("");
				runtime.view.resetScroll();
			}
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- 그리기 (HUD 단계)

	// ---------------------------------------------------------------- 팝업 글자 크기 (설정의 "팝업 글자")

	private static float popupScale() {
		return ModConfig.get().sizes.popupScale;
	}

	/** 탭 이름: 한 단계 작은 글자 */
	private static float tabScale() {
		return UiFont.small() * popupScale();
	}

	private static int titleH() {
		return Math.max(MIN_TITLE_H, UiFont.height(popupScale()) + 6);
	}

	private static int tabH() {
		return Math.max(MIN_TAB_H, UiFont.height(tabScale()) + 6);
	}

	private static int msgTop(PopupWindow w, int headerH) {
		return w.y + headerH + 2;
	}

	/** 대화 영역 아래 끝: 입력줄 자리는 채팅창이 닫혀 있어도 비워 둔다 (열 때 창 모양이 바뀌지 않게) */
	private static int msgBottom(PopupWindow w) {
		return w.y + w.height - (INPUT_H + PAD + 6);
	}

	/** 제목줄 닫기 버튼 한 변 */
	private static int closeButtonSize() {
		return titleH() - 2;
	}

	/** 평소(채팅창 닫힘)에는 테마의 팝업 불투명도만큼만 */
	private static int argb(int color, boolean interactive) {
		return Theme.get().popupAlpha(color | 0xFF000000, interactive);
	}

	static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		for (Runtime runtime : runtimes.values()) runtime.rendered = false;
		if (client.options.hudHidden) return;
		boolean interactive = controls(client.currentScreen);
		if (client.currentScreen != null && !interactive) return;
		if (!interactive) {
			drag = Drag.NONE;
			picker.close();
		}
		// 채팅창이 닫혔거나 편집하던 팝업이 없어지면 스티커 편집도 끝
		if (stickerOwner != null && (!interactive || !windows().contains(stickerOwner))) stopStickers();
		// 앞서 그린 HUD(보스바 등)의 글자는 모아 뒀다가 나중에 그려지는데, 그 사이 반투명 팝업이 먼저 깊이를 차지하면
		// 그 글자가 팝업 자리만 빠져서 구멍 난 것처럼 보인다. 먼저 다 그려 두고 그 위에 팝업을 얹는다
		context.draw();

		TextRenderer tr = client.textRenderer;
		int screenW = context.getScaledWindowWidth(), screenH = context.getScaledWindowHeight();
		Window window = client.getWindow();
		mouseX = (int) (client.mouse.getX() * screenW / window.getWidth());
		mouseY = (int) (client.mouse.getY() * screenH / window.getHeight());
		if (drag != Drag.NONE) updateDrag(client);
		if (stickerLayer.dragging()) {
			if (GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) stickerLayer.drag(mouseX, mouseY);
			else stickerLayer.release();
		}

		// 끌고 있는 것을 놓으면 합쳐질 창
		PopupWindow dropTarget = null;
		if (drag == Drag.TAB || (drag == Drag.MOVE && dragWindow != null && dragWindow.tabs.size() == 1)) {
			dropTarget = dropTargetAt(mouseX, mouseY, dragWindow);
		}

		List<PopupWindow> list = new ArrayList<>(windows());
		for (PopupWindow w : list) {
			clampToScreen(w, screenW, screenH);
			renderWindow(context, tr, w, interactive, w == dropTarget);
		}
		updateInputs(list, interactive);
		picker.render(context, tr, mouseX, mouseY);
		stickerPicker.render(context, tr, mouseX, mouseY);

		if (drag == Drag.TAB && dragTab != null) {
			Theme theme = Theme.get();
			// 같은 창 탭 줄 위면 놓았을 때 들어갈 자리에 세로 막대
			if (dragWindow != null && onTabRow(dragWindow, mouseX, mouseY)) {
				Runtime runtime = rt(dragWindow);
				int at = insertIndex(runtime, mouseX);
				int lx = at < runtime.tabs.size() ? runtime.tabs.get(at).x0 : runtime.tabs.get(runtime.tabs.size() - 1).x1;
				lx = Math.max(dragWindow.x + 1, Math.min(dragWindow.x + dragWindow.width - 1, lx));
				context.fill(lx - 1, dragWindow.y, lx + 1, dragWindow.y + tabH(), theme.selection() | 0xFF000000);
			}
			// 끌고 있는 탭을 마우스 옆에 그린다
			Conversation c = WhisperStore.get().find(dragTab);
			String label = c != null ? tabLabel(c) : dragTab;
			int lw = UiFont.width(tr, label) + 8;
			context.fill(mouseX + 4, mouseY + 4, mouseX + 4 + lw, mouseY + 4 + tabH(), theme.headerBg);
			context.drawText(tr, UiFont.text(label), mouseX + 8, mouseY + 7, theme.text, false);
		}
	}

	private static void renderWindow(DrawContext context, TextRenderer tr, PopupWindow w, boolean interactive, boolean dropHighlight) {
		Runtime runtime = rt(w);
		runtime.tabs.clear();
		List<Conversation> open = openConversations(w);
		Conversation c = active(w, open);
		if (c == null) return;
		runtime.rendered = true;

		int x = w.x, y = w.y, width = w.width, h = w.height;

		// 보고 있는 탭은 읽음 처리
		if (c.unread > 0) WhisperStore.get().markRead(c);

		// 맨 위에 탭 줄(2개 이상일 때), 그 아래 제목줄, 그 아래 대화 내용
		boolean hasTabs = open.size() > 1;
		int bodyY = hasTabs ? y + tabH() : y;
		runtime.headerH = bodyY - y + titleH();
		if (hasTabs) renderTabs(context, tr, runtime, open, c, x, y, width, interactive);

		Theme theme = Theme.get();
		int bodyBg = theme.popupAlpha(theme.chatBg, interactive);
		// 모서리 둥글기: 탭 줄이 있으면 위쪽은 탭이 덮으므로 아래 모서리만
		int r = ModConfig.get().sizes.cornerRadius;
		Shapes.fill(context, x, bodyY, x + width, y + h, bodyBg, r, !hasTabs, !hasTabs, true, true);
		// 배경 그림: 제목줄 아래 전체 (아래 모서리는 창과 같이 둥글게). 채팅창이 닫혀 있으면 팝업처럼 흐리게
		ModConfig.Background bg = ChatBackground.of(c);
		if (ChatBackground.enabled(bg, true)) {
			float alpha = interactive ? 1f : Math.max(10, Math.min(100, ModConfig.get().sizes.popupOpacity)) / 100f;
			ChatBackground.draw(context, bg, x, bodyY + titleH() + 1, x + width, y + h, r, false, false, true, true, alpha);
		}
		Shapes.fill(context, x, bodyY, x + width, bodyY + titleH(), argb(theme.headerBg, interactive), r, !hasTabs, !hasTabs, false, false);
		context.fill(x, bodyY + titleH(), x + width, bodyY + titleH() + 1, argb(theme.divider, interactive)); // 제목줄과 내용 경계
		// 테두리는 채팅창을 열었을 때나 닫았을 때나 같은 모양 (닫혀 있으면 흐리게만)
		Shapes.ring(context, x - 1, bodyY - 1, x + width + 1, y + h + 1, argb(theme.divider, interactive), r, !hasTabs, !hasTabs, true, true);
		if (dropHighlight) context.drawBorder(x - 1, y - 1, width + 2, runtime.headerH + 2, COLOR_DROP);

		String title = (c.favorite ? "★ " : "") + c.displayName();
		int titleColor = c.channel() != null ? theme.channelColor(c.channel()) : theme.text;
		float ps = popupScale();
		int titleTextY = bodyY + (titleH() - UiFont.height(ps)) / 2;
		UiFont.drawText(context, tr, UiFont.text(UiFont.trim(tr, title, width - 2 * titleH() - 8, ps)), x + PAD, titleTextY, titleColor, ps, false);

		// 닫기 버튼: 다른 아이콘 버튼처럼 둥근 바탕 + 아이콘 (가리키면 바탕이 보인다)
		int cb = closeButtonSize(), cx = x + width - cb - 2, cy = bodyY + (titleH() - cb) / 2;
		boolean closeHover = interactive && mouseX >= cx && mouseX < cx + cb && mouseY >= cy && mouseY < cy + cb;
		if (closeHover) UiButton.fillRounded(context, cx, cy, cx + cb, cy + cb, Theme.mix(theme.headerBg | 0xFF000000, theme.text, 0.15f));
		UiButton.drawIcon(context, UiButton.icon("close"), cx, cy, cb, cb, argb(closeHover ? theme.text : theme.mutedText, interactive));
		// 스티커 버튼: 채팅창을 열었을 때만. 편집 중이면 눌린 모양
		if (interactive) {
			int sx = stickerButtonX(w);
			boolean editing = stickerOwner == w;
			boolean stickerHover = mouseX >= sx && mouseX < sx + cb && mouseY >= cy && mouseY < cy + cb;
			if (editing || stickerHover) UiButton.fillRounded(context, sx, cy, sx + cb, cy + cb, Theme.mix(theme.headerBg | 0xFF000000, theme.text, editing ? 0.25f : 0.15f));
			UiButton.drawIcon(context, UiButton.icon("sticker"), sx, cy, cb, cb, editing || stickerHover ? theme.text : theme.mutedText);
		}

		// 대화 영역과 입력줄 자리는 채팅창을 열든 닫든 같다 (열 때 창 모양이 바뀌지 않게)
		runtime.view.setAreaScale(popupScale());
		runtime.view.setMouse(interactive ? mouseX : -1, interactive ? mouseY : -1);
		runtime.view.render(context, c, x + PAD, msgTop(w, runtime.headerH), x + width - PAD - 4, msgBottom(w));

		if (runtime.input != null) {
			runtime.input.setX(x + PAD);
			runtime.input.setY(inputY(w));
			runtime.input.setWidth(emoteButtonX(w) - 2 - (x + PAD));
			runtime.input.setMaxLength(WhisperSender.maxTextLength(c.name));
		}
		int bx = emoteButtonX(w), by = inputY(w);
		if (!interactive) {
			// 채팅창이 닫혀 있을 때는 입력칸 자리에 빈 칸만 흐리게 (열면 그 자리에 진짜 입력칸이 나온다)
			int ix0 = x + PAD, ix1 = bx - 2;
			UiButton.fillRounded(context, ix0, by, ix1, by + INPUT_H, argb(theme.divider, false));
			UiButton.fillRounded(context, ix0 + 1, by + 1, ix1 - 1, by + INPUT_H - 1, argb(Theme.mix(theme.chatBg | 0xFF000000, theme.text, 0.06f), false));
		}
		// 이모티콘 버튼
		boolean hover = interactive && mouseX >= bx && mouseX < bx + EMOTE_BUTTON && mouseY >= by && mouseY < by + INPUT_H;
		boolean on = picker.isOpen() && pickerOwner == w;
		UiButton.fillRounded(context, bx, by, bx + EMOTE_BUTTON, by + INPUT_H, argb(on || hover ? theme.headerBg : theme.tab(1), interactive));
		UiButton.drawIcon(context, UiButton.icon("emote"), bx, by, EMOTE_BUTTON, INPUT_H, argb(theme.text, interactive));
		// 스티커는 팝업 위에 붙인 것처럼 맨 위에. 앞에 그린 글자가 비치지 않게 먼저 내보낸다
		if (!w.stickers.isEmpty()) {
			context.draw();
			float alpha = interactive ? 1f : Math.max(10, Math.min(100, ModConfig.get().sizes.popupOpacity)) / 100f;
			stickerLayer.render(context, w.stickers, stickerFrame(w), interactive && stickerOwner == w, mouseX, mouseY, alpha);
		}
		if (interactive) {
			// 오른쪽 아래 크기 조절 손잡이
			for (int i = 0; i < 3; i++) {
				int off = 2 + i * 2;
				context.fill(x + width - off, y + h - 2, x + width - off + 1, y + h - 1, theme.mutedText);
				context.fill(x + width - 2, y + h - off, x + width - 1, y + h - off + 1, theme.mutedText);
			}
		}
	}

	/**
	 * 입력칸은 채팅창이 HUD 다음에 그리므로 늘 팝업들 위에 보인다.
	 * 위에 다른 팝업이 덮고 있는 창의 입력칸은 숨겨서 뒤 창 입력칸이 앞 창 위로 비쳐 보이지 않게 한다.
	 */
	private static void updateInputs(List<PopupWindow> list, boolean interactive) {
		for (int i = 0; i < list.size(); i++) {
			Runtime runtime = rt(list.get(i));
			if (runtime.input == null) continue;
			TextFieldWidget input = runtime.input;
			boolean covered = false;
			for (int j = i + 1; j < list.size() && !covered; j++) {
				PopupWindow above = list.get(j);
				if (!rt(above).rendered) continue;
				covered = input.getX() < above.x + above.width && input.getX() + input.getWidth() > above.x
						&& input.getY() < above.y + above.height && input.getY() + input.getHeight() > above.y;
			}
			// 이모티콘/스티커 고르는 창에 가려지는 입력칸도 숨긴다
			if (picker.isOpen()) {
				covered |= input.getX() < picker.x() + EmotePicker.WIDTH && input.getX() + input.getWidth() > picker.x()
						&& input.getY() < picker.y() + EmotePicker.HEIGHT && input.getY() + input.getHeight() > picker.y();
			}
			if (stickerPicker.isOpen()) {
				covered |= input.getX() < stickerPicker.x() + StickerPicker.WIDTH && input.getX() + input.getWidth() > stickerPicker.x()
						&& input.getY() < stickerPicker.y() + StickerPicker.HEIGHT && input.getY() + input.getHeight() > stickerPicker.y();
			}
			input.visible = interactive && runtime.rendered && !covered;
		}
	}

	/** 탭 폭은 이름 길이에 맞추되, 다 안 들어가면 똑같이 나눠서 이름을 줄인다 */
	private static void renderTabs(DrawContext context, TextRenderer tr, Runtime runtime, List<Conversation> open, Conversation active,
								   int x, int y, int w, boolean interactive) {
		Theme theme = Theme.get();
		int natural = 0;
		for (Conversation c : open) natural += UiFont.width(tr, tabLabel(c), tabScale()) + 8;
		int even = w / open.size();
		// 안 읽은 메시지가 온 탭: 0 → 1 → 0 으로 부드럽게 오가는 밝기로 탭 안이 강조색으로 차올랐다 빠진다
		// (탭 밖으로는 번지지 않게)
		float pulse = pulse(System.currentTimeMillis());

		int tx = x;
		for (int i = 0; i < open.size(); i++) {
			Conversation c = open.get(i);
			String name = tabLabel(c);
			int tw = natural <= w ? UiFont.width(tr, name, tabScale()) + 8 : even;
			if (i == open.size() - 1 && natural > w) tw = x + w - tx; // 나눗셈 나머지는 마지막 탭에

			boolean isActive = c == active;
			boolean alert = alerting(c, active);
			// 알림 탭은 밝기와 상관없이 늘 또렷하게 (채팅창이 닫혀 팝업이 흐려져 있어도 눈에 띄게)
			int color = isActive ? argb(theme.headerBg, interactive)
					: alert ? Theme.mix(theme.tab(i) | 0xFF000000, theme.accent | 0xFF000000, 0.35f + 0.55f * pulse)
					: argb(theme.tab(i), interactive);
			// 노트 라벨처럼 위쪽 모서리만 둥글게, 탭 사이 틈 없이
			context.fill(tx + 2, y, tx + tw - 2, y + 1, color);
			context.fill(tx + 1, y + 1, tx + tw - 1, y + 2, color);
			context.fill(tx, y + 2, tx + tw, y + tabH(), color);

			String label = UiFont.trim(tr, name, tw - 6, tabScale());
			int textColor = isActive ? theme.text : alert ? 0xFFFFFFFF
					: c.channel() != null ? Theme.mix(theme.channelColor(c.channel()), theme.mutedText, 0.3f) : theme.mutedText;
			// 탭 이름은 한 단계 작게, 탭 높이 가운데에
			UiFont.drawPlain(context, tr, label, tx + 4, y + (tabH() - UiFont.height(tabScale())) / 2 + 1, textColor, tabScale());

			runtime.tabs.add(new Tab(c.name, tx, tx + tw));
			tx += tw;
		}
	}

	/** 안 읽은 메시지가 와서 빛나야 하는 탭인지 (보고 있는 탭과 알림을 끈 대화는 아님) */
	private static boolean alerting(Conversation c, Conversation active) {
		return c != active && c.unread > 0 && !c.muted;
	}

	/** 0 → 1 → 0 을 PULSE_MS 마다 부드럽게 (코사인) */
	static float pulse(long millis) {
		double phase = (millis % PULSE_MS) / (double) PULSE_MS;
		return (float) (0.5 - 0.5 * Math.cos(phase * 2 * Math.PI));
	}

	/** 탭에는 짧게: 채널은 "#전체", 귓속말은 닉네임 */
	private static String tabLabel(Conversation c) {
		Channel channel = c.channel();
		return channel != null ? "#" + channel.label : c.name;
	}

	private static void updateDrag(MinecraftClient client) {
		if (GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
			finishDrag(mouseX, mouseY);
			return;
		}
		PopupWindow w = dragWindow;
		if (w == null) return;
		switch (drag) {
			case MOVE -> {
				w.x = mouseX - grabX;
				w.y = mouseY - grabY;
			}
			case RESIZE -> {
				w.width = mouseX + grabX - w.x;
				w.height = mouseY + grabY - w.y;
			}
			case TAB_PENDING -> {
				if (Math.abs(mouseX - pressX) > TEAR_DISTANCE || Math.abs(mouseY - pressY) > TEAR_DISTANCE) drag = Drag.TAB;
			}
			default -> {}
		}
	}

	private static void clampToScreen(PopupWindow w, int screenW, int screenH) {
		w.width = Math.max(MIN_W, Math.min(w.width, screenW));
		w.height = Math.max(MIN_H, Math.min(w.height, screenH));
		w.x = Math.max(0, Math.min(w.x, screenW - w.width));
		w.y = Math.max(0, Math.min(w.y, screenH - w.height));
	}
}
