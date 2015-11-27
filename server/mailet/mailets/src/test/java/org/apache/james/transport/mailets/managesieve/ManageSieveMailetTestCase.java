/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.transport.mailets.managesieve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import javax.activation.DataHandler;
import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import org.apache.commons.io.IOUtils;
import org.apache.james.managesieve.api.SieveParser;
import org.apache.james.managesieve.api.SyntaxException;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class ManageSieveMailetTestCase {

    public static final String USER = "test@localhost";
    public static final String SCRIPT_NAME = "scriptName";
    public static final String SCRIPT_CONTENT = "scriptContent";
    public static final String SYNTAX_EXCEPTION = "SyntaxException";
    public static final String OLD_SCRIPT_NAME = "oldScriptName";
    public static final String NEW_SCRIPT_NAME = "newScriptName";
    public static final String SIEVE_LOCALHOST = "sieve@localhost";

    private ManageSieveMailet mailet;
    private SieveRepository sieveRepository;
    private SieveParser sieveParser;

    @Before
    public void setUp() throws Exception {
        mailet = new ManageSieveMailet();
        sieveRepository = mock(SieveRepository.class);
        sieveParser = mock(SieveParser.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        mailet.setSieveParser(sieveParser);
        mailet.setSieveRepository(sieveRepository);
        mailet.setUsersRepository(usersRepository);
        FakeMailetConfig config = new FakeMailetConfig("ManageSieve mailet", new FakeMailContext());
        config.setProperty("helpURL", "file:./src/test/resources/help.txt");
        mailet.init(config);
        when(usersRepository.contains(USER)).thenAnswer(new Answer<Boolean>() {
            public Boolean answer(InvocationOnMock invocationOnMock) throws Throwable {
                return true;
            }
        });
    }

    @Test
    public final void testCapabilityUnauthorised() throws MessagingException, IOException {
        MimeMessage message = prepareMimeMessage("CAPABILITY", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        when(sieveParser.getExtensions()).thenAnswer(new Answer<List<String>>() {
            public List<String> answer(InvocationOnMock invocationOnMock) throws Throwable {
                return Lists.newArrayList("a", "b", "c");
            }
        });
        mailet.service(mail);
        ensureResponseContains("Re: CAPABILITY", message.getSender(), "SIEVE a b c",
            "GETACTIVE ",
            "IMPLEMENTATION Apache ManageSieve v1.0",
            "VERSION 1.0",
            "OK");
    }

    @Test
    public final void testCapability() throws MessagingException, IOException {
        MimeMessage message = prepareMimeMessage("CAPABILITY", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        when(sieveParser.getExtensions()).thenAnswer(new Answer<List<String>>() {
            public List<String> answer(InvocationOnMock invocationOnMock) throws Throwable {
                return Lists.newArrayList("a", "b", "c");
            }
        });
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, "test");
        mailet.service(mail);
        ensureResponseContains("Re: CAPABILITY", message.getSender(), "SIEVE a b c",
            "GETACTIVE ",
            "IMPLEMENTATION Apache ManageSieve v1.0",
            "OWNER test@localhost",
            "VERSION 1.0",
            "OK");
    }

    @Test
    public final void testCapabilityExtraArguments() throws MessagingException, IOException {
        MimeMessage message = prepareMimeMessage("CAPABILITY", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        message.setSubject("CAPABILITY extra");
        message.saveChanges();
        mailet.service(mail);
        ensureResponse("Re: CAPABILITY extra", message.getSender(), "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testPutScriptUnauthorised() throws Exception {
        MimeMessage message = prepareMessageWithAttachment(SCRIPT_CONTENT, "PUTSCRIPT \"" + SCRIPT_NAME + "\"", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: PUTSCRIPT \"" + SCRIPT_NAME + "\"", message.getSender(), "NO");
    }

    @Test
    public final void testPutScript() throws Exception {
        when(sieveParser.parse(SCRIPT_CONTENT)).thenAnswer(new Answer<List<String>>() {
            public List<String> answer(InvocationOnMock invocationOnMock) throws Throwable {
                return Lists.newArrayList("warning1", "warning2");
            }
        });
        MimeMessage message = prepareMessageWithAttachment(SCRIPT_CONTENT, "PUTSCRIPT \"" + SCRIPT_NAME + "\"", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
        mailet.service(mail);
        ensureResponse("Re: PUTSCRIPT \"" + SCRIPT_NAME + "\"", message.getSender(), "OK (WARNINGS) \"warning1\" \"warning2\"");
    }

    @Test
    public final void testPutScriptExtraArgs() throws Exception {
        MimeMessage message = prepareMessageWithAttachment(SCRIPT_CONTENT, "PUTSCRIPT \"" + SCRIPT_NAME + "\" extra", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: PUTSCRIPT \"" + SCRIPT_NAME + "\" extra", message.getSender(), "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testPutScriptSyntaxError() throws Exception {
        doThrow(new SyntaxException("error message")).when(sieveParser).parse(SYNTAX_EXCEPTION);
        MimeMessage message = prepareMessageWithAttachment(SYNTAX_EXCEPTION, "PUTSCRIPT \"" + SCRIPT_NAME + "\"", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
        mailet.service(mail);
        ensureResponse("Re: PUTSCRIPT \"" + SCRIPT_NAME + "\"", message.getSender(), "NO \"Syntax Error: error message\"");
    }

    @Test
    public final void testPutScriptNoScript() throws Exception {
        MimeMessage message = prepareMimeMessage("PUTSCRIPT \"" + SCRIPT_NAME + "\"", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: PUTSCRIPT \"" + SCRIPT_NAME + "\"", message.getSender(), "NO \"Missing argument: script content\"");
    }

    @Test
    public final void testPutScriptNoScriptName() throws Exception {
        MimeMessage message = prepareMimeMessage("PUTSCRIPT", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: PUTSCRIPT", message.getSender(), "NO \"Missing argument: script name\"");
    }

    @Test
    public final void testGetScriptNonAuthorized() throws Exception {
        MimeMessage message = prepareMimeMessage("GETSCRIPT \"" + SCRIPT_NAME + "\"", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: GETSCRIPT \"" + SCRIPT_NAME + "\"", message.getSender(), "NO");
    }

    @Test
    public final void testGetScript() throws Exception {
        when(sieveRepository.getScript(USER, SCRIPT_NAME)).thenAnswer(new Answer<String>() {
            public String answer(InvocationOnMock invocationOnMock) throws Throwable {
                return SCRIPT_CONTENT;
            }
        });
        MimeMessage message = prepareMimeMessage("GETSCRIPT \"" + SCRIPT_NAME + "\"", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, USER);
        mailet.service(mail);
        ensureResponse("Re: GETSCRIPT \"" + SCRIPT_NAME + "\"", message.getSender(), "OK", SCRIPT_CONTENT);
    }

    @Test
    public final void testGetScriptExtraArgs() throws Exception {
        MimeMessage message = prepareMimeMessage("GETSCRIPT \"" + SCRIPT_NAME + "\" extra", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: GETSCRIPT \"" + SCRIPT_NAME + "\" extra", message.getSender(), "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testGetScriptNoScript() throws Exception {
        doThrow(new ScriptNotFoundException()).when(sieveRepository).getScript(USER, SCRIPT_NAME);
        MimeMessage message = prepareMimeMessage("GETSCRIPT \"" + SCRIPT_NAME + "\"", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, USER);
        mailet.service(mail);
        ensureResponse("Re: GETSCRIPT \"" + SCRIPT_NAME + "\"", message.getSender(), "NO (NONEXISTENT) \"There is no script by that name\"");
    }

    @Test
    public final void testGetScriptNoScriptName() throws Exception {
        String scriptContent = "line1\r\nline2";
        sieveRepository.putScript(USER, SCRIPT_NAME, scriptContent);
        MimeMessage message = prepareMimeMessage("GETSCRIPT", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);

        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, USER);
        mailet.service(mail);
        ensureResponse("Re: GETSCRIPT", message.getSender(), "NO \"Missing argument: script name\"");
    }

    @Test
    public final void testCheckScriptUnauthorised() throws Exception {
        MimeMessage message = prepareMessageWithAttachment(SCRIPT_CONTENT, "CHECKSCRIPT", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: CHECKSCRIPT", message.getSender(), "NO");
    }

    @Test
    public final void testCheckScript() throws Exception {
        when(sieveParser.parse(SCRIPT_CONTENT)).thenAnswer(new Answer<List<String>>() {
            public List<String> answer(InvocationOnMock invocationOnMock) throws Throwable {
                return Lists.newArrayList("warning1", "warning2");
            }
        });
        MimeMessage message = prepareMessageWithAttachment(SCRIPT_CONTENT, "CHECKSCRIPT", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
        mailet.service(mail);
        ensureResponse("Re: CHECKSCRIPT", message.getSender(), "OK (WARNINGS) \"warning1\" \"warning2\"");
    }

    @Test
    public final void testCheckScriptExtraArgs() throws Exception {
        MimeMessage message = prepareMessageWithAttachment(SCRIPT_CONTENT, "CHECKSCRIPT extra", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
        mailet.service(mail);
        ensureResponse("Re: CHECKSCRIPT extra", message.getSender(), "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testCheckScriptSyntaxError() throws Exception {
        doThrow(new SyntaxException("error message")).when(sieveParser).parse(SYNTAX_EXCEPTION);
        MimeMessage message = prepareMessageWithAttachment(SYNTAX_EXCEPTION, "CHECKSCRIPT", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
        mailet.service(mail);
        ensureResponse("Re: CHECKSCRIPT", message.getSender(), "NO \"Syntax Error: error message\"");
    }

    @Test
    public final void testCheckScriptNoScript() throws Exception {
        MimeMessage message = prepareMimeMessage("CHECKSCRIPT", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
        mailet.service(mail);
        ensureResponse("Re: CHECKSCRIPT", message.getSender(), "NO \"Script part not found in this message\"");
    }

    @Test
    public final void testDeleteScriptUnauthenticated() throws Exception {
        MimeMessage message = prepareMimeMessage("DELETESCRIPT \"" + SCRIPT_NAME + "\"", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: DELETESCRIPT \"" + SCRIPT_NAME + "\"", message.getSender(), "NO");
    }

    @Test
    public final void testDeleteScript() throws Exception {
        MimeMessage message = prepareMimeMessage("DELETESCRIPT \"" + SCRIPT_NAME + "\"", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
        mailet.service(mail);
        ensureResponse("Re: DELETESCRIPT \"" + SCRIPT_NAME + "\"", message.getSender(), "OK");
    }

    @Test
    public final void testDeleteScriptExtraArgs() throws Exception {
        MimeMessage message = prepareMimeMessage("DELETESCRIPT \"" + SCRIPT_NAME + "\" extra", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: DELETESCRIPT \"" + SCRIPT_NAME + "\" extra", message.getSender(), "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testDeleteScriptNoScriptName() throws Exception {
        MimeMessage message = prepareMimeMessage("DELETESCRIPT", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: DELETESCRIPT", message.getSender(), "NO \"Missing argument: script name\"");
    }

    @Test
    public final void testHaveSpaceUnauthenticated() throws Exception {
        MimeMessage message = prepareMimeMessage("HAVESPACE \"" + SCRIPT_NAME + "\" 1", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: HAVESPACE \"" + SCRIPT_NAME + "\" 1", message.getSender(), "NO");
    }

    @Test
    public final void testHaveSpace() throws Exception {
        MimeMessage message = prepareMimeMessage("HAVESPACE \"" + SCRIPT_NAME + "\" 1", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
        mailet.service(mail);
        ensureResponse("Re: HAVESPACE \"" + SCRIPT_NAME + "\" 1", message.getSender(), "OK");
    }

    @Test
    public final void testHaveSpaceExtraArgs() throws Exception {
        MimeMessage message = prepareMimeMessage("HAVESPACE \"" + SCRIPT_NAME + "\" 1 extra", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: HAVESPACE \"" + SCRIPT_NAME + "\" 1 extra", message.getSender(), "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testHaveSpaceNoScriptName() throws Exception {
        MimeMessage message = prepareMimeMessage("HAVESPACE", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: HAVESPACE", message.getSender(), "NO \"Missing argument: script name\"");
    }

    @Test
    public final void testHaveSpaceNoScriptSize() throws Exception {
        MimeMessage message = prepareMimeMessage("HAVESPACE \"" + SCRIPT_NAME + "\"", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: HAVESPACE \"" + SCRIPT_NAME + "\"", message.getSender(), "NO \"Missing argument: script size\"");
    }

    @Test
    public final void testHaveSpaceInvalidScriptSize() throws Exception {
        MimeMessage message = prepareMimeMessage("HAVESPACE \"" + SCRIPT_NAME + "\" X", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: HAVESPACE \"" + SCRIPT_NAME + "\" X", message.getSender(), "NO \"Invalid argument: script size\"");
    }

    @Test
    public final void testListScriptsUnauthorised() throws Exception {
        MimeMessage message = prepareMimeMessage("LISTSCRIPTS", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: LISTSCRIPTS", message.getSender(), "NO");
    }

    @Test
    public final void testListScripts() throws Exception {
        when(sieveRepository.listScripts(USER)).thenAnswer(new Answer<List<ScriptSummary>>() {
            @Override
            public List<ScriptSummary> answer(InvocationOnMock invocationOnMock) throws Throwable {
                return Lists.newArrayList(new ScriptSummary("scriptName2", true), new ScriptSummary("scriptName1", false));
            }
        });
        MimeMessage message = prepareMimeMessage("LISTSCRIPTS", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
        mailet.service(mail);
        ensureResponse("Re: LISTSCRIPTS", message.getSender(), "\"scriptName2\" ACTIVE\r\n\"scriptName1\"\r\nOK");
    }

    @Test
    public final void testListScriptsExtraArgs() throws Exception {
        MimeMessage message = prepareMimeMessage("LISTSCRIPTS extra", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
        mailet.service(mail);
        ensureResponse("Re: LISTSCRIPTS extra", message.getSender(), "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testRenameScriptsUnauthorised() throws Exception {
        sieveRepository.putScript(USER, OLD_SCRIPT_NAME, NEW_SCRIPT_NAME);
        MimeMessage message = prepareMimeMessage("RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\" \"" + NEW_SCRIPT_NAME + "\"", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\" \"" + NEW_SCRIPT_NAME + "\"", message.getSender(), "NO");
    }

    @Test
    public final void testRenameScripts() throws Exception {
        sieveRepository.putScript(USER, OLD_SCRIPT_NAME, NEW_SCRIPT_NAME);
        MimeMessage message = prepareMimeMessage("RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\" \"" + NEW_SCRIPT_NAME + "\"", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
        mailet.service(mail);
        ensureResponse("Re: RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\" \"" + NEW_SCRIPT_NAME + "\"", message.getSender(), "OK");
    }

    @Test
    public final void testRenameScriptsExtraArgs() throws Exception {
        sieveRepository.putScript(USER, OLD_SCRIPT_NAME, NEW_SCRIPT_NAME);
        MimeMessage message = prepareMimeMessage("RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\" \"" + NEW_SCRIPT_NAME + "\" extra", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\" \"" + NEW_SCRIPT_NAME + "\" extra", message.getSender(), "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testRenameScriptsNoScriptName() throws Exception {
        sieveRepository.putScript(USER, OLD_SCRIPT_NAME, NEW_SCRIPT_NAME);
        MimeMessage message = prepareMimeMessage("RENAMESCRIPT", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: RENAMESCRIPT", message.getSender(), "NO \"Missing argument: old script name\"");
    }

    @Test
    public final void testRenameScriptsNoNewScriptName() throws Exception {
        sieveRepository.putScript(USER, OLD_SCRIPT_NAME, NEW_SCRIPT_NAME);
        MimeMessage message = prepareMimeMessage("RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\"", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\"", message.getSender(), "NO \"Missing argument: new script name\"");
    }

    @Test
    public final void testSetActiveUnauthorised() throws Exception {
        MimeMessage message = prepareMimeMessage("SETACTIVE \"" + SCRIPT_NAME + "\"", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: SETACTIVE \"" + SCRIPT_NAME + "\"", message.getSender(), "NO");
    }

    @Test
    public final void testSetActive() throws Exception {
        MimeMessage message = prepareMimeMessage("SETACTIVE \"" + SCRIPT_NAME + "\"", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
        mailet.service(mail);
        ensureResponse("Re: SETACTIVE \"" + SCRIPT_NAME + "\"", message.getSender(), "OK");
    }

    @Test
    public final void testSetActiveExtraArgs() throws Exception {
        MimeMessage message = prepareMimeMessage("SETACTIVE \"" + SCRIPT_NAME + "\" extra", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: SETACTIVE \"" + SCRIPT_NAME + "\" extra", message.getSender(), "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testSetActiveNoScriptName() throws Exception {
        MimeMessage message = prepareMimeMessage("SETACTIVE", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: SETACTIVE", message.getSender(), "NO \"Missing argument: script name\"");
    }

    @Test
    public final void testGetActiveUnauthorized() throws Exception {
        MimeMessage message = prepareMimeMessage("GETACTIVE", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: GETACTIVE", message.getSender(), "NO");
    }

    @Test
    public final void testGetActive() throws Exception {
        when(sieveRepository.getActive(USER)).thenAnswer(new Answer<String>() {
            public String answer(InvocationOnMock invocationOnMock) throws Throwable {
                return SCRIPT_CONTENT;
            }
        });
        MimeMessage message = prepareMimeMessage("GETACTIVE", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, USER);
        mailet.service(mail);
        ensureResponse("Re: GETACTIVE", message.getSender(), "OK", SCRIPT_CONTENT);
    }

    @Test
    public final void testGetActiveExtraArgs() throws Exception {
        MimeMessage message = prepareMimeMessage("GETACTIVE extra", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        message.setSubject("GETACTIVE extra");
        message.saveChanges();
        mailet.service(mail);
        ensureResponse("Re: GETACTIVE extra", message.getSender(), "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testGetActiveDesactivated() throws Exception {
        MimeMessage message = prepareMimeMessage("GETACTIVE", USER, SIEVE_LOCALHOST);
        Mail mail = new FakeMail();
        mail.setMessage(message);
        mailet.service(mail);
        ensureResponse("Re: GETACTIVE", message.getSender(), "NO");
    }

    private MimeMessage prepareMimeMessage(String subject, String sender, String recipient) throws MessagingException {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject(subject);
        message.setSender(new InternetAddress(sender));
        message.setRecipient(RecipientType.TO, new InternetAddress(recipient));
        message.saveChanges();
        return message;
    }

    private MimeMessage prepareMessageWithAttachment(String scriptContent, String subject, String recipient, String sender) throws MessagingException, IOException {
        MimeMessage message = prepareMimeMessage(subject, recipient, sender);
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart scriptPart = new MimeBodyPart();
        scriptPart.setDataHandler(
            new DataHandler(
                new ByteArrayDataSource(
                    scriptContent,
                    "application/sieve; charset=UTF-8")
            ));
        scriptPart.setDisposition(MimeBodyPart.ATTACHMENT);
        // setting a DataHandler with no mailcap definition is not
        // supported by the specs. Javamail activation still work,
        // but Geronimo activation translate it to text/plain.
        // Let's manually force the header.
        scriptPart.setHeader("Content-Type", "application/sieve; charset=UTF-8");
        scriptPart.setFileName(SCRIPT_NAME);
        multipart.addBodyPart(scriptPart);
        message.setContent(multipart);
        message.saveChanges();
        return message;
    }

    private void ensureResponse(String subject, Address recipient, String... contents) throws MessagingException, IOException {
        MimeMessage result = ((FakeMailContext) mailet.getMailetContext()).getSentMessage();
        assertThat(result.getSubject()).isEqualTo(subject);
        assertThat(result.getRecipients(RecipientType.TO)).containsOnly(recipient);
        MimeMultipart multipart = (MimeMultipart) result.getContent();
        assertThat(multipart.getCount()).isEqualTo(contents.length);
        for(int i = 0; i < contents.length; i++) {
            if (multipart.getBodyPart(i).getContent() instanceof String) {
                assertThat(((String) multipart.getBodyPart(i).getContent()).trim()).isEqualTo(contents[i]);
            } else {
                assertThat(IOUtils.toString((ByteArrayInputStream) multipart.getBodyPart(i).getContent()).trim()).isEqualTo(contents[i]);
            }
        }
    }

    private void ensureResponseContains(String subject, Address recipient, String... contents) throws MessagingException, IOException {
        MimeMessage result = ((FakeMailContext) mailet.getMailetContext()).getSentMessage();
        assertThat(result.getSubject()).isEqualTo(subject);
        assertThat(result.getRecipients(RecipientType.TO)).containsOnly(recipient);
        MimeMultipart multipart = (MimeMultipart) result.getContent();
        assertThat(((String) multipart.getBodyPart(0).getContent()).split("\r\n")).containsOnly(contents);
    }
}