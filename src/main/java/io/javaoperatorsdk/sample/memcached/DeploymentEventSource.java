package io.javaoperatorsdk.sample.memcached;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeploymentEventSource extends AbstractEventSource implements Watcher<Deployment> {
  private static final Logger log = LoggerFactory.getLogger(DeploymentEventSource.class);

  private final KubernetesClient client;

  private final Map<String, String> labels;

  public static DeploymentEventSource createAndRegisterWatch(
      KubernetesClient client, Map<String, String> labels) {
    DeploymentEventSource deploymentEventSource = new DeploymentEventSource(client, labels);
    deploymentEventSource.registerWatch();
    return deploymentEventSource;
  }

  private DeploymentEventSource(KubernetesClient client, Map<String, String> labels) {
    this.client = client;
    this.labels = labels;
  }

  private void registerWatch() {
    client.apps().deployments().inAnyNamespace().withLabels(labels).watch(this);
  }

  @Override
  public void eventReceived(Action action, Deployment deployment) {
    log.info(
        "Event received for action: {}, Deployment: {} (rr={})",
        action.name(),
        deployment.getMetadata().getName(),
        deployment.getStatus().getReadyReplicas());

    if (action == Action.ERROR) {
      log.warn(
          "Skipping {} event for custom resource uid: {}, version: {}",
          action,
          getUID(deployment),
          getVersion(deployment));
      return;
    }

    eventHandler.handleEvent(
        new DeploymentEvent(
            action,
            deployment,
            deployment.getMetadata().getOwnerReferences().get(0).getUid(),
            this));
  }

  @Override
  public void onClose(WatcherException e) {
    if (e == null) {
      return;
    }
    if (e.isHttpGone()) {
      log.warn("Received error for watch, will try to reconnect.", e);
      registerWatch();
    } else {
      // Note that this should not happen normally, since fabric8 client handles reconnect.
      // In case it tries to reconnect this method is not called.
      log.error("Unexpected error happened with watch. Will exit.", e);
      System.exit(1);
    }
  }
}
