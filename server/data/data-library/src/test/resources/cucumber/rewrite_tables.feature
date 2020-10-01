Feature: Rewrite Tables tests

  @readonly
  Scenario: rewrite tables should be empty when none defined
    Then mappings should be empty

# Regexp mapping

  Scenario: stored regexp mapping should be retrieved when one mapping matching
    Given store "(.*)@localhost:user@localhost" regexp mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contain only "user@localhost"

  Scenario: stored regexp mapping should be retrieved when two mappings matching
    Given store "(.*)@localhost:user@localhost" regexp mapping for user "test" at domain "localhost"
    And store "tes(.+)@localhost:user2@localhost" regexp mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contain only "user@localhost, user2@localhost"

  Scenario: stored regexp mapping should not be retrieved by another user
    Given store "(.*)@localhost:user@localhost" regexp mapping for user "test" at domain "localhost"
    And store "tes(.+)@localhost:user2@localhost" regexp mapping for user "test" at domain "localhost"
    Then mappings for user "test2" at domain "localhost" should be empty

  Scenario: removing a stored regexp mapping should work
    Given store "(.*)@localhost:user@localhost" regexp mapping for user "test" at domain "localhost"
    And store "tes(.+)@localhost:user2@localhost" regexp mapping for user "test" at domain "localhost"
    When user "test" at domain "localhost" removes a regexp mapping "tes(.+)@localhost:user2@localhost"
    Then mappings for user "test" at domain "localhost" should contain only "user@localhost"

  Scenario: storing an invalid regexp mapping should not work
    When store an invalid ".*):" regexp mapping for user "test" at domain "localhost"
    Then a "InvalidRegexException" exception should have been thrown

  Scenario: storing a wrong formatted regexp mapping should not be retrieved
    Given store "(.*)@localhost" regexp mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should be empty

  Scenario: chained stored regexp mapping should be retrieved when two levels
    Given store "(.*)@localhost:user@localhost" regexp mapping for user "test" at domain "localhost"
    And store "use(.+)@localhost:final@localhost" regexp mapping for user "user" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contain only "final@localhost"

# Address mapping

  Scenario: stored address mapping should be retrieved when one mapping matching
    Given store "test@localhost2" address mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contain only "test@localhost2"

  Scenario: stored address mapping should be retrieved when two mappings matching
    Given store "test@localhost2" address mapping for user "test" at domain "localhost"
    And store "test@james" address mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contain only "test@localhost2, test@james"

  Scenario: stored address mapping should not be retrieved by another user
    Given store "test@localhost2" address mapping for user "test" at domain "localhost"
    And store "test@james" address mapping for user "test" at domain "localhost"
    Then mappings for user "test2" at domain "localhost" should be empty

  Scenario: removing a stored address mapping should work
    Given store "test@localhost2" address mapping for user "test" at domain "localhost"
    And store "test@james" address mapping for user "test" at domain "localhost"
    When user "test" at domain "localhost" removes a address mapping "test@james"
    Then mappings for user "test" at domain "localhost" should contain only "test@localhost2"

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
    Then mappings for user "user" at domain "localhost" should contain only "test@localhost"

  Scenario: stored address mapping as wildcard should be retrieved by two users when one mapping matching
    Given store "test@localhost" address mapping as wildcard for domain "localhost"
    Then mappings for user "user" at domain "localhost" should contain only "test@localhost"
    And mappings for user "user2" at domain "localhost" should contain only "test@localhost"

  Scenario: direct mapping should override address mapping as wildcard
    Given recursive mapping is disable
    Given store "test@localhost" address mapping as wildcard for domain "localhost"
    And store "mine@localhost" address mapping for user "user" at domain "localhost"
    Then retrieving mappings for user "user" at domain "localhost" should raise an ErrorMappingException with message "554 Too many mappings to process"

  Scenario: direct mapping should override address mapping as wildcard (reverse insertion order)
    Given recursive mapping is disable
    Given store "mine@localhost" address mapping for user "user" at domain "localhost"
    And store "test@localhost" address mapping as wildcard for domain "localhost"
    Then retrieving mappings for user "user" at domain "localhost" should raise an ErrorMappingException with message "554 Too many mappings to process"

  Scenario: direct mapping should not override address mapping as wildcard when other user
    Given store "test@localhost" address mapping as wildcard for domain "localhost"
    And store "mine@localhost" address mapping for user "user" at domain "localhost"
    Then mappings for user "user2" at domain "localhost" should contain only "test@localhost"

  Scenario: direct mapping should be retrieved when removing address mapping as wildcard
    Given store "test@localhost" address mapping as wildcard for domain "localhost"
    And store "mine@localhost" address mapping for user "user" at domain "localhost"
    When wildcard address mapping "test@localhost" at domain "localhost" is removed
    Then mappings for user "user" at domain "localhost" should contain only "mine@localhost"

  Scenario: stored address mappings as wildcard should be retrieved when two address mappings as wildcard
    Given store "test@localhost" address mapping as wildcard for domain "localhost"
    And store "test2@localhost" address mapping as wildcard for domain "localhost"
    Then mappings for user "user" at domain "localhost" should contain only "test@localhost, test2@localhost"

# Alias mapping

  Scenario: address mapping should be retrieved when searching with a domain alias
    Given store "aliasdomain" domain mapping for domain "localhost"
    Then mappings for user "test" at domain "aliasdomain" should contain only "test@localhost"

  Scenario: address mapping should be retrieved when searching with a domain alias
    Given store "test2@localhost" address mapping for user "test" at domain "localhost"
    And store "aliasdomain" domain mapping for domain "localhost"
    Then mappings for user "test" at domain "aliasdomain" should contain only "test2@localhost"

  Scenario: address mapping should be retrieved when searching with a domain alias (reverse insertion order)
    Given store "aliasdomain" domain mapping for domain "localhost"
    And store "test2@localhost" address mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "aliasdomain" should contain only "test2@localhost"

  Scenario: address mapping should be retrieved when searching with a domain alias
    Given store "aliasdomain" domain alias for domain "localhost"
    Then mappings for user "test" at domain "aliasdomain" should contain only "test@localhost"

  Scenario: address mapping should be retrieved when searching with a domain alias
    Given store "test2@localhost" address mapping for user "test" at domain "localhost"
    And store "aliasdomain" domain alias for domain "localhost"
    Then mappings for user "test" at domain "aliasdomain" should contain only "test2@localhost"

  Scenario: address mapping should be retrieved when searching with a domain alias (reverse insertion order)
    Given store "aliasdomain" domain alias for domain "localhost"
    And store "test2@localhost" address mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "aliasdomain" should contain only "test2@localhost"

  Scenario: address mapping should be retrieved when searching with the correct domain and exists an alias domain
    Given store "test2@localhost" address mapping for user "test" at domain "localhost"
    And store "aliasdomain" domain mapping for domain "localhost"
    Then mappings for user "test" at domain "localhost" should contain only "test2@localhost"

  Scenario: wildcard address mapping should be retrieved when searching with a domain alias
    Given store "wildcard@localhost" address mapping as wildcard for domain "localhost"
    And store "aliasdomain" domain mapping for domain "localhost"
    Then mappings for user "test" at domain "aliasdomain" should contain only "wildcard@localhost"

  Scenario: wildcard address mapping should be retrieved when searching with a domain and exists an alias domain
    Given store "wildcard@localhost" address mapping as wildcard for domain "localhost"
    And store "aliasdomain" domain mapping for domain "localhost"
    Then mappings for user "test" at domain "localhost" should contain only "wildcard@localhost"

  Scenario: both wildcard address mapping and default user address should be retrieved when wildcard address mapping on alias domain
    Given store "wildcard@localhost" address mapping as wildcard for domain "aliasdomain"
    And store "aliasdomain" domain mapping for domain "localhost"
    Then mappings for user "test" at domain "aliasdomain" should contain only "test@localhost, wildcard@localhost"

  Scenario: both wildcard address mapping and default user address should be retrieved when wildcard address mapping on alias domain (reverse insertion order)
    Given store "aliasdomain" domain mapping for domain "localhost"
    And store "wildcard@localhost" address mapping as wildcard for domain "aliasdomain"
    Then mappings for user "test" at domain "aliasdomain" should contain only "test@localhost, wildcard@localhost"

  Scenario: asking for a removed domain alias should fail
    Given store "wildcard@localhost" address mapping as wildcard for domain "localhost"
    And store "aliasdomain" domain mapping for domain "localhost"
    When domain mapping "aliasdomain" for "localhost" domain is removed
    Then mappings for user "test" at domain "aliasdomain" should be empty

  Scenario: address mapping should be retrieved when searching with the correct domain and exists an alias domain
    Given store "test2@localhost" address mapping for user "test" at domain "localhost"
    And store "aliasdomain" domain alias for domain "localhost"
    Then mappings for user "test" at domain "localhost" should contain only "test2@localhost"

  Scenario: wildcard address mapping should be retrieved when searching with a domain alias
    Given store "wildcard@localhost" address mapping as wildcard for domain "localhost"
    And store "aliasdomain" domain alias for domain "localhost"
    Then mappings for user "test" at domain "aliasdomain" should contain only "wildcard@localhost"

  Scenario: wildcard address mapping should be retrieved when searching with a domain and exists an alias domain
    Given store "wildcard@localhost" address mapping as wildcard for domain "localhost"
    And store "aliasdomain" domain alias for domain "localhost"
    Then mappings for user "test" at domain "localhost" should contain only "wildcard@localhost"

  Scenario: both wildcard address mapping and default user address should be retrieved when wildcard address mapping on alias domain
    Given store "wildcard@localhost" address mapping as wildcard for domain "aliasdomain"
    And store "aliasdomain" domain alias for domain "localhost"
    Then mappings for user "test" at domain "aliasdomain" should contain only "test@localhost, wildcard@localhost"

  Scenario: both wildcard address mapping and default user address should be retrieved when wildcard address mapping on alias domain (reverse insertion order)
    Given store "aliasdomain" domain alias for domain "localhost"
    And store "wildcard@localhost" address mapping as wildcard for domain "aliasdomain"
    Then mappings for user "test" at domain "aliasdomain" should contain only "test@localhost, wildcard@localhost"

  Scenario: asking for a removed domain alias should fail
    Given store "wildcard@localhost" address mapping as wildcard for domain "localhost"
    And store "aliasdomain" domain alias for domain "localhost"
    When domain alias "aliasdomain" for "localhost" domain is removed
    Then mappings for user "test" at domain "aliasdomain" should be empty

# Mixed mapping

  Scenario: mixed mapping should work
    Given store "test2@localhost" address mapping for user "test" at domain "localhost"
    And store "(.*)@localhost:user@localhost" regexp mapping for user "test" at domain "localhost"
    And store "aliasdomain" domain mapping for domain "localhost"
    Then mappings for user "test" at domain "localhost" should contain only "test2@localhost, user@localhost"

  Scenario: mixed mapping should work
    Given store "test2@localhost" address mapping for user "test" at domain "localhost"
    And store "(.*)@localhost:user@localhost" regexp mapping for user "test" at domain "localhost"
    And store "aliasdomain" domain alias for domain "localhost"
    Then mappings for user "test" at domain "localhost" should contain only "test2@localhost, user@localhost"

# Recursive mapping

  Scenario: direct mapping should throw when recursive mapping is disable
    Given recursive mapping is disable
    And store "user2@domain2" address mapping for user "user1" at domain "domain1"
    And store "user3@domain3" address mapping for user "user2" at domain "domain2"
    Then retrieving mappings for user "user1" at domain "localhost1" should raise an ErrorMappingException with message "554 Too many mappings to process"

  Scenario: recursive mapping should work when two levels
    Given recursive mapping is enable
    And store "user2@domain2" address mapping for user "user1" at domain "domain1"
    And store "user3@domain3" address mapping for user "user2" at domain "domain2"
    Then mappings for user "user1" at domain "domain1" should contain only "user3@domain3"

  Scenario: recursive mapping should work when three levels
    Given recursive mapping is enable
    And store "user2@domain2" address mapping for user "user1" at domain "domain1"
    And store "user3@domain3" address mapping for user "user2" at domain "domain2"
    And store "user4@domain4" address mapping for user "user3" at domain "domain3"
    Then mappings for user "user1" at domain "domain1" should contain only "user4@domain4"

  Scenario: recursive mapping should work when a level is removed
    Given recursive mapping is enable
    And store "user2@domain2" address mapping for user "user1" at domain "domain1"
    And store "user3@domain3" address mapping for user "user2" at domain "domain2"
    And store "user4@domain4" address mapping for user "user3" at domain "domain3"
    When user "user2" at domain "domain2" removes a address mapping "user3@domain3"
    Then mappings for user "user1" at domain "domain1" should contain only "user2@domain2"

  Scenario: recursive mapping should work when three levels on alias domains
    Given store "domain2" domain mapping for domain "domain1"
    And store "domain3" domain mapping for domain "domain2"
    And store "domain4" domain mapping for domain "domain3"
    Then mappings for user "test" at domain "domain4" should contain only "test@domain1"

  Scenario: recursive mapping should work when three levels on alias domains
    Given store "domain2" domain alias for domain "domain1"
    And store "domain3" domain alias for domain "domain2"
    And store "domain4" domain alias for domain "domain3"
    Then mappings for user "test" at domain "domain4" should contain only "test@domain1"

# Forward mapping

  Scenario: stored forward mapping should be retrieved when one mapping is matching
    Given store "test@localhost2" forward mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contain only "forward:test@localhost2"

  Scenario: stored forward mapping should be retrieved when two mappings are matching
    Given store "test@localhost2" forward mapping for user "test" at domain "localhost"
    And store "test@james" forward mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contain only "forward:test@localhost2, forward:test@james"

  Scenario: stored forward mapping should not be retrieved by another user
    Given store "test@localhost2" forward mapping for user "test" at domain "localhost"
    And store "test@james" forward mapping for user "test" at domain "localhost"
    Then mappings for user "test2" at domain "localhost" should be empty

  Scenario: removing a stored forward mapping should work
    Given store "test@localhost2" forward mapping for user "test" at domain "localhost"
    And store "test@james" forward mapping for user "test" at domain "localhost"
    When user "test" at domain "localhost" removes a forward mapping "test@james"
    Then mappings for user "test" at domain "localhost" should contain only "forward:test@localhost2"

# Alias mapping

  Scenario: stored alias mapping should be retrieved when one mapping is matching
    Given store user "test@localhost2" alias mapping for alias "test" at domain "localhost"
    Then mappings for alias "test" at domain "localhost" should contain only "alias:test@localhost2"

  Scenario: stored alias mapping should be retrieved when two mappings are matching
    Given store user "test@localhost2" alias mapping for alias "test" at domain "localhost"
    And store user "test@james" alias mapping for alias "test" at domain "localhost"
    Then mappings for alias "test" at domain "localhost" should contain only "alias:test@localhost2, alias:test@james"

  Scenario: stored alias mapping should not be retrieved by another user
    Given store user "test@localhost2" alias mapping for alias "test" at domain "localhost"
    And store user "test@james" alias mapping for alias "test" at domain "localhost"
    Then mappings for user "test2" at domain "localhost" should be empty

  Scenario: removing a stored alias mapping should work
    Given store user "test@localhost2" alias mapping for alias "test" at domain "localhost"
    And store user "test@james" alias mapping for alias "test" at domain "localhost"
    When alias "test" at domain "localhost" removes an alias mapping "test@james"
    Then mappings for alias "test" at domain "localhost" should contain only "alias:test@localhost2"

# Group mapping

  Scenario: stored group mapping should be retrieved when one mapping is matching
    Given store "test@localhost2" group mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contain only "group:test@localhost2"

  Scenario: stored group mapping should be retrieved when two mappings are matching
    Given store "test@localhost2" group mapping for user "test" at domain "localhost"
    And store "test@james" group mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contain only "group:test@localhost2, group:test@james"

  Scenario: stored group mapping should not be retrieved by another user
    Given store "test@localhost2" group mapping for user "test" at domain "localhost"
    And store "test@james" group mapping for user "test" at domain "localhost"
    Then mappings for user "test2" at domain "localhost" should be empty

  Scenario: removing a stored group mapping should work
    Given store "test@localhost2" group mapping for user "test" at domain "localhost"
    And store "test@james" group mapping for user "test" at domain "localhost"
    When user "test" at domain "localhost" removes a group mapping "test@james"
    Then mappings for user "test" at domain "localhost" should contain only "group:test@localhost2"