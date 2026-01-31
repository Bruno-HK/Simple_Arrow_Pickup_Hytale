# Arrow Pickup

A small Hytale mod that lets you recover arrows instead of losing them forever.

## What it does

When an arrow is fired and **does not hit a mob**, it will:

- Fly normally  
- Land in the world  
- Turn into a **pickupable arrow item**  
- Stay there **with no time limit**

No more sprinting after arrows before they vanish.

## Why this exists

I originally wanted arrows to stay persistent in the world after landing.  
I haven’t found a clean or official way to do that with the current API, so this mod is a **workaround** that fixes the real issue: arrows disappearing too fast.

It’s not perfect, but it’s reliable and solves the problem in practice.

## Current behavior

- Only applies to arrows that **don’t hit mobs**
- Uses the arrow’s **actual model** to resolve the correct item ID  
  (for example `Weapon_Arrow_Crude`)
- Drops the arrow at the **final landing position**, not at the player
- No pickup time limit

## Limitations

- Arrows are not truly persistent entities
- This is a workaround, not a perfect solution
- API limitations may change in future Hytale versions

If a proper persistent-projectile solution becomes possible, this mod will be updated to use it.

## Installation

1. Download the latest release  
2. Drop the `.jar` file into your `Mods` folder  
3. Start the game

## Notes

This is my **first Java mod** for Hytale.  
If you run into bugs or weird behavior, that’s on me, and I’ll keep improving it.

## License

MIT License.  
Do whatever you want with it, just don’t be a dick, if you use this mod, parts of it,  
please give credit. A mention is more than enough and appreciated.
