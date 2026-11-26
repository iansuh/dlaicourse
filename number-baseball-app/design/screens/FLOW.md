# Navigation map

Design system v2 (Clean White, 2026-11-26). All screens are specified in this folder; values refer to [`../DESIGN_SYSTEM.md`](../DESIGN_SYSTEM.md) and [`../tokens.json`](../tokens.json). Shots: [`../shots/`](../shots/), rendered from the prototypes by [`../prototype/shoot.js`](../prototype/shoot.js) (`node gen-tokens.js && node shoot.js [filter]`). The engineer's change list is [`../REDESIGN_V2.md`](../REDESIGN_V2.md).

```mermaid
flowchart TD
  Launch([App start]) --> HasAcct{Account token?}
  HasAcct -- no --> Age[01 Age: birth year + month]
  Age -- allowed --> Nick[01 First run: nickname]
  Age -- under age --> NA([01 Not available, final])
  Nick --> Tut[01 Tutorial, skippable]
  Tut --> Home
  HasAcct -- yes --> Home[02 Home]

  Home --> CpuSetup[03 vs Computer setup sheet] --> Secret
  Home -- online action, first time --> Terms[13 Terms gate]
  Terms -- Agree --> Online{{continue to the online action}}
  Terms -- Not now --> Home
  Home --> Search[04 Quick match search] --> Found[04 Match found] --> Secret
  Home --> CreateRoom[07 Create room sheet] --> Lobby[07 Room lobby]
  Home --> Join[07 Join with code] --> Lobby
  Home --> Profile[10 Profile and stats]
  Home --> Settings[11 Settings]
  Settings --> Legal[15 In-app reader: Terms / Privacy]
  Terms --> Legal
  Home -- help / 처음이라면 게임 방법 보기 --> Rules[01 How to play: replays the tutorial]

  Lobby -- host: practise --> Practice[05 Match vs Computer, practice banner]
  Practice -- room fills --> Secret
  Lobby -- room fills --> Secret[05 Match: secret entry]
  Lobby --> Chat[09 Chat sheet]
  Secret --> Guess[05 Match: guessing] --> Result[06 Duel result]
  Guess -- team match --> Board[08 Team match board]
  Guess --> Phrases[09 Quick phrases sheet]
  Guess -- 메모 --> Memo[05 Memo sheet]
  Result -- 회별 기록 보기 --> Innings[06 Innings sheet]
  Board --> TeamResult[06 Team result]
  Guess -- match over, ad due --> Inter([Interstitial ad]) --> Result
  Board -- all duels over, ad due --> Inter2([Interstitial ad]) --> TeamResult
  Settings -- 광고 제거 --> Play([Play purchase sheet])
  Result -- 광고 제거 line --> Play
  Result --> Home
  TeamResult --> Lobby

  Chat -- long-press message / tap name --> Card[12 Player card / report / block]
  Board -- long-press row --> Card
  Result -- flag --> Card
```

## Rules

- **Back:** system back always goes one level up the tree above. From a running online match, back opens the "leave match?" dialog (05); from the lobby it opens "leave room?"; from Home it leaves the app (no confirmation, no ad).
- **Terms gate** appears before the first online action of an account (quick match, create room, join room, first chat) and again when the Terms version changes. vs Computer never needs it.
- **App only** (owner decision): no screen opens a web page. Terms and the privacy policy are read in-app (15); account deletion is in Settings; deletion without the app is by email (server's static `/delete-account` page, not part of the app).
- **Sheets in v2:** the memo (05 §2.4), the innings table (06 §1.1), quick phrases, chat, player card and setup sheets are all bottom sheets; back closes the sheet first.
- **Process death / app switch during a match:** returning within 60 s restores the match screen with the reconnecting banner (14); after 60 s the duel is forfeited and the result screen shows the forfeit line.
- **Deep link** `numbaseball://room/K7Q2M` (and the share link) opens Join with the code filled; Terms gate first if needed.
- **Ads** appear only on Home, results and Profile (DESIGN_SYSTEM section 13). The interstitial (owner decision 2026-11-25) comes after any finished match, vs computer or online, on the way **to** the results screen, with the safeguards in 06 §3 (team match: once, before the team result). Owning `remove_ads` removes all of it; its entry points are Settings (11 §1.1) and the quiet "광고 제거" line above the banner area on the result screens (06 §1.2).

## Screen index

| # | File | Prototype | Shots |
| --- | --- | --- | --- |
| 01 | [01-first-run.md](01-first-run.md) | `age.html`, `nickname.html` | `age-ko/en`, `age-error-ko`, `age-na-ko`, `nickname-ko`, `nickname-error-ko` |
| 02 | [02-home.md](02-home.md) | `home.html` | `home-ko/de/ru` |
| 03 | [03-vs-computer-setup.md](03-vs-computer-setup.md) | — | — |
| 04 | [04-quick-match.md](04-quick-match.md) | `search.html` | `search-ko`, `search-found-ko` |
| 05 | [05-match.md](05-match.md) | `match.html` | `match-*` (incl. `match-memo-ko`) |
| 06 | [06-results.md](06-results.md) | `result.html`, `team-board.html?state=final` | `result-*` (incl. `result-link-ko/en`, `result-innings-ko`), `team-final-ko` |
| 07 | [07-room-lobby.md](07-room-lobby.md) | `lobby.html` | `lobby-ko/en/de/ru` |
| 08 | [08-team-board.md](08-team-board.md) | `team-board.html` | `team-board-ko` |
| 09 | [09-chat.md](09-chat.md) | `chat.html`, `match.html?state=phrases` | `chat-*` (incl. `chat-system-ko`), `match-phrases-ko` |
| 10 | [10-profile.md](10-profile.md) | — | — |
| 11 | [11-settings.md](11-settings.md) | `settings.html` | `settings-*` (incl. `settings-ads-*`) |
| 12 | [12-safety-report-block.md](12-safety-report-block.md) | `report.html`, `chat.html?state=actions` | `report-*` (incl. `report-card-norecord-ko`), `chat-actions-ko` |
| 13 | [13-terms-gate.md](13-terms-gate.md) | `terms.html` | `terms-ko/en` |
| 15 | [15-legal-reader.md](15-legal-reader.md) | `legal.html` | `legal-ko` |
| 14 | [14-connection-and-errors.md](14-connection-and-errors.md) | `match.html?state=reconnect` | `match-reconnect-ko` |
| — | [ADAPTIVE.md](ADAPTIVE.md) | `match.html` at 360 × 320 | `match-small-ko/de` |
