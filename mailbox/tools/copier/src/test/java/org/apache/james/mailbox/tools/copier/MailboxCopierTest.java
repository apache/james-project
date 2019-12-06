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
package org.apache.james.mailbox.tools.copier;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.mock.DataProvisioner;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.Before;
import org.junit.Test;

/**
 * Test class for the {@link MailboxCopierImpl} implementation.
 *
 * The InMemoryMailboxManager will be used as source and destination
 * Mailbox Manager.
 *
 */
public class MailboxCopierTest {
    /**
     * The instance for the test mailboxCopier.
     */
    private MailboxCopierImpl mailboxCopier;

    /**
     * The instance for the source Mailbox Manager.
     */
    private MailboxManager srcMemMailboxManager;

    /**
     * The instance for the destination Mailbox Manager.
     */
    private MailboxManager dstMemMailboxManager;

    /**
     * Setup the mailboxCopier and the source and destination
     * Mailbox Manager.
     *
     * We use a InMemoryMailboxManager implementation.
     *
     * @throws BadCredentialsException
     * @throws MailboxException
     */
    @Before
    public void setup() throws BadCredentialsException, MailboxException {
        mailboxCopier = new MailboxCopierImpl();

        srcMemMailboxManager = newInMemoryMailboxManager();
        dstMemMailboxManager = newInMemoryMailboxManager();

    }

    /**
     * Feed the source MailboxManager with the number of mailboxes and
     * messages per mailbox.
     *
     * Copy the mailboxes to the destination Mailbox Manager, and assert the number
     * of mailboxes and messages per mailbox is the same as in the source
     * Mailbox Manager.
     *
     * @throws MailboxException
     * @throws IOException
     */
    @Test
    public void testMailboxCopy() throws MailboxException, IOException {
        DataProvisioner.feedMailboxManager(srcMemMailboxManager);

        assertMailboxManagerSize(srcMemMailboxManager, 1);

        mailboxCopier.copyMailboxes(srcMemMailboxManager, dstMemMailboxManager);
        assertMailboxManagerSize(dstMemMailboxManager, 1);

        // We copy a second time to assert existing mailboxes does not give issue.
        mailboxCopier.copyMailboxes(srcMemMailboxManager, dstMemMailboxManager);
        assertMailboxManagerSize(dstMemMailboxManager, 2);

    }

    /**
     * Utility method to assert the number of mailboxes and messages per mailbox
     * are the ones expected.
     *
     * @throws MailboxException
     * @throws BadCredentialsException
     */
    private void assertMailboxManagerSize(MailboxManager mailboxManager, int multiplicationFactor) throws BadCredentialsException, MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(Username.of("manager"));
        mailboxManager.startProcessingRequest(mailboxSession);

        List<MailboxPath> mailboxPathList = mailboxManager.list(mailboxSession);

        assertThat(mailboxPathList).hasSize(DataProvisioner.EXPECTED_MAILBOXES_COUNT);

        for (MailboxPath mailboxPath: mailboxPathList) {
            MailboxSession userSession = mailboxManager.createSystemSession(mailboxPath.getUser());
            mailboxManager.startProcessingRequest(mailboxSession);
            MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, userSession);
            assertThat(messageManager.getMetaData(false, userSession, FetchGroup.NO_UNSEEN).getMessageCount()).isEqualTo(DataProvisioner.MESSAGE_PER_MAILBOX_COUNT * multiplicationFactor);
        }

        mailboxManager.endProcessingRequest(mailboxSession);
        mailboxManager.logout(mailboxSession);
    }

    /**
     * Utility method to instantiate a new InMemoryMailboxManger with
     * the needed MailboxSessionMapperFactory, Authenticator and UidProvider.
     *
     * @return a new InMemoryMailboxManager
     */
    private MailboxManager newInMemoryMailboxManager() {
        return InMemoryIntegrationResources.defaultResources().getMailboxManager();
    }

}
