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

import java.io.IOException;

import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.junit.rules.TemporaryFolder;

public class MaildirMailboxManagerProvider {
    public static StoreMailboxManager createMailboxManager(String configuration, TemporaryFolder temporaryFolder) throws MailboxException, IOException {
        MaildirStore store = new MaildirStore(temporaryFolder.newFolder().getPath() + configuration, new JVMMailboxPathLocker());
        MaildirMailboxSessionMapperFactory mf = new MaildirMailboxSessionMapperFactory(store);

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();
        DefaultDelegatingMailboxListener delegatingListener = new DefaultDelegatingMailboxListener();
        MailboxEventDispatcher mailboxEventDispatcher = new MailboxEventDispatcher(delegatingListener);
        StoreRightManager storeRightManager = new StoreRightManager(mf, aclResolver, groupMembershipResolver, mailboxEventDispatcher);

        Authenticator noAuthenticator = null;
        Authorizator noAuthorizator = null;

        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mf, storeRightManager);
        StoreMailboxManager manager = new StoreMailboxManager(mf, noAuthenticator, noAuthorizator, new JVMMailboxPathLocker(),
            messageParser, new DefaultMessageId.Factory(), annotationManager,
            mailboxEventDispatcher, delegatingListener, storeRightManager);
        manager.init();

        return manager;
    }
}
