package net.nennneko5787.lepus.core.format.ir.animation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.Provenance;

/** Reads the texture selection fields used by attachable render controllers. */
@SpecImpl("SC-170#attachable/render_controllers")
public final class RenderControllerFiles {

    private RenderControllerFiles() {
    }

    public static List<RenderControllerIr> parse(JsonObject root, Provenance provenance) {
        List<RenderControllerIr> parsed = new ArrayList<>();
        root.getObject("render_controllers").ifPresent(controllers ->
                controllers.members().forEach((name, raw) -> raw.asObject().ifPresent(body -> {
                    Map<String, List<String>> arrays = new LinkedHashMap<>();
                    body.getObject("arrays").flatMap(value -> value.getObject("textures"))
                            .ifPresent(textureArrays -> textureArrays.members().forEach(
                                    (arrayName, arrayRaw) -> arrayRaw.asArray().ifPresent(values -> {
                                        List<String> entries = values.values().stream()
                                                .map(JsonValue::asString)
                                                .flatMap(java.util.Optional::stream).toList();
                                        arrays.put(arrayName, entries);
                                    })));
                    List<String> textureExpressions = body.getArray("textures")
                            .map(values -> values.values().stream()
                                    .map(JsonValue::asString)
                                    .flatMap(java.util.Optional::stream).toList())
                            .orElse(List.of());
                    parsed.add(new RenderControllerIr(name, arrays, textureExpressions, provenance));
                })));
        return List.copyOf(parsed);
    }
}
