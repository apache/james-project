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

import org.apache.james.core.Username;
import org.apache.james.user.api.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.LDAPBindException;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.ResultCode;

/**
 * Encapsulates the details of a user as taken from an LDAP compliant directory.
 * Instances of this class are only applicable to the
 * {@link ReadOnlyUsersLDAPRepository} or its subclasses. Consequently it does
 * not permit the mutation of user details. It is intended purely as an
 * encapsulation of the user information as held in the LDAP directory, and as a
 * means of authenticating the user against the LDAP server. Consequently
 * invocations of the contract method {@link User#setPassword(String)} always
 * returns <code>false</code>.
 *
 * @see ReadOnlyUsersLDAPRepository
 * 
 */
public class ReadOnlyLDAPUser implements User {
    public static final Logger LOGGER = LoggerFactory.getLogger(ReadOnlyLDAPUser.class);

    /**
     * The user's identifier or name. This is the value that is returned by the
     * method {@link User#getUserName()}. It is also from this value that the
     * user's email address is formed, so for example: if the value of this
     * field is <code>&quot;john.bold&quot;</code>, and the domain is
     * <code>&quot;myorg.com&quot;</code>, the user's email address will be
     * <code>&quot;john.bold&#64;myorg.com&quot;</code>.
     */
    private final Username userName;

    /**
     * The distinguished name of the user-record in the LDAP directory.
     */
    private final DN userDN;

    /**
     * The context for the LDAP server from which to retrieve the
     * user's details.
     */
    private final LDAPConnectionPool connectionPool;

    /**
     * Constructs an instance for the given user-details, and which will
     * authenticate against the given host.
     * @param userName
     *            The user-identifier/name. This is the value with which the
     *            field  will be initialised, and which will be
     *            returned by invoking {@link #getUserName()}.
     * @param userDN
     *            The distinguished (unique-key) of the user details as stored
     * @param connectionPool
     *            The connectionPool for the LDAP server on which the user details are held.
     *            This is also the host against which the user will be
     *            authenticated, when {@link #verifyPassword(String)} is
     *            invoked.
     */
    public ReadOnlyLDAPUser(Username userName, DN userDN, LDAPConnectionPool connectionPool) {
        this.userName = userName;
        this.userDN = userDN;
        this.connectionPool = connectionPool;
    }

    /**
     * Fulfils the contract {@link User#getUserName()}. It returns the value of
     * the field . This is generally the value from which the
     * user email address is built, by appending the domain name to it.
     * 
     * @return The user's identifier or translated username.
     */
    @Override
    public Username getUserName() {
        return userName;
    }

    /**
     * Implementation of contract {@link User#setPassword(String)}, which is
     * provided for compliance purposes only. Instances of this type mirror LDAP
     * data and do not perform any updates to the directory. Consequently, this
     * method always returns <code>false</code> and does not do any work.
     * 
     * @return <code>False</code>
     */
    @Override
    public boolean setPassword(String newPass) {
        return false;
    }

    /**
     * Verifies that the password supplied is actually the user's password, by
     * attempting to rebind to a copy of the LDAP server context using the user's 
     * username and the supplied password.
     * 
     * @param password
     *            The password to validate.
     * @return <code>True</code> if a connection can successfully be established
     *         to the LDAP host using the user's id and the supplied password,
     *         and <code>False</code> otherwise.
     */
    @Override
    public boolean verifyPassword(String password) {
        if (Strings.isNullOrEmpty(password)) {
            LOGGER.info("Error. Password is empty for {}", userName.asString());
            return false;
        }

        try {
            BindResult bindResult = connectionPool.bindAndRevertAuthentication(userDN.toString(), password);
            return bindResult.getResultCode() == ResultCode.SUCCESS;
        } catch (LDAPBindException e) {
            LOGGER.info("Error binding LDAP for {}: {}", userName.asString(), e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.error("Unexpected error upon authentication for {}", userName.asString(), e);
            return false;
        }
    }
}
