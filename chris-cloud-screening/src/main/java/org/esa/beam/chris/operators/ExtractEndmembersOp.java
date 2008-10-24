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
import org.esa.beam.chris.operators.internal.Clusterer;
import org.esa.beam.chris.operators.internal.ExclusiveIndexFilter;
import org.esa.beam.chris.operators.internal.InclusiveIndexFilter;
import org.esa.beam.chris.operators.internal.PixelAccessor;
import org.esa.beam.chris.util.BandFilter;
import org.esa.beam.chris.util.OpUtils;
import org.esa.beam.cluster.EMCluster;
import org.esa.beam.cluster.IndexFilter;
import org.esa.beam.cluster.ProbabilityCalculator;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.SampleCoding;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProperty;
import org.esa.beam.unmixing.Endmember;

import java.awt.*;
import java.util.ArrayList;

/**
 * Extracts endmembers for calculating cloud abundances.
 *
 * @author Ralf Quast
 * @version $Revision$ $Date$
 */
@OperatorMetadata(alias = "chris.ExtractEndmembers",
                  version = "1.0",
                  authors = "Ralf Quast, Marco Z�hlke",
                  copyright = "(c) 2008 Brockmann Consult",
                  description = "Finds endmembers for calculating cloud abundances.",
                  internal = true)
public class ExtractEndmembersOp extends Operator {

    public static final BandFilter BAND_FILTER = new ExclusiveMultiBandFilter(new double[][]{
            {400.0, 440.0},
            {590.0, 600.0},
            {630.0, 636.0},
            {648.0, 658.0},
            {686.0, 709.0},
            {792.0, 799.0},
            {756.0, 775.0},
            {808.0, 840.0},
            {885.0, 985.0},
            {985.0, 1010.0}});

    @SourceProduct(alias = "reflectances")
    private Product reflectanceProduct;
    @SourceProduct(alias = "features")
    private Product featureProduct;
    @SourceProduct(alias = "classification")
    private Product classificationProduct;

    @Parameter
    private String[] featureBandNames;
    @Parameter
    private EMCluster[] clusters;
    @Parameter
    private boolean[] cloudClusters;
    @Parameter
    private boolean[] ignoredClusters;

    @TargetProperty
    private Endmember[] endmembers;

    /**
     * Constructs a new instance of this class.
     */
    public ExtractEndmembersOp() {
    }

    private ExtractEndmembersOp(Product reflectanceProduct,
                                Product featureProduct,
                                Product classificationProduct,
                                String[] featureBandNames,
                                EMCluster[] clusters,
                                boolean[] cloudClusters,
                                boolean[] ignoredClusters) {
        this.reflectanceProduct = reflectanceProduct;
        this.featureProduct = featureProduct;
        this.classificationProduct = classificationProduct;
        this.featureBandNames = featureBandNames;
        this.clusters = clusters;
        this.cloudClusters = cloudClusters;
        this.ignoredClusters = ignoredClusters;
    }

    @Override
    public void initialize() throws OperatorException {
        endmembers = extractEndmembers(reflectanceProduct,
                                       featureProduct,
                                       classificationProduct,
                                       featureBandNames,
                                       clusters,
                                       cloudClusters,
                                       ignoredClusters, ProgressMonitor.NULL);
        setTargetProduct(new Product("EMPTY", "EMPTY_TYPE", 0, 0));
    }

    public static Endmember[] extractEndmembers(Product reflectanceProduct,
                                                Product featureProduct,
                                                Product classificationProduct,
                                                String[] featureBandNames,
                                                EMCluster[] clusters,
                                                final boolean[] cloudClusters,
                                                final boolean[] ignoredClusters, ProgressMonitor pm) {
        final ExtractEndmembersOp op = new ExtractEndmembersOp(reflectanceProduct,
                                                               featureProduct,
                                                               classificationProduct,
                                                               featureBandNames,
                                                               clusters,
                                                               cloudClusters,
                                                               ignoredClusters);
        final Band[] reflectanceBands = OpUtils.findBands(reflectanceProduct, "toa_refl", BAND_FILTER);
        final double[] wavelengths = OpUtils.getWavelenghts(reflectanceBands);

        final PixelAccessor featAccessor = createPixelAccessor(op, featureProduct, featureBandNames, pm);
        final PixelAccessor reflAccessor = createPixelAccessor(op, reflectanceBands, pm);

        final IndexFilter clusterFilter = new ExclusiveIndexFilter(ignoredClusters);
        final IndexFilter surfaceFilter = new ExclusiveIndexFilter(cloudClusters, ignoredClusters);
        final IndexFilter cloudFilter = new InclusiveIndexFilter(cloudClusters);

        final double[] cloudReflectances = extractCloudReflectances(op, featAccessor,
                                                                    reflAccessor,
                                                                    clusters,
                                                                    cloudFilter,
                                                                    clusterFilter, pm);

        final double[][] surfaceReflectances = extractSurfaceReflectances(op, featAccessor,
                                                                          reflAccessor,
                                                                          clusters,
                                                                          surfaceFilter,
                                                                          clusterFilter, pm);

        final ArrayList<Endmember> endmemberList = new ArrayList<Endmember>();
        endmemberList.add(new Endmember("cloud", wavelengths, cloudReflectances));

        final SampleCoding sampleCoding = classificationProduct.getBand("class_indices").getSampleCoding();
        for (int k = 0; k < clusters.length; ++k) {
            if (surfaceFilter.accept(k)) {
                endmemberList.add(new Endmember(sampleCoding.getSampleName(k), wavelengths, surfaceReflectances[k]));
            }
        }

        return endmemberList.toArray(new Endmember[endmemberList.size()]);
    }

    private static double[] extractCloudReflectances(ExtractEndmembersOp op,
                                                     PixelAccessor featAccessor,
                                                     PixelAccessor reflAccessor,
                                                     EMCluster[] clusters,
                                                     IndexFilter cloudFilter,
                                                     IndexFilter clusterFilter,
                                                     ProgressMonitor pm) {
        final ProbabilityCalculator calculator = Clusterer.createProbabilityCalculator(clusters);

        int maxIndex = -1;
        double maxRatio = 0.0;

        for (int i = 0; i < featAccessor.getPixelCount(); ++i) {
            final double[] features = new double[featAccessor.getSampleCount()];
            featAccessor.getSamples(i, features);

            if (features[1] > 0.0) {
                final double[] posteriors = new double[clusters.length];
                calculator.calculate(features, posteriors, clusterFilter);

                for (int k = 0; k < clusters.length; ++k) {
                    if (posteriors[k] > 0.5) {
                        if (cloudFilter.accept(k)) {
                            final double ratio = features[0] / features[1];

                            if (maxIndex == -1 || ratio > maxRatio) {
                                maxIndex = i;
                                maxRatio = ratio;
                            }
                        }
                    }
                }
            }
            op.checkForCancelation(pm);
        }

        return reflAccessor.getSamples(maxIndex, new double[reflAccessor.getSampleCount()]);
    }

    private static double[][] extractSurfaceReflectances(ExtractEndmembersOp op,
                                                         PixelAccessor featAccessor,
                                                         PixelAccessor reflAccessor,
                                                         EMCluster[] clusters,
                                                         IndexFilter surfaceFilter,
                                                         IndexFilter clusterFilter,
                                                         ProgressMonitor pm) {
        final ProbabilityCalculator calculator = Clusterer.createProbabilityCalculator(clusters);

        final double[][] reflectances = new double[clusters.length][reflAccessor.getSampleCount()];
        final int[] count = new int[clusters.length];

        for (int i = 0; i < featAccessor.getPixelCount(); ++i) {
            final double[] features = new double[featAccessor.getSampleCount()];
            featAccessor.getSamples(i, features);
            final double[] posteriors = new double[clusters.length];
            calculator.calculate(features, posteriors, clusterFilter);

            for (int k = 0; k < clusters.length; ++k) {
                if (posteriors[k] > 0.5) {
                    if (surfaceFilter.accept(k)) {
                        reflAccessor.addSamples(i, reflectances[k]);
                        ++count[k];
                        break;
                    }
                }
            }
            op.checkForCancelation(pm);
        }
        for (int k = 0; k < clusters.length; ++k) {
            if (count[k] > 0) {
                for (int i = 0; i < reflAccessor.getSampleCount(); ++i) {
                    reflectances[k][i] /= count[k];
                }
            }
        }

        return reflectances;
    }

    private static PixelAccessor createPixelAccessor(Operator op, Band[] bands, ProgressMonitor pm) {
        final int w = bands[0].getSceneRasterWidth();
        final int h = bands[0].getSceneRasterHeight();
        final Rectangle rectangle = new Rectangle(0, 0, w, h);
        final Tile[] tiles = new Tile[bands.length];

        for (int i = 0; i < tiles.length; ++i) {
            tiles[i] = op.getSourceTile(bands[i], rectangle, pm);
        }

        return new TilePixelAccessor(tiles);
    }

    private static PixelAccessor createPixelAccessor(Operator op, Product product, String[] bandNames,
                                                     ProgressMonitor pm) {
        final int w = product.getSceneRasterWidth();
        final int h = product.getSceneRasterHeight();
        final Rectangle rectangle = new Rectangle(0, 0, w, h);
        final Tile[] tiles = new Tile[bandNames.length];

        for (int i = 0; i < tiles.length; ++i) {
            tiles[i] = op.getSourceTile(product.getBand(bandNames[i]), rectangle, pm);
        }

        return new TilePixelAccessor(tiles);
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ExtractEndmembersOp.class);
        }
    }
}
