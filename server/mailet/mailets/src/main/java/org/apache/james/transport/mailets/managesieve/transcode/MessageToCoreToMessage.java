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

package org.apache.james.transport.mailets.managesieve.transcode;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import javax.activation.DataHandler;
import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.transcode.LineToCoreToLine;
import org.apache.james.managesieve.util.ParserUtils;

public class MessageToCoreToMessage {
    
    public interface HelpProvider {
        String getHelp() throws MessagingException;
    }

    private interface Executable {
        MimeMultipart execute(Session session, String operands, MimeMessage message) throws MessagingException;
    }

    protected static String getScript(MimeMessage message) throws IOException, MessagingException {
        String result = null;
        if (message.getContentType().startsWith("multipart/")) {
            MimeMultipart parts = (MimeMultipart) message.getContent();
            boolean found = false;
            // Find the first part with any of:
            // - an attachment type of "application/sieve"
            // - a file suffix of ".siv"
            // - a file suffix of ".sieve"
            for (int i = 0; !found && i < parts.getCount(); i++) {
                MimeBodyPart part = (MimeBodyPart) parts.getBodyPart(i);
                found = part.isMimeType("application/sieve");
                if (!found) {
                    String fileName = null == part.getFileName() ? null : part.getFileName().toLowerCase();
                    found = fileName != null &&
                        (fileName.endsWith(".siv") || fileName.endsWith(".sieve"));
                }
                if (found) {
                    Object content = part.getContent();
                    if (content instanceof String) {
                        return (String) part.getContent();
                    }
                    InputStream is = (InputStream) part.getContent();
                    Scanner scanner = null;
                    try {
                        scanner = new Scanner(is, "UTF-8").useDelimiter("\\A");
                        if (scanner.hasNext()) {
                            result = scanner.next();
                        }
                    } finally {
                        if (scanner != null) {
                            scanner.close();
                        }
                    }
                }
            }
        }
        if (null == result) {
            throw new MessagingException("Script part not found in this message");
        }
        return result;
    }

    protected static MimeBodyPart toPart(String name, String content) throws MessagingException, IOException {
        MimeBodyPart scriptPart = new MimeBodyPart();
        scriptPart.setDataHandler(
            new DataHandler(
                new ByteArrayDataSource(
                    content,
                    "application/sieve; charset=UTF-8")
            ));
        scriptPart.setDisposition(MimeBodyPart.ATTACHMENT);
        scriptPart.setFileName(name);
        return scriptPart;
    }

    protected static MimeBodyPart toPart(String message) throws MessagingException {
        MimeBodyPart part = new MimeBodyPart();
        part.setText(message);
        part.setDisposition(MimeBodyPart.INLINE);
        return part;
    }

    private final Map<String, Executable> commands;
    private final LineToCoreToLine adapter;
    private final HelpProvider helpProvider;
    
    public MessageToCoreToMessage(LineToCoreToLine adapter, HelpProvider helpProvider) {
        this.commands = computeCommands();
        this.adapter = adapter;
        this.helpProvider = helpProvider;
    }
    
    public MimeMessage execute(Session session, MimeMessage message) throws MessagingException {
        // Extract the command and operands from the subject
        String subject = null == message.getSubject() ? "" : message.getSubject();
        String[] args = subject.split(" ", 2);
        // If there are no arguments, reply with help
        String command = 0 == args.length ? "HELP" : args[0].toUpperCase();
        Executable executable;
        // If the command isn't supported, reply with help
        if (null == (executable = commands.get(command))) {
            executable = commands.get("HELP");
        }
        // Execute the resultant command...
        MimeMultipart content = executable.execute(session, args.length > 1 ? args[1] : "", message);
        // ...and wrap it in a MimeMessage
        MimeMessage reply = (MimeMessage) message.reply(false);
        reply.setContent(content);
        if (null == message.getAllRecipients() || 0 >= message.getAllRecipients().length) {
            throw new MessagingException("Message has no recipients");
        } else {
            Address from = message.getAllRecipients()[0];
            reply.setFrom(from);
        }
        reply.saveChanges();
        return reply;
    }
    
    protected Map<String, Executable> computeCommands() {
        Map<String, Executable> commands = new HashMap<String, Executable>();
        commands.put("HELP", new Executable() {
            public MimeMultipart execute(Session session, String operands, MimeMessage message) throws MessagingException {
                return help();
            }
        });
        commands.put("CAPABILITY", new Executable() {
            public MimeMultipart execute(Session session, String operands, MimeMessage message) throws MessagingException {
                return capability(session, operands);
            }
        });
        commands.put("CHECKSCRIPT", new Executable() {
            public MimeMultipart execute(Session session, String operands, MimeMessage message) throws MessagingException {
                return checkScript(session, operands, message);
            }
        });
        commands.put("DELETESCRIPT", new Executable() {
            public MimeMultipart execute(Session session, String operands, MimeMessage message)
                    throws MessagingException {
                return deleteScript(session, operands);
            }
        });
        commands.put("GETSCRIPT", new Executable() {
            public MimeMultipart execute(Session session, String operands, MimeMessage message) throws MessagingException {
                return getScript(session, operands);
            }
        });
        commands.put("HAVESPACE", new Executable() {
            public MimeMultipart execute(Session session, String operands, MimeMessage message) throws MessagingException {
                return haveSpace(session, operands);
            }
        });
        commands.put("LISTSCRIPTS", new Executable() {
            public MimeMultipart execute(Session session, String operands, MimeMessage message) throws MessagingException {
                return listScripts(session, operands);
            }
        });
        commands.put("PUTSCRIPT", new Executable() {

            public MimeMultipart execute(Session session, String operands, MimeMessage message) throws MessagingException {
                return putScript(session, operands, message);
            }
        });
        commands.put("RENAMESCRIPT", new Executable() {

            public MimeMultipart execute(Session session, String operands, MimeMessage message) throws MessagingException {
                return renameScript(session, operands);
            }
        });
        commands.put("SETACTIVE", new Executable() {

            public MimeMultipart execute(Session session, String operands, MimeMessage message) throws MessagingException {
                return setActive(session, operands);
            }
        });
        commands.put("GETACTIVE", new Executable() {

            public MimeMultipart execute(Session session, String operands, MimeMessage message) throws MessagingException {
                return getActive(session, operands);
            }
        });

        return commands;
    }

    protected MimeMultipart help() throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(helpProvider.getHelp()));
        return multipart;
    }

    protected MimeMultipart capability(Session session, String operands) throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(adapter.capability(session, operands)));
        return multipart;
    }

    protected MimeMultipart checkScript(Session session, String operands, MimeMessage message) throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        String result;
        Scanner scanner = new Scanner(operands).useDelimiter("\\A");
        if (scanner.hasNext()) {
            result = "NO \"Too many arguments: " + scanner.next() + "\"";
        } else {
            try {
                String content = getScript(message);
                result = adapter.checkScript(session, content);
            } catch (MessagingException ex) {
                result = "NO \"" + ex.getMessage() + "\"";
            } catch (IOException ex) {
                result = "NO \"Failed to read script part\"";
            }
        }
        multipart.addBodyPart(toPart(result));
        return multipart;
    }

    protected MimeMultipart deleteScript(Session session, String operands) throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(adapter.deleteScript(session, operands)));
        return multipart;
    }

    protected MimeMultipart getScript(Session session, String operands) throws MessagingException {
        String result = adapter.getScript(session, operands);
        // Everything but the last line is the script
        // The last line is the response
        String response;
        String script = null;
        int endOfScript = result.lastIndexOf("\r\n");
        if (endOfScript > 0) {
            script = result.substring(0, endOfScript);
            response = result.substring(endOfScript + "\r\n".length());
        } else {
            response = result;
        }

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(response));
        if (null != script) {
            try {
                multipart.addBodyPart(toPart(ParserUtils.unquote(ParserUtils
                            .getScriptName(operands)), script));
            } catch (IOException ex) {
                throw new MessagingException("Failed to add script part", ex);
            }
        }
        return multipart;
    }

    protected MimeMultipart haveSpace(Session session, String operands) throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(adapter.haveSpace(session, operands)));
        return multipart;
    }

    protected MimeMultipart listScripts(Session session, String operands) throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(adapter.listScripts(session, operands)));
        return multipart;
    }

    protected MimeMultipart putScript(Session session, String operands, MimeMessage message) throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        String result;
        String scriptName = ParserUtils.getScriptName(operands);
        if (null == scriptName || scriptName.isEmpty()) {
            result = "NO \"Missing argument: script name\"";
        } else {
            Scanner scanner = new Scanner(operands.substring(scriptName.length()).trim())
                    .useDelimiter("\\A");
            if (scanner.hasNext()) {
                result = "NO \"Too many arguments: " + scanner.next() + "\"";
            } else {
                StringBuilder builder = new StringBuilder(scriptName);
                String content = null;
                try {
                    content = getScript(message);
                } catch (MessagingException ex) {
                    result = "NO \"" + ex.getMessage() + "\"";
                } catch (IOException ex) {
                    result = "NO \"Failed to read script part\"";
                }
                if (null != content) {
                    builder
                            .append(' ')
                            .append(content);
                }
                result = adapter.putScript(session, builder.toString().trim());
            }
        }
        multipart.addBodyPart(toPart(result));
        return multipart;
    }

    protected MimeMultipart renameScript(Session session, String operands) throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(adapter.renameScript(session, operands)));
        return multipart;
    }

    protected MimeMultipart setActive(Session session, String operands) throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(adapter.setActive(session, operands)));
        return multipart;
    }

    protected MimeMultipart getActive(Session session, String operands) throws MessagingException {
        String result = adapter.getActive(session, operands);
        adapter.getActive(session, operands);
        // Everything but the last line is the script
        // The last line is the response
        String response;
        String script = null;
        int endOfScript = result.lastIndexOf("\r\n");
        if (endOfScript > 0) {
            script = result.substring(0, endOfScript);
            response = result.substring(endOfScript + "\r\n".length());
        } else {
            response = result;
        }

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(response));
        if (null != script) {
            try {
                multipart.addBodyPart(toPart("active", script));
            } catch (IOException ex) {
                throw new MessagingException("Failed to add script part", ex);
            }
        }
        return multipart;
    }

}
