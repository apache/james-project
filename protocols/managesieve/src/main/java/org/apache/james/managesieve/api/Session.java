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

package org.apache.james.managesieve.api;

/**
 * <code>Session</code>
 */
public interface Session {
    
    public interface UserListener
    {
        abstract public void notifyChange(String user);
    }
    
    abstract public String getUser();
    
    abstract public void addUserListener(UserListener listener);
    
    abstract public void removeUserListener(UserListener listener);
    
    public interface AuthenticationListener
    {
        abstract public void notifyChange(boolean isAuthenticated);
    }
    
    abstract public boolean isAuthenticated();
    
    abstract public void addAuthenticationListener(AuthenticationListener listener);
    
    abstract public void removeAuthenticationListener(AuthenticationListener listener);

}
