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

package org.apache.james.protocols.sasl.kerberos;

import java.util.Locale;

import javax.security.auth.kerberos.KerberosPrincipal;

record CanonicalKerberosPrincipal(String components, String realm) {
    static CanonicalKerberosPrincipal parse(String authenticationId) {
        if (authenticationId == null || authenticationId.chars().anyMatch(character -> character > 0x7F)) {
            throw new IllegalArgumentException("GSSAPI authentication identity must contain only ASCII characters");
        }

        KerberosPrincipal principal = new KerberosPrincipal(authenticationId);
        String realm = principal.getRealm();
        String name = principal.getName();
        CanonicalKerberosPrincipal parsed = new CanonicalKerberosPrincipal(name.substring(0, name.length() - realm.length() - 1), realm);

        // The principal is respelled by the parser: reject anything the parser did not read back verbatim.
        if (!authenticationId.equals(parsed.asString())) {
            throw new IllegalArgumentException("GSSAPI authentication identity is not canonical");
        }
        return parsed;
    }

    CanonicalKerberosPrincipal {
        if (components == null || components.isEmpty() || components.indexOf('@') >= 0) {
            throw new IllegalArgumentException("Kerberos principal components must not be empty nor contain a realm separator");
        }
        if (realm == null || realm.isEmpty()) {
            throw new IllegalArgumentException("Kerberos realm must not be empty");
        }
        if (!components.equals(components.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Kerberos principal components must be lower case");
        }
        if (!realm.equals(realm.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Kerberos realm must be upper case");
        }
    }

    String asString() {
        return components + "@" + realm;
    }
}
