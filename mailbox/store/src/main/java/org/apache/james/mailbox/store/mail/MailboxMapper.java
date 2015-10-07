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

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.transaction.Mapper;

/**
 * Mapper for {@link Mailbox} actions. A {@link MailboxMapper} has a lifecycle from the start of a request 
 * to the end of the request.
 *
 */
public interface MailboxMapper<Id extends MailboxId> extends Mapper {
    
    /**
     * Save the give {@link Mailbox} to the underlying storage
     * 
     * @param mailbox
     * @throws MailboxException
     */
    void save(Mailbox<Id> mailbox) throws MailboxException;
    
    /**
     * Delete the given {@link Mailbox} from the underlying storage
     * 
     * @param mailbox
     * @throws MailboxException
     */
    void delete(Mailbox<Id> mailbox) throws MailboxException;

  
    /**
     * Return the {@link Mailbox} for the given name
     * 
     * @param mailboxName 
     * @return mailbox
     * @throws MailboxException
     * @throws MailboxNotFoundException
     */
    Mailbox<Id> findMailboxByPath(MailboxPath mailboxName)
            throws MailboxException, MailboxNotFoundException;

    /**
     * Return a List of {@link Mailbox} which name is like the given name
     * 
     * @param mailboxPath
     * @return mailboxList
     * @throws MailboxException
     */
    List<Mailbox<Id>> findMailboxWithPathLike(MailboxPath mailboxPath)
            throws MailboxException;

    /**
     * Return if the given {@link Mailbox} has children
     * 
     * @param mailbox not null
     * @param delimiter path delimiter
     * @return true when the mailbox has children, false otherwise
     * @throws MailboxException
     * @throws MailboxNotFoundException
     */
    boolean hasChildren(Mailbox<Id> mailbox, char delimiter)
            throws MailboxException, MailboxNotFoundException;

    /**
     * Update the ACL of the stored mailbox.
     *
     * @param mailbox Mailbox for whom we want to update ACL
     * @param mailboxACLCommand Update to perform
     */
    void updateACL(Mailbox<Id> mailbox, MailboxACL.MailboxACLCommand mailboxACLCommand) throws MailboxException;

    /**
     * Return a unmodifable {@link List} of all {@link Mailbox} 
     * 
     * @return mailboxList
     * @throws MailboxException 
     */
    List<Mailbox<Id>> list() throws MailboxException;
}