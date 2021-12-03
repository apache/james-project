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

import org.apache.james.core.Username;
import org.apache.james.user.api.model.User;

/**
 * Implementation of User Interface. Instances of this class do not allow the
 * the user name to be reset.
 */
public class DefaultUser implements User, Serializable {

    private static final long serialVersionUID = 5178048915868531270L;

    private final Username userName;
    private String hashedPassword;
    private Algorithm currentAlgorithm;
    private final Algorithm preferredAlgorithm;

    /**
     * Standard constructor.
     * 
     * @param name
     *            the String name of this user
     * @param verifyAlg
     *            the algorithm used to verify the hash of the password
     * @param updateAlg
     *            the algorithm used to update the hash of the password
     */
    public DefaultUser(Username name, Algorithm verifyAlg, Algorithm updateAlg) {
        userName = name;
        currentAlgorithm = verifyAlg;
        preferredAlgorithm = updateAlg;
    }

    /**
     * Constructor for repositories that are construcing user objects from
     * separate fields, e.g. databases.
     * 
     * @param name
     *            the String name of this user
     * @param passwordHash
     *            the String hash of this users current password
     * @param verifyAlg
     *            the algorithm used to verify the hash of the password
     * @param updateAlg
     *            the algorithm used to update the hash of the password
     */
    public DefaultUser(Username name, String passwordHash, Algorithm verifyAlg, Algorithm updateAlg) {
        userName = name;
        hashedPassword = passwordHash;
        currentAlgorithm = verifyAlg;
        preferredAlgorithm = updateAlg;
    }

    @Override
    public Username getUserName() {
        return userName;
    }

    @Override
    public boolean verifyPassword(String pass) {
        String hashGuess = digestString(pass, currentAlgorithm, userName.asString());
        return hashedPassword.equals(hashGuess);
    }

    @Override
    public boolean setPassword(String newPass) {
        hashedPassword = digestString(newPass, preferredAlgorithm, userName.asString());
        currentAlgorithm = preferredAlgorithm;
        return true;
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
        return currentAlgorithm;
    }

    /**
     * Calculate digest of given String using given algorithm. Encode digest in
     * MIME-like base64.
     *
     * @param pass the String to be hashed
     * @param algorithm the algorithm to be used
     * @return String Base-64 encoding of digest
     */
    static String digestString(String pass, Algorithm algorithm, String salt) {
        return algorithm.digest(pass, salt);
    }
}
