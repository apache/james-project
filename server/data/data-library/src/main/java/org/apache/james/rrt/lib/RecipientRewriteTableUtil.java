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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;

import org.apache.james.core.Domain;
import org.apache.james.rrt.lib.Mapping.Type;
import org.apache.james.util.OptionalUtils;

/**
 * This helper class contains methods for the RecipientRewriteTable implementations
 */
public class RecipientRewriteTableUtil {

    private RecipientRewriteTableUtil() {
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
