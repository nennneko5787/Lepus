# 0008 — タスク

## 実測（済）

- [x] トロフィーのブロックは `"render_method": "alpha_test"` を宣言している
      （`blocks/kivotos/trophy/1.binah_trophy.json`）
- [x] そのテクスチャは PNG colour type 6（RGBA）。失うべき透明 texel がある
- [x] alpha を捨てるのはシェーダではなく **define**。`terrain.fsh` と `block.fsh` の両方が
      `#ifdef ALPHA_CUTOUT` → `discard`。どちらも自力では discard しない
- [x] 1.21.11 の `CoreShaders` は **`pipeline/solid_block`（define なし）** と
      **`pipeline/cutout_block`（`ALPHA_CUTOUT = 0.5f`）** を別々に組み立てている。
      `solid_terrain` / `cutout_terrain` も同じ
- [x] entity 側も分裂している。`entity_solid` は define なし、`entity_cutout` /
      `entity_cutout_no_cull` / `entity_cutout_no_cull_z_offset` にはある
- [x] **手に持ったとき正常、置くと壊れる** という非対称が `alpha_test` の経路差の署名である
- [x] `ChunkSectionLayer` は `SOLID` / `CUTOUT` / `TRANSLUCENT` の **3 つだけ**
      （1.21.11 の mappings で確認）。Bedrock の 5 個の method 全部は載らない

## 原因として却下したもの

- [x] **§5.3.1（厚みゼロの cube）** — ハローの天板は `size: [24, 0, 15]` で退化しているが、これは
      登録が 1 つ原因ではなく、**症状側が撤回済み**
- [x] **§5.3.2（範囲外 box UV）** — 実際に描く `up` / `down` の `u` は 0..12 で範囲内。
      `u < 0` を持つのは面積ゼロの 4 面だけ

## 実装（loader 非依存側）

- [x] `BlockModels.Materials` が `render_method` を読む。`RenderMethod` enum（5 名）と
      `layerOf` で 1 つの答えへ畳む。alias は texture だけでなく **method も継承**する
- [x] `BlockModelsTest` に 7 件。実パックの `alpha_test` 宣言そのままを fixture にしてある
- [x] `BoundBlocks.Bound` が `renderMethod` を保持する
- [x] `BlockBinding.publish` が materials から詰める。**state 0 するのはハックではない** —
      chunk layer は **block 単位**なので、state 同士で食い違う block は 2 通りでは描けない。
      先頭 state を取ると「どれが sort で先か」に答えが左右される
- [x] `BlockRenderLayers.needed(bindings, pool)` — pool block から method への集合。loader 非依存
- [x] `BlockPoolRegistrationTest` に 2 件（1 slot が alpha を要求 / 未束縛 slot は要求しない）
- [x] `appearanceOf` と同じ `materialsOf` を使う。texture と method は同じオブジェクトにあるので
      2 回解くと**互いに食い違う**。それが今回の症状そのもの
- [ ] **conformance `block/render_method` は renderer を入れてから書く。**
      IR JSON は今 materials を直列化していないので
      `ir.packs[0].behavior.blocks[...].materials.render_method` を assert する場が無い。
      これを足すと既存 golden 11 件が全部動くが、それで得られるのは
      「我々が書き出したものを検証するだけ」で、**何も証明にならない**。
      §5.3.1 で出した失敗とまったく同じ構造。IR で assert できる有意思なものは
      「どの chunk layer に乗るか」**ではなく画面**なので、renderer が入ってから書く。
- [ ] `@SpecImpl("SC-150#minecraft:material_instances")`
- [ ] coverage `render_method: ok`。entry は他フィールドが残るため `partial` のまま
- [ ] `./gradlew specAll` green
- [ ] **トロフィーをワールドに置いてハローの輪郭が透けることを確認**

## 導入側（client に layer を入れる。上の実装とは別）

1.21.11 のバイトコードで**最後の 1 リンク**を確定した。ここは推測ではない。

| | 実測 |
|---|---|
| 層を選ぶ method | `ItemBlockRenderTypes.getChunkRenderType(BlockState)` が `ChunkSectionLayer` を返す |
| その中身 | `TYPE_BY_BLOCK.get(state.getBlock())`、無ければ `SOLID` |
| map の型 | `Map<Block, ChunkSectionLayer>` — **Block 単位**。既定は SOLID |
| 実際の draw 側 | `BlockRenderDispatcher.renderSingleBlock` は `getRenderType(RenderShape)` から `RenderType` を取る。**2 段構え**で、層は前者が決める |
| Fabric | `BlockRenderLayerMap.putBlock(Block, ChunkSectionLayer)` 公開 API がある。**mixin 不要** |
| NeoForge | **`IBlockExtension` に render フックが無い**（javap で確認）。`RenderTypeHelper` は layer の**消費側**であって登録側ではない |

**loader ごとの差は 1 行 +/- mixin で，而且是 loader だけ:**

- Fabric: `BlockRenderLayerMap.putBlock(poolBlock, ChunkSectionLayer.CUTOUT)`
- NeoForge: `ItemBlockRenderTypes` の `TYPE_BY_BLOCK` への accessor mixin。
  **このプロジェクトはまだ neoforge 側に mixin config を持っていない**
  （`src/neoforge/resources/lepus.mixins.json` と `neoforge.mods.toml` の `[[mixins]]` が要る）。
  Fabric 側は `src/fabric/resources/lepus.mixins.json` が既にある

**粒度が合うのは pool の性質であって設計ではない。** 1 Bedrock block = 1 slot = 1 `PoolBlock` なので
`Map<Block, ...>` でちょうど表現できる。slot を block 間で共有するようになったらこの feature は
成立しなくなる。

**残る罠: rebind してもチャンクメッシュは作り直されない。** 層は**メッシュ構築時**に読まれるので、
実行中に pack を有効化/無効化した直後に構築済みの section には反映されない。SC-120 §8 step 5 の
「client が binding を作り直す」はまだその機構を持っていない（既存コードに chunk invalidate が無い）。
**新規ワールドに入って初めて置く**なら要らない。`/lepus pack enable` の後に既に置いたブロックは
再配置か section reload が要る。これは実際に触って測るまで書かない。

## 未測定（実装時に埋める。推測で書かない）

- [ ] `cutout_block` が両面か片面か。`alpha_test_single_sided` と `double_sided` の実装は
      これが分かるまで書けない
- [ ] 26.2 側の pipeline 名と `ChunkSectionLayer` の定数が 1.21.11 と揃っているか