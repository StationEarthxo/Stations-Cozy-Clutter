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

        Tilepack input = new Tilepack();
        input.name = "Test house";
        input.props = Arrays.asList(prop);

        Tilepack output = TilepackCodec.decode(new Gson(), TilepackCodec.encode(new Gson(), input));
        Assert.assertEquals(Tilepack.CURRENT_VERSION, output.version);
        Assert.assertEquals("Test house", output.name);
        Assert.assertEquals(1, output.props.size());
        Assert.assertEquals(100, output.props.get(0).objectId);
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
    public void animatedObjectsAreRejectedButExtendedModelEncodingIsSupported()
    {
        byte[] animatedBytes = {
            1, 1, 0, 42, 10,
            24, 0, 5,
            0
        };
        Assert.assertFalse(ObjectDefinitionDecoder.decode(animatedBytes).isSafeForCustomRendering());

        byte[] extendedBytes = {
            6, 1, 0, 1, 0, 0, 10,
            0
        };
        Assert.assertTrue(ObjectDefinitionDecoder.decode(extendedBytes).isSafeForCustomRendering());
    }

}
