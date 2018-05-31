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

package org.apache.james.mpt.maven;

import java.io.File;
import java.util.Optional;

import org.apache.james.util.Port;


/**
 * Adds a user.
 */
public class AddUser {
    
    private Optional<Port> port = Optional.empty();
    private String user;
    private String passwd;
    private String scriptText;
    private String host;
    private File scriptFile;

    /**
     * Gets the host (either name or number) against which this
     * test will run.
     * @return host, not null
     */
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
    
    /**
     * Gets the port against which the user addition
     * script should be executed.
     * @return port number
     */
    public Optional<Port> getPort() {
        return port;
    }

    /**
     * Sets the port against which the user addition
     * script should be executed.
     * @param port port number
     */
    public void setPort(Port port) {
        this.port = Optional.of(port);
    }

    /**
     * Gets the password for the user.
     * @return password not null
     */
    public String getPasswd() {
        return passwd;
    }

    /**
     * Sets the password for the user.
     * This will be passed in the user creation script.
     * @param passwd not null
     */
    public void setPasswd(String passwd) {
        this.passwd = passwd;
    }

    /**
     * Gets the name of the user to be created.
     * @return user name, not null
     */
    public String getUser() {
        return user;
    }

    /**
     * Sets the name of the user to be created.
     * @param user not null
     */
    public void setUser(String user) {
        this.user = user;
    }
    
    /**
     * Sets user addition script.
     * @param scriptText not null
     */
    public void setScriptText(String scriptText) {
        this.scriptText = scriptText;
    }


    public String getScriptText() {
        return scriptText;
    }

    public File getScriptFile() {
        return scriptFile;
    }
    
    public void setScriptFile(File scriptFile) {
        this.scriptFile = scriptFile;
    }
    
}