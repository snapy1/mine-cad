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

//        settingsPane = new ScrollableControlPane(width, height, title);
        this.setTitleStyle(0xFF2E2E2E, 0.85F);

        // testing the toggle button.
        elements.add(new ToggleButton(40, 20, Component.literal("ON"), Component.literal("OFF"), () -> {
            System.out.println("Toggle left button clicked!");
        }, () -> {
            System.out.println("Toggle right button clicked!");
        }, false, false));

        buildButtons();
//        buildTextFields();
        instantiateElements(elements);

    }


    private void buildButtons(){
        elements.add(new Button(100, 20, Component.literal("Test Button 1"), null));
        elements.add(new Button(100, 20, Component.literal("Test Button 2"), null));
        elements.add(new Button(100, 20, Component.literal("Test Button 3"), null));
        elements.add(new Button(100, 20, Component.literal("Test Button 4"), null));
        elements.add(new Button(100, 20, Component.literal("Test Button 5"), null));
        elements.add(new Button(100, 20, Component.literal("Test Button 6"), null));
    }

    private void buildTextFields(){
        elements.add(new WindowTextField(settingsControlWidth, settingsControlHeight, Component.literal("Test Text Field 1"), null));
        elements.add(new WindowTextField(settingsControlWidth, settingsControlHeight, Component.literal("Test Text Field 2"), null));
        elements.add(new WindowTextField(settingsControlWidth, settingsControlHeight, Component.literal("Test Text Field 3"), null));
        elements.add(new WindowTextField(settingsControlWidth, settingsControlHeight, Component.literal("Test Text Field 4"), null));


    }

    private void instantiateElements(ArrayList<Window> elements) {
        int currentY = 0;

        for (Window element : elements) {
            this.addScrollableChild(element, settingsElementXOffset, currentY);
            currentY += element.getHeight() + settingsElementGap;
        }




//        for (String key : settingKeys) {
//            if (key.startsWith("keybinds.")) {
//                WindowKeybindField keybindField = new WindowKeybindField(settingsControlWidth, settingsControlHeight, Component.literal(key), null);
//                elements.add(keybindField);
//                keybinds.add(keybindField);
//            } else {
//                WindowTextField textField = new WindowTextField(settingsControlWidth, settingsControlHeight, Component.literal(key), null);
//                elements.add(textField);
//                settingFields.add(textField);
//            }
//
//
//        }
    }


    public Window createSettingsButtonWindow(String settingTitle, Button button){
        Window returnable = null;


        return returnable;
    }


}


//    private int calculateSettingsPaneHeight() {
//        int totalHeight = 0;
//        for (WindowTextField field : settingFields) {
//            totalHeight += field.getHeight() + settingsFieldGap;
//        }
//        for (WindowKeybindField keybind : keybinds) {
//            totalHeight += keybind.getHeight() + settingsFieldGap;
//        }
//        return totalHeight;
//    }






