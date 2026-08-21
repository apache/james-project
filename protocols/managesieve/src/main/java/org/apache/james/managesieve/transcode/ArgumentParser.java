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
import java.util.List;
import java.util.function.Function;

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

    public ArgumentParser(CoreCommands core) {
        this.core = core;
        this.validatePutSize = true;
    }

    public ArgumentParser(CoreCommands core, boolean validatePutSize) {
        this.core = core;
        this.validatePutSize = validatePutSize;
    }

    public String capability(Session session, String args) {
        if (!args.trim().isEmpty()) {
            return no("Too many arguments: " + args);
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

    public String deleteScript(Session session, String args) {
        return withArguments(args, arguments -> {
            if (arguments.isEmpty()) {
                return no("Missing argument: script name");
            }
            if (arguments.size() > 1) {
                return no("Too many arguments: " + arguments.get(1));
            }
            return core.deleteScript(session, arguments.get(0));
        });
    }

    public String getScript(Session session, String args) {
        return withArguments(args, arguments -> {
            if (arguments.isEmpty()) {
                return no("Missing argument: script name");
            }
            if (arguments.size() > 1) {
                return no("Too many arguments: " + arguments.get(1));
            }
            return core.getScript(session, arguments.get(0));
        });
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
                return no(e.getMessage());
            }
        }
        if (arguments.hasNext()) {
            return no("Extra arguments not supported");
        } else {
            String content = readContent(firstLine);
            if (content.length() < size && validatePutSize) {
                throw new NotEnoughDataException();
            }
            if (Strings.isNullOrEmpty(content)) {
                return no("Missing argument: script content");
            }
            return core.checkScript(session, content);
        }
    }

    public String haveSpace(Session session, String args) {
        return withArguments(args.trim(), arguments -> {
            if (arguments.isEmpty()) {
                return no("Missing argument: script name");
            }
            if (arguments.size() < 2) {
                return no("Missing argument: script size");
            }
            try {
                long size = Long.parseLong(arguments.get(1));
                if (arguments.size() > 2) {
                    return no("Too many arguments: " + arguments.get(2));
                }
                return core.haveSpace(session, arguments.get(0), size);
            } catch (NumberFormatException e) {
                return no("Invalid argument: script size");
            }
        });
    }

    public String listScripts(Session session, String args) {
        if (!args.trim().isEmpty()) {
            return no("Too many arguments: " + args);
        }
        return core.listScripts(session);
    }

    public String putScript(Session session, String args) {
        Iterator<String> lines = Splitter.on("\r\n").split(args.trim()).iterator();
        return withArguments(lines.next().trim(), arguments -> {
            if (arguments.isEmpty() || Strings.isNullOrEmpty(arguments.get(0))) {
                return no("Missing argument: script name");
            }
            if (arguments.size() < 2) {
                return no("Missing argument: script size");
            }
            long size;
            try {
                size = ParserUtils.getSize(arguments.get(1));
            } catch (ArgumentException e) {
                return no(e.getMessage());
            }
            if (arguments.size() > 2) {
                return no("Extra arguments not supported");
            }
            String content = readContent(lines);
            if (content.length() < size && validatePutSize) {
                throw new NotEnoughDataException();
            }
            return core.putScript(session, arguments.get(0), content);
        });
    }

    public String renameScript(Session session, String args) {
        return withArguments(args, arguments -> {
            if (arguments.isEmpty()) {
                return no("Missing argument: old script name");
            }
            if (arguments.size() < 2) {
                return no("Missing argument: new script name");
            }
            if (arguments.size() > 2) {
                return no("Too many arguments: " + arguments.get(2));
            }
            return core.renameScript(session, arguments.get(0), arguments.get(1));
        });
    }

    public String setActive(Session session, String args) {
        return withArguments(args, arguments -> {
            if (arguments.isEmpty()) {
                return no("Missing argument: script name");
            }
            if (arguments.size() > 1) {
                return no("Too many arguments: " + arguments.get(1));
            }
            return core.setActive(session, arguments.get(0));
        });
    }

    public String startTLS(Session session) {
        return core.startTLS(session);
    }

    /**
     * Splits the arguments of a command line, then hands them over to the command implementation. Command lines
     * violating the string syntax of RFC 5804 are rejected before reaching it.
     */
    private String withArguments(String args, Function<List<String>, String> command) {
        try {
            return command.apply(ParserUtils.splitArguments(args));
        } catch (ArgumentException e) {
            return no(e.getMessage());
        }
    }

    private String readContent(Iterator<String> remainingLines) {
        String content = Joiner.on("\r\n").join(remainingLines);
        if (validatePutSize) {
            return content + "\r\n";
        }
        return content;
    }

    private static String no(String message) {
        return "NO " + ParserUtils.quote(message);
    }

}
