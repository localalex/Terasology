/*
 * Copyright 2017 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.telemetry.metrics;

import com.snowplowanalytics.snowplow.tracker.events.Unstructured;
import org.reflections.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.context.Context;
import org.terasology.engine.subsystem.DisplayDevice;
import org.terasology.module.sandbox.API;
import org.terasology.registry.CoreRegistry;
import org.terasology.telemetry.Metrics;
import org.terasology.telemetry.TelemetryCategory;
import org.terasology.telemetry.TelemetryField;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An abstract class for a telemetry metric.
 * A new metric should extends this class, with annotation {@link org.terasology.telemetry.TelemetryCategory}.
 * All metric fields should be annotated {@link org.terasology.telemetry.TelemetryField}.
 * An example is {@link org.terasology.telemetry.metrics.SystemContextMetric}.
 * The metric will be instantiated automatically in {@link org.terasology.telemetry.Metrics}
 * By convention, a new Metric can have only one constructor and constructor will need no arguments or only {@link org.terasology.context.Context}.
 * If a Metric Constructor needs some specific arguments other than {@link org.terasology.context.Context},
 * it should be instantiated and added to {@link org.terasology.telemetry.Metrics} manually.
 */
@API
public abstract class Metric {

    private static final Logger logger = LoggerFactory.getLogger(Metric.class);

    /**
     * The map contains telemetry field name as key and field value as value.
     * If the telemetry field is a map, then this map equals to that.
     */
    protected Map<String, Object> telemetryFieldToValue = new HashMap<>();

    /**
     * Generates a snowplow unstructured event that the snowplow tracker can track.
     * @return an snowplow unstructured event.
     */
    public abstract Unstructured getUnstructuredMetric();

    /**
     * Fetches all TelemetryFields and create a map associating field's name (key) to field's value (value).
     * @return a map with key (field's name) and value (field's value).
     */
    public Map<String, ?> createTelemetryFieldToValue() {
        return AccessController.doPrivileged((PrivilegedAction<Map<String, ?>>) () -> {

            telemetryFieldToValue = new HashMap<>();
            Set<Field> fields = ReflectionUtils.getFields(this.getClass(), ReflectionUtils.withAnnotation(TelemetryField.class));

            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    telemetryFieldToValue.put(field.getName(), field.get(this));
                } catch (IllegalAccessException e) {
                    logger.error("The field is not inaccessible: ", e);
                }
            }

            return telemetryFieldToValue;
        });
    }

    /**
     * Filter the metric map by the binding map.
     * If the user doesn't want the field to be sent, its value will be covered by "Disabled Field".
     * @param bindingMap the binding map.
     * @return a new metric map that covers the field that the user doesn't want to send by "Disabled Field".
     */
    protected Map<String, ?> filterMetricMap(Map<String, Boolean> bindingMap) {
        TelemetryCategory telemetryCategory = this.getClass().getAnnotation(TelemetryCategory.class);
        Context context = CoreRegistry.get(Context.class);
        DisplayDevice display = context.get(DisplayDevice.class);
        if (display.isHeadless()) {
            return telemetryFieldToValue;
        }
        Map<String, Object> metricMapAfterPermission = new HashMap<>();
        for (Object key : telemetryFieldToValue.keySet()) {
            String fieldName = key.toString();
            String fieldNamewithID = telemetryCategory.id() + ":" + key.toString();
            if (bindingMap.containsKey(fieldNamewithID)) {
                if (bindingMap.get(fieldNamewithID)) {
                    metricMapAfterPermission.put(fieldName, telemetryFieldToValue.get(fieldName));
                } else {
                    metricMapAfterPermission.put(fieldName, "Disabled Field");
                }
            }
        }

        return metricMapAfterPermission;
    }

    /**
     * Add the new metric to {@link org.terasology.telemetry.Metrics} instance.
     * This method will only be used when a metric constructor needs some specific arguments other than {@link org.terasology.context.Context}.
     * @param metrics the metrics class instance in the game context.
     */
    public void addToMetrics(Metrics metrics) {
        metrics.addMetric(this);
    }

    /**
     * Get a list of all the telemetry field names marked with {@link org.terasology.telemetry.TelemetryField} annotation in this class.
     * The field name is in the form telemetryCategory.id() + ":" fieldName.
     * @return the list of all the telemetry field names in this class.
     */
    public List<String> createTelemetryFieldList() {
        TelemetryCategory telemetryCategory = this.getClass().getAnnotation(TelemetryCategory.class);
        List<String> fieldsList = new ArrayList<>();
        if (!telemetryCategory.isOneMapMetric()) {
            Set<Field> fields = ReflectionUtils.getFields(this.getClass(), ReflectionUtils.withAnnotation(TelemetryField.class));
            for (Field field : fields) {
                String fieldName = telemetryCategory.id() + ":" + field.getName();
                fieldsList.add(fieldName);
            }
        }
        return fieldsList;
    }
}
