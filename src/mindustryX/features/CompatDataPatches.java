package mindustryX.features;

import arc.Events;
import mindustry.game.EventType.ContentPatchLoadEvent;

/**
 * Appends built-in balance patches when connecting to newer protocol servers.
 * This keeps database stats closer to the server-side version without hard-forking core content.
 */
public class CompatDataPatches{
    private static final String v155Patch = """
    {
      \"name\": \"mdtx-compat-v155-balance\",
      \"block\": {
        \"scorch\": {
          \"ammoTypes\": {
            \"pyratite\": {
              \"damage\": 27,
              \"ammoMultiplier\": 10
            }
          }
        },
        \"salvo\": {
          \"reload\": 29,
          \"ammoTypes\": {
            \"copper\": {
              \"damage\": 15,
              \"ammoMultiplier\": 4
            },
            \"graphite\": {
              \"damage\": 31,
              \"reloadMultiplier\": 0.8
            },
            \"pyratite\": {
              \"damage\": 25,
              \"splashDamage\": 15
            },
            \"silicon\": {
              \"damage\": 23
            },
            \"thorium\": {
              \"damage\": 28,
              \"armorMultiplier\": 0.8
            }
          }
        }
      }
    }
    """;

    private CompatDataPatches(){
    }

    public static void init(){
        Events.on(ContentPatchLoadEvent.class, CompatDataPatches::onContentPatchLoad);
    }

    private static void onContentPatchLoad(ContentPatchLoadEvent event){
        if(LogicExt.mockProtocol < 155) return;
        if(!LogicExt.serverDataSwitch0.get()) return;

        // Prepend so server-provided patch data can override these defaults if needed.
        event.patches.insert(0, v155Patch);
    }
}
