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
package ch.qos.logback.contrib.eclipse;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * An appender that wraps the Eclipse platform logging facility and redirects
 * messages to Eclipse's Error Log.
 * 
 * Sample logback configuration:
 * <pre>
 * &lt;configuration>
 *    &lt;appender name="eclipse" class="ch.qos.logback.contrib.EclipseLogAppender">
 *        &lt;encoder>
 *            &lt;pattern>[%method] > %msg%n&lt;/pattern>
 *        &lt;/encoder>
 *        
 *        &lt;!-- optional: bundle whose logger will be used for displaying messages -->
 *        &lt;bundleName>com.example.e4.helloworld&lt;/bundleName>
 *    &lt;/appender>
 *
 *    &lt;root level="TRACE">
 *        &lt;appender-ref ref="eclipse" />
 *    &lt;/root>
 * &lt;/configuration>
 * </pre>
 * 
 * @author Anthony Trinh
 */
public class EclipseLogAppender extends AppenderBase<ILoggingEvent> {

    // make sure this variable matches the name value of Bundle-SymbolicName in MANIFEST.MF
    public static final String DEFAULT_BUNDLE_SYMBOLIC_NAME = "ch.qos.logback.contrib.eclipse";
    
    private static final IPlatform _platform = new IPlatform() {
		@Override
		public ILog getLog(Bundle bundle) {
			return Platform.getLog(bundle);
		}
		
		@Override
		public Bundle getBundle(String bundleName) {
			return Platform.getBundle(bundleName);
		}
	};
	
    private PatternLayoutEncoder _encoder = null;
    private ILog _eclipseLog = null;
    private String _bundleName = null;
    
    /**
     * Checks that required parameters are set, and if everything is in order,
     * activates this appender.
     */
    @Override
    public void start() {
        // encoder requires layout
        if ((_encoder == null) || (_encoder.getLayout() == null)) {
            addError("No layout set for the appender named [" + name + "].");
            return;
        }

        // if no bundle name given, use our own Eclipse log instance
        if (_bundleName == null) {
            addInfo("Assuming name of \"" + DEFAULT_BUNDLE_SYMBOLIC_NAME + "\" for the appender named [" + name + "].");
            _bundleName = DEFAULT_BUNDLE_SYMBOLIC_NAME;
        }

        Bundle bundle = getPlatform().getBundle(_bundleName);
        if (bundle == null) {
            addError("Invalid bundle name for the appender named [" + name + "].");
            return;
        }

        _eclipseLog = getPlatform().getLog(bundle);
        super.start();
    }

    /**
     * Writes an event to Eclipse's Error Log
     * 
     * @param event
     *            the event to be logged
     */
    @Override
    protected void append(ILoggingEvent event) {

        if (!isStarted()) {
            return;
        }

        // Map log level to appropriate Eclipse status
        // (one of: CANCEL, ERROR, INFO, OK, WARNING)
        int status;
        int lev = event.getLevel().levelInt;
        switch (lev) {
        case Level.WARN_INT:
            status = Status.WARNING;
            break;

        case Level.ERROR_INT:
            status = Status.ERROR;
            break;

        case Level.INFO_INT:
            status = Status.INFO;
            break;

        case Level.TRACE_INT: // map to CANCEL
            status = Status.CANCEL;
            break;

        default:
            status = Status.OK;
            break;
        }

        // Log the message if level allows (not off)
        if (lev != Level.OFF_INT) {
            String msg = _encoder.getLayout().doLayout(event);
            _eclipseLog.log(new Status(status, _bundleName, lev, msg, null));
        }
    }

    /**
     * Gets the pattern-layout encoder for this appender's log message
     * 
     * @return the pattern-layout encoder
     */
    public PatternLayoutEncoder getEncoder() {
        return _encoder;
    }

    /**
     * Sets the pattern-layout encoder for this appender's log message
     * 
     * @param encoder
     *            the pattern-layout encoder
     */
    public void setEncoder(PatternLayoutEncoder encoder) {
        _encoder = encoder;
    }

    /**
     * Gets the symbolic name of the Eclipse plugin bundle whose logger is used
     * for logging messages
     * 
     * @return the symbolic name of the Eclipse plugin bundle
     */
    public String getBundleName() {
        return _bundleName;
    }

    /**
     * Sets the name of the Eclipse plugin bundle whose logger will be used for
     * logging messages. This must match the name from the "Bundle-SymbolicName"
     * property of the bundle's manifest. If start() is called without setting
     * this value, this Eclipse logback extension's bundle name is used as a 
     * default.
     * 
     * @param bundleName
     *            the symbolic name of the Eclipse plugin bundle
     */
    public void setBundleName(String bundleName) {
        _bundleName = bundleName;
    }
    
    /**
     * Gets the Eclipse platform 
     */
    protected IPlatform getPlatform() {
    	return _platform;
    }
}
