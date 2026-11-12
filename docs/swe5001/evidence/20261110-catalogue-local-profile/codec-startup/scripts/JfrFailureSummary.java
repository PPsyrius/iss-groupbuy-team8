import java.nio.file.*;
import java.time.*;
import java.util.*;
import jdk.jfr.consumer.*;

/** Streaming, local-only JFR aggregation; does not export event bodies or environment. */
class JfrSummarize {
    static Map<String, Long> counts = new TreeMap<>();
    static Map<String, Long> inclusive = new HashMap<>(), leaves = new HashMap<>(), application = new HashMap<>();
    static Map<String, Long> threads = new HashMap<>(), allocations = new HashMap<>(), allocationApps = new HashMap<>();
    static Map<String, Long> parks = new HashMap<>(), sockets = new HashMap<>();
    static Map<String, Long> nativeLeaves = new HashMap<>();
    static List<String[]> windows;
    static Map<String, Map<String, Long>> phaseMethods = new TreeMap<>();
    static long samples, nativeSamples, gcCount, compilationCount;
    static double gcPauseMs, gcMaxPauseMs, compilationMs;
    static void add(Map<String, Long> map, String name, long value) { map.merge(name, value, Long::sum); }
    static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""; }
    static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(text);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) return "{" + String.join(",", map.entrySet().stream().map(e -> quote(e.getKey().toString()) + ":" + json(e.getValue())).toList()) + "}";
        if (value instanceof List<?> list) return "[" + String.join(",", list.stream().map(JfrSummarize::json).toList()) + "]";
        throw new IllegalArgumentException("Unsupported JSON value");
    }
    static List<Map<String, Object>> top(Map<String, Long> map, int maximum) {
        return map.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).limit(maximum)
            .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue())).toList();
    }
    static String phase(Instant at) {
        for (String[] row : windows) if (!at.isBefore(Instant.parse(row[1])) && !at.isAfter(Instant.parse(row[2]))) return row[0];
        return "outsideWorkload";
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of("/results/matrix-v2");
        String prefix = args.length == 1 ? args[0] : "failure";
        if (!prefix.matches("[a-z0-9-]+")) throw new IllegalArgumentException("Invalid recording prefix");
        windows = Files.readAllLines(root.resolve(prefix + "-windows.txt")).stream().map(line -> line.split("\\|", -1)).toList();
        try (RecordingFile recording = new RecordingFile(root.resolve(prefix + ".jfr"))) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                String type = event.getEventType().getName();
                add(counts, type, 1);
                String window = phase(event.getStartTime());
                if (window.equals("outsideWorkload")) continue;
                List<RecordedFrame> frames = event.getStackTrace() == null ? List.of() : event.getStackTrace().getFrames();
                List<String> names = frames.stream().filter(f -> f.getMethod() != null).map(f -> f.getMethod().getType().getName() + "." + f.getMethod().getName()).toList();
                String app = names.stream().filter(n -> n.startsWith("com.blocbuy.")).findFirst().orElse("no application frame");
                if (type.equals("jdk.NativeMethodSample")) {
                    nativeSamples++;
                    if (!names.isEmpty()) add(nativeLeaves, names.getFirst(), 1);
                }
                if (type.equals("jdk.ExecutionSample")) {
                    samples++;
                    RecordedThread thread = event.getThread("sampledThread");
                    add(threads, thread == null ? "unknown" : thread.getJavaName(), 1);
                    if (!names.isEmpty()) add(leaves, names.getFirst(), 1);
                    for (String name : new HashSet<>(names)) {
                        add(inclusive, name, 1);
                        add(phaseMethods.computeIfAbsent(window, ignored -> new HashMap<>()), name, 1);
                        if (name.startsWith("com.blocbuy.")) add(application, name, 1);
                    }
                }
                if (type.equals("jdk.ObjectAllocationSample")) {
                    long weight = event.getLong("weight");
                    add(allocations, event.getClass("objectClass").getName(), weight);
                    add(allocationApps, app, weight);
                }
                if (type.equals("jdk.ThreadPark")) add(parks, String.join(" > ", names.stream().limit(10).toList()), event.getDuration().toNanos());
                if (type.equals("jdk.SocketRead")) add(sockets, String.join(" > ", names.stream().limit(10).toList()), event.getDuration().toNanos());
                if (type.equals("jdk.GarbageCollection")) {
                    gcCount++;
                    double pause = event.getDuration("sumOfPauses").toNanos() / 1e6;
                    gcPauseMs += pause;
                    gcMaxPauseMs = Math.max(gcMaxPauseMs, pause);
                }
                if (type.equals("jdk.Compilation")) {
                    compilationCount++;
                    compilationMs += event.getDuration().toNanos() / 1e6;
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evidenceKind", "local-diagnostic-not-AWS-acceptance");
        result.put("cpuSamplesWithinWorkload", samples);
        result.put("nativeSamplesWithinWorkload", nativeSamples);
        result.put("nativeLeafMethodsNotCpuPercentages", top(nativeLeaves, 20));
        result.put("eventCountsWholeRecording", counts);
        result.put("leafMethods", top(leaves, 35));
        result.put("inclusiveMethods", top(inclusive, 60));
        result.put("applicationMethods", top(application, 35));
        result.put("threads", top(threads, 25));
        result.put("allocationWeightsBytes", top(allocations, 25));
        result.put("allocationApplicationFrames", top(allocationApps, 25));
        result.put("parkCumulativeNanoseconds", top(parks, 10));
        result.put("socketReadCumulativeNanoseconds", top(sockets, 10));
        result.put("gc", Map.of("count", gcCount, "totalPauseMs", gcPauseMs, "maxPauseMs", gcMaxPauseMs));
        result.put("compilation", Map.of("count", compilationCount, "cumulativeEventDurationMs", compilationMs));
        Map<String, Object> phases = new TreeMap<>();
        phaseMethods.forEach((name, methods) -> phases.put(name, top(methods, 30)));
        result.put("phaseInclusiveMethods", phases);
        result.put("limits", "Java execution stacks and allocation weights are estimates. Native samples are separate: epoll/accept/poll stacks include idle waits, not measured CPU consumption. Inclusive counts overlap; parks/socket durations sum across threads and include normal idle waits. Workload windows bracket request completion, not exact executor start.");
        Files.writeString(root.resolve(prefix + "-jfr-analysis.json"), json(result));
        System.out.println("JFR CPU samples within workload: " + samples);
        System.out.println("Top application methods: " + json(top(application, 12)));
        System.out.println("GC: " + json(result.get("gc")));
        System.out.println("Top leaves: " + json(top(leaves, 12)));
    }
}
