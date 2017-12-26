package org.apache.james.transport.util;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.google.common.base.Throwables;

public class Patterns {

    public static Pattern compilePatternUncheckedException(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException mpe) {
            throw Throwables.propagate(mpe);
        }
    }
}
