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
package org.apache.james.queue.library;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueueManagementMBean;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.ManageableMailQueue.MailQueueItemView;
import org.apache.james.queue.api.ManageableMailQueue.MailQueueIterator;
import org.apache.james.queue.api.ManageableMailQueue.Type;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

/**
 * JMX MBean implementation which expose management functions by wrapping a
 * {@link ManageableMailQueue}
 */
public class MailQueueManagement extends StandardMBean implements MailQueueManagementMBean {
    private final ManageableMailQueue queue;

    public MailQueueManagement(ManageableMailQueue queue) throws NotCompliantMBeanException {
        super(MailQueueManagementMBean.class);
        this.queue = queue;

    }

    @Override
    public long clear() throws Exception {
        try {
            return queue.clear();
        } catch (MailQueueException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public long flush() throws Exception {
        try {
            return queue.flush();
        } catch (MailQueueException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public long getSize() throws Exception {
        try {
            return queue.getSize();
        } catch (MailQueueException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public long removeWithName(String name) throws Exception {
        try {
            return queue.remove(Type.Name, name);
        } catch (MailQueueException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public long removeWithRecipient(String address) throws Exception {
        try {
            return queue.remove(Type.Recipient, address);
        } catch (MailQueueException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public long removeWithSender(String address) throws Exception {
        try {
            return queue.remove(Type.Sender, address);
        } catch (MailQueueException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public List<CompositeData> browse() throws Exception {
        MailQueueIterator it = queue.browse();
        List<CompositeData> data = new ArrayList<>();
        String[] names = new String[]{"name", "sender", "state", "recipients", "size", "lastUpdated", "remoteAddress", "remoteHost", "errorMessage", "attributes", "nextDelivery"};
        String[] descs = new String[]{"Unique name", "Sender", "Current state", "Recipients", "Size in bytes", "Timestamp of last update", "IPAddress of the sender", "Hostname of the sender", "Errormessage if any", "Attributes stored", "Timestamp of when the next delivery attempt will be make"};
        OpenType<?>[] types = new OpenType<?>[]{SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.LONG, SimpleType.LONG, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.LONG};

        while (it.hasNext()) {

            MailQueueItemView mView = it.next();
            Mail m = mView.getMail();
            long nextDelivery = mView.getNextDelivery();
            Map<String, Object> map = new HashMap<>();
            map.put(names[0], m.getName());
            String sender = null;
            MailAddress senderAddress = m.getSender();
            if (senderAddress != null) {
                sender = senderAddress.toString();
            }
            map.put(names[1], sender);
            map.put(names[2], m.getState());

            StringBuilder rcptsBuilder = new StringBuilder();
            Collection<MailAddress> rcpts = m.getRecipients();
            if (rcpts != null) {
                Iterator<MailAddress> rcptsIt = rcpts.iterator();
                while (rcptsIt.hasNext()) {
                    rcptsBuilder.append(rcptsIt.next().toString());
                    if (rcptsIt.hasNext()) {
                        rcptsBuilder.append(",");
                    }
                }
            }
            map.put(names[3], rcptsBuilder.toString());
            map.put(names[4], m.getMessageSize());
            map.put(names[5], m.getLastUpdated().getTime());
            map.put(names[6], m.getRemoteAddr());
            map.put(names[7], m.getRemoteHost());
            map.put(names[8], m.getErrorMessage());
            Map<String, String> attrs = new HashMap<>();
            Iterator<String> attrNames = m.getAttributeNames();
            while (attrNames.hasNext()) {
                String attrName = attrNames.next();
                String attrValueString = null;
                Serializable attrValue = m.getAttribute(attrName);
                if (attrValue != null) {
                    attrValueString = attrValue.toString();
                }
                attrs.put(attrName, attrValueString);
            }
            map.put(names[9], attrs.toString());
            map.put(names[10], nextDelivery);
            CompositeDataSupport c = new CompositeDataSupport(new CompositeType(Mail.class.getName(), "Queue Mail", names, descs, types), map);
            data.add(c);
        }
        it.close();
        return data;
    }

}
