package org.jmmo.hessian.example.server;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class HessianServiceInterfaceAsyncImpl implements HessianServiceInterfaceAsync {
    private static final Logger log = Logger.getLogger(HessianServiceInterfaceAsyncImpl.class.getName());

    @Override
    public Integer synchronousCall() {
        log.info(() -> "Calling from http processing thread");
        return 1;
    }

    @Override
    public CompletableFuture<Integer> asynchronousCall() {
        return CompletableFuture.supplyAsync(() -> {
            log.info(() -> "Calling from ForkJoinPool thread");
            return 2;
        });
    }
}
