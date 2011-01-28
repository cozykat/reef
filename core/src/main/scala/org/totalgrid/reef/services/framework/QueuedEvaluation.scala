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
package org.totalgrid.reef.services.framework

import scala.collection.immutable

/**
 * Generic interface for queueing functions to be evaluated at a later time.
 */
trait QueuedEvaluation {

  /**
   * Queue function for later evaluation
   *   @param fun queued code block
   */
  def queue(fun: => Unit): Unit
}

/** 
 * Simple List-based implementation of function-queuing for later evaluation. Queued
 * functions are evaluated in the order they were received.
 *
 */
trait BasicQueuedEvaluation extends QueuedEvaluation {
  protected var evals = immutable.List.empty[() => Unit]

  /**
   * Queue block of code to be evaluated later
   *   @param fun queued code block
   */
  def queue(fun: => Unit): Unit = {
    evals ::= (() => fun)
  }

  /**
   * Evaluate all queued functions
   */
  def evaluate = evals.reverse.foreach(_())

  /**
   * Clear all queued functions without evaluating
   */
  def clearBuffer = { evals = immutable.List.empty[() => Unit] } // NOTE: the name collision here is probably telling us this should be has-a or just implements buffer like
}

/**
 * Links buffer flush/clear notifications to queued evaluation.
 *
 */
trait LinkedBufferedEvaluation
    extends LinkedBufferLike
    with BasicQueuedEvaluation {

  protected def onFlush = evaluate
  protected def onClear = clearBuffer
}
