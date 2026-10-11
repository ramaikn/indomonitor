package id.indomonitor.media.analysis;

import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SimilarityService {
    private static final Pattern SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> STOP = Set.of("dan","yang","di","ke","dari","untuk","pada","ini","itu","dengan","the","of","a");

    public Set<String> tokens(String title) {
        Set<String> tokens = new HashSet<>(Arrays.asList(SEPARATOR.split(title.toLowerCase(Locale.ROOT))));
        tokens.removeIf(word -> word.length() < 3 || STOP.contains(word));
        return tokens;
    }

    public double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        Set<String> common = new HashSet<>(left); common.retainAll(right);
        return (double) common.size() / (left.size() + right.size() - common.size());
    }

    public Map<String, Object> analyze(List<Map<String, Object>> articles) {
        List<Set<String>> words = articles.stream().map(item -> tokens((String) item.get("title"))).toList();
        Map<String, List<Integer>> inverted = new HashMap<>();
        Set<Long> checked = new HashSet<>();
        List<Map<String, Object>> matches = new ArrayList<>();
        for (int i = 0; i < articles.size(); i++) {
            var right = articles.get(i);
            for (String token : words.get(i)) {
                var candidates = inverted.computeIfAbsent(token, unused -> new ArrayList<>());
                // Kata yang muncul terlalu luas tidak dipakai membentuk pasangan kandidat.
                if (candidates.size() < 100) for (int j : candidates) {
                    long key = (long) j * 501 + i;
                    if (!checked.add(key)) continue;
                    var left = articles.get(j);
                    if (left.get("domain").equals(right.get("domain"))) continue;
                    long hours = Math.abs(Duration.between(Instant.parse((String) left.get("first_seen_at")),
                        Instant.parse((String) right.get("first_seen_at"))).toHours());
                    if (hours > 24 || Math.min(words.get(j).size(), words.get(i).size()) < 4) continue;
                    double similarity = jaccard(words.get(j), words.get(i));
                    if (similarity >= 0.65) matches.add(Map.of("left", left, "right", right, "similarity", similarity,
                        "reason", "Judul memiliki token bersama dalam jendela 24 jam. Perlu pemeriksaan sumber."));
                }
                candidates.add(i);
            }
        }
        matches.sort(Comparator.comparingDouble(item -> -((Number) item.get("similarity")).doubleValue()));
        return Map.of("items", matches.stream().limit(50).toList(), "candidateCount", articles.size(),
            "pairCount", matches.size(), "method", "Jaccard token judul v1; threshold 0.65; minimal 4 token; lintas domain; 24 jam",
            "limitation", "Maksimal 500 judul terbaru. Kesamaan judul dapat berasal dari sindikasi atau peristiwa sama, bukan bukti koordinasi.");
    }
}
