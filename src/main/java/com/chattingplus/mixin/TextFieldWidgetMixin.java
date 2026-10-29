package com.chattingplus.mixin;

import com.chattingplus.UiTextField;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 입력칸은 글자를 항상 그림자와 함께 그린다. 밝은 테마에서는 진한 글자에 그림자가 붙어 번져 보이므로,
 * 모드 입력칸(UiTextField)이 그림자를 원하지 않으면 그림자 없이 그린다.
 * 다른 모드(한글 입력 등)도 입력칸을 고칠 수 있어서 덮어쓰지 않고 감싸는(WrapOperation) 방식으로 한다.
 */
@Mixin(TextFieldWidget.class)
abstract class TextFieldWidgetMixin {
	@WrapOperation(method = "renderWidget", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)I"))
	private int chattingplus$plainString(DrawContext context, TextRenderer tr, String text, int x, int y, int color, Operation<Integer> original) {
		if ((Object) this instanceof UiTextField field && field.plainText()) return context.drawText(tr, text, x, y, color, false);
		return original.call(context, tr, text, x, y, color);
	}

	/** 입력한 글자는 OrderedText 로 그린다 */
	@WrapOperation(method = "renderWidget", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"))
	private int chattingplus$plainOrdered(DrawContext context, TextRenderer tr, OrderedText text, int x, int y, int color, Operation<Integer> original) {
		if ((Object) this instanceof UiTextField field && field.plainText()) return context.drawText(tr, text, x, y, color, false);
		return original.call(context, tr, text, x, y, color);
	}

	@WrapOperation(method = "renderWidget", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I"))
	private int chattingplus$plainText(DrawContext context, TextRenderer tr, Text text, int x, int y, int color, Operation<Integer> original) {
		if ((Object) this instanceof UiTextField field && field.plainText()) return context.drawText(tr, text, x, y, color, false);
		return original.call(context, tr, text, x, y, color);
	}
}
