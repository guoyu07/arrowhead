package eu.arrowhead.core.authorization;

import eu.arrowhead.common.DatabaseManager;
import eu.arrowhead.common.Utility;
import eu.arrowhead.common.database.ArrowheadService;
import eu.arrowhead.common.database.ArrowheadSystem;
import eu.arrowhead.common.database.ServiceRegistryEntry;
import eu.arrowhead.common.exception.AuthenticationException;
import eu.arrowhead.common.security.SecurityUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collections;
import java.util.Properties;
import javax.net.ssl.SSLContext;
import javax.ws.rs.core.UriBuilder;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

class AuthorizationMain {

  static PrivateKey privateKey = null;
  private static HttpServer server = null;
  private static HttpServer secureServer = null;
  private static Logger log = Logger.getLogger(AuthorizationMain.class.getName());
  private static Properties prop;
  private static final String BASE_URI = getProp().getProperty("base_uri", "http://0.0.0.0:8444/");
  private static final String BASE_URI_SECURED = getProp().getProperty("base_uri_secured", "https://0.0.0.0:8445/");

  public static void main(String[] args) throws IOException {
    System.out.println("Working directory: " + System.getProperty("user.dir"));

    PropertyConfigurator.configure("config" + File.separator + "log4j.properties");
    KeyStore keyStore = SecurityUtils.loadKeyStore(getProp().getProperty("keystore"), getProp().getProperty("keystorepass"));
    privateKey = SecurityUtils.getPrivateKey(keyStore, getProp().getProperty("keystorepass"));

    boolean daemon = false;
    boolean serverModeSet = false;
    argLoop:
    for (int i = 0; i < args.length; ++i) {
      if (args[i].equals("-d")) {
        daemon = true;
        System.out.println("Starting server as daemon!");
      } else if (args[i].equals("-m")) {
        serverModeSet = true;
        ++i;
        switch (args[i]) {
          case "insecure":
            server = startServer();
            useSRService(false, true);
            break argLoop;
          case "secure":
            secureServer = startSecureServer();
            useSRService(true, true);
            break argLoop;
          case "both":
            server = startServer();
            secureServer = startSecureServer();
            useSRService(false, true);
            useSRService(true, true);
            break argLoop;
          default:
            log.fatal("Unknown server mode: " + args[i]);
            throw new AssertionError("Unknown server mode: " + args[i]);
        }
      }
    }
    if (!serverModeSet) {
      server = startServer();
      useSRService(false, true);
    }

    //This is here to initialize the database connection before the REST resources are initiated
    DatabaseManager dm = DatabaseManager.getInstance();
    if (daemon) {
      System.out.println("In daemon mode, process will terminate for TERM signal...");
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        System.out.println("Received TERM signal, shutting down...");
        shutdown();
      }));
    } else {
      System.out.println("Type \"stop\" to shutdown Authorization Server(s)...");
      BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
      String input = "";
      while (!input.equals("stop")) {
        input = br.readLine();
      }
      br.close();
      shutdown();
    }
  }

  private static HttpServer startServer() throws IOException {
    log.info("Starting server at: " + BASE_URI);
    System.out.println("Starting insecure server at: " + BASE_URI);

    final ResourceConfig config = new ResourceConfig();
    config.registerClasses(AuthorizationResource.class);
    config.packages("eu.arrowhead.common");

    URI uri = UriBuilder.fromUri(BASE_URI).build();
    final HttpServer server = GrizzlyHttpServerFactory.createHttpServer(uri, config);
    server.getServerConfiguration().setAllowPayloadForUndefinedHttpMethods(true);
    server.start();
    return server;
  }

  private static HttpServer startSecureServer() throws IOException {
    log.info("Starting server at: " + BASE_URI_SECURED);
    System.out.println("Starting secure server at: " + BASE_URI_SECURED);

    final ResourceConfig config = new ResourceConfig();
    config.registerClasses(AccessControlFilter.class, AuthorizationResource.class);
    config.packages("eu.arrowhead.common");

    String keystorePath = getProp().getProperty("keystore");
    String keystorePass = getProp().getProperty("keystorepass");
    String keyPass = getProp().getProperty("keypass");
    String truststorePath = getProp().getProperty("truststore");
    String truststorePass = getProp().getProperty("truststorepass");

    SSLContextConfigurator sslCon = new SSLContextConfigurator();
    sslCon.setKeyStoreFile(keystorePath);
    sslCon.setKeyStorePass(keystorePass);
    sslCon.setKeyPass(keyPass);
    sslCon.setTrustStoreFile(truststorePath);
    sslCon.setTrustStorePass(truststorePass);
    if (!sslCon.validateConfiguration(true)) {
      log.fatal("SSL Context is not valid, check the certificate files or app.properties!");
      throw new AuthenticationException("SSL Context is not valid, check the certificate files or app.properties!");
    }

    SSLContext sslContext = sslCon.createSSLContext();
    Utility.setSSLContext(sslContext);

    KeyStore keyStore = SecurityUtils.loadKeyStore(keystorePath, keystorePass);
    X509Certificate serverCert = SecurityUtils.getFirstCertFromKeyStore(keyStore);
    System.out.println("Server PublicKey Base64: " + Base64.getEncoder().encodeToString(serverCert.getPublicKey().getEncoded()));
    String serverCN = SecurityUtils.getCertCNFromSubject(serverCert.getSubjectDN().getName());
    if (!SecurityUtils.isCommonNameArrowheadValid(serverCN)) {
      log.fatal("Server CN is not compliant with the Arrowhead cert structure, since it does not have 6 parts.");
      throw new AuthenticationException(
          "Server CN ( " + serverCN + ") is not compliant with the Arrowhead cert structure, since it does not have 6 parts.");
    }
    log.info("Certificate of the secure server: " + serverCN);
    config.property("server_common_name", serverCN);

    URI uri = UriBuilder.fromUri(BASE_URI_SECURED).build();
    final HttpServer server = GrizzlyHttpServerFactory
        .createHttpServer(uri, config, true, new SSLEngineConfigurator(sslCon).setClientMode(false).setNeedClientAuth(true));
    server.getServerConfiguration().setAllowPayloadForUndefinedHttpMethods(true);
    server.start();
    return server;
  }

  private static void useSRService(boolean isSecure, boolean registering) {
    URI uri;
    ArrowheadService authControlService;
    ArrowheadService tokenGenerationService;
    if (isSecure) {
      uri = UriBuilder.fromUri(BASE_URI_SECURED).build();
      authControlService = new ArrowheadService("coreservices", "SecureAuthorizationControl", Collections.singletonList("JSON"), null);
      tokenGenerationService = new ArrowheadService("coreservices", "SecureTokenGeneration", Collections.singletonList("JSON"), null);
    } else {
      uri = UriBuilder.fromUri(BASE_URI).build();
      authControlService = new ArrowheadService("coreservices", "InsecureAuthorizationControl", Collections.singletonList("JSON"), null);
      tokenGenerationService = new ArrowheadService("coreservices", "InsecureTokenGeneration", Collections.singletonList("JSON"), null);
    }

    //Preparing the payloads
    ArrowheadSystem authSystem = new ArrowheadSystem("coresystems", "authorization", uri.getHost(), uri.getPort(), null);
    ServiceRegistryEntry authControlEntry = new ServiceRegistryEntry(authControlService, authSystem, "authorization");
    ServiceRegistryEntry tokenGenEntry = new ServiceRegistryEntry(tokenGenerationService, authSystem, "authorization/token");

    String baseUri = Utility.getServiceRegistryUri();
    if (registering) {
      try {
        Utility.sendRequest(UriBuilder.fromUri(baseUri).path("register").build().toString(), "POST", authControlEntry);
      } catch (RuntimeException e) {
        if (e.getMessage().contains("DuplicateEntryException")) {
          Utility.sendRequest(UriBuilder.fromUri(baseUri).path("remove").build().toString(), "PUT", authControlEntry);
          Utility.sendRequest(UriBuilder.fromUri(baseUri).path("register").build().toString(), "POST", authControlEntry);
        } else {
          System.out.println("Authorization control service registration failed.");
        }
      }
      try {
        Utility.sendRequest(UriBuilder.fromUri(baseUri).path("register").build().toString(), "POST", tokenGenEntry);
      } catch (RuntimeException e) {
        if (e.getMessage().contains("DuplicateEntryException")) {
          Utility.sendRequest(UriBuilder.fromUri(baseUri).path("remove").build().toString(), "PUT", tokenGenEntry);
          Utility.sendRequest(UriBuilder.fromUri(baseUri).path("register").build().toString(), "POST", tokenGenEntry);
        } else {
          System.out.println("Token generation service registration failed.");
        }
      }
    } else {
      Utility.sendRequest(UriBuilder.fromUri(baseUri).path("remove").build().toString(), "PUT", authControlEntry);
      Utility.sendRequest(UriBuilder.fromUri(baseUri).path("remove").build().toString(), "PUT", tokenGenEntry);
    }
  }

  private static void shutdown() {
    if (server != null) {
      log.info("Stopping server at: " + BASE_URI);
      server.shutdownNow();
      useSRService(false, false);
    }
    if (secureServer != null) {
      log.info("Stopping server at: " + BASE_URI_SECURED);
      secureServer.shutdownNow();
      useSRService(true, false);
    }
    System.out.println("Authorization Server(s) stopped");
  }

  static synchronized Properties getProp() {
    try {
      if (prop == null) {
        prop = new Properties();
        File file = new File("config" + File.separator + "app.properties");
        FileInputStream inputStream = new FileInputStream(file);
        prop.load(inputStream);
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }

    return prop;
  }

}
