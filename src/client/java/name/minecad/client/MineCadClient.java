package name.minecad.client;

import java.util.ArrayList;
import java.util.List;
import name.minecad.client.commands.MineCadCommands;
import name.minecad.client.commands.MineFabClientCommands;
import name.minecad.client.render.SelectionOutlineRenderer;
import name.minecad.client.scannedblock.ScannedBlockManager;
import name.minecad.client.settings.MineCadSettings;
import name.minecad.client.wand.Wand;
import name.minecad.client.wand.WandManager;
import name.minecad.ui.Tab;
import name.minecad.ui.WindowManager;
import name.minecad.ui.TextPanel;
import name.minecad.ui.Window;
import name.minecad.ui.Window3DRenderer;
import name.minecad.ui.Button;
import name.minecad.ui.WindowKeybindField;
import name.minecad.ui.WindowTextField;
import name.minecad.ui.ScenePipRenderer;
import name.minecad.ui.ScrollableControlPane;
import name.minecad.ui.settings.SettingsUI;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

public class MineCadClient implements ClientModInitializer {

	public static final String MOD_ID = "mine-cad";
	private static final ScannedBlockManager scannedBlockManager = new ScannedBlockManager(20);
	private static final WandManager wandManager = new WandManager(new Wand(Items.STICK), scannedBlockManager);



	private static WindowManager windowManager;
	private static int pendingTabsOffsetX = 200;
	private static int pendingTabsOffsetY = 0;
	private static final int TEXT_PANEL_SCROLLBAR_SIZE = 8;

	// instantiating these early so i dont run into any circular dependency issues
	private Window mainWindow;
	private ScrollableControlPane additionalSettingsPane;
	private Button fileLabelBox;
	private WindowTextField fileNameField;
	private Button saveButton;
	private Button importButton;
	private Button exportStlButton;
	private Button exportObjButton;
	private Button exportFbxButton;
	private Button fillScannedButton;
	private Button fillBoundsButton;
	private Button scaleLabelBox;
	private WindowTextField scaleField;
	private Button setScaleButton;
	private Button showScaleButton;
	private Button resetCameraButton;
	private Button deleteButton;
	private Button listButton;
	private Button xLabelBox;
	private WindowTextField xField;
	private Button yLabelBox;
	private WindowTextField yField;
	private Button zLabelBox;
	private WindowTextField zField;
	private Button addButton;
	private Button removeButton;
	private Button pointsAddButton;
	private Button pointsRemoveButton;
	private Button pointsListButton;
	private Button pointsClearButton;
	private Button pointsFillButton;
	private Button pointsInfoButton;
	private Button showButton;
	private Button infoButton;
	private Window3DRenderer view3dPane;
	private ScrollableControlPane settingsPane;
	private WindowTextField[] settingFields;
	private WindowKeybindField[] keybindFields;
	private Button applySettingsButton;
	private Button resetSettingsButton;
	private TextPanel savesPane;
	private TextPanel helpPane;
	private TextPanel infoPane;
	private Window[] panes;
	private Tab mineCadTab;


	public static void setAdditionalTabsOffset(int xOffset, int yOffset) {
		pendingTabsOffsetX = xOffset;
		pendingTabsOffsetY = yOffset;
		if (windowManager != null) {
			windowManager.setAdditionalTabsOffset(xOffset, yOffset);
		}
	}

	public static WindowManager getInventoryTabManager() {
		if (windowManager == null) {
			throw new IllegalStateException("InventoryTabManager is not initialized yet");
		}
		return windowManager;
	}

	public static ScannedBlockManager getScannedBlockManager() {
		return scannedBlockManager;
	}

	public static WandManager getWandManager() {
		return wandManager;
	}



	@Override
	public void onInitializeClient() {
		MineCadSettings.load();
		wandManager.registerEvents();
		MineCadCommands.initialize(scannedBlockManager, wandManager);
		CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> MineCadCommands.register(dispatcher));
		MineFabClientCommands.register();
		SelectionOutlineRenderer.register();

		// delect the selection when the player disconnects from a server or singleplayer world. This way the selection will not carry over to other worlds/maps.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> scannedBlockManager.deleteSelection());

		// The 3D preview is drawn by the GPU through the game's picture-in-picture pipeline.
		PictureInPictureRendererRegistry.register(context -> new ScenePipRenderer());

		mainWindow = new Window(176, 166);
		mainWindow.setPadding(3);

		/** Additional Settings Tab For Selection & 3d View - added AFTER content panes so it renders on top */
		additionalSettingsPane = new ScrollableControlPane(90, 136, Component.literal("Selection Settings"));
		additionalSettingsPane.setTitleStyle(0xFF2E2E2E, 0.80F);
		mainWindow.addChild(additionalSettingsPane);
		// Nested in mainWindow, anchored to its right side and resting flush against the outside edge.
		// Anchoring to the right puts the pane inside the padding, so shift out by its width + the padding.
		final int sidePaneWidth = 90;
		final int mainPadding = 3;
		additionalSettingsPane.setSize(sidePaneWidth, 136);
		additionalSettingsPane.setAnchor(Window.Anchor.TOP_RIGHT);
		additionalSettingsPane.setOffset(sidePaneWidth + mainPadding, -mainPadding); // -padding lines up with the window top
		/** End Additional Settings Tab For Selection & 3d View */


		/** Buttons and Input Fields for 3D additional settings */
		final int commandButtonWidth = additionalSettingsPane.getContentWidth();
		final int commandButtonHeight = 14;
		final float commandButtonFont = 0.66F;
		int commandY = 0;
		final int rowGap = 3;
		final int inlineLabelWidth = 34;
		final int inlineFieldGap = 3;
		final int inlineFieldWidth = Math.max(24, commandButtonWidth - inlineLabelWidth - inlineFieldGap);
		final int fileLabelWidth = 18;
		final int fileFieldWidth = Math.max(24, commandButtonWidth - fileLabelWidth - inlineFieldGap);
		final int scaleLabelWidth = 18;
		final int scaleFieldWidth = Math.max(24, commandButtonWidth - scaleLabelWidth - inlineFieldGap);

		fileLabelBox = new Button(fileLabelWidth, 14, Component.literal("File\nName"), null);
		fileLabelBox.setEnabled(false);
		fileLabelBox.setFontSize(0.46F);
		additionalSettingsPane.addScrollableChild(fileLabelBox, 0, commandY);

		fileNameField = new WindowTextField(
				fileFieldWidth,
				14,
				Component.empty(),
				WindowTextField.InputMode.TEXT
		);
		fileNameField.setText("selection_01");
		fileNameField.setPlaceholder("selection_01");
		additionalSettingsPane.addScrollableChild(fileNameField, fileLabelWidth + inlineFieldGap, commandY);
		commandY += 14 + rowGap;

		saveButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Save"),
				() -> runSelectionCommand("selection save " + readOrDefault(fileNameField, "selection_01")));
		saveButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(saveButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		importButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Import"),
				() -> runSelectionCommand("selection import " + readOrDefault(fileNameField, "selection_01")));
		importButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(importButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		exportStlButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Export STL"),
				() -> runSelectionCommand("selection export stl " + readOrDefault(fileNameField, "selection_export")));
		exportStlButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(exportStlButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		exportObjButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Export OBJ"),
				() -> runSelectionCommand("selection export obj " + readOrDefault(fileNameField, "selection_export")));
		exportObjButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(exportObjButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		exportFbxButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Export FBX"),
				() -> runSelectionCommand("selection export fbx " + readOrDefault(fileNameField, "selection_export")));
		exportFbxButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(exportFbxButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		fillScannedButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Fill Scan"),
				() -> runSelectionCommand("selection fillscanned"));
		fillScannedButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(fillScannedButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		fillBoundsButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Fill Bounds"),
				() -> runSelectionCommand("selection fillbounds"));
		fillBoundsButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(fillBoundsButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		// Scale: how many meters one block represents (1 = default, 0.1 = block is 10 cm, 2 = block is 2 m, ...)
		scaleLabelBox = new Button(scaleLabelWidth, 14, Component.literal("Block\nScale"), null);
		scaleLabelBox.setEnabled(false);
		scaleLabelBox.setFontSize(0.44F);
		additionalSettingsPane.addScrollableChild(scaleLabelBox, 0, commandY);

		scaleField = new WindowTextField(
				scaleFieldWidth,
				14,
				Component.empty(),
				WindowTextField.InputMode.NUMERIC
		);
		scaleField.setText(MineCadSettings.formatNumber(MineCadSettings.getMetersPerBlock()));
		scaleField.setPlaceholder("1");
		additionalSettingsPane.addScrollableChild(scaleField, scaleLabelWidth + inlineFieldGap, commandY);
		commandY += 14 + rowGap;

		setScaleButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Set Scale"), () -> {
			String value = readOrDefault(scaleField, "1");
			try {
				MineCadSettings.setMetersPerBlock(Double.parseDouble(value));
			} catch (NumberFormatException e) {
				System.out.println("[mine-cad] invalid scale value: " + value);
				return;
			}
			scaleField.setText(MineCadSettings.formatNumber(MineCadSettings.getMetersPerBlock()));
			runSelectionCommand("selection scale " + MineCadSettings.formatNumber(MineCadSettings.getMetersPerBlock()));
		});
		setScaleButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(setScaleButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		showScaleButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Show Scale"),
				() -> runSelectionCommand("selection scale"));
		showScaleButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(showScaleButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		// The rest of the controls stay below the primary file/export/fill/scale actions.
		resetCameraButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Reset Camera"), () -> {
			if (view3dPane == null) {
				return;
			}
			view3dPane.resetCameraPosition();
		});
		resetCameraButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(resetCameraButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		deleteButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Delete Selection"),
				() -> runSelectionCommand("selection delete"));
		deleteButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(deleteButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		listButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("List"),
				() -> runSelectionCommand("selection list"));
		listButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(listButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		xLabelBox = new Button(inlineLabelWidth, 14, Component.literal("X:"), null);
		xLabelBox.setEnabled(false);
		xLabelBox.setFontSize(0.60F);
		additionalSettingsPane.addScrollableChild(xLabelBox, 0, commandY);
		xField = new WindowTextField(inlineFieldWidth, 14, Component.empty(), WindowTextField.InputMode.NUMERIC);
		xField.setText("0");
		xField.setPlaceholder("0");
		additionalSettingsPane.addScrollableChild(xField, inlineLabelWidth + inlineFieldGap, commandY);
		commandY += 14 + rowGap;

		yLabelBox = new Button(inlineLabelWidth, 14, Component.literal("Y:"), null);
		yLabelBox.setEnabled(false);
		yLabelBox.setFontSize(0.60F);
		additionalSettingsPane.addScrollableChild(yLabelBox, 0, commandY);
		yField = new WindowTextField(inlineFieldWidth, 14, Component.empty(), WindowTextField.InputMode.NUMERIC);
		yField.setText("64");
		yField.setPlaceholder("64");
		additionalSettingsPane.addScrollableChild(yField, inlineLabelWidth + inlineFieldGap, commandY);
		commandY += 14 + rowGap;

		zLabelBox = new Button(inlineLabelWidth, 14, Component.literal("Z:"), null);
		zLabelBox.setEnabled(false);
		zLabelBox.setFontSize(0.60F);
		additionalSettingsPane.addScrollableChild(zLabelBox, 0, commandY);
		zField = new WindowTextField(inlineFieldWidth, 14, Component.empty(), WindowTextField.InputMode.NUMERIC);
		zField.setText("0");
		zField.setPlaceholder("0");
		additionalSettingsPane.addScrollableChild(zField, inlineLabelWidth + inlineFieldGap, commandY);
		commandY += 14 + rowGap;

		addButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Add"),
				() -> runSelectionCommand("selection add " + readPositionTriplet(xField, yField, zField)));
		addButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(addButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		removeButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Remove"),
				() -> runSelectionCommand("selection remove " + readPositionTriplet(xField, yField, zField)));
		removeButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(removeButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		pointsAddButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Points Add"),
				() -> runSelectionCommand("selection points add " + readPositionTriplet(xField, yField, zField)));
		pointsAddButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(pointsAddButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		pointsRemoveButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Points Remove"),
				() -> runSelectionCommand("selection points remove " + readPositionTriplet(xField, yField, zField)));
		pointsRemoveButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(pointsRemoveButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		pointsListButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Points List"),
				() -> runSelectionCommand("selection points list"));
		pointsListButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(pointsListButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		pointsClearButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Points Clear"),
				() -> runSelectionCommand("selection points clear"));
		pointsClearButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(pointsClearButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		pointsFillButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Points Fill"),
				() -> runSelectionCommand("selection points fill"));
		pointsFillButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(pointsFillButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		pointsInfoButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Points Info"),
				() -> runSelectionCommand("selection points info"));
		pointsInfoButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(pointsInfoButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		showButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Show"),
				() -> runSelectionCommand("selection show"));
		showButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(showButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;

		infoButton = new Button(commandButtonWidth, commandButtonHeight, Component.literal("Info"),
				() -> runSelectionCommand("selection info"));
		infoButton.setFontSize(commandButtonFont);
		additionalSettingsPane.addScrollableChild(infoButton, 0, commandY);
		commandY += commandButtonHeight + rowGap;
		/** End Buttons and Input Fields for 3D additional settings */


		/** 3D VIEW TAB */
		view3dPane = new Window3DRenderer(100, 100);
		view3dPane.setTitle(Component.literal("3D View"));
		view3dPane.setBackgroundColor(0xFFFFFFFF);
		view3dPane.setRenderScalePercent(0.40);

		view3dPane.setOnTabSelected(() -> {
			System.out.println("Settings tab selected.");
			additionalSettingsPane.setVisible(true);

			// render the additional settings for this particular selection
			// TODO: Implement logic to update the additionalSettingsPane with selection-specific settings when the view3d tab is selected.
		});
		/** END 3D VIEW TAB */


		/** SETTINGS TAB */
		SettingsUI testSettingUI = new SettingsUI(100, 100, Component.literal("Test Settings UI"));





//		settingsPane = new ScrollableControlPane(100, 100, Component.literal("Settings"));
//		settingsPane.setTitleStyle(0xFF2E2E2E, 0.85F);
//
//		// test settings
//		SettingsUI testSettings = new SettingsUI(90, 136);
//		settingsPane.addChild(testSettings);
//		// end test settings
//
//
//
//
//
//
//
//
//
//		final float settingsScale = 0.85F;
//		final int settingsControlWidth = settingsPane.getContentWidth();
//		final int settingsControlHeight = 11;
//		final int settingsButtonHeight = 11;
//		final int settingsFieldGap = 3;
//		final Minecraft minecraft = Minecraft.getInstance();
//		final Font settingsFont = minecraft == null ? null : minecraft.font;
//		final int settingsLabelLineHeight = settingsFont == null ? Math.max(1, Math.round(9 * settingsScale))
//				: Math.max(1, Math.round(settingsFont.lineHeight * settingsScale));
//		final int settingsLabelLogicalWidth = Math.max(1, (int) Math.floor(Math.max(1, settingsControlWidth - 6) / settingsScale));
//		int settingsY = 8;
//		final String[] settingKeys = {
//				"outline_settings.enabled",
//				"outline_settings.color",
//				"outline_settings.thickness",
//				"3d_viewport_settings.background_color",
//				"3d_viewport_settings.mouse_sensitivity",
//				"3d_viewport_settings.mouse_acceleration_enabled",
//				"3d_viewport_settings.mouse_acceleration_factor",
//				"3d_viewport_settings.camera_speed",
//				"3d_viewport_settings.camera_acceleration_enabled",
//				"3d_viewport_settings.camera_acceleration_factor",
//				"3d_viewport_settings.max_render_blocks",
//				"3d_viewport_settings.interactive_render_blocks",
//				"3d_viewport_settings.hard_block_limit",
//				"3d_viewport_settings.full_detail_when_idle",
//				"keybinds.toggle_outline",
//				"keybinds.3d_viewport_camera_rotation",
//				"keybinds.3d_viewport_camera_zoom",
//				"keybinds.3d_viewport_camera_pan",
//				"keybinds.3d_viewport_camera_reset",
//				"keybinds.3d_viewport_camera_speed_increase",
//				"keybinds.3d_viewport_camera_speed_decrease",
//				"keybinds.3d_viewport_camera_speed_reset",
//				"keybinds.3d_viewport_camera_in",
//				"keybinds.3d_viewport_camera_out",
//				"keybinds.3d_viewport_camera_left",
//				"keybinds.3d_viewport_camera_right",
//				"keybinds.3d_viewport_camera_up",
//				"keybinds.3d_viewport_camera_down",
//				"measurements.unit",
//				"exports.output_directory",
//				"wand.enabled",
//				"wand.default_wand_item"
//		};
//		settingFields = new WindowTextField[settingKeys.length];
//		keybindFields = new WindowKeybindField[settingKeys.length];
//		for (int i = 0; i < settingKeys.length; i++) {
//			String key = settingKeys[i];
//			int labelLines = wrappedLineCount(settingsFont, key, settingsLabelLogicalWidth);
//			settingsY += labelLines * settingsLabelLineHeight + 1;
//			if (key.startsWith("keybinds.")) {
//				WindowKeybindField field = new WindowKeybindField(
//						settingsControlWidth,
//						settingsControlHeight,
//						Component.literal(key)
//				);
//				field.setFontScale(settingsScale);
//				keybindFields[i] = field;
//				settingsPane.addScrollableChild(field, 0, settingsY);
//			} else {
//				WindowTextField field = new WindowTextField(
//						settingsControlWidth,
//						settingsControlHeight,
//						Component.literal(key),
//						WindowTextField.InputMode.TEXT
//				);
//				field.setFontScale(settingsScale);
//				settingFields[i] = field;
//				settingsPane.addScrollableChild(field, 0, settingsY);
//			}
//			settingsY += settingsControlHeight + settingsFieldGap;
//		}
//
//		Runnable refreshSettingsFields = () -> {
//			MineCadSettings.load();
//			for (int i = 0; i < settingKeys.length; i++) {
//				String value = MineCadSettings.getSettingValue(settingKeys[i]);
//				if (keybindFields[i] != null) {
//					keybindFields[i].setBindingText(value);
//				} else {
//					settingFields[i].setText(value);
//				}
//			}
//		};
//
//		applySettingsButton = new Button(settingsControlWidth, settingsButtonHeight, Component.literal("Apply Settings"), () -> {
//			for (int i = 0; i < settingKeys.length; i++) {
//				try {
//					String value = keybindFields[i] != null ? keybindFields[i].getBindingText() : settingFields[i].getText();
//					MineCadSettings.setSettingValue(settingKeys[i], value);
//				} catch (RuntimeException e) {
//					System.out.println("[mine-cad] invalid value for " + settingKeys[i] + ": " + e.getMessage());
//				}
//			}
//			refreshSettingsFields.run();
//		});
//		applySettingsButton.setFontSize(commandButtonFont * settingsScale);
//		settingsPane.addScrollableChild(applySettingsButton, 0, settingsY + 2);
//		settingsY += settingsButtonHeight + 3;
//
//		resetSettingsButton = new Button(settingsControlWidth, settingsButtonHeight, Component.literal("Reset Defaults"), () -> {
//			MineCadSettings.resetToDefaults();
//			refreshSettingsFields.run();
//		});
//		resetSettingsButton.setFontSize(commandButtonFont * settingsScale);
//		settingsPane.addScrollableChild(resetSettingsButton, 0, settingsY + 2);
//		refreshSettingsFields.run();
//		settingsPane.setOnTabSelected(() -> {
//			System.out.println("Settings tab selected.");
//			additionalSettingsPane.setVisible(false);
//			refreshSettingsFields.run();
//		});
		/** END SETTINGS TAB */

		/** SAVES TAB */
		savesPane = new TextPanel(100, 100, Component.literal("Saves"));
		savesPane.setScrollbarSize(TEXT_PANEL_SCROLLBAR_SIZE);
		savesPane.setStyledLines(List.of(
				TextPanel.line("Saves coming soon.", 0xFF2E2E2E, 1.0F)
		));
		savesPane.setOnTabSelected(() -> {
			System.out.println("Saves tab selected.");
			additionalSettingsPane.setVisible(false);
		});
		/** END SAVES TAB */

		/** HELP TAB */
		helpPane = new TextPanel(100, 100, Component.literal("Help"));
		helpPane.setScrollbarSize(TEXT_PANEL_SCROLLBAR_SIZE);
		helpPane.setStyledLines(List.of(
				TextPanel.line("Middle-drag in 3D to orbit", 0xFF2E2E2E, 1.0F),
				TextPanel.line("Mouse wheel zooms the model", 0xFF404040, 1.0F),
				TextPanel.line("WASD + Q/E moves preview", 0xFF404040, 1.0F),
				TextPanel.line("Use /minecad for commands", 0xFF404040, 1.0F)
		));
		helpPane.setOnTabSelected(() -> {
			System.out.println("Help tab selected.");
			additionalSettingsPane.setVisible(false);
		});
		/** END HELP TAB */

		/** Info Tab */
		infoPane = new TextPanel(100, 100, Component.literal("Info"));
		infoPane.setScrollbarSize(TEXT_PANEL_SCROLLBAR_SIZE);
		infoPane.setStyledLines(List.of(
				TextPanel.line("MineCad Mod Info:", 0xFF1D3557, 1.1F),
				TextPanel.line("- Version: 0.1.0", 0xFF404040, 1.0F),
				TextPanel.line("- Author: snapy1", 0xFF404040, 1.0F),
				TextPanel.line("- License: MIT", 0xFF404040, 1.0F),
				TextPanel.line("This mod was designed so that you can use Minecraft as a CAD/3D Software, " +
						"export your Minecraft builds as 3D files (OBJ, STL, FBX), adjust scale in at an infinite level so " +
						"that you can use Minecraft to create objects in the most fidelity and detail, bringing the opportunity to" +
						" design stuff for real applications all inside Minecraft.", 0xFF404040, 1.0F)
//				InventoryTabTextPanel.line("export your Minecraft builds as 3D files (OBJ, STL, FBX), ", 0xFF404040, 1.0F),
//				InventoryTabTextPanel.line("adjust scale in at an infinite level so that you can use Minecraft to", 0xFF404040, 1.0F),
//				InventoryTabTextPanel.line("create objects in the upmost fidelity and detail.", 0xFF404040, 1.0F)

		));
		infoPane.setOnTabSelected(() -> {
			System.out.println("Info tab selected.");
			additionalSettingsPane.setVisible(false);
		});
		/** End Info Tab */


		/** Consolidating the left side panes into an array */
		panes = new Window[] { view3dPane, /*selectionPane,*/ /*settingsPane*/ testSettingUI, savesPane, helpPane, infoPane };
		for (Window pane : panes) {
			mainWindow.addChild(pane);
			pane.setAnchor(Window.Anchor.TOP_LEFT);
			pane.setOffset(0, 0);
			pane.setRelativeSize(1.0, 1.0);
		}


		ArrayList<Button> leftSideTabButtonsList = new ArrayList<>();

		// rewriting using window buttons instead.
		Button ThreeDTabButton = new Button(52, 20, Component.literal("3D"), () -> activateLeftTab(0, panes, leftSideTabButtonsList));
		Button settingsTabButton = new Button(52, 20, Component.literal("Settings"), () -> activateLeftTab(1, panes, leftSideTabButtonsList));;
		Button savesTabButton = new Button(52, 20, Component.literal("Saves"), () -> activateLeftTab(2, panes, leftSideTabButtonsList));
		Button helpTabButton = new Button(52, 20, Component.literal("Help"), () -> activateLeftTab(3, panes, leftSideTabButtonsList));
		Button infoTabButton = new Button(52, 20, Component.literal("Info"), () -> activateLeftTab(4, panes, leftSideTabButtonsList));

		leftSideTabButtonsList.add(ThreeDTabButton);
		leftSideTabButtonsList.add(settingsTabButton);
		leftSideTabButtonsList.add(savesTabButton);
		leftSideTabButtonsList.add(helpTabButton);
		leftSideTabButtonsList.add(infoTabButton);
		Button[] leftSideTabButtons = leftSideTabButtonsList.toArray(new Button[0]);

		for (int i = 0; i < leftSideTabButtonsList.size(); i++) {
			leftSideTabButtonsList.get(i).setAnchor(Window.Anchor.TOP_LEFT);
			leftSideTabButtonsList.get(i).setOffset(-56, 2 + i * 22);
			mainWindow.addChild(leftSideTabButtonsList.get(i));
		}
		activateLeftTab(0, panes, leftSideTabButtonsList);
		/** End consolidating the panes into an array */


		mineCadTab = new Tab(0, 0, Component.literal("My GUI"), null, false, () -> {}, mainWindow);
		windowManager = new WindowManager(mineCadTab);
		windowManager.setAdditionalTabsOffset(pendingTabsOffsetX, pendingTabsOffsetY);



		// Add custom tabs whenever the player inventory screen is initialized.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> windowManager.registerForScreen(client, screen, width, height));


		// debug log a message to indicate that the client mod has been initialized
		System.out.println("MineCadClient initialized");


	}

	private static void activateLeftTab(int index, Window[] panes, ArrayList<Button> leftSideTabButtons) {
		for (int i = 0; i < panes.length; i++) {
			panes[i].setVisible(i == index);
		}
		for (int i = 0; i < leftSideTabButtons.size(); i++) {
			leftSideTabButtons.get(i).setSelected(i == index);
		}
	}

	private static String readOrDefault(WindowTextField field, String fallback) {
		if (field == null) {
			return fallback;
		}
		String value = field.getText();
		if (value == null) {
			return fallback;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? fallback : trimmed;
	}

	private static String readPositionTriplet(WindowTextField xField,
											  WindowTextField yField,
											  WindowTextField zField) {
		return readOrDefault(xField, "0") + " " + readOrDefault(yField, "64") + " " + readOrDefault(zField, "0");
	}

	private static void runSelectionCommand(String commandWithoutSlash) {
		String command = normalizeMineCadCommand(commandWithoutSlash);
		if (command.isEmpty()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) {
			System.out.println("[mine-cad] command skipped (no player): /" + command);
			return;
		}

		try {
			// Keep this reflective so it works across mapping/API name differences.
			var sendCommand = client.player.connection.getClass().getMethod("sendCommand", String.class);
			sendCommand.invoke(client.player.connection, command);
		} catch (ReflectiveOperationException sendCommandFailed) {
			try {
				var sendUnsignedCommand = client.player.connection.getClass().getMethod("sendUnsignedCommand", String.class);
				sendUnsignedCommand.invoke(client.player.connection, command);
			} catch (ReflectiveOperationException sendUnsignedFailed) {
				System.out.println("[mine-cad] could not execute command, fallback log: /" + command);
			}
		}
	}

	private static String normalizeMineCadCommand(String commandWithoutSlash) {
		String command = commandWithoutSlash == null ? "" : commandWithoutSlash.trim();
		if (command.isEmpty()) {
			return "";
		}
		if (command.startsWith("/")) {
			command = command.substring(1).trim();
		}
		if (command.startsWith("minecad ") || "minecad".equals(command)) {
			return command;
		}
		if (command.startsWith("minefab ")) {
			command = command.substring("minefab ".length()).trim();
		}
		if (command.startsWith("selection ") || command.startsWith("wand ") || command.startsWith("settings ")
				|| command.equals("help") || command.equals("info")) {
			return "minecad " + command;
		}
		return "minecad " + command;
	}

	private static int wrappedLineCount(Font font, String text, int maxWidth) {
		if (text == null || text.isEmpty()) {
			return 1;
		}
		if (font == null) {
			int approxCharWidth = 6;
			int charsPerLine = Math.max(1, maxWidth / approxCharWidth);
			return Math.max(1, (int) Math.ceil((double) text.length() / charsPerLine));
		}
		int lines = 0;
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
			lines++;
			start = lastFitting;
		}
		return Math.max(1, lines);
	}


}
