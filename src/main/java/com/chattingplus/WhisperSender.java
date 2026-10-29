package com.chattingplus;

import com.chattingplus.WhisperStore.Conversation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import java.util.Locale;

/**
 * 귓속말/채널 채팅 명령어 전송. 전송 직후 서버가 돌려주는 안내("~님은 오프라인 상태입니다")를 대화에 연결하고,
 * 채널 채팅에서는 내가 보낸 줄을 알아보고 내 닉네임을 기억한다.
 */
public final class WhisperSender {
	/** 전송 후 이 시간 안에 돌아온 줄만 방금 보낸 것과 연결한다 */
	private static final long PENDING_MS = 3000;

	private static String pendingPartner;
	private static long pendingAt;

	private static Channel pendingChannel;
	private static String pendingChannelText;
	private static long pendingChannelAt;

	private WhisperSender() {}

	/** name 이 채널 키(#global 등)면 채널 명령어, 아니면 귓속말 명령어 */
	public static String commandHead(String name) {
		Channel channel = Channel.fromKey(name);
		String prefix = (channel != null ? channel.settings().command : ModConfig.get().commandPrefix).trim();
		if (prefix.startsWith("/")) prefix = prefix.substring(1);
		return channel != null ? prefix + " " : prefix + " " + name + " ";
	}

	/** 서버 채팅 길이 제한(256자)에서 명령어와 닉네임 길이를 뺀 값 */
	public static int maxTextLength(String name) {
		if (Channel.fromKey(name) == Channel.SYSTEM) return 256;
		return Math.max(1, 256 - commandHead(name).length());
	}

	public static boolean send(String name, String text) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayNetworkHandler network = client.getNetworkHandler();
		String trimmed = text.trim();
		if (network == null || trimmed.isEmpty()) return false;
		if (Channel.fromKey(name) == Channel.SYSTEM) {
			// 시스템 탭: 바닐라 채팅처럼 그대로 보낸다 ("/" 로 시작하면 명령어). 위쪽 화살표 기록에도 남긴다
			client.inGameHud.getChatHud().addToMessageHistory(trimmed);
			if (trimmed.startsWith("/")) network.sendChatCommand(trimmed.substring(1));
			else network.sendChatMessage(trimmed);
			return true;
		}
		// 대화창에는 서버가 돌려주는 메시지를 받아서 추가한다 (실패한 전송이 기록되지 않도록)
		network.sendChatCommand(commandHead(name) + trimmed);
		Channel channel = Channel.fromKey(name);
		if (channel != null) {
			pendingChannel = channel;
			pendingChannelText = trimmed;
			pendingChannelAt = System.currentTimeMillis();
		} else {
			pendingPartner = name;
			pendingAt = System.currentTimeMillis();
		}
		return true;
	}

	static void onWhisper(WhisperParser.Whisper whisper) {
		if (whisper.outgoing() && whisper.partner().equalsIgnoreCase(pendingPartner)) pendingPartner = null;
	}

	/** 채널 채팅 한 줄이 내가 보낸 것인지 */
	static boolean isOwn(Channel.Line line) {
		ModConfig cfg = ModConfig.get();
		if (line.channel() == pendingChannel && System.currentTimeMillis() - pendingChannelAt < PENDING_MS
				&& line.text().startsWith(pendingChannelText)) {
			pendingChannel = null;
			if (!line.sender().equals(cfg.myName)) {
				cfg.myName = line.sender();
				ModConfig.save();
			}
			return true;
		}
		if (cfg.myName != null && cfg.myName.equalsIgnoreCase(line.sender())) return true;
		return line.sender().equalsIgnoreCase(MinecraftClient.getInstance().getSession().getUsername());
	}

	/** 귓속말이 아닌 시스템 메시지가 방금 보낸 귓속말에 대한 안내이면 그 대화를 돌려준다 */
	static Conversation noteTarget(String line) {
		if (pendingPartner == null) return null;
		if (System.currentTimeMillis() - pendingAt > PENDING_MS) {
			pendingPartner = null;
			return null;
		}
		if (!line.toLowerCase(Locale.ROOT).contains(pendingPartner.toLowerCase(Locale.ROOT))) return null;
		Conversation c = WhisperStore.get().find(pendingPartner);
		pendingPartner = null;
		return c;
	}
}
