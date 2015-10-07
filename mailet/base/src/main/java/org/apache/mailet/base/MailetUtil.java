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



package org.apache.mailet.base;

import org.apache.mailet.MailetConfig;


/**
 * Collects utility methods.
 */
public class MailetUtil {
    
    /**
     * <p>This takes the subject string and reduces (normailzes) it.
     * Multiple "Re:" entries are reduced to one, and capitalized.  The
     * prefix is always moved/placed at the beginning of the line, and
     * extra blanks are reduced, so that the output is always of the
     * form:</p>
     * <code>
     * &lt;prefix&gt; + &lt;one-optional-"Re:"*gt; + &lt;remaining subject&gt;
     * </code>
     * <p>I have done extensive testing of this routine with a standalone
     * driver, and am leaving the commented out debug messages so that
     * when someone decides to enhance this method, it can be yanked it
     * from this file, embedded it with a test driver, and the comments
     * enabled.</p>
     */
    public static String normalizeSubject(String subj, String prefix) {
        StringBuilder subject = new StringBuilder(subj);
        int prefixLength = prefix.length();

        // If the "prefix" is not at the beginning the subject line, remove it
        int index = subject.indexOf(prefix);
        if (index != 0) {

            if (index > 0) {
                subject.delete(index, index + prefixLength);
            }
            subject.insert(0, prefix); // insert prefix at the front
        }

        // Replace Re: with RE:
        String match = "Re:";
        index = subject.indexOf(match, prefixLength);

        while(index > -1) {
            subject.replace(index, index + match.length(), "RE:");
            index = subject.indexOf(match, prefixLength);
        }

        // Reduce them to one at the beginning
        match ="RE:";
        int indexRE = subject.indexOf(match, prefixLength) + match.length();
        index = subject.indexOf(match, indexRE);
        while(index > 0) {    
            subject.delete(index, index + match.length());
            index = subject.indexOf(match, indexRE);
        }

        // Reduce blanks
        match = "  ";
        index = subject.indexOf(match, prefixLength);
        while(index > -1) {
            subject.replace(index, index + match.length(), " ");
            index = subject.indexOf(match, prefixLength);
        }
        return subject.toString();
    }

    
    /**
     * <p>Gets a boolean valued init parameter.</p>
     * @param config not null
     * @param name name of the init parameter to be queried
     * @param defaultValue this value will be substituted when the named value
     * cannot be parse or when the init parameter is absent
     * @return true when the init parameter is <code>true</code> (ignoring case);
     * false when the init parameter is <code>false</code> (ignoring case);
     * otherwise the default value
     */
    public static boolean getInitParameter(MailetConfig config, String name, boolean defaultValue) {
        final String value = config.getInitParameter(name);
        final boolean result;
        if ("true".equalsIgnoreCase(value)) {
            result = true;
        } else if ("false".equalsIgnoreCase(value)){
            result = false;
        } else {
            result = defaultValue;
        }
        return result;
    }
}
