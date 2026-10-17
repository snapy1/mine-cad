package name.minecad.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A compact pane that stacks child controls in a virtual vertical list and scrolls with the mouse wheel
 * (also while hovering the controls) or by dragging the pane.
 */
public class ScrollableControlPane extends Window {
  private static final int CONTENT_LEFT = 6;
  private static final int CONTENT_TOP = 22;
  private static final int CONTENT_BOTTOM = 6;
  private static final int SCROLLBAR_WIDTH = 8;
  private static final int SCROLLBAR_RIGHT_MARGIN = 4;
  private static final int SCROLL_STEP = 14;
  private static final int MIN_THUMB_HEIGHT = 14;

  private static final int TRACK_DARK = 0xFF373737;
  private static final int TRACK_FACE = 0xFF8B8B8B;
  private static final int TRACK_LIGHT = 0xFFFFFFFF;
  private static final int THUMB_FACE = 0xFFC6C6C6;
  private static final int THUMB_LIGHT = 0xFFFFFFFF;
  private static final int THUMB_DARK = 0xFF555555;
  private static final int THUMB_GRIP = 0xFF8B8B8B;

  private final List<Entry> entries = new ArrayList<>();
  private int contentHeight;
  private int scrollY;
  private boolean draggingThumb;

  private record Entry(Window child, int x, int y, int height) {
  }

  public ScrollableControlPane(int width, int height, Component title) {
    super(width, height);
    setTitle(title == null ? Component.empty() : title);
    setBackgroundColor(0xFFFFFFFF);
  }

  /** Width left for controls (the scrollbar and margins are already subtracted). */
  public int getContentWidth() {
    return getWidth() - CONTENT_LEFT - SCROLLBAR_WIDTH - SCROLLBAR_RIGHT_MARGIN - 3;
  }

  public <T extends Window> T addScrollableChild(T child, int x, int y) {
    if (child == null) {
      throw new IllegalArgumentException("Scrollable child cannot be null");
    }
    child.setAnchor(Window.Anchor.TOP_LEFT);
    super.addChild(child);
    entries.add(new Entry(child, x, y, child.getHeight()));
    contentHeight = Math.max(contentHeight, y + child.getHeight());
    relayout();
    return child;
  }

  // ---------------------------------------------------------------- input

  @Override
  public boolean onMouseScroll(double localX, double localY, double horizontalAmount, double verticalAmount) {
    if (maxScroll() <= 0 || verticalAmount == 0.0) {
      return false;
    }
    setScroll(scrollY - (int) Math.round(Math.signum(verticalAmount) * SCROLL_STEP));
    return true;
  }

  @Override
  public boolean beginMouseDrag(int button) {
    // Only the bare pane (not a control) receives this, so dragging empty space or the bar scrolls.
    draggingThumb = button == 0 && maxScroll() > 0;
    return draggingThumb;
  }

  @Override
  public void onMouseDrag(double localX, double localY, double deltaX, double deltaY, int button) {
    if (!draggingThumb) {
      return;
    }
    int travel = Math.max(1, viewportHeight() - 2 - thumbHeight());
    setScroll(scrollY + (int) Math.round(deltaY * maxScroll() / travel));
  }

  @Override
  public void endMouseDrag(int button) {
    draggingThumb = false;
  }

  // ---------------------------------------------------------------- drawing

  @Override
  protected void drawSelf(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
    super.drawSelf(graphics, font, x, y, mouseX, mouseY);
    relayout();

    if (maxScroll() <= 0) {
      return;
    }

    int viewportHeight = viewportHeight();
    int left = x + getWidth() - SCROLLBAR_WIDTH - SCROLLBAR_RIGHT_MARGIN;
    int top = y + CONTENT_TOP;
    int right = left + SCROLLBAR_WIDTH;
    int bottom = top + viewportHeight;

    // Inset track: dark top/left edge, light bottom/right edge.
    graphics.fill(left, top, right, bottom, TRACK_LIGHT);
    graphics.fill(left, top, right - 1, bottom - 1, TRACK_DARK);
    graphics.fill(left + 1, top + 1, right - 1, bottom - 1, TRACK_FACE);

    int thumbHeight = thumbHeight();
    int travel = Math.max(0, viewportHeight - 2 - thumbHeight);
    int thumbTop = top + 1 + (int) Math.round((double) scrollY * travel / Math.max(1, maxScroll()));
    int thumbLeft = left + 1;
    int thumbRight = right - 1;
    int thumbBottom = thumbTop + thumbHeight;

    // Raised thumb: light top/left edge, dark bottom/right edge, with grip lines.
    graphics.fill(thumbLeft, thumbTop, thumbRight, thumbBottom, THUMB_DARK);
    graphics.fill(thumbLeft, thumbTop, thumbRight - 1, thumbBottom - 1, THUMB_LIGHT);
    graphics.fill(thumbLeft + 1, thumbTop + 1, thumbRight - 1, thumbBottom - 1, THUMB_FACE);
    int gripCenter = thumbTop + thumbHeight / 2;
    for (int i = -2; i <= 2; i += 2) {
      int gripY = gripCenter + i;
      if (gripY > thumbTop + 1 && gripY < thumbBottom - 2) {
        graphics.fill(thumbLeft + 2, gripY, thumbRight - 2, gripY + 1, THUMB_GRIP);
      }
    }
  }

  // ---------------------------------------------------------------- layout

  private void setScroll(int value) {
    scrollY = Math.max(0, Math.min(maxScroll(), value));
    relayout();
  }

  private void relayout() {
    scrollY = Math.max(0, Math.min(maxScroll(), scrollY));
    for (Entry entry : entries) {
      entry.child().setOffset(CONTENT_LEFT + entry.x(), CONTENT_TOP + entry.y() - scrollY);
    }
  }

  /** Controls are clipped (not hidden) to the viewport, so they slide smoothly in and out of view. */
  @Override
  protected int[] getChildClip() {
    int right = getWidth() - SCROLLBAR_WIDTH - SCROLLBAR_RIGHT_MARGIN - 1;
    return new int[] {3, CONTENT_TOP, right, CONTENT_TOP + viewportHeight()};
  }

  private int thumbHeight() {
    int viewport = viewportHeight();
    int scaled = (int) Math.round((double) (viewport - 2) * viewport / Math.max(1, contentHeight));
    return Math.max(MIN_THUMB_HEIGHT, Math.min(viewport - 2, scaled));
  }

  private int viewportHeight() {
    return Math.max(1, getHeight() - CONTENT_TOP - CONTENT_BOTTOM);
  }

  private int maxScroll() {
    return Math.max(0, contentHeight - viewportHeight());
  }

  public void SetHeight(int height) {
    this.setHeight(height);
  }
}

