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

package org.apache.james.protocols.smtp;

import java.net.URL;

// TODO enable reuse via IMAP
// TODO rename to OidcSASLConfiguration ?
public class SASLConfiguration {
    // TODO move here the code to parse this
    private final URL jwkURL;
    private final URL oidcSessionURL;
    private final String claim;
    private final String scope;

    public SASLConfiguration(URL jwkURL, URL oidcSessionURL, String claim, String scope) {
        this.jwkURL = jwkURL;
        this.oidcSessionURL = oidcSessionURL;
        this.claim = claim;
        this.scope = scope;
    }

    public URL getJwkURL() {
        return jwkURL;
    }

    public URL getOidcSessionURL() {
        return oidcSessionURL;
    }

    public String getClaim() {
        return claim;
    }

    public String getScope() {
        return scope;
    }
}
