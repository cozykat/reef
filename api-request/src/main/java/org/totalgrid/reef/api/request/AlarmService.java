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
package org.totalgrid.reef.api.request;

import org.totalgrid.reef.api.ReefServiceException;
import org.totalgrid.reef.api.javaclient.SubscriptionCreator;
import org.totalgrid.reef.api.javaclient.SubscriptionResult;
import org.totalgrid.reef.proto.Alarms.Alarm;

import java.util.List;

/**
 * Alarms are a refinement of events which identify system occurrences that require operator intervention.
 * Alarm objects are tied closely to event objects. All alarms are associated with events, but not all events cause alarms.
 * <p/>
 * In contrast to events, alarms have persistent state. The three principal alarm states are unacknowledged,
 * acknowledged, and removed. The transitions between these states constitute the alarm lifecycle, and
 * manipulation of the states involves user workflow.
 * <p/>
 * Transitions in alarm state may themselves be events, as they are part of the record of user operations.
 * <p/>
 * During the configuration process, the system designer decides what events trigger alarms. The primary consumers of
 * alarms are operators tasked with monitoring the system in real-time and responding to abnormal conditions.
 */
public interface AlarmService extends SubscriptionCreator {

    /**
     * get a single alarm
     *
     * @param uid uid of alarm
     */
    public Alarm getAlarm(String uid) throws ReefServiceException;

    /**
     * get the most recent alarms
     *
     * @param limit the number of incoming alarms
     */
    public List<Alarm> getActiveAlarms(int limit) throws ReefServiceException;

    /**
     * get the most recent alarms and setup a subscription to all future alarms
     *
     * @param limit the number of incoming events
     */
    public SubscriptionResult<List<Alarm>, Alarm> subscribeToActiveAlarms(int limit) throws ReefServiceException;

    /**
     * get the most recent alarms
     *
     * @param types event type names
     * @param limit the number of incoming alarms
     */
    public List<Alarm> getActiveAlarms(List<String> types, int limit) throws ReefServiceException;

    /**
     * silences an audible alarm
     */
    public Alarm silenceAlarm(Alarm alarm) throws ReefServiceException;

    /**
     * acknowledge the alarm (silences if not already silenced)
     */
    public Alarm acknowledgeAlarm(Alarm alarm) throws ReefServiceException;

    /**
     * "remove" an Alarm from the active list.
     */
    public Alarm removeAlarm(Alarm alarm) throws ReefServiceException;

}