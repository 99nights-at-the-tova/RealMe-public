# API CONTRACT

現行はローカルMVPのinterface契約。将来のSupabase API契約ではない。

- `AuthRepository`: 匿名セッションと端末内アカウント一覧・PIN保護IDの監視、新規アカウント作成、既存ローカルIDへのPIN付き切り替え、起動時PIN検証、5回失敗の30秒ロック、PIN設定・変更・解除、サインアウト。表示名・bio更新は `ProfileRepository` が担当
- `PostRepository`: 新着順投稿の監視、通常/プライバシー投稿、自分の投稿削除、公開後のプライバシー画像差し替え、下書き公開/キャンセル、編集開始（EDITING）、確定済み編集画像の登録（READY）
- `EventRepository`: JST日次イベントの監視とデバッグ用イベント開始
- `CaptureRepository`: ローカル撮影候補の監視・登録・削除、初回撮影時の下書き作成
- `SettingsRepository`: 自動投稿分数、overlay位置・サイズ・透明度、プライバシー画像の監視・更新
- `AccountDeletionRepository`: 現在セッションとPINを確認し、撮影Serviceの停止を待ってから、本人のプロフィール・投稿・撮影・下書き・設定・PIN・通知・保存履歴（旧データ）をローカルから削除する
- `CaptureSessionController`: 稼働中のスクリーンショット撮影セッションを停止し、実行中の保存・登録の完了またはタイムアウトを返す

Repositoryは処理に応じて `Flow`、`Result`、または値を返す。Photo Pickerで選んだ画像はアプリ専用領域へ複製する。他人の投稿画像を端末へ保存する機能は削除した。

