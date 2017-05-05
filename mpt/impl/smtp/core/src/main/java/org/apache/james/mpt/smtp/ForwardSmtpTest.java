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
package org.apache.james.mpt.smtp;

import static com.jayway.restassured.RestAssured.when;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.hamcrest.Matchers.equalTo;

import java.net.InetAddress;
import java.util.Locale;

import javax.inject.Inject;

import org.apache.james.mpt.script.AbstractSimpleScriptedTestProtocol;
import org.apache.james.util.streams.SwarmGenericContainer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public class ForwardSmtpTest extends AbstractSimpleScriptedTestProtocol {

    public static final String USER = "bob";
    public static final String DOMAIN = "mydomain.tld";
    public static final String USER_AT_DOMAIN = USER + "@" + DOMAIN;
    public static final String PASSWORD = "secret";

    private final TemporaryFolder folder = new TemporaryFolder();
    private final SwarmGenericContainer fakeSmtp = new SwarmGenericContainer("weave/rest-smtp-sink:latest")
            .withAffinityToContainer();
    
    @Rule
    public final RuleChain chain = RuleChain.outerRule(folder).around(fakeSmtp);

    @Inject
    private static SmtpHostSystem hostSystem;

    public ForwardSmtpTest() throws Exception {
        super(hostSystem, USER_AT_DOMAIN, PASSWORD, "/org/apache/james/smtp/scripts/");
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        InetAddress containerIp = InetAddresses.forString(fakeSmtp.getIp());
        hostSystem.getInMemoryDnsService()
            .registerRecord("yopmail.com", containerIp, "yopmail.com");
        hostSystem.addAddressMapping(USER, DOMAIN, "ray@yopmail.com");

        RestAssured.requestSpecification = new RequestSpecBuilder()
        		.setContentType(ContentType.JSON)
        		.setAccept(ContentType.JSON)
        		.setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8)))
        		.setPort(80)
        		.setBaseUri("http://" + containerIp.getHostAddress())
        		.build();
    }

    @Test
    public void forwardingAnEmailShouldWork() throws Exception {
        scriptTest("helo", Locale.US);

        when()
            .get("/api/email")
        .then()
            .statusCode(200)
            .body("[0].from", equalTo("matthieu@yopmail.com"))
            .body("[0].subject", equalTo("test"))
            .body("[0].text", equalTo("content"));
    }
}
