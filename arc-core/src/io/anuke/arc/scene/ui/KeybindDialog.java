package io.anuke.arc.scene.ui;

import io.anuke.arc.Application.ApplicationType;
import io.anuke.arc.Core;
import io.anuke.arc.KeyBinds.Axis;
import io.anuke.arc.KeyBinds.KeyBind;
import io.anuke.arc.KeyBinds.Section;
import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.ObjectIntMap;
import io.anuke.arc.collection.OrderedMap;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.input.InputDevice;
import io.anuke.arc.input.InputDevice.DeviceType;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.scene.event.InputEvent;
import io.anuke.arc.scene.event.InputListener;
import io.anuke.arc.scene.style.SkinReader.ReadContext;
import io.anuke.arc.scene.style.Style;
import io.anuke.arc.scene.ui.layout.Stack;
import io.anuke.arc.scene.ui.layout.Table;
import io.anuke.arc.util.Align;
import io.anuke.arc.util.Strings;
import io.anuke.arc.util.Time;

import static io.anuke.arc.Core.*;

public class KeybindDialog extends Dialog{
    protected KeybindDialogStyle style;

    protected Section section;
    protected KeyBind rebindKey = null;
    protected boolean rebindAxis = false;
    protected boolean rebindMin = true;
    protected KeyCode minKey = null;
    protected Dialog rebindDialog;
    protected ObjectIntMap<Section> sectionControls = new ObjectIntMap<>();

    public KeybindDialog(){
        super(bundle.get("text.keybind.title", "Rebind Keys"));
        style = scene.skin.get(KeybindDialogStyle.class);
        setup();
        addCloseButton();
    }

    public void setStyle(KeybindDialogStyle style){
        this.style = style;
        setup();
    }

    private void setup(){
        content().clear();

        Section[] sections = Core.keybinds.getSections();

        Stack stack = new Stack();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        ScrollPane pane = new ScrollPane(stack, style.paneStyle);
        pane.setFadeScrollBars(false);
        this.section = sections[0];

        for(Section section : sections){
            if(!sectionControls.containsKey(section))
                sectionControls.put(section, input.getDevices().indexOf(section.device, true));

            if(sectionControls.get(section, 0) >= input.getDevices().size){
                sectionControls.put(section, 0);
                section.device = input.getDevices().get(0);
            }

            if(sections.length != 1){
                TextButton button = new TextButton(bundle.get("section." + section.name + ".name", Strings.capitalize(section.name)), "toggle");
                if(section.equals(this.section))
                    button.toggle();

                button.clicked(() -> this.section = section);

                group.add(button);
                content().add(button).fill();
            }

            Table table = new Table();

            Label device = new Label("Keyboard");
            //device.setColor(style.controllerColor);
            device.setAlignment(Align.center);

            Array<InputDevice> devices = input.getDevices();

            Table stable = new Table();

            stable.addButton("<", () -> {
                int i = sectionControls.get(section, 0);
                if(i - 1 >= 0){
                    sectionControls.put(section, i - 1);
                    section.device = devices.get(i - 1);
                    settings.save();
                    setup();
                }
            }).disabled(sectionControls.get(section, 0) - 1 < 0).size(40);

            stable.add(device).minWidth(device.getMinWidth() + 60);

            device.setText(input.getDevices().get(sectionControls.get(section, 0)).name());

            stable.addButton(">", () -> {
                int i = sectionControls.get(section, 0);

                if(i + 1 < devices.size){
                    sectionControls.put(section, i + 1);
                    section.device = devices.get(i + 1);
                    settings.save();
                    setup();
                }
            }).disabled(sectionControls.get(section, 0) + 1 >= devices.size).size(40);

            table.add(stable).colspan(3);

            table.row();
            table.add().height(10);
            table.row();
            if(section.device.type() == DeviceType.controller){
                table.table(info -> info.add("Controller Type: [#" + style.controllerColor.toString().toUpperCase() + "]" +
                Strings.capitalize(section.device.name())).left());
            }
            table.row();

            String lastCategory = null;

            for(KeyBind keybind : keybinds.getKeybinds()){
                if(lastCategory != keybind.category() && keybind.category() != null){
                    table.add(bundle.get("category." + keybind.category() + ".name", Strings.capitalize(keybind.category()))).color(Color.GRAY).colspan(3).pad(10).padBottom(4).row();
                    table.addImage("white").color(Color.GRAY).fillX().height(3).pad(6).colspan(3).padTop(0).padBottom(10).row();
                    lastCategory = keybind.category();
                }

                Axis axis = keybinds.get(section, keybind);

                if(keybind.defaultValue(section.device.type()) instanceof Axis){
                    table.add(bundle.get("keybind." + keybind.name() + ".name", Strings.capitalize(keybind.name())), style.keyNameColor).left().padRight(40).padLeft(8);

                    if(axis.key != null){
                        table.add(axis.key.toString(), style.keyColor).left().minWidth(90).padRight(20);
                    }else{
                        Table axt = new Table();
                        axt.left();
                        axt.labelWrap(axis.min.toString() + " [red]/[] " + axis.max.toString()).color(style.keyColor).width(140f).padRight(5);
                        table.add(axt).left().minWidth(90).padRight(20);
                    }

                    table.addButton(bundle.get("text.settings.rebind", "Rebind"), () -> {
                        rebindAxis = true;
                        rebindMin = true;
                        openDialog(section, keybind);
                    }).width(110f);
                    table.row();
                }else{
                    table.add(bundle.get("keybind." + keybind.name() + ".name", Strings.capitalize(keybind.name())),
                    style.keyNameColor).left().padRight(40).padLeft(8);
                    table.add(keybinds.get(section, keybind).key.toString(),
                    style.keyColor).left().minWidth(90).padRight(20);
                    table.addButton(bundle.get("text.settings.rebind", "Rebind"), () -> {
                        rebindAxis = false;
                        rebindMin = false;
                        openDialog(section, keybind);
                    }).width(110f);
                    table.row();
                }
            }

            table.visible(() -> this.section.equals(section));

            table.addButton(bundle.get("text.settings.reset", "Reset to Defaults"), () -> {
                keybinds.resetToDefaults();
                setup();
                settings.save();
            }).colspan(4).padTop(4).fill();

            stack.add(table);
        }

        content().row();

        content().add(pane).growX().colspan(sections.length);

        pack();
    }

    private void rebind(Section section, KeyBind bind, KeyCode newKey){
        rebindDialog.hide();
        boolean isAxis = bind.defaultValue(section.device.type()) instanceof Axis;

        if(isAxis){
            if(newKey.axis || !rebindMin){
                section.binds.getOr(section.device.type(), OrderedMap::new).put(rebindKey, newKey.axis ? new Axis(newKey) : new Axis(minKey, newKey));
            }
        }else{
            section.binds.getOr(section.device.type(), OrderedMap::new).put(rebindKey, new Axis(newKey));
        }

        if(rebindAxis && isAxis && rebindMin && !newKey.axis){
            rebindMin = false;
            minKey = newKey;
            openDialog(section, rebindKey);
        }else{
            settings.save();
            rebindKey = null;
            rebindAxis = false;
            setup();
        }
    }

    private void openDialog(Section section, KeyBind name){
        rebindDialog = new Dialog(rebindAxis ? bundle.get("keybind.press.axis", "Press an axis or key...") : bundle.get("keybind.press", "Press a key..."), "dialog");

        rebindKey = name;

        rebindDialog.getTitleTable().getCells().first().pad(4);

        if(section.device.type() == DeviceType.keyboard){
            rebindDialog.keyDown(i -> setup());

            rebindDialog.addListener(new InputListener(){
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                    if(Core.app.getType() == ApplicationType.Android) return false;
                    rebind(section, name, button);
                    return false;
                }

                @Override
                public boolean keyDown(InputEvent event, KeyCode keycode){
                    rebindDialog.hide();
                    if(keycode == KeyCode.ESCAPE) return false;
                    rebind(section, name, keycode);
                    return false;
                }

                @Override
                public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY){
                    if(!rebindAxis) return false;
                    rebindDialog.hide();
                    rebind(section, name, KeyCode.SCROLL);
                    return false;
                }
            });
        }

        rebindDialog.show();
        Time.runTask(1f, () -> getScene().setScrollFocus(rebindDialog));
    }

    public static class KeybindDialogStyle extends Style{
        public Color keyColor = Color.WHITE;
        public Color keyNameColor = Color.WHITE;
        public Color controllerColor = Color.WHITE;
        public String paneStyle = "default";

        @Override
        public void read(ReadContext read){
            keyColor = read.color("keyColor");
            keyNameColor = read.color("keyNameColor");
            controllerColor = read.color("controllerColor");
            paneStyle = read.str("paneStyle", "default");
        }
    }
}