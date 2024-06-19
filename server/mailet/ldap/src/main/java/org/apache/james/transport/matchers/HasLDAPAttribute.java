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

package org.apache.james.transport.matchers;

import java.util.Collection;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.user.ldap.LDAPConnectionFactory;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

/**
 * Matchers that allow looking up for LDAP attributed for each recipient.
 *
 * To enable this matcher one need first to add the james-server-mailet-ldap.jar in the externals-jars folder of your
 * James installation.
 *
 * In order to match the presence of an attribute:
 *
 * <pre><code>
 * &lt;!-- Matches recipients that have the following attribute regardless of the actual value--&gt;
 * &lt;mailet matcher=&quot;HasLDAPAttibute=description&quot; class=&quot;Null&quot;&gt;
 *
 * &lt;/mailet&gt;
 * </code></pre>
 *
 * And in order to Match a specific value for that attribute:
 *
 * <pre><code>
 * &lt;!-- Matches recipients that have the following attribute with the specified value--&gt;
 * &lt;mailet matcher=&quot;HasLDAPAttribute=description:blocked&quot; class=&quot;Null&quot;&gt;
 *
 * &lt;/mailet&gt;
 * </code></pre>
 */
public class HasLDAPAttribute extends GenericMatcher {
    private final LDAPConnectionPool ldapConnectionPool;
    private final LdapRepositoryConfiguration configuration;
    private final Filter objectClassFilter;
    private final Optional<Filter> userExtraFilter;
    private String attributeName;
    private Optional<String> attributeValue;
    private String[] attributes;

    @Inject
    public HasLDAPAttribute(LdapRepositoryConfiguration configuration) throws LDAPException {
        this.configuration = configuration;
        ldapConnectionPool = new LDAPConnectionFactory(this.configuration).getLdapConnectionPool();

        userExtraFilter = Optional.ofNullable(configuration.getFilter())
            .map(Throwing.function(Filter::create).sneakyThrow());
        objectClassFilter = Filter.createEqualityFilter("objectClass", configuration.getUserObjectClass());
    }

    @Override
    public void init() throws MessagingException {
        String condition = getCondition().trim();
        int commaPosition = condition.indexOf(':');

        if (commaPosition == -1) {
            attributeName = condition;
            attributeValue = Optional.empty();
        } else {
            if (commaPosition == 0) {
                throw new MessagingException("Syntax Error. Missing attribute name.");
            }
            attributeName = condition.substring(0, commaPosition).trim();
            attributeValue = Optional.of(condition.substring(commaPosition + 1).trim());
        }

        attributes = ImmutableSet.builder()
            .add(configuration.getReturnedAttributes())
            .add(attributeName)
            .build().toArray(String[]::new);
    }

    @Override
    public Collection<MailAddress> match(Mail mail) {
        return mail.getRecipients()
            .stream()
            .filter(this::hasAttribute)
            .collect(ImmutableList.toImmutableList());
    }

    private boolean hasAttribute(MailAddress rcpt) {
        try {
            SearchResult searchResult = ldapConnectionPool.search(userBase(rcpt),
                SearchScope.SUB,
                createFilter(rcpt.asString(), configuration.getUserIdAttribute()),
                attributes);

            return searchResult.getSearchEntries()
                .stream()
                .anyMatch(this::hasAttribute);
        } catch (LDAPSearchException e) {
            throw new RuntimeException("Failed searching LDAP", e);
        }
    }

    private boolean hasAttribute(SearchResultEntry entry) {
        return attributeValue.map(value -> Optional.ofNullable(entry.getAttribute(attributeName))
            .map(Attribute::getValue)
            .map(value::equals)
            .orElse(false))
            .orElseGet(() -> entry.hasAttribute(attributeName));
    }

    private Filter createFilter(String retrievalName, String ldapUserRetrievalAttribute) {
        Filter specificUserFilter = Filter.createEqualityFilter(ldapUserRetrievalAttribute, retrievalName);
        return userExtraFilter
            .map(extraFilter -> Filter.createANDFilter(objectClassFilter, specificUserFilter, extraFilter))
            .orElseGet(() -> Filter.createANDFilter(objectClassFilter, specificUserFilter));
    }

    private String userBase(MailAddress mailAddress) {
        return userBase(mailAddress.getDomain());
    }

    private String userBase(Domain domain) {
        return configuration.getPerDomainBaseDN()
            .getOrDefault(domain, configuration.getUserBase());
    }
}
