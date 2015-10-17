package org.jmmo.hessian.example.client;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Launcher implements Runnable {

    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("classpath:org/jmmo/hessian/example/client/client.xml");
        applicationContext.getBean(Runnable.class).run();
    }

    private HessianServiceInterface hessianService;

    public HessianServiceInterface getHessianService() {
        return hessianService;
    }

    public void setHessianService(HessianServiceInterface hessianService) {
        this.hessianService = hessianService;
    }

    @Override
    public void run() {
        System.out.println("synchronous method calling result is " + getHessianService().synchronousCall());
        System.out.println("asynchronous method calling result is " + getHessianService().asynchronousCall());
    }
}
