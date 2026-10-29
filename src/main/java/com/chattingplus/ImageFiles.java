package com.chattingplus;

import net.minecraft.client.texture.NativeImage;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * 사용자가 넣은 그림 파일 읽기 (배경 그림, 스티커). 게임 밖 스레드에서 불러도 된다.
 * PNG/JPG 는 한 장, GIF 는 움직이는 그림이면 프레임을 모두 읽어 한 장의 격자(스프라이트 시트)로 붙인다.
 */
final class ImageFiles {
	/** 움직이는 그림 한 장(모든 프레임 합)의 최대 픽셀 수. 넘으면 프레임을 줄인다 (그래픽 메모리 약 16MB) */
	private static final long MAX_SHEET_PIXELS = 4_000_000;
	private static final int MAX_FRAMES = 200;
	/** 프레임 사이 빈 칸. 부드럽게 그릴 때 옆 프레임 가장자리가 번져 보이지 않게 */
	private static final int GUTTER = 2;
	/** 프레임 시간이 0 이거나 너무 짧으면 브라우저처럼 0.1초로 */
	private static final int DEFAULT_DELAY_MS = 100;

	private ImageFiles() {}

	/**
	 * 프레임 격자 그림. 프레임 i 는 (i % cols, i / cols) 칸에 있고 칸 크기는 frameW+GUTTER × frameH+GUTTER.
	 * ends[i] 는 i 번째 프레임이 끝나는 시각(ms, 처음부터 누적)
	 */
	record Sheet(NativeImage image, int frameW, int frameH, int cols, int[] ends) {
		/** 지금 시각에 보여줄 프레임 */
		int frameAt(long millis) {
			if (ends.length == 1) return 0;
			long t = millis % ends[ends.length - 1];
			for (int i = 0; i < ends.length; i++) if (t < ends[i]) return i;
			return ends.length - 1;
		}

		int u(int frame) {
			return (frame % cols) * (frameW + GUTTER);
		}

		int v(int frame) {
			return (frame / cols) * (frameH + GUTTER);
		}
	}

	/** 한 장짜리 그림을 긴 변 maxSide 이하로 읽는다. 그림이 아니면 null */
	static NativeImage readStill(Path file, int maxSide) throws IOException {
		BufferedImage source;
		try (InputStream in = Files.newInputStream(file)) {
			source = ImageIO.read(in);
		}
		return source == null ? null : toNative(scale(source, maxSide));
	}

	/** 스티커용: GIF 면 모든 프레임, 아니면 한 장. 긴 변은 maxSide 이하. 그림이 아니면 null */
	static Sheet readSheet(Path file, int maxSide) throws IOException {
		if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gif")) {
			NativeImage still = readStill(file, maxSide);
			return still == null ? null : new Sheet(still, still.getWidth(), still.getHeight(), 1, new int[]{1});
		}
		List<BufferedImage> frames = new ArrayList<>();
		List<Integer> delays = new ArrayList<>();
		readGif(file, frames, delays);
		if (frames.isEmpty()) return null;

		int w = frames.get(0).getWidth(), h = frames.get(0).getHeight();
		// 프레임 수만큼 곱한 넓이가 한도를 넘지 않게 더 줄인다
		double fit = Math.sqrt((double) MAX_SHEET_PIXELS / ((long) w * h * frames.size()));
		int side = (int) Math.min(maxSide, Math.max(w, h) * Math.min(1, fit));
		float shrink = Math.min(1f, (float) side / Math.max(w, h));
		int fw = Math.max(1, Math.round(w * shrink)), fh = Math.max(1, Math.round(h * shrink));
		int cols = (int) Math.ceil(Math.sqrt(frames.size()));
		int rows = (frames.size() + cols - 1) / cols;

		BufferedImage sheet = new BufferedImage(cols * (fw + GUTTER), rows * (fh + GUTTER), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = sheet.createGraphics();
		quality(g);
		int[] ends = new int[frames.size()];
		int time = 0;
		for (int i = 0; i < frames.size(); i++) {
			g.drawImage(frames.get(i), (i % cols) * (fw + GUTTER), (i / cols) * (fh + GUTTER), fw, fh, null);
			time += delays.get(i);
			ends[i] = time;
		}
		g.dispose();
		return new Sheet(toNative(sheet), fw, fh, cols, ends);
	}

	/** GIF 프레임을 겹쳐 그리는 규칙(disposal)대로 합쳐서 화면에 보이는 그대로의 프레임들로 */
	private static void readGif(Path file, List<BufferedImage> frames, List<Integer> delays) throws IOException {
		Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
		if (!readers.hasNext()) return;
		ImageReader reader = readers.next();
		try (InputStream raw = Files.newInputStream(file); ImageInputStream in = ImageIO.createImageInputStream(raw)) {
			reader.setInput(in, false);
			int count = Math.min(MAX_FRAMES, reader.getNumImages(true));
			int width = 0, height = 0;
			IIOMetadataNode screen = child(reader.getStreamMetadata() == null ? null
					: (IIOMetadataNode) reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
			if (screen != null) {
				width = intAttr(screen, "logicalScreenWidth", 0);
				height = intAttr(screen, "logicalScreenHeight", 0);
			}
			BufferedImage canvas = null;
			for (int i = 0; i < count; i++) {
				BufferedImage frame = reader.read(i);
				IIOMetadataNode meta = (IIOMetadataNode) reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
				IIOMetadataNode desc = child(meta, "ImageDescriptor");
				IIOMetadataNode control = child(meta, "GraphicControlExtension");
				int left = desc != null ? intAttr(desc, "imageLeftPosition", 0) : 0;
				int top = desc != null ? intAttr(desc, "imageTopPosition", 0) : 0;
				if (canvas == null) {
					if (width <= 0 || height <= 0) {
						width = left + frame.getWidth();
						height = top + frame.getHeight();
					}
					canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				}
				String disposal = control != null ? control.getAttribute("disposalMethod") : "none";
				int delay = control != null ? intAttr(control, "delayTime", 0) * 10 : 0;

				BufferedImage before = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
				Graphics2D g = canvas.createGraphics();
				g.drawImage(frame, left, top, null);
				g.dispose();
				frames.add(copy(canvas));
				delays.add(delay < 20 ? DEFAULT_DELAY_MS : delay);

				if ("restoreToBackgroundColor".equals(disposal)) {
					Graphics2D clear = canvas.createGraphics();
					clear.setComposite(java.awt.AlphaComposite.Clear);
					clear.fillRect(left, top, frame.getWidth(), frame.getHeight());
					clear.dispose();
				} else if (before != null) {
					canvas = before;
				}
			}
		} finally {
			reader.dispose();
		}
	}

	private static IIOMetadataNode child(IIOMetadataNode parent, String name) {
		if (parent == null) return null;
		for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeName().equals(name)) return (IIOMetadataNode) n;
		}
		return null;
	}

	private static int intAttr(IIOMetadataNode node, String name, int fallback) {
		try {
			return Integer.parseInt(node.getAttribute(name));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static BufferedImage copy(BufferedImage image) {
		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return out;
	}

	/** 긴 변이 maxSide 를 넘으면 줄인다 */
	private static BufferedImage scale(BufferedImage source, int maxSide) {
		int w = source.getWidth(), h = source.getHeight();
		float shrink = Math.min(1f, (float) maxSide / Math.max(w, h));
		int tw = Math.max(1, Math.round(w * shrink)), th = Math.max(1, Math.round(h * shrink));
		BufferedImage scaled = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scaled.createGraphics();
		quality(g);
		g.drawImage(source, 0, 0, tw, th, null);
		g.dispose();
		return scaled;
	}

	private static void quality(Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}

	private static NativeImage toNative(BufferedImage image) {
		NativeImage out = new NativeImage(NativeImage.Format.RGBA, image.getWidth(), image.getHeight(), false);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) out.setColorArgb(x, y, image.getRGB(x, y));
		}
		return out;
	}
}
