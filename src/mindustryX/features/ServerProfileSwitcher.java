package mindustryX.features;

import arc.Core;
import arc.Events;
import arc.struct.ObjectSet;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.core.Version;
import mindustry.game.EventType;
import mindustry.mod.Mods.LoadedMod;
import mindustry.net.Packets.ConnectPacket;

/**
 * Switches mod profiles when joining different protocol versions.
 *
 * Cross-version changes require a reload in Mindustry, so this helper stores
 * and restores per-version enabled-mod sets, then auto-reconnects once reloaded.
 */
public class ServerProfileSwitcher{
    private static final String keyActiveProfile = "mdtx.profile.active";
    private static final String keyProfile154 = "mdtx.profile.mods.154";
    private static final String keyProfile155 = "mdtx.profile.mods.155";
    private static final String keyAutoJoinIp = "mdtx.autojoin.ip";
    private static final String keyAutoJoinPort = "mdtx.autojoin.port";
    private static final String keyAutoJoinVersion = "mdtx.autojoin.version";

    private static final int profile154 = 154;
    private static final int profile155 = 155;

    private ServerProfileSwitcher(){
    }

    public static void init(){
        if(Vars.headless) return;
        Events.on(EventType.ClientLoadEvent.class, e -> Core.app.post(ServerProfileSwitcher::autoJoinIfNeeded));
    }

    /** @return true when a reload has been triggered. */
    public static boolean prepareAndMaybeRestart(String ip, int port, int version){
        if(Vars.headless || Vars.mods == null) return false;

        int targetProfile = profileForVersion(version);
        int activeProfile = Core.settings.getInt(keyActiveProfile, profileForVersion(Version.build));

        storeProfile(activeProfile);
        applyProfile(targetProfile, version);
        Core.settings.put(keyActiveProfile, targetProfile);

        if(Vars.mods.requiresReload()){
            Core.settings.put(keyAutoJoinIp, ip);
            Core.settings.put(keyAutoJoinPort, port);
            Core.settings.put(keyAutoJoinVersion, version);
            Vars.mods.reload();
            return true;
        }
        return false;
    }

    private static void autoJoinIfNeeded(){
        String ip = Core.settings.getString(keyAutoJoinIp, "");
        if(ip.isEmpty()) return;

        int port = Core.settings.getInt(keyAutoJoinPort, Vars.port);
        int version = Core.settings.getInt(keyAutoJoinVersion, Version.build);

        Core.settings.remove(keyAutoJoinIp);
        Core.settings.remove(keyAutoJoinPort);
        Core.settings.remove(keyAutoJoinVersion);

        Log.infoTag("MindustryX", "Auto join " + ip + ":" + port + " with protocol " + version);
        ConnectPacket.clientVersion = version;
        Vars.ui.join.connect(ip, port);
    }

    private static void applyProfile(int profile, int targetVersion){
        String key = profileKey(profile);
        if(Core.settings.has(key)){
            applyEnabledSet(Core.settings.getString(key, ""));
        }else if(profile == profile155){
            applySafeDefaultsFor155(targetVersion);
        }
        storeProfile(profile);
    }

    private static void applyEnabledSet(String serialized){
        ObjectSet<String> enabledNames = new ObjectSet<>();
        if(serialized != null && !serialized.isEmpty()){
            for(String token : serialized.split("\\|")){
                if(!token.isEmpty()) enabledNames.add(token);
            }
        }

        for(LoadedMod mod : Vars.mods.list()){
            if(!trackable(mod)) continue;
            Vars.mods.setEnabled(mod, enabledNames.contains(mod.name));
        }
    }

    /**
     * First-time 155 profile defaults:
     * - keep already disabled state
     * - disable unsupported mods
     * - disable mods that ship scripts/content
     * - disable mods explicitly marked for another game build
     */
    private static void applySafeDefaultsFor155(int targetVersion){
        for(LoadedMod mod : Vars.mods.list()){
            if(!trackable(mod) || !mod.enabled()) continue;

            boolean keep = true;
            if(!mod.isSupported()) keep = false;
            if(mod.root != null && (mod.root.child("scripts").exists() || mod.root.child("content").exists())) keep = false;

            int targetMajor = profileForVersion(targetVersion);
            int modMajor = parseMajor(mod.meta.minGameVersion);
            if(modMajor != -1 && modMajor != targetMajor) keep = false;

            Vars.mods.setEnabled(mod, keep);
        }
    }

    private static boolean trackable(LoadedMod mod){
        if(mod == null || mod.meta == null) return false;
        return !mod.meta.hidden;
    }

    private static void storeProfile(int profile){
        StringBuilder builder = new StringBuilder();
        for(LoadedMod mod : Vars.mods.list()){
            if(!trackable(mod) || !mod.enabled()) continue;
            if(builder.length() > 0) builder.append('|');
            builder.append(mod.name);
        }
        Core.settings.put(profileKey(profile), builder.toString());
    }

    private static String profileKey(int profile){
        return profile >= profile155 ? keyProfile155 : keyProfile154;
    }

    private static int profileForVersion(int version){
        return version >= profile155 ? profile155 : profile154;
    }

    private static int parseMajor(String build){
        if(build == null || build.isEmpty()) return -1;
        int dot = build.indexOf('.');
        String raw = dot == -1 ? build : build.substring(0, dot);
        return Strings.parseInt(raw, -1);
    }
}
