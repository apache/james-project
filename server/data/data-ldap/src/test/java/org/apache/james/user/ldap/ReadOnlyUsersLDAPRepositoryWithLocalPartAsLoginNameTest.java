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

import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;
import static org.apache.james.user.ldap.DockerLdapSingleton.DOMAIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.JAMES_USER;
import static org.apache.james.user.ldap.DockerLdapSingleton.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.api.InvalidUsernameException;
import org.apache.james.user.lib.UsersRepositoryContract;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;

public class ReadOnlyUsersLDAPRepositoryWithLocalPartAsLoginNameTest {
    public static final Username JAMES_USER_WITH_DOMAIN_PART = Username.of(JAMES_USER.getLocalPart() + "@" + DOMAIN);
    public static final Username ALICE_WITH_DOMAIN_PART = Username.of("alice" + "@" + DOMAIN);
    public static final Username JAMES_USER_APP1 = Username.of("james-user-app1");
    public static final Username JAMES_USER_APP2 = Username.of("james-user-app2");
    public static final Username JAMES_USER_INVALID_APP = Username.of("james-user-invalidApp");
    public static final Username ALICE_APP1 = Username.of("alice-app1");

    private static LdapGenericContainer ldapContainer = LdapGenericContainer.builder()
        .domain(DOMAIN)
        .password(ADMIN_PASSWORD)
        .dockerFilePrefix("localpartLogin/")
        .build();

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.stop();
    }

    @Nested
    class WhenEnableVirtualHosting {
        @RegisterExtension
        UsersRepositoryContract.UserRepositoryExtension extension = UsersRepositoryContract.UserRepositoryExtension.withVirtualHost();

        private ReadOnlyUsersLDAPRepository usersRepository;

        @BeforeEach
        void setUp(UsersRepositoryContract.TestSystem testSystem) throws Exception {
            usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.empty()), testSystem.getDomainList());
        }

        @Nested
        class LocalPartLogin {
            @Test
            void localPartLoginShouldFailByDefault() throws Exception {
                assertThat(usersRepository.test(Username.of(JAMES_USER.getLocalPart()), PASSWORD))
                    .isEqualTo(Optional.empty());
            }

            @Test
            void localPartLoginShouldSucceedWhenValidResolveLocalPartAttributeAndValidCredential(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("uid")), testSystem.getDomainList());

                assertThat(usersRepository.test(Username.of(JAMES_USER.getLocalPart()), PASSWORD))
                    .isEqualTo(Optional.of(JAMES_USER_WITH_DOMAIN_PART));
            }

            @Test
            void localPartLoginShouldFailWhenValidResolveLocalPartAttributeButInvalidCredential(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("uid")), testSystem.getDomainList());

                assertThat(usersRepository.test(Username.of(JAMES_USER.getLocalPart()), "wrongPassword"))
                    .isEqualTo(Optional.empty());
            }

            @Test
            void localPartLoginShouldFailWhenInvalidResolveLocalPartAttribute(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("invalidAttribute")), testSystem.getDomainList());

                assertThat(usersRepository.test(Username.of(JAMES_USER.getLocalPart()), PASSWORD))
                    .isEqualTo(Optional.empty());
            }

            @Test
            void getUserByLocalPartShouldReturnTranslatedUsernameWhenValidResolveLocalPartAttribute(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("uid")), testSystem.getDomainList());

                assertThat(usersRepository.getUserByName(Username.of(JAMES_USER.getLocalPart())).getUserName())
                    .isEqualTo(JAMES_USER_WITH_DOMAIN_PART);
            }

            @Test
            void getUserByLocalPartShouldReturnNullWhenInvalidResolveLocalPartAttribute(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("invalidAttribute")), testSystem.getDomainList());

                assertThat(usersRepository.getUserByName(Username.of(JAMES_USER.getLocalPart()))).isNull();
            }

            @Test
            void assertValidUsernameShouldBeRelaxWhenResolveLocalPartAttributeEnabled(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("uid")), testSystem.getDomainList());

                assertThatCode(() -> usersRepository.assertValid(Username.of(JAMES_USER.getLocalPart())))
                    .doesNotThrowAnyException();
            }

            @Test
            void assertValidUsernameShouldBeStrictWhenResolveLocalPartAttributeDisabled() {
                assertThatThrownBy(() -> usersRepository.assertValid(Username.of(JAMES_USER.getLocalPart())))
                    .isInstanceOf(InvalidUsernameException.class);
            }
        }

        @Nested
        class OneAppPasswordLogin {
            @Test
            void oneAppLoginShouldFailByDefault() throws Exception {
                assertThat(usersRepository.test(Username.of(JAMES_USER.getLocalPart()), PASSWORD))
                    .isEqualTo(Optional.empty());
            }

            @Test
            void oneAppLoginShouldSucceedWhenValidResolveLocalPartAttributeAndValidCredentials(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("uid")), testSystem.getDomainList());

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    softly.assertThat(usersRepository.test(JAMES_USER_APP1, PASSWORD))
                        .isEqualTo(Optional.of(JAMES_USER_WITH_DOMAIN_PART));
                    softly.assertThat(usersRepository.test(JAMES_USER_APP2, PASSWORD))
                        .isEqualTo(Optional.of(JAMES_USER_WITH_DOMAIN_PART));
                    softly.assertThat(usersRepository.test(ALICE_APP1, PASSWORD))
                        .isEqualTo(Optional.of(ALICE_WITH_DOMAIN_PART));
                }));
            }

            @Test
            void oneAppLoginShouldFailWhenValidResolveLocalPartAttributeButInvalidCredentials(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("uid")), testSystem.getDomainList());

                SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                    assertThat(usersRepository.test(JAMES_USER_APP1, "wrongPassword"))
                        .isEqualTo(Optional.empty());
                    assertThat(usersRepository.test(JAMES_USER_APP2, "wrongPassword"))
                        .isEqualTo(Optional.empty());
                    assertThat(usersRepository.test(JAMES_USER_INVALID_APP, PASSWORD))
                        .isEqualTo(Optional.empty());
                }));
            }

            @Test
            void oneAppLoginShouldFailWhenInvalidResolveLocalPartAttribute(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("invalidAttribute")), testSystem.getDomainList());

                assertThat(usersRepository.test(JAMES_USER_APP1, PASSWORD))
                    .isEqualTo(Optional.empty());
            }

            @Test
            void getUserByAppIdShouldReturnTranslatedUsernameWhenValidResolveLocalPartAttribute(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("uid")), testSystem.getDomainList());

                assertThat(usersRepository.getUserByName(JAMES_USER_APP1).getUserName())
                    .isEqualTo(JAMES_USER_WITH_DOMAIN_PART);
            }

            @Test
            void getUserByAppIdShouldReturnNullWhenInvalidResolveLocalPartAttribute(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("invalidAttribute")), testSystem.getDomainList());

                assertThat(usersRepository.getUserByName(JAMES_USER_APP1)).isNull();
            }

            @Test
            void listUsersShouldNotReturnLdapAppEntriesAsUsersWhenValidResolveLocalPartAttribute(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("uid")), testSystem.getDomainList());

                assertThat(usersRepository.list())
                    .toIterable()
                    .containsExactlyInAnyOrder(JAMES_USER_WITH_DOMAIN_PART, ALICE_WITH_DOMAIN_PART);
            }

            @Test
            void listUsersShouldNotReturnLdapAppEntriesAsUsersEvenWhenMissConfiguredResolveLocalPartAttribute(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("invalidAttribute")), testSystem.getDomainList());

                assertThat(usersRepository.list())
                    .toIterable()
                    .containsExactlyInAnyOrder(JAMES_USER_WITH_DOMAIN_PART, ALICE_WITH_DOMAIN_PART);
            }

            @Test
            void listUsersShouldNotCountLdapAppEntriesAsUsersWhenValidResolveLocalPartAttribute(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("uid")), testSystem.getDomainList());

                assertThat(usersRepository.countUsers()).isEqualTo(2);
            }

            @Test
            void listUsersShouldNotCountLdapAppEntriesAsUsersEvenWhenMissConfiguredResolveLocalPartAttribute(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("invalidAttribute")), testSystem.getDomainList());

                assertThat(usersRepository.countUsers()).isEqualTo(2);
            }

            @Test
            void assertValidUsernameShouldBeRelaxWhenResolveLocalPartAttributeEnabled(UsersRepositoryContract.TestSystem testSystem) throws Exception {
                usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of("uid")), testSystem.getDomainList());

                assertThatCode(() -> usersRepository.assertValid(JAMES_USER_APP1))
                    .doesNotThrowAnyException();
            }

            @Test
            void assertValidUsernameShouldBeStrictWhenResolveLocalPartAttributeDisabled() {
                assertThatThrownBy(() -> usersRepository.assertValid(JAMES_USER_APP1))
                    .isInstanceOf(InvalidUsernameException.class);
            }
        }

    }

    private static ReadOnlyUsersLDAPRepository startUsersRepository(HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfiguration,
                                                                    DomainList domainList) throws Exception {
        ReadOnlyUsersLDAPRepository ldapRepository = new ReadOnlyUsersLDAPRepository(domainList);
        ldapRepository.configure(ldapRepositoryConfiguration);
        ldapRepository.init();
        return ldapRepository;
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer, Optional<String> resolveLocalPartAttribute) {
        return ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of(ADMIN), resolveLocalPartAttribute);
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer, Optional<Username> administrator, Optional<String> resolveLocalPartAttribute) {
        PropertyListConfiguration configuration = baseConfiguration(ldapContainer);
        configuration.addProperty("[@userIdAttribute]", "mail");
        resolveLocalPartAttribute.ifPresent(attribute -> configuration.addProperty("[@resolveLocalPartAttribute]", attribute));
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
