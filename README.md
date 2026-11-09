# Fablewind — 風の国の旅人

オリジナルの童話ファンタジー世界を自由に歩き回る、Android向け2.5D探索ゲームです。

- 数十km規模のシード固定プロシージャルワールド（地形・森・集落・道・川）
- 奥行きのあるアイソメトリック描画と手描きのオリジナル図形モデル
- タッチジョイスティック、ダッシュ、ミニマップ、ズーム、昼夜サイクル
- オフライン動作（ネットからダウンロードするアセットは不要）

## APK

GitHub Actions の `Build Android APK` ワークフローで `app-debug.apk` が作成され、`apk/Fablewind-debug.apk` に保存されます。Actions の Artifacts からもダウンロードできます。

## ローカルビルド

JDK 17、Android SDK 35、Gradle 8.10.2 で `gradle assembleDebug` を実行します。生成先: `app/build/outputs/apk/debug/app-debug.apk`。

## 権利

特定の映画やキャラクターの素材は使用していません。建物・樹木・地形・主人公などは Android Canvas でコードから描画するオリジナルの図形です。
