package org.jmmo.hessian.example.server;

import java.util.concurrent.CompletableFuture;

public interface HessianServiceInterfaceAsync {

    Integer synchronousCall();

    CompletableFuture<Integer> asynchronousCall();
}
