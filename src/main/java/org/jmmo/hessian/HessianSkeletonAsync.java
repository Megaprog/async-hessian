package org.jmmo.hessian;

import com.caucho.hessian.io.AbstractHessianInput;
import com.caucho.hessian.io.AbstractHessianOutput;
import com.caucho.hessian.server.HessianSkeleton;
import com.caucho.services.server.ServiceContext;

import javax.servlet.AsyncContext;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HessianSkeletonAsync extends HessianSkeleton {
    private static final Logger log = Logger.getLogger(HessianSkeletonAsync.class.getName());

    public HessianSkeletonAsync(Object service, Class<?> apiClass) {
        super(service, apiClass);
    }

    public CompletableFuture<?> invokeAsync(Object service, AbstractHessianInput in, AbstractHessianOutput out, Supplier<AsyncContext> asyncContextSupplier) throws Exception {
        ServiceContext context = ServiceContext.getContext();

        // backward compatibility for some frameworks that don't read
        // the call type first
        in.skipOptionalCall();

        // Hessian 1.0 backward compatibility
        String header;
        while ((header = in.readHeader()) != null) {
            Object value = in.readObject();

            context.addHeader(header, value);
        }

        String methodName = in.readMethod();
        int argLength = in.readMethodArgLength();

        Method method;

        method = getMethod(methodName + "__" + argLength);

        if (method == null)
            method = getMethod(methodName);

        if (method == null) {
            if ("_hessian_getAttribute".equals(methodName)) {
                String attrName = in.readString();
                in.completeCall();

                String value = null;

                if ("java.api.class".equals(attrName)) {
                    value = getAPIClassName();
                } else if ("java.home.class".equals(attrName)) {
                    value = getHomeClassName();
                } else if ("java.object.class".equals(attrName)) {
                    value = getObjectClassName();
                }

                out.writeReply(value);
                out.close();
                return CompletableFuture.completedFuture(null);
            } else {
                out.writeFault("NoSuchMethodException", escapeMessage("The service has no method named: " + in.getMethod()), null);
                out.close();
                return CompletableFuture.completedFuture(null);
            }
        }

        Class<?>[] args = method.getParameterTypes();

        if (argLength != args.length && argLength >= 0) {
            out.writeFault("NoSuchMethod", escapeMessage("method " + method + " argument length mismatch, received length=" + argLength), null);
            out.close();
            return CompletableFuture.completedFuture(null);
        }

        Object[] values = new Object[args.length];

        for (int i = 0; i < args.length; i++) {
            // XXX: needs Marshal object
            values[i] = in.readObject(args[i]);
        }

        if (method.getReturnType().isAssignableFrom(CompletableFuture.class)) {
            final AsyncContext asyncContext = asyncContextSupplier.get();
            final CompletableFuture<?> completableFuture = (CompletableFuture<?>) method.invoke(service, values);
            return completableFuture.whenComplete((o, t) -> {
                if (t != null) {
                    final Throwable e = (t instanceof InvocationTargetException) ? ((InvocationTargetException) t).getTargetException() : t;

                    log.log(Level.FINE, e, () -> this + " " + e.toString());

                    try {
                        out.writeFault("ServiceException", escapeMessage(e.getMessage()), e);
                        out.close();
                    } catch (IOException ex) {
                        log.log(Level.INFO, "", ex);
                    }
                } else {
                    try {
                        in.completeCall();
                        out.writeReply(o);
                        out.close();
                    } catch (Exception ex) {
                        log.log(Level.INFO, "", ex);
                    }
                }

                asyncContext.complete();
            });
        } else {
            final Object result;
            try {
                result = method.invoke(service, values);
            } catch (Exception e) {
                final Throwable e1 = (e instanceof InvocationTargetException) ? ((InvocationTargetException) e).getTargetException() : e;

                log.log(Level.FINE, e, () -> this + " " + e.toString());

                out.writeFault("ServiceException", escapeMessage(e1.getMessage()), e1);
                out.close();
                return CompletableFuture.completedFuture(null);
            }

            // The complete call needs to be after the invoke to handle a
            // trailing InputStream
            in.completeCall();

            out.writeReply(result);

            out.close();

            return CompletableFuture.completedFuture(null);
        }
    }

    private String escapeMessage(String msg)
    {
        if (msg == null)
            return null;

        StringBuilder sb = new StringBuilder();

        int length = msg.length();
        for (int i = 0; i < length; i++) {
            char ch = msg.charAt(i);

            switch (ch) {
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case 0x0:
                    sb.append("&#00;");
                    break;
                case '&':
                    sb.append("&amp;");
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }

        return sb.toString();
    }
}
