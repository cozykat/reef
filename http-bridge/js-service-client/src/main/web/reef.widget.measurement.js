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
/*
 This jQuery plugin is written following the best practices from:
   http://docs.jquery.com/Plugins/Authoring
 This code has been formatted and jLint tested using: http://jsfiddle.net
*/
(function($) {

    $.fn.displayMeasurements = function(options) {

        var timeFormatter = function(time) {
            return new Date(time) + "";
        };

        var enhanceMeasurements = function(measurements) {
            return $.map(measurements, function(meas) {
                return enhanceMeasurement(meas);
            });
        };

        var enhanceMeasurement = function(meas) {
            var meas_value;
            switch (meas.type) {
            case "DOUBLE":
                meas_value = meas.double_val;
                break;
            case "INT":
                meas_value = meas.int_val;
                break;
            case "STRING":
                meas_value = meas.string_val;
                break;
            case "BOOL":
                meas_value = meas.bool_val;
                break;
            case "NONE":
                meas_value = "-";
                break;
            }
            meas.value = meas_value;

            var qualityStringParts = [];
            var qual = meas.quality;
            if (qual) {
                if (qual.validity) {
                    qualityStringParts.push(qual.validity);
                }
                var detail_qual = qual.detail_qual;
                if (detail_qual) {
                    var detailParts = [];
                    var detail_qual_names = $.each(detail_qual, function(name, value) {
                        detailParts.push(name);
                        return name;
                    });
                    if (detailParts.length > 0) {
                        qualityStringParts.push("(" + detailParts.join(", ") + ")");
                    }
                    if (detail_qual.inconsistent === true) {
                        meas.abnormal = true;
                    }
                }
            }
            meas.quality_string = qualityStringParts.join(" ");
            meas.time_string = settings.display.time_formatter(meas.time);
            return meas;
        };

        var displayMeasurements = function(measurements) {
            var div_data = $.map(measurements, function(meas) {
                var result = "<div class=\"measurement\">";
                if (settings.display.name_div) {
                    var meas_name = meas.name;
                    if (settings.display.add_unit_to_name) {
                        meas_name += " (" + meas.unit + ")";
                    }
                    result += "<div class=\"meas_name\">" + meas_name + "</div>";
                }
                if (settings.display.value_div) {
                    var extra_value_class = "";
                    if (meas.abnormal) {
                        extra_value_class = " meas_abnormal";
                    }

                    var value_title_parts = [];
                    if (settings.display.time_in_hover) {
                        value_title_parts.push(meas.time_string);
                    }
                    if (settings.display.quality_in_hover) {
                        value_title_parts.push(meas.quality_string);
                    }
                    var value_title_text = value_title_parts.join(" ");

                    result += "<div class=\"meas_value meas_type_" + meas.type + extra_value_class + "\" title=\"" + value_title_text + "\">" + meas.value + "</div>";
                }
                if (settings.display.quality_div) {
                    result += "<div class=\"meas_quality\">" + meas.quality_string + "</div>";
                }
                if (settings.display.time_div) {
                    result += "<div class=\"meas_time\">" + meas.time_string + "</div>";
                }
                result += "</div>";
                return result;
            }).join("\n");
            setTargetDiv(div_data);
        };

        var displayError = function(msg) {
            setTargetDiv("<div class=\"meas_error\">" + msg + "</div>");
        };

        var setTargetDiv = function(text) {
            var header = "<div class=\"measurement_widget\"><div class=\"measurement_widget_header\">Server: " + settings.server + "</div>";
            var footer = "<div class=\"measurement_widget_footer\">Powered by Greenbus.</div></div>";
            settings.target_div.html(header + text + footer);
        };

        var getMeasurements = function() {
           settings.client.getMeasurementsByNames(settings.point_names,
            function(measurements){
                settings.display_function(enhanceMeasurements(measurements));
            }, function(errorString){
                settings.error_function(errorString);
            }
            );
        };

        var settings = $.extend({
            // address of the server to query
            'client': undefined,
            // list of point names, must include atleast one point name
            'pointNames': [],
            // enables live-updates via polling, do not poll more often than once a second
            'polling': {
                'enabled': false,
                'period': 5000,
                'cancel_polling': function() {}
            },
            // allow overriding of the display and error routines
            // display_function takes a list of measurements with the enhanced fields ('value', 'quality_string', 'time_string' and 'abnormal')
            'display_function': displayMeasurements,
            // error message takes a string describing the error
            'error_function': displayError,
            // div we want to replace the contents of (if using default displayMeasurements)
            'target_div': this,
            // options for the default displayMeasurements function
            'display': {
                // *_div options control the adding of a seperate <div> element for each element
                'name_div': true,
                'value_div': true,
                'add_unit_to_name': true,
                'quality_div': false,
                'time_div': false,
                // add time and/or quality to the hover over text (title) for the value
                'time_in_hover': true,
                'quality_in_hover': true,
                // takes a time in milliseconds
                'time_formatter': timeFormatter
            }
        }, options);

        if (settings.point_names.length === 0) {
            throw "No points requested";
        }
        if (settings.client === undefined) {
            throw "No client configured";
        }

        return this.each(function() {
            getMeasurements();

            if (settings.polling && settings.polling.enabled && settings.polling.period) {
                var timer = setInterval(function() {
                    getMeasurements();
                }, settings.polling.period);
                settings.polling.cancel_polling = function() {
                    timer.clearInterval();
                };
            }
        });

    };
})(jQuery);