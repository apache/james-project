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

package org.apache.james.managesieve.api.commands;

import org.apache.james.managesieve.api.AuthenticationRequiredException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;

/**
 * <code>GetScript</code> is an extension to the commands defined by RFC 5804. It provides a means
 * of retrieving a user's currently active script in a single call. The alternative using RFC 5804
 * mandated commands is to call ListScripts, parse the result to identify the active script and 
 * call GetScript to retrieve it. 
 */
public interface GetActive {
    
    abstract public String getActive() throws AuthenticationRequiredException, ScriptNotFoundException, StorageException;

}
