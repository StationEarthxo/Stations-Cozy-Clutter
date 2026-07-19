package com.worldbuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ObjectModelFactory
{
    private static final int OBJECT_CONFIG_ARCHIVE = 6;
    private static final int MODEL_CACHE_SIZE = 256;
    private static final Logger log = LoggerFactory.getLogger(ObjectModelFactory.class);

    private final Client client;
    private final Map<SourceKey, Model> capturedModels = boundedModelCache();
    private final Map<SourceKey, Model> generatedModels = boundedModelCache();
    private final Set<SourceKey> permanentlyFailed = new HashSet<>();

    @Inject
    ObjectModelFactory(Client client)
    {
        this.client = client;
    }

    void capture(int objectId, int type, int orientation, Model model)
    {
        if (model != null)
        {
            SourceKey key = new SourceKey(objectId, type, orientation);
            permanentlyFailed.remove(key);
            capturedModels.put(key, model);
        }
    }

    Model create(PropPlacement placement)
    {
        if (placement.objectId < 0 || placement.modelId >= 0)
        {
            return null;
        }

        SourceKey key = new SourceKey(placement.objectId, placement.objectType, placement.objectOrientation);
        Model base = capturedModels.get(key);
        if (base == null && permanentlyFailed.contains(key))
        {
            return null;
        }
        if (base == null)
        {
            base = generatedModels.get(key);
        }
        if (base == null)
        {
            base = createObjectModel(key);
        }

        if (base == null)
        {
            return null;
        }
        if (placement.scale == 128)
        {
            return ModelSafetyValidator.isSafe(base) ? base : reject(key);
        }

        Model copy = client.mergeModels(base);
        if (copy != null)
        {
            copy.scale(placement.scale, placement.scale, placement.scale);
        }
        return ModelSafetyValidator.isSafe(copy) ? copy : reject(key);
    }

    Model createPreview(int objectId, int type)
    {
        SourceKey key = new SourceKey(objectId, type, 0);
        if (permanentlyFailed.contains(key))
        {
            return null;
        }
        Model cached = generatedModels.get(key);
        return cached != null ? cached : createObjectModel(key);
    }

    void clear()
    {
        capturedModels.clear();
        generatedModels.clear();
        permanentlyFailed.clear();
    }

    boolean isPermanentlyFailed(PropPlacement placement)
    {
        return permanentlyFailed.contains(new SourceKey(placement.objectId, placement.objectType, placement.objectOrientation));
    }

    private Model reject(SourceKey key)
    {
        permanentlyFailed.add(key);
        generatedModels.remove(key);
        capturedModels.remove(key);
        return null;
    }

    private Model createObjectModel(SourceKey key)
    {
        try
        {
            byte[] bytes = client.getIndexConfig().loadData(OBJECT_CONFIG_ARCHIVE, key.objectId);
            // Temporarily unavailable cache data is not a permanent model
            // failure and must never blacklist a known-good object.
            if (bytes == null)
            {
                return null;
            }
            ObjectDefinitionData definition = ObjectDefinitionDecoder.decode(bytes);
            if (!definition.isSafeForCustomRendering())
            {
                log.debug("Object {} decoded as unsupported: name={}, models={}, extended={}, animation={}, transforms={}",
                    key.objectId, definition.name,
                    definition.modelIds == null ? "null" : java.util.Arrays.toString(definition.modelIds),
                    definition.extendedModelIds, definition.animationId, definition.transforms);
                return reject(key);
            }

            List<ModelData> parts = new ArrayList<>();
            for (int i = 0; i < definition.modelIds.length; i++)
            {
                if (definition.modelTypes != null && definition.modelTypes[i] != key.type)
                {
                    continue;
                }
                if (definition.modelTypes == null && key.type != 10)
                {
                    continue;
                }

                ModelData part = client.loadModelData(definition.modelIds[i]);
                if (part == null)
                {
                    return null;
                }
                part = part.shallowCopy().cloneVertices().cloneColors();
                if (part.getFaceTextures() != null)
                {
                    part = part.cloneTextures();
                }
                if (part.getFaceTransparencies() != null)
                {
                    part = part.cloneTransparencies();
                }
                if (definition.rotated)
                {
                    part.rotateY180Ccw();
                }
                parts.add(part);
            }

            if (parts.isEmpty())
            {
                return null;
            }
            ModelData data = parts.size() == 1 ? parts.get(0) : client.mergeModels(parts.toArray(new ModelData[0]));
            if (data == null)
            {
                return null;
            }

            switch (key.orientation & 3)
            {
                case 1:
                    data.rotateY90Ccw();
                    break;
                case 2:
                    data.rotateY180Ccw();
                    break;
                case 3:
                    data.rotateY270Ccw();
                    break;
                default:
                    break;
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
            if (definition.scaleX != 128 || definition.scaleHeight != 128 || definition.scaleY != 128)
            {
                data.scale(definition.scaleX, definition.scaleHeight, definition.scaleY);
            }
            if (definition.offsetX != 0 || definition.offsetHeight != 0 || definition.offsetY != 0)
            {
                data.translate(definition.offsetX, definition.offsetHeight, definition.offsetY);
            }
            Model model = data.light(64 + definition.ambient, 768 + definition.contrast, -50, -10, -50);
            if (model != null && ModelSafetyValidator.isSafe(model))
            {
                generatedModels.put(key, model);
                return model;
            }
            return reject(key);
        }
        catch (RuntimeException ex)
        {
            permanentlyFailed.add(key);
            log.debug("Unable to decode or build object " + key.objectId, ex);
            return null;
        }
    }

    private static Map<SourceKey, Model> boundedModelCache()
    {
        return Collections.synchronizedMap(new LinkedHashMap<SourceKey, Model>(MODEL_CACHE_SIZE, .75f, true)
        {
            @Override
            protected boolean removeEldestEntry(Map.Entry<SourceKey, Model> eldest)
            {
                return size() > MODEL_CACHE_SIZE;
            }
        });
    }

    private static final class SourceKey
    {
        private final int objectId;
        private final int type;
        private final int orientation;

        private SourceKey(int objectId, int type, int orientation)
        {
            this.objectId = objectId;
            this.type = type;
            this.orientation = orientation;
        }

        @Override
        public boolean equals(Object other)
        {
            if (!(other instanceof SourceKey))
            {
                return false;
            }
            SourceKey key = (SourceKey) other;
            return objectId == key.objectId && type == key.type && orientation == key.orientation;
        }

        @Override
        public int hashCode()
        {
            return ((objectId * 31) + type) * 31 + orientation;
        }
    }
}
