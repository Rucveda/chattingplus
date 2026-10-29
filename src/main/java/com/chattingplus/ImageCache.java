package com.chattingplus;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 사용자 폴더의 그림 파일을 텍스처로 올려 두는 곳 (배경 그림, 스티커).
 * 처음 찾을 때 따로 읽기 시작하고(게임이 멈추지 않게), 다 읽은 뒤 다시 찾으면 텍스처로 올린다.
 */
final class ImageCache {
	interface Loader {
		ImageFiles.Sheet load(Path file) throws IOException;
	}

	/** 파일 하나의 상태. texture 가 있으면 그릴 수 있다 */
	static final class Entry {
		private CompletableFuture<ImageFiles.Sheet> loading;
		Identifier texture;
		ImageFiles.Sheet sheet;
		int texWidth, texHeight;
	}

	private final String prefix;
	private final Supplier<Path> dir;
	private final Loader loader;
	private final Map<String, Entry> entries = new HashMap<>();
	private int nextId;

	/** prefix: 텍스처 이름 앞부분 (파일 이름은 한글일 수 있어 번호를 붙인다) */
	ImageCache(String prefix, Supplier<Path> dir, Loader loader) {
		this.prefix = prefix;
		this.dir = dir;
		this.loader = loader;
	}

	/** 그 파일의 상태. 파일 이름이 이상하면 null */
	Entry get(String name) {
		if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")) return null;
		Entry entry = entries.get(name);
		if (entry == null) {
			entry = new Entry();
			Path file = dir.get().resolve(name);
			entry.loading = CompletableFuture.supplyAsync(() -> {
				try {
					return loader.load(file);
				} catch (IOException | RuntimeException e) {
					ChattingPlusClient.LOGGER.warn("[ChattingPlus] failed to read {}: {}", file, e.toString());
					return null;
				}
			}, Util.getIoWorkerExecutor());
			entries.put(name, entry);
		}
		if (entry.loading != null && entry.loading.isDone()) {
			ImageFiles.Sheet sheet = entry.loading.join();
			entry.loading = null;
			if (sheet != null) {
				entry.sheet = sheet;
				entry.texWidth = sheet.image().getWidth();
				entry.texHeight = sheet.image().getHeight();
				entry.texture = Identifier.of(ChattingPlusClient.MOD_ID, prefix + "/" + nextId++);
				MinecraftClient.getInstance().getTextureManager().registerTexture(entry.texture, new NativeImageBackedTexture(sheet.image()));
			}
		}
		return entry;
	}

	/** 그림 파일을 바꿔 넣었을 때: 올린 텍스처를 모두 버리고 다시 읽게 한다 */
	void clear() {
		MinecraftClient client = MinecraftClient.getInstance();
		for (Entry entry : entries.values()) {
			if (entry.texture != null) client.getTextureManager().destroyTexture(entry.texture);
			if (entry.loading != null) entry.loading.thenAccept(sheet -> {
				if (sheet != null) sheet.image().close();
			});
		}
		entries.clear();
	}
}
