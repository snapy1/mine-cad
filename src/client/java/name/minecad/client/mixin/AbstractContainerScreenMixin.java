package name.minecad.client.mixin;

import name.minecad.ui.OverlayState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	// No slot is hovered while the custom tab window is open (removes highlight + item tooltip source).
	@Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true)
	private void minecad$noHoveredSlot(double mouseX, double mouseY, CallbackInfoReturnable<Slot> cir) {
		if (OverlayState.isBlocking(this)) {
			cir.setReturnValue(null);
		}
	}

	@Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
	private void minecad$noTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (OverlayState.isBlocking(this)) {
			ci.cancel();
		}
	}
}

