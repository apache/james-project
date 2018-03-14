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

package org.apache.james.user.jcr.model;

import org.apache.jackrabbit.util.Text;
import org.apache.james.user.api.model.User;

/**
 * User backed by JCR data. Differs from standard James by improved hash. TODO:
 * think about improving DefaultUser.
 */
public class JCRUser implements User {

    /**
     * Static salt for hashing password. Modifying this value will render all
     * passwords unrecognizable.
     */
    public static final String SALT = "JCRUsersRepository";

    /**
     * Hashes salted password.
     * 
     * @param username
     *            not null
     * @param password
     *            not null
     * @return not null
     */
    public static String hashPassword(String username, String password) {
        // Combine dynamic and static salt
        return Text.md5(Text.md5(username + password) + SALT);
    }

    private final String userName;
    private String hashedSaltedPassword;

    public JCRUser(String userName, String hashedSaltedPassword) {
        super();
        this.userName = userName;
        this.hashedSaltedPassword = hashedSaltedPassword;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    /**
     * Gets salted, hashed password.
     * 
     * @return the hashedSaltedPassword
     */
    public final String getHashedSaltedPassword() {
        return hashedSaltedPassword;
    }

    @Override
    public boolean setPassword(String newPass) {
        final boolean result;
        if (newPass == null) {
            result = false;
        } else {
            hashedSaltedPassword = hashPassword(userName, newPass);
            result = true;
        }
        return result;
    }

    @Override
    public boolean verifyPassword(String pass) {
        final boolean result;
        result = pass != null && hashedSaltedPassword.equals(hashPassword(userName, pass));
        return result;
    }
}
