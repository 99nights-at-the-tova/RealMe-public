# ARCHITECTURE

## 概要

シングルActivity + Navigation Compose + MVVM。機能ごとのViewModelがRepository interfaceへ依存し、`AppContainer` がローカル実装を供給する。Supabase / Firebase SDKはまだ導入しない。

## データの境界

- `core/model`: 共有ドメインモデルと日時・連続記録・自動投稿の純粋な計算
- `data/repository`: Auth / Post / Event / Capture / Settingsのinterface
- `data/local`: DataStoreスナップショット、画像コピー、ローカルRepository実装
- `feature/*`: Compose画面とViewModel
- `core/media`: AccessibilityServiceによるスクリーンショット撮影とoverlayのAndroid実装

`LocalAppStore` がPreferences 1件にバージョン付きJSONを保存し、画像本体は `filesDir/images` と `filesDir/captures` に分ける。DataStoreの更新はMutexで直列化する。同じユーザー・同じ日付の投稿は複数許可し、タイムラインでは1件にまとめて横スライドで表示する。将来はDBのインデックスとページングへ移す。

## ON TIME / LATE

判定は画像の `capturedAt` とサーバー移行予定の `DailyEvent.notificationAt` を使う。通知前の画像もゲーム投稿に使え、`notificationAt + 5分` を含む範囲をON TIME、超過をLATEとする。当日締切後の投稿だけを拒否する。PRIVACYは別状態。

## 自動投稿

初回の有効な撮影時刻T0で下書きを1件作成し、未編集時は `min(T0 + 設定分数, T0 + 15分, JST当日24時)` を採用する。元の期限より前に編集を開始した場合、設定分数を上限45分で再計算し、当日24時を超えない範囲へ延長する。保存済み編集画像を優先し、未保存の編集がある場合は元画像を公開せずプライバシー画像へフォールバックする。選択候補の変更時は以前の編集結果を解除する。WorkManagerのunique workで公開し、手動投稿・キャンセル時はworkを取り消す。端末制約により正確時刻や実行を保証せず、将来はサーバー側への移行を検討する。

## 撮影

ユーザー補助サービス（`ScreenshotAccessibilityService`）が `takeScreenshot()` で1枚ずつスクリーンショットを取得する。MediaProjectionの同意ダイアログとForeground Serviceは使わない。初回だけ「設定 > ユーザー補助」でサービスを有効にすれば、通知が届いたときに `TYPE_ACCESSIBILITY_OVERLAY` の撮影ボタンが自動表示される。通知はタップしてもアプリを開かず、約15分で自動的に消える。撮影ボタンも約15分で消え、1枚撮影するか画面外へスワイプすると閉じる。オーバーレイは別途の「他のアプリの上に表示」許可を必要としない。

撮影ボタンは押下時にいったん非表示にし、画面へ写り込まないようにしてから撮影する。保存はアプリ専用領域へJPEGで行い、撮影候補のRepository登録と通知は従来どおり。MediaProjectionの同意トークンは保持しない。アカウント削除時は `CaptureSessionState.stopAndAwait()` でオーバーレイを閉じ、実行中の保存・登録完了を待ってから削除する。API 30未満では撮影機能を利用できない。

撮影後は、自動投稿される時刻（設定分数から算出）を知らせる通知を出す。通知をタップするとホームの上に撮影画像の編集画面を開く。編集画面の×から投稿を破棄すると、進行中の自動投稿だけを取り消して撮影画像は「ライブラリ」タブに残す。「この画像を公開」で投稿してホームへ戻り、投稿した撮影候補は削除する。ライブラリタブは「あとで編集」（当日）と「過去のアーカイブ」（過去）に分ける。取り直す場合は、自動投稿時刻までにライブラリタブの「もう一度撮影する」から撮影ボタンを再表示して撮影する。

このバージョンでは、ゲーム検知や固定時刻によるイベント通知（ランダム通知・勧誘通知）は廃止した。撮影はライブラリの「撮影ボタンを表示」、クイック設定の「RealMe撮影」タイル、Androidのスクリーンショット検知から行う。 `ScreenshotAccessibilityService` は前面アプリの記録だけを続け、スクリーンショット検知の判定に使う。ライブラリタブからホーム画面にRealMeのショートカットを追加できる。

イベント通知を廃止したため、設定画面のランダム通知Switchも削除した。 `UserSettings.randomNotificationEnabled` は既存データとの互換のためモデルに残しているが、現在は使用しない。 `DailyCaptureReminderWorker` と `InactivityReminderWorker` も削除し、通知はスクリーンショットの投稿確認（4104）と撮影結果（4102）だけにした。

ゲーム中のスクリーンショットは `MediaStore` の変更を `ContentObserver` で監視して検知する（`READ_MEDIA_IMAGES` / Android 12以下は `READ_EXTERNAL_STORAGE`）。Android 14以降で「一部の写真のみ許可」を選ばれると新しいスクリーンショットを読めず検知できないため、`READ_MEDIA_VISUAL_USER_SELECTED` で部分許可を検出し、起動時にフルアクセスへ戻せるようもう一度確認する。

クイック設定の「RealMe撮影」タイル（`ScreenshotTileService`）からも、ゲーム中いつでも撮影できる。タップすると透明な `ScreenshotTriggerActivity` を挟んで通知シェードを閉じ、`ScreenshotAccessibilityService.takeScreenshot()` で1枚撮って「スクリーンショットを投稿しますか？」の通知を出す。撮影画像は `files/screenshotprompt` へ一時保存し、「はい」で撮影候補へ取り込み、「いいえ」または通知の失効時に削除する。ホーム画面のアプリアイコンとは別の導線で、アプリを開かずに使える。直近60秒以内に追加され、`Screenshot` を含むパス・ファイル名を持つものを対象にする。投稿確認を出すかどうかは、スクリーンショットを撮った時点でゲーム（Androidの `appCategory=game`）が前面だったかで判定する。スクリーンショットのプレビューや権限ダイアログなど一瞬だけ前面に出るシステムUIはゲームの文脈を消さない扱いとし、ランチャーやRealMe自身へ移ったときだけ文脈を終了する。「スクリーンショットを投稿しますか？」通知の「はい」は `ReminderActionReceiver` へブロードキャストし、アプリを開かずに当日イベントの時刻更新・画像の取り込み・撮影候補登録・自動投稿下書き作成を行い、「HH:mmに自動投稿されます」と通知する。「いいえ」は通知を閉じるだけで画像は投稿しない。検知の重複を避けるため処理済みの画像IDを保持する。

画面テーマは `core/design/Theme.kt` の `darkColorScheme`（背景 `#0E120F`、サーフェス `#161B18`、アクセントのミントとラベンダー）に統一し、`MainActivity` の `SystemBarStyle.dark`、`themes.xml`、`colors.xml` もダーク前提にしている。

## 今後の差し替え

`data/remote` にSupabase実装を追加し、認証UID・Storage path・サーバー時刻・RLSを正とする。ローカルJSONを本番DBの代替とみなさない。




