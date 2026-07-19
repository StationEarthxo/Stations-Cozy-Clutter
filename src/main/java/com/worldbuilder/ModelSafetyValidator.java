package com.worldbuilder;

import net.runelite.api.Model;

final class ModelSafetyValidator
{
    private static final int MAX_VERTICES = 20_000;
    private static final int MAX_FACES = 40_000;
    private static final float MAX_COORDINATE = 131_072f;

    private ModelSafetyValidator()
    {
    }

    static boolean isSafe(Model model)
    {
        if (model == null)
        {
            return false;
        }
        int vertices = model.getVerticesCount();
        int faces = model.getFaceCount();
        if (vertices <= 0 || vertices > MAX_VERTICES || faces <= 0 || faces > MAX_FACES)
        {
            return false;
        }

        float[] x = model.getVerticesX();
        float[] y = model.getVerticesY();
        float[] z = model.getVerticesZ();
        int[] a = model.getFaceIndices1();
        int[] b = model.getFaceIndices2();
        int[] c = model.getFaceIndices3();
        int[] color1 = model.getFaceColors1();
        int[] color2 = model.getFaceColors2();
        int[] color3 = model.getFaceColors3();
        if (!hasLength(x, vertices) || !hasLength(y, vertices) || !hasLength(z, vertices)
            || !hasLength(a, faces) || !hasLength(b, faces) || !hasLength(c, faces)
            || !hasLength(color1, faces) || !hasLength(color2, faces) || !hasLength(color3, faces))
        {
            return false;
        }

        for (int i = 0; i < vertices; i++)
        {
            if (!Float.isFinite(x[i]) || !Float.isFinite(y[i]) || !Float.isFinite(z[i])
                || Math.abs(x[i]) > MAX_COORDINATE || Math.abs(y[i]) > MAX_COORDINATE || Math.abs(z[i]) > MAX_COORDINATE)
            {
                return false;
            }
        }
        for (int i = 0; i < faces; i++)
        {
            if (!validIndex(a[i], vertices) || !validIndex(b[i], vertices) || !validIndex(c[i], vertices))
            {
                return false;
            }
        }

        byte[] transparency = model.getFaceTransparencies();
        short[] textures = model.getFaceTextures();
        if ((transparency != null && transparency.length < faces) || (textures != null && textures.length < faces))
        {
            return false;
        }

        byte[] textureFaces = model.getTextureFaces();
        if (textureFaces != null)
        {
            if (textureFaces.length < faces)
            {
                return false;
            }
            int[] textureA = model.getTexIndices1();
            int[] textureB = model.getTexIndices2();
            int[] textureC = model.getTexIndices3();
            for (int i = 0; i < faces; i++)
            {
                int textureFace = textureFaces[i];
                if (textureFace == -1)
                {
                    continue;
                }
                int index = textureFace & 0xFF;
                if (!hasIndex(textureA, index) || !hasIndex(textureB, index) || !hasIndex(textureC, index)
                    || !validIndex(textureA[index], vertices) || !validIndex(textureB[index], vertices)
                    || !validIndex(textureC[index], vertices))
                {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasLength(float[] values, int length)
    {
        return values != null && values.length >= length;
    }

    private static boolean hasLength(int[] values, int length)
    {
        return values != null && values.length >= length;
    }

    private static boolean hasIndex(int[] values, int index)
    {
        return values != null && index >= 0 && index < values.length;
    }

    private static boolean validIndex(int index, int size)
    {
        return index >= 0 && index < size;
    }
}
