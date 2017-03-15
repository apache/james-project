Feature: GetMessages method
  As a James user
  I want to be able to retrieve my messages

  Background:
    Given a domain named "domain.tld"
    And a connected user "username@domain.tld"
    And "username@domain.tld" has a mailbox "INBOX"

  Scenario: Retrieving a message in several mailboxes should return a single message in these mailboxes
    Given "username@domain.tld" has a mailbox "custom"
    And the user has a message "m1" in "INBOX" and "custom" mailboxes with subject "my test subject", content "testmail"
    When the user ask for messages "m1"
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "m1"
    And the message is in "custom,INBOX" mailboxes

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
    When the user ask for an unknown message
    Then no error is returned
    And the notFound list should contain the requested message id

  Scenario: Retrieving message should return messages when exists
    Given the user has a message "m1" in "INBOX" mailbox with subject "my test subject", content "testmail"
    When the user ask for messages "m1"
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "m1"
    And the threadId of the message is "m1"
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
    Given the user has a message "m1" in <mailbox> mailbox with content-type <content-type> subject <subject>, content <content>
    When the user ask for messages "m1"
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "m1"
    And the threadId of the message is "m1"
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
      |"INBOX" |"text/html"  |"my test subject" |my test subject |"This is a <b>HTML</b> mail"                                                                                                    |"This is a HTML mail"                                                                        |
      |"INBOX" |"text/html"  |"my test subject" |my test subject |"This is a <b>HTML</b> mail containing <u>underlined part</u>, <i>italic part</i> and <u><i>underlined AND italic part</i></u>" |"This is a HTML mail containing underlined part, italic part and underlined AND italic part" |
      |"INBOX" |"text/html"  |"my test subject" |my test subject |"This is a <ganan>HTML</b> mail"                                                                                                |"This is a HTML mail"                                                                        |

  Scenario Outline: Retrieving message should return preview with tags when text message
    Given the user has a message "m1" in <mailbox> mailbox with content-type <content-type> subject <subject>, content <content>
    When the user ask for messages "m1"
    Then no error is returned
    And the list should contain 1 message
    And the preview of the message is <preview>

    Examples:
      |mailbox |content-type |subject           |content                                                                               |preview                                                                               |
      |"INBOX" |"text/plain" |"my test subject" |"Here is a listing of HTML tags : <b>jfjfjfj</b>, <i>jfhdgdgdfj</i>, <u>jfjaaafj</u>" |"Here is a listing of HTML tags : <b>jfjfjfj</b>, <i>jfhdgdgdfj</i>, <u>jfjaaafj</u>" |

  Scenario: Retrieving message should filter properties
    Given the user has a message "m1" in "INBOX" mailbox with subject "my test subject", content "testmail"
    When the user is getting messages "m1" with properties "id, subject"
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "m1"
    And the subject of the message is "my test subject"
    And the property "textBody" of the message is null
    And the property "isUnread" of the message is null
    And the property "preview" of the message is null
    And the property "headers" of the message is null
    And the property "date" of the message is null

  Scenario: Retrieving message should filter header properties
    Given the user has a message "m1" in "INBOX" mailbox with subject "my test subject", content "testmail", headers
      |From    |user@domain.tld |
      |header1 |Header1Content  |
      |HEADer2 |Header2Content  |
    When the user is getting messages "m1" with properties "headers.from, headers.heADER2"
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "m1"
    And the property "subject" of the message is null
    And the property "textBody" of the message is null
    And the property "isUnread" of the message is null
    And the property "preview" of the message is null
    And the headers of the message contains:
      |From    |user@domain.tld |
      |HEADer2 |Header2Content  |
    And the property "date" of the message is null

  Scenario: Retrieving message should return not found when id does not match
    Given the user has a message "m1" in "INBOX" mailbox with subject "my test subject", content "testmail"
    When the user ask for an unknown message
    Then no error is returned
    And the list of messages is empty
    And the notFound list should contain the requested message id
    
  Scenario: Retrieving message should return mandatory properties when not asked
    Given the user has a message "m1" in "INBOX" mailbox with subject "my test subject", content "testmail"
    When the user is getting messages "m1" with properties "subject"
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "m1"
    And the subject of the message is "my test subject"

  Scenario: Retrieving message should return attachments when some
    Given the user has a message "m1" in "INBOX" mailbox with two attachments
    When the user ask for messages "m1"
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
    Given the user has a message "m1" in "INBOX" mailbox with two attachments
    When the user ask for messages "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 2 attachments
    And the preview of the message is "html\n"
    And the property "textBody" of the message is null
    And the htmlBody of the message is "<b>html</b>\n"

  Scenario: Retrieving message should return attachments and text body when some attachments and text message
    Given the user has a message "m1" in "INBOX" mailbox with two attachments in text
    When the user ask for messages "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 2 attachments
    And the preview of the message is "html\n"
    And the textBody of the message is "html\n"
    And the property "htmlBody" of the message is null

  Scenario: Retrieving message should return attachments and both html/text body when some attachments and both html/text message
    Given the user has a multipart message "m1" in "INBOX" mailbox
    When the user ask for messages "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 1 attachment
    And the preview of the message is "blabla\nbloblo\n"
    And the textBody of the message is "/blabla/\n*bloblo*\n"
    And the htmlBody of the message is "<i>blabla</i>\n<b>bloblo</b>\n"

  Scenario: Retrieving message should return image and html body when multipart/alternative where first part is multipart/related with html and image
    Given the user has a multipart/related message "m1" in "INBOX" mailbox
    When the user ask for messages "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 1 attachment
    And the preview of the message is "multipart/related content"
    And the property "textBody" of the message is null
    And the htmlBody of the message is "<html>multipart/related content</html>\n"

  Scenario: Retrieving message should return textBody and htmlBody when incoming mail have an inlined HTML and text body without Content-ID
    Given the user has a message "m1" in "INBOX" mailbox, composed of a multipart with inlined text part and inlined html part
    When the user ask for messages "m1"
    Then no error is returned
    And the list should contain 1 message
    And the textBody of the message is "Hello text body\n"
    And the htmlBody of the message is "<html>Hello html body</html>\n"
    
  Scenario: Retrieving message with more than 1000 char by line should return message when exists
    Given the user has a message "m1" in "INBOX" mailbox beginning by a long line
    When the user ask for messages "m1"
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "m1"

  Scenario:
    Given the user has a message "m1" in "INBOX" mailbox with two same attachments in text
    When the user ask for messages "m1"
    Then no error is returned
    And the list of attachments of the message contains 2 attachments

  Scenario: Retrieving message should read content from multipart when some inline attachment and both html/text multipart
    Given the user has a message "m1" in "INBOX" mailbox with plain/text inline attachment
    When the user ask for messages "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "false"
    And the textBody of the message is "/blabla/\n*bloblo*\n"
    And the htmlBody of the message is "<i>blabla</i>\n<b>bloblo</b>\n"

  Scenario: Retrieving message should find html body when text in main multipart and html in inner multipart
    Given the user has a message "m1" in "INBOX" mailbox with text in main multipart and html in inner multipart
    When the user ask for messages "m1"
    Then no error is returned
    And the list should contain 1 message
    And the textBody of the message is "/blabla/\r\n*bloblo*\r\n"
    And the htmlBody of the message is "<i>blabla</i>\r\n<b>bloblo</b>\r\n"

  Scenario: Retrieving message should compute text body from html body
    Given the user has a message "m1" in "INBOX" mailbox with html body and no text body
    When the user ask for messages "m1"
    Then no error is returned
    And the list should contain 1 message
    And the textBody of the message is "The Test User created an issue"
    And the htmlBody of the message is "<a>The Test User</a> <strong>created</strong> an issue"