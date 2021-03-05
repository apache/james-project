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

package org.apache.james.user.lib.model;

import java.io.Serializable;
import java.security.NoSuchAlgorithmException;

import org.apache.james.core.Username;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.util.DigestUtil;

/**
 * Implementation of User Interface. Instances of this class do not allow the
 * the user name to be reset.
 */
public class DefaultUser implements User, Serializable {

    private static final long serialVersionUID = 5178048915868531270L;

    private final Username userName;
    private String hashedPassword;
    private final Algorithm algorithm;

    /**
     * Standard constructor.
     * 
     * @param name
     *            the String name of this user
     * @param hashAlg
     *            the algorithm used to generate the hash of the password
     */
    public DefaultUser(Username name, Algorithm hashAlg) {
        userName = name;
        algorithm = hashAlg;
    }

    /**
     * Constructor for repositories that are construcing user objects from
     * separate fields, e.g. databases.
     * 
     * @param name
     *            the String name of this user
     * @param passwordHash
     *            the String hash of this users current password
     * @param hashAlg
     *            the String algorithm used to generate the hash of the password
     */
    public DefaultUser(Username name, String passwordHash, Algorithm hashAlg) {
        userName = name;
        hashedPassword = passwordHash;
        algorithm = hashAlg;
    }

    @Override
    public Username getUserName() {
        return userName;
    }

    @Override
    public boolean verifyPassword(String pass) {
        try {
            String hashGuess = DigestUtil.digestString(pass, algorithm);
            return hashedPassword.equals(hashGuess);
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException("Security error: " + nsae);
        }
    }

    @Override
    public boolean setPassword(String newPass) {
        try {
            hashedPassword = DigestUtil.digestString(newPass, algorithm);
            return true;
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException("Security error: " + nsae);
        }
    }

    /**
     * Method to access hash of password
     * 
     * @return the String of the hashed Password
     */
    public String getHashedPassword() {
        return hashedPassword;
    }

    /**
     * Method to access the hashing algorithm of the password.
     * 
     * @return the name of the hashing algorithm used for this user's password
     */
    public Algorithm getHashAlgorithm() {
        return algorithm;
    }
}
