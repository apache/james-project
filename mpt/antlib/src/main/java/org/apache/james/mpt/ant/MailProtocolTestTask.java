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

package org.apache.james.mpt.ant;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

import org.apache.james.mpt.Runner;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.api.Monitor;
import org.apache.james.mpt.host.ExternalHostSystem;
import org.apache.james.mpt.protocol.ProtocolSessionBuilder;
import org.apache.james.mpt.user.ScriptedUserAdder;
import org.apache.james.util.Port;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.FileResource;
import org.apache.tools.ant.types.resources.Union;

/**
 * Task executes MPT scripts against a server
 * running on a given port and host.
 */
public class MailProtocolTestTask extends Task implements Monitor {

    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT);
    
    private boolean quiet = false;
    private File script;
    private Union scripts;
    private Optional<Port> port = Optional.empty();
    private String host = "127.0.0.1";
    private boolean skip = false;
    private String shabang = null;
    private final Collection<AddUser> users = new ArrayList<>();
    private String errorProperty;
    
    /**
     * Gets the error property.
     * 
     * @return name of the ant property to be set on error,
     * null if the script should terminate on error
     */
    public String getErrorProperty() {
        return errorProperty;
    }

    /**
     * Sets the error property.
     * @param errorProperty name of the ant property to be set on error,
     * nul if the script should terminate on error
     */
    public void setErrorProperty(String errorProperty) {
        this.errorProperty = errorProperty;
    }

    /**
     * Should progress output be suppressed?
     * @return true if progress information should be suppressed,
     * false otherwise
     */
    public boolean isQuiet() {
        return quiet;
    }

    /**
     * Sets whether progress output should be suppressed/
     * @param quiet true if progress information should be suppressed,
     * false otherwise
     */
    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    /**
     * Should the execution be skipped?
     * @return true if exection should be skipped, 
     * otherwise false
     */
    public boolean isSkip() {
        return skip;
    }

    /**
     * Sets execution skipping.
     * @param skip true to skip excution
     */
    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    /**
     * Gets the host (either name or number) against which this
     * test will run.
     * @return host, not null
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the host (either name or number) against which this
     * test will run.
     * @param host not null
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Gets the port against which this test will run.
     * @return port number
     */
    public int getPort() {
        return port
            .map(Port::getValue)
            .orElseThrow(() -> new RuntimeException("Port must be set"));
    }

    /**
     * Sets the port aginst which this test will run.
     * @param port port number
     */
    public void setPort(int port) {
        this.port = Optional.of(new Port(port));
    }

    /**
     * Gets the script to execute.
     * @return file containing test script
     */
    public File getScript() {
        return script;
    }

    /**
     * Sets the script to execute.
     * @param script not null
     */
    public void setScript(File script) {
        this.script = script;
    }

    /**
     * Gets script shabang.
     * This will be substituted for the first server response.
     * @return script shabang, 
     * or null for no shabang
     */
    public String getShabang() {
        return shabang;
    }
    
    /**
     * Sets the script shabang.
     * When not null, this value will be used to be substituted for the 
     * first server response.
     * @param shabang script shabang, 
     * or null for no shabang.
     */
    public void setShabang(String shabang) {
        this.shabang = shabang;
    }

    @Override
    public void execute() throws BuildException {
        if (! port.isPresent()) {
            throw new BuildException("Port must be set");
        }
        
        if (scripts == null && script == null) {
            throw new BuildException("Scripts must be specified as an embedded resource collection"); 
        }
        
        if (scripts != null && script != null) {
            throw new BuildException("Scripts can be specified either by the script attribute or as resource collections but not both."); 
        }
        
        for (AddUser user: users) {
            user.validate();
        }
        
        if (skip) {
            log("Skipping excution");
        } else if (errorProperty == null) {
            doExecute();
        } else {
            try {
                doExecute();
            } catch (BuildException e) {
                final Project project = getProject();
                project.setProperty(errorProperty, e.getMessage());
                log(e, Project.MSG_DEBUG);
            }
        }
    }

    public void add(ResourceCollection resources) {
        if (scripts == null) {
            scripts = new Union();
        }
        scripts.add(resources);
    }
    
    private void doExecute() throws BuildException {
        for (AddUser userAdder: users) {
            userAdder.execute();
        }
        
        final ExternalHostSystem host = new ExternalHostSystem(SUPPORTED_FEATURES, getHost(), port.get(), this, getShabang(), null);
        final ProtocolSessionBuilder builder = new ProtocolSessionBuilder();
        
        if (scripts == null) {
            scripts = new Union();
            scripts.add(new FileResource(script));
        }
        
        for (Iterator<?> it = scripts.iterator(); it.hasNext();) {
            final Resource resource = (Resource) it.next();
            try {
                final Runner runner = new Runner();
                
                try {
                    
                    final InputStream inputStream = resource.getInputStream();
                    final String name = resource.getName();
                    builder.addProtocolLines(name == null ? "[Unknown]" : name, inputStream, runner.getTestElements());
                    runner.runSessions(host);
                    
                } catch (UnsupportedOperationException e) {
                    log("Resource cannot be read: " + resource.getName(), Project.MSG_WARN);
                }
            } catch (IOException e) {
                throw new BuildException("Cannot load script " + resource.getName(), e);
            } catch (Exception e) {
                log(e.getMessage(), Project.MSG_ERR);
                throw new BuildException("[FAILURE] in script " + resource.getName() + "\n" + e.getMessage(), e);
            }
            
        }
    
    }
    
    public AddUser createAddUser() {
        final AddUser result = new AddUser();
        users.add(result);
        return result;
    }

    /**
     * Adds a user.
     */
    public class AddUser {
        
        private Port port;
        private String user;
        private String passwd;
        private File script;
        private String scriptText;

        /**
         * Gets the port against which the user addition
         * script should be executed.
         * @return port number
         */
        public int getPort() {
            return port.getValue();
        }

        /**
         * Sets the port against which the user addition
         * script should be executed.
         * @param port port number
         */
        public void setPort(int port) {
            this.port = new Port(port);
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
        public void addText(String scriptText) {
            this.scriptText = getProject().replaceProperties(scriptText);
        }

        /**
         * Gets the file containing the user creation script.
         * @return not null
         */
        public File getScript() {
            return script;
        }

        /**
         * Sets the file containing the user creation script.
         * @param script not null
         */
        public void setScript(File script) {
            this.script = script;
        }
        
        /**
         * Validates mandatory fields have been filled.
         */
        void validate() throws BuildException {
            if (script == null && scriptText == null) {
                throw new BuildException("Either the 'script' attribute must be set, or the body must contain the text of the script");
            }
            
            if (script != null && scriptText != null) {
                throw new BuildException("Choose either script text or script attribute but not both.");
            }
            
            if (port == null) {
                throw new BuildException("'port' attribute must be set on AddUser to the port against which the script should run.");
            }
        }
        
        /**
         * Creates a user.
         * @throws BuildException
         */
        void execute() throws BuildException {
            validate();
            try {
                final File scriptFile = getScript();
                final Reader reader;
                if (scriptFile == null) {
                    reader = new StringReader(scriptText);
                } else {
                    reader = new FileReader(scriptFile);
                }
                final ScriptedUserAdder adder = new ScriptedUserAdder(getHost(), port, MailProtocolTestTask.this);
                adder.addUser(getUser(), getPasswd(), reader);
            } catch (Exception e) {
                log(e.getMessage(), Project.MSG_ERR);
                throw new BuildException("User addition failed: \n" + e.getMessage(), e);
            }
        } 
    }

    @Override
    public void note(String message) {
        if (quiet) {
            log(message, Project.MSG_DEBUG);
        } else {
            log(message, Project.MSG_INFO);
        }
    }

    @Override
    public void debug(char character) {
        log("'" + character + "'", Project.MSG_DEBUG);
    }

    @Override
    public void debug(String message) {
        log(message, Project.MSG_DEBUG);
    }
}
