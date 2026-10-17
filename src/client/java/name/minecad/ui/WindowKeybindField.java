package name.minecad.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Click to arm, then press a key, mouse button, or wheel to bind.
 */
public class WindowKeybindField extends Window {
  private static final int BORDER = 0xFF000000;
  private static final int FACE = 0xFFFFFFFF;
  private static final int FACE_ARMED = 0xFFEAF3FF;
  private static final int HIGHLIGHT = 0xFFFFFFFF;
  private static final int SHADOW = 0xFF666666;
  private static final int TEXT_COLOR = 0xFF202020;
  private static final int HINT_COLOR = 0xFF5A6E8C;
  private static final int LABEL_COLOR = 0xFF404040;

  private Component label = Component.empty();
  private String bindingText = "";
  private boolean focused;
  private boolean awaitingInput;
  private float fontScale = 1.0F;

  public WindowKeybindField(int width, int height, Component label) {
    super(width, height);
    setTitle(Component.empty());
    this.label = label == null ? Component.empty() : label;
    setClickListener((window, localX, localY, button) -> {
      if (button == 0) {
        awaitingInput = true;
      }
    });
  }

  public void setLabel(Component label) {
    this.label = label == null ? Component.empty() : label;
  }

  public void setBindingText(String bindingText) {
    this.bindingText = bindingText == null ? "" : bindingText.trim();
  }

  public String getBindingText() {
    return bindingText;
  }

  public void setFontScale(float fontScale) {
    if (!Float.isFinite(fontScale) || fontScale <= 0.0F) {
      this.fontScale = 1.0F;
      return;
    }
    this.fontScale = fontScale;
  }

  public void setFocused(boolean focused) {
    this.focused = focused;
    if (!focused) {
      awaitingInput = false;
    }
  }

  public boolean isAwaitingInput() {
    return focused && awaitingInput;
  }

  public boolean handleKeyPress(int keyCode) {
    if (!isAwaitingInput()) {
      return false;
    }

    if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
      awaitingInput = false;
      return true;
    }

    String keyToken = keyToken(keyCode);
    if (keyToken == null) {
      return false;
    }

    applyBindingFromPrimaryToken(keyToken);
    return true;
  }

  public boolean captureMouseButton(int mouseButton) {
    if (!isAwaitingInput()) {
      return false;
    }

    String token = switch (mouseButton) {
      case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "MOUSE_LEFT_CLICK";
      case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "MOUSE_RIGHT_CLICK";
      case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MOUSE_SCROLL_CLICK";
      default -> null;
    };

    if (token == null) {
      return false;
    }

    applyBindingFromPrimaryToken(token);
    return true;
  }

  public boolean captureMouseScroll() {
    if (!isAwaitingInput()) {
      return false;
    }

    applyBindingFromPrimaryToken("MOUSE_SCROLL");
    return true;
  }

  @Override
  protected void drawSelf(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
    int w = getWidth();
    int h = getHeight();

    graphics.fill(x, y, x + w, y + h, BORDER);
    graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, isAwaitingInput() ? FACE_ARMED : FACE);
    graphics.fill(x + 1, y + 1, x + w - 1, y + 2, HIGHLIGHT);
    graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, SHADOW);
    graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, SHADOW);

    if (!label.getString().isEmpty()) {
      int labelLineHeight = Math.max(1, Math.round(font.lineHeight * fontScale));
      int logicalLabelWidth = Math.max(1, (int) Math.floor(Math.max(1, w - 6) / fontScale));
      List<String> labelLines = wrapToWidth(font, label.getString(), logicalLabelWidth);
      int labelTop = y - labelLineHeight * labelLines.size() - 1;
      for (int i = 0; i < labelLines.size(); i++) {
        drawScaledText(graphics, font, Component.literal(labelLines.get(i)), x + 3, labelTop + i * labelLineHeight, LABEL_COLOR);
      }
    }

    String text = isAwaitingInput()
        ? "Press key, mouse, or wheel..."
        : (bindingText.isEmpty() ? "Unbound" : bindingText);
    int color = isAwaitingInput() ? HINT_COLOR : TEXT_COLOR;
    int maxTextWidth = Math.max(1, w - 8);
    int logicalMaxTextWidth = Math.max(1, (int) Math.floor(maxTextWidth / fontScale));
    int textHeight = Math.max(1, Math.round(font.lineHeight * fontScale));
    int textY = y + (h - textHeight) / 2;
    drawScaledText(graphics, font, Component.literal(trimToWidth(font, text, logicalMaxTextWidth)), x + 4, textY, color);
  }

  private void applyBindingFromPrimaryToken(String primaryToken) {
    List<String> tokens = modifierTokens();
    if (!tokens.contains(primaryToken)) {
      tokens.add(primaryToken);
    }
    bindingText = String.join(" + ", tokens);
    awaitingInput = false;
  }

  private static List<String> modifierTokens() {
    long handle = Minecraft.getInstance().getWindow().handle();
    List<String> tokens = new ArrayList<>(3);
    if (isAnyPressed(handle, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
      tokens.add("SHIFT");
    }
    if (isAnyPressed(handle, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
      tokens.add("CTRL");
    }
    if (isAnyPressed(handle, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT)) {
      tokens.add("ALT");
    }
    return tokens;
  }

  private static boolean isAnyPressed(long handle, int leftKey, int rightKey) {
    return GLFW.glfwGetKey(handle, leftKey) == GLFW.GLFW_PRESS
        || GLFW.glfwGetKey(handle, rightKey) == GLFW.GLFW_PRESS;
  }

  private static String keyToken(int keyCode) {
    if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
      return String.valueOf((char) ('A' + (keyCode - GLFW.GLFW_KEY_A)));
    }
    if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
      return String.valueOf((char) ('0' + (keyCode - GLFW.GLFW_KEY_0)));
    }
    if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F12) {
      return "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
    }

    return switch (keyCode) {
      case GLFW.GLFW_KEY_SPACE -> "SPACE";
      case GLFW.GLFW_KEY_TAB -> "TAB";
      case GLFW.GLFW_KEY_ENTER -> "ENTER";
      case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
      case GLFW.GLFW_KEY_ESCAPE -> "ESC";
      case GLFW.GLFW_KEY_UP -> "UP";
      case GLFW.GLFW_KEY_DOWN -> "DOWN";
      case GLFW.GLFW_KEY_LEFT -> "LEFT";
      case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
      case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "SHIFT";
      case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "CTRL";
      case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> "ALT";
      default -> null;
    };
  }

  private static String trimToWidth(Font font, String text, int maxWidth) {
    if (font.width(text) <= maxWidth) {
      return text;
    }
    String ellipsis = "...";
    int ellipsisWidth = font.width(ellipsis);
    int end = text.length();
    while (end > 0 && font.width(text.substring(0, end)) + ellipsisWidth > maxWidth) {
      end--;
    }
    return end <= 0 ? ellipsis : text.substring(0, end) + ellipsis;
  }

  private static List<String> wrapToWidth(Font font, String text, int maxWidth) {
    List<String> lines = new ArrayList<>();
    if (text == null || text.isEmpty()) {
      lines.add("");
      return lines;
    }

    int start = 0;
    while (start < text.length()) {
      int end = start + 1;
      int lastFitting = start + 1;
      while (end <= text.length()) {
        String slice = text.substring(start, end);
        if (font.width(slice) <= maxWidth) {
          lastFitting = end;
          end++;
        } else {
          break;
        }
      }
      if (lastFitting == start) {
        lastFitting = Math.min(start + 1, text.length());
      }
      lines.add(text.substring(start, lastFitting));
      start = lastFitting;
    }
    return lines;
  }

  private void drawScaledText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
    if (Math.abs(fontScale - 1.0F) < 0.0001F) {
      graphics.text(font, text, x, y, color, false);
      return;
    }
    graphics.pose().pushMatrix();
    graphics.pose().translate(x, y);
    graphics.pose().scale(fontScale, fontScale);
    graphics.text(font, text, 0, 0, color, false);
    graphics.pose().popMatrix();
  }
}

