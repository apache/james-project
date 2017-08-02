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

package org.apache.james.protocols.api.future;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.protocols.api.AbstractResponse;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.logger.Logger;
import org.apache.james.protocols.api.logger.ProtocolLoggerAdapter;
import org.slf4j.LoggerFactory;

/**
 * {@link FutureResponse} implementation which wraps a {@link AbstractResponse} implementation
 * 
 *
 */
public class FutureResponseImpl implements FutureResponse{
    
    private final Logger logger;

    public FutureResponseImpl() {
        this(new ProtocolLoggerAdapter(LoggerFactory.getLogger(FutureResponseImpl.class)));
    }
    
    public FutureResponseImpl(Logger logger) {
        this.logger = logger;
    }
    
    protected Response response;
    private List<ResponseListener> listeners;
    private int waiters;

    protected final synchronized void checkReady() {
        while (!isReady()) {
            try {
                waiters++;
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                waiters--;
            }
        }
    }
    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.FutureResponse#addListener(org.apache.james.protocols.api.FutureResponse.ResponseListener)
     */
    public synchronized void addListener(ResponseListener listener) {
        if (isReady()) {
            listener.onResponse(this);
        } else {
            if (listeners == null) {
                listeners = new ArrayList<>();
            }
            listeners.add(listener);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.FutureResponse#removeListener(org.apache.james.protocols.api.FutureResponse.ResponseListener)
     */
    public synchronized void removeListener(ResponseListener listener) {
        if (!isReady()) {
            if (listeners != null) {
                listeners.remove(listener);
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.FutureResponse#isReady()
     */
    public synchronized boolean isReady() {
        return response != null;
    }
    
    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.Response#getLines()
     */
    public List<CharSequence> getLines() {
        checkReady();
        return response.getLines();
    }


    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.Response#getRetCode()
     */
    public String getRetCode() {
        checkReady();
        return response.getRetCode();
    }


    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.Response#isEndSession()
     */
    public boolean isEndSession() {
        checkReady();
        return response.isEndSession();
    }

    @Override
    public synchronized String toString() {
        checkReady();
        return response.toString();
    }
    
    /**
     * Set the {@link Response} which will be used to notify the registered
     * {@link ResponseListener}'. After this method is called all waiting
     * threads will get notified and {@link #isReady()} will return <code>true<code>. 
     * 
     * @param response
     */
    public void setResponse(Response response) {
        boolean fire = false;
        synchronized (this) {
            if (!isReady()) {
                this.response = response;
                fire = listeners != null;

                if (waiters > 0) {
                    notifyAll();
                }
            }
        }

        if (fire) {
            for (ResponseListener listener : listeners) {
                try {
                    listener.onResponse(this);
                } catch (Throwable e) {
                    logger.warn("An exception was thrown by the listener " + listener, e);
                }
            }
            listeners = null;
            
        }
    }

}
