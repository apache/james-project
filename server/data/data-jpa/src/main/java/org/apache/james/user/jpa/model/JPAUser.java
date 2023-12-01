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

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;

import org.apache.james.core.Username;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.model.Algorithm;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

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
    static String hashPassword(String password, String nullableSalt, String nullableAlgorithm) {
        Algorithm algorithm = Algorithm.of(Optional.ofNullable(nullableAlgorithm).orElse("SHA-512"));
        if (algorithm.isPBKDF2()) {
            return algorithm.digest(password, nullableSalt);
        }
        String credentials = password;
        if (algorithm.isSalted() && nullableSalt != null) {
            credentials = nullableSalt + password;
        }
        return chooseHashFunction(algorithm.getName()).apply(credentials);
    }

    interface PasswordHashFunction extends Function<String, String> {}

    private static PasswordHashFunction chooseHashFunction(String algorithm) {
        switch (algorithm) {
            case "NONE":
                return password -> password;
            default:
                return password -> chooseHashing(algorithm).hashString(password, StandardCharsets.UTF_8).toString();
        }
    }

    @SuppressWarnings("deprecation")
    private static HashFunction chooseHashing(String algorithm) {
        switch (algorithm) {
            case "MD5":
                return Hashing.md5();
            case "SHA-256":
                return Hashing.sha256();
            case "SHA-512":
                return Hashing.sha512();
            case "SHA-1":
            case "SHA1":
                return Hashing.sha1();
            default:
                return Hashing.sha512();
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
        this.password = hashPassword(password, userName, alg);
    }

    @Override
    public Username getUserName() {
        return Username.of(name);
    }

    @Override
    public boolean setPassword(String newPass) {
        final boolean result;
        if (newPass == null) {
            result = false;
        } else {
            password = hashPassword(newPass, name, alg);
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
            result = password != null && password.equals(hashPassword(pass, name, alg));
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
