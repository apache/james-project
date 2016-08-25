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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.io.IOUtils;
import org.apache.james.managesieve.api.SieveParser;
import org.apache.james.managesieve.api.SyntaxException;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

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
    private UsersRepository usersRepository;
    private FakeMailContext fakeMailContext;

    @Before
    public void setUp() throws Exception {
        sieveRepository = mock(SieveRepository.class);
        sieveParser = mock(SieveParser.class);
        usersRepository = mock(UsersRepository.class);
        initializeMailet();
        when(usersRepository.contains(USER)).thenReturn(true);
    }

    @Test
    public final void testCapabilityUnauthorised() throws Exception {
        MimeMessage message = prepareMimeMessage("CAPABILITY");
        Mail mail = createUnauthenticatedMail(message);
        when(sieveParser.getExtensions()).thenReturn(Lists.newArrayList("a", "b", "c"));
        initializeMailet();
        mailet.service(mail);
        ensureResponseContains("Re: CAPABILITY", "\"SIEVE\" \"a b c\"",
            "\"IMPLEMENTATION\" \"Apache ManageSieve v1.0\"",
            "\"VERSION\" \"1.0\"",
            "\"STARTTLS\"",
            "\"SASL\" \"PLAIN\"",
            "OK");
    }

    @Test
    public final void testCapability() throws Exception {
        MimeMessage message = prepareMimeMessage("CAPABILITY");
        Mail mail = createUnauthenticatedMail(message);
        when(sieveParser.getExtensions()).thenReturn(Lists.newArrayList("a", "b", "c"));
        initializeMailet();
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, "test");
        mailet.service(mail);
        ensureResponseContains("Re: CAPABILITY", "\"SIEVE\" \"a b c\"",
            "\"IMPLEMENTATION\" \"Apache ManageSieve v1.0\"",
            "\"OWNER\" \"test@localhost\"",
            "\"VERSION\" \"1.0\"",
            "\"STARTTLS\"",
            "\"SASL\" \"PLAIN\"",
            "OK");
    }

    @Test
    public final void testCapabilityExtraArguments() throws Exception {
        MimeMessage message = prepareMimeMessage("CAPABILITY");
        Mail mail = createUnauthenticatedMail(message);
        message.setSubject("CAPABILITY extra");
        message.saveChanges();
        mailet.service(mail);
        ensureResponse("Re: CAPABILITY extra", "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testPutScriptinvalidLiteral() throws Exception {
        MimeMessage message = prepareMessageWithAttachment(SCRIPT_CONTENT, "PUTSCRIPT \"" + SCRIPT_NAME + "\"");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: PUTSCRIPT \"" + SCRIPT_NAME + "\"", "NO \"Missing argument: script size\"");
    }

    @Test
    public final void testPutScript() throws Exception {
        when(sieveParser.parse(SCRIPT_CONTENT)).thenReturn(Lists.newArrayList("warning1", "warning2"));
        MimeMessage message = prepareMessageWithAttachment(SCRIPT_CONTENT, "PUTSCRIPT \"" + SCRIPT_NAME + "\" {100+}");
        Mail mail = createAuthentificatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: PUTSCRIPT \"" + SCRIPT_NAME + "\" {100+}", "OK (WARNINGS) \"warning1\" \"warning2\"");
    }

    @Test
    public final void testPutScriptInvalidLiteral() throws Exception {
        MimeMessage message = prepareMessageWithAttachment(SCRIPT_CONTENT, "PUTSCRIPT \"" + SCRIPT_NAME + "\" extra");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: PUTSCRIPT \"" + SCRIPT_NAME + "\" extra", "NO \"extra is an invalid size literal : it should be at least 4 char looking like {_+}\"");
    }

    @Test
    public final void testPutScriptExtraArgs() throws Exception {
        MimeMessage message = prepareMessageWithAttachment(SCRIPT_CONTENT, "PUTSCRIPT \"" + SCRIPT_NAME + "\" {10+} extra");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: PUTSCRIPT \"" + SCRIPT_NAME + "\" {10+} extra", "NO \"Extra arguments not supported\"");
    }

    @Test
    public final void testPutScriptSyntaxError() throws Exception {
        doThrow(new SyntaxException("error message")).when(sieveParser).parse(SYNTAX_EXCEPTION);
        MimeMessage message = prepareMessageWithAttachment(SYNTAX_EXCEPTION, "PUTSCRIPT \"" + SCRIPT_NAME + "\" {10+}");
        Mail mail = createAuthentificatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: PUTSCRIPT \"" + SCRIPT_NAME + "\" {10+}", "NO \"Syntax Error: error message\"");
    }

    @Test
    public final void testPutScriptNoScript() throws Exception {
        MimeMessage message = prepareMimeMessage("PUTSCRIPT \"" + SCRIPT_NAME + "\"");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: PUTSCRIPT \"" + SCRIPT_NAME + "\"", "NO \"Missing argument: script size\"");
    }

    @Test
    public final void testPutScriptNoScriptName() throws Exception {
        MimeMessage message = prepareMimeMessage("PUTSCRIPT");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: PUTSCRIPT", "NO \"Missing argument: script name\"");
    }

    @Test
    public final void testGetScriptNonAuthorized() throws Exception {
        MimeMessage message = prepareMimeMessage("GETSCRIPT \"" + SCRIPT_NAME + "\"");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: GETSCRIPT \"" + SCRIPT_NAME + "\"", "NO");
    }

    @Test
    public final void testGetScript() throws Exception {
        when(sieveRepository.getScript(USER, SCRIPT_NAME)).thenReturn(new ByteArrayInputStream(SCRIPT_CONTENT.getBytes()));
        MimeMessage message = prepareMimeMessage("GETSCRIPT \"" + SCRIPT_NAME + "\"");
        Mail mail = createUnauthenticatedMail(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, USER);
        mailet.service(mail);
        ensureResponse("Re: GETSCRIPT \"" + SCRIPT_NAME + "\"", "{13}\r\n" + SCRIPT_CONTENT + "\r\nOK");
    }

    @Test
    public final void testGetScriptExtraArgs() throws Exception {
        MimeMessage message = prepareMimeMessage("GETSCRIPT \"" + SCRIPT_NAME + "\" extra");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: GETSCRIPT \"" + SCRIPT_NAME + "\" extra", "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testGetScriptNoScript() throws Exception {
        doThrow(new ScriptNotFoundException()).when(sieveRepository).getScript(USER, SCRIPT_NAME);
        MimeMessage message = prepareMimeMessage("GETSCRIPT \"" + SCRIPT_NAME + "\"");
        Mail mail = createUnauthenticatedMail(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, USER);
        mailet.service(mail);
        ensureResponse("Re: GETSCRIPT \"" + SCRIPT_NAME + "\"", "NO (NONEXISTENT) \"There is no script by that name\"");
    }

    @Test
    public final void testGetScriptNoScriptName() throws Exception {
        String scriptContent = "line1\r\nline2";
        sieveRepository.putScript(USER, SCRIPT_NAME, scriptContent);
        MimeMessage message = prepareMimeMessage("GETSCRIPT");
        Mail mail = createUnauthenticatedMail(message);

        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, USER);
        mailet.service(mail);
        ensureResponse("Re: GETSCRIPT", "NO \"Missing argument: script name\"");
    }

    @Test
    public final void testCheckScriptUnauthorised() throws Exception {
        MimeMessage message = prepareMessageWithAttachment(SCRIPT_CONTENT, "CHECKSCRIPT {10+}");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: CHECKSCRIPT {10+}", "NO");
    }

    @Test
    public final void testCheckScript() throws Exception {
        when(sieveParser.parse(SCRIPT_CONTENT)).thenReturn(Lists.newArrayList("warning1", "warning2"));
        MimeMessage message = prepareMessageWithAttachment(SCRIPT_CONTENT, "CHECKSCRIPT {100+}");
        Mail mail = createAuthentificatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: CHECKSCRIPT {100+}", "OK (WARNINGS) \"warning1\" \"warning2\"");
    }

    @Test
    public final void testCheckScriptExtraArgs() throws Exception {
        MimeMessage message = prepareMessageWithAttachment(SCRIPT_CONTENT, "CHECKSCRIPT {10+} extra");
        Mail mail = createAuthentificatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: CHECKSCRIPT {10+} extra", "NO \"Extra arguments not supported\"");
    }

    @Test
    public final void testCheckScriptSyntaxError() throws Exception {
        doThrow(new SyntaxException("error message")).when(sieveParser).parse(SYNTAX_EXCEPTION);
        MimeMessage message = prepareMessageWithAttachment(SYNTAX_EXCEPTION, "CHECKSCRIPT {10+}");
        Mail mail = createAuthentificatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: CHECKSCRIPT {10+}", "NO \"Syntax Error: error message\"");
    }

    @Test
    public final void testCheckScriptNoSize() throws Exception {
        MimeMessage message = prepareMimeMessage("CHECKSCRIPT");
        Mail mail = createAuthentificatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: CHECKSCRIPT", "NO \" is an invalid size literal : it should be at least 4 char looking like {_+}\"");
    }

    @Test
    public final void testCheckScriptNoScript() throws Exception {
        MimeMessage message = prepareMimeMessage("CHECKSCRIPT {10+}");
        Mail mail = createAuthentificatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: CHECKSCRIPT {10+}", "NO \"Missing argument: script content\"");
    }

    @Test
    public final void testDeleteScriptUnauthenticated() throws Exception {
        MimeMessage message = prepareMimeMessage("DELETESCRIPT \"" + SCRIPT_NAME + "\"");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: DELETESCRIPT \"" + SCRIPT_NAME + "\"", "NO");
    }

    @Test
    public final void testDeleteScript() throws Exception {
        MimeMessage message = prepareMimeMessage("DELETESCRIPT \"" + SCRIPT_NAME + "\"");
        Mail mail = createAuthentificatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: DELETESCRIPT \"" + SCRIPT_NAME + "\"", "OK");
    }

    @Test
    public final void testDeleteScriptExtraArgs() throws Exception {
        MimeMessage message = prepareMimeMessage("DELETESCRIPT \"" + SCRIPT_NAME + "\" extra");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: DELETESCRIPT \"" + SCRIPT_NAME + "\" extra", "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testDeleteScriptNoScriptName() throws Exception {
        MimeMessage message = prepareMimeMessage("DELETESCRIPT");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: DELETESCRIPT", "NO \"Missing argument: script name\"");
    }

    @Test
    public final void testHaveSpaceUnauthenticated() throws Exception {
        MimeMessage message = prepareMimeMessage("HAVESPACE \"" + SCRIPT_NAME + "\" 1");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: HAVESPACE \"" + SCRIPT_NAME + "\" 1", "NO");
    }

    @Test
    public final void testHaveSpace() throws Exception {
        MimeMessage message = prepareMimeMessage("HAVESPACE \"" + SCRIPT_NAME + "\" 1");
        Mail mail = createAuthentificatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: HAVESPACE \"" + SCRIPT_NAME + "\" 1", "OK");
    }

    @Test
    public final void testHaveSpaceExtraArgs() throws Exception {
        MimeMessage message = prepareMimeMessage("HAVESPACE \"" + SCRIPT_NAME + "\" 1 extra");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: HAVESPACE \"" + SCRIPT_NAME + "\" 1 extra", "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testHaveSpaceNoScriptName() throws Exception {
        MimeMessage message = prepareMimeMessage("HAVESPACE");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: HAVESPACE", "NO \"Missing argument: script name\"");
    }

    @Test
    public final void testHaveSpaceNoScriptSize() throws Exception {
        MimeMessage message = prepareMimeMessage("HAVESPACE \"" + SCRIPT_NAME + "\"");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: HAVESPACE \"" + SCRIPT_NAME + "\"", "NO \"Missing argument: script size\"");
    }

    @Test
    public final void testHaveSpaceInvalidScriptSize() throws Exception {
        MimeMessage message = prepareMimeMessage("HAVESPACE \"" + SCRIPT_NAME + "\" X");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: HAVESPACE \"" + SCRIPT_NAME + "\" X", "NO \"Invalid argument: script size\"");
    }

    @Test
    public final void testListScriptsUnauthorised() throws Exception {
        MimeMessage message = prepareMimeMessage("LISTSCRIPTS");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: LISTSCRIPTS", "NO");
    }

    @Test
    public final void testListScripts() throws Exception {
        when(sieveRepository.listScripts(USER)).thenReturn(Lists.newArrayList(new ScriptSummary("scriptName2", true), new ScriptSummary("scriptName1", false)));
        MimeMessage message = prepareMimeMessage("LISTSCRIPTS");
        Mail mail = createAuthentificatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: LISTSCRIPTS", "\"scriptName2\" ACTIVE\r\n\"scriptName1\"\r\nOK");
    }

    @Test
    public final void testListScriptsExtraArgs() throws Exception {
        MimeMessage message = prepareMimeMessage("LISTSCRIPTS extra");
        Mail mail = createAuthentificatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: LISTSCRIPTS extra", "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testRenameScriptsUnauthorised() throws Exception {
        sieveRepository.putScript(USER, OLD_SCRIPT_NAME, NEW_SCRIPT_NAME);
        MimeMessage message = prepareMimeMessage("RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\" \"" + NEW_SCRIPT_NAME + "\"");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\" \"" + NEW_SCRIPT_NAME + "\"", "NO");
    }

    @Test
    public final void testRenameScripts() throws Exception {
        sieveRepository.putScript(USER, OLD_SCRIPT_NAME, NEW_SCRIPT_NAME);
        MimeMessage message = prepareMimeMessage("RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\" \"" + NEW_SCRIPT_NAME + "\"");
        Mail mail = createAuthentificatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\" \"" + NEW_SCRIPT_NAME + "\"", "OK");
    }

    @Test
    public final void testRenameScriptsExtraArgs() throws Exception {
        sieveRepository.putScript(USER, OLD_SCRIPT_NAME, NEW_SCRIPT_NAME);
        MimeMessage message = prepareMimeMessage("RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\" \"" + NEW_SCRIPT_NAME + "\" extra");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\" \"" + NEW_SCRIPT_NAME + "\" extra", "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testRenameScriptsNoScriptName() throws Exception {
        sieveRepository.putScript(USER, OLD_SCRIPT_NAME, NEW_SCRIPT_NAME);
        MimeMessage message = prepareMimeMessage("RENAMESCRIPT");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: RENAMESCRIPT", "NO \"Missing argument: old script name\"");
    }

    @Test
    public final void testRenameScriptsNoNewScriptName() throws Exception {
        sieveRepository.putScript(USER, OLD_SCRIPT_NAME, NEW_SCRIPT_NAME);
        MimeMessage message = prepareMimeMessage("RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\"");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: RENAMESCRIPT \"" + OLD_SCRIPT_NAME + "\"", "NO \"Missing argument: new script name\"");
    }

    @Test
    public final void testSetActiveUnauthorised() throws Exception {
        MimeMessage message = prepareMimeMessage("SETACTIVE \"" + SCRIPT_NAME + "\"");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: SETACTIVE \"" + SCRIPT_NAME + "\"", "NO");
    }

    @Test
    public final void testSetActive() throws Exception {
        MimeMessage message = prepareMimeMessage("SETACTIVE \"" + SCRIPT_NAME + "\"");
        Mail mail = createAuthentificatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: SETACTIVE \"" + SCRIPT_NAME + "\"", "OK");
    }

    @Test
    public final void testSetActiveExtraArgs() throws Exception {
        MimeMessage message = prepareMimeMessage("SETACTIVE \"" + SCRIPT_NAME + "\" extra");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: SETACTIVE \"" + SCRIPT_NAME + "\" extra", "NO \"Too many arguments: extra\"");
    }

    @Test
    public final void testSetActiveNoScriptName() throws Exception {
        MimeMessage message = prepareMimeMessage("SETACTIVE");
        Mail mail = createUnauthenticatedMail(message);
        mailet.service(mail);
        ensureResponse("Re: SETACTIVE", "NO \"Missing argument: script name\"");
    }

    private void initializeMailet() throws MessagingException {
        mailet = new ManageSieveMailet();
        mailet.setSieveParser(sieveParser);
        mailet.setSieveRepository(sieveRepository);
        mailet.setUsersRepository(usersRepository);
        fakeMailContext = FakeMailContext.defaultContext();
        FakeMailetConfig config = new FakeMailetConfig("ManageSieve mailet", fakeMailContext);
        config.setProperty("helpURL", "file:./src/test/resources/help.txt");
        mailet.init(config);
    }

    private Mail createUnauthenticatedMail(MimeMessage message) throws Exception {
        FakeMail mail = new FakeMail();
        mail.setMessage(message);
        mail.setSender(new MailAddress(USER));
        mail.setRecipients(Lists.newArrayList(new MailAddress(SIEVE_LOCALHOST)));
        return mail;
    }

    private Mail createAuthentificatedMail(MimeMessage message) throws Exception {
        Mail mail = createUnauthenticatedMail(message);
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
        return mail;
    }

    private MimeMessage prepareMimeMessage(String subject) throws MessagingException {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject(subject);
        message.setSender(new InternetAddress(USER));
        message.setRecipient(RecipientType.TO, new InternetAddress(SIEVE_LOCALHOST));
        message.saveChanges();
        return message;
    }

    private MimeMessage prepareMessageWithAttachment(String scriptContent, String subject) throws MessagingException, IOException {
        MimeMessage message = prepareMimeMessage(subject);
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

    private void ensureResponse(String subject, String... contents) throws MessagingException, IOException {
        MimeMessage result = verifyHeaders(subject);
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

    private void ensureResponseContains(String subject, String... contents) throws MessagingException, IOException {
        MimeMessage result = verifyHeaders(subject);
        MimeMultipart multipart = (MimeMultipart) result.getContent();
        assertThat(((String) multipart.getBodyPart(0).getContent()).split("\r\n")).containsOnly(contents);
    }

    private MimeMessage verifyHeaders(String subject) throws MessagingException {
        assertThat(fakeMailContext.getSentMails()).containsOnly(new FakeMailContext.SentMail(new MailAddress(SIEVE_LOCALHOST), Lists.newArrayList(new MailAddress(USER)), null));
        MimeMessage result = fakeMailContext.getSentMails().get(0).getMsg();
        assertThat(result.getSubject()).isEqualTo(subject);
        assertThat(result.getRecipients(RecipientType.TO)).containsOnly(new InternetAddress(USER));
        return result;
    }
}