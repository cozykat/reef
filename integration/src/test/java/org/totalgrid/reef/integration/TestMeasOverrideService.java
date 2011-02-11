/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Green Energy Corp licenses this file
 * to you under the GNU Affero General Public License Version 3.0
 * (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/agpl.html
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.totalgrid.reef.integration;

import org.junit.*;

import static org.junit.Assert.*;

import org.totalgrid.reef.proto.Measurements.*;
import org.totalgrid.reef.proto.Model.*;
import org.totalgrid.reef.proto.Processing.MeasOverride;
import java.util.List;

import org.totalgrid.reef.protoapi.ISubscription;

import org.totalgrid.reef.messaging.javabridge.*;
import org.totalgrid.reef.messaging.Descriptors;
import org.totalgrid.reef.integration.helpers.*;

@SuppressWarnings("unchecked")
public class TestMeasOverrideService extends JavaBridgeTestBase {

	/** Test that the measurement overrides work correctly */
	@Test
	public void deleteAndPutOverrides() throws InterruptedException {

        // use a point we know will be static
        String pointName = "StaticSubstation.Line02.Current";
        Point p = Point.newBuilder().setName(pointName).build();
		MockEventAcceptor<Measurement> mock = new MockEventAcceptor<Measurement>(true);
		ISubscription sub = client.addSubscription(Descriptors.measurementSnapshot(), mock);

		{
			MeasOverride ovr = SampleProtos.makeMeasOverride(p);
			client.delete(ovr);
		}

		{
            // subscribe to updates for this point
			MeasurementSnapshot ms = SampleProtos.makeMeasSnapshot(pointName);
			MeasurementSnapshot rsp = client.getOne(ms, sub);
			assertEquals(rsp.getMeasurementsCount(), 1);
		}

        // create an override
		Measurement m = SampleProtos.makeIntMeas(pointName, 12345, 11111);
		MeasOverride ovrRequest = SampleProtos.makeMeasOverride(p, m);
		MeasOverride override = client.putOne(ovrRequest);

        // make sure we see it in the event stream
        assertTrue(mock.waitFor(SampleProtos.makeSubstituted(m), 5000));

        // if we now try to put a measurement it should be suppressed (b/c we have overridden it)
        Measurement suppressedValue = SampleProtos.makeIntMeas(pointName, 22222, 22222);
        MeasurementBatch mb = SampleProtos.makeMeasBatch(suppressedValue);
        client.putOne(mb);

        // we store the most recently reported value in a cache so if we publish a value
        // while it is overridden and then take off the override we should see the cached value published
        Measurement cachedValue = SampleProtos.makeIntMeas(pointName, 33333, 33333);
        MeasurementBatch mb2 = SampleProtos.makeMeasBatch(cachedValue);
        client.putOne(mb2);

        // now we remove the override, we should get the second measurement we attempted to publish
		client.deleteOne(override);

        // publish a final measurement we expect to see on the subscription channel
        Measurement finalValue = SampleProtos.makeIntMeas(pointName, 44444, 44444);
        MeasurementBatch mb3 = SampleProtos.makeMeasBatch(finalValue);
        client.putOne(mb3);

        // verify that we get that final value
        assertTrue(mock.waitFor(finalValue, 5000));

        // then verify that we never got the suppressed value
        List<Measurement> measurements = mock.getPayloads();
        assertTrue(measurements.contains(cachedValue));
        assertFalse(measurements.contains(suppressedValue));
	}
}
