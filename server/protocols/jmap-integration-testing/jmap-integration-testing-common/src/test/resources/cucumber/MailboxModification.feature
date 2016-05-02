Feature: Mailbox modification
  As a James user
  I want a mailbox to be modified when I modify it
  I want my mails to be kept when modifying a mailbox

  Background:
    Given a domain named "domain.tld"
    And a current user with username "username@domain.tld" and password "secret"

  Scenario: Renaming a mailbox should keep messages
    Given mailbox "A" with 2 messages
    When renaming mailbox "A" to "B"
    Then mailbox "B" contains 2 messages

  Scenario: Moving a mailbox should keep messages
    Given mailbox "A" with 2 messages
    And mailbox "A.B" with 3 messages
    And mailbox "B" with 4 messages
    When moving mailbox "A.B" to "B"
    Then mailbox "B.B" contains 3 messages
