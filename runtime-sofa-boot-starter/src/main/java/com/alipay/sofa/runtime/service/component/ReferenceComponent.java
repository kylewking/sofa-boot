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
package com.alipay.sofa.runtime.service.component;

import com.alipay.sofa.runtime.api.ServiceRuntimeException;
import com.alipay.sofa.runtime.api.client.ServiceClient;
import com.alipay.sofa.runtime.api.component.ComponentName;
import com.alipay.sofa.runtime.api.component.Property;
import com.alipay.sofa.runtime.model.ComponentType;
import com.alipay.sofa.runtime.model.InterfaceMode;
import com.alipay.sofa.runtime.service.binding.JvmBinding;
import com.alipay.sofa.runtime.service.component.impl.ServiceImpl;
import com.alipay.sofa.runtime.service.helper.ReferenceRegisterHelper;
import com.alipay.sofa.runtime.service.impl.BindingFactoryContainer;
import com.alipay.sofa.runtime.spi.binding.Binding;
import com.alipay.sofa.runtime.spi.binding.BindingAdapter;
import com.alipay.sofa.runtime.spi.binding.BindingAdapterFactory;
import com.alipay.sofa.runtime.spi.component.*;
import com.alipay.sofa.runtime.spi.constants.SofaConfigurationConstants;
import com.alipay.sofa.runtime.spi.health.HealthResult;
import com.alipay.sofa.runtime.spi.log.SofaLogger;
import com.alipay.sofa.runtime.spi.util.ComponentNameFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * reference component
 *
 * @author xuanbei 18/3/1
 */
public class ReferenceComponent extends AbstractComponent {

    public static final ComponentType REFERENCE_COMPONENT_TYPE = new ComponentType("reference");

    private BindingAdapterFactory     bindingAdapterFactory;
    private Reference                 reference;
    private CountDownLatch            latch                    = new CountDownLatch(1);

    public ReferenceComponent(Reference reference, Implementation implementation,
                              SofaRuntimeContext sofaRuntimeContext) {
        this.componentName = ComponentNameFactory.createComponentName(
            REFERENCE_COMPONENT_TYPE,
            reference.getInterfaceType(),
            reference.getUniqueId() + "#"
                + ReferenceRegisterHelper.generateBindingHashCode(reference));
        this.reference = reference;
        this.implementation = implementation;
        this.sofaRuntimeContext = sofaRuntimeContext;
        bindingAdapterFactory = BindingFactoryContainer.getBindingAdapterFactory();
    }

    @Override
    public ComponentType getType() {
        return REFERENCE_COMPONENT_TYPE;
    }

    @Override
    public Map<String, Property> getProperties() {
        return null;
    }

    /**
     * skip reference check or not
     *
     * @return true or false
     */
    private boolean skipJVMReferenceHealth() {
        String skipJVMReferenceHealth = sofaRuntimeContext.getAppConfiguration().getPropertyValue(
            SofaConfigurationConstants.SOFA_RUNTIME_SKIP_JVM_REFERENCE_HEALTH_CHECK, "true");

        return StringUtils.hasText(skipJVMReferenceHealth)
            && "true".equalsIgnoreCase(skipJVMReferenceHealth);
    }

    /**
     * get service target
     *
     * @return service target
     */
    private Object getServiceTarget() {
        Object serviceTarget = null;
        ComponentName componentName = ComponentNameFactory.createComponentName(
            ServiceComponent.SERVICE_COMPONENT_TYPE, reference.getInterfaceType(),
            reference.getUniqueId());
        ComponentInfo componentInfo = sofaRuntimeContext.getComponentManager().getComponentInfo(
            componentName);

        if (componentInfo != null) {
            serviceTarget = componentInfo.getImplementation().getTarget();
        }
        return serviceTarget;
    }

    @Override
    public HealthResult isHealthy() {
        if (!isActivated()) {
            return super.isHealthy();
        }

        HealthResult result = new HealthResult(componentName.getRawName());
        List<HealthResult> bindingHealth = new ArrayList<HealthResult>();

        JvmBinding jvmBinding = null;
        HealthResult jvmBindingHealthResult = null;

        if (reference.hasBinding()) {
            for (Binding binding : reference.getBindings()) {
                bindingHealth.add(binding.healthCheck());
                if (JvmBinding.JVM_BINDING_TYPE.equals(binding.getBindingType())) {
                    jvmBinding = (JvmBinding) binding;
                    jvmBindingHealthResult = bindingHealth.get(bindingHealth.size() - 1);
                }
            }
        }

        // check reference has a corresponding service
        if (!skipJVMReferenceHealth() && jvmBinding != null) {
            Object serviceTarget = getServiceTarget();
            if (serviceTarget == null && !jvmBinding.hasBackupProxy()) {
                jvmBindingHealthResult.setHealthy(false);
                jvmBindingHealthResult.setHealthReport("can not find corresponding jvm service");
            }
        }

        List<HealthResult> failedBindingHealth = new ArrayList<HealthResult>();

        for (HealthResult healthResult : bindingHealth) {
            if (healthResult != null && !healthResult.isHealthy()) {
                failedBindingHealth.add(healthResult);
            }
        }

        if (failedBindingHealth.size() == 0) {
            result.setHealthy(true);
        } else {
            String healthReport = "|";
            for (HealthResult healthResult : failedBindingHealth) {
                healthReport = healthReport + healthResult.getHealthName() + "#"
                    + healthResult.getHealthReport();
            }
            result.setHealthReport(healthReport.substring(1, healthReport.length()));
            result.setHealthy(false);
        }

        return result;
    }

    @Override
    public void activate() throws ServiceRuntimeException {
        if (reference.hasBinding()) {
            Binding candidate = null;
            Set<Binding> bindings = reference.getBindings();
            if (bindings.size() == 1) {
                candidate = bindings.iterator().next();
            } else if (bindings.size() > 1) {
                Object backupProxy = null;
                for (Binding binding : bindings) {
                    if ("jvm".equals(binding.getName())) {
                        candidate = binding;
                    } else {
                        backupProxy = createProxy(reference, binding);
                    }
                }
                if (candidate != null) {
                    ((JvmBinding) candidate).setBackupProxy(backupProxy);
                }
            }

            Object proxy = null;
            if (candidate != null) {
                proxy = createProxy(reference, candidate);
            }

            this.implementation = new DefaultImplementation();
            implementation.setTarget(proxy);
        }

        publishAsService(reference, implementation.getTarget());

        super.activate();
        latch.countDown();
    }

    @Override
    public void unregister() throws ServiceRuntimeException {
        super.unregister();
        if (reference.hasBinding()) {
            for (Binding binding : reference.getBindings()) {
                BindingAdapter<Binding> bindingAdapter = this.bindingAdapterFactory
                    .getBindingAdapter(binding.getBindingType());
                if (bindingAdapter == null) {
                    throw new ServiceRuntimeException("Can't find BindingAdapter of type "
                        + binding.getBindingType()
                        + " for reference " + reference + ".");
                }
                SofaLogger.info(" >>Un-in Binding [{0}] Begins - {1}.", binding.getBindingType()
                    , reference);
                try {
                    bindingAdapter.unInBinding(reference, binding, sofaRuntimeContext);
                } finally {
                    SofaLogger.info(" >>Un-in Binding [{0}] Ends - {1}.", binding.getBindingType()
                        , reference);
                }
            }
        }
        removeService();
    }

    @Override
    public void exception(Exception e) throws ServiceRuntimeException {
        super.exception(e);
        latch.countDown();
    }

    @Override
    public Implementation getImplementation() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new ServiceRuntimeException("Failed to get the implementation.", e);
        }

        if (e != null) {
            throw new ServiceRuntimeException(
                "Unable to get implementation of reference component, there's some error occurred when register this reference component.",
                e);
        }

        return super.getImplementation();
    }

    public Reference getReference() {
        return reference;
    }

    private Object createProxy(Reference reference, Binding binding) {
        BindingAdapter<Binding> bindingAdapter = bindingAdapterFactory.getBindingAdapter(binding
            .getBindingType());
        if (bindingAdapter == null) {
            throw new ServiceRuntimeException("Can't find BindingAdapter of type "
                + binding.getBindingType() + " for reference "
                + reference + ".");
        }
        SofaLogger.info(" >>In Binding [{0}] Begins - {1}.", binding.getBindingType(),
            reference);
        Object proxy = null;
        try {
            proxy = bindingAdapter.inBinding(reference, binding, sofaRuntimeContext);
        } finally {
            SofaLogger.info(" >>In Binding [{0}] Ends - {1}.", binding.getBindingType(), reference);
        }
        return proxy;
    }

    private void publishAsService(Reference reference, Object target) {
        if (!reference.jvmService()) {
            return;
        }

        Implementation serviceImplementation = new DefaultImplementation();
        serviceImplementation.setTarget(target);
        Service service = new ServiceImpl("", reference.getInterfaceType(), InterfaceMode.api,
            target);
        service.addBinding(new JvmBinding());
        ComponentInfo serviceComponent = new ServiceComponent(implementation, service,
            sofaRuntimeContext);
        sofaRuntimeContext.getComponentManager().register(serviceComponent);
    }

    private void removeService() {
        if (!reference.jvmService()) {
            return;
        }
        sofaRuntimeContext.getClientFactory().getClient(ServiceClient.class)
            .removeService(reference.getInterfaceType(), 0);

    }
}