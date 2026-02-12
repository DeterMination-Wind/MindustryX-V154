package mindustry.editor;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.TextField.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.io.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustryX.features.*;
import mindustryX.features.ArcWaveSpawner.*;
import mindustryX.features.ui.*;

import static mindustry.Vars.*;
import static mindustry.game.SpawnGroup.*;

public class WaveInfoDialog extends BaseDialog{
    Seq<SpawnGroup> groups = new Seq<>();
    private @Nullable SpawnGroup expandedGroup;

    private Table table;
    private int search = -1;
    private @Nullable UnitType filterType;
    private Sort sort = Sort.begin;
    private boolean reverseSort = false;
    private boolean checkedSpawns;
    private WaveGraph graph = new WaveGraph();

    //Arc extended;
    private int winWave;
    private boolean wavesListMode;
    private Cell<?> wavesListCell;
    private Table wavesList;
    private Element wavesListPane;

    public WaveInfoDialog(){
        super("@waves.title");

        shown(() -> {
            checkedSpawns = false;
            setup();
        });
        hidden(() -> state.rules.spawns = groups);

        addCloseButton();

        buttons.button("@waves.edit", Icon.edit, () -> {
            BaseDialog dialog = new BaseDialog("@waves.edit");
            dialog.addCloseButton();
            dialog.setFillParent(false);
            dialog.cont.table(Tex.button, t -> {
                var style = Styles.cleart;
                t.defaults().size(280f, 64f).pad(2f);

                t.button("@waves.copy", Icon.copy, style, () -> {
                    ui.showInfoFade("@waves.copied");
                    Core.app.setClipboardText(maps.writeWaves(groups));
                    dialog.hide();
                }).disabled(b -> groups == null || groups.isEmpty()).marginLeft(12f).row();

                t.button("@waves.load", Icon.download, style, () -> {
                    try{
                        groups = maps.readWaves(Core.app.getClipboardText());
                        buildGroups();
                    }catch(Exception e){
                        Log.err(e);
                        ui.showErrorMessage("@waves.invalid");
                    }
                    dialog.hide();
                }).disabled(Core.app.getClipboardText() == null || !Core.app.getClipboardText().startsWith("[")).marginLeft(12f).row();

                t.button("@clear", Icon.none, style, () -> ui.showConfirm("@confirm", "@settings.clear.confirm", () -> {
                    groups.clear();
                    buildGroups();
                    dialog.hide();
                })).marginLeft(12f).row();

                t.button("@settings.reset", Icon.refresh, style, () -> ui.showConfirm("@confirm", "@settings.clear.confirm", () -> {
                    groups = JsonIO.copy(waves.get());
                    buildGroups();
                    dialog.hide();
                })).marginLeft(12f);
            });

            dialog.show();
        }).size(250f, 64f);

        buttons.button("切换显示模式", () -> {
            wavesListMode = !wavesListMode;
            wavesListCell.setElement(wavesListMode ? wavesListPane : graph);
        }).width(200f);

        buttons.button(Core.bundle.get("waves.random"), Icon.refresh, () -> {
            groups.clear();
            groups = Waves.generate(1f / 10f);
            buildGroups();
        }).width(200f);
    }

    void setup(){
        groups = JsonIO.copy(state.rules.spawns.isEmpty() ? waves.get() : state.rules.spawns);
        if(groups == null) groups = new Seq<>();

        cont.clear();
        cont.stack(new Table(Tex.clear, main -> {
            main.table(s -> {
                s.image(Icon.zoom).padRight(8);
                s.field(search < 0 ? "" : (search + 1) + "", TextFieldFilter.digitsOnly, text -> {
                    search = groups.any() ? Strings.parseInt(text, 0) - 1 : -1;
                    buildGroups();
                }).growX().maxTextLength(8).get().setMessageText("@waves.search");
                s.button(Icon.units, Styles.emptyi, () -> showUnits(type -> filterType = type, true)).size(46f).tooltip("@waves.filter")
                .update(b -> b.getStyle().imageUp = filterType != null ? new TextureRegionDrawable(filterType.uiIcon) : Icon.filter);
            }).growX().pad(6f).row();

            main.pane(t -> table = t).grow().padRight(8f).scrollX(false).row();

            main.table(t -> {
                t.button("@add", () -> {
                    showUnits(type -> groups.add(expandedGroup = new SpawnGroup(type)), false);
                    buildGroups();
                }).growX().height(70f);

                t.button(Icon.filter, () -> {
                    BaseDialog dialog = new BaseDialog("@waves.sort");
                    dialog.setFillParent(false);
                    dialog.cont.table(Tex.button, f -> {
                        for(Sort s : Sort.all){
                            f.button("@waves.sort." + s, Styles.flatTogglet, () -> {
                                sort = s;
                                dialog.hide();
                                buildGroups();
                            }).size(150f, 60f).checked(s == sort);
                        }
                    }).row();
                    dialog.cont.check("@waves.sort.reverse", b -> {
                        reverseSort = b;
                        buildGroups();
                    }).padTop(4).checked(reverseSort).padBottom(8f);
                    dialog.addCloseButton();
                    dialog.show();
                }).size(64f, 70f).padLeft(6f);
            }).growX();

        }), new Label("@waves.none"){{
            visible(() -> groups.isEmpty());
            this.touchable = Touchable.disabled;
            setWrap(true);
            setAlignment(Align.center, Align.center);
        }}).width(390f).growY();

        graph = new WaveGraph();
        wavesList = new Table();
        winWave = -1;

        var cell = wavesListCell = cont.pane(wavesList).scrollX(false).grow();
        wavesListPane = cell.get();
        if(!wavesListMode) wavesListCell.setElement(graph);

        buildGroups();
    }

    void buildWavesList(){
        if(winWave < 0){
            winWave = Math.min(ArcWaveSpawner.calWinWave(), 200);//default 200 max
        }
        int maxWaves = ArcWaveSpawner.calWinWaveClamped();
        winWave = Math.min(winWave, maxWaves);

        wavesList.clearChildren();
        wavesList.margin(0).defaults().pad(5).growX();
        wavesList.table(Tex.button, t -> t.add("\uE86D 为单位数量；\uE813 为单位血+盾；\uE810 为计算buff的血+盾；\uE86E 为预估DPS。在游戏中时会考虑地图出怪点数目").color(Pal.accent)).scrollX(false).growX().row();
        float firstWaveTime = state.rules.initialWaveSpacing <= 0 ? (2 * state.rules.waveSpacing) : state.rules.initialWaveSpacing;
        int winWave = this.winWave;
        for(int waveI = 0; waveI < winWave; waveI++){
            WaveInfo wave = ArcWaveSpawner.getOrInit(waveI);
            wavesList.table(Tex.button, t -> {
                t.table(tt -> {
                    tt.add("第[accent]" + (wave.wave + 1) + "[]波").row();
                    int thisTime = (int)(wave.wave * state.rules.waveSpacing + firstWaveTime);
                    tt.add(FormatDefault.duration(thisTime / 60f, false)).row();
                    tt.label(() -> {
                        if(!state.isGame()) return "";
                        int deltaTime = thisTime - (int)(state.wave <= 1 ? (firstWaveTime - state.wavetime) : (firstWaveTime + state.rules.waveSpacing * (state.wave - 1) - state.wavetime));
                        return FormatDefault.duration(deltaTime / 60f, false);
                    }).row();
                }).width(120f).left();
                if(wave.amount == 0){
                    t.add();
                    t.add("该波次没有敌人");
                }else{
                    t.add(wave.proTable(true, -1, group -> true));
                    t.pane(wave.unitTable(-1, group -> true, mobile ? 8 : 15)).scrollX(true).scrollY(false).growX();
                }
            }).growX().row();
        }
        wavesList.button("加载更多波次", () -> {
            this.winWave *= 2;
            buildWavesList();
        }).disabled(winWave == maxWaves).fillX().height(32f);
    }

    void buildGroups(){
        table.clear();
        table.top();
        table.margin(10f);

        if(groups != null){
            groups.sort(Structs.comps(Structs.comparingFloat(sort.sort), Structs.comparingFloat(sort.secondary)));
            if(reverseSort) groups.reverse();

            for(SpawnGroup group : groups){
                if(group.effect == StatusEffects.none) group.effect = null;
                if((search >= 0 && group.getSpawned(search) <= 0) || (filterType != null && group.type != filterType)) continue;

                table.table(Tex.button, t -> {
                    t.margin(0).defaults().pad(3).padLeft(5f).growX().left();
                    t.button(b -> {
                        b.left();
                        b.image(group.type.uiIcon).size(32f).padRight(3).scaling(Scaling.fit);
                        b.add(group.type.localizedName).ellipsis(true).maxWidth(110f).left().color(ArcWaveSpawner.unitTypeColor(group.type));
                        if(group.items != null && group.items.amount > 0)
                            b.image(group.items.item.uiIcon).size(20f).padRight(3).scaling(Scaling.fit);
                        if(group.payloads != null && group.payloads.size > 0)
                            b.image(Icon.uploadSmall).size(20f).padRight(3).scaling(Scaling.fit);

                        b.add().growX();

                        b.label(() -> {
                            StringBuilder builder = new StringBuilder();
                            builder.append("[lightgray]").append(group.begin + 1);
                            if(group.begin == group.end) return builder.toString();
                            if(group.end > 999999) builder.append("+");
                            else builder.append("~").append(group.end + 1);
                            if(group.spacing > 1) builder.append("[white]|[lightgray]").append(group.spacing);
                            return builder.append("  ").toString();
                        }).minWidth(45f).labelAlign(Align.left).left();
                        b.button(Icon.copySmall, Styles.emptyi, () -> {
                            groups.insert(groups.indexOf(group) + 1, expandedGroup = group.copy());
                            buildGroups();
                        }).pad(-6).size(46f).tooltip("@editor.copy");

                        b.button(group.effect != null ?
                        new TextureRegionDrawable(group.effect.uiIcon) :
                        Icon.effectSmall,
                        Styles.emptyi, iconSmall, () -> showEffects(group)).pad(-6).size(46f).scaling(Scaling.fit).tooltip(group.effect != null ? group.effect.localizedName : "@none");

                        b.button(Icon.unitsSmall, Styles.emptyi, () -> showUnits(type -> group.type = type, false)).pad(-6).size(46f).tooltip("@stat.unittype");
                        b.button(Icon.cancel, Styles.emptyi, () -> {
                            groups.remove(group);
                            if(expandedGroup == group) expandedGroup = null;
                            table.getCell(t).pad(0f);
                            t.remove();
                            buildGroups();
                        }).pad(-6).size(46f).padRight(-12f).tooltip("@waves.remove");
                        b.clicked(KeyCode.mouseMiddle, () -> {
                            groups.insert(groups.indexOf(group) + 1, expandedGroup = group.copy());
                            buildGroups();
                        });
                    }, () -> {
                        expandedGroup = expandedGroup == group ? null : group;
                        buildGroups();
                    }).height(46f).pad(-6f).padBottom(0f).row();

                    if(expandedGroup == group){
                        t.table(spawns -> {
                            spawns.field("" + (group.begin + 1), TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.begin = Strings.parseInt(text) - 1;
                                    updateWaves();
                                }
                            }).width(100f);
                            spawns.add("@waves.to").padLeft(4).padRight(4);
                            spawns.field(group.end == never ? "" : (group.end + 1) + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.end = Strings.parseInt(text) - 1;
                                    updateWaves();
                                }else if(text.isEmpty()){
                                    group.end = never;
                                    updateWaves();
                                }
                            }).width(100f).get().setMessageText("∞");
                        }).row();

                        t.table(p -> {
                            p.add("@waves.every").padRight(4);
                            p.field(group.spacing + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text) && Strings.parseInt(text) > 0){
                                    group.spacing = Strings.parseInt(text);
                                    updateWaves();
                                }
                            }).width(100f);
                            p.add("@waves.waves").padLeft(4);
                        }).row();

                        t.table(a -> {
                            a.field(group.unitAmount + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.unitAmount = Strings.parseInt(text);
                                    updateWaves();
                                }
                            }).width(80f);

                            a.add(" + ");
                            a.field(Strings.fixed(Math.max((Mathf.zero(group.unitScaling) ? 0 : 1f / group.unitScaling), 0), 2), TextFieldFilter.floatsOnly, text -> {
                                if(Strings.canParsePositiveFloat(text)){
                                    group.unitScaling = 1f / Strings.parseFloat(text);
                                    updateWaves();
                                }
                            }).width(80f);
                            a.add("@waves.perspawn").padLeft(4);
                        }).row();

                        t.table(a -> {
                            a.field(group.max + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.max = Strings.parseInt(text);
                                    updateWaves();
                                }
                            }).width(80f);

                            a.add("@waves.max").padLeft(5);
                        }).row();

                        t.table(a -> {
                            a.field((int)group.shields + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.shields = Strings.parseInt(text);
                                    updateWaves();
                                }
                            }).width(80f);

                            a.add(" + ");
                            a.field((int)group.shieldScaling + "", TextFieldFilter.digitsOnly, text -> {
                                if(Strings.canParsePositiveInt(text)){
                                    group.shieldScaling = Strings.parseInt(text);
                                    updateWaves();
                                }
                            }).width(80f);
                            a.add("@waves.shields").padLeft(4);
                        }).row();

                        t.check("@waves.guardian", b -> {
                            group.effect = (b ? StatusEffects.boss : null);
                            buildGroups();
                        }).padTop(4).update(b -> b.setChecked(group.effect == StatusEffects.boss)).padBottom(8f).row();

                        t.table(a -> {
                            a.add("@waves.team").padRight(8);

                            a.button(b -> b.image(Tex.whiteui).size(iconSmall).update(i -> i.setColor(group.team == null ? Color.clear : group.team.color)), Styles.squarei,
                            () -> MapObjectivesDialog.showTeamSelect(true, team -> group.team = team)).size(38f);
                        }).padTop(0).row();

                        t.table(a -> {
                            a.add("@waves.spawn").padRight(8);

                            a.button("", () -> {
                                if(!checkedSpawns){
                                    //recalculate waves when changed
                                    Vars.spawner.reset();
                                    checkedSpawns = true;
                                }

                                BaseDialog dialog = new BaseDialog("@waves.spawn.select");
                                dialog.cont.pane(p -> {
                                    p.background(Tex.button).margin(10f);
                                    int i = 0;
                                    int cols = 4;
                                    int max = 20;

                                    if(spawner.getSpawns().size >= max){
                                        p.add("[lightgray](first " + max + ")").colspan(cols).padBottom(4).row();
                                    }

                                    for(var spawn : spawner.getSpawns()){
                                        p.button(spawn.x + ", " + spawn.y, Styles.flatTogglet, () -> {
                                            group.spawn = Point2.pack(spawn.x, spawn.y);
                                            dialog.hide();
                                        }).size(110f, 45f).checked(spawn.pos() == group.spawn);

                                        if(++i % cols == 0){
                                            p.row();
                                        }

                                        //only display first 20 spawns, you don't need to see more.
                                        if(i >= 20){
                                            break;
                                        }
                                    }

                                    if(spawner.getSpawns().isEmpty()){
                                        p.add("@waves.spawn.none");
                                    }else{
                                        p.button("@waves.spawn.all", Styles.flatTogglet, () -> {
                                            group.spawn = -1;
                                            dialog.hide();
                                        }).size(110f, 45f).checked(-1 == group.spawn);
                                    }
                                }).grow();
                                dialog.setFillParent(false);
                                dialog.addCloseButton();
                                dialog.show();
                            }).width(160f).height(36f).get().getLabel().setText(() -> group.spawn == -1 ? "@waves.spawn.all" : Point2.x(group.spawn) + ", " + Point2.y(group.spawn));

                        }).row();

                        t.table(a -> {
                            a.defaults().pad(2);
                            a.add("携带物品[gold]X[]: ");

                            a.button((group.items != null ? new TextureRegionDrawable(group.items.item.uiIcon) : Icon.noneSmall), Styles.emptyi, iconSmall, () -> {
                                if(group.type.itemCapacity <= 0){
                                    UIExt.announce("[red]该单位不可携带物品");
                                    return;
                                }
                                var dialog = new ContentSelectDialog();
                                dialog.addNull(() -> {
                                    group.items = null;
                                    buildGroups();
                                    dialog.hide();
                                }).setChecked(group.items == null);
                                dialog.addContents(content.items(), group.items != null ? group.items.item : null, (it) -> {
                                    if(group.items == null)
                                        group.items = new ItemStack(it, 1);
                                    group.items.item = it;
                                    dialog.hide();
                                    buildGroups();
                                });
                                dialog.show();
                            });
                            if(group.items != null){
                                a.label(() -> "x" + group.items.amount);

                                a.slider(1, group.type.itemCapacity, 1, (it) -> {
                                    group.items.amount = (int)it;
                                }).padLeft(8).growX();
                            }else{
                                a.add().growX();
                            }
                        }).margin(8).row();

                        t.table(a -> {
                            a.defaults().pad(2);
                            a.add("携带载荷[gold]X[]: ");

                            if(group.payloads != null)
                                for(var it : group.payloads){
                                    a.button(new TextureRegionDrawable(it.uiIcon), Styles.emptyi, iconSmall, () -> group.payloads.remove(it));
                                }

                            a.add().growX();
                            a.button(Icon.addSmall, Styles.emptyi, iconSmall, () -> showUnits((type) -> {
                                if(group.type.payloadCapacity <= 0){
                                    UIExt.announce("[red]该单位不可携带载荷");
                                    return;
                                }
                                if(group.payloads == null) group.payloads = Seq.with();
                                group.payloads.add(type);
                            }, false));
                        }).margin(8).row();
                    }
                }).width(340f).pad(8);

                table.row();
            }

            if(table.getChildren().isEmpty() && groups.any()){
                table.add("@none.found");
            }
        }else{
            table.add("@editor.default");
        }

        updateWaves();
    }

    void showUnits(Cons<UnitType> cons, boolean reset){
        var dialog = new ContentSelectDialog();
        dialog.title.setText(reset ? "@waves.filter" : "");
        if(reset){
            dialog.addNull(() -> {
                cons.get(null);
                dialog.hide();
                buildGroups();
            });
        }
        dialog.addContents(content.units().select((type) -> !type.isHidden() || mindustryX.VarsX.allUnlocked.get()), null, (type) -> {
            cons.get(type);
            dialog.hide();
            buildGroups();
        });
        dialog.show();
    }

    void showEffects(SpawnGroup group){
        var dialog = new ContentSelectDialog();
        dialog.addNull(() -> {
            group.effect = null;
            dialog.hide();
            buildGroups();
        });
        dialog.addContents(content.statusEffects().select((effect) -> (!effect.isHidden() && !effect.reactive) || mindustryX.VarsX.allUnlocked.get()), null, (type) -> {
            group.effect = type;
            dialog.hide();
            buildGroups();
        });
        dialog.show();
    }

    enum Sort{
        begin(g -> g.begin, g -> g.type.id),
        health(g -> g.type.health),
        type(g -> g.type.id);

        static final Sort[] all = values();

        final Floatf<SpawnGroup> sort, secondary;

        Sort(Floatf<SpawnGroup> sort){
            this(sort, g -> g.begin);
        }

        Sort(Floatf<SpawnGroup> sort, Floatf<SpawnGroup> secondary){
            this.sort = sort;
            this.secondary = secondary;
        }
    }

    void updateWaves(){
        graph.groups = groups;
        graph.rebuild();
        ArcWaveSpawner.reload(groups);
        buildWavesList();
    }
}