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
package org.totalgrid.reef.services.core

import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.squeryl.PrimitiveTypeMode._

import org.totalgrid.reef.proto.Commands.{ CommandAccess => AccessProto }
import org.totalgrid.reef.models._
import org.totalgrid.reef.japi.{ BadRequestException, UnauthorizedException }
import org.totalgrid.reef.sapi.RequestEnv
import org.totalgrid.reef.services.{ HeadersRequestContext, ServiceDependencies }

class CommandTestRig {
  val modelFactories = new ModelFactories(new ServiceDependencies)

  val commands = modelFactories.cmds
  val accesses = modelFactories.accesses
  val userRequests = modelFactories.userRequests

  def seed(sql: Command): Command = {
    ApplicationSchema.commands.insert(sql)
  }

  def seed(sql: CommandAccessModel): CommandAccessModel = {
    ApplicationSchema.commandAccess.insert(sql)
  }
  def seed(name: String): Command = {
    seed(Command.newInstance(name, name, 1, None))
  }
}

@RunWith(classOf[JUnitRunner])
class CommandAccessServiceModelTest extends DatabaseUsingTestBase with RunTestsInsideTransaction {

  import AccessProto._

  val context = new HeadersRequestContext()

  def lastSelectFor(cmd: String) = {
    Command.findByNames(cmd :: Nil).head.lastSelectId
  }

  def checkCmds(cmds: List[Command], size: Int, accessId: Long) = {
    cmds.length should equal(size)
    cmds.map { cmd =>
      cmd.lastSelectId should equal(Some(accessId))
      cmd.id
    }
  }

  def accessCount = ApplicationSchema.commandAccess.where(t => true === true).size

  def checkAccess(accInt: Int, expireTime: Option[Long], user: Option[String]) = {
    val entries = ApplicationSchema.commandAccess.where(t => true === true).toList
    entries.length should equal(1)
    val entry = entries.head
    entry.access should equal(accInt)
    entry.expireTime should equal(expireTime)
    entry.agent should equal(user)
    entry
  }

  def allJoins = ApplicationSchema.commandToBlocks.where(t => true === true).toList
  def joinCount = allJoins.size
  def existsJoinBetween(accessId: Long, cmdId: Long) = {
    ApplicationSchema.commandToBlocks.where(t => t.commandId === cmdId and t.accessId === accessId).size == 1
  }
  def joinsForEntry(accessId: Long) = {
    ApplicationSchema.commandToBlocks.where(t => t.accessId === accessId).toList
  }

  test("Block") {
    val r = new CommandTestRig
    r.seed("cmd01")
    r.seed("cmd02")

    // Do the block
    val inserted = r.accesses.blockCommands(context, "user01", List("cmd01"))

    // Check for block in sql
    val entry = checkAccess(AccessMode.BLOCKED.getNumber, None, Some("user01"))

    // Check for command in sql
    val cmds = Command.findByNames("cmd01" :: Nil).toList
    val cmd = cmds.head
    cmd.lastSelectId should equal(Some(inserted.id))

    // Check for join in sql
    joinCount should equal(1)
    existsJoinBetween(inserted.id, cmd.id) should equal(true)

    // Test areAnyBlocked method
    r.accesses.areAnyBlocked(List("cmd01")) should equal(true)
    r.accesses.areAnyBlocked(List("cmd02")) should equal(false)
    r.accesses.areAnyBlocked(List("cmd01", "cmd02")) should equal(true)

    // Can't select blocked command
    val expireTime = System.currentTimeMillis + 5000
    intercept[UnauthorizedException] {
      r.accesses.selectCommands(context, "user02", Some(expireTime), List("cmd01"))
    }

    r.accesses.removeAccess(context, entry)
    r.accesses.areAnyBlocked(List("cmd01")) should equal(false)
    ApplicationSchema.commandToBlocks.where(t => true === true).size should equal(0)
    ApplicationSchema.commands.where(t => true === true).head.lastSelectId should equal(None)
  }

  test("Multi-Block") {
    val r = new CommandTestRig
    val cmd1 = r.seed("cmd01")
    r.seed("cmd02")

    // Do the block
    val block1 = r.accesses.blockCommands(context, "user01", List("cmd01", "cmd02"))

    // Check for block in sql
    checkAccess(AccessMode.BLOCKED.getNumber, None, Some("user01"))

    // Check for command in sql
    val cmds = ApplicationSchema.commands.where(t => true === true).toList
    val blockedIds = checkCmds(cmds, 2, block1.id)

    // Check for joins in sql
    joinCount should equal(2)
    for (id <- blockedIds)
      existsJoinBetween(block1.id, id) should equal(true)

    r.accesses.areAnyBlocked(List("cmd01")) should equal(true)
    r.accesses.areAnyBlocked(List("cmd02")) should equal(true)

    // Insert second block
    val block2 = r.accesses.blockCommands(context, "user01", List("cmd01"))

    lastSelectFor("cmd01") should equal(Some(block2.id))
    lastSelectFor("cmd02") should equal(Some(block1.id))

    accessCount should equal(2)
    joinCount should equal(3)
    existsJoinBetween(block2.id, cmd1.id) should equal(true)

    r.accesses.areAnyBlocked(List("cmd01")) should equal(true)
    r.accesses.areAnyBlocked(List("cmd02")) should equal(true)

    // Remove first block, show second still applies
    r.accesses.removeAccess(context, block1)
    lastSelectFor("cmd01") should equal(Some(block2.id))
    lastSelectFor("cmd02") should equal(None)

    // Test areAnyBlocked method
    r.accesses.areAnyBlocked(List("cmd01")) should equal(true)
    r.accesses.areAnyBlocked(List("cmd02")) should equal(false)
    r.accesses.areAnyBlocked(List("cmd01", "cmd02")) should equal(true)

    accessCount should equal(1)
    joinCount should equal(1)
    existsJoinBetween(block2.id, cmd1.id) should equal(true)
  }

  test("Block multiple") {
    val r = new CommandTestRig

    r.seed("cmd01")
    r.seed("cmd02")
    r.seed("cmd03")

    val blockedCmds = List("cmd01", "cmd02", "cmd03")

    val inserted = r.accesses.blockCommands(context, "user01", blockedCmds)

    // Check for block in sql
    checkAccess(AccessMode.BLOCKED.getNumber, None, Some("user01"))

    // Check for commands in sql
    val cmds = ApplicationSchema.commands.where(t => true === true).toList
    val blockedIds = checkCmds(cmds, 3, inserted.id)

    // Check for joins in sql
    joinCount should equal(3)
    for (id <- blockedIds)
      existsJoinBetween(inserted.id, id) should equal(true)
  }

  test("Select") {
    val r = new CommandTestRig
    r.seed("cmd01")

    val expireTime = System.currentTimeMillis + 5000
    val inserted = r.accesses.selectCommands(context, "user01", Some(expireTime), List("cmd01"))

    val entry = checkAccess(AccessMode.ALLOWED.getNumber, Some(expireTime), Some("user01"))

    val cmds = ApplicationSchema.commands.where(t => true === true).toList
    cmds.length should equal(1)
    val cmd = cmds.head
    cmd.lastSelectId should equal(Some(inserted.id))

    // Check for join in sql
    joinCount should equal(1)
    existsJoinBetween(entry.id, cmd.id) should equal(true)

    r.accesses.userHasSelect(cmd, "user01", expireTime - 1000) should equal(true)
    r.accesses.userHasSelect(cmd, "user01", expireTime + 1000) should equal(false)

    r.accesses.removeAccess(context, entry)
    r.accesses.userHasSelect(cmd, "user01", expireTime - 1000) should equal(false)
    ApplicationSchema.commands.where(t => true === true).head.lastSelectId should equal(None)
  }

  test("Select twice") {
    val r = new CommandTestRig
    val cmd = r.seed("cmd01")

    val expireTime = System.currentTimeMillis + 5000
    val inserted = r.accesses.selectCommands(context, "user01", Some(expireTime), List("cmd01"))

    val entry = checkAccess(AccessMode.ALLOWED.getNumber, Some(expireTime), Some("user01"))

    r.accesses.userHasSelect(cmd, "user01", expireTime - 1000) should equal(true)
    r.accesses.userHasSelect(cmd, "user01", expireTime + 1000) should equal(false)

    intercept[UnauthorizedException] {
      r.accesses.selectCommands(context, "user02", Some(expireTime), List("cmd01"))
    }

    // Same user
    intercept[UnauthorizedException] {
      r.accesses.selectCommands(context, "user01", Some(expireTime), List("cmd01"))
    }
  }

  test("Select multiple") {
    val r = new CommandTestRig
    r.seed("cmd01")
    r.seed("cmd02")
    r.seed("cmd03")

    val expireTime = System.currentTimeMillis + 5000
    val inserted = r.accesses.selectCommands(context, "user01", Some(expireTime), List("cmd01", "cmd02", "cmd03"))

    checkAccess(AccessMode.ALLOWED.getNumber, Some(expireTime), Some("user01"))

    val cmds = r.commands.table.where(t => true === true).toList
    val blockedIds = checkCmds(cmds, 3, inserted.id)

    // Check for joins in sql
    joinCount should equal(3)
    for (id <- blockedIds)
      existsJoinBetween(inserted.id, id) should equal(true)
  }

  test("Same command name multiple times") {
    val r = new CommandTestRig
    r.seed("cmd01")

    val expireTime = System.currentTimeMillis + 5000
    r.accesses.selectCommands(context, "user01", Some(expireTime), List("cmd01", "cmd01"))

    checkAccess(AccessMode.ALLOWED.getNumber, Some(expireTime), Some("user01"))
  }

  test("Unknown command fails with good error message") {
    val r = new CommandTestRig

    val expireTime = System.currentTimeMillis + 5000
    val exception = intercept[BadRequestException] {
      r.accesses.selectCommands(context, "user01", Some(expireTime), List("cmd01"))
    }
    exception.getMessage should include("cmd01")
  }
}