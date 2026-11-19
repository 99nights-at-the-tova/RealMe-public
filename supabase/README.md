# Supabase（未設定）

現行のオフラインMVPではSupabaseプロジェクト、Migration、Edge Functions、Cronを作成していません。秘密鍵の取得や外部接続も行いません。

本番移行時は `docs/ARCHITECTURE.md` と `docs/API_CONTRACT.md` を起点に、Auth UUID、PostgreSQL UNIQUE制約、RLS、非公開Storage、サーバー時刻、冪等な自動公開を先に設計します。
