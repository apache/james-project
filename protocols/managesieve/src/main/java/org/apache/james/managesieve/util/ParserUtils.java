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

import com.google.common.base.Splitter;
import org.apache.james.managesieve.api.ArgumentException;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParserUtils {

    private static final Pattern SCRIPT_NAME_REGEX = Pattern.compile("[^\\s\"']+|\"[^\"]*\"|'[^']*'");

    public static long getSize(String args) throws ArgumentException {
        System.out.println("<<<" + args + ">>>>");
        if (args.length() > 3
            && args.charAt(0) == '{'
            && args.charAt(args.length() - 1) == '}'
            && args.charAt(args.length() - 2) == '+' ) {
            System.out.println("yolooo");
            try {
                return Long.parseLong(args.substring(1, args.length() - 2));
            } catch (NumberFormatException e) {
                throw new ArgumentException("Size is not a long : " + e.getMessage());
            }
        }
        throw new ArgumentException(args + " is an invalid size literal : it should be at least 4 char looking like {_+}");
    }

    public static String getScriptName(String args) {
        Matcher regexMatcher = SCRIPT_NAME_REGEX.matcher(args);
        regexMatcher.find();
        String name = null;
        try {
        name = regexMatcher.group();
        } catch(IllegalStateException ex)
        {
            // no op
        }
        return name;
    }

    public static String unquote(String quoted) {
        String result = quoted;
        if (quoted.startsWith("\"") && quoted.endsWith("\"")) {
            result = quoted.substring(1, quoted.length() - 1);
        } else if (quoted.startsWith("'") && quoted.endsWith("'")) {
            result = quoted.substring(1, quoted.length() - 1);
        }
        return result;
    }

}
