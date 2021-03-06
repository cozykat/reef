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
package org.totalgrid.reef.client.operations.impl

import java.util.UUID
import com.google.protobuf.ByteString
import org.totalgrid.reef.client.types.{ TypeDescriptor, ServiceTypeInformation }
import collection.mutable.Queue
import org.totalgrid.reef.client.operations.scl.ScalaPromise._
import scala.collection.JavaConversions._
import org.totalgrid.reef.client.proto.{ StatusCodes, Envelope }
import org.totalgrid.reef.client.operations.scl.ScalaResponse
import org.totalgrid.reef.client.proto.Envelope.{ ServiceResponse, BatchServiceRequest, SelfIdentityingServiceRequest, Verb }
import org.totalgrid.reef.client.exception._
import org.totalgrid.reef.client.{ RequestHeaders, Promise }
import org.totalgrid.reef.client.operations.{ Response, RestOperations }
import org.totalgrid.reef.client.impl.ClassLookup

trait BatchRestOperations extends RestOperations with OptionallyBatchedRestOperations {
  def batched: Option[BatchRestOperations] = Some(this)
  def flush(): Promise[java.lang.Integer]
  def batchedFlush(batchSize: Int): Promise[java.lang.Integer]
}

/*class DefaultBatchRestOperations(protected val ops: RestOperations, client: DefaultClient) extends BatchRestOperationsImpl {
  protected def getServiceInfo[A](klass: Class[A]): ServiceTypeInformation[A, _] = client.getServiceInfo(klass)
  protected def futureSource[A](onAwait: Option[() => Unit]) = FuturePromise.openWithAwaitNotifier[A](client, onAwait)
  protected def notifyListeners[A](verb: Envelope.Verb, payload: A, promise: Promise[Response[A]]) {
    client.notifyListeners(verb, payload, promise)
  }

}*/

trait BatchRestOperationsImpl extends BatchRestOperations with DerivedRestOperations {
  protected def getServiceInfo[A](klass: Class[A]): ServiceTypeInformation[A, _]
  protected def futureSource[A](onAwait: Option[() => Unit]): OpenPromise[A]
  protected def ops: RestOperations
  protected def notifyListeners[A](verb: Envelope.Verb, payload: A, promise: Promise[Response[A]])

  case class QueuedRequest[A](request: SelfIdentityingServiceRequest, descriptor: TypeDescriptor[A], promise: OpenPromise[Response[A]])
  private class PendingBatchRequests {
    private val requestQueue = Queue.empty[QueuedRequest[_]]
    private var notYetFlushed = true

    def checkFlushed() = {
      if (notYetFlushed) throw new ServiceIOException("Batch has not been flushed, await cannot possibly complete")
    }

    def getPending: List[QueuedRequest[_]] = {
      val list = requestQueue.toList
      requestQueue.clear()
      unflushedRequests.notYetFlushed = false
      list
    }

    def enqueue(request: QueuedRequest[_]) {
      if (!notYetFlushed) throw new IllegalArgumentException("Batch state error")
      requestQueue.enqueue(request)
    }
  }

  private var unflushedRequests = new PendingBatchRequests

  protected def request[A](verb: Verb, payload: A, headers: Option[RequestHeaders]): Promise[Response[A]] = {

    val descriptor: TypeDescriptor[A] = getServiceInfo(ClassLookup.get(payload)).getDescriptor
    val uuid = UUID.randomUUID().toString

    val builder = Envelope.ServiceRequest.newBuilder.setVerb(verb).setId(uuid)
    builder.setPayload(ByteString.copyFrom(descriptor.serialize(payload)))
    headers.foreach { h => builder.addAllHeaders(h.toEnvelopeRequestHeaders) }

    val request = SelfIdentityingServiceRequest.newBuilder.setExchange(descriptor.id).setRequest(builder).build

    val promise = futureSource[Response[A]](Some(unflushedRequests.checkFlushed _))

    unflushedRequests.enqueue(QueuedRequest[A](request, descriptor, promise))

    notifyListeners(verb, payload, promise)
    promise
  }

  def flush(): Promise[java.lang.Integer] = {
    batchedFlush(-1)
  }

  def batchedFlush(batchSize: Int): Promise[java.lang.Integer] = {

    def nextBatch(prevFailed: Option[ReefServiceException], pending: List[QueuedRequest[_]], totalSize: Int, promise: OpenPromise[java.lang.Integer]) {
      prevFailed match {
        case Some(rse) =>
          // if an early request failed, fail all of the future promises as well
          pending.foreach(_.promise.setFailure(rse))
          promise.setFailure(rse)
        case None => pending match {
          case Nil => promise.setSuccess(totalSize)
          case remains =>
            val (now, later) = if (batchSize > 0) {
              remains.splitAt(batchSize)
            } else {
              (remains, Nil)
            }
            sendBatch(now, Some(nextBatch(_, later, totalSize, promise)))
        }
      }
    }

    val promise = futureSource[java.lang.Integer](None)

    val list = popRequests()
    nextBatch(None, list, list.size, promise)

    promise
  }

  private def sendBatch(requests: List[QueuedRequest[_]], chain: Option[(Option[ReefServiceException]) => Unit]) {

    def applyResponseToPromise[A](response: ServiceResponse, desc: TypeDescriptor[A], promise: OpenPromise[Response[A]]) {
      StatusCodes.isSuccess(response.getStatus) match {
        case true =>
          val data = response.getPayloadList.toList.map(bs => desc.deserialize(bs.toByteArray))
          promise.setSuccess(ScalaResponse.success(response.getStatus, data))
        case false =>
          promise.setFailure(new ReefServiceException(response.getErrorMessage, response.getStatus))
      }
    }

    val batch = {
      val b = BatchServiceRequest.newBuilder
      requests.foreach(r => b.addRequests(r.request))
      b.build
    }

    val batchPromise: Promise[Response[BatchServiceRequest]] = ops.request(Envelope.Verb.POST, batch)

    batchPromise.listenEither {
      case Right(resp) => {
        resp.isSuccess match {
          case true => {
            val responses = resp.getList.get(0).getRequestsList.toList.map(_.getResponse)
            responses.zip(requests).foreach {
              case (servResp, QueuedRequest(_, desc, promise)) => applyResponseToPromise(servResp, desc, promise)
            }
            chain.foreach(_(None))
          }
          case false =>
            val rse = resp.getException
            requests.foreach(_.promise.setFailure(rse))
            chain.foreach(_(Some(rse)))
        }
      }
      case Left(ex) => {
        val rse = ex match {
          case rse: ReefServiceException => rse
          case other => new InternalClientError("Problem with batch request", ex)
        }
        requests.foreach(_.promise.setFailure(rse))
        chain.foreach(_(Some(rse)))
      }
    }
  }

  private def popRequests(): List[QueuedRequest[_]] = {
    val list = unflushedRequests.getPending
    unflushedRequests = new PendingBatchRequests
    list
  }
}

