# ADR 0002: 名刺画像フォルダは SAF で指定する

## Status

- Accepted

## Decision Drivers

- 利用者が任意のフォルダを選び、そこにある名刺画像を取り込みたい
- ストレージ全体への広い読み取り権限を要求したくない
- Android のバージョンごとにストレージ API が異なるため、分岐を増やしたくない
- アプリ再起動後も同じフォルダを参照し続けたい

## Context

名刺画像は利用者が既に持っている写真フォルダやクラウド同期フォルダに置かれている。アプリが場所を決めるのではなく、利用者が指定する必要がある。

Android には大別して三つの方法がある。

1. アプリ専用領域にだけ置かせ、共有はインポート画面で行う
2. `READ_MEDIA_IMAGES` などのメディア権限で端末全体を走査する
3. Storage Access Framework（SAF）でフォルダを一つ選ばせる

2 は対象フォルダを絞れず、権限の説明も広くなる。1 は利用者の既存フォルダを使えない。

## Options Considered

- アプリ専用領域のみ: 権限は最小だが、利用者が毎回コピーする手間が増える
- メディア権限で全体走査: 実装は短いが、対象外の写真まで見える権限になる
- SAF でフォルダを指定: 利用者が範囲を決められ、権限も選択した tree に限定できる

## Decision

- 名刺画像の取り込み元は、`ACTION_OPEN_DOCUMENT_TREE` で利用者が選んだフォルダ（tree URI）とする
- 選択時に `takePersistableUriPermission` で読み取り権限を永続化し、再起動後も同じフォルダを参照する
- アプリは広いストレージ権限（`MANAGE_EXTERNAL_STORAGE`、`READ_MEDIA_IMAGES` など）を要求しない
- フォルダ内の列挙と読み出しは `DocumentsContract` / `ContentResolver` を通す。ファイルシステム上の実パスは前提にしない
- 権限が失効した場合は、解析を続けずにフォルダの再選択を求める

## Consequences

- Positive: 利用者が選んだフォルダだけを読み、権限の範囲を説明しやすい
- Positive: クラウドの document provider も同じ手順で扱える
- Negative: 実パスや `File` API が使えず、列挙・名前取得・読み出しが provider 経由になる
- Negative: フォルダの移動や削除、権限の失効を利用者ごとに検出して案内する必要がある
- Follow-up: 複数フォルダを同時に扱う要件が出た場合は、保存する tree URI を集合へ広げる新しい ADR を検討する

## Adoption and Exceptions

- 名刺画像の読み出しは保存済み tree URI の子孫に限る
- フォルダ選択以外の場所から画像を読む機能を追加するときは、この ADR を更新する
- `READ_MEDIA_IMAGES` や全ファイルアクセスを追加しない
