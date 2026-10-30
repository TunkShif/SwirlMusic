# NetEase API fixtures

## Captured

Taken from the deployment with the development token, then trimmed.

- `login_status_signed_out.json` — real response, no cookie.

## Synthetic

Built from the documented shape and the real `/user/detail` payload. NetEase only returns these
fields when signed in, and there is no automation account.

- `user_account_signed_in.json` — every value is fake. `example.invalid` hosts and a `90000001`
  user id make that obvious.

Extra fields are included on purpose: they prove that `ignoreUnknownKeys` holds when NetEase adds
or renames something.
