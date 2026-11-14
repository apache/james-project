# MPT script syntax

An MPT script is a line based description of a protocol conversation. Each line starts with a directive:

| Line | Meaning |
|------|---------|
| `C: <text>` | Send `<text>` followed by CRLF. `C:` alone sends an empty line. |
| `S: <regex>` | Read one server line; it must fully match the Java regex (`Pattern.matches`). Escape `* ( ) [ ] { } + . ? \| ^ $` with `\`. |
| `SUB {` ... `}` | Unordered block: only `S:` lines inside; reads as many server lines, each matching one (distinct) pattern, in any order. |
| `UNTIL: <regex>` | Read and discard server lines until one matches (max 10000 lines). Handy for untagged noise: `UNTIL: A1 OK .*`. |
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
* IMAP literals: `C: A3 APPEND INBOX {N}` then `S: \+.*` then the content lines. N is the byte count including
  the CRLF of every content line. Prefer non-synchronizing literals `{N+}` (no `S: \+` line). Do not write the exact
  line `S: \+ Ok`: it is reserved for in-process continuations.
* SMTP DATA: send the message lines with `C:`, an empty `C:` between headers and body, and `C: .` to end it.
* Several sessions may target different services (e.g. session 0 on smtps to send, session 1 on imaps to check
  delivery); delivery is asynchronous, poll with `WAIT` + a new command, or `UNTIL:`.
* Use `continueAfterFailure` to collect every mismatch instead of stopping at the first one.

## Example (IMAPS)

```
S: \* OK .*
C: A1 LOGIN ${user} ${password}
S: A1 OK .*
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
