package com.worldbuilder;

import com.google.gson.Gson;
import java.util.Arrays;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.junit.Assert;
import org.junit.Test;

public class WorldBuilderPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(WorldBuilderPlugin.class);
        RuneLite.main(args);
    }

    @Test
    public void tilepackRoundTrips() throws Exception
    {
        PropPlacement prop = new PropPlacement();
        prop.name = "Rock";
        prop.objectId = 100;
        prop.worldX = 3200;
        prop.worldY = 3201;
        prop.plane = 0;
        prop.animationId = 472;
        prop.offsetX = 24;
        prop.offsetY = -16;

        Tilepack input = new Tilepack();
        input.name = "Test house";
        input.props = Arrays.asList(prop);

        Tilepack output = TilepackCodec.decode(new Gson(), TilepackCodec.encode(new Gson(), input));
        Assert.assertEquals(Tilepack.CURRENT_VERSION, output.version);
        Assert.assertEquals("Test house", output.name);
        Assert.assertEquals(1, output.props.size());
        Assert.assertEquals(100, output.props.get(0).objectId);
        Assert.assertEquals(472, output.props.get(0).animationId);
        Assert.assertEquals(24, output.props.get(0).offsetX);
        Assert.assertEquals(-16, output.props.get(0).offsetY);
    }

    @Test
    public void decoderReadsModelTransforms()
    {
        byte[] bytes = {
            2, 'R', 'o', 'c', 'k', 0,
            1, 1, 0x04, (byte) 0xD2, 10,
            62,
            65, 0, (byte) 160,
            70, (byte) 0xFF, (byte) 0xE0,
            0
        };
        ObjectDefinitionData data = ObjectDefinitionDecoder.decode(bytes);
        Assert.assertEquals("Rock", data.name);
        Assert.assertArrayEquals(new int[]{1234}, data.modelIds);
        Assert.assertArrayEquals(new int[]{10}, data.modelTypes);
        Assert.assertTrue(data.rotated);
        Assert.assertEquals(160, data.scaleX);
        Assert.assertEquals(-32, data.offsetX);
        Assert.assertTrue(data.isSafeForCustomRendering());
    }

    @Test
    public void rawModelPlacementsAreRejectedButObjectsRemainValid()
    {
        PropPlacement raw = new PropPlacement();
        raw.name = "legacy raw model";
        raw.objectId = -1;
        raw.modelId = 5662;
        raw.worldX = 3200;
        raw.worldY = 3200;
        Assert.assertFalse(raw.isValid());

        PropPlacement object = new PropPlacement();
        object.name = "Flower";
        object.objectId = 1187;
        object.modelId = -1;
        object.worldX = 3200;
        object.worldY = 3200;
        Assert.assertTrue(object.isValid());
    }

    @Test
    public void animatedObjectsAndExtendedModelEncodingAreSupported()
    {
        byte[] animatedBytes = {
            1, 1, 0, 42, 10,
            24, 0, 5,
            0
        };
        ObjectDefinitionData animated = ObjectDefinitionDecoder.decode(animatedBytes);
        Assert.assertTrue(animated.isSafeForCustomRendering());
        Assert.assertEquals(5, animated.animationId);

        byte[] extendedBytes = {
            6, 1, 0, 1, 0, 0, 10,
            0
        };
        Assert.assertTrue(ObjectDefinitionDecoder.decode(extendedBytes).isSafeForCustomRendering());
    }

    @Test
    public void npcDecoderLinksModelsAndAnimations()
    {
        byte[] bytes = {
            1, 1, 0, 42,
            2, 'B', 'a', 'k', 'e', 'r', 0,
            13, 0, 10,
            14, 0, 11,
            114, 0, 12,
            0
        };
        NpcDefinitionData npc = NpcDefinitionDecoder.decode(bytes);
        Assert.assertEquals("Baker", npc.name);
        Assert.assertArrayEquals(new int[]{42}, npc.modelIds);
        Assert.assertEquals(10, npc.standingAnimation);
        Assert.assertEquals(11, npc.walkingAnimation);
        Assert.assertEquals(12, npc.runAnimation);
        Assert.assertTrue(npc.isSafeForCustomRendering());
    }

    @Test
    public void animatedNpcPlacementsAreValidAndCopyTheirAnimation()
    {
        PropPlacement npc = new PropPlacement();
        npc.name = "Baker - Idle";
        npc.objectId = -1;
        npc.npcId = 123;
        npc.animationId = 456;
        npc.worldX = 3200;
        npc.worldY = 3200;
        Assert.assertTrue(npc.isValid());

        PropPlacement copy = npc.copy();
        Assert.assertEquals(123, copy.npcId);
        Assert.assertEquals(456, copy.animationId);
        Assert.assertTrue(copy.animationLoop);
    }

    @Test
    public void nudgesAreBoundedAndOldPlacementsDefaultToTileCentre()
    {
        PropPlacement prop = new PropPlacement();
        prop.name = "Poster";
        prop.objectId = 100;
        prop.worldX = 3200;
        prop.worldY = 3200;
        Assert.assertTrue(prop.isValid());
        Assert.assertEquals(0, prop.offsetX);
        Assert.assertEquals(0, prop.offsetY);

        prop.offsetX = 64;
        prop.offsetY = -64;
        Assert.assertTrue(prop.isValid());
        PropPlacement copy = prop.copy();
        Assert.assertEquals(64, copy.offsetX);
        Assert.assertEquals(-64, copy.offsetY);

        prop.offsetX = 65;
        Assert.assertFalse(prop.isValid());
    }

}
