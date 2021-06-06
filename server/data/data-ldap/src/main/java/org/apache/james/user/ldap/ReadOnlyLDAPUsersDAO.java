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

package org.apache.james.user.ldap;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.net.SocketFactory;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.directory.api.ldap.model.filter.FilterEncoder;
import org.apache.james.core.Username;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.UsersDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

public class ReadOnlyLDAPUsersDAO implements UsersDAO, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReadOnlyLDAPUsersDAO.class);

    private LdapRepositoryConfiguration ldapConfiguration;
    private String filterTemplate;
    private LDAPConnectionPool ldapConnectionPool;

    @Inject
    public ReadOnlyLDAPUsersDAO() {

    }

    /**
     * Extracts the parameters required by the repository instance from the
     * James server configuration data. The fields extracted include
     *
     * @param configuration
     *            An encapsulation of the James server configuration data.
     */
    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        configure(LdapRepositoryConfiguration.from(configuration));
    }

    public void configure(LdapRepositoryConfiguration configuration) {
        ldapConfiguration = configuration;
    }

    /**
     * Initialises the user-repository instance. It will create a connection to
     * the LDAP host using the supplied configuration.
     *
     * @throws Exception
     *             If an error occurs authenticating or connecting to the
     *             specified LDAP host.
     */
    public void init() throws Exception {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(this.getClass().getName() + ".init()" + '\n' + "LDAP host: " + ldapConfiguration.getLdapHost()
                + '\n' + "User baseDN: " + ldapConfiguration.getUserBase() + '\n' + "userIdAttribute: "
                + ldapConfiguration.getUserIdAttribute() + '\n' + "Group restriction: " + ldapConfiguration.getRestriction()
                + '\n' + "connectionTimeout: "
                + ldapConfiguration.getConnectionTimeout() + '\n' + "readTimeout: " + ldapConfiguration.getReadTimeout()
                + '\n' + "maxRetries: " + ldapConfiguration.getMaxRetries() + '\n');
        }
        filterTemplate = "(&({0}={1})(objectClass={2})" + StringUtils.defaultString(ldapConfiguration.getFilter(), "") + ")";

        LDAPConnectionOptions connectionOptions = new LDAPConnectionOptions();
        connectionOptions.setConnectTimeoutMillis(ldapConfiguration.getConnectionTimeout());
        connectionOptions.setResponseTimeoutMillis(ldapConfiguration.getReadTimeout());

        URI uri = new URI(ldapConfiguration.getLdapHost());
        SocketFactory socketFactory = null;
        LDAPConnection ldapConnection = new LDAPConnection(socketFactory, connectionOptions, uri.getHost(), uri.getPort(), ldapConfiguration.getPrincipal(), ldapConfiguration.getCredentials());
        ldapConnectionPool = new LDAPConnectionPool(ldapConnection, 4);
        // TODO implement retries
    }

    @PreDestroy
    void dispose() {
        ldapConnectionPool.close();
    }


    /**
     * Indicates if the user with the specified DN can be found in the group
     * membership map&#45;as encapsulated by the specified parameter map.
     *
     * @param userDN
     *            The DN of the user to search for.
     * @param groupMembershipList
     *            A map containing the entire group membership lists for the
     *            configured groups. This is organised as a map of
     *
     *            <code>&quot;&lt;groupDN&gt;=&lt;[userDN1,userDN2,...,userDNn]&gt;&quot;</code>
     *            pairs. In essence, each <code>groupDN</code> string is
     *            associated to a list of <code>userDNs</code>.
     * @return <code>True</code> if the specified userDN is associated with at
     *         least one group in the parameter map, and <code>False</code>
     *         otherwise.
     */
    private boolean userInGroupsMembershipList(String userDN,
            Map<String, Collection<String>> groupMembershipList) {
        boolean result = false;

        Collection<Collection<String>> memberLists = groupMembershipList.values();
        Iterator<Collection<String>> memberListsIterator = memberLists.iterator();

        while (memberListsIterator.hasNext() && !result) {
            Collection<String> groupMembers = memberListsIterator.next();
            result = groupMembers.contains(userDN);
        }

        return result;
    }

    private Set<String> getAllUsersFromLDAP() throws LDAPException {
        LDAPConnection connection = ldapConnectionPool.getConnection();
        try {
            SearchResult searchResult = connection.search(ldapConfiguration.getUserBase(),
                SearchScope.SUB,
                filterTemplate);

            return searchResult.getSearchEntries()
                .stream()
                .map(entry -> entry.getObjectClassAttribute().getName())
                .collect(Guavate.toImmutableSet());
        } finally {
            ldapConnectionPool.releaseConnection(connection);
        }
    }

    /**
     * Extract the user attributes for the given collection of userDNs, and
     * encapsulates the user list as a collection of {@link ReadOnlyLDAPUser}s.
     * This method delegates the extraction of a single user's details to the
     * method {@link #buildUser(String)}.
     *
     * @param userDNs
     *            The distinguished-names (DNs) of the users whose information
     *            is to be extracted from the LDAP repository.
     * @return A collection of {@link ReadOnlyLDAPUser}s as taken from the LDAP
     *         server.
     * @throws LDAPException
     *             Propagated from the underlying LDAP communication layer.
     */
    private Collection<ReadOnlyLDAPUser> buildUserCollection(Collection<String> userDNs) throws LDAPException {
        List<ReadOnlyLDAPUser> results = new ArrayList<>();

        for (String userDN : userDNs) {
            Optional<ReadOnlyLDAPUser> user = buildUser(userDN);
            user.ifPresent(results::add);
        }

        return results;
    }

    private ReadOnlyLDAPUser searchAndBuildUser(Username name) throws LDAPException {
        LDAPConnection connection = ldapConnectionPool.getConnection();
        try {
            String sanitizedFilter = FilterEncoder.format(
                filterTemplate,
                ldapConfiguration.getUserIdAttribute(),
                name.asString(),
                ldapConfiguration.getUserObjectClass());

            SearchResult searchResult = connection.search(ldapConfiguration.getUserBase(),
                SearchScope.SUB,
                sanitizedFilter,
                ldapConfiguration.getUserIdAttribute());

            return searchResult.getSearchEntries()
                .stream()
                .map(entry -> new ReadOnlyLDAPUser(
                    Username.of(entry.getAttribute(ldapConfiguration.getUserIdAttribute()).getName()),
                    entry.getDN(),
                    ldapConnectionPool))
                .findFirst()
                .orElse(null);
        } finally {
            ldapConnectionPool.releaseConnection(connection);
        }

        /*
        TODO implement restrictions

        if (!ldapConfiguration.getRestriction().isActivated()
            || userInGroupsMembershipList(r.getNameInNamespace(), ldapConfiguration.getRestriction().getGroupMembershipLists(ldapContext))) {
            return new ReadOnlyLDAPUser(Username.of(userName.get().toString()), r.getNameInNamespace(), ldapContext);
        }

        return null;
        */
    }

    private Optional<ReadOnlyLDAPUser> buildUser(String userDN) throws LDAPException {
        LDAPConnection connection = ldapConnectionPool.getConnection();
        try {
            SearchResultEntry userAttributes = connection.getEntry(userDN);
            Optional<String> userName = Optional.ofNullable(userAttributes.getAttributeValue(ldapConfiguration.getUserIdAttribute()));
            return userName
                .map(Username::of)
                .map(username -> new ReadOnlyLDAPUser(username, userDN, ldapConnectionPool));
        } finally {
            ldapConnectionPool.releaseConnection(connection);
        }
    }

    @Override
    public boolean contains(Username name) throws UsersRepositoryException {
        return getUserByName(name).isPresent();
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        try {
            return Math.toIntExact(getValidUsers().stream()
                .map(Throwing.function(this::buildUser).sneakyThrow())
                .flatMap(Optional::stream)
                .count());
        } catch (LDAPException e) {
            throw new UsersRepositoryException("Unable to retrieve user count from ldap", e);
        }
    }

    @Override
    public Optional<User> getUserByName(Username name) throws UsersRepositoryException {
        try {
          return Optional.ofNullable(searchAndBuildUser(name));
        } catch (LDAPException e) {
            throw new UsersRepositoryException("Unable to retrieve user from ldap", e);
        }
    }

    @Override
    public Iterator<Username> list() throws UsersRepositoryException {
        try {
            return buildUserCollection(getValidUsers())
                .stream()
                .map(ReadOnlyLDAPUser::getUserName)
                .iterator();
        } catch (LDAPException namingException) {
            throw new UsersRepositoryException(
                    "Unable to retrieve users list from LDAP due to unknown naming error.",
                    namingException);
        }
    }

    private Collection<String> getValidUsers() throws LDAPException {
        return getAllUsersFromLDAP();

        /*
        TODO Implement restrictions
         */
        /*
        Collection<String> validUserDNs;
        if (ldapConfiguration.getRestriction().isActivated()) {
            Map<String, Collection<String>> groupMembershipList = ldapConfiguration.getRestriction()
                    .getGroupMembershipLists(ldapContext);
            validUserDNs = new ArrayList<>();

            Iterator<String> userDNIterator = userDNs.iterator();
            String userDN;
            while (userDNIterator.hasNext()) {
                userDN = userDNIterator.next();
                if (userInGroupsMembershipList(userDN, groupMembershipList)) {
                    validUserDNs.add(userDN);
                }
            }
        } else {
            validUserDNs = userDNs;
        }
        return validUserDNs;
         */
    }

    @Override
    public void removeUser(Username name) throws UsersRepositoryException {
        throw new UsersRepositoryException("This user-repository is read-only. Modifications are not permitted.");

    }

    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        throw new UsersRepositoryException("This user-repository is read-only. Modifications are not permitted.");
    }

    @Override
    public void addUser(Username username, String password) throws UsersRepositoryException {
        throw new UsersRepositoryException("This user-repository is read-only. Modifications are not permitted.");
    }
}
