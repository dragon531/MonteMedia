/*
 * @(#)ILBMImageReader.java
 * Copyright © 2023 Werner Randelshofer, Switzerland. MIT License.
 */
package org.monte.media.ilbm;

import org.monte.media.image.AnimatedImageReader;
import org.monte.media.io.ImageInputStreamAdapter;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Reads an image in the Amiga IFF Interleaved Bitmap image format (ILBM).
 *
 * @author Werner Randelshofer
 */
public class ILBMImageReader extends ImageReader implements AnimatedImageReader {

    private ArrayList<ColorCyclingMemoryImageSource> images = null;

    public ILBMImageReader(ILBMImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }

    @Override
    public int getNumImages(boolean allowSearch) throws IOException {
        if (allowSearch && images == null) {
            readImages();
        }
        return images.size();
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
        readImages();
        return images.get(imageIndex).getWidth();
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        readImages();
        return images.get(imageIndex).getHeight();
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
        readImages();
        ColorCyclingMemoryImageSource iip = images.get(imageIndex);

        LinkedList<ImageTypeSpecifier> l = new LinkedList<>();
        l.add(new ImageTypeSpecifier(iip.getColorModel(), //
                iip.getColorModel().createCompatibleSampleModel(iip.getWidth(), iip.getHeight())));
        return l.iterator();
    }

    /**
     * Returns the aspect ratio of the given image (that is, its width divided
     * by its height) as a <code>float</code>. For images that are inherently
     * resizable, this method provides a way to determine the appropriate width
     * given a deired height, or vice versa. For non-resizable images, the true
     * width and height are used.
     *
     * <p>
     * The default implementation simply returns
     * <code>(float)getWidth(imageIndex)/getHeight(imageIndex)</code>.
     *
     * @param imageIndex the index of the image to be queried.
     * @return a <code>float</code> indicating the aspect ratio of the given
     * image.
     * @throws IllegalStateException     if the input source has not been set.
     * @throws IndexOutOfBoundsException if the supplied index is out of
     *                                   bounds.
     * @throws IOException               if an error occurs during reading.
     */
    @Override
    public float getAspectRatio(int imageIndex) throws IOException {
        readImages();
        ColorCyclingMemoryImageSource mis = images.get(imageIndex);
        float ratio = (float) getWidth(imageIndex) / getHeight(imageIndex);
        if (mis.getProperties().containsKey("aspect")) {
            ratio *= (Double) mis.getProperties().get("aspect");
        }
        return ratio;
    }

    @Override
    public IIOMetadata getStreamMetadata() throws IOException {
        return null;
    }

    @Override
    public IIOMetadata getImageMetadata(int imageIndex) throws IOException {
        return null;
    }

    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param)
            throws IOException {
        readImages();

        return images.get(imageIndex).toBufferedImage();
    }

    @Override
    public Image readAnimatedImage(int imageIndex)
            throws IOException {
        readImages();
        ColorCyclingMemoryImageSource ccmis = images.get(imageIndex);
        if (ccmis.isColorCyclingAvailable()) {
            ccmis.start();
        }
        return Toolkit.getDefaultToolkit().createImage(ccmis);
    }

    private void readImages() throws IOException {
        ImageInputStream in = (ImageInputStream) getInput();
        if (in == null) {
            throw new IOException("input is null");
        }
        if (images == null) {
            in.seek(0);
            ILBMDecoder d = new ILBMDecoder(new ImageInputStreamAdapter(in));
            images = d.produce();
        }
    }
}
