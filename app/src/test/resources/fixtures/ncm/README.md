# NetEase API fixtures

## Captured

Taken from the deployment with the development token, then trimmed.

- `login_status_signed_out.json` — real response, no cookie.
- `artist_detail.json` — real `/artist/detail?id=9272` (public artist data), trimmed to the
  artist object plus a few extra keys; `briefDesc` shortened, `identify`, `blacklist`,
  `secondaryExpertIdentiy` dropped. Track `ar` entries carry no image, hence this call.

## Synthetic

Built from the documented shape and the real `/user/detail` payload. NetEase only returns these
fields when signed in, and there is no automation account.

- `user_account_signed_in.json` — every value is fake. `example.invalid` hosts and a `90000001`
  user id make that obvious.
- `lyric_new_translated.json` — `/lyric/new` shape with invented text: `[mm:ss:cc]` stamps,
  multi-stamp lines, a break line, JSON credits, tlyric offset ~30 ms from lrc, and extra keys
  (`klyric`, `sgc`, `transUser`) to prove `ignoreUnknownKeys`.
- `lyric_new_word_timed.json` — invented yrc word timing `[s,d](ws,wd,i)word` whose line starts
  differ from lrc by seconds, plus JSON credits with negative `t`.
- `lyric_new_pure_music.json` — `pureMusic: true` with invented JSON credits and the
  placeholder instrumental line.

Extra fields are included on purpose: they prove that `ignoreUnknownKeys` holds when NetEase adds
or renames something.
