package com.mydeco.jmx;

import com.sun.net.httpserver.*;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.logging.Logger;
import java.net.URI;
import java.net.MalformedURLException;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ReflectionException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;


class JMXClient {
    private static final Logger log = Logger.getLogger(JMXClient.class.getName());
    private String host = null;
    private String port = null;
    private MBeanServerConnection server = null;

    public JMXClient(String host, String port) throws MalformedURLException, IOException {
        this.host = host;
        this.port = port;
        this.server = this.getMBeanServer();
    }
    
    private MBeanServerConnection getMBeanServer() throws MalformedURLException, IOException {
        String uri = "service:jmx:rmi:///jndi/rmi://" + this.host + ":" + this.port + "/jmxrmi";
        JMXServiceURL target = new JMXServiceURL(uri);

        // Connect to target (assuming no security)
        JMXConnector connector = JMXConnectorFactory.connect(target);
        MBeanServerConnection server = connector.getMBeanServerConnection();
        return server;
    }

    public String getAttribute(String domain, String type, String attrname) {
        log.info("Get attribute - domain:"+domain+", type:"+type+", attrname: "+attrname);
        try {
            ObjectName oname = new ObjectName(domain+":type=" + type);
            return this.server.getAttribute(oname, attrname).toString();
        } 
        catch (InstanceNotFoundException e) {
            log.info("Not found an instance for domain:"+domain+", type:"+type+", attrname: "+attrname);
        }
        catch (Exception e) { 
            log.severe("Exception ocurred ==> " + e.toString());
        }
        return "";
    }
}

class JMX2HTTPHandler implements HttpHandler {

    private static final Logger log = 
        Logger.getLogger(JMX2HTTPHandler.class.getName());
    private JMXClient client = null;

    public JMX2HTTPHandler(JMXClient client) { 
        this.client = client;
    }

    private HashMap<String, String> parseQuery(String query) {
        HashMap<String,String> rqs = new HashMap<String,String>();
        for (String s: query.split("&")) {
            String [] kv = s.split("=", 2);
            rqs.put(kv[0], kv[1]);
        }
        return rqs;
    }
    
    public void handle(HttpExchange t) throws IOException {
        URI requri = t.getRequestURI();
        log.info("Request from " + t.getRemoteAddress().toString() + 
                 " to " + requri.toString());
        String query = requri.getQuery();
        HashMap<String, String> m = this.parseQuery(query);

        String response = this.client.getAttribute(m.get("domain"), m.get("type"), m.get("attribute"));

        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}

public class JMX2HTTPServer {
    private static final Logger log = 
        Logger.getLogger(JMX2HTTPServer.class.getName());
    private static final String URL_PREFIX = "/jmx";

    public static void main(String [] args) {
        if (args.length == 3) {
            int port = (new Integer(args[0])).intValue();
            String jmx_service_host = args[1];
            String jmx_service_port = args[2];
            try {
                log.info("Strating JMX2HTTPServer ...");
                log.info(" ==> Listening in port " + port);

                log.info("Creating JMX client to JMX2HTTPServer service ...");    
                JMXClient client = new JMXClient(jmx_service_host, jmx_service_port);
                log.info(" ==> connected to " + jmx_service_host + ":" + jmx_service_port);

                JMX2HTTPHandler handler = new JMX2HTTPHandler(client);

                HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
                server.createContext(URL_PREFIX, handler);
                server.setExecutor(null); // creates a default executor
                server.start();    
            }
            catch (Exception e) {
                log.severe("Exception ocurred: " + e.toString());
                log.severe(e.getStackTrace().toString());
            }
        }
        else {
            System.out.println("Usage: jmx2http PORT JMX_SERVICE_HOST JMX_SERVICE_PORT");
        }
    }
}
