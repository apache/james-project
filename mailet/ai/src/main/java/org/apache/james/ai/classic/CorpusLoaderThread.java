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

package org.apache.james.ai.classic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically reloads corpus.
 */
class CorpusLoaderThread extends Thread {
    private static final Logger LOGGER = LoggerFactory.getLogger(CorpusLoaderThread.class);

    private final BayesianAnalysis analysis;

    CorpusLoaderThread(BayesianAnalysis analysis) {
        super("BayesianAnalysis Corpus Loader");
        this.analysis = analysis;
    }

    /**
     * Thread entry point.
     */
    public void run() {
        LOGGER.info("CorpusLoader thread started: will wake up every " + BayesianAnalysis.CORPUS_RELOAD_INTERVAL + " ms");

        try {
            Thread.sleep(BayesianAnalysis.CORPUS_RELOAD_INTERVAL);

            while (true) {
                if (analysis.getLastCorpusLoadTime() < JDBCBayesianAnalyzer.getLastDatabaseUpdateTime()) {
                    LOGGER.info("Reloading Corpus ...");
                    try {
                        analysis.loadData(analysis.datasource.getConnection());
                        LOGGER.info("Corpus reloaded");
                    } catch (java.sql.SQLException se) {
                        LOGGER.error("SQLException: ", se);
                    }

                }

                if (Thread.interrupted()) {
                    break;
                }
                Thread.sleep(BayesianAnalysis.CORPUS_RELOAD_INTERVAL);
            }
        } catch (InterruptedException ex) {
            interrupt();
        }
    }

}