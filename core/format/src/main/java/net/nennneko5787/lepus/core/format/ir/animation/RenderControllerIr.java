package net.nennneko5787.lepus.core.format.ir.animation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.value.Provenance;

/** The texture-array subset of one resource-pack render controller. */
@SpecImpl("SC-170#attachable/render_controllers")
public record RenderControllerIr(String name, Map<String, List<String>> textureArrays,
        List<String> textureExpressions, Provenance provenance) {

    public RenderControllerIr {
        Map<String, List<String>> copied = new LinkedHashMap<>();
        textureArrays.forEach((key, value) -> copied.put(key, List.copyOf(value)));
        textureArrays = Collections.unmodifiableMap(copied);
        textureExpressions = List.copyOf(textureExpressions);
    }

    /** Resolves the first supported {@code Array.name[query.variant]} expression. */
    public Optional<List<String>> variantTextures() {
        for (String expression : textureExpressions) {
            int open = expression.indexOf('[');
            if (open <= 0 || !expression.substring(open)
                    .matches("\\[\\s*query\\.variant\\s*\\]")) {
                continue;
            }
            List<String> textures = textureArrays.get(expression.substring(0, open).trim());
            if (textures != null && !textures.isEmpty()) {
                return Optional.of(textures);
            }
        }
        return Optional.empty();
    }
}
