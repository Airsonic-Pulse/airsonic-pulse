package org.airsonic.player;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards i18n completeness at the source level.
 *
 * <p>The build (maven-antrun-plugin, generate-resources) overwrites the packaged base
 * {@code ResourceBundle.properties} with {@code ResourceBundle_en.properties}, so the base
 * file has no independent existence at runtime: a key added only to the base file is
 * silently discarded from the WAR and en-locale users see {@code ??key_en??} placeholders
 * (the mechanism behind #297). The two source files must therefore stay in lockstep.
 *
 * <p>Both checks read the files under {@code src/main/resources} rather than the test
 * classpath: by test time the antrun copy has already replaced the base bundle under
 * {@code target/classes}, so a classpath-based parity check would pass vacuously.
 */
public class I18nCompletenessTest {

    private static final Path I18N_DIR = Paths.get("src/main/resources/org/airsonic/player/i18n");
    private static final Path TEMPLATES_DIR = Paths.get("src/main/resources/templates");

    /**
     * A message-key reference whose key is fully literal: {@code #{key}} or {@code #{key(args)}}.
     * Dynamic keys ({@code #{${...}}}, {@code #{prefix.__${...}__...}}) contain characters
     * outside the key character class and are deliberately not matched.
     */
    private static final Pattern LITERAL_KEY_REF = Pattern.compile("#\\{([A-Za-z0-9._-]+)[(}]");

    /**
     * A helpToolTip fragment insertion with a literal topic. The fragment derives
     * {@code helppopup.<topic>.title} and {@code helppopup.<topic>.text} from the topic.
     */
    private static final Pattern HELP_TOOLTIP_TOPIC = Pattern.compile("helpToolTip\\s*\\(\\(topic=([A-Za-z0-9_]+)\\)\\)");

    @Test
    public void baseAndEnBundlesHaveIdenticalKeySets() throws IOException {
        List<String> baseKeys = loadKeys(I18N_DIR.resolve("ResourceBundle.properties"));
        List<String> enKeys = loadKeys(I18N_DIR.resolve("ResourceBundle_en.properties"));

        List<String> baseDuplicates = duplicates(baseKeys);
        List<String> enDuplicates = duplicates(enKeys);
        assertTrue(baseDuplicates.isEmpty(), "Duplicate keys in ResourceBundle.properties: " + baseDuplicates);
        assertTrue(enDuplicates.isEmpty(), "Duplicate keys in ResourceBundle_en.properties: " + enDuplicates);

        TreeSet<String> missingFromEn = new TreeSet<>(baseKeys);
        missingFromEn.removeAll(enKeys);
        TreeSet<String> missingFromBase = new TreeSet<>(enKeys);
        missingFromBase.removeAll(baseKeys);

        if (!missingFromEn.isEmpty() || !missingFromBase.isEmpty()) {
            fail("Base and _en bundles have diverged; the antrun copy makes _en the packaged base bundle,"
                    + " so every key must exist in both files."
                    + "\nIn ResourceBundle.properties but missing from ResourceBundle_en.properties: " + missingFromEn
                    + "\nIn ResourceBundle_en.properties but missing from ResourceBundle.properties: " + missingFromBase);
        }
    }

    @Test
    public void templateReferencedKeysResolveInEnBundle() throws IOException {
        TreeSet<String> enKeys = new TreeSet<>(loadKeys(I18N_DIR.resolve("ResourceBundle_en.properties")));

        // key -> templates referencing it
        Map<String, TreeSet<String>> missing = new TreeMap<>();
        try (Stream<Path> templates = Files.walk(TEMPLATES_DIR)) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                String content = Files.readString(template, StandardCharsets.UTF_8);
                Matcher keyRef = LITERAL_KEY_REF.matcher(content);
                while (keyRef.find()) {
                    recordIfMissing(keyRef.group(1), template, enKeys, missing);
                }
                Matcher topic = HELP_TOOLTIP_TOPIC.matcher(content);
                while (topic.find()) {
                    recordIfMissing("helppopup." + topic.group(1) + ".title", template, enKeys, missing);
                    recordIfMissing("helppopup." + topic.group(1) + ".text", template, enKeys, missing);
                }
            }
        }

        assertTrue(missing.isEmpty(),
                "Template-referenced message keys missing from ResourceBundle_en.properties"
                        + " (dynamic #{${...}} / #{prefix.__${...}__} references are not covered): " + missing);
    }

    private static void recordIfMissing(String key, Path template, TreeSet<String> enKeys, Map<String, TreeSet<String>> missing) {
        if (!enKeys.contains(key)) {
            missing.computeIfAbsent(key, k -> new TreeSet<>()).add(TEMPLATES_DIR.relativize(template).toString());
        }
    }

    /**
     * Parses property keys line by line (unlike {@link java.util.Properties}, which hides
     * duplicates), honouring backslash line continuations.
     */
    private static List<String> loadKeys(Path bundle) throws IOException {
        assertTrue(Files.isRegularFile(bundle), "Bundle not found at " + bundle.toAbsolutePath()
                + "; this test expects the Surefire working directory to be the airsonic-main module root.");
        List<String> lines = Files.readAllLines(bundle, StandardCharsets.ISO_8859_1);
        List<String> keys = new ArrayList<>();
        boolean continuation = false;
        for (String line : lines) {
            if (continuation) {
                continuation = endsWithOddBackslashes(line);
                continue;
            }
            String stripped = line.strip();
            if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("!")) {
                continue;
            }
            continuation = endsWithOddBackslashes(line);
            int separator = indexOfSeparator(stripped);
            if (separator > 0) {
                keys.add(stripped.substring(0, separator).strip());
            }
        }
        return keys;
    }

    private static int indexOfSeparator(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '=' || c == ':') {
                return i;
            }
        }
        return -1;
    }

    private static boolean endsWithOddBackslashes(String line) {
        int count = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            count++;
        }
        return count % 2 == 1;
    }

    private static List<String> duplicates(List<String> keys) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String key : keys) {
            counts.merge(key, 1, Integer::sum);
        }
        return counts.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).toList();
    }
}
