/**
 * Copyright 2011 Green Energy Corp.
 * 
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.reef.client.factory;

import net.agileautomata.executor4s.Executor;
import net.agileautomata.executor4s.ExecutorService;
import net.agileautomata.executor4s.Executors;
import net.agileautomata.executor4s.Minutes;
import org.totalgrid.reef.broker.BrokerConnection;
import org.totalgrid.reef.broker.BrokerConnectionFactory;
import org.totalgrid.reef.broker.qpid.QpidBrokerConnectionFactory;
import org.totalgrid.reef.client.Connection;
import org.totalgrid.reef.client.ConnectionWatcher;
import org.totalgrid.reef.client.ReconnectingConnectionFactory;
import org.totalgrid.reef.client.ServicesList;
import org.totalgrid.reef.client.javaimpl.ConnectionWrapper;
import org.totalgrid.reef.client.sapi.client.rest.impl.DefaultConnection;
import org.totalgrid.reef.client.sapi.client.rest.impl.DefaultReconnectingFactory;
import org.totalgrid.reef.client.settings.AmqpSettings;

import java.util.HashSet;
import java.util.Set;

/**
 * implementation of reconnecting factory for java applications.
 */
public class ReefReconnectingFactory implements ReconnectingConnectionFactory
{
    // TODO: Make this a static factory class, put implementations in scala

    private class Watcher implements org.totalgrid.reef.client.sapi.client.rest.ConnectionWatcher
    {
        @Override
        public synchronized void onConnectionClosed( boolean expected )
        {
            for ( ConnectionWatcher cw : watchers )
            {
                cw.onConnectionClosed( expected );
            }
        }

        @Override
        public synchronized void onConnectionOpened( BrokerConnection connection )
        {
            org.totalgrid.reef.client.sapi.client.rest.impl.DefaultConnection scalaConnection;
            scalaConnection = new DefaultConnection( connection, exe, 5000 );
            scalaConnection.addServicesList( servicesList );
            Connection c = new ConnectionWrapper( scalaConnection, exe );
            for ( ConnectionWatcher cw : watchers )
            {
                cw.onConnectionOpened( c );
            }
        }
    }

    private Set<ConnectionWatcher> watchers = new HashSet<ConnectionWatcher>();
    private final BrokerConnectionFactory brokerConnectionFactory;
    private final ExecutorService exeService;
    private final Executor exe;
    private final ServicesList servicesList;

    private final DefaultReconnectingFactory factory;

    private final Watcher watcher = new Watcher();

    /**
     * @param settings amqp settings
     * @param list services list from service-client package
     * @param startDelayMs beginning delay if can't connect first time
     * @param maxDelayMs delay doubles in length upto this maxTime
     */
    public static ReconnectingConnectionFactory buildFactory( AmqpSettings settings, ServicesList list, long startDelayMs, long maxDelayMs )
    {
        BrokerConnectionFactory brokerConnectionFactory = new QpidBrokerConnectionFactory( settings );
        return new ReefReconnectingFactory( brokerConnectionFactory, list, startDelayMs, maxDelayMs );
    }

    /**
     * @param settings amqp settings
     * @param list services list from service-client package
     * @param startDelayMs beginning delay if can't connect first time
     * @param maxDelayMs delay doubles in length upto this maxTime
     *
     * @deprecated Use buildFactory() instead
     */
    @Deprecated
    public ReefReconnectingFactory( AmqpSettings settings, ServicesList list, long startDelayMs, long maxDelayMs )
    {
        this.brokerConnectionFactory = new QpidBrokerConnectionFactory( settings );
        this.exeService = Executors.newResizingThreadPool( new Minutes( 5 ) );
        this.exe = exeService;
        servicesList = list;
        factory = new DefaultReconnectingFactory( brokerConnectionFactory, exe, startDelayMs, maxDelayMs );
        factory.addConnectionWatcher( watcher );
    }

    /**
     * @param brokerConnectionFactory connection factory to the broker
     * @param list services list from service-client package
     * @param startDelayMs beginning delay if can't connect first time
     * @param maxDelayMs delay doubles in length upto this maxTime
     */
    public ReefReconnectingFactory( BrokerConnectionFactory brokerConnectionFactory, ServicesList list, long startDelayMs, long maxDelayMs )
    {
        this.brokerConnectionFactory = brokerConnectionFactory;
        this.exeService = Executors.newResizingThreadPool( new Minutes( 5 ) );
        this.exe = exeService;
        servicesList = list;
        factory = new DefaultReconnectingFactory( brokerConnectionFactory, exe, startDelayMs, maxDelayMs );
        factory.addConnectionWatcher( watcher );
    }

    /**
     * @param brokerConnectionFactory connection factory to the broker
     * @param exe Executor to use
     * @param list services list from service-client package
     * @param startDelayMs beginning delay if can't connect first time
     * @param maxDelayMs delay doubles in length upto this maxTime
     */
    public ReefReconnectingFactory( BrokerConnectionFactory brokerConnectionFactory, Executor exe, ServicesList list, long startDelayMs,
        long maxDelayMs )
    {
        this.brokerConnectionFactory = brokerConnectionFactory;
        this.exeService = null;
        this.exe = exe;
        servicesList = list;
        factory = new DefaultReconnectingFactory( brokerConnectionFactory, exe, startDelayMs, maxDelayMs );
        factory.addConnectionWatcher( watcher );
    }


    public synchronized void addConnectionWatcher( ConnectionWatcher watcher )
    {
        watchers.add( watcher );
    }

    public synchronized void removeConnectionWatcher( ConnectionWatcher watcher )
    {
        watchers.remove( watcher );
    }

    public void start()
    {
        factory.start();
    }

    public void stop()
    {
        factory.stop();
        if ( exeService != null )
        {
            exeService.terminate();
        }
    }
}
