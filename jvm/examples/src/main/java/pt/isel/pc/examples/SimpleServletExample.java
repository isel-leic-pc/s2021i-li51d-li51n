package pt.isel.pc.examples;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SimpleServletExample {

    private static final Logger log = LoggerFactory.getLogger(SimpleServletExample.class);
    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        Server server = new Server(PORT);
        ServletHandler handler = new ServletHandler();
        TheServlet servlet = new TheServlet();

        handler.addServletWithMapping(new ServletHolder(servlet), "/*");
        log.info("registered {} on all paths", servlet);

        server.setHandler(handler);
        server.start();
        log.info("server started listening on port {}", PORT);

        log.info("Waiting for server to end");
        server.join();

        log.info("main is ending");
    }

    static class TheServlet extends HttpServlet {

        private String fieldRequestURI;

        @Override
        public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            log.info("doGet request: URI='{}', method='{}", request.getMethod(), request.getRequestURI());

            // private to the thread
            String localRequestURI = request.getRequestURI();

            // SHARED between all threads
            this.fieldRequestURI = request.getRequestURI();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.warn("Sleep interrupted, continuing");
            }

            String bodyString = String.format("Request processed on thread '%s', method='%s', URI='%s'",
                    Thread.currentThread().getName(),
                    request.getMethod(),
                    request.getRequestURI());
            byte[] bodyBytes = bodyString.getBytes(StandardCharsets.UTF_8);

            response.addHeader("Content-Type", "text/plain, charset=utf-8");
            response.addHeader("Content-Length", Integer.toString(bodyBytes.length));
            response.getOutputStream().write(bodyBytes);
        }
    }
}
