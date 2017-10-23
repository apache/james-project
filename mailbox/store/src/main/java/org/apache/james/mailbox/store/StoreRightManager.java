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
        return myRights(mailboxPath, session).contains(right);
    }

    @Override
    public boolean hasRight(MailboxId mailboxId, Right right, MailboxSession session) throws MailboxException {
        return myRights(mailboxId, session).contains(right);
    }

    public boolean hasRight(Mailbox mailbox, Right right, MailboxSession session) throws MailboxException {
        return myRights(mailbox, session).contains(right);
    }

    @Override
    public Rfc4314Rights myRights(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);
        return myRights(mailbox, session);
    }

    @Override
    public Rfc4314Rights myRights(MailboxId mailboxId, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxById(mailboxId);
        return myRights(mailbox, session);
    }

    public Rfc4314Rights myRights(Mailbox mailbox, MailboxSession session) throws UnsupportedRightException {
        MailboxSession.User user = session.getUser();
        if (user != null) {
            return aclResolver.resolveRights(user.getUserName(), groupMembershipResolver, mailbox.getACL(), mailbox.getUser(), new GroupFolderResolver(session).isGroupFolder(mailbox));
        } else {
            return MailboxACL.NO_RIGHTS;
        }
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
        return isReadWrite(myRights(mailbox, session), sharedPermanentFlags);
    }

    private boolean isReadWrite(Rfc4314Rights rights, Flags sharedPermanentFlags) {
        if (rights.contains(Right.Insert) || rights.contains(Right.PerformExpunge)) {
            return true;
        }

        /*
         * then go through shared flags. RFC 4314 section 4:
         * Changing flags: STORE
         * - the server MUST check if the user has "t" right
         * - when the user modifies \Deleted flag "s" right
         * - when the user modifies \Seen flag "w" right - for all other message flags.
         */
       if (sharedPermanentFlags != null) {
            if (sharedPermanentFlags.contains(Flags.Flag.DELETED) && rights.contains(Right.DeleteMessages)) {
                return true;
            } else if (sharedPermanentFlags.contains(Flags.Flag.SEEN) && rights.contains(Right.WriteSeenFlag)) {
                return true;
            } else {
                boolean hasWriteRight = rights.contains(Right.Write);
                return hasWriteRight &&
                    (sharedPermanentFlags.contains(Flags.Flag.ANSWERED) ||
                    sharedPermanentFlags.contains(Flags.Flag.DRAFT) ||
                    sharedPermanentFlags.contains(Flags.Flag.FLAGGED) ||
                    sharedPermanentFlags.contains(Flags.Flag.RECENT) ||
                    sharedPermanentFlags.contains(Flags.Flag.USER));
            }
        }

        return false;
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
}
