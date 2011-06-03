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
package org.totalgrid.reef.protocol.dnp3

import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

@RunWith(classOf[JUnitRunner])
class DNP3BindingTest extends FunSuite with ShouldMatchers {

  val startPort = 32323

  class MockStateObserver {
    import scala.collection.mutable.{ Queue, Map }
    val states = Map.empty[String, Queue[MasterStates]]

    var observers = List.empty[IMasterObserver]
    def getObserver(name: String): IMasterObserver = {
      val obs = new IMasterObserver() {
        override def OnStateChange(state: MasterStates) {
          states.get(name) match {
            case Some(l: Queue[MasterStates]) => l.enqueue(state)
            case None => states += name -> Queue(state)
          }
        }
      }
      // need to keep a reference to the observer so it doesn't get GCed
      observers ::= obs
      obs
    }

    def checkStates(names: List[String], expected: List[MasterStates]) {
      names.foreach { name =>
        states.get(name).map { _.toList }.getOrElse(List.empty[MasterStates]) should equal(expected)
      }
    }
  }

  /// This test shows that the startup/teardown behavior is working without crashing
  test("StartupTeardownOnJVM") {
    val num_port = 100
    val num_stack = 10
    val sm = new StackManager(true) // the stack will start running as soon as a master is added		

    val stateObserver = new MockStateObserver

    var names = List.empty[String]

    // startup <num_stack> masters on <num_port> ports
    (1 to num_port).foreach { port =>

      val s = new PhysLayerSettings(FilterLevel.LEV_WARNING, 1000)
      sm.AddTCPClient(port.toString, s, "127.0.0.1", startPort)

      (1 to num_stack).foreach { stack =>
        val name = "port-" + port + "-stack" + stack

        names ::= name

        val cfg = new MasterStackConfig

        cfg.getLink.setLocalAddr(stack)

        cfg.getMaster.setMpObserver(stateObserver.getObserver(name))

        // the masters won't get any data, so setting the IPublisher to null is OK
        sm.AddMaster(port.toString, name, FilterLevel.LEV_WARNING, null, cfg)
      }
    }

    stateObserver.checkStates(names, List(MasterStates.MS_COMMS_DOWN))

    // the manager is already running at this point, so let's stop it explicitly
    sm.Stop

    // make sure we didnt get a second update
    stateObserver.checkStates(names, List(MasterStates.MS_COMMS_DOWN))
  }

  test("MasterToSlaveOnJVM") {
    val num_pairs = 100
    val port_start = startPort
    val port_end = port_start + num_pairs - 1
    val lev = FilterLevel.LEV_WARNING
    val sm = new StackManager(false)
    val a = new CountingPublisherActor

    val stateObserver = new MockStateObserver
    var names = List.empty[String]

    val master = new MasterStackConfig
    master.getMaster.setIntegrityRate(60000)
    val slave = new SlaveStackConfig

    slave.setDevice(new DeviceTemplate(100, 100, 100))

    val s = new PhysLayerSettings(FilterLevel.LEV_WARNING, 1000)
    (port_start to port_end).foreach { port =>
      val client = "client-" + port
      val server = "server-" + port
      sm.AddTCPClient(client, s, "127.0.0.1", port)
      sm.AddTCPServer(server, s, "0.0.0.0", port)

      master.getMaster.setMpObserver(stateObserver.getObserver(server))
      names ::= server

      sm.AddMaster(client, client, lev, a.addPub, master)
      sm.AddSlave(server, server, lev, null, slave)
    }

    sm.Start
    assert(a.waitForMinMessages(300, 10000))
    sm.Stop

    // make sure we got the down-up-down callbacks we expected
    stateObserver.checkStates(names, List(MasterStates.MS_COMMS_DOWN, MasterStates.MS_COMMS_UP, MasterStates.MS_COMMS_DOWN))
  }

}
