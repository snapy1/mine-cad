package name.minecad.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A simple text panel used by non-3D tabs (settings/help/exports placeholders).
 */
public class TextPanel extends Window {
  private static final int DEFAULT_TEXT_COLOR = 0xFF404040;
  private static final float DEFAULT_FONT_SIZE = 1.0F;
  private static final int DEFAULT_SCROLLBAR_SIZE = 6;
  private static final int DEFAULT_MIN_THUMB_SIZE = 12;
  private static final int DEFAULT_SCROLL_STEP_X = 12;
  private static final int DEFAULT_SCROLL_STEP_Y = 14;
  private static final int TRACK_DARK = 0xFF373737;
  private static final int TRACK_FACE = 0xFF8B8B8B;
  private static final int TRACK_LIGHT = 0xFFFFFFFF;
  private static final int THUMB_FACE = 0xFFC6C6C6;
  private static final int THUMB_LIGHT = 0xFFFFFFFF;
  private static final int THUMB_DARK = 0xFF555555;
  private static final int THUMB_GRIP = 0xFF8B8B8B;

  private static final int CONTENT_LEFT_PADDING = 8;
  private static final int CONTENT_TOP_PADDING = 24;
  private static final int CONTENT_RIGHT_PADDING = 8;
  private static final int CONTENT_BOTTOM_PADDING = 10;

  private int scrollX;
  private int scrollY;
  private int scrollbarSize = DEFAULT_SCROLLBAR_SIZE;
  private int minThumbSize = DEFAULT_MIN_THUMB_SIZE;
  private int scrollStepX = DEFAULT_SCROLL_STEP_X;
  private int scrollStepY = DEFAULT_SCROLL_STEP_Y;

  private int maxScrollX;
  private int maxScrollY;
  private int viewportWidth;
  private int viewportHeight;
  private int verticalScrollbarX;
  private int verticalScrollbarY;
  private int verticalScrollbarHeight;
  private int verticalThumbY;
  private int verticalThumbHeight;
  private int horizontalScrollbarX;
  private int horizontalScrollbarY;
  private int horizontalScrollbarWidth;
  private int horizontalThumbX;
  private int horizontalThumbWidth;

  private boolean showVerticalScrollbar;
  private boolean showHorizontalScrollbar;
  private boolean wrapText = true;

  private boolean draggingScroll;

  public record StyledTextLine(Component text, int color, float fontSize) {
    public StyledTextLine {
      text = text == null ? Component.empty() : text;
      fontSize = Float.isFinite(fontSize) && fontSize > 0.0F ? fontSize : DEFAULT_FONT_SIZE;
    }
  }

  private final List<StyledTextLine> lines = new ArrayList<>();

  public TextPanel(int width, int height, Component title) {
    super(width, height);
    setTitle(title == null ? Component.empty() : title);
    setBackgroundColor(0xFFF6F6F6);
  }

  public void setLines(List<Component> newLines) {
    lines.clear();
    if (newLines != null) {
      for (Component line : newLines) {
        lines.add(new StyledTextLine(line, DEFAULT_TEXT_COLOR, DEFAULT_FONT_SIZE));
      }
    }
    clampScroll();
  }

  public void setLines(Component... newLines) {
    lines.clear();
    if (newLines != null) {
      for (Component line : newLines) {
        lines.add(new StyledTextLine(line, DEFAULT_TEXT_COLOR, DEFAULT_FONT_SIZE));
      }
    }
    clampScroll();
  }

  public void setStyledLines(List<StyledTextLine> newLines) {
    lines.clear();
    if (newLines != null) {
      lines.addAll(newLines);
    }
    clampScroll();
  }

  public void setStyledLines(StyledTextLine... newLines) {
    lines.clear();
    if (newLines != null) {
      for (StyledTextLine line : newLines) {
        lines.add(line == null ? new StyledTextLine(Component.empty(), DEFAULT_TEXT_COLOR, DEFAULT_FONT_SIZE) : line);
      }
    }
    clampScroll();
  }

  public void setScrollbarSize(int size) {
    scrollbarSize = Math.max(4, size);
  }

  public void setScrollbarMinThumbSize(int size) {
    minThumbSize = Math.max(6, size);
  }

  public void setScrollStep(int horizontalPixels, int verticalPixels) {
    scrollStepX = Math.max(1, horizontalPixels);
    scrollStepY = Math.max(1, verticalPixels);
  }

  public static StyledTextLine line(Component text, int color, float fontSize) {
    return new StyledTextLine(text, color, fontSize);
  }

  public static StyledTextLine line(String text, int color, float fontSize) {
    return new StyledTextLine(Component.literal(text == null ? "" : text), color, fontSize);
  }

  /** Enables word-wrapping to keep text within panel width (no horizontal scrolling). */
  public void setWrapText(boolean wrapText) {
    this.wrapText = wrapText;
    if (wrapText) {
      scrollX = 0;
      maxScrollX = 0;
      showHorizontalScrollbar = false;
    }
    clampScroll();
  }

  @Override
  protected void drawSelf(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
    super.drawSelf(graphics, font, x, y, mouseX, mouseY);
    int contentLeft = x + CONTENT_LEFT_PADDING;
    int contentTop = y + CONTENT_TOP_PADDING;
    int contentWidth = Math.max(1, getWidth() - CONTENT_LEFT_PADDING - CONTENT_RIGHT_PADDING);
    int contentHeight = Math.max(1, getHeight() - CONTENT_TOP_PADDING - CONTENT_BOTTOM_PADDING);

    int availableWidth = contentWidth;
    int availableHeight = contentHeight;

    List<StyledTextLine> renderedLines = lines;
    int textContentHeight;
    int textContentWidth;
    boolean needVertical;
    boolean needHorizontal;

    if (wrapText) {
      // Wrapping width depends on whether the vertical scrollbar is visible, so resolve in two passes.
      for (int pass = 0; pass < 2; pass++) {
        renderedLines = wrapStyledLines(font, availableWidth);
        textContentHeight = 0;
        for (StyledTextLine line : renderedLines) {
          textContentHeight += lineHeight(font, line);
        }
        needVertical = textContentHeight > availableHeight;
        int nextWidth = needVertical ? Math.max(1, contentWidth - scrollbarSize - 2) : contentWidth;
        if (nextWidth == availableWidth) {
          break;
        }
        availableWidth = nextWidth;
      }
      textContentHeight = 0;
      textContentWidth = 0;
      for (StyledTextLine line : renderedLines) {
        textContentHeight += lineHeight(font, line);
        textContentWidth = Math.max(textContentWidth, font.width(line.text()));
      }
      needVertical = textContentHeight > availableHeight;
      needHorizontal = false;
      scrollX = 0;
    } else {
      textContentHeight = 0;
      textContentWidth = 0;
      for (StyledTextLine line : lines) {
        textContentHeight += lineHeight(font, line);
        textContentWidth = Math.max(textContentWidth, font.width(line.text()));
      }
      needVertical = textContentHeight > availableHeight;
      if (needVertical) {
        availableWidth = Math.max(1, availableWidth - scrollbarSize - 2);
      }
      needHorizontal = textContentWidth > availableWidth;
      if (needHorizontal) {
        availableHeight = Math.max(1, availableHeight - scrollbarSize - 2);
      }
      if (!needVertical && textContentHeight > availableHeight) {
        needVertical = true;
        availableWidth = Math.max(1, availableWidth - scrollbarSize - 2);
      }
      renderedLines = lines;
    }

    showVerticalScrollbar = needVertical;
    showHorizontalScrollbar = needHorizontal;
    viewportWidth = availableWidth;
    viewportHeight = availableHeight;
    maxScrollX = Math.max(0, textContentWidth - viewportWidth);
    maxScrollY = Math.max(0, textContentHeight - viewportHeight);
    clampScroll();

    int textY = contentTop - scrollY;
    for (StyledTextLine line : renderedLines) {
      int lineHeight = lineHeight(font, line);
      if (textY + lineHeight >= contentTop && textY <= contentTop + viewportHeight) {
        Component visible = wrapText
            ? line.text()
            : visibleSlice(font, line.text(), scrollX, viewportWidth);
        if (!visible.getString().isEmpty()) {
          graphics.text(font, visible, contentLeft, textY, line.color(), false);
        }
      }
      textY += lineHeight;
      if (textY > contentTop + viewportHeight) {
        break;
      }
    }

    if (showVerticalScrollbar) {
      verticalScrollbarX = contentLeft + viewportWidth + 2;
      verticalScrollbarY = contentTop;
      verticalScrollbarHeight = viewportHeight;
      verticalThumbHeight = thumbSize(viewportHeight, textContentHeight);
      verticalThumbY = verticalScrollbarY + thumbOffset(scrollY, maxScrollY, viewportHeight, verticalThumbHeight);

      int barLeft = verticalScrollbarX;
      int barTop = verticalScrollbarY;
      int barRight = verticalScrollbarX + scrollbarSize;
      int barBottom = verticalScrollbarY + verticalScrollbarHeight;
      // Inset track: dark top/left edge, light bottom/right edge.
      graphics.fill(barLeft, barTop, barRight, barBottom, TRACK_LIGHT);
      graphics.fill(barLeft, barTop, barRight - 1, barBottom - 1, TRACK_DARK);
      graphics.fill(barLeft + 1, barTop + 1, barRight - 1, barBottom - 1, TRACK_FACE);

      int thumbLeft = barLeft + 1;
      int thumbTop = verticalThumbY;
      int thumbRight = barRight - 1;
      int thumbBottom = verticalThumbY + verticalThumbHeight;
      // Raised thumb: light top/left edge, dark bottom/right edge, with grip lines.
      graphics.fill(thumbLeft, thumbTop, thumbRight, thumbBottom, THUMB_DARK);
      graphics.fill(thumbLeft, thumbTop, thumbRight - 1, thumbBottom - 1, THUMB_LIGHT);
      graphics.fill(thumbLeft + 1, thumbTop + 1, thumbRight - 1, thumbBottom - 1, THUMB_FACE);
      int gripCenter = thumbTop + (verticalThumbHeight / 2);
      for (int i = -2; i <= 2; i += 2) {
        int gripY = gripCenter + i;
        if (gripY > thumbTop + 1 && gripY < thumbBottom - 2) {
          graphics.fill(thumbLeft + 2, gripY, thumbRight - 2, gripY + 1, THUMB_GRIP);
        }
      }
    }

    if (showHorizontalScrollbar) {
      horizontalScrollbarX = contentLeft;
      horizontalScrollbarY = contentTop + viewportHeight + 2;
      horizontalScrollbarWidth = viewportWidth;
      horizontalThumbWidth = thumbSize(viewportWidth, textContentWidth);
      horizontalThumbX = horizontalScrollbarX + thumbOffset(scrollX, maxScrollX, viewportWidth, horizontalThumbWidth);

      int barLeft = horizontalScrollbarX;
      int barTop = horizontalScrollbarY;
      int barRight = horizontalScrollbarX + horizontalScrollbarWidth;
      int barBottom = horizontalScrollbarY + scrollbarSize;
      graphics.fill(barLeft, barTop, barRight, barBottom, TRACK_LIGHT);
      graphics.fill(barLeft, barTop, barRight - 1, barBottom - 1, TRACK_DARK);
      graphics.fill(barLeft + 1, barTop + 1, barRight - 1, barBottom - 1, TRACK_FACE);

      int thumbLeft = horizontalThumbX;
      int thumbTop = barTop + 1;
      int thumbRight = horizontalThumbX + horizontalThumbWidth;
      int thumbBottom = barBottom - 1;
      graphics.fill(thumbLeft, thumbTop, thumbRight, thumbBottom, THUMB_DARK);
      graphics.fill(thumbLeft, thumbTop, thumbRight - 1, thumbBottom - 1, THUMB_LIGHT);
      graphics.fill(thumbLeft + 1, thumbTop + 1, thumbRight - 1, thumbBottom - 1, THUMB_FACE);
      int gripCenter = thumbLeft + (horizontalThumbWidth / 2);
      for (int i = -2; i <= 2; i += 2) {
        int gripX = gripCenter + i;
        if (gripX > thumbLeft + 1 && gripX < thumbRight - 2) {
          graphics.fill(gripX, thumbTop + 2, gripX + 1, thumbBottom - 2, THUMB_GRIP);
        }
      }
    }
  }

  @Override
  public boolean beginMouseDrag(int button) {
    draggingScroll = button == 0 && (showVerticalScrollbar || showHorizontalScrollbar);
    return draggingScroll;
  }

  @Override
  public void onMouseDrag(double localX, double localY, double deltaX, double deltaY, int button) {
    if (!draggingScroll || button != 0) {
      return;
    }
    if (showHorizontalScrollbar && maxScrollX > 0) {
      scrollX = Math.max(0, Math.min(maxScrollX, scrollX + (int) Math.round(deltaX)));
    }
    if (showVerticalScrollbar && maxScrollY > 0) {
      scrollY = Math.max(0, Math.min(maxScrollY, scrollY + (int) Math.round(deltaY)));
    }
  }

  @Override
  public void endMouseDrag(int button) {
    if (button == 0) {
      draggingScroll = false;
    }
  }

  @Override
  public boolean onMouseScroll(double localX, double localY, double horizontalAmount, double verticalAmount) {
    boolean consumed = false;
    if (showVerticalScrollbar && maxScrollY > 0 && verticalAmount != 0.0) {
      int amount = (int) Math.round(verticalAmount * scrollStepY);
      scrollY = Math.max(0, Math.min(maxScrollY, scrollY - amount));
      consumed = true;
    }
    if (showHorizontalScrollbar && maxScrollX > 0 && horizontalAmount != 0.0) {
      int amount = (int) Math.round(horizontalAmount * scrollStepX);
      scrollX = Math.max(0, Math.min(maxScrollX, scrollX - amount));
      consumed = true;
    }
    return consumed;
  }

  private void clampScroll() {
    scrollX = Math.max(0, Math.min(scrollX, maxScrollX));
    scrollY = Math.max(0, Math.min(scrollY, maxScrollY));
  }

  private int lineHeight(Font font, StyledTextLine line) {
    // Font scaling is stored per line; current GUI text API draws at one font size,
    // so we apply the value to spacing until scaled text drawing is introduced.
    return Math.max(1, Math.round((font.lineHeight + 2) * line.fontSize()));
  }

  private int thumbSize(int viewport, int content) {
    if (content <= 0) {
      return viewport;
    }
    int scaled = (int) Math.round((double) viewport * viewport / (double) content);
    return Math.max(minThumbSize, Math.min(viewport, scaled));
  }

  private int thumbOffset(int scroll, int maxScroll, int viewport, int thumb) {
    if (maxScroll <= 0) {
      return 0;
    }
    int travel = Math.max(0, viewport - thumb);
    return (int) Math.round((double) scroll * travel / (double) maxScroll);
  }

  private Component visibleSlice(Font font, Component text, int startPixels, int maxPixels) {
    String value = text == null ? "" : text.getString();
    if (value.isEmpty() || maxPixels <= 0) {
      return Component.empty();
    }

    int startIndex = 0;
    int consumedPixels = 0;
    while (startIndex < value.length() && consumedPixels < startPixels) {
      int charWidth = font.width(String.valueOf(value.charAt(startIndex)));
      if (consumedPixels + charWidth > startPixels) {
        break;
      }
      consumedPixels += charWidth;
      startIndex++;
    }

    int endIndex = startIndex;
    int usedPixels = 0;
    while (endIndex < value.length()) {
      int charWidth = font.width(String.valueOf(value.charAt(endIndex)));
      if (usedPixels + charWidth > maxPixels) {
        break;
      }
      usedPixels += charWidth;
      endIndex++;
    }
    if (endIndex <= startIndex) {
      return Component.empty();
    }
    return Component.literal(value.substring(startIndex, endIndex));
  }

  private List<StyledTextLine> wrapStyledLines(Font font, int maxWidth) {
    List<StyledTextLine> wrapped = new ArrayList<>();
    int safeWidth = Math.max(1, maxWidth);
    for (StyledTextLine line : lines) {
      String raw = line == null || line.text() == null ? "" : line.text().getString();
      if (raw.isEmpty()) {
        wrapped.add(new StyledTextLine(Component.empty(), line == null ? DEFAULT_TEXT_COLOR : line.color(), line == null ? DEFAULT_FONT_SIZE : line.fontSize()));
        continue;
      }

      String[] logicalLines = raw.split("\\R", -1);
      for (String logical : logicalLines) {
        if (logical.isEmpty()) {
          wrapped.add(new StyledTextLine(Component.empty(), line.color(), line.fontSize()));
          continue;
        }

        String remaining = logical;
        while (!remaining.isEmpty()) {
          int split = findWrapSplitIndex(font, remaining, safeWidth);
          String segment = remaining.substring(0, split).stripTrailing();
          wrapped.add(new StyledTextLine(Component.literal(segment), line.color(), line.fontSize()));
          if (split >= remaining.length()) {
            break;
          }
          remaining = remaining.substring(split).stripLeading();
        }
      }
    }
    return wrapped;
  }

  private int findWrapSplitIndex(Font font, String value, int maxWidth) {
    if (value.isEmpty()) {
      return 0;
    }

    int width = 0;
    int lastWhitespace = -1;
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      int charWidth = font.width(String.valueOf(ch));
      if (Character.isWhitespace(ch)) {
        lastWhitespace = i;
      }
      if (width + charWidth > maxWidth) {
        if (lastWhitespace >= 0) {
          return Math.max(1, lastWhitespace + 1);
        }
        return Math.max(1, i);
      }
      width += charWidth;
    }
    return value.length();
  }
}

