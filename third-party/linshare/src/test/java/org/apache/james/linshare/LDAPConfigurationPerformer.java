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

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;

import java.nio.charset.StandardCharsets;

import io.restassured.authentication.BasicAuthScheme;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

class LDAPConfigurationPerformer {
    static void configureLdap(Linshare linshare) {
        BasicAuthScheme basicAuthScheme = new BasicAuthScheme();
        basicAuthScheme.setUserName("root@localhost.localdomain");
        basicAuthScheme.setPassword("adminlinshare");

        RequestSpecification specification = new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setBaseUri("http://" + linshare.getIp())
            .setPort(linshare.getPort())
            .setAuth(basicAuthScheme)
            .build();

        String ldapId = given(specification)
            .body("{" +
                "  \"label\":\"ldap-local\"," +
                "  \"providerUrl\":\"ldap://ldap:389\"," +
                "  \"securityPrincipal\":\"cn=linshare,dc=linshare,dc=org\"," +
                "  \"securityCredentials\":\"linshare\"" +
                "}")
            .post("/linshare/webservice/rest/admin/ldap_connections")
            .jsonPath()
            .getString("uuid");

        given(specification)
            .body("{" +
                "  \"uuid\":\"868400c0-c12e-456a-8c3c-19e985290586\"," +
                "  \"label\":\"openldap-local\"," +
                "  \"description\":\"This is pattern the default pattern for the OpenLdap structure.\"," +
                "  \"authCommand\":\"ldap.search(domain, \\\"(&(objectClass=inetOrgPerson)(mail=*)(givenName=*)(sn=*)(|(mail=\\\"+login+\\\")(uid=\\\"+login+\\\")))\\\");\"," +
                "  \"searchUserCommand\":\"ldap.search(domain, \\\"(&(objectClass=inetOrgPerson)(mail=\\\"+mail+\\\")(givenName=\\\"+first_name+\\\")(sn=\\\"+last_name+\\\"))\\\");\"," +
                "  \"userMail\":\"mail\"," +
                "  \"userFirstName\":\"givenName\"," +
                "  \"userLastName\":\"sn\"," +
                "  \"ldapUid\":\"uid\"," +
                "  \"autoCompleteCommandOnAllAttributes\":\"ldap.search(domain, \\\"(&(objectClass=inetOrgPerson)(mail=*)(givenName=*)(sn=*)(|(mail=\\\" + pattern + \\\")(sn=\\\" + pattern + \\\")(givenName=\\\" + pattern + \\\")))\\\");\"," +
                "  \"autoCompleteCommandOnFirstAndLastName\":\"ldap.search(domain, \\\"(&(objectClass=inetOrgPerson)(mail=*)(givenName=*)(sn=*)(|(&(sn=\\\" + first_name + \\\")(givenName=\\\" + last_name + \\\"))(&(sn=\\\" + last_name + \\\")(givenName=\\\" + first_name + \\\"))))\\\");\"," +
                "  \"searchPageSize\":100," +
                "  \"searchSizeLimit\":100," +
                "  \"completionPageSize\":10," +
                "  \"completionSizeLimit\":10" +
                "}")
            .post("/linshare/webservice/rest/admin/domain_patterns");

        String ldapPatternId = given(specification)
            .get("/linshare/webservice/rest/admin/domain_patterns")
            .getBody()
            .jsonPath()
            .getString("[0].uuid");

        String mimePolicyId = given(specification)
            .get("/linshare/webservice/rest/admin/mime_policies?domainId=LinShareRootDomain&onlyCurrentDomain=false")
            .jsonPath()
            .getString("[0].uuid");

        String welcomeMessageId = given(specification)
            .get("/linshare/webservice/rest/admin/welcome_messages?domainId=LinShareRootDomain&parent=true")
            .jsonPath()
            .getString("[0].uuid");

        String mailConfigId = given(specification)
            .get("/linshare/webservice/rest/admin/mail_configs?domainId=LinShareRootDomain&onlyCurrentDomain=false")
            .jsonPath()
            .getString("[0].uuid");

        given(specification)
            .body("{" +
                "  \"parent\":\"LinShareRootDomain\"," +
                "  \"type\":\"TOPDOMAIN\"," +
                "  \"providers\":[" +
                "    {" +
                "      \"ldapConnectionUuid\":\"" + ldapId + "\"," +
                "      \"userLdapPatternUuid\":\"" + ldapPatternId + "\"," +
                "      \"baseDn\":\"ou=People,dc=linshare,dc=org\"" +
                "     }]," +
                "  \"externalMailLocale\":\"ENGLISH\"," +
                "  \"language\":\"ENGLISH\"," +
                "  \"mailConfigUuid\":\"" + mailConfigId + "\"," +
                "  \"currentWelcomeMessage\":{\"uuid\":\"" + welcomeMessageId + "\"}," +
                "  \"mimePolicyUuid\":\"" + mimePolicyId + "\"," +
                "  \"userRole\":\"SIMPLE\"," +
                "  \"policy\":{\"identifier\":\"DefaultDomainPolicy\"}," +
                "  \"label\":\"linshare.org\"," +
                "  \"description\":\"linshare.org domain\"" +
                "}")
            .post("/linshare/webservice/rest/admin/domains");
    }
}
