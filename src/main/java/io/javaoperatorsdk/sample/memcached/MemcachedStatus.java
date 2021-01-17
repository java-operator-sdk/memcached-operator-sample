package io.javaoperatorsdk.sample.memcached;

import java.util.ArrayList;
import java.util.List;

public class MemcachedStatus {

  private List<String> nodes;

  public List<String> getNodes() {
    if (nodes == null) {
      nodes = new ArrayList<>();
    }
    return nodes;
  }

  public void setNodes(List<String> nodes) {
    this.nodes = nodes;
  }
}
