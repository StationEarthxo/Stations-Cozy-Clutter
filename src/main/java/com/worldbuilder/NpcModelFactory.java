package com.worldbuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;

final class NpcModelFactory
{
    private static final int NPC_CONFIG_ARCHIVE = 9;
    private static final int CACHE_SIZE = 160;

    private final Client client;
    private final Map<Integer, Model> cache = Collections.synchronizedMap(
        new LinkedHashMap<Integer, Model>(CACHE_SIZE, .75f, true)
        {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Model> eldest)
            {
                return size() > CACHE_SIZE;
            }
        });

    @Inject
    NpcModelFactory(Client client)
    {
        this.client = client;
    }

    Model create(PropPlacement placement)
    {
        Model base = createPreview(placement.npcId);
        if (base == null)
        {
            return null;
        }
        if (placement.scale == 128)
        {
            return ModelSafetyValidator.isSafe(base) ? base : null;
        }
        Model copy = client.mergeModels(base);
        if (copy != null)
        {
            copy.scale(placement.scale, placement.scale, placement.scale);
        }
        return ModelSafetyValidator.isSafe(copy) ? copy : null;
    }

    Model createPreview(int npcId)
    {
        Model cached = cache.get(npcId);
        if (cached != null)
        {
            return cached;
        }
        try
        {
            byte[] bytes = client.getIndexConfig().loadData(NPC_CONFIG_ARCHIVE, npcId);
            if (bytes == null)
            {
                return null;
            }
            NpcDefinitionData definition = NpcDefinitionDecoder.decode(bytes);
            if (!definition.isSafeForCustomRendering())
            {
                return null;
            }
            List<ModelData> parts = new ArrayList<>(definition.modelIds.length);
            for (int modelId : definition.modelIds)
            {
                ModelData part = client.loadModelData(modelId);
                if (part == null)
                {
                    return null;
                }
                part = part.shallowCopy().cloneVertices().cloneColors();
                if (part.getFaceTextures() != null) part = part.cloneTextures();
                if (part.getFaceTransparencies() != null) part = part.cloneTransparencies();
                parts.add(part);
            }
            ModelData data = parts.size() == 1 ? parts.get(0) : client.mergeModels(parts.toArray(new ModelData[0]));
            if (data == null)
            {
                return null;
            }
            if (definition.recolorFrom != null)
            {
                for (int i = 0; i < definition.recolorFrom.length; i++)
                {
                    data.recolor(definition.recolorFrom[i], definition.recolorTo[i]);
                }
            }
            if (definition.retextureFrom != null)
            {
                for (int i = 0; i < definition.retextureFrom.length; i++)
                {
                    data.retexture(definition.retextureFrom[i], definition.retextureTo[i]);
                }
            }
            if (definition.widthScale != 128 || definition.heightScale != 128)
            {
                data.scale(definition.widthScale, definition.heightScale, definition.widthScale);
            }
            Model model = data.light(64 + definition.ambient, 850 + definition.contrast, -30, -50, -30);
            if (ModelSafetyValidator.isSafe(model))
            {
                cache.put(npcId, model);
                return model;
            }
        }
        catch (RuntimeException | AssertionError ignored)
        {
        }
        return null;
    }

    void clear()
    {
        cache.clear();
    }
}
