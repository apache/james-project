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

package org.apache.james.mpt.onami.test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.james.mpt.onami.test.annotation.GuiceModules;
import org.apache.james.mpt.onami.test.annotation.GuiceProvidedModules;
import org.apache.james.mpt.onami.test.annotation.Mock;
import org.apache.james.mpt.onami.test.annotation.MockFramework;
import org.apache.james.mpt.onami.test.annotation.MockType;
import org.apache.james.mpt.onami.test.guice.MockTypeListener;
import org.apache.james.mpt.onami.test.handler.GuiceInjectableClassHandler;
import org.apache.james.mpt.onami.test.handler.GuiceModuleHandler;
import org.apache.james.mpt.onami.test.handler.GuiceProvidedModuleHandler;
import org.apache.james.mpt.onami.test.handler.MockFrameworkHandler;
import org.apache.james.mpt.onami.test.handler.MockHandler;
import org.apache.james.mpt.onami.test.mock.MockEngine;
import org.apache.james.mpt.onami.test.reflection.ClassVisitor;
import org.apache.james.mpt.onami.test.reflection.HandleException;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.matcher.Matchers;
import com.google.inject.util.Modules;

/**
 * <p>
 * It's a {@link BlockJUnit4ClassRunner} runner.
 * </p>
 * <p>
 * This class creates a Google Guice {@link Injector} configured by {@link GuiceModules} annotation (only fr modules
 * with default constructor) and {@link GuiceProvidedModules} annotation and {@link Mock}.
 * </p>
 * <p>
 * <b>Example #1:</b> <br>
 * 
 * <pre>
 * 
 * &#064;org.junit.runner.RunWith( OnamiRunner.class )
 * &#064;GuiceModules( SimpleModule.class )
 * public class AcmeTestCase
 * {
 * 
 *     &#064;GuiceProvidedModules
 *     static public Module getProperties()
 *     {

import org.apache.onami.test.reflection.AnnotationHandler;
import org.apache.onami.test.reflection.HandleException;
 *         ...
 *         return Modules.combine(new ComplexModule( loadProperies() ), ...  );
 *     }
 * 
 * </pre>
 * 
 * </p>
 * <p>
 * <b>Example #2:</b> <br>
 * 
 * <pre>
 * 
 * &#064;org.junit.runner.RunWith( OnamiRunner.class )
 * public class AcmeTestCase
 *     extends com.google.inject.AbstractModule
 * {
 * 
 *     public void configure()
 *     {
 *         // Configure your proper modules
 *         ...
 *         bind( Service.class ).annotatedWith( TestAnnotation.class ).to( ServiceTestImpl.class );
 *         ...
 *     }
 * 
 *     &#064;Mock
 *     private AnotherService serviceMock;
 * 
 *     &#064;Inject
 *     private Service serviceTest;
 * 
 *     &#064;org.junit.Test
 *     public void test()
 *     {
 *         assertNotNull( serviceMock );
 *         assertNotNull( serviceTest );
 *     }
 * </pre>
 * 
 * </p>
 * 
 * @see GuiceMockModule
 */
public class OnamiRunner
    extends BlockJUnit4ClassRunner
{

    private static final Logger LOGGER = Logger.getLogger( OnamiRunner.class.getName() );

    private Injector injector;

    private final List<Module> allModules;

    private final Map<Field, Object> mocked = new HashMap<Field, Object>( 1 );

    private MockType mockFramework = MockType.EASY_MOCK;

    /**
     * OnamiRunner constructor to create the core JUnice class.
     * 
     * @see org.junit.runner.RunWith
     * @param klass The test case class to run.
     * @throws org.junit.runners.model.InitializationError if any error occurs.
     */
    public OnamiRunner( Class<?> klass )
        throws InitializationError
    {
        super( klass );

        try
        {
            if ( LOGGER.isLoggable( Level.FINER ) )
            {
                LOGGER.finer( "Inizializing injector for test class: " + klass.getName() );
            }

            this.allModules = inizializeInjector( klass );

            if ( LOGGER.isLoggable( Level.FINER ) )
            {
                LOGGER.finer( "done..." );
            }
        }
        catch ( Exception e )
        {
            final List<Throwable> throwables = new LinkedList<Throwable>();
            throwables.add( e );
            throw new InitializationError( throwables );
        }
    }

    /**
     * OnamiRunner constructor to create the runner needed
     * by the OnamiSuite class.
     * 
     * @see org.junit.runner.RunWith
     * @param suite The suite test case class to run.
     * @param test The test case class to run.
     * @throws org.junit.runners.model.InitializationError if any error occurs.
     */
    public OnamiRunner( Class<?> suite, Class<?> test )
        throws InitializationError
    {
        super( test );

        try
        {
            if ( LOGGER.isLoggable( Level.FINER ) )
            {
                LOGGER.finer( "Inizializing injector for test class: " + test.getName() );
            }

            this.allModules = inizializeInjector( suite, test );

            if ( LOGGER.isLoggable( Level.FINER ) )
            {
                LOGGER.finer( "done..." );
            }
        }
        catch ( Exception e )
        {
            final List<Throwable> throwables = new LinkedList<Throwable>();
            throwables.add( e );
            throw new InitializationError( throwables );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run( final RunNotifier notifier )
    {
        if ( LOGGER.isLoggable( Level.FINER ) )
        {
            LOGGER.finer( " ### Run test case: " + getTestClass().getJavaClass() + " ### " );
            LOGGER.finer( " #### Creating injector ####" );
        }

        this.injector = createInjector( allModules );
        super.run( notifier );
        this.flush();

        if ( LOGGER.isLoggable( Level.FINER ) )
        {
            LOGGER.finer( " ### End test case: " + getTestClass().getJavaClass().getName() + " ### " );
        }
    }

    /**
     * {@inheritDoc}
     */
    private void flush()
    {
        this.injector = null;
        this.allModules.clear();
        this.mocked.clear();
    }

    @Override
    protected void runChild( FrameworkMethod method, RunNotifier notifier )
    {
        if ( LOGGER.isLoggable( Level.FINER ) )
        {
            LOGGER.finer( " +++ invoke test method: " + method.getName() + " +++ " );
        }

        super.runChild( method, notifier );
        resetAllResetAfterMocks();

        if ( LOGGER.isLoggable( Level.FINER ) )
        {
            LOGGER.finer( " --- end test method: " + method.getName() + " --- " );
        }
    }

    /**
     * Creates test instance via Google-Guice to inject all not-static dependencies.
     * @return The instance of the test case.
     * @throws Exception when an error occurs.
     */
    @Override
    protected Object createTest()
        throws Exception
    {
        if ( LOGGER.isLoggable( Level.FINER ) )
        {
            LOGGER.finer( " Create and inject test class: " + getTestClass().getJavaClass() );
        }
        return this.injector.getInstance( getTestClass().getJavaClass() );
    }

    /**
     * Shortcut to create the Injector given a list of Modules.
     *
     * @param modules the list of modules have to be load
     * @return an Injector instance built using the input Module list
     */
    protected Injector createInjector( List<Module> modules )
    {
        return Guice.createInjector( modules );
    }

    /**
     * This method collects modules from {@link GuiceModules}, {@link GuiceProvidedModules}, {@link Mock}
     * and {@ OnamiSuite}.
     *
     * @param <T> whatever input type is accepted
     * @param suite the input suite to be analyzed
     * @param test the input class has to be analyzed
     * @return a List of Guice Modules built after input class analysis.
     * @throws IllegalAccessException when a n error occurs.
     * @throws InstantiationException when a n error occurs.
     * @throws HandleException when a n error occurs.
     */
    protected <T> List<Module> inizializeInjector( Class<?> suite, Class<T> test)
        throws HandleException, InstantiationException, IllegalAccessException
    {
        final List<Module> modules = inizializeInjector(test);
        Module m = visitClass( suite );
        if ( m != null )
        {
            modules.add( m );
        }
        return modules;
    }

    /**
     * This method collects modules from {@link GuiceModules}, {@link GuiceProvidedModules}, {@link Mock}.
     *
     * @param <T> whatever input type is accepted
     * @param clazz the input class has to be analyzed
     * @return a List of Guice Modules built after input class analysis.
     * @throws IllegalAccessException when a n error occurs.
     * @throws InstantiationException when a n error occurs.
     * @throws HandleException when a n error occurs.
     */
    protected <T> List<Module> inizializeInjector( Class<T> clazz )
        throws HandleException, InstantiationException, IllegalAccessException
    {
        final List<Module> modules = new ArrayList<Module>();
        Module m = visitClass( clazz );
        if ( m != null )
        {
            modules.add( m );
        }
        return modules;
    }

    private void resetAllResetAfterMocks()
    {
        for ( Entry<Field, Object> entry : mocked.entrySet() )
        {
            final Mock mockAnnotation = entry.getKey().getAnnotation( Mock.class );
            if ( mockAnnotation.resetAfter() )
            {
                MockEngine mockEngine = MockEngineFactory.getMockEngine( mockFramework );
                mockEngine.resetMock( entry.getValue() );
            }
        }
    }

    /**
     * @throws HandleException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    private <T> Module visitClass( final Class<T> clazz )
        throws HandleException, InstantiationException, IllegalAccessException
    {
        try
        {
            if ( LOGGER.isLoggable( Level.FINER ) )
            {
                LOGGER.finer( "  Start introspecting class: " + clazz.getName() );
            }
            final List<Module> allModules = new ArrayList<Module>();

            // Setup the handlers
            final GuiceProvidedModuleHandler guiceProvidedModuleHandler = new GuiceProvidedModuleHandler();
            final GuiceModuleHandler guiceModuleHandler = new GuiceModuleHandler();
            final GuiceInjectableClassHandler<Inject> guiceInjectableClassHandler = new GuiceInjectableClassHandler<com.google.inject.Inject>();
            final GuiceInjectableClassHandler<javax.inject.Inject> jsr330InjectableClassHandler = new GuiceInjectableClassHandler<javax.inject.Inject>();

            final MockHandler mockHandler = new MockHandler();
            final MockFrameworkHandler mockFrameworkHandler = new MockFrameworkHandler();

            // Visit class and super-classes
            new ClassVisitor()
            .registerHandler( GuiceProvidedModules.class, guiceProvidedModuleHandler )
            .registerHandler( GuiceModules.class, guiceModuleHandler )
            .registerHandler( Mock.class, mockHandler )
            .registerHandler( MockFramework.class, mockFrameworkHandler )
            .registerHandler( com.google.inject.Inject.class, guiceInjectableClassHandler )
            .registerHandler( javax.inject.Inject.class, jsr330InjectableClassHandler )
            .visit( clazz );

            // Retrieve mock framework
            if ( mockFrameworkHandler.getMockType() != null )
            {
                this.mockFramework = mockFrameworkHandler.getMockType();
            }

            // retrieve the modules founded
            allModules.addAll( guiceProvidedModuleHandler.getModules() );
            allModules.addAll( guiceModuleHandler.getModules() );
            MockEngine engine = MockEngineFactory.getMockEngine( this.mockFramework );
            this.mocked.putAll( mockHandler.getMockedObject( engine ) );
            if ( !this.mocked.isEmpty() )
            {
                // Replace all real module binding with Mocked moduled.
                Module m = Modules.override( allModules ).with( new GuiceMockModule( this.mocked ) );
                allModules.clear();
                allModules.add( m );
            }

            // Add only clasess that have got the Inject annotation
             final Class<?>[] guiceInjectableClasses = guiceInjectableClassHandler.getClasses();
             final Class<?>[] jsr330InjectableClasses = jsr330InjectableClassHandler.getClasses();

            final AbstractModule statcInjector = new AbstractModule()
            {
                @Override
                protected void configure()
                {
                    // inject all STATIC dependencies
                    if ( guiceInjectableClasses.length != 0 )
                    {
                        requestStaticInjection( guiceInjectableClasses );
                    }
                    
                    if ( jsr330InjectableClasses.length != 0 )
                    {
                        requestStaticInjection( jsr330InjectableClasses );
                    }

                    
                }
            };
            if ( guiceInjectableClasses.length != 0 || jsr330InjectableClasses.length != 0 )
            {
                allModules.add( statcInjector );
            }

            // Check if the class is itself a Google Module.
            if ( Module.class.isAssignableFrom( getTestClass().getJavaClass() ) )
            {
                if ( LOGGER.isLoggable( Level.FINER ) )
                {
                    LOGGER.finer( "   creating module from test class " + getTestClass().getJavaClass() );
                }
                final Module classModule = (Module) getTestClass().getJavaClass().newInstance();
                allModules.add( classModule );
            }

            // create MockTypeListenerModule
            if ( this.mocked.size() != 0 )
            {
                final AbstractModule mockTypeListenerModule = new AbstractModule()
                {
                    @Override
                    protected void configure()
                    {
                        bindListener( Matchers.any(), new MockTypeListener( mocked ) );
                    }
                };

                // BEGIN patch for issue: google-guice: #452
                for ( Entry<Field, Object> entry : mocked.entrySet() )
                {
                    final Field field = entry.getKey();
                    final Object mock = entry.getValue();
                    if ( Modifier.isStatic( field.getModifiers() ) )
                    {
                        if ( LOGGER.isLoggable( Level.FINER ) )
                        {
                            LOGGER.finer( "   inject static mock field: " + field.getName() );
                        }

                        AccessController.doPrivileged( new PrivilegedAction<Void>()
                        {

                            public Void run()
                            {
                                field.setAccessible( true );
                                return null;
                            }

                        } );
                        field.set( field.getDeclaringClass(), mock );
                    }
                }
                // END patch for issue: google-guice: #452

                allModules.add( mockTypeListenerModule );
            }

            if ( allModules.size() != 0 )
            {
                if ( LOGGER.isLoggable( Level.FINER ) )
                {
                    StringBuilder builder = new StringBuilder();
                    builder.append( " Collected modules: " );
                    builder.append( "\n" );
                    for ( Module module : allModules )
                    {
                        builder.append( "    " ).append( module );
                        builder.append( "\n" );
                    }
                    LOGGER.finer( builder.toString() );
                }
                return Modules.combine( allModules );
            }
            return null;
        }
        finally
        {
            if ( LOGGER.isLoggable( Level.FINER ) )
            {
                LOGGER.finer( " ...done" );
            }
        }
    }

}
