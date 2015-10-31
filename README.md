# Asynchronous Hessian

The approach to calling [Hessian](http://hessian.caucho.com/) service implementation asynchronously by servlet 3.0 spec.

## How to get it?

You can use it as a maven dependency:

```xml
<dependency>
    <groupId>org.jmmo</groupId>
    <artifactId>async-hessian</artifactId>
    <version>1.1</version>
</dependency>
```

Or download the latest build at:
    https://github.com/megaprog/async-hessian/releases

## How to use it?

Create client interface:

```java
public interface HessianServiceInterface {

    Integer synchronousCall();

    Integer asynchronousCall();
}
```

Create server interface with same method signatures as in client one but asynchronous methods must return CompletableFuture:

```java
public interface HessianServiceInterfaceAsync {

    Integer synchronousCall();

    CompletableFuture<Integer> asynchronousCall();
}
```

Implement server asynchronous interface:

```java
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
```

Export server interface:

```xml
<bean name="/HessianService" class="org.jmmo.hessian.HessianServiceExporterAsync">
    <property name="service" ref="hessianServiceAsync"/>
    <property name="serviceInterface" value="org.jmmo.hessian.example.server.HessianServiceInterfaceAsync"/>
</bean>

<bean id="hessianServiceAsync" class="org.jmmo.hessian.example.server.HessianServiceInterfaceAsyncImpl"/>
```

Define proxy service bean in client:

```xml
<bean id="hessianService" class="org.springframework.remoting.caucho.HessianProxyFactoryBean">
    <property name="serviceInterface" value="org.jmmo.hessian.example.client.HessianServiceInterface"/>
    <property name="serviceUrl" value="${hessianServiceUrl}"/>
</bean>
```

See more at client-server example:
    https://github.com/Megaprog/async-hessian/tree/master/example
    