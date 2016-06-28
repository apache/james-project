Feature: Alternative authentication mechanism for getting attachment via a POST request returning a specific authentication token
  As a James user
  I want to retrieve my attachments without an alternative authentication mechanim

  Background:
    Given a domain named "domain.tld"
    And a connected user "username@domain.tld"
    And "username@domain.tld" has a mailbox "inbox"

  Scenario: Asking for an attachment access token with an unknown blobId
    When "username@domain.tld" asks for a token for attachment "123"
    Then the user should receive a not found response

  Scenario: Asking for an attachment access token with a previously stored blobId
    Given "username@domain.tld" mailbox "inbox" contains a message "1" with an attachment "2"
    When "username@domain.tld" asks for a token for attachment "2"
    Then the user should receive an attachment access token
