/**
 * Copyright (C) 2016, The logback-contrib developers. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.contrib.mongodb;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

import java.net.UnknownHostException;

/**
 * Factory of {@link com.mongodb.MongoClient} instances
 */
public class DefaultMongoClientFactory implements MongoClientFactory {

    /**
     * Creates a {@link com.mongodb.Mongo} instance
     * @param uri - database URI
     * @return the MongoClient instance
     * @throws UnknownHostException
     */
    @Override
    public MongoClient createMongoClient(MongoClientURI uri) throws UnknownHostException {
        return new MongoClient(uri);
    }
}
