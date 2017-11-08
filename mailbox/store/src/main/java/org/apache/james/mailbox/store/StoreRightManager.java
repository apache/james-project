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

import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.RightManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.exception.DifferentDomainException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.ACLCommand;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.transaction.Mapper;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

public class StoreRightManager implements RightManager {
    public static final boolean GROUP_FOLDER = true;

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

        return Optional.ofNullable(user)
            .map(Throwing.function(value ->
                aclResolver.resolveRights(
                    user.getUserName(),
                    groupMembershipResolver,
                    mailbox.getACL(),
                    mailbox.getUser(),
                    !GROUP_FOLDER))
                .sneakyThrow())
            .orElse(MailboxACL.NO_RIGHTS);
    }

    @Override
    public Rfc4314Rights[] listRigths(MailboxPath mailboxPath, EntryKey key, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);

        return aclResolver.listRights(key,
            groupMembershipResolver,
            mailbox.getUser(),
            !GROUP_FOLDER);
    }

    @Override
    public void applyRightsCommand(MailboxPath mailboxPath, ACLCommand mailboxACLCommand, MailboxSession session) throws MailboxException {
        assertSharesBelongsToUserDomain(mailboxPath.getUser(), mailboxACLCommand);
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);
        mapper.execute(Mapper.toTransaction(() -> mapper.updateACL(mailbox, mailboxACLCommand)));
    }

    private void assertSharesBelongsToUserDomain(String user, ACLCommand mailboxACLCommand) throws DifferentDomainException {
        assertSharesBelongsToUserDomain(user, ImmutableMap.of(mailboxACLCommand.getEntryKey(), mailboxACLCommand.getRights()));
    }

    public boolean isReadWrite(MailboxSession session, Mailbox mailbox, Flags sharedPermanentFlags) throws UnsupportedRightException {
        Rfc4314Rights rights = myRights(mailbox, session);

        /*
         * then go through shared flags. RFC 4314 section 4:
         * Changing flags: STORE
         * - the server MUST check if the user has "t" (expunge) right
         * - when the user modifies \Deleted flag "s" (seen) right
         * - when the user modifies \Seen flag "w" (write) - for all other message flags.
         */
        return rights.contains(Right.Insert) ||
            rights.contains(Right.PerformExpunge) ||
            checkDeleteFlag(rights, sharedPermanentFlags) ||
            checkSeenFlag(rights, sharedPermanentFlags) ||
            checkWriteFlag(rights, sharedPermanentFlags);
    }

    private boolean checkWriteFlag(Rfc4314Rights rights, Flags sharedPermanentFlags) {
        return rights.contains(Right.Write) &&
            (sharedPermanentFlags.contains(Flags.Flag.ANSWERED) ||
                sharedPermanentFlags.contains(Flags.Flag.DRAFT) ||
                sharedPermanentFlags.contains(Flags.Flag.FLAGGED) ||
                sharedPermanentFlags.contains(Flags.Flag.RECENT) ||
                sharedPermanentFlags.contains(Flags.Flag.USER));
    }

    private boolean checkSeenFlag(Rfc4314Rights rights, Flags sharedPermanentFlags) {
        return sharedPermanentFlags.contains(Flags.Flag.SEEN) && rights.contains(Right.WriteSeenFlag);
    }

    private boolean checkDeleteFlag(Rfc4314Rights rights, Flags sharedPermanentFlags) {
        return sharedPermanentFlags.contains(Flags.Flag.DELETED) && rights.contains(Right.DeleteMessages);
    }

    @Override
    public void setRights(MailboxId mailboxId, MailboxACL mailboxACL, MailboxSession session) throws MailboxException {
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxById(mailboxId);

        setRights(mailbox.generateAssociatedPath(), mailboxACL, session);
    }

    @Override
    public void setRights(MailboxPath mailboxPath, MailboxACL mailboxACL, MailboxSession session) throws MailboxException {
        assertSharesBelongsToUserDomain(mailboxPath.getUser(), mailboxACL.getEntries());
        MailboxMapper mapper = mailboxSessionMapperFactory.getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxByPath(mailboxPath);

        setRights(mailboxACL, mapper, mailbox);
    }

    @VisibleForTesting
    void assertSharesBelongsToUserDomain(String user, Map<EntryKey, Rfc4314Rights> entries) throws DifferentDomainException {
        if (entries.keySet().stream()
            .filter(entry -> !entry.getNameType().equals(NameType.special))
            .map(EntryKey::getName)
            .anyMatch(name -> areDomainsDifferent(name, user))) {
            throw new DifferentDomainException();
        }
    }

    @VisibleForTesting
    boolean areDomainsDifferent(String user, String otherUser) {
        Optional<String> domain = User.fromUsername(user).getDomainPart();
        Optional<String> otherDomain = User.fromUsername(otherUser).getDomainPart();
        return !domain.equals(otherDomain);
    }

    private void setRights(MailboxACL mailboxACL, MailboxMapper mapper, Mailbox mailbox) throws MailboxException {
        mapper.execute(Mapper.toTransaction(() -> mapper.setACL(mailbox, mailboxACL)));
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
            !GROUP_FOLDER);

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
