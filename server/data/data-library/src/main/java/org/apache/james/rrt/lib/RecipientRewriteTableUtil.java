/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.rrt.lib;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.rrt.lib.Mapping.Type;
import org.apache.james.util.OptionalUtils;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/**
 * This helper class contains methods for the RecipientRewriteTable implementations
 */
public class RecipientRewriteTableUtil {

    private static final int REGEX = 1;
    private static final int PARAMETERIZED_STRING = 2;

    private RecipientRewriteTableUtil() {
    }

    /**
     * Processes regex virtual user mapping
     * 
     * If a mapped target string begins with the prefix regex:, it must be
     * formatted as regex:<regular-expression>:<parameterized-string>, e.g.,
     * regex:(.*)@(.*):${1}@tld
     */
    public static Optional<String> regexMap(MailAddress address, Mapping mapping) {
        Preconditions.checkArgument(mapping.getType() == Type.Regex);

        List<String> parts = Splitter.on(':').splitToList(mapping.asString());
        if (parts.size() != 3) {
            throw new PatternSyntaxException("Regex should be formatted as regex:<regular-expression>:<parameterized-string>", mapping.asString(), 0);
        }

        Pattern pattern = Pattern.compile(parts.get(REGEX));
        Matcher match = pattern.matcher(address.asString());

        if (match.matches()) {
            ImmutableList<String> parameters = listMatchingGroups(match);
            return Optional.of(replaceParameters(parts.get(PARAMETERIZED_STRING), parameters));
        }
        return Optional.empty();
    }

    private static ImmutableList<String> listMatchingGroups(Matcher match) {
        return IntStream
            .rangeClosed(1, match.groupCount())
            .mapToObj(match::group)
            .collect(Guavate.toImmutableList());
    }

    private static String replaceParameters(String input, List<String> parameters) {
        int i = 1;
        for (String parameter: parameters) {
            input = input.replace("${" + i++ + "}", parameter);
        }
        return input;
    }

    /**
     * Returns the real recipient given a virtual username and domain.
     *
     * @param user
     *            the virtual user
     * @param domain
     *            the virtual domain
     * @return the real recipient address, or <code>null</code> if no mapping
     *         exists
     */
    public static String getTargetString(String user, Domain domain, Map<String, String> mappings) {
        StringBuffer buf;
        String target;

        // Look for exact (user@domain) match
        buf = new StringBuffer().append(user).append("@").append(domain.asString());
        target = mappings.get(buf.toString());
        if (target != null) {
            return target;
        }

        // Look for user@* match
        buf = new StringBuffer().append(user).append("@*");
        target = mappings.get(buf.toString());
        if (target != null) {
            return target;
        }

        // Look for *@domain match
        buf = new StringBuffer().append("*@").append(domain.asString());
        target = mappings.get(buf.toString());
        if (target != null) {
            return target;
        }

        return null;
    }

    /**
     * Returns the character used to delineate multiple addresses.
     * 
     * @param targetString
     *            the string to parse
     * @return the character to tokenize on
     */
    public static String getSeparator(String targetString) {
        return OptionalUtils.or(
                mayContainComma(targetString),
                mayContainSemicolon(targetString),
                mayContainColon(targetString))
            .orElse("");
    }

    private static Optional<String> mayContainComma(String targetString) {
        return mayContain(targetString, ",");
    }

    private static Optional<String> mayContainSemicolon(String targetString) {
        return mayContain(targetString, ";");
    }

    private static Optional<String> mayContainColon(String targetString) {
        if (Type.hasPrefix(targetString)) {
            return Optional.empty();
        }
        return Optional.of(":");
    }

    private static Optional<String> mayContain(String targetString, String expectedCharacter) {
        return Optional.of(expectedCharacter)
            .filter(targetString::contains);
    }

    /**
     * Returns a Map which contains the mappings
     * 
     * @param mapping
     *            A String which contains a list of mappings
     * @return Map which contains the mappings
     */
    public static Map<String, String> getXMLMappings(String mapping) {
        Map<String, String> mappings = new HashMap<>();
        StringTokenizer tokenizer = new StringTokenizer(mapping, ",");
        while (tokenizer.hasMoreTokens()) {
            String mappingItem = tokenizer.nextToken();
            int index = mappingItem.indexOf('=');
            String virtual = mappingItem.substring(0, index).trim().toLowerCase(Locale.US);
            String real = mappingItem.substring(index + 1).trim().toLowerCase(Locale.US);
            mappings.put(virtual, real);
        }
        return mappings;
    }

}
