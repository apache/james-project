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

import java.net.URI;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A host/port of the test bench an agent is allowed to run scripts against.
 */
public record Target(String name, Protocol protocol, String host, int port, boolean tls, String description) {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]+");

    public Target {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(host, "host");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid target name '" + name + "'");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port " + port + " for target " + name);
        }
        description = description == null ? "" : description;
    }

    /**
     * Parses <code>scheme://host[:port]</code>, scheme being one of smtp, smtps, imap, imaps, pop3, pop3s, sieve, sieves.
     * The <code>s</code> suffix means implicit TLS on connect.
     */
    public static Target parse(String name, String uri) {
        URI parsed = URI.create(uri.trim());
        if (parsed.getScheme() == null || parsed.getHost() == null) {
            throw new IllegalArgumentException("Invalid target URI '" + uri + "', expecting scheme://host:port");
        }
        Protocol protocol = Protocol.fromName(parsed.getScheme())
            .orElseThrow(() -> new IllegalArgumentException("Unknown scheme '" + parsed.getScheme() + "' for target " + name));
        boolean tls = protocol.isTlsScheme(parsed.getScheme());
        int port = parsed.getPort() > 0 ? parsed.getPort() : protocol.defaultPort(tls);
        return new Target(name, protocol, parsed.getHost(), port, tls, "");
    }

    public String uri() {
        return protocol.scheme(tls) + "://" + host + ":" + port;
    }
}
