---
layout: post
title:  "Linagora June newsletter"
date:   2018-06-06 11:30:00 +0700
categories: james update
---

In the name of the James team @Linagora I will be presenting you what we did in the past few weeks, and plan to work on in the coming weeks.

We have to work on some specific features to make James easier to adopt for large organizations.

# What was achieved in May

## Notifications when soon over quota

> A mail needs to be sent to the user to warn him. This is an attempts to allow clients not implementing quota's RFCs to still get the warning.
>
> This is implemented with mailbox listeners (and not mailets), with event sourcing. We added the interfaces and implementations to achieve this feature in James using event sourcing with events being stored in Cassandra or in memory.
> Such a technical solution might be very useful to implement other feature.
>
> This mailbox listener is optional. We allowed one to register his (potentially custom) mailbox listeners when working with Guice.

## Searching user by quota usage

> This would allow an admin to inspect per-user quota usage, via a webadmin interface.
>
> Again, we provided two implementations: a scanning one, which naively enumerates mailboxes to compute occupation, and one where quota occupation is indexed in ElasticSearch.

## Performance enhancements

> In order to have a very good mail search experience, it is possible to plug Apache Tika as a text extractor into James. We use Tika as an external service that we call with HTTP. When you use this Tika extractor with ElasticSearch indexing, you get a very powerful fulltext search even on complex attachment format like LibreOffice documents.
>
> We did experience slow requests with our Tika server. We contributed [this issue] which significantly enhanced Tika server performance, and we introduce a cache on James side to reduce calls to tika.
>
> We also worked on improving massive JMAP operation performance (delete a large number of emails).

# What we will work on in June

## Data Leak Prevention

> We should have a matcher applying rules defined by the admin. This matcher will then store suspicious emails in specific mail repository for an admin review.
>
> To implement this, we need to enhance browsing to MailRepository via WebAdmin. We need to allow storing such rules as well as updating them via webadmin. We need finally a mailet storing emails in a repository given its sender domain.

### Exporting a mail account

> As an administrator, I should be able to export the content of my mail account. This both allows easier migrations, comply with legal requirements as well as allow some forms of backups.
>
> The chosen format is EMLs in a ZIP format, with the folder structure described in archive meta-data, and specific metadata to ease restoring a James account (flags, etc...). An administrator (via webamin) should be able to download/upload such backups in order to do export/restores mail accounts.

### Coming next

Along side these features that turns out to be necessary for large organisations, our focus will be on having a distributed
James server. To achieve this, we will propose soon a distributed mail queue based on RabbitMQ.

[this issue]: https://github.com/apache/tika/pull/237