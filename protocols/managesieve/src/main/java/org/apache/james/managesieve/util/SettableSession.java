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

package org.apache.james.managesieve.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.managesieve.api.Session;

/**
 * <code>SettableSession</code>
 */
public class SettableSession implements Session {

    String _user = null;
    boolean _isAuthenticated = false;
    List<UserListener> _userListeners = new ArrayList<UserListener>();
    List<AuthenticationListener> _authenticationListeners = new ArrayList<AuthenticationListener>(); 
    
    /**
     * @see org.apache.james.managesieve.api.Session#addAuthenticationListener(org.apache.james.managesieve.api.Session.AuthenticationListener)
     */
    public void addAuthenticationListener(AuthenticationListener listener) {
        _authenticationListeners.add(listener);
    }

    /**
     * @see org.apache.james.managesieve.api.Session#addUserListener(org.apache.james.managesieve.api.Session.UserListener)
     */
    public void addUserListener(UserListener listener) {
        _userListeners.add(listener);            
    }

    /**
     * @see org.apache.james.managesieve.api.Session#getUser()
     */
    public String getUser() {
        return _user;
    }

    /**
     * @see org.apache.james.managesieve.api.Session#isAuthenticated()
     */
    public boolean isAuthenticated() {
        return _isAuthenticated;
    }

    /**
     * @see org.apache.james.managesieve.api.Session#removeAuthenticationListener(org.apache.james.managesieve.api.Session.AuthenticationListener)
     */
    public void removeAuthenticationListener(AuthenticationListener listener) {
        _authenticationListeners.remove(listener);
    }

    /**
     * @see org.apache.james.managesieve.api.Session#removeUserListener(org.apache.james.managesieve.api.Session.UserListener)
     */
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
