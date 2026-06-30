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

package org.apache.james.examples.imap.sasl;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;

public record ExampleTokenSaslConfiguration(String expectedToken, Username authorizedUser) {
    private static final String EXPECTED_TOKEN_PROPERTY = "auth.exampleToken.expectedToken";
    private static final String AUTHORIZED_USER_PROPERTY = "auth.exampleToken.authorizedUser";

    public static ExampleTokenSaslConfiguration from(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        if (!configuration.containsKey(EXPECTED_TOKEN_PROPERTY)) {
            throw new ConfigurationException(EXPECTED_TOKEN_PROPERTY + " is mandatory");
        }
        if (!configuration.containsKey(AUTHORIZED_USER_PROPERTY)) {
            throw new ConfigurationException(AUTHORIZED_USER_PROPERTY + " is mandatory");
        }

        return new ExampleTokenSaslConfiguration(
            configuration.getString(EXPECTED_TOKEN_PROPERTY),
            Username.of(configuration.getString(AUTHORIZED_USER_PROPERTY)));
    }
}
