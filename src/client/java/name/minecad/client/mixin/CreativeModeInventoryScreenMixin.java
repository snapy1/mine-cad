package name.minecad.client.mixin;

import name.minecad.ui.OverlayState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
	// Vanilla creative tabs show their name tooltip on hover; suppress it while the custom window is open.
	@Inject(method = "checkTabHovering", at = @At("HEAD"), cancellable = true)
	private void minecad$noTabHover(GuiGraphicsExtractor graphics, CreativeModeTab tab, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
		if (OverlayState.isBlocking(this)) {
			cir.setReturnValue(false);
		}
	}

	@Shadow
	private static CreativeModeTab selectedTab;

	// While the custom tab is active, no vanilla tab matches "selectedTab", so all draw with their unselected (gray) sprites.
	@Redirect(
			method = "extractTabButton",
			at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen;selectedTab:Lnet/minecraft/world/item/CreativeModeTab;", opcode = 178)
	)
	private CreativeModeTab minecad$noSelectedTab() {
		return OverlayState.isBlocking(this) ? null : selectedTab;
	}
}
