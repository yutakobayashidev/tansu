# Architecture Decision Records

このディレクトリには、tansu の実装を長期的に拘束する技術判断を記録します。

## 運用

- 1判断につき1ファイルを使う
- ファイル名は `NNNN-short-decision-name.md` とする
- 合意前は `Proposed`、採用後は `Accepted` とする
- 判断を変更するときは過去の記録を書き換えず、新しい ADR から `Superseded` にする
- 実装や設定を変更するときは、関連 ADR も同じ変更で更新する
- 用語は [GLOSSARY.md](../../GLOSSARY.md) に従う

## 一覧

| ADR | Status | Decision |
|---|---|---|
| [0001](0001-dual-auth-byo-first-with-flagged-chatgpt-sign-in.md) | Accepted | Sign in with ChatGPT を主、BYO API キーを従とし、出荷は BYO 先行・SIWC は機能フラグで隔離する |
| [0002](0002-select-image-folder-via-saf.md) | Accepted | 名刺画像フォルダは SAF で指定する |
| [0003](0003-extract-all-cards-and-keep-original-images.md) | Accepted | 1画像から N 件の名刺を全件抽出し、画像は無加工で複製保持する |
| [0004](0004-export-vcard4-and-csv-with-conditional-photo.md) | Accepted | 出力は vCard 4.0 + CSV とし、PHOTO は候補が1件のときだけ縮小版を埋め込む |
