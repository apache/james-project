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
package org.apache.james.server.core.filesystem;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.io.Files;

public class SimpleUrl {
    private static final String FOLDER_SEPARATOR = "/";

    private static final char WINDOWS_FOLDER_SEPARATOR = '\\';

    private static final String CURRENT_PATH = ".";

    private static final Pattern URL_REGEXP = Pattern.compile("^([^/][^/]*:(?://)?)?(.*)");

    private static String url;
    private static String protocol;
    private static String path;
    private static String simplifiedUrl;

    public SimpleUrl(String url) {
        SimpleUrl.url = url;
        String urlWithUnixSeparators = CharMatcher.is(WINDOWS_FOLDER_SEPARATOR).replaceFrom(url, FOLDER_SEPARATOR);
        extractComponents(urlWithUnixSeparators);
        simplifiedUrl = protocol + simplifyPath(path);
    }

    private static void extractComponents(String urlWithUnixSeparators) {
        Matcher m = URL_REGEXP.matcher(urlWithUnixSeparators);
        m.matches();
        protocol = Optional.ofNullable(m.group(1)).orElse("");
        path = Optional.ofNullable(m.group(2)).orElse("");
    }

    @VisibleForTesting
    static String simplifyPath(String path) {
        String simplified = Files.simplifyPath(path);
        if (CURRENT_PATH.equals(simplified)) {
            return "";
        }
        return simplified;
    }

    public String getSimplified() {
        return simplifiedUrl;
    }

}