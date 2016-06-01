Feature: Download GET
  As a James user
  I want to retrieve my attachments

  Background:
    Given a domain named "domain.tld"
    And a current user with username "username@domain.tld" and password "secret"

  Scenario: Getting an attachment previously stored
    Given a message containing an attachment
    When getting the attachment with its correct blobId
    Then the user should receive that attachment

  Scenario: Getting an attachment with an unknown blobId
    When getting the attachment with an unknown blobId
    Then the user should receive a not found response
