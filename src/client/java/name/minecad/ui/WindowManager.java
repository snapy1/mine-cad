package name.minecad.ui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import name.minecad.client.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
public final class WindowManager {
    private enum InventoryTabView {
        VANILLA,
        MINE_CAD
    }

    private static final int TAB_WIDTH = 26;
    private static final int TAB_HEIGHT = 32;

    private int additionalTabsOffsetX = 200;
    private int additionalTabsOffsetY = 0;
    private InventoryTabView activeInventoryTab = InventoryTabView.VANILLA;
    private final Tab mineCadTab;
    private Screen currentScreen;
    // Last known window origin and mouse position (updated every frame while a screen is shown).
    private int lastScreenLeft;
    private int lastScreenTop;
    private double lastMouseX;
    private double lastMouseY;
    private Window activeDragWindow;
    private int activeDragRootX;
    private int activeDragRootY;
    private int activeDragButton = -1;
    private WindowTextField focusedTextField;
    private WindowKeybindField focusedKeybindField;

    public WindowManager(Tab mineCadTab) {
        this.mineCadTab = mineCadTab;
    }

    public void setAdditionalTabsOffset(int xOffset, int yOffset) {
        additionalTabsOffsetX = xOffset;
        additionalTabsOffsetY = yOffset;
    }

    public void registerForScreen(Minecraft client, Screen screen, int width, int height) {
        boolean inventoryScreen = screen instanceof InventoryScreen;
        boolean creativeInventoryScreen = screen instanceof CreativeModeInventoryScreen;

        if (!inventoryScreen && !creativeInventoryScreen) {
            return;
        }

         currentScreen = screen;
         syncOverlayState();
         ScreenEvents.remove(screen).register(OverlayState::clear);
         // If the screen closes mid-drag, end the drag so the cursor is shown again.
         ScreenEvents.remove(screen).register(closedScreen -> cancelActiveDrag());

         int backgroundWidth = creativeInventoryScreen ? 195 : 176;
         int backgroundHeight = creativeInventoryScreen ? 136 : 166;
         final Tab[] tabs = new Tab[2];

         // MineCad tab stays inside the same inventory screen.
         tabs[1] = mineCadTab;
         tabs[1].setIcon(new ItemStack(Items.BOOK));
         // Initial position will be updated in the render event for proper handling of screen size changes
         tabs[1].setX(0);
         tabs[1].setY(0);
         tabs[1].setSelected(activeInventoryTab == InventoryTabView.MINE_CAD);
         tabs[1].setAction(() -> {
             // Toggle the tab - if it's already selected, deselect it (switch to VANILLA)
             if (activeInventoryTab == InventoryTabView.MINE_CAD && tabs[1].isSelectedTab()) {
                 setActiveInventoryTab(InventoryTabView.VANILLA, tabs[0], tabs[1]);
             } else {
                 setActiveInventoryTab(InventoryTabView.MINE_CAD, tabs[0], tabs[1]);
             }
             // Bind overlay state to the screen this tab actually lives on (not a possibly stale shared field).
             currentScreen = screen;
             syncOverlayState();
         });
         // Re-sync before vanilla draws its tabs each frame so they always reflect the active view.
         ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, tickProgress) -> {
             currentScreen = s;
             syncOverlayState();
         });
         Screens.getWidgets(screen).add(tabs[1]);

      // Prefer the window attached to this tab so callers can provide custom windows per tab.
      final Window configuredWindow = tabs[1].getWindow();
      final Window window = configuredWindow != null ? configuredWindow : new Window(backgroundWidth, backgroundHeight);
      {
        // While a window is being dragged (middle mouse), swallow key presses (E, Q, number keys...)
        // so they don't close the inventory or drop items. Escape still works.
        ScreenKeyboardEvents.allowKeyPress(screen).register((currentScreen, keyEvent) -> {
            if (activeDragWindow != null && keyEvent.key() != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                return false;
            }
            if (activeInventoryTab == InventoryTabView.MINE_CAD
                && tabs[1] != null
                && tabs[1].isSelectedTab()
                && focusedKeybindField != null
                && focusedKeybindField.handleKeyPress(keyEvent.key())) {
                if (!focusedKeybindField.isAwaitingInput()) {
                    setFocusedKeybindField(null);
                }
                return false;
            }
            if (activeInventoryTab == InventoryTabView.MINE_CAD
                && tabs[1] != null
                && tabs[1].isSelectedTab()
                && focusedTextField != null
                && focusedTextField.handleKeyPress(keyEvent.key())) {
                return false;
            }
            return true;
        });
        ScreenKeyboardEvents.allowKeyRelease(screen).register((currentScreen, keyEvent) ->
            activeDragWindow == null);
        ScreenMouseEvents.allowMouseClick(screen).register((currentScreen, event) -> {
            if (activeInventoryTab == InventoryTabView.MINE_CAD
                && tabs[1] != null
                && tabs[1].isSelectedTab()
                && focusedKeybindField != null
                && focusedKeybindField.captureMouseButton(event.button())) {
                setFocusedKeybindField(null);
                return false;
            }

            int[] b = bounds(currentScreen, backgroundWidth, backgroundHeight);

            // Check if the click is inside the MineCad tab window area and if the MineCad tab is selected.
            boolean blocked = isWindowInteractionBlocked(tabs[1], window, event.x(), event.y(), b[0], b[1]);
            if (blocked) {
                Window.WindowHit hit = window.findWindowAt(b[0], b[1], event.x(), event.y());
                if (hit != null && hit.window().beginMouseDrag(event.button())) {
                    activeDragWindow = hit.window();
                    activeDragRootX = b[0];
                    activeDragRootY = b[1];
                    activeDragButton = event.button();
                }
                // Tell the window chain about the click (topmost window under the cursor gets it).
                Window clicked = window.dispatchClick(b[0], b[1], event.x(), event.y(), event.button());
                if (event.button() == 0) {
                    setFocusedTextField(clicked instanceof WindowTextField textField ? textField : null);
                    setFocusedKeybindField(clicked instanceof WindowKeybindField keybindField ? keybindField : null);
                }
            } else if (event.button() == 0) {
                setFocusedTextField(null);
                setFocusedKeybindField(null);
            }
            return !blocked;
        });
        ScreenMouseEvents.allowMouseRelease(screen).register((currentScreen, event) -> {
            int[] b = bounds(currentScreen, backgroundWidth, backgroundHeight);
            if (activeDragWindow != null && event.button() == activeDragButton) {
                activeDragWindow.endMouseDrag(event.button());
                activeDragWindow = null;
                activeDragButton = -1;
            }
            return !isWindowInteractionBlocked(tabs[1], window, event.x(), event.y(), b[0], b[1]);
        });
        ScreenMouseEvents.allowMouseDrag(screen).register((currentScreen, event, horizontalAmount, verticalAmount) -> {
            int[] b = bounds(currentScreen, backgroundWidth, backgroundHeight);
            if (activeDragWindow != null) {
                Window.WindowHit location = window.locate(b[0], b[1], activeDragWindow);
                double originX = location != null ? location.x() : b[0];
                double originY = location != null ? location.y() : b[1];
                double localX = event.x() - originX;
                double localY = event.y() - originY;
                activeDragWindow.onMouseDrag(localX, localY, horizontalAmount, verticalAmount, activeDragButton);
                return false;
            }
            return !isWindowInteractionBlocked(tabs[1], window, event.x(), event.y(), b[0], b[1]);
        });
        ScreenMouseEvents.allowMouseScroll(screen).register((currentScreen, mouseX, mouseY, horizontalAmount, verticalAmount) -> {
            if (activeInventoryTab == InventoryTabView.MINE_CAD
                && tabs[1] != null
                && tabs[1].isSelectedTab()
                && focusedKeybindField != null
                && focusedKeybindField.captureMouseScroll()) {
                setFocusedKeybindField(null);
                return false;
            }

            int[] b = bounds(currentScreen, backgroundWidth, backgroundHeight);
            boolean blocked = isWindowInteractionBlocked(tabs[1], window, mouseX, mouseY, b[0], b[1]);
            if (blocked) {
                // The topmost window under the cursor may use the wheel (e.g. 3D zoom).
                window.dispatchScroll(b[0], b[1], mouseX, mouseY, horizontalAmount, verticalAmount);
            }
            return !blocked;
        });
        ScreenEvents.afterExtract(screen).register((currentScreen, graphics, mouseX, mouseY, tickProgress) -> {
          int[] b = bounds(currentScreen, backgroundWidth, backgroundHeight);
          int x = b[0];
          int y = b[1];
          // Remember where the window is and where the mouse is, for isMouseInsideWindow().
          lastScreenLeft = x;
          lastScreenTop = y;
          lastMouseX = mouseX;
          lastMouseY = mouseY;
          // Update tab position based on the vanilla screen's actual position
          tabs[1].setX(x + additionalTabsOffsetX);
          tabs[1].setY((y - TAB_HEIGHT) + additionalTabsOffsetY);

          if (activeInventoryTab == InventoryTabView.MINE_CAD && tabs[1].isSelectedTab()) {
            // Root window always fills the vanilla inventory area; nested windows keep their own sizes.
            window.setSize(b[2], b[3]);
            window.drawWindow(graphics, currentScreen.getFont(), x, y, (int) mouseX, (int) mouseY);

            // Vanilla may have requested a scrollbar/text cursor underneath; the last request wins.
            if (window.containsPoint(x, y, mouseX, mouseY)) {
              graphics.requestCursor(CursorTypes.ARROW);
            }
          }
        });
      }

          if (creativeInventoryScreen) {
              ScreenMouseEvents.afterMouseClick(screen).register((clickedScreen, event, consumed) -> {
                  if (tabs[1] == null) {
                      return consumed;
                  }

                  int[] b = bounds(clickedScreen, backgroundWidth, backgroundHeight);
                  int creativeBackgroundWidth = b[2];
                  int x = b[0];
                  int y = b[1];
                  double mouseX = event.x();
                  double mouseY = event.y();
                  boolean clickedMineCadTab = isInside(mouseX, mouseY, tabs[1].getX(), tabs[1].getY(), TAB_WIDTH, TAB_HEIGHT);
                  if (clickedMineCadTab) {
                      return consumed;
                  }

                  boolean clickedTopVanillaTabs = isInside(mouseX, mouseY, x, y - 30, creativeBackgroundWidth, 30);
                  boolean clickedBottomVanillaTabs = isInside(mouseX, mouseY, x, y + b[3], creativeBackgroundWidth, 30);
                  if (clickedTopVanillaTabs || clickedBottomVanillaTabs) {
                      setActiveInventoryTab(InventoryTabView.VANILLA, tabs[0], tabs[1]);
                  }

                  return consumed;
              });
          }
    }

    private void setActiveInventoryTab(InventoryTabView view, Tab tab, Tab mineCadTab) {
        if (view != InventoryTabView.MINE_CAD) {
            cancelActiveDrag();
            setFocusedTextField(null);
            setFocusedKeybindField(null);
        }
        activeInventoryTab = view;
        syncOverlayState();
        if (tab != null) {
            tab.setSelected(view == InventoryTabView.VANILLA);
        }
        if (mineCadTab != null) {
            mineCadTab.setSelected(view == InventoryTabView.MINE_CAD);
        }
    }

    /** Ends any drag in progress (restores things like a hidden cursor). */
    private void cancelActiveDrag() {
        if (activeDragWindow != null) {
            activeDragWindow.endMouseDrag(activeDragButton);
            activeDragWindow = null;
            activeDragButton = -1;
        }
    }

    private void setFocusedTextField(WindowTextField textField) {
        if (focusedTextField == textField) {
            return;
        }
        if (focusedTextField != null) {
            focusedTextField.setFocused(false);
        }
        focusedTextField = textField;
        if (focusedTextField != null) {
            focusedTextField.setFocused(true);
        }
    }

    private void setFocusedKeybindField(WindowKeybindField keybindField) {
        if (focusedKeybindField == keybindField) {
            return;
        }
        if (focusedKeybindField != null) {
            focusedKeybindField.setFocused(false);
        }
        focusedKeybindField = keybindField;
        if (focusedKeybindField != null) {
            focusedKeybindField.setFocused(true);
        }
    }

    /** Returns {leftPos, topPos, imageWidth, imageHeight} read from the vanilla screen (falls back to defaults). */
    private static int[] bounds(Screen screen, int defaultWidth, int defaultHeight) {
        if (screen instanceof AbstractContainerScreenAccessor accessor) {
            return new int[] {
                    accessor.minecad$getLeftPos(),
                    accessor.minecad$getTopPos(),
                    accessor.minecad$getImageWidth(),
                    accessor.minecad$getImageHeight()
            };
        }
        return new int[] {(screen.width - defaultWidth) / 2, (screen.height - defaultHeight) / 2, defaultWidth, defaultHeight};
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void syncOverlayState() {
        OverlayState.setBlockingScreen(
                activeInventoryTab == InventoryTabView.MINE_CAD ? currentScreen : null
        );
    }

  private boolean isWindowInteractionBlocked(Tab tab, Window window, double mouseX, double mouseY, int x, int y) {
    return activeInventoryTab == InventoryTabView.MINE_CAD
        && tab != null
        && tab.isSelectedTab()
        && window != null
        && window.containsPoint(x, y, mouseX, mouseY);
  }

  /** True while the MineCad tab is open and the mouse is inside the given window (the root window or any nested one). */
  public boolean isMouseInsideWindow(Window window) {
    Window root = mineCadTab.getWindow();
    return window != null
        && root != null
        && activeInventoryTab == InventoryTabView.MINE_CAD
        && mineCadTab.isSelectedTab()
        && root.isPointInside(lastScreenLeft, lastScreenTop, window, lastMouseX, lastMouseY);
  }
}
