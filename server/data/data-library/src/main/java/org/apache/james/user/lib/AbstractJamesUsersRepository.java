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

package org.apache.james.user.lib;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.rrt.lib.MappingsImpl.Builder;
import org.apache.james.user.api.JamesUsersRepository;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.JamesUser;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.model.DefaultJamesUser;

/**
 * A partial implementation of a Repository to store users.
 * <p>
 * This implements common functionality found in different UsersRespository
 * implementations, and makes it easier to create new User repositories.
 * </p>
 * 
 * @deprecated Please implement {@link UsersRepository}
 */
@Deprecated
public abstract class AbstractJamesUsersRepository extends AbstractUsersRepository implements JamesUsersRepository, RecipientRewriteTable {

    /**
     * Ignore case in usernames
     */
    protected boolean ignoreCase;

    /**
     * Enable Aliases frmo JamesUser
     */
    protected boolean enableAliases;

    /**
     * Wether to enable forwarding for JamesUser or not
     */
    protected boolean enableForwarding;

    @Override
    public void configure(HierarchicalConfiguration configuration) throws ConfigurationException {
        setIgnoreCase(configuration.getBoolean("ignoreCase", false));
        setEnableAliases(configuration.getBoolean("enableAliases", false));
        setEnableForwarding(configuration.getBoolean("enableForwarding", false));
        super.configure(configuration);
    }

    /**
     * Adds a user to the underlying Repository. The user name must not clash
     * with an existing user.
     * 
     * @param user
     *            the user to add
     */
    protected abstract void doAddUser(User user) throws UsersRepositoryException;

    /**
     * Updates a user record to match the supplied User.
     * 
     * @param user
     *            the user to update
     */
    protected abstract void doUpdateUser(User user) throws UsersRepositoryException;

    /**
     * @see
     * org.apache.james.user.lib.AbstractUsersRepository#doAddUser(java.lang.String, java.lang.String)
     */
    protected void doAddUser(String username, String password) throws UsersRepositoryException {
        User newbie = new DefaultJamesUser(username, "SHA");
        newbie.setPassword(password);
        doAddUser(newbie);
    }

    /**
     * Update the repository with the specified user object. A user object with
     * this username must already exist.
     * 
     * @param user
     *            the user to be updated
     * @throws UsersRepositoryException
     */
    public void updateUser(User user) throws UsersRepositoryException {
        // Return false if it's not found.
        if (!contains(user.getUserName())) {
            throw new UsersRepositoryException("User " + user.getUserName() + " does not exist");
        } else {
            doUpdateUser(user);
        }
    }

    /**
     * @throws RecipientRewriteTableException
     * @see org.apache.james.rrt.api.RecipientRewriteTable#getMappings(java.lang.String,
     *      java.lang.String)
     */
    public Mappings getMappings(String username, String domain) throws ErrorMappingException, RecipientRewriteTableException {
        Builder mappingsBuilder = MappingsImpl.builder();
        try {
            User user = getUserByName(username);

            if (user instanceof JamesUser) {
                JamesUser jUser = (JamesUser) user;

                if (enableAliases && jUser.getAliasing()) {
                    String alias = jUser.getAlias();
                    if (alias != null) {
                        mappingsBuilder.add(alias + "@" + domain);
                    }
                }

                if (enableForwarding && jUser.getForwarding()) {
                    String forward;
                    if (jUser.getForwardingDestination() != null && ((forward = jUser.getForwardingDestination().toString()) != null)) {
                        mappingsBuilder.add(forward);
                    } else {
                        String errorBuffer = "Forwarding was enabled for " + username + " but no forwarding address was set for this account.";
                        getLogger().error(errorBuffer);
                    }
                }
            }
        } catch (UsersRepositoryException e) {
            throw new RecipientRewriteTableException("Unable to lookup forwards/aliases", e);
        }
        Mappings mappings = mappingsBuilder.build();
        if (mappings.size() == 0) {
            return null;
        } else {
            return mappings;
        }
    }

    /**
     * @see org.apache.james.user.api.JamesUsersRepository#setEnableAliases(boolean)
     */
    public void setEnableAliases(boolean enableAliases) {
        this.enableAliases = enableAliases;
    }

    /**
     * @see org.apache.james.user.api.JamesUsersRepository#setEnableForwarding(boolean)
     */
    public void setEnableForwarding(boolean enableForwarding) {
        this.enableForwarding = enableForwarding;
    }

    /**
     * @see org.apache.james.user.api.JamesUsersRepository#setIgnoreCase(boolean)
     */
    public void setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    /**
     * @see org.apache.james.rrt.api.RecipientRewriteTable#getAllMappings()
     */
    public Map<String, Mappings> getAllMappings() throws RecipientRewriteTableException {
        Map<String, Mappings> mappings = new HashMap<>();
        if (enableAliases || enableForwarding) {
            try {
                Iterator<String> users = list();
                while (users.hasNext()) {
                    String user = users.next();
                    int index = user.indexOf("@");
                    String username;
                    String domain;
                    if (index != -1) {
                        username = user.substring(0, index);
                        domain = user.substring(index + 1, user.length());
                    } else {
                        username = user;
                        domain = "localhost";
                    }
                    try {
                        mappings.put(user, getMappings(username, domain));
                    } catch (ErrorMappingException e) {
                        // shold never happen here
                    }
                }
            } catch (UsersRepositoryException e) {
                throw new RecipientRewriteTableException("Unable to access forwards/aliases", e);
            }
        }

        return mappings;
    }

    /**
     * @see
     * org.apache.james.rrt.api.RecipientRewriteTable#getUserDomainMappings(java.lang.String, java.lang.String)
     */
    public Mappings getUserDomainMappings(String user, String domain) throws RecipientRewriteTableException {
        return MappingsImpl.empty();
    }

    public void addRegexMapping(String user, String domain, String regex) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");
    }

    public void removeRegexMapping(String user, String domain, String regex) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    public void addAddressMapping(String user, String domain, String address) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    public void removeAddressMapping(String user, String domain, String address) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    public void addErrorMapping(String user, String domain, String error) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    public void removeErrorMapping(String user, String domain, String error) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    public void addMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    public void removeMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    public void addAliasDomainMapping(String aliasDomain, String realDomain) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

    public void removeAliasDomainMapping(String aliasDomain, String realDomain) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only RecipientRewriteTable");

    }

}
