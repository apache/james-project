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
package org.apache.james.jmap.methods;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.apache.james.http.jetty.Configuration;
import org.apache.james.http.jetty.JettyHttpServer;
import org.apache.james.jmap.AuthenticationFilter;
import org.apache.james.jmap.JMAPServlet;
import org.apache.james.jmap.api.AccessTokenManager;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.jmap.methods.GetMailboxesMethod;
import org.apache.james.jmap.methods.JmapRequestParser;
import org.apache.james.jmap.methods.JmapRequestParserImpl;
import org.apache.james.jmap.methods.JmapResponseWriter;
import org.apache.james.jmap.methods.JmapResponseWriterImpl;
import org.apache.james.jmap.methods.RequestHandler;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MailboxMetaData;
import org.apache.james.mailbox.store.TestId;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

public class GetMailboxesMethodTest {

    private RequestHandler requestHandler;
    private JettyHttpServer server;
    private UUID accessToken;
    private MailboxManager mockedMailboxManager;
    private MailboxMapperFactory<TestId> mockedMailboxMapperFactory;
    private MailboxSession mockedMailboxSession;
    
    @SuppressWarnings("unchecked")
    @Before
    public void setup() throws Exception {
        String username = "username@domain.tld";

        AccessTokenManager mockedAccessTokenManager = mock(AccessTokenManager.class);
        mockedMailboxMapperFactory = mock(MailboxMapperFactory.class);
        mockedMailboxManager = mock(MailboxManager.class);
        mockedMailboxSession = mock(MailboxSession.class);

        JmapRequestParser jmapRequestParser = new JmapRequestParserImpl(ImmutableSet.of(new Jdk8Module()));
        JmapResponseWriter jmapResponseWriter = new JmapResponseWriterImpl(ImmutableSet.of(new Jdk8Module()));

        requestHandler = new RequestHandler(ImmutableSet.of(new GetMailboxesMethod<>(jmapRequestParser, jmapResponseWriter, mockedMailboxManager, mockedMailboxMapperFactory)));
        JMAPServlet jmapServlet = new JMAPServlet(requestHandler);

        AuthenticationFilter authenticationFilter = new AuthenticationFilter(mockedAccessTokenManager, mockedMailboxManager);
        
        when(mockedMailboxManager.createSystemSession(eq(username), any(Logger.class))).thenReturn(mockedMailboxSession);
        when(mockedAccessTokenManager.isValid(any(AccessToken.class))).thenReturn(true);
        when(mockedAccessTokenManager.getUsernameFromToken(any(AccessToken.class))).thenReturn(username);
        
        accessToken = UUID.randomUUID();
        
        server = JettyHttpServer.create(
                Configuration.builder()
                .filter("/jmap")
                .with(authenticationFilter)
                .serve("/jmap")
                .with(jmapServlet)
                .randomPort()
                .build());
        
        server.start();

        RestAssured.port = server.getPort();
        RestAssured.config = newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8));
    }

    @After
    public void teardown() throws Exception {
        server.stop();
    }
    
    @Test
    public void getMailboxesShouldErrorNotSupportedWhenRequestContainsNonNullAccountId() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken)
            .body("[[\"getMailboxes\", {\"accountId\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"error\",{\"type\":\"Not yet implemented\"},\"#0\"]]"));
    }

    
    @Test
    public void getMailboxesShouldErrorNotSupportedWhenRequestContainsNonNullIds() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken)
            .body("[[\"getMailboxes\", {\"ids\": []}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"error\",{\"type\":\"Not yet implemented\"},\"#0\"]]"));
    }
    
    @Test
    public void getMailboxesShouldErrorNotSupportedWhenRequestContainsNonNullProperties() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken)
            .body("[[\"getMailboxes\", {\"properties\": []}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"error\",{\"type\":\"Not yet implemented\"},\"#0\"]]"));
    }
    
    @Test
    public void getMailboxesShouldErrorInvalidArgumentsWhenRequestIsInvalid() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken)
            .body("[[\"getMailboxes\", {\"ids\": true}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"error\",{\"type\":\"invalidArguments\"},\"#0\"]]"));
    }

    @Test
    public void getMailboxesShouldReturnEmptyListWhenNoMailboxes() throws Exception {
        when(mockedMailboxManager.list(any()))
            .thenReturn(ImmutableList.<MailboxPath>of());
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken)
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"getMailboxes\",{\"accountId\":null,\"state\":null,\"list\":[],\"notFound\":null},\"#0\"]]"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getMailboxesShouldReturnMailboxesWhenAvailable() throws Exception {
        MailboxPath mailboxPath = new MailboxPath("namespace", "user", "name");
        when(mockedMailboxManager.list(eq(mockedMailboxSession)))
            .thenReturn(ImmutableList.<MailboxPath>of(mailboxPath));

        MessageManager mockedMessageManager = mock(MessageManager.class);
        when(mockedMailboxManager.getMailbox(eq(mailboxPath), eq(mockedMailboxSession)))
            .thenReturn(mockedMessageManager);

        MailboxMetaData mailboxMetaData = new MailboxMetaData(ImmutableList.of(), null, 123L, 5L, 10L, 3L, 2L, 1L, false, false, null);
        when(mockedMessageManager.getMetaData(eq(false), eq(mockedMailboxSession), eq(FetchGroup.UNSEEN_COUNT)))
            .thenReturn(mailboxMetaData);

        MailboxMapper<TestId> mockedMailboxMapper = mock(MailboxMapper.class);
        when(mockedMailboxMapperFactory.getMailboxMapper(eq(mockedMailboxSession)))
            .thenReturn(mockedMailboxMapper);

        SimpleMailbox<TestId> simpleMailbox = new SimpleMailbox<TestId>(mailboxPath, 5L);
        simpleMailbox.setMailboxId(TestId.of(23432L));
        when(mockedMailboxMapper.findMailboxByPath(mailboxPath))
            .thenReturn(simpleMailbox);

        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken)
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"getMailboxes\",{\"accountId\":null,\"state\":null,\"list\":[{\"id\":\"23432\",\"name\":\"name\",\"parentId\":null,\"role\":null,\"sortOrder\":0,\"mustBeOnlyMailbox\":false,\"mayReadItems\":false,\"mayAddItems\":false,\"mayRemoveItems\":false,\"mayCreateChild\":false,\"mayRename\":false,\"mayDelete\":false,\"totalMessages\":0,\"unreadMessages\":2,\"totalThreads\":0,\"unreadThreads\":0}],\"notFound\":null},\"#0\"]]"));
    }
}
