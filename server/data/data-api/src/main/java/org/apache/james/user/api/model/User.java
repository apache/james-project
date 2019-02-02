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

package org.apache.james.user.api.model;

/**
 * Interface for objects representing users.
 */
public interface User {

    /**
     * Return the user name of this user
     * 
     * @return the user name for this user
     */
    String getUserName();

    /**
     * Return true if pass matches password of this user.
     * 
     * @param pass
     *            the password to test
     * @return whether the password being tested is valid
     */
    boolean verifyPassword(String pass);

    /**
     * Sets new password from String. No checks made on guessability of
     * password.
     * 
     * @param newPass
     *            the String that is the new password.
     * @return true if newPass successfully added
     */
    boolean setPassword(String newPass);

    default boolean hasUsername(String username) {
        org.apache.james.core.User thisUser = org.apache.james.core.User.fromUsername(getUserName());
        org.apache.james.core.User thatUser = org.apache.james.core.User.fromUsername(username);

        return thisUser.equals(thatUser);
    }
}
