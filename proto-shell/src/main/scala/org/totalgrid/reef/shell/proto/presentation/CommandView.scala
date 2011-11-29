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
package org.totalgrid.reef.shell.proto.presentation

import org.totalgrid.reef.proto.Model.Command
import scala.collection.JavaConversions._
import org.totalgrid.reef.proto.Commands.{ CommandStatus, CommandAccess, UserCommandRequest }
import org.totalgrid.reef.proto.OptionalProtos._
import org.totalgrid.reef.util.Table

object CommandView {

  def commandList(cmds: List[Command]) = {
    Table.printTable(commandHeader, cmds.map(commandRow(_)))
  }

  def commandHeader = {
    "Name" :: "DisplayName" :: "Type" :: "Endpoint" :: Nil
  }

  def commandRow(a: Command) = {
    a.getName ::
      a.getDisplayName ::
      a.getType.toString ::
      a.endpoint.name.getOrElse("") ::
      Nil
  }

  //def selectResponse(resp: CommandAccess)
  def commandResponse(resp: UserCommandRequest) = {
    val rows = ("ID: " :: "[" + resp.getId + "]" :: Nil) ::
      ("Command:" :: resp.commandRequest.command.name.getOrElse("unknown") :: Nil) ::
      ("User:" :: resp.getUser :: Nil) ::
      ("Status:" :: resp.getStatus.toString :: Nil) :: Nil

    Table.justifyColumns(rows).foreach(line => println(line.mkString(" ")))
  }
  def commandResponse(resp: CommandStatus) = {
    val rows = ("Status:" :: resp.toString :: Nil) :: Nil

    Table.justifyColumns(rows).foreach(line => println(line.mkString(" ")))
  }

  def removeBlockResponse(removed: List[CommandAccess]) = {
    val rows = removed.map(acc => "Removed:" :: "[" + acc.getId + "]" :: Nil)
    Table.renderRows(rows, " ")
  }

  def blockResponse(acc: CommandAccess) = {
    println("Block successful.")
    accessInspect(acc)
  }

  def accessInspect(acc: CommandAccess) = {
    val commands = acc.getCommandsList.toList
    val first = commands.headOption.map { _.name }.flatten.toString
    val tail = commands.tail

    val rows: List[List[String]] = ("ID:" :: "[" + acc.getId.getValue + "]" :: Nil) ::
      ("Mode:" :: acc.getAccess.toString :: Nil) ::
      ("User:" :: acc.getUser :: Nil) ::
      ("Expires:" :: timeString(acc) :: Nil) ::
      ("Commands:" :: first :: Nil) :: Nil

    val cmdRows = tail.map(cmd => ("" :: cmd.name.toString :: Nil))

    Table.renderRows(rows ::: cmdRows, " ")
  }

  def timeString(acc: CommandAccess) = new java.util.Date(acc.getExpireTime).toString

  def accessHeader = {
    "Id" :: "Mode" :: "User" :: "Commands" :: "Expire Time" :: Nil
  }

  def accessRow(acc: CommandAccess): List[String] = {
    val commands = commandsEllipsis(acc.getCommandsList.toList)
    val time = new java.util.Date(acc.getExpireTime).toString
    "[" + acc.getId.getValue + "]" :: acc.getAccess.toString :: acc.getUser :: commands :: time :: Nil
  }

  def commandsEllipsis(names: List[Command]) = {
    names.length match {
      case 0 => ""
      case 1 => names.head.name.getOrElse("unknown")
      case _ => "(" + names.head.name.getOrElse("unknown") + ", ...)"
    }
  }

  def printAccessTable(list: List[CommandAccess]) = {
    Table.printTable(accessHeader, list.map(accessRow(_)))
  }

  def printHistoryTable(history: List[UserCommandRequest]) = {
    Table.printTable(historyHeader, history.map(historyRow(_)))
  }

  def historyHeader = {
    "Id" :: "Command" :: "Status" :: "User" :: "Type" :: Nil
  }

  def historyRow(a: UserCommandRequest) = {
    a.id.value.getOrElse("unknown") ::
      a.commandRequest.command.name.getOrElse("unknown") ::
      a.status.map { _.toString }.getOrElse("unknown") ::
      a.user.getOrElse("unknown") ::
      a.commandRequest._type.toString ::
      Nil
  }
}