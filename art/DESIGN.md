# Fablewind 3D / design and asset provenance

The new runtime renders original glTF 2.0 binary models with a shared material atlas, normals, UVs, metallic/roughness values and emissive surfaces. It does not substitute Canvas drawings for GLB objects. Canvas draws the interface only. All mesh geometry and texture swatches are authored by `create_assets.py` and `tools/make_atlas.py`, with no downloaded film/game models, textures or character assets. The packed editable Blender scene is `fablewind-assets.blend`.

## Visual research

The production interviews below describe a contemporary storybook aesthetic, painterly surfaces, expressive silhouettes, an adventure/western rhythm, and contrast between inviting places and threatening fantasy locations. Fablewind translates those principles into its own architecture, creatures, geography and traveler.

- [Creators Society: Joel Crawford and Januel Mercado](https://www.creatorssociety.net/animation-podcast-blog/joel-crawford-januel-mercado)
- [The Hollywood Reporter: contemporary fairy tale / Nate Wragg and Joel Crawford](https://www.hollywoodreporter.com/movies/movie-features/puss-in-boots-the-last-wish-director-interview-contemporary-fairy-tale-1235282908/)
- [Business Insider: directors on impressionistic fairy-tale painting and darker Grimm influences](https://www.businessinsider.com/puss-in-boots-last-wish-joel-crawford-januel-mercado-animation-2023-2)

## Regional form language

| Region | Architecture | Plants | Wildlife / night creatures | Useful finds |
|---|---|---|---|---|
| Sunlit village | Arched stucco houses, overlapping clay tiles, shutters, inn, market, well | Olive and berry bushes | Rabbits; village sanctuary | Wood, berries, fiber, merchant |
| Rose castle | Five towers, crenellations, pointed roofs, rose arcade manor | Slender cypress and roses | Rabbits / thornlings | Herbs, stone, chests |
| Golden hills | Tapered windmill, timber sails | Flat-canopy acacia, modeled wheat heads | Goats / thornlings | Wheat and seeds |
| Mist marsh | Elevated hut with stilts, deck and steps; lighthouse | Drooping willow branches, reeds | Herons / wisps | Clay, fiber, fish, water |
| Starlit ruins | Broken arch columns, copper-domed observatory and telescope | Crystalline branching trees | Fireflies / stone golems | Ore, crystals, chests |
| Wishing forest | Treehouse with ladder/deck, massive root-buttressed tree with hanging lights | Luminous mushrooms and glow trees | Fireflies / wisps | Mushrooms, herbs, quest altar |
| Amber valley | Rounded pumpkin-roofed cottage with curled stem | Maple, modeled pumpkins | Goats / thornlings | Berries, wood, herbs |

## Production bounds and verification

Asset counts, bounds, part counts and triangle counts are in the generated model manifest. The hero has separately named limbs and tail; the native shader animates them about fixed pivots. Roof tiles, window trim, shutters, door boards, wheel rings, tools, feather vanes and roots are actual geometry. The cannon bore uses a recessed dark interior; no physics simulation or ballistic aiming is claimed.

The renderer targets OpenGL ES 2.0 and uses one atlas to reduce texture switches, a bounded 66×66 terrain window, proximity/frustum culling, shared GPU buffers, smooth terrain normals, ambient/directional/specular lighting, emissive highlights, three nearby point lights, contact shadows, distance haze and animated water highlights. This is an original stylized 3D game, not visual parity with an AAA engine. Contact shadows are approximate; there is no cascaded shadow map, global illumination, skeletal skinning or model LOD pipeline yet.

`tests/GlbTest.java` exercises the exact native GLB decoder against all shipped files. `SmokeInstrumentation` runs on a real Android EGL surface in the CI emulator and captures the seven regions, room, night and menus. Device-specific GPU/performance coverage remains separate from emulator evidence.
