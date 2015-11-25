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

package org.apache.james.transport.mailets.managesieve;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetContext;

/**
 * <code>MockMailetConfig</code>
 */
public class MockMailetConfig implements MailetConfig {
    
    private MailetContext _context = null;
    
    private Map<String, String> _parameters = null;

    /**
     * Creates a new instance of MockMailetConfig.
     *
     * @param context
     */
    public MockMailetConfig(MailetContext context) {
        super();
        _context = context;
        _parameters = new HashMap<String, String>();
    } 
    
    /**
     * @see MailetConfig#getInitParameter(String)
     */
    public String getInitParameter(String s) {
        return _parameters.get(s);
    }
    
    public void setInitParameter(String k, String v) {
        _parameters.put(k, v);
    }

    /**
     * @see MailetConfig#getInitParameterNames()
     */
    @SuppressWarnings("unchecked")
    public Iterator getInitParameterNames() {
        return _parameters.keySet().iterator();
    }

    /**
     * @see MailetConfig#getMailetContext()
     */
    public MailetContext getMailetContext() {
        return _context;
    }

    /**
     * @see MailetConfig#getMailetName()
     */
    public String getMailetName() {
        return "";
    }

}
