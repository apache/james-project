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
package org.apache.james.cli.probe;

import org.apache.james.adapter.mailbox.SerializableQuota;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;

import java.io.Closeable;
import java.util.Collection;
import java.util.Map;

public interface ServerProbe extends Closeable {
    /**
     * Add a user to this mail server.
     *
     * @param userName
     *            The name of the user being added.
     * @param password
     *            The password of the user being added.
     * @throws Exception
     */
    public void addUser(String userName, String password) throws Exception;

    /**
     * Delete a user from this mail server.
     *
     * @param username
     *            The name of the user being deleted.
     * @throws Exception
     */
    public void removeUser(String username) throws Exception;

    /**
     * Get a List the names of all users.
     *
     * @return a List of all user names.
     * @throws Exception
     */
    public String[] listUsers() throws Exception;

    /**
     * Set a user's password.
     *
     * @param userName
     *            The name of the user whose password will be changed.
     * @param password
     *            The new password.
     * @throws Exception
     */
    public void setPassword(String userName, String password) throws Exception;

    /**
     * Add domain to the service.
     *
     * @param domain
     *            The domain to add.
     * @throws Exception
     */
    public void addDomain(String domain) throws Exception;

    /**
     * Return true if the domain exists in the service
     *
     * @param domain
     *            The domain to remove.
     * @throws Exception
     */
    public boolean containsDomain(String domain) throws Exception;

    /**
     * Remove domain from the service
     *
     * @param domain
     *            The domain to remove.
     * @throws Exception
     */
    public void removeDomain(String domain) throws Exception;

    /**
     * Get a list of domains for the service.
     *
     * @return domains an array of domains, or null if no domains exist.
     * @throws Exception
     */
    public String[] listDomains() throws Exception;

    /**
     * Get a Map which holds all mappings. The key is the user@domain and the
     * value is a Collection which holds all mappings.
     *
     * @return a Map which holds all mappings.
     * @throws Exception
     */
    public Map<String, Collection<String>> listMappings() throws Exception;

    /**
     * Add address mapping.
     *
     * @param user
     *            The username, or null if no username should be used.
     * @param domain
     *            The domain, or null if no domain should be used.
     * @param toAddress
     *            The address.
     * @throws Exception
     */
    public void addAddressMapping(String user, String domain, String toAddress) throws Exception;

    /**
     * Remove address mapping.
     *
     * @param user
     *            The username, or null if no username should be used.
     * @param domain
     *            The domain, or null if no domain should be used
     * @param fromAddress
     *            The address.
     * @throws Exception
     */
    public void removeAddressMapping(String user, String domain, String fromAddress) throws Exception;

    /**
     * Return the explicit mapping stored for the given user and domain. Return
     * null if no mapping was found
     *
     * @param user
     *            The username.
     * @param domain
     *            The domain.
     * @return the collection which holds the mappings, or null if no mapping is
     *         found.
     * @throws Exception
     */
    public Collection<String> listUserDomainMappings(String user, String domain) throws Exception;

    /**
     * Remove regex mapping.
     *
     * @param user
     *            The username, or null if no username should be used.
     * @param domain
     *            The domain, or null if no domain should be used.
     * @param regex
     *            The regex.
     * @throws Exception
     */
    public void addRegexMapping(String user, String domain, String regex) throws Exception;

    /**
     * Remove regex mapping.
     *
     * @param user
     *            The username, or null if no username should be used.
     * @param domain
     *            The domain, or null if no domain should be used.
     * @param regex
     *            The regex.
     * @throws Exception
     */
    public void removeRegexMapping(String user, String domain, String regex) throws Exception;

    /**
     * Copy Mailbox.
     *
     * @param srcBean
     *            The name of the bean that manages the source mailbox.
     * @param dstBean
     *            The name of the bean that manages the destination mailbox.
     * @throws Exception
     */
    void copyMailbox(String srcBean, String dstBean) throws Exception;

    /**
     * Delete mailboxes Belonging to #private:${user}
     *
     * @param user Username of the user we want to list mailboxes on
     * @return Collection of the mailboxes names
     * @throws Exception
     */
    void deleteUserMailboxesNames(String user) throws Exception;

    /**
     * Create a mailbox
     *
     * @param namespace Namespace of the created mailbox
     * @param user User of the created mailbox
     * @param name Name of the created mailbox
     */
    void createMailbox(String namespace, String user, String name);

    /**
     * List mailboxes belonging to the private namespace of a user
     *
     * @param user The given user
     * @return List of mailboxes belonging to the private namespace of a user
     */
    Collection<String> listUserMailboxes(String user);

    /**
     * Delete the given mailbox
     *
     * @param namespace Namespace of the mailbox to delete
     * @param user User the mailbox to delete belongs to
     * @param name Name of the mailbox to delete
     */
    void deleteMailbox(String namespace, String user, String name);

    String getQuotaRoot(String namespace, String user, String name) throws MailboxException;

    SerializableQuota getMessageCountQuota(String quotaRoot) throws MailboxException;

    SerializableQuota getStorageQuota(String quotaRoot) throws MailboxException;

    long getMaxMessageCount(String quotaRoot) throws MailboxException;

    long getMaxStorage(String quotaRoot) throws MailboxException;

    long getDefaultMaxMessageCount() throws MailboxException;

    long getDefaultMaxStorage() throws MailboxException;

    void setMaxMessageCount(String quotaRoot, long maxMessageCount) throws MailboxException;

    void setMaxStorage(String quotaRoot, long maxSize) throws MailboxException;

    void setDefaultMaxMessageCount(long maxDefaultMessageCount) throws MailboxException;

    void setDefaultMaxStorage(long maxDefaultSize) throws MailboxException;
}
