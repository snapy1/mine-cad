package name.minecad.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A window that can contain any number of child windows (a tree).
 *
 * Layout of a child inside its parent:
 *  - the parent's padding insets the area children can use,
 *  - the child's margin is the space kept between the child and that area's edges,
 *  - the child's anchor picks where in the area it sits (top, bottom, centre, ...),
 *  - the child's offset then nudges it by a fixed amount.
 * Children without an anchor use the legacy placement: the parent's child offset
 * (see setChildOffset) unless the child has its own offset.
 */
public class Window {
  /** Where a child sits inside the usable area of its parent. */
  public enum Anchor {
    TOP_LEFT(0, 0), TOP(1, 0), TOP_RIGHT(2, 0),
    LEFT(0, 1), CENTER(1, 1), RIGHT(2, 1),
    BOTTOM_LEFT(0, 2), BOTTOM(1, 2), BOTTOM_RIGHT(2, 2);

    private final int horizontal;
    private final int vertical;
    private boolean isEnabled = true;

    // Static map tracking which windows are registered to each anchor
    private static final Map<Anchor, List<Window>> anchorToWindows = new HashMap<>();

    static {
      for (Anchor anchor : Anchor.values()) {
        anchorToWindows.put(anchor, new ArrayList<>());
      }
    }

    Anchor(int horizontal, int vertical) {
      this.horizontal = horizontal;
      this.vertical = vertical;
    }

    public boolean isEnabled() {
      return isEnabled;
    }

    public void setEnabled(boolean enabled) {
      isEnabled = enabled;
      if (!enabled) {
        // Disable all windows registered to this anchor
        for (Window window : anchorToWindows.getOrDefault(this, Collections.emptyList())) {
          window.setVisible(false);
        }
      }
    }

    /** Register a window as using this anchor (called automatically by window layout). */
    static void registerWindow(Anchor anchor, Window window) {
      if (anchor != null) {
        anchorToWindows.getOrDefault(anchor, new ArrayList<>()).add(window);
      }
    }

    /** Unregister a window from this anchor. */
    static void unregisterWindow(Anchor anchor, Window window) {
      if (anchor != null) {
        anchorToWindows.getOrDefault(anchor, new ArrayList<>()).remove(window);
      }
    }
  }

  /** Called when this window is the topmost window under a click. Coordinates are local to the window. */
  @FunctionalInterface
  public interface ClickListener {
    void onClick(Window window, double localX, double localY, int button);
  }

  /** Called when this window's visibility changes (e.g., tab selection). */
  @FunctionalInterface
  public interface TabSelectionListener {
    /** Fired when the window becomes visible (selected as active tab). */
    void onTabSelected(Window window);
    
    /** Fired when the window becomes invisible (deselected). */
    default void onTabDeselected(Window window) {}
  }

  /** The window under a point, plus that window's absolute screen position. */
  public record WindowHit(Window window, int x, int y) {
  }

  private static final int DEFAULT_BACKGROUND_COLOR = 0xFFC6C6C6;
  private static final int BORDER_COLOR = 0xFF000000;
  private static final int HIGHLIGHT_COLOR = 0xFFFFFFFF;
  private static final int SHADOW_COLOR = 0xFF555555;
  private static final int DEFAULT_TITLE_COLOR = 0xFF404040;
  private static final float DEFAULT_TITLE_SCALE = 1.0F;
  private static final int DEFAULT_CHILD_OFFSET_X = 8;
  private static final int DEFAULT_CHILD_OFFSET_Y = 24;
  private static final int MAX_NESTING_DEPTH = 64;
  private static final int TITLE_INSET_X = 8;
  private static final int TITLE_INSET_Y = 8;
  private static final int TITLE_LINE_GAP = 1;
  private static final float TITLE_WRAP_SCALE_THRESHOLD = 0.8F;

  private int width;
  private int height;
  private final List<Window> children = new ArrayList<>();
  private final String backgroundTextureFilePath;
  private int backgroundColor = DEFAULT_BACKGROUND_COLOR;
  private Component title = Component.empty();
  private int titleColor = DEFAULT_TITLE_COLOR;
  private float titleScale = DEFAULT_TITLE_SCALE;
  private boolean autoFitTitle;

  // Default position for children that have no anchor and no offset of their own.
  private int childOffsetX = DEFAULT_CHILD_OFFSET_X;
  private int childOffsetY = DEFAULT_CHILD_OFFSET_Y;

  // Layout properties (padding applies to this window's children, the rest to this window inside its parent).
  private int padding;
  private int margin;
  private Anchor anchor;
  private Window relativeAnchorTarget;
  private Anchor relativeAnchorTargetPoint = Anchor.TOP_LEFT;
  private Anchor relativeAnchorOwnPoint = Anchor.TOP_LEFT;
  private boolean hasOffset;
  private int offsetX;
  private int offsetY;
  private boolean centerX;
  private boolean centerY;
  private boolean visible = true;

  // Relative sizing as a fraction of the parent's usable area; zero means use the fixed size.
  private double relativeWidth;
  private double relativeHeight;

  private ClickListener clickListener;
  private TabSelectionListener tabSelectionListener;

  public Window(int width, int height, String backgroundTextureFilePath) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Window width and height must be positive");
    }
    this.width = width;
    this.height = height;
    this.backgroundTextureFilePath = backgroundTextureFilePath;
  }

  public Window(int width, int height) {
    this(width, height, (String) null);
  }

  public Window(int width, int height, Window nextWindow) {
    this(width, height);
    if (nextWindow != null) {
      addChild(nextWindow);
    }
  }

  // ---------------------------------------------------------------- appearance

  public void setBackgroundColor(int argbColor) {
    backgroundColor = argbColor;
  }

  public void setTitle(Component title) {
    this.title = title == null ? Component.empty() : title;
  }

  public void setTitleColor(int argbColor) {
    titleColor = argbColor;
  }

  public void setTitleScale(float scale) {
    if (!Float.isFinite(scale) || scale <= 0.0F) {
      titleScale = DEFAULT_TITLE_SCALE;
      return;
    }
    titleScale = scale;
  }

  public void setTitleStyle(int argbColor, float scale) {
    setTitleColor(argbColor);
    setTitleScale(scale);
  }

  /**
   * Opt in to fitting the title inside this window: it shrinks (or wraps onto two lines) when too long
   * and is centered. Off by default, in which case the title is drawn at the top-left as usual.
   */
  public void setAutoFitTitle(boolean autoFitTitle) {
    this.autoFitTitle = autoFitTitle;
  }

  public void setSize(int width, int height) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Window width and height must be positive");
    }
    this.width = width;
    this.height = height;
  }


  public void setHeight(int height) {
    if (height <= 0) {
      throw new IllegalArgumentException("Window height must be positive");
    }
    this.height = height;
  }

  public void setWidth(int width) {
    if (width <= 0) {
      throw new IllegalArgumentException("Window width must be positive");
    }
    this.width = width;
  }

  public int getWidth() {

    return width;

  }



  public int getHeight() {

    return height;

  }

  public void setVisible(boolean visible) {
    if (this.visible != visible) {
      this.visible = visible;
      if (tabSelectionListener != null) {
        if (visible) {
          tabSelectionListener.onTabSelected(this);
        } else {
          tabSelectionListener.onTabDeselected(this);
        }
      }
    }
  }

  public boolean isVisible() {
    return visible;
  }

  /** Set a listener to be notified when this window's tab is selected or deselected. */
  public void setTabSelectionListener(TabSelectionListener listener) {
    this.tabSelectionListener = listener;
  }

  /** Set a callback to execute when this tab is selected (becomes visible). */
  public void setOnTabSelected(Runnable onSelected) {
    this.tabSelectionListener = new TabSelectionListener() {
      @Override
      public void onTabSelected(Window window) {
        if (onSelected != null) onSelected.run();
      }
      
      @Override
      public void onTabDeselected(Window window) {}
    };
  }

  /** Set a callback to execute when this tab is deselected (becomes invisible). */
  public void setOnTabDeselected(Runnable onDeselected) {
    this.tabSelectionListener = new TabSelectionListener() {
      @Override
      public void onTabSelected(Window window) {}
      
      @Override
      public void onTabDeselected(Window window) {
        if (onDeselected != null) onDeselected.run();
      }
    };
  }

  /** Set callbacks for both tab selection and deselection. */
  public void setTabCallbacks(Runnable onSelected, Runnable onDeselected) {
    this.tabSelectionListener = new TabSelectionListener() {
      @Override
      public void onTabSelected(Window window) {
        if (onSelected != null) onSelected.run();
      }
      
      @Override
      public void onTabDeselected(Window window) {
        if (onDeselected != null) onDeselected.run();
      }
    };
  }



  public String getBackgroundTextureFilePath() {
    return backgroundTextureFilePath;
  }

  // ---------------------------------------------------------------- hierarchy

  /**

   * Creates a button, adds it as a child of this window and returns it so it can be positioned

   * with the usual layout calls (setAnchor, setMargin, setOffset, setRelativeSize, centerWindow...).

   * The action runs whenever the button is left-clicked.

   */

  public Button addButton(String label, Runnable action) {

    Button button = new Button(Component.literal(label), action);

    addChild(button);

    return button;

  }



  /** Adds a child window (several children are allowed, e.g. a top and a bottom window). Returns the child. */
  public Window addChild(Window child) {
    if (child == null) {
      throw new IllegalArgumentException("Child window cannot be null");
    }
    if (child == this) {
      throw new IllegalArgumentException("A window cannot be its own child");
    }
    children.add(child);
    return child;
  }

  public boolean removeChild(Window child) {
    return children.remove(child);
  }

  public List<Window> getChildren() {
    return Collections.unmodifiableList(children);
  }

  /** First child, kept for the older single-chain API. */
  public Window getNextWindow() {
    return children.isEmpty() ? null : children.get(0);
  }

  /** Replaces all children with a single window (older single-chain API). */
  public void setNextWindow(Window nextWindow) {
    children.clear();
    if (nextWindow != null) {
      addChild(nextWindow);
    }
  }

  /**
   * Nests a window under the deepest window reached by following first children, and returns it.
   * Use addChild instead when you want several windows side by side inside the same parent.
   */
  public Window appendWindow(Window window) {
    if (window == null) {
      throw new IllegalArgumentException("Appended window cannot be null");
    }
    Window tail = this;
    int depth = 0;
    while (!tail.children.isEmpty() && depth < MAX_NESTING_DEPTH) {
      tail = tail.children.get(0);
      depth++;
    }
    if (depth >= MAX_NESTING_DEPTH) {
      throw new IllegalStateException("Window nesting depth exceeded safe limit");
    }
    tail.addChild(window);
    return window;
  }

  // ---------------------------------------------------------------- layout

  /** Default position for children without an anchor or own offset. */
  public void setChildOffset(int x, int y) {
    childOffsetX = x;
    childOffsetY = y;
  }

  /** Space kept free inside this window; children are laid out in what remains. */
  public void setPadding(int padding) {
    this.padding = Math.max(0, padding);
  }

  /** Space kept between this window and the edges of its parent's usable area. */
  public void setMargin(int margin) {
    this.margin = Math.max(0, margin);
  }

  /** Where this window sits inside its parent's usable area. */
  public void setAnchor(Anchor anchor) {
    if (this.anchor != null) {
      Anchor.unregisterWindow(this.anchor, this);
    }
    this.anchor = anchor;
    if (anchor != null) {
      Anchor.registerWindow(anchor, this);
    }
  }

  /** Extra shift applied after anchoring (or the position itself when there is no anchor). */
  public void setOffset(int x, int y) {
    hasOffset = true;
    offsetX = x;
    offsetY = y;
  }

  /**
   * Anchors this window to another child of the same parent (useful for daisy-chaining controls).
   * For example, own TOP to target BOTTOM stacks this window directly underneath the target.
   */
  public void anchorToWindow(Window target, Anchor targetPoint, Anchor ownPoint) {
    if (target == null) {
      throw new IllegalArgumentException("Target window cannot be null");
    }
    if (target == this) {
      throw new IllegalArgumentException("A window cannot anchor to itself");
    }
    relativeAnchorTarget = target;
    relativeAnchorTargetPoint = targetPoint == null ? Anchor.TOP_LEFT : targetPoint;
    relativeAnchorOwnPoint = ownPoint == null ? Anchor.TOP_LEFT : ownPoint;
  }

  /** Stops anchoring this window to another window and falls back to normal parent anchoring. */
  public void clearRelativeAnchor() {
    relativeAnchorTarget = null;
  }

  /**
   * Sizes this window as a fraction of its parent's usable area (the parent's size minus its padding
   * and this window's margin). For example 1.0 and 0.5 mean full width and half height. Re-resolved
   * every frame, so the layout looks the same in survival and creative inventories.
   * Use 0 for an axis to keep its fixed size.
   */
  public void setRelativeSize(double widthFraction, double heightFraction) {
    relativeWidth = widthFraction;
    relativeHeight = heightFraction;
  }

  /** Centers this window in the window it is a child of. Resolved every frame against the parent's size. */
  public void centerWindow() {
    centerX = true;
    centerY = true;
  }

  public void centerWindowX() {
    centerX = true;
  }

  public void centerWindowY() {
    centerY = true;
  }

  /** Same as centerWindow(); the parent argument is only validated. */
  public void centerWindow(Window parentWindow) {
    requireParent(parentWindow);
    centerWindow();
  }

  public void centerWindowX(Window parentWindow) {
    requireParent(parentWindow);
    centerWindowX();
  }

  public void centerWindowY(Window parentWindow) {
    requireParent(parentWindow);
    centerWindowY();
  }

  private static void requireParent(Window parentWindow) {
    if (parentWindow == null) {
      throw new IllegalArgumentException("Parent window cannot be null");
    }
  }

  private void applyRelativeSize(Window parent) {
    if (relativeWidth > 0) {
      int usable = parent.width - 2 * parent.padding - 2 * margin;
      width = Math.max(1, (int) Math.round(usable * relativeWidth));
    }
    if (relativeHeight > 0) {
      int usable = parent.height - 2 * parent.padding - 2 * margin;
      height = Math.max(1, (int) Math.round(usable * relativeHeight));
    }
  }

  /** Position of an axis: 0 = start, 1 = centre, 2 = end of the usable area. */
  private static int axisPosition(int mode, int parentSize, int parentPadding, int childSize, int childMargin) {
    return switch (mode) {
      case 0 -> parentPadding + childMargin;
      case 1 -> parentPadding + (parentSize - 2 * parentPadding - childSize) / 2;
      default -> parentSize - parentPadding - childMargin - childSize;
    };
  }

  private int resolveChildX(Window child) {
    return resolveChildX(child, new HashSet<>());
  }

  private int resolveChildX(Window child, Set<Window> visiting) {
    if (!visiting.add(child)) {
      return resolveChildXIgnoringRelative(child);
    }
    if (isRelativeAnchorUsable(child)) {
      int targetX = resolveChildX(child.relativeAnchorTarget, visiting);
      int targetPointX = targetX + anchorPointOffset(child.relativeAnchorTargetPoint.horizontal, child.relativeAnchorTarget.width);
      int ownPointX = anchorPointOffset(child.relativeAnchorOwnPoint.horizontal, child.width);
      return targetPointX - ownPointX + (child.hasOffset ? child.offsetX : 0);
    }
    int extra = child.hasOffset ? child.offsetX : 0;
    if (child.centerX) {
      return axisPosition(1, width, padding, child.width, child.margin) + extra;
    }
    if (child.anchor != null) {
      return axisPosition(child.anchor.horizontal, width, padding, child.width, child.margin) + extra;
    }
    return padding + child.margin + (child.hasOffset ? child.offsetX : childOffsetX);
  }

  private int resolveChildY(Window child) {
    return resolveChildY(child, new HashSet<>());
  }

  private int resolveChildY(Window child, Set<Window> visiting) {
    if (!visiting.add(child)) {
      return resolveChildYIgnoringRelative(child);
    }
    if (isRelativeAnchorUsable(child)) {
      int targetY = resolveChildY(child.relativeAnchorTarget, visiting);
      int targetPointY = targetY + anchorPointOffset(child.relativeAnchorTargetPoint.vertical, child.relativeAnchorTarget.height);
      int ownPointY = anchorPointOffset(child.relativeAnchorOwnPoint.vertical, child.height);
      return targetPointY - ownPointY + (child.hasOffset ? child.offsetY : 0);
    }
    int extra = child.hasOffset ? child.offsetY : 0;
    if (child.centerY) {
      return axisPosition(1, height, padding, child.height, child.margin) + extra;
    }
    if (child.anchor != null) {
      return axisPosition(child.anchor.vertical, height, padding, child.height, child.margin) + extra;
    }
    return padding + child.margin + (child.hasOffset ? child.offsetY : childOffsetY);
  }

  // ---------------------------------------------------------------- drawing

  public void drawWindow(GuiGraphicsExtractor graphics, Font font, int x, int y) {

    drawWindowRecursive(graphics, font, x, y, -1, -1, 0);

  }



  /** Draws the window tree; the mouse position lets children (such as buttons) show hover effects. */

  public void drawWindow(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {

    drawWindowRecursive(graphics, font, x, y, mouseX, mouseY, 0);

  }



  private void drawWindowRecursive(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY, int depth) {
    if (!visible) {
      return;
    }
    if (depth > MAX_NESTING_DEPTH) {
      return;
    }

    drawSelf(graphics, font, x, y, mouseX, mouseY);

    int[] clip = getChildClip();
    if (clip != null) {
      graphics.enableScissor(x + clip[0], y + clip[1], x + clip[2], y + clip[3]);
    }
    for (Window child : children) {
      child.applyRelativeSize(this);
      child.drawWindowRecursive(graphics, font, x + resolveChildX(child), y + resolveChildY(child), mouseX, mouseY, depth + 1);
    }
    if (clip != null) {
      graphics.disableScissor();
    }
  }

  /**
   * Optional clip rectangle {left, top, right, bottom}, relative to this window, that children are
   * drawn and hit-tested inside. Return null (the default) for no clipping.
   */
  protected int[] getChildClip() {
    return null;
  }

  private boolean isInsideChildClip(int x, int y, double mouseX, double mouseY) {
    int[] clip = getChildClip();
    return clip == null
        || (mouseX >= x + clip[0] && mouseX < x + clip[2] && mouseY >= y + clip[1] && mouseY < y + clip[3]);
  }

  /** Draws just this window. Subclasses (such as buttons) override this to change how they look. */
  protected void drawSelf(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
    graphics.fill(x, y, x + width, y + height, BORDER_COLOR);
    graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, backgroundColor);
    graphics.fill(x + 2, y + 2, x + width - 2, y + 3, HIGHLIGHT_COLOR);
    graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, SHADOW_COLOR);
    graphics.fill(x + width - 3, y + 2, x + width - 2, y + height - 2, SHADOW_COLOR);

    drawTitle(graphics, font, x, y);
  }

  private void drawTitle(GuiGraphicsExtractor graphics, Font font, int x, int y) {
    if (title.getString().isEmpty()) {
      return;
    }

    if (!autoFitTitle) {
      // Original behaviour: fixed top-left position, optional fixed scale.
      if (Math.abs(titleScale - 1.0F) < 0.0001F) {
        graphics.text(font, title, x + TITLE_INSET_X, y + TITLE_INSET_Y, titleColor, false);
      } else {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + TITLE_INSET_X, y + TITLE_INSET_Y);
        graphics.pose().scale(titleScale, titleScale);
        graphics.text(font, title, 0, 0, titleColor, false);
        graphics.pose().popMatrix();
      }
      return;
    }

    if (font == null) {
      return;
    }
    String rawTitle = title.getString();

    int availableWidth = Math.max(1, width - TITLE_INSET_X * 2);
    int availableHeight = Math.max(1, height - TITLE_INSET_Y * 2);

    List<String> lines = splitTitleLines(rawTitle);
    float fittedScale = fitTitleScale(font, lines, availableWidth, availableHeight);

    if (lines.size() == 1 && fittedScale < TITLE_WRAP_SCALE_THRESHOLD) {
      List<String> wrappedLines = wrapTitleIntoTwoLines(font, rawTitle, availableWidth);
      float wrappedScale = fitTitleScale(font, wrappedLines, availableWidth, availableHeight);
      if (wrappedLines.size() > 1 && wrappedScale > fittedScale) {
        lines = wrappedLines;
        fittedScale = wrappedScale;
      }
    }

    fittedScale = Math.min(fittedScale, titleScale);
    int lineHeight = Math.max(1, Math.round(font.lineHeight * fittedScale));
    int totalHeight = lineHeight * lines.size() + TITLE_LINE_GAP * Math.max(0, lines.size() - 1);
    int startY = y + TITLE_INSET_Y + Math.max(0, (availableHeight - totalHeight) / 2);

    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      int lineWidth = Math.round(font.width(line) * fittedScale);
      int lineX = x + TITLE_INSET_X + Math.max(0, (availableWidth - lineWidth) / 2);
      int lineY = startY + i * (lineHeight + TITLE_LINE_GAP);
      drawTitleLine(graphics, font, line, lineX, lineY, fittedScale);
    }
  }

  private void drawTitleLine(GuiGraphicsExtractor graphics, Font font, String line, int x, int y, float scale) {
    if (line.isEmpty()) {
      return;
    }
    if (Math.abs(scale - 1.0F) < 0.0001F) {
      graphics.text(font, Component.literal(line), x, y, titleColor, false);
      return;
    }
    graphics.pose().pushMatrix();
    graphics.pose().translate(x, y);
    graphics.pose().scale(scale, scale);
    graphics.text(font, Component.literal(line), 0, 0, titleColor, false);
    graphics.pose().popMatrix();
  }

  private float fitTitleScale(Font font, List<String> lines, int availableWidth, int availableHeight) {
    float scale = titleScale;
    int widestLine = 0;
    for (String line : lines) {
      widestLine = Math.max(widestLine, font.width(line));
    }
    if (widestLine > 0) {
      scale = Math.min(scale, (float) availableWidth / widestLine);
    }

    int lineCount = Math.max(1, lines.size());
    int totalLineHeight = font.lineHeight * lineCount + TITLE_LINE_GAP * Math.max(0, lineCount - 1);
    if (totalLineHeight > 0) {
      scale = Math.min(scale, (float) availableHeight / totalLineHeight);
    }

    return Math.max(0.25F, scale);
  }

  private static List<String> splitTitleLines(String text) {
    if (text.contains("\n")) {
      List<String> lines = new ArrayList<>();
      Collections.addAll(lines, text.split("\\n", -1));
      return lines;
    }
    List<String> lines = new ArrayList<>();
    lines.add(text);
    return lines;
  }

  private static List<String> wrapTitleIntoTwoLines(Font font, String text, int maxWidth) {
    List<String> words = splitWords(text);
    if (words.size() <= 1) {
      return wrapTextByCharacter(font, text, maxWidth);
    }

    List<String> best = null;
    int bestWidth = Integer.MAX_VALUE;
    for (int i = 1; i < words.size(); i++) {
      String first = joinWords(words, 0, i);
      String second = joinWords(words, i, words.size());
      int firstWidth = font.width(first);
      int secondWidth = font.width(second);
      int widest = Math.max(firstWidth, secondWidth);
      if (widest < bestWidth) {
        bestWidth = widest;
        best = List.of(first, second);
      }
      if (firstWidth <= maxWidth && secondWidth <= maxWidth) {
        return List.of(first, second);
      }
    }
    return best == null ? wrapTextByCharacter(font, text, maxWidth) : best;
  }

  private static List<String> splitWords(String text) {
    String trimmed = text == null ? "" : text.trim();
    if (trimmed.isEmpty()) {
      return List.of("");
    }
    String[] words = trimmed.split("\\s+");
    List<String> result = new ArrayList<>();
    Collections.addAll(result, words);
    return result;
  }

  private static String joinWords(List<String> words, int startInclusive, int endExclusive) {
    StringBuilder builder = new StringBuilder();
    for (int i = startInclusive; i < endExclusive; i++) {
      if (builder.length() > 0) {
        builder.append(' ');
      }
      builder.append(words.get(i));
    }
    return builder.toString();
  }

  private static List<String> wrapTextByCharacter(Font font, String text, int maxWidth) {
    List<String> lines = new ArrayList<>();
    if (text == null || text.isEmpty()) {
      lines.add("");
      return lines;
    }

    int splitPoint = text.length() / 2;
    int bestSplit = Math.max(1, splitPoint);
    int bestWidth = Integer.MAX_VALUE;
    for (int i = 1; i < text.length(); i++) {
      String first = text.substring(0, i).trim();
      String second = text.substring(i).trim();
      if (first.isEmpty() || second.isEmpty()) {
        continue;
      }
      int widest = Math.max(font.width(first), font.width(second));
      if (widest < bestWidth) {
        bestWidth = widest;
        bestSplit = i;
      }
      if (font.width(first) <= maxWidth && font.width(second) <= maxWidth) {
        bestSplit = i;
        break;
      }
    }

    lines.add(text.substring(0, bestSplit).trim());
    lines.add(text.substring(bestSplit).trim());
    return lines;
  }

  // ---------------------------------------------------------------- hit testing and clicks

  /** True if the point is inside this window or any of its descendants. */
  public boolean containsPoint(int x, int y, double mouseX, double mouseY) {
    return findWindowAt(x, y, mouseX, mouseY) != null;
  }

  public void setClickListener(ClickListener listener) {
    this.clickListener = listener;
  }

  /** Called when a mouse button is pressed on this window. Return true to capture drag events. */
  public boolean beginMouseDrag(int button) {
    return false;
  }

  /** Called while a captured drag is in progress. localX/localY are relative to this window. */
  public void onMouseDrag(double localX, double localY, double deltaX, double deltaY, int button) {
  }

  /** Called when a captured drag ends. */
  public void endMouseDrag(int button) {
  }

  /** Called when the mouse wheel moves over this window. Return true if the scroll was used. */
  public boolean onMouseScroll(double localX, double localY, double horizontalAmount, double verticalAmount) {
    return false;
  }

  /** Sends a scroll to the topmost window under the cursor that uses it, then to its ancestors. */
  public boolean dispatchScroll(int x, int y, double mouseX, double mouseY,
                                double horizontalAmount, double verticalAmount) {
    return dispatchScrollRecursive(x, y, mouseX, mouseY, horizontalAmount, verticalAmount, 0);
  }

  /** Screen position of the target window (this window or a descendant), or null if it is not in this tree. */
  public WindowHit locate(int rootX, int rootY, Window target) {
    return locateRecursive(rootX, rootY, target, 0);
  }

  /**
   * Finds the topmost window containing the point (children draw above their parent, and later
   * children above earlier ones), or null if the point is outside every window.
   * x and y are this window's screen position.
   */
  public WindowHit findWindowAt(int x, int y, double mouseX, double mouseY) {
    return findWindowAtRecursive(x, y, mouseX, mouseY, 0);
  }

  /**
   * Dispatches a click to the topmost window under the cursor.
   * Returns the window that was hit, or null if the click was outside all windows.
   */
  public Window dispatchClick(int x, int y, double mouseX, double mouseY, int button) {
    WindowHit hit = findWindowAt(x, y, mouseX, mouseY);
    if (hit == null) {
      return null;
    }
    if (hit.window().clickListener != null) {
      hit.window().clickListener.onClick(hit.window(), mouseX - hit.x(), mouseY - hit.y(), button);
    }
    return hit.window();
  }

  /**
   * True if the point is inside the given window (which must be this window or one of its descendants),
   * whether or not another window is drawn on top of it. rootX and rootY are this window's screen position.
   */
  public boolean isPointInside(int rootX, int rootY, Window target, double mouseX, double mouseY) {
    WindowHit location = locateRecursive(rootX, rootY, target, 0);
    if (location == null) {
      return false;
    }
    return mouseX >= location.x() && mouseX < location.x() + target.width
        && mouseY >= location.y() && mouseY < location.y() + target.height;
  }

  private WindowHit locateRecursive(int x, int y, Window target, int depth) {
    if (!visible) {
      return null;
    }
    if (depth > MAX_NESTING_DEPTH) {
      return null;
    }
    if (this == target) {
      return new WindowHit(this, x, y);
    }
    for (Window child : children) {
      child.applyRelativeSize(this);
      WindowHit found = child.locateRecursive(x + resolveChildX(child), y + resolveChildY(child), target, depth + 1);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private WindowHit findWindowAtRecursive(int x, int y, double mouseX, double mouseY, int depth) {
    if (!visible) {
      return null;
    }
    if (depth > MAX_NESTING_DEPTH) {
      return null;
    }
    WindowHit result = null;
    if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
      result = new WindowHit(this, x, y);
    }
    boolean childrenReachable = isInsideChildClip(x, y, mouseX, mouseY);
    for (Window child : children) {
      if (!childrenReachable) {
        break;
      }
      child.applyRelativeSize(this);
      WindowHit childHit = child.findWindowAtRecursive(
          x + resolveChildX(child), y + resolveChildY(child), mouseX, mouseY, depth + 1);
      if (childHit != null) {
        result = childHit;
      }
    }
    return result;
  }

  private boolean dispatchScrollRecursive(int x, int y, double mouseX, double mouseY,
                                          double horizontalAmount, double verticalAmount, int depth) {
    if (!visible || depth > MAX_NESTING_DEPTH) {
      return false;
    }
    // Children may sit outside their parent's bounds (e.g. the side pane), so always check them first.
    // Children draw above parents, and later children draw above earlier ones.
    boolean childrenReachable = isInsideChildClip(x, y, mouseX, mouseY);
    for (int i = children.size() - 1; i >= 0 && childrenReachable; i--) {
      Window child = children.get(i);
      child.applyRelativeSize(this);
      int childX = x + resolveChildX(child);
      int childY = y + resolveChildY(child);
      if (child.dispatchScrollRecursive(childX, childY, mouseX, mouseY, horizontalAmount, verticalAmount, depth + 1)) {
        return true;
      }
    }

    if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
      return false;
    }
    return onMouseScroll(mouseX - x, mouseY - y, horizontalAmount, verticalAmount);
  }

  private boolean isRelativeAnchorUsable(Window child) {
    return child.relativeAnchorTarget != null && children.contains(child.relativeAnchorTarget);
  }

  private int resolveChildXIgnoringRelative(Window child) {
    int extra = child.hasOffset ? child.offsetX : 0;
    if (child.centerX) {
      return axisPosition(1, width, padding, child.width, child.margin) + extra;
    }
    if (child.anchor != null) {
      return axisPosition(child.anchor.horizontal, width, padding, child.width, child.margin) + extra;
    }
    return padding + child.margin + (child.hasOffset ? child.offsetX : childOffsetX);
  }

  private int resolveChildYIgnoringRelative(Window child) {
    int extra = child.hasOffset ? child.offsetY : 0;
    if (child.centerY) {
      return axisPosition(1, height, padding, child.height, child.margin) + extra;
    }
    if (child.anchor != null) {
      return axisPosition(child.anchor.vertical, height, padding, child.height, child.margin) + extra;
    }
    return padding + child.margin + (child.hasOffset ? child.offsetY : childOffsetY);
  }

  private static int anchorPointOffset(int mode, int size) {
    return switch (mode) {
      case 0 -> 0;
      case 1 -> size / 2;
      default -> size;
    };
  }
}


