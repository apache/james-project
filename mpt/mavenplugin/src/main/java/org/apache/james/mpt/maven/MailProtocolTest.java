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
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

import org.apache.james.mpt.Runner;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.api.Monitor;
import org.apache.james.mpt.host.ExternalHostSystem;
import org.apache.james.mpt.protocol.ProtocolSessionBuilder;
import org.apache.james.mpt.user.ScriptedUserAdder;
import org.apache.james.util.Port;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

public class MailProtocolTest implements Monitor {

   private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT);

   private Integer port;
   
   private File scriptFile;
   
   private String host;

   private String shabang;
   
   private AddUser[] addUsers;

   public void setScriptFile(File scriptFile) {
       this.scriptFile = scriptFile;
   }
   
   public void setPort(Integer port) {
       this.port = port;
   }
   
   public void setHost(String host) {
       this.host = host;
   }
   
   public void setShabang(String shabang) {
       this.shabang = shabang;
   }
   
   public void setAddUser(AddUser[] addUsers) {
       this.addUsers = addUsers;
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
    * Gets the port against which this test will run.
    * @return port number
    */
   public int getPort() {
       return port;
   }

   
    public void execute() throws MojoExecutionException, MojoFailureException {
        validate();


        for (AddUser addUser : addUsers) {
            try {

                final Reader reader;
                if (addUser.getScriptText() != null) {
                    reader = new StringReader(addUser.getScriptText());
                } else {
                    reader = new FileReader(addUser.getScriptFile());
                }
                final ScriptedUserAdder adder = new ScriptedUserAdder(addUser.getHost(), addUser.getPort().orElseThrow(() -> new RuntimeException("Port should be set")), this);
                adder.addUser(addUser.getUser(), addUser.getPasswd(), reader);
            } catch (Exception e) {
                //getLog().error("Unable to add user", e);
                throw new MojoFailureException("User addition failed: \n" + e.getMessage());
            }
        }
       final Runner runner = new Runner();
       InputStream inputStream;
        try {
            inputStream = new FileInputStream(scriptFile);

            final ExternalHostSystem hostSystem = new ExternalHostSystem(SUPPORTED_FEATURES, host, new Port(port), this, shabang, null);
            final ProtocolSessionBuilder builder = new ProtocolSessionBuilder();

            builder.addProtocolLines(scriptFile.getName(), inputStream, runner.getTestElements());
            runner.runSessions(hostSystem);

        } catch (IOException e1) {
           throw new MojoExecutionException("Cannot load script " + scriptFile.getName(), e1);
       } catch (Exception e) {
           throw new MojoExecutionException("[FAILURE] in script " + scriptFile.getName() + "\n" + e.getMessage(), e);
       }
      
    }

    /**
     * Validate if the configured parameters are valid
     *
     * @throws MojoFailureException
     */
    private void validate() throws MojoFailureException {
        if (port <= 0) {
           throw new MojoFailureException("'port' configuration must be set.");
        }

        if (scriptFile.exists() == false) {
           throw new MojoFailureException("'scriptFile' not exists");
        }

        for (AddUser addUser : addUsers) {

            if (addUser.getScriptText() == null && addUser.getScriptFile() == null) {
                throw new MojoFailureException("AddUser must contain the text of the script or a scriptFile");
            }

            if (! addUser.getPort().isPresent()) {
                throw new MojoFailureException("'port' attribute must be set on AddUser to the port against which the script should run.");
            }

            if (addUser.getHost() == null) {
                throw new MojoFailureException("'host' attribute must be set on AddUser to the host against which the script should run.");
            }
        }

    }


    @Override
    public void debug(char character) {
        //getLog().debug("'" + character + "'");
        // do nothing by default
    }

    @Override
    public void debug(String message) {
        //getLog().debug(message);
        // do nothing by default

    }

    @Override
    public void note(String message) {
        //getLog().debug(message);
        System.out.println(message);
    }
}
