package org.kohsuke.stapler.export;

import com.google.common.collect.ImmutableList;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Test;
import org.mortbay.util.ajax.JSON;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class RobustnessTest {

    static ModelBuilder MODEL_BUILDER = new ModelBuilder();

    @Test
    public void testSerializationOfActionsShouldNotFailSerialization() throws IOException {

        // My Action is configured to throw an exception
        Pipeline pipeline = new Pipeline(ImmutableList.of(new CurrentTimeAction(), MyAction.explodingAction()), false);
        String json = doExport(pipeline, Pipeline.class);
        assertNotNull(json);

        // Check we've serialized the expected root object
        HashMap jsonObject = (HashMap)JSON.parse(json);
        assertEquals("org.kohsuke.stapler.export.RobustnessTest$Pipeline", jsonObject.get("_class"));

        // Ensure we have actions
        Object[] actions = (Object[]) jsonObject.get("actions");
        assertNotNull(actions);
        assertEquals(1, actions.length);

        // Action serialized looks good
        HashMap action = (HashMap)actions[0];
        assertEquals(2, action.size());
        assertEquals("org.kohsuke.stapler.export.RobustnessTest$CurrentTimeAction", action.get("_class"));
        assertEquals("1/1/2017", action.get("time"));
    }

    @Test
    public void testAllActionsShouldSerialize() throws IOException {

        // My Action is configured to throw an exception
        Pipeline pipeline = new Pipeline(ImmutableList.of(new CurrentTimeAction(), MyAction.action()), false);
        String json = doExport(pipeline, Pipeline.class);
        assertNotNull(json);

        // Check we've serialized the expected root object
        HashMap jsonObject = (HashMap)JSON.parse(json);
        assertEquals("org.kohsuke.stapler.export.RobustnessTest$Pipeline", jsonObject.get("_class"));

        // Ensure we have actions
        Object[] actions = (Object[]) jsonObject.get("actions");
        assertNotNull(actions);
        assertEquals(2, actions.length);

        // Action serialized looks good
        HashMap action = (HashMap)actions[0];
        assertEquals(2, action.size());

        assertEquals("org.kohsuke.stapler.export.RobustnessTest$CurrentTimeAction", action.get("_class"));
        assertEquals("1/1/2017", action.get("time"));

        HashMap action2 = (HashMap)actions[1];
        assertEquals("org.kohsuke.stapler.export.RobustnessTest$MyAction", action2.get("_class"));
        assertEquals("Bob", action2.get("myName"));
    }

    @Test
    public void testSerializationOfPipelineShouldFailIfPropertyThrowsException() throws IOException {
        // No actions are configured to throw exceptions
        Pipeline pipeline = new Pipeline(ImmutableList.of(new CurrentTimeAction(), MyAction.action()), true);

        try {
            doExport(pipeline, Pipeline.class);
            fail("Should have failed");
        } catch (IOException e) {
            // Pass
        }
    }

    @Test
    public void testCollectionOfPipelinesShouldFailIfASingleOneWasThrowingExceptionsButNotWithinAnAction() throws Exception {
        List<Pipeline> pipelines = ImmutableList.of(
                new Pipeline(ImmutableList.of(new CurrentTimeAction(), MyAction.action()), true), // should cause the all the serialization to crash
                new Pipeline(ImmutableList.of(new CurrentTimeAction(), MyAction.action()), false),
                new Pipeline(ImmutableList.of(new CurrentTimeAction(), MyAction.explodingAction()), false) // This pipeline should only have the CurrentTimeAction serialized
        );
        try {
            doExport(new CollectionResponse(pipelines), CollectionResponse.class);
            fail("This should have failed rather than return 2 pipelines");
        } catch (IOException e) {
            // Pass
        }
    }

    private String doExport(Object model, Class<?> modelClass) throws IOException {
        Model p = MODEL_BUILDER.get(modelClass);
        StringWriter writer = new StringWriter();
        p.writeTo(model, new JSONDataWriter(writer, new ExportConfig().withPrettyPrint(true)));
        return writer.toString();
    }

    @ExportedBean
    static class CollectionResponse {
        private List items;

        public CollectionResponse(List items) {
            this.items = items;
        }

        @Exported(inline = true)
        public List getItems() {
            return items;
        }
    }

    @ExportedBean
    static class Pipeline {

        private static final AtomicInteger count = new AtomicInteger(1);

        private final Collection<Action> actions;
        private final boolean explode;

        public Pipeline(Collection<Action> actions, boolean explode) {
            this.actions = actions;
            this.explode = explode;
        }

        @Exported(inline = true)
        public Run getLatestRun() {
            return new Run(explode);
        }

        @Exported(inline = true, visibility = 10)
        public Collection<Action> getActions() {
            return actions;
        }

        @Exported(inline = true)
        public String getName() {
            if (explode) throw new RuntimeException("Ain't dem the breaks");
            return "Pipeline";
        }
    }

    @ExportedBean
    static class Run {

        private final boolean explode;

        public Run(boolean explode) {
            this.explode = explode;
        }

        @Exported(inline = true)
        public int getId() {
            return 1;
        }

        @Exported(inline = true)
        public String getCommitMessage() {
            if (this.explode) throw new RuntimeException(":(");
            return "war and peace";
        }
    }

    interface Action {
    }

    @ExportedBean
    public static class MyAction implements Action {
        private boolean explode = false;

        public MyAction(boolean explode) {
            this.explode = explode;
        }

        @Exported(inline = true)
        public String getMyName() {
            if (explode) throw new RuntimeException("suprise! I've just crashed blue ocean");
            return "Bob";
        }

        public static Action explodingAction() {
            return new MyAction(true);
        }

        public static Action action() {
            return new MyAction(false);
        }
    }

    @ExportedBean
    public class CurrentTimeAction implements Action {

        @Exported
        public String getTime() {
            return "1/1/2017";
        }
    }
}
