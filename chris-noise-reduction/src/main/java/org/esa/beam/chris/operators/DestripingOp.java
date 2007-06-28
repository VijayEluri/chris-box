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
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.AbstractOperator;
import org.esa.beam.framework.gpf.AbstractOperatorSpi;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Raster;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.dataio.chris.ChrisConstants;

import java.awt.Rectangle;

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

    /**
     * Creates an instance of this class.
     *
     * @param spi the operator service provider interface.
     */
    public DestripingOp(OperatorSpi spi) {
        super(spi);
    }

    protected Product initialize(ProgressMonitor pm) throws OperatorException {
        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        for (final Band band : sourceProduct.getBands()) {
            final Band targetBand = ProductUtils.copyBand(band.getName(), sourceProduct, targetProduct);

            if (band.getFlagCoding() != null) {
                final FlagCoding flagCoding = band.getFlagCoding();
                if (targetProduct.getFlagCoding(flagCoding.getName()) == null) {
                    ProductUtils.copyFlagCoding(flagCoding, targetProduct);
                }
                targetBand.setFlagCoding(targetProduct.getFlagCoding(flagCoding.getName()));
            }
        }
        ProductUtils.copyBitmaskDefs(sourceProduct, targetProduct);
        copyMetadataElementsAndAttributes(sourceProduct.getMetadataRoot(), targetProduct.getMetadataRoot());
        copyMetadataElementsAndAttributes(factorProduct.getMetadataRoot().getElement(ChrisConstants.MPH_NAME),
                                          targetProduct.getMetadataRoot().getElement(ChrisConstants.MPH_NAME));

        return targetProduct;
    }

    @Override
    public void computeBand(Raster targetRaster, ProgressMonitor pm) throws OperatorException {
        final String name = targetRaster.getRasterDataNode().getName();
        final Rectangle targetRectangle = targetRaster.getRectangle();

        if (name.startsWith("radiance")) {
            try {
                pm.beginTask("removing vertical striping artifacts", targetRectangle.height);

                final Raster sourceRaster = getRaster(sourceProduct.getBand(name), targetRectangle);
                final Raster factorRaster = getRaster(factorProduct.getBand(name.replace("radiance", "vs_corr")),
                                                      new Rectangle(targetRectangle.x, 0, targetRectangle.width, 1));

                for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; ++y) {
                    for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; ++x) {
                        final int value = (int) (sourceRaster.getInt(x, y) * factorRaster.getDouble(x, 0) + 0.5);
                        targetRaster.setInt(x, y, value);
                    }
                    pm.worked(1);
                }
            } finally {
                pm.done();
            }
        } else {
            getRaster(sourceProduct.getBand(name), targetRectangle, targetRaster.getDataBuffer());
        }
    }

    // todo -- move
    private static void copyMetadataElementsAndAttributes(MetadataElement source, MetadataElement target) {
        for (final MetadataElement element : source.getElements()) {
            target.addElement(element.createDeepClone());
        }
        for (final MetadataAttribute attribute : source.getAttributes()) {
            target.addAttribute(attribute.createDeepClone());
        }
    }


    public static class Spi extends AbstractOperatorSpi {

        public Spi() {
            super(DestripingOp.class, "Destriping");
            // todo -- set description etc.
        }
    }

}
