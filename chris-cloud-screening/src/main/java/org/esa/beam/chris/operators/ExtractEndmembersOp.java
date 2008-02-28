/* 
 * Copyright (C) 2002-2008 by Brockmann Consult
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
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProperty;
import org.esa.beam.unmixing.Endmember;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * todo - API doc
 *
 * @author Ralf Quast
 * @version $Revision$ $Date$
 */
@OperatorMetadata(alias = "chris.ExtractEndmembers",
        version = "1.0",
        authors = "Ralf Quast, Marco Z�hlke",
        copyright = "(c) 2008 Brockmann Consult",
        description = "Extracts endmembers for calculating cloud abundances.")
public class ExtractEndmembersOp extends Operator {

    @SourceProduct(alias = "reflectances")
    private Product reflectanceProduct;
    @SourceProduct(alias = "features")
    private Product featureProduct;
    @SourceProduct(alias = "clusters")
    private Product clusterProduct;

    @Parameter(alias = "cloudClusterIndexes", notEmpty = true, notNull = true)
    private int[] cloudClusterIndexes;
    @Parameter(alias = "surfaceClusterIndexes", notEmpty = true, notNull = true)
    private int[] surfaceClusterIndexes;
    @Parameter(alias = "surfaceClusterLabels", notEmpty = true, notNull = true)
    private String[] surfaceClusterLabels;

    @TargetProperty
    private Endmember[] endmembers;

    /**
     * Constructs a new instance of this class.
     */
    public ExtractEndmembersOp() {
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param reflectanceProduct    the reflectance product.
     * @param featureProduct        the feature product.
     * @param clusterProduct        the cluster product.
     * @param cloudClusterIndexes   the cloud cluster indexes.
     * @param surfaceClusterIndexes the surface cluster indexes.
     * @param surfaceClusterLabels  the surface cluster labels.
     */
    public ExtractEndmembersOp(Product reflectanceProduct, Product featureProduct, Product clusterProduct,
                               int[] cloudClusterIndexes, int[] surfaceClusterIndexes, String[] surfaceClusterLabels) {
        this.reflectanceProduct = reflectanceProduct;
        this.featureProduct = featureProduct;
        this.clusterProduct = clusterProduct;
        this.cloudClusterIndexes = cloudClusterIndexes;
        this.surfaceClusterIndexes = surfaceClusterIndexes;
        this.surfaceClusterLabels = surfaceClusterLabels;
    }

    @Override
    public void initialize() throws OperatorException {
        endmembers = computeEndmembers(ProgressMonitor.NULL);
        setTargetProduct(new Product("empty", "empty", 0, 0));
    }

    private Endmember[] computeEndmembers(ProgressMonitor pm) {
        final Band brightnessBand = featureProduct.getBand("brightness_vis");
        final Band whitenessBand = featureProduct.getBand("whiteness_vis");
        final Band membershipBand = clusterProduct.getBand("membership_mask");

        final Band[] reflectanceBands = findBands(reflectanceProduct, "reflectance");
        final double[] wavelengths = getEndmemberWavelength(reflectanceBands);
        final Endmember[] endmembers = new Endmember[surfaceClusterIndexes.length + 1];

        final int h = membershipBand.getRasterHeight();
        final int w = membershipBand.getRasterWidth();

        int cloudEndmemberX = -1;
        int cloudEndmemberY = -1;
        double maxRatio = 0.0;

        for (int y = 0; y < h; ++y) {
            final Rectangle rectangle = new Rectangle(0, y, w, 1);
            final Tile membershipTile = getSourceTile(membershipBand, rectangle, pm);
            final Tile brightnessTile = getSourceTile(brightnessBand, rectangle, pm);
            final Tile whitenessTile = getSourceTile(whitenessBand, rectangle, pm);

            for (int x = 0; x < w; ++x) {
                if (isContained(membershipTile.getSampleInt(x, y), cloudClusterIndexes)) {
                    if (whitenessTile.getSampleDouble(x, y) > 0.0) {
                        final double ratio = brightnessTile.getSampleDouble(x, y) / whitenessTile.getSampleDouble(x, y);
                        if (cloudEndmemberX == -1 || cloudEndmemberY == -1 || ratio > maxRatio) {
                            cloudEndmemberX = x;
                            cloudEndmemberY = y;
                            maxRatio = ratio;
                        }
                    }
                }
            }
        }
        final double[] reflectances = new double[reflectanceBands.length];

        for (int i = 0; i < reflectanceBands.length; ++i) {
            final Rectangle rectangle = new Rectangle(cloudEndmemberX, cloudEndmemberY, 1, 1);
            final Tile reflectanceTile = getSourceTile(reflectanceBands[i], rectangle, pm);

            reflectances[i] = reflectanceTile.getSampleDouble(cloudEndmemberX, cloudEndmemberY);
        }
        final Endmember em = new Endmember("cloud", wavelengths, reflectances);
        endmembers[0] = em;


        final Band[] probabilityBands = findBands(clusterProduct, "probability");

        ///////////////////////
        final double[][] meanReflectances = new double[probabilityBands.length][reflectanceBands.length];
        final int[] count = new int[probabilityBands.length];

        for (int y = 0; y < h; ++y) {
            final Rectangle rectangle = new Rectangle(0, y, w, 1);

            for (int k = 0; k < probabilityBands.length; ++k) {
                if (isContained(k, surfaceClusterIndexes)) {
                    final Tile probabilityTile = getSourceTile(probabilityBands[k], rectangle, pm);
                    for (int x = 0; x < w; ++x) {
                        if (probabilityTile.getSampleDouble(x, y) > 0.5) {
                            for (int i = 0; i < reflectanceBands.length; ++i) {
                                final Tile reflectanceTile = getSourceTile(reflectanceBands[i], rectangle, pm);
                                meanReflectances[k][i] += reflectanceTile.getSampleDouble(x, y);
                            }
                            ++count[k];
                        }
                    }
                }
            }
        }

        for (int k = 0, j = 1; k < probabilityBands.length; ++k) {
            if (count[k] > 0) {
                for (int i = 0; i < reflectanceBands.length; ++i) {
                    meanReflectances[k][i] /= count[k];
                }
                endmembers[j] = new Endmember(surfaceClusterLabels[j - 1], wavelengths, meanReflectances[k]);
                ++j;
            }
        }

        return endmembers;
    }

    private double[] getEndmemberWavelength(Band[] reflectanceBands) {
        final double[] wavelengths = new double[reflectanceBands.length];
        for (int i = 0; i < reflectanceBands.length; ++i) {
            wavelengths[i] = reflectanceBands[i].getSpectralWavelength();
        }
        return wavelengths;
    }

    private static boolean isContained(int index, int[] indexes) {
        for (int i : indexes) {
            if (index == i) {
                return true;
            }
        }

        return false;
    }

    private static Band[] findBands(Product product, String prefix) {
        final List<Band> bandList = new ArrayList<Band>();

        for (final Band band : product.getBands()) {
            if (band.getName().startsWith(prefix)) {
                bandList.add(band);
            }
        }
        return bandList.toArray(new Band[bandList.size()]);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ExtractEndmembersOp.class);
        }
    }
}
