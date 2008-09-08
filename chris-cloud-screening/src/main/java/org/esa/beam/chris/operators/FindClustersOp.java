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
import org.esa.beam.cluster.EMCluster;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;

import java.awt.*;
import java.util.Comparator;

/**
 * Clustering operator.
 *
 * @author Ralf Quast
 * @version $Revision$ $Date$
 * @since BEAM 4.2
 */
@OperatorMetadata(alias = "chris.FindClusters",
                  version = "1.0",
                  authors = "Ralf Quast",
                  copyright = "(c) 2008 by Brockmann Consult",
                  description = "Performs an expectation-maximization (EM) cluster analysis.",
                  internal = true)
public class FindClustersOp extends Operator {

    @SourceProduct(alias = "source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(label = "Number of clusters", defaultValue = "14", interval = "[2,99]")
    private int clusterCount;
    @Parameter(label = "Number of iterations", defaultValue = "30", interval = "[1,999]")
    private int iterationCount;
    @Parameter(label = "Random seed",
               defaultValue = "31415",
               description = "The seed used for initializing the EM clustering algorithm.")
    private int seed;
    @Parameter(label = "Source bands", sourceProductId = "source")
    private String[] sourceBandNames;

    private FindClustersOp(Product sourceProduct, int clusterCount, int iterationCount, int seed,
                      String[] sourceBandNames) {
        this.sourceProduct = sourceProduct;
        this.clusterCount = clusterCount;
        this.iterationCount = iterationCount;
        this.seed = seed;
        this.sourceBandNames = sourceBandNames;

        initialize();
    }

    @Override
    public void initialize() throws OperatorException {
        final int w = sourceProduct.getSceneRasterWidth();
        final int h = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product("NULL", "NULL", 0, 0);
        targetProduct.setPreferredTileSize(w, h);
    }

    public static EMCluster[] findClusters(Product sourceProduct,
                                    int clusterCount,
                                    int iterationCount,
                                    int seed,
                                    String[] sourceBandNames,
                                    Comparator<EMCluster> clusterComparator,
                                    ProgressMonitor pm) {
        final FindClustersOp op = new FindClustersOp(sourceProduct, clusterCount, iterationCount, seed, sourceBandNames);

        final Tile[] tiles = new Tile[sourceBandNames.length];
        final int w = sourceProduct.getSceneRasterWidth();
        final int h = sourceProduct.getSceneRasterHeight();

        try {
            pm.beginTask("Performing cluster analysis...", iterationCount);

            final Rectangle sourceRectangle = new Rectangle(0, 0, w, h);
            for (int i = 0; i < sourceBandNames.length; i++) {
                tiles[i] = op.getSourceTile(sourceProduct.getBand(sourceBandNames[i]), sourceRectangle, pm);
            }

            final Clusterer clusterer = new Clusterer(new TilePixelAccessor(tiles), clusterCount, seed);
            for (int i = 0; i < iterationCount; ++i) {
                op.checkForCancelation(pm);
                clusterer.iterate();
                pm.worked(1);
            }

            return clusterer.getClusters(clusterComparator);
        } catch (OperatorException e) {
            throw e;
        } catch (Throwable t) {
            throw new OperatorException(t);
        } finally {
            pm.done();
        }
    }

    private static class TilePixelAccessor implements PixelAccessor {
        private final Tile[] tiles;

        public TilePixelAccessor(Tile[] tiles) {
            this.tiles = tiles;
        }

        @Override
        public void getPixel(int i, double[] samples) {
            final int y = getY(i);
            final int x = getX(i, y);

            for (int j = 0; j < samples.length; ++j) {
                samples[j] = tiles[j].getSampleDouble(tiles[j].getMinX() + x, tiles[j].getMinY() + y);
            }
        }

        @Override
        public int getPixelCount() {
            return tiles[0].getWidth() * tiles[0].getHeight();
        }

        @Override
        public int getSampleCount() {
            return tiles.length;
        }

        private int getX(int i, int y) {
            return i - y * tiles[0].getWidth();
        }

        private int getY(int i) {
            return i / tiles[0].getWidth();
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(FindClustersOp.class);
        }
    }
}
