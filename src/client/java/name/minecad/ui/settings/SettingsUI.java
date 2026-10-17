package name.minecad.ui.settings;

import name.minecad.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public class SettingsUI extends ScrollableControlPane {
    private final String[] settingKeys = {
            "outline_settings.enabled",
            "outline_settings.color",
            "outline_settings.thickness",
            "3d_viewport_settings.background_color",
            "3d_viewport_settings.mouse_sensitivity",
            "3d_viewport_settings.mouse_acceleration_enabled",
            "3d_viewport_settings.mouse_acceleration_factor",
            "3d_viewport_settings.camera_speed",
            "3d_viewport_settings.camera_acceleration_enabled",
            "3d_viewport_settings.camera_acceleration_factor",
            "3d_viewport_settings.max_render_blocks",
            "3d_viewport_settings.interactive_render_blocks",
            "3d_viewport_settings.hard_block_limit",
            "3d_viewport_settings.full_detail_when_idle",
            "keybinds.toggle_outline",
            "keybinds.3d_viewport_camera_rotation",
            "keybinds.3d_viewport_camera_zoom",
            "keybinds.3d_viewport_camera_pan",
            "keybinds.3d_viewport_camera_reset",
            "keybinds.3d_viewport_camera_speed_increase",
            "keybinds.3d_viewport_camera_speed_decrease",
            "keybinds.3d_viewport_camera_speed_reset",
            "keybinds.3d_viewport_camera_in",
            "keybinds.3d_viewport_camera_out",
            "keybinds.3d_viewport_camera_left",
            "keybinds.3d_viewport_camera_right",
            "keybinds.3d_viewport_camera_up",
            "keybinds.3d_viewport_camera_down",
            "measurements.unit",
            "exports.output_directory",
            "wand.enabled",
            "wand.default_wand_item"
    };


    final float settingsScale = 0.85F;
    final int settingsControlWidth = 150; // TODO: May have to change this
    final int settingsControlHeight = 11;
    final int settingsButtonHeight = 11;
    final int settingsElementGap = 3; // the amount of gap that every element (button or text field) will have between each other.
    final int settingsElementXOffset = 5; // the amount of gap that every element (button or text field) will have from the left side of the window.
    final Minecraft minecraft = Minecraft.getInstance();
    final Font settingsFont = minecraft == null ? null : minecraft.font;
    final int settingsLabelLineHeight = settingsFont == null ? Math.max(1, Math.round(9 * settingsScale)) : Math.max(1, Math.round(settingsFont.lineHeight * settingsScale));
    final int settingsLabelLogicalWidth = Math.max(1, (int) Math.floor(Math.max(1, settingsControlWidth - 6) / settingsScale));
    int settingsY = 8;

    private ArrayList<Window> elements;

//    ScrollableControlPane settingsPane;


    public SettingsUI(int width, int height, Component title) {
        super(width, height, title);
        elements = new ArrayList<>();

        this.setTitleStyle(0xFF2E2E2E, 0.85F);


        // testing text field
        Window testTextFieldLabelCombo = createSettingsTextFieldWindow("Test Text Field", new WindowTextField(100, settingsControlHeight, null));
        elements.add(testTextFieldLabelCombo);

        // testing a keybind field.
        WindowKeybindField testKeybindField = new WindowKeybindField(100, settingsControlHeight, null);
        Window testKeybindFieldLabelCombo = createKeybindField("Test Keybind Field", testKeybindField);
        elements.add(testKeybindFieldLabelCombo);


        // testing toggle button
        ToggleButton testToggle = new ToggleButton(40, 20, Component.literal("ON"), Component.literal("OFF"), () -> {
            System.out.println("Test Toggle left button clicked!");
        }, () -> {
            System.out.println("Test Toggle right button clicked!");
        }, false, false);

        Window testButtonLabelCombo = createSettingsButtonWindow("Block Selection Highlight", testToggle);
        elements.add(testButtonLabelCombo);

        Button testButton = new Button(80, 20, Component.literal("Test Button"), () -> {
            System.out.println("Test Button clicked!");
        });

        Window testButton2LabelCombo = createSettingsButtonWindow("Test Button", testButton);
        elements.add(testButton2LabelCombo);

        buildButtons();
        instantiateElements(elements);

    }

    private Font getSettingsFont() {
        return settingsFont;
    }


    private void buildButtons() {
        elements.add(new Button(100, 20, Component.literal("Test Button 1"), null));
        elements.add(new Button(100, 20, Component.literal("Test Button 2"), null));
        elements.add(new Button(100, 20, Component.literal("Test Button 3"), null));
        elements.add(new Button(100, 20, Component.literal("Test Button 4"), null));
        elements.add(new Button(100, 20, Component.literal("Test Button 5"), null));
        elements.add(new Button(100, 20, Component.literal("Test Button 6"), null));
    }

    private void buildTextFields() {
        elements.add(new WindowTextField(settingsControlWidth, settingsControlHeight, Component.literal("Test Text Field 1"), null));
        elements.add(new WindowTextField(settingsControlWidth, settingsControlHeight, Component.literal("Test Text Field 2"), null));
        elements.add(new WindowTextField(settingsControlWidth, settingsControlHeight, Component.literal("Test Text Field 3"), null));
        elements.add(new WindowTextField(settingsControlWidth, settingsControlHeight, Component.literal("Test Text Field 4"), null));


    }

    private void instantiateElements(ArrayList<Window> elements) {
        int currentY = 0;

        for (Window element : elements) {
            this.addScrollableChild(element, settingsElementXOffset, currentY);
            currentY += element.getHeight() + getExtraSpaceBelow(element) + settingsElementGap;
        }
    }

    /**
     * Text fields hang below their label window (they are offset down by their own height), so that
     * height has to be reserved in addition to the window's own height.
     */
    private int getExtraSpaceBelow(Window element) {
        int extra = 0;
        for (Window child : element.getChildren()) {
            if (child instanceof WindowTextField || child instanceof WindowKeybindField) {
                extra = Math.max(extra, child.getHeight());
            }
        }
        return extra;
    }


    /**
     * This function will pair place a button (or toggle button) and a label in a window together so that they are side by side.
     *
     * @param settingTitle
     * @param button
     * @return
     */
    public Window createSettingsButtonWindow(String settingTitle, Button button) {
        Window returnable = new Window(this.getWidth() - 6, button.getHeight() + 6);

        returnable.setTitle(Component.literal(settingTitle));
        returnable.setAutoFitTitle(true);

        // match the height of the button to the height of the label window.
        button.setHeight(returnable.getHeight());

        // subtract the width of the buttons from the width of the returnable window, and set that as the width of the returnable window.

        // if the button is a toggle button, we need to subtract the width of both buttons from the width of the returnable window, since toggle buttons have two buttons side by side.
        if (button instanceof ToggleButton) {
            returnable.setWidth(returnable.getWidth() - ((ToggleButton) button).getWidth() * 2);
        } else {
            returnable.setWidth(returnable.getWidth() - button.getWidth());
        }

        // offset the button to the right side of the returnable window, with a 6 pixel gap from the right edge.
        button.setOffset(returnable.getWidth(), 0);

        returnable.addChild(button);

        return returnable;
    }


    public Window createSettingsTextFieldWindow(String settingTitle, WindowTextField textField) {
        Window returnable = new Window(this.getWidth() - 6, (textField.getHeight()) * 2);

        returnable.setTitle(Component.literal(settingTitle));
        returnable.setAutoFitTitle(true);

        // put the text field directly below the title of the returnable window, and make it the same width as the returnable window.

        textField.setHeight(returnable.getHeight());
        textField.setWidth(returnable.getWidth());

        textField.setOffset(0, textField.getHeight());

        textField.setAnchor(Anchor.BOTTOM);
        returnable.addChild(textField);
        return returnable;
    }

    public Window createKeybindField(String settingTitle, WindowKeybindField keybindField) {
        Window returnable = new Window(this.getWidth() - 6, (keybindField.getHeight()) * 2);

        returnable.setTitle(Component.literal(settingTitle));
        returnable.setAutoFitTitle(true);

        // put the keybind field directly below the title of the returnable window, and make it the same width as the returnable window.
        keybindField.setHeight(returnable.getHeight());
        keybindField.setWidth(returnable.getWidth());

        keybindField.setOffset(0, keybindField.getHeight());

        keybindField.setAnchor(Anchor.BOTTOM);
        returnable.addChild(keybindField);
        return returnable;
    }


}






