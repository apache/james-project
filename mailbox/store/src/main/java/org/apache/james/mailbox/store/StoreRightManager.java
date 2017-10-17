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

package org.apache.james.mailbox.store;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.RightManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.ACLCommand;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.transaction.Mapper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

public class StoreRightManager implements RightManager {

    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final MailboxACLResolver aclResolver;
    private final GroupMembershipResolver groupMembershipResolver;

    @Inject
    public StoreRightManager(MailboxSessionMapperFactory mailboxSessionMapperFactory,
                             MailboxACLResolver aclResolver,
                             GroupMembershipResolver groupMembershipResolver) {
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.aclResolver = aclResolver;
        this.groupMembershipResolver = groupMembershipResolver;
    }

    @Override
    public boolean hasRight(MailboxPath mailboxPath, Right right, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);
        return hasRight(mailbox, right, session);
    }

    public boolean hasRight(Mailbox mailbox, Right right, MailboxSession session) throws MailboxException {
        MailboxSession.User user = session.getUser();
        String userName = user != null ? user.getUserName() : null;
        boolean resourceOwnerIsGroup = new GroupFolderResolver(session).isGroupFolder(mailbox);

        return aclResolver.hasRight(userName, groupMembershipResolver, right, mailbox.getACL(), mailbox.getUser(),
                                    resourceOwnerIsGroup);
    }

    @Override
    public Rfc4314Rights myRights(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);
        return myRights(session, mailbox);
    }

    @Override
    public Rfc4314Rights myRights(MailboxId mailboxId, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxById(mailboxId);
        return myRights(session, mailbox);
    }

    @Override
    public Rfc4314Rights[] listRigths(MailboxPath mailboxPath, EntryKey key, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);
        return aclResolver.listRights(key, groupMembershipResolver, mailbox.getUser(), new GroupFolderResolver(session).isGroupFolder(mailbox));
    }

    @Override
    public void applyRightsCommand(MailboxPath mailboxPath, ACLCommand mailboxACLCommand, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);
        mapper.execute(Mapper.toTransaction(() -> mapper.updateACL(mailbox, mailboxACLCommand)));
    }

    @Override
    public void setRights(MailboxPath mailboxPath, MailboxACL mailboxACL, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);
        mapper.execute(Mapper.toTransaction(() -> mapper.setACL(mailbox, mailboxACL)));
    }

    public boolean isReadWrite(MailboxSession session, Mailbox mailbox, Flags sharedPermanentFlags) throws UnsupportedRightException {
        return aclResolver.isReadWrite(myRights(session, mailbox), sharedPermanentFlags);
    }

    @Override
    public void setRights(MailboxId mailboxId, MailboxACL mailboxACL, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxById(mailboxId);
        setRights(mailbox.generateAssociatedPath(), mailboxACL, session);
    }

    /**
     * Applies the global ACL (if there are any) to the mailbox ACL.
     *
     * @param mailboxSession
     * @return the ACL of the present mailbox merged with the global ACL (if
     *         there are any).
     * @throws UnsupportedRightException
     */
    public MailboxACL getResolvedMailboxACL(Mailbox mailbox, MailboxSession mailboxSession) throws UnsupportedRightException {
        MailboxACL acl = aclResolver.applyGlobalACL(
            mailbox.getACL(),
            new GroupFolderResolver(mailboxSession).isGroupFolder(mailbox));

        return filteredForSession(mailbox, acl, mailboxSession);
    }

    /**
     * ACL is sensible information and as such we should expose as few information as possible
     * to users. This method allows to filter a {@link MailboxACL} in order to present it to
     * the connected user.
     */
    @VisibleForTesting
    static MailboxACL filteredForSession(Mailbox mailbox, MailboxACL acl, MailboxSession mailboxSession) throws UnsupportedRightException {
        if (mailboxSession.getUser().isSameUser(mailbox.getUser())) {
            return acl;
        }
        MailboxACL.EntryKey userAsKey = MailboxACL.EntryKey.createUserEntryKey(mailboxSession.getUser().getUserName());
        Rfc4314Rights rights = acl.getEntries().getOrDefault(userAsKey, new Rfc4314Rights());
        if (rights.contains(MailboxACL.Right.Administer)) {
            return acl;
        }
        return new MailboxACL(ImmutableMap.of(userAsKey, rights));
    }

    private Rfc4314Rights myRights(MailboxSession session, Mailbox mailbox) throws UnsupportedRightException {
        MailboxSession.User user = session.getUser();
        if (user != null) {
            return aclResolver.resolveRights(user.getUserName(), groupMembershipResolver, mailbox.getACL(), mailbox.getUser(), new GroupFolderResolver(session).isGroupFolder(mailbox));
        } else {
            return MailboxACL.NO_RIGHTS;
        }
    }
}
