package name.minecad.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Simple in-window text input that supports either free-form text or numeric values.
 */
public class WindowTextField extends Window {
  public enum InputMode {
    TEXT,
    NUMERIC
  }

  private static final int BORDER = 0xFF000000;
  private static final int FACE = 0xFFFFFFFF;
  private static final int FACE_FOCUSED = 0xFFF2F8FF;
  private static final int HIGHLIGHT = 0xFFFFFFFF;
  private static final int SHADOW = 0xFF666666;
  private static final int TEXT_COLOR = 0xFF202020;
  private static final int PLACEHOLDER_COLOR = 0xFF7A7A7A;
  private static final int LABEL_COLOR = 0xFF404040;

  private final InputMode mode;
  private final StringBuilder value = new StringBuilder();
  private Component label = Component.empty();
  private String placeholder = "";
  private float fontScale = 1.0F;

  private boolean focused;
  private int caret;
  private int horizontalOffset;

  public WindowTextField(int width, int height, Component label, InputMode mode) {
    super(width, height);
    setTitle(Component.empty());
    this.label = label == null ? Component.empty() : label;
    this.mode = mode == null ? InputMode.TEXT : mode;
    setClickListener((window, localX, localY, button) -> {
      if (button == 0) {
        moveCaretToMouse((int) Math.round(localX));
      }
    });
  }

  public void setLabel(Component label) {
    this.label = label == null ? Component.empty() : label;
  }

  public void setPlaceholder(String placeholder) {
    this.placeholder = placeholder == null ? "" : placeholder;
  }

  public void setFontScale(float fontScale) {
    if (!Float.isFinite(fontScale) || fontScale <= 0.0F) {
      this.fontScale = 1.0F;
      return;
    }
    this.fontScale = fontScale;
  }

  public String getText() {
    return value.toString();
  }

  public void setText(String text) {
    value.setLength(0);
    caret = 0;
    horizontalOffset = 0;
    if (text != null && !text.isEmpty()) {
      appendText(text);
    }
    caret = Math.min(caret, value.length());
    horizontalOffset = Math.min(horizontalOffset, caret);
  }

  public void setFocused(boolean focused) {
    this.focused = focused;
  }

  public boolean isFocused() {
    return focused;
  }

  public boolean handleKeyPress(int keyCode) {
    if (!focused) {
      return false;
    }

    if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
      if (caret > 0) {
        value.deleteCharAt(caret - 1);
        caret--;
      }
      return true;
    }
    if (keyCode == GLFW.GLFW_KEY_DELETE) {
      if (caret < value.length()) {
        value.deleteCharAt(caret);
      }
      return true;
    }
    if (keyCode == GLFW.GLFW_KEY_LEFT) {
      caret = Math.max(0, caret - 1);
      return true;
    }
    if (keyCode == GLFW.GLFW_KEY_RIGHT) {
      caret = Math.min(value.length(), caret + 1);
      return true;
    }
    if (keyCode == GLFW.GLFW_KEY_HOME) {
      caret = 0;
      return true;
    }
    if (keyCode == GLFW.GLFW_KEY_END) {
      caret = value.length();
      return true;
    }

    if (isControlDown() && keyCode == GLFW.GLFW_KEY_V) {
      String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
      appendText(clipboard == null ? "" : clipboard);
      return true;
    }

    Character typed = keyCodeToChar(keyCode, isShiftDown());
    if (typed == null) {
      return false;
    }
    if (!isAllowed(typed)) {
      return true;
    }

    value.insert(caret, typed);
    caret++;
    return true;
  }

  @Override
  protected void drawSelf(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
    int w = getWidth();
    int h = getHeight();

    graphics.fill(x, y, x + w, y + h, BORDER);
    graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, focused ? FACE_FOCUSED : FACE);
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

    int textLeft = x + 4;
    int textHeight = Math.max(1, Math.round(font.lineHeight * fontScale));
    int textTop = y + (h - textHeight) / 2;
    int maxTextWidth = Math.max(1, w - 8);
    int logicalMaxTextWidth = Math.max(1, (int) Math.floor(maxTextWidth / fontScale));

    ensureCaretVisible(font, logicalMaxTextWidth);
    String display = visibleText(font, logicalMaxTextWidth);
    boolean empty = value.length() == 0;

    if (empty && !focused && !placeholder.isEmpty()) {
      drawScaledText(graphics, font, Component.literal(placeholder), textLeft, textTop, PLACEHOLDER_COLOR);
    } else {
      drawScaledText(graphics, font, Component.literal(display), textLeft, textTop, TEXT_COLOR);
    }

    if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
      int caretPixel = font.width(value.substring(horizontalOffset, caret));
      int scaledCaretPixel = Math.round(caretPixel * fontScale);
      int caretX = textLeft + Math.min(scaledCaretPixel, maxTextWidth);
      graphics.fill(caretX, y + 3, caretX + 1, y + h - 3, TEXT_COLOR);
    }
  }

  private void appendText(String text) {
    if (text == null || text.isEmpty()) {
      return;
    }
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (isAllowed(c)) {
        value.insert(caret, c);
        caret++;
      }
    }
  }

  private boolean isAllowed(char c) {
    if (mode == InputMode.TEXT) {
      return c >= 32 && c != 127;
    }

    if (Character.isDigit(c)) {
      return true;
    }
    if (c == '-') {
      return caret == 0 && value.indexOf("-") < 0;
    }
    if (c == '.') {
      return value.indexOf(".") < 0;
    }
    return false;
  }

  private void moveCaretToMouse(int localX) {
    Font font = Minecraft.getInstance().font;
    int textPixel = (int) Math.round(Math.max(0, localX - 4) / fontScale);
    int index = horizontalOffset;
    int width = 0;
    while (index < value.length()) {
      int charWidth = font.width(String.valueOf(value.charAt(index)));
      if (width + charWidth / 2 >= textPixel) {
        break;
      }
      width += charWidth;
      index++;
    }
    caret = Math.max(0, Math.min(value.length(), index));
  }

  private void ensureCaretVisible(Font font, int maxTextWidth) {
    horizontalOffset = Math.max(0, Math.min(horizontalOffset, caret));

    while (horizontalOffset < caret && font.width(value.substring(horizontalOffset, caret)) > maxTextWidth) {
      horizontalOffset++;
    }

    while (horizontalOffset > 0 && font.width(value.substring(horizontalOffset - 1, caret)) <= maxTextWidth) {
      horizontalOffset--;
    }
  }

  private String visibleText(Font font, int maxTextWidth) {
    int end = horizontalOffset;
    int width = 0;
    while (end < value.length()) {
      int charWidth = font.width(String.valueOf(value.charAt(end)));
      if (width + charWidth > maxTextWidth) {
        break;
      }
      width += charWidth;
      end++;
    }
    return value.substring(horizontalOffset, end);
  }

  private static boolean isControlDown() {
    long handle = Minecraft.getInstance().getWindow().handle();
    return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
        || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
  }

  private static boolean isShiftDown() {
    long handle = Minecraft.getInstance().getWindow().handle();
    return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
        || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
  }

  private static Character keyCodeToChar(int keyCode, boolean shifted) {
    if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
      char base = (char) ('a' + (keyCode - GLFW.GLFW_KEY_A));
      return shifted ? Character.toUpperCase(base) : base;
    }
    if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
      String shiftedDigits = ")!@#$%^&*(";
      int index = keyCode - GLFW.GLFW_KEY_0;
      return shifted ? shiftedDigits.charAt(index) : (char) ('0' + index);
    }

    return switch (keyCode) {
      case GLFW.GLFW_KEY_SPACE -> ' ';
      case GLFW.GLFW_KEY_MINUS -> shifted ? '_' : '-';
      case GLFW.GLFW_KEY_EQUAL -> shifted ? '+' : '=';
      case GLFW.GLFW_KEY_PERIOD -> shifted ? '>' : '.';
      case GLFW.GLFW_KEY_COMMA -> shifted ? '<' : ',';
      case GLFW.GLFW_KEY_SLASH -> shifted ? '?' : '/';
      case GLFW.GLFW_KEY_SEMICOLON -> shifted ? ':' : ';';
      case GLFW.GLFW_KEY_APOSTROPHE -> shifted ? '"' : '\'';
      case GLFW.GLFW_KEY_LEFT_BRACKET -> shifted ? '{' : '[';
      case GLFW.GLFW_KEY_RIGHT_BRACKET -> shifted ? '}' : ']';
      case GLFW.GLFW_KEY_BACKSLASH -> shifted ? '|' : '\\';
      case GLFW.GLFW_KEY_GRAVE_ACCENT -> shifted ? '~' : '`';
      default -> null;
    };
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

