# RealMe 開発ガイド

## 目的と現在の方針

Android専用のゲーム版BeRealを開発する。現在は外部サービス未設定でも動くオフラインMVPを優先し、Supabase / Firebaseへの接続・秘密鍵・課金設定を行わない。後で remote 実装へ差し替えられるRepository interfaceを維持する。

## 技術基盤

- Kotlin / Jetpack Compose / Material 3 / MVVM / Coroutines / StateFlow
- AGP 9.5.1（built-in Kotlin）、Gradle 9.6.0、compileSdk / targetSdk 37、minSdk 29
- Androidモジュールは `android/app`、application ID は `com.realme`
- ローカル永続化はDataStore Preferences内のスキーマ付きJSONとアプリ専用ファイル領域
- 自動投稿はローカルMVPではWorkManager。サーバー側Cronへ置き換えるまでは端末到達保証の制約を明示する

## 担当分離

- 担当A（撮影）: `feature/capture`, `feature/overlay`, `core/media`, `core/image`
- 担当B（SNS）: `feature/auth`, `feature/timeline`, `feature/profile`, `feature/posting`, `feature/settings`, `data/repository`
- 共通model / Repository interfaceを先に変更提案し、担当外の実装を勝手に書き換えない。

## 実装ルール

- 既存コードと `docs/HANDOFF.md` を確認してから変更する。
- 動作する小さな変更を保ち、実機またはエミュレーターで確認できない機能を完成扱いしない。
- 画像は原則アプリ専用領域へコピーし、公開済みとローカル撮影候補を区別する。
- MediaProjection同意トークンを保存・再利用しない。Service停止時にVirtualDisplay / ImageReader / MediaProjectionを解放する。
- 秘密鍵、Service Role Key、署名ファイルをGitへ追加しない。
- Git commit / push / reset / rebaseなどの変更操作はユーザー明示指示なしに行わない。

## 検証コマンド（Windows PowerShell）

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :android:app:assembleDebug :android:app:testDebugUnitTest
```

エミュレーター専用のADB操作はシリアル `emulator-*` を明示し、物理端末を対象にしない。

connected testは対象アプリを再インストール・アンインストールする場合がある。ユーザーデータ入りのAVDでは直接実行せず、`scripts/verify-local.ps1 -IncludeConnectedTests` の自動バックアップ・復元を使うか、テスト専用AVDを使用する。

## 作業終了時

`docs/ROADMAP.md` と `docs/HANDOFF.md` に、実装内容、実行コマンド、結果、未検証事項、変更ファイル、Git状態、次作業を記録する。
