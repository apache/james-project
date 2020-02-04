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

package org.apache.james.linshare;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public interface LinshareFixture {

    class Credential {

        private final String username;
        private final String password;

        @VisibleForTesting
        public Credential(String username, String password) {
            Preconditions.checkNotNull(username);
            Preconditions.checkNotNull(password);

            this.username = username;
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }

    Credential USER_1 = new Credential("user1@linshare.org", "password1");
    Credential USER_2 = new Credential("user2@linshare.org", "password2");
    Credential USER_3 = new Credential("user3@linshare.org", "password3");
    Credential USER_4 = new Credential("user4@linshare.org", "password4");
    Credential USER_5 = new Credential("user5@linshare.org", "password5");
    Credential USER_6 = new Credential("user6@linshare.org", "password6");
    Credential USER_7 = new Credential("user7@linshare.org", "password7");
    Credential LINAGORA = new Credential("linagora@linshare.org", "linagora");
    Credential EXTERNAL_1 = new Credential("external1@linshare.org", "password1");
    Credential EXTERNAL_2 = new Credential("external2@linshare.org", "password2");

    List<Credential> USER_CREDENTIALS = ImmutableList.of(
        USER_1,
        USER_2,
        USER_3,
        USER_4,
        USER_5,
        USER_6,
        USER_7,
        LINAGORA,
        EXTERNAL_1,
        EXTERNAL_2);

    Map<String, Credential> USER_CREDENTIAL_MAP = USER_CREDENTIALS.stream()
        .collect(Guavate.toImmutableMap(Credential::getUsername, Function.identity()));

    String MATCH_ALL_QUERY = "{" +
        "\"combinator\": \"and\"," +
        "\"criteria\": []" +
        "}";

    Credential TECHNICAL_ACCOUNT = new Credential("Technical@linshare.org", "Technical");
    Credential ADMIN_ACCOUNT = new Credential("root@localhost.localdomain", "adminlinshare");

    boolean ACCOUNT_ENABLED = true;

    List<String> TECHNICAL_PERMISSIONS = ImmutableList.of("DOCUMENT_ENTRIES_CREATE");
}
