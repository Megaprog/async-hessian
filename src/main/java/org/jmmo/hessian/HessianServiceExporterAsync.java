package org.jmmo.hessian;

import com.caucho.hessian.io.*;
import org.apache.commons.logging.Log;
import org.springframework.remoting.caucho.HessianServiceExporter;
import org.springframework.util.CommonsLogWriter;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.util.NestedServletException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Objects;

public class HessianServiceExporterAsync extends HessianServiceExporter {

    private HessianSkeletonAsync skeletonAsync;
    private SerializerFactory serializerFactoryAsync = new SerializerFactory();
    private HessianRemoteResolver remoteResolverAsync;
    private Log debugLoggerAsync;

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!"POST".equals(request.getMethod())) {
            throw new HttpRequestMethodNotSupportedException(request.getMethod(),
                    new String[] {"POST"}, "HessianServiceExporter only supports POST requests");
        }

        response.setContentType(CONTENT_TYPE_HESSIAN);
        try {
            invokeAsync(request, response);
        }
        catch (Throwable ex) {
            throw new NestedServletException("Hessian skeleton invocation failed", ex);
        }
    }

    @Override
    public void prepare() {
        super.prepare();
        this.skeletonAsync = new HessianSkeletonAsync(getProxyForService(), getServiceInterface());
    }

    public void invokeAsync(HttpServletRequest request, HttpServletResponse response) throws Exception {
       Objects.requireNonNull(this.skeletonAsync, "Hessian exporter has not been initialized");

        ClassLoader originalClassLoader = overrideThreadContextClassLoader();
        try {
            InputStream isToUse = request.getInputStream();
            OutputStream osToUse = response.getOutputStream();

            if (this.debugLoggerAsync != null && this.debugLoggerAsync.isDebugEnabled()) {
                PrintWriter debugWriter = new PrintWriter(new CommonsLogWriter(this.debugLoggerAsync));
                @SuppressWarnings("resource")
                HessianDebugInputStream dis = new HessianDebugInputStream(isToUse, debugWriter);
                @SuppressWarnings("resource")
                HessianDebugOutputStream dos = new HessianDebugOutputStream(osToUse, debugWriter);
                dis.startTop2();
                dos.startTop2();
                isToUse = dis;
                osToUse = dos;
            }

            if (!isToUse.markSupported()) {
                isToUse = new BufferedInputStream(isToUse);
                isToUse.mark(1);
            }

            int code = isToUse.read();
            int major;
            int minor;

            AbstractHessianInput in;
            AbstractHessianOutput out;

            if (code == 'H') {
                // Hessian 2.0 stream
                major = isToUse.read();
                minor = isToUse.read();
                if (major != 0x02) {
                    throw new IOException("Version " + major + "." + minor + " is not understood");
                }
                in = new Hessian2Input(isToUse);
                out = new Hessian2Output(osToUse);
                in.readCall();
            }
            else if (code == 'C') {
                // Hessian 2.0 call... for some reason not handled in HessianServlet!
                isToUse.reset();
                in = new Hessian2Input(isToUse);
                out = new Hessian2Output(osToUse);
                in.readCall();
            }
            else if (code == 'c') {
                // Hessian 1.0 call
                major = isToUse.read();
                minor = isToUse.read();
                in = new HessianInput(isToUse);
                if (major >= 2) {
                    out = new Hessian2Output(osToUse);
                }
                else {
                    out = new HessianOutput(osToUse);
                }
            }
            else {
                throw new IOException("Expected 'H'/'C' (Hessian 2.0) or 'c' (Hessian 1.0) in hessian input at " + code);
            }

            if (this.serializerFactoryAsync != null) {
                in.setSerializerFactory(this.serializerFactoryAsync);
                out.setSerializerFactory(this.serializerFactoryAsync);
            }
            if (this.remoteResolverAsync != null) {
                in.setRemoteResolver(this.remoteResolverAsync);
            }

            final InputStream finalIsToUse = isToUse;
            final OutputStream finalOsToUse = osToUse;
            skeletonAsync.invokeAsync(getProxyForService(), in, out, () -> request.startAsync(request, response)).whenComplete((o, t) -> {
                try {
                    in.close();
                    finalIsToUse.close();
                } catch (IOException ex) {
                    // ignore
                }
                try {
                    out.close();
                    finalOsToUse.close();
                } catch (IOException ex) {
                    // ignore
                }
            });
        }
        finally {
            resetThreadContextClassLoader(originalClassLoader);
        }
    }

    @Override
    public void setSerializerFactory(SerializerFactory serializerFactory) {
        super.setSerializerFactory(serializerFactory);
        this.serializerFactoryAsync = serializerFactory;
    }

    @Override
    public void setRemoteResolver(HessianRemoteResolver remoteResolver) {
        super.setRemoteResolver(remoteResolver);
        this.remoteResolverAsync = remoteResolver;
    }

    @Override
    public void setDebug(boolean debug) {
        super.setDebug(debug);
        this.debugLoggerAsync = (debug ? logger : null);
    }
}
