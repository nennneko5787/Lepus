# 0007 — 計画

## 達した順と、その理由

**調査（測定）を先に置いた。** 規則を先に書くと、`BlockGeometry` に触らない修正案が書けてしまう。
実際、最初に flat-cube を疑った段階では「Java のローダが要素を落とした」可能性と「両方描画した」
可能性が候補として 2 つあり、**どちらを直すべきかが決まらないままコードを書くことになる。**

その順序が正解だったのは、証拠が後から 2 つとも必要になったからで、どちらも計測なしには
出せなかった。

- 旧 jar と現行ソースの `element()` / `faceJson()` のバイトコード比較。**モデルが書き出すバイト列は同一**
- `terrain.fsh` と `block.fsh` の実ファイル読み。**alpha の discard は cutout にしか無い**

## やること

1. **SC-150 §5.3.1** — 厚みゼロは**両面**。attachable 側の既存規則とは逆で、その理由を書いておく
2. **SC-150 §5.3.2** — box UV の範囲外。**規則ではなく「未測定」という記録にする。** 代替案が 2 つ
   ありどちら も検証できていないため。**そしてトロフィーのハローをこの欠陥の实例にしてはならない** —
   §5.3.1 の症状と取り違えると、誰も答えに到達しない
3. **coverage** — `zero_extent_cube: ok` / `box_uv_outside_texture: missing`。エントリ自体は
   `partial` のまま（fidelity note が残っているため。ADR-0011）
4. **実装** — `BlockGeometry.flatFace` を**撤去**し、6 面をそのまま戻す
5. **test** — 実トロフィーの plate（`size: [24, 0, 15]`）をそのまま fixture に
6. **conformance** — `block/geometry_flat_cube`。厚みあり/なしの 2 cube を置き、
   「どちらの cube も 6 面」を両方主張する
7. **golden** — `-Dlepus.accept=true` で生成し、**差分を読む**

## やらないこと

- **box UV の折り返しを実装しない。** 継ぎ目の挙動が未測定。実装すると、実装した人間以外が
  「なぜこうなったか」を追えなくなる
- **`BlockGeometry` に 3 つ目の flat-face 規則を置かない。** 規則は attachable と block の 2 箇所。
  3 つ目があれば 3 つ目の抜け道になる
- **`render_method` をここで直さない。** §5.4 の話であり別 feature（`0008-render-method`）である。
  1 つの feature に 2 つの症状を混ぜると、片方が直ったときにどちらの検証が緑になったのか
  分からなくなる

## 検証

- `./gradlew --project-dir core build` — Minecraft 非依存側
- `./gradlew specAll` — `specValidate` / `specLanguage` / `adrIndex` / `specLinks` /
  `specConformance` / `specUpstreamDiff` / `specReport`
- `./gradlew chiseledCompile` — 4 ノード
- 最終確認は**ワールドに置くこと**。この feature の最初の版は corpus を全部緑にしたまま退行を
  出荷したからである
