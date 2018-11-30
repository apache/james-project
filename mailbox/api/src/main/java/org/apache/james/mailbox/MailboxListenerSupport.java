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

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;

/**
 * Implementations of this interface supports {@link MailboxListener}. Its needed that the events get handled
 * in the submitted order
 * 
 *
 */
public interface MailboxListenerSupport {

    /**
     * <p>
     * Implementations of Mailbox may interpret the fact that someone is
     * listening and do some caching and even postpone persistence until
     * everyone has removed itself.
     * </p>
     * 
     * @param mailboxId
     *            not null
     * @param listener
     *            not null
     * @param session
     *            not null
     * @throws MailboxException
     */
    void addListener(MailboxId mailboxId, MailboxListener listener, MailboxSession session) throws MailboxException;

    /**
     * Remove the {@link MailboxListener}
     * 
     * @param mailboxId
     * @param listner
     * @param session
     * @throws MailboxException
     */
    void removeListener(MailboxId mailboxId, MailboxListener listner, MailboxSession session) throws MailboxException;
    
    /**
     * Add a {@link MailboxListener} which get fired for ever
     * {@link MailboxPath}
     * 
     * @param listener
     * @param session
     * @throws MailboxException
     */
    void addGlobalListener(MailboxListener listener, MailboxSession session) throws MailboxException;
    
    
    /**
     * Remove the {@link MailboxListener}
     * 
     * @param listner
     * @param session
     * @throws MailboxException
     */
    void removeGlobalListener(MailboxListener listner, MailboxSession session) throws MailboxException;


}
