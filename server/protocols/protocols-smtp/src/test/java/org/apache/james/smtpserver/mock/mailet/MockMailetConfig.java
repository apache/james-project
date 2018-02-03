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
package org.apache.james.smtpserver.mock.mailet;

import java.util.Iterator;
import java.util.Properties;

import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetContext;

/**
 * MailetConfig over Properties
 */
public class MockMailetConfig extends Properties implements MailetConfig {

    private final String mailetName;
    private final MailetContext mc;

    public MockMailetConfig(String mailetName, MailetContext mc) {
        super();
        this.mailetName = mailetName;
        this.mc = mc;
    }

    public MockMailetConfig(String mailetName, MailetContext mc, Properties arg0) {
        super(arg0);
        this.mailetName = mailetName;
        this.mc = mc;
    }

    @Override
    public String getInitParameter(String name) {
        return getProperty(name);
    }

    @Override
    public Iterator<String> getInitParameterNames() {
        return stringPropertyNames().iterator();
    }

    @Override
    public MailetContext getMailetContext() {
        return mc;
    }

    @Override
    public String getMailetName() {
        return mailetName;
    }

    // Override setProperty to work like it should in this MockMailetConfig
    @Override
    public Object setProperty(String key, String value) {
        String oldValue = getProperty(key);
        String newValue = value;

        if (oldValue != null) {
            newValue = oldValue + "," + value;
        }
        return super.setProperty(key, newValue);
    }
}
