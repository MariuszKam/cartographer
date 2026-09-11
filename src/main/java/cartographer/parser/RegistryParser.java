package cartographer.parser;

import cartographer.model.BlockInfo;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegistryParser {
    private static final Pattern TEXT_ENTRY = Pattern.compile("(\\d+)\\s*[:=]\\s*([a-zA-Z0-9_:\\-*/.]+)");

    public Map<Integer, BlockInfo> parse(byte[] payload) {
        Map<Integer, BlockInfo> blocks = new HashMap<>();
        if (payload == null || payload.length == 0) {
            return blocks;
        }

        String text = new String(payload, StandardCharsets.UTF_8);
        Matcher matcher = TEXT_ENTRY.matcher(text);
        while (matcher.find()) {
            int id = Integer.parseInt(matcher.group(1));
            blocks.put(id, new BlockInfo(id, matcher.group(2)));
        }
        return blocks;
    }
}
