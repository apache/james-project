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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.apache.james.managesieve.api.ArgumentException;
import org.apache.james.managesieve.api.AuthenticationException;
import org.apache.james.managesieve.api.AuthenticationRequiredException;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.SessionTerminatedException;
import org.apache.james.managesieve.api.SyntaxException;
import org.apache.james.managesieve.api.UnknownSaslMechanism;
import org.apache.james.managesieve.api.commands.Capability.Capabilities;
import org.apache.james.managesieve.api.commands.CoreCommands;
import org.apache.james.managesieve.util.ParserUtils;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.apache.james.sieverepository.api.exception.UserNotFoundException;

import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Parses the user input and calls the underlying command processor
 */
public class LineToCore{
    
    private final CoreCommands core;

    public LineToCore(CoreCommands core) {
        this.core = core;
    }
    
    public Map<Capabilities, String> capability(Session session, String args) throws ArgumentException {
        if (!args.trim().isEmpty()) {
            throw new ArgumentException("Too many arguments: " + args);
        }
        return core.capability(session);
    }

    public String noop(String args) {
        return core.noop(args);
    }

    public String unauthenticate(Session session, String args) {
        if (Strings.isNullOrEmpty(args)) {
            return core.unauthenticate(session);
        } else {
            return "NO UNAUTHENTICATE do not take arguments";
        }
    }

    public void logout() throws SessionTerminatedException {
        core.logout();
    }

    public String chooseMechanism(Session session, String mechanism) throws AuthenticationException, UnknownSaslMechanism, SyntaxException {
        return core.chooseMechanism(session, mechanism);
    }

    public String authenticate(Session session, String suppliedData) throws AuthenticationException, SyntaxException {
        return core.authenticate(session, suppliedData);
    }
    
    public void deleteScript(Session session, String args) throws AuthenticationRequiredException, ScriptNotFoundException, IsActiveException, ArgumentException {
        String scriptName = ParserUtils.getScriptName(args);
        if (null == scriptName || scriptName.isEmpty()) {
            throw new ArgumentException("Missing argument: script name");
        }
        
        Scanner scanner = new Scanner(args.substring(scriptName.length()).trim()).useDelimiter("\\A");
        if (scanner.hasNext()) {
            throw new ArgumentException("Too many arguments: " + scanner.next());
        }
        core.deleteScript(session, ParserUtils.unquote(scriptName));
    }    
    
    public String getScript(Session session, String args) throws AuthenticationRequiredException, ScriptNotFoundException, ArgumentException, StorageException {
        String scriptName = ParserUtils.getScriptName(args);
        if (null == scriptName || scriptName.isEmpty()) {
            throw new ArgumentException("Missing argument: script name");
        }
        Scanner scanner = new Scanner(args.substring(scriptName.length()).trim()).useDelimiter("\\A");
        if (scanner.hasNext()) {
            throw new ArgumentException("Too many arguments: " + scanner.next());
        }
        return core.getScript(session, ParserUtils.unquote(scriptName));
    }     
    
    public List<String> checkScript(Session session, String args) throws ArgumentException, AuthenticationRequiredException, SyntaxException {
        Iterator<String> firstLine = Splitter.on("\r\n").split(args.trim()).iterator();
        Iterator<String> arguments = Splitter.on(' ').split(firstLine.next().trim()).iterator();


        if (! arguments.hasNext()) {
            throw new ArgumentException("Missing argument: script size");
        } else {
            ParserUtils.getSize(arguments.next());
        }
        if (arguments.hasNext()) {
            throw new ArgumentException("Extra arguments not supported");
        } else {
            String content = Joiner.on("\r\n").join(firstLine);
            if (Strings.isNullOrEmpty(content)) {
                throw new ArgumentException("Missing argument: script content");
            }
            return core.checkScript(session, content);
        }
    }

    public void haveSpace(Session session, String args) throws AuthenticationRequiredException, QuotaExceededException, ArgumentException, UserNotFoundException, StorageException {
        String scriptName = ParserUtils.getScriptName(args);
        if (null == scriptName || scriptName.isEmpty()) {
            throw new ArgumentException("Missing argument: script name");
        }

        Scanner scanner = new Scanner(args.substring(scriptName.length()).trim());
        long size;
        try {
            size = scanner.nextLong();
        } catch (InputMismatchException ex) {
            throw new ArgumentException("Invalid argument: script size");
        } catch (NoSuchElementException ex) {
            throw new ArgumentException("Missing argument: script size");
        }

        scanner.useDelimiter("\\A");
        if (scanner.hasNext()) {
            throw new ArgumentException("Too many arguments: " + scanner.next().trim());
        }
        core.haveSpace(session, ParserUtils.unquote(scriptName), size);
    }

    public List<ScriptSummary> listScripts(Session session, String args) throws AuthenticationRequiredException, ArgumentException {
        if (!args.trim().isEmpty()) {
            throw new ArgumentException("Too many arguments: " + args);
        }
        return core.listScripts(session);
    }

    public List<String> putScript(Session session, String args) throws AuthenticationRequiredException, SyntaxException, QuotaExceededException, ArgumentException {
        Iterator<String> firstLine = Splitter.on("\r\n").split(args.trim()).iterator();
        Iterator<String> arguments = Splitter.on(' ').split(firstLine.next().trim()).iterator();

        String scriptName;
        if (! arguments.hasNext()) {
             throw new ArgumentException("Missing argument: script name");
        } else {
            scriptName = ParserUtils.unquote(arguments.next());
            if (Strings.isNullOrEmpty(scriptName)) {
                throw new ArgumentException("Missing argument: script name");
            }
        }
        if (! arguments.hasNext()) {
            throw new ArgumentException("Missing argument: script size");
        } else {
            ParserUtils.getSize(arguments.next());
        }
        if (arguments.hasNext()) {
            throw new ArgumentException("Extra arguments not supported");
        } else {
            String content = Joiner.on("\r\n").join(firstLine);
            return core.putScript(session, ParserUtils.unquote(scriptName), content);
        }
    }

    public void renameScript(Session session, String args) throws AuthenticationRequiredException, ScriptNotFoundException, DuplicateException, ArgumentException {
        String oldName = ParserUtils.getScriptName(args);
        if (null == oldName || oldName.isEmpty()) {
            throw new ArgumentException("Missing argument: old script name");
        }
        
        String newName = ParserUtils.getScriptName(args.substring(oldName.length()));
        if (null == newName || newName.isEmpty()) {
            throw new ArgumentException("Missing argument: new script name");
        } 
        
        Scanner scanner = new Scanner(args.substring(oldName.length() + 1 + newName.length()).trim()).useDelimiter("\\A");
        if (scanner.hasNext()) {
            throw new ArgumentException("Too many arguments: " + scanner.next());
        }
        core.renameScript(session, ParserUtils.unquote(oldName), ParserUtils.unquote(newName));
    }

    public void setActive(Session session, String args) throws AuthenticationRequiredException, ScriptNotFoundException, ArgumentException, UserNotFoundException, StorageException {
        String scriptName = ParserUtils.getScriptName(args);
        if (null == scriptName || scriptName.isEmpty()) {
            throw new ArgumentException("Missing argument: script name");
        }
        
        Scanner scanner = new Scanner(args.substring(scriptName.length()).trim()).useDelimiter("\\A");
        if (scanner.hasNext()) {
            throw new ArgumentException("Too many arguments: " + scanner.next());
        }
        core.setActive(session, ParserUtils.unquote(scriptName));
    } 
    
    public String getActive(Session session, String args) throws AuthenticationRequiredException, ScriptNotFoundException, ArgumentException, StorageException {
        Scanner scanner = new Scanner(args.trim()).useDelimiter("\\A");
        if (scanner.hasNext()) {
            throw new ArgumentException("Too many arguments: " + scanner.next());
        }
        return core.getActive(session);
    }

    public String startTLS(Session session) {
        return core.startTLS(session);
    }

}
