package name.minecad.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import name.minecad.client.input.BindingInput;
import name.minecad.client.settings.MineCadSettings;
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;

/**
 * An orbiting 3D view of the scanned blocks that lives inside an InventoryTabWindow.
 * The scene itself is drawn on the GPU (see {@link ScenePipRenderer}); this window only handles
 * input and tells the renderer where and how to draw.
 *
 * Controls:
 *  - middle-mouse drag rotates the view (cursor hidden and locked while dragging); rotation uses
 *    mouse acceleration, so faster movement rotates more per pixel,
 *  - while the middle button is held: W/S forward/back (zoom), A/D strafe, E/Q up/down,
 *  - the mouse wheel zooms.
 */
public class Window3DRenderer extends Window {
  private static final double ROTATE_SENSITIVITY = 0.35;
  private static final double ACCELERATION = 0.15; // extra rotation multiplier per pixel of mouse movement
  private static final double MAX_ACCELERATION = 6.0;
  private static final double PAN_SENSITIVITY = 0.01;
  private static final double MOVE_SPEED = 3.0; // preview units per second
  private static final double CAMERA_DISTANCE = 4.5;
  private static final double SCENE_SCALE = 0.70;
  private static final double DEFAULT_RENDER_SCALE_PERCENT = 0.42;
  private static final double MIN_RENDER_SCALE_PERCENT = 0.10;
  private static final double MAX_RENDER_SCALE_PERCENT = 2.00;
  private static final double ZOOM_STEP = 1.10; // scale change per wheel notch
  private static final long VIEW_SETTLE_NANOS = 150_000_000L; // still for this long -> full quality

  private final ScannedBlockScene scene = new ScannedBlockScene();
  private double yawDegrees = 0.0; // looking north from the south side
  private double pitchDegrees = -25.0;
  private boolean dragging;
  private DragMode dragMode = DragMode.NONE;
  private double savedCursorX;
  private double savedCursorY;
  private double renderScalePercent = DEFAULT_RENDER_SCALE_PERCENT;
  // View-space offset of the scene, changed with WASD (and Q/E) while the middle button is held.
  private double offsetX;
  private double offsetY;
  private double offsetZ;
  private long lastMoveNanos;
  private boolean resetBindingPreviouslyDown;
  // Used to tell whether the view is currently moving (see renderScene).
  private double lastYaw = Double.NaN;
  private double lastPitch = Double.NaN;
  private double lastOffsetX = Double.NaN;
  private double lastOffsetY = Double.NaN;
  private float lastScale = Float.NaN;
  private long lastViewChangeNanos = Long.MIN_VALUE / 2;
  private long lastRenderNanos = Long.MIN_VALUE / 2;
  private static final long REDRAW_GAP_NANOS = 100_000_000L;

  private float defaultRotation;
  private float defaultXPosition;
  private float defaultYPosition;
  private float defaultZPosition;

  public Window3DRenderer(int width, int height) {
    super(width, height);
    setTitle(Component.literal("3D"));
    setBackgroundColor(0xFFECECEC);

    // set the default rotation and position values
    defaultRotation = 0.0f;
    defaultXPosition = 0.0f;
    defaultYPosition = 0.0f;
    defaultZPosition = 0.0f;
  }

  // ---------------------------------------------------------------- settings

  /**
   * Adjusts how large the scene is rendered relative to the available space.
   * 1.0 uses the full base size, 0.5 renders it at half size, and so on.
   */
  public void setRenderScalePercent(double renderScalePercent) {
    if (renderScalePercent <= 0.0) {
      throw new IllegalArgumentException("Render scale percent must be positive");
    }
    this.renderScalePercent = renderScalePercent;
  }

  public void resetCameraPosition() {
    yawDegrees = defaultRotation;
    offsetX = defaultXPosition;
    offsetY = defaultYPosition;
    offsetZ = defaultZPosition;
  }

  // ---------------------------------------------------------------- interaction

  @Override
  public boolean beginMouseDrag(int button) {
    int rotateButton = BindingInput.mouseButtonForBinding(
        MineCadSettings.getSettingValue("keybinds.3d_viewport_camera_rotation"),
        GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
    int panButton = BindingInput.mouseButtonForBinding(
        MineCadSettings.getSettingValue("keybinds.3d_viewport_camera_pan"),
        GLFW.GLFW_MOUSE_BUTTON_RIGHT);

    if (button == rotateButton) {
      dragMode = DragMode.ROTATE;
    } else if (button == panButton) {
      dragMode = DragMode.PAN;
    } else {
      return false;
    }

    dragging = true;
    lastMoveNanos = System.nanoTime();
    long handle = Minecraft.getInstance().getWindow().handle();
    double[] cursorX = new double[1];
    double[] cursorY = new double[1];
    GLFW.glfwGetCursorPos(handle, cursorX, cursorY);
    savedCursorX = cursorX[0];
    savedCursorY = cursorY[0];
    // Hide and lock the cursor: movement keeps being reported, so the rotation never hits a screen edge.
    GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
    return true;
  }

  @Override
  public void onMouseDrag(double localX, double localY, double deltaX, double deltaY, int button) {
    if (!dragging) {
      return;
    }

    if (dragMode == DragMode.ROTATE) {
      // Mouse acceleration: the faster the mouse moves, the more each pixel rotates.
      double speed = Math.hypot(deltaX, deltaY);
      double sensitivity = ROTATE_SENSITIVITY * MineCadSettings.getMouseSensitivity();
      double factor = 1.0;
      if (MineCadSettings.isMouseAccelerationEnabled()) {
        factor = Math.min(
            MAX_ACCELERATION,
            1.0 + (ACCELERATION * MineCadSettings.getMouseAccelerationFactor() * speed));
      }
      yawDegrees += deltaX * sensitivity * factor;
      pitchDegrees -= deltaY * sensitivity * factor;
      pitchDegrees = Math.max(-89.0, Math.min(89.0, pitchDegrees));
      return;
    }

    if (dragMode == DragMode.PAN) {
      double panStep = PAN_SENSITIVITY * MineCadSettings.getCameraSpeed();
      offsetX -= deltaX * panStep;
      offsetY += deltaY * panStep;
      offsetX = Math.max(-20.0, Math.min(20.0, offsetX));
      offsetY = Math.max(-20.0, Math.min(20.0, offsetY));
    }
  }

  @Override
  public void endMouseDrag(int button) {
    stopDragging();
  }

  /** Shows the cursor again and puts it back where the drag started. */
  private void stopDragging() {
    if (!dragging) {
      return;
    }
    dragging = false;
    dragMode = DragMode.NONE;
    long handle = Minecraft.getInstance().getWindow().handle();
    GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    GLFW.glfwSetCursorPos(handle, savedCursorX, savedCursorY);
  }

  /** W/S move forward/back, A/D strafe, E/Q move up/down (polled directly from GLFW). */
  private void applyKeyboardMovement(long handle) {
    long now = System.nanoTime();
    double dt = Math.min(0.1, (now - lastMoveNanos) / 1.0e9);
    lastMoveNanos = now;
    double step = MOVE_SPEED * MineCadSettings.getCameraSpeed() * dt;
    if (MineCadSettings.isCameraAccelerationEnabled()) {
      step *= MineCadSettings.getCameraAccelerationFactor();
    }

    if (BindingInput.isBindingActive(MineCadSettings.getSettingValue("keybinds.3d_viewport_camera_speed_reset"), handle)) {
      step = MOVE_SPEED * dt;
    }
    if (BindingInput.isBindingActive(MineCadSettings.getSettingValue("keybinds.3d_viewport_camera_speed_increase"), handle)) {
      step *= 2.0;
    }
    if (BindingInput.isBindingActive(MineCadSettings.getSettingValue("keybinds.3d_viewport_camera_speed_decrease"), handle)) {
      step *= 0.5;
    }

    // Objects move opposite to the camera in view space.
    if (BindingInput.isBindingActive(MineCadSettings.getSettingValue("keybinds.3d_viewport_camera_in"), handle)) {
      offsetZ -= step;
    }
    if (BindingInput.isBindingActive(MineCadSettings.getSettingValue("keybinds.3d_viewport_camera_out"), handle)) {
      offsetZ += step;
    }
    if (BindingInput.isBindingActive(MineCadSettings.getSettingValue("keybinds.3d_viewport_camera_left"), handle)) {
      offsetX += step;
    }
    if (BindingInput.isBindingActive(MineCadSettings.getSettingValue("keybinds.3d_viewport_camera_right"), handle)) {
      offsetX -= step;
    }
    if (BindingInput.isBindingActive(MineCadSettings.getSettingValue("keybinds.3d_viewport_camera_up"), handle)) {
      offsetY -= step;
    }
    if (BindingInput.isBindingActive(MineCadSettings.getSettingValue("keybinds.3d_viewport_camera_down"), handle)) {
      offsetY += step;
    }
    offsetZ = Math.max(-CAMERA_DISTANCE + 2.0, Math.min(30.0, offsetZ));
    offsetX = Math.max(-20.0, Math.min(20.0, offsetX));
    offsetY = Math.max(-20.0, Math.min(20.0, offsetY));
  }

  /** Mouse wheel zooms the scene in and out by changing the render scale. */
  @Override
  public boolean onMouseScroll(double localX, double localY, double horizontalAmount, double verticalAmount) {
    if (verticalAmount == 0) {
      return false;
    }
    long handle = Minecraft.getInstance().getWindow().handle();
    if (!BindingInput.isScrollBindingActive(MineCadSettings.getSettingValue("keybinds.3d_viewport_camera_zoom"), handle)) {
      return false;
    }

    double zoomInput = verticalAmount * MineCadSettings.getMouseSensitivity();
    double scaled = renderScalePercent * Math.pow(ZOOM_STEP, zoomInput);
    renderScalePercent = Math.max(MIN_RENDER_SCALE_PERCENT, Math.min(MAX_RENDER_SCALE_PERCENT, scaled));
    return true;
  }

  // ---------------------------------------------------------------- drawing

  @Override
  protected void drawSelf(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
    setBackgroundColor(MineCadSettings.getViewportBackgroundColor());
    super.drawSelf(graphics, font, x, y, mouseX, mouseY);
    checkResetShortcut();
    if (dragging) {
      long handle = Minecraft.getInstance().getWindow().handle();
      int expectedButton = dragMode == DragMode.PAN
          ? BindingInput.mouseButtonForBinding(MineCadSettings.getSettingValue("keybinds.3d_viewport_camera_pan"), GLFW.GLFW_MOUSE_BUTTON_RIGHT)
          : BindingInput.mouseButtonForBinding(MineCadSettings.getSettingValue("keybinds.3d_viewport_camera_rotation"), GLFW.GLFW_MOUSE_BUTTON_MIDDLE);

      // Safety net: if release was missed, give the cursor back.
      if (GLFW.glfwGetMouseButton(handle, expectedButton) != GLFW.GLFW_PRESS) {
        stopDragging();
      } else {
        applyKeyboardMovement(handle);
      }
    }
    renderScene(graphics, x, y);

    // disable this for now.
//    drawStatusText(graphics, font, x, y);
  }

  private void drawStatusText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
    if (scene.isEmpty()) {
      graphics.text(font, Component.literal("No blocks selected"), x + 8, y + getHeight() - 12, 0xFF404040, false);
    } else if (scene.isCapped()) {
      graphics.text(font, Component.literal("Render limit: " + scene.previewBlockCount() + " of "
          + scene.visibleBlockCount() + " visible blocks"), x + 8, y + getHeight() - 12, 0xFF404040, false);
    } else if (scene.isSimplified()) {
      graphics.text(font, Component.literal("Surface preview: " + scene.previewBlockCount() + " of "
          + scene.selectedBlockCount() + " blocks"), x + 8, y + getHeight() - 12, 0xFF404040, false);
    }
  }

  /** Queues the GPU render of the scene; the game draws it right after the GUI is assembled. */
  private void renderScene(GuiGraphicsExtractor graphics, int x, int y) {
    int left = x + 4;
    int top = y + 4; // the 3D view fills the whole window; the title draws on top of it
    int right = x + getWidth() - 4;
    int bottom = y + getHeight() - 4;
    int areaWidth = right - left;
    int areaHeight = bottom - top;
    if (areaWidth <= 0 || areaHeight <= 0) {
      return;
    }

    // Moving "forward" brings the scene closer, which in an orthographic view is the same as zooming.
    double zoom = CAMERA_DISTANCE / (CAMERA_DISTANCE + offsetZ);
    float scale = (float) (Math.min(areaWidth, areaHeight) * SCENE_SCALE * renderScalePercent * zoom);

    // While the view is changing every frame is a full re-render, so draw the cheaper coarse preview
    // of the scene; once the view has been still for a moment the full scene is rendered one more time.
    long now = System.nanoTime();
    // The view was not drawn for a while (another tab was open): do not trust the cached picture.
    if (now - lastRenderNanos > REDRAW_GAP_NANOS) {
      scene.invalidate();
    }
    lastRenderNanos = now;
    if (yawDegrees != lastYaw || pitchDegrees != lastPitch || offsetX != lastOffsetX
        || offsetY != lastOffsetY || scale != lastScale) {
      lastYaw = yawDegrees;
      lastPitch = pitchDegrees;
      lastOffsetX = offsetX;
      lastOffsetY = offsetY;
      lastScale = scale;
      lastViewChangeNanos = now;
    }
    // Holding the drag button (rotate/pan) keeps the low detail even when the mouse is momentarily still.
    if (dragging) {
      lastViewChangeNanos = now;
    }
    boolean viewBusy = now - lastViewChangeNanos < VIEW_SETTLE_NANOS;

    // Rebuilding a big scene takes long, so block-state-only changes wait until the view is still.
    scene.update(viewBusy);
    if (scene.isEmpty()) {
      return;
    }
    int blockBudget = viewBusy ? scene.lodBlockCount() : 0;

    ScreenRectangle scissor = graphics.scissorStack.peek();
    graphics.guiRenderState.addPicturesInPictureState(new ScenePipRenderState(
        scene, (float) yawDegrees, (float) pitchDegrees, (float) offsetX, (float) offsetY,
        left, top, right, bottom, scale, new Matrix3x2f(graphics.pose()), scissor, blockBudget));
  }

  private void checkResetShortcut() {
    long handle = Minecraft.getInstance().getWindow().handle();
    boolean down = BindingInput.isBindingActive(MineCadSettings.getSettingValue("keybinds.3d_viewport_camera_reset"), handle);
    if (down && !resetBindingPreviouslyDown) {
      resetCameraPosition();
    }
    resetBindingPreviouslyDown = down;
  }

  private enum DragMode {
    NONE,
    ROTATE,
    PAN
  }
}

