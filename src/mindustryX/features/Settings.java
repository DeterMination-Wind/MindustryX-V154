package mindustryX.features;

import arc.*;
import arc.scene.ui.layout.*;
import mindustry.gen.*;
import mindustry.ui.dialogs.SettingsMenuDialog.*;

import static mindustry.Vars.ui;

public class Settings{
    @SuppressWarnings({"deprecation"})
    public static void addSettings(){
        ui.settings.getCategories().add(new SettingsCategory("@settings.category.settingV2", Icon.box, (c) -> {
        }){
            {
                table = new SettingsTable(){
                    @Override
                    public Table build(){
                        SettingsV2.buildSettingsTable(this);
                        add().width(500).row();
                        return this;
                    }
                };
            }
        });
        ArcOld.addSettings();
    }

    public static void toggle(String name){
        Core.settings.put(name, !Core.settings.getBool(name));
    }

    public static void cycle(String name, int max){
        Core.settings.put(name, (Core.settings.getInt(name) + 1) % max);
    }
}
