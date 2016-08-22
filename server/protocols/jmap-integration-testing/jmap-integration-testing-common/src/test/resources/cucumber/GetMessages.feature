Feature: GetMessages method
  As a James user
  I want to be able to retrieve my messages

  Background:
    Given a domain named "domain.tld"
    And a connected user "username@domain.tld"
    And "username@domain.tld" has a mailbox "inbox"

  Scenario: Retrieving messages with a non null accountId should return a NotSupported error
    When the user ask for messages using its accountId
    Then an error "Not yet implemented" is returned

  Scenario: Unknown arguments should be ignored when retrieving messages
    When the user ask for messages using unknown arguments
    Then no error is returned
    And the list of unknown messages is empty
    And the list of messages is empty

  Scenario: Retrieving messages with invalid argument should return an InvalidArguments error
    When the user ask for messages using invalid argument
    Then an error "invalidArguments" is returned
    And the description is "N/A (through reference chain: org.apache.james.jmap.model.Builder["ids"])"

  Scenario: Retrieving messages should return empty list when no message
    When the user ask for messages
    Then no error is returned
    And the list of messages is empty

  Scenario: Retrieving message should return not found when message doesn't exist
    When the user ask for messages "["username@domain.tld|inbox|12"]"
    Then no error is returned
    And the notFound list should contains "username@domain.tld|inbox|12"

  Scenario: Retrieving message should return messages when exists
    Given the user has a message in "inbox" mailbox with subject "my test subject" and content "testmail"
    When the user ask for messages "["username@domain.tld|inbox|1"]"
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "username@domain.tld|inbox|1"
    And the threadId of the message is "username@domain.tld|inbox|1"
    And the subject of the message is "my test subject"
    And the textBody of the message is "testmail"
    And the isUnread of the message is "true"
    And the preview of the message is "testmail"
    And the headers of the message contains:
      |Subject |my test subject |
    And the date of the message is "2014-10-30T14:12:00Z"
    And the hasAttachment of the message is "false"
    And the list of attachments of the message is empty

  Scenario Outline: Retrieving message should return messages when exists and is a html message
    Given the user has a message in <mailbox> mailbox with content-type <content-type> subject <subject> and content <content>
    When the user ask for messages "["username@domain.tld|inbox|1"]"
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "username@domain.tld|inbox|1"
    And the threadId of the message is "username@domain.tld|inbox|1"
    And the subject of the message is <subject>
    And the htmlBody of the message is <content>
    And the isUnread of the message is "true"
    And the preview of the message is <preview>
    And the headers of the message contains:
      |Content-Type |text/html        |
      |Subject      |<subject-header> |
    And the date of the message is "2014-10-30T14:12:00Z"

    Examples:
      |mailbox |content-type |subject           |subject-header  |content                                                                                                                         |preview                                                                                      |
      |"inbox" |"text/html"  |"my test subject" |my test subject |"This is a <b>HTML</b> mail"                                                                                                    |"This is a HTML mail"                                                                        |
      |"inbox" |"text/html"  |"my test subject" |my test subject |"This is a <b>HTML</b> mail containing <u>underlined part</u>, <i>italic part</i> and <u><i>underlined AND italic part</i></u>" |"This is a HTML mail containing underlined part, italic part and underlined AND italic part" |
      |"inbox" |"text/html"  |"my test subject" |my test subject |"This is a <ganan>HTML</b> mail"                                                                                                |"This is a HTML mail"                                                                        |

  Scenario Outline: Retrieving message should return preview with tags when text message
    Given the user has a message in <mailbox> mailbox with content-type <content-type> subject <subject> and content <content>
    When the user ask for messages "["username@domain.tld|inbox|1"]"
    Then no error is returned
    And the list should contain 1 message
    And the preview of the message is <preview>

    Examples:
      |mailbox |content-type |subject           |content                                                                               |preview                                                                               |
      |"inbox" |"text/plain" |"my test subject" |"Here is a listing of HTML tags : <b>jfjfjfj</b>, <i>jfhdgdgdfj</i>, <u>jfjaaafj</u>" |"Here is a listing of HTML tags : <b>jfjfjfj</b>, <i>jfhdgdgdfj</i>, <u>jfjaaafj</u>" |

  Scenario: Retrieving message should filter properties
    Given the user has a message in "inbox" mailbox with subject "my test subject" and content "testmail"
    When the user is getting his messages with parameters
      |ids        |["username@domain.tld\|inbox\|1"] |
      |properties |["id", "subject"]                 |
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "username@domain.tld|inbox|1"
    And the subject of the message is "my test subject"
    And the property "textBody" of the message is null
    And the property "isUnread" of the message is null
    And the property "preview" of the message is null
    And the property "headers" of the message is null
    And the property "date" of the message is null

  Scenario: Retrieving message should filter header properties
    Given the user has a message in "inbox" mailbox with subject "my test subject" and content "testmail" with headers
      |From    |user@domain.tld |
      |header1 |Header1Content  |
      |HEADer2 |Header2Content  |
    When the user is getting his messages with parameters
      |ids        |["username@domain.tld\|inbox\|1"]   |
      |properties |["headers.from", "headers.heADER2"] |
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "username@domain.tld|inbox|1"
    And the property "subject" of the message is null
    And the property "textBody" of the message is null
    And the property "isUnread" of the message is null
    And the property "preview" of the message is null
    And the headers of the message contains:
      |From    |user@domain.tld |
      |HEADer2 |Header2Content  |
    And the property "date" of the message is null

  Scenario: Retrieving message should return not found when id does not match
    Given the user has a message in "inbox" mailbox with subject "my test subject" and content "testmail"
    When the user ask for messages "["username@domain.tld|inbox|12"]"
    Then no error is returned
    And the list of messages is empty
    And the notFound list should contains "username@domain.tld|inbox|12"

  Scenario: Retrieving message should return mandatory properties when not asked
    Given the user has a message in "inbox" mailbox with subject "my test subject" and content "testmail"
    When the user is getting his messages with parameters
      |ids        |["username@domain.tld\|inbox\|1"] |
      |properties |["subject"]                       |
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "username@domain.tld|inbox|1"
    And the subject of the message is "my test subject"

  Scenario: Retrieving message should return attachments when some
    Given the user has a message in "inbox" mailbox with two attachments
    When the user ask for messages "["username@domain.tld|inbox|1"]"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 2 attachments
    And the first attachment is:
      |key      | value                                     |
      |blobId   |"223a76c0e8c1b1762487d8e0598bd88497d73ef2" |
      |type     |"image/jpeg"                               |
      |size     |846                                        |
      |cid      |null                                       |
      |isInline |false                                      |
    And the second attachment is:
      |key      | value                                     |
      |blobId   |"58aa22c2ec5770fb9e574ba19008dbfc647eba43" |
      |type     |"image/jpeg"                               |
      |size     |597                                        |
      |cid      |"part1.37A15C92.A7C3488D@linagora.com"     |
      |isInline |true                                       |

  Scenario: Retrieving message should return attachments and html body when some attachments and html message
    Given the user has a message in "inbox" mailbox with two attachments
    When the user ask for messages "["username@domain.tld|inbox|1"]"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 2 attachments
    And the preview of the message is "html\n"
    And the property "textBody" of the message is null
    And the htmlBody of the message is "<b>html</b>\n"

  Scenario: Retrieving message should return attachments and text body when some attachments and text message
    Given the user has a message in "inbox" mailbox with two attachments in text
    When the user ask for messages "["username@domain.tld|inbox|1"]"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 2 attachments
    And the preview of the message is "html\n"
    And the textBody of the message is "html\n"
    And the property "htmlBody" of the message is null

  Scenario: Retrieving message should return attachments and both html/text body when some attachments and both html/text message
    Given the user has a multipart message in "inbox" mailbox
    When the user ask for messages "["username@domain.tld|inbox|1"]"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 1 attachment
    And the preview of the message is "blabla\nbloblo\n"
    And the textBody of the message is "/blabla/\n*bloblo*\n"
    And the htmlBody of the message is "<i>blabla</i>\n<b>bloblo</b>\n"
