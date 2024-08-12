/*
 * @(#)AmigaBitmapCodec.java
 * Copyright © 2023 Werner Randelshofer, Switzerland. MIT License.
 */
package org.monte.media.amigabitmap.codec.video;

import org.monte.media.amigabitmap.AmigaBitmapImage;
import org.monte.media.amigabitmap.AmigaBitmapImageFactory;
import org.monte.media.av.Buffer;
import org.monte.media.av.Format;
import org.monte.media.av.FormatKeys.MediaType;
import org.monte.media.av.codec.video.AbstractVideoCodec;
import org.monte.media.ilbm.ColorCyclingMemoryImageSource;
import org.monte.media.ilbm.ILBMDecoder;
import org.monte.media.pbm.PBMDecoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import static org.monte.media.av.BufferFlag.DISCARD;
import static org.monte.media.av.BufferFlag.KEYFRAME;
import static org.monte.media.av.FormatKeys.EncodingKey;
import static org.monte.media.av.FormatKeys.MIME_JAVA;
import static org.monte.media.av.FormatKeys.MediaTypeKey;
import static org.monte.media.av.FormatKeys.MimeTypeKey;
import static org.monte.media.av.codec.video.VideoFormatKeys.DataClassKey;
import static org.monte.media.av.codec.video.VideoFormatKeys.DepthKey;
import static org.monte.media.av.codec.video.VideoFormatKeys.ENCODING_BITMAP_IMAGE;
import static org.monte.media.av.codec.video.VideoFormatKeys.ENCODING_BUFFERED_IMAGE;
import static org.monte.media.av.codec.video.VideoFormatKeys.HeightKey;
import static org.monte.media.av.codec.video.VideoFormatKeys.WidthKey;

/**
 * Decodes media data into a {@code Bitmap}.
 *
 * @author Werner Randelshofer
 */
public class AmigaBitmapCodec extends AbstractVideoCodec {
    public AmigaBitmapCodec() {
        super(new Format[]{
                        new Format(MediaTypeKey, MediaType.VIDEO, MimeTypeKey, MIME_JAVA,
                                EncodingKey, ENCODING_BUFFERED_IMAGE), //
                        new Format(MediaTypeKey, MediaType.VIDEO, MimeTypeKey, MIME_JAVA,
                                EncodingKey, ENCODING_BITMAP_IMAGE, DataClassKey, AmigaBitmapImage.class), //
                },
                new Format[]{
                        new Format(MediaTypeKey, MediaType.VIDEO, MimeTypeKey, MIME_JAVA,
                                EncodingKey, ENCODING_BUFFERED_IMAGE), //
                        new Format(MediaTypeKey, MediaType.VIDEO, MimeTypeKey, MIME_JAVA,
                                EncodingKey, ENCODING_BITMAP_IMAGE, DataClassKey, AmigaBitmapImage.class), //
                });
    }

    @Override
    public Format setOutputFormat(Format f) {
        super.setOutputFormat(f);

        // This codec can not scale an image.
        // Enforce these properties
        if (outputFormat != null) {
            if (inputFormat != null) {
                outputFormat = outputFormat.prepend(inputFormat.intersectKeys(WidthKey, HeightKey, DepthKey));
            }
        }
        return this.outputFormat;
    }

    @Override
    public int process(Buffer in, Buffer out) {
        if (outputFormat.get(EncodingKey) == ENCODING_BITMAP_IMAGE) {
            return encodeToBitmapImage(in, out);
        } else if (outputFormat.get(EncodingKey) == ENCODING_BUFFERED_IMAGE) {
            return encodeToBufferedImage(in, out);
        } else {
            out.setFlag(DISCARD);
            return CODEC_FAILED;
        }
    }

    private int encodeToBitmapImage(Buffer in, Buffer out) {
        out.setMetaTo(in);
        if (in.isFlag(DISCARD)) {
            return CODEC_OK;
        }
        out.format = outputFormat;
        try {
            out.setFlag(KEYFRAME);

            if (in.data instanceof File) {
                File f = (File) in.data;
                boolean success;
                {
                    InputStream ins = new BufferedInputStream(new FileInputStream(f));
                    try {
                        ILBMDecoder d = new ILBMDecoder(ins);
                        ArrayList<AmigaBitmapImage> imgs = d.produceBitmaps();
                        AmigaBitmapImage img = imgs.getFirst();
                        out.data = img;
                        success = true;
                    } catch (IOException e) {
                        success = false;
                    } finally {
                        ins.close();
                    }
                }
                if (!success) {
                    InputStream ins = new BufferedInputStream(new FileInputStream(f));
                    try {
                        PBMDecoder d = new PBMDecoder(ins);
                        ArrayList<ColorCyclingMemoryImageSource> imgs = d.produce();
                        ColorCyclingMemoryImageSource mis = imgs.getFirst();

                        out.data = AmigaBitmapImageFactory.toBitmapImage(mis);
                        success = true;
                    } catch (IOException e) {
                        success = false;
                    } finally {
                        ins.close();
                    }
                }
                if (!success) {
                    BufferedImage img = ImageIO.read(f);
                    out.data = AmigaBitmapImageFactory.toBitmapImage(img);
                    success = true;
                }
            } else if (in.data instanceof AmigaBitmapImage) {
                out.data = in.data;
            } else if (in.data instanceof BufferedImage) {
                out.data = AmigaBitmapImageFactory.toBitmapImage((BufferedImage) in.data);
            }
            return CODEC_OK;
        } catch (IOException e) {
            out.exception = e;
            out.setFlag(DISCARD);
            return CODEC_FAILED;
        }
    }

    private int encodeToBufferedImage(Buffer in, Buffer out) {
        out.setMetaTo(in);
        if (in.isFlag(DISCARD)) {
            return CODEC_OK;
        }
        out.format = outputFormat;
        try {
            out.setFlag(KEYFRAME);

            if (in.data instanceof File) {
                File f = (File) in.data;
                boolean success;
                {
                    InputStream ins = new BufferedInputStream(new FileInputStream(f));
                    try {
                        ILBMDecoder d = new ILBMDecoder(ins);
                        ArrayList<AmigaBitmapImage> imgs = d.produceBitmaps();
                        AmigaBitmapImage img = imgs.getFirst();
                        out.data = AmigaBitmapImageFactory.toBufferedImage(img);
                        success = true;
                    } catch (IOException e) {
                        success = false;
                    } finally {
                        ins.close();
                    }
                }
                if (!success) {
                    InputStream ins = new BufferedInputStream(new FileInputStream(f));
                    try {
                        PBMDecoder d = new PBMDecoder(ins);
                        ArrayList<ColorCyclingMemoryImageSource> imgs = d.produce();
                        ColorCyclingMemoryImageSource mis = imgs.getFirst();
                        out.data = AmigaBitmapImageFactory.toBufferedImage(
                                AmigaBitmapImageFactory.toBitmapImage(mis));
                        success = true;
                    } catch (IOException e) {
                        success = false;
                    } finally {
                        ins.close();
                    }
                }
                if (!success) {
                    BufferedImage img = ImageIO.read(f);
                    out.data = img;
                    success = true;
                }
            } else if (in.data instanceof AmigaBitmapImage) {
                out.data = AmigaBitmapImageFactory.toBufferedImage(
                        (AmigaBitmapImage) in.data);
            } else if (in.data instanceof BufferedImage) {
                out.data = in.data;
            }
            return CODEC_OK;
        } catch (IOException e) {
            out.exception = e;
            out.setFlag(DISCARD);
            return CODEC_FAILED;
        }
    }

}
