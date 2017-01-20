Feature: IMAP compatibility of JMAP setMessages method used to update mailboxes
  As a James user
  I want to be able to access by IMAP messages moved and copied by JMAP

  Background:
    Given a domain named "domain.tld"
    And a connected user "username@domain.tld"
    And "username@domain.tld" has a mailbox "source"
    And "username@domain.tld" has a mailbox "mailbox"
    And "username@domain.tld" has a mailbox "trash"

  Scenario: A message moved by JMAP is seen as moved by IMAP
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    When the user move "m1" to mailbox "mailbox"
    Then the user has a IMAP message in mailbox "mailbox"
    And the user does not have a IMAP message in mailbox "source"

  Scenario: A message copied by JMAP is seen as copied by IMAP
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    When the user copy "m1" from mailbox "source" to mailbox "mailbox"
    Then the user has a IMAP message in mailbox "mailbox"
    And the user has a IMAP message in mailbox "source"

  Scenario: If a message is moved by JMAP, IMAP client will be notified when selecting mailbox
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    When the user move "m1" to mailbox "mailbox"
    Then the user has a IMAP notification about 1 new message when selecting mailbox "mailbox"

  Scenario: If a message is moved by JMAP, IMAP client that have selected the destination mailbox will be notified
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    Given the user has a open IMAP connexion with mailbox "mailbox" selected
    When the user move "m1" to mailbox "mailbox"
    Then the user has a IMAP RECENT and  notification about new message with uid 1 on connexion for mailbox "mailbox"