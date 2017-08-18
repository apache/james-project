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
package org.apache.james.protocols.api.handler;

import java.util.Collection;

import org.apache.james.protocols.api.ProtocolSession;

import com.google.common.collect.ImmutableSet;

/**
 * A special {@link CommandHandler} implementation which should be extended by {@link CommandHandler}'s which should get called for unknown command. So this is some kind
 * of a <strong>fallback</strong> {@link CommandHandler} which will get executed if no other matching {@link CommandHandler} could be found for a given command.
 * 
 *
 * @param <S>
 */
public abstract class UnknownCommandHandler<S extends ProtocolSession> implements CommandHandler<S>{

    /**
     * Identifier which is used in {@link #getImplCommands()} 
     */
    public final static String COMMAND_IDENTIFIER ="UNKNOWN_CMD";
    
    
    private static final Collection<String> COMMANDS = ImmutableSet.of(COMMAND_IDENTIFIER);
  
    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.handler.CommandHandler#getImplCommands()
     */
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
