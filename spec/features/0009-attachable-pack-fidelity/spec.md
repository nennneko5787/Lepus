# 0009 — Attachable pack fidelity

## Scope

Improve the rendering path used by the Hoshino piggyback attachable: preserve geometry reset and
mirror declarations, select the texture named by its render controller, and render zero-thickness
cubes consistently on supported Minecraft versions.

The Mojang elytra animation controller and Bedrock material names remain dependent on loading the
vanilla resource and behavior packs. This feature does not emulate either one. `hold_on_last_frame`
is unchanged.

## Behavior

- Geometry bones preserve the `reset` flag. Each rendered pose starts from the geometry bind pose,
  then applies active animations; resetting a bone must not accumulate prior-frame animation.
- Bone `mirror` is inherited by cubes. A cube's explicit `mirror` value overrides the bone value,
  including explicit `false`.
- An attachable render controller may choose its texture from a declared array using a Molang
  expression. Missing or invalid controller data falls back to the attachable's default texture.
- Zero-thickness cube faces render without depth fighting on both supported Minecraft versions.

