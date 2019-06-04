# jetty-NameNotFoundException-remaining-name-env-reproduction

This repository provides a reproduction case for a problem that appears to arise when
two jetty servers are started in parallel and `EnvEntry` is used. Specifically, some
proportion of the time (on my local machine is about once every 300 restarts with this setup)
we see deployment fail with the following stack trace

```
2019-06-04 15:15:27.353:WARN:oejw.WebAppContext:pool-770-thread-1: Failed startup of context o.e.j.w.WebAppContext@2e9ef7ef{/s,file:///private/var/folders/9l/6pbmxlyj3h35v9ctkx28d37m0000gn/T/jetty-0.0.0.0-0-sample.war-_s-any-9846695075825088971.dir/webapp/,UNAVAILABLE}{/sample.war}
javax.naming.NameNotFoundException; remaining name 'env'
	at org.eclipse.jetty.jndi.NamingContext.lookup(NamingContext.java:484)
	at org.eclipse.jetty.jndi.NamingContext.lookup(NamingContext.java:571)
	at org.eclipse.jetty.jndi.NamingContext.lookup(NamingContext.java:587)
	at org.eclipse.jetty.jndi.java.javaRootURLContext.lookup(javaRootURLContext.java:108)
	at java.naming/javax.naming.InitialContext.lookup(InitialContext.java:409)
	at org.eclipse.jetty.plus.jndi.NamingEntry.bindToENC(NamingEntry.java:106)
	at org.eclipse.jetty.plus.webapp.EnvConfiguration.bindEnvEntries(EnvConfiguration.java:218)
	at org.eclipse.jetty.plus.webapp.EnvConfiguration.configure(EnvConfiguration.java:130)
	at org.eclipse.jetty.webapp.WebAppContext.configure(WebAppContext.java:517)
	at org.eclipse.jetty.webapp.WebAppContext.startContext(WebAppContext.java:1454)
	at org.eclipse.jetty.server.handler.ContextHandler.doStart(ContextHandler.java:852)
	at org.eclipse.jetty.servlet.ServletContextHandler.doStart(ServletContextHandler.java:278)
	at org.eclipse.jetty.webapp.WebAppContext.doStart(WebAppContext.java:545)
	at org.eclipse.jetty.util.component.AbstractLifeCycle.start(AbstractLifeCycle.java:68)
	at org.eclipse.jetty.util.component.ContainerLifeCycle.start(ContainerLifeCycle.java:167)
	at org.eclipse.jetty.util.component.ContainerLifeCycle.doStart(ContainerLifeCycle.java:119)
	at org.eclipse.jetty.server.handler.AbstractHandler.doStart(AbstractHandler.java:113)
	at org.eclipse.jetty.util.component.AbstractLifeCycle.start(AbstractLifeCycle.java:68)
	at org.eclipse.jetty.util.component.ContainerLifeCycle.start(ContainerLifeCycle.java:167)
	at org.eclipse.jetty.util.component.ContainerLifeCycle.doStart(ContainerLifeCycle.java:119)
	at org.eclipse.jetty.server.handler.AbstractHandler.doStart(AbstractHandler.java:113)
	at org.eclipse.jetty.util.component.AbstractLifeCycle.start(AbstractLifeCycle.java:68)
	at org.eclipse.jetty.util.component.ContainerLifeCycle.start(ContainerLifeCycle.java:167)
	at org.eclipse.jetty.server.Server.start(Server.java:418)
	at org.eclipse.jetty.util.component.ContainerLifeCycle.doStart(ContainerLifeCycle.java:110)
	at org.eclipse.jetty.server.handler.AbstractHandler.doStart(AbstractHandler.java:113)
	at org.eclipse.jetty.server.Server.doStart(Server.java:382)
	at org.eclipse.jetty.util.component.AbstractLifeCycle.start(AbstractLifeCycle.java:68)
	at Main$1.call(Main.java:50)
	at Main$1.call(Main.java:47)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	at java.base/java.lang.Thread.run(Thread.java:834)
```

This was initially reported to the jetty developers via the mailing list in https://www.eclipse.org/lists/jetty-dev/msg03323.html and this 
code provides a way to reproduce the problem with the simplest environment I could arrange.

To run the test after, cloning this repository...

1. cd to the root of the cloned repository
2. Run `mvn clean package`
3. Run `mvn exec:java -Dexec.mainClass="Main"`

The Main class will then create a single `EnvEntry` and then repeatedly start two jetty servers in parallel.

Each server is using a DeploymentManager/WebAppProvider to read from a context directory which contains a single context 
that tries to deploy a trivially small war file (taken from https://tomcat.apache.org/tomcat-7.0-doc/appdev/sample/).
After both servers have started we check that the app is available by making an HTTP request to it. If the HTTP requests
succeed we stop the servers and repeat, if an HTTP request fails we assume deployment failed and stop the process
(and from what I've seen the jetty log above is likely to show the deployment failed with the stack trace above).

In my testing the test above fails within about 300 restarts (which generally takes less than 1 minute).