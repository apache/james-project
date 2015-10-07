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

package org.apache.james.util.retry.api;


/**
 * <code>ExceptionRetryingProxy</code> defines the behaviour for a
 * proxy that can retry <codeException</code> and its subclasses.
 */
public interface ExceptionRetryingProxy {
    /**
     * @return a new instance that the proxy delegates to
     * @throws Exception
     */
    abstract public Object newDelegate() throws Exception;
    
    /**
     * @return the current instance of the proxy delegate
     * @throws Exception
     */
    abstract public Object getDelegate();
    
    /**
     * Resets the delegate instance to a state from which it can perform the 
     * operations delegated to it.
     *
     * @throws Exception
     */
    abstract public void resetDelegate() throws Exception;

}
