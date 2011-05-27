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
/**
Proto definition file for Tags.
<pre>package org.totalgrid.reef.proto.Tags;

import "Commands.proto";
import "Model.proto";

option java_package = "org.totalgrid.reef.proto";
option java_outer_classname = "Tags";




//  MAJOR TYPES IN THIS PROTO:
//
//    TagType
//        - The type of tag to choose from when creating a tag.
//          Example: informational, maintenance, blocking, etc.
//
//    Tag
//        - Information or control inhibitor tied to a device, or equipment group.
//          Example: Block all controls on breaker AB1357
//
//    TagList
//        - Used to get tags. Contains a TagQuery object.
//
//    FieldType, FieldDescriptor, Field
//        - Used to define custom fields for each TagType


//  USE CASES:
//
//  _______________________________________________________
//  Get all TagTypes (because the user has to pick one when creating a tag).
//
//  ==> GET: TagType
//  {}
//
//  <== Result: TagType
//  [
//    { id: 123456,
//      name: "Blocking",
//      color: "FF0000",
//      control: {
//        action: BLOCK,
//        command: { ... "command-open" ... },
//        command_locked: true,
//        entity: { ... "breaker" ... }
//      },
//               //                                 Short                                 Field  Create Read
//               // ID    Label              Type   Label         Validate    W   H   L   Order  Only   Only
//               // ====  =========          ====   =========     ==========  ==  ==  ==  =====  ====== ====
//      fields: [ [ 1002, "Completion Est",  TIME, "Complete",   "[A-z0-9]+", 20,  1, 64,     2, true,  false]
//              ]
//    },
//    {...}
//  ]
//
//  _______________________________________________________
//  Get Latest Tags
//
//  ==> GET: TagList
//  { query: {}}
//
//  <== Result: TagList
//  { tag: [
//    { id: 234567,
//      name: "WO1234",
//      type_id: 123456,
//      entity: {...},
//      control: {...},
//      create_time: 47124092323,
//      create_user: "johndoe",
//      modified_time: 238473948732,
//      field: [
//               { type: "TIME",
//                 data_time: 98472398479
//               }
//             ]
//    },
//    {...},
//    {...}
//  ]}
//
//
//  _______________________________________________________
//  Create a blocking tag
//
//  ==> PUT: Tag
//  { name: "WO1234",
//    type_id: 123456,
//    entity: {...},
//    control: {...},
//    field: [
//             { type: "TIME",
//               data_time: 98472398479
//             }
//           ]
//  }
//
//  <== Result: Tag
//  { id: 23456
//    name: "WO1234",
//    type_id: 123456,
//    entity: {...},
//    control: {...},
//    create_time: 47124092323,
//    create_user: "johndoe",
//    modified_time: 47124092323,
//    field: [
//             { type: "TIME",
//               data_time: 98472398479
//             }
//           ]
//  }



// The type for a custom Field.
enum FieldType {
  STRING = 1;    // User specified string
  ID     = 2;    // Identifier. Probably internally generated, but may be user-entered from an external system.
  TIME   = 3;    // Date/Time information.
  USERID = 4;    // User ID
}


// Field Descriptor
// All the properties needed to display and validate a single custom field.
// A set of Field Descriptors can define the layout for a dialog.
//
message FieldDescr {
  required uint64    id            =  1; // Auto-incrementing unique ID greater than 0.
  required string    name          =  2; // visible label name. Unique across fields for one type.
  optional string    short_name    =  4; // visible label name for tight spaces
  required FieldType type          =  3; // The type of data for this field in a tag instance.
  optional string    validation    =  5; // regular expression used for validation.  See: http://www.w3schools.com/jsref/jsref_obj_regexp.asp
  required uint32    field_width   =  6; // Width of field on screen
  required uint32    field_height  =  7; // 1: TextField, 2 or more: Text area
  required uint32    max_length    =  8; // Max number of string characters. ? How does browser edit field length map to DB varchar length for unicode?
  optional uint32    display_order =  9; // First field is 1.
  required bool      create_only   = 10; // True: only editable on create. Can't update this field.
  required bool      read_only     = 11; // This field is always read-only.
}

//
// The data for a custom Field in Tag instance
//
message Field {
  required FieldType type = 1;

  // Union to store instance data as specific type.
  // TODO: is this how it's done in protos?
  optional string    data_string = 2;
  optional uint64    data_uint   = 3;
  optional uint64    data_time   = 4;
}


//
// The action the tag is taking
//
enum TagAction {

  NONE  = 1;
  BLOCK = 2;  // Do not allow the control to execute.
  WARN  = 3;  // Warn the user who's attempting to execute the control. The user can Cancel or Continue to execute the control.
}



// Define the control inhibitions enforced when a specific TagType is chosen.
// When the user picks a TagType, the TagControl specifies the controls to
// inhibit or warn.
//
// If command_locked, the user cannot override the control actions when
// creating a Tag. If unlocked, items are preselected, but the user may
// override them when creating a Tag.
//
message TagControl {
  required TagAction     action         = 1;  // The action for this tag: NONE, BLOCKING, WARNING. etc.
  required org.totalgrid.reef.proto.Commands.CommandAccess command        = 2;  // The affected command(s) (ex: TRIP, CLOSE, ANY_CONTROL)
  optional bool          command_locked = 3;  // True: The commands are locked so the user cannot edit them. 
  repeated org.totalgrid.reef.proto.Model.Entity        entity         = 4;  // TODO: Should we specify the valid entity types for this TagControl?
}



//
// A system has multiple Tag Types. Each tag type has a name,
// color, etc. Tags may also have a list of custom fields in addition to
// the standard fields for all tag types.
//
// A typical system will have around 4 to 8 types of tags, but could have many more.
//
message TagType {
  optional uint64     id        = 1;  // Required: Auto-incrementing unique ID greater than 0.
  optional string     name      = 2;  // Required: Display name of this type
  optional string     color     = 3;  // Required: RGB color specification. CSS compliant
  optional string     image     = 4;  // Optional: Image file location. TODO: Could store a binary image.
  optional string     iconChar  = 5;  // Optional: Single Unicode character overlaid on small tag icon to distinguish this type.
  optional TagControl control   = 6;  // Optional: What controls are being blocked or warned against?

  // Additional custom fields for specific tag types
  repeated FieldDescr field = 100;
}


//
// A tag instance. This is associated with an entity and may specify controls to block.
//
message Tag {
  optional uint64        id      = 1;  // Instance ID. Auto-incrementing unique ID greater than 0.  TODO: can this be combined with "name"?
  optional string        name    = 2;  // User-entered unique name for this tag instance
  required uint64        type_id = 3;  // ID for this tag's tag type
  required org.totalgrid.reef.proto.Model.Entity        entity  = 4;  // This tag is _on_ this entity
  optional org.totalgrid.reef.proto.Commands.CommandAccess command = 5;  // The control(s) that are blocked by this tag

  optional uint64        create_time   = 23;  // Date/Time this tag was created.
  optional string        create_user   = 24;  // UserID of user who created tag
  optional uint64        field_time    = 25;  // Data/Time the physical tag was placed in the field
  optional string        field_user    = 26;  // UserId of user who placed physical tag in the field
  optional uint64        modified_time = 27;  // Data/Time this tag was last modified.

  repeated Field         field = 100;
}


//
// Tag queries and subscriptions
//
message TagQuery {
  // Empty: get the latest tags. Default limit is 1000.
  repeated uint64   id                 =  1 ;  // List of tag IDs. Used to get a specific tag instance or set of tags.
  repeated uint64   type               =  2 ;  // List of tag types
  optional uint64   time_from          =  3 ;  // If not present, get latest with limit
  optional uint64   time_to            =  4 ;  // if time_to is unavailable, use now
  repeated string   equipmentgroup     =  5 ;  // List of equipment groups or substations
  repeated string   entity             =  6 ;  // List of entity IDs. TODO: Is this different than equipmentgroup?
  repeated string   user_id            =  7 ;  // List of userIds
  optional uint32   limit              =  8 ;  // Max number of rows for query result
  optional bool     ascending          =  9 ;  // Time sort order. If not present, order is descending.
  optional uint64   id_after           = 10 ;  // Get tags after this ID (not including this id). Used for getting new tags since last query.

  // TODO: Tag counts will use a different mechanism?
}

//
// Use this to query for tags. Send the TagQuery and return an array of Tag.
//
message TagList {
  optional TagQuery query = 1 ;
  repeated Tag      tags  = 2 ;
}
</pre>
*/
package org.totalgrid.reef.api.request.protodoc.tags;