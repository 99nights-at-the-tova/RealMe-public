# DATABASE（ローカルMVP）

Supabase DBは未作成。DataStore Preferencesの `app_snapshot` に `AppSnapshot(schemaVersion = 1)` をJSON保存する。

- `profiles`: id / displayName / bio
- `session`: 匿名ユーザーID（端末内のダミー認証）
- `events`: id / eventDateJst / notificationAt / deadlineAt
- `posts`: id / userId / eventId / eventDateJst / imagePath / imageType / capturedAt / postedAt / status / caption
- `captures`: id / userId / eventId / filePath / capturedAt / eligible
- `draft`: userId / eventId / selectedCaptureId / configuredMinutes（作成時の設定スナップショット）/ firstCapturedAt / autoPublishAt / editState / editedImagePath / status
- `settings`: autoPostMinutes / overlay size / opacity / x / y / privacyImagePath
- `imageSaves`: id / postId / savedByUserId / createdAt（postId + savedByUserIdで重複防止）
- `notifications`: id / userId（投稿者）/ type / title / message / postId / actorUserId / createdAt / readAt
- `accountPins`: userId / pinHash / salt / updatedAt / failedAttempts / lockedUntil（PBKDF2-SHA256。PIN平文は保存しない。5回失敗で30秒ロック）

画像本体はアプリ専用ファイル領域。ローカル制約はサーバー側UNIQUE / RLSの代替ではなく、本番移行時にDBでも強制する。
