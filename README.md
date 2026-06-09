# BestEconomy

## Internal placeholders

BestEconomy does not require PlaceholderAPI for its built-in placeholders. Player-facing messages, the scoreboard, and the tab list can use these internal placeholders:

- `{player}` / `{Player}` - the receiving player's name.
- `{money}` / `{Money}` - the receiving player's Money balance.
- `{shards}` / `{Shards}` - the receiving player's Shards balance.
- `{playtime}` / `{Playtime}` - the receiving player's total playtime, formatted as days/hours/minutes/seconds.
- `{ping}` / `{Ping}` - the receiving player's ping in milliseconds.
- `{tps}` / `{TPS}` - the current server TPS.
- `{players_online}` / `{online}` - the current online player count.
- `{max_players}` - the server max player count.
- `{tab_prefix}` - tab-list only; selected from `tab.yml` prefix rules.

## Scoreboard and tab list

- `scoreboard.yml` controls the sidebar scoreboard title, update interval, and lines.
- `tab.yml` controls the tab header, footer, player-name format, online-player display, and permission-based tab prefixes.

## Custom achievements

Custom achievement definitions live in `achievements.yml`. Progress is persisted in `achievement-progress.yml`, batched at the configured `achievements.save-interval-seconds`, and flushed during reload/shutdown. Players can use `/achievements` (or `/ach`) to view numeric partial progress; completed achievements use vanilla advancement toasts and chat announcements, which DiscordSRV can relay through its advancement notifications.

## Teleport requests

- `/tpa <player>` and `/tpahere <player>` send teleport requests.
- `/tpblock` toggles whether a player receives new teleport requests; the preference persists across restarts.
- Admins with `besteconomy.teleport.ban` can use `/tpban <player> <reason> <1h|1d|1w|1m|p>` to prevent a player from sending `/tpa` or `/tpahere` requests. Reasons may contain spaces, `1m` means 30 days, and `p` is permanent.
- `/tpban <player> unban` removes a teleport ban. Teleport-banned players can still use `/tpaccept` and `/tpdeny` on requests they receive.


## Player utility commands

All utility features require explicit permission nodes (their defaults are operator-only):

- `/nick <nickname>` sets a 3-16 character nickname and supports `&` color codes with `besteconomy.nick`. Formatting codes such as bold and italic additionally require `besteconomy.nick.formatting`.
- `/realname <player>` resolves an online real name or nickname with `besteconomy.realname`.
- `/resetnick` resets your nickname with `besteconomy.nick.reset`; `/resetnick <player>` additionally requires `besteconomy.nick.reset.others`.
- `/workbench` (`/wb`), `/enderchest` (`/ec`), and `/hat` require `besteconomy.workbench`, `besteconomy.enderchest`, and `besteconomy.hat`, respectively.
