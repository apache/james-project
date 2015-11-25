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
 * <code>LineToCoreToLine</code>
 */
public class LineToCoreToLine {
    
    private LineToCore _lineToCore = null;

    /**
     * Creates a new instance of LineToCoreToLine.
     *
     */
    private LineToCoreToLine() {
        super();
    }
    
    public LineToCoreToLine(LineToCore lineToCore) {
        this();
        _lineToCore = lineToCore;
    }    

    public String capability(String args) {       
        Set<Entry<Capabilities, String>> entries = null;
        try {
            entries =_lineToCore.capability(args).entrySet(); 
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }
        
        StringBuilder builder = new StringBuilder();
        for (Entry<Capabilities, String> entry : entries)
        {
            builder
                .append(entry.getKey().toString())
                .append(' ')
                .append(null == entry.getValue() ? "" : entry.getValue())
                .append("\r\n");
        }
        builder.append("OK");
        return builder.toString();
    }

    public String checkScript(String args) {
        List<String> warnings = null;
        try {
            warnings = _lineToCore.checkScript(args);
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        } catch (SyntaxException ex) {
            return "NO \"Syntax Error: " + ex.getMessage() + "\"";
        }

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
    }

    public String deleteScript(String args) {
        try {
            _lineToCore.deleteScript(args);
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

    public String getScript(String args) {
        String content = null;
        try {
            content = _lineToCore.getScript(args);
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ScriptNotFoundException ex) {
            return "NO (NONEXISTENT) \"There is no script by that name\"";
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        } catch (StorageException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }
        StringBuilder builder = new StringBuilder(content);
        builder
            .append("\r\n")
            .append("OK");
        return builder.toString();
    }

    public String haveSpace(String args) {
        try {
            _lineToCore.haveSpace(args);
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (QuotaExceededException ex) {
            return "NO (QUOTA/MAXSIZE) \"Quota exceeded\"";
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }
        return "OK";
    }

    public String listScripts(String args) {
        List<ScriptSummary> summaries = null;
        try {
            summaries = _lineToCore.listScripts(args);
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }
        StringBuilder builder = new StringBuilder();
        for (ScriptSummary summary : summaries)
        {
            builder
                .append('"')
                .append(summary.getName())
                .append('"');
            if (summary.isActive())
            {
                builder
                    .append(' ')
                    .append("ACTIVE");
            }
            builder
                .append("\r\n");
        }
        builder.append("OK");
        return builder.toString();
    }

    public String putScript(String args) {
        List<String> warnings = null;
        try {
            warnings = _lineToCore.putScript(args);
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (SyntaxException ex) {
            return "NO \"Syntax Error: " + ex.getMessage() + "\"";
        } catch (QuotaExceededException ex) {
            return "NO (QUOTA/MAXSIZE) \"Quota exceeded\"";
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }
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
    }

    public String renameScript(String args) {
        try {
            _lineToCore.renameScript(args);
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

    public String setActive(String args) {
        try {
            _lineToCore.setActive(args);
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ScriptNotFoundException ex) {
            return "NO (NONEXISTENT) \"There is no script by that name\"";
        }  catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }
        return "OK";
    }
    
    public String getActive(String args) {
        String content = null;
        try {
            content = _lineToCore.getActive(args);
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ScriptNotFoundException ex) {
            return "NO (NONEXISTENT) \"" + ex.getMessage() + "\"";
        } catch (ArgumentException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        } catch (StorageException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        }
        StringBuilder builder = new StringBuilder(content);
        builder
            .append("\r\n")
            .append("OK");
        return builder.toString();
    }

}
