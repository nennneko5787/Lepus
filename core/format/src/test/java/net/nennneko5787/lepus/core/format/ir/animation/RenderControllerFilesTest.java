package net.nennneko5787.lepus.core.format.ir.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import org.junit.jupiter.api.Test;

@ProvesSpec("SC-170#attachable/render_controllers")
class RenderControllerFilesTest {

    @Test
    void resolvesQueryVariantTextureArray() {
        var root = Json.parse("""
                {
                  "format_version": "1.8.0",
                  "render_controllers": {
                    "controller.render.skin": {
                      "arrays": { "textures": {
                        "Array.skins": ["Texture.default", "Texture.alex"]
                      }},
                      "textures": ["Array.skins[query.variant]"]
                    }
                  }
                }
                """).asObject().orElseThrow();

        var controller = RenderControllerFiles.parse(root,
                Provenance.file(PackId.NONE, "render_controllers/skin.json")).get(0);
        assertEquals(List.of("Texture.default", "Texture.alex"),
                controller.variantTextures().orElseThrow());
    }
}
