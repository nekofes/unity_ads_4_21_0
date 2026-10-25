# unity_ads_4_21_0

Unity広告SDK（バージョン4.21.0）をAndroid（Kotlin）に実装するためのサンプルプログラムです。

## 📌 主な機能
* バナー広告の表示

## 🛠 使い方
このプロジェクトに含まれる `MainActivity.kt` の内容を、ご自身のプロジェクトに必要に応じてコピーしてご使用ください。

## 🛠 開発環境
* **開発ツール:** Android Studio
* **言語:** Kotlin
* **Unity Ads SDK Version:** 4.21.0

## 📂 主要なファイル
* `MainActivity.kt` : 広告の読み込みと表示をコントロールするメイン処理
* `capture/` : テストモードでの実行画面キャプチャを格納

---

## 🔍 実装のポイントと設定方法

### 1. テストモードの自動判定 (`testMethod`)
`MainActivity.kt` 内にある `testMethod` は、`BuildConfig.DEBUG` を自動で読み込みます。
• アップデート内容: context.packageName を用いて、ご自身のアプリのパッケージ名を動的に取得するよう改良されました。
そのため、ソースコードを書き換える必要はありません。 そのままプロジェクトに配置するだけで動作します。
* **戻り値:** `0`（本番環境） / `1`（テスト環境）
* 正しい戻り値が取得できると、`adTestMethod` および `placementId` から分岐した設定値を自動取得できるようになります。

### 2. Unity Game IDの設定 (`adTestMethod`)
`MainActivity.kt` 内の `adTestMethod` メソッドにある `gameId` を書き換えます。
* サンプルコードでは `123456789` になっています。ご自身の **Unity Game ID** に差し替えてください。

### 3. Placement IDの設定 (`placementId`)
`MainActivity.kt` 内の `placementId` メソッドにある ID を書き換えます。
* サンプルコードでは `xxx` になっています。Unityダッシュボードで取得した **Placement ID** に差し替えてください。

### 4. 🔴 OSのサイズ確定イベントによる無限ループ回避
システムバーや広告の追加を検知する `ViewCompat.setOnApplyWindowInsetsListener` は、広告のビューが追加された衝撃で何度も再呼び出しされる特性があります。
* 対策として、サンプルでは `isSizeCalculated` フラグによるロック制御を導入しています。
* 最初の1回のみサイズ計算を実行し、広告ロード成功時の再計算による「座標の再上書き（バナーやラベルの文字が描画バグで消える現象）」を防止しています。

### 📊 広告に使用するパラメータ一覧

| パラメータの種類 | 本番モード | テストモード |
| :--- | :--- | :--- |
| **Game ID** | あなたのUnity Game ID | `4550882` |
| **Test Mode** | `false` | `true` |
| **Placement ID** | Unity登録画面で取得したID | `Banner_Android` |

---

## ⚙️ 各種ファイルの配置・修正例

### 📄 AndroidManifest.xml
Android 12以降のシミュレーターや実機で広告IDを正常に読み取るため、以下のパーミッションを `<manifest>` タグ内に追加してください。

```xml
<!-- Android 12以降で広告IDを読み取るために必須 -->
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />
```

### 📄 libs.version.toml
新規プロジェクトに導入する場合、`coreKtx` のバージョン競合が原因でエラーが発生することがあります。本サンプル（unity-ads:4.21.0）は、**`1.15.0`** で動作確認をしています。

**[場所]** `Gradle Scripts` ＞ `libs.version.toml`

```toml
[versions]
coreKtx = "1.15.0"
# ... その他のバージョン

[libraries]
# ... 既存のライブラリ指定
androidx-ui-text = { group = "androidx.compose.ui", name = "ui-text", version.ref = "androidxComposeUi" }
```

### 📄 build.gradle.kts (:app)
Unity広告SDKを利用するために、依存関係を追加します。

**[場所]** `Gradle Scripts` ＞ `build.gradle.kts (:app)`

```kotlin
dependencies {
    // ... 他の依存関係
    
    // Unity Ads SDK
    implementation("com.unity3d.ads:unity-ads:4.21.0")
}
```

### 📄 settings.gradle.kts
Kotlin 2.1.0などの新しいプラグインは `mavenCentral()` から取得する必要があります。
Googleリポジトリに対して設定されているフィルター制限（`content { includeGroupByRegex(...) }`）により、Kotlin関連のプラグインが検索から除外され、`UnknownPluginException (PluginResolutionResult.getFound)` が発生することがあります。
リポジトリの制限を解除し、Kotlinのグループを追加するために、以下の行を必ず追記してください。

```kotlin
includeGroupByRegex("org\\.jetbrains\\.kotlin.*")
```

**[場所]** `Gradle Scripts` ＞ `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("org\\.jetbrains\\.kotlin.*") // 👈 これを追記
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
```

### 📊 組み合わせの例

| ツール | バージョン | 理由 |
| :--- | :--- | :--- |
| **Gradle** | 8.13（今回のサンプル） | 最新のビルドキャッシュ最適化が効くため、ビルドが高速です。 |
| **Android Gradle Plugin (AGP)** | 8.13.0（今回のサンプル） | AGPはGradleのメジャーバージョンと合わせるのが最も親和性が高く、エラーが起きにくいです。 |
| **Kotlin** | 2.1.0（今回のサンプル) | K2コンパイラが標準有効化されており、ビルド速度・安定性が向上しています。 |

### 📄 activity_main.xml
バナー広告を表示するためのレイアウト例です。
💡 **Tips:** Unity Adsの広告がうまく表示されない場合、`layout_height` を通常の `50dp` から **`55dp`** に少し広げることで、表示の誤差（内部コンテナのクリッピング）を吸収できる場合があります。
また、広告の重複追加を防ぐため、コード上で `addView(adView)` を行う直前に必ず `binding.adView.removeAllViews()` を実行してください。

```xml
<FrameLayout
    android:id="@+id/adView"
    android:layout_width="320dp"
    android:layout_height="55dp"
    android:layout_gravity="center_horizontal" />
```

---

## 更新履歴
- 2026/10/25: Unity Ads SDKの最新仕様に対応。特定の端末で発生するnullバグを回避するため最新の `onAdLoaded` リスナーに移行し、親レイアウトの型崩れによるアプリ強制終了のバグを修正。

## 🔗 関連リンク
* **開発者サイト:** [nekofes](https://nekofes.github.io/)
