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

import java.util.Iterator;

import jakarta.inject.Inject;

import org.apache.james.managesieve.api.ArgumentException;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.SessionTerminatedException;
import org.apache.james.managesieve.api.commands.CoreCommands;
import org.apache.james.managesieve.util.ParserUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

/**
 * Parses the user input and calls the underlying command processor
 */
public class ArgumentParser {
    
    private final CoreCommands core;
    private final boolean validatePutSize;

    @Inject
    public ArgumentParser(CoreCommands core) {
        this.core = core;
        this.validatePutSize = true;
    }

    public ArgumentParser(CoreCommands core, boolean validatePutSize) {
        this.core = core;
        this.validatePutSize = validatePutSize;
    }

    public String getAdvertisedCapabilities() {
        return core.getAdvertisedCapabilities();
    }

    public String capability(Session session, String args) {
        if (!args.trim().isEmpty()) {
            return "NO \"Too many arguments: " + args + "\"";
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

    public String chooseMechanism(Session session, String mechanism) {
        return core.chooseMechanism(session, mechanism);
    }

    public String authenticate(Session session, String suppliedData) {
        return core.authenticate(session, suppliedData);
    }
    
    public String deleteScript(Session session, String args) {
        Iterator<String> argumentIterator = Splitter.on(' ').omitEmptyStrings().split(args).iterator();
        if (!argumentIterator.hasNext()) {
            return "NO \"Missing argument: script name\"";
        }
        String scriptName = ParserUtils.unquote(argumentIterator.next());
        if (argumentIterator.hasNext()) {
            return "NO \"Too many arguments: " + argumentIterator.next() + "\"";
        }
        return core.deleteScript(session, scriptName);
    }    
    
    public String getScript(Session session, String args) {
        Iterator<String> argumentIterator = Splitter.on(' ').omitEmptyStrings().split(args).iterator();
        if (!argumentIterator.hasNext()) {
            return "NO \"Missing argument: script name\"";
        }
        String scriptName = ParserUtils.unquote(argumentIterator.next());
        if (argumentIterator.hasNext()) {
            return "NO \"Too many arguments: " + argumentIterator.next() + "\"";
        }
        return core.getScript(session, scriptName);
    }     
    
    public String checkScript(Session session, String args) {
        Iterator<String> firstLine = Splitter.on("\r\n").split(args.trim()).iterator();
        Iterator<String> arguments = Splitter.on(' ').split(firstLine.next().trim()).iterator();

        long size;
        if (! arguments.hasNext()) {
            return "NO : Missing argument: script size";
        } else {
            try {
                size = ParserUtils.getSize(arguments.next());
            } catch (ArgumentException e) {
                return "NO \"" + e.getMessage() + "\"";
            }
        }
        if (arguments.hasNext()) {
            return "NO \"Extra arguments not supported\"";
        } else {
            String content = Joiner.on("\r\n").join(firstLine);
            if (validatePutSize) {
                content += "\r\n";
            }
            if (content.length() < size && validatePutSize) {
                throw new NotEnoughDataException();
            }
            if (Strings.isNullOrEmpty(content)) {
                return "NO \"Missing argument: script content\"";
            }
            return core.checkScript(session, content);
        }
    }

    public String haveSpace(Session session, String args) {
        Iterator<String> argumentIterator = Splitter.on(' ').omitEmptyStrings().split(args.trim()).iterator();
        if (!argumentIterator.hasNext()) {
            return "NO \"Missing argument: script name\"";
        }
        String scriptName = ParserUtils.unquote(argumentIterator.next());
        long size;
        if (!argumentIterator.hasNext()) {
            return "NO \"Missing argument: script size\"";
        }
        try {
            size = Long.parseLong(argumentIterator.next());
        } catch (NumberFormatException e) {
            return "NO \"Invalid argument: script size\"";
        }
        if (argumentIterator.hasNext()) {
            return "NO \"Too many arguments: " + argumentIterator.next().trim() + "\"";
        }
        return core.haveSpace(session, scriptName, size);
    }

    public String listScripts(Session session, String args) {
        if (!args.trim().isEmpty()) {
            return "NO \"Too many arguments: " + args + "\"";
        }
        return core.listScripts(session);
    }

    public String putScript(Session session, String args) {
        Iterator<String> firstLine = Splitter.on("\r\n").split(args.trim()).iterator();
        Iterator<String> arguments = Splitter.on(' ').split(firstLine.next().trim()).iterator();

        String scriptName;
        long size;
        if (! arguments.hasNext()) {
             return "NO \"Missing argument: script name\"";
        } else {
            scriptName = ParserUtils.unquote(arguments.next());
            if (Strings.isNullOrEmpty(scriptName)) {
               return "NO \"Missing argument: script name\"";
            }
        }
        if (! arguments.hasNext()) {
            return "NO \"Missing argument: script size\"";
        } else {
            try {
                size = ParserUtils.getSize(arguments.next());
            } catch (ArgumentException e) {
                return "NO \"" + e.getMessage() + "\"";
            }
        }
        if (arguments.hasNext()) {
            return "NO \"Extra arguments not supported\"";
        } else {
            String content = Joiner.on("\r\n").join(firstLine);
            if (validatePutSize) {
                content += "\r\n";
            }
            if (content.length() < size && validatePutSize) {
                throw new NotEnoughDataException();
            }
            return core.putScript(session, ParserUtils.unquote(scriptName), content);
        }
    }

    public String renameScript(Session session, String args) {
        Iterator<String> argumentIterator = Splitter.on(' ').omitEmptyStrings().split(args).iterator();
        if (!argumentIterator.hasNext()) {
            return "NO \"Missing argument: old script name\"";
        }
        String oldName = ParserUtils.unquote(argumentIterator.next());
        if (!argumentIterator.hasNext()) {
            return "NO \"Missing argument: new script name\"";
        }
        String newName = ParserUtils.unquote(argumentIterator.next());
        if (argumentIterator.hasNext()) {
            return "NO \"Too many arguments: " + argumentIterator.next() + "\"";
        }
        return core.renameScript(session, oldName, newName);
    }

    public String setActive(Session session, String args) {
        Iterator<String> argumentIterator = Splitter.on(' ').omitEmptyStrings().split(args).iterator();
        if (!argumentIterator.hasNext()) {
            return "NO \"Missing argument: script name\"";
        }
        String scriptName = ParserUtils.unquote(argumentIterator.next());
        if (argumentIterator.hasNext()) {
            return "NO \"Too many arguments: " + argumentIterator.next() + "\"";
        }
        return core.setActive(session, scriptName);
    }

    public String startTLS(Session session) {
        return core.startTLS(session);
    }

}
