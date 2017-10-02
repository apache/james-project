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

package org.apache.james.jmap.methods.integration.cucumber;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.james.jmap.HttpJmapAuthentication;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.model.MailboxConstants;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;

import cucumber.api.PendingException;
import cucumber.api.java.en.Given;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class UserStepdefs {

    private final MainStepdefs mainStepdefs;
    
    protected Map<String, String> passwordByUser;
    protected Set<String> domains;
    protected Map<String, AccessToken> tokenByUser;
    protected String lastConnectedUser;
    
    @Inject
    private UserStepdefs(MainStepdefs mainStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.domains = new HashSet<>();
        this.passwordByUser = new HashMap<>();
        this.tokenByUser = new HashMap<>();
    }

    @Given("^a domain named \"([^\"]*)\"$")
    public void createDomain(String domain) throws Exception {
        mainStepdefs.dataProbe.addDomain(domain);
        domains.add(domain);
    }

    @Given("^some users (.*)$")
    public void createUsers(List<String> users) throws Throwable {
        users.stream()
            .map(this::unquote)
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
    public void createConnectedUser(String username) throws Throwable {
        createUser(username);
        connectUser(username);
    }
    
    @Given("^\"([^\"]*)\" has a mailbox \"([^\"]*)\"$")
    public void createMailbox(String username, String mailbox) throws Throwable {
        mainStepdefs.mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, username, mailbox);
    }

    
    @Given("^\"([^\"]*)\" is connected$")
    public void connectUser(String username) throws Throwable {
        String password = passwordByUser.get(username);
        Preconditions.checkState(password != null, "unknown user " + username);
        AccessToken accessToken = HttpJmapAuthentication.authenticateJamesUser(mainStepdefs.baseUri(), username, password);
        tokenByUser.put(username, accessToken);
        lastConnectedUser = username;
    }
    
    @Given("^\"([^\"]*)\" shares its mailbox \"([^\"]*)\" with \"([^\"]*)\"$")
    public void shareMailbox(String owner, String mailbox, String shareTo) throws Throwable {
        throw new PendingException();
    }

    private String generatePassword(String username) {
        return Hashing.murmur3_128().hashString(username, Charsets.UTF_8).toString();
    }
}
