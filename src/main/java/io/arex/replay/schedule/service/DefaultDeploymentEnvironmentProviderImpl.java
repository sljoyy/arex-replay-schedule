package io.arex.replay.schedule.service;

import io.arex.replay.schedule.model.AppServiceDescriptor;
import io.arex.replay.schedule.model.deploy.DeploymentEnvironmentProvider;
import io.arex.replay.schedule.model.deploy.DeploymentImage;
import io.arex.replay.schedule.model.deploy.DeploymentVersion;
import io.arex.replay.schedule.model.deploy.ServiceInstance;
import io.arex.replay.schedule.model.deploy.ServiceInstanceOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

/**
 * @author jmo
 * @since 2021/9/18
 */
@Slf4j
@Component
public final class DefaultDeploymentEnvironmentProviderImpl implements DeploymentEnvironmentProvider {
    @Override
    public DeploymentVersion getVersion(String appId, String env) {
        DeploymentVersion deploymentVersion = new DeploymentVersion();
        deploymentVersion.setImageId("0");
        DeploymentImage deploymentImage = new DeploymentImage();
        deploymentImage.setName("Unknown");
        deploymentImage.setCreator("Unknown author");
        deploymentImage.setNote("Unknown commit message");
        deploymentVersion.setImage(deploymentImage);
        return deploymentVersion;
    }

    @Override
    public List<ServiceInstance> getActiveInstanceList(AppServiceDescriptor serviceDescriptor, String env) {
        ServiceInstance serviceInstance = parse(env);
        if (serviceInstance != null) {
            return Collections.singletonList(parse(env));
        }
        return Collections.emptyList();
    }

    private ServiceInstance parse(String env) {
        try {
            URI uri = new URI(env);
            if (StringUtils.isEmpty(uri.getHost())) {
                LOGGER.error("pared empty host from '{}'", env);
                return null;
            }
            ServiceInstance instance = new ServiceInstance();
            instance.setProtocol(uri.getScheme());
            instance.setPort(uri.getPort());
            instance.setIp(uri.getHost());
            ServiceInstanceOperation operation = new ServiceInstanceOperation();
            operation.setName(uri.getPath());
            instance.setOperationList(Collections.singletonList(operation));
            instance.setUrl(env);
            return instance;
        } catch (URISyntaxException e) {
            LOGGER.error("parse active instance error : {} , {}", e.getMessage(), env, e);
        }
        return null;
    }

    @Override
    public ServiceInstance getActiveInstance(AppServiceDescriptor serviceDescriptor, String ip) {
        return parse(ip);
    }
}
