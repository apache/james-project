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


package org.apache.mailet.base.mail;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

import javax.activation.ActivationDataFlavor;
import javax.activation.DataContentHandler;
import javax.activation.DataSource;
import javax.mail.MessagingException;


/**
 * Abstract class providing common Data Handler behavior.
 */
public abstract class AbstractDataContentHandler implements DataContentHandler {

    private ActivationDataFlavor fieldDataFlavor;

    /**
     * Default Constructor
     */
    public AbstractDataContentHandler() {
        super();
    }

    /**
     * Update the current DataFlavor.
     * 
     */    
    protected void updateDataFlavor() {
        setDataFlavor(computeDataFlavor());
    }

    /**
     * Compute an ActivationDataFlavor.
     * 
     * @return A new ActivationDataFlavor
     */
    protected abstract ActivationDataFlavor computeDataFlavor();

    protected void setDataFlavor(ActivationDataFlavor aDataFlavor) {
        fieldDataFlavor = aDataFlavor;
    }

    @Override
    public Object getContent(DataSource aDataSource) throws IOException {
        Object content = null;
        try {
            content = computeContent(aDataSource);
        } catch (MessagingException e) {
            // No-op
        }
        return content;
    }

    /**
     * Compute the content from aDataSource.
     * 
     * @param aDataSource
     * @return new Content built from the DataSource
     * @throws MessagingException
     */
    protected abstract Object computeContent(DataSource aDataSource)
            throws MessagingException;

    @Override
    public Object getTransferData(DataFlavor aDataFlavor, DataSource aDataSource)
            throws UnsupportedFlavorException, IOException {
        Object content = null;
        if (getDataFlavor().equals(aDataFlavor)) {
            content = getContent(aDataSource);
        }
        return content;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{getDataFlavor()};
    }

    /**
     * Get the DataFlavor, lazily initialised if required.
     * 
     * @return Returns the dataFlavor, lazily initialised.
     */
    protected ActivationDataFlavor getDataFlavor() {
        ActivationDataFlavor dataFlavor;
        if (null == (dataFlavor = getDataFlavorBasic())) {
            updateDataFlavor();
            return getDataFlavor();
        }
        return dataFlavor;
    }

    /**
     * Get the DataFlavor.
     * 
     * @return Returns the dataFlavor.
     */
    private ActivationDataFlavor getDataFlavorBasic() {
        return fieldDataFlavor;
    }

}
