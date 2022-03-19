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
package org.apache.james.container.spring.bean.factory.mailetcontainer;

import jakarta.mail.MessagingException;

import org.apache.james.mailetcontainer.api.MailetLoader;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Loads Mailets for use inside James by using the
 * {@link ConfigurableListableBeanFactory} of spring.
 * 
 * The Mailets are not registered in the factory after loading them!
 */
public class MailetLoaderBeanFactory extends AbstractLoaderBeanFactory<Mailet> implements MailetLoader {

    @Override
    public Mailet getMailet(MailetConfig config) throws MessagingException {
        String mailetName = config.getMailetName();

        try {

            final Mailet mailet = load(mailetName);

            // init the mailet
            mailet.init(config);

            return mailet;

        } catch (MessagingException me) {
            throw me;
        } catch (Exception e) {
            throw loadFailed(mailetName, "mailet", e);
        }
    }

    @Override
    protected String getStandardPackage() {
        return "org.apache.james.transport.mailets";
    }

}
