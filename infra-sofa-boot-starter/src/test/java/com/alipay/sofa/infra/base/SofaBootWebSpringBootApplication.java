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
package com.alipay.sofa.infra.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ImportResource;

/**
 * SofaBootWebSpringBootApplication
 * <p/>
 * Created by yangguanchao on 16/7/22.
 */
@ImportResource({ "classpath*:META-INF/sofaboot-web-test/*.xml" })
@org.springframework.boot.autoconfigure.SpringBootApplication
public class SofaBootWebSpringBootApplication {

    // 在Java类中创建 logger 实例
    private static final Logger logger = LoggerFactory.getLogger(SofaBootWebSpringBootApplication.class);

    public static void main(String[] args) throws Exception {
        SpringApplication springApplication = new SpringApplication(SofaBootWebSpringBootApplication.class);
        ApplicationContext applicationContext = springApplication.run(args);
    }
}
