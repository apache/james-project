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
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_LOCAL_PART;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;
import static org.apache.james.user.ldap.DockerLdapSingleton.DOMAIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.JAMES_USER;
import static org.apache.james.user.ldap.DockerLdapSingleton.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.apache.james.user.lib.AbstractUsersRepository;
import org.apache.james.user.lib.AbstractUsersRepositoryContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

class ReadOnlyUsersLDAPRepositoryTest {

    static final Logger LOGGER = LoggerFactory.getLogger(ReadOnlyUsersLDAPRepositoryTest.class);
    static final Username JAMES_USER_MAIL = Username.of(JAMES_USER.getLocalPart()+ "@" + DOMAIN);
    static final Username UNKNOWN = Username.of("unknown");
    static final String BAD_PASSWORD = "badpassword";

    static LdapGenericContainer ldapContainer = LdapGenericContainer.builder()
        .domain(DOMAIN)
        .password(ADMIN_PASSWORD)
        .build();

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.start();
    }

    @Nested
    class WhenEnableVirtualHosting implements AbstractUsersRepositoryContract.WithVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withVirtualHost();

        private ReadOnlyUsersLDAPRepository usersRepository;

        @BeforeEach
        void setUp(TestSystem testSystem) throws Exception {
            usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer), testSystem.getDomainList());
        }

        @Override
        public AbstractUsersRepository testee() {
            return usersRepository;
        }

        @Override
        @Test
        public void isAdministratorShouldReturnTrueWhenConfiguredAndUserIsAdmin(TestSystem testSystem) throws Exception {
            assertThat(testee().isAdministrator(testSystem.getAdmin())).isTrue();
        }

        @Test
        void knownUserShouldBeAbleToLogInWhenPasswordIsCorrectWithVirtualHosting() throws Exception {
            assertThat(usersRepository.test(JAMES_USER_MAIL, PASSWORD)).isTrue();
        }

        @Test
        void testShouldStillWorkAfterRestartingLDAP() throws Exception {
            usersRepository.test(JAMES_USER_MAIL, PASSWORD);

            DockerLdapSingleton.ldapContainer.pause();
            try {
                usersRepository.test(JAMES_USER_MAIL, PASSWORD);
            } catch (Exception e) {
                LOGGER.info("This exception is expected as we shut down the LDAP and forced its use", e);
            }
            DockerLdapSingleton.ldapContainer.unpause();

            assertThat(usersRepository.test(JAMES_USER_MAIL, PASSWORD)).isTrue();
        }

        @Test
        void knownUserShouldNotBeAbleToLogInWhenPasswordIsNotCorrectWithVirtualHosting() throws Exception {
            assertThat(usersRepository.test(JAMES_USER, BAD_PASSWORD)).isFalse();
        }

        @Test
        void unknownUserShouldNotBeAbleToLogInWithVirtualHosting() throws Exception {
            assertThat(usersRepository.test(UNKNOWN, BAD_PASSWORD)).isFalse();
        }

        @Test
        void unknownUserShouldNotBeAbleToLogInWhenPasswordIsCorrectWithVirtualHosting() throws Exception {
            assertThat(usersRepository.test(UNKNOWN, PASSWORD)).isFalse();
        }

        @Test
        void specialCharacterInUserInputShouldBeSanitized() throws Exception {
            Username patternMatchingMultipleUsers = Username.of("j*");

            assertThat(usersRepository.test(patternMatchingMultipleUsers, PASSWORD)).isFalse();
        }

        @Test
        void containsWithGetUserShouldBeTrueWithVirtualHosting() throws Exception {
            assertThat(usersRepository.contains(usersRepository.getUsername(JAMES_USER_MAIL.asMailAddress()))).isTrue();
        }

        @Disabled("JAMES-3088 isAdministrator is case sensitive")
        @Override
        @Test
        public void isAdministratorShouldBeCaseInsentive(TestSystem testSystem) throws Exception {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void isAdministratorShouldReturnFalseWhenNotConfigured(TestSystem testSystem) throws Exception {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void addUserShouldThrowWhenSameUsernameWithDifferentCase(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void updateUserShouldThrowWhenAUserIsNoMoreInRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void removeUserShouldBeCaseInsentive(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 Users are provisioned by default from Dockerfile, cannot setup this test case")
        @Override
        @Test
        public void listShouldReturnEmptyIteratorWhenEmptyRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void countUsersShouldReturnNumberOfUsersWhenNotEmptyRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void addUserShouldDisableCaseVariation(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void updateUserShouldAllowToAuthenticateWithNewPassword(TestSystem testSystem){
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void addUserShouldAddAUserWhenNotEmptyRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldReturnFalseWhenAUserHasAnIncorrectCasePassword(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void listShouldReturnExactlyUsersInRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldReturnTrueWhenAUserHasACorrectPassword(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void containsShouldBeCaseInsentiveWhenOriginalValueLowerCased(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void getUserByNameShouldReturnLowerCaseAddedUser(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 Users are provisioned by default from Dockerfile, cannot setup this test case")
        @Override
        @Test
        public void countUsersShouldReturnZeroWhenEmptyRepository() {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldReturnFalseWhenAUserIsNotInRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldReturnTrueWhenAUserHasACorrectPasswordAndOtherCaseInDomain(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void addUserShouldDisableCaseVariationWhenOriginalValueLowerCased(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldReturnTrueWhenAUserHasAnIncorrectCaseName(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void addUserShouldAddAUserWhenEmptyRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldBeCaseInsentive(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void containsShouldBeCaseInsentive(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void containsShouldPreserveCaseVariation(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void updateUserShouldNotAllowToAuthenticateWithOldPassword(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void getUserByNameShouldReturnAUserWhenContainedInRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void removeUserShouldRemoveAUserWhenPresentInRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldReturnFalseWhenAUserHasAnIncorrectPassword(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldReturnFalseWhenAUserIsRemovedFromRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void removeUserShouldBeCaseInsentiveOnCaseVariationUser(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void listShouldReturnLowerCaseUser(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void getUserByNameShouldBeCaseInsentive(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void getUserByNameShouldReturnUserWhenDifferentCase(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void addUserShouldThrowWhenUserAlreadyPresentInRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldBeCaseInsentiveOnCaseVariationUser(TestSystem testSystem) {
        }
    }

    @Nested
    class WhenDisableVirtualHosting implements AbstractUsersRepositoryContract.WithOutVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withoutVirtualHosting();

        private ReadOnlyUsersLDAPRepository usersRepository;

        @BeforeEach
        void setUp(TestSystem testSystem) throws Exception {
            usersRepository = startUsersRepository(ldapRepositoryConfiguration(ldapContainer), testSystem.getDomainList());
        }

        @Override
        public AbstractUsersRepository testee() {
            return usersRepository;
        }

        @Test
        void knownUserShouldBeAbleToLogInWhenPasswordIsCorrect() throws Exception {
            assertThat(usersRepository.test(JAMES_USER, PASSWORD)).isTrue();
        }

        @Test
        void knownUserShouldNotBeAbleToLogInWhenPasswordIsNotCorrect() throws Exception {
            assertThat(usersRepository.test(JAMES_USER, BAD_PASSWORD)).isFalse();
        }

        @Test
        void unknownUserShouldNotBeAbleToLogIn() throws Exception {
            assertThat(usersRepository.test(UNKNOWN, BAD_PASSWORD)).isFalse();
        }

        @Test
        void unknownUserShouldNotBeAbleToLogInWhenPasswordIsCorrect() throws Exception {
            assertThat(usersRepository.test(UNKNOWN, PASSWORD)).isFalse();
        }

        @Test
        void containsWithGetUserShouldBeTrue() throws Exception {
            assertThat(usersRepository.contains(usersRepository.getUsername(JAMES_USER_MAIL.asMailAddress()))).isTrue();
        }

        @Override
        @Test
        public void isAdministratorShouldReturnTrueWhenConfiguredAndUserIsAdmin(TestSystem testSystem) throws Exception {
            assertThat(testee().isAdministrator(testSystem.getAdmin())).isTrue();
        }

        @Disabled("JAMES-3088 isAdministrator is case sensitive")
        @Override
        @Test
        public void isAdministratorShouldBeCaseInsentive(TestSystem testSystem) throws Exception {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void isAdministratorShouldReturnFalseWhenNotConfigured(TestSystem testSystem) throws Exception {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void addUserShouldThrowWhenSameUsernameWithDifferentCase(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void updateUserShouldThrowWhenAUserIsNoMoreInRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void removeUserShouldBeCaseInsentive(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 Users are provisioned by default from Dockerfile, cannot setup this test case")
        @Override
        @Test
        public void listShouldReturnEmptyIteratorWhenEmptyRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void countUsersShouldReturnNumberOfUsersWhenNotEmptyRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void addUserShouldDisableCaseVariation(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void updateUserShouldAllowToAuthenticateWithNewPassword(TestSystem testSystem){
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void addUserShouldAddAUserWhenNotEmptyRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldReturnFalseWhenAUserHasAnIncorrectCasePassword(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void listShouldReturnExactlyUsersInRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldReturnTrueWhenAUserHasACorrectPassword(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void containsShouldBeCaseInsentiveWhenOriginalValueLowerCased(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void getUserByNameShouldReturnLowerCaseAddedUser(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 Users are provisioned by default from Dockerfile, cannot setup this test case")
        @Override
        @Test
        public void countUsersShouldReturnZeroWhenEmptyRepository() {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldReturnFalseWhenAUserIsNotInRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void addUserShouldDisableCaseVariationWhenOriginalValueLowerCased(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldReturnTrueWhenAUserHasAnIncorrectCaseName(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void addUserShouldAddAUserWhenEmptyRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldBeCaseInsentive(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void containsShouldBeCaseInsentive(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void containsShouldPreserveCaseVariation(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void updateUserShouldNotAllowToAuthenticateWithOldPassword(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void getUserByNameShouldReturnAUserWhenContainedInRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void removeUserShouldRemoveAUserWhenPresentInRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldReturnFalseWhenAUserHasAnIncorrectPassword(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldReturnFalseWhenAUserIsRemovedFromRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void removeUserShouldBeCaseInsentiveOnCaseVariationUser(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void listShouldReturnLowerCaseUser(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void getUserByNameShouldBeCaseInsentive(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void getUserByNameShouldReturnUserWhenDifferentCase(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void addUserShouldThrowWhenUserAlreadyPresentInRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 This user-repository is read-only. Modifications are not permitted.")
        @Override
        @Test
        public void testShouldBeCaseInsentiveOnCaseVariationUser(TestSystem testSystem) {
        }

    }

    @Nested
    class SupportVirtualHosting {

        @Test
        void supportVirtualHostingShouldReturnFalseByDefault() throws Exception {
            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
            usersLDAPRepository.configure(ldapRepositoryConfiguration(ldapContainer));

            assertThat(usersLDAPRepository.supportVirtualHosting()).isFalse();
        }

        @Test
        void supportVirtualHostingShouldReturnTrueWhenReportedInConfig() throws Exception {
            HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
            configuration.addProperty(ReadOnlyUsersLDAPRepository.SUPPORTS_VIRTUAL_HOSTING, "true");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
            usersLDAPRepository.configure(configuration);

            assertThat(usersLDAPRepository.supportVirtualHosting()).isTrue();
        }

        @Test
        void supportVirtualHostingShouldReturnFalseWhenReportedInConfig() throws Exception {
            HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
            configuration.addProperty(ReadOnlyUsersLDAPRepository.SUPPORTS_VIRTUAL_HOSTING, "false");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
            usersLDAPRepository.configure(configuration);

            assertThat(usersLDAPRepository.supportVirtualHosting()).isFalse();
        }

        @Test
        void configureShouldThrowOnNonBooleanValueForSupportsVirtualHosting() {
            HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
            configuration.addProperty(ReadOnlyUsersLDAPRepository.SUPPORTS_VIRTUAL_HOSTING, "bad");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());

            assertThatThrownBy(() -> usersLDAPRepository.configure(configuration))
                .isInstanceOf(ConversionException.class);
        }
    }

    private static ReadOnlyUsersLDAPRepository startUsersRepository(HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfiguration,
                                                             DomainList domainList) throws Exception {
        ReadOnlyUsersLDAPRepository ldapRepository = new ReadOnlyUsersLDAPRepository(domainList);
        ldapRepository.configure(ldapRepositoryConfiguration);
        ldapRepository.init();
        return ldapRepository;
    }

    private static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfiguration(LdapGenericContainer ldapContainer) {
        PropertyListConfiguration configuration = baseConfiguration(ldapContainer);
        configuration.addProperty("[@userIdAttribute]", "uid");
        configuration.addProperty("[@administratorId]", ADMIN_LOCAL_PART);
        return configuration;
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer) {
        PropertyListConfiguration configuration = baseConfiguration(ldapContainer);
        configuration.addProperty("[@userIdAttribute]", "mail");
        configuration.addProperty("supportsVirtualHosting", true);
        configuration.addProperty("[@administratorId]", ADMIN.asString());
        return configuration;
    }

    private static PropertyListConfiguration baseConfiguration(LdapGenericContainer ldapContainer) {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", ldapContainer.getLdapHost());
        configuration.addProperty("[@principal]", "cn=admin,dc=james,dc=org");
        configuration.addProperty("[@credentials]", ADMIN_PASSWORD);
        configuration.addProperty("[@userBase]", "ou=People,dc=james,dc=org");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@maxRetries]", "1");
        configuration.addProperty("[@retryStartInterval]", "0");
        configuration.addProperty("[@retryMaxInterval]", "2");
        configuration.addProperty("[@retryIntervalScale]", "100");
        configuration.addProperty("[@connectionTimeout]", "100");
        configuration.addProperty("[@readTimeout]", "100");
        return configuration;
    }
}
