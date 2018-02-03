---
layout: post
title:  "New administration features: manage your queues and your reporitories"
date:   2018-01-26 00:00:22 +0200
categories: james update
---

An often asked feature for James is the ability to handle the mails, that after some processing,
have landed in a [repository], for example /var/mail/error/.

A brand new webadmin API allows you to handle the content of these repositories, see the documentation in [manage-webadmin].
It allows you to list the repositories, their content, but also to reprocess one or several mails in the processor and queue
of your choice.

Oh! And bonus, now you can forget this non scalable file repository and use the brand new Cassandra repository!

On the same documentation page you will also find the new mail queue management API, done to list queued mails, remove some
of them regarding different criteria, but also flush delayed mails.

Most of the work on these already usable features is done, but we still have some ideas for related improvements. [Contributions] are welcomed!

[repository]: http://james.apache.org/server/feature-persistence.html
[Contributions]: https://issues.apache.org/jira/issues/?jql=project%20%3D%20JAMES%20AND%20resolution%20%3D%20Unresolved%20AND%20labels%20in%20(feature)%20AND%20component%20%20in%20(Queue%2C%20%22MailStore%20%26%20MailRepository%22)%20AND%20type%20in%20(Improvement)
[manage-webadmin]: https://james.apache.org/server/manage-webadmin.html
