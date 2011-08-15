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
package org.totalgrid.reef.japi.client;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 *   Settings class that defines properties for an AMQP connection
 */
public class AMQPConnectionSettings
{

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final String virtualHost;

    /**
     * @param host The IP address or DNS name of AMQP broker
     * @param port The TCP port that the broker is listening on (default 5672)
     * @param user The username for the connection
     * @param password The password for the connection
     * @param virtualHost The virtual host to use, default is '/'
     */
    public AMQPConnectionSettings( String host, int port, String user, String password, String virtualHost )
    {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.virtualHost = virtualHost;
    }

    public static AMQPConnectionSettings loadFromFile( String filename ) throws Exception
    {
        Properties props = new Properties();

        FileInputStream fis = new FileInputStream( filename );
        props.load( fis );
        fis.close();

        String reefIp = loadProperty( "org.totalgrid.reef.amqp.host", props );
        String port = loadProperty( "org.totalgrid.reef.amqp.port", props );
        String user = loadProperty( "org.totalgrid.reef.amqp.user", props );
        String password = loadProperty( "org.totalgrid.reef.amqp.password", props );
        String virtualHost = loadProperty( "org.totalgrid.reef.amqp.virtualHost", props );

        return new AMQPConnectionSettings( reefIp, Integer.parseInt( port ), user, password, virtualHost );
    }

    private static String loadProperty( String id, Properties props ) throws Exception
    {
        String prop = props.getProperty( id );
        if ( prop == null )
        {
            throw new Exception( "Could not load configuration. Missing: " + id );
        }
        return prop;
    }


    /**
     *
     * @return host name
     */
    public String getHost()
    {
        return host;
    }

    /**
     *
     * @return TCP port
     */
    public int getPort()
    {
        return port;
    }

    /**
     *
     * @return username
     */
    public String getUser()
    {
        return user;
    }

    /**
     *
     * @return password
     */
    public String getPassword()
    {
        return password;
    }

    /**
     *
     * @return virtual host
     */
    public String getVirtualHost()
    {
        return virtualHost;
    }
}
