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
/**
 * Proto definition file for Events.
 * 
 * <pre>
 * package org.totalgrid.reef.client.service.proto.Events;
 * 
 * import "Model.proto";
 * import "Utils.proto";
 * 
 * option java_package = "org.totalgrid.reef.client.service.proto";
 * option java_outer_classname = "Events";
 * 
 * 
 * //  MAJOR TYPES IN THIS PROTO:
 * //
 * //  Event  -- A simple event
 * //  EventList   -- Used to get events
 * //  Log         -- Log entry
 * //  EventConfig -- Configuration for event severity and whether a posted message is an alarm, event, or log.
 * 
 * 
 * // USE CASES:
 * //
 * //  _______________________________________________________
 * //  Get the latest events. Normal sort is reverse time order.
 * //
 * //  ==> GET: EventList
 * //  { select: {} }
 * //
 * //  <== Result: EventList
 * //  { event:
 * //      //  id   event_type      alarm   time      severity subsystem user_id    entity
 * //      //  ===== =============   ======  ========  ======== ========= ========   ==================
 * //      [ [ 1235, "BreakerClose", true,   98273496, 2,       "FEP",    "system",  "subst42.breaker23" ],
 * //        [ 1234, "BreakerOpen",  true,   98273495, 2,       "FEP",    "system",  "subst42.breaker23" ],
 * //        [ 1236, "RtuOnline",    false,  98273494, 5,       "FEP",    "system",  "subst42.rtu8"      ],
 * //        [ 1236, "UserLogin",    false,  98273493, 6,       "Auth",   "system",  "subst42.rtu8"      ],
 * //        [ 1235, "BreakerClose", true,   98273492, 2,       "FEP",    "system",  "subst42.breaker23" ],
 * //        [ 1234, "BreakerOpen",  true,   98273491, 2,       "FEP",    "system",  "subst42.breaker23" ]
 * //      ]
 * //  }
 * //
 * //  _______________________________________________________
 * //  Get the latest events for device subst42.breaker23 
 * //
 * //  ==> GET: EventList
 * //  {
 * //    select: {
 * //      entity: {
 * //        name: "subst42.breaker23"
 * //      }
 * //    }
 * //  }
 * //
 * //  <== Result: EventList
 * //  { event:
 * //      //  id   event_type      alarm   time      severity subsystem user_id    entity
 * //      //  ===== =============   ======  ========  ======== ========= ========   ==================
 * //      [ [ 1235, "BreakerClose", true,   98273496, 2,       "FEP",    "system",  "subst42.breaker23" ],
 * //        [ 1234, "BreakerOpen",  true,   98273495, 2,       "FEP",    "system",  "subst42.breaker23" ],
 * //        [ 1235, "BreakerClose", true,   98273492, 2,       "FEP",    "system",  "subst42.breaker23" ],
 * //        [ 1234, "BreakerOpen",  true,   98273491, 2,       "FEP",    "system",  "subst42.breaker23" ]
 * //      ]
 * //  }
 * //
 * 
 * 
 * // An Event
 * //
 * message Event {
 *   optional org.totalgrid.reef.client.service.proto.Model.ReefID   id           =  1;   // Unique ID for this instance. Only available for events coming from the DB.
 *   optional string   event_type    =  2;   // The type of event. Ex: UserLogout, BreakerTrip, etc.
 *   optional bool     alarm         =  3 [default = false];  // True: This event is an Alarm
 *   optional uint64   time          =  4;   // milliseconds since midnight January 1, 1970 not counting leap seconds (Unix time, but with milliseconds)
 *   optional uint64   device_time   =  5 [default = 0]; // milliseconds since midnight January 1, 1970 not counting leap seconds (Unix time, but with milliseconds)
 *   optional uint32   severity      =  6;   // 1 is most severe. Number of severity levels is configurable (default is 1-8).
 *   optional string   subsystem     =  7;   // Subsystem that authored this event (frontend, measproc, etc.)
 *   optional string   user_id       =  8;   // User's login ID.
 *   optional org.totalgrid.reef.client.service.proto.Model.Entity entity =  9;   // Device or point (ex: southeast.substationAlpha.bay42.breaker23)
 *   optional org.totalgrid.reef.client.service.proto.Utils.AttributeList args = 10;   // Extra arguments for a specific event. Used by the localization resource string.
 *   optional string   rendered      = 11;   // This event rendered as a localized string.
 * }
 * 
 * 
 * // Use EventSelect within EventList to get or subscribe to events.
 * //
 * message EventSelect {
 * 
 *   // EMPTY:
 *   // If EventSelect is empty, select all recent events
 * 
 *   repeated string   event_type         =  1 ;  // List of EventTypes: UserLogin, BreakerTrip, etc. 
 *   optional uint64   time_from          =  2 ;  // If not present, get latest with limit
 *   optional uint64   time_to            =  3 ;  // if time_to is unavailable, use now
 *   repeated uint32   severity           =  4 ;  // List of specific severities
 *   optional uint32   severity_or_higher =  5 ;  // If present, overrides severity list above. Ex: '3' selects 1, 2 & 3.
 *   repeated string   subsystem          =  6 ;  // List of subsystems  
 *   repeated string   user_id            =  7 ;  // List of userIds
 *   repeated org.totalgrid.reef.client.service.proto.Model.Entity entity =  8;   // List of devices or points (ex: southeast.substationAlpha.bay42.breaker23)
 *   optional uint32   limit              =  9 ;  // Max number of rows for select result
 *   optional bool     ascending          = 10 ;  // Time sort order. If not present, order is descending.
 *   //optional string   uid_after          = 11 ;  // Get events after this UID (not including this id). Used for getting new events since last select.
 * }
 * 
 * 
 * // Use EventList to select and subscribe to events & alarms.
 * //
 * // See also: AlarmList - select and subscribe to alarms.
 * //
 * message EventList {
 *   optional EventSelect select  = 1 ;
 *   repeated Event  events  = 2 ;
 * }
 * 
 * 
 * // Log level
 * //
 * enum Level {
 *     EVENT     = 1;
 *     ERROR     = 2;
 *     WARNING   = 3;
 *     INFO      = 4;
 *     INTERPRET = 5;
 *     COM       = 6;
 *     DEBUG     = 7;
 *     TRACE     = 8;
 * }
 * 
 * // Log message or record
 * //
 * message Log {
 *   required uint64   time         = 1 ; // milliseconds since midnight January 1, 1970 not counting leap seconds (Unix time, but with milliseconds)
 *   required Level    level        = 2 ;
 *   required string   subsystem    = 3 ;   // Subsystem that authored this log
 *   optional string   file_name    = 4 ;
 *   optional uint32   line_number  = 5 ;
 *   required string   message      = 6 ;   // The message being logged.
 * }
 * </pre>
 */
package org.totalgrid.reef.client.service.protodoc.events;

