import java.nio.file.*;
import org.jacoco.core.tools.ExecFileLoader;

/** Local coverage diagnostic; emits only cache class IDs and aggregate probe counts. */
class ExecProbeReview {
    public static void main(String[] args) throws Exception {
        java.util.List<Path> files;
        try (var paths = Files.walk(Path.of("/build"))) {
            files = paths.filter(p -> p.getFileName().toString().endsWith(".exec")).toList();
        }
        for (Path file : files) {
            if (!Files.exists(file)) continue;
            var loader = new ExecFileLoader();
            loader.load(file.toFile());
            for (var data : loader.getExecutionDataStore().getContents()) {
                if (!data.getName().equals("com/blocbuy/groupbuy/gateway/RedisOpenDealOfferCache")) continue;
                long covered = 0;
                for (boolean probe : data.getProbes()) if (probe) covered++;
                System.out.println(Path.of("/build").relativize(file) + " classId=" + Long.toHexString(data.getId())
                    + " coveredProbes=" + covered + " totalProbes=" + data.getProbes().length);
            }
        }
    }
}
