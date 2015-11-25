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

import org.apache.james.managesieve.transcode.LineToCoreToLine;
import org.apache.james.managesieve.util.ParserUtils;

/**
 * <code>MessageToCoreToMessage</code>
 */
public class MessageToCoreToMessage {
    
    public interface HelpProvider {
        abstract public String getHelp() throws MessagingException;
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
                    String fileName = null == part.getFileName() ? null : part.getFileName()
                            .toLowerCase();
                    found = null != fileName
                            && (fileName.endsWith(".siv") || fileName.endsWith(".sieve"));
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
                        if (null != scanner) {
                            scanner.close();
                        }
                    }
                }
            }
        }
        if (null == result)
        {
            throw new MessagingException("Script part not found in this message");
        }
        return result;
    }

    protected static MimeBodyPart toPart(String name, String content) throws MessagingException,
            IOException {
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
    
    private interface Executable {
        public MimeMultipart execute(String operands, MimeMessage message)
                throws MessagingException;
    }

    private Map<String, Executable> _commands = null;
    
    private LineToCoreToLine _adapter = null;
    
    private HelpProvider _helpProvider = null;
    
    private MessageToCoreToMessage()
    {
        super();
        _commands = computeCommands();
    }
    
    public MessageToCoreToMessage(LineToCoreToLine adapter, HelpProvider helpProvider)
    {
        this();
        _adapter = adapter;
        _helpProvider = helpProvider;
    }
    
    public MimeMessage execute(MimeMessage message) throws MessagingException {
        // Extract the command and operands from the subject
        String subject = null == message.getSubject() ? "" : message.getSubject();
        String[] args = subject.split(" ", 2);
        // If there are no arguments, reply with help
        String command = 0 == args.length ? "HELP" : args[0].toUpperCase();
        Executable executable = null;
        // If the command isn't supported, reply with help
        if (null == (executable = _commands.get(command))) {
            executable = _commands.get("HELP");
        }
        // Execute the resultant command...
        MimeMultipart content = executable.execute(args.length > 1 ? args[1] : "", message);
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
            public MimeMultipart execute(String operands, MimeMessage message)
                    throws MessagingException {
                return help(operands, message);
            }
        });
        commands.put("CAPABILITY", new Executable() {

            public MimeMultipart execute(String operands, MimeMessage message)
                    throws MessagingException {
                return capability(operands, message);
            }
        });
        commands.put("CHECKSCRIPT", new Executable() {

            public MimeMultipart execute(String operands, MimeMessage message)
                    throws MessagingException {
                return checkScript(operands, message);
            }
        });
        commands.put("DELETESCRIPT", new Executable() {

            public MimeMultipart execute(String operands, MimeMessage message)
                    throws MessagingException {
                return deleteScript(operands, message);
            }
        });
        commands.put("GETSCRIPT", new Executable() {

            public MimeMultipart execute(String operands, MimeMessage message)
                    throws MessagingException {
                return getScript(operands, message);
            }
        });
        commands.put("HAVESPACE", new Executable() {

            public MimeMultipart execute(String operands, MimeMessage message)
                    throws MessagingException {
                return haveSpace(operands, message);
            }
        });
        commands.put("LISTSCRIPTS", new Executable() {

            public MimeMultipart execute(String operands, MimeMessage message)
                    throws MessagingException {
                return listScripts(operands, message);
            }
        });
        commands.put("PUTSCRIPT", new Executable() {

            public MimeMultipart execute(String operands, MimeMessage message)
                    throws MessagingException {
                return putScript(operands, message);
            }
        });
        commands.put("RENAMESCRIPT", new Executable() {

            public MimeMultipart execute(String operands, MimeMessage message)
                    throws MessagingException {
                return renameScript(operands, message);
            }
        });
        commands.put("SETACTIVE", new Executable() {

            public MimeMultipart execute(String operands, MimeMessage message)
                    throws MessagingException {
                return setActive(operands, message);
            }
        });
        commands.put("GETACTIVE", new Executable() {

            public MimeMultipart execute(String operands, MimeMessage message)
                    throws MessagingException {
                return getActive(operands, message);
            }
        });

        return commands;
    }

    protected MimeMultipart help(String operands, MimeMessage message)
                throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(_helpProvider.getHelp()));
        return multipart;
    }

    protected MimeMultipart capability(String operands, MimeMessage message)
                throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(_adapter.capability(operands)));
        return multipart;
    }

    protected MimeMultipart checkScript(String operands, MimeMessage message)
                throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        String result = null;
        Scanner scanner = new Scanner(operands).useDelimiter("\\A");
        if (scanner.hasNext()) {
            result = "NO \"Too many arguments: " + scanner.next() + "\"";
        } else {
            try {
                String content = getScript(message);
                result = _adapter.checkScript(content);
            } catch (MessagingException ex) {
                result = "NO \"" + ex.getMessage() + "\"";
            } catch (IOException ex) {
                result = "NO \"Failed to read script part\"";
            }
        }
        multipart.addBodyPart(toPart(result));
        return multipart;
    }

    protected MimeMultipart deleteScript(String operands, MimeMessage message)
                throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(_adapter.deleteScript(operands)));
        return multipart;
    }

    protected MimeMultipart getScript(String operands, MimeMessage message)
                throws MessagingException {
        String result = _adapter.getScript(operands);
        // Everything but the last line is the script
        // The last line is the response
        String response = null;
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

    protected MimeMultipart haveSpace(String operands, MimeMessage message)
                throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(_adapter.haveSpace(operands)));
        return multipart;
    }

    protected MimeMultipart listScripts(String operands, MimeMessage message)
                throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(_adapter.listScripts(operands)));
        return multipart;
    }

    protected MimeMultipart putScript(String operands, MimeMessage message)
                throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        String result = null;
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
                result = _adapter.putScript(builder.toString().trim());
            }
        }
        multipart.addBodyPart(toPart(result));
        return multipart;
    }

    protected MimeMultipart renameScript(String operands, MimeMessage message)
                throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(_adapter.renameScript(operands)));
        return multipart;
    }

    protected MimeMultipart setActive(String operands, MimeMessage message)
                throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(_adapter.setActive(operands)));
        return multipart;
    }

    protected MimeMultipart getActive(String operands, MimeMessage message)
            throws MessagingException {
        String result = _adapter.getActive(operands);
        _adapter.getActive(operands);
        // Everything but the last line is the script
        // The last line is the response
        String response = null;
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
