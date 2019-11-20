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
import org.junit.Assume;
import org.junit.Test;


public abstract class AbstractUsersRepositoryTest {

    private static final Domain DOMAIN = Domain.of("domain");

    protected AbstractUsersRepository usersRepository;
    protected SimpleDomainList domainList;

    /**
     * Create the repository to be tested.
     *
     * @return the user repository
     * @throws Exception
     */
    protected abstract AbstractUsersRepository getUsersRepository() throws Exception;

    private Username user1;
    private Username user1CaseVariation;
    private Username user2;
    private Username user3;
    private Username admin;
    private Username adminCaseVariation;

    public void setUp() throws Exception {
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

    public void tearDown() throws Exception {
        disposeUsersRepository();
    }
    
    private Username login(String login) {
        if (usersRepository.supportVirtualHosting()) {
            return Username.of(login + '@' + DOMAIN.name());
        } else {
            return Username.of(login);
        }
    }
    
    @Test
    public void countUsersShouldReturnZeroWhenEmptyRepository() throws UsersRepositoryException {
        //Given
        int expected = 0;
        //When
        int actual = usersRepository.countUsers();
        //Then
        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    public void countUsersShouldReturnNumberOfUsersWhenNotEmptyRepository() throws UsersRepositoryException {
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
    public void listShouldReturnEmptyIteratorWhenEmptyRepository() throws UsersRepositoryException {
        //When
        Iterator<Username> actual = usersRepository.list();
        //Then
        assertThat(actual)
            .toIterable()
            .isEmpty();
    }
    
    @Test
    public void listShouldReturnExactlyUsersInRepository() throws UsersRepositoryException {
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
    public void addUserShouldAddAUserWhenEmptyRepository() throws UsersRepositoryException {
        //When
        usersRepository.addUser(user2, "password2");
        //Then
        assertThat(usersRepository.contains(user2)).isTrue();
    }

    @Test
    public void containsShouldPreserveCaseVariation() throws UsersRepositoryException {
        usersRepository.addUser(user1CaseVariation, "password2");

        assertThat(usersRepository.contains(user1CaseVariation)).isTrue();
    }

    @Test
    public void containsShouldBeCaseInsentive() throws UsersRepositoryException {
        usersRepository.addUser(user1CaseVariation, "password2");

        assertThat(usersRepository.contains(user1)).isTrue();
    }

    @Test
    public void containsShouldBeCaseInsentiveWhenOriginalValueLowerCased() throws UsersRepositoryException {
        usersRepository.addUser(user1, "password2");

        assertThat(usersRepository.contains(user1CaseVariation)).isTrue();
    }

    @Test
    public void addUserShouldDisableCaseVariationWhenOriginalValueLowerCased() throws UsersRepositoryException {
        usersRepository.addUser(user1, "password2");

        assertThatThrownBy(() -> usersRepository.addUser(user1CaseVariation, "pass"))
            .isInstanceOf(UsersRepositoryException.class);
    }

    @Test
    public void addUserShouldDisableCaseVariation() throws UsersRepositoryException {
        usersRepository.addUser(user1CaseVariation, "password2");

        assertThatThrownBy(() -> usersRepository.addUser(user1, "pass"))
            .isInstanceOf(UsersRepositoryException.class);
    }

    @Test
    public void listShouldReturnLowerCaseUser() throws UsersRepositoryException {
        usersRepository.addUser(user1CaseVariation, "password2");

        assertThat(usersRepository.list())
            .toIterable()
            .containsExactly(user1);
    }

    @Test
    public void removeUserShouldBeCaseInsentiveOnCaseVariationUser() throws UsersRepositoryException {
        usersRepository.addUser(user1CaseVariation, "password2");

        usersRepository.removeUser(user1);

        assertThat(usersRepository.list())
            .toIterable()
            .isEmpty();
    }

    @Test
    public void removeUserShouldBeCaseInsentive() throws UsersRepositoryException {
        usersRepository.addUser(user1, "password2");

        usersRepository.removeUser(user1CaseVariation);

        assertThat(usersRepository.list())
            .toIterable()
            .isEmpty();
    }

    @Test
    public void getUserByNameShouldBeCaseInsentive() throws UsersRepositoryException {
        usersRepository.addUser(user1, "password2");

        assertThat(usersRepository.getUserByName(user1CaseVariation).getUserName())
            .isEqualTo(user1);
    }

    @Test
    public void getUserByNameShouldReturnLowerCaseAddedUser() throws UsersRepositoryException {
        usersRepository.addUser(user1CaseVariation, "password2");

        assertThat(usersRepository.getUserByName(user1).getUserName())
            .isEqualTo(user1);
    }

    @Test
    public void getUserShouldBeCaseInsentive() throws Exception {
        assertThat(usersRepository.getUser(user1CaseVariation.asMailAddress()))
            .isEqualTo(user1);
    }

    @Test
    public void isAdministratorShouldBeCaseInsentive() throws Exception {
        usersRepository.setAdministratorId(Optional.of(admin));
        assertThat(usersRepository.isAdministrator(adminCaseVariation))
            .isTrue();
    }

    @Test
    public void testShouldBeCaseInsentiveOnCaseVariationUser() throws UsersRepositoryException {
        String password = "password2";
        usersRepository.addUser(user1CaseVariation, password);

        assertThat(usersRepository.test(user1, password))
            .isTrue();
    }

    @Test
    public void testShouldBeCaseInsentive() throws UsersRepositoryException {
        String password = "password2";
        usersRepository.addUser(user1, password);

        assertThat(usersRepository.test(user1CaseVariation, password))
            .isTrue();
    }
    
    @Test 
    public void addUserShouldAddAUserWhenNotEmptyRepository() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(user2, "password2");
        //When
        usersRepository.addUser(user3, "password3");
        //Then
        assertThat(usersRepository.contains(user3)).isTrue();
    }
    
    @Test(expected = AlreadyExistInUsersRepositoryException.class)
    public void addUserShouldThrowWhenSameUsernameWithDifferentCase() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(login("myUsername"), "password");
        //When
        usersRepository.addUser(login("MyUsername"), "password"); 
    }
    
    @Test(expected = AlreadyExistInUsersRepositoryException.class)
    public void addUserShouldThrowWhenUserAlreadyPresentInRepository() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(user1, "password");
        //When
        usersRepository.addUser(user1, "password2");
    }
    
    @Test
    public void getUserByNameShouldReturnAUserWhenContainedInRepository() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(user1, "password");
        //When
        User actual = usersRepository.getUserByName(user1);
        //Then
        assertThat(actual).isNotNull();
        assertThat(actual.getUserName()).isEqualTo(user1);
    }

    @Test
    public void getUserByNameShouldReturnUserWhenDifferentCase() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(login("username"), "password");
        //When
        User actual = usersRepository.getUserByName(login("uSERNAMe"));
        //Then
        assertThat(actual).isNotNull();
        assertThat(actual.getUserName()).isEqualTo(user1);
    }
   
    @Test
    public void testShouldReturnTrueWhenAUserHasACorrectPassword() throws UsersRepositoryException { 
        //Given
        usersRepository.addUser(user1, "password");
        //When
        boolean actual = usersRepository.test(user1, "password");
        //Then
        assertThat(actual).isTrue();
    }
    
    @Test
    public void testShouldReturnTrueWhenAUserHasACorrectPasswordAndOtherCaseInDomain() throws Exception { 
        usersRepository.setEnableVirtualHosting(true);

        domainList.addDomain(Domain.of("jAmEs.oRg"));
        String username = "myuser";
        String password = "password";
        usersRepository.addUser(Username.of(username + "@jAmEs.oRg"), password);

        boolean actual = usersRepository.test(Username.of(username + "@james.org"), password);

        assertThat(actual).isTrue();
    }

    @Test
    public void testShouldReturnFalseWhenAUserHasAnIncorrectPassword() throws UsersRepositoryException { 
        //Given
        usersRepository.addUser(user1, "password");
        //When
        boolean actual = usersRepository.test(user1, "password2");
        //Then
        assertThat(actual).isFalse();
    }
    
    @Test
    public void testShouldReturnFalseWhenAUserHasAnIncorrectCasePassword() throws UsersRepositoryException { 
        //Given
        usersRepository.addUser(user1, "password");
        //When
        boolean actual = usersRepository.test(user1, "Password");
        //Then
        assertThat(actual).isFalse();
    }
    
    @Test
    public void testShouldReturnFalseWhenAUserIsNotInRepository() throws UsersRepositoryException { 
        //Given
        usersRepository.addUser(login("username"), "password");
        //When
        boolean actual = usersRepository.test(login("username2"), "password"); 
        //Then
        assertThat(actual).isFalse();
    }

    @Test
    public void testShouldReturnTrueWhenAUserHasAnIncorrectCaseName() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(login("username"), "password");
        //When
        boolean actual = usersRepository.test(login("userName"), "password");
        //Then
        assertThat(actual).isTrue();
    }

    @Test
    public void testShouldReturnFalseWhenEmptyRepository() throws UsersRepositoryException {
        //When
        boolean actual = usersRepository.test(user1, "password");
        //Then
        assertThat(actual).isFalse();
    }

    @Test
    public void testShouldReturnFalseWhenAUserIsRemovedFromRepository() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(user1, "password");
        usersRepository.removeUser(user1);
        //When
        boolean actual = usersRepository.test(user1, "password");
        //Then
        assertThat(actual).isFalse();
    }
    
    @Test
    public void removeUserShouldRemoveAUserWhenPresentInRepository() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(user1, "password");
        //When
        usersRepository.removeUser(user1);
        //Then
        assertThat(usersRepository.contains(user1)).isFalse();
    }
    
    @Test(expected = UsersRepositoryException.class)
    public void removeUserShouldThrowWhenUserNotInRepository() throws UsersRepositoryException {
        //When
        usersRepository.removeUser(user1);
    }
    
    @Test
    public void updateUserShouldAllowToAuthenticateWithNewPassword() throws UsersRepositoryException { 
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
    public void updateUserShouldNotAllowToAuthenticateWithOldPassword() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(user1, "password");
        User user = usersRepository.getUserByName(user1);
        user.setPassword("newpass");
        //When
        usersRepository.updateUser(user);
        //Then
        assertThat(usersRepository.test(user1, "password")).isFalse();
    }
    
    @Test(expected = UsersRepositoryException.class)
    public void updateUserShouldThrowWhenAUserIsNoMoreInRepository() throws UsersRepositoryException {
        //Given
        usersRepository.addUser(user1, "password");
        User user = usersRepository.getUserByName(user1);
        usersRepository.removeUser(user1);
        //When
        usersRepository.updateUser(user);
    }

    @Test
    public void virtualHostedUsersRepositoryShouldUseFullMailAddressAsUsername() throws Exception {
        usersRepository.setEnableVirtualHosting(true);

        // Some implementations do not support changing virtual hosting value
        Assume.assumeTrue(usersRepository.supportVirtualHosting());

        assertThat(usersRepository.getUser(new MailAddress("local@domain"))).isEqualTo(Username.of("local@domain"));
    }

    @Test
    public void nonVirtualHostedUsersRepositoryShouldUseLocalPartAsUsername() throws Exception {
        usersRepository.setEnableVirtualHosting(false);

        // Some implementations do not support changing virtual hosting value
        Assume.assumeFalse(usersRepository.supportVirtualHosting());

        assertThat(usersRepository.getUser(new MailAddress("local@domain"))).isEqualTo(Username.of("local"));
    }

    protected void disposeUsersRepository() throws UsersRepositoryException {
        LifecycleUtil.dispose(this.usersRepository);
    }

    @Test
    public void isAdministratorShouldReturnFalseWhenNotConfigured() throws Exception {
        usersRepository.setAdministratorId(Optional.empty());

        assertThat(usersRepository.isAdministrator(admin)).isFalse();
    }

    @Test
    public void isAdministratorShouldReturnTrueWhenConfiguredAndUserIsAdmin() throws Exception {
        usersRepository.setAdministratorId(Optional.of(admin));

        assertThat(usersRepository.isAdministrator(admin)).isTrue();
    }

    @Test
    public void isAdministratorShouldReturnFalseWhenConfiguredAndUserIsNotAdmin() throws Exception {
        usersRepository.setAdministratorId(Optional.of(admin));

        assertThat(usersRepository.isAdministrator(user1)).isFalse();
    }

    @Test
    public void getMailAddressForShouldBeIdentityWhenVirtualHosting() throws Exception {
        usersRepository.setEnableVirtualHosting(true);

        // Some implementations do not support changing virtual hosting value
        Assume.assumeTrue(usersRepository.supportVirtualHosting());

        String username = "user@domain";
        assertThat(usersRepository.getMailAddressFor(Username.of(username)))
            .isEqualTo(username);
    }

    @Test
    public void getMailAddressForShouldAppendDefaultDomainWhenNoVirtualHosting() throws Exception {
        usersRepository.setEnableVirtualHosting(false);

        // Some implementations do not support changing virtual hosting value
        Assume.assumeFalse(usersRepository.supportVirtualHosting());

        String username = "user";
        assertThat(usersRepository.getMailAddressFor(Username.of(username)))
            .isEqualTo(new MailAddress(username, domainList.getDefaultDomain()));
    }
}
