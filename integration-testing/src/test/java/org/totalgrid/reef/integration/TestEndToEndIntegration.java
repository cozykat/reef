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
package org.totalgrid.reef.integration;

import org.junit.Test;

import org.totalgrid.reef.clientapi.Subscription;
import org.totalgrid.reef.clientapi.SubscriptionEvent;
import org.totalgrid.reef.clientapi.SubscriptionResult;
import org.totalgrid.reef.clientapi.exceptions.ReefServiceException;
import org.totalgrid.reef.clientapi.proto.Envelope;
import org.totalgrid.reef.integration.helpers.MockSubscriptionEventAcceptor;
import org.totalgrid.reef.integration.helpers.ReefConnectionTestBase;

import org.totalgrid.reef.client.rpc.MeasurementService;

import org.totalgrid.reef.client.rpc.PointService;
import org.totalgrid.reef.proto.FEP;
import org.totalgrid.reef.proto.Measurements;
import org.totalgrid.reef.proto.Model;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * tests to prove that the simulator is up and measurements are being processed correctly.
 */
@SuppressWarnings("unchecked")
public class TestEndToEndIntegration extends ReefConnectionTestBase
{

    /**
     * Tests subscribing to the measurement snapshot service via a get operation
     */
    @Test
    public void testSimulatorProducingMeasurements() throws java.lang.InterruptedException, ReefServiceException
    {

        MeasurementService ms = helpers;
        PointService ps = helpers;

        // make sure all of the endpoints are enabled and COMMS_UP so measurements should be published
        List<FEP.Endpoint> endpoints = helpers.getEndpoints();
        for ( FEP.Endpoint endpoint : endpoints )
        {
            FEP.EndpointConnection connection = helpers.getEndpointConnectionByUuid( endpoint.getUuid() );
            assertEquals( connection.getEnabled(), true );
            assertEquals( connection.getState(), FEP.EndpointConnection.State.COMMS_UP );
        }

        // mock object that will receive queue and measurement subscription
        MockSubscriptionEventAcceptor<Measurements.Measurement> mock = new MockSubscriptionEventAcceptor<Measurements.Measurement>();


        List<Model.Point> points = ps.getPoints();

        SubscriptionResult<List<Measurements.Measurement>, Measurements.Measurement> result = ms.subscribeToMeasurementsByPoints( points );

        List<Measurements.Measurement> response = result.getResult();
        Subscription<Measurements.Measurement> sub = result.getSubscription();

        assertEquals( response.size(), points.size() );

        sub.start( mock );

        // check that at least one measurement has been updated in the queue
        SubscriptionEvent<Measurements.Measurement> m = mock.pop( 10000 );
        assertEquals( m.getEventType(), Envelope.SubscriptionEventType.MODIFIED );

        // now cancel the subscription
        sub.cancel();
        Thread.sleep( 1000 );
        mock.clear();

        try
        {
            mock.pop( 1000 );
            fail( "pop() should raise an Exception" );
        }
        catch ( Exception e )
        {
        }

    }

}
