package io.javaoperatorsdk.sample.memcached;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

import javax.inject.Inject;

@QuarkusMain
public class MemcachedOperator implements QuarkusApplication {

  @Inject KubernetesClient client;

  @Inject ConfigurationService configuration;

  @Inject MemcachedController controller;

  @Inject Operator operator;

  public static void main(String... args) {
    Quarkus.run(MemcachedOperator.class, args);
  }

  @Override
  public int run(String... args) throws Exception {
    final var config = configuration.getConfigurationFor(controller);
    System.out.println("CR class: " + config.getCustomResourceClass());
    System.out.println("Doneable class = " + config.getDoneableClass());
    Quarkus.waitForExit();
    return 0;
  }
}
