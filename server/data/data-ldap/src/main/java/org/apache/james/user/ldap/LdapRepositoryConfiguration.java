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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class LdapRepositoryConfiguration {
    public static final String SUPPORTS_VIRTUAL_HOSTING = "supportsVirtualHosting";

    private static final int NO_CONNECTION_TIMEOUT = 0;
    private static final int NO_READ_TIME_OUT = 0;
    private static final boolean ENABLE_VIRTUAL_HOSTING = true;
    private static final ReadOnlyLDAPGroupRestriction NO_RESTRICTION = new ReadOnlyLDAPGroupRestriction(null);
    private static final String NO_FILTER = null;
    private static final Optional<String> NO_ADMINISTRATOR_ID = Optional.empty();
    private static final int DEFAULT_POOL_SIZE = 4;

    public static class Builder {
        private Optional<List<URI>> ldapHosts;
        private Optional<String> principal;
        private Optional<String> credentials;
        private Optional<String> userBase;
        private Optional<String> userIdAttribute;
        private Optional<String> resolveLocalPartAttribute;
        private Optional<String> userObjectClass;
        private Optional<Integer> poolSize;
        private Optional<Boolean> trustAllCerts;
        private ImmutableMap.Builder<Domain, String> perDomainBaseDN;

        public Builder() {
            ldapHosts = Optional.empty();
            principal = Optional.empty();
            credentials = Optional.empty();
            userBase = Optional.empty();
            userIdAttribute = Optional.empty();
            resolveLocalPartAttribute = Optional.empty();
            userObjectClass = Optional.empty();
            poolSize = Optional.empty();
            trustAllCerts = Optional.empty();
            perDomainBaseDN = ImmutableMap.builder();
        }

        public Builder ldapHosts(List<URI> ldapHosts) {
            this.ldapHosts = Optional.of(ldapHosts);
            return this;
        }

        public Builder principal(String principal) {
            this.principal = Optional.of(principal);
            return this;
        }

        public Builder credentials(String credentials) {
            this.credentials = Optional.of(credentials);
            return this;
        }

        public Builder userBase(String userBase) {
            this.userBase = Optional.of(userBase);
            return this;
        }

        public Builder userIdAttribute(String userIdAttribute) {
            this.userIdAttribute = Optional.of(userIdAttribute);
            return this;
        }

        public Builder resolveLocalPartAttribute(String resolveLocalPartAttribute) {
            this.resolveLocalPartAttribute = Optional.of(resolveLocalPartAttribute);
            return this;
        }

        public Builder userObjectClass(String userObjectClass) {
            this.userObjectClass = Optional.of(userObjectClass);
            return this;
        }

        public Builder poolSize(int poolSize) {
            this.poolSize = Optional.of(poolSize);
            return this;
        }

        public Builder trustAllCerts(boolean trustAllCerts) {
            this.trustAllCerts = Optional.of(trustAllCerts);
            return this;
        }

        public Builder addPerDomainDN(Domain domain, String dn) {
            this.perDomainBaseDN.put(domain, dn);
            return this;
        }

        public LdapRepositoryConfiguration build() throws ConfigurationException {
            Preconditions.checkState(ldapHosts.isPresent(), "'ldapHosts' is mandatory");
            Preconditions.checkState(principal.isPresent(), "'principal' is mandatory");
            Preconditions.checkState(credentials.isPresent(), "'credentials' is mandatory");
            Preconditions.checkState(userBase.isPresent(), "'userBase' is mandatory");
            Preconditions.checkState(userIdAttribute.isPresent(), "'userIdAttribute' is mandatory");
            Preconditions.checkState(userObjectClass.isPresent(), "'userObjectClass' is mandatory");

            return new LdapRepositoryConfiguration(
                ldapHosts.get(),
                principal.get(),
                credentials.get(),
                userBase.get(),
                userIdAttribute.get(),
                resolveLocalPartAttribute,
                userObjectClass.get(),
                NO_CONNECTION_TIMEOUT,
                NO_READ_TIME_OUT,
                !ENABLE_VIRTUAL_HOSTING,
                poolSize.orElse(DEFAULT_POOL_SIZE),
                NO_RESTRICTION,
                NO_FILTER,
                NO_ADMINISTRATOR_ID,
                trustAllCerts.orElse(false),
                perDomainBaseDN.build());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static LdapRepositoryConfiguration from(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        List<URI> ldapHosts = Splitter.on(",")
            .splitToList(Optional.ofNullable(configuration.getString("[@ldapHosts]", null))
                .orElse(configuration.getString("[@ldapHost]", ""))
                .trim())
            .stream()
            .map(Throwing.function(URI::new))
            .collect(ImmutableList.toImmutableList());

        String principal = configuration.getString("[@principal]", "");
        String credentials = configuration.getString("[@credentials]", "");
        String userBase = configuration.getString("[@userBase]");
        String userIdAttribute = configuration.getString("[@userIdAttribute]");
        Optional<String> resolveLocalPartAttribute = Optional.ofNullable(configuration.getString("[@resolveLocalPartAttribute]", null));
        String userObjectClass = configuration.getString("[@userObjectClass]");
        // Default is to use connection pooling
        int connectionTimeout = configuration.getInt("[@connectionTimeout]", NO_CONNECTION_TIMEOUT);
        int readTimeout = configuration.getInt("[@readTimeout]", NO_READ_TIME_OUT);
        boolean supportsVirtualHosting = configuration.getBoolean(SUPPORTS_VIRTUAL_HOSTING, !ENABLE_VIRTUAL_HOSTING);

        HierarchicalConfiguration<ImmutableNode> restrictionConfig = null;
        // Check if we have a restriction we can use
        // See JAMES-1204
        if (configuration.containsKey("restriction[@memberAttribute]")) {
            restrictionConfig = configuration.configurationAt("restriction");
        }
        ReadOnlyLDAPGroupRestriction restriction = new ReadOnlyLDAPGroupRestriction(restrictionConfig);

        //see if there is a filter argument
        String filter = configuration.getString("[@filter]");
        Boolean trustAllCerts = configuration.getBoolean("[@trustAllCerts]", false);

        Optional<String> administratorId = Optional.ofNullable(configuration.getString("[@administratorId]"));
        int poolSize = Optional.ofNullable(configuration.getInteger("[@poolSize]", null))
                .orElse(DEFAULT_POOL_SIZE);

        ImmutableMap.Builder<Domain, String> builder = ImmutableMap.builder();
        if (configuration.getNodeModel()
                .getInMemoryRepresentation()
                .getChildren()
                .stream()
                .anyMatch(n -> n.getNodeName().equals("domains"))) {
            HierarchicalConfiguration<ImmutableNode> domains = configuration.configurationAt("domains");
            Iterator<String> keys = domains.getKeys();
            while (keys.hasNext()) {
                String next = keys.next();
                builder.put(Domain.of(next), domains.getString(next));
            }
        }
        return new LdapRepositoryConfiguration(
            ldapHosts,
            principal,
            credentials,
            userBase,
            userIdAttribute,
            resolveLocalPartAttribute,
            userObjectClass,
            connectionTimeout,
            readTimeout,
            supportsVirtualHosting,
            poolSize,
            restriction,
            filter,
            administratorId,
            trustAllCerts,
            builder.build());
    }

    /**
     * The list of URLs of the LDAP servers against which users are to be authenticated.
     * Note that users are actually authenticated by binding against the LDAP
     * servers using the users &quot;dn&quot; and &quot;credentials&quot;.The
     * value of this field is taken from the value of the configuration
     * attribute &quot;ldapHosts&quot; and fallback to the legacy attribute &quot;ldapHost&quot;.
     * URLs are split by the comma  &quot;,&quot; character.
     */
    private final List<URI> ldapHosts;

    /**
     * The user with which to initially bind to the LDAP server. The value of
     * this field is taken from the configuration attribute
     * &quot;principal&quot;.
     */
    private final String principal;

    /**
     * The password/credentials with which to initially bind to the LDAP server.
     * The value of this field is taken from the configuration attribute
     * &quot;credentials&quot;.
     */
    private final String credentials;

    /**
     * This is the LDAP context/sub-context within which to search for user
     * entities. The value of this field is taken from the configuration
     * attribute &quot;userBase&quot;.
     */
    private final String userBase;

    /**
     * The value of this field is taken from the configuration attribute
     * &quot;userIdAttribute&quot;. This is the LDAP attribute type which holds
     * the userId value. Note that this is not the same as the email address
     * attribute.
     */
    private final String userIdAttribute;

    /**
     * The value of this field is taken from the configuration attribute
     * &quot;resolveLocalPartAttribute&quot;. This is the LDAP attribute type which enables
     * user authentication using local part as login username while Virtual Hosting is on.
     * Default to empty, which disables login with local part as username.
     */
    private final Optional<String> resolveLocalPartAttribute;

    /**
     * The value of this field is taken from the configuration attribute
     * &quot;userObjectClass&quot;. This is the LDAP object class to use in the
     * search filter for user nodes under the userBase value.
     */
    private final String userObjectClass;

    // The connection timeout in milliseconds.
    // A value of less than or equal to zero means to use the network protocol's
    // (i.e., TCP's) timeout value.
    private final int connectionTimeout;

    // The LDAP read timeout in milliseconds.
    private final int readTimeout;

    private final boolean supportsVirtualHosting;
    private final int poolSize;

    /**
     * Encapsulates the information required to restrict users to LDAP groups or
     * roles. This object is populated from the contents of the configuration
     * element &lt;restriction&gt;.
     */
    private final ReadOnlyLDAPGroupRestriction restriction;

    /**
     * The value of this field is taken from the configuration attribute &quot;filter&quot;.
     * This is the search filter to use to find the desired user.
     */
    private final String filter;

    /**
     * UserId of the administrator
     * The administrator is allowed to log in as other users
     */
    private final Optional<Username> administratorId;

    private final boolean trustAllCerts;

    private final ImmutableMap<Domain, String> perDomainBaseDN;

    private LdapRepositoryConfiguration(List<URI> ldapHosts, String principal, String credentials, String userBase, String userIdAttribute,
                                        Optional<String> resolveLocalPartAttribute, String userObjectClass, int connectionTimeout, int readTimeout,
                                        boolean supportsVirtualHosting, int poolSize, ReadOnlyLDAPGroupRestriction restriction, String filter,
                                        Optional<String> administratorId, boolean trustAllCerts,
                                        ImmutableMap<Domain, String> perDomainBaseDN) throws ConfigurationException {
        this.ldapHosts = ldapHosts;
        this.principal = principal;
        this.credentials = credentials;
        this.userBase = userBase;
        this.userIdAttribute = userIdAttribute;
        this.resolveLocalPartAttribute = resolveLocalPartAttribute;
        this.userObjectClass = userObjectClass;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
        this.supportsVirtualHosting = supportsVirtualHosting;
        this.poolSize = poolSize;
        this.restriction = restriction;
        this.filter = filter;
        this.administratorId = administratorId.map(Username::of);
        this.trustAllCerts = trustAllCerts;
        this.perDomainBaseDN = perDomainBaseDN;

        checkState();
    }

    private void checkState() throws ConfigurationException {
        if (userBase == null) {
            throw new ConfigurationException("[@userBase] is mandatory");
        }
        if (userIdAttribute == null) {
            throw new ConfigurationException("[@userIdAttribute] is mandatory");
        }
        if (userObjectClass == null) {
            throw new ConfigurationException("[@userObjectClass] is mandatory");
        }
    }

    public List<URI> getLdapHosts() {
        return ldapHosts;
    }

    public String getPrincipal() {
        return principal;
    }

    public String getCredentials() {
        return credentials;
    }

    public String getUserBase() {
        return userBase;
    }

    public String getUserIdAttribute() {
        return userIdAttribute;
    }

    public Optional<String> getResolveLocalPartAttribute() {
        return resolveLocalPartAttribute;
    }

    public String getUserObjectClass() {
        return userObjectClass;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public boolean supportsVirtualHosting() {
        return supportsVirtualHosting;
    }

    public ReadOnlyLDAPGroupRestriction getRestriction() {
        return restriction;
    }

    public String getFilter() {
        return filter;
    }

    public Optional<Username> getAdministratorId() {
        return administratorId;
    }

    public boolean isTrustAllCerts() {
        return trustAllCerts;
    }

    public ImmutableMap<Domain, String> getPerDomainBaseDN() {
        return perDomainBaseDN;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof LdapRepositoryConfiguration) {
            LdapRepositoryConfiguration that = (LdapRepositoryConfiguration) o;

            return Objects.equals(this.connectionTimeout, that.connectionTimeout)
                && Objects.equals(this.readTimeout, that.readTimeout)
                && Objects.equals(this.supportsVirtualHosting, that.supportsVirtualHosting)
                && Objects.equals(this.ldapHosts, that.ldapHosts)
                && Objects.equals(this.principal, that.principal)
                && Objects.equals(this.credentials, that.credentials)
                && Objects.equals(this.userBase, that.userBase)
                && Objects.equals(this.userIdAttribute, that.userIdAttribute)
                && Objects.equals(this.resolveLocalPartAttribute, that.resolveLocalPartAttribute)
                && Objects.equals(this.userObjectClass, that.userObjectClass)
                && Objects.equals(this.restriction, that.restriction)
                && Objects.equals(this.filter, that.filter)
                && Objects.equals(this.poolSize, that.poolSize)
                && Objects.equals(this.administratorId, that.administratorId)
                && Objects.equals(this.trustAllCerts, that.trustAllCerts)
                && Objects.equals(this.perDomainBaseDN, that.perDomainBaseDN);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(ldapHosts, principal, credentials, userBase, userIdAttribute, resolveLocalPartAttribute, userObjectClass,
            connectionTimeout, readTimeout, supportsVirtualHosting, restriction, filter, administratorId, poolSize,
            trustAllCerts, perDomainBaseDN);
    }
}
