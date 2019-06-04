import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.jetty.deploy.DeploymentManager;
import org.eclipse.jetty.deploy.providers.WebAppProvider;
import org.eclipse.jetty.plus.jndi.EnvEntry;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.io.File;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {

    private static OkHttpClient client = new OkHttpClient();

    public static void main(String[] args) throws Exception {
        new EnvEntry("some-env-entry", new ConcurrentHashMap<>());

        for (;;) { // Retry forever until deploying fails...
            Map<String, Server> servers = new HashMap<String, Server>();
            servers.put("one", configureServer(new File("src/main/resources/one")));
            servers.put("two", configureServer(new File("src/main/resources/two")));

            try {
                // We start the servers in parallel
                // The problem appears to go away if the servers are started serially
                ExecutorService es = Executors.newFixedThreadPool(servers.size());
                List<Future<Boolean>> serverStartFutures = new LinkedList<>();
                for (Map.Entry<String, Server> serverEntry : servers.entrySet()) {
                    final Server server = serverEntry.getValue();
                    serverStartFutures.add(es.submit(new Callable<Boolean>() {
                        @Override public Boolean call() throws Exception {
                            try {
                                server.start();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                            return true;
                        }
                    }));
                }

                // Get results from starting each server - After this we expect them to have started.
                for (Future<Boolean> f : serverStartFutures) {
                    if (!f.get()) {
                        throw new RuntimeException(
                            "Server did not successfully start - I haven't seen this happen so if you see this error some problem other than the one I'm trying to expose happened! Maybe try running it again?");
                    }
                }

                // Check that each server actually deployed the web app we expected - When deploying fails we'll get
                // an error from the HTTP request.
                //
                // No doubt there's a better way to check this, but I haven't looked into it and this check seems
                // to have been sufficient to date.
                for (Map.Entry<String, Server> serverEntry : servers.entrySet()) {
                    int port = ((ServerConnector) serverEntry.getValue().getConnectors()[0]).getLocalPort();

                    String url = "http://localhost:" + port + "/s/hello";

                    Request request = new Request.Builder()
                        .url(url)
                        .build();

                    try (Response response = client.newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            throw new RuntimeException("Failed to get " + url + " for server " + serverEntry.getKey() + "\n"
                                + " - If there's a deploy error above saying NameNotFoundException; remaining name 'env' then we reproduced the error we were trying to expose. If not, maybe try running it again?");
                        } else {
                            System.out.println("Got " + url + " for server " + serverEntry.getKey());
                        }
                    } catch (SocketTimeoutException e) {
                        System.err.println("Got SocketTimeoutException " + url + " for server " + serverEntry.getKey() + "\n"
                            + " - Ignoring it as it's not the failure mode we're aiming to replicate");
                        // Not sure what goes wrong in this case - Maybe the server is not always 100% ready?
                    }
                }

            } finally {
                // Stop each server before we try to start new ones.
                for (Map.Entry<String, Server> serverEntry : servers.entrySet()) {
                    serverEntry.getValue().stop();
                }
            }
        }
    }

    private static Server configureServer(File contextDirectory) {
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setName(contextDirectory.getName());

        Server server = new Server(threadPool);

        server.setAttribute("org.eclipse.jetty.webapp.configuration", new String[] {
            "org.eclipse.jetty.webapp.WebInfConfiguration", "org.eclipse.jetty.webapp.WebXmlConfiguration",
            "org.eclipse.jetty.webapp.MetaInfConfiguration",
            "org.eclipse.jetty.webapp.FragmentConfiguration",
            "org.eclipse.jetty.plus.webapp.EnvConfiguration",
            "org.eclipse.jetty.plus.webapp.PlusConfiguration",
            "org.eclipse.jetty.webapp.JettyWebXmlConfiguration" });

        HttpConfiguration httpConfig = new HttpConfiguration();
        HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory(httpConfig);

        ServerConnector httpConnector = new ServerConnector(server, httpConnectionFactory);
        httpConnector.setPort(0);

        server.addConnector(httpConnector);

        DeploymentManager deploymentManager = new DeploymentManager();

        WebAppProvider webAppProvider = new WebAppProvider();
        webAppProvider.setMonitoredDirName(contextDirectory.getAbsolutePath());
        webAppProvider.setScanInterval(1);
        webAppProvider.setExtractWars(true);

        deploymentManager.addAppProvider(webAppProvider);

        HandlerCollection serverHandlers = new HandlerCollection();

        ContextHandlerCollection handlerCollection = new ContextHandlerCollection();
        deploymentManager.setContexts(handlerCollection);
        serverHandlers.addHandler(handlerCollection);

        serverHandlers.addHandler(new DefaultHandler());

        server.addBean(deploymentManager);

        server.setHandler(serverHandlers);

        return server;
    }
}
