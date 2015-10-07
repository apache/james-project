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
package org.apache.james.mailbox.store.mail.model;

import org.apache.james.mailbox.model.MailboxACL;

/**
 * Models long term mailbox data.
 */
public interface Mailbox<Id extends MailboxId> {

    /**
     * Gets the unique mailbox ID.
     * @return mailbox id
     */
    Id getMailboxId();

    /**
     * Gets the current namespace for this mailbox.
     * @return not null
     */
    String getNamespace();
    
    /**
     * Sets the current namespace for this mailbox.
     * @param namespace not null
     */
    void setNamespace(String namespace);

    /**
     * Gets the current user for this mailbox.
     * @return not null
     */
    String getUser();
    
    /**
     * Sets the current user for this mailbox.
     * @param user not null
     */
    void setUser(String user);

    /**
     * Gets the current name for this mailbox.
     * @return not null
     */
    String getName();
    
    /**
     * Sets the current name for this mailbox.
     * @param name not null
     */
    void setName(String name);

    /**
     * Gets the current UID VALIDITY for this mailbox.
     * @return uid validity
     */
    long getUidValidity();
    
    
    /**
     * Gets the current ACL for this mailbox.
     *
     * @return ACL
     */
    MailboxACL getACL();
    
    /**
     * Sets the current ACL for this mailbox.
     *
     * @param acl
     */
    void setACL(MailboxACL acl);
    
}