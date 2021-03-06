/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.collector.instrument;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;

/**
 * @author wu-sheng
 */
public class ServiceMetricTracing {
    private volatile ConcurrentHashMap<Method, ServiceMetric> metrics = new ConcurrentHashMap<>();

    public ServiceMetricTracing() {
    }

    @RuntimeType
    public Object intercept(
        @This Object inst,
        @SuperCall Callable<?> zuper,
        @AllArguments Object[] allArguments,
        @Origin Method method
    ) throws Throwable {
        ServiceMetric metric = this.metrics.get(method);
        if (metric == null) {
            GraphComputingMetric annotation = method.getAnnotation(GraphComputingMetric.class);
            String metricName = annotation.name();
            synchronized (inst) {
                MetricTree.MetricNode metricNode = MetricTree.INSTANCE.lookup(metricName);
                ServiceMetric serviceMetric = metricNode.getMetric(method, allArguments);
                metrics.put(method, serviceMetric);
                metric = serviceMetric;
            }
        }
        boolean occurError = false;
        long startNano = System.nanoTime();
        long endNano;
        try {
            return zuper.call();
        } catch (Throwable t) {
            occurError = true;
            throw t;
        } finally {
            endNano = System.nanoTime();

            metric.trace(endNano - startNano, occurError, allArguments);
        }
    }
}
