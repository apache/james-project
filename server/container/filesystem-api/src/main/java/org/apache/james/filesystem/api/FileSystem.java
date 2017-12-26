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

package org.apache.james.filesystem.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * This service is used by components that wants to lookup a File resource from
 * the application folder.
 * 
 * @since James 2.4
 */
public interface FileSystem {

    /**
     * Prefix for loading of a filesystem based on the current directory
     */
    String FILE_PROTOCOL = "file://";

    /**
     * Prefix for loading of a filesystem using the absolute path
     */
    String FILE_PROTOCOL_ABSOLUTE = "file:///";

    /**
     * Prefix for loading of the config directory
     */
    String FILE_PROTOCOL_AND_CONF = "file://conf/";

    /**
     * Prefix for loading of the var directory
     */
    String FILE_PROTOCOL_AND_VAR = "file://var/";

    /**
     * Prefix for loading of the classpath
     */
    String CLASSPATH_PROTOCOL = "classpath:";

    /**
     * to retrieve a resource. this is typically a file resource, but depending
     * on the implementation, this could also be from classpath or from another
     * source.
     * 
     * @param url
     *            the url of the resource
     * @return the resource as an input stream
     * @throws IOException
     *             if the resource could not be accessed
     */
    InputStream getResource(String url) throws IOException;

    /**
     * Used to retrieve a specific file in the application context
     * 
     * @param fileURL
     *            file
     * @return the File found
     * @throws FileNotFoundException
     *             if the file cannot be found/read
     */
    File getFile(String fileURL) throws FileNotFoundException;

    /**
     * Return the base folder used by the application
     */
    File getBasedir() throws FileNotFoundException;

}
