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

package org.apache.james.user.api;

import static helpers.TrimSuffixOfPlusSign.trimSuffixOfPlusSign;

import java.util.Iterator;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.user.api.model.User;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;

/**
 * Interface for a repository of users. A repository represents a logical
 * grouping of users, typically by common purpose. E.g. the users served by an
 * email server or the members of a mailing list.
 */
public interface UsersRepository {

    /**
     * Adds a user to the repository with the specified password
     * 
     * @param username
     *            the username of the user to be added
     * @param password
     *            the password of the user to add
     * @throws UsersRepositoryException
     *             if error
     * 
     */
    void addUser(Username username, String password) throws UsersRepositoryException;

    /**
     * Get the user object with the specified user name. Return null if no such
     * user.
     * 
     * @param name
     *            the name of the user to retrieve
     * @return the user being retrieved, null if the user doesn't exist
     * @throws UsersRepositoryException
     *             if error
     */
    User getUserByName(Username name) throws UsersRepositoryException;

    /**
     * Update the repository with the specified user object. A user object with
     * this username must already exist.
     * 
     * @throws UsersRepositoryException
     *             if error
     */
    void updateUser(User user) throws UsersRepositoryException;

    /**
     * Removes a user from the repository
     * 
     * @param name
     *            the user to remove from the repository
     * @throws UsersRepositoryException
     *             if error
     */
    void removeUser(Username name) throws UsersRepositoryException;

    /**
     * Returns whether or not this user is in the repository
     * 
     * @param name
     *            the name to check in the repository
     * @return whether the user is in the repository
     * @throws UsersRepositoryException
     *             if error
     */
    boolean contains(Username name) throws UsersRepositoryException;

    Publisher<Boolean> containsReactive(Username name);

    /**
     * Test if user with login name 'name' has password 'password'.
     * 
     * @param name
     *            the login name of the user to be tested
     * @param password
     *            the password to be tested
     * 
     * @return Optional of a Username if the test is successful, an empty Optional if the user doesn't exist
     *         or if the password is incorrect
     * @throws UsersRepositoryException
     *             if error
     * 
     */
    Optional<Username> test(Username name, String password) throws UsersRepositoryException;

    /**
     * Returns a count of the users in the repository.
     * 
     * @return the number of users in the repository
     * @throws UsersRepositoryException
     *             if error
     */
    int countUsers() throws UsersRepositoryException;

    /**
     * List users in repository.
     * 
     * @return Iterator over a collection of Strings, each being one user in the
     *         repository.
     * @throws UsersRepositoryException
     *             if error
     */
    Iterator<Username> list() throws UsersRepositoryException;

    Publisher<Username> listReactive();

    /**
     * Return true if virtualHosting support is enabled, otherwise false
     * 
     * @return true or false
     */
    boolean supportVirtualHosting() throws UsersRepositoryException;

    /**
     * Returns username to be used for a given MailAddress
     *
     * @return Username used by James for this mailAddress
     */
    default Username getUsername(MailAddress mailAddress) throws UsersRepositoryException {
        if (supportVirtualHosting()) {
            return Username.of(trimSuffixOfPlusSign(mailAddress).asString());
        } else {
            return Username.of(trimSuffixOfPlusSign(mailAddress).getLocalPart());
        }
    }

    /**
     * Returns one of the possible mail addresses to be used to send a mail to that user
     *
     * This makes sense as it handles virtual-hosting logic.
     */
    MailAddress getMailAddressFor(Username username) throws UsersRepositoryException;
    
    /**
     * Return true if the user is an admin for this repository
     */
    boolean isAdministrator(Username username) throws UsersRepositoryException;

    /**
     * @return true if one can use {@link UsersRepository#updateUser(User)} {@link UsersRepository#addUser(Username, String)}
     *             {@link UsersRepository#removeUser(Username)} and false overwhise
     */
    boolean isReadOnly();

    default void assertValid(Username username) throws UsersRepositoryException {
        if (username.getDomainPart().isPresent() != supportVirtualHosting()) {
            throw new UsersRepositoryException(username.asString() + " username candidate do not match the virtualHosting strategy");
        }
    }

    default Publisher<Username> listUsersOfADomainReactive(Domain domain) {
        return Flux.from(listReactive())
            .filter(username -> username.getDomainPart()
                .map(domain::equals)
                .orElse(false));
    }
}
