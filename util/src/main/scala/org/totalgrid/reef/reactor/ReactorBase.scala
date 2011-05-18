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
package org.totalgrid.reef.reactor

import scala.actors.{ Actor, AbstractActor, Exit }

import org.totalgrid.reef.util.Logging

import Reactor._

trait ReactorBase extends Actor with Logging {

  val reactable: Reactable
  def beforeExit()

  private var running = false

  trapExit = true // sends us an Exit message rather than just calling exit directly

  override def exceptionHandler: PartialFunction[Exception, Unit] = {
    {
      case e: Exception =>
        reefLogger.error("exception encountered in actor: " + e.getMessage, e)
    }
  }

  /// Sending this object causes the actor to shutdown
  private case object Stop

  /// main partial that implements the behaviors using the accompanying
  /// case class messages
  protected val mainPartial: PartialFunction[Any, Unit] = {
    case Execute(fun) =>
      fun()
    case Request(fun) =>
      reply(fun())
    case Link(a) =>
      link(a)
    case UnLink(a) =>
      unlink(a)
    case Exit(parent, reason) => // linked actor initiated shutdown
      handleStopping
      exit(reason)
    case Stop => // application initiated shutdown
      handleStopping
      reply(Stop)
      exit
  }

  private def handleStopping = {
    try {
      // run onStop callbacks
      running = false
      beforeExit()
    } catch {
      case t: Throwable => reefLogger.error("exception encountered in handleStopping(): " + t.getMessage, t)
    }
  }

  def stop() = {
    if (running) {
      val ret = this.!?(10000, Stop)
      if (ret != Some(Stop)) try {
        throw new Exception("Actor deadlock detected on stop")
      } catch {
        case e: Exception =>
          reefLogger.error("exception encountered during stop: " + e.getMessage, e)
      }
    } else throw new IllegalStateException("Reactable not running")
  }

  override def start() = {
    running = true
    super.start
  }

}
