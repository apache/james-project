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

package org.apache.james.transport.mailets;

// TODO After new release of mime4j with commit https://github.com/apache/james-mime4j/commit/66a09219457854c7a26e5b7c0e4c9dd59b4b0c32, remove this class
public class MimeUtil {
    /**
     * Splits the specified string into a multiple-line representation with
     * lines no longer than the maximum number of characters (because the line might contain
     * encoded words; see <a href='http://www.faqs.org/rfcs/rfc2047.html'>RFC
     * 2047</a> section 2). If the string contains non-whitespace sequences
     * longer than the maximum number of characters a line break is inserted at the whitespace
     * character following the sequence resulting in a line longer than the maximum number of
     * characters.
     *
     * @param s
     *            string to split.
     * @param usedCharacters
     *            number of characters already used up. Usually the number of
     *            characters for header field name plus colon and one space.
     * @param maxCharacters
     *            maximum number of characters
     * @return a multiple-line representation of the given string.
     */
    public static String fold(String s, int usedCharacters, int maxCharacters) {
        final int length = s.length();
        if (usedCharacters + length <= maxCharacters) {
            return s;
        }

        StringBuilder sb = new StringBuilder();

        int lastLineBreak = -usedCharacters;
        int wspIdx = indexOfWsp(s, 0);
        while (true) {
            if (wspIdx == length) {
                sb.append(s.substring(Math.max(0, lastLineBreak)));
                return sb.toString();
            }

            int nextWspIdx = indexOfWsp(s, wspIdx + 1);

            if (nextWspIdx - lastLineBreak > maxCharacters) {
                sb.append(s, Math.max(0, lastLineBreak), wspIdx);
                sb.append("\r\n");
                lastLineBreak = wspIdx;
            }

            wspIdx = nextWspIdx;
        }
    }

    private static int indexOfWsp(String s, int fromIndex) {
        final int len = s.length();
        for (int index = fromIndex; index < len; index++) {
            char c = s.charAt(index);
            if (c == ' ' || c == '\t') {
                return index;
            }
        }
        return len;
    }
}
