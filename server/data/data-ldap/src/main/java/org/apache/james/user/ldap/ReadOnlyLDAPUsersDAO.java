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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;


import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.UsersDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

public class ReadOnlyLDAPUsersDAO implements UsersDAO, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReadOnlyLDAPUsersDAO.class);

    private final GaugeRegistry gaugeRegistry;
    private final LdapRepositoryConfiguration ldapConfiguration;
    private LDAPConnectionPool ldapConnectionPool;
    private Optional<Filter> userExtraFilter;
    private Filter objectClassFilter;
    private Filter listingFilter;

    @Inject
    public ReadOnlyLDAPUsersDAO(GaugeRegistry gaugeRegistry,
                                LDAPConnectionPool ldapConnectionPool,
                                LdapRepositoryConfiguration configuration) {
        this.gaugeRegistry = gaugeRegistry;
        this.ldapConnectionPool = ldapConnectionPool;
        this.ldapConfiguration = configuration;
    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {

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
            LOGGER.debug(this.getClass().getName() + ".init()" + '\n' + "LDAP hosts: " + ldapConfiguration.getLdapHosts()
                + '\n' + "User baseDN: " + ldapConfiguration.getUserBase() + '\n' + "userIdAttribute: "
                + ldapConfiguration.getUserIdAttribute() + '\n' + "Group restriction: " + ldapConfiguration.getRestriction()
                + '\n' + "connectionTimeout: "
                + ldapConfiguration.getConnectionTimeout() + '\n' + "readTimeout: " + ldapConfiguration.getReadTimeout());
        }

        userExtraFilter = Optional.ofNullable(ldapConfiguration.getFilter())
            .map(Throwing.function(Filter::create).sneakyThrow());
        objectClassFilter = Filter.createEqualityFilter("objectClass", ldapConfiguration.getUserObjectClass());
        listingFilter = userExtraFilter.map(extraFilter -> Filter.createANDFilter(objectClassFilter, extraFilter))
            .orElse(objectClassFilter);

        if (!ldapConfiguration.getPerDomainBaseDN().isEmpty()) {
            Preconditions.checkState(ldapConfiguration.supportsVirtualHosting(), "'virtualHosting' is needed for per domain DNs");
        }

        gaugeRegistry.register("ldap-connection-available-count", () -> ldapConnectionPool.getConnectionPoolStatistics().getNumAvailableConnections());
        gaugeRegistry.register("ldap-created-connection-count", () -> ldapConnectionPool.getConnectionPoolStatistics().getNumSuccessfulConnectionAttempts());
    }

    @PreDestroy
    void dispose() {
        ldapConnectionPool.close();
    }

    private Filter createFilter(String retrievalName, String ldapUserRetrievalAttribute) {
        Filter specificUserFilter = Filter.createEqualityFilter(ldapUserRetrievalAttribute, retrievalName);
        return userExtraFilter
            .map(extraFilter -> Filter.createANDFilter(objectClassFilter, specificUserFilter, extraFilter))
            .orElseGet(() -> Filter.createANDFilter(objectClassFilter, specificUserFilter));
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
    private boolean userInGroupsMembershipList(DN userDN,
            Map<String, Collection<DN>> groupMembershipList) {
        boolean result = false;

        Collection<Collection<DN>> memberLists = groupMembershipList.values();
        Iterator<Collection<DN>> memberListsIterator = memberLists.iterator();

        while (memberListsIterator.hasNext() && !result) {
            Collection<DN> groupMembers = memberListsIterator.next();
            result = groupMembers.contains(userDN);
        }

        return result;
    }

    private String userBase(Domain domain) {
        return ldapConfiguration.getPerDomainBaseDN()
            .getOrDefault(domain, ldapConfiguration.getUserBase());
    }

    private String userBase(Username username) {
        return username.getDomainPart().map(this::userBase).orElse(ldapConfiguration.getUserBase());
    }

    private Set<DN> getAllUsersDNFromLDAP() {
        return allDNs()
            .flatMap(Throwing.<String, Stream<SearchResultEntry>>function(this::entriesFromDN).sneakyThrow())
            .map(Throwing.function(Entry::getParsedDN))
            .collect(ImmutableSet.toImmutableSet());
    }

    private Stream<String> allDNs() {
        return Stream.concat(
            Stream.of(ldapConfiguration.getUserListBase()),
            ldapConfiguration.getPerDomainBaseDN().values().stream());
    }

    private Stream<SearchResultEntry> entriesFromDN(String dn) throws LDAPSearchException {
        return entriesFromDN(dn, SearchRequest.NO_ATTRIBUTES);
    }

    private Stream<SearchResultEntry> entriesFromDN(String dn, String attributes) throws LDAPSearchException {
        SearchRequest searchRequest = new SearchRequest(dn,
            SearchScope.SUB,
            listingFilter,
            attributes);

        return ldapConnectionPool.search(searchRequest)
            .getSearchEntries()
            .stream();
    }

    private Stream<Username> getAllUsernamesFromLDAP() throws LDAPException {
        String usernameAttribute = ldapConfiguration.getUsernameAttribute().orElse(ldapConfiguration.getUserIdAttribute());
        return allDNs()
            .flatMap(Throwing.<String, Stream<SearchResultEntry>>function(s -> entriesFromDN(s, usernameAttribute)).sneakyThrow())
            .flatMap(entry -> Optional.ofNullable(entry.getAttribute(usernameAttribute)).stream())
            .map(Attribute::getValue)
            .map(Username::of)
            .distinct();
    }

    /**
     * Extract the user attributes for the given collection of userDNs, and
     * encapsulates the user list as a collection of {@link ReadOnlyLDAPUser}s.
     * This method delegates the extraction of a single user's details to the
     * method {@link #buildUser(DN)}.
     *
     * @param userDNs
     *            The distinguished-names (DNs) of the users whose information
     *            is to be extracted from the LDAP repository.
     * @return A collection of {@link ReadOnlyLDAPUser}s as taken from the LDAP
     *         server.
     * @throws LDAPException
     *             Propagated from the underlying LDAP communication layer.
     */
    private Collection<ReadOnlyLDAPUser> buildUserCollection(Collection<DN> userDNs) throws LDAPException {
        List<ReadOnlyLDAPUser> results = new ArrayList<>();

        for (DN userDN : userDNs) {
            Optional<ReadOnlyLDAPUser> user = buildUser(userDN);
            user.ifPresent(results::add);
        }

        return results;
    }

    private Optional<ReadOnlyLDAPUser> searchAndBuildUser(Username retrievalName) throws LDAPException {
        Optional<String> resolveLocalPartAttribute = ldapConfiguration.getResolveLocalPartAttribute();

        SearchResult searchResult = ldapConnectionPool.search(userBase(retrievalName),
            SearchScope.SUB,
            createFilter(retrievalName.asString(), evaluateLdapUserRetrievalAttribute(retrievalName, resolveLocalPartAttribute)),
            ldapConfiguration.getReturnedAttributes());

        SearchResultEntry result = searchResult.getSearchEntries()
            .stream()
            .findFirst()
            .orElse(null);

        if (result == null) {
            return Optional.empty();
        }

        if (!ldapConfiguration.getRestriction().isActivated()
            || userInGroupsMembershipList(result.getParsedDN(), ldapConfiguration.getRestriction().getGroupMembershipLists(ldapConnectionPool))) {

            String usernameAttribute = ldapConfiguration.getUsernameAttribute().orElse(ldapConfiguration.getUserIdAttribute());
            Username translatedUsername = Username.of(result.getAttributeValue(usernameAttribute));
            return Optional.of(new ReadOnlyLDAPUser(translatedUsername, result.getParsedDN(), ldapConnectionPool));
        }
        return Optional.empty();
    }

    private String evaluateLdapUserRetrievalAttribute(Username retrievalName, Optional<String> resolveLocalPartAttribute) {
        if (retrievalName.asString().contains("@")) {
            return ldapConfiguration.getUserIdAttribute();
        } else {
            return resolveLocalPartAttribute.orElse(ldapConfiguration.getUserIdAttribute());
        }
    }

    private Optional<ReadOnlyLDAPUser> buildUser(DN userDN) throws LDAPException {
        SearchResultEntry userAttributes = ldapConnectionPool.getEntry(userDN.toString());
        String usernameAttribute = ldapConfiguration.getUsernameAttribute().orElse(ldapConfiguration.getUserIdAttribute());
        Optional<String> userName = Optional.ofNullable(userAttributes.getAttributeValue(usernameAttribute));
        return userName
            .flatMap(name -> {
                try {
                    return Optional.of(Username.of(name));
                } catch (Exception e) {
                    LOGGER.warn("Invalid username in the LDAP: {}", name, e);
                    return Optional.empty();
                }
            })
            .map(username -> new ReadOnlyLDAPUser(username, userDN, ldapConnectionPool));
    }

    @Override
    public boolean contains(Username name) throws UsersRepositoryException {
        return getUserByName(name)
            .filter(readOnlyLDAPUser -> readOnlyLDAPUser.getUserName().equals(name))
            .isPresent();
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        try {
            return Math.toIntExact(doCountUsers());
        } catch (LDAPException e) {
            throw new UsersRepositoryException("Unable to retrieve user count from ldap", e);
        }
    }

    private long doCountUsers() throws LDAPException {
        if (!ldapConfiguration.getRestriction().isActivated()) {
            return getAllUsernamesFromLDAP().count();
        }

        return getValidUserDNs().stream()
            .map(Throwing.function(this::buildUser).sneakyThrow())
            .flatMap(Optional::stream)
            .map(ReadOnlyLDAPUser::getUserName)
            .distinct()
            .count();
    }

    @Override
    public Optional<ReadOnlyLDAPUser> getUserByName(Username name) throws UsersRepositoryException {
        try {
            return searchAndBuildUser(name);
        } catch (Exception e) {
            throw new UsersRepositoryException("Unable check user existence from ldap", e);
        }
    }

    @Override
    public Iterator<Username> list() throws UsersRepositoryException {
        try {
            if (!ldapConfiguration.getRestriction().isActivated()) {
                return getAllUsernamesFromLDAP().iterator();
            }

            return buildUserCollection(getValidUserDNs())
                .stream()
                .map(ReadOnlyLDAPUser::getUserName)
                .distinct()
                .iterator();
        } catch (LDAPException e) {
            throw new UsersRepositoryException("Unable to list users from ldap", e);
        }
    }


    private Collection<DN> getValidUserDNs() throws LDAPException {
        Set<DN> userDNs = getAllUsersDNFromLDAP();
        Collection<DN> validUserDNs;
        if (ldapConfiguration.getRestriction().isActivated()) {
            Map<String, Collection<DN>> groupMembershipList = ldapConfiguration.getRestriction()
                .getGroupMembershipLists(ldapConnectionPool);
            validUserDNs = new ArrayList<>();

            Iterator<DN> userDNIterator = userDNs.iterator();
            DN userDN;
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
