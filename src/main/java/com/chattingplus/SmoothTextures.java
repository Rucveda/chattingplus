package com.chattingplus;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;
import net.minecraft.util.Util;

import java.util.function.Function;

/**
 * 부드럽게(선형 보간으로) 그리는 GUI 그림. 마인크래프트의 기본 GUI 그림은 픽셀 아트용이라 보간을 꺼 두어서,
 * 이모티콘이나 아이콘처럼 매끈한 그림을 크기를 바꿔 그리면 테두리가 계단처럼 보인다.
 * 기본 GUI 그림과 설정은 같고 보간만 켠 것.
 */
public final class SmoothTextures {
	private static final Function<Identifier, RenderLayer> LAYER = Util.memoize(texture -> RenderLayer.of(
			"chattingplus_gui_smooth", VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS, 786432,
			RenderLayer.MultiPhaseParameters.builder()
					.texture(new RenderPhase.Texture(texture, TriState.TRUE, false))
					.program(RenderPhase.POSITION_TEXTURE_COLOR_PROGRAM)
					.transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
					.depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
					.build(false)));

	private SmoothTextures() {}

	public static RenderLayer layer(Identifier texture) {
		return LAYER.apply(texture);
	}

	/** 이모티콘을 size(GUI 픽셀) 크기로. 실제 화면 픽셀 크기에 맞는 그림을 고른다. */
	public static void drawEmote(DrawContext context, Emotes.Emote emote, int x, int y, int size) {
		drawEmote(context, emote, x, y, size, 0xFFFFFFFF);
	}

	/** color 로 투명도를 줄 수 있다 (흐려진 팝업의 스티커) */
	public static void drawEmote(DrawContext context, Emotes.Emote emote, int x, int y, int size, int color) {
		double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
		int texture = emote.pickSize((int) Math.ceil(size * scale));
		context.drawTexture(SmoothTextures::layer, emote.texture(texture), x, y, 0, 0, size, size, texture, texture, texture, texture, color);
	}
}
