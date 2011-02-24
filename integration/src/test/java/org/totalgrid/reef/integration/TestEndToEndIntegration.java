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

import org.junit.Test;
import org.totalgrid.reef.api.ISubscription;
import org.totalgrid.reef.api.ServiceTypes;
import org.totalgrid.reef.api.ReefServiceException;
import org.totalgrid.reef.integration.helpers.JavaBridgeTestBase;
import org.totalgrid.reef.integration.helpers.MockEventAcceptor;
import org.totalgrid.reef.proto.Descriptors;
import org.totalgrid.reef.proto.Commands;
import org.totalgrid.reef.api.Envelope;
import org.totalgrid.reef.proto.Measurements;
import org.totalgrid.reef.proto.Model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * tests to prove that the simulator is up and measurements are being processed correctly.
 */
@SuppressWarnings("unchecked")
public class TestEndToEndIntegration extends JavaBridgeTestBase {

    /**
	 * The "EXECUTING" status just tells us that the command was received and allowed The response
	 * from the field device / endpoint comes back via an asynchronous subscription
	 */
	@Test
	public void testSimulatorHandlingCommands() throws InterruptedException, ReefServiceException {
		Model.Command cmd = SampleRequests.getAllCommands(client).get(0);
		SampleRequests.clearCommandAccess(client, cmd.getName());
		Commands.CommandAccess accessResponse = SampleRequests.putCommandAccess(client, "user", cmd, 5000, true);
		assertTrue(accessResponse.getExpireTime() > 0);

		// create infrastructure to execute a control with subscription to
		// result

		MockEventAcceptor<Commands.UserCommandRequest> mock = new MockEventAcceptor<Commands.UserCommandRequest>();
		Commands.UserCommandRequest request = SampleProtos.makeControlRequest(cmd, "user");
		ISubscription sub = client.addSubscription(Descriptors.userCommandRequest(), mock);
		client.putOne(request, sub);

		// We get 2 events here. Since the subscription is bound before the request is made,
		// we see the ADDED/EXECUTING and then the MODIFIED/SUCCESS
		{
			ServiceTypes.Event<Commands.UserCommandRequest> rsp = mock.pop(5000);
			assertEquals(Envelope.Event.ADDED, rsp.getEvent());
			assertEquals(Commands.CommandStatus.EXECUTING, rsp.getResult().getStatus());
		}
		{
			ServiceTypes.Event<Commands.UserCommandRequest> rsp = mock.pop(5000);
			assertEquals(Envelope.Event.MODIFIED, rsp.getEvent());
			assertEquals(Commands.CommandStatus.SUCCESS, rsp.getResult().getStatus());
		}

		// cancel the subscription
		sub.cancel();
	}

    /**
	 * Tests subscribing to the measurement snapshot service via a get operation
	 */
	@Test
	public void testSimulatorProducingMeasurements() throws java.lang.InterruptedException, ReefServiceException {

		// mock object that will receive queue and measurement subscription
		MockEventAcceptor<Measurements.Measurement> mock = new MockEventAcceptor<Measurements.Measurement>();

		ISubscription sub = client.addSubscription(Descriptors.measurementSnapshot(), mock);

		// make the all points request, w/ subscribe queue set
		Measurements.MeasurementSnapshot request = SampleProtos.makeMeasSnapshot(SampleRequests.getAllPoints(client));
		Measurements.MeasurementSnapshot response = client.getOne(request, sub);

		assertEquals(request.getPointNamesCount(), response.getMeasurementsCount());

		// check that at least one measurement has been updated in the queue
		ServiceTypes.Event<Measurements.Measurement> m = mock.pop(10000);
		assertEquals(m.getEvent(), Envelope.Event.MODIFIED);

		// now cancel the subscription
		sub.cancel();
		Thread.sleep(1000);
		mock.clear();

		try {
			mock.pop(1000);
			fail("pop() should raise an Exception");
		} catch (Exception e) {
		}

	}

}
