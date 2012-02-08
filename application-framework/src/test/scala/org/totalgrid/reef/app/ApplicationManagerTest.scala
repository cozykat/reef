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
package org.totalgrid.reef.app

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import net.agileautomata.executor4s._
import net.agileautomata.executor4s.testing._
import net.agileautomata.commons.testing._
import org.mockito.Mockito
import org.totalgrid.reef.test.MockitoStubbedOnly
import org.totalgrid.reef.client.settings._
import org.totalgrid.reef.client.sapi.client.rest.{ Client, Connection }
import org.totalgrid.reef.client.sapi.rpc.AllScadaService
import org.totalgrid.reef.client.sapi.client.impl.FixedPromise
import org.totalgrid.reef.client.exception.{ ReefServiceException, ServiceIOException }
import org.totalgrid.reef.client.service.proto.Application.{ HeartbeatConfig, ApplicationConfig }
import org.totalgrid.reef.client.service.proto.ProcessStatus.StatusSnapshot
import net.agileautomata.executor4s.Failure._
import org.totalgrid.reef.client.sapi.client.Promise

@RunWith(classOf[JUnitRunner])
class ApplicationManagerTest extends FunSuite with ShouldMatchers {

  val userSettings = new UserSettings("user", "password")
  val nodeSettings = new NodeSettings("nodeName", "location", "network")

  val capabilites = List("Cap1", "Cap2")
  val instanceName = "instance"

  val settings = ApplicationSettings(userSettings, nodeSettings, instanceName, capabilites, None)

  test("Check status functions") {
    val executor = new MockExecutor()

    val connectionProvider = Mockito.mock(classOf[ConnectionProvider])
    val manager = new SimpleApplicationConnectionManager(executor, connectionProvider)

    manager.isConnected should equal(false)
    manager.isShutdown should equal(true)
    intercept[ServiceIOException] { manager.getConnection }

    manager.start(settings)

    Mockito.verify(connectionProvider).addConsumer(manager)

    manager.isConnected should equal(false)
    manager.isShutdown should equal(false)
    intercept[ServiceIOException] { manager.getConnection }

    manager.stop()

    Mockito.verify(connectionProvider).removeConsumer(manager)

    manager.isConnected should equal(false)
    manager.isShutdown should equal(true)
    intercept[ServiceIOException] { manager.getConnection }
  }

  class MockAppListener extends ApplicationConnectionListener {
    val connected = new SynchronizedVariable(false)
    var errors = new SynchronizedList[String]()

    def clearErrors() = errors = new SynchronizedList[String]()
    def onConnectionStatusChanged(isConnected: Boolean) = connected.set(isConnected)

    def onConnectionError(msg: String, exception: Option[Exception]) = errors.append(msg)

    def errorsShouldInclude(msg: String) = {
      def evaluate(success: Boolean, last: List[String], timeout: Long) =
        if (!success) throw new Exception("Expected strings to include " + msg + " within " + timeout + " ms but final value was " + last)
      val (result, success) = errors.value.awaitUntil(500)(list => list.find(_.indexOf(msg) != -1).isDefined)
      evaluate(success, result, 500)
    }

    def connectedShouldBecome(value: Boolean) = {
      connected.shouldBecome(value)
    }
  }

  def makeServices(): (Connection, AllScadaService) = {
    val client = Mockito.mock(classOf[Client], new MockitoStubbedOnly)
    val services = Mockito.mock(classOf[AllScadaService], new MockitoStubbedOnly)
    val connection = Mockito.mock(classOf[Connection], new MockitoStubbedOnly)

    Mockito.doReturn(new FixedPromise(Success(true))).when(client).logout()

    Mockito.doReturn(services).when(client).getRpcInterface(classOf[AllScadaService])
    Mockito.doReturn(new FixedPromise(Success(client))).when(connection).login(userSettings)
    (connection, services)
  }

  test("Login failure retries") {
    val executor = new MockExecutor()

    val listener = new MockAppListener

    val connectionProvider = Mockito.mock(classOf[ConnectionProvider])
    val manager = new SimpleApplicationConnectionManager(executor, connectionProvider)
    manager.addConnectionListener(listener)

    val (connection, services) = makeServices()
    Mockito.doReturn(new FixedPromise(Failure("Unknown user"))).when(connection).login(userSettings)

    manager.start(settings)

    manager.handleConnection(connection)

    executor.runUntilIdle()

    listener errorsShouldInclude ("Unknown user")
    listener.clearErrors()

    executor.tick(10000.milliseconds)
    executor.runUntilIdle()

    listener errorsShouldInclude ("Unknown user")
  }

  test("Application registration failure causes relogin attempts") {
    val executor = new MockExecutor()

    val listener = new MockAppListener

    val connectionProvider = Mockito.mock(classOf[ConnectionProvider])
    val manager = new SimpleApplicationConnectionManager(executor, connectionProvider)
    manager.addConnectionListener(listener)
    manager.start(settings)

    val (connection, services) = makeServices()

    Mockito.doReturn(new FixedPromise(Failure("Can't register app"))).when(services).registerApplication(nodeSettings, instanceName, capabilites)

    manager.handleConnection(connection)

    executor.runUntilIdle()

    listener errorsShouldInclude ("Can't register app")
    listener.clearErrors()

    Mockito.doReturn(new FixedPromise(Failure("Unknown user"))).when(connection).login(userSettings)

    executor.tick(10000.milliseconds)
    executor.runUntilIdle()

    listener errorsShouldInclude ("Unknown user")
  }

  test("Sucessful Connection") {
    val executor = new MockExecutor()

    val listener = new MockAppListener

    val connectionProvider = Mockito.mock(classOf[ConnectionProvider])
    val manager = new SimpleApplicationConnectionManager(executor, connectionProvider)
    manager.addConnectionListener(listener)

    val (connection, services) = makeServices()

    Mockito.doReturn(new FixedPromise(Failure("Can't register app"))).when(services).registerApplication(nodeSettings, instanceName, capabilites)

    val appConfig = ApplicationConfig.newBuilder.setInstanceName("name").setHeartbeatCfg(HeartbeatConfig.newBuilder).build
    Mockito.doReturn(new FixedPromise(Success(appConfig))).when(services).registerApplication(nodeSettings, instanceName, capabilites)
    val status = StatusSnapshot.newBuilder.build
    Mockito.doReturn(new FixedPromise(Success(status))).when(services).sendHeartbeat(appConfig)

    Mockito.doReturn(new FixedPromise(Success(status))).when(services).sendApplicationOffline(appConfig)

    manager.start(settings)

    manager.handleConnection(connection)

    executor.runUntilIdle()

    listener connectedShouldBecome (true) within 500

    manager.stop()

    listener connectedShouldBecome (false) within 500

    Mockito.verify(services).sendApplicationOffline(appConfig)
  }

  test("Heartbeat failure causes disconnect") {
    val executor = new MockExecutor()

    val listener = new MockAppListener

    val connectionProvider = Mockito.mock(classOf[ConnectionProvider])
    val manager = new SimpleApplicationConnectionManager(executor, connectionProvider)
    manager.addConnectionListener(listener)

    val (connection, services) = makeServices()

    Mockito.doReturn(new FixedPromise(Failure("Can't register app"))).when(services).registerApplication(nodeSettings, instanceName, capabilites)

    val appConfig = ApplicationConfig.newBuilder.setInstanceName("name").setHeartbeatCfg(HeartbeatConfig.newBuilder.setPeriodMs(100)).build
    Mockito.doReturn(new FixedPromise(Success(appConfig))).when(services).registerApplication(nodeSettings, instanceName, capabilites)
    val status = StatusSnapshot.newBuilder.build
    Mockito.doReturn(new FixedPromise(Success(status))).when(services).sendHeartbeat(appConfig)

    Mockito.doReturn(new FixedPromise(Success(status))).when(services).sendApplicationOffline(appConfig)

    manager.start(settings)

    manager.handleConnection(connection)

    executor.runUntilIdle()

    listener connectedShouldBecome (true) within 500

    executor.tick(10000.milliseconds)

    Mockito.doReturn(new FixedPromise(Failure("Unexpected heartbeat failure"))).when(services).sendHeartbeat(appConfig)

    executor.tick(10000.milliseconds)

    listener errorsShouldInclude ("Unexpected heartbeat failure")
    listener connectedShouldBecome (false) within 500
    intercept[ServiceIOException] { manager.getConnection }

    Mockito.doReturn(new FixedPromise(Success(status))).when(services).sendHeartbeat(appConfig)

    executor.tick(10000.milliseconds)
    listener connectedShouldBecome (true) within 500

    manager.getConnection
  }

}