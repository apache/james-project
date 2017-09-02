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
import java.util.Arrays;

/**
 * This filters files based on the extension (what the filename ends with). This
 * is used in retrieving all the files of a particular type.
 * 
 * <p>
 * Eg., to retrieve and print all <code>*.java</code> files in the current
 * directory:
 * </p>
 * 
 * <pre>
 * File dir = new File(&quot;.&quot;);
 * String[] files = dir.list(new ExtensionFileFilter(new String[] { &quot;java&quot; }));
 * for (int i = 0; i &lt; files.length; i++) {
 *     System.out.println(files[i]);
 * }
 * </pre>
 */
public class ExtensionFileFilter implements FilenameFilter {
    private final String[] m_extensions;

    public ExtensionFileFilter(String[] extensions) {
        m_extensions = extensions;
    }

    public ExtensionFileFilter(String extension) {
        m_extensions = new String[] { extension };
    }

    public boolean accept(File file, String name) {
        return Arrays.stream(m_extensions).anyMatch(name::endsWith);
    }
}
