package io.javaoperatorsdk.sample.memcached;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.Watcher;
import io.javaoperatorsdk.operator.processing.event.AbstractEvent;

public class DeploymentEvent extends AbstractEvent {

  private final Watcher.Action action;
  private final Deployment deployment;

  public DeploymentEvent(
      Watcher.Action action,
      Deployment resource,
      String ownerUid,
      DeploymentEventSource deploymentEventSource) {
    super(ownerUid, deploymentEventSource);
    this.action = action;
    this.deployment = resource;
  }

  public Watcher.Action getAction() {
    return action;
  }

  public String resourceUid() {
    return getDeployment().getMetadata().getUid();
  }

  @Override
  public String toString() {
    return "CustomResourceEvent{"
        + "action="
        + action
        + ", resource=[ name="
        + getDeployment().getMetadata().getName()
        + ", kind="
        + getDeployment().getKind()
        + ", apiVersion="
        + getDeployment().getApiVersion()
        + " ,resourceVersion="
        + getDeployment().getMetadata().getResourceVersion()
        + ", markedForDeletion: "
        + (getDeployment().getMetadata().getDeletionTimestamp() != null
            && !getDeployment().getMetadata().getDeletionTimestamp().isEmpty())
        + " ]"
        + '}';
  }

  public Deployment getDeployment() {
    return deployment;
  }
}
