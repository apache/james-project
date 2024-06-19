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

import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collection;
import java.util.Optional;

import jakarta.mail.MessagingException;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.user.ldap.DockerLdapSingleton;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SenderHasLDAPAttributeTest {

    static LdapGenericContainer ldapContainer = DockerLdapSingleton.ldapContainer;

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.stop();
    }

    @Test
    void shouldThrowWhenPrefixedWithDelimiter() throws Exception {
        SenderHasLDAPAttribute testee = new SenderHasLDAPAttribute(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("HasLDAPAttribute")
            .condition(":abcdef")
            .build();

        assertThatThrownBy(() -> testee.init(matcherConfig))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void shouldNotThrowWhenValueContainsDelimiter() throws Exception {
        SenderHasLDAPAttribute testee = new SenderHasLDAPAttribute(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("HasLDAPAttribute")
            .condition("description:abc:def")
            .build();
        testee.init(matcherConfig);

        assertThatCode(() -> testee.init(matcherConfig))
            .doesNotThrowAnyException();;
    }

    @Test
    void shouldReturnRecipientWhenHasAttribute() throws Exception {
        SenderHasLDAPAttribute testee = new SenderHasLDAPAttribute(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("SenderHasLDAPAttribute")
            .condition("description")
            .build();
        testee.init(matcherConfig);

        MailAddress sender = new MailAddress("james-user@james.org");
        MailAddress recipient = new MailAddress("recipient@james.org");
        Collection<MailAddress> matched = testee.match(FakeMail.builder()
            .name("default-id")
            .sender(sender)
            .recipient(recipient)
            .build());

        assertThat(matched).containsOnly(recipient);
    }

    @Test
    void shouldNotReturnRecipientWhenDoesNotHaveAttribute() throws Exception {
        SenderHasLDAPAttribute testee = new SenderHasLDAPAttribute(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("SenderHasLDAPAttribute")
            .condition("descriptionaa")
            .build();
        testee.init(matcherConfig);

        MailAddress sender = new MailAddress("james-user@james.org");
        MailAddress recipient = new MailAddress("recipient@james.org");
        Collection<MailAddress> matched = testee.match(FakeMail.builder()
            .name("default-id")
            .sender(sender)
            .recipient(recipient)
            .build());

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldReturnSenderWhenHasAttribute() throws Exception {
        SenderHasLDAPAttribute testee = new SenderHasLDAPAttribute(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("HasLDAPAttribute")
            .condition("description:abcdef")
            .build();
        testee.init(matcherConfig);

        MailAddress sender = new MailAddress("james-user@james.org");
        MailAddress recipient = new MailAddress("recipient@james.org");
        Collection<MailAddress> matched = testee.match(FakeMail.builder()
            .name("default-id")
            .sender(sender)
            .recipient(recipient)
            .build());

        assertThat(matched).containsOnly(recipient);
    }

    @Test
    void shouldReturnEmptyWhenNoSender() throws Exception {
        SenderHasLDAPAttribute testee = new SenderHasLDAPAttribute(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("HasLDAPAttribute")
            .condition("description:abcdef")
            .build();
        testee.init(matcherConfig);

        MailAddress recipient = new MailAddress("recipient@james.org");
        Collection<MailAddress> matched = testee.match(FakeMail.builder()
            .name("default-id")
            .recipient(recipient)
            .build());

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenHasNoAttribute() throws Exception {
        SenderHasLDAPAttribute testee = new SenderHasLDAPAttribute(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("HasLDAPAttribute")
            .condition("description:abcdef")
            .build();
        testee.init(matcherConfig);

        MailAddress sender = new MailAddress("jbob@james.org");
        MailAddress recipient = new MailAddress("recipient@james.org");
        Collection<MailAddress> matched = testee.match(FakeMail.builder()
            .name("default-id")
            .sender(sender)
            .recipient(recipient)
            .build());

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenHasBadAttribute() throws Exception {
        SenderHasLDAPAttribute testee = new SenderHasLDAPAttribute(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("HasLDAPAttribute")
            .condition("description:abcdefg")
            .build();
        testee.init(matcherConfig);

        MailAddress sender = new MailAddress("james-user@james.org");
        MailAddress recipient = new MailAddress("recipient@james.org");
        Collection<MailAddress> matched = testee.match(FakeMail.builder()
            .name("default-id")
            .sender(sender)
            .recipient(recipient)
            .build());

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenHasPartialAttribute() throws Exception {
        SenderHasLDAPAttribute testee = new SenderHasLDAPAttribute(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("HasLDAPAttribute")
            .condition("description:abcde")
            .build();
        testee.init(matcherConfig);

        MailAddress sender = new MailAddress("james-user@james.org");
        MailAddress recipient = new MailAddress("recipient@james.org");
        Collection<MailAddress> matched = testee.match(FakeMail.builder()
            .name("default-id")
            .sender(sender)
            .recipient(recipient)
            .build());

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenHasLongerAttributeName() throws Exception {
        SenderHasLDAPAttribute testee = new SenderHasLDAPAttribute(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("HasLDAPAttribute")
            .condition("descriptionaaa:abcdef")
            .build();
        testee.init(matcherConfig);

        MailAddress sender = new MailAddress("james-user@james.org");
        MailAddress recipient = new MailAddress("recipient@james.org");
        Collection<MailAddress> matched = testee.match(FakeMail.builder()
            .name("default-id")
                .sender(sender)
            .recipient(recipient)
            .build());

        assertThat(matched).isEmpty();
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer) {
        return ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of(ADMIN));
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer, Optional<Username> administrator) {
        PropertyListConfiguration configuration = baseConfiguration(ldapContainer);
        configuration.addProperty("[@userIdAttribute]", "mail");
        configuration.addProperty("supportsVirtualHosting", true);
        administrator.ifPresent(username -> configuration.addProperty("[@administratorId]", username.asString()));
        return configuration;
    }

    private static PropertyListConfiguration baseConfiguration(LdapGenericContainer ldapContainer) {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", ldapContainer.getLdapHost());
        configuration.addProperty("[@principal]", "cn=admin,dc=james,dc=org");
        configuration.addProperty("[@credentials]", ADMIN_PASSWORD);
        configuration.addProperty("[@userBase]", "ou=people,dc=james,dc=org");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@connectionTimeout]", "2000");
        configuration.addProperty("[@readTimeout]", "2000");
        return configuration;
    }
}