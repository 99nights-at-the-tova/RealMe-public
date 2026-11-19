# DECISIONS

## 2026-11-15 オフライン優先

Supabase / Firebase未設定のため、認証とSNSをDataStore + ファイル領域のローカル実装で開始する。外部接続やキーを追加しない。

## 2026-11-15 API 37に合わせた最新安定基盤

AGP 9.5.1 / Gradle 9.6.0 / Compose BOM 2026.09.00を採用。AGP built-in Kotlinを使い、Kotlin android pluginは適用しない。Composeとserializationのcompiler pluginのみ適用する。

## 2026-11-15 RoomではなくDataStore JSON

オフラインMVPのデータ量が小さく、2人開発の初期結合を簡単にするため。投稿一意性などの重要制約は将来PostgreSQLへ移し、ローカル実装を本番と誤認しない。

## 2026-11-15 画像の永続化

Photo PickerのURI権限だけへ依存せず、選択画像をアプリ専用領域へコピーする。公開投稿とプライバシー画像の差し替え時は、管理下の元ゲーム画像を削除する。既に他端末へ保存されたコピーの回収は保証しない。
