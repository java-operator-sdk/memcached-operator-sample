package io.javaoperatorsdk.sample.memcached;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerConfiguration()
public class MemcachedController
    implements Reconciler<Memcached>, EventSourceInitializer<Memcached> {

  private static Logger log = LoggerFactory.getLogger(MemcachedController.class);

  private final KubernetesClient client;

  public MemcachedController(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<Memcached> context) {
      InformerConfiguration<Deployment> deploymentInformerConfiguration = InformerConfiguration.from(Deployment.class, context)
              .withLabelSelector("app.kubernetes.io/managed-by=memcached-operator")
              .withSecondaryToPrimaryMapper(Mappers.fromOwnerReference())
              .build();

      return EventSourceInitializer.nameEventSources(new InformerEventSource<>(deploymentInformerConfiguration, context));
  }

  @Override
  public UpdateControl<Memcached> reconcile(Memcached memcached, Context context) {

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
      return UpdateControl.updateStatus(memcached);
    }

    return UpdateControl.noUpdate();
  }

  private Deployment createMemcachedDeployment(Memcached m) {
    return new DeploymentBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(m.getMetadata().getName())
                .withNamespace(m.getMetadata().getNamespace())
                .withLabels(Map.of("app.kubernetes.io/managed-by", "memcached-operator"))
                .withOwnerReferences(
                    new OwnerReferenceBuilder()
                        .withApiVersion(m.getApiVersion())
                        .withKind(m.getKind())
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
}
