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

package org.apache.james.container.spring.bean.factorypostprocessor;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.container.spring.lifecycle.ConfigurationProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import com.google.common.base.Strings;

public class EventsConfigurationBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        ConfigurationProvider confProvider = beanFactory.getBean(ConfigurationProvider.class);
        try {
            HierarchicalConfiguration config = confProvider.getConfiguration("events");
            String type = config.getString("type", "default");
            String serialization = config.getString("serialization", "json");
            String publisher = config.getString("publisher", "kafka");
            String registration = config.getString("registration", "cassandra");
            String delivery = config.getString("delivery", "synchronous");
            String delegatingListenerAlias = getDelegatingListenerAlias(type);
            String serializationAlias = getSerializationAlias(serialization);
            String registrationAlias = getRegistrationAlias(registration);
            String deliveryAlias = getDeliveryString(delivery);
            String publisherAlias = null;
            String consumerAlias = null;

            if (publisher.equals("kafka")) {
                publisherAlias = "kafka-publisher";
                consumerAlias = "kafka-consumer";
            }

            detectInvalidValue(delegatingListenerAlias, "Delegating listener type " + type + " not supported!");
            detectInvalidValue(deliveryAlias, "Event delivery " + delivery + " not supported");
            beanFactory.registerAlias(delegatingListenerAlias, "delegating-listener");
            beanFactory.registerAlias(deliveryAlias, "event-delivery");
            if (!delegatingListenerAlias.equals("default")) {
                detectInvalidValue(serializationAlias, "Serialization system type " + serialization + " not supported!");
                detectInvalidValue(publisherAlias, "Publisher system type " + publisher + " not supported!");
                beanFactory.registerAlias(serializationAlias, "event-serializer");
                beanFactory.registerAlias(publisherAlias, "publisher");
                beanFactory.registerAlias(consumerAlias, "consumer");
                if (delegatingListenerAlias.equals("registered")) {
                    detectInvalidValue(registrationAlias, "Registration system type " + registration + " not supported!");
                    beanFactory.registerAlias(registrationAlias, "distant-mailbox-path-register-mapper");
                }
            }

        } catch (ConfigurationException e) {
            throw new FatalBeanException("Unable to config the mailboxmanager", e);
        }
    }

    private void detectInvalidValue(String registrationAlias, String message) throws ConfigurationException {
        if (Strings.isNullOrEmpty(registrationAlias)) {
            throw new ConfigurationException(message);
        }
    }

    private String getRegistrationAlias(String registration) {
        if (registration.equals("cassandra")) {
            return  "cassandra-mailbox-path-register-mapper";
        }
        return null;
    }

    private String getSerializationAlias(String serialization) {
        if (serialization.equals("json")) {
            return "json-event-serializer";
        } else if (serialization.equals("message-pack")) {
            return "message-pack-event-serializer";
        }
        return null;
    }

    private String getDelegatingListenerAlias(String type) {
        if (type.equals("default")) {
            return "default-delegating-listener";
        } else if (type.equals("broadcast")) {
            return "broadcast-delegating-listener";
        } else if (type.equals("registered")) {
            return "registered-delegating-listener";
        }
        return null;
    }

    public String getDeliveryString(String delivery) {
        if (delivery.equals("synchronous")) {
            return  "synchronous-event-delivery";
        } else if (delivery.equals("asynchronous")) {
            return  "asynchronous-event-delivery";
        } else if (delivery.equals("mixed")) {
            return  "mixed-event-delivery";
        }
        return null;
    }
}
