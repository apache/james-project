Feature: Download endpoint
  As a James user
  I want to access to the download endpoint

  Background:
    Given a domain named "domain.tld"

  Scenario: A known user should initiate the access to the download endpoint
    Given a current user with username "username@domain.tld" and password "secret"
    When checking for the availability of the attachment endpoint
    Then the user should be authorized

  Scenario: An unauthenticated user should initiate the access to the download endpoint
    When checking for the availability of the attachment endpoint
    Then the user should be authorized

  Scenario: A known user should have access to the download endpoint
    Given a current user with username "username@domain.tld" and password "secret"
    When asking for an attachment
    Then the user should be authorized

  @Ignore
  Scenario: An unauthenticated user should not have access to the download endpoint
    When asking for an attachment
    Then the user should not be authorized

  Scenario: A known user should not have access to the download endpoint without a blobId
    Given a current user with username "username@domain.tld" and password "secret"
    When asking for an attachment without blobId parameter
    Then the user should receive a bad request response
