Feature: Rewrite Tables tests

  @readonly
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

  @readonly
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
    Then retrieving mappings for user "test" at domain "localhost" should raise an ErrorMappingException with message "bounce!"

  Scenario: stored error mapping should be retrieved when two mappings matching
    Given store "bounce!" error mapping for user "test" at domain "localhost"
    And store "error" error mapping for user "test" at domain "localhost"
    Then retrieving mappings for user "test" at domain "localhost" should raise an ErrorMappingException with message "bounce!"

  Scenario: stored error mapping should not be retrieved by another user
    Given store "bounce!" error mapping for user "test" at domain "localhost"
    And store "error" error mapping for user "test" at domain "localhost"
    Then mappings for user "test2" at domain "localhost" should be empty

  Scenario: removing a stored error mapping should work
    Given store "bounce!" error mapping for user "test" at domain "localhost"
    When user "test" at domain "localhost" removes a error mapping "bounce!"
    Then mappings for user "test" at domain "localhost" should be empty

  Scenario: an exception should be thrown when an error mapping is not the first stored
    Given store "test@localhost2" address mapping for user "test" at domain "localhost"
    And store "bounce!" error mapping for user "test" at domain "localhost"
    Then retrieving mappings for user "test" at domain "localhost" should raise an ErrorMappingException with message "bounce!"

  Scenario: an exception should be thrown when an error mapping is the first stored
    Given store "bounce!" error mapping for user "test" at domain "localhost"
    And store "test@localhost2" address mapping for user "test" at domain "localhost"
    Then retrieving mappings for user "test" at domain "localhost" should raise an ErrorMappingException with message "bounce!"

# Wildcard mapping

  Scenario: stored address mapping as wildcard should be retrieved by one user when one mapping matching
    Given store "test@localhost" address mapping as wildcard for domain "localhost"
    Then mappings for user "user" at domain "localhost" should contains only "test@localhost"

  Scenario: stored address mapping as wildcard should be retrieved by two users when one mapping matching
    Given store "test@localhost" address mapping as wildcard for domain "localhost"
    Then mappings for user "user" at domain "localhost" should contains only "test@localhost"
    And mappings for user "user2" at domain "localhost" should contains only "test@localhost"

  Scenario: direct mapping should override address mapping as wildcard
    Given recursive mapping is disable
    Given store "test@localhost" address mapping as wildcard for domain "localhost"
    And store "mine@localhost" address mapping for user "user" at domain "localhost"
    Then mappings for user "user" at domain "localhost" should contains only "mine@localhost"

  Scenario: direct mapping should override address mapping as wildcard (reverse insertion order)
    Given recursive mapping is disable
    Given store "mine@localhost" address mapping for user "user" at domain "localhost"
    And store "test@localhost" address mapping as wildcard for domain "localhost"
    Then mappings for user "user" at domain "localhost" should contains only "mine@localhost"

  Scenario: direct mapping should not override address mapping as wildcard when other user
    Given store "test@localhost" address mapping as wildcard for domain "localhost"
    And store "mine@localhost" address mapping for user "user" at domain "localhost"
    Then mappings for user "user2" at domain "localhost" should contains only "test@localhost"

  Scenario: direct mapping should be retrieved when removing address mapping as wildcard
    Given store "test@localhost" address mapping as wildcard for domain "localhost"
    And store "mine@localhost" address mapping for user "user" at domain "localhost"
    When wildcard address mapping "test@localhost" at domain "localhost" is removed
    Then mappings for user "user" at domain "localhost" should contains only "mine@localhost"

  Scenario: stored address mappings as wildcard should be retrieved when two address mappings as wildcard
    Given store "test@localhost" address mapping as wildcard for domain "localhost"
    And store "test2@localhost" address mapping as wildcard for domain "localhost"
    Then mappings for user "user" at domain "localhost" should contains only "test@localhost, test2@localhost"

# Alias mapping

  Scenario: address mapping should be retrieved when searching with a domain alias
    Given store "aliasdomain" alias domain mapping for domain "localhost"
    Then mappings for user "test" at domain "aliasdomain" should contains only "test@localhost"

  Scenario: address mapping should be retrieved when searching with a domain alias
    Given store "test2@localhost" address mapping for user "test" at domain "localhost"
    And store "aliasdomain" alias domain mapping for domain "localhost"
    Then mappings for user "test" at domain "aliasdomain" should contains only "test2@localhost"

  Scenario: address mapping should be retrieved when searching with a domain alias (reverse insertion order)
    Given store "aliasdomain" alias domain mapping for domain "localhost"
    And store "test2@localhost" address mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "aliasdomain" should contains only "test2@localhost"

  Scenario: address mapping should be retrieved when searching with the correct domain and exists an alias domain
    Given store "test2@localhost" address mapping for user "test" at domain "localhost"
    And store "aliasdomain" alias domain mapping for domain "localhost"
    Then mappings for user "test" at domain "localhost" should contains only "test2@localhost"

  Scenario: wildcard address mapping should be retrieved when searching with a domain alias
    Given store "wildcard@localhost" address mapping as wildcard for domain "localhost"
    And store "aliasdomain" alias domain mapping for domain "localhost"
    Then mappings for user "test" at domain "aliasdomain" should contains only "wildcard@localhost"

  Scenario: wildcard address mapping should be retrieved when searching with a domain and exists an alias domain
    Given store "wildcard@localhost" address mapping as wildcard for domain "localhost"
    And store "aliasdomain" alias domain mapping for domain "localhost"
    Then mappings for user "test" at domain "localhost" should contains only "wildcard@localhost"

  Scenario: both wildcard address mapping and default user address should be retrieved when wildcard address mapping on alias domain
    Given store "wildcard@localhost" address mapping as wildcard for domain "aliasdomain"
    And store "aliasdomain" alias domain mapping for domain "localhost"
    Then mappings for user "test" at domain "aliasdomain" should contains only "test@localhost, wildcard@localhost"

  Scenario: both wildcard address mapping and default user address should be retrieved when wildcard address mapping on alias domain (reverse insertion order)
    Given store "aliasdomain" alias domain mapping for domain "localhost"
    And store "wildcard@localhost" address mapping as wildcard for domain "aliasdomain"
    Then mappings for user "test" at domain "aliasdomain" should contains only "test@localhost, wildcard@localhost"

  Scenario: asking for a removed domain alias should fail
    Given store "wildcard@localhost" address mapping as wildcard for domain "localhost"
    And store "aliasdomain" alias domain mapping for domain "localhost"
    When alias domain mapping "aliasdomain" for "localhost" domain is removed
    Then mappings for user "test" at domain "aliasdomain" should be empty

# Mixed mapping

  Scenario: mixed mapping should work
    Given store "test2@localhost" address mapping for user "test" at domain "localhost"
    And store "(.*)@localhost" regexp mapping for user "test" at domain "localhost"
    And store "aliasdomain" alias domain mapping for domain "localhost"
    Then mappings for user "test" at domain "localhost" should contains only "test2@localhost, regex:(.*)@localhost"

# Recursive mapping

  Scenario: direct mapping should be returned when recursive mapping is disable
    Given recursive mapping is disable
    And store "user2@domain2" address mapping for user "user1" at domain "domain1"
    And store "user3@domain3" address mapping for user "user2" at domain "domain2"
    Then mappings for user "user1" at domain "domain1" should contains only "user2@domain2"

  Scenario: recursive mapping should work when two levels
    Given recursive mapping is enable
    And store "user2@domain2" address mapping for user "user1" at domain "domain1"
    And store "user3@domain3" address mapping for user "user2" at domain "domain2"
    Then mappings for user "user1" at domain "domain1" should contains only "user3@domain3"

  Scenario: recursive mapping should work when three levels
    Given recursive mapping is enable
    And store "user2@domain2" address mapping for user "user1" at domain "domain1"
    And store "user3@domain3" address mapping for user "user2" at domain "domain2"
    And store "user4@domain4" address mapping for user "user3" at domain "domain3"
    Then mappings for user "user1" at domain "domain1" should contains only "user4@domain4"

  Scenario: recursive mapping should throw exception when a loop exists
    Given recursive mapping is enable
    And store "user2@domain2" address mapping for user "user1" at domain "domain1"
    And store "user3@domain3" address mapping for user "user2" at domain "domain2"
    And store "user1@domain1" address mapping for user "user3" at domain "domain3"
    Then retrieving mappings for user "user1" at domain "domain1" should raise an ErrorMappingException with message "554 Too many mappings to process"

  Scenario: recursive mapping should work when a level is removed
    Given recursive mapping is enable
    And store "user2@domain2" address mapping for user "user1" at domain "domain1"
    And store "user3@domain3" address mapping for user "user2" at domain "domain2"
    And store "user4@domain4" address mapping for user "user3" at domain "domain3"
    When user "user2" at domain "domain2" removes a address mapping "user3@domain3"
    Then mappings for user "user1" at domain "domain1" should contains only "user2@domain2"

  Scenario: recursive mapping should work when three levels on alias domains
    Given store "domain2" alias domain mapping for domain "domain1"
    And store "domain3" alias domain mapping for domain "domain2"
    And store "domain4" alias domain mapping for domain "domain3"
    Then mappings for user "test" at domain "domain4" should contains only "test@domain1"

# Forward mapping

  Scenario: stored forward mapping should be retrieved when one mapping is matching
    Given store "test@localhost2" forward mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contains only "forward:test@localhost2"

  Scenario: stored forward mapping should be retrieved when two mappings are matching
    Given store "test@localhost2" forward mapping for user "test" at domain "localhost"
    And store "test@james" forward mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contains only "forward:test@localhost2, forward:test@james"

  Scenario: stored forward mapping should not be retrieved by another user
    Given store "test@localhost2" forward mapping for user "test" at domain "localhost"
    And store "test@james" forward mapping for user "test" at domain "localhost"
    Then mappings for user "test2" at domain "localhost" should be empty

  Scenario: removing a stored forward mapping should work
    Given store "test@localhost2" forward mapping for user "test" at domain "localhost"
    And store "test@james" forward mapping for user "test" at domain "localhost"
    When user "test" at domain "localhost" removes a forward mapping "test@james"
    Then mappings for user "test" at domain "localhost" should contains only "forward:test@localhost2"