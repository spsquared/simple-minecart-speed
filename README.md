# SP^2 Minecart Speeds

A very simple mod that adds a way to make minecarts not useless for transportation. (It's not actually that simple, I rewrote the whole minecart movement code for rideable minecarts)

### Features
* Minecarts go really fast (only rideable ones and furnace minecarts)
* Minecarts preserve their velocity when going off rails
* Ground drag (when the minecart rolls on the ground) is different for pickaxe mineables, axe mineables, ice, and air.
* Fixes minecarts randomly flying off rails when going down and colliding with the ground when going up at high speeds

### TODO:
* Preserve vertical velocity when leaving rails
* Add "trimmed" rails crafted using normal rails and some block to change the max speed (texture will likely have a thin strip of color like powered/activator rails)
* Moving chest/furnace/hopper minecarts or minecarts with passengers forceload chunks
* Make detector and activator rails work at high speeds