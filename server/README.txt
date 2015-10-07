### http://james.apache.org/server/3/ ###

Thank you for testing Apache James Server 3.0-beta4!

  * What's new in 3.0-beta3 for end users
    - Numerous IMAP bug fixes (better client support, memory improvement, NIO and IO support...)
    - Support for IMAP IDLE (RFC 2177, server transmit updates to the client in real time)
    - Support for IMAP User Flags
    - Support for IMAP WITHIN Extensions (RFC 5032)
    - Support for IMAP SEARCHRES
    - Support for IMAP AUTH=PLAIN
    - Support for IMAP ENABLE
    - Support for IMAP ESEARCH
    - Support for IMAP I18NLEVEL=1
    - Telnet Management has been removed in favor of JMX with client shell
    - More metrics counters available via JMX
    - JPA validated against more databases (among others Oracle)
    - Better debug logging on protocols
    - Multiple address and port configurations per protocol
    - POP3 is now operational (was buggy in 3.0-M2)
    - Mailbox Tooling to copy from a persistence to another persistence
    - Upgrade tool from James 2.3 is available
    - Better logging on protocols with adjustable level
    - Full mailet package must be specified
    - Composite Matchers
    - Mailing list functionality has been removed
    - More documentation on web site for configuration,...
    - Java 1.6 mandatory
    - ... and much more, see details on https://issues.apache.org/jira/secure/ReleaseNote.jspa?in&version=12316850&styleName=Html&projectId=10411
  * What's new in 3.0-beta3 for developers
    - Less maven modules
    - Maven 3.0.2 required to build
    - Upgrade to latest frameworks versions (netty, activemq, jackrabbit...)
    - Code reports generation via 'mvn site -P site-reports' maven profile
    - Corrections further to findbugs,... reports
    - Code formatting
    - ... and much more, see details on https://issues.apache.org/jira/secure/ReleaseNote.jspa?in&version=12316850&styleName=Html&projectId=10411

  * Quick Start  http://james.apache.org/server/3/quick-start.html

  * Install      http://james.apache.org/server/3/install.html
  * Configure    http://james.apache.org/server/3/config.html
  * Manage       http://james.apache.org/server/3/manage.html
  * Monitor      http://james.apache.org/server/3/monitor.html

  * Upgrade from James 3.0-M2
      For JPA Mailbox, database table names have changes.
        You have to manually migrate the database...
          or use the mailbox-copier to backup to maildir, recreate database from scratch, and recopy from maildir

  * Develop      http://james.apache.org/server/3/dev.html

  * Feedbacks and Questions 
      Mailing lists    http://james.apache.org/mail.html
      Twitter          http://twitter.com/ApacheJames

  * Upgrade from James 2.3 http://james.apache.org/server/3/upgrade-2.3.html

  * Limitations
      Some issues are identified in some use cases with clients such as Outlook Express,...
      You are kindly invited to report any issue on https://issues.apache.org/jira/browse/JAMES
      or on our mailing list (http://james.apache.org/mail.html)
