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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.apache.james.protocols.api.BaseRequest;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.future.FutureResponse;
import org.apache.james.protocols.api.future.FutureResponse.ResponseListener;
import org.apache.james.protocols.api.future.FutureResponseImpl;



/**
 *  A CommandDispatcher is responsible to call the right {@link CommandHandler} for a given Command
 *
 */
public class CommandDispatcher<Session extends ProtocolSession> implements ExtensibleHandler, LineHandler<Session> {
    /**
     * The list of available command handlers
     */
    private final HashMap<String, List<CommandHandler<Session>>> commandHandlerMap = new HashMap<String, List<CommandHandler<Session>>>();

    private final List<ProtocolHandlerResultHandler<Response, Session>> rHandlers = new ArrayList<ProtocolHandlerResultHandler<Response, Session>>();

    private final Collection<String> mandatoryCommands;
    
    public CommandDispatcher(Collection<String> mandatoryCommands) {
        this.mandatoryCommands = mandatoryCommands;
    }
    
    public CommandDispatcher() {
        this(Collections.<String>emptyList());
    }
    
    /**
     * Add it to map (key as command name, value is an array list of CommandHandlers)
     *
     * @param commandName the command name which will be key
     * @param cmdHandler The CommandHandler object
     */
    protected void addToMap(String commandName, CommandHandler<Session> cmdHandler) {
        List<CommandHandler<Session>> handlers = commandHandlerMap.get(commandName);
        if(handlers == null) {
            handlers = new ArrayList<CommandHandler<Session>>();
            commandHandlerMap.put(commandName, handlers);
        }
        handlers.add(cmdHandler);
    }


    /**
     * Returns all the configured CommandHandlers for the specified command
     *
     * @param command the command name which will be key
     * @param session not null
     * @return List of CommandHandlers
     */
    protected List<CommandHandler<Session>> getCommandHandlers(String command, ProtocolSession session) {
        if (command == null) {
            return null;
        }
        if (session.getLogger().isDebugEnabled()) {
            session.getLogger().debug("Lookup command handler for command: " + command);
        }
        List<CommandHandler<Session>> handlers =  commandHandlerMap.get(command);
        if(handlers == null) {
            handlers = commandHandlerMap.get(getUnknownCommandHandlerIdentifier());
        }

        return handlers;
    }

    /**
     * @throws WiringException 
     * @see org.apache.james.protocols.api.handler.ExtensibleHandler#wireExtensions(java.lang.Class, java.util.List)
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void wireExtensions(Class interfaceName, List extension) throws WiringException {
        if (interfaceName.equals(ProtocolHandlerResultHandler.class)) {
            rHandlers.addAll(extension);
        }
        if (interfaceName.equals(CommandHandler.class)) {
            for (Iterator it = extension.iterator(); it.hasNext();) {
                CommandHandler handler = (CommandHandler) it.next();
                Collection implCmds = handler.getImplCommands();

                for (Iterator i = implCmds.iterator(); i.hasNext();) {
                    String commandName = ((String) i.next()).trim().toUpperCase(Locale.US);
                    addToMap(commandName, (CommandHandler) handler);
                }
            }
            
            if (commandHandlerMap.size() < 1) {
                throw new WiringException("No commandhandlers configured");
            } else {
                for (String cmd: mandatoryCommands) {
                    if (!commandHandlerMap.containsKey(cmd)) {
                        throw new WiringException("No commandhandlers configured for mandatory command " + cmd);
                    }
                }
            }
        }

    }
    
    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.handler.LineHandler#onLine(org.apache.james.protocols.api.ProtocolSession, java.nio.ByteBuffer)
     */
    public Response onLine(final Session session, ByteBuffer line) {
        
        try {
            
            Request request = parseRequest(session, line);
            if (request == null) {
                return null;
            }
            return dispatchCommandHandlers(session, request);
        } catch (Exception e) {
            session.getLogger().debug("Unable to parse request", e);
            return session.newFatalErrorResponse();
        } 

       
    }
    
    /**
     * Dispatch the {@link CommandHandler}'s for the given {@link Request} and return a {@link Response} or <code>null</code> if non should get written
     * back to the client
     * 
     * @param session
     * @param request
     * @return response
     */
    protected Response dispatchCommandHandlers(Session session, Request request) {
        if (session.getLogger().isDebugEnabled()) {
            session.getLogger().debug(getClass().getName() + " received: " + request.getCommand());
        }
        List<CommandHandler<Session>> commandHandlers = getCommandHandlers(request.getCommand(), session);
        // fetch the command handlers registered to the command
        Iterator<CommandHandler<Session>> handlers = commandHandlers.iterator();
        
        while (handlers.hasNext()) {
            final long start = System.currentTimeMillis();
            CommandHandler<Session> cHandler = handlers.next();
            Response response = cHandler.onCommand(session, request);
            if (response != null) {
                long executionTime = System.currentTimeMillis() - start;

                // now process the result handlers
                response = executeResultHandlers(session, response, executionTime, cHandler, rHandlers.iterator());
                if (response != null) {
                    return response;
                }
            }


        }
        return null;
    }

    private Response executeResultHandlers(final Session session, Response response, final long executionTime, final CommandHandler<Session> cHandler, final Iterator<ProtocolHandlerResultHandler<Response, Session>> resultHandlers) {
        // Check if the there is a ResultHandler left to execute if not just return the response
        if (resultHandlers.hasNext()) {
            // Special handling of FutureResponse
            // See PROTOCOLS-37
            if (response instanceof FutureResponse) {
                final FutureResponseImpl futureResponse = new FutureResponseImpl();
                ((FutureResponse) response).addListener(new ResponseListener() {

                    public void onResponse(FutureResponse response) {
                        Response r = resultHandlers.next().onResponse(session, response, executionTime, cHandler);
                        
                        // call the next ResultHandler 
                        r = executeResultHandlers(session, r, executionTime, cHandler, resultHandlers);
                        
                        // notify the FutureResponse that we are ready
                        futureResponse.setResponse(r);
                    }
                });
                
                // just return the new FutureResponse which will get notified once its ready
                return futureResponse;
            }  else {
                response = resultHandlers.next().onResponse(session, response, executionTime, (CommandHandler<Session>) cHandler);
                
                // call the next ResultHandler 
                return executeResultHandlers(session, response, executionTime, cHandler, resultHandlers);
            }
        }
        return response;
    }
    /**
     * Parse the line into a {@link Request}
     * 
     * @param session
     * @param line
     * @return request
     * @throws Exception
     */
    protected Request parseRequest(Session session, ByteBuffer buffer) throws Exception {
        String curCommandName = null;
        String curCommandArgument = null;
        byte[] line;
        if (buffer.hasArray()) {
            line = buffer.array();
        } else {
            line = new byte[buffer.remaining()];
            buffer.get(line);
        }
        // This should be changed once we move to java6
        String cmdString = new String(line, session.getCharset().name()).trim();
        int spaceIndex = cmdString.indexOf(" ");
        if (spaceIndex > 0) {
            curCommandName = cmdString.substring(0, spaceIndex);
            curCommandArgument = cmdString.substring(spaceIndex + 1);
        } else {
            curCommandName = cmdString;
        }
        curCommandName = curCommandName.toUpperCase(Locale.US);

        BaseRequest request = new BaseRequest(curCommandName, curCommandArgument);
        return request;

    }
   
    /**
     * @see org.apache.james.protocols.api.handler.ExtensibleHandler#getMarkerInterfaces()
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public List<Class<?>> getMarkerInterfaces() {
        List res = new LinkedList();
        res.add(CommandHandler.class);
        res.add(ProtocolHandlerResultHandler.class);
        return res;
    }

    /**
     * Return the identifier to lookup the UnknownCmdHandler in the handler map
     * 
     * @return identifier
     */
    protected String getUnknownCommandHandlerIdentifier() {
        return UnknownCommandHandler.COMMAND_IDENTIFIER;
    }
}
