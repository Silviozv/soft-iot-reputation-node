package reputation.node.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class WriteDataTest {

    // Teste da lista de credibilidade antes e depois do KMeans
    public static void writelistDebug(List<Float> nodeCredibility, List<Float> kMeansResult) {
        String FILE_PATH = "/opt/karaf/data/log/lists_credibility.log";
        DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (PrintWriter writer = new PrintWriter(new FileWriter(FILE_PATH, true))) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            writer.printf("%s: %s -> %s%n", timestamp, nodeCredibility.toString(), kMeansResult.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}