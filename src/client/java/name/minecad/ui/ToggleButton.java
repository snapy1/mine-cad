package name.minecad.ui;


import net.minecraft.network.chat.Component;

/**
 * A simple toggle button UI that can be turned on and off.
 */
public class ToggleButton extends Button {
    private Button partnerButton;
    private boolean isOn = false; // defaults to false
    private boolean flipDirection = false; // defaults to false
    private Runnable leftToggleAction;
    private Runnable rightToggleAction;

    public ToggleButton(int width, int height, Component labelLeft, Component labelRight, Runnable leftToggleAction, Runnable rightToggleAction, boolean isOn, boolean flipDirection) {
        super(width, height, labelLeft, leftToggleAction);
        this.isOn = isOn;
        this.flipDirection = flipDirection;
        this.leftToggleAction = leftToggleAction;
        this.rightToggleAction = rightToggleAction;

        // creating the partner button with the same dimensions and label, but with a different action
        partnerButton = new Button(width, height, labelRight, this::executeRightAction);
//        partnerButton = new Button(width, height, labelRight, () -> {
//            executeRightAction();
//            rightToggleAction.run(); // call the original action
//            // perform any other action here, e.g., change the state of the button or perform some other logic
//        });


        pairButtonsSideBySide(this, partnerButton);



        this.setAction(this::executeLeftAction);
//        this.setAction(() -> {
//            executeLeftAction();
//            leftToggleAction.run(); // call the original action too.
//        });

        // Ensure visual state matches the requested initial isOn value.
        toggleButtonColor();


    }

    private void pairButtonsSideBySide(Button button1, Button button2) {
        this.addChild(button2);
        button2.setOffset(button1.getWidth(), 0); // position button2 to the right of button1
    }





    private void executeLeftAction() {
        // Left side is selected
        isOn = !flipDirection;

        toggleButtonColor();

        if (leftToggleAction != null) {
            leftToggleAction.run();
        }
    }

    private void executeRightAction() {
        // Right side is selected
        isOn = flipDirection;

        toggleButtonColor();

        if (rightToggleAction != null) {
            rightToggleAction.run();
        }
    }

    public void toggleButtonColor() {
        int activeColor = 0xFF8B8B8B;
        int inactiveColor = 0xFFC6C6C6;

        boolean leftSelected = isOn != flipDirection;

        this.setBackgroundColor(
                leftSelected ? activeColor : inactiveColor
        );

        partnerButton.setBackgroundColor(
                leftSelected ? inactiveColor : activeColor
        );
    }

    public void setOn(boolean isOn) {
        this.isOn = isOn;
        toggleButtonColor();
    }

    public boolean isOn() {
        return isOn;
    }

    @Override
    public void setHeight(int height) {
        super.setHeight(height);
        partnerButton.setHeight(height);
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        partnerButton.setWidth(width);
    }


//    public void toggleButtonColor(){
//        if (isOn) {
//            // darken the right button (or left button if flipDirection is true) to indicate it's active
//            if (flipDirection) {
//                partnerButton.setBackgroundColor(0xFF8B8B8B); // darken left button
//                this.setBackgroundColor(0xFFC6C6C6); // reset right button
//            } else {
//                this.setBackgroundColor(0xFF8B8B8B); // darken right button
//                partnerButton.setBackgroundColor(0xFFC6C6C6); // reset left button
//            }
//        } else {
//            // reset both buttons to default color when toggled off
//            this.setBackgroundColor(0xFFC6C6C6);
//            partnerButton.setBackgroundColor(0xFFC6C6C6);
//        }
//    }

//    public void setOn(boolean isOn) {
//        this.isOn = isOn;
//    }

//    public void toggle() {
//        isOn = !isOn;
//        if (isOn) {
//        // darken the right button (or left button if flipDirection is true) to indicate it's active
//            if (flipDirection) {
//                partnerButton.setBackgroundColor(0xFF8B8B8B); // darken left button
//                this.setBackgroundColor(0xFFC6C6C6); // reset right button
//            } else {
//                this.setBackgroundColor(0xFF8B8B8B); // darken right button
//                partnerButton.setBackgroundColor(0xFFC6C6C6); // reset left button
//            }
//        } else {
//            // reset both buttons to default color when toggled off
//            this.setBackgroundColor(0xFFC6C6C6);
//            partnerButton.setBackgroundColor(0xFFC6C6C6);
//        }




    }

//    public boolean isOn() {
//        return isOn;
//    }

//    public void editRightButtonAction(Runnable action) {
//        // keep the toggle functionality though of course
//        partnerButton.setAction(() -> {
//            executeRightAction();
//            action.run(); // call the new action
//        });
//    }
//
//    public void editLeftButtonAction(Runnable action) {
//        this.setAction(() -> {
//            executeLeftAction();
//            action.run(); // call the new action
//        });
//    }

