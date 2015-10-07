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

public interface ImapConstants {
    // Basic response types
    public static final String OK = "OK";

    public static final String NO = "NO";

    public static final String BAD = "BAD";

    public static final String BYE = "BYE";

    public static final String UNTAGGED = "*";

    public static final String CONTINUATION = "+";

    public static final String SP = " ";

    public static final String NIL = "NIL";

    public static final String UID = "UID";

    public static final String MIME_HEADER_CONTENT_LOCATION = "Content-Location";

    public static final String MIME_HEADER_CONTENT_MD5 = "Content-MD5";

    public static final String MIME_HEADER_CONTENT_LANGUAGE = "Content-Language";

    public static final String[] EMPTY_STRING_ARRAY = {};

    public static final byte BYTE_OPENING_PARENTHESIS = 0x28;

    public static final byte BYTE_CLOSING_PARENTHESIS = 0x29;

    public static final byte BYTE_SP = 0x20;

    public static final byte BYTE_DQUOTE = 0x22;

    public static final byte BYTE_BACK_SLASH = 0x5C;

    public static final byte BYTE_QUESTION = 0x3F;

    public static final byte BYTE_OPEN_SQUARE_BRACKET = 0x5B;

    public static final byte BYTE_CLOSE_SQUARE_BRACKET = 0x5D;

    public static final byte BYTE_OPEN_BRACE = 0x7B;

    public static final byte BYTE_CLOSE_BRACE = 0x7D;

    public static final char OPENING_PARENTHESIS = '(';

    public static final char CLOSING_PARENTHESIS = ')';

    public static final char OPENING_SQUARE_BRACKET = '[';

    public static final char CLOSING_SQUARE_BRACKET = ']';

    public static final char SP_CHAR = ' ';

    public static final char DQUOTE = '\"';

    public static final String VERSION = "IMAP4rev1";

    public static final String SUPPORTS_LITERAL_PLUS = "LITERAL+";

    public static final String SUPPORTS_RFC3348 = "CHILDREN";

    public static final String UTF8 = "UTF-8";
    
    public static final String SUPPORTS_I18NLEVEL_1 = "I18NLEVEL=1";

    public static final String SUPPORTS_NAMESPACES = "NAMESPACE";

    public static final String SUPPORTS_STARTTLS = "STARTTLS";

    public static final String SUPPORTS_IDLE = "IDLE";

    public static final String SUPPORTS_XLIST = "XLIST";

    public static final String SUPPORTS_ENABLE = "ENABLE";
    
    public static final String SUPPORTS_CONDSTORE = "CONDSTORE";
    
    public static final String SUPPORTS_QRESYNC = "QRESYNC";

    public static final String SUPPORTS_ACL = "ACL";

    public static final String SUPPORTS_QUOTA = "QUOTA";
    
    public static final String INBOX_NAME = "INBOX";

    public static final String MIME_TYPE_TEXT = "TEXT";

    public static final String MIME_TYPE_MULTIPART = "MULTIPART";

    public static final String MIME_SUBTYPE_PLAIN = "PLAIN";

    public static final String MIME_TYPE_MESSAGE = "MESSAGE";

    public static final String MIME_SUBTYPE_RFC822 = "RFC822";

    // RFC822 CONSTANTS:
    // TODO: Consider switching to standard case
    public static final String RFC822_BCC = "Bcc";

    public static final String RFC822_CC = "Cc";

    public static final String RFC822_FROM = "From";

    public static final String RFC822_DATE = "Date";

    public static final String RFC822_SUBJECT = "Subject";

    public static final String RFC822_TO = "To";

    public static final String RFC822_SENDER = "Sender";

    public static final String RFC822_REPLY_TO = "Reply-To";

    public static final String RFC822_IN_REPLY_TO = "In-Reply-To";

    public static final String RFC822_MESSAGE_ID = "Message-ID";

    public static final String NAME_ATTRIBUTE_HAS_CHILDREN = "\\HasChildren";

    public static final String NAME_ATTRIBUTE_HAS_NO_CHILDREN = "\\HasNoChildren";

    public static final String NAMESPACE_COMMAND_NAME = "NAMESPACE";

    public static final char BACK_SLASH = '\\';

    public static final String STATUS_UNSEEN = "UNSEEN";

    public static final String STATUS_UIDVALIDITY = "UIDVALIDITY";

    public static final String STATUS_UIDNEXT = "UIDNEXT";

    public static final String STATUS_RECENT = "RECENT";

    public static final String STATUS_MESSAGES = "MESSAGES";
    
    public static final String STATUS_HIGHESTMODSEQ = "HIGHESTMODSEQ";

    public static final String UNSUBSCRIBE_COMMAND_NAME = "UNSUBSCRIBE";

    public static final String UID_COMMAND_NAME = "UID";

    public static final String SUBSCRIBE_COMMAND_NAME = "SUBSCRIBE";

    public static final String STORE_COMMAND_NAME = "STORE";

    public static final String STATUS_COMMAND_NAME = "STATUS";

    public static final String SELECT_COMMAND_NAME = "SELECT";

    public static final String UNSELECT_COMMAND_NAME = "UNSELECT";

    public static final String SEARCH_COMMAND_NAME = "SEARCH";

    public static final String RENAME_COMMAND_NAME = "RENAME";

    public static final String NOOP_COMMAND_NAME = "NOOP";

    public static final String IDLE_COMMAND_NAME = "IDLE";

    public static final String LSUB_COMMAND_NAME = "LSUB";

    public static final String LOGOUT_COMMAND_NAME = "LOGOUT";

    public static final String LOGIN_COMMAND_NAME = "LOGIN";

    public static final String LIST_COMMAND_NAME = "LIST";

    public static final String XLIST_COMMAND_NAME = "XLIST";

    public static final String FETCH_COMMAND_NAME = "FETCH";

    public static final String EXPUNGE_COMMAND_NAME = "EXPUNGE";

    public static final String EXAMINE_COMMAND_NAME = "EXAMINE";

    public static final String DELETE_COMMAND_NAME = "DELETE";

    public static final String CREATE_COMMAND_NAME = "CREATE";

    public static final String COPY_COMMAND_NAME = "COPY";

    public static final String MOVE_COMMAND_NAME = "MOVE";

    public static final String CLOSE_COMMAND_NAME = "CLOSE";

    public static final String CHECK_COMMAND_NAME = "CHECK";

    public static final String CAPABILITY_COMMAND_NAME = "CAPABILITY";

    public static final String AUTHENTICATE_COMMAND_NAME = "AUTHENTICATE";

    public static final String APPEND_COMMAND_NAME = "APPEND";
    
    public static final String ENABLE_COMMAND_NAME = "ENABLE";
    
    public static final String GETACL_COMMAND_NAME = "GETACL";

    public static final String SETACL_COMMAND_NAME = "SETACL";
    
    public static final String DELETEACL_COMMAND_NAME = "DELETEACL";
    
    public static final String LISTRIGHTS_COMMAND_NAME = "LISTRIGHTS";
    
    public static final String MYRIGHTS_COMMAND_NAME = "MYRIGHTS";

    public static final String GETQUOTAROOT_COMMAND_NAME = "GETQUOTAROOT";

    public static final String GETQUOTA_COMMAND_NAME = "GETQUOTA";

    public static final String SETQUOTA_COMMAND_NAME = "SETQUOTA";

    public static final String LIST_RESPONSE_NAME = "LIST";

    public static final String XLIST_RESPONSE_NAME = "XLIST";

    public static final String LSUB_RESPONSE_NAME = "LSUB";

    public static final String SEARCH_RESPONSE_NAME = "SEARCH";

    public static final String ACL_RESPONSE_NAME = "ACL";

    public static final String QUOTA_RESPONSE_NAME = "QUOTA";

    public static final String QUOTAROOT_RESPONSE_NAME = "QUOTAROOT";

    public static final String LISTRIGHTS_RESPONSE_NAME = "LISTRIGHTS";
    
    public static final String MYRIGHTS_RESPONSE_NAME = "MYRIGHTS";

    public static final String NAME_ATTRIBUTE_NOINFERIORS = "\\Noinferiors";

    public static final String NAME_ATTRIBUTE_NOSELECT = "\\Noselect";

    public static final String NAME_ATTRIBUTE_MARKED = "\\Marked";

    public static final String NAME_ATTRIBUTE_UNMARKED = "\\Unmarked";

    public static final String PS_TEXT = "TEXT";

    public static final String PS_HEADER = "HEADER";

    public static final String PS_MIME = "MIME";

    public static final String FETCH_RFC822 = "RFC822";

    public static final String FETCH_RFC822_HEADER = "RFC822.HEADER";

    public static final String FETCH_RFC822_TEXT = "RFC822.TEXT";

    public static final String FETCH_BODY_STRUCTURE = "BODYSTRUCTURE";

    public static final String FETCH_BODY = "BODY";
    
    public static final String FETCH_MODSEQ = "MODSEQ";

    
    public static final String STARTTLS = "STARTTLS";

    public static final String LINE_END = "\r\n";
    public static final long MAX_NZ_NUMBER = 4294967295L;
    public static final long MIN_NZ_NUMBER = 1L;

    public static final String COMPRESS_COMMAND_NAME = "COMPRESS";

    public static final int DEFAULT_BATCH_SIZE = 100;

    // Quota resources definition

    public static final String STORAGE_QUOTA_RESOURCE = "STORAGE";

    public static final String MESSAGE_QUOTA_RESOURCE = "MESSAGE";
}
