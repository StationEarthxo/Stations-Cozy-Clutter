# Station's Cozy Clutter

Station's Cozy Clutter is a local RuneLite plugin for placing game scenery as client-side props. Builds are cosmetic: they do not alter the game server, collision, or what players without the same Tilepack see.

Hope you all get to express your amazing creativity and enjoy making Gielinor juuust a little prettier while you wait for your friends between raids :D

## Run locally

This project requires Java 11 or newer.

```powershell
.\gradlew.bat test
.\gradlew.bat run
```

The `run` task starts RuneLite in developer mode with Station's Cozy Clutter loaded.

## Building with Station's Cozy Clutter

1. Enable **Station's Cozy Clutter** in RuneLite's plugin list.
2. Open the coloured-block **Station's Cozy Clutter** button in RuneLite's sidebar.
3. Search for an object's name and browse every match with the page arrows.
4. Click its 3D preview to attach the object to your cursor.
5. Move over the world, use the mouse wheel to rotate the preview, and left-click to place as many copies as you like.
6. Right-click or press **Escape** to leave placement mode.

You can also hold **Shift**, right-click existing scenery, and choose **Copy to Station's Cozy Clutter**. Shift-right-click placement remains available as a fallback workflow.

Shift-right-click a tile containing a placed prop to rotate, raise, lower, resize, duplicate, or delete the newest prop on that tile.

## Tilepacks

From the ground-tile **Station's Cozy Clutter** menu, use **Export Tilepack** to copy a `WB1:` code. Use **Import Tilepack** after copying someone else's code. Imported objects stay anchored to their saved world coordinates.

Tilepacks contain only cosmetic client-side placements. Sharing a Tilepack does not change the game server or make the objects visible to players who have not imported it.

## Current limitations

- Props are visual only and have no collision.
- Static scenery is the main target. Animated or transformed objects may use their static base model.
- Some unusually constructed objects may need a future catalogue entry to reproduce perfectly after restarting.
- Legacy Tilepacks containing unrestricted raw cache-model IDs are skipped because some raw models can crash RuneLite. Named catalogue objects remain supported.
- Animated, transformed, extended-ID, malformed, and known-incompatible models are excluded from the public catalogue.
- New object types are safety-probed for five healthy game ticks. If RuneLite is interrupted during activation, that object is automatically quarantined next launch to prevent a crash loop.
