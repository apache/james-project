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

import org.apache.james.imap.api.message.Capability;

public interface ImapConstants {
    // Basic response types
    String OK = "OK";

    String NO = "NO";

    String BAD = "BAD";

    String BYE = "BYE";

    String UNTAGGED = "*";

    String CONTINUATION = "+";

    String SP = " ";

    String NIL = "NIL";

    String UID = "UID";

    String MIME_HEADER_CONTENT_LOCATION = "Content-Location";

    String MIME_HEADER_CONTENT_MD5 = "Content-MD5";

    String MIME_HEADER_CONTENT_LANGUAGE = "Content-Language";

    String[] EMPTY_STRING_ARRAY = {};

    byte BYTE_OPENING_PARENTHESIS = 0x28;

    byte BYTE_CLOSING_PARENTHESIS = 0x29;

    byte BYTE_SP = 0x20;

    byte BYTE_DQUOTE = 0x22;

    byte BYTE_BACK_SLASH = 0x5C;

    byte BYTE_QUESTION = 0x3F;

    byte BYTE_OPEN_SQUARE_BRACKET = 0x5B;

    byte BYTE_CLOSE_SQUARE_BRACKET = 0x5D;

    byte BYTE_OPEN_BRACE = 0x7B;

    byte BYTE_CLOSE_BRACE = 0x7D;

    char OPENING_PARENTHESIS = '(';

    char CLOSING_PARENTHESIS = ')';

    char OPENING_SQUARE_BRACKET = '[';

    char CLOSING_SQUARE_BRACKET = ']';

    char SP_CHAR = ' ';

    char DQUOTE = '\"';

    String VERSION = "IMAP4rev1";

    Capability BASIC_CAPABILITIES = Capability.of(VERSION);

    Capability SUPPORTS_LITERAL_PLUS = Capability.of("LITERAL+");

    Capability SUPPORTS_RFC3348 = Capability.of("CHILDREN");

    String UTF8 = "UTF-8";

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

    Capability SUPPORTS_MOVE = Capability.of("MOVE");

    Capability SUPPORTS_UIDPLUS = Capability.of("UIDPLUS");

    Capability SUPPORTS_ANNOTATION = Capability.of("ANNOTATION");
    
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

    String NAME_ATTRIBUTE_HAS_CHILDREN = "\\HasChildren";

    String NAME_ATTRIBUTE_HAS_NO_CHILDREN = "\\HasNoChildren";

    String NAMESPACE_COMMAND_NAME = "NAMESPACE";

    char BACK_SLASH = '\\';

    String STATUS_UNSEEN = "UNSEEN";

    String STATUS_UIDVALIDITY = "UIDVALIDITY";

    String STATUS_UIDNEXT = "UIDNEXT";

    String STATUS_RECENT = "RECENT";

    String STATUS_MESSAGES = "MESSAGES";
    
    String STATUS_HIGHESTMODSEQ = "HIGHESTMODSEQ";

    String UNSUBSCRIBE_COMMAND_NAME = "UNSUBSCRIBE";

    String UID_COMMAND_NAME = "UID";

    String SUBSCRIBE_COMMAND_NAME = "SUBSCRIBE";

    String STORE_COMMAND_NAME = "STORE";

    String STATUS_COMMAND_NAME = "STATUS";

    String SELECT_COMMAND_NAME = "SELECT";

    String UNSELECT_COMMAND_NAME = "UNSELECT";

    String SEARCH_COMMAND_NAME = "SEARCH";

    String RENAME_COMMAND_NAME = "RENAME";

    String NOOP_COMMAND_NAME = "NOOP";

    String IDLE_COMMAND_NAME = "IDLE";

    String LSUB_COMMAND_NAME = "LSUB";

    String LOGOUT_COMMAND_NAME = "LOGOUT";

    String LOGIN_COMMAND_NAME = "LOGIN";

    String LIST_COMMAND_NAME = "LIST";

    String XLIST_COMMAND_NAME = "XLIST";

    String FETCH_COMMAND_NAME = "FETCH";

    String EXPUNGE_COMMAND_NAME = "EXPUNGE";

    String EXAMINE_COMMAND_NAME = "EXAMINE";

    String DELETE_COMMAND_NAME = "DELETE";

    String CREATE_COMMAND_NAME = "CREATE";

    String COPY_COMMAND_NAME = "COPY";

    String MOVE_COMMAND_NAME = "MOVE";

    String CLOSE_COMMAND_NAME = "CLOSE";

    String CHECK_COMMAND_NAME = "CHECK";

    String CAPABILITY_COMMAND_NAME = "CAPABILITY";

    String AUTHENTICATE_COMMAND_NAME = "AUTHENTICATE";

    String APPEND_COMMAND_NAME = "APPEND";
    
    String ENABLE_COMMAND_NAME = "ENABLE";
    
    String GETACL_COMMAND_NAME = "GETACL";

    String SETACL_COMMAND_NAME = "SETACL";
    
    String DELETEACL_COMMAND_NAME = "DELETEACL";
    
    String LISTRIGHTS_COMMAND_NAME = "LISTRIGHTS";
    
    String MYRIGHTS_COMMAND_NAME = "MYRIGHTS";

    String GETQUOTAROOT_COMMAND_NAME = "GETQUOTAROOT";

    String GETQUOTA_COMMAND_NAME = "GETQUOTA";

    String SETQUOTA_COMMAND_NAME = "SETQUOTA";

    String SETANNOTATION_COMMAND_NAME = "SETMETADATA";

    String GETANNOTATION_COMMAND_NAME = "GETMETADATA";

    String LIST_RESPONSE_NAME = "LIST";

    String XLIST_RESPONSE_NAME = "XLIST";

    String LSUB_RESPONSE_NAME = "LSUB";

    String SEARCH_RESPONSE_NAME = "SEARCH";

    String ACL_RESPONSE_NAME = "ACL";

    String QUOTA_RESPONSE_NAME = "QUOTA";

    String QUOTAROOT_RESPONSE_NAME = "QUOTAROOT";

    String LISTRIGHTS_RESPONSE_NAME = "LISTRIGHTS";
    
    String MYRIGHTS_RESPONSE_NAME = "MYRIGHTS";

    String ANNOTATION_RESPONSE_NAME = "METADATA";

    String NAME_ATTRIBUTE_NOINFERIORS = "\\Noinferiors";

    String NAME_ATTRIBUTE_NOSELECT = "\\Noselect";

    String NAME_ATTRIBUTE_MARKED = "\\Marked";

    String NAME_ATTRIBUTE_UNMARKED = "\\Unmarked";

    String PS_TEXT = "TEXT";

    String PS_HEADER = "HEADER";

    String PS_MIME = "MIME";

    String FETCH_RFC822 = "RFC822";

    String FETCH_RFC822_HEADER = "RFC822.HEADER";

    String FETCH_RFC822_TEXT = "RFC822.TEXT";

    String FETCH_BODY_STRUCTURE = "BODYSTRUCTURE";

    String FETCH_BODY = "BODY";
    
    String FETCH_MODSEQ = "MODSEQ";

    
    String STARTTLS = "STARTTLS";

    String LINE_END = "\r\n";
    long MAX_NZ_NUMBER = 4294967295L;
    long MIN_NZ_NUMBER = 1L;

    String COMPRESS_COMMAND_NAME = "COMPRESS";

    int DEFAULT_BATCH_SIZE = 100;

    // Quota resources definition

    String STORAGE_QUOTA_RESOURCE = "STORAGE";

    String MESSAGE_QUOTA_RESOURCE = "MESSAGE";
}
