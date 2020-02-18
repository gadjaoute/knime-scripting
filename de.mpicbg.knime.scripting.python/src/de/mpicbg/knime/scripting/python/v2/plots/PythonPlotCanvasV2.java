package de.mpicbg.knime.scripting.python.v2.plots;

import de.mpicbg.knime.scripting.core.ImageClipper;
import de.mpicbg.knime.scripting.python.v2.plots.AbstractPythonPlotV2NodeModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;


/**
 * A renderer which allows to display Python plots. 
 *
 * @author Holger Brandl, Antje Janosch
 */
@SuppressWarnings("serial")
public class PythonPlotCanvasV2 extends JPanel {
	
	private BufferedImage baseImage;
    private BufferedImage scaledImage;
    private AbstractPythonPlotV2NodeModel m_plotModel;
    
    public PythonPlotCanvasV2(AbstractPythonPlotV2NodeModel plotModel) {
    	
    	this.m_plotModel = plotModel;
    	
        setFocusable(true);
        setPreferredSize(new Dimension(m_plotModel.getConfigWidth(), m_plotModel.getConfigHeight()));

        baseImage = toBufferedImage(m_plotModel.getImage());

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (!isVisible()) {
                    return;
                }

                BufferedImage bufImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
                bufImage.createGraphics();
                AffineTransform at = AffineTransform.getScaleInstance((double) getWidth() / baseImage.getWidth(null),
                        (double) getHeight() / baseImage.getHeight(null));

                AffineTransformOp op = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
                scaledImage = op.filter(baseImage, null);
            }
        });

        // add clipboard copy paste
        addKeyListener(new KeyAdapter() {

            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_C && e.isMetaDown())
                    new ImageClipper().copyToClipboard(PythonPlotCanvasV2.this.baseImage);
            }
        });


    }

    public void paint(Graphics g) {
        g.drawImage(scaledImage != null ? scaledImage : baseImage, 0, 0, null);
    }

    public static BufferedImage toBufferedImage(Image image) {

        if (image instanceof BufferedImage) {
            return (BufferedImage) image;
        }

        // This code ensures that all the pixels in the image are loaded
        image = new ImageIcon(image).getImage();

        // Determine if the image has transparent pixels
        boolean hasAlpha = hasAlpha(image);

        // Create a buffered image with a format that's compatible with the
        // screen
        BufferedImage bimage = null;
        GraphicsEnvironment ge = GraphicsEnvironment
                .getLocalGraphicsEnvironment();
        try {
            // Determine the type of transparency of the new buffered image
            int transparency = Transparency.OPAQUE;
            if (hasAlpha == true) {
                transparency = Transparency.BITMASK;
            }

            // Create the buffered image
            GraphicsDevice gs = ge.getDefaultScreenDevice();
            GraphicsConfiguration gc = gs.getDefaultConfiguration();
            bimage = gc.createCompatibleImage(image.getWidth(null), image
                    .getHeight(null), transparency);
        } catch (HeadlessException e) {
        } // No screen

        if (bimage == null) {
            // Create a buffered image using the default color model
            int type = BufferedImage.TYPE_INT_RGB;
            if (hasAlpha == true) {
                type = BufferedImage.TYPE_INT_ARGB;
            }
            bimage = new BufferedImage(image.getWidth(null), image
                    .getHeight(null), type);
        }

        // Copy image to buffered image
        Graphics g = bimage.createGraphics();

        // Paint the image onto the buffered image
        g.drawImage(image, 0, 0, null);
        g.dispose();

        return bimage;
    }

    public static boolean hasAlpha(Image image) {
        // If buffered image, the color model is readily available
        if (image instanceof BufferedImage) {
            return ((BufferedImage) image).getColorModel().hasAlpha();
        }

        // Use a pixel grabber to retrieve the image's color model;
        // grabbing a single pixel is usually sufficient
        PixelGrabber pg = new PixelGrabber(image, 0, 0, 1, 1, false);
        try {
            pg.grabPixels();
        } catch (InterruptedException e) {
        }

        // Get the image's color model
        return pg.getColorModel().hasAlpha();
    }
}
