# Problem

As stated in the [Logback documentation](http://logback.qos.ch/setup.html)
conditional processing in configuration files requires the
[Janino library](http://unkrig.de/w/Janino). When running in an OSGi environment
(e.g. an Eclipse RCP application) simply placing `commons-compiler.jar` and
`janino.jar` on your application's class path is not sufficient enough.
You will get the following exception:

```
<...>
12:50:47,754 |-INFO in ch.qos.logback.classic.joran.action.RootLoggerAction - Setting level of ROOT logger to DEBUG
12:50:47,755 |-INFO in ch.qos.logback.core.joran.action.AppenderRefAction - Attaching appender named [stdout] to Logger[ROOT]
12:50:47,755 |-INFO in ch.qos.logback.core.joran.action.AppenderRefAction - Attaching appender named [file] to Logger[ROOT]
12:50:47,810 |-ERROR in ch.qos.logback.core.joran.conditional.IfAction -
  Failed to parse condition [property("instance").equals("PRODUCTION_ENV")] org.codehaus.janino.JaninoRuntimeException:
  Cannot load class 'ch.qos.logback.core.joran.conditional.PropertyWrapperForScripts' through the parent loader
  at org.codehaus.janino.JaninoRuntimeException
<...>
```

# Solution implemented by this project

The `org.codehaus.janino` bundle needs a `Require-Bundle: ch.qos.logback.core`
directive in it's manifest file in order for the bundle class loader to be
able to load classes from the `ch.qos.logback.core` bundle. This can be achieved
by extending the `org.codehaus.janino` bundle via a fragment
(`ch.qos.logback.contrib.logback-janino-fragment`).
