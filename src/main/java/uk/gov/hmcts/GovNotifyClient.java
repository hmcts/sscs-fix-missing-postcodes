package uk.gov.hmcts;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import uk.gov.service.notify.LetterResponse;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

public class GovNotifyClient {
    private final NotificationClient notificationClient;

    public GovNotifyClient(String apiKey) {
        notificationClient = new NotificationClient(apiKey);
    }

    public Map<String, Map<String, String>> sendPrecompiledLetter(Map<String, File> caseRefToFileMap) {
        Map<String, String> successfullySentLetters = new HashMap<>();
        Map<String, String> failedLetters = new HashMap<>();

        caseRefToFileMap.entrySet()
                .stream()
                .forEach(caseRefToFileEntry -> {
                    try {
                        LetterResponse response = notificationClient.sendPrecompiledLetter(
                                caseRefToFileEntry.getKey(),
                                caseRefToFileEntry.getValue()
                        );
                        successfullySentLetters.
                                put(caseRefToFileEntry.getKey(), caseRefToFileEntry.getValue().getName());
                        System.out.println("Letter was successfully sent: " + response);
                    } catch (NotificationClientException e) {
                        failedLetters.put(caseRefToFileEntry.getKey(), caseRefToFileEntry.getValue().getName());
                        System.out.println("failed to send pdf letter for case {} " + caseRefToFileEntry.getKey() + e);
                    }
                });
        return Map.of("successful", successfullySentLetters, "failed", failedLetters);
    }

    public static void main(String[] args) {
        GovNotifyClient client = new GovNotifyClient(System.getenv("GOV_NOTIFY_API_KEY"));
        Map<String, File> caseRefToFileMap = new HashMap<>();

        var csv = new File(System.getProperty("user.home") + "/sscs-notify-letters.csv");
        try {
            var lines = java.nio.file.Files.readAllLines(csv.toPath());
            for (var line : lines) {
                var parts = line.split(",");
                caseRefToFileMap.put(parts[0], new File(parts[3]));
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }

        var result = client.sendPrecompiledLetter(caseRefToFileMap);
        System.out.println("Successfully sent letters: " + result.get("successful"));
        System.out.println("Failed to send letters: " + result.get("failed"));
    }
}
