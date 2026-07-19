package com.worldbuilder;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.Model;

final class ModelPreviewRenderer
{
    private ModelPreviewRenderer()
    {
    }

    static BufferedImage render(Model model, int width, int height)
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(24, 24, 27, 255));
        graphics.fillRoundRect(0, 0, width, height, 10, 10);

        float[] vx = model.getVerticesX();
        float[] vy = model.getVerticesY();
        float[] vz = model.getVerticesZ();
        int vertexCount = model.getVerticesCount();
        if (vertexCount == 0)
        {
            graphics.dispose();
            return image;
        }

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double[] tx = new double[vertexCount];
        double[] ty = new double[vertexCount];
        double[] tz = new double[vertexCount];
        double yaw = Math.toRadians(35);
        double pitch = Math.toRadians(22);
        for (int i = 0; i < vertexCount; i++)
        {
            double x = vx[i];
            double y = -vy[i];
            double z = vz[i];
            double rx = x * Math.cos(yaw) + z * Math.sin(yaw);
            double rz = -x * Math.sin(yaw) + z * Math.cos(yaw);
            double ry = y * Math.cos(pitch) - rz * Math.sin(pitch);
            rz = y * Math.sin(pitch) + rz * Math.cos(pitch);
            tx[i] = rx;
            ty[i] = ry;
            tz[i] = rz;
            minX = Math.min(minX, rx);
            maxX = Math.max(maxX, rx);
            minY = Math.min(minY, ry);
            maxY = Math.max(maxY, ry);
        }

        double spanX = Math.max(1, maxX - minX);
        double spanY = Math.max(1, maxY - minY);
        double scale = Math.min((width - 12) / spanX, (height - 12) / spanY);
        double centerX = (minX + maxX) / 2;
        double centerY = (minY + maxY) / 2;

        int[] faceA = model.getFaceIndices1();
        int[] faceB = model.getFaceIndices2();
        int[] faceC = model.getFaceIndices3();
        int[] colors = model.getFaceColors1();
        byte[] transparencies = model.getFaceTransparencies();
        List<Face> faces = new ArrayList<>(model.getFaceCount());
        for (int i = 0; i < model.getFaceCount(); i++)
        {
            int a = faceA[i], b = faceB[i], c = faceC[i];
            if (a >= vertexCount || b >= vertexCount || c >= vertexCount || colors[i] == -2)
            {
                continue;
            }
            faces.add(new Face(i, (tz[a] + tz[b] + tz[c]) / 3));
        }
        faces.sort(Comparator.comparingDouble(face -> face.depth));

        for (Face face : faces)
        {
            int i = face.index;
            int a = faceA[i], b = faceB[i], c = faceC[i];
            Polygon triangle = new Polygon(
                new int[]{project(tx[a], centerX, scale, width), project(tx[b], centerX, scale, width), project(tx[c], centerX, scale, width)},
                new int[]{projectY(ty[a], centerY, scale, height), projectY(ty[b], centerY, scale, height), projectY(ty[c], centerY, scale, height)},
                3);
            Color color = hslToColor(colors[i] < 0 ? 0 : colors[i]);
            int alpha = transparencies == null ? 255 : 255 - (transparencies[i] & 0xFF);
            graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            graphics.fillPolygon(triangle);
        }
        graphics.dispose();
        return image;
    }

    private static int project(double value, double center, double scale, int size)
    {
        return (int) Math.round(size / 2.0 + (value - center) * scale);
    }

    private static int projectY(double value, double center, double scale, int size)
    {
        return (int) Math.round(size / 2.0 - (value - center) * scale);
    }

    private static Color hslToColor(int packed)
    {
        double h = ((packed >> 10) & 63) / 64.0;
        double s = ((packed >> 7) & 7) / 8.0;
        double l = (packed & 127) / 128.0;
        double q = l < .5 ? l * (1 + s) : l + s - l * s;
        double p = 2 * l - q;
        return new Color((float) hue(p, q, h + 1.0 / 3), (float) hue(p, q, h), (float) hue(p, q, h - 1.0 / 3));
    }

    private static double hue(double p, double q, double t)
    {
        if (t < 0) t++;
        if (t > 1) t--;
        if (t < 1.0 / 6) return p + (q - p) * 6 * t;
        if (t < 1.0 / 2) return q;
        if (t < 2.0 / 3) return p + (q - p) * (2.0 / 3 - t) * 6;
        return p;
    }

    private static final class Face
    {
        private final int index;
        private final double depth;

        private Face(int index, double depth)
        {
            this.index = index;
            this.depth = depth;
        }
    }
}
