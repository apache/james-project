Feature: Download GET
  As a James user
  I want to retrieve my attachments

  Background:
    Given a domain named "domain.tld"
    And a connected user "username@domain.tld"
    And "username@domain.tld" has a mailbox "inbox"

  Scenario: Getting an attachment previously stored
    Given "username@domain.tld" mailbox "inbox" contains a message "1" with an attachment "2"
    When "username@domain.tld" downloads "2"
    Then the user should receive that attachment

  Scenario: Getting an attachment with an unknown blobId
    When "username@domain.tld" downloads "123"
    Then the user should receive a not found response

  Scenario: Getting an attachment previously stored with a desired name
    Given "username@domain.tld" mailbox "inbox" contains a message "1" with an attachment "2"
    When "username@domain.tld" downloads "2" with "myFileName.txt" name
    Then the user should receive that attachment
    And the attachment is named "myFileName.txt"
