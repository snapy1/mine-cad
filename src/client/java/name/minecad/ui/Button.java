package name.minecad.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * A clickable button that lives inside an InventoryTabWindow.
 *
 * It is itself a window, so it supports the same layout calls as any window
 * (setAnchor, setMargin, setOffset, setRelativeSize, centerWindow, ...), and it takes part in the
 * same hit testing and click dispatching. Create one with window.addButton("Label", () -> ...)
 * or build it yourself and add it with window.addChild(button).
 */
public class Button extends Window {
  private static final int DEFAULT_WIDTH = 60;
  private static final int DEFAULT_HEIGHT = 20;

  private static final int BORDER = 0xFF000000;
  private static final int HIGHLIGHT = 0xFFFFFFFF;
  private static final int SHADOW = 0xFF555555;
  private static final int FACE = 0xFFC6C6C6;
  private static final int FACE_HOVER = 0xFFDADADA;
  private static final int FACE_DISABLED = 0xFF8B8B8B;
  private static final int TEXT = 0xFF202020;
  private static final int TEXT_DISABLED = 0xFF606060;
  private static final float DEFAULT_FONT_SIZE = 1.0F;

  private Component label;
  private Runnable action;
  private boolean enabled = true;
  private float fontSize = DEFAULT_FONT_SIZE;
  private int faceColor = FACE;

  public Button(int width, int height, Component label, Runnable action) {
    super(width, height);
    setTitle(Component.empty());
    this.label = label == null ? Component.empty() : label;
    this.action = action;
    // Left click presses the button.
    setClickListener((window, localX, localY, mouseButton) -> {
      if (mouseButton == 0) {
        press();
      }
    });
  }

  public Button(Component label, Runnable action) {
    this(DEFAULT_WIDTH, DEFAULT_HEIGHT, label, action);
  }

  /** Sets what happens when the button is clicked. */
  public void setAction(Runnable action) {
    this.action = action;
  }

  public void setLabel(Component label) {
    this.label = label == null ? Component.empty() : label;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public void setBackgroundColor(int argbColor) {
    super.setBackgroundColor(argbColor);
    this.faceColor = argbColor;
  }

  public boolean isEnabled() {
    return enabled;
  }

  /** Scales the button label text; 1.0 is default size. */
  public void setFontSize(float fontSize) {
    if (!Float.isFinite(fontSize) || fontSize <= 0.0F) {
      this.fontSize = DEFAULT_FONT_SIZE;
      return;
    }
    this.fontSize = fontSize;
  }

  /** Runs the button's action (if enabled). Also usable to trigger the button from code. */
  public void press() {
    if (!enabled || action == null) {
      return;
    }
    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    action.run();
  }

  @Override
  protected void drawSelf(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
    int w = getWidth();
    int h = getHeight();
    boolean hovered = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

    int face = !enabled ? FACE_DISABLED : hovered ? lightenColor(faceColor, 0x14) : faceColor;
    graphics.fill(x, y, x + w, y + h, BORDER);
    graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
    graphics.fill(x + 1, y + 1, x + w - 1, y + 2, HIGHLIGHT);
    graphics.fill(x + 1, y + 1, x + 2, y + h - 1, HIGHLIGHT);
    graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, SHADOW);
    graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, SHADOW);

    String[] lines = label.getString().split("\\n", -1);
    int lineHeight = Math.max(1, Math.round(font.lineHeight * fontSize));
    int blockHeight = lineHeight * lines.length;
    int textY = y + (h - blockHeight) / 2;

    for (String line : lines) {
      Component lineComponent = Component.literal(line);
      int lineWidth = Math.round(font.width(lineComponent) * fontSize);
      int textX = x + (w - lineWidth) / 2;
      if (Math.abs(fontSize - 1.0F) < 0.0001F) {
        graphics.text(font, lineComponent, textX, textY, enabled ? TEXT : TEXT_DISABLED, false);
      } else {
        graphics.pose().pushMatrix();
        graphics.pose().translate(textX, textY);
        graphics.pose().scale(fontSize, fontSize);
        graphics.text(font, lineComponent, 0, 0, enabled ? TEXT : TEXT_DISABLED, false);
        graphics.pose().popMatrix();
      }
      textY += lineHeight;
    }
  }

  private static int lightenColor(int argb, int amount) {
    int a = (argb >>> 24) & 0xFF;
    int r = Math.min(255, ((argb >>> 16) & 0xFF) + amount);
    int g = Math.min(255, ((argb >>> 8) & 0xFF) + amount);
    int b = Math.min(255, (argb & 0xFF) + amount);
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  public void setSelected(boolean b) {



  }
}

