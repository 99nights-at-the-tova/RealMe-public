# RealMe — Offline Android MVP

ゲーム版BeRealを目指す、Android / Kotlin / Jetpack Composeアプリです。このブランチの現行実装は、**Supabase / Firebaseなしで動くローカルMVP**です。オンラインSNSや複数端末間共有が完成したという意味ではありません。

協力者の方は、環境構築・検証方法・共有されないデータをまとめた [協力者向け引き継ぎ](docs/COLLABORATOR_GUIDE.md) からお読みください。

デモ動画は公開用コピーには含めていません。

## 現在の進捗（2026-11-17）

- 端末内での投稿・画像編集・自動投稿・プロフィール切替・PIN・アカウント削除を実装。UIはダーク基調（深い緑の背景と明るいミントのアクセント）に刷新しました。
- 画面上へ撮影ボタンを手動で表示し、撮影後は自動投稿される時刻を知らせる通知を出す流れを実装。古い撮影候補は整理時に、投稿や有効な下書きに必要な画像を残して削除します。
- 撮影方式をMediaProjectionの画面共有から、AccessibilityServiceの `takeScreenshot()` へ変更しました。初回だけユーザー補助を有効にすれば、以降は撮影ボタンやクイック設定タイルから撮影でき、画面共有の承認ダイアログは出ません。
- API 37のエミュレーターでビルド・単体テストとユーザー補助サービスの接続を確認しました。実ゲーム上での撮影、長時間のバックグラウンド動作、複数端末の同期、FCM通知は未検証または未実装です。

## 実装範囲

- ダミー匿名認証、ローカルプロフィール、既存アカウントの端末内切り替え（任意の端末内PIN付き。サーバー認証はなし）
- ダーク基調のテーマ（`darkColorScheme`）の全体タイムライン（同じ端末内のローカルデータ + デモ投稿）
- Photo Pickerによる画像選択、圧縮、ローカル投稿・削除
- プライバシー画像の設定、PRIVACY投稿、公開後の差し替え
- JST日次イベント、撮影から5分以内のON TIME / LATE（通知前の画像も投稿可）
- 現在・最高・累計の連続投稿記録
- 初回撮影からの自動投稿設定、WorkManager下書き、手動キャンセル
- AccessibilityServiceの `takeScreenshot()` によるスクリーンショット撮影と、移動可能なフローティング撮影ボタン（MediaProjectionの同意は不要）
- 複数枚のローカル撮影候補と、チェックボタンからの即時投稿（投稿後は撮影候補から削除）
- 撮影後の通知から編集画面を開き、×で破棄確認、投稿でホームへ戻る導線
- ライブラリタブを「あとで編集」（当日）と「過去のアーカイブ」（過去）に分割
- イベント通知（ランダム通知・勧誘通知）はこのバージョンでは廃止
- ゲーム中のスクリーンショットを検知し、「投稿しますか？」の通知から「はい」で自動投稿の予定を作成（「○時に投稿されます」と通知）。開いた直後だけでなく、プレイ中のいつでも検知する
- ホーム画面にRealMeのショートカットを追加
- クイック設定の「RealMe撮影」タイルから、ゲーム中でもいつでもスクリーンショットを撮って「投稿しますか？」を表示
- ローカル画像編集（フィルター、プリセット/自由矩形クロップ、範囲指定ぼかし・モザイク、スタンプの移動・拡大縮小・回転、テキスト、色変更、Undo、保存）。範囲スライダーは指を離すと反映され、文字・スタンプはプレビューと保存で共通描画。
- 編集開始時の自動投稿期限管理と、未保存編集時の元画像公開防止
- 端末内アカウント切替・起動時ロック用の任意PIN（PBKDF2-SHA256、ソルト付きハッシュ、5回失敗で30秒ロック）
- ローカルアカウント削除（PIN・確認入力、撮影セッション停止、本人データ削除、失敗時の永続クリーンアップ再試行）

最新のビルド・テスト結果と未検証項目は [docs/HANDOFF.md](docs/HANDOFF.md) を参照してください。API 37エミュレーターではユーザー補助サービスによる撮影、画面回転、撮影中のアカウント削除、2アカウントの保存通知まで確認済みです。物理端末、実ゲーム、実時間の日付またぎ、長時間バックグラウンド投稿は未検証です。

## プロジェクト構成

```text
RealMe/
├── android/app/          # Androidアプリケーション
├── docs/                 # 設計・引き継ぎ・検証記録
├── gradle/               # Version Catalog / Wrapper
└── scripts/              # Windows検証スクリプト
```

パッケージは `core/model`, `core/design`, `core/media`, `data/local`, `data/repository`, `feature/*` に分離しています。外部サービス追加時は `data/remote` に実装を追加し、画面からRepository interface以外へ依存しない構成を維持します。

## Windowsでのビルド

前提: Android Studio、Android SDK Platform 37、Build Tools 36.0.0。JDKはAndroid Studio同梱JBRを利用できます。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:GRADLE_USER_HOME = "$PWD\.gradle-user-home"
.\gradlew.bat :android:app:assembleDebug :android:app:testDebugUnitTest
```

または:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1
```

connected testまで実行する場合:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1 -IncludeConnectedTests
```

このスクリプトは対象アプリがインストール済みなら、connected test前にアプリ専用 `files` を `verification/pre-connected-*.tar` へ退避し、テスト後に復元します。テスト中断などで復元できなかった場合もこのtarを保持します。ただし、ユーザーデータを保護するため、通常はRealMeのデータが入ったAVDではなくテスト専用AVDを使用してください。

`GRADLE_USER_HOME` をリポジトリ内へ設定するため、Wrapperと依存関係のキャッシュは `.gradle-user-home/`（Git無視）に保存されます。Android Studioで開く場合はrootの `settings.gradle.kts` を開いてください。

## Pixel 8 / API 37エミュレーター

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Pixel_8
```

端末テストはユーザーデータを保護するため、テスト専用AVDで実行してください。ADBを直接使う場合は `-s emulator-5554` のようにエミュレーターのシリアルを必ず指定し、物理端末を操作しないでください。

## 撮影の手動確認

1. ローカルアカウントで開始する。
2. 「ライブラリ」タブで「ユーザー補助を有効にする」を押し、Androidの「設定 > ユーザー補助 > RealMe 画面撮影」をユーザー自身がオンにする（初回のみ）。
3. 撮影方法は次の3つ。
   - ライブラリの「撮影ボタンを表示」→ ゲームなどへ切り替えて丸いボタンをタップ
   - クイック設定の「RealMe撮影」タイル（「クイック設定に撮影タイルを追加」から追加）
   - Android標準のスクリーンショット（電源＋音量下）。エミュレーターでは下のスクリプトを使う
4. スクリーンショット検知では「スクリーンショットを投稿しますか？」が出る。「はい」で「画像を保存しました」「HH:mmに自動投稿されます」。
5. アプリへ戻り、撮影候補のプレビュー・選択・削除を確認する。

端末側のスクリーンショットを撮るスクリプト（エミュレーターのツールバーのカメラはPC保存のため検知されません）。

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\take-screenshot.ps1
```

`scripts\take-screenshot.bat` をダブルクリックしても実行できます。ゲームなど別のアプリを前面にしてから実行してください。

撮影時はボタンを約120ms非表示にしてから画面を取得するため、ボタン自体は写り込みません。画面回転はAPI 37エミュレーターで確認済みですが、物理端末の機種差、FLAG_SECUREを使うゲーム、実際のゲーム描画との整合は追加検証が必要です。

## ローカルMVPの制約

- タイムラインは端末内データであり、インターネット上の全ユーザーへ公開されません。
- ダミー認証は実際のSupabase Anonymous Sign-Inではありません。
- 日時は端末時刻を使うため、本番のサーバー時刻保証や端末時刻改変対策は未実装です。
- 日次イベントと権限確認はローカルRepositoryの制約であり、PostgreSQLのRLSではありません。同じ日の複数投稿はローカルで許可しています。
- WorkManagerは正確な公開時刻を保証しません。アプリ・端末が停止していても確実に公開するにはサーバーCronが必要です。
- FCMプッシュ、通報・ブロック、正式アカウントは未実装です。他人の投稿画像を保存する機能と保存通知は削除しました。

## 秘密情報

Supabase Service Role Key、Firebaseサービスアカウント鍵、署名鍵をGitへ追加しないでください。現在、外部サービスの設定ファイルと秘密鍵は不要です。




