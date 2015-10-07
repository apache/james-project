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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.mailet.MailAddress;

/**
 * This helper class contains methods for the RecipientRewriteTable implementations
 */
public class RecipientRewriteTableUtil {

    private RecipientRewriteTableUtil() {
    }

    // @deprecated QUERY is deprecated - SQL queries are now located in
    // sqlResources.xml
    public static final String QUERY = "select RecipientRewriteTable.target_address from RecipientRewriteTable, RecipientRewriteTable as VUTDomains where (RecipientRewriteTable.user like ? or RecipientRewriteTable.user like '\\%') and (RecipientRewriteTable.domain like ? or (RecipientRewriteTable.domain like '%*%' and VUTDomains.domain like ?)) order by concat(RecipientRewriteTable.user,'@',RecipientRewriteTable.domain) desc limit 1";

    /**
     * Processes regex virtual user mapping
     * 
     * If a mapped target string begins with the prefix regex:, it must be
     * formatted as regex:<regular-expression>:<parameterized-string>, e.g.,
     * regex:(.*)@(.*):${1}@tld
     * 
     * @param address
     *            the MailAddress to be mapped
     * @param targetString
     *            a String specifying the mapping
     * @throws MalformedPatternException
     */
    public static String regexMap(MailAddress address, String targetString) {
        String result = null;
        int identifierLength = RecipientRewriteTable.REGEX_PREFIX.length();

        int msgPos = targetString.indexOf(':', identifierLength + 1);

        // Throw exception on invalid format
        if (msgPos < identifierLength + 1)
            throw new PatternSyntaxException("Regex should be formatted as regex:<regular-expression>:<parameterized-string>", targetString, 0);

        // log("regex: targetString = " + targetString);
        // log("regex: msgPos = " + msgPos);
        // log("regex: compile " + targetString.substring("regex:".length(),
        // msgPos));
        // log("regex: address = " + address.toString());
        // log("regex: replace = " + targetString.substring(msgPos + 1));

        Pattern pattern = Pattern.compile(targetString.substring(identifierLength, msgPos));
        Matcher match = pattern.matcher(address.toString());

        if (match.matches()) {
            Map<String, String> parameters = new HashMap<String, String>(match.groupCount());
            for (int i = 1; i < match.groupCount(); i++) {
                parameters.put(Integer.toString(i), match.group(i));
            }
            result = replaceParameters(targetString.substring(msgPos + 1), parameters);
        }
        return result;
    }

    /**
     * Returns a named string, replacing parameters with the values set.
     * 
     * @param str
     *            the name of the String resource required.
     * @param parameters
     *            a map of parameters (name-value string pairs) which are
     *            replaced where found in the input strings
     * @return the requested resource
     */
    static public String replaceParameters(String str, Map<String, String> parameters) {
        if (str != null && parameters != null) {
            // Do parameter replacements for this string resource.
            StringBuilder replaceBuffer = new StringBuilder(64);
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                replaceBuffer.setLength(0);
                replaceBuffer.append("${").append(entry.getKey()).append("}");
                str = substituteSubString(str, replaceBuffer.toString(), entry.getValue());
            }
        }

        return str;
    }

    /**
     * Replace substrings of one string with another string and return altered
     * string.
     * 
     * @param input
     *            input string
     * @param find
     *            the string to replace
     * @param replace
     *            the string to replace with
     * @return the substituted string
     */
    static private String substituteSubString(String input, String find, String replace) {
        int find_length = find.length();
        int replace_length = replace.length();

        StringBuilder output = new StringBuilder(input);
        int index = input.indexOf(find);
        int outputOffset = 0;

        while (index > -1) {
            output.replace(index + outputOffset, index + outputOffset + find_length, replace);
            outputOffset = outputOffset + (replace_length - find_length);

            index = input.indexOf(find, index + find_length);
        }

        String result = output.toString();
        return result;
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
    public static String getTargetString(String user, String domain, Map<String, String> mappings) {
        StringBuffer buf;
        String target;

        // Look for exact (user@domain) match
        buf = new StringBuffer().append(user).append("@").append(domain);
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
        buf = new StringBuffer().append("*@").append(domain);
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
        return (targetString.indexOf(',') > -1 ? "," : (targetString.indexOf(';') > -1 ? ";" : ((targetString.contains(RecipientRewriteTable.ERROR_PREFIX) || targetString.contains(RecipientRewriteTable.REGEX_PREFIX) || targetString.contains(RecipientRewriteTable.ALIASDOMAIN_PREFIX)) ? "" : ":")));
    }

    /**
     * Returns a Map which contains the mappings
     * 
     * @param mapping
     *            A String which contains a list of mappings
     * @return Map which contains the mappings
     */
    public static Map<String, String> getXMLMappings(String mapping) {
        Map<String, String> mappings = new HashMap<String, String>();
        StringTokenizer tokenizer = new StringTokenizer(mapping, ",");
        while (tokenizer.hasMoreTokens()) {
            String mappingItem = tokenizer.nextToken();
            int index = mappingItem.indexOf('=');
            String virtual = mappingItem.substring(0, index).trim().toLowerCase();
            String real = mappingItem.substring(index + 1).trim().toLowerCase();
            mappings.put(virtual, real);
        }
        return mappings;
    }

    /**
     * Return a Collection which holds the extracted mappings of the given
     * String
     * 
     * @param rawMapping
     * @deprecated Use mappingToCollection(String rawMapping)
     */
    public static Collection<String> getMappings(String rawMapping) {
        return mappingToCollection(rawMapping);
    }

    /**
     * Convert a raw mapping String to a Collection
     * 
     * @param rawMapping
     *            the mapping String
     * @return map a collection which holds all mappings
     */
    public static ArrayList<String> mappingToCollection(String rawMapping) {
        ArrayList<String> map = new ArrayList<String>();
        StringTokenizer tokenizer = new StringTokenizer(rawMapping, RecipientRewriteTableUtil.getSeparator(rawMapping));
        while (tokenizer.hasMoreTokens()) {
            final String raw = tokenizer.nextToken().trim();
            map.add(raw);
        }
        return map;
    }

    /**
     * Convert a Collection which holds mappings to a raw mapping String
     * 
     * @param map
     *            the Collection
     * @return mapping the mapping String
     */
    public static String CollectionToMapping(Collection<String> map) {
        StringBuilder mapping = new StringBuilder();
        Iterator<String> mappings = map.iterator();
        while (mappings.hasNext()) {
            mapping.append(mappings.next());
            if (mappings.hasNext()) {
                mapping.append(";");
            }
        }
        return mapping.toString();
    }

}
