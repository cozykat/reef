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
package org.totalgrid.reef.messaging.javabridge

import org.totalgrid.reef.proto.Envelope
import org.totalgrid.reef.messaging.ProtoServiceTypes._
import org.totalgrid.reef.messaging.ServiceHandlerHeaders
import com.google.protobuf.{ GeneratedMessage, ByteString, InvalidProtocolBufferException }

/**
 * Helper that adapts the scala deserialization functiosn to a Java interface
 */
trait ProtoDescriptor[T] {
  @throws(classOf[InvalidProtocolBufferException])
  def deserializeString(data: ByteString): T

  @throws(classOf[InvalidProtocolBufferException])
  def deserializeBytes(data: Array[Byte]): T

  def getKlass: Class[_]
}

/**
 *  Adapts raw events functions to a Java interface
 */
trait EventAcceptor[T] {
  def onEvent(event: Event[T]): Unit
}

/**
 *  A subscription object that can be canceled
 */
trait Subscription {
  def configure(headers: ServiceHandlerHeaders)
  def cancel()
}

/**
 *  The interface that a concrete service client must provide.
 */
trait IServiceClient {

  def request[T <: GeneratedMessage](verb: Envelope.Verb, payload: T, env: ServiceHandlerHeaders): java.util.List[T]

  def get[T <: GeneratedMessage](payload: T): java.util.List[T]
  def delete[T <: GeneratedMessage](payload: T): java.util.List[T]
  def post[T <: GeneratedMessage](payload: T): java.util.List[T]
  def put[T <: GeneratedMessage](payload: T): java.util.List[T]

  def get[T <: GeneratedMessage](payload: T, sub: Subscription): java.util.List[T]
  def delete[T <: GeneratedMessage](payload: T, sub: Subscription): java.util.List[T]
  def post[T <: GeneratedMessage](payload: T, sub: Subscription): java.util.List[T]
  def put[T <: GeneratedMessage](payload: T, sub: Subscription): java.util.List[T]

  def get_one[T <: GeneratedMessage](payload: T): T
  def delete_one[T <: GeneratedMessage](payload: T): T
  def put_one[T <: GeneratedMessage](payload: T): T

  def get_one[T <: GeneratedMessage](payload: T, sub: Subscription): T
  def delete_one[T <: GeneratedMessage](payload: T, sub: Subscription): T
  def put_one[T <: GeneratedMessage](payload: T, sub: Subscription): T

  def addSubscription[T <: GeneratedMessage](pd: ProtoDescriptor[T], ea: EventAcceptor[T]): Subscription

  def getDefaultEnv(): ServiceHandlerHeaders

  def close()
}
