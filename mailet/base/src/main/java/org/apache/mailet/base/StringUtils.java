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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Joiner;
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
                while (depth < array[i-1].length() && array[i-1].charAt(array[i-1].length() - 1 - depth) == '\\') depth ++;
                escaped = depth % 2 == 1;
            }
            if (!escaped) list.add(array[i]);
            else {
                String prev = list.remove(list.size() - 1);
                list.add(prev.substring(0, prev.length() - 1) + pattern + array[i]);
            }
        }
        return list.toArray(new String[list.size()]);
    }

    /**
     * Creates an MD5 digest from the message.
     * Note that this implementation is unsalted.
     * @param message not null
     * @return MD5 digest, not null
     */
    public static String md5(java.lang.String message) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            StringBuilder sb = new StringBuilder();
            byte buf[] = message.getBytes();
            byte[] md5 = md.digest(buf);

            for (byte aMd5 : md5) {
                String tmpStr = "0" + Integer.toHexString((0xff & aMd5));
                sb.append(tmpStr.substring(tmpStr.length() - 2));
            }
            return sb.toString();
            
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * Capitalizes each word in the given text by converting the
     * first letter to upper case.
     * @param data text to be capitalize, possibly null
     * @return text with each work capitalized, 
     * or null when the text is null
     */
    public static String capitalizeWords(String data) {
        if (data==null) return null;
        StringBuilder res = new StringBuilder();
        char ch;
        char prevCh = '.';
        for ( int i = 0;  i < data.length();  i++ ) {
            ch = data.charAt(i);
            if ( Character.isLetter(ch)) {
                if (!Character.isLetter(prevCh) ) res.append( Character.toUpperCase(ch) );
                else res.append( Character.toLowerCase(ch) );
            } else res.append( ch );
            prevCh = ch;
        }
        return res.toString();
    }
    
    /**
     * Utility method for obtaining a string representation of an array of Objects.
     */
    public static String arrayToString(Object[] array) {
        if (array == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(1024);
        sb.append("[");
        sb.append(Joiner.on(",").join(array));
        sb.append("]");
        return sb.toString();
    }

    public static String listToString(List<String> strings) {
        return arrayToString(Iterables.toArray(strings, String.class));
    }
}
