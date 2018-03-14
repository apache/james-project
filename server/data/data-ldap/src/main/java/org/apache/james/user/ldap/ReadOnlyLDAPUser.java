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

import java.io.Serializable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;

import org.apache.james.user.api.model.User;
import org.apache.james.user.ldap.api.LdapConstants;

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
 * @see SimpleLDAPConnection
 * @see ReadOnlyUsersLDAPRepository
 * 
 */
public class ReadOnlyLDAPUser implements User, Serializable {
    // private static final long serialVersionUID = -6712066073820393235L; 
    private static final long serialVersionUID = -5201235065842464013L;

    /**
     * The user's identifier or name. This is the value that is returned by the
     * method {@link User#getUserName()}. It is also from this value that the
     * user's email address is formed, so for example: if the value of this
     * field is <code>&quot;john.bold&quot;</code>, and the domain is
     * <code>&quot;myorg.com&quot;</code>, the user's email address will be
     * <code>&quot;john.bold&#64;myorg.com&quot;</code>.
     */
    private String userName;

    /**
     * The distinguished name of the user-record in the LDAP directory.
     */
    private String userDN;

    /**
     * The context for the LDAP server from which to retrieve the
     * user's details.
     */
    private LdapContext ldapContext = null;

    /**
     * Creates a new instance of ReadOnlyLDAPUser.
     *
     */
    private ReadOnlyLDAPUser() {
        super();
    }

    /**
     * Constructs an instance for the given user-details, and which will
     * authenticate against the given host.
     * 
     * @param userName
     *            The user-identifier/name. This is the value with which the
     *            field  will be initialised, and which will be
     *            returned by invoking {@link #getUserName()}.
     * @param userDN
     *            The distinguished (unique-key) of the user details as stored
     *            on the LDAP directory.
     * @param ldapContext
     *            The context for the LDAP server on which the user details are held.
     *            This is also the host against which the user will be
     *            authenticated, when {@link #verifyPassword(String)} is
     *            invoked.
     * @throws NamingException 
     */
    public ReadOnlyLDAPUser(String userName, String userDN, LdapContext ldapContext) {
        this();
        this.userName = userName;
        this.userDN = userDN;
        this.ldapContext = ldapContext;
    }

    /**
     * Fulfils the contract {@link User#getUserName()}. It returns the value of
     * the field . This is generally the value from which the
     * user email address is built, by appending the domain name to it.
     * 
     * @return The user's identifier or name.
     */
    @Override
    public String getUserName() {
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
        boolean result = false;
        LdapContext ldapContext = null;
        try {
            ldapContext = this.ldapContext.newInstance(null);
            ldapContext.addToEnvironment(Context.SECURITY_AUTHENTICATION,
                    LdapConstants.SECURITY_AUTHENTICATION_SIMPLE);
            ldapContext.addToEnvironment(Context.SECURITY_PRINCIPAL, userDN);
            ldapContext.addToEnvironment(Context.SECURITY_CREDENTIALS, password);
            ldapContext.reconnect(null);
            result = true;
        } catch (NamingException exception) {
            // no-op
        } finally {
            if (null != ldapContext) {
                try {
                    ldapContext.close();
                } catch (NamingException ex) {
                    // no-op
                }
            }
        }
        return result;
    }
}
