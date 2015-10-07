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

package org.apache.mailet.base.test;

import java.util.Iterator;
import java.util.Properties;

import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetContext;

/**
 * MailetConfig over Properties
 */
public class FakeMailetConfig extends Properties implements MailetConfig {

    private static final long serialVersionUID = 1L;

    public String mailetName;
    public MailetContext mc;

    public FakeMailetConfig() {
    	this("A Mailet", new FakeMailContext());
    }
    
    public FakeMailetConfig(String mailetName, MailetContext mc) {
        super();
        this.mailetName = mailetName;
        this.mc = mc;
    }

    public FakeMailetConfig(String mailetName, MailetContext mc, Properties arg0) {
        super(arg0);
        this.mailetName = mailetName;
        this.mc = mc;
    }

    public String getInitParameter(String name) {
        return getProperty(name);
    }

    public Iterator getInitParameterNames() {
        return keySet().iterator();
    }

    public MailetContext getMailetContext() {
        return mc;
    }

    public String getMailetName() {
        return mailetName;
    }

    // Override setProperty to work like it should in this MockMailetConfig
    public Object setProperty(String key, String value) {
        String oldValue = getProperty(key);
        String newValue = value;

        if (oldValue != null) {
            newValue = oldValue + "," + value;
        }
        return super.setProperty(key, newValue);
    }
}
