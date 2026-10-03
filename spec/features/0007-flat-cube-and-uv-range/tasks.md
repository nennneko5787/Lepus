# 0007 — タスク

## 実測

- [x] トロフィーの天板は `{"origin": [-12, 19, -4], "size": [24, 0, 15], "uv": [-15, 11]}`
      — 厚みゼロ、かつ `uv` 原点が負
- [x] 旧 jar（8 月）と現行ソースの `BlockGeometry.element()` / `faceJson()` は**バイトコードレベルで
      同一**。10 月の改変はコメント 13 行だけで、挙動の差は無い
- [x] 退行の原因は 10 月のコンパイル済み class にあった `BlockGeometry.flatFace(float, float, float)`。
      厚みゼロの 2 面の片方を落とし、`up` を落としたのでハローが消えた
- [x] 退行の証拠はコンパイル済み class に `flatFace` が**存在すること**、ソースに無いこと
- [x] 8 月のランは有効 3 パックでハローが出ていた。10 月の dev ランは有効 6 パックで出ていない。
      **有効パック数は原因ではない**
- [x] transpile 可能 18 モデルのうち、zero-thickness axis 14、0..16 外の UV 25 面（u<0 が 11、
      u>16 が 10、v>16 が 6）
- [x] `BlockElementFace.UVs` は検証もクランプも折り返しもしない。`terrain.fsh` は discard しない。
      つまり範囲外 UV は**欠落ではなく誤った絵**になる
- [x] ハローの `up`（Java `u` 0→6）と `down`（6→12）は**範囲内**。`u < 0` を持つのは面積ゼロの
      4 面だけ。**ハローは §5.3.2 の实例ではない**

## 実装と記録

- [x] SC-150 §5.3.1 — 厚みゼロは**両面**。attachable 側の規則を移植した 것이誤りだった経緯を書く
- [x] SC-150 §5.3.2 — box UV の範囲外。**規則ではなく未測定として記録**（候補を 2 つ挙げ、理由付きで
      否定しない）。ハローを实例として挙げない
- [x] §5.3.1 / §5.3.2 が §5.3 の**比較表の途中**に入っていたのを、表的後ろへ移動
- [x] coverage `zero_extent_cube: ok` / `box_uv_outside_texture: missing`
- [x] coverage の `conformance` に `block/geometry_flat_cube` を追加
- [x] `BlockGeometry.flatFace` を撤去。6 面そのまま（attachable 側の `AttachableGeometry.flatFace` は残す）
- [x] `BlockGeometryTest` — 実トロフィーの plate を fixture にした 3 件
- [x] conformance `block/geometry_flat_cube` + golden を再生成（差分は `elements[1]` に `up` が
      1 行追加されただけ）
- [x] `./gradlew --project-dir core build` green
- [x] `./gradlew specAll` green

## 残る測定（実装しない）

- [ ] **継ぎ目をまたぐ box UV** — Bedrock の実測 1 枚。`barHold` と同型の画面。
      測定を取る前に `partial` を書かないこと。field は `missing` のまま
- [ ] **新規ワールド既定で 4 パック** — 6 パック全部が有効になるべきなら実害。
      SC-120 §8 との整合を確認してから決める
- [ ] **26.1 ノード** — 存在しない。`settings.gradle.kts` に `match("26.1", …)` と
      `stonecutter.properties.toml` の節が必要（SC-220 §6）

## 別 feature に移した

- [ ] **`render_method`（SC-150 §5.4）** — ハローの**絵が歪む**方の原因。`alpha_test` を宣言した
      テクスチャが `RenderType.solid()` で描かれるため alpha が効かない。`0008-render-method` で扱う
