Feature: Rewrite Tables tests

  Scenario: rewrite tables should be empty when none defined
    Then mappings should be empty

# Regexp mapping

  Scenario: stored regexp mapping should be retrieved when one mapping matching
    Given store "(.*)@localhost" regexp mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contains only "regex:(.*)@localhost"

  Scenario: stored regexp mapping should be retrieved when two mappings matching
    Given store "(.*)@localhost" regexp mapping for user "test" at domain "localhost"
    And store "(.+)@test" regexp mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contains only "regex:(.*)@localhost, regex:(.+)@test"

  Scenario: stored regexp mapping should not be retrieved by another user
    Given store "(.*)@localhost" regexp mapping for user "test" at domain "localhost"
    And store "(.+)@test" regexp mapping for user "test" at domain "localhost"
    Then mappings for user "test2" at domain "localhost" should be empty

  Scenario: removing a stored regexp mapping should work
    Given store "(.*)@localhost" regexp mapping for user "test" at domain "localhost"
    And store "(.+)@test" regexp mapping for user "test" at domain "localhost"
    When user "test" at domain "localhost" removes a regexp mapping "(.+)@test"
    Then mappings for user "test" at domain "localhost" should contains only "regex:(.*)@localhost"

  Scenario: storing an invalid regexp mapping should not work
    When store an invalid ".*):" regexp mapping for user "test" at domain "localhost"
    Then a "RecipientRewriteTableException" exception should have been thrown

# Address mapping

  Scenario: stored address mapping should be retrieved when one mapping matching
    Given store "test@localhost2" address mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contains only "test@localhost2"

  Scenario: stored address mapping should be retrieved when two mappings matching
    Given store "test@localhost2" address mapping for user "test" at domain "localhost"
    And store "test@james" address mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contains only "test@localhost2, test@james"

  Scenario: stored address mapping should not be retrieved by another user
    Given store "test@localhost2" address mapping for user "test" at domain "localhost"
    And store "test@james" address mapping for user "test" at domain "localhost"
    Then mappings for user "test2" at domain "localhost" should be empty

  Scenario: removing a stored address mapping should work
    Given store "test@localhost2" address mapping for user "test" at domain "localhost"
    And store "test@james" address mapping for user "test" at domain "localhost"
    When user "test" at domain "localhost" removes a address mapping "test@james"
    Then mappings for user "test" at domain "localhost" should contains only "test@localhost2"

# Error mapping

  Scenario: stored error mapping should be retrieved when one mapping matching
    Given store "bounce!" error mapping for user "test" at domain "localhost"
    Then retrieving mappings for user "test" at domain "localhost" should raise a "ErrorMappingException" exception with message "bounce!"

# Bad messsage: "bounce!;test@localhost2" instead of "bounce!"
  @ignore
  Scenario: stored error mapping should be retrieved when two mappings matching
    Given store "bounce!" error mapping for user "test" at domain "localhost"
    And store "error" error mapping for user "test" at domain "localhost"
    Then retrieving mappings for user "test" at domain "localhost" should raise a "ErrorMappingException" exception with message "bounce!"

  Scenario: stored error mapping should not be retrieved by another user
    Given store "bounce!" error mapping for user "test" at domain "localhost"
    And store "error" error mapping for user "test" at domain "localhost"
    Then mappings for user "test2" at domain "localhost" should be empty

  Scenario: removing a stored error mapping should work
    Given store "bounce!" error mapping for user "test" at domain "localhost"
    When user "test" at domain "localhost" removes a error mapping "bounce!"
    Then mappings for user "test" at domain "localhost" should be empty

# Should fail, but not currently
  @ignore
  Scenario: an exception should be thrown when an error mapping is not the first stored
    Given store "test@localhost2" address mapping for user "test" at domain "localhost"
    And store "bounce!" error mapping for user "test" at domain "localhost"
    Then retrieving mappings for user "test" at domain "localhost" should raise a "ErrorMappingException" exception with message "bounce!"

# Bad messsage: "bounce!;test@localhost2" instead of "bounce!"
  @ignore
  Scenario: an exception should be thrown when an error mapping is the first stored
    Given store "bounce!" error mapping for user "test" at domain "localhost"
    And store "test@localhost2" address mapping for user "test" at domain "localhost"
    Then retrieving mappings for user "test" at domain "localhost" should raise a "ErrorMappingException" exception with message "bounce!"
