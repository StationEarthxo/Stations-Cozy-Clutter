package com.worldbuilder;

/**
 * Minimal NPC-definition decoder for models and the animations naturally
 * associated with them. Opcode lengths follow RuneLite's cache NpcLoader.
 */
final class NpcDefinitionDecoder
{
    private NpcDefinitionDecoder()
    {
    }

    static NpcDefinitionData decode(byte[] data)
    {
        NpcDefinitionData definition = new NpcDefinitionData();
        if (data == null)
        {
            return definition;
        }
        Reader in = new Reader(data);
        while (in.remaining() > 0)
        {
            int opcode = in.u8();
            if (opcode == 0)
            {
                break;
            }
            decodeOpcode(definition, in, opcode);
        }
        return definition;
    }

    private static void decodeOpcode(NpcDefinitionData d, Reader in, int opcode)
    {
        if (opcode == 1 || opcode == 60)
        {
            int count = in.u8();
            if (opcode == 1)
            {
                d.modelIds = new int[count];
                for (int i = 0; i < count; i++) d.modelIds[i] = in.u16();
            }
            else
            {
                in.skip(count * 2);
            }
        }
        else if (opcode == 2)
        {
            d.name = in.string();
        }
        else if (opcode == 12)
        {
            in.skip(1);
        }
        else if (opcode == 13)
        {
            d.standingAnimation = animation(in.u16());
        }
        else if (opcode == 14)
        {
            d.walkingAnimation = animation(in.u16());
        }
        else if (opcode == 15 || opcode == 16 || opcode == 18 || opcode == 95
            || opcode == 103 || opcode == 124 || opcode == 126 || (opcode >= 74 && opcode <= 79))
        {
            in.skip(2);
        }
        else if (opcode == 17)
        {
            d.walkingAnimation = animation(in.u16());
            in.skip(6);
        }
        else if (opcode >= 30 && opcode < 35)
        {
            in.string();
        }
        else if (opcode == 40 || opcode == 41)
        {
            int count = in.u8();
            short[] from = new short[count];
            short[] to = new short[count];
            for (int i = 0; i < count; i++)
            {
                from[i] = (short) in.u16();
                to[i] = (short) in.u16();
            }
            if (opcode == 40)
            {
                d.recolorFrom = from;
                d.recolorTo = to;
            }
            else
            {
                d.retextureFrom = from;
                d.retextureTo = to;
            }
        }
        else if (opcode == 61 || opcode == 62)
        {
            int count = in.u8();
            if (opcode == 61)
            {
                d.modelIds = new int[count];
                for (int i = 0; i < count; i++) d.modelIds[i] = in.i32();
            }
            else
            {
                in.skip(count * 4);
            }
        }
        else if (opcode == 93 || opcode == 99 || opcode == 107 || opcode == 109
            || opcode == 111 || opcode == 122 || opcode == 123 || opcode == 129
            || opcode == 130 || opcode == 145 || opcode == 147)
        {
            // Flag-only opcodes.
        }
        else if (opcode == 97)
        {
            d.widthScale = in.u16();
        }
        else if (opcode == 98)
        {
            d.heightScale = in.u16();
        }
        else if (opcode == 100)
        {
            d.ambient = in.i8();
        }
        else if (opcode == 101)
        {
            d.contrast = in.i8() * 5;
        }
        else if (opcode == 102)
        {
            int bitfield = in.u8();
            int count = 0;
            for (int bits = bitfield; bits != 0; bits >>>= 1) count++;
            for (int i = 0; i < count; i++)
            {
                if ((bitfield & (1 << i)) != 0)
                {
                    in.bigSmart2();
                    in.unsignedShortSmartMinusOne();
                }
            }
        }
        else if (opcode == 106 || opcode == 118)
        {
            d.transforms = true;
            in.skip(opcode == 118 ? 6 : 4);
            int count = in.u8();
            in.skip((count + 1) * 2);
        }
        else if (opcode == 114)
        {
            d.runAnimation = animation(in.u16());
        }
        else if (opcode == 115)
        {
            d.runAnimation = animation(in.u16());
            in.skip(6);
        }
        else if (opcode == 116)
        {
            d.crawlAnimation = animation(in.u16());
        }
        else if (opcode == 117)
        {
            d.crawlAnimation = animation(in.u16());
            in.skip(6);
        }
        else if (opcode == 146)
        {
            in.skip(2);
        }
        else if (opcode == 249)
        {
            int count = in.u8();
            for (int i = 0; i < count; i++)
            {
                boolean stringValue = in.u8() == 1;
                in.skip(3);
                if (stringValue) in.string(); else in.skip(4);
            }
        }
        else if (opcode == 251)
        {
            in.skip(2);
            in.string();
        }
        else if (opcode == 252)
        {
            in.skip(13);
            in.string();
        }
        else if (opcode == 253)
        {
            in.skip(14);
            in.string();
        }
        else
        {
            throw new IllegalArgumentException("Unknown NPC-definition opcode " + opcode);
        }
    }

    private static int animation(int id)
    {
        return id == 0xFFFF ? -1 : id;
    }

    private static final class Reader
    {
        private final byte[] data;
        private int offset;

        private Reader(byte[] data)
        {
            this.data = data;
        }

        int remaining()
        {
            return data.length - offset;
        }

        int u8()
        {
            require(1);
            return data[offset++] & 0xFF;
        }

        int i8()
        {
            return (byte) u8();
        }

        int u16()
        {
            return (u8() << 8) | u8();
        }

        int i32()
        {
            return (u8() << 24) | (u8() << 16) | (u8() << 8) | u8();
        }

        int bigSmart2()
        {
            require(1);
            return (data[offset] & 0x80) == 0 ? u16() : i32() & 0x7FFFFFFF;
        }

        int unsignedShortSmartMinusOne()
        {
            require(1);
            return (data[offset] & 0xFF) < 128 ? u8() - 1 : u16() - 32769;
        }

        String string()
        {
            StringBuilder value = new StringBuilder();
            int next;
            while ((next = u8()) != 0) value.append((char) next);
            return value.toString();
        }

        void skip(int amount)
        {
            require(amount);
            offset += amount;
        }

        void require(int amount)
        {
            if (amount < 0 || offset + amount > data.length)
            {
                throw new IllegalArgumentException("Truncated NPC definition");
            }
        }
    }
}
