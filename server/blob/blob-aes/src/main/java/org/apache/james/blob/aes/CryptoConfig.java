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

package org.apache.james.blob.aes;

import java.util.Arrays;
import java.util.Objects;

import com.google.crypto.tink.subtle.Hex;

public class CryptoConfig {

    public static CryptoConfigBuilder builder() {
        return new CryptoConfigBuilder();
    }

    private final String salt;
    private final char[] password;

    public CryptoConfig(String salt, char[] password) {
        this.salt = salt;
        this.password = password;
    }

    public byte[] salt() {
        return Hex.decode(salt);
    }

    public char[] password() {
        return password;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof CryptoConfig) {
            CryptoConfig that = (CryptoConfig) o;

            return Objects.equals(this.salt, that.salt)
                && Arrays.equals(this.password, that.password);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(salt, password);
    }
}