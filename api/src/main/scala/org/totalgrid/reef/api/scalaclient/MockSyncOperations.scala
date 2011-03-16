package org.totalgrid.reef.api.scalaclient

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
import com.google.protobuf.GeneratedMessage
import scala.collection.mutable.Queue
import org.totalgrid.reef.api.{ Envelope, RequestEnv, IDestination, AnyNode }
import org.totalgrid.reef.api.ServiceTypes._
/**
 * Mock the ISyncServiceClient to collect all puts, posts, and deletes. A 'get' function
 * is specified upon construction.
 *
 * @param doGet    Function that is called for client.get
 * @param putQueue Mutable queue where puts & posts are enqueued.
 * @param delQueue Mutable queue where deletes are enqueued.
 *
 */
class MockSyncOperations(
    doGet: (AnyRef) => MultiResult[AnyRef],
    putQueue: Queue[AnyRef] = Queue[AnyRef](),
    delQueue: Queue[AnyRef] = Queue[AnyRef]()) extends SyncOperations with DefaultHeaders {

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
   *   Assert that the elements in the internal putQueue are the same as
   * the elements in the specified putQ.
   * TODO: report which element is not equal (report the index, proto class diff, etc.).
   */
  def assertPuts(putQ: Queue[GeneratedMessage]): Boolean = {
    putQueue == putQ
    //putQueue.corresponds( putQ)(_==_)
  }

  /**
   * Override request to define all of the verb helpers
   */
  override def request[A <: AnyRef](verb: Envelope.Verb, payload: A, env: RequestEnv = getDefaultHeaders, dest: IDestination = AnyNode): MultiResult[A] = verb match {
    case Envelope.Verb.GET => doGet(payload).asInstanceOf[MultiResult[A]]
    case Envelope.Verb.DELETE =>
      delQueue.enqueue(payload)
      MultiSuccess(Envelope.Status.OK, List[A](payload))
    case Envelope.Verb.PUT =>
      putQueue.enqueue(payload)
      MultiSuccess(Envelope.Status.OK, List[A](payload))
    case Envelope.Verb.POST => throw new Exception("unimplemented")
  }

}

