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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.crypto.tink.subtle.Hex;

public class CryptoConfigBuilder {
    private String salt;
    private char[] password;

    CryptoConfigBuilder() {
    }

    public CryptoConfigBuilder salt(String salt) {
        this.salt = salt;
        return this;
    }

    public CryptoConfigBuilder password(char[] password) {
        this.password = password;
        return this;
    }

    public CryptoConfig build() {
        Preconditions.checkState(!Strings.isNullOrEmpty(salt), "'salt' is mandatory and must not be empty");
        Preconditions.checkState(password != null && password.length > 0, "'password' is mandatory and must not be empty");

        return new CryptoConfig(Hex.encode(Hex.decode(salt)), password);
    }
}