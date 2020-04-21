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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Iterables;

/**
 * Collects useful string utility methods.
 */
public final class StringUtils {

    private StringUtils() {
        // make this class non instantiable
    }
    
    /**
     * Splits a string given a pattern (regex), considering escapes.
     * <p> For example considering a pattern "," we have:
     * one,two,three => {one},{two},{three}
     * one\,two\\,three => {one,two\\},{three}
     * <p>
     * NOTE: Untested with pattern regex as pattern and untested for escape chars in text or pattern.
     */
    public static String[] split(String text, String pattern) {
        String[] array = text.split(pattern, -1);
        ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < array.length; i++) {
            boolean escaped = false;
            if (i > 0 && array[i - 1].endsWith("\\")) {
                // When the number of trailing "\" is odd then there was no separator and this pattern is part of
                // the previous match.
                int depth = 1;
                while (depth < array[i - 1].length() && array[i - 1].charAt(array[i - 1].length() - 1 - depth) == '\\') {
                    depth++;
                }
                escaped = depth % 2 == 1;
            }
            if (!escaped) {
                list.add(array[i]);
            } else {
                String prev = list.remove(list.size() - 1);
                list.add(prev.substring(0, prev.length() - 1) + pattern + array[i]);
            }
        }
        return list.toArray(String[]::new);
    }

    public static String listToString(List<String> strings) {
        return Arrays.toString(Iterables.toArray(strings, String.class));
    }
}
