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

import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoURI;

import java.net.UnknownHostException;

/**
 * Specification for a factory that creates MongoClient instances
 */
public interface MongoClientFactory {

    /**
     * Creates a MongoClient instance
     * @param uri - database URI
     * @return the MongoClient instance
     * @throws UnknownHostException
     */
    MongoClient createMongoClient(MongoClientURI uri) throws UnknownHostException;
}
