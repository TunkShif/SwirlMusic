# NetEase API fixtures

## Captured

Taken from the deployment with the development token, then trimmed.

- `login_status_signed_out.json` — real response, no cookie.
- `artist_detail.json` — real `/artist/detail?id=9272` (public artist data), trimmed to the
  artist object plus a few extra keys; `briefDesc` shortened, `identify`, `blacklist`,
  `secondaryExpertIdentiy` dropped. Track `ar` entries carry no image, hence this call.
- `album_detail_dynamic.json` — real `/album/detail/dynamic?id=1089008`; `isSub: true`.
- `album_detail.json` — its `album.artists` array was added from a live `/album?id=1571654`
  read on 2026-11-08 (the original capture had it trimmed).
- `artist_detail_dynamic.json` — real `/artist/detail/dynamic?id=`; `followed: true`.
- `album_sub.json` — real `/album/sub?t=1` success body.
- `album_sub_locked.json` — real `/album/sub?t=1` refusal body, answered as HTTP 401.
- `album_unsub_not_saved.json` — real `/album/sub?t=0` body when the album isn't saved,
  answered as HTTP 404.
- `artist_sub.json` — real `/artist/sub` success body.
- `artists.json` — real `/artists?id=6452`, `hotSongs` trimmed to 3.
- `artist_album.json` — real `/artist/album?id=6452`, `hotAlbums` trimmed to one per
  `type`/`subType` variant and the `artist` object dropped.
- `artist_desc.json` — real `/artist/desc?id=6452`, `briefDesc` and `txt` cut to 200 chars,
  `introduction` trimmed to 2, `topicData`/`count` dropped.
- `cloudsearch_songs.json` — real `/cloudsearch?keywords=magdalena bay&type=1&limit=3`; per-song
  audio payloads (`privilege`, `h`, `m`, `l`, `sq`, `rt*`) dropped, `al`/`ar`/`dt`/`id`/`name`/
  `fee`/`no`/`cd` kept — `/cloudsearch` spells artists and album `ar`/`al`, like `/song/detail`.

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
- `like_true.json` — real `/like?id=…&like=true` body; `playlistId` is the liked-songs list.
- `like_false.json` — real `/like?id=…&like=false` body; identical shape to the like answer.
- `like_bad_id.json` — real `/like?id=1` body; HTTP 200 carrying `code:400`, i.e. a refusal.
- `record_recent_album.json` — real `/record/recent/album?limit=3` (2026-11-20). Item
  `data` trimmed to the fields the mapper reads plus `type`/`subType`/`alias`/`company`/
  `publishTime` for `ignoreUnknownKeys`; `songs`/`lastSong` dropped (that pair is ~90% of the
  body). Public album data; nothing user-identifying.
- `comment_new_*.json` — real `/comment/new` bodies (2026-11-16), comments trimmed to 2–4 per
  file. Redacted: `nickname` → `Listener N`, `userId` → `9000000N`, `encryptUserId` replaced,
  every image URL → `example.invalid`. Comment text is kept. `song_recommended` (song 186016,
  sort 99 page 1), `song_recommended_page2` (sort 99 page 2: `hasMore` false, cursor `"0"`),
  `song_hot` (sort 2, cursor `normalHot#20`), `album_newest` (album 18905, sort 3, ms cursor,
  one `beReplied` entry), `empty` (song id 1), `bad_resource` (`type=9`, HTTP 200 + `code:400`).
