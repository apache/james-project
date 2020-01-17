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

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;


public abstract class AbstractUsersRepositoryTest {

    static final Domain DOMAIN = Domain.of("domain");

    protected AbstractUsersRepository usersRepository;
    protected SimpleDomainList domainList;

    /**
     * Create the repository to be tested.
     *
     * @return the user repository
     * @throws Exception
     */
    protected abstract AbstractUsersRepository getUsersRepository() throws Exception;

    Username user1;
    Username user1CaseVariation;
    Username user2;
    Username user3;
    Username admin;
    Username adminCaseVariation;

    protected void setUp() throws Exception {
        domainList = new SimpleDomainList();
        domainList.addDomain(DOMAIN);
        this.usersRepository = getUsersRepository();
        user1 = login("username");
        user2 = login("username2");
        user3 = login("username3");
        user1CaseVariation = login("uSeRnaMe");
        admin = login("admin");
        adminCaseVariation = login("adMin");
    }

    protected void tearDown() throws Exception {
        LifecycleUtil.dispose(this.usersRepository);
    }
    
    private Username login(String login) {
        if (usersRepository.supportVirtualHosting()) {
            return Username.of(login + '@' + DOMAIN.name());
        } else {
            return Username.of(login);
        }
    }
    
    @Test
    void countUsersShouldReturnZeroWhenEmptyRepository() throws UsersRepositoryException {
        //Given
        int expected = 0;
        //When
        int actual = usersRepository.countUsers();
        //Then
        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    void countUsersShouldReturnNumberOfUsersWhenNotEmptyRepository() throws UsersRepositoryException {
        //Given
        ArrayList<Username> keys = new ArrayList<>(3);
        keys.add(user1);
        keys.add(user2);
        keys.add(user3);
        for (Username username : keys) {
            usersRepository.addUser(username, username.asString());
        }
        //When
        int actual = usersRepository.countUsers();
        //Then
        assertThat(actual).isEqualTo(keys.size());
    }
    
    @Test
    void listShouldReturnEmptyIteratorWhenEmptyRepository() throws UsersRepositoryException {
        //When
        Iterator<Username> actual = usersRepository.list();
        //Then
        assertThat(actual)
            .toIterable()
            .isEmpty();
    }
    
    @Test
    void listShouldReturnExactlyUsersInRepository() throws UsersRepositoryException {
        //Given
        ArrayList<Username> keys = new ArrayList<>(3);
        keys.add(user1);
        keys.add(user2);
        keys.add(user3);
        for (Username username : keys) {
            usersRepository.addUser(username, username.asString());
        }
        //When
        Iterator<Username> actual = usersRepository.list();
        //Then
        assertThat(actual)
            .toIterable()
            .containsOnly(user1, user2, user3);
    }
    
    @Test
    void addUserShouldAddAUserWhenEmptyRepository() throws UsersRepositoryException {
        //When
        usersRepository.addUser(user2, "password2");
        //Then
        assertThat(usersRepository.contains(user2)).isTrue();
    }

    @Test
    void containsShouldPreserveCaseVariation() throws UsersRepositoryException {
        usersRepository.addUser(user1CaseVariation, "password2");

        assertThat(usersRepository.contains(user1CaseVariation)).isTrue();
    }

    @Test
    void containsShouldBeCaseInsentive() throws UsersRepositoryException {
        usersRepository.addUser(user1CaseVariation, "password2");

        assertThat(usersRepository.contains(user1)).isTrue();
    }

    @Test
    void containsShouldBeCaseInsentiveWhenOriginalValueLowerCased() throws UsersRepositoryException {
        usersRepository.addUser(user1, "password2");

        assertThat(usersRepository.contains(user1CaseVariation)).isTrue();
    }

    @Test
    void addUserShouldDisableCaseVariationWhenOriginalValueLowerCased() throws UsersRepositoryException {
        usersRepository.addUser(user1, "password2");

        assertThatThrownBy(() -> usersRepository.addUser(user1CaseVariation, "pass"))
            .isInstanceOf(UsersRepositoryException.class);
    }

    @Test
    void addUserShouldDisableCaseVariation() throws UsersRepositoryException {
        usersRepository.addUser(user1CaseVariation, "password2");

        assertThatThrownBy(() -> usersRepository.addUser(user1, "pass"))
            .isInstanceOf(UsersRepositoryException.class);
    }

    @Test
    void listShouldReturnLowerCaseUser() throws UsersRepositoryException {
        usersRepository.addUser(user1CaseVariation, "password2");

        assertThat(usersRepository.list())
            .toIterable()
            .containsExactly(user1);
    }

    @Test
    void removeUserShouldBeCaseInsentiveOnCaseVariationUser() throws UsersRepositoryException {
        usersRepository.addUser(user1CaseVariation, "password2");

        usersRepository.removeUser(user1);

        assertThat(usersRepository.list())
            .toIterable()
            .isEmpty();
    }

    @Test
    void removeUserShouldBeCaseInsentive() throws UsersRepositoryException {
        usersRepository.addUser(user1, "password2");

        usersRepository.removeUser(user1CaseVariation);

        assertThat(usersRepository.list())
            .toIterable()
            .isEmpty();
    }

    @Test
    void getUserByNameShouldBeCaseInsentive() throws UsersRepositoryException {
        usersRepository.addUser(user1, "password2");

        assertThat(usersRepository.getUserByName(user1CaseVariation).getUserName())
            .isEqualTo(user1);
    }

    @Test
    void getUserByNameShouldReturnLowerCaseAddedUser() throws UsersRepositoryException {
        usersRepository.addUser(user1CaseVariation, "password2");

        assertThat(usersRepository.getUserByName(user1).getUserName())
            .isEqualTo(user1);
    }

    @Test
    void getUserShouldBeCaseInsentive() throws Exception {
        assertThat(usersRepository.getUser(user1CaseVariation.asMailAddress()))
            .isEqualTo(user1);
    }

    @Test
    void isAdministratorShouldBeCaseInsentive() throws Exception {
        usersRepository.setAdministratorId(Optional.of(admin));
        assertThat(usersRepository.isAdministrator(adminCaseVariation))
            .isTrue();
    }

    @Test
    void testShouldBeCaseInsentiveOnCaseVariationUser() throws UsersRepositoryException {
        String password = "password2";
        usersRepository.addUser(user1CaseVariation, password);

        assertThat(usersRepository.test(user1, password))
            .isTrue();
    }

    @Test
    void testShouldBeCaseInsentive() throws UsersRepositoryException {
        String password = "password2";
        usersRepository.addUser(user1, password);

        assertThat(usersRepository.test(user1CaseVariation, password))
            .isTrue();
    }
    
    @Test 
    void addUserShouldAddAUserWhenNotEmptyRepository() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(user2, "password2");
        //When
        usersRepository.addUser(user3, "password3");
        //Then
        assertThat(usersRepository.contains(user3)).isTrue();
    }
    
    @Test
    void addUserShouldThrowWhenSameUsernameWithDifferentCase() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(login("myUsername"), "password");
        //When
        assertThatThrownBy(() -> usersRepository.addUser(login("MyUsername"), "password"))
            .isInstanceOf(AlreadyExistInUsersRepositoryException.class);
    }
    
    @Test
    void addUserShouldThrowWhenUserAlreadyPresentInRepository() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(user1, "password");
        //When
        assertThatThrownBy(() -> usersRepository.addUser(user1, "password2"))
            .isInstanceOf(AlreadyExistInUsersRepositoryException.class);
    }
    
    @Test
    void getUserByNameShouldReturnAUserWhenContainedInRepository() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(user1, "password");
        //When
        User actual = usersRepository.getUserByName(user1);
        //Then
        assertThat(actual).isNotNull();
        assertThat(actual.getUserName()).isEqualTo(user1);
    }

    @Test
    void getUserByNameShouldReturnUserWhenDifferentCase() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(login("username"), "password");
        //When
        User actual = usersRepository.getUserByName(login("uSERNAMe"));
        //Then
        assertThat(actual).isNotNull();
        assertThat(actual.getUserName()).isEqualTo(user1);
    }
   
    @Test
    void testShouldReturnTrueWhenAUserHasACorrectPassword() throws UsersRepositoryException { 
        //Given
        usersRepository.addUser(user1, "password");
        //When
        boolean actual = usersRepository.test(user1, "password");
        //Then
        assertThat(actual).isTrue();
    }
    
    @Test
    void testShouldReturnTrueWhenAUserHasACorrectPasswordAndOtherCaseInDomain() throws Exception { 
        usersRepository.setEnableVirtualHosting(true);

        domainList.addDomain(Domain.of("jAmEs.oRg"));
        String username = "myuser";
        String password = "password";
        usersRepository.addUser(Username.of(username + "@jAmEs.oRg"), password);

        boolean actual = usersRepository.test(Username.of(username + "@james.org"), password);

        assertThat(actual).isTrue();
    }

    @Test
    void testShouldReturnFalseWhenAUserHasAnIncorrectPassword() throws UsersRepositoryException { 
        //Given
        usersRepository.addUser(user1, "password");
        //When
        boolean actual = usersRepository.test(user1, "password2");
        //Then
        assertThat(actual).isFalse();
    }
    
    @Test
    void testShouldReturnFalseWhenAUserHasAnIncorrectCasePassword() throws UsersRepositoryException { 
        //Given
        usersRepository.addUser(user1, "password");
        //When
        boolean actual = usersRepository.test(user1, "Password");
        //Then
        assertThat(actual).isFalse();
    }
    
    @Test
    void testShouldReturnFalseWhenAUserIsNotInRepository() throws UsersRepositoryException { 
        //Given
        usersRepository.addUser(login("username"), "password");
        //When
        boolean actual = usersRepository.test(login("username2"), "password"); 
        //Then
        assertThat(actual).isFalse();
    }

    @Test
    void testShouldReturnTrueWhenAUserHasAnIncorrectCaseName() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(login("username"), "password");
        //When
        boolean actual = usersRepository.test(login("userName"), "password");
        //Then
        assertThat(actual).isTrue();
    }

    @Test
    void testShouldReturnFalseWhenEmptyRepository() throws UsersRepositoryException {
        //When
        boolean actual = usersRepository.test(user1, "password");
        //Then
        assertThat(actual).isFalse();
    }

    @Test
    void testShouldReturnFalseWhenAUserIsRemovedFromRepository() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(user1, "password");
        usersRepository.removeUser(user1);
        //When
        boolean actual = usersRepository.test(user1, "password");
        //Then
        assertThat(actual).isFalse();
    }
    
    @Test
    void removeUserShouldRemoveAUserWhenPresentInRepository() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(user1, "password");
        //When
        usersRepository.removeUser(user1);
        //Then
        assertThat(usersRepository.contains(user1)).isFalse();
    }
    
    @Test
    void removeUserShouldThrowWhenUserNotInRepository() {
        //When
        assertThatThrownBy(() -> usersRepository.removeUser(user1))
            .isInstanceOf(UsersRepositoryException.class);
    }
    
    @Test
    void updateUserShouldAllowToAuthenticateWithNewPassword() throws UsersRepositoryException { 
        //Given
        usersRepository.addUser(user1, "password");
        User user = usersRepository.getUserByName(user1);
        user.setPassword("newpass");
        //When
        usersRepository.updateUser(user);
        //Then
        assertThat(usersRepository.test(user1, "newpass")).isTrue();
    }
   
    @Test
    void updateUserShouldNotAllowToAuthenticateWithOldPassword() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(user1, "password");
        User user = usersRepository.getUserByName(user1);
        user.setPassword("newpass");
        //When
        usersRepository.updateUser(user);
        //Then
        assertThat(usersRepository.test(user1, "password")).isFalse();
    }
    
    @Test
    void updateUserShouldThrowWhenAUserIsNoMoreInRepository() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(user1, "password");
        User user = usersRepository.getUserByName(user1);
        usersRepository.removeUser(user1);
        //When
        assertThatThrownBy(() -> usersRepository.updateUser(user))
            .isInstanceOf(UsersRepositoryException.class);
    }

    @Test
    void virtualHostedUsersRepositoryShouldUseFullMailAddressAsUsername() throws Exception {
        usersRepository.setEnableVirtualHosting(true);

        // Some implementations do not support changing virtual hosting value
        Assumptions.assumeTrue(usersRepository.supportVirtualHosting());

        assertThat(usersRepository.getUser(new MailAddress("local@domain"))).isEqualTo(Username.of("local@domain"));
    }

    @Test
    void nonVirtualHostedUsersRepositoryShouldUseLocalPartAsUsername() throws Exception {
        usersRepository.setEnableVirtualHosting(false);

        // Some implementations do not support changing virtual hosting value
        Assumptions.assumeFalse(usersRepository.supportVirtualHosting());

        assertThat(usersRepository.getUser(new MailAddress("local@domain"))).isEqualTo(Username.of("local"));
    }

    @Test
    void isAdministratorShouldReturnFalseWhenNotConfigured() throws Exception {
        usersRepository.setAdministratorId(Optional.empty());

        assertThat(usersRepository.isAdministrator(admin)).isFalse();
    }

    @Test
    void isAdministratorShouldReturnTrueWhenConfiguredAndUserIsAdmin() throws Exception {
        usersRepository.setAdministratorId(Optional.of(admin));

        assertThat(usersRepository.isAdministrator(admin)).isTrue();
    }

    @Test
    void isAdministratorShouldReturnFalseWhenConfiguredAndUserIsNotAdmin() throws Exception {
        usersRepository.setAdministratorId(Optional.of(admin));

        assertThat(usersRepository.isAdministrator(user1)).isFalse();
    }

    @Test
    void getMailAddressForShouldBeIdentityWhenVirtualHosting() throws Exception {
        usersRepository.setEnableVirtualHosting(true);

        // Some implementations do not support changing virtual hosting value
        Assumptions.assumeTrue(usersRepository.supportVirtualHosting());

        String username = "user@domain";
        assertThat(usersRepository.getMailAddressFor(Username.of(username)))
            .isEqualTo(username);
    }

    @Test
    void getMailAddressForShouldAppendDefaultDomainWhenNoVirtualHosting() throws Exception {
        usersRepository.setEnableVirtualHosting(false);

        // Some implementations do not support changing virtual hosting value
        Assumptions.assumeFalse(usersRepository.supportVirtualHosting());

        String username = "user";
        assertThat(usersRepository.getMailAddressFor(Username.of(username)))
            .isEqualTo(new MailAddress(username, domainList.getDefaultDomain()));
    }
}
