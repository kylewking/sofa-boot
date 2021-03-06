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
 */


package com.alipay.sofa.healthcheck.core;

import com.alipay.sofa.healthcheck.service.SofaBootComponentHealthCheckInfo;
import com.alipay.sofa.healthcheck.service.SpringContextHealthCheckInfo;
import com.alipay.sofa.healthcheck.startup.SofaBootApplicationAfterHealthCheckCallback;
import com.alipay.sofa.healthcheck.startup.SofaBootMiddlewareAfterHealthCheckCallback;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 * @author liangen
 * @version $Id: HealthCheckManager.java, v 0.1 2017年10月16日 上午10:40 liangen Exp $
 */
public class HealthCheckManager {
    private static ApplicationContext applicationContext;

    public static void init(ApplicationContext applicationContext) {
        HealthCheckManager.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public static List<HealthChecker> getHealthCheckers() {
        List<HealthChecker> healthCheckers = new ArrayList<HealthChecker>();

        Map<String, HealthChecker> stringToHealthChecker = applicationContext.getBeansOfType(HealthChecker.class);
        if (!CollectionUtils.isEmpty(stringToHealthChecker)) {
            for (HealthChecker healthChecker : stringToHealthChecker.values()) {
                healthCheckers.add(healthChecker);
            }
        }

        return healthCheckers;
    }

    public static List<HealthIndicator> getHealthIndicator() {
        List<HealthIndicator> healthIndicators = new ArrayList<HealthIndicator>();

        Map<String, HealthIndicator> stringToHealthIndicator = applicationContext.getBeansOfType(HealthIndicator.class);
        if (!CollectionUtils.isEmpty(stringToHealthIndicator)) {
            for (HealthIndicator healthIndicator : stringToHealthIndicator.values()) {
                if (!(healthIndicator instanceof SofaBootComponentHealthCheckInfo) &&
                    !(healthIndicator instanceof SpringContextHealthCheckInfo)) { //排除掉SofaBootComponentHealthCheckInfo 和 SpringContextHealthCheckInfo
                    healthIndicators.add(healthIndicator);
                }
            }
        }

        return healthIndicators;
    }

    public static List<SofaBootApplicationAfterHealthCheckCallback> getApplicationAfterHealthCheckCallbacks() {
        List<SofaBootApplicationAfterHealthCheckCallback> afterHealthCheckCallbacks = null;

        Map<String, SofaBootApplicationAfterHealthCheckCallback> stringToCallback = applicationContext
            .getBeansOfType(SofaBootApplicationAfterHealthCheckCallback.class);
        if (!CollectionUtils.isEmpty(stringToCallback)) {
            afterHealthCheckCallbacks = new ArrayList<SofaBootApplicationAfterHealthCheckCallback>(
                stringToCallback.values());
        } else {
            afterHealthCheckCallbacks = Collections.EMPTY_LIST;
        }

        return afterHealthCheckCallbacks;

    }

    public static List<SofaBootMiddlewareAfterHealthCheckCallback> getMiddlewareAfterHealthCheckCallbacks() {
        List<SofaBootMiddlewareAfterHealthCheckCallback> afterHealthCheckCallbacks = null;

        Map<String, SofaBootMiddlewareAfterHealthCheckCallback> stringToCallback = applicationContext
            .getBeansOfType(SofaBootMiddlewareAfterHealthCheckCallback.class);
        if (!CollectionUtils.isEmpty(stringToCallback)) {
            afterHealthCheckCallbacks = new ArrayList<SofaBootMiddlewareAfterHealthCheckCallback>(
                stringToCallback.values());
        } else {
            afterHealthCheckCallbacks = Collections.EMPTY_LIST;
        }

        return afterHealthCheckCallbacks;
    }

    public static void publishEvent(ApplicationEvent applicationEvent) {
        applicationContext.publishEvent(applicationEvent);
    }

    public static boolean springContextCheck() {
        boolean isHealth = false;

        if (applicationContext == null) {

            isHealth = false;

        } else if (applicationContext instanceof AbstractApplicationContext) {

            isHealth = ((AbstractApplicationContext) applicationContext).isActive();

        } else {

            isHealth = true;
        }

        return isHealth;
    }

}