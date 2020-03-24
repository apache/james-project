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

package org.apache.james.jmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is copied from io.projectreactor.netty:reactor-netty version0.9.0-RELEASE and was originaly license under
 * Apache license version 2. Copied because of private access.
 * <p>
 * Represents a URI template. A URI template is a URI-like String that contains
 * variables enclosed by braces (<code>{</code>, <code>}</code>), which can be
 * expanded to produce an actual URI.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @author Jon Brisbin
 * @see <a href="https://tools.ietf.org/html/rfc6570">RFC 6570: URI Templates</a>
 */
public class UriPathTemplate {
    private static final Pattern FULL_SPLAT_PATTERN =
        Pattern.compile("[\\*][\\*]");
    private static final String FULL_SPLAT_REPLACEMENT = ".*";

    private static final Pattern NAME_SPLAT_PATTERN =
        Pattern.compile("\\{([^/]+?)\\}[\\*][\\*]");

    private static final Pattern NAME_PATTERN = Pattern.compile("\\{([^/]+?)\\}");
    // JDK 6 doesn't support named capture groups

    private final List<String> pathVariables =
        new ArrayList<>();
    private final HashMap<String, Matcher> matchers =
        new HashMap<>();
    private final HashMap<String, Map<String, String>> vars =
        new HashMap<>();

    private final Pattern uriPattern;

    private static String getNameSplatReplacement(String name) {
        return "(?<" + name + ">.*)";
    }

    private static String getNameReplacement(String name) {
        return "(?<" + name + ">[^\\/]*)";
    }

    static String filterQueryParams(String uri) {
        int hasQuery = uri.lastIndexOf('?');
        if (hasQuery != -1) {
            return uri.substring(0, hasQuery);
        } else {
            return uri;
        }
    }

    /**
     * Creates a new {@code UriPathTemplate} from the given {@code uriPattern}.
     *
     * @param uriPattern The pattern to be used by the template
     */
    UriPathTemplate(String uriPattern) {
        String s = "^" + filterQueryParams(uriPattern);

        Matcher m = NAME_SPLAT_PATTERN.matcher(s);
        while (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                String name = m.group(i);
                pathVariables.add(name);
                s = m.replaceFirst(getNameSplatReplacement(name));
                m.reset(s);
            }
        }

        m = NAME_PATTERN.matcher(s);
        while (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                String name = m.group(i);
                pathVariables.add(name);
                s = m.replaceFirst(getNameReplacement(name));
                m.reset(s);
            }
        }

        m = FULL_SPLAT_PATTERN.matcher(s);
        while (m.find()) {
            s = m.replaceAll(FULL_SPLAT_REPLACEMENT);
            m.reset(s);
        }

        this.uriPattern = Pattern.compile(s + "$");
    }

    /**
     * Tests the given {@code uri} against this template, returning {@code true} if
     * the uri matches the template, {@code false} otherwise.
     *
     * @param uri The uri to match
     * @return {@code true} if there's a match, {@code false} otherwise
     */
    public boolean matches(String uri) {
        return matcher(uri).matches();
    }

    private Matcher matcher(String uri) {
        uri = filterQueryParams(uri);
        Matcher m = matchers.get(uri);
        if (null == m) {
            m = uriPattern.matcher(uri);
            synchronized (matchers) {
                matchers.put(uri, m);
            }
        }
        return m;
    }

}
