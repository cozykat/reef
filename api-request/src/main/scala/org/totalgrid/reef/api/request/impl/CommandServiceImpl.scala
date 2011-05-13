/**
 * Copyright 2011 Green Energy Corp.
 *
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
package org.totalgrid.reef.api.request.impl

import org.totalgrid.reef.proto.Commands.{ UserCommandRequest, CommandStatus, CommandAccess }
import org.totalgrid.reef.proto.Model.Command
import scala.collection.JavaConversions._
import org.totalgrid.reef.api.request.builders.{ CommandRequestBuilders, UserCommandRequestBuilders, CommandAccessRequestBuilders }
import org.totalgrid.reef.api.scalaclient.{ SingleSuccess, Failure }
import org.totalgrid.reef.api.request.{ CommandService }

trait CommandServiceImpl extends ReefServiceBaseClass with CommandService {

  def createCommandExecutionLock(id: Command): CommandAccess = createCommandExecutionLock(id :: Nil)
  def createCommandExecutionLock(ids: java.util.List[Command]): CommandAccess = {
    ops { _.putOneOrThrow(CommandAccessRequestBuilders.allowAccessForCommands(ids)) }
  }

  def deleteCommandLock(uid: String): CommandAccess = {
    ops { _.deleteOneOrThrow(CommandAccessRequestBuilders.getForUid(uid)) }
  }
  def deleteCommandLock(ca: CommandAccess): CommandAccess = {
    ops { _.deleteOneOrThrow(CommandAccessRequestBuilders.getForUid(ca.getUid)) }
  }

  def clearCommandLocks(): java.util.List[CommandAccess] = {
    ops { _.deleteOrThrow(CommandAccessRequestBuilders.getAll) }
  }

  def executeCommandAsControl(id: Command): CommandStatus = {
    val result = ops { _.putOneOrThrow(UserCommandRequestBuilders.executeControl(id)) }
    result.getStatus
  }

  def executeCommandAsSetpoint(id: Command, value: Double): CommandStatus = {
    val result = ops { _.putOneOrThrow(UserCommandRequestBuilders.executeSetpoint(id, value)) }
    result.getStatus
  }

  def executeCommandAsSetpoint(id: Command, value: Int): CommandStatus = {
    val result = ops { _.putOneOrThrow(UserCommandRequestBuilders.executeSetpoint(id, value)) }
    result.getStatus
  }

  def createCommandDenialLock(ids: java.util.List[Command]): CommandAccess = {
    ops { _.putOneOrThrow(CommandAccessRequestBuilders.blockAccessForCommands(ids)) }
  }

  def getCommandLocks(): java.util.List[CommandAccess] = {
    ops { _.getOrThrow(CommandAccessRequestBuilders.getAll) }
  }

  def getCommandLock(uid: String) = {
    ops { _.getOneOrThrow(CommandAccessRequestBuilders.getForUid(uid)) }
  }

  def getCommandLockOnCommand(id: Command): CommandAccess = {
    ops {
      _.getOne(CommandAccessRequestBuilders.getByCommand(id)) match {
        case SingleSuccess(status, lock) => lock
        case Failure(status, str) => null
      }
    }
  }

  def getCommandLocksOnCommands(ids: java.util.List[Command]): java.util.List[CommandAccess] = {
    ops { _.getOrThrow(CommandAccessRequestBuilders.getByCommands(ids)) }
  }

  def getCommandHistory(): java.util.List[UserCommandRequest] = {
    ops { _.getOrThrow(UserCommandRequestBuilders.getForUid("*")) }
  }

  def getCommands(): java.util.List[Command] = {
    ops { _.getOrThrow(CommandRequestBuilders.getAll) }
  }

  def getCommandByName(name: String) = {
    ops { _.getOneOrThrow(CommandRequestBuilders.getByEntityName(name)) }
  }
}