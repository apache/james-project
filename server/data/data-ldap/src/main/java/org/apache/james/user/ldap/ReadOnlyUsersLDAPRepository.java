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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.directory.api.ldap.model.filter.FilterEncoder;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.ldap.api.LdapConstants;
import org.apache.james.util.OptionalUtils;
import org.apache.james.util.retry.DoublingRetrySchedule;
import org.apache.james.util.retry.api.RetrySchedule;
import org.apache.james.util.retry.naming.ldap.RetryingLdapContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Strings;

/**
 * <p>
 * This repository implementation serves as a bridge between Apache James and
 * LDAP. It allows James to authenticate users against an LDAP compliant server
 * such as Apache DS or Microsoft AD. It also enables role/group based access
 * restriction based on LDAP groups.
 * </p>
 * <p>
 * It is intended for organisations that already have a user-authentication and
 * authorisation mechanism in place, and want to leverage this when deploying
 * James. The assumption inherent here is that such organisations would not want
 * to manage user details via James, but will do so externally using whatever
 * mechanism provided by, or built on top off, their LDAP implementation.
 * </p>
 * <p>
 * Based on this assumption, this repository is strictly <b>read-only</b>. As a
 * consequence, user modification, deletion and creation requests will be
 * ignored when using this repository.
 * </p>
 * <p>
 * The following fragment of XML provides an example configuration to enable
 * this repository: </br>
 *
 * <pre>
 *  &lt;users-store&gt;
 *      &lt;repository name=&quot;LDAPUsers&quot;
 *      class=&quot;org.apache.james.userrepository.ReadOnlyUsersLDAPRepository&quot;
 *      ldapHost=&quot;ldap://myldapserver:389&quot;
 *      principal=&quot;uid=ldapUser,ou=system&quot;
 *      credentials=&quot;password&quot;
 *      userBase=&quot;ou=People,o=myorg.com,ou=system&quot;
 *      userIdAttribute=&quot;uid&quot;
 *      userObjectClass=&quot;inetOrgPerson&quot;
 *      maxRetries=&quot;20&quot;
 *      retryStartInterval=&quot;0&quot;
 *      retryMaxInterval=&quot;30&quot;
 *      retryIntervalScale=&quot;1000&quot;
 *      administratorId=&quot;ldapAdmin&quot;
 *  &lt;/users-store&gt;
 * </pre>
 *
 * </br>
 *
 * Its constituent attributes are defined as follows:
 * <ul>
 * <li><b>ldapHost:</b> The URL of the LDAP server to connect to.</li>
 * <li>
 * <b>principal:</b> (optional) The name (DN) of the user with which to
 * initially bind to the LDAP server.</li>
 * <li>
 * <b>credentials:</b> (optional) The password with which to initially bind to
 * the LDAP server.</li>
 * <li>
 * <b>userBase:</b>The context within which to search for user entities.</li>
 * <li>
 * <b>userIdAttribute:</b>The name of the LDAP attribute which holds user ids.
 * For example &quot;uid&quot; for Apache DS, or &quot;sAMAccountName&quot; for
 * Microsoft Active Directory.</li>
 * <li>
 * <b>userObjectClass:</b>The objectClass value for user nodes below the
 * userBase. For example &quot;inetOrgPerson&quot; for Apache DS, or
 * &quot;user&quot; for Microsoft Active Directory.</li>
 **
 * <li>
 * <b>maxRetries:</b> (optional, default = 0) The maximum number of times to
 * retry a failed operation. -1 means retry forever.</li>
 * <li>
 * <b>retryStartInterval:</b> (optional, default = 0) The interval in
 * milliseconds to wait before the first retry. If > 0, subsequent retries are
 * made at double the proceeding one up to the <b>retryMaxInterval</b> described
 * below. If = 0, the next retry is 1 and subsequent retries proceed as above.</li>
 * <li>
 * <b>retryMaxInterval:</b> (optional, default = 60) The maximum interval in
 * milliseconds to wait between retries</li>
 * <li>
 * <b>retryIntervalScale:</b> (optional, default = 1000) The amount by which to
 * multiply each retry interval. The default value of 1000 (milliseconds) is 1
 * second, so the default <b>retryMaxInterval</b> of 60 is 60 seconds, or 1
 * minute.
 * </ul>
 * </p>
 * <p>
 * <em>Example Schedules</em>
 * <ul>
 * <li>
 * Retry after 1000 milliseconds, doubling the interval for each retry up to
 * 30000 milliseconds, subsequent retry intervals are 30000 milliseconds until
 * 10 retries have been attempted, after which the <code>Exception</code>
 * causing the fault is thrown:
 * <ul>
 * <li>maxRetries = 10
 * <li>retryStartInterval = 1000
 * <li>retryMaxInterval = 30000
 * <li>retryIntervalScale = 1
 * </ul>
 * <li>
 * Retry immediately, then retry after 1 * 1000 milliseconds, doubling the
 * interval for each retry up to 30 * 1000 milliseconds, subsequent retry
 * intervals are 30 * 1000 milliseconds until 20 retries have been attempted,
 * after which the <code>Exception</code> causing the fault is thrown:
 * <ul>
 * <li>maxRetries = 20
 * <li>retryStartInterval = 0
 * <li>retryMaxInterval = 30
 * <li>retryIntervalScale = 1000
 * </ul>
 * <li>
 * Retry after 5000 milliseconds, subsequent retry intervals are 5000
 * milliseconds. Retry forever:
 * <ul>
 * <li>maxRetries = -1
 * <li>retryStartInterval = 5000
 * <li>retryMaxInterval = 5000
 * <li>retryIntervalScale = 1
 * </ul>
 * </ul>
 * </p>
 *
 * <p>
 * In order to enable group/role based access restrictions, you can use the
 * &quot;&lt;restriction&gt;&quot; configuration element. An example of this is
 * shown below: <br>
 *
 * <pre>
 * &lt;restriction
 *  memberAttribute=&quot;uniqueMember&quot;&gt;
 *    &lt;group&gt;cn=PermanentStaff,ou=Groups,o=myorg.co.uk,ou=system&lt;/group&gt;
 *          &lt;group&gt;cn=TemporaryStaff,ou=Groups,o=myorg.co.uk,ou=system&lt;/group&gt;
 * &lt;/restriction&gt;
 * </pre>
 *
 * Its constituent attributes and elements are defined as follows:
 * <ul>
 * <li>
 * <b>memberAttribute:</b> The LDAP attribute whose values indicate the DNs of
 * the users which belong to the group or role.</li>
 * <li>
 * <b>group:</b> A valid group or role DN. A user is only authenticated
 * (permitted access) if they belong to at least one of the groups listed under
 * the &quot;&lt;restriction&gt;&quot; sections.</li>
 * </ul>
 * </p>
 *
 * <p>
 * The following parameters may be used to adjust the underlying
 * <code>com.sun.jndi.ldap.LdapCtxFactory</code>. See <a href=
 * "http://docs.oracle.com/javase/1.5.0/docs/guide/jndi/jndi-ldap.html#SPIPROPS"
 * > LDAP Naming Service Provider for the Java Naming and Directory InterfaceTM
 * (JNDI) : Provider-specific Properties</a> for details.
 * <ul>
 * <li>
 * <b>useConnectionPool:</b> (optional, default = true) Sets property
 * <code>com.sun.jndi.ldap.connect.pool</code> to the specified boolean value
 * <li>
 * <b>connectionTimeout:</b> (optional) Sets property
 * <code>com.sun.jndi.ldap.connect.timeout</code> to the specified integer value
 * <li>
 * <b>readTimeout:</b> (optional) Sets property
 * <code>com.sun.jndi.ldap.read.timeout</code> to the specified integer value.
 * Applicable to Java 6 and above.
 * <li>
 * <b>administratorId:</b> (optional) User identifier of the administrator user.
 * The administrator user is allowed to authenticate as other users.
 * </ul>
 * </p>
 *
 * <p>
 * The <b>supportsVirtualHosting</b> tag allows you to define this repository as supporing
 * virtual hosting. For this LDAP repository, it means users will be looked for by their email
 * address instead of their unique identifier.
 * Generally to make it work, you need to configure <b>userIdAttribute</b> attribute to map
 * to a mail attribute such as <code>mail</code> instead of an unique id identifier.
 * </p>
 *
 * @see ReadOnlyLDAPUser
 * @see ReadOnlyLDAPGroupRestriction
 *
 */
public class ReadOnlyUsersLDAPRepository implements UsersRepository, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReadOnlyUsersLDAPRepository.class);

    // The name of the factory class which creates the initial context
    // for the LDAP service provider
    private static final String INITIAL_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";

    private static final String PROPERTY_NAME_CONNECTION_POOL = "com.sun.jndi.ldap.connect.pool";
    private static final String PROPERTY_NAME_CONNECT_TIMEOUT = "com.sun.jndi.ldap.connect.timeout";
    private static final String PROPERTY_NAME_READ_TIMEOUT = "com.sun.jndi.ldap.read.timeout";
    public static final String SUPPORTS_VIRTUAL_HOSTING = "supportsVirtualHosting";

    /**
     * The context for the LDAP server. This is the connection that is built
     * from the configuration attributes &quot;ldapHost&quot;,
     * &quot;principal&quot; and &quot;credentials&quot;.
     */
    private LdapContext ldapContext;
    // The schedule for retry attempts
    private RetrySchedule schedule = null;

    private final DomainList domainList;
    private LdapRepositoryConfiguration ldapConfiguration;

    @Inject
    public ReadOnlyUsersLDAPRepository(DomainList domainList) {
        super();
        this.domainList = domainList;
    }

    /**
     * Extracts the parameters required by the repository instance from the
     * James server configuration data. The fields extracted include
     * {@link LdapRepositoryConfiguration#ldapHost}, {@link LdapRepositoryConfiguration#userIdAttribute}, {@link LdapRepositoryConfiguration#userBase},
     * {@link LdapRepositoryConfiguration#principal}, {@link LdapRepositoryConfiguration#credentials} and {@link LdapRepositoryConfiguration#restriction}.
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

        schedule = new DoublingRetrySchedule(
            configuration.getRetryStartInterval(),
            configuration.getRetryMaxInterval(),
            configuration.getScale());
    }

    /**
     * Initialises the user-repository instance. It will create a connection to
     * the LDAP host using the supplied configuration.
     *
     * @throws Exception
     *             If an error occurs authenticating or connecting to the
     *             specified LDAP host.
     */
    @PostConstruct
    public void init() throws Exception {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(this.getClass().getName() + ".init()" + '\n' + "LDAP host: " + ldapConfiguration.getLdapHost()
                + '\n' + "User baseDN: " + ldapConfiguration.getUserBase() + '\n' + "userIdAttribute: "
                + ldapConfiguration.getUserIdAttribute() + '\n' + "Group restriction: " + ldapConfiguration.getRestriction()
                + '\n' + "UseConnectionPool: " + ldapConfiguration.useConnectionPool() + '\n' + "connectionTimeout: "
                + ldapConfiguration.getConnectionTimeout() + '\n' + "readTimeout: " + ldapConfiguration.getReadTimeout()
                + '\n' + "retrySchedule: " + schedule + '\n' + "maxRetries: " + ldapConfiguration.getMaxRetries() + '\n');
        }
        // Setup the initial LDAP context
        updateLdapContext();
    }

    /**
     * Answer the LDAP context used to connect with the LDAP server.
     *
     * @return an <code>LdapContext</code>
     * @throws NamingException
     */
    protected LdapContext getLdapContext() throws NamingException {
        if (null == ldapContext) {
            updateLdapContext();
        }
        return ldapContext;
    }

    protected void updateLdapContext() throws NamingException {
        ldapContext = computeLdapContext();
    }

    /**
     * Answers a new LDAP/JNDI context using the specified user credentials.
     *
     * @return an LDAP directory context
     * @throws NamingException
     *             Propagated from underlying LDAP communication API.
     */
    protected LdapContext computeLdapContext() throws NamingException {
        return new RetryingLdapContext(schedule, ldapConfiguration.getMaxRetries()) {

            @Override
            public Context newDelegate() throws NamingException {
                return new InitialLdapContext(getContextEnvironment(), null);
            }
        };
    }

    protected Properties getContextEnvironment() {
        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, INITIAL_CONTEXT_FACTORY);
        props.put(Context.PROVIDER_URL, Optional.ofNullable(ldapConfiguration.getLdapHost())
            .orElse(""));
        if (Strings.isNullOrEmpty(ldapConfiguration.getCredentials())) {
            props.put(Context.SECURITY_AUTHENTICATION, LdapConstants.SECURITY_AUTHENTICATION_NONE);
        } else {
            props.put(Context.SECURITY_AUTHENTICATION, LdapConstants.SECURITY_AUTHENTICATION_SIMPLE);
            props.put(Context.SECURITY_PRINCIPAL, Optional.ofNullable(ldapConfiguration.getPrincipal())
                .orElse(""));
            props.put(Context.SECURITY_CREDENTIALS, ldapConfiguration.getCredentials());
        }
        // The following properties are specific to com.sun.jndi.ldap.LdapCtxFactory
        props.put(PROPERTY_NAME_CONNECTION_POOL, String.valueOf(ldapConfiguration.useConnectionPool()));
        if (ldapConfiguration.getConnectionTimeout() > -1) {
            props.put(PROPERTY_NAME_CONNECT_TIMEOUT, String.valueOf(ldapConfiguration.getConnectionTimeout()));
        }
        if (ldapConfiguration.getReadTimeout() > -1) {
            props.put(PROPERTY_NAME_READ_TIMEOUT, Integer.toString(ldapConfiguration.getReadTimeout()));
        }
        return props;
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

    /**
     * Gets all the user entities taken from the LDAP server, as taken from the
     * search-context given by the value of the attribute {@link LdapRepositoryConfiguration#userBase}.
     *
     * @return A set containing all the relevant users found in the LDAP
     *         directory.
     * @throws NamingException
     *             Propagated from the LDAP communication layer.
     */
    private Set<String> getAllUsersFromLDAP() throws NamingException {
        Set<String> result = new HashSet<>();

        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        sc.setReturningAttributes(new String[] { "distinguishedName" });
        NamingEnumeration<SearchResult> sr = ldapContext.search(ldapConfiguration.getUserBase(), "(objectClass="
                + ldapConfiguration.getUserObjectClass() + ")", sc);
        while (sr.hasMore()) {
            SearchResult r = sr.next();
            result.add(r.getNameInNamespace());
        }

        return result;
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
     * @throws NamingException
     *             Propagated from the underlying LDAP communication layer.
     */
    private Collection<ReadOnlyLDAPUser> buildUserCollection(Collection<String> userDNs)
            throws NamingException {
        List<ReadOnlyLDAPUser> results = new ArrayList<>();

        for (String userDN : userDNs) {
            Optional<ReadOnlyLDAPUser> user = buildUser(userDN);
            user.ifPresent(results::add);
        }

        return results;
    }

    /**
     * For a given name, this method makes ldap search in userBase with filter {@link LdapRepositoryConfiguration#userIdAttribute}=name
     * and objectClass={@link LdapRepositoryConfiguration#userObjectClass} and builds {@link User} based on search result.
     *
     * @param name
     *            The userId which should be value of the field {@link LdapRepositoryConfiguration#userIdAttribute}
     * @return A {@link ReadOnlyLDAPUser} instance which is initialized with the
     *         userId of this user and ldap connection information with which
     *         the user was searched. Return null if such a user was not found.
     * @throws NamingException
     *             Propagated by the underlying LDAP communication layer.
     */
    private ReadOnlyLDAPUser searchAndBuildUser(Username name) throws NamingException {
        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        sc.setReturningAttributes(new String[] { ldapConfiguration.getUserIdAttribute() });
        sc.setCountLimit(1);

        String filterTemplate = "(&({0}={1})(objectClass={2})" +
            StringUtils.defaultString(ldapConfiguration.getFilter(), "") +
            ")";

        String sanitizedFilter = FilterEncoder.format(
            filterTemplate,
            ldapConfiguration.getUserIdAttribute(),
            name.asString(),
            ldapConfiguration.getUserObjectClass());

        NamingEnumeration<SearchResult> sr = ldapContext.search(ldapConfiguration.getUserBase(), sanitizedFilter, sc);

        if (!sr.hasMore()) {
            return null;
        }

        SearchResult r = sr.next();
        Attribute userName = r.getAttributes().get(ldapConfiguration.getUserIdAttribute());

        if (!ldapConfiguration.getRestriction().isActivated()
            || userInGroupsMembershipList(r.getNameInNamespace(), ldapConfiguration.getRestriction().getGroupMembershipLists(ldapContext))) {
            return new ReadOnlyLDAPUser(Username.of(userName.get().toString()), r.getNameInNamespace(), ldapContext);
        }

        return null;
    }

    /**
     * Given a userDN, this method retrieves the user attributes from the LDAP
     * server, so as to extract the items that are of interest to James.
     * Specifically it extracts the userId, which is extracted from the LDAP
     * attribute whose name is given by the value of the field
     * {@link LdapRepositoryConfiguration#userIdAttribute}.
     *
     * @param userDN
     *            The distinguished-name of the user whose details are to be
     *            extracted from the LDAP repository.
     * @return A {@link ReadOnlyLDAPUser} instance which is initialized with the
     *         userId of this user and ldap connection information with which
     *         the userDN and attributes were obtained.
     * @throws NamingException
     *             Propagated by the underlying LDAP communication layer.
     */
    private Optional<ReadOnlyLDAPUser> buildUser(String userDN) throws NamingException {
      Attributes userAttributes = ldapContext.getAttributes(userDN);
      Optional<Attribute> userName = Optional.ofNullable(userAttributes.get(ldapConfiguration.getUserIdAttribute()));
      return userName
          .map(Throwing.<Attribute, String>function(u -> u.get().toString()).sneakyThrow())
          .map(Username::of)
          .map(username -> new ReadOnlyLDAPUser(username, userDN, ldapContext));
    }

    @Override
    public boolean contains(Username name) throws UsersRepositoryException {
        return getUserByName(name) != null;
    }

    @Deprecated
    public boolean containsCaseInsensitive(String name) throws UsersRepositoryException {
        return getUserByNameCaseInsensitive(name) != null;
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        try {
            return Math.toIntExact(getValidUsers().stream()
                .map(Throwing.function(this::buildUser).sneakyThrow())
                .flatMap(OptionalUtils::toStream)
                .count());
        } catch (NamingException e) {
            LOGGER.error("Unable to retrieve user count from ldap", e);
            throw new UsersRepositoryException("Unable to retrieve user count from ldap", e);

        }
    }

    @Deprecated
    public Username getRealName(String name) throws UsersRepositoryException {
        User u = getUserByNameCaseInsensitive(name);
        if (u != null) {
            return u.getUserName();
        }

        return null;
    }

    @Override
    public User getUserByName(Username name) throws UsersRepositoryException {
        try {
          return searchAndBuildUser(name);
        } catch (NamingException e) {
            LOGGER.error("Unable to retrieve user from ldap", e);
            throw new UsersRepositoryException("Unable to retrieve user from ldap", e);

        }
    }

    @Deprecated
    public User getUserByNameCaseInsensitive(String name) throws UsersRepositoryException {
        try {
            for (ReadOnlyLDAPUser u : buildUserCollection(getValidUsers())) {
                if (u.getUserName().asString().equalsIgnoreCase(name)) {
                    return u;
                }
            }

        } catch (NamingException e) {
            LOGGER.error("Unable to retrieve user from ldap", e);
            throw new UsersRepositoryException("Unable to retrieve user from ldap", e);

        }
        return null;
    }

    @Override
    public Iterator<Username> list() throws UsersRepositoryException {
        try {
            return buildUserCollection(getValidUsers())
                .stream()
                .map(ReadOnlyLDAPUser::getUserName)
                .collect(Guavate.toImmutableList())
                .iterator();
        } catch (NamingException namingException) {
            throw new UsersRepositoryException(
                    "Unable to retrieve users list from LDAP due to unknown naming error.",
                    namingException);
        }
    }

    private Collection<String> getValidUsers() throws NamingException {
        Set<String> userDNs = getAllUsersFromLDAP();
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
    }

    @Override
    public void removeUser(Username name) throws UsersRepositoryException {
        LOGGER.warn("This user-repository is read-only. Modifications are not permitted.");
        throw new UsersRepositoryException(
                "This user-repository is read-only. Modifications are not permitted.");

    }

    @Override
    public boolean test(Username name, String password) throws UsersRepositoryException {
        User u = getUserByName(name);
        return u != null && u.verifyPassword(password);
    }

    @Override
    public void addUser(Username username, String password) throws UsersRepositoryException {
        LOGGER.error("This user-repository is read-only. Modifications are not permitted.");
        throw new UsersRepositoryException(
                "This user-repository is read-only. Modifications are not permitted.");
    }

    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        LOGGER.error("This user-repository is read-only. Modifications are not permitted.");
        throw new UsersRepositoryException(
                "This user-repository is read-only. Modifications are not permitted.");
    }

    /**
     * VirtualHosting not supported
     */
    @Override
    public boolean supportVirtualHosting() {
        return ldapConfiguration.supportsVirtualHosting();
    }

    @Override
    public boolean isAdministrator(Username username) {
        if (ldapConfiguration.getAdministratorId().isPresent()) {
            return ldapConfiguration.getAdministratorId().get().equals(username);
        }
        return false;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }


    @Override
    public MailAddress getMailAddressFor(Username username) throws UsersRepositoryException {
        try {
            if (supportVirtualHosting()) {
                return new MailAddress(username.asString());
            }
            return new MailAddress(username.getLocalPart(), domainList.getDefaultDomain());
        } catch (Exception e) {
            throw new UsersRepositoryException("Failed to compute mail address associated with the user", e);
        }
    }
}
