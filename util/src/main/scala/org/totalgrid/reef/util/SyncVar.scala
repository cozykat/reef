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
package org.totalgrid.reef.util

import scala.annotation.tailrec
import scala.collection.mutable.Queue

class EmptySyncVar[A <: Any] extends SyncVar[A] {
  @tailrec
  final override def evaluate(fun: A => Boolean): Boolean = {
    if (queue.size == 0) false
    else if (fun(queue.dequeue())) true
    else evaluate(fun)
  }
}

// New implementation of sync var uses standard synchronization and a mutable queue
class SyncVar[A <: Any](initialValue: Option[A] = None) {

  def this(initialValue: A) = this(Some(initialValue))

  val defaultTimeout = 5000

  protected val queue = new Queue[A]
  initialValue.foreach { x => queue.enqueue(x) }

  def current = queue.synchronized { queue.last }

  def update(value: A): Unit = queue.synchronized {
    queue.enqueue(value)
    queue.notifyAll
  }

  def atomic(fun: A => A): Unit = queue.synchronized {
    queue.enqueue(fun(queue.last))
    queue.notifyAll
  }

  def lastValueAfter(msec: Long): A = {
    waitFor(x => false, msec, false)
    current
  }

  def waitUntil(value: A, msec: Long = defaultTimeout, throwOnFailure: Boolean = true, customException: => Option[Exception] = { None }): Boolean = {
    waitFor(current => current == value, msec, throwOnFailure, customException)
  }

  def waitWhile(value: A, msec: Long = defaultTimeout, throwOnFailure: Boolean = true, customException: => Option[Exception] = { None }): Boolean = {
    waitFor(current => current != value, msec, throwOnFailure, customException)
  }

  def waitFor(fun: A => Boolean, msec: Long = defaultTimeout, throwOnFailure: Boolean = true, customException: => Option[Exception] = { None }): Boolean = queue.synchronized {

    @tailrec
    def waitUntilExpiration(fun: A => Boolean, expiration: Long): Boolean = {
      if (evaluate(fun)) true
      else {
        val diff = expiration - System.currentTimeMillis
        if (diff > 0) {
          queue.wait(diff)
          waitUntilExpiration(fun, expiration)
        } else {
          if (throwOnFailure) {
            throw customException.getOrElse(new Exception("Condition not met, final value was: " + queue.last))
          } else false
        }
      }
    }

    waitUntilExpiration(fun, System.currentTimeMillis + msec)
  }

  @tailrec
  private def privateEvaluate(fun: A => Boolean): Boolean = {
    val i = queue.dequeue()
    if (queue.size == 0) {
      queue.enqueue(i) //never let the queue be empty
      fun(i)
    } else {
      if (fun(i)) true
      else privateEvaluate(fun)
    }
  }

  protected def evaluate(fun: A => Boolean): Boolean = privateEvaluate(fun)

}

