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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.james.managesieve.api.ArgumentException;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.SessionTerminatedException;
import org.apache.james.managesieve.api.commands.CoreCommands;
import org.apache.james.managesieve.util.ParserUtils;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

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
            return no("UNAUTHENTICATE do not take arguments");
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
        CommandWithLiteral command = CommandWithLiteral.parse(args);
        return withLiteralArguments(command, arguments -> {
            Optional<String> content = readLiteral(announcedLiteralSize(arguments), command.followingData());

            if (arguments.isEmpty()) {
                return no("Missing argument: script size");
            }
            try {
                ParserUtils.getSize(arguments.get(0));
            } catch (ArgumentException e) {
                return no(e.getMessage());
            }
            if (arguments.size() > 1) {
                return no("Extra arguments not supported");
            }
            return content.map(script -> {
                    if (Strings.isNullOrEmpty(script)) {
                        return no("Missing argument: script content");
                    }
                    return core.checkScript(session, script);
                })
                .orElseGet(() -> no("Unexpected data after the script literal"));
        });
    }

    public String haveSpace(Session session, String args) {
        return withArguments(args.trim(), arguments -> {
            // As PUTSCRIPT: RFC 5804 section 2.5, HAVESPACE tells whether a PUTSCRIPT would succeed
            if (arguments.isEmpty() || Strings.isNullOrEmpty(arguments.get(0))) {
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
        CommandWithLiteral command = CommandWithLiteral.parse(args);
        return withLiteralArguments(command, arguments -> {
            Optional<String> content = readLiteral(announcedLiteralSize(arguments), command.followingData());

            if (arguments.isEmpty() || Strings.isNullOrEmpty(arguments.get(0))) {
                return no("Missing argument: script name");
            }
            if (arguments.size() < 2) {
                return no("Missing argument: script size");
            }
            try {
                ParserUtils.getSize(arguments.get(1));
            } catch (ArgumentException e) {
                return no(e.getMessage());
            }
            if (arguments.size() > 2) {
                return no("Extra arguments not supported");
            }
            return content.map(script -> core.putScript(session, arguments.get(0), script))
                .orElseGet(() -> no("Unexpected data after the script literal"));
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

    /**
     * A command whose last argument is a literal: its first line holds the arguments, the literal octets follow.
     */
    private record CommandWithLiteral(String firstLine, String followingData) {
        static CommandWithLiteral parse(String args) {
            int firstLineEnd = args.indexOf("\r\n");
            if (firstLineEnd < 0) {
                return new CommandWithLiteral(args.trim(), "");
            }
            return new CommandWithLiteral(args.substring(0, firstLineEnd).trim(), args.substring(firstLineEnd + 2));
        }
    }

    /**
     * RFC 5804 section 4: a literal is made of exactly the number of octets announced by its size, whatever they are.
     *
     * The literal announced as the last argument is read before the arguments get validated: a rejected command
     * still consumes its literal, which otherwise would be interpreted as commands.
     *
     * @throws NotEnoughDataException when the announced octets did not all arrive yet
     * @return the literal content, empty when more data than announced was received
     */
    private Optional<String> readLiteral(Optional<Long> announcedSize, String followingData) {
        if (!validatePutSize) {
            return Optional.of(followingData.stripTrailing());
        }
        long receivedOctets = followingData.getBytes(StandardCharsets.UTF_8).length;
        long expectedOctets = announcedSize.orElse(0L);
        if (receivedOctets < expectedOctets) {
            throw new NotEnoughDataException();
        }
        if (receivedOctets > expectedOctets) {
            return Optional.empty();
        }
        return Optional.of(followingData);
    }

    /**
     * Same as {@link #withArguments(String, Function)}, but a command line violating the string syntax still
     * consumes the literal it announces.
     */
    private String withLiteralArguments(CommandWithLiteral command, Function<List<String>, String> handler) {
        List<String> arguments;
        try {
            arguments = ParserUtils.splitArguments(command.firstLine());
        } catch (ArgumentException e) {
            String lastToken = command.firstLine().substring(command.firstLine().lastIndexOf(' ') + 1);
            readLiteral(literalSize(lastToken), command.followingData());
            return no(e.getMessage());
        }
        return handler.apply(arguments);
    }

    private static Optional<Long> announcedLiteralSize(List<String> arguments) {
        return Optional.ofNullable(Iterables.getLast(arguments, null))
            .flatMap(ArgumentParser::literalSize);
    }

    private static Optional<Long> literalSize(String argument) {
        try {
            return Optional.of(ParserUtils.getSize(argument));
        } catch (ArgumentException e) {
            return Optional.empty();
        }
    }

    private static String no(String message) {
        return "NO " + ParserUtils.quote(message);
    }

}
