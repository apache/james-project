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

package org.apache.james.mpt.mcp;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum Protocol {
    SMTP("smtp", 25, "smtps", 465),
    IMAP("imap", 143, "imaps", 993),
    POP3("pop3", 110, "pop3s", 995),
    MANAGESIEVE("sieve", 4190, "sieves", 4190);

    public static Optional<Protocol> fromName(String value) {
        String normalized = value.trim().toLowerCase(Locale.US);
        if (normalized.equals("managesieve") || normalized.equals("managesieves")) {
            return Optional.of(MANAGESIEVE);
        }
        return Arrays.stream(values())
            .filter(protocol -> protocol.plainScheme.equals(normalized) || protocol.tlsScheme.equals(normalized))
            .findFirst();
    }

    private final String plainScheme;
    private final int plainPort;
    private final String tlsScheme;
    private final int tlsPort;

    Protocol(String plainScheme, int plainPort, String tlsScheme, int tlsPort) {
        this.plainScheme = plainScheme;
        this.plainPort = plainPort;
        this.tlsScheme = tlsScheme;
        this.tlsPort = tlsPort;
    }

    public boolean isTlsScheme(String scheme) {
        String normalized = scheme.trim().toLowerCase(Locale.US);
        return normalized.equals(tlsScheme) || normalized.equals("managesieves");
    }

    public int defaultPort(boolean tls) {
        return tls ? tlsPort : plainPort;
    }

    public String scheme(boolean tls) {
        return tls ? tlsScheme : plainScheme;
    }
}
