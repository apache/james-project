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
package org.apache.james.mailbox.store.mail;

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.transaction.Mapper;

import com.google.common.base.Preconditions;

/**
 * Mapper for {@link Mailbox} actions. A {@link MailboxMapper} has a lifecycle from the start of a request 
 * to the end of the request.
 *
 */
public interface MailboxMapper extends Mapper {

    /**
     * Create the given {@link Mailbox} to the underlying storage
     */
    default MailboxId create(Mailbox mailbox) throws MailboxException {
        Preconditions.checkArgument(mailbox.getMailboxId() == null, "A mailbox we want to create should not have a mailboxId set already");

        return rename(mailbox);
    }

    /**
     * Rename the given {@link Mailbox} to the underlying storage
     */
    MailboxId rename(Mailbox mailbox) throws MailboxException;
    
    /**
     * Delete the given {@link Mailbox} from the underlying storage
     */
    void delete(Mailbox mailbox) throws MailboxException;

  
    /**
     * Return the {@link Mailbox} for the given name
     */
    Mailbox findMailboxByPath(MailboxPath mailboxName)
            throws MailboxException, MailboxNotFoundException;

    /**
     * Return the {@link Mailbox} for the given name
     */
    Mailbox findMailboxById(MailboxId mailboxId)
            throws MailboxException, MailboxNotFoundException;

    /**
     * Return a List of {@link Mailbox} for the given userName and matching the right
     */
    List<Mailbox> findNonPersonalMailboxes(Username userName, Right right) throws MailboxException;

    /**
     * Return a List of {@link Mailbox} which name is like the given name
     */
    List<Mailbox> findMailboxWithPathLike(MailboxQuery.UserBound query)
            throws MailboxException;

    /**
     * Return if the given {@link Mailbox} has children
     * 
     * @param mailbox not null
     * @param delimiter path delimiter
     * @return true when the mailbox has children, false otherwise
     */
    boolean hasChildren(Mailbox mailbox, char delimiter)
            throws MailboxException, MailboxNotFoundException;

    /**
     * Update the ACL of the stored mailbox.
     *
     * @param mailbox Mailbox for whom we want to update ACL
     * @param mailboxACLCommand Update to perform
     */
    ACLDiff updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand) throws MailboxException;

    /**
     * Reset the ACL of the stored mailbox.
     *
     * @param mailbox Mailbox for whom we want to update ACL
     * @param mailboxACL New value of the ACL for this mailbox
     */
    ACLDiff setACL(Mailbox mailbox, MailboxACL mailboxACL) throws MailboxException;

    /**
     * Return a unmodifable {@link List} of all {@link Mailbox}
     */
    List<Mailbox> list() throws MailboxException;
}