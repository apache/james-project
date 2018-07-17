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

package org.apache.james.user.jpa.model;

import java.util.function.Function;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Version;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.james.user.api.model.User;

import com.google.common.annotations.VisibleForTesting;

@Entity(name = "JamesUser")
@Table(name = "JAMES_USER")
@NamedQueries({ 
    @NamedQuery(name = "findUserByName", query = "SELECT user FROM JamesUser user WHERE user.name=:name"), 
    @NamedQuery(name = "deleteUserByName", query = "DELETE FROM JamesUser user WHERE user.name=:name"),
    @NamedQuery(name = "containsUser", query = "SELECT COUNT(user) FROM JamesUser user WHERE user.name=:name"), 
    @NamedQuery(name = "countUsers", query = "SELECT COUNT(user) FROM JamesUser user"), 
    @NamedQuery(name = "listUserNames", query = "SELECT user.name FROM JamesUser user") })
public class JPAUser implements User {

    /**
     * Hash password.
     * 
     * @param password
     *            not null
     * @return not null
     */
    @VisibleForTesting
    static String hashPassword(String password, String alg) {
        return chooseHashFunction(alg).apply(password);
    }

    interface HashFunction extends Function<String, String> {}

    private static HashFunction chooseHashFunction(String algorithm) {
        if (algorithm == null) {
            return DigestUtils::md5Hex;
        }
        switch (algorithm) {
            case "MD5":
                return DigestUtils::md5Hex;
            case "NONE":
                return (password) -> "password";
            case "SHA-256":
                return DigestUtils::sha256Hex;
            case "SHA-512":
                return DigestUtils::sha512Hex;
            default:
                return DigestUtils::sha1Hex;
        }
    }

    /** Prevents concurrent modification */
    @Version
    private int version;

    /** Key by user name */
    @Id
    @Column(name = "USER_NAME", nullable = false, length = 100)
    private String name;

    /** Hashed password */
    @Basic
    @Column(name = "PASSWORD", nullable = false, length = 128)
    private String password;

    @Basic
    @Column(name = "PASSWORD_HASH_ALGORITHM", nullable = false, length = 100)
    private String alg;

    protected JPAUser() {
    }

    public JPAUser(String userName, String password, String alg) {
        super();
        this.name = userName;
        this.alg = alg;
        this.password = hashPassword(password, alg);
    }

    @Override
    public String getUserName() {
        return name;
    }

    @Override
    public boolean setPassword(String newPass) {
        final boolean result;
        if (newPass == null) {
            result = false;
        } else {
            password = hashPassword(newPass, alg);
            result = true;
        }
        return result;
    }

    @Override
    public boolean verifyPassword(String pass) {
        final boolean result;
        if (pass == null) {
            result = password == null;
        } else {
            result = password != null && password.equals(hashPassword(pass, alg));
        }
        return result;
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final JPAUser other = (JPAUser) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "[User " + name + "]";
    }

}
