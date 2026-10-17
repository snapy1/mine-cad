package name.minecad.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class Tab extends AbstractButton {
	private static final int TAB_WIDTH = 26;
	private static final int TAB_HEIGHT = 32;

	private ItemStack icon;
	private Runnable action;
	private boolean selected;

	private Window window;


	public Tab(int x, int y, Component name, ItemStack icon, boolean selected, Runnable action, Window window) {
		super(x, y, TAB_WIDTH, TAB_HEIGHT, name);
		this.icon = icon;
		this.selected = selected;
		this.action = action;
		this.window = window;
	}

	// Overloaded constructor without the window parameter -- really only used for testing.
	public Tab(int x, int y, Component name, ItemStack icon, boolean selected, Runnable action) {
		this(x, y, name, icon, selected, action, null);
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	public boolean isSelectedTab() {
		return selected;
	}

	public void setIcon(ItemStack icon) {
		this.icon = icon;
	}

	public void setAction(Runnable action) {
		this.action = action;
	}

	public Window getWindow() {
		return window;
	}

	public void setWindow(Window window) {
		this.window = window;
	}

	@Override
	public void onPress(InputWithModifiers inputWithModifiers) {
		action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {

		int x = getX();
		int y = getY();

		int frameColor = 0xFF000000;
		int bodyColor;

		if (selected) {
			bodyColor = 0xFFC6C6C6;
		} else if (isHovered()) {
			bodyColor = 0xFFB0B0B0;
		} else {
			bodyColor = 0xFF9E9E9E;
		}

		graphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT, frameColor);
		graphics.fill(x + 2, y + 2, x + TAB_WIDTH - 2, y + TAB_HEIGHT - 2, bodyColor);

		// Blend selected tabs into the inventory panel border.
		if (selected) {
			graphics.fill(x + 2, y + TAB_HEIGHT - 3, x + TAB_WIDTH - 2, y + TAB_HEIGHT, bodyColor);
		}

		if (icon != null) {
			graphics.item(icon, x + 5, y + 8);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		defaultButtonNarrationText(narrationElementOutput);
	}
}
