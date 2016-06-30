Feature: An upload endpoint should be available to upload contents
  As a James user
  I want to upload my attachments

  Background:
    Given a domain named "domain.tld"
    And a connected user "username@domain.tld"

  Scenario: An authenticated user should initiate the access to the upload endpoint
    When "username@domain.tld" checks for the availability of the upload endpoint
    Then the user should receive an authorized response

  Scenario: An unauthenticated user should initiate the access to the download endpoint
    When "non-authenticated@domain.tld" checks for the availability of the upload endpoint
    Then the user should receive an authorized response

  Scenario: Uploading a content without being authenticated
    When "non-authenticated@domain.tld" upload a content
    Then the user should receive a not authorized response

  Scenario: Uploading a content being authenticated
    When "username@domain.tld" upload a content
    Then the user should receive a created response
