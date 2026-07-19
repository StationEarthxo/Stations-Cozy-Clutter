package com.worldbuilder;

/** Minimal object-definition decoder for the model-related cache opcodes. */
final class ObjectDefinitionDecoder
{
    private ObjectDefinitionDecoder()
    {
    }

    static ObjectDefinitionData decode(byte[] data)
    {
        ObjectDefinitionData definition = new ObjectDefinitionData();
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

    private static void decodeOpcode(ObjectDefinitionData d, Reader in, int opcode)
    {
        switch (opcode)
        {
            case 1:
            {
                int count = in.u8();
                d.modelIds = new int[count];
                d.modelTypes = new int[count];
                for (int i = 0; i < count; i++)
                {
                    d.modelIds[i] = in.u16();
                    d.modelTypes[i] = in.u8();
                }
                return;
            }
            case 2:
                d.name = in.string();
                return;
            case 3:
            case 30:
            case 31:
            case 32:
            case 33:
            case 34:
                in.string();
                return;
            case 5:
            {
                int count = in.u8();
                d.modelIds = new int[count];
                d.modelTypes = null;
                for (int i = 0; i < count; i++)
                {
                    d.modelIds[i] = in.u16();
                }
                return;
            }
            case 6:
            {
                d.extendedModelIds = true;
                int count = in.u8();
                d.modelIds = new int[count];
                d.modelTypes = new int[count];
                for (int i = 0; i < count; i++)
                {
                    d.modelIds[i] = in.i32();
                    d.modelTypes[i] = in.u8();
                }
                return;
            }
            case 7:
            {
                d.extendedModelIds = true;
                int count = in.u8();
                d.modelIds = new int[count];
                d.modelTypes = null;
                for (int i = 0; i < count; i++)
                {
                    d.modelIds[i] = in.i32();
                }
                return;
            }
            case 14:
            case 15:
            case 19:
            case 28:
            case 69:
            case 75:
            case 81:
            case 104:
                in.skip(1);
                return;
            case 61:
            case 68:
            case 82:
            case 97:
            case 98:
            case 103:
                in.skip(2);
                return;
            case 24:
            {
                int animation = in.u16();
                d.animationId = animation == 0xFFFF ? -1 : animation;
                return;
            }
            case 29:
                d.ambient = in.i8();
                return;
            case 39:
                d.contrast = in.i8() * 25;
                return;
            case 40:
            {
                int count = in.u8();
                d.recolorFrom = new short[count];
                d.recolorTo = new short[count];
                for (int i = 0; i < count; i++)
                {
                    d.recolorFrom[i] = (short) in.u16();
                    d.recolorTo[i] = (short) in.u16();
                }
                return;
            }
            case 41:
            {
                int count = in.u8();
                d.retextureFrom = new short[count];
                d.retextureTo = new short[count];
                for (int i = 0; i < count; i++)
                {
                    d.retextureFrom[i] = (short) in.u16();
                    d.retextureTo[i] = (short) in.u16();
                }
                return;
            }
            case 62:
                d.rotated = true;
                return;
            case 65:
                d.scaleX = in.u16();
                return;
            case 66:
                d.scaleHeight = in.u16();
                return;
            case 67:
                d.scaleY = in.u16();
                return;
            case 70:
                d.offsetX = in.i16();
                return;
            case 71:
                d.offsetHeight = in.i16();
                return;
            case 72:
                d.offsetY = in.i16();
                return;
            case 77:
            case 92:
            {
                d.transforms = true;
                in.skip(4);
                if (opcode == 92)
                {
                    in.skip(2);
                }
                int count = in.u8();
                in.skip((count + 1) * 2);
                return;
            }
            case 78:
                in.skip(4);
                return;
            case 79:
            {
                in.skip(6);
                int count = in.u8();
                in.skip(count * 2);
                return;
            }
            case 90:
            case 94:
                return;
            case 91:
            case 95:
            case 96:
                in.skip(1);
                return;
            case 93:
                in.skip(6);
                return;
            case 100:
                in.skip(2);
                in.string();
                return;
            case 101:
                in.skip(13);
                in.string();
                return;
            case 102:
                in.skip(15);
                in.string();
                return;
            case 249:
            {
                int count = in.u8();
                for (int i = 0; i < count; i++)
                {
                    boolean stringValue = in.u8() == 1;
                    in.skip(3);
                    if (stringValue)
                    {
                        in.string();
                    }
                    else
                    {
                        in.skip(4);
                    }
                }
                return;
            }
            default:
                // Known flag-only opcodes.
                if ((opcode >= 17 && opcode <= 23) || opcode == 27 || opcode == 64
                    || opcode == 73 || opcode == 74 || opcode == 89 || opcode == 105)
                {
                    return;
                }
                throw new IllegalArgumentException("Unknown object-definition opcode " + opcode);
        }
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

        int i16()
        {
            int value = u16();
            return value > 32767 ? value - 65536 : value;
        }

        int i32()
        {
            return (u8() << 24) | (u8() << 16) | (u8() << 8) | u8();
        }

        String string()
        {
            StringBuilder value = new StringBuilder();
            int next;
            while ((next = u8()) != 0)
            {
                value.append((char) next);
            }
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
                throw new IllegalArgumentException("Truncated object definition");
            }
        }
    }
}
