/*
 *  Copyright (C) 2010-2015 JPEXS, All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.jpexs.decompiler.flash.tags;

import com.jpexs.decompiler.flash.SWF;
import com.jpexs.decompiler.flash.SWFInputStream;
import com.jpexs.decompiler.flash.SWFOutputStream;
import com.jpexs.decompiler.flash.configuration.Configuration;
import com.jpexs.decompiler.flash.helpers.ImageHelper;
import com.jpexs.decompiler.flash.tags.base.AloneTag;
import com.jpexs.decompiler.flash.tags.base.ImageTag;
import com.jpexs.decompiler.flash.tags.enums.ImageFormat;
import com.jpexs.decompiler.flash.types.ALPHABITMAPDATA;
import com.jpexs.decompiler.flash.types.ALPHACOLORMAPDATA;
import com.jpexs.decompiler.flash.types.BasicType;
import com.jpexs.decompiler.flash.types.annotations.Conditional;
import com.jpexs.decompiler.flash.types.annotations.HideInRawEdit;
import com.jpexs.decompiler.flash.types.annotations.Internal;
import com.jpexs.decompiler.flash.types.annotations.SWFType;
import com.jpexs.helpers.ByteArrayRange;
import com.jpexs.helpers.Helper;
import com.jpexs.helpers.SerializableImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.InflaterInputStream;

/**
 *
 * @author JPEXS
 */
public class DefineBitsLossless2Tag extends ImageTag implements AloneTag {

    public static final int ID = 36;

    public static final String NAME = "DefineBitsLossless2";

    @SWFType(BasicType.UI8)
    public int bitmapFormat;

    @SWFType(BasicType.UI16)
    public int bitmapWidth;

    @SWFType(BasicType.UI16)
    public int bitmapHeight;

    @SWFType(BasicType.UI8)
    @Conditional(value = "bitmapFormat", options = {FORMAT_8BIT_COLORMAPPED})
    public int bitmapColorTableSize;

    public ByteArrayRange zlibBitmapData; //TODO: Parse ALPHACOLORMAPDATA,ALPHABITMAPDATA

    public static final int FORMAT_8BIT_COLORMAPPED = 3;

    public static final int FORMAT_32BIT_ARGB = 5;

    @HideInRawEdit
    private ALPHACOLORMAPDATA colorMapData;

    @HideInRawEdit
    private ALPHABITMAPDATA bitmapData;

    @Internal
    private boolean decompressed = false;

    /**
     * Constructor
     *
     * @param swf
     */
    public DefineBitsLossless2Tag(SWF swf) {
        super(swf, ID, NAME, null);
        characterID = swf.getNextCharacterId();
        bitmapFormat = DefineBitsLossless2Tag.FORMAT_32BIT_ARGB;
        bitmapWidth = 1;
        bitmapHeight = 1;
        zlibBitmapData = new ByteArrayRange(createEmptyImage());
        forceWriteAsLong = true;
    }

    public DefineBitsLossless2Tag(SWFInputStream sis, ByteArrayRange data) throws IOException {
        super(sis.getSwf(), ID, NAME, data);
        readData(sis, data, 0, false, false, false);
    }

    @Override
    public final void readData(SWFInputStream sis, ByteArrayRange data, int level, boolean parallel, boolean skipUnusualTags, boolean lazy) throws IOException {
        characterID = sis.readUI16("characterID");
        bitmapFormat = sis.readUI8("bitmapFormat");
        bitmapWidth = sis.readUI16("bitmapWidth");
        bitmapHeight = sis.readUI16("bitmapHeight");
        if (bitmapFormat == FORMAT_8BIT_COLORMAPPED) {
            bitmapColorTableSize = sis.readUI8("bitmapColorTableSize");
        }
        zlibBitmapData = sis.readByteRangeEx(sis.available(), "zlibBitmapData");
    }

    /**
     * Gets data bytes
     *
     * @return Bytes of data
     */
    @Override
    public byte[] getData() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStream os = baos;
        SWFOutputStream sos = new SWFOutputStream(os, getVersion());
        try {
            sos.writeUI16(characterID);
            sos.writeUI8(bitmapFormat);
            sos.writeUI16(bitmapWidth);
            sos.writeUI16(bitmapHeight);
            if (bitmapFormat == FORMAT_8BIT_COLORMAPPED) {
                sos.writeUI8(bitmapColorTableSize);
            }
            sos.write(zlibBitmapData);
        } catch (IOException e) {
            throw new Error("This should never happen.", e);
        }
        return baos.toByteArray();
    }

    private byte[] createEmptyImage() {
        try {
            ALPHABITMAPDATA bitmapData = new ALPHABITMAPDATA();
            bitmapData.bitmapPixelData = new int[]{0xff000000};
            ByteArrayOutputStream bitmapDataOS = new ByteArrayOutputStream();
            SWFOutputStream sos = new SWFOutputStream(bitmapDataOS, getVersion());
            sos.writeALPHABITMAPDATA(bitmapData, FORMAT_32BIT_ARGB, 1, 1);
            ByteArrayOutputStream zlibOS = new ByteArrayOutputStream();
            SWFOutputStream sos2 = new SWFOutputStream(zlibOS, getVersion());
            sos2.writeBytesZlib(bitmapDataOS.toByteArray());
            return zlibOS.toByteArray();
        } catch (IOException ex) {
            Logger.getLogger(DefineBitsLossless2Tag.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public void setImage(byte[] data) throws IOException {
        SerializableImage image = new SerializableImage(ImageHelper.read(data));
        ALPHABITMAPDATA bitmapData = new ALPHABITMAPDATA();
        int width = image.getWidth();
        int height = image.getHeight();
        bitmapData.bitmapPixelData = new int[width * height];
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int pos = 0; pos < pixels.length; pos++) {
            int argb = pixels[pos];
            int a = (argb >> 24) & 0xff;
            int r = (argb >> 16) & 0xff;
            int g = (argb >> 8) & 0xff;
            int b = (argb) & 0xff;

            r = r * a / 255;
            g = g * a / 255;
            b = b * a / 255;

            bitmapData.bitmapPixelData[pos] = ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        }

        int format = FORMAT_32BIT_ARGB;
        ByteArrayOutputStream bitmapDataOS = new ByteArrayOutputStream();
        SWFOutputStream sos = new SWFOutputStream(bitmapDataOS, getVersion());
        sos.writeALPHABITMAPDATA(bitmapData, format, width, height);
        ByteArrayOutputStream zlibOS = new ByteArrayOutputStream();
        SWFOutputStream sos2 = new SWFOutputStream(zlibOS, getVersion());
        sos2.writeBytesZlib(bitmapDataOS.toByteArray());
        zlibBitmapData = new ByteArrayRange(zlibOS.toByteArray());
        bitmapFormat = format;
        bitmapWidth = width;
        bitmapHeight = height;
        decompressed = false;
        clearCache();
        setModified(true);
    }

    public ALPHACOLORMAPDATA getColorMapData() {
        if (!decompressed) {
            uncompressData();
        }
        return colorMapData;
    }

    public ALPHABITMAPDATA getBitmapData() {
        if (!decompressed) {
            uncompressData();
        }
        return bitmapData;
    }

    private void uncompressData() {
        try {
            SWFInputStream sis = new SWFInputStream(swf, Helper.readStream(new InflaterInputStream(new ByteArrayInputStream(zlibBitmapData.getArray(), zlibBitmapData.getPos(), zlibBitmapData.getLength()))));
            if (bitmapFormat == FORMAT_8BIT_COLORMAPPED) {
                colorMapData = sis.readALPHACOLORMAPDATA(bitmapColorTableSize, bitmapWidth, bitmapHeight, "colorMapData");
            }
            if (bitmapFormat == FORMAT_32BIT_ARGB) {
                bitmapData = sis.readALPHABITMAPDATA(bitmapFormat, bitmapWidth, bitmapHeight, "bitmapData");
            }
        } catch (IOException ex) {
        }
        decompressed = true;
    }

    @Override
    public ImageFormat getImageFormat() {
        return ImageFormat.PNG;
    }

    @Override
    public InputStream getImageData() {
        return null;
    }

    @Override
    public SerializableImage getImage() {
        if (cachedImage != null) {
            return cachedImage;
        }
        SerializableImage bi = new SerializableImage(bitmapWidth, bitmapHeight, SerializableImage.TYPE_INT_ARGB);
        ALPHACOLORMAPDATA colorMapData = null;
        ALPHABITMAPDATA bitmapData = null;
        if (bitmapFormat == DefineBitsLossless2Tag.FORMAT_8BIT_COLORMAPPED) {
            colorMapData = getColorMapData();
        }
        if (bitmapFormat == DefineBitsLossless2Tag.FORMAT_32BIT_ARGB) {
            bitmapData = getBitmapData();
        }
        int pos32aligned = 0;
        int pos = 0;
        for (int y = 0; y < bitmapHeight; y++) {
            for (int x = 0; x < bitmapWidth; x++) {
                int c = 0;
                if ((bitmapFormat == DefineBitsLossless2Tag.FORMAT_8BIT_COLORMAPPED)) {
                    int colorTableIndex = colorMapData.colorMapPixelData[pos32aligned] & 0xff;
                    if (colorTableIndex < colorMapData.colorTableRGB.length) {
                        c = multiplyAlpha(colorMapData.colorTableRGB[colorTableIndex]);
                    }
                }
                if ((bitmapFormat == DefineBitsLossless2Tag.FORMAT_32BIT_ARGB)) {
                    c = multiplyAlpha(bitmapData.bitmapPixelData[pos]);
                }
                bi.setRGB(x, y, c);
                pos32aligned++;
                pos++;
            }
            while ((pos32aligned % 4 != 0)) {
                pos32aligned++;
            }
        }

        if (Configuration.cacheImages.get()) {
            cachedImage = bi;
        }

        return bi;
    }
}
