# 協力者向け引き継ぎ

更新: 2026-11-19。まずこの文書を読み、現在の状況は [HANDOFF](HANDOFF.md)、予定は [ROADMAP](ROADMAP.md)、実装上のルールは [AGENTS](../AGENTS.md) を参照してください。

デモ動画は公開用コピーには含めていません。

## アプリの目的と現状

RealMeは、ゲーム中に通知を受け取り、その場の画面を撮影して共有するAndroidアプリを目指しています。現在は**端末内で完結するオフラインMVP**です。投稿やアカウントはその端末に保存され、協力者同士の端末に同期されません。Supabase、Firebase、FCM、実際のサーバーや複数端末への配信はまだありません。

現在あるもの: ローカルアカウント・任意PIN、投稿とタイムライン、プライバシー画像、自動投稿、撮影候補の選択、画像編集、アカウント削除と後片付けの再試行。UIはダーク基調で、大文字Rのロゴを使用しています。

このバージョンでは、イベント通知（ランダム通知・勧誘通知）は廃止しています。撮影はライブラリの「撮影ボタンを表示」、クイック設定の「RealMe撮影」タイル、またはAndroidのスクリーンショット検知から行います。ゲーム（`appCategory=game` のアプリ）中にスクリーンショットを撮ると「スクリーンショットを投稿しますか？」という通知が出て、「はい」を押すとその画像が撮影候補として登録され「画像を保存しました」「HH:mmに自動投稿されます」と通知されます。検知は撮影した時点でゲームが前面かで判定するため、ゲームを開いた直後だけでなくプレイ中のいつでも対象になります。ゲームを離れたあと（ランチャーやRealMeの画面）のスクリーンショットは対象外です。アプリのアイコンとは別に、クイック設定へ「RealMe撮影」タイルを追加できます。ライブラリタブの「クイック設定に撮影タイルを追加」から追加すると、ゲーム中でも通知シェードからタップするだけで撮影でき、「投稿しますか？」が出ます。通知をタップすると編集画面が開き、投稿するか、×から「はい」で投稿を破棄して「ライブラリ」タブに残せます。次回の通知表示時に古い撮影候補を整理しますが、投稿や有効な下書きなどから参照される画像は保持します。

## GitHubにあるもの・ないもの

- アプリのコード、ビルド設定、テスト、ドキュメントは公開用リポジトリにあります。
- APK、ビルドキャッシュ、Android Studioの個人設定、端末固有の `local.properties`、検証用バックアップ、エミュレーター本体やその中のアカウント・画像・アプリは含まれません。各自のPCで構築します。
- ローカルMVPの実行にSupabaseやFirebaseの認証情報は不要です。秘密鍵・署名鍵・個人の端末データをリポジトリへ追加しないでください。

## 実際に使用した環境

元のPCはWindows、Android Studioビルド `262.10968.63.2622.16840690`（設定ディレクトリ名 `AndroidStudio2026.2.2`）です。アプリはKotlin / Jetpack Compose、Gradle Wrapper 9.6.0、Android Gradle Plugin 9.5.1、compile/target SDK 37、min SDK 29です。JDKはAndroid Studio同梱のJBRを使用しました。SDK Platform 37とBuild Tools 36.0.0が必要です。

現在そのPCで登録を確認できたAVDは `Pixel_8`（表示名 Pixel 8）、API 37、Google Play x86_64、RAM 2048 MB、データ領域10 GBです。Android StudioのDevice Managerで同等の仮想端末を作成できます。AVD名は各自で決められます。

過去のテストでは専用AVD `RealMe_Test` / API 37を使用しましたが、今回の確認時には登録AVD一覧に現れていません。再現する場合は自分のPCにテスト専用AVDを作成してください。ADBシリアル `emulator-5554` / `emulator-5556` は起動状況で変わるため固定値として扱わず、毎回 `adb devices -l` と `adb -s <serial> emu avd name` で確認します。物理端末には勝手に操作しません。

直近の手動撮影では、撮影対象としてエミュレーター標準の **Android設定** を使用しました。ブラウザや実ゲームでの今回の通し試験は未実施です。利用した追加アプリの網羅的な一覧や、元のAVD内のデータは記録・共有されていません。

## 自分のPCで起動する

1. このリポジトリをクローンします。Android Studioでリポジトリのルートを開き、Gradleの同期を待ちます。
2. SDK ManagerでSDK Platform 37、Build Tools 36.0.0、Android Emulatorと対応するシステムイメージを用意します。Device ManagerでPixel 8 / API 37相当のAVDを作成・起動します。
3. Android Studioから `android/app` のDebug構成を実行します。初回は自分のエミュレーターにローカルアカウントを作成します。相手の投稿やアカウントは表示されません。
4. 通知を許可し、「ライブラリ」→「ユーザー補助を有効にする」を選択します。初回のみAndroidの「設定 > ユーザー補助 > RealMe 画面撮影」をオンにします。
5. Debug版のタイムラインにある「時間内のデモ」で通知を試せます。通知処理が完了したら別アプリ上で撮影ボタンを押し、撮影後の「HH:mmに自動投稿されます」通知を確認します。取り直す場合は、その時刻までにアプリのライブラリタブで「もう一度撮影する」を押します。自動投稿の時間はプロフィール→自動投稿で設定します。すでに進行中の下書きには設定変更が反映されません。

ユーザー補助サービスを無効化・停止した場合は、再度ユーザー補助を有効にする必要があります。通知の実行はWorkManagerのため、正確な時刻や端末停止中の実行は保証されません。

## ビルドとテスト

Windows PowerShellでリポジトリのルートから実行する例です。Android Studioのインストール場所は環境に合わせてください。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :android:app:build :android:app:testDebugUnitTest :android:app:lintDebug :android:app:assembleDebugAndroidTest --no-daemon
```

端末テストはアプリの再インストール等によりデータが消えることがあります。日常使用AVDには直接実行せず、**専用のテストAVD**を使ってください。ビルド後、対象がテストAVDであることを確認して実行します。

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices -l
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s <確認したエミュレーターのシリアル> emu avd name
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s <確認したエミュレーターのシリアル> install -r android/app/build/outputs/apk/debug/app-debug.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s <確認したエミュレーターのシリアル> install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s <確認したエミュレーターのシリアル> shell am instrument -w com.realme.test/androidx.test.runner.AndroidJUnitRunner
```

`<確認したエミュレーターのシリアル>` は山括弧ごと実際の値に置き換えます。

直近結果はビルド・単体21件成功、Lint 0 errors / 1 warning。端末55件も成功しましたが、その後、遅延した通知予定の回復処理を追加し、ビルド・単体・Lintのみ再実行しています。**最後の変更を含む端末テストは未実行**です。

## 次に確認・対応すること

1. 最新コードで専用AVDの端末テストを再実行する。
2. 通知→別アプリ上で複数回撮影→投稿前に候補変更→編集保存→自動投稿で、公開画像と撮影時刻を通し確認する。実ゲームと長時間バックグラウンド、日付またぎも別途確認する。
3. オフラインMVPの先は、方針を決めてからSupabase Auth / DB / Storage / RLS / Cron、FCM、通報・ブロックなどを進める。サーバーへの移行はまだ着手済みとみなさない。

詳しいテスト内容・制約は [HANDOFF](HANDOFF.md) を参照してください。






