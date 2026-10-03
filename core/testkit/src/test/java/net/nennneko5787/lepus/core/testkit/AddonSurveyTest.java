package net.nennneko5787.lepus.core.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The survey, against a pack built for the purpose. {@code spec/process.md} §1.
 *
 * <p>The survey's whole value is that its answers are true, so the thing worth testing is that it
 * reports a component as unread when nothing reads it and read when something does — from the
 * parsers' own constants rather than from a list of its own.
 */
@ProvesSpec("SC-110")
class AddonSurveyTest {

    private static final String MANIFEST = """
            {
              "format_version": 2,
              "header": {
                "name": "Survey Fixture",
                "description": "Authored for this test.",
                "uuid": "5c00c1e0-0000-4000-8000-0000000000f1",
                "version": [1, 0, 0],
                "min_engine_version": [1, 21, 0]
              },
              "modules": [
                { "type": "data", "uuid": "5c00c1e0-0000-4000-8000-0000000000f2",
                  "version": [1, 0, 0] },
                { "type": "resources", "uuid": "5c00c1e0-0000-4000-8000-0000000000f3",
                  "version": [1, 0, 0] }
              ]
            }""";

    /** A block using one component this build reads and one it does not. */
    private static final String BLOCK = """
            {
              "format_version": "1.21.0",
              "minecraft:block": {
                "description": { "identifier": "survey:stone" },
                "components": {
                  "minecraft:light_emission": 7,
                  "minecraft:flammable": true,
                  "minecraft:material_instances": { "*": { "texture": "survey_stone" } }
                }
              }
            }""";

    private static final String TERRAIN = """
            {
              "texture_data": {
                "survey_stone": { "textures": "textures/blocks/survey_stone" }
              }
            }""";

    private static Path fixture(Path root, boolean withTexture) throws IOException {
        Path pack = root.resolve("survey_pack");
        Files.createDirectories(pack.resolve("blocks"));
        Files.createDirectories(pack.resolve("textures/blocks"));
        Files.writeString(pack.resolve("manifest.json"), MANIFEST);
        Files.writeString(pack.resolve("blocks/stone.json"), BLOCK);
        Files.writeString(pack.resolve("textures/terrain_texture.json"), TERRAIN);
        if (withTexture) {
            // Content is never read - the survey asks whether the path resolves, not what is in it.
            Files.write(pack.resolve("textures/blocks/survey_stone.png"), new byte[] {1, 2, 3});
        }
        return root;
    }

    @Test
    void reportsWhatIsUsedAndWhetherAnythingReadsIt(@TempDir Path root) throws IOException {
        AddonSurvey.Report report = AddonSurvey.of(fixture(root, true));

        assertEquals(1, report.packs());
        assertEquals(1, report.blocks());

        // Read, because BlockPhysics names it. Unread, because nothing does - and the survey knows
        // which is which by asking the parsers, so this assertion fails the day that changes.
        assertTrue(usage(report, "minecraft:light_emission").read());
        assertFalse(usage(report, "minecraft:flammable").read());
        assertTrue(usage(report, "minecraft:material_instances").read());
    }

    @Test
    void namesTheBlocksWhoseTextureResolvesToNothing(@TempDir Path root) throws IOException {
        // The offline form of SCE-2032. Three separate misdiagnoses in this project's history were
        // this exact question answered by hand, twice wrongly.
        AddonSurvey.Report present = AddonSurvey.of(fixture(root.resolve("with"), true));
        assertEquals(List.of(), present.blocksWithoutTexture());

        AddonSurvey.Report absent = AddonSurvey.of(fixture(root.resolve("without"), false));
        assertEquals(List.of("survey:stone"), absent.blocksWithoutTexture());
    }

    @Test
    void rendersSomethingAPersonCanRead(@TempDir Path root) throws IOException {
        List<String> lines = AddonSurvey.render(AddonSurvey.of(fixture(root, true)));
        assertTrue(lines.get(0).contains("blocks 1"), lines.get(0));
        assertTrue(lines.stream().anyMatch(line -> line.contains("minecraft:flammable")));
    }

    private static AddonSurvey.Usage usage(AddonSurvey.Report report, String id) {
        return report.blockComponents().stream()
                .filter(entry -> entry.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError(id + " was not reported at all"));
    }

    /**
     * <b>A comparison that says an entry "fired" without saying whether it draws is the failure this
     * report exists to prevent.</b> {@code spec/process.md} §1.
     *
     * <p>Two first-person animations of the corpus's own shape: constants only, so a length of zero,
     * guarded by the same condition. One is a {@code loop: true} and one is
     * {@code hold_on_last_frame}. Both conditions read 1.0 in first person. **Only the second draws
     * anything**, and that is the whole difference between the corpus's two characters — so a
     * comparison reporting blends alone prints the same number for both and the distinction is gone.
     *
     * <p>It also has to be the same rig in both, or the comparison has no fixed point to compare
     * against: {@code head2}'s facing is asserted identical across the two views here, which is
     * exactly what the character that is right in both views looks like from this tool.
     */
    @Test
    void aComparisonDistinguishesAnEntryThatFiresFromOneThatDraws(@TempDir Path root)
            throws IOException {
        Path pack = root.resolve("rig");
        Files.createDirectories(pack.resolve("attachables"));
        Files.createDirectories(pack.resolve("models/entity"));
        Files.createDirectories(pack.resolve("animations"));
        Files.writeString(pack.resolve("manifest.json"), MANIFEST);
        Files.writeString(pack.resolve("models/entity/rig.json"), GEOMETRY);
        Files.writeString(pack.resolve("animations/rig.json"), ANIMATIONS);
        Files.writeString(pack.resolve("attachables/loop.json"),
                attachable("loop", "animation.rig.loop"));
        Files.writeString(pack.resolve("attachables/held.json"),
                attachable("held", "animation.rig.held"));
        Files.writeString(pack.resolve("attachables/vanilla.json"),
                attachable("vanilla", "controller.animation.elytra.default"));

        String looping = String.join("\n",
                AddonSurvey.viewComparison(root, "survey:loop", "main", ""));
        String held = String.join("\n",
                AddonSurvey.viewComparison(root, "survey:held", "main", ""));
        String vanilla = String.join("\n",
                AddonSurvey.viewComparison(root, "survey:vanilla", "main", ""));

        // The condition is identical in both and reads 1.0 in first person. The `draws` cell is the
        // only thing that tells the two apart, which is why it is a column and not a footnote.
        assertTrue(looping.contains("loop") && looping.contains("NO"), looping);
        assertTrue(held.contains("held") && held.contains("yes"), held);

        // A controller no pack ships is reported unresolved, per view - a build with no vanilla
        // animation namespace always will be, and the corpus's attachables all do it.
        assertTrue(vanilla.contains("controller.animation.elytra.default"), vanilla);
        assertTrue(vanilla.contains("unresolved"), vanilla);

        // The space each view draws in is stated, so the extents below it can be read with it.
        assertTrue(held.contains("origin at the eye"), held);
        assertTrue(held.contains("0.94") || held.contains("NOT MEASURED"), held);
    }

    /**
     * <b>The two views of a rig that poses nothing view-conditionally are the same pose.</b>
     *
     * <p>Asserted because it is the shape of a character that is <em>right</em> in both views, and a
     * tool that could not produce it would be unable to say which of a broken pair is the odd one.
     * The facing is a property of the pose and not of the view's space, which is what makes it the
     * one number here that can be compared across the two columns.
     */
    @Test
    void aViewIndependentRigPosesIdenticallyInBothViews(@TempDir Path root) throws IOException {
        Path pack = root.resolve("rig");
        Files.createDirectories(pack.resolve("attachables"));
        Files.createDirectories(pack.resolve("models/entity"));
        Files.createDirectories(pack.resolve("animations"));
        Files.writeString(pack.resolve("manifest.json"), MANIFEST);
        Files.writeString(pack.resolve("models/entity/rig.json"), GEOMETRY);
        Files.writeString(pack.resolve("animations/rig.json"), ANIMATIONS);
        Files.writeString(pack.resolve("attachables/held.json"),
                attachable("held", "animation.rig.held"));

        List<String> lines = AddonSurvey.viewComparison(root, "survey:held", "main", "");
        // The pose table only. The extents below it also carry a `head2` row, and a filter that
        // matched both would compare a facing against a range and pass or fail for no reason.
        List<String> head = lines.stream()
                .filter(line -> line.contains(" head2 ") && !line.contains(".."))
                .toList();
        assertEquals(1, head.size(), String.join("\n", lines));
        // bone, then two triples of facing, then two triples of scale. Compared as triples rather
        // than by index so that a column reordering fails the assertion instead of silently
        // comparing a facing against a scale.
        List<String> cells = java.util.Arrays.stream(head.get(0).trim().split("\\s+"))
                .toList();
        assertEquals(13, cells.size(), head.get(0));
        assertEquals(cells.subList(1, 4), cells.subList(4, 7),
                "facing must be the same in both views: " + head.get(0));
        assertEquals(cells.subList(7, 10), cells.subList(10, 13),
                "scale must be the same in both views: " + head.get(0));
    }

    /**
     * A rig whose content hangs off <b>{@code waist}</b>, with a cube-less {@code body} beside it.
     *
     * <p>The parentage is the point, and it is the corpus's own asymmetry. First person drives
     * {@code body} and never {@code waist} (SC-180 §4.2.1), so a rig hanging off {@code body} turns
     * half way round in that view and a rig hanging off {@code waist} does not — which is why one of
     * the corpus's two characters is posed by her first-person animation and the other is not, and
     * why only the second is right in both views. A fixture that put the cube under {@code body}
     * would make the two views differ and the comparison below would assert the wrong thing.
     */
    private static final String GEOMETRY = """
            {
              "format_version": "1.16.100",
              "minecraft:geometry": [{
                "description": { "identifier": "geometry.rig",
                                 "texture_width": 16, "texture_height": 16 },
                "bones": [
                  { "name": "root", "pivot": [0, 0, 0] },
                  { "name": "waist", "pivot": [0, 12, 0] },
                  { "name": "body", "pivot": [0, 20, 0] },
                  { "name": "head2", "parent": "waist", "pivot": [0, 20, 0],
                    "cubes": [ { "origin": [-4, 20, -4], "size": [8, 8, 8],
                                 "uv": [0, 0] } ] }
                ]
              }]
            }""";

    /** The corpus's pair: constants only, so no length, differing in one word. */
    private static final String ANIMATIONS = """
            {
              "format_version": "1.8.0",
              "animations": {
                "animation.rig.loop": {
                  "loop": true,
                  "bones": { "head2": { "position": [0, 0, -8] } }
                },
                "animation.rig.held": {
                  "loop": "hold_on_last_frame",
                  "bones": { "head2": { "position": [0, 0, -8] } }
                }
              }
            }""";

    private static String attachable(String name, String animation) {
        return """
                {
                  "format_version": "1.16.100",
                  "minecraft:attachable": {
                    "description": {
                      "identifier": "survey:%s",
                      "materials": { "default": "entity_alphatest" },
                      "textures": { "default": "textures/blocks/survey_stone" },
                      "geometry": { "default": "geometry.rig" },
                      "animations": { "hand": "%s" },
                      "scripts": {
                        "animate": [ { "hand": "c.is_first_person" } ],
                        "pre_animation": []
                      }
                    }
                  }
                }""".formatted(name, animation);
    }
}
