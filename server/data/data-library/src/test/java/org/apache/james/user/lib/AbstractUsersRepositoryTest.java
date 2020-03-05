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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.InvalidUsernameException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
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


public abstract class AbstractUsersRepositoryTest {

    protected static class UserRepositoryExtension implements BeforeEachCallback, ParameterResolver {

        private final boolean supportVirtualHosting;
        private TestSystem testSystem;

        public UserRepositoryExtension(boolean supportVirtualHosting) {
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
    }

    protected static class TestSystem {
        static final Domain DOMAIN = Domain.of("domain");

        private final boolean supportVirtualHosting;
        private final SimpleDomainList domainList;
        private final Username user1;
        private final Username user1CaseVariation;
        private final Username user2;
        private final Username user3;
        private final Username admin;
        private final Username adminCaseVariation;

        TestSystem(boolean supportVirtualHosting) throws Exception {
            this.supportVirtualHosting = supportVirtualHosting;
            domainList = new SimpleDomainList();
            domainList.addDomain(DOMAIN);
            user1 = toUsername("username");
            user2 = toUsername("username2");
            user3 = toUsername("username3");
            user1CaseVariation = toUsername("uSeRnaMe");
            admin = toUsername("testSystem.admin");
            adminCaseVariation = toUsername("testSystem.admin");
        }

        private Username toUsername(String login) {
            if (supportVirtualHosting) {
                return Username.of(login + '@' + DOMAIN.name());
            } else {
                return Username.of(login);
            }
        }

        public SimpleDomainList getDomainList() {
            return domainList;
        }
    }

    protected abstract AbstractUsersRepository testee();
    
    @Test
    void countUsersShouldReturnZeroWhenEmptyRepository() throws UsersRepositoryException {
        //Given
        int expected = 0;
        //When
        int actual = testee().countUsers();
        //Then
        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    void countUsersShouldReturnNumberOfUsersWhenNotEmptyRepository(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        ArrayList<Username> keys = new ArrayList<>(3);
        keys.add(testSystem.user1);
        keys.add(testSystem.user2);
        keys.add(testSystem.user3);
        for (Username username : keys) {
            testee().addUser(username, username.asString());
        }
        //When
        int actual = testee().countUsers();
        //Then
        assertThat(actual).isEqualTo(keys.size());
    }
    
    @Test
    void listShouldReturnEmptyIteratorWhenEmptyRepository(TestSystem testSystem) throws UsersRepositoryException {
        //When
        Iterator<Username> actual = testee().list();
        //Then
        assertThat(actual)
            .toIterable()
            .isEmpty();
    }
    
    @Test
    void listShouldReturnExactlyUsersInRepository(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        ArrayList<Username> keys = new ArrayList<>(3);
        keys.add(testSystem.user1);
        keys.add(testSystem.user2);
        keys.add(testSystem.user3);
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
    void addUserShouldAddAUserWhenEmptyRepository(TestSystem testSystem) throws UsersRepositoryException {
        //When
        testee().addUser(testSystem.user2, "password2");
        //Then
        assertThat(testee().contains(testSystem.user2)).isTrue();
    }

    @Test
    void containsShouldPreserveCaseVariation(TestSystem testSystem) throws UsersRepositoryException {
        testee().addUser(testSystem.user1CaseVariation, "password2");

        assertThat(testee().contains(testSystem.user1CaseVariation)).isTrue();
    }

    @Test
    void containsShouldBeCaseInsentive(TestSystem testSystem) throws UsersRepositoryException {
        testee().addUser(testSystem.user1CaseVariation, "password2");

        assertThat(testee().contains(testSystem.user1)).isTrue();
    }

    @Test
    void containsShouldBeCaseInsentiveWhenOriginalValueLowerCased(TestSystem testSystem) throws UsersRepositoryException {
        testee().addUser(testSystem.user1, "password2");

        assertThat(testee().contains(testSystem.user1CaseVariation)).isTrue();
    }

    @Test
    void addUserShouldDisableCaseVariationWhenOriginalValueLowerCased(TestSystem testSystem) throws UsersRepositoryException {
        testee().addUser(testSystem.user1, "password2");

        assertThatThrownBy(() -> testee().addUser(testSystem.user1CaseVariation, "pass"))
            .isInstanceOf(UsersRepositoryException.class);
    }

    @Test
    void addUserShouldDisableCaseVariation(TestSystem testSystem) throws UsersRepositoryException {
        testee().addUser(testSystem.user1CaseVariation, "password2");

        assertThatThrownBy(() -> testee().addUser(testSystem.user1, "pass"))
            .isInstanceOf(UsersRepositoryException.class);
    }

    @Test
    void listShouldReturnLowerCaseUser(TestSystem testSystem) throws UsersRepositoryException {
        testee().addUser(testSystem.user1CaseVariation, "password2");

        assertThat(testee().list())
            .toIterable()
            .containsExactly(testSystem.user1);
    }

    @Test
    void removeUserShouldBeCaseInsentiveOnCaseVariationUser(TestSystem testSystem) throws UsersRepositoryException {
        testee().addUser(testSystem.user1CaseVariation, "password2");

        testee().removeUser(testSystem.user1);

        assertThat(testee().list())
            .toIterable()
            .isEmpty();
    }

    @Test
    void removeUserShouldBeCaseInsentive(TestSystem testSystem) throws UsersRepositoryException {
        testee().addUser(testSystem.user1, "password2");

        testee().removeUser(testSystem.user1CaseVariation);

        assertThat(testee().list())
            .toIterable()
            .isEmpty();
    }

    @Test
    void getUserByNameShouldBeCaseInsentive(TestSystem testSystem) throws UsersRepositoryException {
        testee().addUser(testSystem.user1, "password2");

        assertThat(testee().getUserByName(testSystem.user1CaseVariation).getUserName())
            .isEqualTo(testSystem.user1);
    }

    @Test
    void getUserByNameShouldReturnLowerCaseAddedUser(TestSystem testSystem) throws UsersRepositoryException {
        testee().addUser(testSystem.user1CaseVariation, "password2");

        assertThat(testee().getUserByName(testSystem.user1).getUserName())
            .isEqualTo(testSystem.user1);
    }

    @Test
    void getUserShouldBeCaseInsentive(TestSystem testSystem) throws Exception {
        assertThat(testee().getUsername(testSystem.user1CaseVariation.asMailAddress()))
            .isEqualTo(testSystem.user1);
    }

    @Test
    void isAdministratorShouldBeCaseInsentive(TestSystem testSystem) throws Exception {
        testee().setAdministratorId(Optional.of(testSystem.admin));
        assertThat(testee().isAdministrator(testSystem.adminCaseVariation))
            .isTrue();
    }

    @Test
    void testShouldBeCaseInsentiveOnCaseVariationUser(TestSystem testSystem) throws UsersRepositoryException {
        String password = "password2";
        testee().addUser(testSystem.user1CaseVariation, password);

        assertThat(testee().test(testSystem.user1, password))
            .isTrue();
    }

    @Test
    void testShouldBeCaseInsentive(TestSystem testSystem) throws UsersRepositoryException {
        String password = "password2";
        testee().addUser(testSystem.user1, password);

        assertThat(testee().test(testSystem.user1CaseVariation, password))
            .isTrue();
    }
    
    @Test 
    void addUserShouldAddAUserWhenNotEmptyRepository(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        testee().addUser(testSystem.user2, "password2");
        //When
        testee().addUser(testSystem.user3, "password3");
        //Then
        assertThat(testee().contains(testSystem.user3)).isTrue();
    }
    
    @Test
    void addUserShouldThrowWhenSameUsernameWithDifferentCase(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        testee().addUser(testSystem.toUsername("myUsername"), "password");
        //When
        assertThatThrownBy(() -> testee().addUser(testSystem.toUsername("MyUsername"), "password"))
            .isInstanceOf(AlreadyExistInUsersRepositoryException.class);
    }
    
    @Test
    void addUserShouldThrowWhenUserAlreadyPresentInRepository(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        testee().addUser(testSystem.user1, "password");
        //When
        assertThatThrownBy(() -> testee().addUser(testSystem.user1, "password2"))
            .isInstanceOf(AlreadyExistInUsersRepositoryException.class);
    }
    
    @Test
    void getUserByNameShouldReturnAUserWhenContainedInRepository(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        testee().addUser(testSystem.user1, "password");
        //When
        User actual = testee().getUserByName(testSystem.user1);
        //Then
        assertThat(actual).isNotNull();
        assertThat(actual.getUserName()).isEqualTo(testSystem.user1);
    }

    @Test
    void getUserByNameShouldReturnUserWhenDifferentCase(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        testee().addUser(testSystem.toUsername("username"), "password");
        //When
        User actual = testee().getUserByName(testSystem.toUsername("uSERNAMe"));
        //Then
        assertThat(actual).isNotNull();
        assertThat(actual.getUserName()).isEqualTo(testSystem.user1);
    }
   
    @Test
    void testShouldReturnTrueWhenAUserHasACorrectPassword(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        testee().addUser(testSystem.user1, "password");
        //When
        boolean actual = testee().test(testSystem.user1, "password");
        //Then
        assertThat(actual).isTrue();
    }
    
    @Test
    void testShouldReturnTrueWhenAUserHasACorrectPasswordAndOtherCaseInDomain(TestSystem testSystem) throws Exception {
        testee().setEnableVirtualHosting(true);

        testSystem.domainList.addDomain(Domain.of("jAmEs.oRg"));
        String username = "myuser";
        String password = "password";
        testee().addUser(Username.of(username + "@jAmEs.oRg"), password);

        boolean actual = testee().test(Username.of(username + "@james.org"), password);

        assertThat(actual).isTrue();
    }

    @Test
    void testShouldReturnFalseWhenAUserHasAnIncorrectPassword(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        testee().addUser(testSystem.user1, "password");
        //When
        boolean actual = testee().test(testSystem.user1, "password2");
        //Then
        assertThat(actual).isFalse();
    }
    
    @Test
    void testShouldReturnFalseWhenAUserHasAnIncorrectCasePassword(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        testee().addUser(testSystem.user1, "password");
        //When
        boolean actual = testee().test(testSystem.user1, "Password");
        //Then
        assertThat(actual).isFalse();
    }
    
    @Test
    void testShouldReturnFalseWhenAUserIsNotInRepository(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        testee().addUser(testSystem.toUsername("username"), "password");
        //When
        boolean actual = testee().test(testSystem.toUsername("username2"), "password");
        //Then
        assertThat(actual).isFalse();
    }

    @Test
    void testShouldReturnTrueWhenAUserHasAnIncorrectCaseName(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        testee().addUser(testSystem.toUsername("username"), "password");
        //When
        boolean actual = testee().test(testSystem.toUsername("userName"), "password");
        //Then
        assertThat(actual).isTrue();
    }

    @Test
    void testShouldReturnFalseWhenEmptyRepository(TestSystem testSystem) throws UsersRepositoryException {
        //When
        boolean actual = testee().test(testSystem.user1, "password");
        //Then
        assertThat(actual).isFalse();
    }

    @Test
    void testShouldReturnFalseWhenAUserIsRemovedFromRepository(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        testee().addUser(testSystem.user1, "password");
        testee().removeUser(testSystem.user1);
        //When
        boolean actual = testee().test(testSystem.user1, "password");
        //Then
        assertThat(actual).isFalse();
    }
    
    @Test
    void removeUserShouldRemoveAUserWhenPresentInRepository(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        testee().addUser(testSystem.user1, "password");
        //When
        testee().removeUser(testSystem.user1);
        //Then
        assertThat(testee().contains(testSystem.user1)).isFalse();
    }
    
    @Test
    void removeUserShouldThrowWhenUserNotInRepository(TestSystem testSystem) {
        //When
        assertThatThrownBy(() -> testee().removeUser(testSystem.user1))
            .isInstanceOf(UsersRepositoryException.class);
    }
    
    @Test
    void updateUserShouldAllowToAuthenticateWithNewPassword(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        testee().addUser(testSystem.user1, "password");
        User user = testee().getUserByName(testSystem.user1);
        user.setPassword("newpass");
        //When
        testee().updateUser(user);
        //Then
        assertThat(testee().test(testSystem.user1, "newpass")).isTrue();
    }
   
    @Test
    void updateUserShouldNotAllowToAuthenticateWithOldPassword(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        testee().addUser(testSystem.user1, "password");
        User user = testee().getUserByName(testSystem.user1);
        user.setPassword("newpass");
        //When
        testee().updateUser(user);
        //Then
        assertThat(testee().test(testSystem.user1, "password")).isFalse();
    }
    
    @Test
    void updateUserShouldThrowWhenAUserIsNoMoreInRepository(TestSystem testSystem) throws UsersRepositoryException {
        //Given
        testee().addUser(testSystem.user1, "password");
        User user = testee().getUserByName(testSystem.user1);
        testee().removeUser(testSystem.user1);
        //When
        assertThatThrownBy(() -> testee().updateUser(user))
            .isInstanceOf(UsersRepositoryException.class);
    }

    @Test
    void virtualHostedUsersRepositoryShouldUseFullMailAddressAsUsername() throws Exception {
        testee().setEnableVirtualHosting(true);

        // Some implementations do not support changing virtual hosting value
        Assumptions.assumeTrue(testee().supportVirtualHosting());

        assertThat(testee().getUsername(new MailAddress("local@domain"))).isEqualTo(Username.of("local@domain"));
    }

    @Test
    void nonVirtualHostedUsersRepositoryShouldUseLocalPartAsUsername() throws Exception {
        testee().setEnableVirtualHosting(false);

        // Some implementations do not support changing virtual hosting value
        Assumptions.assumeFalse(testee().supportVirtualHosting());

        assertThat(testee().getUsername(new MailAddress("local@domain"))).isEqualTo(Username.of("local"));
    }

    @Test
    void isAdministratorShouldReturnFalseWhenNotConfigured(TestSystem testSystem) throws Exception {
        testee().setAdministratorId(Optional.empty());

        assertThat(testee().isAdministrator(testSystem.admin)).isFalse();
    }

    @Test
    void isAdministratorShouldReturnTrueWhenConfiguredAndUserIsAdmin(TestSystem testSystem) throws Exception {
        testee().setAdministratorId(Optional.of(testSystem.admin));

        assertThat(testee().isAdministrator(testSystem.admin)).isTrue();
    }

    @Test
    void isAdministratorShouldReturnFalseWhenConfiguredAndUserIsNotAdmin(TestSystem testSystem) throws Exception {
        testee().setAdministratorId(Optional.of(testSystem.admin));

        assertThat(testee().isAdministrator(testSystem.user1)).isFalse();
    }

    @Test
    void getMailAddressForShouldBeIdentityWhenVirtualHosting() throws Exception {
        testee().setEnableVirtualHosting(true);

        // Some implementations do not support changing virtual hosting value
        Assumptions.assumeTrue(testee().supportVirtualHosting());

        String username = "user@domain";
        assertThat(testee().getMailAddressFor(Username.of(username)))
            .isEqualTo(username);
    }

    @Test
    void getMailAddressForShouldAppendDefaultDomainWhenNoVirtualHosting(TestSystem testSystem) throws Exception {
        testee().setEnableVirtualHosting(false);

        // Some implementations do not support changing virtual hosting value
        Assumptions.assumeFalse(testee().supportVirtualHosting());

        String username = "user";
        assertThat(testee().getMailAddressFor(Username.of(username)))
            .isEqualTo(new MailAddress(username, testSystem.domainList.getDefaultDomain()));
    }

    @ParameterizedTest
    @MethodSource("illegalCharacters")
    void assertValidShouldThrowWhenUsernameLocalPartWithIllegalCharacter(String illegalCharacter) {
        assertThatThrownBy(() -> testee().assertValid(Username.of("a" + illegalCharacter + "a")))
            .isInstanceOf(InvalidUsernameException.class);
    }

    private static Stream<Arguments> illegalCharacters() {
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
