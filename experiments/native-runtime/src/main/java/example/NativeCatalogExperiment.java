package example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.executors.RunnableTaskBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** One localhost experiment, not an executor, authorization framework or general validator. */
public final class NativeCatalogExperiment {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String CUSTOMER = "customer-example";
  private static final String SENTINEL = "aapi-harmless-nonexistent-sentinel";
  private static final List<Map<String,Object>> results = new ArrayList<>();
  private static String selected = "all";
  private static final String STARTED_AT = java.time.Instant.now().toString();

  static Map<String,Object> map(Object... pairs) {
    Map<String,Object> out = new LinkedHashMap<>();
    for (int i=0; i<pairs.length; i+=2) out.put((String)pairs[i], pairs[i+1]);
    return out;
  }
  static String hash(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
  static void require(boolean ok, String message) {
    if (!ok) throw new IllegalStateException(message);
  }
  static void reply(HttpExchange exchange, int status, byte[] bytes, String type) throws IOException {
    exchange.getResponseHeaders().set("Content-Type", type);
    exchange.sendResponseHeaders(status, bytes.length);
    try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
    exchange.close();
  }
  static void replyJson(HttpExchange exchange, int status, Object body) throws IOException {
    reply(exchange, status, JSON.writeValueAsBytes(body), "application/json");
  }

  static final class Catalog implements AutoCloseable {
    final HttpServer server;
    final Map<String,byte[]> bytes = new TreeMap<>();
    final Map<String,String> hashes = new TreeMap<>();
    final List<Map<String,Object>> fetches = Collections.synchronizedList(new ArrayList<>());
    Catalog(Path root) throws Exception {
      try (var files = Files.walk(root)) {
        for (Path p : files.filter(Files::isRegularFile).toList()) {
          String key = "/catalog/" + root.relativize(p).toString().replace('\\','/');
          byte[] content = Files.readAllBytes(p);
          bytes.put(key, content); hashes.put(key, hash(content));
        }
      }
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/catalog/", e -> {
        String path = e.getRequestURI().getPath();
        byte[] content = bytes.get(path);
        int status = content == null ? 404 : 200;
        fetches.add(map("path",path,"status",status,"sha256",hashes.get(path)));
        reply(e,status,content == null ? "No such catalog version".getBytes(StandardCharsets.UTF_8) : content,"application/yaml");
      });
      server.start();
    }
    String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/catalog"; }
    public void close() { server.stop(0); }
  }

  static final class Business implements AutoCloseable {
    final HttpServer server;
    final String scenario;
    final java.util.concurrent.atomic.AtomicInteger captureHits = new java.util.concurrent.atomic.AtomicInteger();
    boolean eligible = true;
    final Map<String,String> billingOwners = new HashMap<>();
    final Map<String,String> serviceOwners = new HashMap<>();
    final List<Map<String,Object>> calls = Collections.synchronizedList(new ArrayList<>());
    final List<String> effects = Collections.synchronizedList(new ArrayList<>());
    Business(String scenario) throws IOException {
      this.scenario = scenario;
      billingOwners.put("billing-other","customer-other");
      server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
      server.createContext("/customers/", this::handle);
      server.createContext("/capture/unapproved", e -> {
        captureHits.incrementAndGet();
        replyJson(e,200,map("captured",true));
      });
      server.start();
    }
    String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    void handle(HttpExchange e) throws IOException {
      String[] path = e.getRequestURI().getPath().split("/");
      String customer = path.length == 4 ? path[2] : "";
      String part = path.length == 4 ? path[3] : "";
      String operation = switch(part) {
        case "billing" -> "configureBilling";
        case "service" -> "activateService";
        case "reporting" -> "enableReporting";
        default -> "unknown";
      };
      JsonNode body = JSON.readTree(e.getRequestBody());
      String error = null;
      if (!e.getRequestMethod().equals("PUT")) error = "method-not-allowed";
      else if (!customer.equals(CUSTOMER)) error = "unknown-customer";
      else if (!eligible) error = "customer-ineligible";
      else if (operation.equals("activateService") &&
          !customer.equals(billingOwners.get(body.path("billingRecordId").asText()))) error = "billing-owner-mismatch";
      else if (operation.equals("enableReporting") &&
          !customer.equals(serviceOwners.get(body.path("serviceId").asText()))) error = "service-owner-mismatch";
      else if (operation.equals("configureBilling") && !body.path("cycle").asText().equals("monthly")) error = "unsupported-cycle";
      else if (operation.equals("activateService") && !body.path("package").asText().equals("standard")) error = "unsupported-package";
      else if (operation.equals("enableReporting") && !body.path("enabled").asBoolean()) error = "reporting-not-requested";
      else if (operation.equals("unknown")) error = "unknown-operation";
      if (error != null) {
        calls.add(map("operation",operation,"body",body,"status",409,"effectApplied",false,"reason",error));
        replyJson(e,409,map("error",error));
        return;
      }
      effects.add(operation);
      Object response;
      if (operation.equals("configureBilling")) {
        billingOwners.put("billing-example",customer);
        response = map("billingRecordId",scenario.equals("wrong-owner") ? "billing-other" : "billing-example");
        if (scenario.equals("eligibility-drift")) eligible = false;
      } else if (operation.equals("activateService")) {
        serviceOwners.put("service-example",customer);
        response = map("serviceId","service-example");
      } else response = map("reportingEnabled",true);
      calls.add(map("operation",operation,"body",body,"status",200,"effectApplied",true));
      replyJson(e,200,response);
    }
    public void close() { server.stop(0); }
  }

  static Object functionTask(String name, String version) {
    return map("call",name+":"+version+"@local","with",map("customerId","${ $workflow.input.customerId }"));
  }
  static Object shellTask() {
    return map("run",map("shell",map("command",SENTINEL),"return","code"));
  }
  static Object containerTask() {
    return map("run",map("container",map("image","aapi-never-pull.invalid/sentinel:never")));
  }
  static String errors(Throwable failure) {
    List<String> chain = new ArrayList<>();
    for (Throwable t=failure; t!=null && chain.size()<8; t=t.getCause())
      chain.add(t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage()));
    return String.join(" | ",chain);
  }

  static void run(Catalog catalog, String id, String scenario, Object task,
      boolean expectSuccess, List<String> expectedCalls, List<String> expectedEffects,
      String requiredError, String classification) throws Exception {
    if (!selected.equals("all") && !selected.equals(id)) return;
    int firstFetch = catalog.fetches.size();
    Map<String,Object> entry = map("case",id,"classification",classification,
        "expected",map("success",expectSuccess,"calls",expectedCalls,"effects",expectedEffects,"errorContains",requiredError));
    try (Business business = new Business(scenario);
         WorkflowApplication app = WorkflowApplication.builder().disableLifeCycleCEPublishing().disableStatusChangePublishing().build()) {
      require(app.allowedCommands().isEmpty(),"native shell allowlist is unexpectedly nonempty");
      List<String> providers = app.serviceLoadedClasses(RunnableTaskBuilder.class).stream()
          .map(v -> v.getClass().getName()).sorted().toList();
      require(providers.stream().noneMatch(v -> v.toLowerCase(Locale.ROOT).contains("container")),
          "container provider is present: refuse to run container probe");
      entry.put("nativeAllowedCommands",app.allowedCommands());
      entry.put("runnableProviders",providers);
      Map<String,Object> document = map(
          "document",map("dsl","1.0.1","namespace","example","name",id,"version","1.0.0"),
          "use",map("catalogs",map("local",map("endpoint",catalog.endpoint()))),
          "do",List.of(map("selected",task)));
      boolean succeeded = false; String error = ""; Object output = null; String phase = "parse-validation";
      try {
        var workflow = WorkflowReader.readWorkflowFromString(JSON.writeValueAsString(document),WorkflowFormat.JSON);
        phase = "definition";
        var definition = app.workflowDefinition(workflow);
        phase = "instance";
        var instance = definition.instance(map("customerId",CUSTOMER,"businessBase",business.endpoint()));
        phase = "start";
        var pending = instance.start();
        phase = "await";
        WorkflowModel out = pending.get(15,TimeUnit.SECONDS);
        output = out.asJavaObject(); succeeded = true; phase = "complete";
      } catch (Exception e) { error = errors(e); }
      List<String> observedCalls = business.calls.stream().map(v -> (String)v.get("operation")).toList();
      boolean pass = succeeded == expectSuccess && observedCalls.equals(expectedCalls)
          && business.effects.equals(expectedEffects) && (requiredError.isEmpty() || error.contains(requiredError));
      if (expectSuccess) {
        JsonNode out = JSON.valueToTree(output);
        if (id.equals("unapproved-endpoint")) pass &= out.path("captured").asBoolean() && business.captureHits.get()==1;
        else pass &= id.equals("with-reporting") ? out.path("reportingEnabled").asBoolean() : out.path("serviceId").asText().equals("service-example");
      }
      if (id.equals("wrong-owner") || id.equals("eligibility-drift")) {
        String reason = id.equals("wrong-owner") ? "billing-owner-mismatch" : "customer-ineligible";
        entry.put("expectedGuardReason",reason);
        pass &= !business.calls.isEmpty() && reason.equals(business.calls.get(business.calls.size()-1).get("reason"));
      }
      if (expectSuccess && !id.equals("unapproved-endpoint")) {
        Set<String> fetched = new HashSet<>();
        for (var f : catalog.fetches.subList(firstFetch,catalog.fetches.size())) if (f.get("status").equals(200)) fetched.add((String)f.get("path"));
        pass &= fetched.contains("/catalog/main/functions/"+id+"/1.0.0/function.yaml");
        pass &= fetched.contains("/catalog/main/functions/configure-billing/1.0.0/function.yaml");
        pass &= fetched.contains("/catalog/main/functions/activate-service/1.0.0/function.yaml");
        if (id.equals("with-reporting")) pass &= fetched.contains("/catalog/main/functions/enable-reporting/1.0.0/function.yaml");
      }
      if (id.equals("nested-shell")) pass &= catalog.fetches.subList(firstFetch,catalog.fetches.size()).stream().anyMatch(v -> v.get("path").equals("/catalog/main/functions/blocked-shell/1.0.0/function.yaml") && v.get("status").equals(200));
      if (id.equals("unknown-version")) pass &= catalog.fetches.subList(firstFetch,catalog.fetches.size()).stream().anyMatch(v -> v.get("status").equals(404));
      entry.put("actual",map("success",succeeded,"phase",phase,"output",output,"error",error,
          "calls",List.copyOf(business.calls),"effects",List.copyOf(business.effects),"unapprovedEndpointCaptureHits",business.captureHits.get()));
      entry.put("catalogFetches",List.copyOf(catalog.fetches.subList(firstFetch,catalog.fetches.size())));
      entry.put("passed",pass);
      results.add(entry);
      System.out.println((pass ? "PASS " : "FAIL ") + id);
    }
  }

  public static void main(String[] args) throws Exception {
    require(args.length == 2 || args.length == 3,"usage: NativeCatalogExperiment CATALOG_DIR RESULTS_JSON [CASE]");
    if (args.length == 3) selected=args[2];
    try (Catalog catalog = new Catalog(Path.of(args[0]))) {
      run(catalog,"with-reporting","normal",functionTask("with-reporting","1.0.0"),true,
          List.of("configureBilling","activateService","enableReporting"),
          List.of("configureBilling","activateService","enableReporting"),"","native catalog/runtime execution");
      run(catalog,"without-reporting","normal",functionTask("without-reporting","1.0.0"),true,
          List.of("configureBilling","activateService"),List.of("configureBilling","activateService"),"","native catalog/runtime execution");
      run(catalog,"wrong-owner","wrong-owner",functionTask("with-reporting","1.0.0"),false,
          List.of("configureBilling","activateService"),List.of("configureBilling"),"","mock application ownership guard");
      run(catalog,"eligibility-drift","eligibility-drift",functionTask("with-reporting","1.0.0"),false,
          List.of("configureBilling","activateService"),List.of("configureBilling"),"","mock application fresh-state guard");
      run(catalog,"unapproved-endpoint","normal",functionTask("unapproved-endpoint","1.0.0"),true,List.of(),List.of(),"","observed native default: endpoint not denied; owned localhost capture only");
      run(catalog,"top-shell","normal",shellTask(),false,List.of(),List.of(),"not allowed","native empty command allowlist");
      run(catalog,"top-container","normal",containerTask(),false,List.of(),List.of(),"No runnable found","capability omitted: no container module");
      run(catalog,"nested-shell","normal",functionTask("blocked-shell","1.0.0"),false,List.of(),List.of(),"not allowed","native command policy inside catalog function");
      run(catalog,"unknown-version","normal",functionTask("with-reporting","9.9.9"),false,List.of(),List.of(),"FileNotFoundException","native catalog lookup refusal");
      Map<String,Object> report = map("runtime","io.serverlessworkflow:7.35.4.Final",
          "startedAtUtc",STARTED_AT,"completedAtUtc",java.time.Instant.now().toString(),
          "javaVersion",System.getProperty("java.version"),"catalogDigests",catalog.hashes,
          "cases",results,"limitations",List.of(
              "Localhost mock execution only; no real business, cloud, LLM or human approval ceremony.",
              "Ownership and eligibility are mock application guards, not a native runtime authorization profile.",
              "Container omission is not a sandbox for a runtime where that module is enabled.",
              "The native default called one owned localhost endpoint outside the fictional application allowlist; this is not an external-network or universal policy test.",
              "Catalog server serves frozen fixture bytes; production catalog trust and mutable-version policy are not implemented."));
      Path result = Path.of(args[1]);
      Files.createDirectories(result.toAbsolutePath().getParent());
      JSON.writerWithDefaultPrettyPrinter().writeValue(result.toFile(),report);
    }
    require(!results.isEmpty(),"no case matched");
    if (results.stream().anyMatch(v -> !Boolean.TRUE.equals(v.get("passed"))))
      throw new AssertionError("One or more native experiment expectations failed; inspect results JSON.");
  }
}
