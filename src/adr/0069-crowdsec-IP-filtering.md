# 69. Crowdsec

Date: 2022-10-26

## Status

Accepted (lazy consensus).

Implemented. 

## Context

Currently, there is no machanism to check harmful IP addresses implementing attacks like bruteforce attacks, dictionary attacks, etc.

To that end, James is integrated with [Crowdsec](https://www.crowdsec.net/). Crowdsec will check James logs, based on defined scenarios, Crowdsec can detect malevolent behaviors, and block them from accessing James at various levels (infrastructural, system, applicative). For basic, we implement Crowdsec to detect at system level.

A quick introduction about Crowdsec:

```
CrowdSec Security Engine is an open-source and lightweight software that allows you to detect peers with malevolent behaviors and block them from accessing your systems at various levels (infrastructural, system, applicative).

To achieve this, the Security Engine reads logs from different sources (files, streams ...) to parse, normalize and enrich them before matching them to threats patterns called scenarios. 
```

## Decision 

Set up a new maven project dedicated to Crowdsec extension. This allows to be embedded in a James server as a soft dependency
using the external-jar loading mechanism. With this way, the extension could be dropped in one's James installation, and not a runtime dependency.

To connect to Crowdsec, we use https protocol with reactor http client. 

## Consequences

## Alternatives

## References

- [JIRA](https://issues.apache.org/jira/browse/JAMES-3897)
- [Crowdsec](https://www.crowdsec.net/)