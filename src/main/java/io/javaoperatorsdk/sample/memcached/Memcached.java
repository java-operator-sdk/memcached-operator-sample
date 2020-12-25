package io.javaoperatorsdk.sample.memcached;

import io.fabric8.kubernetes.client.CustomResource;

public class Memcached extends CustomResource {

    private MemcachedSpec spec;

    private MemcachedStatus status;

    public MemcachedSpec getSpec() {
        if (spec == null) {
            spec = new MemcachedSpec();
        }
        return spec;
    }

    public void setSpec(MemcachedSpec spec) {
        this.spec = spec;
    }

    public MemcachedStatus getStatus() {
        if (status == null) {
            status = new MemcachedStatus();
        }
        return status;
    }

    public void setStatus(MemcachedStatus status) {
        this.status = status;
    }
}
