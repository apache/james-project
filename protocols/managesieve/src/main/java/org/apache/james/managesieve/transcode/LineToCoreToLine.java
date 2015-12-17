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

package org.apache.james.managesieve.transcode;

import org.apache.james.managesieve.api.ArgumentException;
import org.apache.james.managesieve.api.AuthenticationRequiredException;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.SyntaxException;
import org.apache.james.managesieve.api.commands.Capability.Capabilities;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;

import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Calls the parser (that handles interactions with the processor)
 * and format the outputs into an answer.
 *
 * You can use this as a base for implementing a manageSieve server.
 *
 * Note : you still need to identify the given commands.
 */
public class LineToCoreToLine {
    
    private final LineToCore lineToCore;
    
    public LineToCoreToLine(LineToCore lineToCore) {
        this.lineToCore = lineToCore;
    }    

    public String capability(Session session, String args) {
        try {
            Set<Entry<Capabilities, String>> entries = lineToCore.capability(session, args).entrySet();
            StringBuilder builder = new StringBuilder();
            for (Entry<Capabilities, String> entry : entries) {
                builder.append(entry.getKey().toString())
                    .append(' ')
                    .append(entry.getValue() == null ? "" : entry.getValue())
                    .append("\r\n");
            }
            builder.append("OK");
            return builder.toString();
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }
    }

    public String noop(String args) {
        return lineToCore.noop(args);
    }

    public String unauthenticate(Session session, String args) {
        return lineToCore.unauthenticate(session, args);
    }

    public String checkScript(Session session, String args) {
        try {
            List<String> warnings = lineToCore.checkScript(session, args);
            StringBuilder builder = new StringBuilder();
            if (!warnings.isEmpty()) {
                builder.append("OK (WARNINGS)");
                for (String warning : warnings) {
                    builder.append(" \"")
                        .append(warning)
                        .append('"');
                }
            } else {
                builder.append("OK");
            }
            return builder.toString();
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        } catch (SyntaxException ex) {
            return "NO \"Syntax Error: " + ex.getMessage() + "\"";
        }
    }

    public String deleteScript(Session session, String args) {
        try {
            lineToCore.deleteScript(session, args);
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ScriptNotFoundException ex) {
            return "NO (NONEXISTENT) \"There is no script by that name\"";
        } catch (IsActiveException ex) {
            return "NO (ACTIVE) \"You may not delete an active script\"";
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }
        return "OK";
    }

    public String getScript(Session session, String args) {
        try {
            return lineToCore.getScript(session, args) + "\r\n" + "OK";
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ScriptNotFoundException ex) {
            return "NO (NONEXISTENT) \"There is no script by that name\"";
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        } catch (StorageException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }

    }

    public String haveSpace(Session session, String args) {
        try {
            lineToCore.haveSpace(session, args);
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (QuotaExceededException ex) {
            return "NO (QUOTA/MAXSIZE) \"Quota exceeded\"";
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }
        return "OK";
    }

    public String listScripts(Session session, String args) {
        try {
            List<ScriptSummary> summaries = lineToCore.listScripts(session, args);
            StringBuilder builder = new StringBuilder();
            for (ScriptSummary summary : summaries) {
                builder.append('"')
                    .append(summary.getName())
                    .append('"');
                if (summary.isActive()) {
                    builder.append(' ')
                        .append("ACTIVE");
                }
                builder.append("\r\n");
            }
            builder.append("OK");
            return builder.toString();
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }
    }

    public String putScript(Session session, String args) {
        try {
            List<String> warnings = lineToCore.putScript(session, args);
            StringBuilder builder = new StringBuilder();
            if (!warnings.isEmpty()) {
                builder.append("OK (WARNINGS)");
                for (String warning : warnings) {
                    builder
                        .append(" \"")
                        .append(warning)
                        .append('"');
                }
            } else {
                builder.append("OK");
            }
            return builder.toString();
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (SyntaxException ex) {
            return "NO \"Syntax Error: " + ex.getMessage() + "\"";
        } catch (QuotaExceededException ex) {
            return "NO (QUOTA/MAXSIZE) \"Quota exceeded\"";
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }

    }

    public String renameScript(Session session, String args) {
        try {
            lineToCore.renameScript(session, args);
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ScriptNotFoundException ex) {
            return "NO (NONEXISTENT) \"There is no script by that name\"";
        }  catch (DuplicateException ex) {
            return "NO (ALREADYEXISTS) \"A script with that name already exists\"";
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }
        return "OK";
    }

    public String setActive(Session session, String args) {
        try {
            lineToCore.setActive(session, args);
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ScriptNotFoundException ex) {
            return "NO (NONEXISTENT) \"There is no script by that name\"";
        }  catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }
        return "OK";
    }
    
    public String getActive(Session session, String args) {
        try {
            return lineToCore.getActive(session, args) + "\r\n" + "OK";
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ScriptNotFoundException ex) {
            return "NO (NONEXISTENT) \"" + ex.getMessage() + "\"";
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        } catch (StorageException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }
    }

}
