/* $Id: $
 *
 * Copyright (C) 2002-2007 by Brockmann Consult
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.chris.operators;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.chris.ChrisConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.AbstractOperator;
import org.esa.beam.framework.gpf.AbstractOperatorSpi;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;
import java.text.MessageFormat;

/**
 * Operator for applying the vertical striping (VS) correction factors calculated by
 * the {@link DestripingFactorsOp}.
 *
 * @author Ralf Quast
 * @author Marco Z�hlke
 * @version $Revision$ $Date$
 */
public class DestripingOp extends AbstractOperator {

    @SourceProduct(alias = "input")
    Product sourceProduct;
    @SourceProduct(alias = "factors")
    Product factorProduct;
    @TargetProduct
    Product targetProduct;

    @Override
    protected Product initialize() throws OperatorException {
        assertValidity(sourceProduct);

        targetProduct = new Product(sourceProduct.getName() + "_NR", sourceProduct.getProductType() + "_NR",
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);

        for (final Band sourceBand : sourceProduct.getBands()) {
            final Band targetBand = ProductUtils.copyBand(sourceBand.getName(), sourceProduct, targetProduct);

            final FlagCoding flagCoding = sourceBand.getFlagCoding();
            if (flagCoding != null) {
                targetBand.setFlagCoding(targetProduct.getFlagCoding(flagCoding.getName()));
            }
        }
        ProductUtils.copyBitmaskDefs(sourceProduct, targetProduct);

        ProductUtils.copyElementsAndAttributes(sourceProduct.getMetadataRoot(), targetProduct.getMetadataRoot());
        setAnnotationString(targetProduct, ChrisConstants.ATTR_NAME_NOISE_REDUCTION,
                            getAnnotationString(factorProduct, ChrisConstants.ATTR_NAME_NOISE_REDUCTION));

        return targetProduct;
    }

    @Override
    public void computeTile(Band band, Tile targetTile) throws OperatorException {
        final String name = band.getName();
        ProgressMonitor pm = createProgressMonitor();
        if (name.startsWith("radiance")) {
            computeRciBand(name, targetTile, pm);
        } else {
            computeMaskBand(name, targetTile, pm);
        }
    }

    private void computeRciBand(String name, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        try {
            pm.beginTask("removing vertical striping artifacts", targetTile.getHeight());

            final Band sourceBand = sourceProduct.getBand(name);
            final Band factorBand = factorProduct.getBand(name.replace("radiance", "vs_corr"));

            final Rectangle targetRectangle = targetTile.getRectangle();
            final Rectangle factorRectangle = new Rectangle(targetRectangle.x, 0, targetRectangle.width, 1);
            final Tile sourceTile = getSourceTile(sourceBand, targetRectangle);
            final Tile factorTile = getSourceTile(factorBand, factorRectangle);

            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; ++y) {
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; ++x) {
                    final int value = (int) (sourceTile.getSampleInt(x, y) * factorTile.getSampleDouble(x, 0) + 0.5);
                    targetTile.setSample(x, y, value);
                }
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    private void computeMaskBand(String name, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        try {
            pm.beginTask("copying mask band", targetTile.getHeight());

            final Rectangle targetRectangle = targetTile.getRectangle();
            final Tile sourceTile = getSourceTile(sourceProduct.getBand(name), targetTile.getRectangle());

            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; ++y) {
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; ++x) {
                    targetTile.setSample(x, y, sourceTile.getSampleInt(x, y));
                }
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    private static void assertValidity(Product product) throws OperatorException {
        try {
            getAnnotationString(product, ChrisConstants.ATTR_NAME_CHRIS_MODE);
            getAnnotationString(product, ChrisConstants.ATTR_NAME_CHRIS_TEMPERATURE);
            final String string = getAnnotationString(product, ChrisConstants.ATTR_NAME_NOISE_REDUCTION);
            if (!string.equalsIgnoreCase("none")) {
                throw new OperatorException(MessageFormat.format(
                    "product ''{0}'' already is noise-corrected", product.getName()));
            }
        } catch (OperatorException e) {
            throw new OperatorException(MessageFormat.format(
                    "product ''{0}'' is not a CHRIS product", product.getName()));
        }
    }

    /**
     * Returns a CHRIS annotation for a product of interest.
     *
     * @param product the product of interest.
     * @param name    the name of the CHRIS annotation.
     *
     * @return the annotation or {@code null} if the annotation could not be found.
     *
     * @throws OperatorException if the annotation could not be read.
     */
    // todo -- move
    private static String getAnnotationString(Product product, String name) throws OperatorException {
        final MetadataElement element = product.getMetadataRoot().getElement(ChrisConstants.MPH_NAME);

        if (element == null) {
            throw new OperatorException(MessageFormat.format("could not get CHRIS annotation ''{0}''", name));
        }
        return element.getAttributeString(name, null);
    }

    // todo -- move
    private static void setAnnotationString(Product product, String name, String value) throws OperatorException {
        MetadataElement element = product.getMetadataRoot().getElement(ChrisConstants.MPH_NAME);
        if (element == null) {
            element = new MetadataElement(ChrisConstants.MPH_NAME);
            product.getMetadataRoot().addElement(element);
        }
        element.setAttributeString(name, value);
    }


    public static class Spi extends AbstractOperatorSpi {

        public Spi() {
            super(DestripingOp.class, "Destriping");
            // todo -- set description etc.
        }
    }

}
