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

package org.apache.james.mpt.user;

import java.io.Reader;
import java.io.StringReader;

import org.apache.james.mpt.Runner;
import org.apache.james.mpt.api.Monitor;
import org.apache.james.mpt.api.UserAdder;
import org.apache.james.mpt.monitor.NullMonitor;
import org.apache.james.mpt.protocol.ProtocolSessionBuilder;
import org.apache.james.mpt.session.ExternalSessionFactory;
import org.apache.james.util.Port;

/**
 * Adds a user by executing a script at a port.
 * The user name and password supplied will be substituted 
 * for the variables <code>${user}</code> and <code>${password}</code>.
 */
public class ScriptedUserAdder implements UserAdder {

    private static final String SCRIPT_NAME = "Add User Script";
    private static final String PASSWORD_VARIABLE_NAME = "password";
    private static final String USER_VARIABLE_NAME = "user";
    
    private final String host;
    private final Port port;
    private final String script;
    private final Monitor monitor;
    
    /**
     * Constructs an adder without a script.
     * Note that {@link #addUser(String, String)} will not be available
     * @param host connect to this host
     * @param port connect to this port
     */
    public ScriptedUserAdder(String host, Port port) {
        this(host, port, (String) null);
    }
    
    public ScriptedUserAdder(String host, Port port, String script) {
        this(host, port, script, new NullMonitor());
    }
    
    /**
     * Note that {@link #addUser(String, String)} will not be available
     * @param host connect to this host
     * @param port connect to this port
     * @param monitor not null
     */
    public ScriptedUserAdder(String host, Port port, Monitor monitor) {
        this(host, port, null, monitor);
    }
    
    public ScriptedUserAdder(String host, Port port, String script, Monitor monitor) {
        this.host = host;
        this.port = port;
        this.script = script;
        this.monitor = monitor;
    }
    
    /**
     * Adds a user using the script read from the given input.
     * @param user user name, not null
     * @param password password to set, not null
     * @throws Exception upon failure
     * @throws NullPointerException when script has not been set
     */
    @Override
    public void addUser(String user, String password) throws Exception {
        final StringReader reader = new StringReader(script);
        addUser(user, password, reader);
    }

    /**
     * Adds a user using the script read from the given input.
     * @param user user name, not null
     * @param password password to set, not null
     * @param reader reader for script, not null
     * @throws Exception upon failure
     */
    public void addUser(String user, String password, Reader reader) throws Exception {
        final ProtocolSessionBuilder builder = new ProtocolSessionBuilder();
        builder.setVariable(USER_VARIABLE_NAME, user);
        builder.setVariable(PASSWORD_VARIABLE_NAME, password);
        
        final Runner runner = new Runner();
        builder.addProtocolLines(SCRIPT_NAME, reader, runner.getTestElements());
        final ExternalSessionFactory factory = new ExternalSessionFactory(host, port, monitor, null);
        runner.runSessions(factory);
    }

    /**
     * Constructs a <code>String</code> with all attributes
     * in name = value format.
     *
     * @return a <code>String</code> representation 
     * of this object.
     */
    public String toString() {
        final String TAB = " ";

        return "ScriptedUserAdder ( "
            + super.toString() + TAB
            + "host = " + this.host + TAB
            + "port = " + this.port + TAB
            + "script = " + this.script + TAB
            + "monitor = " + this.monitor + TAB
            + " )";
    }
    
    
}
