package mindustryX.features;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.blocks.*;
import mindustryX.events.*;
import mindustryX.features.SettingsV2.*;

import static arc.util.Tmp.*;

/**
 * 玩家子弹伤害跳字
 * Create by 2024/6/5
 */
public class DamagePopup{
    private static final ObjectMap<Sized, Popup> damagePopups = new ObjectMap<>();
    private static final ObjectMap<Sized, Popup> healPopups = new ObjectMap<>();

    // 跳字初始缩放限制
    public static final float minScale = 1f / 4 / Scl.scl(1);
    public static final float maxScale = 1f / 2 / Scl.scl(1);

    // 无持续攻击的消退时间
    public static float popupLifetime = 60f;

    // 设置
    private static final SettingsV2.CheckPref
    enable = new CheckPref("damagePopup.enable"),
    playerOnly = new CheckPref("damagePopup.playerOnly", true),
    healPopup = new CheckPref("damagePopup.showHeal");
    private static final SliderPref minHealth = new SliderPref("damagePopup.minHealth", 600, 0, 4000, 50, v -> v + "[red]HP");

    static{
        enable.addFallbackName("damagePopup");
        playerOnly.addFallbackName("playerPopupOnly");
        healPopup.addFallbackName("healPopup");
        minHealth.addFallbackName("popupMinHealth");
    }

    public static void init(){
        Events.on(HealthChangedEvent.class, DamagePopup::handleEvent);

        Events.on(ResetEvent.class, e -> {
            damagePopups.clear();
            healPopups.clear();
        });
    }

    private static void handleEvent(HealthChangedEvent event){
        if(!enable.get()) return;
        if(event.entity.maxHealth() < minHealth.get()) return;
        if(event.amount < 0 && !healPopup.get()) return;
        if(event.source != null){// 视角外的跳字
            Rect cameraBounds = Core.camera.bounds(r1).grow(4 * Vars.tilesize);
            if(!cameraBounds.contains(event.entity.getX(), event.entity.getY())) return;
            if(event.source != null && playerOnly.get() && !inControl(getOwner(event.source))) return;
        }

        if(event.entity instanceof Sized entitySized)
            popup(event.source instanceof Sized sized ? sized : null, entitySized, event.amount, event.isSplash);
    }

    private static @Nullable Entityc getOwner(Entityc source){
        Entityc current = source;
        while(current instanceof Ownerc o){
            current = o.owner();
            //Lightning create Bullet(owner=null,data=hitter)
            if(current == null && o instanceof Bulletc o2 && o2.data() instanceof Bulletc bullet){
                current = bullet;
            }
        }
        return current;
    }

    private static boolean inControl(Entityc entity){
        if(entity instanceof Unit u && (u.isLocal() || Vars.control.input.selectedUnits.contains(u))) return true;
        if(entity instanceof ControlBlock b && b.unit().isLocal()) return true;
        return false;
    }

    private static void popup(@Nullable Sized source, Sized damaged, float amount, boolean isSplash){
        if(Mathf.equal(amount, 0f)) return;

        float hitSize = damaged.hitSize();
        float rotation = source != null ? damaged.angleTo(source) + Mathf.random(35f) : 90 + Mathf.range(35f);
        float scale = Mathf.clamp(hitSize / 64 / Scl.scl(1), minScale, maxScale);
        float offsetLength = hitSize * Mathf.random(0.4f, 0.7f);

        if(!isSplash){
            float offsetX = Angles.trnsx(rotation, hitSize * Mathf.random(0.2f, 0.4f));
            float offsetY = Angles.trnsy(rotation, hitSize * Mathf.random(0.2f, 0.4f));
            if(amount >= 0){
                Popup data = damagePopups.get(damaged);
                if(data != null){
                    data.superposeAmount(amount);
                }else{
                    data = Popup.create();
                    damagePopups.put(damaged, data);
                    data.set(damaged, damagePopups, "", Pal.health, offsetX, offsetY, popupLifetime, amount, 1f, scale, rotation, offsetLength).add();
                }
            }else{
                Popup data = healPopups.get(damaged);
                if(data != null){
                    data.superposeAmount(-amount);
                }else{
                    data = Popup.create();
                    healPopups.put(damaged, data);
                    data.set(damaged, healPopups, "", Pal.heal, offsetX, offsetY, popupLifetime, -amount, 1f, scale, rotation, offsetLength).add();
                }
            }
        }else{
            float offsetX = Angles.trnsx(rotation, hitSize * Mathf.random(0.3f, 0.4f));
            float offsetY = Angles.trnsy(rotation, hitSize * Mathf.random(0.3f, 0.4f));
            scale *= 0.65f; // 堆叠的跳字有更大的效果

            Popup.create().set(damaged, null, StatusEffects.blasted.emoji(), StatusEffects.blasted.color, offsetX, offsetY, popupLifetime, Math.abs(amount), 1f, scale, rotation, offsetLength).add();
        }
    }

    private static class Popup extends Decal{
        public static float maxAmountEffect = 5_000;
        public static int maxCountEffect = 50;
        public static float amountEffect = 3f;
        public static float countEffect = 2f;
        public static float fontScaleEffectScl = 8f;
        public static float splashTime = 15f;

        // data
        public Font font = Fonts.outline;
        public Sized damaged;
        public @Nullable ObjectMap<Sized, Popup> superposeMap;
        public String icon;
        public float alpha;
        public float scale;
        public float offsetX, offsetY, offsetLength;

        public float amount;
        public int count;

        private float floatTimer;
        private float splashTimer;

        public Popup set(Sized damaged, @Nullable ObjectMap<Sized, Popup> superposeMap, String icon, Color color, float offsetX, float offsetY, float lifetime, float amount, float alpha, float scale, float rotation, float offsetLength){
            this.damaged = damaged;
            this.superposeMap = superposeMap;
            this.color.set(color);
            this.icon = icon;

            set(damaged.getX() + offsetX, damaged.getY() + offsetY);
            this.offsetX = superposeMap != null ? offsetX : x;
            this.offsetY = superposeMap != null ? offsetY : y;
            this.offsetLength = offsetLength;

            this.lifetime = lifetime;
            this.amount = amount;
            this.alpha = alpha;
            this.scale = scale;
            this.rotation = rotation;

            return this;
        }

        @Override
        public float clipSize(){
            return 40f;
        }

        public void draw(){
            if(RenderExt.unitHide.get() && damaged instanceof Unit) return;
            float alphaScaleEase = Bezier.quadratic(v1, fin(),
            v2.set(1f, 1f),
            v3.set(0f, 1f),
            v4.set(0f, 0.8f),
            v5.set(0f, 0.5f)).y;

            float alpha = this.alpha * alphaScaleEase;
            float scale = this.scale * alphaScaleEase * Math.max(effect() / fontScaleEffectScl, 1);

            Draw.z(Layer.overlayUI);
            c1.set(color).a(alpha).lerp(Color.white, splashTimer / splashTime * 0.75f);
            String text = icon + Strings.autoFixed(amount, 1);
            font.draw(text, x, y, color, scale, false, Align.center);
        }

        public void update(){
            if(superposeMap != null){
                x = damaged.getX() + offsetX;
                y = damaged.getY() + offsetY;
            }else{
                float positionEase = Bezier.quadratic(v1, fin(),
                v2.set(0f, 0f),
                v3.set(0.19f, 1f),
                v4.set(0.22f, 1f),
                v5.set(1f, 1f)).y;

                float offsetLength = this.offsetLength * positionEase;
                x = offsetX + Angles.trnsx(rotation, offsetLength);
                y = offsetY + Angles.trnsy(rotation, offsetLength);
            }

            if(floatTimer > 0){
                floatTimer = Math.max(0, floatTimer - Time.delta);
            }else{
                super.update();
            }

            if(splashTimer > 0){
                splashTimer = Math.max(0, splashTimer - Time.delta);
            }
        }

        protected float effect(){
            float damageEffect = Popup.amountEffect * Math.min(amount / maxAmountEffect, 1);
            float countEffect = Popup.countEffect * Math.min(count / maxCountEffect, 1);
            return 1f + damageEffect + countEffect;
        }

        public void superposeAmount(float amount){
            this.amount += amount;
            count++;

            floatTimer = lifetime;
            splashTimer = splashTime;
        }

        public static Popup create(){
            return Pools.obtain(Popup.class, Popup::new);
        }


        @Override
        public void remove(){
            if(damaged != null && superposeMap != null){
                superposeMap.remove(damaged);
            }
            super.remove();
        }

        @Override
        public void reset(){
            super.reset();
            damaged = null;
            superposeMap = null;
            icon = null;

            alpha = 0;
            scale = 0;
            offsetX = offsetY = offsetLength = 0;

            amount = 0f;
            count = 0;
            floatTimer = splashTimer = 0;
        }
    }
}
