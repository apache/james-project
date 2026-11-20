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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLEncoder;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;

/**
 * Creates isolated test accounts through James WebAdmin. Only accounts created here can be deleted.
 */
public class AccountManager {
    public record Account(String username, String password) {
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String GENERATED_PREFIX = "mpt-";

    private final McpMptConfiguration.WebAdminConfiguration configuration;
    private final Optional<String> defaultDomain;
    private final HttpClient client;
    private final Set<String> createdAccounts = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    public AccountManager(McpMptConfiguration.WebAdminConfiguration configuration, Map<String, String> variables) {
        this.configuration = configuration;
        this.defaultDomain = configuration.domain()
            .or(() -> Optional.ofNullable(variables.get("user"))
                .filter(user -> user.contains("@"))
                .map(user -> user.substring(user.indexOf('@') + 1)));
        this.client = HttpClient.create()
            .responseTimeout(TIMEOUT)
            .headers(headers -> {
                configuration.token().ifPresent(token -> headers.add("Authorization", "Bearer " + token));
                configuration.password().ifPresent(password -> headers.add("Password", password));
            });
    }

    public Optional<String> defaultDomain() {
        return defaultDomain;
    }

    public Set<String> createdAccounts() {
        return Set.copyOf(createdAccounts);
    }

    public Account create(Optional<String> requestedUsername, Optional<String> requestedPassword) {
        String username = requestedUsername.map(String::trim).filter(value -> !value.isEmpty())
            .map(value -> value.contains("@") ? value : value + "@" + domain())
            .orElseGet(() -> GENERATED_PREFIX + hex(4) + "@" + domain());
        String password = requestedPassword.filter(value -> !value.isEmpty()).orElseGet(() -> hex(12));
        String body;
        try {
            body = mapper.writeValueAsString(Map.of("password", password));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        // without ?force, WebAdmin refuses to overwrite an existing account
        int status = client.put()
            .uri(userUri(username))
            .send(ByteBufFlux.fromString(Mono.just(body)))
            .responseSingle((response, content) -> Mono.just(response.status().code()))
            .block(TIMEOUT);
        if (status == 409) {
            throw new IllegalArgumentException("Account " + username + " already exists");
        }
        if (status / 100 != 2) {
            throw new IllegalStateException("WebAdmin refused to create " + username + " (HTTP " + status + ")");
        }
        createdAccounts.add(username);
        return new Account(username, password);
    }

    public void delete(String username) {
        if (!createdAccounts.contains(username)) {
            throw new IllegalArgumentException("Only accounts created by this server can be deleted. Created: " + createdAccounts);
        }
        int status = client.delete()
            .uri(userUri(username))
            .responseSingle((response, content) -> Mono.just(response.status().code()))
            .block(TIMEOUT);
        if (status / 100 != 2) {
            throw new IllegalStateException("WebAdmin refused to delete " + username + " (HTTP " + status + ")");
        }
        createdAccounts.remove(username);
    }

    private String domain() {
        return defaultDomain.orElseThrow(() -> new IllegalArgumentException(
            "No account domain: pass a full username, or configure MPT_MCP_ACCOUNT_DOMAIN"));
    }

    private String userUri(String username) {
        return configuration.url() + "/users/" + URLEncoder.encode(username, UTF_8).replace("+", "%20");
    }

    private String hex(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }
}
