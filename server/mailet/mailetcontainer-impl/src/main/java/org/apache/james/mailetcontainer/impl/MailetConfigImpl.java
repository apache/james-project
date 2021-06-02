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

package org.apache.james.mailetcontainer.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetContext;

/**
 * Implements the configuration object for a Mailet.
 */
public class MailetConfigImpl implements MailetConfig {
    
    // A Pattern used to locate attributes
    private static final Pattern ATTRIBUTE_REGEX = Pattern.compile(Pattern.quote("[@") + ".+" + Pattern.quote("]"));

    /** The mailet MailetContext */
    private MailetContext mailetContext;

    /** The mailet name */
    private String name;

    // This would probably be better.
    // Properties params = new Properties();
    // Instead, we're tied to the Configuration object
    /** The mailet Avalon Configuration */
    private Configuration configuration;

    /**
     * No argument constructor for this object.
     */
    public MailetConfigImpl() {
    }

    /**
     * Get the value of an parameter stored in this MailetConfig. Multi-valued
     * parameters are returned as a comma-delineated string.
     * 
     * @param name
     *            the name of the parameter whose value is to be retrieved.
     * 
     * @return the parameter value
     */
    @Override
    public String getInitParameter(String name) {
        return configuration.getString(name);
    }

    /**
     * @return an iterator over the set of configuration parameter names.
     */
    @Override
    public Iterator<String> getInitParameterNames() {
        Iterator<String> it = configuration.getKeys();
        List<String> params = new ArrayList<>();
        while (it.hasNext()) {
            String param = it.next();
            // Remove all attributes
            param = (ATTRIBUTE_REGEX.matcher(param).replaceAll("")).trim();
            // Store each parameter name just once with a guard that we have not reduced the parameter name to nothing
            if (!params.contains(param) && !param.isEmpty()) {
                params.add(param);
            }
        }
        return params.iterator();
    }

    @Override
    public MailetContext getMailetContext() {
        return mailetContext;
    }

    /**
     * Set the mailet's Avalon Configuration object.
     * 
     * @param newContext
     *            the MailetContext for the mailet
     */
    public void setMailetContext(MailetContext newContext) {
        mailetContext = newContext;
    }

    /**
     * Set the Avalon Configuration object for the mailet.
     * 
     * @param newConfiguration
     *            the new Configuration for the mailet
     */
    public void setConfiguration(Configuration newConfiguration) {
        BaseHierarchicalConfiguration builder = new BaseHierarchicalConfiguration();
        
        // Disable the delimiter parsing. See JAMES-1232
        builder.setListDelimiterHandler(new DisabledListDelimiterHandler());
        Iterator<String> keys = newConfiguration.getKeys();
        while (keys.hasNext()) {
            String key = keys.next();
            String[] values = newConfiguration.getStringArray(key);
            // See JAMES-1177
            // Need to replace ".." with "."
            // See
            // http://commons.apache.org/configuration/userguide-1.2/howto_xml.html
            // Escaping dot characters in XML tags
            key = key.replaceAll("\\.\\.", "\\.");
            
            // Convert array values to a "," delimited string value
            StringBuilder valueBuilder = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                valueBuilder.append(values[i]);
                if (i + 1 < values.length) {
                    valueBuilder.append(",");
                }
            }
            builder.addProperty(key, valueBuilder.toString());
        }

        configuration = builder;
    }

    @Override
    public String getMailetName() {
        return name;
    }

    /**
     * Set the name for the mailet.
     * 
     * @param newName
     *            the new name for the mailet
     */
    public void setMailetName(String newName) {
        name = newName;
    }
}
