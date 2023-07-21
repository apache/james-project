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

package org.apache.james.user.ldap;

import org.apache.james.core.Username;

public class DockerLdapSingleton {
    public static final Username JAMES_USER = Username.of("james-user");
    public static final String PASSWORD = "secret";
    public static final String DOMAIN = "james.org";
    public static final String ADMIN_PASSWORD = "mysecretpassword";
    public static final String ADMIN_LOCAL_PART = "admin";
    public static final Username ADMIN = Username.fromLocalPartWithDomain(ADMIN_LOCAL_PART, DOMAIN);

    public static final LdapGenericContainer ldapContainer = LdapGenericContainer.builder()
        .domain(DOMAIN)
        .password(ADMIN_PASSWORD)
        .build();

    public static final LdapGenericContainer ldapContainerWithLocalPartLogin = LdapGenericContainer.builder()
        .domain(DOMAIN)
        .password(ADMIN_PASSWORD)
        .dockerFilePrefix("localpartLogin/")
        .build();
}
