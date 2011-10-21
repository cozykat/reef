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

import static org.junit.Assert.*;
import org.junit.*;
import org.totalgrid.reef.api.japi.ReefServiceException;
import org.totalgrid.reef.proto.Model.Entity;

import org.totalgrid.reef.integration.helpers.*;

@SuppressWarnings("unchecked")
public class TestServiceClientExceptionBehavior extends ReefConnectionTestBase
{

    @Test
    public void getAllEntities()
    {
        // TODO: reimplement test
        /*
        client.close();

        try
        {
            client.get( Entity.newBuilder().build() );
            fail( "Closed client should throw exception" );
        }
        catch ( ReefServiceException ex )
        {
        }
         */
    }

    @Test
    public void settingNullAuthTokenThrows()
    {
        // TODO: reimplement test
        /*
        try
        {
            helpers.setHeaders( helpers.getHeaders().setAuthToken( null ) );
            assertTrue( false );
        }
        catch ( IllegalArgumentException e )
        {
            assertTrue( true );
        }
         */
    }
}
