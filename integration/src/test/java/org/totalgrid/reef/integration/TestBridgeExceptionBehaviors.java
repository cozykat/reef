/**
 * Copyright 2011 Green Energy Corp.
 * 
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the GNU Affero General Public License
 * Version 3.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 * http://www.gnu.org/licenses/agpl.html
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.reef.integration;

import static org.junit.Assert.fail;

import org.junit.Test;

import org.totalgrid.reef.api.japi.client.impl.AMQPConnectionSettingImpl;
import org.totalgrid.reef.api.japi.client.ConnectionSettings;
import org.totalgrid.reef.api.japi.client.Connection;
import org.totalgrid.reef.messaging.javaclient.AMQPConnection;

public class TestBridgeExceptionBehaviors
{
    @Test
    public void throwsExceptionWhenNotStarted()
    {
        ConnectionSettings settings = new AMQPConnectionSettingImpl( "127.0.0.1", 5672, "guest", "guest", "test", false, "", "" );
        Connection connection = new AMQPConnection( settings, 5000 );

        try
        {
            connection.newSession();
            fail( "Should throw exception when not connected" );
        }
        catch ( Exception ex )
        {
        }
    }
}
