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
package ch.qos.logback.classic.net;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * End to end test for XmppAppender
 * @author szalik
 */
@Ignore
public class XmppAppenderEntToEndTest {

    @Test 
    public void testSimpleMessage() {
        Logger logger = LoggerFactory.getLogger(getClass());
        logger.warn("Test message 22");
    }

    @Test
    public void testMessageWithException() {
        Logger logger = LoggerFactory.getLogger(getClass());
        try {
            throw new Exception("Exception message.");
        } catch (Exception e) {
            logger.info("Exception text.", e);
        }
    }

    /**
     * Wait for async thread to deliver the message, before java process is killed.
     */
    @After
    public void waitFor() throws InterruptedException {
        Thread.sleep(1800);
    }
}
