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
package org.totalgrid.reef.loader.helpers

import scala.collection.mutable.Queue

import org.totalgrid.reef.client.sapi.client.rest.RestOperations
import net.agileautomata.executor4s.testing.MockFuture
import org.totalgrid.reef.client.proto.Envelope
import org.totalgrid.reef.client.types.TypeDescriptor
import org.totalgrid.reef.client.sapi.client._
import net.agileautomata.executor4s.{ Failure, Result, Future }

/**
 * Mock the ISyncClientSession to collect all puts, posts, and deletes. A 'get' function
 * is specified upon construction.
 *
 * @param doGet    Function that is called for client.get
 * @param putQueue Mutable queue where puts & posts are enqueued.
 * @param delQueue Mutable queue where deletes are enqueued.
 *
 * TODO: get rid of MockSyncOperations and using tests
 */
final class MockSyncOperations(
    doGet: (AnyRef) => Response[AnyRef],
    putQueue: Queue[AnyRef] = Queue[AnyRef](),
    delQueue: Queue[AnyRef] = Queue[AnyRef]()) extends RestOperations with DefaultHeaders {

  override def subscribe[A](descriptor: TypeDescriptor[A]) = MockFuture.defined(Failure(new IllegalArgumentException("not implemented")))

  /**
   * Reset all queues.
   */
  def reset = {
    putQueue.clear
    delQueue.clear
  }

  /**
   * Return the internal mutable queue where puts & posts are enqueued. Caller should use dequeue to get protos.
   */
  def getPutQueue = putQueue

  /**
   * Return the internal mutable queue where deletes are enqueued. Caller should use dequeue to get protos.
   */
  def getDelQueue = delQueue

  /**
   * Assert that the elements in the internal putQueue are the same as the elements in the specified putQ.
   * TODO: report which element is not equal (report the index, proto class diff, etc.).
   */
  def assertPuts(putQ: Queue[AnyRef]): Boolean = {
    putQueue == putQ
    //putQueue.corresponds( putQ)(_==_)
  }

  /**
   * Override request to define all of the verb helpers
   */
  override def request[A](verb: Envelope.Verb, payload: A, env: Option[BasicRequestHeaders]): Future[Response[A]] = verb match {
    case Envelope.Verb.GET => new MockFuture(Some(doGet(payload.asInstanceOf[AnyRef]).asInstanceOf[Response[A]]))
    case Envelope.Verb.DELETE =>
      delQueue.enqueue(payload.asInstanceOf[AnyRef])
      new MockFuture(Some(SuccessResponse(Envelope.Status.OK, List[A](payload))))
    case Envelope.Verb.PUT =>
      putQueue.enqueue(payload.asInstanceOf[AnyRef])
      new MockFuture(Some(SuccessResponse(Envelope.Status.OK, List[A](payload))))
    case Envelope.Verb.POST => throw new Exception("unimplemented")
  }

}
