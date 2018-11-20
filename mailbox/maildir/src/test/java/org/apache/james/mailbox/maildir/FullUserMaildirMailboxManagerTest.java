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
package org.apache.james.mailbox.maildir;

import java.io.UnsupportedEncodingException;

import org.apache.james.junit.TemporaryFolderExtension;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManagerTest;
import org.apache.james.mailbox.exception.MailboxException;
import org.junit.Ignore;
import org.junit.jupiter.api.extension.RegisterExtension;

public class FullUserMaildirMailboxManagerTest extends MailboxManagerTest {
    @RegisterExtension
    TemporaryFolderExtension temporaryFolder = new TemporaryFolderExtension();
    
    @Override
    protected MailboxManager provideMailboxManager() {
        try {
            return MaildirMailboxManagerProvider.createMailboxManager("/%fulluser", temporaryFolder.getTemporaryFolder().getTempDir());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Ignore("https://issues.apache.org/jira/browse/MAILBOX-292")
    @Override
    public void createMailboxShouldReturnRightId() throws MailboxException, UnsupportedEncodingException {

    }
}
