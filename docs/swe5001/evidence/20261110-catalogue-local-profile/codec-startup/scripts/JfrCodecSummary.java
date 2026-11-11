import java.nio.file.*;
import java.time.*;
import java.util.*;
import jdk.jfr.consumer.*;

/** Local streaming phase diagnosis: aggregate symbols/durations only, no event bodies or peers. */
class JfrColdSummary {
    static class Phase {
        long samples, tls, starts, gc, compilationCount, compilationNs, allocationBytes;
        double tlsMs, gcMs, gcMaxMs;
        final Map<String, Long> leaves = new HashMap<>(), compilations = new HashMap<>(), allocations = new HashMap<>(), parks = new HashMap<>();
        final Map<String, Long> cpuStacks = new HashMap<>(), cpuCategories = new HashMap<>(), allocationStacks = new HashMap<>(), compilerLevels = new HashMap<>();
        final Map<String, Long> appFrames = new HashMap<>(), monitorStacks = new HashMap<>(), socketStacks = new HashMap<>(), allocationCategories = new HashMap<>(), threadCpuCounts = new HashMap<>();
        final Map<String, Long> compilationMethods = new HashMap<>(), socketReadCounts = new HashMap<>(), monitorCategories = new HashMap<>();
        final Map<String, Double> threadCpuSums = new HashMap<>(), threadCpuMax = new HashMap<>();
        Map<String, Object> result() {
            Map<String, Object> result = new TreeMap<>(Map.of("executionSamples", samples, "leafMethods", top(leaves),
                "compilationDurationNanosByClass", top(compilations), "allocationSampleWeightsBytes", top(allocations),
                "threadParkDurationNanos", top(parks), "threadStarts", starts,
                "tlsHandshakes", Map.of("count", tls, "eventDurationMs", tlsMs),
                "gc", Map.of("count", gc, "totalPauseMs", gcMs, "maxPauseMs", gcMaxMs)));
            result.put("compilation", Map.of("count", compilationCount, "totalEventDurationNanos", compilationNs, "byLevel", compilerLevels));
            result.put("cpuStacks", top(cpuStacks)); result.put("cpuInclusiveCategories", cpuCategories);
            result.put("allocationTotalWeightBytes", allocationBytes); result.put("allocationStacks", top(allocationStacks));
            result.put("cpuNearestApplicationFrame", top(appFrames)); result.put("monitorDurationNanosByStack", top(monitorStacks));
            result.put("socketReadDurationNanosByStack", top(socketStacks)); result.put("allocationInclusiveCategoryWeights", allocationCategories);
            result.put("threadCpuLoad", Map.of("sampleCountsByRole", threadCpuCounts, "sumUserPlusSystemByRole", threadCpuSums, "maxUserPlusSystemByRole", threadCpuMax));
            result.put("compilationDurationNanosByMethod", top(compilationMethods)); result.put("socketReadCounts", socketReadCounts); result.put("monitorInclusiveCategoryDurationNanos", monitorCategories);
            return result;
        }
    }
    static final Map<String,String> CATEGORIES = Map.ofEntries(
        Map.entry("logging", "ch.qos.logback|org.springframework.boot.json|org.springframework.boot.logging"), Map.entry("cryptoTLS", "crypto|sun.security.ssl"), Map.entry("springAnnotations", "org.springframework.core.annotation|org.springframework.core.ResolvableType"), Map.entry("mybatis", "org.apache.ibatis"), Map.entry("json", "jackson"), Map.entry("datasource", "com.zaxxer.hikari|org.postgresql"), Map.entry("observability", "micrometer"), Map.entry("application", "com.blocbuy"), Map.entry("environment", "org.springframework.core.env|org.springframework.boot.context.properties"),
        Map.entry("mybatisResultMapping", "org.apache.ibatis.executor.resultset.DefaultResultSetHandler"), Map.entry("postgresTimestamp", "org.postgresql.jdbc.TimestampUtils|org.postgresql.jdbc.PgResultSet.getTimestamp"), Map.entry("productImageParsing", "com.blocbuy.catalog.persistence.ProductConverter.parseImages"), Map.entry("classLoading", "java.lang.ClassLoader.loadClass|org.springframework.boot.loader"), Map.entry("secureRandom", "java.security.SecureRandom|sun.security.provider.NativePRNG"), Map.entry("validation", "org.hibernate.validator"));
    static void add(Phase p, RecordedEvent event) {
        String type = event.getEventType().getName();
        List<String> stack = event.getStackTrace() == null ? List.of() : event.getStackTrace().getFrames().stream()
            .map(f -> f.getMethod().getType().getName()+"."+f.getMethod().getName()).toList();
        String joined = String.join(" > ", stack);
        if (type.equals("jdk.ExecutionSample")) {
            p.samples++; if (!stack.isEmpty()) p.leaves.merge(stack.getFirst(), 1L, Long::sum);
            p.cpuStacks.merge(String.join(" > ", stack.stream().limit(16).toList()), 1L, Long::sum);
            CATEGORIES.forEach((category, terms) -> { if (Arrays.stream(terms.split("\\|")).anyMatch(joined::contains)) p.cpuCategories.merge(category, 1L, Long::sum); });
            stack.stream().filter(s -> s.startsWith("com.blocbuy.")).findFirst().ifPresent(s -> p.appFrames.merge(s, 1L, Long::sum));
        }
        if (type.equals("jdk.ThreadStart")) p.starts++;
        if (type.equals("jdk.TLSHandshake")) { p.tls++; p.tlsMs += event.getDuration().toNanos()/1e6; }
        if (type.equals("jdk.Compilation")) {
            p.compilationCount++; p.compilationNs += event.getDuration().toNanos();
            p.compilerLevels.merge(Integer.toString(event.getInt("compileLevel")), event.getDuration().toNanos(), Long::sum);
            p.compilations.merge(event.getValue("method") instanceof RecordedMethod m ? m.getType().getName() : "unknown", event.getDuration().toNanos(), Long::sum);
            p.compilationMethods.merge(event.getValue("method") instanceof RecordedMethod m ? m.getType().getName()+"."+m.getName() : "unknown", event.getDuration().toNanos(), Long::sum);
        }
        if (type.equals("jdk.ObjectAllocationSample")) {
            long weight = event.getLong("weight"); p.allocationBytes += weight;
            p.allocations.merge(event.getClass("objectClass").getName(), weight, Long::sum);
            p.allocationStacks.merge(String.join(" > ", stack.stream().limit(12).toList()), weight, Long::sum);
            CATEGORIES.forEach((category, terms) -> { if (Arrays.stream(terms.split("\\|")).anyMatch(joined::contains)) p.allocationCategories.merge(category, weight, Long::sum); });
        }
        if (type.equals("jdk.JavaMonitorEnter")) {
            p.monitorStacks.merge(String.join(" > ", stack.stream().limit(12).toList()), event.getDuration().toNanos(), Long::sum);
            CATEGORIES.forEach((category, terms) -> { if (Arrays.stream(terms.split("\\|")).anyMatch(joined::contains)) p.monitorCategories.merge(category, event.getDuration().toNanos(), Long::sum); });
        }
        if (type.equals("jdk.SocketRead")) {
            p.socketStacks.merge(String.join(" > ", stack.stream().limit(16).toList()), event.getDuration().toNanos(), Long::sum);
            p.socketReadCounts.merge(joined.contains("org.postgresql") ? "postgres" : "other", 1L, Long::sum);
        }
        if (type.equals("jdk.ThreadCPULoad")) {
            RecordedThread thread = event.getThread(); String name = thread == null ? "" : thread.getJavaName();
            String role = name.contains("Compiler") ? (name.startsWith("C2") ? "compilerC2" : "compilerC1") : name.contains("http-nio") ? "tomcat" : name.contains("lettuce") ? "lettuce" : name.contains("Hikari") ? "hikari" : name.contains("JFR") ? "jfr" : name.equals("main") ? "main" : "other";
            double load = event.getFloat("user") + event.getFloat("system");
            p.threadCpuCounts.merge(role, 1L, Long::sum); p.threadCpuSums.merge(role, load, Double::sum); p.threadCpuMax.merge(role, load, Math::max);
        }
        if (type.equals("jdk.ThreadPark")) p.parks.merge(String.join(" > ", stack.stream().limit(8).toList()), event.getDuration().toNanos(), Long::sum);
        if (type.equals("jdk.GarbageCollection")) { p.gc++; double ms = event.getDuration("sumOfPauses").toNanos()/1e6; p.gcMs += ms; p.gcMaxMs = Math.max(p.gcMaxMs, ms); }
    }
    static List<Map<String, Object>> top(Map<String, Long> values) {
        return values.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).limit(15)
            .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue())).toList();
    }
    static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\""+s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")+"\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof List<?> a) return "["+String.join(",", a.stream().map(JfrColdSummary::json).toList())+"]";
        if (value instanceof Map<?, ?> m) return "{"+String.join(",", m.entrySet().stream().map(e -> json(e.getKey().toString())+":"+json(e.getValue())).toList())+"}";
        throw new IllegalArgumentException("Unsupported JSON");
    }
    public static void main(String[] args) throws Exception {
        String prefix = args[0];
        if (!prefix.matches("codec-(base|candidate)-[ab]")) throw new IllegalArgumentException("Unexpected recording");
        Path root = Path.of("/results/matrix-v2");
        List<String[]> windows = Files.readAllLines(root.resolve(prefix+"-windows.txt")).stream().map(s -> s.split("\\|", -1)).toList();
        Map<String, Phase> phases = new TreeMap<>();
        try (var recording = new RecordingFile(root.resolve(prefix+".jfr"))) {
            while (recording.hasMoreEvents()) {
                var event = recording.readEvent();
                String window = "outsideWorkload";
                for (var row : windows) if (!event.getStartTime().isBefore(Instant.parse(row[1])) && !event.getStartTime().isAfter(Instant.parse(row[2]))) { window = row[0]; break; }
                add(phases.computeIfAbsent(window, ignored -> new Phase()), event);
                if (window.endsWith("-warmup")) {
                    Instant start = windows.stream().filter(r -> r[0].equals(prefix+"-warmup")).map(r -> Instant.parse(r[1])).findFirst().orElseThrow();
                    long second = Duration.between(start, event.getStartTime()).toSeconds();
                    String slice = second < 5 ? "cold-00to05" : second < 15 ? "cold-05to15" : second < 45 ? "cold-15to45" : "cold-45plus";
                    add(phases.computeIfAbsent(slice, ignored -> new Phase()), event);
                }
            }
        }
        Map<String, Object> result = new TreeMap<>();
        phases.forEach((name, phase) -> result.put(name, phase.result()));
        String out = json(Map.of("kind", "local-phase-JFR-diagnosis-not-causal-proof", "phases", result,
            "limits", "No peer identities or event bodies exported. Samples/weights are estimates; category counts overlap and TraceIdFilter-inclusive stacks are not filter self time. Execution samples exclude native compiler CPU. Event durations are attributed to their start bracket, not clipped, and overlap across threads; compiler event duration is not CPU time; parks include normal idle waits. ThreadCPULoad intervals are coarse, first emitted sample may cover pre-workload startup, raw sums are not CPU seconds or percentages. Workload brackets are controller timestamps, not request-start windows. Cold slices duplicate parent warmup events; do not sum parent and child phases."));
        Files.writeString(root.resolve(prefix+"-cold-jfr-analysis.json"), out);
        System.out.println(prefix+" phase diagnostics saved; "+phases.size()+" brackets, no event bodies exported.");
    }
}
