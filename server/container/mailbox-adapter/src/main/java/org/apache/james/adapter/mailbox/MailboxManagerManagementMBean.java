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
package org.apache.james.adapter.mailbox;

import java.util.List;

/**
 * JMX MBean for Mailbox management
 */
public interface MailboxManagerManagementMBean {

    /**
     * Delete all Mailboxes which belong to the user
     * 
     * @param username
     * @return successful
     */
    boolean deleteMailboxes(String username);

    /**
     * List all mailboxes for a user
     * 
     * @param username
     * @return mailboxes
     */
    List<String> listMailboxes(String username);

    /**
     * Create a mailbox
     *
     * @param namespace Namespace of the created mailbox
     * @param user User of the created mailbox
     * @param name Name of the created mailbox
     */
    void createMailbox(String namespace,String user, String name);

    /**
     * Delete the given mailbox
     *
     * @param namespace Namespace of the mailbox to delete
     * @param user User the mailbox to delete belongs to
     * @param name Name of the mailbox to delete
     */
    void deleteMailbox(String namespace, String user, String name);
}