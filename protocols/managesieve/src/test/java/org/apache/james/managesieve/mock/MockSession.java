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

package org.apache.james.managesieve.mock;

import org.apache.james.managesieve.api.Session;

import java.util.ArrayList;
import java.util.List;

/**
 * <code>MockSession</code>
 */
public class MockSession implements Session {

    String _user = null;
    boolean _isAuthenticated = false;
    List<UserListener> _userListeners = new ArrayList<UserListener>();
    List<AuthenticationListener> _authenticationListeners = new ArrayList<AuthenticationListener>(); 
    
    public MockSession()
    {
        super();
    }

    public void addAuthenticationListener(AuthenticationListener listener) {
        _authenticationListeners.add(listener);
    }

    public void addUserListener(UserListener listener) {
        _userListeners.add(listener);            
    }

    public String getUser() {
        return _user;
    }

    public boolean isAuthenticated() {
        return _isAuthenticated;
    }

    public void removeAuthenticationListener(AuthenticationListener listener) {
        _authenticationListeners.remove(listener);
    }

    public void removeUserListener(UserListener listener) {
        _userListeners.remove(listener);       
    }

    public void setAuthentication(boolean isAuthenticated) {
        _isAuthenticated = isAuthenticated;
        for(AuthenticationListener listener : _authenticationListeners)
        {                  
            listener.notifyChange(isAuthenticated);
        }                
    }

    public void setUser(String user) {
        _user = user;
        for(UserListener listener : _userListeners)
        {                  
            listener.notifyChange(user);
        }
    }

}
