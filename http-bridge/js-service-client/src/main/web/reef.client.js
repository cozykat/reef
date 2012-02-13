/*
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
(function($) {

    $.reefClient = function(options) {

        var clientObject = {};

        var displayError = function(msg) {
            if(settings.error_div){
                settings.error_div.html("<div class=\"client_error\">" + msg + "</div>");
            }
        };

        var settings = $.extend({
            // address of the server to query
            'server': '',
            // error message takes a string describing the error
            'error_function': displayError,
            // div we want to replace the contents of (if using default displayError)
            'error_div': undefined,
            // username and password to start logging in with
            'autoLogin' : {
                'name' : undefined,
                'password' : undefined
            },
            // list of services sets we want to load
            'service_lists' : ['core']

        }, options);

        var doAjax = function(options) {

            if (settings.authToken) {
                var authHeader = {
                    'REEF_AUTH_TOKEN': settings.authToken
                };
                if (options.headers) {
                    $.extend(authHeader, options.headers);
                }
                else {
                    options.headers = authHeader;
                }
            }

            // add the standard options
            var requestSettings = $.extend({
                type: 'GET',
                dataType: 'json',
                success: function(jsonData, textStatus, jqXhdr) {
                    // if the server is totally unavailable success is called with null data and textStatus "success"
                    // that is strange, though possibly a caching issue
                    if (jsonData) {
                        options.handleData(jsonData, jqXhdr);
                    } else {
                        options.handleError("Couldn't connect to server.");
                    }

                    // kick off the next request (if theres any queued)
                    $(clientObject).dequeue();
                },
                error: function(XMLHttpRequest, textStatus, errorThrown) {
                    var msg = XMLHttpRequest.status + " " + XMLHttpRequest.statusText;
                    if (errorThrown) {
                        msg += " " + errorThrown;
                    }
                    options.handleError(msg);
                    // kick off the next request (if theres any queued)
                    $(clientObject).dequeue();
                }
            }, options);

            // start the asynchronous request
            $.ajax(requestSettings);
        };

        var enqueueRequest = function(options) {
            // use the jQuery queuing functions to maintain a "single threaded" queue of requests
            $(clientObject).queue(function() {
                doAjax(options);
            });
        };

        var apiRequest = function(options) {
            var requestName = options.request;
            var requestData = options.data;
            var onData = options.success;

            var onError = settings.error_function;
            if (options.error !== undefined) {
                onError = options.error;
            }

            enqueueRequest({
                url: settings.server + "/api/" + requestName,
                data: requestData,
                handleData: function(jsonData, jqXhdr) {

                    // TODO: expose headers support is coming
                    //var style = jqXhdr.getResponseHeader("REEF_RETURN_STYLE");
                    var style = options.style;
                    switch (style) {
                    case "MULTI":
                        onData(jsonData.results);
                        break;
                    case "SINGLE":
                        onData(jsonData);
                        break;
                    default:
                        onError("unknown return style");
                    }
                },
                handleError: onError
            });

        };

        var login = function(userName, userPassword) {
            if (settings.authToken) {
                throw "Already logged in, logout first";
            }

            enqueueRequest({
                url: settings.server + "/login",
                data: {
                    name: userName,
                    password: userPassword
                },
                type: 'GET',
                dataType: 'text',
                handleData: function(jsonData, jqXhdr) {
                    //console.log("Logged in: " + userName);
                    settings.authToken = jsonData;
                },
                handleError: settings.error_function
            });

        };
        clientObject = {
            'apiRequest': apiRequest,
            'login': login
        };

        $.each(settings.service_lists, function(i, name){
          try{
            eval("$.reefServiceList_" + name + "(clientObject);");
          }catch(err){
            alert("Can't load reef service list: " + name + ". Did you include reef.client.core-services.js?")
          }
        });

        if(settings.autoLogin && settings.autoLogin.name){
            clientObject.login(options.autoLogin.name, options.autoLogin.password);
        }

        return clientObject;
    };
})(jQuery);