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

import java.util.Map;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginException;

class KerberosLoginContextFactory {
    private static final String LOGIN_CONTEXT_NAME = "JamesGssapiAcceptor";
    private static final String KRB5_LOGIN_MODULE = "com.sun.security.auth.module.Krb5LoginModule";

    KerberosLoginContext login(GssapiSaslConfiguration configuration) throws LoginException {
        javax.security.auth.login.LoginContext loginContext = new javax.security.auth.login.LoginContext(LOGIN_CONTEXT_NAME, null, null, jaasConfiguration(configuration));
        loginContext.login();
        return new KerberosLoginContext(loginContext);
    }

    private Configuration jaasConfiguration(GssapiSaslConfiguration configuration) {
        Map<String, String> options = Map.of(
            "doNotPrompt", "true",
            "isInitiator", "false",
            "keyTab", configuration.keyTab().toString(),
            "principal", configuration.principal(),
            "storeKey", "true",
            "useKeyTab", "true",
            "useTicketCache", "false");

        return new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                return new AppConfigurationEntry[] {
                    new AppConfigurationEntry(KRB5_LOGIN_MODULE, AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options)
                };
            }
        };
    }
}
