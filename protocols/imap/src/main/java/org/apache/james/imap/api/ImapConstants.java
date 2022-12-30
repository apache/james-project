/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.imap.api;

import static java.nio.charset.StandardCharsets.US_ASCII;

import org.apache.james.imap.api.message.Capability;

public interface ImapConstants {
    // Basic response types
    String OK = "OK";

    byte[] NO = "NO".getBytes(US_ASCII);

    String BAD = "BAD";

    String BYE = "BYE";

    byte UNTAGGED = 0x2A; // *

    byte CONTINUATION = 0x2B; // +

    byte SP = 0x20;

    byte[] NIL = "NIL".getBytes(US_ASCII);

    byte[] UID = "UID".getBytes(US_ASCII);

    byte BYTE_OPENING_PARENTHESIS = 0x28;

    byte BYTE_CLOSING_PARENTHESIS = 0x29;

    byte BYTE_DQUOTE = 0x22;

    byte BYTE_BACK_SLASH = 0x5C;

    byte BYTE_QUESTION = 0x3F;

    byte BYTE_OPEN_SQUARE_BRACKET = 0x5B;

    byte BYTE_CLOSE_SQUARE_BRACKET = 0x5D;

    byte BYTE_OPEN_BRACE = 0x7B;

    byte BYTE_CLOSE_BRACE = 0x7D;

    char DQUOTE = '\"';

    String VERSION = "IMAP4rev1";

    Capability BASIC_CAPABILITIES = Capability.of(VERSION);

    Capability SUPPORTS_LITERAL_PLUS = Capability.of("LITERAL+");

    Capability SUPPORTS_RFC3348 = Capability.of("CHILDREN");

    Capability SUPPORTS_OBJECTID = Capability.of("OBJECTID");

    Capability SUPPORTS_SAVEDATE = Capability.of("SAVEDATE");

    Capability SUPPORTS_I18NLEVEL_1 = Capability.of("I18NLEVEL=1");

    Capability SUPPORTS_NAMESPACES = Capability.of("NAMESPACE");

    Capability SUPPORTS_STARTTLS = Capability.of("STARTTLS");

    Capability SUPPORTS_IDLE = Capability.of("IDLE");

    Capability SUPPORTS_XLIST = Capability.of("XLIST");

    Capability SUPPORTS_ENABLE = Capability.of("ENABLE");

    Capability SUPPORTS_CONDSTORE = Capability.of("CONDSTORE");

    Capability SUPPORTS_UNSELECT = Capability.of("UNSELECT");

    Capability SUPPORTS_QRESYNC = Capability.of("QRESYNC");

    Capability SUPPORTS_ACL = Capability.of("ACL");

    Capability SUPPORTS_QUOTA = Capability.of("QUOTA");
    Capability SUPPORTS_QUOTA_RES_STORAGE = Capability.of("QUOTA=RES-STORAGE");
    Capability SUPPORTS_QUOTA_RES_MESSAGE = Capability.of("QUOTA=RES-MESSAGE");

    Capability SUPPORTS_MOVE = Capability.of("MOVE");

    Capability SUPPORTS_UIDPLUS = Capability.of("UIDPLUS");

    Capability SUPPORTS_ANNOTATION = Capability.of("METADATA");
    
    String INBOX_NAME = "INBOX";

    String MIME_TYPE_TEXT = "TEXT";

    String MIME_TYPE_MULTIPART = "MULTIPART";

    String MIME_SUBTYPE_PLAIN = "PLAIN";

    String MIME_TYPE_MESSAGE = "MESSAGE";

    String MIME_SUBTYPE_RFC822 = "RFC822";

    // RFC822 CONSTANTS:
    // TODO: Consider switching to standard case
    String RFC822_BCC = "Bcc";

    String RFC822_CC = "Cc";

    String RFC822_FROM = "From";

    String RFC822_DATE = "Date";

    String RFC822_SUBJECT = "Subject";

    String RFC822_TO = "To";

    String RFC822_SENDER = "Sender";

    String RFC822_REPLY_TO = "Reply-To";

    String RFC822_IN_REPLY_TO = "In-Reply-To";

    String RFC822_MESSAGE_ID = "Message-ID";

    byte[] NAME_ATTRIBUTE_HAS_CHILDREN = "\\HasChildren".getBytes(US_ASCII);

    byte[] NAME_ATTRIBUTE_HAS_NO_CHILDREN = "\\HasNoChildren".getBytes(US_ASCII);

    byte[] NAME_ATTRIBUTE_SUBSCRIBED = "\\Subscribed".getBytes(US_ASCII);

    byte[] NAME_ATTRIBUTE_NON_EXISTENT = "\\NonExistent".getBytes(US_ASCII);

    char BACK_SLASH = '\\';

    String STATUS_UNSEEN = "UNSEEN";

    String STATUS_MAILBOXID = "MAILBOXID";

    String STATUS_UIDVALIDITY = "UIDVALIDITY";

    String STATUS_UIDNEXT = "UIDNEXT";

    String STATUS_RECENT = "RECENT";

    String STATUS_MESSAGES = "MESSAGES";

    String STATUS_APPENDLIMIT = "APPENDLIMIT";

    String STATUS_SIZE = "SIZE";

    String STATUS_DELETED = "DELETED";

    String STATUS_DELETED_STORAGE = "DELETED-STORAGE";

    String STATUS_HIGHESTMODSEQ = "HIGHESTMODSEQ";

    ImapCommand CAPABILITY_COMMAND = ImapCommand.anyStateCommand("CAPABILITY");
    ImapCommand COMPRESS_COMMAND = ImapCommand.anyStateCommand("COMPRESS");
    ImapCommand LOGOUT_COMMAND = ImapCommand.anyStateCommand("LOGOUT");
    ImapCommand NOOP_COMMAND = ImapCommand.anyStateCommand("NOOP");
    ImapCommand ID_COMMAND = ImapCommand.anyStateCommand("ID");

    ImapCommand AUTHENTICATE_COMMAND = ImapCommand.nonAuthenticatedStateCommand("AUTHENTICATE");
    ImapCommand LOGIN_COMMAND = ImapCommand.nonAuthenticatedStateCommand("LOGIN");
    ImapCommand STARTTLS_COMMAND = ImapCommand.nonAuthenticatedStateCommand("STARTTLS");

    ImapCommand APPEND_COMMAND = ImapCommand.authenticatedStateCommand("APPEND");
    ImapCommand CREATE_COMMAND = ImapCommand.authenticatedStateCommand("CREATE");
    ImapCommand DELETE_COMMAND = ImapCommand.authenticatedStateCommand("DELETE");
    ImapCommand ENABLE_COMMAND = ImapCommand.authenticatedStateCommand("ENABLE");
    ImapCommand EXAMINE_COMMAND = ImapCommand.authenticatedStateCommand("EXAMINE");
    ImapCommand IDLE_COMMAND = ImapCommand.authenticatedStateCommand("IDLE");
    ImapCommand LIST_COMMAND = ImapCommand.authenticatedStateCommand("LIST");
    ImapCommand LSUB_COMMAND = ImapCommand.authenticatedStateCommand("LSUB");
    ImapCommand NAMESPACE_COMMAND = ImapCommand.authenticatedStateCommand("NAMESPACE");
    ImapCommand RENAME_COMMAND = ImapCommand.authenticatedStateCommand("RENAME");
    ImapCommand SELECT_COMMAND = ImapCommand.authenticatedStateCommand("SELECT");
    ImapCommand STATUS_COMMAND = ImapCommand.authenticatedStateCommand("STATUS");
    ImapCommand SUBSCRIBE_COMMAND = ImapCommand.authenticatedStateCommand("SUBSCRIBE");
    ImapCommand UNSELECT_COMMAND = ImapCommand.authenticatedStateCommand("UNSELECT");
    ImapCommand UNSUBSCRIBE_COMMAND = ImapCommand.authenticatedStateCommand("UNSUBSCRIBE");
    ImapCommand XLIST_COMMAND = ImapCommand.authenticatedStateCommand("XLIST");
    // RFC-4314 IMAP ACL
    ImapCommand DELETEACL_COMMAND = ImapCommand.authenticatedStateCommand("DELETEACL");
    ImapCommand LISTRIGHTS_COMMAND = ImapCommand.authenticatedStateCommand("LISTRIGHTS");
    ImapCommand MYRIGHTS_COMMAND = ImapCommand.authenticatedStateCommand("MYRIGHTS");
    ImapCommand GETACL_COMMAND = ImapCommand.authenticatedStateCommand("GETACL");
    ImapCommand SETACL_COMMAND = ImapCommand.authenticatedStateCommand("SETACL");
    // RFC-2087 IMAP Quota
    ImapCommand GETQUOTA_COMMAND = ImapCommand.authenticatedStateCommand("GETQUOTA");
    ImapCommand GETQUOTAROOT_COMMAND = ImapCommand.authenticatedStateCommand("GETQUOTAROOT");
    ImapCommand SETQUOTA_COMMAND = ImapCommand.authenticatedStateCommand("SETQUOTA");
    // RFC-5464 IMAP Metadata (mailbox annotations)
    ImapCommand GETMETDATA_COMMAND = ImapCommand.authenticatedStateCommand("GETMETADATA");
    ImapCommand SETMETADATA_COMMAND = ImapCommand.authenticatedStateCommand("SETMETADATA");

    ImapCommand CHECK_COMMAND = ImapCommand.selectedStateCommand("CHECK");
    ImapCommand CLOSE_COMMAND = ImapCommand.selectedStateCommand("CLOSE");
    ImapCommand COPY_COMMAND = ImapCommand.selectedStateCommand("COPY");
    ImapCommand EXPUNGE_COMMAND = ImapCommand.selectedStateCommand("EXPUNGE");
    ImapCommand FETCH_COMMAND = ImapCommand.selectedStateCommand("FETCH");
    ImapCommand MOVE_COMMAND = ImapCommand.selectedStateCommand("MOVE");
    ImapCommand SEARCH_COMMAND = ImapCommand.selectedStateCommand("SEARCH");
    ImapCommand STORE_COMMAND = ImapCommand.selectedStateCommand("STORE");
    ImapCommand UID_COMMAND = ImapCommand.selectedStateCommand("UID");
    ImapCommand REPLACE_COMMAND = ImapCommand.selectedStateCommand("REPLACE");

    String ACL_RESPONSE_NAME = "ACL";

    String QUOTA_RESPONSE_NAME = "QUOTA";

    String QUOTAROOT_RESPONSE_NAME = "QUOTAROOT";

    String ANNOTATION_RESPONSE_NAME = "METADATA";

    byte[] NAME_ATTRIBUTE_NOINFERIORS = "\\Noinferiors".getBytes(US_ASCII);

    byte[] NAME_ATTRIBUTE_NOSELECT = "\\Noselect".getBytes(US_ASCII);

    byte[] NAME_ATTRIBUTE_MARKED = "\\Marked".getBytes(US_ASCII);

    byte[] NAME_ATTRIBUTE_UNMARKED = "\\Unmarked".getBytes(US_ASCII);

    String FETCH_RFC822 = "RFC822";

    String FETCH_RFC822_HEADER = "RFC822.HEADER";

    String FETCH_RFC822_TEXT = "RFC822.TEXT";

    byte[] FETCH_BODY_STRUCTURE = "BODYSTRUCTURE".getBytes(US_ASCII);

    byte[] FETCH_BODY = "BODY".getBytes(US_ASCII);

    byte[] FETCH_MODSEQ = "MODSEQ".getBytes(US_ASCII);

    String LINE_END = "\r\n";
    byte[] LINE_END_BYTES = LINE_END.getBytes();
    long MAX_NZ_NUMBER = 4294967295L;
    long MIN_NZ_NUMBER = 1L;

    // Quota resources definition

    String STORAGE_QUOTA_RESOURCE = "STORAGE";

    String MESSAGE_QUOTA_RESOURCE = "MESSAGE";
    String EMAILID = "EMAILID";
    String THREADID = "THREADID";
    String SAVEDATE = "SAVEDATE";
}
