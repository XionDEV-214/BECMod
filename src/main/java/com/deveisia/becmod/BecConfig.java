package com.deveisia.becmod;

import net.minecraftforge.fml.loading.FMLPaths;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BecConfig {
    public static final String FILE_NAME = BecMod.MOD_ID + "-common.toml";

    private static final String KEY_WHITELIST = "whitelist";
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_MAX_FRAMES = "maxAnimationFrames";

    private static Set<String> whitelist = Collections.emptySet();
    private static int temperature = 1;
    private static int maxAnimationFrames = 256;

    private BecConfig() {}

    public static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        CommentedFileConfig config = CommentedFileConfig.builder(path)
                .sync()
                .preserveInsertionOrder()
                .build();
        try {
            config.load();

            if (!config.contains(KEY_WHITELIST)) {
                config.set(KEY_WHITELIST, new ArrayList<String>());
            }
            config.setComment(KEY_WHITELIST, String.join("\n",
                    " Materials that get a Bose-Einstein Condensated fluid variant.",
                    " Format: \"modid:material\" or just \"material\" (modid defaults to gtceu).",
                    " Only plain liquids are accepted. Materials that only have molten / gas / plasma",
                    " fluids are skipped with a warning.",
                    " Must be the same on server and client.",
                    " Example: whitelist = [\"water\", \"gtceu:distilled_water\", \"oil\"]"));

            if (!config.contains(KEY_TEMPERATURE)) {
                config.set(KEY_TEMPERATURE, 1);
            }
            config.setComment(KEY_TEMPERATURE,
                    " Temperature (Kelvin) of the condensate fluids. Must be >= 0.");

            if (!config.contains(KEY_MAX_FRAMES)) {
                config.set(KEY_MAX_FRAMES, 256);
            }
            config.setComment(KEY_MAX_FRAMES, String.join("\n",
                    " [client] Upper bound for frames in a generated condensate texture.",
                    " The base liquid and overlay animations are combined over their common period;",
                    " if that would exceed this many frames the overlay's period is used instead."));

            config.save();

            whitelist = parseWhitelist(config.get(KEY_WHITELIST));
            temperature = Math.max(0, ((Number) config.get(KEY_TEMPERATURE)).intValue());
            maxAnimationFrames = Math.max(1, ((Number) config.get(KEY_MAX_FRAMES)).intValue());

            BecMod.LOGGER.info("Loaded {} with {} whitelisted material(s): {}", FILE_NAME, whitelist.size(),
                    whitelist);
        } catch (Exception e) {
            BecMod.LOGGER.error("Failed to read {}; no condensate fluids will be registered", path, e);
            whitelist = Collections.emptySet();
        } finally {
            config.close();
        }
    }

    private static Set<String> parseWhitelist(Object raw) {
        Set<String> out = new LinkedHashSet<>();
        if (!(raw instanceof List<?> list)) {
            BecMod.LOGGER.warn("'{}' in {} is not a list, ignoring it", KEY_WHITELIST, FILE_NAME);
            return out;
        }
        for (Object o : list) {
            if (o == null) continue;
            String s = o.toString().trim().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) continue;
            if (s.indexOf(':') < 0) s = "gtceu:" + s;
            out.add(s);
        }
        return Collections.unmodifiableSet(out);
    }

    public static Set<String> whitelist() {
        return whitelist;
    }

    public static int temperature() {
        return temperature;
    }

    public static int maxAnimationFrames() {
        return maxAnimationFrames;
    }
}
