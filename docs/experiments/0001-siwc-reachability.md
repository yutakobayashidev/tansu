# E1–E3: Sign in with ChatGPT の疎通・画像入力・json_schema 可否

- Status: Proposed
- Date: 2026-11-26
- 関連: ADR 0001（SIWC は機能フラグで隔離）
- 目的: SIWC のフラグを外せるかを、3つの実測で決める

## 前提

- Linux、`curl`、`jq`、`python3`、`openssl`
- ChatGPT Go / Plus / Pro / Business のいずれかの契約
- 作業ディレクトリは**リポジトリの外**: `/tmp/opencode/siwc-spike`
- テスト画像は自分で作ったダミー名刺。実在の知人の名刺は使わない（画像は OpenAI に送信される）
- アクセストークン、認可コード、発行された `client_id` を Git・ログ・Issue に貼らない

## 記録表

| 項目 | 値 |
|---|---|
| 実施日 / 実施者 | |
| 契約プラン | |
| E1 結果（response.completed まで到達） | |
| 発行 client_id の prefix（`oaiapp_...` の有無） | |
| 付与スコープに `chatgpt.tokens.use.direct` があるか | |
| E2 で使えたモデル slug | |
| E2 抽出できた FN（ダミー名刺と一致したか） | |
| E3 の `text.format` 可否 | |
| 各段の所要時間 | |

## E1: Responses API への疎通

### 1. PKCE・state・nonce・host ID を作る

```bash
mkdir -p /tmp/opencode/siwc-spike && cd /tmp/opencode/siwc-spike
python3 - <<'PY'
import base64, hashlib, os, secrets, uuid
v = base64.urlsafe_b64encode(os.urandom(48)).rstrip(b'=').decode()
c = base64.urlsafe_b64encode(hashlib.sha256(v.encode()).digest()).rstrip(b'=').decode()
state, nonce = secrets.token_urlsafe(24), secrets.token_urlsafe(24)
host = "urn:uuid:" + str(uuid.uuid4())
open("pkce.txt", "w").write(f"{v}\n{state}\n{nonce}\n{host}\n")
print("verifier :", v)
print("challenge:", c)
print("state    :", state)
print("nonce    :", nonce)
print("host     :", host)
PY
```

### 2. コールバックリスナを先に起動する

`redirect_uri` のパスは `/auth/callback` 固定、ホストは `127.0.0.1`、ポートのみ可変。`localhost` は不可。

```bash
python3 - <<'PY'
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs
class H(BaseHTTPRequestHandler):
    def do_GET(self):
        q = parse_qs(urlparse(self.path).query)
        with open("/tmp/opencode/siwc-spike/callback.txt", "w") as f:
            for k, v in q.items():
                f.write(f"{k}={v[0]}\n")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write("<p>done. go back to the terminal.</p>".encode())
        self.server.shutdown()
    def log_message(self, *a):
        pass
HTTPServer(("127.0.0.1", 1455), H).serve_forever()
PY
```

### 3. 認可 URL を開く

各値を URL エンコードして組み立てる。

```
https://auth.openai.com/api/accounts/authorize
  ?client_id=dynamic_agent_client
  &agent_name_hint=tansu
  &ext_agent_host_id=<host>
  &response_type=code
  &redirect_uri=http%3A%2F%2F127.0.0.1%3A1455%2Fauth%2Fcallback
  &scope=openid+profile+email+offline_access+resource.invoke+chatgpt.tokens.use.direct
  &resource=https%3A%2F%2Fapi.openai.com%2Fv1
  &state=<state>
  &nonce=<nonce>
  &code_challenge_method=S256
  &code_challenge=<challenge>
```

承認後、`callback.txt` に `code` と `state`、新規登録なら発行された `client_id`（`oaiapp_...`）が入る。`state` が一致することを確認する。

### 4. トークン交換

`client_id` には**発行された値**を使う。`dynamic_agent_client` を保存・使用してはいけない。

```bash
curl -sS https://auth.openai.com/api/accounts/oauth/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode grant_type=authorization_code \
  --data-urlencode client_id=<issued client_id> \
  --data-urlencode code=<code> \
  --data-urlencode code_verifier=<verifier> \
  --data-urlencode redirect_uri=http://127.0.0.1:1455/auth/callback \
  --data-urlencode resource=https://api.openai.com/v1 \
  -o token.json
jq '{token_type, expires_in, scope, has_access: (.access_token != null), has_refresh: (.refresh_token != null)}' token.json
```

### 5. モデル一覧

```bash
AT=$(jq -r .access_token token.json)
curl -sS https://api.openai.com/v1/models -H "Authorization: Bearer $AT" \
  | jq -r '.models[] | select(.visibility == "list") | .slug' | head -20
```

### 6. テキストだけで 1 回呼ぶ

```bash
curl -N -sS https://api.openai.com/v1/responses \
  -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d '{
    "model": "<slug>",
    "instructions": "Return the single word OK and nothing else.",
    "input": [{"role": "user", "content": [{"type": "input_text", "text": "OK?"}]}],
    "store": false,
    "stream": true
  }'
```

`response.completed` が来れば E1 通過。`temperature`、`max_output_tokens`、system ロールのメッセージは送らない。

## E2: 画像入力

ダミー名刺を長辺 1600px 程度の JPEG にして使う。

```bash
IMG=$(base64 -w0 card.jpg)
jq -n --arg img "data:image/jpeg;base64,$IMG" --arg m "<slug>" '{
  model: $m,
  instructions: "Extract every business card in the image as JSON. Do not guess values that are not printed.",
  input: [{role: "user", content: [
    {type: "input_text", text: "List the cards."},
    {type: "input_image", image_url: $img}
  ]}],
  store: false,
  stream: true
}' > req.json
curl -N -sS https://api.openai.com/v1/responses \
  -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' --data @req.json
```

判定: ダミー名刺の `FN` が正しく返る。400 ならエラーコードと `param` を記録し、別の `slug` で再試行する。

## E3: json_schema

E2 のボディに `text` を足す。strict モードでは全プロパティが `required`、`additionalProperties: false`。無い値は空文字で返させる。

```json
"text": {
  "format": {
    "type": "json_schema",
    "name": "cards",
    "strict": true,
    "schema": {
      "type": "object",
      "additionalProperties": false,
      "required": ["cards"],
      "properties": {
        "cards": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["fn", "family", "given", "org", "title", "tel", "email", "adr", "url", "note", "uncertain_fields"],
            "properties": {
              "fn": {"type": "string"},
              "family": {"type": "string"},
              "given": {"type": "string"},
              "org": {"type": "string"},
              "title": {"type": "string"},
              "tel": {"type": "array", "items": {"type": "string"}},
              "email": {"type": "array", "items": {"type": "string"}},
              "adr": {"type": "string"},
              "url": {"type": "string"},
              "note": {"type": "string"},
              "uncertain_fields": {"type": "array", "items": {"type": "string"}}
            }
          }
        }
      }
    }
  }
}
```

判定: 200 かつスキーマ一致の JSON。400 なら設定値 `structured_output = off`（プロンプト指示 + パース）を既定にする。

## 判定と分岐

| E1 | E2 | E3 | 決定 |
|---|---|---|---|
| pass | pass | pass | フラグ解除可。既定モデル = E2 で通った slug。`structured_output = auto` |
| pass | pass | fail | フラグ解除可。`structured_output = off` を既定に |
| pass | fail | — | SIWC はこの用途に使えない。BYO のみで出荷し、SIWC は保留 |
| fail | — | — | 参加資格・プラン・環境の問題。エラーコードで切り分ける |

## 失敗時の切り分け

| 症状 | 見るもの | 対処 |
|---|---|---|
| `invalid_grant` | code / verifier / redirect_uri の不一致 | 新しい認可からやり直す |
| `subscription_sharing_usage_unavailable` | プラン未有効 | 契約とスコープを確認 |
| `subscription_sharing_usage_limit_exceeded` | 上限到達 | 時間を置く。アプリ側は R27 の専用表示 |
| `/v1/models` が 403 | スコープ不足 | 付与スコープを記録 |
| 400 `unsupported_parameter` | 送ってはいけない項目 | `temperature`・`max_output_tokens`・system を除去 |

## 後始末

- `/tmp/opencode/siwc-spike` を削除する。`token.json` を残さない。
- Issue には結果（可否・エラーコード・slug）だけを書き、トークンと認可コードを書かない。

## 見積

E1 30分 / E2 20分 / E3 20分。E4（Android のループバック実機）は本手順の対象外で、別途半日。
