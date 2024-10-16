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

import org.osgi.framework.Bundle;
import org.eclipse.core.runtime.ILog;

public interface IPlatform {
    ILog getLog(Bundle bundle);
    Bundle getBundle(String bundleName);
}
