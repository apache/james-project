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

import org.apache.james.mpt.script.SimpleScriptedTestProtocol;
import org.apache.james.util.streams.SwarmGenericContainer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.containers.wait.HostPortWaitStrategy;

import com.google.common.base.Charsets;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public abstract class ForwardSmtpTest {

    public static final String USER = "bob";
    public static final String DOMAIN = "mydomain.tld";
    public static final String USER_AT_DOMAIN = USER + "@" + DOMAIN;
    public static final String PASSWORD = "secret";

    private final TemporaryFolder folder = new TemporaryFolder();
    private final SwarmGenericContainer fakeSmtp = new SwarmGenericContainer("weave/rest-smtp-sink:latest")
            .withExposedPorts(25)
            .withAffinityToContainer()
            .waitingFor(new HostPortWaitStrategy());
    
    @Rule
    public final RuleChain chain = RuleChain.outerRule(folder).around(fakeSmtp);

    protected abstract SmtpHostSystem createSmtpHostSystem();
    
    private SmtpHostSystem hostSystem;
    private SimpleScriptedTestProtocol scriptedTest;

    @Before
    public void setUp() throws Exception {
        hostSystem = createSmtpHostSystem();

        scriptedTest = new SimpleScriptedTestProtocol("/org/apache/james/smtp/scripts/", hostSystem)
                .withLocale(Locale.US)
                .withUser(USER_AT_DOMAIN, PASSWORD);

        InetAddress containerIp = InetAddress.getByName(fakeSmtp.getContainerIp());
        
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
        scriptedTest.run("helo");

        when()
            .get("/api/email")
        .then()
            .statusCode(200)
            .body("[0].from", equalTo("matthieu@yopmail.com"))
            .body("[0].subject", equalTo("test"))
            .body("[0].text", equalTo("content"));
    }
}
