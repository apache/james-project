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

package org.apache.james.mailbox.jpa.openjpa;


import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.JPAMailboxManager;
import org.apache.james.mailbox.jpa.JPAMailboxSessionMapperFactory;
import org.apache.james.mailbox.jpa.mail.model.openjpa.EncryptDecryptHelper;
import org.apache.james.mailbox.jpa.openjpa.OpenJPAMessageManager.AdvancedFeature;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.mail.model.Mailbox;

/**
 * OpenJPA implementation of MailboxManager
 *
 */
public class OpenJPAMailboxManager extends JPAMailboxManager {

    private AdvancedFeature feature;

    public OpenJPAMailboxManager(JPAMailboxSessionMapperFactory mapperFactory, Authenticator authenticator, MailboxPathLocker locker, boolean useStreaming, MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver) {
        super(mapperFactory, authenticator,  locker, aclResolver, groupMembershipResolver);
        if (useStreaming) {
            feature = AdvancedFeature.Streaming;
        } else {
            feature = AdvancedFeature.None;
        }
    }

    public OpenJPAMailboxManager(JPAMailboxSessionMapperFactory mapperFactory, Authenticator authenticator, MailboxPathLocker locker,  String encryptPass, MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver) {
        super(mapperFactory, authenticator,  locker, aclResolver, groupMembershipResolver);
        if (encryptPass != null) {
            EncryptDecryptHelper.init(encryptPass);
            feature = AdvancedFeature.Encryption;
        } else {
            feature = AdvancedFeature.None;
        }
    }
    
    public OpenJPAMailboxManager(JPAMailboxSessionMapperFactory mapperFactory, Authenticator authenticator, MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver) {
        this(mapperFactory, authenticator, new JVMMailboxPathLocker(), false, aclResolver, groupMembershipResolver);
    }

    @Override
    protected StoreMessageManager<JPAId> createMessageManager(Mailbox<JPAId> mailboxRow, MailboxSession session) throws MailboxException {
        return new OpenJPAMessageManager(getMapperFactory(),
            getMessageSearchIndex(),
            getEventDispatcher(),
            getLocker(),
            mailboxRow,
            feature,
            getAclResolver(),
            getGroupMembershipResolver(),
            getQuotaManager(),
            getQuotaRootResolver());
    }
}
