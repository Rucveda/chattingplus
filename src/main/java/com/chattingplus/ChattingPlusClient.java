package com.chattingplus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChattingPlusClient implements ClientModInitializer {
	public static final String MOD_ID = "chattingplus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static KeyBinding openKey;
	private static KeyBinding popupKey;

	@Override
	public void onInitializeClient() {
		ModConfig.load();

		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.chattingplus.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "category.chattingplus"));
		// 바닐라 채팅(T) 없이 팝업만 조작하는 키
		popupKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.chattingplus.popup", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "category.chattingplus"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.wasPressed()) {
				if (client.currentScreen == null) client.setScreen(new WhisperScreen());
			}
			while (popupKey.wasPressed()) {
				if (client.currentScreen != null) continue;
				if (PopupChat.hasWindows()) client.setScreen(new PopupScreen());
				else client.inGameHud.setOverlayMessage(Text.translatable("chattingplus.popup.none"), false);
			}
			UpdateChecker.tick(client);
			// 바뀐 기록과 설정은 모아서 저장
			WhisperStore.get().tick();
			ModConfig.tick();
		});
		// 게임을 끌 때는 저장이 끝날 때까지 기다린다
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			WhisperStore.get().saveAndWait();
			ModConfig.saveAndWait();
		});

		// 서버 플러그인이 보내는 귓속말은 보통 시스템 메시지지만, 혹시 몰라 플레이어 채팅도 확인한다
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || !onMessage(message, true));
		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, timestamp) -> !onMessage(message, false));

		// 채널/월드 이동 때도 JOIN 이 다시 불린다. 같은 서버면 WhisperStore 가 기록을 그대로 유지한다.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			WhisperStore.get().load(serverKey(client));
			WhisperSender.setServer(serverKey(client));
			UpdateChecker.onJoin();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WhisperStore.get().save());

		// 팝업은 설정에 따라 HUD 맨 위(기본) 또는 보스바 아래에 그린다.
		// 서버가 보스바로 채팅창 위에 띄우는 안내("마을채팅(/ㅁ)" 등)를 팝업이 가리지 않게 아래로 보낼 수 있다.
		// HUD 는 층마다 앞쪽(z)으로 조금씩 올라가며 그려서, 맨 위에 그리려면 마지막 층으로 넣어야 한다
		// (HudRenderCallback 은 가장 뒤쪽 z 에 그려져서 보스바 글자에 가려지고, 팝업 자리가 구멍처럼 보였다)
		HudLayerRegistrationCallback.EVENT.register(drawer -> {
			drawer.attachLayerBefore(IdentifiedLayer.BOSS_BAR, Identifier.of(MOD_ID, "popups_under"), (context, tickCounter) -> {
				if (ModConfig.get().popupsUnderHud) PopupChat.render(context);
			});
			drawer.addLayer(IdentifiedLayer.of(Identifier.of(MOD_ID, "popups"), (context, tickCounter) -> {
				if (!ModConfig.get().popupsUnderHud) PopupChat.render(context);
				UnreadHud.render(context, tickCounter);
			}));
		});
		PopupChat.register();

		// 리소스를 다시 읽으면(서버 리소스팩 적용, F3+T, 글꼴 새로고침) 이모티콘 팩 목록을 다시 읽고 말풍선 폭도 다시 잰다
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of(MOD_ID, "emotes");
			}

			@Override
			public void reload(ResourceManager manager) {
				Emotes.invalidate();
				ConversationView.resourcesReloaded();
			}
		});
	}

	public static KeyBinding openKey() {
		return openKey;
	}

	public static KeyBinding popupKey() {
		return popupKey;
	}

	/** 귓속말이면 기록하고, 채팅창에서 숨겨야 하면 true */
	private static boolean onMessage(Text message, boolean system) {
		String line = message.getString();
		WhisperParser.Whisper whisper = WhisperParser.parse(line);
		if (whisper == null) {
			Channel.Line channelLine = Channel.parse(message);
			if (channelLine != null) {
				// 다른 모드의 데이터 문자열은 기록하지도, 채팅창에서 숨기지도 않는다 (지우는 건 그 모드 몫)
				if (WhisperParser.isModData(channelLine.text())) return false;
				return onChannelMessage(channelLine);
			}
			if (ModConfig.get().debugLog && (line.contains("보냄") || line.contains("받음"))) {
				LOGGER.info("[ChattingPlus] not recognized as whisper: {}", line);
			}
			// 방금 보낸 귓속말에 대한 서버 안내("오프라인입니다" 등)는 그 대화에, 나머지는 모두 시스템 탭에
			WhisperStore.Conversation noteFor = system ? WhisperSender.noteTarget(line) : null;
			if (noteFor != null) {
				WhisperStore.get().addNote(noteFor, WhisperParser.stripIcons(line));
				return false;
			}
			if (line.isBlank()) return false;
			MinecraftClient client = MinecraftClient.getInstance();
			String key = Channel.SYSTEM.key();
			boolean viewing = (client.currentScreen instanceof WhisperScreen screen && screen.isViewing(key)) || PopupChat.isShowing(key);
			WhisperStore.get().addSystem(message, !viewing);
			return Channel.SYSTEM.settings().hideInChat;
		}
		if (WhisperParser.isModData(whisper.text())) return false;
		if (ModConfig.get().debugLog) {
			LOGGER.info("[ChattingPlus] whisper {} {}: {}", whisper.outgoing() ? "to" : "from", whisper.partner(), whisper.text());
		}

		MinecraftClient client = MinecraftClient.getInstance();
		boolean inScreen = client.currentScreen instanceof WhisperScreen screen && screen.isViewing(whisper.partner());
		boolean viewing = inScreen || PopupChat.isShowing(whisper.partner());
		WhisperStore.get().add(whisper, !viewing);
		WhisperSender.onWhisper(whisper);

		// 알림을 끈 대화는 기록만 하고 소리나 화면 알림은 내지 않는다
		WhisperStore.Conversation conversation = WhisperStore.get().find(whisper.partner());
		if (!whisper.outgoing() && (conversation == null || !conversation.muted)) {
			if (!viewing) UnreadHud.notifyIncoming(whisper);
			if (ModConfig.get().playSound && !inScreen) {
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 1.6f));
			}
		}
		return ModConfig.get().hideInChat;
	}

	/** 채널 채팅은 기록만 하고 알림음/화면 알림은 내지 않는다 (전체 채팅은 양이 많아서) */
	private static boolean onChannelMessage(Channel.Line line) {
		String key = line.channel().key();
		MinecraftClient client = MinecraftClient.getInstance();
		boolean viewing = (client.currentScreen instanceof WhisperScreen screen && screen.isViewing(key)) || PopupChat.isShowing(key);
		if (ModConfig.get().debugLog) {
			LOGGER.info("[ChattingPlus] channel {} {} label: {}", line.channel().id, line.sender(), NameLabels.toJson(line.label()));
		}
		WhisperStore.get().addChannel(line, WhisperSender.isOwn(line), !viewing);
		return line.channel().settings().hideInChat;
	}

	private static String serverKey(MinecraftClient client) {
		ServerInfo info = client.getCurrentServerEntry();
		return info != null ? info.address : "singleplayer";
	}
}
