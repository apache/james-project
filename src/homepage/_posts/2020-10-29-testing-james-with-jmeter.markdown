---
layout: post
title:  "Performance testing for James with JMeter"
date:   2020-10-29  15:16:30 +0200
categories: community
---

Ever wanted to figure out what Apache James gets in its guts?

Xian Long detailed in this [blog post][post] how to be using JMeter in order to run some
IMAP performance tests on top of the distributed server, using [JMeter][jmeter]

[Other tools][james-gatling], based on [Gatling][gatling] had been developed within the community, addressing SMTP,
IMAP and [JMAP][JMAP] protocols.

[post]: https://www.cnblogs.com/hanxianlong/p/13894595.html
[jmeter]: https://jmeter.apache.org/
[gatling]: https://gatling.io/
[JMAP]: https://jmap.io/
[james-gatling]: https://github.com/linagora/james-gatling
