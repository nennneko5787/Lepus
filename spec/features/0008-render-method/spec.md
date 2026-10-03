# 0008 — `render_method` を描画レイヤーに反映する

対象カバレッジエントリ:

- `minecraft:material_instances` の `render_method`（**未実装・原因は確定**）

対応する normative: SC-150 §5.4（本次で原因を追記）。

## 症状

Blue Archive パック `キヴォトス生徒追加アドオン[B]` のブロック `kivotos:binah_trophy`（ビナーのトロフィー）。

**トロフィーのハローだけ絵が歪む。** 本体は不透明なので変化がなく、ハローだけが黒く塗りつぶされて
形は残ったままになる。**同じブロックを手に持ったときは正常**である。

## 原因（確定。全リンクを実測済み）

1. **パックが宣言している。** `blocks/kivotos/trophy/1.binah_trophy.json` に
   `"render_method": "alpha_test"`。
2. **テクスチャが透明を要求している。** `textures/decagrammaton/binah_trophy.png` は PNG colour
   type 6（RGBA）。失うべき透明 texel がある。
3. **我々が読んでいない。** `grep -rn "render_method\|renderMethod" src/ core/` は**0 件**。
   `BlockModels.Materials` は texture key しか持たない。
4. **alpha はシェーダではなく define で捨てられる。** `terrain.fsh` と `block.fsh` の両方が
   `#ifdef ALPHA_CUTOUT` → `discard`。どちらのシェーダも自力で discard しない。
5. **1.21.11 には専用の pipeline がある。** `CoreShaders` のバイトコードが
   **`pipeline/solid_block`（`ALPHA_CUTOUT` なし）** と **`pipeline/cutout_block`
   （`ALPHA_CUTOUT = 0.5f`）** を別々に組み立てている。`solid_terrain` / `cutout_terrain` も同じ。
6. **entity 側も同じ分裂。** `pipeline/entity_solid` には define がなく、`entity_cutout` /
   `entity_cutout_no_cull` / `entity_cutout_no_cull_z_offset` にはある。

したがって: ブロックは `solid_block` で描かれ、**透明 texel が不透明として塗られる**。欠落ではなく
**誤った絵**（黒いハロー）になる。

**手に持ったとき正常、置くと壊れる、という非対称が署名である。** 手持ちは
`entity_cutout_no_cull`（= 我々の `AttachableRenderTypes.solid()` が返すもの）を通るので define が
ある。**これが「geometry ではなく描画レイヤーの問題」だと示す決め手になる。**

## 取得あるいは見たものを誤った順（残す価値がある）

1. **まず flat-cube を疑った**（§5.3.1）。13 行のコメントだけであることも確かめた。
2. **次に box UV を疑った**（§5.3.2）。範囲外 25 面の算術まで数えた。
3. **どちらも原因ではなかった。** そして §5.3.1 の規則そのものが、報告者が後から撤回した症状
   （「ハローが消える」）を根拠に書かれていた。
4. **答えは 3 行の JSON に書かれていた。** `material_instances.render_method`。
   SC-150 §5.4 に規則は既にあり、コードだけが未実装だった — 記録されていた穴。

**教訓:** geometry と UV を袋小路まで追い切ったあとで、ファイル自身を見れば済んだ。**原因は
レイヤーにあると分かったとき、最初にパックの宣言を読むべき。** 我々は 1 も 2 も既に実装済みで
「直せば直る」ように見えたため、そこを疑わなかった。

## やらないこと

- **`render_method` 以外を同時に直さない。** `face_dimming` / `ambient_occlusion` /
  `tint_method` / `isotropic` は同じエントリの別フィールドであり、混ぜると
  「どれで緑になったのか」が分からなくなる。
- **UV に触らない。** §5.3.2 は未測定のまま残す。0007 の「折り返すか拒否するか」という
  別の問いと混ざるため。
- **実測していないことを実測のように書かない。** 1.21.11 の pipeline 名はバイトコードから取った
  もの。`BlockRenderDispatcher` が実際に `solid_block` を選ぶ経路を追えていないので、
  「ブロックは solid で描かれる」は**我々のコードが RenderType を一度も選ばない**ことから出る
  帰結であり、直接の実測ではない。この区別は実装時に埋める。

## 検証

- `./gradlew --project-dir core build`
- `./gradlew specAll`
- 最終確認は**トロフィーをワールドに置いて**、ハローの輪郭が透けて見えること

## 未測定（実装しない）

- [ ] **`alpha_test_single_sided` のカリング** — SC-150 §5.4 は「cutout with culling」と書くが、
      どの面が切れるかは Java 側で表れていない。`block.fsh` と同じ define を持つ pipeline は
      1.21.11 には 1 種しかない（`cutout_block`）ので、**カリング無しの版が存在しない**可能性が
      ある。`solid_block` と合わせて両面か片面かをバイトコードで確かめてから実装する
- [ ] **`double_sided`** — 同上。`pipeline/cutout_block` が既定で両面か片面かは未確認
- [ ] **`blend` → translucent** — `pipeline/translucent_terrain` に define が無いのは正しい（discard
      しない）ので構造的には合う。`translucent_block` が存在するかは未確認