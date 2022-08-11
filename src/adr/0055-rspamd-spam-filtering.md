# 55. RSPAMD

Date: 2022-08-10

## Status

Accepted (lazy consensus).

Implemented. 

## Context

In order to detect spam, James provide 3 mechanism: 

- SMTP Hook: decide to reject or not before spooling on the SMTP level
- Mailet: custom the mailet pipeline for changing the mail processing when detect spam mail.
- Mailbox listener:  based on user action, eg: move in/out the message to Inbox/Spam mailbox, then decide report spam or not to separate Spam filtering system.

For basic, we can base on username, DSN filter, domains, IP... for configuring black or grey list.
If we want to analysis deeper in each message content more, we need a more complex system.
Currently, James is integrated with [SpamAssassin](https://spamassassin.apache.org/) to tackle this problem.

For more selection, James's repository provides the RSpamD extension, which provides the same way as SpamAssassin but another system - [RSpamD](https://github.com/rspamd/rspamd)

A quick introduce about RSpamD:

```
Rspamd is an advanced spam filtering system and email processing framework that allows evaluation of messages by a number of rules including regular expressions, statistical analysis and custom services such as URL black lists. Each message is analysed by Rspamd and given a verdict that might be used by MTA for further processing (e.g. to reject a message, or add a special header indicating spam) along with other information, such as possible DKIM signature or modifications suggested for a message.

Rspamd can act as a Milter allowing direct interaction with popular MTA systems, such as Postfix or Sendmail.

Rspamd is designed to process hundreds of messages per second simultaneously, and provides a number of useful features including a comprehensive Lua API that allows access to messages processing in various aspects as well as asynchronous network API to access external resources, such as DNS, HTTP or even generic TCP/UDP services.
```

## Decision 

Set up a new maven project dedicated to rspamd extension. This allows to be embedded in a James server as a soft dependency
using the external-jar loading mechanism. With this way, the extension could be dropped in one's James installation, and not a runtime dependency.

Based on James' support customize mailets, listeners, web admin routes. RSpamD extension can be done via:

- `RSpamDScanner` mailet: with each mail income, this mailet will query to RSpamD for get result spam or ham. Then append new headers to mail with status/flag spam.
By setup with `IsMarkedAsSpam` matcher, the mail will be action rejected or not.
This mailet will be configured in [mailetcontainer.xml](/server/apps/distributed-app/sample-configuration/mailetcontainer.xml).

- Web admin route: to create feeding ham/spam messages task (batch mechanism). It helps spam classify learning.

- `RSpamDListener`: the listener will handler mailbox event, based on `MessageMoveEvent`, `MailboxEvents.Added` to detecting the mail is spam or ham, then report to RSpamD,
en rich data to RSpamD, thus we will get more exact results in the next query.
This listener will be configured in [mailetcontainer.xml](/server/apps/distributed-app/sample-configuration/listeners.xml).

For connect to RSpamD, we use http protocol with reactor http client. 

## Consequences

- For higher performance, lower latency, the RSpamD should run in same network with James.
- The query to RSpamD will get different score for same message. 


## Alternatives

- Rspamd can act as a [milter](https://en.wikipedia.org/wiki/Milter), we can use it to replace HTTP call, but more complicated

## References

- [JIRA](https://issues.apache.org/jira/browse/JAMES-3775)