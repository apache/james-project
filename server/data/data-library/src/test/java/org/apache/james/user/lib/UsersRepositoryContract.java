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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.InvalidUsernameException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.model.Algorithm;
import org.apache.james.user.lib.model.DefaultUser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import reactor.core.publisher.Flux;

public interface UsersRepositoryContract {

    class UserRepositoryExtension implements BeforeEachCallback, ParameterResolver {

        public static UserRepositoryExtension withVirtualHost() {
            return new UserRepositoryExtension(true);
        }

        public static UserRepositoryExtension withoutVirtualHosting() {
            return new UserRepositoryExtension(false);
        }

        private final boolean supportVirtualHosting;
        private TestSystem testSystem;

        private UserRepositoryExtension(boolean supportVirtualHosting) {
            this.supportVirtualHosting = supportVirtualHosting;
        }

        @Override
        public void beforeEach(ExtensionContext extensionContext) throws Exception {
            testSystem = new TestSystem(supportVirtualHosting);
        }

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return parameterContext.getParameter().getType() == TestSystem.class;
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return testSystem;
        }

        public boolean isSupportVirtualHosting() {
            return supportVirtualHosting;
        }
    }

    class TestSystem {
        static final Domain DOMAIN = Domain.of("james.org");
        static final Domain UNKNOWN_DOMAIN = Domain.of("unknown.org");

        private final boolean supportVirtualHosting;
        private final SimpleDomainList domainList;
        private final Username user1;
        private final Username user1CaseVariation;
        private final Username user2;
        private final Username user3;
        private final Username admin;
        private final Username adminCaseVariation;
        private final Username userWithUnknownDomain;
        private final Username invalidUsername;

        TestSystem(boolean supportVirtualHosting) throws Exception {
            this.supportVirtualHosting = supportVirtualHosting;
            domainList = new SimpleDomainList();
            domainList.addDomain(DOMAIN);
            user1 = toUsername("username");
            user2 = toUsername("username2");
            user3 = toUsername("username3");
            user1CaseVariation = toUsername("uSeRnaMe");
            admin = toUsername("admin");
            adminCaseVariation = toUsername("adMin");
            userWithUnknownDomain = toUsername("unknown", UNKNOWN_DOMAIN);
            invalidUsername = toUsername("userContains)*(");
        }

        private Username toUsername(String login) {
            return toUsername(login, DOMAIN);
        }

        private Username toUsername(String login, Domain domain) {
            if (supportVirtualHosting) {
                return Username.fromLocalPartWithDomain(login, domain);
            } else {
                return Username.fromLocalPartWithoutDomain(login);
            }
        }

        public SimpleDomainList getDomainList() {
            return domainList;
        }

        public Username getAdmin() {
            return admin;
        }

        public Username getUserWithUnknownDomain() {
            return userWithUnknownDomain;
        }
    }

    UsersRepository testee();

    UsersRepository testee(Optional<Username> administrator) throws Exception;


    interface ReadOnlyContract extends UsersRepositoryContract {
        @Test
        default void countUsersShouldReturnZeroWhenEmptyRepository() throws UsersRepositoryException {
            //Given
            int expected = 0;
            //When
            int actual = testee().countUsers();
            //Then
            assertThat(actual).isEqualTo(expected);
        }

        @Test
        default void listShouldReturnEmptyIteratorWhenEmptyRepository(TestSystem testSystem) throws UsersRepositoryException {
            //When
            Iterator<Username> actual = testee().list();
            //Then
            assertThat(actual)
                .toIterable()
                .isEmpty();
        }

        @Test
        default void isAdministratorShouldBeCaseInsensitive(TestSystem testSystem) throws Exception {
            UsersRepository testee = testee(Optional.of(testSystem.admin));
            assertThat(testee.isAdministrator(testSystem.adminCaseVariation))
                .isTrue();
        }

        @Test
        default void testShouldReturnEmptyWhenEmptyRepository(TestSystem testSystem) throws UsersRepositoryException {
            assertThat(testee().test(testSystem.user1, "password")).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("illegalCharacters")
        default void assertValidShouldThrowWhenUsernameLocalPartWithIllegalCharacter(String illegalCharacter) {
            assertThatThrownBy(() -> testee().assertValid(Username.of("a" + illegalCharacter + "a")))
                .isInstanceOf(InvalidUsernameException.class);
        }

        static Stream<Arguments> illegalCharacters() {
            return Stream.of(
                "\"",
                "(",
                ")",
                ",",
                ":",
                ";",
                "<",
                ">",
                "@",
                "[",
                "\\",
                "]",
                " ")
                .map(Arguments::of);
        }
    }

    interface ReadWriteContract extends UsersRepositoryContract {

        @Test
        default void countUsersShouldReturnNumberOfUsersWhenNotEmptyRepository(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            List<Username> keys = Arrays.asList(testSystem.user1, testSystem.user2, testSystem.user3);
            for (Username username : keys) {
                testee().addUser(username, username.asString());
            }
            //When
            int actual = testee().countUsers();
            //Then
            assertThat(actual).isEqualTo(keys.size());
        }

        @Test
        default void listShouldReturnExactlyUsersInRepository(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            List<Username> keys = Arrays.asList(testSystem.user1, testSystem.user2, testSystem.user3);
            for (Username username : keys) {
                testee().addUser(username, username.asString());
            }
            //When
            Iterator<Username> actual = testee().list();
            //Then
            assertThat(actual)
                .toIterable()
                .containsOnly(testSystem.user1, testSystem.user2, testSystem.user3);
        }

        @Test
        default void addUserShouldAddAUserWhenEmptyRepository(TestSystem testSystem) throws UsersRepositoryException {
            //When
            testee().addUser(testSystem.user2, "password2");
            //Then
            assertThat(testee().contains(testSystem.user2)).isTrue();
        }

        @Test
        default void containsShouldPreserveCaseVariation(TestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.user1CaseVariation, "password2");

            assertThat(testee().contains(testSystem.user1CaseVariation)).isTrue();
        }

        @Test
        default void containsShouldBeCaseInsensitive(TestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.user1CaseVariation, "password2");

            assertThat(testee().contains(testSystem.user1)).isTrue();
        }

        @Test
        default void containsShouldBeCaseInsensitiveWhenOriginalValueLowerCased(TestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.user1, "password2");

            assertThat(testee().contains(testSystem.user1CaseVariation)).isTrue();
        }

        @Test
        default void addUserShouldDisableCaseVariationWhenOriginalValueLowerCased(TestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.user1, "password2");

            assertThatThrownBy(() -> testee().addUser(testSystem.user1CaseVariation, "pass"))
                .isInstanceOf(UsersRepositoryException.class);
        }

        @Test
        default void addUserShouldDisableCaseVariation(TestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.user1CaseVariation, "password2");

            assertThatThrownBy(() -> testee().addUser(testSystem.user1, "pass"))
                .isInstanceOf(UsersRepositoryException.class);
        }

        @Test
        default void listShouldReturnLowerCaseUser(TestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.user1CaseVariation, "password2");

            assertThat(testee().list())
                .toIterable()
                .containsExactly(testSystem.user1);
        }

        @Test
        default void removeUserShouldBeCaseInsensitiveOnCaseVariationUser(TestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.user1CaseVariation, "password2");

            testee().removeUser(testSystem.user1);

            assertThat(testee().list())
                .toIterable()
                .isEmpty();
        }

        @Test
        default void removeUserShouldBeCaseInsensitive(TestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.user1, "password2");

            testee().removeUser(testSystem.user1CaseVariation);

            assertThat(testee().list())
                .toIterable()
                .isEmpty();
        }

        @Test
        default void getUserByNameShouldBeCaseInsensitive(TestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.user1, "password2");

            assertThat(testee().getUserByName(testSystem.user1CaseVariation).getUserName())
                .isEqualTo(testSystem.user1);
        }

        @Test
        default void getUserByNameShouldReturnLowerCaseAddedUser(TestSystem testSystem) throws UsersRepositoryException {
            testee().addUser(testSystem.user1CaseVariation, "password2");

            assertThat(testee().getUserByName(testSystem.user1).getUserName())
                .isEqualTo(testSystem.user1);
        }


        @Test
        default void testShouldBeCaseInsensitiveOnCaseVariationUser(TestSystem testSystem) throws UsersRepositoryException {
            String password = "password2";
            testee().addUser(testSystem.user1CaseVariation, password);

            assertThat(testee().test(testSystem.user1, password))
                .isEqualTo(Optional.of(testSystem.user1));
        }

        @Test
        default void testShouldBeCaseInsensitive(TestSystem testSystem) throws UsersRepositoryException {
            String password = "password2";
            testee().addUser(testSystem.user1, password);

            assertThat(testee().test(testSystem.user1CaseVariation, password))
                .isEqualTo(Optional.of(testSystem.user1CaseVariation));
        }

        @Test
        default void addUserShouldAddAUserWhenNotEmptyRepository(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.user2, "password2");
            //When
            testee().addUser(testSystem.user3, "password3");
            //Then
            assertThat(testee().contains(testSystem.user3)).isTrue();
        }

        @Test
        default void addUserShouldThrowWhenSameUsernameWithDifferentCase(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.toUsername("myUsername"), "password");
            //When
            assertThatThrownBy(() -> testee().addUser(testSystem.toUsername("MyUsername"), "password"))
                .isInstanceOf(AlreadyExistInUsersRepositoryException.class);
        }

        @Test
        default void addUserShouldThrowWhenUserAlreadyPresentInRepository(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.user1, "password");
            //When
            assertThatThrownBy(() -> testee().addUser(testSystem.user1, "password2"))
                .isInstanceOf(AlreadyExistInUsersRepositoryException.class);
        }

        @Test
        default void getUserByNameShouldReturnAUserWhenContainedInRepository(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.user1, "password");
            //When
            User actual = testee().getUserByName(testSystem.user1);
            //Then
            assertThat(actual).isNotNull();
            assertThat(actual.getUserName()).isEqualTo(testSystem.user1);
        }

        @Test
        default void getUserByNameShouldReturnUserWhenDifferentCase(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.toUsername("username"), "password");
            //When
            User actual = testee().getUserByName(testSystem.toUsername("uSERNAMe"));
            //Then
            assertThat(actual).isNotNull();
            assertThat(actual.getUserName()).isEqualTo(testSystem.user1);
        }

        @Test
        default void testShouldReturnUsernameWhenAUserHasACorrectPassword(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.user1, "password");
            //When
            Optional<Username> loggedInUser = testee().test(testSystem.user1, "password");
            //Then
            assertThat(loggedInUser).isEqualTo(Optional.of(testSystem.user1));
        }

        @Test
        default void testShouldReturnEmptyWhenAUserHasAnIncorrectPassword(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.user1, "password");
            //When
            Optional<Username> loggedInUser = testee().test(testSystem.user1, "password2");
            //Then
            assertThat(loggedInUser).isEmpty();
        }

        @Test
        default void testShouldReturnEmptyWhenAUserHasAnIncorrectCasePassword(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.user1, "password");
            //When
            Optional<Username> loggedInUser = testee().test(testSystem.user1, "Password");
            //Then
            assertThat(loggedInUser).isEmpty();
        }

        @Test
        default void testShouldReturnEmptyWhenAUserIsNotInRepository(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.toUsername("username"), "password");
            //When
            Optional<Username> loggedInUser = testee().test(testSystem.toUsername("username2"), "password");
            //Then
            assertThat(loggedInUser).isEmpty();
        }

        @Test
        default void testShouldReturnUsernameWhenAUserHasAnIncorrectCaseName(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.toUsername("username"), "password");
            //When
            Optional<Username> loggedInUser = testee().test(testSystem.toUsername("userName"), "password");
            //Then
            assertThat(loggedInUser).isEqualTo(Optional.of(testSystem.toUsername("userName")));
        }


        @Test
        default void testShouldReturnEmptyWhenAUserIsRemovedFromRepository(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.user1, "password");
            testee().removeUser(testSystem.user1);
            //When
            Optional<Username> loggedInUser = testee().test(testSystem.user1, "password");
            //Then
            assertThat(loggedInUser).isEmpty();
        }

        @Test
        default void removeUserShouldRemoveAUserWhenPresentInRepository(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.user1, "password");
            //When
            testee().removeUser(testSystem.user1);
            //Then
            assertThat(testee().contains(testSystem.user1)).isFalse();
        }

        @Test
        default void updateUserShouldAllowToAuthenticateWithNewPassword(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.user1, "password");
            User user = testee().getUserByName(testSystem.user1);
            user.setPassword("newpass");
            //When
            testee().updateUser(user);
            //Then
            assertThat(testee().test(testSystem.user1, "newpass")).isEqualTo(Optional.of(testSystem.user1));
        }

        @Test
        default void updateUserShouldNotAllowToAuthenticateWithOldPassword(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.user1, "password");
            User user = testee().getUserByName(testSystem.user1);
            user.setPassword("newpass");
            //When
            testee().updateUser(user);
            //Then
            assertThat(testee().test(testSystem.user1, "password")).isEmpty();
        }

        @Test
        default void updateUserShouldThrowWhenAUserIsNoMoreInRepository(TestSystem testSystem) throws UsersRepositoryException {
            //Given
            testee().addUser(testSystem.user1, "password");
            User user = testee().getUserByName(testSystem.user1);
            testee().removeUser(testSystem.user1);
            //When
            assertThatThrownBy(() -> testee().updateUser(user))
                .isInstanceOf(UsersRepositoryException.class);
        }

        @Test
        default void removeUserShouldThrowWhenUserNotInRepository(TestSystem testSystem) {
            //When
            assertThatThrownBy(() -> testee().removeUser(testSystem.user1))
                .isInstanceOf(UsersRepositoryException.class);
        }

        @Test
        default void isAdministratorShouldReturnFalseWhenNotConfigured(TestSystem testSystem) throws Exception {
            UsersRepository testee = testee(Optional.empty());

            assertThat(testee.isAdministrator(testSystem.admin)).isFalse();
        }

        @Test
        default void isAdministratorShouldReturnTrueWhenConfiguredAndUserIsAdmin(TestSystem testSystem) throws Exception {
            UsersRepository testee = testee(Optional.of(testSystem.admin));

            assertThat(testee.isAdministrator(testSystem.admin)).isTrue();
        }

        @Test
        default void isAdministratorShouldReturnFalseWhenConfiguredAndUserIsNotAdmin(TestSystem testSystem) throws Exception {
            UsersRepository testee = testee(Optional.of(testSystem.admin));

            assertThat(testee.isAdministrator(testSystem.user1)).isFalse();
        }
    }

    interface WithVirtualHostingReadWriteContract extends ReadWriteContract {

        @Test
        default void testShouldReturnUsernameWhenAUserHasACorrectPasswordAndOtherCaseInDomain(TestSystem testSystem) throws Exception {
            testSystem.domainList.addDomain(Domain.of("Domain.OrG"));
            String username = "myuser";
            String password = "password";
            testee().addUser(Username.of(username + "@Domain.OrG"), password);

            Optional<Username> loggedInUser = testee().test(Username.of(username + "@domain.org"), password);

            assertThat(loggedInUser).isEqualTo(Optional.of(Username.of(username + "@domain.org")));
        }

        @Test
        default void listUsersOfADomainShouldNotListOtherDomainUsers(TestSystem testSystem) throws Exception {
            testSystem.domainList.addDomain(Domain.of("domain1.tld"));
            testee().addUser(Username.of("user1@domain1.tld"), "password");

            testSystem.domainList.addDomain(Domain.of("domain2.tld"));
            testee().addUser(Username.of("user2@domain2.tld"), "password");

            assertThat(Flux.from(testee().listUsersOfADomainReactive(Domain.of("domain1.tld")))
                .collectList()
                .block())
                .containsOnly(Username.of("user1@domain1.tld"));
        }

        @Test
        default void addUserShouldThrowWhenUserDoesNotBelongToDomainList(TestSystem testSystem) {
            assertThatThrownBy(() -> testee().addUser(testSystem.userWithUnknownDomain, "password"))
                .isInstanceOf(InvalidUsernameException.class)
                .hasMessage("Domain does not exist in DomainList");
        }

        @Test
        default void addUserShouldThrowWhenInvalidUser(TestSystem testSystem) {
            assertThatThrownBy(() -> testee().addUser(testSystem.invalidUsername, "password"))
                .isInstanceOf(InvalidUsernameException.class)
                .hasMessageContaining("should not contain any of those characters");
        }

        @Test
        default void updateUserShouldThrowWhenUserDoesNotBelongToDomainList(TestSystem testSystem) {
            assertThatThrownBy(() -> testee().updateUser(new DefaultUser(testSystem.userWithUnknownDomain, Algorithm.of("hasAlg"), Algorithm.of("hasAlg"))))
                .isInstanceOf(InvalidUsernameException.class)
                .hasMessage("Domain does not exist in DomainList");
        }

        @Test
        default void updateUserShouldNotThrowInvalidUsernameExceptionWhenInvalidUser(TestSystem testSystem) {
            assertThatThrownBy(() -> testee().updateUser(new DefaultUser(testSystem.invalidUsername, Algorithm.of("hasAlg"), Algorithm.of("hasAlg"))))
                .isNotInstanceOf(InvalidUsernameException.class);
        }

        @Test
        default void removeUserShouldThrowWhenUserDoesNotBelongToDomainList(TestSystem testSystem) {
            assertThatThrownBy(() -> testee().removeUser(testSystem.userWithUnknownDomain))
                .isInstanceOf(InvalidUsernameException.class)
                .hasMessage("Domain does not exist in DomainList");
        }

        @Test
        default void removeUserShouldNotThrowInvalidUsernameExceptionWhenInvalidUser(TestSystem testSystem) {
            assertThatThrownBy(() -> testee().removeUser(testSystem.invalidUsername))
                .isNotInstanceOf(InvalidUsernameException.class);
        }
    }

    interface WithVirtualHostingReadOnlyContract extends ReadOnlyContract {

        @Test
        default void getUserByNameShouldNotThrowWhenUserDoesNotBelongToDomainList(TestSystem testSystem) {
            assertThatCode(() -> testee().getUserByName(testSystem.userWithUnknownDomain))
                .doesNotThrowAnyException();
        }

        @Test
        default void containsShouldNotThrowWhenUserDoesNotBelongToDomainList(TestSystem testSystem) {
            assertThatCode(() -> testee().contains(testSystem.userWithUnknownDomain))
                .doesNotThrowAnyException();
        }

        @Test
        default void testShouldNotThrowWhenUserDoesNotBelongToDomainList(TestSystem testSystem) {
            assertThatCode(() -> testee().test(testSystem.userWithUnknownDomain, "password"))
                .doesNotThrowAnyException();
        }

        @Test
        default void isAdministratorShouldThrowWhenUserDoesNotBelongToDomainList(TestSystem testSystem) {
            assertThatThrownBy(() -> testee().isAdministrator(testSystem.userWithUnknownDomain))
                .isInstanceOf(InvalidUsernameException.class)
                .hasMessage("Domain does not exist in DomainList");
        }

        @Test
        default void virtualHostedUsersRepositoryShouldUseFullMailAddressAsUsername() throws Exception {
            // Some implementations do not support changing virtual hosting value
            Assumptions.assumeTrue(testee().supportVirtualHosting());

            assertThat(testee().getUsername(new MailAddress("local@domain"))).isEqualTo(Username.of("local@domain"));
        }

        @Test
        default void getMailAddressForShouldBeIdentityWhenVirtualHosting() throws Exception {
            // Some implementations do not support changing virtual hosting value
            Assumptions.assumeTrue(testee().supportVirtualHosting());

            String username = "user@domain";
            assertThat(testee().getMailAddressFor(Username.of(username)))
                .isEqualTo(username);
        }

        @Test
        default void getUserShouldBeCaseInsensitive() throws Exception {
            assertThat(testee().getUsername(new MailAddress("lowerUPPER", TestSystem.DOMAIN)))
                .isEqualTo(Username.fromLocalPartWithDomain("lowerupper", TestSystem.DOMAIN));
        }

        @Test
        default void assertDomainPartValidShouldThrowWhenDomainPartIsMissing() throws Exception {
            Username withoutDomainPart = Username.fromLocalPartWithoutDomain("localPartOnly");

            assertThatThrownBy(() -> testee().assertValid(withoutDomainPart))
                .isInstanceOf(InvalidUsernameException.class)
                .hasMessage("Given Username needs to contain a @domainpart");
        }

        @Test
        default void assertDomainPartValidShouldThrowWhenDomainPartIsNotManaged(TestSystem testSystem) {
            assertThatThrownBy(() -> testee().assertValid(testSystem.userWithUnknownDomain))
                .isInstanceOf(InvalidUsernameException.class)
                .hasMessage("Domain does not exist in DomainList");
        }

        @Test
        default void assertDomainPartValidShouldNotThrowWhenDomainPartIsManaged() {
            Username userWithManagedDomain = Username.fromLocalPartWithDomain(
                "localPart",
                TestSystem.DOMAIN);

            assertThatCode(() -> testee().assertValid(userWithManagedDomain))
                .doesNotThrowAnyException();
        }
    }

    interface WithOutVirtualHostingReadOnlyContract extends ReadOnlyContract {
        @Test
        default void nonVirtualHostedUsersRepositoryShouldUseLocalPartAsUsername() throws Exception {
            // Some implementations do not support changing virtual hosting value
            Assumptions.assumeFalse(testee().supportVirtualHosting());

            assertThat(testee().getUsername(new MailAddress("local@domain"))).isEqualTo(Username.of("local"));
        }

        @Test
        default void getMailAddressForShouldAppendDefaultDomainWhenNoVirtualHosting(TestSystem testSystem) throws Exception {
            // Some implementations do not support changing virtual hosting value
            Assumptions.assumeFalse(testee().supportVirtualHosting());

            String username = "user";
            assertThat(testee().getMailAddressFor(Username.of(username)))
                .isEqualTo(new MailAddress(username, testSystem.domainList.getDefaultDomain()));
        }

        @Test
        default void getUserShouldBeCaseInsensitive() throws Exception {
            assertThat(testee().getUsername(new MailAddress("lowerUPPER", TestSystem.DOMAIN)))
                .isEqualTo(Username.fromLocalPartWithoutDomain("lowerupper"));
        }

        @Test
        default void assertDomainPartValidShouldThrowWhenDomainPartIsPresent() {
            Username withDomainPart = Username.fromLocalPartWithDomain(
                "localPart",
                TestSystem.DOMAIN);

            assertThatThrownBy(() -> testee().assertValid(withDomainPart))
                .isInstanceOf(InvalidUsernameException.class)
                .hasMessage("Given Username contains a @domainpart but virtualhosting support is disabled");
        }

        @Test
        default void assertDomainPartValidShouldNotThrowWhenDomainPartIsMissing() {
            Username withOutDomainPart = Username.fromLocalPartWithoutDomain("localPartOnly");

            assertThatCode(() -> testee().assertValid(withOutDomainPart))
                .doesNotThrowAnyException();
        }
    }

    interface WithVirtualHostingContract extends WithVirtualHostingReadOnlyContract, WithVirtualHostingReadWriteContract {
    }

    interface WithOutVirtualHostingContract extends WithOutVirtualHostingReadOnlyContract, ReadWriteContract {
    }
}
