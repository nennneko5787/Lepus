# 0009 — Plan

1. Extend the geometry IR to retain bone reset and optional cube mirror declarations.
2. Apply effective mirror at geometry submission and retain per-frame bind-pose evaluation.
3. Parse the limited render-controller texture/array subset needed by attachables, evaluate
   `query.variant`, and resolve the selected texture during drawing.
4. Replace version-specific flat-cube render-layer behavior with one shared rendering strategy.
5. Keep Mojang-owned elytra controllers and material resolution deferred until vanilla pack loading.

The existing static attachable texture remains the fallback for unsupported or malformed controller
constructs. This scope does not claim general render-controller support.

