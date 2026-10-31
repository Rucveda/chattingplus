package com.chattingplus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** 대화 상대별 귓속말 기록. 서버 주소마다 config/chattingplus/history/ 아래 파일로 저장된다. */
public final class WhisperStore {
	public static class Message {
		public boolean outgoing;
		/** "오프라인 상태입니다" 처럼 서버가 보낸 안내. 가운데에 회색으로 표시된다. */
		public boolean system;
		/** 채널 채팅에서 말한 사람. 귓속말이면 null */
		public String sender;
		/** 말한 사람의 이름표(등급 아이콘, 칭호, 닉네임 색)를 서버가 보낸 모양 그대로 저장한 JSON. 없으면 null */
		public String senderLabel;
		public String text;
		/** 시스템 탭 메시지: 서버가 보낸 줄 전체를 모양(색, 아이콘) 그대로 저장한 JSON. 그 밖에는 null */
		public String styled;
		public long time;

		Message(boolean outgoing, boolean system, String text, long time) {
			this.outgoing = outgoing;
			this.system = system;
			this.text = text;
			this.time = time;
		}
	}

	public static class Conversation {
		public String name;
		public List<Message> messages = new ArrayList<>();
		public int unread;
		public long lastTime;
		public boolean favorite;
		/** 알림 끄기: 안 읽은 개수는 세지만 소리, 화면 알림, 팝업 탭 빛이 없고 배지는 회색. 메인 창과 팝업이 같이 쓴다 */
		public boolean muted;
		/** 이 대화에만 쓰는 배경 그림. null 이면 기본 배경. 채널과 즐겨찾기 대화만 가진다 ({@link ChatBackground#canCustomize}) */
		public ModConfig.Background background;

		Conversation(String name) {
			this.name = name;
		}

		public Message last() {
			return messages.isEmpty() ? null : messages.get(messages.size() - 1);
		}

		/** 채널 대화면 그 채널, 귓속말이면 null */
		public Channel channel() {
			return Channel.fromKey(name);
		}

		public String displayName() {
			Channel channel = channel();
			if (channel == Channel.SYSTEM) return channel.label;
			return channel != null ? channel.label + " 채팅" : name;
		}
	}

	private static final WhisperStore INSTANCE = new WhisperStore();
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("chattingplus").resolve("history");

	/** 닉네임 소문자 → 대화 */
	private final Map<String, Conversation> conversations = new LinkedHashMap<>();
	private Path file;
	/** 내용이 바뀔 때마다 증가. 화면이 다시 그릴지 판단하는 데 쓴다. */
	private int version;

	private WhisperStore() {}

	public static WhisperStore get() {
		return INSTANCE;
	}

	public int version() {
		return version;
	}

	/**
	 * 서버에 들어갈 때마다 불린다. 채널/월드 이동도 서버 입장으로 처리되므로,
	 * 같은 서버라면 메모리에 있는 대화를 그대로 두고 다른 서버일 때만 파일에서 읽는다.
	 */
	public void load(String serverKey) {
		Path target = DIR.resolve(serverKey.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
		if (target.equals(file)) return;
		save();
		conversations.clear();
		labels.clear();
		file = target;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				List<Conversation> list = GSON.fromJson(reader, new TypeToken<List<Conversation>>() {}.getType());
				if (list != null) {
					boolean purged = false;
					for (Conversation c : list) {
						if (c.messages == null) c.messages = new ArrayList<>();
						// 예전 버전이 기록해 둔 다른 모드의 데이터 문자열(Together Plus 의 ~tp)은 지운다
						if (c.messages.removeIf(m -> m.styled == null && m.text != null && WhisperParser.isModData(m.text))) {
							purged = true;
							if (c.messages.isEmpty() && !c.favorite && c.channel() == null) continue;
						}
						if (!ChatBackground.canCustomize(c)) c.background = null;
						if (c.background != null) c.background.sanitize();
						conversations.put(key(c.name), c);
						for (Message m : c.messages) rememberLabel(m);
					}
					if (purged) markDirty();
				}
			} catch (Exception e) {
				ChattingPlusClient.LOGGER.error("Failed to read {}", file, e);
			}
		}
		version++;
	}

	public Conversation add(WhisperParser.Whisper whisper, boolean markUnread) {
		Conversation c = getOrCreate(whisper.partner());
		c.name = whisper.partner(); // 대소문자는 가장 최근 표기를 따른다
		append(c, new Message(whisper.outgoing(), false, whisper.text(), System.currentTimeMillis()));
		if (whisper.outgoing()) {
			c.unread = 0; // 내가 답장했으면 읽은 것으로 본다
		} else if (markUnread) {
			c.unread++;
		}
		markDirty();
		return c;
	}

	public Conversation addChannel(Channel.Line line, boolean outgoing, boolean markUnread) {
		Conversation c = channel(line.channel());
		Message m = new Message(outgoing, false, line.text(), System.currentTimeMillis());
		m.sender = line.sender();
		m.senderLabel = NameLabels.toJson(line.label());
		rememberLabel(m);
		append(c, m);
		if (outgoing) c.unread = 0;
		else if (markUnread) c.unread++;
		markDirty();
		return c;
	}

	/** 채널, 귓속말이 아닌 메시지를 시스템 탭에 서버가 보낸 모양 그대로 */
	public Conversation addSystem(Text message, boolean markUnread) {
		Conversation c = channel(Channel.SYSTEM);
		Message m = new Message(false, false, message.getString(), System.currentTimeMillis());
		m.styled = NameLabels.toJson(message);
		append(c, m);
		if (markUnread) c.unread++;
		markDirty();
		return c;
	}

	/** 채널 대화는 항상 있다. 그리는 도중에도 불리므로 version 을 올리지 않는다. */
	public Conversation channel(Channel channel) {
		return conversations.computeIfAbsent(key(channel.key()), k -> {
			Conversation c = new Conversation(channel.key());
			// 시스템 메시지는 많고 자주 와서 처음에는 알림을 꺼 둔다 (종 버튼으로 켤 수 있다)
			c.muted = channel == Channel.SYSTEM;
			return c;
		});
	}

	public List<Conversation> channels() {
		List<Conversation> list = new ArrayList<>();
		for (Channel c : Channel.values()) list.add(channel(c));
		return list;
	}

	public void addNote(Conversation c, String text) {
		append(c, new Message(false, true, text, System.currentTimeMillis()));
		markDirty();
	}

	private void append(Conversation c, Message m) {
		c.messages.add(m);
		int limit = c.channel() != null ? ModConfig.get().maxMessagesPerChannel : ModConfig.get().maxMessagesPerConversation;
		int overflow = c.messages.size() - Math.max(1, limit);
		if (overflow > 0) c.messages.subList(0, overflow).clear();
		c.lastTime = m.time;
		version++;
	}

	// ---------------------------------------------------------------- 이름표 (등급, 닉네임 색)

	/** 닉네임 소문자 → 그 사람이 채널 채팅에 마지막으로 보인 이름표 JSON */
	private final Map<String, String> labels = new HashMap<>();

	private void rememberLabel(Message m) {
		if (m.sender != null && m.senderLabel != null) labels.put(key(m.sender), m.senderLabel);
	}

	/** 이 사람의 이름표(서버가 보낸 모양 그대로). 채널 채팅에서 본 적이 없으면 null */
	public Text label(String name) {
		return name == null ? null : NameLabels.fromJson(labels.get(key(name)));
	}

	public Conversation find(String name) {
		return name == null ? null : conversations.get(key(name));
	}

	public Conversation getOrCreate(String name) {
		Conversation c = conversations.computeIfAbsent(key(name), k -> {
			Conversation created = new Conversation(name);
			created.lastTime = System.currentTimeMillis();
			return created;
		});
		version++;
		return c;
	}

	public void remove(Conversation c) {
		conversations.remove(key(c.name));
		version++;
		markDirty();
	}

	public void markRead(Conversation c) {
		if (c.unread == 0) return;
		c.unread = 0;
		version++;
		markDirty();
	}

	public void toggleFavorite(Conversation c) {
		c.favorite = !c.favorite;
		// 즐겨찾기를 풀면 그 대화만의 배경 설정도 지운다
		if (!ChatBackground.canCustomize(c)) c.background = null;
		version++;
		markDirty();
	}

	public void toggleMuted(Conversation c) {
		c.muted = !c.muted;
		version++;
		markDirty();
	}

	/** 대화만의 배경 설정을 바꾼 뒤 (null 이면 기본 배경으로) 저장하게 한다 */
	public void setBackground(Conversation c, ModConfig.Background background) {
		c.background = ChatBackground.canCustomize(c) ? background : null;
		markDirty();
	}

	/** 안 읽은 귓속말 수 (채널 채팅은 양이 많아서, 알림을 끈 대화는 알리지 않으려고 빼고 센다) */
	public int totalUnread() {
		int total = 0;
		for (Conversation c : conversations.values()) if (c.channel() == null && !c.muted) total += c.unread;
		return total;
	}

	/** 귓속말 대화만. 즐겨찾기 먼저, 그 안에서 최근 대화 순 */
	public List<Conversation> sorted() {
		List<Conversation> list = new ArrayList<>();
		for (Conversation c : conversations.values()) if (c.channel() == null) list.add(c);
		list.sort(Comparator.comparing((Conversation c) -> !c.favorite)
				.thenComparing(Comparator.comparingLong((Conversation c) -> c.lastTime).reversed()));
		return list;
	}

	// ---------------------------------------------------------------- 저장
	// 메시지마다 파일 전체를 다시 쓰면 전체 채팅이 활발할 때 1초에 여러 번 디스크에 쓰게 된다.
	// 그래서 바뀌었다고 표시만 해 두고, 마지막 저장 후 SAVE_DELAY_MS 가 지나면 한 번에 저장한다.
	// 파일 쓰기는 게임이 멈칫하지 않도록 별도 스레드에서 하고, 순서가 섞이지 않게 한 줄로 처리한다.

	private static final long SAVE_DELAY_MS = 3000;
	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "ChattingPlus history writer");
		thread.setDaemon(true);
		return thread;
	});

	private boolean dirty;
	private long dirtySince;

	private void markDirty() {
		if (!dirty) {
			dirty = true;
			dirtySince = System.currentTimeMillis();
		}
	}

	/** 매 틱 불린다. 바뀐 지 SAVE_DELAY_MS 가 지났으면 저장 */
	public void tick() {
		if (dirty && System.currentTimeMillis() - dirtySince >= SAVE_DELAY_MS) save();
	}

	/** 바뀐 게 있으면 지금 저장을 시작한다 (끝나기를 기다리지 않음) */
	public void save() {
		flush();
	}

	/** 게임을 끌 때: 저장이 끝날 때까지 기다린다 */
	public void saveAndWait() {
		Future<?> pending = flush();
		if (pending == null) return;
		try {
			pending.get(5, TimeUnit.SECONDS);
		} catch (Exception e) {
			ChattingPlusClient.LOGGER.error("Failed to finish saving {}", file, e);
		}
	}

	/** 지금 내용을 글자로 만들어(게임 스레드) 쓰기 스레드에 넘긴다 */
	private Future<?> flush() {
		if (!dirty || file == null) return null;
		dirty = false;
		// 메시지 없이 열어보기만 한 대화는 저장하지 않는다 (즐겨찾기는 유지)
		List<Conversation> list = new ArrayList<>();
		for (Conversation c : conversations.values()) if (!c.messages.isEmpty() || c.favorite) list.add(c);
		String json = GSON.toJson(list);
		Path target = file;
		return WRITER.submit(() -> write(target, json));
	}

	/** 임시 파일에 다 쓴 뒤 바꿔치기해서, 쓰는 도중에 게임이 꺼져도 기록 파일이 깨지지 않게 한다 */
	private static void write(Path target, String json) {
		try {
			Files.createDirectories(target.getParent());
			Path temp = target.resolveSibling(target.getFileName() + ".tmp");
			Files.writeString(temp, json, StandardCharsets.UTF_8);
			try {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			ChattingPlusClient.LOGGER.error("Failed to write {}", target, e);
		}
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
