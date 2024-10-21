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
package org.apache.james.droplists.api;

import java.util.List;

import org.apache.james.core.MailAddress;

/**
 * JMX MBean for Droplist.
 */
public interface DropListManagementMBean {

    /**
     * Add an entry to the droplist.
     *
     * @param entry The entry to add.
     */
    void add(DropListEntry entry);

    /**
     * Remove an entry from the droplist.
     *
     * @param entry The entry to remove.
     */
    void remove(DropListEntry entry);

    /**
     * List all entries in the droplist for a specific owner.
     *
     * @param ownerScope The scope of the owner.
     * @param owner      The owner for which to list the entries.
     * @return The list of entries in the droplist.
     */
    List<String> list(OwnerScope ownerScope, String owner);

    /**
     * Query the status of a sender's email address in the droplist.
     *
     * @param ownerScope The scope of the owner.
     * @param owner      The owner for which to query the status.
     * @param deniedEntity     The email address of the sender.
     * @return The status of the sender's email address (ALLOWED or BLOCKED).
     */
    String query(OwnerScope ownerScope, String owner, MailAddress deniedEntity);
}
