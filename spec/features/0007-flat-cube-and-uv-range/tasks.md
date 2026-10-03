# 0007 — タスク

## 実測

- [x] トロフィーの天板は `{"origin": [-12, 19, -4], "size": [24, 0, 15], "uv": [-15, 11]}`
      — 厚みゼロ、かつ `uv` 原点が負
- [x] 旧 jar（8 月）と現行ソースの `BlockGeometry.element()` / `faceJson()` は**バイトコードレベルで
      同一**。10 月の改変はコメント 13 行だけで、挙動の差は無い
- [x] 10 月のコンパイル済み class に `BlockGeometry.flatFace(float, float, float)` があった。
      厚みゼロの 2 面の片方を落としている。**この時点では原因の筈だった**
- [x] 退行の証拠はコンパイル済み class に `flatFace` が**存在すること**、ソースに無いこと
- [x] 8 月のランは有効 3 パックでハローが出ていた。10 月の dev ランは有効 6 パックで出ていない。
      **有効パック数は原因ではない**
- [x] transpile 可能 18 モデルのうち、zero-thickness axis 14、0..16 外の UV 25 面（u<0 が 11、
      u>16 が 10、v>16 が 6）
- [x] `BlockElementFace.UVs` は検証もクランプも折り返しもしない。`terrain.fsh` は discard しない。
      つまり範囲外 UV は**欠落ではなく誤った絵**になる
- [x] ハローの `up`（Java `u` 0→6）と `down`（6→12）は**範囲内**。`u < 0` を持つのは面積ゼロの
      4 面だけ。**ハローは §5.3.2 の実例ではない**

### 症状の撤回と、それを受けた訂正

- [x] **「ハローが消える」は報告者が撤回した。** ハローは消えていなかった。実在する症状は
      「絵が歪む」1 つだけで、原因は §5.4 の `render_method` である
- [x] **8 月と 10 月の比較は比較でなかった。** 8 月のインスタンスは VulkanMod / EMF / ETF /
      entityculling / Figura 入り + リソースパック 2 個、10 月の dev ランは vanilla + Fabric API のみ。
      ログからどのモッドが何を変えたかは特定できない
- [x] SC-150 §5.3.1 から「実測」「ハローが消えた」を削除。**Minecraft の baker が退化を
      どう扱うかは未測定**と書き、根拠を「不明を明示したうえでの安い選択」に差し替えた
      （捨てられるのは高々 14 quad）。根拠として attachable 側が `entityCutoutNoCull` で
      カリングオフであることを引用した — 我々のコードから直接読めるので
- [x] coverage の fidelity note も同じ撤回に合わせる

## 実装と記録

- [x] SC-150 §5.3.1 — 厚みゼロは**両面**。attachable 側の規則を移植したのが誤りだった経緯と、
      **その根拠が撤回された経緯**を書く
- [x] SC-150 §5.3.2 — box UV の範囲外。**規則ではなく未測定として記録**（候補を 2 つ挙げ、理由付きで
      否定しない）。ハローを実例として挙げない
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
- [x] **新規ワールド既定で 4 パック** — **実害ではない。** 新規ワールドには `active.json` が無く、
      §8 の「未指示なら有効なものなし」がそのまま既定になる。4 パックは Screen から有効化した
      4 つで、残り 2 つは誰も有効化していないだけ
      - 「インストール済みを全部有効化する」は §8.1 に反する。新規ワールドの時点で全パックの
        ブロックを束하려とし、プール枯渇は**再起動が要る唯一の操作**である。既定を既定が破ると
        いうことになる
      - 「前のワールドの集合をコピー」は、開いた順に世界の中身が変わる。予想も検証もできない
      - **圏外だった項目**（仕様が触れていなかった）なので SC-120 §8 に 1 段落書いた。以後は
        `active.json` の無いワールドが一意に決まる
      - 残る穴は別の話。SC-120 §8 が名指しする**ワールド作成画面が存在しない**ので、有効化は
        ゲーム内の Screen を通るしかない。SC-280 の領域でありカバレッジエントリも無いので
        AGENTS.md に従ってここでは作らない。**ユーザーに要否を聞くこと**
- [ ] **26.1 ノード** — 存在しない。`settings.gradle.kts` に `match("26.1", …)` と
      `stonecutter.properties.toml` の節が必要（SC-220 §6）

## 別 feature に移した

- [ ] **`render_method`（SC-150 §5.4）** — ハローの**絵が歪む**方の原因。`alpha_test` を宣言した
      テクスチャが `RenderType.solid()` で描かれるため alpha が効かない。`0008-render-method` で扱う
