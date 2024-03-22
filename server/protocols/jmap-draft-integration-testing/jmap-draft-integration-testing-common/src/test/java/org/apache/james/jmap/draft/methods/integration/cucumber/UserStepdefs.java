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

package org.apache.james.jmap.draft.methods.integration.cucumber;

import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.Splitter;
import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.AccessToken;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.runnable.ThrowingRunnable;
import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;

import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;

@ScenarioScoped
public class UserStepdefs {

    private final MainStepdefs mainStepdefs;
    
    private Map<String, String> passwordByUser;
    private Set<String> domains;
    private Map<String, AccessToken> tokenByUser;
    private Optional<String> lastConnectedUser;
    
    @Inject
    private UserStepdefs(MainStepdefs mainStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.domains = new HashSet<>();
        this.passwordByUser = new HashMap<>();
        this.tokenByUser = new HashMap<>();
        this.lastConnectedUser = Optional.empty();
    }

    public void execWithUser(String user, ThrowingRunnable sideEffect) {
        Optional<String> previousConnectedUser = lastConnectedUser;
        connectUser(user);
        try {
            sideEffect.run();
        } finally {
            previousConnectedUser.ifPresent(Throwing.consumer(this::connectUser));
        }
    }

    public String getConnectedUser() {
        Preconditions.checkArgument(lastConnectedUser.isPresent(), "No user is connected");

        return lastConnectedUser.get();
    }

    public String getUserPassword(String user) {
        Preconditions.checkArgument(passwordByUser.containsKey(user), "User has no password created yet");

        return passwordByUser.get(user);
    }

    @Given("^a domain named \"([^\"]*)\"$")
    public void createDomain(String domain) throws Exception {
        mainStepdefs.dataProbe.addDomain(domain);
        domains.add(domain);
    }

    @Given("^some users \"(.*)\"$")
    public void createUsers(String users) {
        Splitter.on(',').trimResults()
            .splitToStream(users)
            .forEach(Throwing.consumer(this::createUser));
    }
    
    private String unquote(String quotedString) {
        return quotedString.substring(1, quotedString.length() - 1);
    }

    @Given("^a user \"([^\"]*)\"$")
    public void createUser(String username) throws Exception {
        String password = generatePassword(username);
        mainStepdefs.dataProbe.addUser(username, password);
        passwordByUser.put(username, password);
    }

    @Given("^a connected user \"([^\"]*)\"$")
    public void createConnectedUser(String username) throws Exception {
        createUser(username);
        connectUser(username);
    }

    @Given("^\"([^\"]*)\" is connected$")
    public void connectUser(String username) {
        AccessToken accessToken = authenticate(username);
        tokenByUser.put(username, accessToken);
        lastConnectedUser = Optional.of(username);
    }

    public AccessToken authenticate(String username) {
        return tokenByUser.computeIfAbsent(username, (user) -> {
            String password = passwordByUser.get(user);
            Preconditions.checkState(password != null, "unknown user %s", user);

            return authenticateJamesUser(baseUri(mainStepdefs.jmapServer), Username.of(user), password);
        });
    }

    private String generatePassword(String username) {
        return Hashing.murmur3_128().hashString(username, StandardCharsets.UTF_8).toString();
    }
}
