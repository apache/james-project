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

import org.apache.james.core.Username;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * A base class for all tests that have to deal with the mailbox path delimiter.
 * It will make sure to install the delimiter specified by the subclass during the test lifecycle and provides
 * some utility methods for writing tests using the active delimiter.
 * <p>
 * NOTE: If you add a new folder delimiter, make sure to add tests for it in all classes extending this one!
 */
public abstract class MailboxFolderDelimiterAwareTest {
    public abstract char folderDelimiter();

    static char initialFolderDelimiter;

    @BeforeEach
    public void setUp() {
        initialFolderDelimiter = MailboxConstants.FOLDER_DELIMITER;
        MailboxConstants.FOLDER_DELIMITER = folderDelimiter();
    }

    @AfterEach
    public void tearDown() {
        MailboxConstants.FOLDER_DELIMITER = initialFolderDelimiter;
    }

    /**
     * Adjust the given string assumed to contain path delimiter dots ('.') to an equivalent version for a different
     * delimiter.
     * For example, a string "folder.subfolder.subsubfolder" would be converted into "folder/subfolder/subsubfolder" when
     * the active FOLDER_DELIMITER is '/'.
     * This is used to test that all delimiters are handled correctly in a lot of different scenarios
     * without having to manually assemble strings with the active path delimiter
     * (like "folder" + MailboxConstants.FOLDER_DELIMITER + "subfolder" + MailboxConstants.FOLDER_DELIMITER + "subsubfolder")
     * everywhere, which quickly becomes tedious.
     */
    public static String adjustToActiveFolderDelimiter(String valueWithDots) {
        // Because the test setup will configure the desired delimiter to be used,
        // we do not need to pass it in manually here.
        return valueWithDots.replace('.', MailboxConstants.FOLDER_DELIMITER);
    }

    /**
     * See {@link #adjustToActiveFolderDelimiter(String)}.
     */
    public static Username adjustToActiveFolderDelimiter(Username username) {
        return Username.of(adjustToActiveFolderDelimiter(username.asString()));
    }
}
