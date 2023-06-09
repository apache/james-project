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

package org.apache.james.mailbox;

import java.util.List;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.reactivestreams.Publisher;

public interface RightManager {
    /**
     * Tells whether the given {@link MailboxSession}'s user has the given
     * {@link MailboxACL.Right} for this {@link MessageManager}'s mailbox.
     *
     * @param mailboxPath MailboxPath of the mailbox we want to check
     * @param right Right we want to check.
     * @param session Session of the user we want to check this right against.
     * @return true if the given {@link MailboxSession}'s user has the given
     *         {@link MailboxACL.Right} for this {@link MessageManager}'s
     *         mailbox; false otherwise.
     * @throws MailboxException in case of unknown mailbox or unsupported right
     */
    boolean hasRight(MailboxPath mailboxPath, Right right, MailboxSession session) throws MailboxException;

    Publisher<Boolean> hasRightReactive(MailboxPath mailboxPath, Right right, MailboxSession session);

    boolean hasRight(Mailbox mailbox, Right right, MailboxSession session) throws MailboxException;

    /**
     * Tells whether the given {@link MailboxSession}'s user has the given
     * {@link MailboxACL.Right} for this {@link MessageManager}'s mailbox.
     *
     * @param mailboxId MailboxId of the mailbox we want to check
     * @param right Right we want to check.
     * @param session Session of the user we want to check this right against.
     * @return true if the given {@link MailboxSession}'s user has the given
     *         {@link MailboxACL.Right} for this {@link MessageManager}'s
     *         mailbox; false otherwise.
     * @throws MailboxException in case of unknown mailbox or unsupported right
     */
    boolean hasRight(MailboxId mailboxId, Right right, MailboxSession session) throws MailboxException;

    /**
     * Computes a result suitable for the LISTRIGHTS IMAP command. The result is
     * computed for this mailbox and the given {@code identifier}.
     *
     * From RFC 4314 section 3.7:
     * The first element of the resulting array contains the (possibly empty)
     * set of rights the identifier will always be granted in the mailbox.
     * Following this are zero or more right sets the identifier can be granted
     * in the mailbox. Rights mentioned in the same set are tied together. The
     * server MUST either grant all tied rights to the identifier in the mailbox
     * or grant none.
     *
     * The same right MUST NOT be listed more than once in the LISTRIGHTS
     * command.
     *
     * @param mailboxPath Path of the mailbox you want to get the rights list.
     * @param identifier
     *            the identifier from the LISTRIGHTS command.
     * @param session Right of the user performing the request.
     * @return result suitable for the LISTRIGHTS IMAP command
     * @throws MailboxException in case of unknown mailbox or unsupported right
     */
    List<MailboxACL.Rfc4314Rights> listRights(MailboxPath mailboxPath, MailboxACL.EntryKey identifier, MailboxSession session) throws MailboxException;

    List<MailboxACL.Rfc4314Rights> listRights(Mailbox mailbox, MailboxACL.EntryKey identifier, MailboxSession session) throws MailboxException;

    MailboxACL listRights(MailboxPath mailboxPath, MailboxSession session) throws MailboxException;

    Publisher<MailboxACL> listRightsReactive(MailboxPath mailboxPath, MailboxSession session);

    MailboxACL listRights(MailboxId mailboxId, MailboxSession session) throws MailboxException;

    Publisher<MailboxACL> listRightsReactive(MailboxId mailboxId, MailboxSession session);

    /**
     * Returns the rights applicable to the user who has sent the current
     * request on the mailbox designated by this mailboxPath.
     *
     * @param mailboxPath Path of the mailbox you want to get your rights on.
     * @param session The session used to determine the user we should retrieve the rights of.
     * @return the rights applicable to the user who has sent the request,
     *         returns {@link MailboxACL#NO_RIGHTS} if
     *         {@code session.getUser()} is null.
     * @throws MailboxException in case of unknown mailbox or unsupported right
     */
    Rfc4314Rights myRights(MailboxPath mailboxPath, MailboxSession session) throws MailboxException;

    Publisher<Rfc4314Rights> myRightsReactive(MailboxPath mailboxPath, MailboxSession session);

    /**
     * Returns the rights applicable to the user who has sent the current
     * request on the mailbox designated by this mailboxPath.
     *
     * @param mailboxId Id of the mailbox you want to get your rights on.
     * @param session The session used to determine the user we should retrieve the rights of.
     * @return the rights applicable to the user who has sent the request,
     *         returns {@link MailboxACL#NO_RIGHTS} if
     *         {@code session.getUser()} is null.
     * @throws MailboxException in case of unknown mailbox or unsupported right
     */
    Publisher<Rfc4314Rights> myRights(MailboxId mailboxId, MailboxSession session);

    /**
     * Returns the rights applicable to the user who has sent the current
     * request on the mailbox designated by this mailboxPath.
     *
     * @param mailbox The mailbox you want to get your rights on.
     * @param session The session used to determine the user we should retrieve the rights of.
     * @return the rights applicable to the user who has sent the request,
     *         returns {@link MailboxACL#NO_RIGHTS} if
     *         {@code session.getUser()} is null.
     * @throws MailboxException in case of unknown mailbox or unsupported right
     */
    Rfc4314Rights myRights(Mailbox mailbox, MailboxSession session);

    /**
     * Update the Mailbox ACL of the designated mailbox. We can either ADD REPLACE or REMOVE entries.
     *
     * @param mailboxPath Path of the mailbox you want to apply rights on.
     * @param mailboxACLCommand Update to perform.
     * @param session The session used to determine the user used to apply rights.
     * @throws MailboxException in case of unknown mailbox or unsupported right
     */
    void applyRightsCommand(MailboxPath mailboxPath, MailboxACL.ACLCommand mailboxACLCommand, MailboxSession session) throws MailboxException;

    Publisher<Void> applyRightsCommandReactive(MailboxPath mailboxPath, MailboxACL.ACLCommand mailboxACLCommand, MailboxSession session);

    /**
     * Update the Mailbox ACL of the designated mailbox. We can either ADD REPLACE or REMOVE entries.
     *
     * @param mailboxId Id of the mailbox you want to apply rights on.
     * @param mailboxACLCommand Update to perform.
     * @param session The session used to determine the user used to apply rights.
     * @throws MailboxException in case of unknown mailbox or unsupported right
     */
    void applyRightsCommand(MailboxId mailboxId, MailboxACL.ACLCommand mailboxACLCommand, MailboxSession session) throws MailboxException;

    /**
     * Reset the Mailbox ACL of the designated mailbox.
     *
     * @param mailboxPath Path of the mailbox you want to set the rights.
     * @param mailboxACL New ACL value
     * @param session The session used to determine the user used to set rights.
     * @throws MailboxException in case of unknown mailbox or unsupported right
     */
    void setRights(MailboxPath mailboxPath, MailboxACL mailboxACL, MailboxSession session) throws MailboxException;


    /**
     * Reset the Mailbox ACL of the designated mailbox.
     *
     * @param mailboxId Id of the mailbox you want to set the rights.
     * @param mailboxACL New ACL value
     * @param session The session used to determine the user used to set rights.
     * @throws MailboxException in case of unknown mailbox or unsupported right
     */
    void setRights(MailboxId mailboxId, MailboxACL mailboxACL, MailboxSession session) throws MailboxException;
}
