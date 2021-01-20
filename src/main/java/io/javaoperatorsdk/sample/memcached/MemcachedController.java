package io.javaoperatorsdk.sample.memcached;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.*;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class MemcachedController implements ResourceController<Memcached> {

  private static Logger log = LoggerFactory.getLogger(MemcachedController.class);

  private final KubernetesClient client;

  public MemcachedController(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public void init(EventSourceManager eventSourceManager) {
    Map<String, String> deploymentLabel = new HashMap<>();
    deploymentLabel.put("app", "memcached");
    eventSourceManager.registerEventSource(
        "memcached-deployment",
        DeploymentEventSource.createAndRegisterWatch(client, deploymentLabel));
  }

  @Override
  public UpdateControl<Memcached> createOrUpdateResource(
      Memcached memcached, Context<Memcached> context) {
    Deployment deployment =
        client
            .apps()
            .deployments()
            .inNamespace(memcached.getMetadata().getNamespace())
            .withName(memcached.getMetadata().getName())
            .get();

    if (deployment == null) {
      Deployment newDeployment = createMemcachedDeployment(memcached);
      client.apps().deployments().create(newDeployment);
      return UpdateControl.noUpdate();
    }

    int currentReplicas = deployment.getSpec().getReplicas();
    int requiredReplicas = memcached.getSpec().getSize();
    if (currentReplicas != requiredReplicas) {
      deployment.getSpec().setReplicas(requiredReplicas);
      client.apps().deployments().createOrReplace(deployment);
      return UpdateControl.noUpdate();
    }

    List<Pod> pods =
        client
            .pods()
            .inNamespace(memcached.getMetadata().getNamespace())
            .withLabels(labelsForMemcached(memcached))
            .list()
            .getItems();

    List<String> podNames =
        pods.stream().map(p -> p.getMetadata().getName()).collect(Collectors.toList());

    if (memcached.getStatus() == null
        || !CollectionUtils.isEqualCollection(podNames, memcached.getStatus().getNodes())) {
      if (memcached.getStatus() == null) memcached.setStatus(new MemcachedStatus());
      memcached.getStatus().setNodes(podNames);
      return UpdateControl.updateStatusSubResource(memcached);
    }

    return UpdateControl.noUpdate();
  }

  private Deployment createMemcachedDeployment(Memcached m) {
    return new DeploymentBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(m.getMetadata().getName())
                .withNamespace(m.getMetadata().getNamespace())
                .withOwnerReferences(
                    new OwnerReferenceBuilder()
                        .withApiVersion("v1alpha1")
                        .withKind("Memcached")
                        .withName(m.getMetadata().getName())
                        .withUid(m.getMetadata().getUid())
                        .build())
                .build())
        .withSpec(
            new DeploymentSpecBuilder()
                .withReplicas(m.getSpec().getSize())
                .withSelector(
                    new LabelSelectorBuilder().withMatchLabels(labelsForMemcached(m)).build())
                .withTemplate(
                    new PodTemplateSpecBuilder()
                        .withMetadata(
                            new ObjectMetaBuilder().withLabels(labelsForMemcached(m)).build())
                        .withSpec(
                            new PodSpecBuilder()
                                .withContainers(
                                    new ContainerBuilder()
                                        .withImage("memcached:1.4.36-alpine")
                                        .withName("memcached")
                                        .withCommand("memcached", "-m=64", "-o", "modern", "-v")
                                        .withPorts(
                                            new ContainerPortBuilder()
                                                .withContainerPort(11211)
                                                .withName("memcached")
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build())
        .build();
  }

  private Map<String, String> labelsForMemcached(Memcached m) {
    Map<String, String> labels = new HashMap<>();
    labels.put("app", "memcached");
    labels.put("memcached_cr", m.getMetadata().getName());
    return labels;
  }

  @Override
  public DeleteControl deleteResource(Memcached memcached, Context<Memcached> context) {
    log.info("Deleting memcached object {}", memcached.getMetadata().getName());
    // nothing to do here...
    // framework takes care of deleting the memcached object
    // k8s takes care of deleting deployment and pods because of ownerreference set
    return DeleteControl.DEFAULT_DELETE;
  }
}
