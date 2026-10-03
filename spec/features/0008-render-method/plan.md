# 0008 — 計画

## 順序を先に決める理由

**先に「どの描画レイヤーを選ぶか」を確定してから型に触る。** `render_method` を IR まで通すのは
10 行で終わる作業だが、ブロック経路で何を選ぶかは未確定（`BlockRenderDispatcher` がどの
pipeline を呼ぶのか、どの面が切れるのか）。これを不明のまま型だけ入れると、
「型は正しいので実装済み」に見えてしまう。

## やること

1. **`BlockModels.Materials` に `render_method` を通す** — 現行は texture key の
   `Map<String, String>` なので、`MaterialInstance`（texture と `renderMethod` を持つ）に
   入れ替える。IR 側の変更は小さい
2. **conformance を先に書く** — `alpha_test` / `opaque` / `blend` / `double_sided` /
   `alpha_test_single_sided` を宣言したブロックを 1 つずつ置き、IR に現れることを assert する。
   実装前に書くと赤になる。それでいい
3. **`BlockBinding` 側で pipeline を選ぶ** — ここが本作の本体。per-version の差が出る可能性が
   あるので、差が 5 行を超えてたら source ディレクトリに逃がす
4. **読むのは 1 か所だけ** — 生成されたブロックモデルに形にして渡す。Java の `BlockModel` に
   「シェーダ」の概念は無いので、外部 API が必要になる可能性があり、**それが実装の可否を決める**。
   まずここを調べる

## やらないこと

- **`face_dimming` / `ambient_occlusion` / `tint_method` / `isotropic`** — 同じエントリの別の
  フィールド。同じコミットで直すと、どれの検証が緑になったのか分からなくなる
- **UV（SC-150 §5.3.2）** — 別 feature。持ち込むと、原因が分からないまま「直ったことになる」
- **`flipbook_textures` とアニメーションフレーム** — 別 feature

## 検証

- `./gradlew --project-dir core build` — Minecraft 非依存側（IR と conformance）
- `./gradlew specAll` — ledger と conformance と生成 docs
- `./gradlew chiseledCompile` — 4 ノード
- **トロフィーをワールドに置く** — ハローの輪郭が透けて見えること。**これが最後の判定**。
  0007 はテストを全て緑のまま退行を出荷した

## 残る未測定（実装中に埋める）

- `cutout_block` が両面か片面か
- `solid_block` と `cutout_block` 以外の block pipeline が 1.21.11 に存在するか。
  pipeline の一覧を先に 1 枚取っておく
- 26.2 側の pipeline 名（1.21.11 と揃っているとは限らない）