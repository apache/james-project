# MPT script syntax

An MPT script is a line based description of a protocol conversation. Each line starts with a directive:

| Line | Meaning |
|------|---------|
| `C: <text>` | Send `<text>` followed by CRLF. `C:` alone sends an empty line. |
| `S: <regex>` | Read one server line; it must fully match the Java regex (`Pattern.matches`). Escape `* ( ) [ ] { } + . ? \| ^ $` with `\`. |
| `SUB {` ... `}` | Unordered block: only `S:` lines inside; reads as many server lines, each matching one (distinct) pattern, in any order. |
| `UNTIL: <regex>` | Read and discard server lines until one matches (max 10000 lines). Handy for untagged noise: `UNTIL: A1 OK .*`. |
| `IGNORE: <regex>` | From now on (current session), a server line that does not match the expected `S:`/`SUB` line but matches an ignored pattern is skipped. The expected line always wins: `IGNORE: \* .*` then `S: \* 3 EXISTS` still checks EXISTS. `IGNORE:` alone clears the patterns. |
| `C: <command> {}` / `C: <command> {+}` then `L:` / `L-:` lines | Literal with computed size. `L: text` adds `text` + CRLF, `L-: text` adds `text` without CRLF. The UTF-8 byte size replaces `{}` (synchronizing: the `+` continuation is awaited automatically) or `{+}` (non synchronizing). Continue the command with a `C:` line (an empty `C:` ends it). |
| `POLL <timeoutMs> <intervalMs> {` `C:`... `EXPECT: <regex>` `END: <regex>` `}` | Send the `C:` lines, read lines up to the one matching `END:`; done when one line matched `EXPECT:` (named groups captured), otherwise wait and retry until the timeout. `${pollAttempt}` counts attempts. |
| `SESSION: <n>` | Following lines apply to session (connection) `n` (0 based). Before any `SESSION:` line, lines apply to every session. |
| `SET: name=value` | Define variable `name` (value is itself substituted). |
| `SET-BASE64: name=value` | Define `name` as the base64 of the UTF-8 value, `\0` meaning NUL. SASL PLAIN: `SET-BASE64: plain=\0${user}\0${password}`. |
| `STARTTLS` | Upgrade the current session to TLS (after the server accepted `STARTTLS`). |
| `WAIT <ms>` | Sleep. |
| `REINIT` | Close and reopen the current session (a new greeting must be read). |
| `LOG INFO <msg>` | Log a message (`DEBUG`, `INFO`, `WARN`, `ERR`). |
| `TIMER start\|print\|reset <name>` | Measure durations. |
| `# ...` / empty | Comment. |

## Variables and captures

* `${name}` is substituted when the line runs. In `C:` lines the value is inserted verbatim; in `S:`, `SUB` and `UNTIL:`
  lines it is inserted as a regex literal (quoted). Undefined variables are left untouched.
* Named groups capture into variables: `S: \* OK \[UIDVALIDITY (?<uidvalidity>\d+)\].*` then `C: A2 UID FETCH ${uidvalidity} ...`.
* Predefined: `${runId}` (random hex unique to the run, to name mailboxes/scripts without collisions), the variables
  configured on the server (see `list_targets`), and the ones passed to `run_script`.

## Tips

* The first line sent by a server is its greeting: start with `S: \* OK .*` (IMAP), `S: 220 .*` (SMTP),
  `S: \+OK.*` (POP3), or the ManageSieve capability lines followed by `S: OK.*` (use `UNTIL: OK.*`).
* SMTP multi-line replies: `S: 250-.*` for each continuation line then `S: 250 .*`, or simply `UNTIL: 250 .*`.
* Literals: never count bytes by hand, use `{}` / `{+}` with `L:` lines (raw UTF-8 is fine). Do not write the exact
  line `S: \+ Ok`: it is reserved for in-process continuations.
* Untagged noise (IMAP `* n EXISTS`, `* OK [...]`...): `IGNORE: \* .*` right after the greeting makes scripts robust,
  while `S: \* ...` lines keep checking the untagged responses you care about.
* Failures report the session, the last command actually sent (with its script line) and the server lines left unread.
* SMTP DATA: send the message lines with `C:`, an empty `C:` between headers and body, and `C: .` to end it.
* Several sessions may target different services (e.g. session 0 on smtps to send, session 1 on imaps to check
  delivery); delivery is asynchronous: use `POLL` (IMAP `STATUS`/`SEARCH`; POP3 freezes the maildrop at login).
* Plain text targets (smtp 25, submission 587, imap 143, pop3 110): `C: A0 STARTTLS` / `S: A0 OK.*` / `STARTTLS`
  (POP3: `C: STLS`, SMTP: `C: STARTTLS` + `S: 220 .*`), then greet again (SMTP `EHLO`, IMAP `CAPABILITY`).
* Isolated accounts: `create_account` (when available) gives a fresh account; pass it as `variables`.
* Use `continueAfterFailure` to collect every mismatch instead of stopping at the first one.

## Example (IMAPS)

```
S: \* OK .*
IGNORE: \* .*
C: A1 LOGIN ${user} ${password}
S: A1 OK .*
C: A2 APPEND INBOX {}
L: Subject: héllo ${runId}
L:
L: body
C:
S: A2 OK .*
C: A2 CREATE test-${runId}
S: A2 OK .*
C: A3 SELECT test-${runId}
UNTIL: \* OK \[UIDVALIDITY (?<uidvalidity>\d+)\].*
UNTIL: A3 OK \[READ-WRITE\].*
C: A4 DELETE test-${runId}
S: A4 OK .*
C: A5 LOGOUT
S: \* BYE .*
S: A5 OK .*
```
