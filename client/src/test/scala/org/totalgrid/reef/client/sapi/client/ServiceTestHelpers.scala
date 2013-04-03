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
package org.totalgrid.reef.client.sapi.client

import net.agileautomata.executor4s._
import org.totalgrid.reef.client.{ PromiseListener, PromiseTransform, PromiseErrorTransform, Subscription => JavaSubscription, SubscriptionEventAcceptor, SubscriptionResult, Promise => JPromise }
import org.totalgrid.reef.client.exception.{ UnknownServiceException, ReefServiceException }

object ServiceTestHelpers {

  class MockSubscription[A](val id: String = "queue") extends JavaSubscription[A] {
    def getId = id
    var canceled = false
    var acceptor = Option.empty[SubscriptionEventAcceptor[A]]
    def start(acc: SubscriptionEventAcceptor[A]) = {
      acceptor = Some(acc)
      this
    }

    def cancel() = canceled = true
  }

  class MockSubscriptionResult[A](result: List[A], val mockSub: MockSubscription[A]) extends SubscriptionResult[List[A], A] {

    def getResult = result
    def getSubscription = mockSub

    def this(one: A) = this(one :: Nil, new MockSubscription[A]())
    def this(many: List[A]) = this(many, new MockSubscription[A]())
  }

  // TODO: merge away these fixed promises
  class FixedSuccessPromise[A](v: A) extends JPromise[A] {
    def await(): A = v
    def isComplete: Boolean = true
    def listen(listener: PromiseListener[A]) {
      listener.onComplete(this)
    }
    def transform[U](transform: PromiseTransform[A, U]): JPromise[U] = {
      new FixedSuccessPromise(transform.transform(v))
    }
    def transformError(transform: PromiseErrorTransform): JPromise[A] = this
  }
  class FixedFailurePromise[A](ex: ReefServiceException) extends JPromise[A] {
    def await(): A = throw ex
    def isComplete: Boolean = true
    def listen(listener: PromiseListener[A]) {
      listener.onComplete(this)
    }
    def transform[U](transform: PromiseTransform[A, U]): JPromise[U] = {
      new FixedFailurePromise[U](ex)
    }

    def transformError(transform: PromiseErrorTransform): JPromise[A] = {
      new FixedFailurePromise[A](transform.transformError(ex))
    }
  }

  def success[A](a: A) = new FixedSuccessPromise(a)
  def failure[A](ex: ReefServiceException) = new FixedFailurePromise(ex)
  def failure[A](msg: String) = new FixedFailurePromise(new UnknownServiceException(msg))

  def subSuccess[A](a: A) = new FixedSuccessPromise(new MockSubscriptionResult[A](a :: Nil))
  def subSuccess[A](a: List[A]) = new FixedSuccessPromise(new MockSubscriptionResult[A](a))
  def subFailure[A](ex: ReefServiceException) = new FixedFailurePromise(ex)

  // TODO: Figure out the right type hinting to make this unnecessary in FrontEndManagerTest
  def subSuccessInterface[A](a: List[A]): JPromise[SubscriptionResult[List[A], A]] = new FixedSuccessPromise(new MockSubscriptionResult[A](a))
  def subFailureInterface[A](ex: ReefServiceException): JPromise[SubscriptionResult[List[A], A]] = new FixedFailurePromise[SubscriptionResult[List[A], A]](ex)

  /*def success[A](a: A) = new FixedPromise(Success(a))
def failure[A](ex: Exception) = new FixedPromise(Failure(ex))
def failure[A](msg: String) = new FixedPromise(Failure(msg))

def subSuccess[A](a: A) = new FixedPromise(Success(new MockSubscriptionResult[A](a :: Nil)))
def subSuccess[A](a: List[A]) = new FixedPromise(Success(new MockSubscriptionResult[A](a)))
def subFailure[A](ex: Exception) = new FixedPromise(Failure(ex))*/
}
