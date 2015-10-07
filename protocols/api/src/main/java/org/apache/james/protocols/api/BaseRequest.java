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

package org.apache.james.protocols.api;

/**
 * Basic Request which contains a command and argument
 *
 */
public class BaseRequest implements Request{

    private final String command;
    private final String argument;

    public BaseRequest(final String command, final String argument) {
        this.command = command;
        this.argument = argument;
        
    }
    
    /**
     * @see org.apache.james.protocols.api.Request#getArgument()
     */
    public String getArgument() {
        return argument;
    }

    /**
     * @see org.apache.james.protocols.api.Request#getCommand()
     */
    public String getCommand() {
        return command;
    }


    @Override
    public final String toString() {
        if (argument == null) {
            return command;
        } else {
            return command + " " + argument;
        }
    }
}
