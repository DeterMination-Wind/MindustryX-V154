## Patch Name Rule
### V3
> WIP
* Purpose: 
  * O: Optimize performance.
  * FIX: Patch for fix bugs.
  * F: Pure api for mod/plugin, or Fully implemented features(Not include settings and bundles).
  * H: Hook for MindustryX
* Zone:
  * G: Game logic changes, affect both server and client.
  * S: server/headless only change.
  * C: client only change.
  * R: Render/UI changes, implicit only C.
  * API: patch for external usage.
  * BUILD: build time change, not affect runtime.

Can be separated by `.`, and followed by specific scope tags. 
For example: `OH.BUILD(gradle) xxx` `HR(HudFragment) xxx` 

### Old(V2)
* Type
    * O: Optimize performance
    * F: Added Feature
    * API: no effect, pure api for mod/plugin.
    * UI: Added ui element(implicit FC)
    * FIX: Fix bug
* Zone
    * S: server only change.
    * C: client only change.
    * BUILD: build time change, not runtime
* None tag means not game change.
* Can Combine Like `OC` `FS` `FC&UI`