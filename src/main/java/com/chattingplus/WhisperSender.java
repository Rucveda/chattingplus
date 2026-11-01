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

	/** 지금 들어와 있는 서버 주소. 내 닉네임을 서버마다 따로 기억한다 */
	private static String server;
	/** 이미 아는 내 닉네임과 다른 이름으로 내 말이 돌아왔을 때, 한 번 더 확인하려고 적어 두는 이름 */
	private static String nameCandidate;

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

	static void setServer(String serverKey) {
		server = serverKey;
		nameCandidate = null;
	}

	/** 이 서버에서 알아낸 내 닉네임. 모르면 예전 버전이 저장한 이름, 그것도 없으면 null */
	static String myName() {
		ModConfig cfg = ModConfig.get();
		String name = server != null ? cfg.myNames.get(server) : null;
		return name != null ? name : cfg.myName;
	}

	/** 채널 채팅 한 줄이 내가 보낸 것인지 */
	static boolean isOwn(Channel.Line line) {
		// 방금 보낸 말과 내용이 정확히 같아야 한다 (앞부분만 같으면 남이 "ㅋㅋㅋ" 처럼 이어 친 말도 걸린다)
		if (line.channel() == pendingChannel && System.currentTimeMillis() - pendingChannelAt < PENDING_MS
				&& line.text().trim().equals(pendingChannelText)) {
			pendingChannel = null;
			return learnName(line.sender());
		}
		String me = myName();
		if (me != null && me.equalsIgnoreCase(line.sender())) return true;
		return line.sender().equalsIgnoreCase(MinecraftClient.getInstance().getSession().getUsername());
	}

	/**
	 * 내가 보낸 말이 이 이름으로 돌아왔다. 처음 알게 된 이름이면 바로 기억하고, 이미 아는 이름과 다르면
	 * 다른 사람이 우연히(또는 일부러) 같은 말을 먼저 친 것일 수 있어서 두 번 연속 같은 이름일 때만 바꾼다.
	 * 내 말로 볼지를 돌려준다
	 */
	private static boolean learnName(String sender) {
		String me = myName();
		if (sender.equals(me)) {
			nameCandidate = null;
			return true;
		}
		if (me != null && !sender.equalsIgnoreCase(me) && !sender.equals(nameCandidate)) {
			nameCandidate = sender;
			return false;
		}
		nameCandidate = null;
		ModConfig cfg = ModConfig.get();
		if (server != null) cfg.myNames.put(server, sender);
		else cfg.myName = sender;
		ModConfig.save();
		return true;
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
