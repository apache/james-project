#***************************************************************
# Licensed to the Apache Software Foundation (ASF) under one   *
# or more contributor license agreements.  See the NOTICE file *
# distributed with this work for additional information        *
# regarding copyright ownership.  The ASF licenses this file   *
# to you under the Apache License, Version 2.0 (the            *
# "License"); you may not use this file except in compliance   *
# with the License.  You may obtain a copy of the License at   *
#                                                              *
#   http://www.apache.org/licenses/LICENSE-2.0                 *
#                                                              *
# Unless required by applicable law or agreed to in writing,   *
# software distributed under the License is distributed on an  *
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
# KIND, either express or implied.  See the License for the    *
# specific language governing permissions and limitations      *
# under the License.                                           *
# **************************************************************/
Feature: GetMessages method
  As a James user
  I want to be able to retrieve my messages

  Background:
    Given a domain named "domain.tld"
    And a user "alice@domain.tld"
    And "alice@domain.tld" has a mailbox "INBOX"

  Scenario: Retrieving a message in several mailboxes should return a single message in these mailboxes
    Given "alice@domain.tld" has a mailbox "custom"
    And "alice@domain.tld" has a message "m1" in "INBOX" and "custom" mailboxes with subject "my test subject", content "testmail"
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "m1"
    And the message is in "custom,INBOX" mailboxes

  Scenario: Retrieving messages with a non null accountId should return a NotSupported error
    When "alice@domain.tld" ask for messages using its accountId
    Then an error "The field 'accountId' of 'GetMessagesMethod' is not supported" with type "invalidArguments" is returned

  Scenario: Unknown arguments should be ignored when retrieving messages
    When "alice@domain.tld" ask for messages using unknown arguments
    Then no error is returned
    And the list of unknown messages is empty
    And the list of messages is empty

  Scenario: Retrieving messages with invalid argument should return an InvalidArguments error
    When "alice@domain.tld" ask for messages using invalid argument
    Then an error of type "invalidArguments" is returned

  Scenario: Retrieving messages should return empty list when no message
    When "alice@domain.tld" ask for messages
    Then no error is returned
    And the list of messages is empty

  Scenario: Retrieving message should return not found when message doesn't exist
    When "alice@domain.tld" ask for an unknown message
    Then no error is returned
    And the notFound list should contain the requested message id

  @BasicFeature
  Scenario: Retrieving message should return messages when exists
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with subject "my test subject", content "testmail"
    When "alice@domain.tld" ask for message "m1"
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
    Given "alice@domain.tld" has a message "m1" in <mailbox> mailbox with content-type <content-type> subject <subject>, content <content>
    When "alice@domain.tld" ask for message "m1"
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
    Given "alice@domain.tld" has a message "m1" in <mailbox> mailbox with content-type <content-type> subject <subject>, content <content>
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the preview of the message is <preview>

    Examples:
      |mailbox |content-type |subject           |content                                                                               |preview                                                                               |
      |"INBOX" |"text/plain" |"my test subject" |"Here is a listing of HTML tags : <b>jfjfjfj</b>, <i>jfhdgdgdfj</i>, <u>jfjaaafj</u>" |"Here is a listing of HTML tags : <b>jfjfjfj</b>, <i>jfhdgdgdfj</i>, <u>jfjaaafj</u>" |

  Scenario: Retrieving message should filter properties
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with subject "my test subject", content "testmail"
    When "alice@domain.tld" is getting message "m1" with properties "id, subject"
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
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with subject "my test subject", content "testmail", headers
      |From    |user@domain.tld |
      |header1 |Header1Content  |
      |HEADer2 |Header2Content  |
    When "alice@domain.tld" is getting message "m1" with properties "headers.from, headers.heADER2"
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
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with subject "my test subject", content "testmail"
    When "alice@domain.tld" ask for an unknown message
    Then no error is returned
    And the list of messages is empty
    And the notFound list should contain the requested message id

  Scenario: Retrieving message should return mandatory properties when not asked
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with subject "my test subject", content "testmail"
    When "alice@domain.tld" is getting message "m1" with properties "subject"
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "m1"
    And the subject of the message is "my test subject"

  @BasicFeature
  Scenario: Retrieving message should return attachments when some
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with two attachments
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 2 attachments
    And the first attachment is:
      |type     |image/jpeg; name="4037_014.jpg"                                    |
      |size     |846                                                                |
      |cid      |null                                                               |
      |isInline |false                                                              |
    And the second attachment is:
      |type     |image/jpeg; name="4037_015.jpg"                                    |
      |size     |597                                                                |
      |cid      |part1.37A15C92.A7C3488D@linagora.com                               |
      |isInline |true                                                               |

  Scenario: Retrieving message should return attachments and html body when some attachments and html message
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with two attachments
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 2 attachments
    And the preview of the message is "html  tiramisu"
    And the property "textBody" of the message is null
    And the htmlBody of the message is "<b>html tiramisu</b>\n"

  Scenario: Retrieving message should return attachments and text body when some attachments and text message
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with two attachments in text
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 2 attachments
    And the preview of the message is "html\n"
    And the textBody of the message is "html\n"
    And the property "htmlBody" of the message is null

  Scenario: Retrieving message should return attachments and both html/text body when some attachments and both html/text message
    Given "alice@domain.tld" has a multipart message "m1" in "INBOX" mailbox
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 1 attachment
    And the preview of the message is "blabla\nbloblo\n"
    And the textBody of the message is "/blabla/\n*bloblo*\n"
    And the htmlBody of the message is "<i>blabla</i>\n<b>bloblo</b>\n"

  Scenario: Retrieving message should return image and html body when multipart/alternative where first part is multipart/related with html and image
    Given "alice@domain.tld" has a multipart/related message "m1" in "INBOX" mailbox
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 1 attachment
    And the preview of the message is "multipart/related content"
    And the property "textBody" of the message is null
    And the htmlBody of the message is "<html>multipart/related content</html>\n"

  Scenario: Retrieving message should return textBody and htmlBody when incoming mail have an inlined HTML and text body without Content-ID
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox, composed of a multipart with inlined text part and inlined html part
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the textBody of the message is "Hello text body\n"
    And the htmlBody of the message is "<html>Hello html body</html>\n"

  Scenario: Retrieving message with more than 1000 char by line should return message when exists
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox beginning by a long line
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "m1"

  Scenario:
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with two same attachments in text
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list of attachments of the message contains 2 attachments

  Scenario: Retrieving message should read content from multipart when some inline attachment and both html/text multipart
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with plain/text inline attachment
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "false"
    And the textBody of the message is "/blabla/\n*bloblo*\n"
    And the htmlBody of the message is "<i>blabla</i>\n<b>bloblo</b>\n"

  Scenario: Retrieving message should find html body when text in main multipart and html in inner multipart
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with text in main multipart and html in inner multipart
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the textBody of the message is "/blabla/\r\n*bloblo*\r\n"
    And the htmlBody of the message is "<i>blabla</i>\r\n<b>bloblo</b>\r\n"

  Scenario: Retrieving message should compute text body from html body
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with html body and no text body
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the textBody of the message is "The Test User created an issue"
    And the htmlBody of the message is "<a>The Test User</a> <strong>created</strong> an issue"

  Scenario: Retrieving message with inline attachment but no CID should convert that inlined attachment to normal attachment
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with inline attachment but no CID
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 1 attachments
    And the first attachment is:
      |type     |application/pdf;	x-unix-mode=0644;	name="deromaCollection-628.pdf"  |
      |cid      |null                                                                    |
      |isInline |false                                                                   |

  Scenario: Retrieving message with inline attachment and blank CID should convert that inlined attachment to normal attachment
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with inline attachment and blank CID
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 1 attachments
    And the first attachment is:
        |type     |application/pdf;	x-unix-mode=0644;	name="deromaCollection-628.pdf"      |
        |cid      |null                                                                      |
        |isInline |false                                                                     |

  Scenario: Preview should be computed even when HTML body contains many tags without content
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with HTML body with many empty tags
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the preview of the message is not empty

  Scenario: Retrieving message which contains multiple same inlined attachments
    Given "alice@domain.tld" has a message "m1" in the "INBOX" mailbox with multiple same inlined attachments "ia1"
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "false"
    And the list of attachments of the message contains only one attachment with cid "1482981567586480bfca67b793175279@linagora.com"

  Scenario: Preview and bodies should respect given charset
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with specific charset
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the preview of the message is "àààà éééé èèèè"
    And the textBody of the message is "àààà\r\n\r\néééé\r\n\r\nèèèè\r\n"
    And the htmlBody of the message is "<html>\r\n  <p>àààà</p>\r\n  <p>éééé</p>\r\n  <p>èèèè</p>\r\n</html>\r\n"

  Scenario: Preview should be normalized in case of long and complicated HTML content
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with long and complicated HTML content
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the preview of the message is not empty
    And the preview should not contain consecutive spaces or blank characters

  Scenario Outline: Preview should display printable characters with charset
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with content-type <content-type> subject "Subject", content <content>, headers
        |Content-Transfer-Encoding    |<tranfer-encoding> |
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the preview of the message contains: "<preview>"

    Examples:
            |content-type                                       |tranfer-encoding   |content                                                                                                     |preview                                                                                      |
            |"text/html; charset=iso-8859-1"                    |quoted-printable   |"Dans le cadre du stage effectu=E9 Mlle 2017, =E0 sign=E9e d=E8s que possible, =E0, tr=E8s, journ=E9e.."    |effectué Mlle 2017, à signée dès que possible, à, très, journée                                                                        |

  @BasicFeature
  Scenario Outline: Retrieving message should display keywords as jmap flag
    Given "alice@domain.tld" has a message "m1" in the "INBOX" mailbox with flags <flags>
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the keywords of the message is <keyword>

    Examples:
            |flags                          |keyword                        |
            |"$Flagged,$Answered,$Draft"    |$Flagged,$Answered,$Draft      |

  Scenario Outline: GetMessages should filter invalid keywords
    Given "alice@domain.tld" has a message "m1" in the "INBOX" mailbox with flags <flags>
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the keywords of the message is <keyword>

    Examples:
      |flags                               |keyword                |
      |"$Draft,@ert,t^a,op§,$user_flag"    |$Draft,$user_flag      |

  Scenario Outline: Retrieving message should display keywords without unsupported jmap flag
    Given "alice@domain.tld" has a message "m1" in the "INBOX" mailbox with flags <flags>
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the keywords of the message is <keyword>

    Examples:
            |flags                                  |keyword                 |
            |"$Flagged,$Answered,$Deleted,$Recent"  |$Flagged,$Answered      |

  Scenario Outline: Retrieving message should display keywords with custom user jmap flag
    Given "alice@domain.tld" has a message "m1" in the "INBOX" mailbox with flags <flags>
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the keywords of the message is <keyword>

    Examples:
            |flags                    |keyword                 |
            |"$Flagged,$Forwarded"    |$Forwarded,$Flagged     |

  Scenario: Retrieving message should include true isForwarded property when set
    Given "alice@domain.tld" has a message "m1" in the "INBOX" mailbox with flags "$Forwarded"
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the isForwarded property of the message is "true"

  Scenario: Retrieving message should include false isForwarded property when not set
    Given "alice@domain.tld" has a message "m1" in the "INBOX" mailbox with flags "$Answered"
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the isForwarded property of the message is "false"

  Scenario: Retrieving message should be possible when message with inlined attachment but without content disposition
    Given "alice@domain.tld" has a message "m1" in the "INBOX" mailbox with inlined attachments without content disposition
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 1 attachments
    And the first attachment is:
      |type     |application/octet-stream; name="encrypted.asc"   |
      |cid      |null                                             |
      |name     |encrypted.asc                                    |
      |isInline |false                                            |

  Scenario: Retrieving message should be possible when message with inlined image but without content disposition
    Given "alice@domain.tld" has a message "m1" in the "INBOX" mailbox with inlined image without content disposition
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 1 attachments
    And the first attachment is:
      |type     |image/png; name=vlc.png                          |
      |cid      |14672787885774e5c4d4cee471352039@linagora.com    |
      |name     |vlc.png                                          |
      |isInline |false                                            |

  Scenario: Retrieving message should be possible when message with inlined attachment but without content ID
    Given "alice@domain.tld" has a message "m1" in the "INBOX" mailbox with inlined image without content ID
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 1 attachments
    And the first attachment is:
    |type     |image/jpeg;	name=IMG_6112.JPG;	x-apple-part-url=B11616AF-86EB-47AF-863A-176A823498DB  |
    |cid      |null                                                                                    |
    |name     |IMG_6112.JPG                                                                            |
    |isInline |false                                                                                   |

  Scenario: Header only text calendar should be read as normal calendar attachment by JMAP
    Given "alice@domain.tld" receives a SMTP message specified in file "eml/ics_in_header.eml" as message "m1"
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the hasAttachment of the message is "true"
    And the list of attachments of the message contains 1 attachments
    And the first attachment is:
    |type     |text/calendar; method=REPLY; charset=UTF-8; name=event.ics   |
    |size     |1096                                                         |
    |name     |event.ics                                                    |
    |isInline |false                                                        |
