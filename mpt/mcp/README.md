# James MCP MPT

A [Model Context Protocol](https://modelcontextprotocol.io) server letting an agent write, validate and run
[MPT](../README.md) scripts against the SMTP, IMAP, POP3 and ManageSieve ports of an Apache James server deployed on
a test bench. The agent can only reach the targets configured on the server.

## Tools

| Tool | Purpose |
|------|---------|
| `list_targets` | Configured targets (name, protocol, host, port, TLS) and predefined variables |
| `get_mpt_syntax` | Script syntax reference (also exposed as resource `mpt://syntax` and in the `initialize` instructions) |
| `validate_script` | Parse only: syntax errors with line numbers, number of sessions |
| `run_script` | Run the script: `PASSED` / `FAILED` / `ERROR` / `TIMEOUT`, mismatches, captured variables, transcript |

`run_script` arguments: `script`, `target` (default target of every session), `sessions` (per session override, e.g.
`{"0": "smtps", "1": "imaps"}`), `variables`, `continueAfterFailure`, `timeoutSeconds`.

## MPT syntax extensions

On top of the classic `C:` / `S:` / `SUB {}` / `SESSION:` / `WAIT` / `REINIT` / `LOG` / `TIMER` lines, MPT now supports:

* runtime `${variables}`: verbatim in `C:` lines, regex-quoted in `S:` / `SUB` / `UNTIL:` lines;
* captures: named groups of a matched line become variables, `S: \* OK \[UIDVALIDITY (?<uidvalidity>\d+)\].*`;
* `UNTIL: <regex>`: skip server lines until one matches;
* `SET: name=value` and `SET-BASE64: name=value` (`\0` = NUL, for SASL PLAIN);
* `STARTTLS`: upgrade the current connection to TLS;
* accurate `script:<line>` locations in parse errors and failures, failures collected with `continueAfterFailure`.

See [src/main/resources/mpt-syntax.md](src/main/resources/mpt-syntax.md).

## Configuration

Environment variables:

| Variable | Default | Meaning |
|----------|---------|---------|
| `MPT_MCP_TARGETS` | see below | `name=scheme://host:port,...` with scheme in `smtp(s)`, `imap(s)`, `pop3(s)`, `sieve(s)`; `s` = implicit TLS |
| `MPT_MCP_HOST` | `james` | Host of the default targets, used when no target is configured |
| `MPT_MCP_VARIABLES` | | Predefined script variables, `user=bob@domain.tld,password=secret` |
| `MPT_MCP_CONFIG` | | Optional JSON configuration file (see below), overriding the environment |
| `MPT_MCP_TRUST_ALL` | `true` | Accept any server certificate (self-signed bench). `false`: JVM truststore + hostname check |
| `MPT_MCP_CONNECT_TIMEOUT` / `MPT_MCP_READ_TIMEOUT` | `10` / `10` | Seconds |
| `MPT_MCP_SCRIPT_TIMEOUT` / `MPT_MCP_MAX_SCRIPT_TIMEOUT` | `60` / `300` | Seconds, default and upper bound of a run |
| `MPT_MCP_MAX_CONCURRENT_RUNS` | `4` | Concurrent `run_script` calls |
| `MPT_MCP_MAX_TRANSCRIPT_LINES` | `2000` | Transcript size cap |
| `MPT_MCP_TRANSPORT` | `stdio` (`http` in the image) | `stdio` or `http` |
| `MPT_MCP_HTTP_PORT` | `8080` | HTTP transport port, endpoint `POST /mcp`, liveness `GET /health` |
| `MPT_MCP_AUTH_TOKEN` | | When set, HTTP requests need `Authorization: Bearer <token>` |
| `MPT_MCP_LOG_LEVEL` | `INFO` | Logs go to stderr |

Default targets: `smtps=smtps://james:465`, `imaps=imaps://james:993`, `pop3s=pop3s://james:995`,
`managesieves=sieves://james:4190`.

JSON file (mount it under `/root/conf`):

```json
{
  "targets": [
    {"name": "smtps", "uri": "smtps://james.bench:465", "description": "submission"},
    {"name": "imaps", "protocol": "imap", "host": "james.bench", "port": 993, "tls": true},
    {"name": "sieve", "uri": "sieve://james.bench:4190", "description": "plain, use STARTTLS"}
  ],
  "variables": {"user": "bob@domain.tld", "password": "secret"},
  "trustAllCertificates": true,
  "scriptTimeoutSeconds": 60
}
```

## Build and run

```
mvn install -pl mpt/mcp -am            # also builds target/jib-image.tar
docker load -i mpt/mcp/target/jib-image.tar

# HTTP transport (image default)
docker run -d -p 8080:8080 \
  -e MPT_MCP_HOST=james.bench \
  -e MPT_MCP_VARIABLES="user=bob@domain.tld,password=secret" \
  -e MPT_MCP_AUTH_TOKEN=changeme \
  apache/james-mcp-mpt:latest
claude mcp add --transport http james-mpt http://localhost:8080/mcp --header "Authorization: Bearer changeme"

# stdio transport
claude mcp add james-mpt -- docker run -i --rm -e MPT_MCP_TRANSPORT=stdio -e MPT_MCP_HOST=james.bench apache/james-mcp-mpt:latest
```

The image name can be changed with `-Dmcp.docker.image=...`.
