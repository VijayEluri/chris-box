package org.esa.beam.chris.operators;

import com.bc.ceres.core.ProgressMonitor;
import de.gkss.hs.datev2004.Clucov;
import de.gkss.hs.datev2004.DataSet;
import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.ChiSquaredDistribution;
import org.apache.commons.math.distribution.DistributionFactory;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.logging.BeamLogManager;

import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * New class.
 *
 * @author Ralf Quast
 * @version $Revision$ $Date$
 */
@OperatorMetadata(alias = "chris.FindClucovClusters",
                  version = "1.0",
                  authors = "Ralf Quast",
                  copyright = "(c) 2007 by Brockmann Consult",
                  description = "Finds clusters for features extracted from TOA reflectances.")
public class FindClucovClustersOp extends Operator {
    @SourceProduct
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter(alias = "features")
    private String[] sourceBandNames;
    @Parameter
    private String roiExpression;
    @Parameter
    private int clusterCount;

    private transient Band[] sourceBands;
    private transient Map<Short, Band> likelihoodBandMap;
    private transient Band groupBand;
    private transient Band sumBand;
    private transient Clucov clucov;


    @Override
    public void initialize() throws OperatorException {
        sourceBands = new Band[sourceBandNames.length];
        for (int i = 0; i < sourceBandNames.length; i++) {
            String featureBandName = sourceBandNames[i];
            Band band = sourceProduct.getBand(featureBandName);
            if (band == null) {
                throw new OperatorException("feature band not found: " + featureBandName);
            }
            sourceBands[i] = band;
        }

        int width = sourceProduct.getSceneRasterWidth();
        int height = sourceProduct.getSceneRasterHeight();
        targetProduct = new Product("clucov", "clucov", width, height);
        targetProduct.setPreferredTileSize(width, height);

        try {
            computeClusters();
        } catch (IOException e) {
            throw new OperatorException(e);
        }
        clusterCount = clucov.clusterMap.keySet().size();
        likelihoodBandMap = new HashMap<Short, Band>(clusterCount);
        for (short i : clucov.clusterMap.keySet()) {
            final Band targetBand = targetProduct.addBand("likelihood_" + i, ProductData.TYPE_FLOAT64);
            targetBand.setUnit("dl");
            targetBand.setDescription("Cluster membership likelihood");

            likelihoodBandMap.put(i, targetBand);
        }
        storeClustersInProduct();

        groupBand = targetProduct.addBand("group", ProductData.TYPE_UINT8);
        groupBand.setUnit("dl");
        groupBand.setDescription("Cluster group");

        sumBand = targetProduct.addBand("sum", ProductData.TYPE_FLOAT64);
        sumBand.setUnit("dl");
        sumBand.setDescription("Cluster sum");
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        if (clucov == null) {
            try {
                computeClusters();
                clusterCount = clucov.clusterMap.keySet().size();
                likelihoodBandMap = new HashMap<Short, Band>(clusterCount);
                for (short i : clucov.clusterMap.keySet()) {
                    final Band band = targetProduct.addBand("likelihood_" + i, ProductData.TYPE_FLOAT64);
                    targetBand.setUnit("dl");
                    targetBand.setDescription("Cluster membership likelihood");

                    likelihoodBandMap.put(i, band);
                }
                storeClustersInProduct();
                targetProduct.setModified(true);
            } catch (IOException e) {
                throw new OperatorException(e);
            }
        }

        if (targetBand == groupBand) {
            Rectangle rectangle = targetTile.getRectangle();
            final int sourceWidth = sourceProduct.getSceneRasterWidth();
            DataSet ds = clucov.ds;
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    int dsIndex = y * sourceWidth + x;
                    targetTile.setSample(x, y, ds.group[dsIndex]);
                }
            }
        } else if (targetBand == sumBand) {
            final ChiSquaredDistribution dist = DistributionFactory.newInstance().createChiSquareDistribution(sourceBands.length);
            Rectangle rectangle = targetTile.getRectangle();
            final int sourceWidth = sourceProduct.getSceneRasterWidth();
            DataSet ds = clucov.ds;
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    int dsIndex = y * sourceWidth + x;
                    double v = 0.0;
                    try {
                      for (Clucov.Cluster cluster : clucov.clusterMap.values()) {
                        v += 1.0 - dist.cumulativeProbability(cluster.gauss.distancesqu(ds.pt[dsIndex]));
                      }
                    } catch (MathException e) {
                        throw new OperatorException(e);
                    }
                    targetTile.setSample(x, y, v);
                }
            }
        } else {
            final ChiSquaredDistribution dist = DistributionFactory.newInstance().createChiSquareDistribution(sourceBands.length);

            for (short i : clucov.clusterMap.keySet()) {
                if (targetBand == likelihoodBandMap.get(i)) {
                    final Clucov.Cluster cluster = clucov.clusterMap.get(i);
                    Rectangle rectangle = targetTile.getRectangle();
                    final int sourceWidth = sourceProduct.getSceneRasterWidth();
                    DataSet ds = clucov.ds;
                    for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                        for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                            int dsIndex = y * sourceWidth + x;
                            final double v;
                            try {
                                v = 1.0 - dist.cumulativeProbability(cluster.gauss.distancesqu(ds.pt[dsIndex]));
                            } catch (MathException e) {
                                throw new OperatorException(e);
                            }
                            targetTile.setSample(x, y, v);
                        }
                    }
                }
            }
        }
    }

    private void storeClustersInProduct() {
        MetadataElement metadataRoot = targetProduct.getMetadataRoot();
        Set<Short> shorts = clucov.clusterMap.keySet();
        MetadataElement clustersElement = new MetadataElement("clusters");
        metadataRoot.addElement(clustersElement);
        for (Short aShort : shorts) {
            Clucov.Cluster cluster = clucov.clusterMap.get(aShort);
            MetadataElement clusterElement = new MetadataElement("cluster");
            clusterElement.addAttribute(new MetadataAttribute("group", ProductData.createInstance(new short[]{cluster.group}), true));
            clusterElement.addAttribute(new MetadataAttribute("gauss.normfactor", ProductData.createInstance(new double[]{cluster.gauss.normfactor}), true));
            clusterElement.addAttribute(new MetadataAttribute("gauss.cog", ProductData.createInstance(cluster.gauss.cog), true));
            double[][] array = cluster.gauss.covinv.getArray();
            for (int i = 0; i < array.length; i++) {
                clusterElement.addAttribute(new MetadataAttribute("gauss.covinv." + i, ProductData.createInstance(array[i]), true));
            }
            clustersElement.addElement(clusterElement);
        }
    }

    private void computeClusters() throws IOException {
        int width = sourceProduct.getSceneRasterWidth();
        int height = sourceProduct.getSceneRasterHeight();
        double[] scanLine = new double[width];
        double[][] dsVectors = new double[width][sourceBands.length];

        // todo - handle valid expression!
        DataSet ds = new DataSet(width * height, sourceBands.length);
        for (int y = 0; y < height; y++) {
            for (int i = 0; i < sourceBands.length; i++) {
                Band featureBand = sourceBands[i];
                featureBand.readPixels(0, y, width, 1, scanLine, ProgressMonitor.NULL);

                // todo - handle no-data!
                for (int x = 0; x < width; x++) {
                    dsVectors[x][i] = scanLine[x];
                }
            }
            for (int x = 0; x < width; x++) {
                ds.add(dsVectors[x]);
            }
        }
        BeamLogManager.configureSystemLogger(BeamLogManager.createFormatter("clucov", "1.0", "BC"), true);
        clucov = new Clucov(ds, BeamLogManager.getSystemLogger());
        //clucov.
        clucov.initialize(clusterCount);
        clucov.run();
    }

    @Override
    public void dispose() {
        // todo - add any clean-up code here
        clucov = null;
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(FindClucovClustersOp.class);
        }
    }
}
