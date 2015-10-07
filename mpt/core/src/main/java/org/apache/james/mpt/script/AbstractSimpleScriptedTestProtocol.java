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

package org.apache.james.mpt.script;

import java.io.InputStream;
import java.util.Locale;

import org.apache.commons.io.IOUtils;
import org.apache.james.mpt.api.HostSystem;
import org.apache.james.mpt.protocol.FileProtocolSessionBuilder;
import org.apache.james.mpt.protocol.ProtocolSession;
import org.junit.After;

/**
 * A Protocol test which reads the test protocol session from a file. The file
 * read is taken as "<test-name>.test", where <test-name> is the value passed
 * into the constructor. Subclasses of this test can set up {@link #preElements}
 * and {@link #postElements} for extra elements not defined in the protocol
 * session file.
 */
public abstract class AbstractSimpleScriptedTestProtocol extends AbstractProtocolTestFramework {
    
    private static final Locale BASE_DEFAULT_LOCALE = Locale.getDefault();

    private FileProtocolSessionBuilder builder = new FileProtocolSessionBuilder();
    private final String scriptDirectory;

    /**
     * Sets up a SimpleFileProtocolTest which reads the protocol session from a
     * file of name "<fileName>.test". This file should be available in the
     * classloader in the same location as this test class.
     * 
     * @param scriptDirectory
     *            name of the directory containing the scripts to be run
     * @param fileName
     *            The name of the file to read protocol elements from.
     * @throws Exception
     */
    public AbstractSimpleScriptedTestProtocol(HostSystem hostSystem, String userName, String password,
            String scriptDirectory) throws Exception {
        super(hostSystem, userName, password);
        this.scriptDirectory = scriptDirectory;
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        Locale.setDefault(BASE_DEFAULT_LOCALE);
    }

    /**
     * Reads test elements from the protocol session file and adds them to the
     * {@link #testElements} ProtocolSession. Then calls {@link #runSessions}.
     * 
     * @param locale
     *            execute the test using this locale
     */
    protected void scriptTest(String fileName, Locale locale) throws Exception {
        Locale.setDefault(locale);
        addTestFile(fileName + ".test", testElements);
        runSessions();
    }

    /**
     * Finds the protocol session file identified by the test name, and builds
     * protocol elements from it. All elements from the definition file are
     * added to the supplied ProtocolSession.
     * 
     * @param fileName
     *            The name of the file to read
     * @param session
     *            The ProtocolSession to add elements to.
     */
    protected void addTestFile(String fileName, ProtocolSession session) throws Exception {

        fileName = scriptDirectory + fileName;
        
        // Need to find local resource.
        InputStream is = this.getClass().getResourceAsStream(fileName);

        if (is == null) {
            throw new Exception("Test Resource '" + fileName + "' not found.");
        }

        try {
            builder.addProtocolLinesFromStream(is, session, fileName);
        }
        finally {
            IOUtils.closeQuietly(is);
        }
        
    }

}
