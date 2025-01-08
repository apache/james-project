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

package org.apache.james.mailbox.model;

import java.util.Optional;

/**
 * Constants which are used within the mailbox api and implementations
 */
public class MailboxConstants {

    /**
     * The char which is used to prefix a namespace
     */
    public static final char NAMESPACE_PREFIX_CHAR = '#';

    /** The namespace used for store user inboxes */
    public static final String USER_NAMESPACE = NAMESPACE_PREFIX_CHAR + "private";

    /** The delimiter used to seperated parent/child folders */
    public static char FOLDER_DELIMITER = Optional.ofNullable(System.getProperty("james.mailbox.folder.delimiter"))
            .map(MailboxFolderDelimiter::parse).orElse(MailboxFolderDelimiter.DOT).value;

    public enum MailboxFolderDelimiter {
        // NOTE: When changing this list, make sure to adjust the MailboxFolderDelimiterAwareTests as well.
        // Values currently left-out explicitly:
        // hash sign '#' (Clashes with namespace prefix character)
        // backslash '\\' (Anticipated some problems with the PrefixedRegex matching.
        //                 Also, because it is the escaping character, it can generally be a bit more annoying
        //                 to deal with in strings)
        DOT('.'),
        SLASH('/'),
        PIPE('|'),
        COMMA(','),
        COLON(':'),
        SEMICOLON(';');

        public final char value;

        MailboxFolderDelimiter(char value) {
            this.value = value;
        }

        static MailboxFolderDelimiter parse(String input) {
            for (MailboxFolderDelimiter delimiter: values()) {
                if (delimiter.name().equalsIgnoreCase(input)) {
                    return delimiter;
                }
            }
            throw new IllegalArgumentException(String.format("Invalid mailbox delimiter `%s`", input));
        }
    }

    /** The name of the INBOX */
    public static final String INBOX = "INBOX";

    /** The limitation of annotation data */
    public static final int DEFAULT_LIMIT_ANNOTATION_SIZE = 1024;

    /** The maximum number of annotations on a mailbox */
    public static final int DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX = 10;
}
