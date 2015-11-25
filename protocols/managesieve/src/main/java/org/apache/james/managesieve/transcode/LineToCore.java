/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.managesieve.transcode;

import org.apache.james.managesieve.api.ArgumentException;
import org.apache.james.managesieve.api.AuthenticationRequiredException;
import org.apache.james.managesieve.api.SyntaxException;
import org.apache.james.managesieve.api.commands.Capability.Capabilities;
import org.apache.james.managesieve.api.commands.CoreCommands;
import org.apache.james.managesieve.util.ParserUtils;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;

import java.util.InputMismatchException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * <code>LineToCore</code>
 */
public class LineToCore{
    
    private CoreCommands _core = null;

    /**
     * Creates a new instance of LineToCore.
     *
     */
    private LineToCore() {
        super();
    }
    
    /**
     * Creates a new instance of LineToCore.
     *
     * @param core
     */
    public LineToCore(CoreCommands core) {
        this();
        _core = core;
    }
    
    public Map<Capabilities, String> capability(String args) throws ArgumentException
    {
        if (!args.trim().isEmpty())
        {
            throw new ArgumentException("Too many arguments: " + args);
        }
        return _core.capability();
    }
    
    public void deleteScript(String args) throws AuthenticationRequiredException, ScriptNotFoundException, IsActiveException, ArgumentException
    {       
        String scriptName = ParserUtils.getScriptName(args);
        if (null == scriptName || scriptName.isEmpty())
        {
            throw new ArgumentException("Missing argument: script name");
        }
        
        Scanner scanner = new Scanner(args.substring(scriptName.length()).trim()).useDelimiter("\\A");
        if (scanner.hasNext())
        {
            throw new ArgumentException("Too many arguments: " + scanner.next());
        }
        _core.deleteScript(ParserUtils.unquote(scriptName));
    }    
    
    public String getScript(String args) throws AuthenticationRequiredException, ScriptNotFoundException, ArgumentException, StorageException {
        String scriptName = ParserUtils.getScriptName(args);
        if (null == scriptName || scriptName.isEmpty())
        {
            throw new ArgumentException("Missing argument: script name");
        }
        Scanner scanner = new Scanner(args.substring(scriptName.length()).trim()).useDelimiter("\\A");
        if (scanner.hasNext())
        {
            throw new ArgumentException("Too many arguments: " + scanner.next());
        }
        return _core.getScript(ParserUtils.unquote(scriptName));
    }     
    
    public List<String> checkScript(String args) throws ArgumentException, AuthenticationRequiredException, SyntaxException
    {
        if (args.trim().isEmpty())
        {
            throw new ArgumentException("Missing argument: script content");
        }
        return _core.checkScript(args);
    }

    public void haveSpace(String args) throws AuthenticationRequiredException,
            QuotaExceededException, ArgumentException {
        String scriptName = ParserUtils.getScriptName(args);
        if (null == scriptName || scriptName.isEmpty()) {
            throw new ArgumentException("Missing argument: script name");
        }

        Scanner scanner = new Scanner(args.substring(scriptName.length()).trim());
        long size = 0;

        try {
            size = scanner.nextLong();
        } catch (InputMismatchException ex) {
            throw new ArgumentException("Invalid argument: script size");
        } catch (NoSuchElementException ex) {
            throw new ArgumentException("Missing argument: script size");
        }

        scanner.useDelimiter("\\A");
        if (scanner.hasNext()) {
            throw new ArgumentException("Too many arguments: " + scanner.next().trim());
        }
        _core.haveSpace(ParserUtils.unquote(scriptName), size);
    }

    public List<ScriptSummary> listScripts(String args) throws AuthenticationRequiredException, ArgumentException {
        if (!args.trim().isEmpty())
        {
            throw new ArgumentException("Too many arguments: " + args);
        }
        return _core.listScripts();
    }

    public List<String> putScript(String args)
            throws AuthenticationRequiredException, SyntaxException, QuotaExceededException, ArgumentException {
        String scriptName = ParserUtils.getScriptName(args);
        if (null == scriptName || scriptName.isEmpty())
        {
            throw new ArgumentException("Missing argument: script name");
        }
        Scanner scanner = new Scanner(args.substring(scriptName.length()).trim()).useDelimiter("\\A");
        if (!scanner.hasNext())
        {
            throw new ArgumentException("Missing argument: script content");
        }
        String content = scanner.next();
        return _core.putScript(ParserUtils.unquote(scriptName), content);
    }

    public void renameScript(String args)
            throws AuthenticationRequiredException, ScriptNotFoundException,
            DuplicateException, ArgumentException {
        String oldName = ParserUtils.getScriptName(args);
        if (null == oldName || oldName.isEmpty())
        {
            throw new ArgumentException("Missing argument: old script name");
        }
        
        String newName = ParserUtils.getScriptName(args.substring(oldName.length()));
        if (null == newName || newName.isEmpty())
        {
            throw new ArgumentException("Missing argument: new script name");
        } 
        
        Scanner scanner = new Scanner(args.substring(oldName.length() + 1 + newName.length()).trim()).useDelimiter("\\A");
        if (scanner.hasNext())
        {
            throw new ArgumentException("Too many arguments: " + scanner.next());
        }
        _core.renameScript(oldName, newName);    
    }

    public void setActive(String args) throws AuthenticationRequiredException,
            ScriptNotFoundException, ArgumentException {
        String scriptName = ParserUtils.getScriptName(args);
        if (null == scriptName || scriptName.isEmpty())
        {
            throw new ArgumentException("Missing argument: script name");
        }
        
        Scanner scanner = new Scanner(args.substring(scriptName.length()).trim()).useDelimiter("\\A");
        if (scanner.hasNext())
        {
            throw new ArgumentException("Too many arguments: " + scanner.next());
        }
        _core.setActive(ParserUtils.unquote(scriptName));
    } 
    
    public String getActive(String args) throws AuthenticationRequiredException, ScriptNotFoundException, ArgumentException, StorageException {
        Scanner scanner = new Scanner(args.trim()).useDelimiter("\\A");
        if (scanner.hasNext())
        {
            throw new ArgumentException("Too many arguments: " + scanner.next());
        }
        return _core.getActive();
    }  

}
