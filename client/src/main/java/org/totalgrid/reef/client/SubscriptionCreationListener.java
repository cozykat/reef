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
package org.totalgrid.reef.client;

/**
 * Interface to receive notifications when a subscription binding is created using a service
 * wrapper. This makes it much easier to create leak-proof subscription handling, all
 * subscriptions should be eventually canceled.
 */
public interface SubscriptionCreationListener
{

    /**
     * Called when a subscription or "client side service" is created
     * @param binding Newly created SubscriptionBinding
     */
    public void onSubscriptionCreated( SubscriptionBinding binding );
}
