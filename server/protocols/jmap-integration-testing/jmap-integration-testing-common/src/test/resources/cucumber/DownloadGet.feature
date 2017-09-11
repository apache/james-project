Feature: Download GET
  As a James user
  I want to retrieve my attachments

  Background:
    Given a domain named "domain.tld"
    And a connected user "username@domain.tld"
    And "username@domain.tld" has a mailbox "INBOX"

  Scenario: Getting an attachment previously stored
    Given "username@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "username@domain.tld" downloads "2"
    Then the user should receive that blob
    And the blob size is 3071

  Scenario: Getting an attachment with an unknown blobId
    Given "username@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "username@domain.tld" downloads "2" with a valid authentication token but a bad blobId
    Then the user should receive a not found response

  Scenario: Getting an attachment previously stored with a desired name
    Given "username@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "username@domain.tld" downloads "2" with "myFileName.txt" name
    Then the user should receive that blob
    And the attachment is named "myFileName.txt"

  Scenario: Getting an attachment previously stored with a non ASCII name
    Given "username@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "username@domain.tld" downloads "2" with "ديناصور.odt" name
    Then the user should receive that blob
    And the attachment is named "ديناصور.odt"

  Scenario: Getting a message blob previously stored
    Given "username@domain.tld" mailbox "INBOX" contains a message "1"
    When "username@domain.tld" downloads "1"
    Then the user should receive that blob
    And the blob size is 4963