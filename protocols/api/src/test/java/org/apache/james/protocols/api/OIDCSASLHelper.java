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

package org.apache.james.protocols.api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.google.common.collect.ImmutableList;

public class OIDCSASLHelper {
    // See the XOAUTH2 specification athttps://developers.google.com/workspace/gmail/imap/xoauth2-protocol
    // for details.
    public static String generateEncodedXOauth2InitialClientResponse(String username, String token) {
        return Base64.getEncoder().encodeToString(String.join("" + OIDCSASLParser.SASL_SEPARATOR,
                ImmutableList.of("user=" + username, "auth=Bearer " + token, "", ""))
            .getBytes(StandardCharsets.US_ASCII));
    }

    // See the OAUTHBEARER specification at https://datatracker.ietf.org/doc/html/rfc5801#section-4
    // for details.
    public static String generateEncodedOauthbearerInitialClientResponse(String username, String token) {
        return Base64.getEncoder().encodeToString(String.join("" + OIDCSASLParser.SASL_SEPARATOR,
                ImmutableList.of("n,a=" + username, "auth=Bearer " + token, "", ""))
            .getBytes(StandardCharsets.US_ASCII));
    }
}
