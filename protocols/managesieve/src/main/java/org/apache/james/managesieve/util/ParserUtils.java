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

package org.apache.james.managesieve.util;

import java.util.List;

import org.apache.james.managesieve.api.ArgumentException;

import com.google.common.collect.ImmutableList;

public class ParserUtils {

    private static final char QUOTE = '"';
    private static final char ESCAPE = '\\';

    /**
     * States of the automaton splitting a ManageSieve command line into its arguments.
     */
    private enum SplitState {
        /** Between two arguments: white spaces are the only thing expected here. */
        BETWEEN_ARGUMENTS,
        /** Within an argument that is not enclosed in quotes: the next white space ends it. */
        WITHIN_ATOM,
        /** Within an argument enclosed in quotes: white spaces belong to it, only the closing quote ends it. */
        WITHIN_QUOTES,
        /** Right after a backslash within quotes: the next character is taken literally, be it a quote or a backslash. */
        AFTER_ESCAPE
    }

    /**
     * Splits a ManageSieve command line into its arguments.
     *
     * Contrary to a plain whitespace split, the quoted-string syntax of RFC 5804 section 4 is honoured: an argument
     * enclosed in double quotes may contain spaces, and both '"' and '\' can be escaped with a leading '\'.
     *
     * Returned arguments are unquoted.
     */
    public static List<String> splitArguments(String line) throws ArgumentException {
        ImmutableList.Builder<String> arguments = ImmutableList.builder();
        StringBuilder pending = new StringBuilder();
        SplitState state = SplitState.BETWEEN_ARGUMENTS;

        for (char current : line.toCharArray()) {
            state = switch (state) {
                case BETWEEN_ARGUMENTS -> {
                    if (isWhiteSpace(current)) {
                        yield SplitState.BETWEEN_ARGUMENTS;
                    }
                    if (current == QUOTE) {
                        yield SplitState.WITHIN_QUOTES;
                    }
                    pending.append(current);
                    yield SplitState.WITHIN_ATOM;
                }
                case WITHIN_ATOM -> {
                    if (isWhiteSpace(current)) {
                        arguments.add(unquote(flush(pending)));
                        yield SplitState.BETWEEN_ARGUMENTS;
                    }
                    pending.append(current);
                    yield SplitState.WITHIN_ATOM;
                }
                case WITHIN_QUOTES -> {
                    if (current == ESCAPE) {
                        yield SplitState.AFTER_ESCAPE;
                    }
                    if (current == QUOTE) {
                        arguments.add(flush(pending));
                        yield SplitState.BETWEEN_ARGUMENTS;
                    }
                    pending.append(current);
                    yield SplitState.WITHIN_QUOTES;
                }
                case AFTER_ESCAPE -> {
                    pending.append(current);
                    yield SplitState.WITHIN_QUOTES;
                }
            };
        }

        return switch (state) {
            case WITHIN_QUOTES, AFTER_ESCAPE -> throw new ArgumentException("Unterminated quoted string");
            case WITHIN_ATOM -> arguments.add(unquote(flush(pending))).build();
            case BETWEEN_ARGUMENTS -> arguments.build();
        };
    }

    private static boolean isWhiteSpace(char c) {
        return c == ' ' || c == '\t';
    }

    private static String flush(StringBuilder pending) {
        String value = pending.toString();
        pending.setLength(0);
        return value;
    }

    /**
     * Renders a value as a ManageSieve quoted string, escaping the QUOTED-SPECIALS of RFC 5804 section 4 so that
     * user supplied data can safely be echoed back within a response.
     */
    public static String quote(String value) {
        return QUOTE + value
            .replace(String.valueOf(ESCAPE), "\\\\")
            .replace(String.valueOf(QUOTE), "\\\"") + QUOTE;
    }

    public static long getSize(String args) throws ArgumentException {
        if (args != null && args.length() > 3
            && args.charAt(0) == '{'
            && args.charAt(args.length() - 1) == '}'
            && args.charAt(args.length() - 2) == '+') {
            try {
                return Long.parseLong(args.substring(1, args.length() - 2));
            } catch (NumberFormatException e) {
                throw new ArgumentException("Size is not a long : " + e.getMessage(), e);
            }
        }
        throw new ArgumentException(args + " is an invalid size literal : it should be at least 4 char looking like {_+}");
    }

    public static String unquote(String quoted) {
        String result = quoted;
        if (quoted != null) {
            if (quoted.startsWith("\"") && quoted.endsWith("\"")) {
                result = quoted.substring(1, quoted.length() - 1);
            } else if (quoted.startsWith("'") && quoted.endsWith("'")) {
                result = quoted.substring(1, quoted.length() - 1);
            }
        }
        return result;
    }

    public static String unquoteFirst(String quoted) {
        if (quoted == null) {
            return null;
        }
        if (quoted.length() > 2 && quoted.startsWith("\"") && quoted.indexOf('\"', 1) >= 0) {
            return quoted.substring(1, quoted.indexOf('\"', 1));
        } else if (quoted.length() > 2 && quoted.startsWith("'") && quoted.indexOf('\'', 1) >= 0) {
            return quoted.substring(1, quoted.indexOf('\'', 1));
        }
        return null;
    }

}
