/**
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Green Energy Corp licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.totalgrid.reef.protocol.dnp3

import org.totalgrid.reef.proto.{ Mapping, Measurements, Commands }
import scala.collection.mutable

class MockCommandAcceptor extends ICommandAcceptor {
  val commands = new mutable.Queue[Tuple3[BinaryOutput, Long, Int]]
  val setpoints = new mutable.Queue[Tuple3[Setpoint, Long, Int]]

  override def AcceptCommand(obj: BinaryOutput, index: Long, seq: Int, accept: IResponseAcceptor) {
    commands += Tuple3(obj, index, seq)
  }
  override def AcceptCommand(obj: Setpoint, index: Long, seq: Int, accept: IResponseAcceptor) {
    setpoints += Tuple3(obj, index, seq)
  }
}

import org.scalatest.Suite
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

@RunWith(classOf[JUnitRunner])
class CommandAdapterTests extends Suite with ShouldMatchers {

  def pop[T](responses: mutable.Queue[T])(f: T => Unit) = f(responses.dequeue)

  def testCommandAndResponse {
    val assoc = Mapping.CommandMap.newBuilder
    assoc.setIndex(5)
    assoc.setCommandName("testCommand")
    assoc.setType(Mapping.CommandType.PULSE)

    runATest(List(assoc)) { (adapt, acceptor, responses) =>
      val request = Commands.CommandRequest.newBuilder
      request.setType(Commands.CommandRequest.ValType.NONE)
      request.setName("testCommand")
      request.setCorrelationId("testCommandID")

      adapt.send(request.build)

      acceptor.setpoints.size should equal(0)
      acceptor.commands.size should equal(1)
      pop(acceptor.commands) { cmd =>
        cmd._2 should equal(5)
        cmd._3 should equal(1)
      }

      val resp = new CommandResponse(CommandStatus.CS_SUCCESS)
      adapt.AcceptResponse(resp, 1)
      adapt.AcceptResponse(resp, 1) // Duplicate should be ignored

      responses.size should equal(1)
      pop(responses) { rsp =>
        rsp.getCorrelationId() should equal("testCommandID")
        rsp.getStatus() should equal(Commands.CommandStatus.SUCCESS)
      }

    }
  }

  def testSetpointAndResponse {
    val assoc = Mapping.CommandMap.newBuilder
    assoc.setIndex(7)
    assoc.setCommandName("testSetpoint")
    assoc.setType(Mapping.CommandType.SETPOINT)

    runATest(List(assoc)) { (adapt, acceptor, responses) =>
      val request = Commands.CommandRequest.newBuilder
      request.setType(Commands.CommandRequest.ValType.INT)
      request.setName("testSetpoint")
      request.setCorrelationId("testSetpointID")

      adapt.send(request.build)

      acceptor.commands.size should equal(0)
      acceptor.setpoints.size should equal(1)

      pop(acceptor.setpoints) { cmd =>
        cmd._2 should equal(7)
        cmd._3 should equal(1)
      }

      val resp = new CommandResponse(CommandStatus.CS_SUCCESS)
      adapt.AcceptResponse(resp, 1)

      responses.size should equal(1)
      pop(responses) { rsp =>
        rsp.getCorrelationId() should equal("testSetpointID")
        rsp.getStatus() should equal(Commands.CommandStatus.SUCCESS)
      }
    }
  }
  def testSequencing {
    val a = new mutable.ArrayBuffer[Mapping.CommandMap.Builder]
    a += Mapping.CommandMap.newBuilder.setIndex(2).setCommandName("testCommand1").setType(Mapping.CommandType.PULSE)
    a += Mapping.CommandMap.newBuilder.setIndex(3).setCommandName("testCommand2").setType(Mapping.CommandType.PULSE)

    runATest(a.toList) { (adapt, acceptor, responses) =>
      var request = Commands.CommandRequest.newBuilder
      request.setType(Commands.CommandRequest.ValType.NONE)
      request.setName("testCommand1")
      request.setCorrelationId("testCommandID1")
      adapt.send(request.build)
      request = Commands.CommandRequest.newBuilder
      request.setType(Commands.CommandRequest.ValType.NONE)
      request.setName("testCommand2")
      request.setCorrelationId("testCommandID2")
      adapt.send(request.build)

      acceptor.setpoints.size should equal(0)
      acceptor.commands.size should equal(2)

      pop(acceptor.commands) { cmd =>
        cmd._2 should equal(2)
        cmd._3 should equal(1)
      }

      pop(acceptor.commands) { cmd =>
        cmd._2 should equal(3)
        cmd._3 should equal(2)
      }

      val resp = new CommandResponse(CommandStatus.CS_SUCCESS)
      adapt.AcceptResponse(resp, 2)
      adapt.AcceptResponse(resp, 1)

      responses.size should equal(2)
      pop(responses) { rsp =>
        rsp.getCorrelationId() should equal("testCommandID2")
        rsp.getStatus() should equal(Commands.CommandStatus.SUCCESS)
      }
      pop(responses) { rsp =>
        rsp.getCorrelationId() should equal("testCommandID1")
        rsp.getStatus() should equal(Commands.CommandStatus.SUCCESS)
      }
    }
  }
  def testSequencingSameCommand {
    val a = new mutable.ArrayBuffer[Mapping.CommandMap.Builder]
    a += Mapping.CommandMap.newBuilder.setIndex(2).setCommandName("testCommand").setType(Mapping.CommandType.PULSE)

    runATest(a.toList) { (adapt, acceptor, responses) =>
      var request = Commands.CommandRequest.newBuilder
      request.setType(Commands.CommandRequest.ValType.NONE)
      request.setName("testCommand")
      request.setCorrelationId("testCommandID1")
      adapt.send(request.build)
      request = Commands.CommandRequest.newBuilder
      request.setType(Commands.CommandRequest.ValType.NONE)
      request.setName("testCommand")
      request.setCorrelationId("testCommandID2")
      adapt.send(request.build)

      acceptor.setpoints.size should equal(0)
      acceptor.commands.size should equal(2)

      pop(acceptor.commands) { cmd =>
        cmd._2 should equal(2)
        cmd._3 should equal(1)
      }
      pop(acceptor.commands) { cmd =>
        cmd._2 should equal(2)
        cmd._3 should equal(2)
      }

      val resp = new CommandResponse(CommandStatus.CS_SUCCESS)
      adapt.AcceptResponse(resp, 2)
      adapt.AcceptResponse(resp, 1)

      responses.size should equal(2)
      pop(responses) { rsp =>
        rsp.getCorrelationId() should equal("testCommandID2")
        rsp.getStatus() should equal(Commands.CommandStatus.SUCCESS)
      }
      pop(responses) { rsp =>
        rsp.getCorrelationId() should equal("testCommandID1")
        rsp.getStatus() should equal(Commands.CommandStatus.SUCCESS)
      }

    }
  }

  def runATest(mappings: List[Mapping.CommandMap.Builder])(testfun: (CommandAdapter, MockCommandAcceptor, mutable.Queue[Commands.CommandResponse]) => Unit) = {

    val map = Mapping.IndexMapping.newBuilder
    map.setDeviceUid("test")
    for (mapping <- mappings) map.addCommandmap(mapping)
    val responses = new mutable.Queue[Commands.CommandResponse]
    val acceptor = new MockCommandAcceptor
    val adapt = new CommandAdapter(map.build, acceptor, responses += _)
    testfun(adapt, acceptor, responses)
  }
}
