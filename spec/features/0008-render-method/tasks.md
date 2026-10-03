# 0008 — タスク

## 実測（済）

- [x] トロフィーのブロックは `"render_method": "alpha_test"` を宣言している
      （`blocks/kivotos/trophy/1.binah_trophy.json`）
- [x] そのテクスチャは PNG colour type 6（RGBA）。失うべき透明 texel がある
- [x] `grep -rn "render_method\|renderMethod" src/ core/` は **0 件**。読まれていない
- [x] alpha を捨てるのはシェーダではなく **define**。`terrain.fsh` と `block.fsh` の両方が
      `#ifdef ALPHA_CUTOUT` → `discard`。どちらも自力では discard しない
- [x] 1.21.11 の `CoreShaders` は **`pipeline/solid_block`（define なし）** と
      **`pipeline/cutout_block`（`ALPHA_CUTOUT = 0.5f`）** を別々に組み立てている。
      `solid_terrain` / `cutout_terrain` も同じ
- [x] entity 側も分裂している。`entity_solid` は define なし、`entity_cutout` /
      `entity_cutout_no_cull` / `entity_cutout_no_cull_z_offset` はあり
- [x] **手に持ったとき正常、置くと壊れる** という非対称が `alpha_test` の経路差の署名である

## 原因として却下したもの

- [x] **§5.3.1（厚みゼロの cube）** — ハローの天板は `size: [24, 0, 15]` で退化しているが、これは
      登録が 1 つ原因ではなく、**症状側が撤回済み**
- [x] **§5.3.2（範囲外 box UV）** — 実際に描く `up` / `down` の `u` は 0..12 で範囲内。
      `u < 0` を持つのは面積ゼロの 4 面だけ

## 実装（まだ）

- [x] `BlockModels.Materials` が `render_method` を読む。`RenderMethod` enum（5 名）と
      `layerOf` で 1 つの答えへ畳む。alias は texture だけでなく **method も継承**する
- [x] `BlockModelsTest` に 7 件。実パックの `alpha_test` 宣言そのままを fixture にしている
- [ ] `BlockBinding` が slot ごとに Bedrock の `render_method` を client 側へ出す
- [ ] **conformance `block/render_method` は renderer を入れてから書く。**
      IR JSON は今 materials を直列化していないので、
      `ir.packs[0].behavior.blocks[...].materials.render_method` を assert する場が無い。
      これを足すと既存 golden 11 件が全部動くが、それで得られるのは
      「我々が書き出したものを検証するだけ」で、**何も証明にならない**。
      §5.3.1 で出した失敗とまったく同じ構造。IR で assert できる有意思なものは
      「どの chunk layer に乗るか」**ではなく画面**なので、renderer が入ってから書く。
- [ ] `@SpecImpl("SC-150#minecraft:material_instances")`
- [ ] coverage `render_method: ok`。entry は他フィールドが残るため `partial` のまま
- [ ] `./gradlew specAll` green
- [ ] **トロフィーをワールドに置いてハローの輪郭が透けることを確認**

## 未測定（実装時に埋める。推測で書かない）

- [ ] `cutout_block` が両面か片面か。`alpha_test_single_sided` と `double_sided` の実装は
      これが分かるまで書けない
- [ ] `BlockRenderDispatcher` が実際に `solid_block` を選ぶ経路。現在たどれていないのは
      「我々が RenderType を一度も選ばない」という事実からの帰結であり、直接の実測ではない
- [ ] 26.2 の pipeline 名が 1.21.11 と揃っているか