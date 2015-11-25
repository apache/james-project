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

import org.apache.james.managesieve.mock.MockSieveParser;
import org.apache.james.managesieve.mock.MockSieveRepository;
import org.apache.james.sieverepository.api.exception.DuplicateUserException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.apache.james.sieverepository.api.exception.UserNotFoundException;
import org.apache.mailet.Mail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.activation.DataHandler;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

public class ManageSieveMailetTestCase {

    ManageSieveMailet _mailet = null;
    SieveRepository _repository = null;
    MockSieveParser _parser = null;

    @Before
    public void setUp() throws Exception {
        _mailet = new ManageSieveMailet();
        _repository = new MockSieveRepository();
        _parser = new MockSieveParser();
        _mailet.setSieveParser(_parser);
        _mailet.setSieveRepository(_repository);
        MockMailetConfig config = new MockMailetConfig(new MockMailetContext());
        config.setInitParameter("helpURL", "file:./src/test/resources/help.txt");
        _mailet.init(config);
    }

    @Test
    public final void testCapability() throws MessagingException, IOException {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("CAPABILITY");
        message.setSender(new InternetAddress("test@localhost"));
        message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
        message.saveChanges();
        Mail mail = new MockMail();
        mail.setMessage(message);

        _parser.setExtensions(Arrays.asList("a", "b", "c"));

        // Unauthorised
        _mailet.service(mail);
        MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
        assertNotNull(result);
        // Check the subject header
        assertEquals("Re: CAPABILITY", result.getSubject());
        // Check the recipient
        Address[] recipients = result.getRecipients(RecipientType.TO);
        assertEquals(1, recipients.length);
        assertEquals(message.getSender(), recipients[0]);
        // Check the response
        MimeMultipart multipart = (MimeMultipart) result.getContent();
        assertEquals(1, multipart.getCount());
        BodyPart part = multipart.getBodyPart(0);
        String response = (String) part.getContent();
        Scanner scanner = new Scanner(response);
        Map<String, String> capabilities = new HashMap<String, String>();
        while (scanner.hasNextLine()) {
            String key = scanner.next();
            String value = null;
            if (scanner.hasNextLine()) {
                value = scanner.nextLine().trim();
            }
            capabilities.put(key, value);
        }
        assertEquals("1.0", capabilities.get("VERSION"));
        assertEquals("a b c", capabilities.get("SIEVE"));
        assertEquals("Apache ManageSieve v1.0", capabilities.get("IMPLEMENTATION"));
        assertEquals(null, capabilities.get("OK"));

        // Authorised
        mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, "test");
        _mailet.service(mail);
        // Check the response
        result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
        multipart = (MimeMultipart) result.getContent();
        assertEquals(1, multipart.getCount());
        part = multipart.getBodyPart(0);
        response = (String) part.getContent();
        scanner = new Scanner(response);
        capabilities = new HashMap<String, String>();
        while (scanner.hasNextLine()) {
            String key = scanner.next();
            String value = null;
            if (scanner.hasNextLine()) {
                value = scanner.nextLine().trim();
            }
            capabilities.put(key, value);
        }
        assertEquals("1.0", capabilities.get("VERSION"));
        assertEquals(message.getSender().toString(), capabilities.get("OWNER"));
        assertEquals("a b c", capabilities.get("SIEVE"));
        assertEquals("Apache ManageSieve v1.0", capabilities.get("IMPLEMENTATION"));
        assertEquals(null, capabilities.get("OK"));

        // Extra arguments should be rejected
        message.setSubject("CAPABILITY extra");
        message.saveChanges();
        _mailet.service(mail);
        result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
        multipart = (MimeMultipart) result.getContent();
        assertEquals(1, multipart.getCount());
        part = multipart.getBodyPart(0);
        response = (String) part.getContent();
        assertEquals("NO \"Too many arguments: extra\"", response);


    }

    @Test
    public final void testPutScript() throws MessagingException, IOException, UserNotFoundException, ScriptNotFoundException, StorageException {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        String scriptName = "scriptName";
        String scriptContent = "scriptContent";
        message.setSubject("PUTSCRIPT \"" + scriptName + "\"");
        message.setSender(new InternetAddress("test@localhost"));
        message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
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
        scriptPart.setFileName(scriptName);
        multipart.addBodyPart(scriptPart);
        message.setContent(multipart);
        message.saveChanges();
        Mail mail = new MockMail();
        mail.setMessage(message);

        // Unauthorised
        {
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            assertNotNull(result);
            // Check the subject header
            assertEquals("Re: PUTSCRIPT \"" + scriptName + "\"", result.getSubject());
            // Check the recipient
            Address[] recipients = result.getRecipients(RecipientType.TO);
            assertEquals(1, recipients.length);
            assertEquals(message.getSender(), recipients[0]);
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO", response.trim());
        }

        // Authorised
        {
            mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals(response, "OK (WARNINGS) \"warning1\" \"warning2\"");
            assertEquals(scriptContent, _repository.getScript(message.getSender().toString(), scriptName));
        }

        // Extra arguments
        {
            message.setSubject("PUTSCRIPT \"" + scriptName + "\" extra");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Too many arguments: extra\"", response);
        }

        // Syntax Error
        {
            message = new MimeMessage(Session.getDefaultInstance(new Properties()));
            message.setSubject("PUTSCRIPT \"" + scriptName + "\"");
            message.setSender(new InternetAddress("test@localhost"));
            message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
            multipart = new MimeMultipart();
            scriptPart = new MimeBodyPart();
            scriptPart.setDataHandler(
                    new DataHandler(
                            new ByteArrayDataSource(
                                    "SyntaxException",
                                    "application/sieve; charset=UTF-8")
                    ));
            scriptPart.setHeader("Content-Type", "application/sieve; charset=UTF-8");
            scriptPart.setDisposition(MimeBodyPart.ATTACHMENT);
            scriptPart.setFileName(scriptName);
            multipart.addBodyPart(scriptPart);
            message.setContent(multipart);
            message.saveChanges();
            mail.setMessage(message);
            mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertTrue(response.startsWith("NO \"Syntax Error: "));
            assertEquals(scriptContent, _repository.getScript(message.getSender().toString(), scriptName));
        }

        // No script
        {
            message = new MimeMessage(Session.getDefaultInstance(new Properties()));
            message.setSubject("PUTSCRIPT \"" + scriptName + "\"");
            message.setSender(new InternetAddress("test@localhost"));
            message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
            message.saveChanges();
            mail.setMessage(message);
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Missing argument: script content\"", response);
        }

        // No script name
        {
            message = new MimeMessage(Session.getDefaultInstance(new Properties()));
            message.setSubject("PUTSCRIPT");
            message.setSender(new InternetAddress("test@localhost"));
            message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
            message.saveChanges();
            mail.setMessage(message);
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Missing argument: script name\"", response);
        }
    }

    @Test
    public final void testGetScript() throws MessagingException, IOException, UserNotFoundException, StorageException, QuotaExceededException, DuplicateUserException {
        String scriptName = "scriptName";
        String scriptContent = "line1\r\nline2";
        String user = "test@localhost";
        _repository.addUser(user);
        _repository.putScript(user, scriptName, scriptContent);
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("GETSCRIPT \"" + scriptName + "\"");
        message.setSender(new InternetAddress(user));
        message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
        message.saveChanges();
        Mail mail = new MockMail();
        mail.setMessage(message);

        // Unauthorised
        {
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            assertNotNull(result);
            // Check the subject header
            assertEquals("Re: GETSCRIPT \"" + scriptName + "\"", result.getSubject());
            // Check the recipient
            Address[] recipients = result.getRecipients(RecipientType.TO);
            assertEquals(1, recipients.length);
            assertEquals(message.getSender(), recipients[0]);
            // Check the response
            MimeMultipart multipart = (MimeMultipart) result.getContent();
            assertEquals(1, multipart.getCount());
            BodyPart part = multipart.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO", response);
        }

        // Authorised
        {
            mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, user);
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart multipart = (MimeMultipart) result.getContent();
            assertEquals(2, multipart.getCount());
            BodyPart part = multipart.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("OK", response);
            BodyPart part2 = multipart.getBodyPart(1);
            String script = null;
            Scanner scanner = null;
            try {
                scanner = new Scanner((InputStream) part2.getContent(), "UTF-8").useDelimiter("\\A");
                script = scanner.next();
            } finally {
                if (null != scanner) {
                    scanner.close();
                }
            }
            assertEquals(scriptContent, script);
        }

        // Extra arguments
        {
            message.setSubject("GETSCRIPT \"" + scriptName + "\" extra");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Too many arguments: extra\"", response);
        }

        // No such script
        {
            message = new MimeMessage(Session.getDefaultInstance(new Properties()));
            message.setSubject("GETSCRIPT \"" + scriptName + "X\"");
            message.setSender(new InternetAddress(user));
            message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
            message.saveChanges();
            mail.setMessage(message);
            mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, user);
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart multipart = (MimeMultipart) result.getContent();
            assertEquals(1, multipart.getCount());
            BodyPart part = multipart.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO (NONEXISTENT) \"There is no script by that name\"", response);
        }

        // No such user
        {
            message = new MimeMessage(Session.getDefaultInstance(new Properties()));
            message.setSubject("GETSCRIPT \"" + scriptName + "\"");
            message.setSender(new InternetAddress(user + "X"));
            message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
            message.saveChanges();
            mail.setMessage(message);
            mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, user);
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart multipart = (MimeMultipart) result.getContent();
            assertEquals(1, multipart.getCount());
            BodyPart part = multipart.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO (NONEXISTENT) \"There is no script by that name\"", response);
        }

        // No script name
        {
            message = new MimeMessage(Session.getDefaultInstance(new Properties()));
            message.setSubject("GETSCRIPT");
            message.setSender(new InternetAddress(user));
            message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
            message.saveChanges();
            mail.setMessage(message);
            mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, user);
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart multipart = (MimeMultipart) result.getContent();
            assertEquals(1, multipart.getCount());
            BodyPart part = multipart.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Missing argument: script name\"", response);
        }

    }


    @Test
    public final void testCheckScript() throws MessagingException, IOException, UserNotFoundException, ScriptNotFoundException {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        String scriptName = "scriptName";
        String scriptContent = "scriptContent";
        message.setSubject("CHECKSCRIPT");
        message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
        message.setSender(new InternetAddress("test@localhost"));
        message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart scriptPart = new MimeBodyPart();
        scriptPart.setDataHandler(
                new DataHandler(
                        new ByteArrayDataSource(
                                scriptContent,
                                "application/sieve; charset=UTF-8")
                ));
        scriptPart.setHeader("Content-Type", "application/sieve; charset=UTF-8");
        scriptPart.setDisposition(MimeBodyPart.ATTACHMENT);
        scriptPart.setFileName(scriptName);
        multipart.addBodyPart(scriptPart);
        message.setContent(multipart);
        message.saveChanges();
        Mail mail = new MockMail();
        mail.setMessage(message);

        // Unauthorised
        {
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            assertNotNull(result);
            // Check the subject header
            assertEquals("Re: CHECKSCRIPT", result.getSubject());
            // Check the recipient
            Address[] recipients = result.getRecipients(RecipientType.TO);
            assertEquals(1, recipients.length);
            assertEquals(message.getSender(), recipients[0]);
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO", response.trim());
        }

        // Authorised
        {
            mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("OK (WARNINGS) \"warning1\" \"warning2\"", response);
        }

        // Extra arguments should be rejected
        {
            message.setSubject("CHECKSCRIPT extra");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Too many arguments: extra\"", response);
        }

        // Syntax Error
        {
            message = new MimeMessage(Session.getDefaultInstance(new Properties()));
            message.setSubject("CHECKSCRIPT");
            message.setSender(new InternetAddress("test@localhost"));
            message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
            multipart = new MimeMultipart();
            scriptPart = new MimeBodyPart();
            scriptPart.setDataHandler(
                    new DataHandler(
                            new ByteArrayDataSource(
                                    "SyntaxException",
                                    "application/sieve; charset=UTF-8")
                    ));
            scriptPart.setHeader("Content-Type", "application/sieve; charset=UTF-8");
            scriptPart.setDisposition(MimeBodyPart.ATTACHMENT);
            scriptPart.setFileName(scriptName);
            multipart.addBodyPart(scriptPart);
            message.setContent(multipart);
            message.saveChanges();
            mail.setMessage(message);
            mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertTrue(response.startsWith("NO \"Syntax Error: "));
        }

        // No script
        {
            message = new MimeMessage(Session.getDefaultInstance(new Properties()));
            message.setSubject("CHECKSCRIPT");
            message.setSender(new InternetAddress("test@localhost"));
            message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
            message.saveChanges();
            mail.setMessage(message);
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Script part not found in this message\"", response);
        }

    }

    @Test
    public final void testDeleteScript() throws DuplicateUserException, StorageException, UserNotFoundException, QuotaExceededException, MessagingException, IOException {
        String scriptName = "scriptName";
        String scriptContent = "line1\r\nline2";
        String user = "test@localhost";
        _repository.addUser(user);
        _repository.putScript(user, scriptName, scriptContent);
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("DELETESCRIPT \"" + scriptName + "\"");
        message.setSender(new InternetAddress(user));
        message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
        message.saveChanges();
        Mail mail = new MockMail();
        mail.setMessage(message);

        // Unauthorised
        {
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            assertNotNull(result);
            // Check the subject header
            assertEquals("Re: DELETESCRIPT \"" + scriptName + "\"", result.getSubject());
            // Check the recipient
            Address[] recipients = result.getRecipients(RecipientType.TO);
            assertEquals(1, recipients.length);
            assertEquals(message.getSender(), recipients[0]);
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO", response.trim());
        }

        // Authorised
        {
            mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("OK", response.trim());
        }

        // Extra arguments
        {
            message.setSubject("DELETESCRIPT \"" + scriptName + "\" extra");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Too many arguments: extra\"", response);
        }

        // No script name
        {
            message.setSubject("DELETESCRIPT");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Missing argument: script name\"", response.trim());
        }

    }

    @Test
    public final void testHaveSpace() throws DuplicateUserException, StorageException, UserNotFoundException, QuotaExceededException, MessagingException, IOException {
        String scriptName = "scriptName";
        String scriptContent = "line1\r\nline2";
        String user = "test@localhost";
        _repository.addUser(user);
        _repository.putScript(user, scriptName, scriptContent);
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("HAVESPACE \"" + scriptName + "\" 1");
        message.setSender(new InternetAddress(user));
        message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
        message.saveChanges();
        Mail mail = new MockMail();
        mail.setMessage(message);

        // Unauthorised
        {
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            assertNotNull(result);
            // Check the subject header
            assertEquals("Re: HAVESPACE \"" + scriptName + "\" 1", result.getSubject());
            // Check the recipient
            Address[] recipients = result.getRecipients(RecipientType.TO);
            assertEquals(1, recipients.length);
            assertEquals(message.getSender(), recipients[0]);
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO", response.trim());
        }

        // Authorised
        {
            mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("OK", response.trim());
        }

        // Extra arguments
        {
            message.setSubject("HAVESPACE \"" + scriptName + "\" 1 extra");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Too many arguments: extra\"", response);
        }

        // No script name
        {
            message.setSubject("HAVESPACE");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Missing argument: script name\"", response.trim());
        }

        // No script size
        {
            message.setSubject("HAVESPACE \"" + scriptName + "\"");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Missing argument: script size\"", response.trim());
        }

        // Invalid script size
        {
            message.setSubject("HAVESPACE \"" + scriptName + "\" X");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Invalid argument: script size\"", response.trim());
        }

    }

    @Test
    public final void testListScripts() throws MessagingException, DuplicateUserException, StorageException, UserNotFoundException, QuotaExceededException, ScriptNotFoundException, IOException {
        String scriptName1 = "scriptName1";
        String scriptName2 = "scriptName2";
        String scriptContent = "line1\r\nline2";
        String user = "test@localhost";
        _repository.addUser(user);
        _repository.putScript(user, scriptName1, scriptContent);
        _repository.putScript(user, scriptName2, scriptContent);
        _repository.setActive(user, scriptName2);
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("LISTSCRIPTS");
        message.setSender(new InternetAddress(user));
        message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
        message.saveChanges();
        Mail mail = new MockMail();
        mail.setMessage(message);

        // Unauthorised
        {
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            assertNotNull(result);
            // Check the subject header
            assertEquals("Re: LISTSCRIPTS", result.getSubject());
            // Check the recipient
            Address[] recipients = result.getRecipients(RecipientType.TO);
            assertEquals(1, recipients.length);
            assertEquals(message.getSender(), recipients[0]);
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO", response.trim());
        }

        // Authorised
        {
            mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("\"scriptName2\" ACTIVE\r\n\"scriptName1\"\r\nOK", response.trim());
        }

        // Extra arguments
        {
            message.setSubject("LISTSCRIPTS extra");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Too many arguments: extra\"", response);
        }

    }

    @Test
    public final void testRenameScripts() throws MessagingException, DuplicateUserException, StorageException, UserNotFoundException, QuotaExceededException, IOException {
        String oldScriptName = "oldScriptName";
        String newScriptName = "newScriptName";
        String scriptContent = "line1\r\nline2";
        String user = "test@localhost";
        _repository.addUser(user);
        _repository.putScript(user, oldScriptName, scriptContent);
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("RENAMESCRIPT \"" + oldScriptName + "\" \"" + newScriptName + "\"");
        message.setSender(new InternetAddress(user));
        message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
        message.saveChanges();
        Mail mail = new MockMail();
        mail.setMessage(message);

        // Unauthorised
        {
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            assertNotNull(result);
            // Check the subject header
            assertEquals("Re: RENAMESCRIPT \"" + oldScriptName + "\" \"" + newScriptName + "\"", result.getSubject());
            // Check the recipient
            Address[] recipients = result.getRecipients(RecipientType.TO);
            assertEquals(1, recipients.length);
            assertEquals(message.getSender(), recipients[0]);
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO", response.trim());
        }

        // Authorised
        {
            mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("OK", response.trim());
        }

        // Extra arguments
        {
            message.setSubject("RENAMESCRIPT \"" + oldScriptName + "\" \"" + newScriptName + "\" extra");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Too many arguments: extra\"", response);
        }

        // No script names
        {
            message.setSubject("RENAMESCRIPT");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Missing argument: old script name\"", response.trim());
        }

        // No new script name
        {
            message.setSubject("RENAMESCRIPT \"" + oldScriptName + "\"");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Missing argument: new script name\"", response.trim());
        }

    }

    @Test
    public final void testSetActive() throws DuplicateUserException, StorageException, UserNotFoundException, QuotaExceededException, MessagingException, IOException {
        String scriptName = "scriptName";
        String scriptContent = "line1\r\nline2";
        String user = "test@localhost";
        _repository.addUser(user);
        _repository.putScript(user, scriptName, scriptContent);
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("SETACTIVE \"" + scriptName + "\"");
        message.setSender(new InternetAddress(user));
        message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
        message.saveChanges();
        Mail mail = new MockMail();
        mail.setMessage(message);

        // Unauthorised
        {
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            assertNotNull(result);
            // Check the subject header
            assertEquals("Re: SETACTIVE \"" + scriptName + "\"", result.getSubject());
            // Check the recipient
            Address[] recipients = result.getRecipients(RecipientType.TO);
            assertEquals(1, recipients.length);
            assertEquals(message.getSender(), recipients[0]);
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO", response.trim());
        }

        // Authorised
        {
            mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, message.getSender().toString());
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("OK", response.trim());
        }

        // Extra arguments
        {
            message.setSubject("SETACTIVE \"" + scriptName + "\" extra");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Too many arguments: extra\"", response);
        }

        // No script name
        {
            message.setSubject("SETACTIVE");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Missing argument: script name\"", response.trim());
        }

        // Deactivated
        {
            message.setSubject("SETACTIVE \"\"");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("OK", response.trim());
            boolean thrown = false;
            try {
                _repository.getActive(user);
            } catch (ScriptNotFoundException ex) {
                thrown = true;
            }
            assertTrue("Expected ScriptNotFoundException", thrown);
        }

    }

    @Test
    public final void testGetActive() throws MessagingException, UserNotFoundException, ScriptNotFoundException, StorageException, QuotaExceededException, DuplicateUserException, IOException {
        String scriptName = "scriptName";
        String scriptContent = "line1\r\nline2";
        String user = "test@localhost";
        _repository.addUser(user);
        _repository.putScript(user, scriptName, scriptContent);
        _repository.setActive(user, scriptName);
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("GETACTIVE");
        message.setSender(new InternetAddress(user));
        message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
        message.saveChanges();
        Mail mail = new MockMail();
        mail.setMessage(message);

        // Unauthorised
        {
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            assertNotNull(result);
            // Check the subject header
            assertEquals("Re: GETACTIVE", result.getSubject());
            // Check the recipient
            Address[] recipients = result.getRecipients(RecipientType.TO);
            assertEquals(1, recipients.length);
            assertEquals(message.getSender(), recipients[0]);
            // Check the response
            MimeMultipart multipart = (MimeMultipart) result.getContent();
            assertEquals(1, multipart.getCount());
            BodyPart part = multipart.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO", response);
        }

        // Authorised
        {
            mail.setAttribute(ManageSieveMailet.SMTP_AUTH_USER_ATTRIBUTE_NAME, user);
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart multipart = (MimeMultipart) result.getContent();
            assertEquals(2, multipart.getCount());
            BodyPart part = multipart.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("OK", response);
            BodyPart part2 = multipart.getBodyPart(1);
            String script = null;
            Scanner scanner = null;
            try {
                scanner = new Scanner((InputStream) part2.getContent(), "UTF-8").useDelimiter("\\A");
                script = scanner.next();
            } finally {
                if (null != scanner) {
                    scanner.close();
                }
            }
            assertEquals(scriptContent, script);
        }

        // Extra arguments
        {
            message.setSubject("GETACTIVE extra");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("NO \"Too many arguments: extra\"", response);
        }

        // Deactivated
        {
            _repository.setActive(user, "");
            message.setSubject("GETACTIVE");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            // Check the response
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertTrue(response.trim().startsWith("NO (NONEXISTENT)"));
        }

    }

    @Ignore("Ignore this test as it depends of your environment (file path changes with your position in the project)")
    @Test
    public final void testHelp() throws MessagingException, IOException {
        String user = "test@localhost";
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("HELP");
        message.setSender(new InternetAddress(user));
        message.setRecipient(RecipientType.TO, new InternetAddress("sieve@localhost"));
        message.saveChanges();
        Mail mail = new MockMail();
        mail.setMessage(message);

        // Explicit invocation
        {
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            assertNotNull(result);
            // Check the subject header
            assertEquals("Re: HELP", result.getSubject());
            // Check the recipient
            Address[] recipients = result.getRecipients(RecipientType.TO);
            assertEquals(1, recipients.length);
            assertEquals(message.getSender(), recipients[0]);
            // Check the response
            MimeMultipart multipart = (MimeMultipart) result.getContent();
            assertEquals(1, multipart.getCount());
            BodyPart part = multipart.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("Help text", response);
        }

        // Extra arguments
        {
            message.setSubject("HELP extra");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            MimeMultipart content = (MimeMultipart) result.getContent();
            assertEquals(1, content.getCount());
            BodyPart part = content.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("Help text", response);
        }

        // Implicit invocation - no subject header
        {
            message.removeHeader("subject");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            assertNotNull(result);
            // Check the subject header
            assertEquals(null, result.getSubject());
            // Check the response
            MimeMultipart multipart = (MimeMultipart) result.getContent();
            assertEquals(1, multipart.getCount());
            BodyPart part = multipart.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("Help text", response);
        }

        // Implicit invocation - empty subject
        {
            message.setSubject("");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            assertNotNull(result);
            // Check the subject header
            // Javamail returns "Re: " instead Geronimo returns "Re:" (no trailing space)
            assertEquals("Re:", result.getSubject().trim());
            // Check the response
            MimeMultipart multipart = (MimeMultipart) result.getContent();
            assertEquals(1, multipart.getCount());
            BodyPart part = multipart.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("Help text", response);
        }

        // Implicit invocation - invalid command
        {
            message.setSubject("INVALID");
            message.saveChanges();
            _mailet.service(mail);
            MimeMessage result = ((MockMailetContext) _mailet.getMailetContext()).getMessage();
            assertNotNull(result);
            // Check the subject header
            assertEquals("Re: INVALID", result.getSubject());
            // Check the response
            MimeMultipart multipart = (MimeMultipart) result.getContent();
            assertEquals(1, multipart.getCount());
            BodyPart part = multipart.getBodyPart(0);
            String response = (String) part.getContent();
            assertEquals("Help text", response);
        }
    }

    /*
    @Test
    public final void testMessageWrap() throws MessagingException, IOException {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("CAPABILITY");
        message.setSender(new InternetAddress("test@localhost"));
        message.saveChanges();
        Mail mail = new MockMail();
        mail.setMessage(message);
        
        _parser.setExtensions(Arrays.asList(new String[]{"a","b","c"}));
        
        _mailet.service(mail);
        MimeMessage result = ((MockMailetContext)_mailet.getMailetContext()).getMessage();
        MimeMessageWrapper wrapped = new MimeMessageWrapper(result);
    }
    */
}
