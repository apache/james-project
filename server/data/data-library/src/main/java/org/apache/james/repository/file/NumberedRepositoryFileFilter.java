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

package org.apache.james.repository.file;

import java.io.File;
import java.io.FilenameFilter;

/**
 * This filters files based on the extension and is tailored to provide
 * backwards compatibility of the numbered repositories that Avalon does.
 */
public class NumberedRepositoryFileFilter implements FilenameFilter {
    private final String postfix;
    private final String prefix;

    /**
     * Default Constructor
     * 
     * @param extension
     *            the extension for which checked
     */
    public NumberedRepositoryFileFilter(String extension) {
        postfix = extension;
        prefix = ".Repository";
    }

    @Override
    public boolean accept(File file, String name) {
        // System.out.println("check: " + name);
        // System.out.println("post: " + postfix);
        if (!name.endsWith(postfix)) {
            return false;
        }
        // Look for a couple of digits next
        int pos = name.length() - postfix.length();
        // We have to find at least one digit... if not then this isn't what we
        // want
        if (!Character.isDigit(name.charAt(pos - 1))) {
            return false;
        }
        pos--;
        while (pos >= 1 && Character.isDigit(name.charAt(pos - 1))) {
            // System.out.println("back one");
            pos--;
        }
        // System.out.println("sub: " + name.substring(0, pos));
        // Now want to check that we match the rest
        return name.substring(0, pos).endsWith(prefix);
    }
}
